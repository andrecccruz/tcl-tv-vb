# Pacotes da TV TCL G07 (Google TV, Android 11)

Só `pm disable-user --user 0 <pacote>`: o pacote continua instalado e volta com `adb shell pm enable <pacote>`.
Nunca `pm uninstall`, nunca mexer em partições. Até 10 pacotes por vez, e teste antes do próximo lote:
controle remoto, botão de entradas, HDMI e os apps que você usa.

## NUNCA desativar

| Pacote | Por quê |
|---|---|
| `com.tcl.tv`, `com.tcl.tvinput`, `com.tvos`, `com.tcl.system.server` | Entradas, HDMI, antena, base da TV |
| `com.tcl.suspension` | Menu de Entradas |
| `com.google.android.tv.remote.service`, `com.tcl.autopair`, `com.android.bluetooth` | Controle remoto |
| `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending` | Serviços do Google e Play Store |
| `com.android.location.fused` | Sem ele, a TV fica presa reiniciando |
| `com.tcl.inputmethod.international`, `com.google.android.inputmethod.latin` | Teclados |
| `android`, `com.android.systemui`, `com.android.tv.settings*`, `com.tcl.settings`, `com.tcl.providers.config`, `com.android.providers.*`, `com.android.shell` | Sistema e configurações |
| `com.google.android.webview`, `com.tcl.android.webview` | WebView (muitos apps dependem) |
| `com.google.android.permissioncontroller`, `com.google.android.packageinstaller` | Permissões e instalação |
| `com.tcl.factory.view`, `com.tcl.guard`, `com.tcl.initsetup`, `com.tcl.partnercustomizer` | Sistema da TCL (o `partnercustomizer` trata teclas especiais do controle) |
| `com.google.android.apps.tv.launcherx` | Tela inicial do Google: desativar faz o Home entrar em loop (ver NOTAS-TECNICAS) |
| `com.google.android.tungsten.setupwraith` | Fica logo abaixo do launcherx no Home; mexer nele não ajuda |
| `com.tcl.ttvs` | Serviço central da TCL (telemetria, T-Cast, menus, atualização manual); roda como sistema |

## Desativados e testados (sem efeito colateral)

| Pacote | O que é |
|---|---|
| `com.tcl.esticker` | Adesivo de demonstração de loja |
| `com.tcl.hotelmenu` | Modo hotel |
| `com.google.android.videos` | Loja de filmes do Google |
| `com.google.android.play.games` | Google Play Games |
| `com.google.android.feedback` | Envio de relatórios ao Google |
| `com.tcl.waterfall.overseas` | Hub de conteúdo e recomendações da TCL; inicia com a TV e acorda a cada mudança de rede |
| `com.tcl.usercenter` | Conta TCL; roda em segundo plano |
| `com.tcl.notereminder` | Lembretes e avisos da TCL (depende da conta TCL e da caixa de mensagens) |
| `com.tcl.smartalexa` | Integração com a Alexa |
| `com.tcl.channelplus` | TCL_Live: canais IPTV gratuitos da TCL, registrado como entrada de TV (as entradas HDMI/antena do `tcl.tvinput` continuam) |
| `com.tcl.messagebox` | Caixa de mensagens e promoções da TCL. Efeito: os avisos de firmware podem parar; cheque em Configurações → Sistema → Sobre → Atualização |
| `com.tcl.tv.tclhome_passive` | "TCL Home" passivo: abre no boot, desenha por cima de outras telas, ligado à conta TCL |
| `com.tcl.dashboard` | Painel da TCL com "GiftBox" de promoções |

Depois de desativar o `com.tcl.waterfall.overseas`, o botão **TCL Channel** do controle passa a mostrar
"Coming Soon" (ver NOTAS-TECNICAS).

Regionais, que costumam vir desativados de fábrica no Brasil: `au.com.stan.and` (Stan, Austrália),
`tv.wuaki.apptv` (Rakuten TV).

## Depende do uso

| Pacote | O que é | Desativar se... |
|---|---|---|
| `com.google.android.youtube.tvmusic` | YouTube Music | não usa |
| `com.amazon.amazonvideo.livingroom` | Prime Video | não usa |
| `com.google.android.marvin.talkback` | TalkBack (leitor de tela) | não precisa de acessibilidade |
| `com.google.android.katniss` | Busca por voz / Google Assistente | não usa voz |
| `com.google.android.tv.assistant` | Google Assistente | não usa voz |
| `com.tcl.assistant` | Integra o Assistente ao controle (voz ao segurar o botão, LED) | não usa voz |
| `com.google.android.apps.mediashell` | Chromecast embutido | não transmite do celular |
| `com.tcl.miracast` | Espelhar a tela (Miracast; o Samsung DeX usa isso) | não espelha |
| `com.tcl.MultiScreenInteraction_TV` | T-Cast (app TCL no celular) | não usa o T-Cast |

## Investigados: não são anúncio (manter)

| Pacote | O que é |
|---|---|
| `com.tcl.t_solo` | "Listen Alone": áudio só no fone Bluetooth |
| `com.tcl.keyhelp` | Ajuda do menu e teclas virtuais do controle |
| `com.tcl.micmanager` | Microfone USB |
| `com.google.android.apps.nbu.smartconnect.tv` | Economia de dados / VPN do Google |
| `com.google.android.apps.tv.dreamx` | Protetor de tela (modo ambiente); ajuste nas Configurações em vez de desativar |

## Como investigar um pacote desconhecido

```
adb shell dumpsys package <pacote>      # activities, serviços, receivers de boot, permissões, uid
adb shell pidof <pacote>                # está rodando?
adb shell dumpsys tv_input              # é uma entrada de TV?
```
Se roda como `android.uid.system`, registra entrada de TV ou é usado por algo da lista "nunca desativar",
desative sozinho, num lote só dele.
