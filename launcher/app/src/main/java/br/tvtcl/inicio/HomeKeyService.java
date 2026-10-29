package br.tvtcl.inicio;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Faz o botão Home do controle abrir o Início, e troca a tela do Google pelo Início quando ela aparece sozinha.
 * <p>
 * Nesta TV não dá para trocar a tela inicial de verdade: o launcherx (prioridade 2) e o setupwraith (prioridade 1)
 * vêm antes do Início, e sem eles o Home entra em loop (ver docs/NOTAS-TECNICAS.md). Um serviço de acessibilidade com filtro de teclas
 * recebe o Home antes do sistema (antes do interceptKeyBeforeDispatching da TCL) e pode consumi-lo.
 * <p>
 * O sistema também abre o launcherx sem tecla nenhuma: ao ligar a TV e ao sair de um app com Voltar. Para esses casos
 * o serviço recebe só o evento "janela mudou" do pacote do launcherx (filtrado no XML, sem ler a tela) e abre o Início.
 * Isso só vale com "Usar como tela inicial" ligado em Ajustes do Início.
 * <p>
 * Só o Home é tocado; todas as outras teclas seguem normalmente. Liga e desliga por ADB (comandos no README).
 */
public class HomeKeyService extends AccessibilityService {

    private static final String TAG = "InicioHomeKey";
    private static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx";
    /** Logo depois de ligar a TV, o launcherx pode ter aberto antes de o serviço conectar. */
    private static final long BOOT_WINDOW_MS = 5 * 60_000;
    /** Trava contra loop (por exemplo, se o Início fechar com erro): no máximo 3 trocas em 15 s. */
    private static final int MAX_REDIRECTS = 3;
    private static final long REDIRECT_WINDOW_MS = 15_000;

    private final long[] recentRedirects = new long[MAX_REDIRECTS];
    private int redirectSlot;

    @Override
    protected void onServiceConnected() {
        // Reforça o pedido de filtro de teclas além do XML (alguns sistemas só respeitam o que vem daqui).
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        Log.i(TAG, "conectado, flags=" + info.flags);

        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && useAsHome()) {
            Log.i(TAG, "TV recém-ligada: abrindo o Início");
            openHome();
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) {
            return false;
        }
        // Consome o apertar e o soltar (para o sistema não ver nenhum dos dois) e abre o Início ao soltar.
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            openHome();
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null
                || !GOOGLE_HOME.contentEquals(event.getPackageName())
                || !useAsHome()) {
            return;
        }
        if (tooManyRedirects()) {
            Log.w(TAG, "launcherx voltou várias vezes seguidas: deixando ele na tela");
            return;
        }
        openHome();
    }

    private boolean tooManyRedirects() {
        long now = SystemClock.elapsedRealtime();
        long oldest = recentRedirects[redirectSlot];
        if (oldest != 0 && now - oldest < REDIRECT_WINDOW_MS) {
            return true;
        }
        recentRedirects[redirectSlot] = now;
        redirectSlot = (redirectSlot + 1) % MAX_REDIRECTS;
        return false;
    }

    /** O interruptor "Usar como tela inicial" liga o alias HOME; desligado, a tela do Google fica em paz. */
    private boolean useAsHome() {
        ComponentName alias = new ComponentName(this, MainActivity.class.getPackage().getName() + ".HomeAlias");
        return getPackageManager().getComponentEnabledSetting(alias)
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)   // faz o Início se comportar como tela inicial (Voltar não fecha)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            // Se algo der errado, o Home simplesmente não faz nada; o serviço pode ser desligado por ADB.
        }
    }

    @Override
    public void onInterrupt() {
    }
}
