# Testes E2E (Maestro)

Os mesmos 13 fluxos rodam no **Android** e no **iOS**, sem uma linha de diferença entre as duas
execuções — o que o app tem de plataforma-específico foi resolvido *no app*, não no YAML (§2.5).

```bash
# Android
./gradlew :app:android:installDebug                       # instala o APK de debug (o de release não serve)
maestro --device emulator-5554 test .maestro              # roda tudo

# iOS
xcrun simctl install <UDID> iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/Finsight.app
maestro --device <UDID> test .maestro                     # roda tudo

maestro --device <alvo> test .maestro/flows/budgets/lifecycle.yaml   # um fluxo só (§2.3)
```

A suíte roda **à mão**: hoje não há script que a prepare nem CI que a rode (§2.1). Ela exige um
aparelho fixado por plataforma — um emulador **API 36, perfil `pixel_6`** (§2.2) e um simulador
**iPhone 16 / iOS 18.5** (§2.2.2), os dois **em inglês, com teclado de tela**. É pré-condição de
quem executa, que nada verifica nem conserta, e que em boa parte não tem conserto depois do boot.
Confira as linhas da **§2.2** antes de rodar: num aparelho divergente, vermelho ou verde não conta.

Artefatos de falha (log, captura, hierarquia) vão para `.maestro/report/` e `~/.maestro/tests/`.

---

## 1. O que esta suíte é dona (e o que não é)

O E2E dirige o app de verdade num aparelho de verdade. Ele é dono **da travessia** — de que telas,
navegação, modais e persistência se sustentam quando uma pessoa realmente toca por eles. Não é dono
das regras.

**É dono de:** jornadas de negócio de ponta a ponta; contratos entre telas; persistência real
observada pela UI; o que o app **oferece** em cada estado (botão habilitado, comando que aparece e
some); e o que só existe no aparelho — relógio do sistema, teclado, gesto de voltar.

**Não é dono de:** correção de regra de negócio; formatação; validação de campo; combinatória de
estados; layout e pixels; performance.

Na pirâmide, é o anel mais externo: a suíte unitária (`./gradlew allTests`) é dona do comportamento,
esta é dona da jornada. São os únicos testes que rodam o sistema montado — logo, os únicos que podem
falhar por integração, e é só por isso que valem o que custam.

**O custo, com números.** A suíte inteira leva **~28 minutos** para 13 fluxos. Os dois de fumaça somam
menos de 20 segundos; os de jornada custam de 1m a 4m30 **cada um**. Isso é o orçamento (§6), e é
o que torna "adicionar um fluxo" uma decisão, não uma adição livre. Um fluxo que duplica o que um
teste de ViewModel já prova custa dois minutos de emulador para não contar nada de novo.

Meça com a máquina ociosa. Um build Gradle rodando ao lado disputa CPU com o emulador e a mesma
suíte sobe para mais de 30 minutos — um número que não descreve a suíte e não deve entrar aqui.

## 2. Rodar a suíte

```bash
maestro --device <alvo> test .maestro                      # roda tudo
maestro --device <alvo> test --include-tags smoke .maestro # só os fluxos com a tag `smoke`
```

`<alvo>` é o serial do emulador (`emulator-5554`) ou o UDID do simulador, e **a flag vem antes do
`test`**. Reinstale o build de debug sempre que mexer no app: `./gradlew :app:android:installDebug`
no Android, reconstruir e `xcrun simctl install` no iOS (§2.2.2).

O caminho é `.maestro`, o **workspace**. Apontar para dentro dele muda o que roda — e o modo mais
barato de perder um run é apontar para `.maestro/flows`, que não acha fluxo nenhum e sai com
código 0. A tabela da §2.3 diz o que cada caminho faz.

Precisa da CLI do Maestro (`curl -Ls https://get.maestro.mobile.dev | bash`) e do aparelho da §2.2
ligado. No iOS não é preciso mais nada: o `idb` deixou de ser dependência no Maestro 1.18, e o
driver XCUITest de hoje só pede as ferramentas de linha de comando do Xcode.

Rode com o build de **debug** — o de release não serve, e por três motivos, todos do mesmo lugar.
O ferramental que um aparelho não pode oferecer vive em **`:app:debug`**, um módulo KMP que os dois
apps agregam: o relógio móvel de que três fluxos dependem (§5.2, padrão 9), o suporte respondido da
memória em vez do Firestore (`InMemorySupportRepository`) e a fonte de câmbio que não cota nada
(`OfflineRateSource`). O Android o traz por `debugImplementation`; o iOS, pela configuração Debug
do Xcode, que passa `-Pfinsight.debugTools=true` ao Gradle — Kotlin/Native não tem build types, e
essa flag é o que faz as vezes deles. Num build de release o módulo não é compilado.

O armazenamento do suporte morre com o processo, então `support/lifecycle` é o único fluxo que
**não pode** relançar o app.

Duas ferramentas de inspeção: `maestro studio` abre um inspetor sobre o app em execução, e
`maestro hierarchy` despeja a árvore de acessibilidade — o jeito mais rápido de descobrir o que uma
tela de fato expõe.

### 2.1 Quem roda é quem responde

Hoje não existe script de preparo, alvo Gradle, `make e2e` nem job de CI por trás desta pasta:
acertar os dois ficou fora do escopo de quando a suíte entrou, e ninguém voltou a eles. Não é uma
proibição — é o estado atual, e enquanto ele durar **o aparelho não tem outro dono senão quem
executa**. Metade das linhas da §2.2 é lida no boot da AVD e a outra metade exige que ela tenha sido
criada em inglês, então nada as conserta com o emulador ligado, e nenhum comando avisa quando estão
erradas: sobra conferir antes. No iOS é o oposto e igualmente traiçoeiro: o CoreSimulator
**reinjeta o idioma do Mac a cada boot**, então as chaves da §2.2.2 têm de ser reescritas *depois*
de ligar, todas as vezes. Quem muda uma tela é quem responde se a travessia continua de pé.

Na prática, para quem for rodar — pessoa ou agente de IA, sem distinção:

1. **Montar o aparelho é parte da tarefa.** Se não há AVD ou simulador que sirva, crie-o (§2.2,
   §2.2.2). Não rode "no que estiver conectado" — e com mais de um ligado, fixe o alvo (§2.2.1).
2. **Conferir as linhas da §2.2 à mão, antes do run**, e **reinstalar o build de debug**. Menos
   de um minuto, contra meia hora de resultado que não vale nada.
