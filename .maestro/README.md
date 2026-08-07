# Testes E2E (Maestro)

```bash
./gradlew :app:android:installDebug              # instala o APK de debug (o de release não serve)
maestro test .maestro                            # roda tudo (~25 min)
maestro test .maestro/flows/budgets/lifecycle.yaml   # um fluxo só (§2.3)
```

A suíte roda **à mão**: não há script que a prepare nem CI que a rode, e a ausência é deliberada
(§2.1). Ela exige um emulador **API 36, perfil `pixel_6`, em inglês, com teclado de tela e sem
teclado físico** — pré-condição de quem executa, que nada verifica nem conserta, e que na maior
parte não tem conserto depois do boot da AVD. Confira as sete linhas da **§2.2** antes de rodar: num
aparelho divergente, vermelho ou verde não conta.

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

**O custo, com números.** A suíte inteira leva **~25 minutos** para 12 fluxos. Os dois de fumaça somam
menos de 20 segundos; os de jornada custam de 1m a 4m30 **cada um**. Isso é o orçamento (§6), e é
o que torna "adicionar um fluxo" uma decisão, não uma adição livre. Um fluxo que duplica o que um
teste de ViewModel já prova custa dois minutos de emulador para não contar nada de novo.

Meça com a máquina ociosa. Um build Gradle rodando ao lado disputa CPU com o emulador e a mesma
suíte sobe para mais de 30 minutos — um número que não descreve a suíte e não deve entrar aqui.

## 2. Rodar a suíte

```bash
./gradlew :app:android:installDebug        # instala o APK de debug; refaça sempre que mexer no app
maestro test .maestro                      # roda tudo
maestro test --include-tags smoke .maestro # só os fluxos com a tag `smoke`
```

O caminho é `.maestro`, o **workspace**. Apontar para dentro dele muda o que roda e o que vale — e
o modo mais barato de perder um run é apontar para `.maestro/flows`, que não acha fluxo nenhum e
sai com código 0. A tabela da §2.3 diz o que cada caminho faz.

Precisa da CLI do Maestro (`curl -Ls https://get.maestro.mobile.dev | bash`) e do emulador da §2.2
ligado. Rode com o APK de debug — o de release não serve, e por dois motivos:
o relógio móvel de que dois fluxos dependem (§5.2, padrão 9) só existe em debug, e o suporte só é
testável porque o build de debug o responde da memória em vez do Firestore
(`InMemorySupportRepository`, no source set de debug do app). Esse armazenamento morre com o
processo, então `support/lifecycle` é o único fluxo que **não pode** relançar o app.

Duas ferramentas de inspeção: `maestro studio` abre um inspetor sobre o app em execução, e
`maestro hierarchy` despeja a árvore de acessibilidade — o jeito mais rápido de descobrir o que uma
tela de fato expõe.

### 2.1 Quem roda é quem responde

Não existe script de preparo, alvo Gradle, `make e2e` nem job de CI por trás desta pasta, e a
ausência é deliberada: um script que monta a AVD daria a impressão de garantir o aparelho, e ele
**não pode** — metade das linhas da §2.2 é lida no boot da AVD, a outra metade exige que ela tenha
sido criada em inglês, e nenhuma das duas se conserta com o emulador ligado. Automação que não
garante o aparelho só produziria vermelhos e verdes que não contam. Quem muda uma tela é quem
responde se a travessia continua de pé.

Na prática, para quem for rodar — pessoa ou agente de IA, sem distinção:

1. **Montar o aparelho é parte da tarefa.** Se não há AVD que sirva, crie-a (§2.2). Não rode "no que
   estiver conectado" — e com mais de um ligado, fixe o alvo (§2.2.1).
2. **Conferir as sete linhas da §2.2 à mão, antes do run**, e **reinstalar o APK de debug**. Menos
   de um minuto, contra 25 de resultado que não vale nada.
3. **Reportar em que aparelho rodou.** Um "12/12 verde" sem o aparelho ao lado não é um resultado,
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

Falta uma oitava entrada, e ela **não é do aparelho**: as animações, que o `config.yaml` desliga em
tempo de execução (§2.3) porque transição em curso é a causa nº 1 de toque perdido. Não a confira
por `adb` — num aparelho correto `settings get global window_animation_scale` responde `1.0`, e
isso não é reprovação. O que garante essa entrada é rodar pelo workspace.

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

### 2.3 Rodar menos que a suíte inteira

O `config.yaml` é do **workspace**, e o workspace é a pasta que o `maestro test` recebe. Duas coisas
saem dele — o glob `flows/**` e `disableAnimations` —, e as duas se perdem juntas quando o caminho
aponta para dentro:

| Comando | O que acontece |
|---|---|
| `maestro test .maestro` | Workspace: os 12 fluxos, com as animações desligadas pelo `config.yaml` |
| `maestro test --include-tags smoke .maestro` | Workspace, filtrado por tag — a forma certa de rodar um subconjunto |
| `maestro test .maestro/flows/budgets/lifecycle.yaml` | Roda o fluxo, **sem** o `config.yaml`: as animações ficam como o aparelho as tiver |
| `maestro test .maestro/flows` | **Não roda nada** e sai com código 0 — só há subpastas, e o glob ficou para trás |

Apontar um `.yaml` direto é o laço de iteração legítimo enquanto se escreve um fluxo. Só não é o
run que conta: com animação ligada, um toque perdido vira um vermelho que não é do app. Antes do
veredito, rode pelo workspace.

