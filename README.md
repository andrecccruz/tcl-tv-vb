# tv-tcl: debloat e launcher próprio para TV TCL (Google TV)

Limpeza de uma TV TCL com Google TV usando só ADB (sem root e sem desinstalar nada do sistema), mais um launcher
próprio, o **Início**, que troca a tela inicial do Google por uma tela simples, sem anúncios nem recomendações.

Testado em: TCL plataforma **G07**, Android TV 11, interface Google TV
(build `TCL/G07_4K_GB_NF/G07:11/RP1A.200622.001`). Outros modelos TCL com Google TV devem se comportar parecido,
mas os nomes de pacote e as prioridades podem mudar: confira antes.

> **Aviso:** use por sua conta e risco. Tudo aqui é reversível (`pm disable-user` desfaz com `pm enable`), mas
> desativar o pacote errado pode deixar a TV presa reiniciando. Leia a lista "nunca desativar" antes.
> Projeto independente, sem relação com a TCL ou o Google. As marcas citadas pertencem aos seus donos.

## O que tem aqui

| Pasta | Conteúdo |
|---|---|
| `docs/DEBLOAT.md` | Pacotes da TCL e do Google: o que cada um faz, o que dá para desativar e o que **nunca** desativar |
| `docs/NOTAS-TECNICAS.md` | Como o botão Home funciona nessa TV, por que não dá para só trocar o launcher, botão "TCL Channel", medidas de desempenho |
| `scripts/` | `backup.ps1` (salva o estado antes de mexer) e `desativar.ps1` (desativa em lotes pequenos, com log e comando de desfazer) |
| `launcher/` | Código do Início (Java, sem bibliotecas, sem AndroidX) |
| `CLAUDE.md` | Regras de trabalho usadas com o Claude Code neste projeto (servem para qualquer pessoa ou agente) |

## Debloat em 4 passos

1. Na TV: Configurações → Sistema → Sobre → aperte 7 vezes em "Build do Android TV OS" para liberar as opções do
   desenvolvedor. Em Opções do desenvolvedor, ligue a **depuração USB** e a **depuração por rede**.
2. No PC (com [platform-tools](https://developer.android.com/tools/releases/platform-tools)):
   `adb connect <ip-da-tv>:5555` e aceite a autorização que aparece na TV. Confira com `adb devices`.
3. Salve o estado atual: `powershell -ExecutionPolicy Bypass -File scripts\backup.ps1`
4. Desative até 10 pacotes por vez e teste o controle, as entradas/HDMI e os seus apps antes do próximo lote:
   `powershell -ExecutionPolicy Bypass -File scripts\desativar.ps1 com.tcl.esticker com.tcl.hotelmenu`

Para desfazer: `adb shell pm enable <pacote>`. A lista comentada está em [`docs/DEBLOAT.md`](docs/DEBLOAT.md).

Dica sem ADB: o **Modo somente apps** (nas configurações da conta Google, na própria TV) tira os patrocinados do Google TV,
mas nessa TV também desliga o Google Assistente. O slideshow de recomendações no topo continua; ele faz parte do
launcher do Google e só some com um launcher próprio.

## O launcher "Início"

<!-- Sugestão: coloque aqui uma captura de tela em docs/inicio.png -->

- Saudação, data por extenso e relógio
- Uma fileira de apps com o app em foco sempre no centro (favoritos primeiro, se configurados em `Apps.FAVORITES`)
- Barra de menu (↓): Pesquisar (busca por voz do Google), Todos os apps, Ajustes do início, Configurações da TV
- Fundo animado leve (luzes, partículas e ondas; ~4% da CPU total da TV, e zero com outro app na frente),
  com interruptor e quantidade de partículas nos Ajustes
- Segurar OK num app abre a tela dele nas Configurações (forçar parada, limpar cache)
- Serviço de acessibilidade que faz o **botão Home** abrir o Início (detalhes abaixo)

### Compilar

Precisa de JDK 17, Android SDK (platform 34, build-tools 34) e Gradle 8.7+. Não precisa de admin no Windows: dá para
usar versões portáteis dentro de `tools/` (ignorada pelo git):

```
tools/
  jdk/            # JDK 17 extraído (ex.: Eclipse Temurin)
  android-sdk/    # cmdline-tools + "platforms;android-34" + "build-tools;34.0.0"
  gradle/         # Gradle 8.7 extraído
```

```
powershell -ExecutionPolicy Bypass -File launcher\build.ps1
adb install -r launcher\app\build\outputs\apk\debug\app-debug.apk
```

Se `JAVA_HOME`, `ANDROID_HOME` e `gradle` já estiverem configurados no sistema, o script usa eles.
O APK sai assinado com a chave debug local, gerada na primeira compilação.

### Usar como tela inicial

No Google TV não dá para só escolher outro launcher: o `launcherx` do Google tem prioridade maior e, se for desativado,
o sistema entra em loop (ver [`docs/NOTAS-TECNICAS.md`](docs/NOTAS-TECNICAS.md)). Por isso o Início usa um
**serviço de acessibilidade** que consome só o botão Home e abre o Início. Ele também troca a tela do Google pelo
Início quando ela aparece sozinha (ao ligar a TV ou ao sair de um app). Ele não lê o conteúdo da tela e deixa as
outras teclas passarem.

1. Abra o Início, vá em Ajustes do início e ligue **"Usar como tela inicial"**.
2. Ligue o serviço pelo ADB:
   ```
   adb shell settings put secure enabled_accessibility_services br.tvtcl.inicio/.HomeKeyService
   adb shell settings put secure accessibility_enabled 1
   ```
3. Confira: `adb shell dumpsys accessibility | findstr Bound` deve mostrar `Início: botão Home`.

Atenção: o comando do passo 2 **substitui** a lista de serviços de acessibilidade. Se você já usa algum (TalkBack,
por exemplo), veja o valor atual com `adb shell settings get secure enabled_accessibility_services` e junte os
nomes com `:`.

Para desligar:
```
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
```
Ou só desligue "Usar como tela inicial" nos Ajustes do início, sem ADB.

Depois de reinstalar o APK várias vezes seguidas, o Android pode marcar o serviço como travado e desligá-lo
(`Crashed services` no `dumpsys accessibility`). Nesse caso, rode o comando de ligar e reinstale o APK mais uma vez.

Efeito colateral: o Home longo, que no Google TV abre o painel lateral, também fica com o Início.

## Licença

[MIT](LICENSE).