3. **Reportar em que aparelho rodou.** Um "13/13 verde" sem o aparelho ao lado não é um resultado,
   é uma afirmação sem lastro — e um agente que só imprime o placar está reportando exatamente isso.
4. **Vermelho não é ambiente até que a §4 diga que é.** A pergunta 1 daquela lista existe para ser
   respondida com dados, não usada como explicação de saída.

Nada disso é dispensável porque "a suíte passou da última vez". O aparelho é entrada do teste; o
único jeito de saber qual entrada foi usada é olhar.

### 2.2 O dispositivo de referência

A suíte roda num aparelho só, e isso não é frescura: cada linha abaixo é uma **entrada do teste**.
Deixá-la livre é aceitar entrada aleatória.

| Fixado | Valor | Como conferir | Por que é fixo |
|---|---|---|---|
| API | 36 | `ro.build.version.sdk` | Diálogos de permissão, animações e gestos de sistema mudam entre versões |
| Perfil | `pixel_6` — 1080x2400, densidade 420 | `wm size` e `wm density` | Os fluxos rolam para alcançar o que está abaixo da dobra; densidade e altura decidem se o `scrollUntilVisible` acha o campo. A folha de adicionar transação põe o botão de enviar abaixo da dobra num perfil e acima em outro |
| Idioma | inglês (`persist.sys.locale`) | `getprop`, com fallback em `ro.product.locale` | As asserções leem figuras e rótulos renderizados; o locale muda separador decimal, ordem de data e as palavras assertadas |
| Sem teclado físico | `hw.keyboard = no` (lido no boot) | `am get-config` (`nokeys`) | Com um teclado físico anunciado, o Gboard troca o teclado por uma barra flutuante que se sobrepõe à folha aberta, e o texto digitado por baixo se perde |
| Teclado virtual ativo | um IME padrão instalado | `settings get secure default_input_method` | Sem IME o primeiro `inputText` de todo fluxo falha, e por um motivo que nenhuma mensagem nomeia |
| `hw.keyboard.lid` | `no` (lido no boot) | — (escrito no `config.ini` antes do boot) | A tampa aberta é a outra metade do que convida a barra flutuante |
| `show_ime_with_hard_keyboard` | `0` | `settings get secure show_ime_with_hard_keyboard` | Ligar *parece* pedir um teclado e faz o oposto. Só tem efeito onde há teclado físico — que a linha acima já exclui — então é reforço, não a garantia |

