## Why

Hoje a chrome do Home é fixa em todas as plataformas: `Scaffold` com bottom bar e FAB central, e o conteúdo ocupa a largura toda. No desktop (e em janelas largas) isso desperdiça o espaço horizontal e coloca a navegação num lugar pouco ergonômico para telas grandes. Além disso, a feature Support é `mobile-only` (o `jvmMain` usa `UnsupportedSupportRepository`), mas ainda aparece parcialmente no desktop. Queremos que o layout se adapte à largura da janela e que features mobile-only não sejam oferecidas onde não funcionam.

## What Changes

- A chrome do Home passa a ser **adaptativa por largura de janela** (via API oficial `currentWindowAdaptiveInfo().windowSizeClass` do Material3 Adaptive):
  - **Janela larga (≥ Medium, 600dp)**: `NavigationRail` na lateral esquerda, com o FAB no slot `header` do rail; conteúdo à direita.
  - **Janela estreita (< Medium)**: mantém o `Scaffold` com bottom bar e FAB central de hoje.
- O eixo de largura (rail vs bottom bar) é **independente** do eixo de plataforma (Support existe?). Uma janela desktop estreita ainda mostra bottom bar; uma janela larga em qualquer plataforma mostra rail.
- A semântica de visibilidade da chrome (`HomeChromeConfig` / `HomeChromeEffect`) vale para os dois modos: uma tela que publica `ContentOnly` oculta rail **ou** bottom bar e o FAB.
- Novo componente `NavigationRailBar` em `:core:designsystem`, irmão do `BottomNavigationBar`, reutilizando a interface `BottomNavigationItem`.
- Features mobile-only (Support) são **ocultadas no desktop** de forma consistente: além do grid de quick actions (já gated), o botão de Support no `TopAppBar` do Dashboard também passa a respeitar `isDesktop`.
- Adição do artifact oficial `org.jetbrains.compose.material3.adaptive:adaptive` ao catálogo de dependências.

Sem breaking changes: `NavigationItem` continua com Dashboard + Transactions, rotas, grafos, Koin e `AppNavHost` permanecem intocados.

## Capabilities

### New Capabilities
- `platform-adaptive-features`: features `mobile-only` (ex.: Support) SHALL ser ocultadas nas plataformas onde não são suportadas (desktop), de forma consistente em todos os pontos de entrada (quick actions e top bar).

### Modified Capabilities
- `navigation`: a requirement "Chrome do Home derivada do destino e da tela" passa a definir a chrome como **adaptativa por largura de janela** — `NavigationRail` (FAB no header) em janelas largas e bottom bar + FAB central em janelas estreitas —, mantendo a mesma semântica de visibilidade via `HomeChromeConfig`.

## Impact

- **Dependências**: novo `org.jetbrains.compose.material3.adaptive:adaptive` no version catalog; aplicado em `:core:designsystem` (rail) e/ou `feature:home:impl` (medição de largura).
- **Código**:
  - `feature/home/impl` — `HomeChromeHost`: ramifica entre rail e bottom bar pela largura.
  - `core/designsystem` — novo `NavigationRailBar`.
  - `feature/dashboard/impl` — `DashboardScreen`: gate `isDesktop` no botão de Support do `TopAppBar`.
- **Intocados**: `:app:shared` (`App`, `AppNavHost`), rotas, grafos, `NavigationItem`, Koin.
