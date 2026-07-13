## Context

Hoje a navegação do app assume o modelo mobile como universal:

- **`AppNavHost`** (`:app:shared`) é um `NavHost` único onde `HomeGraph` aninha Dashboard + Transactions, e as outras 7 features (accounts, budgets, categories, creditcards, recurring, report) são graphs irmãos top-level. `startDestination = HomeGraph`.
- **`HomeGraph`** existe só para agrupar as 2 abas: `homeGraph()` (em `feature:home:impl`) monta `navigation<HomeGraph>(startDestination = DashboardGraph)` e registra `DashboardEntry`/`TransactionsEntry` via Koin.
- **`HomeChromeHost`** (`feature:home:impl`) envolve o `AppNavHost` inteiro e, hoje, já ramifica por largura (change `adaptive-layout`): rail lateral (≥600dp) ou bottom bar (<600dp), sempre com os 2 itens de `NavigationItem`. A visibilidade da chrome é a conjunção de `isHome` (destino ∈ `HomeGraph`) **e** `HomeChromeConfig` publicado pela tela via `HomeChromeEffect`.
- **Troca de aba** usa `popUpTo(DashboardRoute){inclusive=false}; launchSingleTop=true`, deliberadamente **sem** `saveState`/`restoreState` (a spec atual documenta o porquê: o Dashboard empilha destinos de outras abas, ex.: transações filtradas por um widget).
- **Features não-aba** (Contas, Cartões, …) só são alcançadas pelo **grid de quick actions** (`QuickActionType`, 8 itens sem ícone, em `feature:dashboard:impl`). Ao navegar para uma delas, `isHome` fica `false` → a chrome some → tela full-screen com voltar.
- `HomeChromeConfig`/`Controller`/`LocalHomeChromeController`/`HomeChromeEffect` vivem em `feature:home:api`; `HomeChromeStateHolder` em `feature:home:impl`.

Constraint arquitetural relevante (`module-architecture`): `:app:shared` MUST NOT conter chrome, `Scaffold`, rotas nem enumeração de abas; `impl ⊄ impl`; `:core:*` não pode nomear feature nem depender de `androidx.navigation` (`:core:designsystem`).

O problema: no desktop o rail deveria ser uma **sidebar persistente** que alterna entre todas as seções, cada uma guardando sua navegação interna. O modelo atual (chrome gated por `isHome`, sem back stacks por seção) não comporta isso — clicar numa feature esconderia o rail.

## Goals / Non-Goals

**Goals:**
- Uma **primitiva de navegação única** parametrizada por plataforma, não dois códigos paralelos: mobile e desktop diferem só na **lista de membros do seletor** e no **local do seletor**.
- Desktop: rail persistente com todas as features; navegação interna por seção com back stacks preservados; voltar só para sub-destinos.
- Mobile: comportamento preservado (bottom bar de 2 abas + grid + voltar full-screen), mas rodando sobre a mesma primitiva.
- **Fonte única** de destinos alimentando rail, bottom bar e grid.
- Renomear `feature:home` → `feature:shell`, refletindo o novo papel; mover o contrato de chrome para `:core`.
- Manter `:app:shared` sem chrome e `:core:*` sem nomear features.

**Non-Goals:**
- Master-detail / dois painéis (list-detail) — o rail alterna seção inteira, não abre painel de detalhe lado a lado.
- Persistência de back stacks entre execuções do app (só em memória, durante a sessão).
- Redesenho visual do rail além de acomodar 9 itens (ordenação/divisor é decisão de UX pontual, ver Open Questions).
- Alterar o comportamento de deep-link externo além do necessário para o `startDestination`.
- Mudar o eixo de plataforma (`isDesktop`) que governa Support — permanece como a change `adaptive-layout` deixou.

## Decisions

### Decisão 1 — Uma primitiva: "navegar para seção" vs "navegar para sub-destino"

Toda navegação do app se resolve em dois casos:

```kotlin
// item do seletor (aba no mobile; qualquer graph no desktop)
fun navigateToSection(route: NavRoute) = navController.navigate(route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

// sub-destino (detalhe empilhado dentro de uma seção)
fun navigateToDetail(route: NavRoute) = navController.navigate(route)
```

`navigateToSection` dá **multiple back stacks**: cada seção preserva sua pilha interna ao alternar. `navigateToDetail` é push comum, com voltar. A diferença mobile↔desktop é **apenas quais rotas passam por `navigateToSection`** — não há ramo de código por plataforma na primitiva.