### 2.4 Quando nada roda

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Todo fluxo morre em `UNAVAILABLE: io exception` | Sessão órfã do Maestro Studio ([#3065](https://github.com/mobile-dev-inc/maestro/issues/3065)) | Feche o Studio e `rm ~/.maestro/sessions` |
| "Top-level directories do not contain any Flows", código 0 | O caminho foi `.maestro/flows` | Rode o workspace: `maestro test .maestro` (§2.3) |
| Falhas espalhadas sem padrão | Aparelho fora de uma das linhas da §2.2 | Rode os sete comandos da §2.2; nenhum resultado num aparelho divergente conta |
| Texto some por baixo de uma barra flutuante | AVD com `hw.keyboard = yes` | Desligue o emulador, corrija o `config.ini` (§2.2) e suba de novo; não existe conserto por `adb` com ele ligado |
| Texto digitado some | Campo recebeu digitação antes do foco | Não é ambiente: é o fluxo. Veja §5.2, padrão 4 |
| Tudo vermelho depois de mexer no app | APK velho instalado | `./gradlew :app:android:installDebug` antes de rodar |
| O run não reflete o que você instalou, ou `adb` diz `more than one device` | Dois aparelhos ligados; o run caiu no outro | Fixe o alvo por serial (§2.2.1) |
| Toques perdidos só quando se roda um fluxo isolado | Animações ligadas: o `config.yaml` ficou de fora | Confirme pelo workspace antes de culpar o app (§2.3) |

## 3. Mapa da suíte

```
.maestro/
├── config.yaml            # o workspace: quais fluxos rodam, onde vai o relatório
├── flows/                 # tudo aqui roda como teste, uma pasta por área
└── subflows/              # peças reutilizáveis; só rodam quando um fluxo as chama
```

`subflows/` fica fora do glob `flows/**` de propósito — é isso que impede um bloco compartilhado de
ser executado como teste próprio. Hoje são oito: `launch_fresh` (estado inicial), `open_section`
(chegar a uma seção pela grade de ações rápidas), `record_transaction`, `record_categorized_expense`
e `record_card_expense` (lançar), `create_account`, `create_credit_card` e `create_category` (abrir
uma conta, abrir um cartão, criar uma categoria).

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

1. **O aparelho bate com a §2.2?** Os sete comandos de lá, com a saída colada na resposta. Perfil
   divergente é resultado inválido, não pista — e "acho que bate" não responde a pergunta.
2. **O fluxo falha sozinho?** `maestro test .maestro/flows/<área>/lifecycle.yaml` responde em dois
   minutos. Falha sozinho e passa na suíte (ou o contrário) é dependência de ordem — que nesta suíte
   não deveria existir, porque todo fluxo começa de `launch_fresh`. Lembre que aí o `config.yaml`
   fica de fora (§2.3): confirme o vermelho pelo workspace antes de abrir bug.
3. **É determinístico?** Rode duas vezes. Intermitente é quase sempre sincronização (§5.2, padrão 3).
4. **O passo que falhou é o assunto do fluxo ou um passo de preparo?** Falha no preparo raramente é
   bug do app; é a UI que mudou de forma debaixo do fluxo.
5. **Reproduz noutra máquina?** Se só na sua, volte à pergunta 1 — a diferença está no aparelho.

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
   de UX e quebra um teste que não tinha nada com o assunto.
2. **Asserte a figura renderizada, não a existência do elemento.** `assertVisible: id=balance` passa
   com o saldo errado. `457.10` no nó que o renderiza prova que duas escritas foram persistidas,
   somadas e lidas de volta — a única coisa que o E2E prova melhor que qualquer camada. Prefira o
   número sem o símbolo (`457.10`), para sobreviver a uma troca de símbolo mas não de valor.
3. **Sincronize por condição, nunca por tempo.** `extendedWaitUntil` depois de qualquer ação que
   anime ou carregue; nunca uma espera fixa.
4. **Releia cada campo depois de digitar.** Uma tecla perdida falha ali, e não três telas adiante,
   como um botão que não envia. E `hideKeyboard` entre campos: o que o aparelho põe na tela se
   sobrepõe à folha, e o próximo campo pode ficar por baixo.
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

**Orçamento: ~25 minutos e 12 fluxos** (medido de ponta a ponta: 24m36 e 25m47). Ao estourar,
corta-se ou funde-se — o teto não sobe por reflexo. Cada fluxo novo compete com os existentes pelo
tempo de quem roda a suíte; ao propor um, diga **qual sai ou por que o teto muda**.

**Instabilidade é bug.** Um fluxo que fica vermelho sem mudança de código entra em investigação no
mesmo dia. Não existe fluxo em quarentena permanente nesta pasta, e a ausência disso é o que a
mantém confiável.

**O precedente.** O teto subiu quatro vezes, e cada uma fica registrada porque é ela que autoriza a
próxima recusa. Nenhuma tirou um fluxo em troca; todas entraram pelo mesmo argumento — cobrir uma
travessia que nenhuma camada abaixo alcança:

| Teto | O que entrou | Custo | O que comprou, e só ele compra |
|---|---|---|---|
| ~16 → ~20 | edição de transação, e `report/lifecycle` | — | O único comando corretivo do app sem travessia — o botão sequer tinha `testTag`. E recolher as pernas de relatório espalhadas por três histórias: cinco gerações viraram três, e a que sobrou em `creditcards/lifecycle` soma uma fatura liquidada e uma aberta, que nenhuma outra consegue |
| ~20 → ~21 | `support/lifecycle` | 1m06–1m12 | A última feature sem travessia nenhuma, intestável até o build de debug responder suporte da memória (`InMemorySupportRepository`) no lugar do Firestore, que exigiria rede, credenciais e projeto de verdade |
| ~21 → ~23 | `dashboard/customization` | 1m42 | O único gesto do app sem botão por trás: arrastar. Reordenar é arrastar, e pôr ou tirar **um** componente também — os comandos em massa movem os onze ou nenhum. Nenhuma camada monta o editor e a tela que lê o resultado |
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
