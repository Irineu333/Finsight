## Context

A chrome do Home vive em `feature/home/impl/HomeChromeHost.kt` como um `Scaffold` com `bottomBar` (`BottomNavigationBar`, 2 itens: Dashboard, Transactions) e um `FloatingActionButton` central (`FabPosition.Center`). A visibilidade da bottom bar e do FAB é derivada de duas condições: (1) o destino atual pertencer à hierarquia de `HomeGraph` e (2) o `HomeChromeConfig` publicado pela tela via `HomeChromeEffect`. O `Scaffold` fica por fora do `NavHost`, e o `App()` do `:app:shared` apenas invoca `HomeChromeHost`.

O projeto já expõe `expect val isDesktop` em `:core:common`, usado em `DashboardComponentsBuilder` para ocultar a quick action de Support no desktop (`takeUnless { isDesktop }`). A feature Support é genuinamente mobile-only: o `jvmMain` provê `UnsupportedSupportRepository`.

Esta mudança torna a chrome adaptativa por largura de janela, mantendo todo o resto do contrato de chrome intacto.

## Goals / Non-Goals

**Goals:**
- Em janelas largas (≥ Medium/600dp), navegação primária como `NavigationRail` à esquerda com o FAB no `header`.
- Em janelas estreitas (< Medium), manter exatamente a chrome atual (bottom bar + FAB central).
- Reaproveitar `NavigationItem`, `selectedItem`, `onItemSelected` e `HomeChromeConfig` sem duplicar lógica.
- Ocultar features mobile-only (Support) no desktop de forma consistente em todos os pontos de entrada.
- Usar exclusivamente a API oficial do Material3 Adaptive para medir a largura.

**Non-Goals:**
- Master-detail / layout de dois painéis (adiado; escopo "só rail por enquanto").
- Constrangimento de largura máxima / centralização de conteúdo (adiado).
- Promover as quick actions a itens permanentes do rail (rail segue com os 2 itens da bottom bar).
- Alterar rotas, grafos, `AppNavHost`, Koin ou `:app:shared`.

## Decisions

### Decisão 1 — Medição de largura: `WindowSizeClass` oficial, não `BoxWithConstraints`
Usar `currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)` do artifact `org.jetbrains.compose.material3.adaptive:adaptive`.

- **Por quê**: alinha com os breakpoints canônicos do Material3, reage a redimensionamento de janela em tempo real e é a API que o time escolheu ("lib oficial").
- **Alternativa descartada**: `BoxWithConstraints` com threshold em `dp` — zero dependência, mas fora do guideline oficial e com breakpoint "mágico".

### Decisão 2 — Ramificação manual no `HomeChromeHost`, não `NavigationSuiteScaffold`
Manter o `HomeChromeHost` como dono do layout, ramificando internamente entre dois arranjos:
- largo → `Row { NavigationRailBar(header = FAB) ; conteúdo }`
- estreito → `Scaffold(bottomBar = BottomNavigationBar, floatingActionButton = FAB, FabPosition.Center)` (atual)

- **Por quê**: o FAB do app é central na bottom bar e precisa ir para o `header` do rail — algo que o `NavigationSuiteScaffold` não hospeda (ele só cuida do container de navegação, não do FAB nem da animação custom de `HomeChromeConfig`). A ramificação manual dá controle total e reusa o `BottomNavigationBar`, o FAB e o `HomeChromeStateHolder` que já existem.
- **Alternativa descartada**: `material3-adaptive-navigation-suite` / `NavigationSuiteScaffold` — menos código de layout, mas exigiria reescrever a integração do FAB e a lógica de visibilidade para ganhar pouco.

### Decisão 3 — Dois eixos independentes: largura (chrome) × plataforma (features)
- **Largura** decide rail vs bottom bar (`WindowSizeClass`).
- **Plataforma** decide se Support é oferecido (`isDesktop`).

São dimensões ortogonais: uma janela desktop estreita mostra bottom bar **e** oculta Support; uma janela larga no Android mostra rail **e** mantém Support. Nunca misturar os dois gatilhos num só.

### Decisão 4 — `NavigationRailBar` novo em `:core:designsystem`, reusando `BottomNavigationItem`
Criar `NavigationRailBar` como irmão de `BottomNavigationBar`, consumindo a mesma interface `BottomNavigationItem` (icon + labelRes) e o mesmo esquema de cores (`Primary1`). O FAB é passado por um slot `header` para o `HomeChromeHost` posicioná-lo.

- **Por quê**: mantém o design system como dono dos componentes de navegação genéricos, sem que ele nomeie features (respeita a spec `navigation`: `:core:designsystem` não contém `NavigationItem`).

### Decisão 5 — `HomeChromeConfig` vale para ambos os modos
A conjunção de visibilidade (destino ∈ `HomeGraph` **e** `HomeChromeConfig`) governa igualmente rail e bottom bar. `ContentOnly` oculta a navegação e o FAB nos dois arranjos. Nenhuma nova flag de config é necessária.

## Risks / Trade-offs

- **Rail com apenas 2 itens fica esparsa** → aceito no escopo atual; a promoção das quick actions ao rail é um passo futuro explicitamente adiado.
- **Animação de esconder chrome difere entre rail e bottom bar** → a animação atual (`slideInVertically`) é vertical e casa com a bottom bar; para o rail uma transição de fade/slide horizontal é mais natural. Mitigação: reusar `updateTransition` com `AnimatedVisibility` apropriado por modo, sem novo estado.
- **Nova dependência (`material3-adaptive`)** aumenta a superfície do build → mitigação: artifact oficial JetBrains, já parte do ecossistema Compose Multiplatform; adicionar via version catalog.
- **Janela redimensionada cruzando o breakpoint** recompõe a chrome (rail ↔ bottom bar) → comportamento esperado; o `NavController` e a pilha são preservados porque a ramificação é só de layout, não de `NavHost`.