- **Por quê**: é o mecanismo canônico de bottom-nav/rail do Navigation Compose; unifica os dois form factors sob uma regra só; realiza literalmente "a navegação ocorre dentro do graph".
- **Alternativa descartada — NavHosts aninhados (um por seção)**: daria isolamento total de pilha, mas viola "exatamente um `NavHost`" da spec, complica deep-link e a troca de seção perderia o `NavController` raiz. `saveState`/`restoreState` entrega o mesmo efeito com um `NavHost` só.

### Decisão 2 — Achatar `HomeGraph`; Dashboard e Transactions viram graphs top-level

`HomeGraph` some. `feature:dashboard:impl` e `feature:transactions:impl` passam a expor `dashboardGraph()`/`transactionsGraph()` (cada um `navigation<XGraph>(startDestination = …)`), simétricos às demais features. `AppNavHost` fica achatado, `startDestination = DashboardGraph`:

```
NavHost(startDestination = DashboardGraph)
  dashboardGraph()      transactionsGraph()   accountsGraph()   budgetsGraph()
  creditCardsGraph()    categoriesGraph()     recurringGraph()  reportGraph()
  supportGraph()   // registrado só onde suportado
```

- **Por quê**: o único papel do `HomeGraph` era agrupar as 2 abas para o `popUpTo(Dashboard)`; back stacks por seção (Decisão 1) tornam o agrupamento desnecessário. Achatar remove a assimetria "2 features aninhadas, 7 irmãs" e faz o seletor operar sobre uma lista uniforme de graphs.
- **Consequência sobre a spec atual**: a regra "troca de abas MUST NOT usar `saveState`/`restoreState`" (justificada pelo Dashboard empilhar transações de um widget) é **substituída**. Com back stacks por seção, abrir "transações filtradas" a partir de um widget do Dashboard é um `navigateToDetail` **dentro da seção Dashboard** (empilha na pilha do Dashboard), não um pulo para a seção Transactions. O voltar retorna ao Dashboard; tocar na seção Transactions no seletor mostra a pilha própria de Transactions. O motivo original do veto deixa de existir.

### Decisão 3 — `feature:home` → `feature:shell` (mantém `api` + `impl`)

A shell é dona da chrome adaptativa e do catálogo de destinos. `shell:api` herda o contrato de chrome (`HomeChromeConfig`/`Controller`/`LocalController`/`Effect`) e o **tipo/interface** do catálogo — tudo referencia só `:core` (`NavRoute`, `StringResource`, `ImageVector`), como `home:api` já fazia. `shell:impl` provê a **lista concreta** e, para construir `AccountsRoute()`/`BudgetsRoute()`/…, depende de todas as `feature:*:api` (permitido: `impl → qualquer api`).

```
feature/home/api   ──►  feature/shell/api   (só :core; contrato de chrome + tipo do catálogo + NavCatalog)
feature/home/impl  ──►  feature/shell/impl  (depende de TODAS as feature:*:api)
                          - AppChrome (ex-HomeChromeHost): rail | bottom bar | FAB
                          - catálogo concreto + binding Koin do NavCatalog
                          - regra de seleção e de voltar
```

- **Por quê**: o nome `home` descrevia "grupo de abas", papel que evapora. `shell` descreve "casca de navegação adaptativa". Mantém a chrome **fora** de `:app:shared` (respeita `module-architecture`).
- **Por quê manter `api`**: features já dependem de `home:api` para publicar chrome via `HomeChromeEffect`; e `dashboard:impl` precisa do tipo do catálogo. Manter em `shell:api` (rename) preserva o status quo sem descer nada para `:core`.
- **Alternativa descartada — manter o nome `home`**: menos churn de imports, mas o nome passa a mentir sobre o papel; o usuário pediu o rename explicitamente.
- **Alternativa descartada — chrome sobe para `:app:shared`**: viola a regra "`:app:shared` sem chrome/`Scaffold`".

### Decisão 4 — Catálogo único de destinos como fonte de verdade

Um tipo de metadados de destino + uma lista concreta substituem `NavigationItem` (2) e `QuickActionType` (8):

