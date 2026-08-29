## Why

O app tem **dez botões de ação flutuantes**. Um vive na casca (`ChromeHost.kt` — `AddTransactionFab`)
e adiciona transação; os outros nove vivem cada um no `Scaffold` da sua tela: contas
(`AccountsScreen.kt:208`), cartões (`CreditCardsScreen.kt:160`), categorias
(`CategoriesScreen.kt:136`), orçamentos (`BudgetsScreen.kt:99`), recorrentes
(`RecurringScreen.kt:170`), parcelamentos (`InstallmentsScreen.kt:236`), moedas
(`CurrenciesScreen.kt:103`), taxas (`ExchangeRatesScreen.kt:132`) e suporte
(`SupportScreen.kt:94`).

Eles nunca foram acertados entre si, e a janela larga expõe o resultado: a rail carrega o FAB da
casca no seu `header` enquanto a tela desenha o seu próprio, de modo que **dois botões idênticos**
ficam na mesma janela — o que o backlog já registra em
`issues/the-fab-covers-the-last-rows-figure-when-the-list-reaches-the-bottom.md`.

A divergência não é só de contagem. Cada tela oferece **uma** ação e não tem onde pôr uma segunda,
porque o botão é o `Scaffold` dela. E no celular a casca apaga o FAB fora das abas primárias
(`effectiveConfig` cai em `ContentOnly` quando o destino não é `primaryTab`), de modo que a mesma
superfície aparece por dois motivos diferentes conforme a tela — uma vez porque a casca a
desenhou, nove vezes porque a tela a desenhou.

## What Changes

- A casca passa a ser dona **do único FAB do app**: um componente, uma posição, uma regra de
  visibilidade. Os nove FABs de tela são removidos dos seus `Scaffold`s.
- As telas passam a **publicar as suas ações** pelo canal de chrome que já existe
  (`ChromeEffect`), e a casca as renderiza. A decisão de *quais* ações continua na feature dona;
  o que a casca decide é *como* elas aparecem. Um catálogo estático na casca não serviria: a ação
  de categorias depende do filtro corrente (`CategoriesScreen.kt:138` —
  `CategoryFormModal(initialType = uiState.filter.fabInitialType)`).
- A **ação primária** de cada tela é a que o FAB local dela já executa hoje, a um toque. As
  demais ficam atrás de um chevron. Nenhuma tela perde o que tinha.
- O menu existe em **três** das onze telas: contas (transferência, transação na conta), cartões
  (transação no cartão) e categorias (o outro tipo). As outras oito publicam uma ação só e o FAB
  não tem chevron — indistinguível do que existe hoje.
- **BREAKING (contrato interno):** `ChromeConfig.isFloatingActionButtonVisible` é **removido**. A
  visibilidade do FAB passa a ser derivada — ele aparece se, e somente se, a tela publicou ações.
  `ContentOnly` passa a significar apenas a ausência da barra. O Dashboard em modo de edição
  publica zero ações e o FAB some sem regra nova.
- A visibilidade do FAB **desacopla-se da barra**: a bottom bar continua restrita a `primaryTab`,
  o FAB não. É o que permite que ele exista nas nove telas que hoje o desenham por conta própria.
- A **posição acompanha a barra**: `FabPosition.Center` quando a bottom bar está visível, canto
  (`End`) quando não está. É exatamente onde cada um dos dez botões já está hoje, agora dito uma
  vez só.
- `TransferBetweenAccountsModal` ganha um **terceiro modo**: registrar sem conta de origem
  pré-selecionada. Hoje o construtor a exige, embora o formulário já saiba operar sem ela —
  `selectedSourceAccount` é nullable no `UiState`, `SelectSourceAccount` aceita `null` e o
  `AccountSelector` da origem já lista todas as contas (`TransferBetweenAccountsModal.kt:178`).
  A proibição vive só na fronteira.
- `TransactionsEntry.addTransactionModal()` ganha **pré-seleção**: aberta do FAB de cartões ela
  nasce com o cartão em foco (`CreditCardsRoute.creditCardId`), e do de contas com a conta em
  foco. Sem isso a ação existiria mas seria pior do que navegar até a aba de transações.
- **BREAKING (E2E):** os `testTag` dos nove FABs de tela deixam de existir; o FAB da casca passa a
  publicar o tag da tela corrente. Seis subflows Maestro tocam esses ids.

## Capabilities

### New Capabilities

- `contextual-fab`: o FAB como superfície de ações da tela corrente — quem declara as ações, o que
  é primário e o que fica no menu, como a forma do botão deriva da quantidade de ações, quando ele
  aparece e onde fica em cada largura de janela. Hoje esse assunto está disperso entre um trecho
  de `navigation` e nove `Scaffold`s; ele passa a ter dono.

