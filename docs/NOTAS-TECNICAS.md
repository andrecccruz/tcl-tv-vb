# Notas técnicas

## Por que não dá para só trocar o launcher

- O `launcherx` do Google tem `priority=2` no filtro HOME; um launcher de terceiros tem 0. Mesmo com
  `cmd role add-role-holder android.app.role.HOME <pacote> 0` (o `dumpsys role` mostra o novo dono), o sistema
  continua abrindo o launcherx: no logcat, `start home ainfo is com.google.android.apps.tv.launcherx`.
- `pm set-home-activity <componente>` retorna Success, mas sem efeito.
- O ADB não consegue ligar um componente de outro app (`pm enable <pacote>/.HomeAlias` dá
  "Shell cannot change component state"). Por isso o alias HOME do Início é ligado pelo próprio app, no interruptor
  "Usar como tela inicial".
- **Desativar o launcherx entra em loop**: o próximo da fila é `com.google.android.tungsten.setupwraith/.RecoveryActivity`
  (`priority=1`). O sistema tenta abri-lo várias vezes por segundo (`reason is noMoreActivities`) e o foco fica nulo.
  Abaixo dele ainda vem `com.android.tv.settings/.system.FallbackHome` (`priority=-1000`). Reativar o launcherx
  logo em seguida resolve na hora.

Desfazer o papel HOME:
`adb shell cmd role add-role-holder android.app.role.HOME com.google.android.apps.tv.launcherx 0`

## A saída: serviço de acessibilidade (`HomeKeyService`)

- Com `FLAG_REQUEST_FILTER_KEY_EVENTS`, o serviço recebe as teclas **antes** do `interceptKeyBeforeDispatching` da
  TCL, então pode consumir o `KEYCODE_HOME` (o apertar e o soltar) e abrir o Início.
- O sistema também abre o launcherx sem tecla nenhuma: ao ligar a TV e ao sair de um app com Voltar. Para isso, o
  serviço recebe só `TYPE_WINDOW_STATE_CHANGED`, e só do pacote `com.google.android.apps.tv.launcherx` (filtro
  `packageNames` no XML, `canRetrieveWindowContent=false`). Quando o launcherx aparece, ele abre o Início.
- Nos primeiros 5 minutos depois do boot, ao conectar, ele também abre o Início, porque o launcherx pode ter aberto
  antes de o serviço subir.
- Contra loop: no máximo 3 trocas em 15 s.
- Teclas injetadas por `adb shell input keyevent` **não** passam pelo filtro de acessibilidade. O teste tem que ser
  com o controle físico.
- Cada `adb install -r` mata o processo. Depois de muitas reinstalações seguidas, o Android põe o serviço em
  "Crashed services" e limpa `enabled_accessibility_services`. Para voltar: ligue de novo e reinstale o APK uma vez
  (a atualização do pacote limpa a marcação).
- O ADB por Wi-Fi: se `persist.adb.tcp.port` estiver vazio, a porta 5555 pode não voltar depois de reiniciar a TV, e
  a autorização pode ser pedida de novo na tela.

## Botão "TCL Channel" do controle

- `getevent`: o botão gera `KEY_SWITCHVIDEOMODE` (scanCode 227), que vira `keyCode=4020`, uma tecla própria da TCL.
- O `TclPhoneWindowManager.handleCustomKey`, no `system_server`, intercepta a tecla antes dos apps e abre
  `com.tcl.partnercustomizer/.tkey.WithIcon`, que por sua vez abre `com.tcl.waterfall.overseas`. Com ele
  desativado, aparece "Coming Soon".
- O ícone "TCL Channel" na grade de apps é essa mesma activity do `partnercustomizer` (que não deve ser desativado).
- Remapear dá: um serviço de acessibilidade com filtro de teclas recebe o 4020 antes do sistema (o mesmo mecanismo do
  `HomeKeyService`). Não implementado aqui.

## "Liberar memória" não funciona em app comum

O sistema da TCL recusa `killBackgroundProcesses` de apps comuns (logcat: `Invalid packageName` para todos os
pacotes), mesmo com `KILL_BACKGROUND_PROCESSES` e `QUERY_ALL_PACKAGES`. Pelo ADB funciona
(`adb shell am kill <pacote>`). O TGuard da TCL (`com.tcl.guard`) já encerra processos sozinho quando a memória aperta.

## Desempenho do fundo animado

Medido com `launcher\medir.ps1` (30 s com o Início parado na tela). A CPU é em % de 1 núcleo; a TV tem 4.

| Abordagem | CPU | Observação |
|---|---|---|
| View comum, janela inteira redesenhada | 20% | |
| SurfaceView, Canvas por software, meia resolução, 30 FPS | 10% | partículas simples |
| Ondas com degradê na mesma superfície, software | 63% | preencher áreas grandes com degradê é caro |
| Mesma superfície, `lockHardwareCanvas` | 18% | **travou**: `EGL_BAD_ALLOC` na RenderThread ao abrir outra activity |
| Bitmap em meia resolução ampliado por software | 96% | a ampliação custa mais do que economiza |
| **Atual: View com Canvas por hardware, Choreographer limitado a 30 FPS, tudo em bitmaps ALPHA_8 feitos uma vez** | **16%** | 0,1% de quadros com atraso, p99 7 ms |

O que funcionou nessa GPU (Realtek): nada de `Path` nem desfoque por quadro, nada alocado no `onDraw`, só
retângulos com textura. A animação para em `onPause`/`onStop`: com outro app na frente, o gasto é zero.
O compositor (`surfaceflinger`) soma cerca de 15% de 1 núcleo enquanto a animação roda.

## Layout

As telas são feitas em dp de uma TV de 1280x720. `Ui.scaled` ajusta a densidade para que 1280 dp ocupem a largura
real (na TV 4K com saída da interface em 1920x1080, 1 dp = 1,5 px).