```kotlin
// tipo em feature:shell:api (só referencia :core: NavRoute + StringResource + ImageVector)
data class NavDestination(
    val icon: ImageVector,
    val labelRes: StringResource,
    val route: NavRoute,
    val primaryTab: Boolean,   // Dashboard, Transactions
    val mobileOnly: Boolean,   // Support
)
interface NavCatalog { val destinations: List<NavDestination> }
```

`shell:impl` provê a `List<NavDestination>` concreta (construindo `AccountsRoute()`, `BudgetsRoute()`, …) e registra o `NavCatalog` no Koin. Cada consumidor projeta:

```
rail (desktop)    = destinations.filter { !it.mobileOnly }          // 9, Support fora
bottom (mobile)   = destinations.filter { it.primaryTab }           // Dashboard, Transactions
grid (mobile)     = destinations.filter { !it.primaryTab }          // 8 quick actions (Support incl.)
```

`feature:dashboard:impl` injeta `NavCatalog` (via Koin; depende de `shell:api` para o tipo) e alimenta o grid — sem violar `impl ⊄ impl` (é `impl → api`).

- **Por quê**: elimina a decisão duplicada de "quais são as features" e garante ícone/label/rota consistentes entre as três afordâncias.
- **Alternativa descartada — catálogo concreto em `shell:impl` consumido direto pelo grid**: `dashboard:impl` não pode depender de `shell:impl` (`impl ⊄ impl`). Por isso o **tipo/interface** fica em `shell:api` e a **lista concreta** em `shell:impl`, ligadas por Koin.
- **Alternativa descartada — manter os dois enums**: perde a fonte única que o usuário pediu e ressincroniza ícones/rotas na mão.

### Decisão 5 — Contrato de chrome permanece em `shell:api` (renomeado de `home:api`)

`HomeChromeConfig`, `HomeChromeController`, `LocalHomeChromeController`, `HomeChromeEffect` (e o `NavDestination`/`NavCatalog` da Decisão 4) ficam em `feature:shell:api` — o mesmo lugar que `home:api` ocupava, só renomeado. Nenhum tipo desce para `:core`.

- **Por quê**: as features já dependem de `home:api` para publicar chrome via `HomeChromeEffect`; manter em `shell:api` preserva exatamente esse arranjo com blast radius mínimo. Descer para `:core` seria uma limpeza opcional (qualquer feature publica chrome sem depender do módulo shell), mas não é necessária e foi adiada (Open Questions).
- **Efeito na spec `navigation`**: o cenário "Core de UI inspecionado" continua válido na essência (esses tipos **não** residem em `:core:ui`/`:core:designsystem`); só a referência a `feature:home:api` vira `feature:shell:api`.

### Decisão 6 — Regra de botão voltar derivada do back stack

```
mostra voltar  ⟺  (isDesktop == false && destino não é primaryTab)
                   || (profundidade da pilha da seção corrente > 1)
```

- Desktop, raiz de qualquer seção → sem voltar (o rail é a navegação).
- Desktop, sub-destino → voltar (volta dentro da seção).
- Mobile, aba → sem voltar; mobile, qualquer outra tela → voltar (como hoje).

- **Por quê**: unifica "voltar só no mobile ou em sub-destinos" numa expressão derivável, sem estado novo. A seleção do item ativo continua por `hasRoute<T>()` sobre a `hierarchy`, cobrindo sub-destinos (o item raiz da seção permanece destacado).

### Decisão 7 — Seleção e visibilidade da chrome no modelo achatado

`selectedItem` = o `NavDestination` cuja `route` casa com a `hierarchy` do destino atual (`hasRoute`). Como não há mais `HomeGraph`, a antiga condição `isHome` some. A visibilidade da chrome passa a ser:

- **Rail (desktop)**: persistente — visível sempre, exceto quando a tela publica `ChromeConfig.ContentOnly` (fluxos full-screen: edição do dashboard, wizards, etc.).
- **Bottom bar (mobile)**: visível quando o destino é `primaryTab` **e** o `ChromeConfig` não pede `ContentOnly`; oculta em telas empilhadas (preserva o comportamento atual em que features não-aba são full-screen).

`ContentOnly` continua sendo o único canal para uma tela esconder a chrome, agora reinterpretado por form factor. Os chamadores atuais de `ContentOnly` (ex.: modo de edição do Dashboard) precisam de re-auditoria para confirmar a intenção no desktop (Open Questions).

## Risks / Trade-offs

