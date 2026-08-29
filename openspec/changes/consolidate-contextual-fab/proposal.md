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
- O botão **não desaparece numa tela sem ação própria**: ela recebe a **ação universal** —
  registrar uma transação —, que é o que o FAB da casca já faz hoje. Isso preserva o alcance
  existente no desktop, onde o botão aparece no `header` da rail em toda tela (relatórios,
  configurações, faturas, histórico de taxas), e o estende ao celular, onde ele hoje some fora das
  abas primárias.
- `ChromeConfig.isFloatingActionButtonVisible` **permanece**, com um significado mais estreito:
  supressão explícita. `ContentOnly` continua apagando seletor e botão, e é assim que o Dashboard
  em modo de edição fica sem botão. O que muda é que a flag deixa de ser forçada a `false` fora das
  abas primárias.
- A visibilidade do FAB **desacopla-se da barra**: a bottom bar continua restrita a `primaryTab`,
  o FAB não. É o que permite que ele exista nas nove telas que hoje o desenham por conta própria.
- A **posição acompanha a barra**: `FabPosition.Center` quando a bottom bar está visível, canto
  (`End`) quando não está. É exatamente onde cada um dos dez botões já está hoje, agora dito uma
  vez só.
- A transferência publicada no menu de Contas nasce com a **conta em evidência** como origem, e a
  conta padrão (`Account.isDefault`) como resguardo. `TransferBetweenAccountsModal` **não muda**:
  `AccountsUiState.Content` sempre tem uma conta em foco (`selectedAccountIndex`, pareado com
  `domainAccounts`), então o construtor que exige a origem continua servindo, e o modo "sem origem"
  que se cogitou não é necessário.
- `TransactionsEntry.addTransactionModal()` ganha **pré-seleção**: aberta do menu de cartões ela
  nasce com o cartão em foco, e do de contas com a conta em foco. O foco é o do pager
  (`CreditCardsUiState.Content.selectedCardIndex`), e **não** o argumento de rota, que é apenas a
  seleção inicial e fica velho assim que o usuário desliza. Sem pré-seleção a ação existiria mas
  seria pior do que navegar até a aba de transações.
- **BREAKING (E2E):** os `testTag` dos nove FABs de tela deixam de existir; o FAB da casca passa a
  publicar o tag da tela corrente. Dezesseis arquivos Maestro tocam esses ids — seis em
  `subflows/` e o restante em `flows/`.

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
`transfer-editing` **não muda**. Chegou-se a cogitar um modo sem conta de origem, e o código o
dispensa: a tela de contas sempre tem uma conta em evidência, e a transferência continua nascendo
apontando para a conta de onde o dinheiro sai — que é o que o requisito já diz.

## Impact

**Build**
- Nove `impl` passam a publicar ações e precisam de `implementation(projects.feature.shell.api)`;
  hoje só `feature/dashboard/impl/build.gradle.kts` o declara.
- `core/designsystem/build.gradle.kts` precisa de `compose.uiTest` e `compose.desktop.currentOs`
  para o teste do componente — hoje só `core/ui` os tem.

**Código**
- `core/designsystem` — novo componente de FAB com ação primária e menu. O
  `FloatingActionButtonMenu`/`ToggleFloatingActionButton` do M3 Expressive **não existe** no
  `org.jetbrains.compose.material3:material3:1.9.0` que o Compose Multiplatform 1.10.1 resolve
  (verificado no jar: só `FloatingActionButton` e `ExtendedFloatingActionButton`), então o
  componente é nosso, incluindo animação, scrim e o comportamento de fechar.
- `feature/shell/api` — `ChromeController` ganha o canal das ações, chaveado por destino. As ações
  **não** entram no `ChromeConfig`: ele é o `targetState` de um `updateTransition` em
  `ChromeHost.kt`, e uma lista de lambdas nunca é igual a si mesma entre recomposições, o que
  reiniciaria a transição da chrome indefinidamente.
- `feature/shell/impl` — `ChromeHost` renderiza o novo componente, mantém `TransactionsEntry` para
  a ação universal, e desenha o botão **fora** do slot `floatingActionButton` do `Scaffold`: o
  slot é subcomposto antes do conteúdo, não comporta um scrim de janela inteira, e o seu
  `fabOffsetFromBottom` lê os `contentWindowInsets` que a casca zera — o que poria o botão sob a
  barra do sistema numa tela sem bottom bar.
- As onze telas — as nove que perdem o `Scaffold.floatingActionButton` mais Dashboard e
  Transações, que passam a publicar a ação que a casca hoje assume por elas. `DashboardScreen` é
  a única que já usa `ChromeEffect` hoje. Seis das nove condicionam hoje o botão ao estado
  (`is Content`, e `!is Loading` em recorrentes); a condição vira "publicar nenhuma ação" nos
  mesmos estados, ou o botão passa a aparecer durante o carregamento.
- `feature/transactions/api` + `impl` — pré-seleção em `addTransactionModal()` e no
  `AddTransactionModal`, que hoje não tem parâmetro algum. O `AddTransactionViewModel` guarda
  `Account`/`CreditCard`, não ids, e a resolução é assíncrona — a pré-seleção não é um valor
  inicial, e sim um estado que assenta quando o repositório responde.

**Testes**
- `AppNavCatalogTest` não cobre o FAB; a cobertura nova é de ViewModel e de composição — quais
  ações cada tela publica, e a forma do botão para zero, uma e várias ações.
- Maestro: dezesseis arquivos tocam os ids dos botões, seis em `subflows/` e o restante em
  `flows/`. Como a primária de cada tela é a de hoje, esses ids são reemitidos pelo botão da casca
  e **nenhum flow precisa de um toque a mais** — só os que forem exercitar as ações novas. Duas
  ressalvas: `CurrenciesScreen` não tem `testTag` hoje, então ou se introduz um id novo ou ela
  publica sem; e as ações do menu só alcançam o Maestro se a raiz que as compõe publicar os test
  tags.

**Strings**
- Cada ação do menu precisa de rótulo visível, e cada FAB de `contentDescription`. As chaves
  entram em `values/` e `values-en/` no mesmo commit. O backlog já registra que só o FAB tem
  rótulo acessível hoje (`issues/icon-buttons-are-clickable-without-any-accessible-label.md`).

**Riscos**
- Durante uma transição de navegação **duas telas estão compostas ao mesmo tempo**, e ambas
  publicam. Sem um registro por destino, o botão alterna entre as ações certas e nenhuma ação
  durante toda a animação, e a tela que se dispõe apaga o que a tela que entra publicou.
- Em janela larga o botão é o `header` da rail, que é um `Surface` e portanto recorta o que
  transborda: o menu precisa ser desenhado fora dela.
- O botão passa a existir em telas empilhadas, onde a casca zera os `contentWindowInsets`. Sem
  tratamento, ele é desenhado sob a barra de navegação do sistema.