### Modified Capabilities

- `navigation`: o requisito da casca adaptativa cede ao `contextual-fab` tudo o que dizia do FAB e
  altera o que resta. Sai a afirmação de que o FAB é o da ação primária obtida por entry point pela
  casca (a casca deixa de conhecer `TransactionsEntry`); sai `isFloatingActionButtonVisible` do
  contrato de chrome; e a regra de visibilidade passa a valer só para o seletor — o FAB tem a sua,
  derivada das ações publicadas. O que a casca continua decidindo (rail vs bottom bar, catálogo
  único, seleção do item ativo) fica intacto.
- `feature-entry-points`: o cenário da ação primária hospedada por outra feature muda de sujeito —
  não é mais a casca que obtém `addTransactionModal()`, é cada tela que publica a ação, obtendo o
  modal do entry point quando ele pertence a outra feature (contas e cartões já injetam
  `TransactionsEntry`). Acresce a pré-seleção na assinatura: um entry point pode receber o
  contexto que a tela chamadora tem à mão, sem que isso vaze tipo de `impl`.
- `transfer-editing`: a conta de origem passa a ser uma **pré-seleção**, não um pré-requisito.
  Registrar uma transferência continua nascendo na tela de contas — o requisito que diz isso não
  muda —, mas nasce dela inteira, e não só do card de uma conta aberta. As validações existentes
  seguem valendo sem alteração: a origem continua obrigatória **no envio**, e o que muda é apenas
  o estado inicial do formulário.

## Impact

**Código**
- `core/designsystem` — novo componente de FAB com ação primária e menu. O
  `FloatingActionButtonMenu`/`ToggleFloatingActionButton` do M3 Expressive **não existe** no
  `org.jetbrains.compose.material3:material3:1.9.0` que o Compose Multiplatform 1.10.1 resolve
  (verificado no jar: só `FloatingActionButton` e `ExtendedFloatingActionButton`), então o
  componente é nosso, incluindo animação, scrim e o comportamento de fechar.
- `feature/shell/api` — `ChromeConfig` perde `isFloatingActionButtonVisible`; `ChromeController`
  ganha o canal das ações. As ações **não** entram no `ChromeConfig`: ele é o `targetState` de um
  `updateTransition` em `ChromeHost.kt`, e uma lista de lambdas nunca é igual a si mesma entre
  recomposições, o que reiniciaria a transição da chrome indefinidamente.
- `feature/shell/impl` — `ChromeHost` renderiza o novo componente e deixa de injetar
  `TransactionsEntry`; `ChromeStateHolder` guarda as ações.
- As onze telas — as nove que perdem o `Scaffold.floatingActionButton` mais Dashboard e
  Transações, que passam a publicar a ação que a casca hoje assume por elas. `DashboardScreen` é
  a única que já usa `ChromeEffect` hoje.
- `feature/accounts/impl` — terceiro construtor de `TransferBetweenAccountsModal` e
  `initialSourceAccount` nullable no ViewModel, incluindo a linha que hoje cai em
  `accounts.firstOrNull()` quando a origem não resolve.
- `feature/transactions/api` + `impl` — pré-seleção em `addTransactionModal()` e no
  `AddTransactionModal`, que hoje não tem parâmetro algum.

**Testes**
- `AppNavCatalogTest` não cobre o FAB; a cobertura nova é de ViewModel e de composição — quais
  ações cada tela publica, e a forma do botão para zero, uma e várias ações.
- Maestro: `create_account.yaml` (`accounts_add`), `create_credit_card.yaml`
  (`credit_cards_add`), `create_category.yaml` (`categories_add`) e três subflows que tocam
  `add_transaction_fab`. Como a primária de cada tela é a de hoje, os quatro ids podem ser
  reemitidos pelo FAB da casca e **nenhum flow precisa de um toque a mais** — só os que forem
  exercitar as ações novas.

**Strings**
- Cada ação do menu precisa de rótulo visível, e cada FAB de `contentDescription`. As chaves
  entram em `values/` e `values-en/` no mesmo commit. O backlog já registra que só o FAB tem
  rótulo acessível hoje (`issues/icon-buttons-are-clickable-without-any-accessible-label.md`).

**Riscos**
- `ChromeEffect` publica em `SideEffect`, que roda depois da composição: no primeiro frame após
  navegar, o FAB mostraria as ações da tela anterior. Hoje isso é invisível porque o canal só
  liga e desliga visibilidade. O design precisa resolver a publicação síncrona.
- Em janela larga o FAB é o `header` da rail, encostado no topo: o menu abre para o lado e cai
  sobre o título da tela e a primeira linha da lista.