- **Mexer no back de mobile é área sensível (predictive back, gestos)** → back stacks por seção mudam a árvore de pilhas. Mitigação: cobrir com testes de navegação os fluxos "widget do Dashboard → transações filtradas → voltar" e "alternar aba preserva pilha"; validar predictive back no Android manualmente.
- **`saveState`/`restoreState` pode reter estado indesejado de uma seção** → o veto original da spec era justamente esse. Mitigação: com o achatamento, transações-de-widget empilham na seção Dashboard (não na Transactions), então nenhuma seção guarda destino de outra; documentar isso na spec nova como o cenário que a substitui.
- **Rename `home`→`shell` gera churn amplo** (pacotes, Gradle, Koin, imports, `settings.gradle.kts`, exports iOS, testes) → mitigação: rename mecânico num passo isolado, com `./gradlew check` antes de mudar comportamento.
- **`shell:impl` depende de todas as `feature:*:api`** → acopla a shell a toda a superfície de rotas. Aceito: é o preço de um seletor que alterna entre todas as seções; a topologia estrela se mantém (é `impl → api`, nunca `impl → impl`).
- **Rail com 9 itens pode ficar denso / sem hierarquia visual** → mitigação: divisor entre `{Dashboard, Transactions}` e as features (decisão de UX, Open Questions).
- **`ContentOnly` reinterpretado no desktop** pode esconder o rail em telas onde ele deveria persistir → mitigação: re-auditar chamadores; default no desktop é rail persistente, `ContentOnly` só onde a tela realmente ocupa a janela toda.
- **Ícones das 7 features ainda não existem** → bloqueio de UX, não técnico; resolver antes/junto (Open Questions).

## Migration Plan

Passos incrementais, cada um com `./gradlew check` verde antes do próximo:

1. **Renomear `feature:home` → `feature:shell`** (diretório, pacote, módulo Gradle/Koin, `settings.gradle.kts`, exports iOS, `App()`, imports em `dashboard:impl` e `app:shared`). Rename mecânico, sem mudança de comportamento. `./gradlew check` verde.
2. **Achatar o `AppNavHost`**: `dashboardGraph()` e `transactionsGraph()` (já existem) passam a ser chamados direto no `AppNavHost`; remover `homeGraph()`/`HomeGraph`; `startDestination = DashboardGraph`. Ainda usando o `popUpTo(Dashboard)` atual — só a estrutura de grafos muda. Validar navegação.
3. **Introduzir `NavDestination`/`NavCatalog`** em `shell:api` + a lista concreta em `shell:impl`. Ainda sem trocar consumidores. Comportamento inalterado.
4. **Trocar os consumidores para o catálogo**: bottom bar + grid projetam de `NavCatalog` (removendo `NavigationItem` e `QuickActionType`); `dashboard:impl` injeta `NavCatalog`.
5. **Implementar a primitiva unificada** (`navigateToSection`/`navigateToDetail`) com `saveState`/`restoreState`, e ligar o seletor: bottom bar (mobile, `primaryTab`) e rail (desktop, `!mobileOnly`).
6. **Regra de voltar** derivada do back stack; re-auditar chamadores de `ContentOnly`.
7. **Ícones** das features no catálogo (após decisão de UX).

Rollback: cada passo é um commit isolado; reverter o passo 5/6 restaura o modelo gated-por-`isHome` sem tocar nos passos estruturais 1–4.

## Open Questions

- **Ícones das 7 features** — qual conjunto (Material vs custom)? Decisão de UX; sugerido passar pelo `ux-ui-designer` antes do passo 7.
- **Divisor/ordenação no rail** — separar visualmente as 2 abas primárias das 7 features, ou lista corrida? UX.
- **Descer o contrato de chrome para `:core`?** — adiado. Mantido em `shell:api` por ora; mover para `:core:navigation` seria limpeza futura (exige confirmar que `:core:navigation` pode depender de `StringResource`/`ImageVector` sem violar "sem enumerar features").
- **Nomes** — manter `HomeChromeConfig`/`HomeChromeEffect` ou encurtar para `ChromeConfig`/`ChromeEffect` ao renomear o módulo?
- **`ContentOnly` no desktop** — enumerar exatamente quais telas devem esconder o rail (edição do Dashboard? seletor de ícone? modais já são overlay).
- **Grid no desktop** — some totalmente ou o Dashboard ganha outro widget no lugar? A proposta diz "rail substitui o grid"; confirmar que o Dashboard no desktop não fica com um buraco visual.
