## Context

Hoje o botão de ação existe em dois lugares que não se conhecem. A casca desenha o seu no
`Scaffold` de `ChromeHost.kt` — `FabPosition.Center`, `offset(y = 40.dp)`, ancorado à bottom bar —
e obtém o modal por `TransactionsEntry.addTransactionModal()`. As nove telas listadas no proposal
desenham o seu no próprio `Scaffold`, na posição `End` que é o padrão do componente.

O canal para inverter isso já existe e é usado por uma tela só: `ChromeEffect(config)` publica um
`ChromeConfig` no `ChromeStateHolder`, e `DashboardScreen.kt:38` é o único chamador.

Sete restrições valem para tudo o que segue, todas verificadas na fonte:

1. **Não há componente pronto.** `org.jetbrains.compose.material3:material3:1.9.0` — o que o
   Compose Multiplatform 1.10.1 resolve — exporta `FloatingActionButton` e
   `ExtendedFloatingActionButton`, e nada mais.
2. **`:core:designsystem` não pode nomear feature** (`navigation`, cenário "Design system
   inspecionado"), e não pode expor `UiText` numa assinatura pública: `:core:common` é
   `implementation` ali, e `feature/shell/impl` sequer o declara.
3. **A casca não pode enumerar features.** Um catálogo de *ações* nela não funcionaria para a ação
   de categorias, que depende do filtro em vigor.
4. **O `Scaffold` subcompõe o FAB antes do conteúdo.** `Scaffold.kt:187` (`Fab`) contra
   `Scaffold.kt:274` (`MainContent`), no mesmo `SubcomposeLayout`. Um `SideEffect` do conteúdo não
   alcança o slot do FAB no mesmo frame.
5. **Duas telas ficam compostas ao mesmo tempo.** O `NavHost` anima com `AnimatedContent`, que
   mantém o estado que sai composto até a animação terminar. Ambas as telas rodam `ChromeEffect`, e
   `SideEffect` roda a cada recomposição — não só na primeira.
6. **A `NavigationRail` recorta.** `NavigationRail.kt:149` é um `Surface`, e `Surface.kt:475`
   termina em `.clip(shape)`.
7. **A casca zera os insets.** `ChromeHost.kt:141` declara `contentWindowInsets = WindowInsets()`,
   e é justamente deles que `Scaffold.kt:229-248` tira o `fabOffsetFromBottom` quando não há barra.

`assets/interaction-plate.html` acompanha este documento: o botão nas suas três formas, as onze
telas com a ação que cada uma publica, e as duas larguras de janela com as duas posições, todas
acionáveis. Foi nela que a questão da posição (D4) apareceu — ela não se enxerga lendo o código,
porque as duas posições coexistem hoje em botões diferentes.

## Goals / Non-Goals

**Goals:**
- Um componente de botão de ação, um dono da posição, uma regra de visibilidade.
- A ação primária de cada tela permanece a um toque, e é a mesma de hoje.
- A decisão sobre quais ações existem permanece na feature dona.
- O botão nunca exibe as ações de outro destino, e nunca fica vazio por um frame durante a
  transição.
- Nenhuma tela perde o botão: sem ação própria, ela recebe a ação universal.

**Non-Goals:**
- **Rever o repertório de ações.** O que cada tela oferece é o que ela já oferece, mais as quatro
  entradas de menu nomeadas no proposal.
- **Alterar `TransferBetweenAccountsModal`.** Ver D10 — o modal serve como está.
- **Corrigir a folga inferior das listas.**
  `issues/the-fab-covers-the-last-rows-figure-when-the-list-reaches-the-bottom.md` continua aberto.
- **Introduzir `prefers-reduced-motion`.** Não existe em lugar nenhum do projeto, não há API comum
  no Compose Multiplatform para isso, e nenhum outro componente animado da casa o honra. Fazê-lo
  num componente só criaria inconsistência; se for para existir, é uma change própria.
- **Rever a bottom bar.** Continua restrita aos `primaryTab`.

## Decisions

### D1 — As ações viajam por um canal próprio, e não dentro do `ChromeConfig`

`ChromeConfig` é o `targetState` do `updateTransition` que anima a chrome. Uma `List<ChromeAction>`
com lambdas dentro dele nunca seria igual a si mesma entre recomposições, e a transição reiniciaria
a cada frame.

`ChromeController` ganha um verbo para publicar ações, e `ChromeStateHolder` as guarda em estado
separado. `ChromeConfig` continua com os dois campos que tem e continua animando o seletor.

### D2 — Um registro por destino, e não um slot com filtro

Cada destino tem o seu próprio registro de ações, chaveado pela identidade do `NavBackStackEntry`;
a casca lê o do destino corrente.

A razão é a restrição 5, e não a 4. Com um slot único — mesmo filtrado por identidade — a tela que
está saindo recompõe durante toda a animação e sobrescreve o slot com o seu próprio id; a casca
filtra, não encontra o destino corrente, e desenha **nada**. O botão piscaria entre as ações certas
e nenhuma ação durante a transição inteira. Com um registro por chave, a escrita da tela que sai não
toca a da tela que entra.

Pelo mesmo motivo, a limpeza é chaveada: `ChromeEffect` hoje faz `reset()` no `onDispose`
(`Chrome.kt:46-50`), que dispara **depois** de a tela nova ter publicado. Ela passa a limpar apenas
o registro da sua própria identidade.

**A identidade não é escrita por nenhuma tela.** O `ChromeEffect` a resolve sozinho: dentro de
qualquer `composable<Rota>{}`, o `NavBackStackEntry` é provido como `LocalViewModelStoreOwner`, e
`feature:shell:api` já enxerga o tipo (declara `api(projects.core.navigation)`, que por sua vez
declara `api(libs.androidx.navigation.compose)`). Zero cerimônia por tela, nenhum site a esquecer.

*Alternativas descartadas:* um `CompositionLocal` provido por cada `composable{}` do grafo custaria
20 sites de edição e cada esquecimento se manifestaria como "a tela perdeu o botão". Um `ViewModel`
com escopo na entrada dispensaria comparação e limpeza — o framework o destrói junto com a entrada —
mas obrigaria a casca a instanciar o `ViewModel` de outra entrada; fica registrado como a saída caso
o registro dê problema.

*Consequência para o spec:* a exigência não é sobre **quando** se publica, e sim sobre **o que se
desenha** — ordem de composição não é contratual entre telas irmãs, e nenhuma ordem resolveria a
restrição 5. O requisito foi escrito assim.

### D3 — O botão sai do slot `floatingActionButton` do `Scaffold`

Ele passa a ser desenhado num overlay próprio, dentro do `Box(Modifier.weight(1f))` de
`ChromeHost.kt:202-204`, ao lado de `content(padding)`. Quatro razões, todas verificadas:

- O `fabPlaceable` é medido e posicionado pelo `Scaffold` (`Scaffold.kt:187` e `291-293`), então um
  scrim de janela inteira dentro do slot seria posicionado junto com o botão — e o menu, ao crescer,
  moveria o botão.
- `ChromeHost.kt:168` fixa `Modifier.size(56.dp)`, que o componente novo não tem.
- `ChromeHost.kt:167` aplica `offset(y = 40.dp)`, que só faz sentido ancorado à barra e, sem ela,
  jogaria o botão para fora da área útil.
- `fabOffsetFromBottom` lê `contentWindowInsets` (restrição 7), que a casca zera — o botão no canto
  de uma tela empilhada cairia sob a barra do sistema.

O `Box` de conteúdo dá os limites certos de graça: não invade a rail nem o `DetailPane`
(`ChromeHost.kt:206-212`), e expõe o `padding`, cujo `calculateBottomPadding()` é a altura da bottom
bar — o ancoramento sai daí, sem o `offset` mágico. `aboveSharedElements(OverlayPriority.FloatingActionButton)`
continua aplicável tal como está.

O scrim é a exceção: ele cobre a janela inteira, e portanto é irmão do `Scaffold`, não filho do
`Box`. Do contrário a bottom bar continuaria clicável com o menu aberto.

### D4 — A posição acompanha o seletor

Com a bottom bar visível, o botão é central e ancorado a ela; sem ela, fica no canto; em janela
larga, é o `header` da rail. É onde cada um dos dez botões já está hoje.

A ressalva de D3 vale: "onde já está hoje" só é verdade depois de os insets serem reintroduzidos.
Nas nove telas o botão vive hoje sob `WindowInsets.safeDrawing` do `Scaffold` da própria tela; ao
mudar de dono, isso tem de ser reposto explicitamente.

Trocar `floatingActionButtonPosition` dinamicamente estaria descartado de todo modo: `fabPosition` é
lido no bloco de medição (`Scaffold.kt:199-215`) e o offset resultante é colocado sem animação
(`Scaffold.kt:291-293`) — o botão teleportaria. No overlay próprio, a posição é um `Alignment`
animado.

*Alternativas descartadas:* posição fixa ao centro — o botão central é convenção de FAB ancorado a
uma barra, e nas nove telas sem barra ficaria solto. Posição fixa no canto — mudaria de lugar nas
duas abas mais usadas.

### D5 — Sem ação própria, a tela recebe a ação universal

Registrar uma transação não pertence a tela alguma: é a razão de o app existir. Uma tela que não
publica nada recebe essa ação, e a casca continua obtendo-a por `TransactionsEntry`, como já faz.

Isso preserva alcance que a primeira versão desta proposta teria destruído sem dizer. Hoje, em
janela larga, `effectiveConfig` é `chromeController.config` (`ChromeHost.kt:124-127`) e só o
Dashboard publica — logo o botão aparece no `header` da rail em **toda** tela do desktop:
relatórios, configurações, faturas, histórico de taxas. Derivar a visibilidade de "publicou ações?"
apagaria o botão em todas elas.

Daí decorre que **`isFloatingActionButtonVisible` permanece**, com significado mais estreito:
supressão explícita. Não ter ação própria e pedir para não ter botão são coisas diferentes, e a
segunda precisa ser dizível — é como o Dashboard em modo de edição fica sem botão, via `ContentOnly`.
O que muda é que a flag deixa de ser forçada a `false` fora das abas primárias.

### D6 — Na rail, o botão fica no `header` e o menu é desenhado fora dela

A `NavigationRail` recorta (restrição 6), então um menu que abra de dentro dela é cortado por
construção. O botão permanece no `header` — a aparência do desktop não muda —, e o menu é desenhado
como irmão da rail, no overlay de D3, ancorado pela posição do botão.

*Alternativas descartadas:* um `Popup` não é recortado, mas é uma raiz de composição própria — sai
do `SharedTransitionScope` que `shared-element-transitions` exige, e sai do alcance do
`Modifier.exposeTestTags()` do `App`, o que tornaria as ações do menu invisíveis ao Maestro sem uma
chamada nova. Tirar o botão do `header` resolveria o recorte, mas mudaria a aparência do desktop sem
que ninguém tenha pedido.

### D7 — O modelo de uma ação copia o que já existe

`core/designsystem/.../ui/component/BottomNavigationBar.kt:14-20` já resolve este problema: uma
interface com `icon`, `labelRes: StringResource` e `testTag`, com os componentes genéricos sobre ela
e `NavDestination` (em `feature:shell:api`) implementando. A ação do botão segue o mesmo molde, mais
o `onClick`.

Mora em `:core:designsystem`, junto do componente que a consome — `:core:*` não pode depender de
`feature:*`, então não há escolha. Declarar um segundo tipo em `feature:shell:api` não compraria
nada: ela já declara `api(projects.core.designsystem)`.

**O rótulo é `StringResource`, não `UiText`**: é o que `BottomNavigationItem` usa, `UiText` vive em
`:core:common`, que é `implementation` no designsystem, e a casca sequer o declara.

**A lista é memoizada na tela.** Cada recomposição produziria uma lista nova — o `data class` compara
lambdas por referência —, invalidando o leitor a cada frame de qualquer animação. A tela constrói a
lista dentro de `remember(deps)`, com `deps` sendo aquilo de que a ação depende: para categorias,
o filtro (que é literalmente D11). Excluir `onClick` do `equals` seria pior: congelaria um lambda
com o filtro velho.

### D8 — O botão emite a identidade de teste que a tela declarou

Cada ação carrega o seu `testTag`; o botão emite o da primária da tela em foco, e cada item do menu
o seu. Os ids que o Maestro dirige hoje continuam existindo, agora declarados pela tela, e nenhum
subflow ganha um toque a mais.

Duas ressalvas verificadas: `CurrenciesScreen.kt:102-108` **não tem** `testTag` hoje (só
`contentDescription`), então ali ou se introduz um id novo ou se publica sem; e o
`contentDescription` passa a ser derivado do rótulo da ação, o que fecha de graça parte de
`issues/icon-buttons-are-clickable-without-any-accessible-label.md` — hoje os nove FABs de tela
passam `contentDescription = null`.

### D9 — A pré-seleção é um tipo, e vem do foco corrente

`addTransactionModal()` passa a aceitar a origem em foco. Dois nullables — conta e cartão —
admitiriam "os dois ao mesmo tempo", que não significa nada; o projeto já rejeita essa forma no
`CreditCardsEntry` e no `TransferBetweenAccountsModal`. A origem é um tipo selado em
`feature/transactions/api`, e o parâmetro é opcional. `accounts:impl` e `creditcards:impl` já
declaram `implementation(projects.feature.transactions.api)` e já injetam `TransactionsEntry`
(`AccountsScreen.kt:139`, `CreditCardsScreen.kt:101`).

**A origem é o foco corrente, não o argumento de rota.** `CreditCardsRoute.creditCardId` é apenas a
seleção inicial (`CreditCardsScreen.kt:73-76`); o foco é o índice do pager
(`CreditCardsUiState.Content.selectedCardIndex`). Ler a rota entregaria o cartão errado assim que o
usuário deslizasse.

O custo real não é o parâmetro: `AddTransactionViewModel` guarda `Account?`/`CreditCard?`, não ids, e
resolvê-los é assíncrono. A pré-seleção não pode ser valor inicial — precisa assentar quando o
repositório responde, sem correr contra o `input.target` padrão.

### D10 — A transferência não precisa de modo novo

Ela nasce com a **conta em evidência** como origem, e a conta padrão (`Account.isDefault`) como
resguardo. `AccountsUiState.Content` sempre tem uma conta em foco — `selectedAccountIndex` é
não-nulo e pareado com `domainAccounts` —, então `TransferBetweenAccountsModal(sourceAccount)`
serve como está.

Isso dispensa por inteiro o terceiro construtor que se cogitou, o `initialSourceAccount` nullable, e
a pergunta que ele abria e ninguém tinha respondido: `TransferBetweenAccountsModal.kt:211-213`
denomina o campo de valor pela moeda da origem e o rotula com o nome dela — sem origem não haveria
nem uma nem outra. `transfer-editing` fica intacta.

A ação só é publicada quando há mais de uma conta, como `AccountsScreen.kt:297`
(`canTransfer = uiState.accounts.size > 1`) já exige; do contrário ofereceria uma operação
impossível.

### D11 — Categorias mantém a primária dependente do filtro

A primária continua abrindo o formulário com o tipo que o filtro corrente indica, como
`CategoriesScreen.kt:138` já faz, e o menu oferece o outro. É o caso que prova por que as ações são
publicadas pela tela.

## Risks / Trade-offs

- **O menu na rail cobre o topo do conteúdo** → Aberto, ele cai sobre o título da tela e a primeira
  linha da lista. É transitório e dispensável com um toque fora. Não resolvido aqui.
- **O padrão fica pouco descoberto** → O controle de expansão existe em três das onze telas, e em
  nenhuma das duas abas principais. É consequência aceita: o valor é a consolidação.
- **Seis telas condicionam o botão ao estado** → `is Content` em cinco delas e `!is Loading` em
  recorrentes. "Publicar a mesma ação" tem de significar "publicar nenhuma ação nos mesmos estados",
  ou o botão passa a aparecer durante o carregamento.
- **Regressão silenciosa** → Uma tela que esqueça de publicar não quebra a compilação; ela apenas
  cai na ação universal, o que é discreto demais para ser notado. Mitigação: o teste estrutural de
  4.9, que falha se um `Scaffold` de feature voltar a declarar `floatingActionButton`.
- **Onze telas tocadas** → Mecânico em nove delas, mas amplo. A ordem abaixo mantém o app
  funcionando a cada passo.

## Migration Plan

0. **Fundações** — `implementation(projects.feature.shell.api)` nos nove `impl` que passarão a
   publicar; `compose.uiTest` e `compose.desktop.currentOs` em `core/designsystem`; e o nível novo
   em `OverlayPriority` para o scrim, entre a chrome e o botão. Puramente aditivo.
1. **Componente** em `:core:designsystem`, com as três formas e o scrim — sem consumidor.
2. **Canal** em `feature:shell:api`: o registro por destino, a identidade resolvida dentro do
   `ChromeEffect`, a limpeza chaveada. Nada muda nas telas.
3. **Casca**: o overlay de D3, a posição de D4, o menu de D6, a ação universal de D5. Dashboard e
   Transações publicam as suas. A partir daqui a flag deixa de ser forçada fora das abas.
4. **As nove telas**, uma a uma: remover o `floatingActionButton` e publicar a mesma ação, com o
   mesmo `testTag` e a mesma condição de estado.
5. **As ações novas**: a pré-seleção de D9 e as quatro entradas de menu.
6. **Strings** nos dois idiomas.
7. **Verificação**, incluindo a suíte Maestro no dispositivo conforme.

Reversão: até o passo 3 a mudança é aditiva. Depois dele, é devolver o `floatingActionButton` de
cada tela — mecânico, mas por tela.

## Open Questions

- **O scrim escurece a rail também?** D3 o põe cobrindo a janela para que a bottom bar não fique
  clicável; em janela larga isso escurece a rail inteira, o que pode ser demais para um menu de duas
  entradas.
- **O menu fecha ao navegar?** Nenhuma ação prevista navega, mas a regra deve existir antes que a
  primeira apareça. Com o estado do menu vivendo na casca chaveado pelo destino, isso cai de graça.