**Não há oitava entrada, e onde havia era uma crença errada.** O `disableAnimations` do
`config.yaml` é um recurso do **Maestro Cloud**: ele não toca em aparelho local, nem no emulador nem
no simulador ([docs](https://docs.maestro.dev/reference/workspace-configuration)). Este documento
já afirmou o contrário, e quem lesse aquilo procuraria no lugar errado quando um toque se perdesse.
A chave continua declarada, porque o dia em que a suíte rodar na cloud ela passa a valer — mas
**localmente as animações estão ligadas nos dois aparelhos, sempre**, e é por isso que a defesa
contra toque perdido é o `waitForAnimationToEnd` escrito nos fluxos (§5.2, padrão 3), não uma
configuração. Não adianta conferir por `adb`: num aparelho correto
`settings get global window_animation_scale` responde `1.0`, e isso está certo.

**Um perfil divergente não é "meu ambiente", é um resultado inválido** — vermelho ou verde nele não
conta, e um tablet em inglês na API 36 bateria em tudo o mais e ainda assim não contaria. Nada
verifica isso por você: a coluna do meio existe para você conferir à mão antes de rodar. As sete
linhas da tabela, em ordem, são estes sete comandos:

```bash
adb shell getprop ro.build.version.sdk                       # 36
adb shell wm size                                            # 1080x2400
adb shell wm density                                         # 420
adb shell getprop persist.sys.locale || adb shell getprop ro.product.locale   # en-US
adb shell am get-config                                      # contém `-en-rUS-` e `-nokeys-`
adb shell settings get secure default_input_method           # um IME, não `null`
adb shell settings get secure show_ime_with_hard_keyboard    # 0
```

`am get-config` responde por duas linhas de uma vez: é ele que prova o `nokeys`, e é onde o
`hw.keyboard.lid` do `config.ini` deixa de ser uma aposta. Nenhum dos sete conserta nada — quatro
não têm conserto com a AVD ligada, e é para isso que serve criar a AVD certa uma vez.

A ausência de teclado físico sai de `adb shell am get-config`, a `Configuration` que o próprio app
resolve — `nokeys` é a palavra do Android para ela:

```
...-en-rUS-...-420dpi-finger-keysexposed-nokeys-navhidden-nonav-2400x1080-v36
```

Só `nokeys` importa dessa linha. A densidade nela é o *nome do bucket* sempre que existe um (480 sai
como `xxhdpi`), então conferir número contra ela funciona para 420 e para de funcionar em silêncio
para quem repinar o perfil — a tela vem de `wm size` e `wm density`, que sempre dão o número.

O idioma **não se ajusta depois**: a propriedade exige root e reinício do framework, e o `pm clear`
que cada fluxo executa apaga qualquer idioma por app. Crie a AVD já em inglês.

O teclado também **não se conserta tarde demais**: `hw.keyboard` e `hw.keyboard.lid` são lidos no
boot e nenhum comando `adb` os muda depois. O `avdmanager` não tem flag para nenhum dos dois — ele
aceita um perfil de aparelho e mais nada — então eles vão no `config.ini` da AVD **depois de
criada** e antes de ligá-la. Vale para as duas metades: `-d pixel_6` já dá `hw.keyboard = no`, mas
deixa `hw.keyboard.lid = yes`, que é justamente o que convida a barra flutuante.

```bash
IMAGE="system-images;android-36;google_apis_playstore;arm64-v8a"
sdkmanager "$IMAGE"                                            # uma vez
avdmanager create avd -n finsight_e2e -d pixel_6 -k "$IMAGE" <<< "no"

CFG=~/.android/avd/finsight_e2e.avd/config.ini                 # as chaves podem não existir:
grep -v -E '^hw\.keyboard(\.lid)? *=' "$CFG" > "$CFG.tmp"      # tire as que houver...
printf 'hw.keyboard = no\nhw.keyboard.lid = no\n' >> "$CFG.tmp"  # ...e acrescente as suas
mv "$CFG.tmp" "$CFG"

emulator -avd finsight_e2e -no-snapshot-load -no-boot-anim      # só depois do config.ini
```

O `<<< "no"` responde ao prompt de hardware customizado que o `avdmanager` faz sempre que a entrada
padrão é um terminal.

Um aparelho montado à mão é um aparelho montado diferente a cada vez, e cada linha dele é uma
entrada do teste — monte a AVD uma vez e reutilize sempre a mesma.

### 2.2.1 Mais de um aparelho ligado

Com dois aparelhos conectados nada disto vale por si: os sete comandos da §2.2 podem conferir um
aparelho e o run acontecer noutro, sem erro que denuncie. `adb` recusa (`more than one device`), mas
`installDebug` e `maestro test` escolhem sozinhos. Descubra quem é quem e fixe o alvo:

```bash
adb devices -l                                    # os seriais ligados
adb -s emulator-5554 emu avd name                 # qual AVD é cada serial
export ANDROID_SERIAL=emulator-5554               # vale para o adb e para o installDebug
maestro test --device emulator-5554 .maestro      # o Maestro tem a sua própria flag
```

`ANDROID_SERIAL` não alcança o Maestro e `--device` não alcança o `adb`, então com dois ligados os
dois são necessários — e não conferir o aparelho **por serial** é conferir outro aparelho. Desligar
o que não é o da §2.2 continua sendo o caminho mais curto.

No iOS a regra é a mesma e o remédio é mais simples, porque tudo passa por um UDID:

```bash
xcrun simctl list devices booted                  # quem está ligado
maestro --device <UDID> test .maestro             # a flag vem ANTES do `test`
```

**Um simulador ligado por vez.** Dois `maestro` no mesmo simulador se derrubam, e dois simuladores
ligados dividem CPU com o que está sendo medido. Isto não é zelo: dois drivers XCUITest no mesmo
aparelho terminam um ao outro no meio do run, e o sintoma — `Device became unreachable`,
`Connection refused` na porta do driver — não se parece com um conflito.

### 2.2.2 O simulador de referência (iOS)

O irmão da §2.2, e existe pelo mesmo motivo: cada linha é uma **entrada do teste**.

| Fixado | Valor | Como conferir | Por que é fixo |
|---|---|---|---|
| Modelo | iPhone 16 — 393×852 pt | `xcrun simctl list devices` | O análogo do `pixel_6`: telefone de tamanho padrão. Os fluxos rolam para alcançar o que está abaixo da dobra, e a altura decide se o `scrollUntilVisible` acha o campo |
| iOS | 18.5 | idem | Gestos e diálogos de sistema mudam entre versões |
| Idioma e região | `en-US` | `defaults read -g AppleLocale` e `AppleLanguages` | As asserções leem figuras e rótulos renderizados |
| Teclados | só `en_US` + emoji | `defaults read -g AppleKeyboards` | Um teclado pt-BR ativo traz autocorreção em outro idioma sobre o texto digitado — e a tecla de retorno muda de nome |
| Tutorial do teclado | desligado | `defaults read com.apple.keyboard.preferences DidShowContinuousPathIntroduction` | Num simulador novo ele cobre o teclado inteiro no primeiro campo, e nada no fluxo o dispensa |
| Autocorreção e previsão | desligadas | `defaults read com.apple.Preferences KeyboardAutocorrection` | O que o teste digita tem de ser o que o campo recebe |

**O CoreSimulator reinjeta o idioma e os teclados do Mac a cada boot.** Escrever no
`.GlobalPreferences.plist` com o simulador desligado não adianta: depois de ligar, `pt-BR` está de
volta na lista. As três primeiras chaves têm de ser reescritas **com o simulador já ligado**, e a
cada boot. É o oposto do Android, onde nada se conserta depois — e é igualmente fácil de esquecer.

```bash
UDID=$(xcrun simctl create finsight_e2e \
  com.apple.CoreSimulator.SimDeviceType.iPhone-16 \
  com.apple.CoreSimulator.SimRuntime.iOS-18-5)
xcrun simctl boot "$UDID"

# depois de CADA boot — o CoreSimulator repõe o idioma do Mac:
xcrun simctl spawn "$UDID" defaults write -g AppleLanguages -array "en-US"
xcrun simctl spawn "$UDID" defaults write -g AppleLocale -string en_US
xcrun simctl spawn "$UDID" defaults write -g AppleKeyboards -array "en_US@sw=QWERTY;hw=Automatic" "emoji@sw=Emoji"

# uma vez só:
xcrun simctl spawn "$UDID" defaults write com.apple.keyboard.preferences DidShowContinuousPathIntroduction -bool true
xcrun simctl spawn "$UDID" defaults write com.apple.Preferences KeyboardAutocorrection -bool false
xcrun simctl spawn "$UDID" defaults write com.apple.Preferences KeyboardPrediction -bool false
xcrun simctl spawn "$UDID" defaults write com.apple.Preferences KeyboardCapitalization -bool false
```

Confira olhando: abra qualquer campo e veja o teclado. Em inglês ele diz `space` e `next`, e **não
tem a tecla do globo** — o globo só aparece com mais de um teclado instalado, então a sua ausência
é a prova de que a lista foi fixada.

O app é instalado pelo `xcrun simctl install` (o Maestro não instala nada, nas duas plataformas), e
o `.app` sai do build de debug:

```bash
(cd iosApp && xcodebuild -project iosApp.xcodeproj -scheme Finsight -configuration Debug \
   -sdk iphonesimulator -derivedDataPath build/DerivedData \
   -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build)
xcrun simctl install "$UDID" iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/Finsight.app
```

`clearState: true` custa mais aqui do que no Android: o Maestro o implementa **reinstalando o app**
(copiar o bundle, desinstalar, instalar), não como o `pm clear`. É o motivo de o `launch_fresh` do
iOS ser visivelmente mais lento.

### 2.3 Rodar menos que a suíte inteira

O `config.yaml` é do **workspace**, e o workspace é a pasta que o `maestro test` recebe. O que sai
dele é o glob `flows/**`, o `testOutputDir` e a ordem de execução — e tudo isso se perde quando o
caminho aponta para dentro:

| Comando | O que acontece |
|---|---|
| `maestro --device <alvo> test .maestro` | Workspace: os 13 fluxos |
| `maestro --device <alvo> test --include-tags smoke .maestro` | Workspace, filtrado por tag |
| `maestro --device <alvo> test .maestro/flows/budgets/lifecycle.yaml` | Roda o fluxo, sem o `config.yaml` |
| `maestro --device <alvo> test .maestro/flows` | **Não roda nada** e sai com código 0 — só há subpastas, e o glob ficou para trás |

Apontar um `.yaml` direto é o laço de iteração legítimo enquanto se escreve um fluxo, **e o
resultado dele conta**: o que o `config.yaml` deixaria de fora não muda comportamento nenhum de
aparelho (§2.2 — o `disableAnimations` é da cloud). O que o workspace ainda decide é *quais* fluxos
rodam e onde vai o relatório.

**Rodar fluxo a fluxo, em processos separados, é a forma recomendada no iOS.** O driver XCUITest
tem um bug conhecido em modo de pasta: a partir do segundo ou terceiro fluxo a porta do driver para
de responder e a suíte trava ou morre no meio
([#3254](https://github.com/mobile-dev-inc/maestro/issues/3254),
[#3318](https://github.com/mobile-dev-inc/maestro/issues/3318)). Um `maestro` por fluxo dá a cada
um a sua sessão, e a queda de um não leva os outros:

```bash
for f in .maestro/flows/*/*.yaml; do maestro --device "$UDID" test "$f"; done
```

### 2.4 Quando nada roda

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Todo fluxo morre em `UNAVAILABLE: io exception` | Sessão órfã do Maestro Studio ([#3065](https://github.com/mobile-dev-inc/maestro/issues/3065)) | Feche o Studio e `rm ~/.maestro/sessions` |
| "Top-level directories do not contain any Flows", código 0 | O caminho foi `.maestro/flows` | Rode o workspace: `maestro test .maestro` (§2.3) |
| Falhas espalhadas sem padrão | Aparelho fora de uma das linhas da §2.2 | Rode os sete comandos da §2.2; nenhum resultado num aparelho divergente conta |
| Texto some por baixo de uma barra flutuante | AVD com `hw.keyboard = yes` | Desligue o emulador, corrija o `config.ini` (§2.2) e suba de novo; não existe conserto por `adb` com ele ligado |
| Texto digitado some | Campo recebeu digitação antes do foco | Não é ambiente: é o fluxo. Veja §5.2, padrão 4 |
| Tudo vermelho depois de mexer no app | Build velho instalado | `./gradlew :app:android:installDebug`, ou reconstruir e `xcrun simctl install` (§2.2.2) |
| O run não reflete o que você instalou, ou `adb` diz `more than one device` | Dois aparelhos ligados; o run caiu no outro | Fixe o alvo por serial ou UDID (§2.2.1) |
| **iOS:** `Device became unreachable`, `Connection refused` numa porta 6xxxx | O driver XCUITest morreu — dois `maestro` no mesmo simulador, ou o bug de modo-pasta | Um simulador e um `maestro` por vez; rode fluxo a fluxo (§2.3); `rm ~/.maestro/sessions` |
| **iOS:** um toque "COMPLETED" que não faz nada | Toque emitido contra a posição de antes de a tela assentar | É o fluxo: falta `waitForAnimationToEnd` (§5.2, padrão 3). Nada desliga animação em aparelho local |
| **iOS:** teclado em português, ou tutorial cobrindo o teclado | O CoreSimulator repôs o idioma do Mac no boot | Reescreva as chaves da §2.2.2 **com o simulador ligado** |

### 2.5 O que difere entre as plataformas, e onde a diferença mora

Um fluxo é um só para as duas plataformas, e isso não saiu de graça: quatro comandos do Maestro têm
comportamento diferente ou nenhum no iOS. A regra que se seguiu foi **resolver no app, não no
YAML** — um `when: platform:` espalhado pelos fluxos seria uma segunda suíte disfarçada de uma.

| O que o Android faz sozinho | No iOS | Onde ficou resolvido |
|---|---|---|
| `- back` sai da tela | **Não faz nada.** `IOSDriver.backPress()` é vazio, e o swipe de borda não chega ao Compose deste app (testado com três geometrias) | Toda tela renderiza o mesmo `BackButton` (`core/designsystem`), com a tag `top_bar_back`; os fluxos chamam `go_back` |
| `- back` fecha uma folha modal | Não faz nada | `dismiss_modal` arrasta a folha para baixo pela alça — o gesto que a folha já tem |
| `hideKeyboard` guarda o teclado | **Falha o fluxo.** O Maestro dá dois micro-swipes no centro da tela; numa folha rolável isso rola a folha, e o comando reprova | A alça da folha libera o foco (`ModalManager`), com a tag `modal_release_keyboard`; os fluxos chamam `dismiss_keyboard` |
| Toque logo depois de um scroll acerta | **Erra.** A lista segue deslizando e o toque cai no que escorregou para a posição | `waitForAnimationToEnd` entre rolar e tocar (§5.2, padrão 3) |
| `checked: true` lê um switch | Lê `selected`, nunca `checked` | Nenhum fluxo lê o controle: lê a legenda que a linha renderiza (`account_form_yield_state`) |
| Pager vira a página com swipe ancorado no elemento | Volta atrás: meia largura não passa do limiar | `next_account`/`previous_account`, por coordenadas |

Sobra **um** `when: platform:` na suíte inteira, em `ledger/lifecycle`, e ele está lá porque a
diferença é real e não tem conserto no app: a tela de Transações é uma *aba*, sem barra superior e
sem botão de voltar. Chegar nela tocando uma figura do dashboard a empilha com um filtro; o Android
desempilha com o gesto de sistema — que é a única saída lá, e o único lugar onde esta suíte ainda
cobre esse gesto —, e o iOS volta pela aba de onde veio, que é o que uma pessoa faria.

O `appId` é o mesmo nos dois (`com.neoutils.finsight`), então nenhum fluxo precisa de variável para
saber quem lançar. Isso é sorte estrutural, e vale mantê-la: mudar o bundle id de um dos dois faria
cada fluxo precisar de `--env`.

## 3. Mapa da suíte

```
.maestro/
├── config.yaml            # o workspace: quais fluxos rodam, onde vai o relatório
├── flows/                 # tudo aqui roda como teste, uma pasta por área
└── subflows/              # peças reutilizáveis; só rodam quando um fluxo as chama
```

`subflows/` fica fora do glob `flows/**` de propósito — é isso que impede um bloco compartilhado de
ser executado como teste próprio. Hoje são treze, em três famílias:

- **Estado e arranjo:** `launch_fresh` (estado inicial), `open_section` (chegar a uma seção pela
  grade de ações rápidas), `record_transaction`, `record_categorized_expense` e `record_card_expense`
  (lançar), `create_account`, `create_credit_card` e `create_category`.
- **Gestos que as duas plataformas fazem diferente:** `go_back` (sair de uma tela pelo botão da
  barra superior), `dismiss_modal` (fechar uma folha arrastando-a pela alça) e `dismiss_keyboard`
  (guardar o teclado pela mesma alça). Cada um existe porque o comando "óbvio" do Maestro —
  `- back`, `hideKeyboard` — é de Android e não faz nada, ou faz outra coisa, no iOS (§2.5).
- **Geometria que precisa ser dita uma vez:** `next_account` e `previous_account`, que viram a
  página do pager de contas por coordenadas.

Um subflow carrega o **arranjo**, nunca a afirmação: quem chama é que diz o que o estado criado
deve mostrar. É por isso que `create_credit_card` não assere o limite disponível do cartão que
acabou de criar — dois fluxos criam cartões com limites diferentes, e a afirmação é de cada um.

Quase toda área tem um único `lifecycle.yaml`, e isso é deliberado: `launch_fresh` limpa o banco,
então uma história partida em duas gastaria a primeira metade recriando o que a segunda precisa.

| Fluxo | A afirmação pela qual ele existe |
|---|---|
| `smoke/launch` | o app sobe e publica seu chrome |
| `dashboard/initial_state` | o que o dia zero mostra e — tanto quanto — o que ele retém |
| `dashboard/customization` | o dashboard é do usuário: reordenado, esvaziado, repovoado, um componente por vez e configurado — e continua assim depois que o processo morre |
| `ledger/lifecycle` | duas escritas de naturezas diferentes, somadas e lidas de volta; corrigir uma reescreve as duas pernas, não acrescenta uma terceira; e a figura do dashboard leva à lista já cortada por ela |
| `report/lifecycle` | o relatório *escopa*: a mesma escrita lida por contas diferentes, e por perspectivas diferentes, diz coisas diferentes |
| `accounts/lifecycle` | uma transferência move dinheiro sem criar nenhum; uma conta zerada pode ser arquivada e reencontrada |
| `creditcards/lifecycle` | um cartão cria dívida, não gasto de caixa, até a fatura ser fechada e paga; e um cartão com movimento se aposenta arquivando, não apagando, e só quando não deve nada |
| `installments/lifecycle` | uma parcela devida por fatura, a compra inteira comprometida contra o limite |
| `recurring/lifecycle` | um recorrente não é dinheiro até ser confirmado, e pular liquida um ciclo, não a ordem |
| `currency/lifecycle` | duas moedas no mesmo bolso: o segundo campo nasce da discordância entre dois seletores, e a mesma escrita é exata na conta e aproximada no total — sob duas moedas base |
| `budgets/lifecycle` | uma despesa categorizada chega ao orçamento que a vigia, e passado o limite a leitura muda |
| `categories/lifecycle` | sem movimento a categoria se apaga, com movimento se arquiva; arquivada sai dos seletores e continua no gasto do mês, e volta inteira |
| `support/lifecycle` | uma folha, uma lista e um chat entregam a mesma conversa uns aos outros, e a resposta continua lá ao reabrir |

**Identificadores.** `snake_case`, descrevendo o elemento e não sua posição: `add_transaction_save`,
`bottom_navigation_bar`. Itens de navegação derivam o seu da rota — `NavDestination.name` transforma
`DashboardRoute` em `dashboard`, dando `nav_item_dashboard` — então a tag não pode se descolar do
destino que nomeia.

Um id é um `Modifier.testTag` do Compose, e ele só chega ao Maestro porque a raiz de composição
publica as tags na árvore de acessibilidade, via `Modifier.exposeTestTags()` (`core/designsystem` —
`ui/util/ExposeTestTags`). **Uma raiz precisa aderir explicitamente, e uma folha modal, um diálogo ou
um popup são raízes próprias.** Hoje aderem: o `Surface` do `App`, o `ModalBottomSheet`, o painel de
detalhe e dois `DropdownMenu`. Uma janela nova precisa da sua própria chamada, ou suas tags serão
invisíveis sem nenhum erro que explique o porquê.

## 4. Quando um teste fica vermelho

Perguntas em ordem fixa, da mais barata para a mais cara:

1. **O aparelho bate com a §2.2?** Os comandos de lá — os sete do Android, os da §2.2.2 no iOS —
   com a saída colada na resposta. Perfil divergente é resultado inválido, não pista, e "acho que
   bate" não responde a pergunta.
2. **Falha na outra plataforma também?** É a pergunta mais barata que existe agora, e ela separa
   duas coisas que se parecem: um vermelho nos **dois** aparelhos não é do driver nem do sistema —
   é do app ou do fluxo. Foi assim que se descobriu que o salto de 45 dias do `creditcards` era um
   bug de data e não uma diferença de iOS: ele falhava igual no Android, no dia 10 de um mês cujo
   cartão fecha no dia 10.
3. **O fluxo falha sozinho?** `maestro --device <alvo> test .maestro/flows/<área>/lifecycle.yaml`
   responde em dois minutos. Falha sozinho e passa na suíte (ou o contrário) é dependência de
   ordem — que nesta suíte não deveria existir, porque todo fluxo começa de `launch_fresh`.
4. **É determinístico?** Rode duas vezes. Intermitente é quase sempre sincronização (§5.2, padrão 3),
   e no iOS quase sempre um toque emitido contra uma lista que ainda desliza: o comando "sucede" e
   o alvo não recebe nada.
5. **O passo que falhou é o assunto do fluxo ou um passo de preparo?** Falha no preparo raramente é
   bug do app; é a UI que mudou de forma debaixo do fluxo.
6. **Reproduz noutra máquina?** Se só na sua, volte à pergunta 1 — a diferença está no aparelho.

**"Elemento não encontrado" quase nunca significa que o elemento não existe.** Significa: fora da
viewport; ainda não composto (o dashboard é uma `LazyColumn` — o que não foi rolado **não existe** na
árvore); atrás do teclado; ou numa raiz de composição que não publicou suas tags (§3).

Onde olhar: o log de passos impresso pelo run, e a pasta que ele imprime no fim
(`~/.maestro/tests/<timestamp>/`) com captura e hierarquia no momento da falha.

**Fluxo vermelho não vira comentado nem ignorado.** Ou conserta, ou reverte a mudança que o
quebrou — a suíte só protege enquanto for verde por inteiro.

## 5. Escrever um fluxo novo

Decidir se cabe (§5.1) → escrever seguindo os padrões (§5.2) → revisar contra os antipadrões (§5.3)
e o checklist (§5.4).

Todo fluxo começa de um estado conhecido:

```yaml
appId: com.neoutils.finsight
name: smoke_launch
tags:
  - smoke
---
- runFlow: ../../subflows/launch_fresh.yaml
```

Uma instalação nova **não é um app vazio**: a conta padrão (*Carteira* / *Wallet*) é semeada e o
dashboard já carrega seu layout padrão completo (`GetDashboardPreferencesUseCase`). Também **não é um
app mobiliado**: a conta é semeada, **as categorias não** — o `CreateDefaultCategoriesUseCase` roda a
partir da oferta da tela de categorias, nunca no boot. Um fluxo que precise de categoria cria uma.

Daí em diante todo trecho tem a mesma forma — esperar, agir, asserir a figura no nó que a renderiza:

```yaml
- runFlow:                                  # o meio, reutilizado
    file: ../../subflows/record_transaction.yaml
    env:
      TYPE: income
      TITLE: ${INCOME_TITLE}
      AMOUNT: "50000"                       # dígitos: o campo põe o separador sozinho
- scrollUntilVisible:                       # o que não foi rolado não está composto
    element:
      id: "dashboard_total_balance_amount"
    direction: UP
    timeout: 25000
- assertVisible:
    id: "dashboard_total_balance_amount"    # o nó que renderiza a figura...
    text: "[$]500[.,]00"                    # ...e a figura, tolerante a símbolo e separador
```

**Leia `flows/ledger/lifecycle.yaml` antes de escrever o seu.** É o mais curto dos
fluxos de jornada e o que melhor mostra a forma inteira: uma história só, figuras que não
compartilham dígitos, e cada asserção comentada com a claim que ela sustenta.

### 5.1 Cabe em E2E?

> **Cabe se, e somente se, a pergunta for "as peças reais, montadas, entregam este resultado?" — e
> não puder ser respondida com o sistema desmontado.**

Três perguntas resolvem qualquer caso fora da tabela:

1. **Desmonta?** Se uma camada só responde, o teste é dessa camada.
2. **É combinatória?** De N variações da mesma regra, no máximo **uma** é E2E.
3. **Depende do aparelho real?** Relógio, teclado, gesto de voltar, ciclo de vida do processo — aí é
   E2E mesmo que pareça pequeno, porque nenhuma outra camada alcança.

**Na dúvida, desça uma camada.** Um teste bom na camada de baixo custa uma fração e falha com mais
precisão.

| Cabe em E2E | Não cabe — cabe em |
|---|---|
| Lançar receita e despesa e o saldo na tela ser a soma delas | O cálculo do saldo a partir dos lançamentos — **unitário** do repositório de lançamentos |
| Transferir entre contas e o patrimônio total não se mover | A regra de que transferência é neutra — **unitário** do caso de uso |
| Gastar no cartão e o caixa não se mover, mas a fatura sim | Como um lançamento vira lançamentos contábeis — **unitário** do escritor do razão |
| Uma conta zerada poder ser arquivada, e uma com saldo não | A mensagem de erro de cada motivo de recusa — **unitário** do caso de uso |
| O rótulo virar "Excluir" ou "Arquivar" conforme o histórico | A matriz completa de estados que produz cada rótulo — **unitário** do mapeador |
| Passado o limite, o orçamento trocar "Restante" por "Excedido em" | O cálculo do percentual e do restante — **unitário** do progresso de orçamento |
| Confirmar um recorrente depois de virar o mês e a ocorrência cair no mês certo | A idempotência de confirmar duas vezes — **unitário** do caso de uso |
| Uma compra em 3x aparecer em três faturas | A divisão do valor e o arredondamento — **unitário** |
| O dashboard esconder a linha de contas enquanto só há uma | Quais componentes o layout padrão declara — **unitário** das preferências |
| Fechar uma fatura e a seguinte abrir com o limite de volta | O cálculo do limite disponível — **unitário** |
| Um valor gravado continuar lá depois de reiniciar o app | Uma consulta devolver as linhas certas — **integração** do DAO |
| Migração de esquema não quebrar as telas | A migração em si, versão a versão — **integração** do banco |
| Um formulário válido concluir a jornada | Cada mensagem de campo inválido — **unitário** da validação |
| A aparência de um estado vazio | — **screenshot test**, não esta suíte |

### 5.2 Padrões

1. **Alcance elementos por `id`, nunca pelo texto da interface.** Rótulo é copy: muda numa revisão
   de UX e quebra um teste que não tinha nada com o assunto. Vale também contra o *outro* nó que
   renderiza a mesma palavra: a lista de recentes e a linha de recorrentes pendentes dividem o
   mesmo título, e qual dos dois um `tapOn: <texto>` encontra não é o mesmo nas duas plataformas —
   por isso os dois se alcançam por `id` **mais** texto.
1b. **Quando o centro do nó não é o alvo, diga onde tocar — no fluxo, nunca na tela.** O Maestro
   toca no centro geométrico do elemento, e um campo com conteúdo à direita (o contador de parcelas
   é o caso do app) publica **um nó só**, cujo centro cai no conteúdo e não na área de digitação.
   `point` ao lado de um seletor é relativo aos bounds *daquele elemento*, e resolve isso sem que a
   tela mude nada:

   ```yaml
   - tapOn:
       id: "add_installment_amount"
       point: "20%, 50%"      # um quinto da largura do campo, não o meio
   ```

   O Android atravessa esse toque por acaso — o botão sob o centro está desabilitado e não o
   consome; o iOS não atravessa. Um alvo que só funciona por acaso numa plataforma é o fluxo que
   está errado, não a interface: **não redesenhe uma tela para acomodar o ponto de toque padrão de
   uma ferramenta.**
2. **Asserte a figura renderizada, não a existência do elemento.** `assertVisible: id=balance` passa
   com o saldo errado. `457.10` no nó que o renderiza prova que duas escritas foram persistidas,
   somadas e lidas de volta — a única coisa que o E2E prova melhor que qualquer camada. Prefira o
   número sem o símbolo (`457.10`), para sobreviver a uma troca de símbolo mas não de valor.
3. **Sincronize por condição, nunca por tempo — e conte o assentar como uma condição.**
   `extendedWaitUntil` depois de qualquer ação que anime ou carregue; nunca uma espera fixa. E
   `waitForAnimationToEnd` entre **rolar e tocar**: o scroll para, a lista não, e o toque é emitido
   contra a posição que o scroll reportou. No iOS isso não é raro, é a regra — reproduzido e
   isolado: sem essa linha o `open_section` toca e a seção não abre; com ela, abre sempre. Nada
   desliga animação num aparelho local (§2.2).
4. **Releia cada campo depois de digitar.** Uma tecla perdida falha ali, e não três telas adiante,
   como um botão que não envia. E guarde o teclado entre campos, com `dismiss_keyboard` — nunca com
   `hideKeyboard`, que é de Android e reprova o fluxo no iOS (§2.5).
5. **Escreva o valor esperado literal; nunca o calcule no fluxo.** Um valor calculado reimplementa a
   regra do app, e quando os dois erram juntos o teste fica verde para sempre.
6. **Escolha figuras que não compartilhem dígitos.** `500.00` e `42.90` deixando `457.10`: nenhuma
   asserção pode passar lendo a figura do vizinho.
7. **Asserte o que o app oferece, nos dois estados.** O que a UI *oferece* é tanto uma regra quanto o
   que ela calcula, e um fluxo que já passa pelos dois lados ganha a asserção quase de graça.
8. **Uma jornada, um assunto.** Se o nome precisa de dois "e", são dois fluxos — dentro do limite do
   que `launch_fresh` permite (§3).
9. **Use o relógio móvel para alcançar o que a data esconde.** `clockOffsetDays` e
   `clockOffsetMonths` (só em debug, e somáveis) são o que permite fechar uma fatura ou virar o mês.
   Toda tela lê o `Clock` injetado; qualquer código que leia `Clock.System` direto discorda do resto
   do app no instante em que esse argumento é passado — isso é bug, e se conserta, não se contorna.
   O mesmo argumento serve às duas plataformas: o Maestro o entrega como extra de intent no Android
   e como argumento de processo (`-clockOffsetDays 45`, o domínio de argumentos do `NSUserDefaults`)
   no iOS, e cada app o lê à sua maneira antes de entregá-lo ao mesmo `applyTimeTravel` de
   `:app:debug`.

   Para virar o mês, use `clockOffsetMonths`, não trinta e um dias. `+31` a partir do dia 30 de um
   mês de 31 dias cai **dois** meses adiante, e o fluxo fica vermelho num punhado de dias por ano
   por um motivo que nenhuma mensagem de falha nomeia — o que o §6 chama de bug, não de azar.
10. **Reuse o meio, mantenha inline o assunto.** Subflow para preparo; asserção compartilhada
    esconde o que o fluxo afirma e torna a falha ilegível.
11. **Marque só o que um fluxo precisa tocar.** Uma tag sem fluxo por trás é peso morto que ainda
    assim tem de ser mantido correto.
12. **Comente a claim, não o comando.** `tapOn: save` não precisa de comentário; *por que aquele
    número prova alguma coisa* precisa.

### 5.3 Antipadrões

| Antipadrão | Como você percebe que caiu nele | Em vez disso |
|---|---|---|
| **Espera mágica** | O fluxo passa numa máquina e falha noutra; alguém aumenta o sleep; a suíte engorda e segue instável | `extendedWaitUntil` na condição |
| **Seletor por copy** | Um PR que só reescreve textos deixa dez fluxos vermelhos | `id` para alcançar; texto só para asserir |
| **Asserção tautológica** | O fluxo nunca falhou na vida — e afirma "a tela existe" | Quebre a funcionalidade de propósito uma vez; se não fica vermelho, a asserção é enfeite |
| **Ausência sem premissa** | `assertNotVisible` passa porque o componente está fora da viewport, não porque sumiu | Assere a ausência com um componente *posterior* visível, provando que a região foi composta |
| **Ausência por texto solto** | `assertNotVisible: "E2E Salary"` passa (ou falha) por causa de outro lugar da tela que renderiza o mesmo nome | Ausência por `id` — e com a figura, quando vários nós compartilham o id |
| **Oráculo espelhado** | A regra muda, a implementação erra, o fluxo continua verde porque errou igual | Valor literal, conferido por gente |
| **A grande turnê** | Um fluxo longo que encadeia assuntos sem relação; quando falha no passo 90, ninguém sabe se o app quebrou ou se a navegação mudou | Uma jornada por fluxo — o teste é o assunto, não a contagem |
| **Retry analgésico** | A suíte "passa" em três vezes o tempo, e ninguém abre as tentativas descartadas | Instabilidade é bug — do app ou do fluxo |
| **Cemitério de ignorados** | N fluxos comentados com "flaky, ver depois"; a suíte é verde e não protege nada | Consertar ou apagar |
| **Espelhar a pirâmide** | Um fluxo para cada regra de validação; a suíte passa de meia hora e ninguém lê o relatório | Combinatória desce de camada (§5.1) |
| **Ramificação defensiva** | `if` no fluxo ("se aparecer o modal, feche"); ele passa em cenários que ninguém projetou | Estado inicial determinístico |
| **Screenshot como asserção** | Toda troca de tema gera dezenas de diffs e o time aprova baselines em massa | Captura é artefato de diagnóstico; comparação visual é outra suíte |
| **Redesenhar a tela para o teste passar** | Uma proposta de mudar layout, mover um controle ou trocar um componente aparece numa investigação de fluxo vermelho | A suíte serve o app, não o contrário. Um toque que erra o alvo se conserta com `point` (§5.2, 1b); um estado que o driver lê diferente se assere por outro nó (`account_form_yield_state`). Só se muda a tela quando a mudança se sustenta **sozinha**, sem o teste como argumento |

**Sobre "a grande turnê", que já foi contada em passos.** Três fluxos passam de 120 e os três são
uma história só — a decisão está tomada e não é para ser reaberta a cada leitura:

- **`recurring/lifecycle`** (170) — a claim final é *confirmar depois do salto arquiva a ocorrência
  no mês do relógio do app*, e ela depende de tudo que veio antes: dois recorrentes, um confirmado
  por valor corrigido, um pulado, um arquivado e reativado. Partir ao meio obrigaria a segunda
  metade a refazer esse arranjo inteiro.
- **`creditcards/lifecycle`** (179) — o ciclo de vida da fatura é indivisível pelo mesmo motivo:
  fechar exige ter gasto, pagar exige ter fechado, e a fatura seguinte só existe porque a anterior
  fechou. E o cartão só se aposenta por arquivamento porque teve movimento, e só é aceito porque não
  deve mais nada — as duas coisas só valem para um cartão que esta história inteira produziu.
- **`accounts/lifecycle`** (126) — arquivar **exige** saldo zero, que exige o ajuste, que exige a
  transferência. O cabeçalho do arquivo diz isso.

O que o antipadrão condena é encadear assuntos sem relação, e o sintoma é o nome precisar de dois
"e" (§5.2, padrão 8). Um fluxo longo cujo último passo depende do primeiro não é uma turnê; é uma
história que custa o que custa.

### 5.4 Antes de abrir o PR

- [ ] O fluxo passa sozinho **e** dentro da suíte inteira.
- [ ] Passou duas vezes seguidas.
- [ ] Toda figura assertada está no nó que a renderiza (`id` + `text` no mesmo seletor).
- [ ] Nenhuma figura do fluxo compartilha dígitos com outra.
- [ ] Toda `assertNotVisible` tem premissa de que a região foi composta.
- [ ] Nenhuma tag nova ficou sem fluxo por trás.
- [ ] O cabeçalho do arquivo diz, em duas linhas, qual é a claim do fluxo.

## 6. Saúde da suíte

**Orçamento: ~28 minutos e 13 fluxos** (medido de ponta a ponta: 25m16 e 28m10). Ao estourar,
corta-se ou funde-se — o teto não sobe por reflexo. Cada fluxo novo compete com os existentes pelo
tempo de quem roda a suíte; ao propor um, diga **qual sai ou por que o teto muda**.

**Instabilidade é bug.** Um fluxo que fica vermelho sem mudança de código entra em investigação no
mesmo dia. Não existe fluxo em quarentena permanente nesta pasta, e a ausência disso é o que a
mantém confiável.

**A exceção, nomeada e medida: `dashboard/customization` no iOS.** O arrasto de reordenação — o
único gesto do app sem botão por trás, e o único ponto da suíte que depende de arrastar — é instável
lá, e só lá. O Maestro sintetiza o arrasto como um fluxo de eventos de toque, e a rolagem automática
da lista responde a esse fluxo de outro jeito no iOS: com `duration: 2500` o item viaja quatro
posições, com `900` ele fica aquém, e com os `1500` que estão no fluxo ele acerta na maioria das
vezes e não em todas (medido: 3 verdes em 5 execuções). No Android o mesmo fluxo é verde.
Isso é **limite de ferramenta**, não bug de tela — e a diferença importa, porque a resposta a um
limite de ferramenta nunca é mexer na tela. Enquanto durar, o fluxo conta como verde pelo Android e
o seu vermelho no iOS não autoriza nenhuma mudança de app.

**O precedente.** O teto subiu quatro vezes, e cada uma fica registrada porque é ela que autoriza a
próxima recusa. Nenhuma tirou um fluxo em troca; todas entraram pelo mesmo argumento — cobrir uma
travessia que nenhuma camada abaixo alcança:

| Teto | O que entrou | Custo | O que comprou, e só ele compra |
|---|---|---|---|
| ~16 → ~20 | edição de transação, e `report/lifecycle` | — | O único comando corretivo do app sem travessia — o botão sequer tinha `testTag`. E recolher as pernas de relatório espalhadas por três histórias: cinco gerações viraram três, e a que sobrou em `creditcards/lifecycle` soma uma fatura liquidada e uma aberta, que nenhuma outra consegue |
| ~20 → ~21 | `support/lifecycle` | 1m06–1m12 | A última feature sem travessia nenhuma, intestável até o build de debug responder suporte da memória (`InMemorySupportRepository`) no lugar do Firestore, que exigiria rede, credenciais e projeto de verdade |
| ~21 → ~23 | `dashboard/customization` | 1m42 | O único gesto do app sem botão por trás: arrastar. Reordenar é arrastar, e pôr ou tirar **um** componente também — os comandos em massa movem os onze ou nenhum. Nenhuma camada monta o editor e a tela que lê o resultado |
| ~25 → ~28 | `currency/lifecycle` | 2m53 | O **segundo campo de valor**, único controle do app que nasce da discordância entre dois seletores: o arquivo o pré-preenche e digitar por cima retira a oferta. Ele existia no código sem fluxo nenhum — `transfer_destination_amount` era peso morto. E a **fronteira da consolidação**, que exige a tela de contas e o dashboard montados ao mesmo tempo sob **duas** moedas base: a mesma escrita exata de um lado e aproximada do outro. Nenhuma camada abaixo monta duas telas, e é a fronteira que a feature inteira existe para não quebrar |
| ~23 → ~25 | `categories/lifecycle` | 2m19–2m22 | O ciclo de aposentadoria da dimensão, que `budgets/lifecycle` só atravessa de raspão: o comando trocar de *Delete* para *Archive* porque **outra feature** escreveu no razão, e os dois leitores de `isArchived` discordando na direção certa — o seletor de transação deixa de oferecê-la no instante em que o dashboard continua somando o que ela gastou |

Duas recusas vêm no mesmo pacote, e valem como precedente igual. A aritmética do arrasto **não** é
E2E: `DashboardEditLayout.move` foi extraída do ViewModel e tem matriz unitária própria
(`DashboardEditLayoutTest`, 12 casos) — o E2E prova uma vez que o gesto chega nela, e as cinco
ramificações custam milissegundos onde estão. E a oferta "Use default" das categorias ficou de
fora: quatorze categorias nomeadas por recurso seriam quatorze seletores de que o fluxo não é dono.

Os dois fluxos `smoke` somam 15 segundos, o que mantém barata a opção de automatizá-los um dia; a
decisão está em aberto, e o que ela custa não é o minuto de execução — é montar, em CI, o aparelho
da §2.2. A tag `smoke` não foi realocada quando `accounts/default_account` saiu: levá-la para uma
história de dois minutos mataria exatamente a possibilidade que ela existe para manter aberta.

---

**Referência da ferramenta:** [docs.maestro.dev](https://docs.maestro.dev). Este documento não a
repete — descreve o que é nosso.
