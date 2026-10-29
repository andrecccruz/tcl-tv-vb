package br.tvtcl.inicio;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Tela inicial: saudação e data à esquerda, hora à direita, uma fileira de apps com o foco fixo no centro e o menu
 * do rodapé. O foco é controlado aqui (fileira ou menu), não pelo sistema de foco do Android.
 * <p>
 * Controle: ← → deslizam a fileira (ou andam no menu), ↓ vai para o menu, ↑ volta, OK abre, segurar OK abre a tela
 * do app nas Configurações (para forçar parada).
 */
public class MainActivity extends Activity {

    private static final long TICK_MS = 60_000;
    private static final Locale PT_BR = new Locale("pt", "BR");

    /** Por processo: a abertura só passa quando o launcher inicia, não ao voltar de um app. */
    private static boolean introPlayed;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d 'de' MMMM", PT_BR);
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            handler.postDelayed(this, TICK_MS);
        }
    };
    private final Runnable longPress = this::onLongPress;

    private BackgroundView background;
    private IntroView intro;
    private View content;
    private AppRowView row;
    private MenuBarView menu;
    private TextView greeting;
    private TextView date;
    private boolean menuMode;
    private boolean okDown;
    private boolean longPressFired;
    private boolean stoppedSinceResume;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.scaled(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // O BackgroundView é opaco: o fundo da janela só seria desenhado à toa por baixo dele.
        getWindow().setBackgroundDrawable(null);

        background = findViewById(R.id.background);
        intro = findViewById(R.id.intro);
        content = findViewById(R.id.content);
        row = findViewById(R.id.row);
        menu = findViewById(R.id.menu);
        greeting = findViewById(R.id.greeting);
        date = findViewById(R.id.date);

        if (!introPlayed && Prefs.animatedBackground(this)) {
            content.setAlpha(0f);
            intro.play(this::revealContent);
        }
        introPlayed = true;
    }

    /** Depois da faixa de luz: a interface aparece subindo de leve. */
    private void revealContent() {
        content.setTranslationY(Ui.dp(this, 18));
        content.animate().alpha(1f).translationY(0f).setDuration(700)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        stoppedSinceResume = false;
        // Relido a cada volta, para valer na hora o que mudou em Ajustes do início.
        background.configure(Prefs.animatedBackground(this), Prefs.particles(this));
        background.start();
        // Recarrega sempre: apps podem ter sido instalados ou removidos enquanto outro app estava aberto.
        row.setApps(Apps.load(this, AppRowView.tilePx(this), true));
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Com outro app (ou os Ajustes) na frente, a animação não gasta nada.
        background.stop();
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(longPress);
        okDown = false;
    }

    @Override
    protected void onStop() {
        background.stop();
        stoppedSinceResume = true;
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Home apertado com o Início já na tela: volta ao primeiro app da fileira. Voltando de um app, fica onde estava.
        if (!stoppedSinceResume) {
            setMenuMode(false);
            row.select(0, true);
        }
    }

    @Override
    public void onBackPressed() {
        // Como tela inicial, "voltar" não tem para onde ir. Aberto pela grade de apps, fecha normalmente.
        if (getIntent() == null || !getIntent().hasCategory(Intent.CATEGORY_HOME)) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (intro.isPlaying()) {
            if (down) {
                intro.finish();   // qualquer tecla pula a abertura
            }
            return true;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    int delta = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    if (menuMode) {
                        menu.move(delta);
                    } else {
                        row.move(delta);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && !menuMode) {
                    setMenuMode(true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && menuMode) {
                    setMenuMode(false);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                handleOk(event);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (menuMode) {
                    if (!down) {
                        setMenuMode(false);
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    /** OK abre ao soltar; segurado (tempo padrão do Android), abre a tela do app nas Configurações. */
    private void handleOk(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                okDown = true;
                longPressFired = false;
                if (!menuMode) {
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
            }
        } else if (okDown) {
            okDown = false;
            handler.removeCallbacks(longPress);
            if (!longPressFired && !event.isCanceled()) {
                activate();
            }
        }
    }

    private void onLongPress() {
        longPressFired = true;
        Apps.Entry app = row.selected();
        if (app != null) {
            Apps.openDetails(this, app.component);
        }
    }

    private void activate() {
        if (!menuMode) {
            Apps.Entry app = row.selected();
            if (app != null) {
                Apps.open(this, app.component);
            }
            return;
        }
        switch (menu.focused()) {
            case MenuBarView.SEARCH:
                openSearch();
                break;
            case MenuBarView.ALL_APPS:
                Apps.start(this, new Intent(this, AllAppsActivity.class));
                break;
            case MenuBarView.LAUNCHER_SETTINGS:
                Apps.start(this, new Intent(this, SettingsActivity.class));
                break;
            case MenuBarView.SETTINGS:
                Apps.start(this, new Intent(Settings.ACTION_SETTINGS));
                break;
            default:
                break;
        }
    }

    /** Busca do sistema: a do Google TV (busca global), senão o Assistente. */
    private void openSearch() {
        String[] actions = {"android.search.action.GLOBAL_SEARCH", Intent.ACTION_ASSIST, Intent.ACTION_WEB_SEARCH};
        for (String action : actions) {
            try {
                startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        Toast.makeText(this, R.string.no_search, Toast.LENGTH_SHORT).show();
    }

    private void setMenuMode(boolean on) {
        if (menuMode == on) {
            return;
        }
        menuMode = on;
        handler.removeCallbacks(longPress);
        menu.setExpanded(on);
        row.setDimmed(on);
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int text;
        if (hour >= 5 && hour < 12) {
            text = R.string.good_morning;
        } else if (hour >= 12 && hour < 18) {
            text = R.string.good_afternoon;
        } else {
            text = R.string.good_evening;
        }
        greeting.setText(text);
        String today = dateFormat.format(new Date());
        date.setText(today.substring(0, 1).toUpperCase(PT_BR) + today.substring(1));
    }
}
