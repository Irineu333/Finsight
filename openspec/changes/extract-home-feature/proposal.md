## Why

O Home não é um detalhe do shell — é uma feature com rota, chrome, abas e uma ação primária. Hoje ele está dissolvido no `:app:shared` (`HomeGraph`, `NavigationItem`, o `Scaffold` do `App()`), e seu contrato de chrome (`HomeChromeConfig`/`HomeChromeEffect`) foi parar em `:core:ui`, fazendo um módulo core conhecer o conceito "home" — uma inversão de camada.

O shell só consegue hospedar o Home porque é o único módulo que enxerga os `impl`: ele aninha `dashboardGraph()`/`transactionsGraph()` e abre o `AddTransactionModal` de `transactions:impl` no FAB. Extrair o Home força esses dois acoplamentos a se tornarem contratos explícitos, e devolve ao `:app:shared` seu papel declarado: agregar, não implementar.

## What Changes

- **Novos módulos `feature/home/api` e `feature/home/impl`.** A `api` declara `HomeGraph` e o contrato de chrome; o `impl` hospeda `NavigationItem`, o `Scaffold` da chrome, o FAB e `NavGraphBuilder.homeGraph()`.
- **`HomeChromeConfig`/`HomeChromeController`/`HomeChromeEffect`/`LocalHomeChromeController` migram de `:core:ui` para `feature/home/api`.** `:core:ui` deixa de nomear uma feature. `dashboard:impl` passa a importá-los de `home:api`.
- **Novo módulo `feature/dashboard/api`**, hospedando `DashboardRoute` e `DashboardEntry`. Hoje o dashboard é `impl`-only porque nenhum módulo o navegava; com o Home fora do shell, `home:impl` passa a navegar até `DashboardRoute` pela bottom bar.
- **`Entry` ganha registro de grafo.** `DashboardEntry` e `TransactionsEntry` expõem `context(builder: NavGraphBuilder) fun register()`, permitindo que `home:impl` monte o subgrafo de abas sem depender de nenhum `impl`. Este é o terceiro tipo de acesso cross-feature à UI, ao lado de rota e modal.
- **`TransactionsEntry` ganha `addTransactionModal(): Modal`.** O FAB do Home deixa de instanciar `AddTransactionModal` de `transactions:impl`.
- **O `Scaffold` da chrome sai de `App()` e vira `HomeChromeHost()` em `home:impl`**, invocado pelo shell envolvendo o `AppNavHost`. Mantém-se **por fora** do `NavHost`: a posição na árvore de composição não muda.
- **`:app:shared` encolhe** para `App()` (tema, contexto de plataforma, `ModalManagerHost`, `LocalNavController`), `AppNavHost()` (só chamadas a `<nome>Graph()`) e `appModules`. Perde `HomeGraph`, `NavigationItem` e a lógica de chrome.
- **Sem mudança de comportamento observável.** `NavHost` único preservado, `Scaffold` por fora do `NavHost` preservado, `popUpTo(DashboardRoute) { inclusive = false }` + `launchSingleTop` preservado. Nenhum NavHost aninhado é reintroduzido.

### Relação com `2026-07-10-refactor-navigation`

Esta mudança **revisa a decisão 4** daquele change, que rejeitou criar `feature:dashboard:api` com o argumento de que seria "um módulo inteiro para um `data object` que ninguém importa". A premissa era verdadeira e deixa de ser: `home:impl` importa `DashboardRoute`. A regra que a sustentava — um tipo só sobe para a `api` quando outro módulo o consome — é justamente a que agora exige o módulo.

As decisões 5 (`NavHost` único, `HomeScreen` dissolvido, sem NavHost aninhado) e 6 (`HomeChrome` sobrevive como conjunção destino ∧ config) são **preservadas**. O `HomeScreen` com `NavHost` interno, removido em `cd55639d`, **não** volta: ele tornava as abas inalcançáveis pelo controller raiz e nunca preservou estado de aba.

## Capabilities

### New Capabilities

Nenhuma. O comportamento do Home já é normatizado pelas capabilities `navigation` e `module-architecture`; esta mudança realoca a propriedade dos requisitos existentes.

### Modified Capabilities

- `module-architecture`: `:app:shared` deixa de conter o `Scaffold` da chrome, `HomeGraph` e `NavigationItem`, e deixa de ser o único módulo autorizado a enumerar features — passa a ser o único autorizado a depender de `impl`s. A enumeração das abas passa a `home:impl`. Uma feature ganha módulo `api` quando outro módulo consome um tipo seu, incluindo quando esse módulo é outra feature (`home:impl` → `dashboard:api`).
- `navigation`: o `Scaffold` da chrome reside em `feature:home:impl`, não em `App()`. O subgrafo de abas é montado por `NavGraphBuilder.homeGraph()`, que registra as abas via entry points, e não pelo shell. `HomeGraph` e os tipos de `HomeChrome` residem em `feature:home:api`. A restrição "nenhum módulo `:core:*` enumera features" é mantida e reforçada: `:core:ui` deixa de conhecer `HomeChrome`.
- `feature-entry-points`: o entry point de uma feature MAY expor o registro do seu subgrafo de navegação (`context(builder: NavGraphBuilder) fun register()`), permitindo que outra feature componha o grafo da feature de destino sem enxergar seu `impl`. Passa a ser o quarto tipo de acesso cross-feature à UI.

## Impact

**Módulos criados:** `feature/home/api`, `feature/home/impl`, `feature/dashboard/api` (+ `settings.gradle.kts`, `build.gradle.kts` de cada, `appModules`, `export()` do `:app:ios`).

**Código movido:**
- `app/shared/.../ui/screen/home/HomeGraph.kt` → `feature/home/api`
- `app/shared/.../ui/screen/home/NavigationItem.kt` → `feature/home/impl`
- `app/shared/.../ui/screen/root/App.kt` (`AppScaffold`) → `feature/home/impl` (`HomeChromeHost`)
- `app/shared/.../ui/screen/root/{App,AppNavHost}.kt` → `app/shared/.../ui/` (o pacote `screen/root` deixa de existir: o shell não tem mais telas)
- `core/ui/.../ui/screen/home/HomeChrome.kt` → `feature/home/api`
- `feature/dashboard/impl/.../DashboardRoute.kt` → `feature/dashboard/api`

**APIs alteradas:** `TransactionsEntry` (+`addTransactionModal`, +`register`), `DashboardEntry` (novo).

**Consumidores afetados:** `dashboard:impl` (importa `HomeChrome` de `home:api`), `transactions:impl` (implementa os novos métodos do entry), `:app:shared`, `:app:ios`.

**Dependências:** `feature:home:api` e `feature:dashboard:api` passam a depender de `androidx.navigation` (dependência de biblioteca, não de projeto — admitida pela convenção `feature.api`, precedente em `transactions:api`).

**Riscos:** o `NavGraphBuilder` lambda não é `@Composable`, então `homeGraph()` resolve os entry points via `KoinPlatform.getKoin()` em vez de `koinInject()` — troca de uma garantia de compilação (o grafo existe) por uma de runtime. Detalhado em `design.md`.

**Sem impacto:** banco de dados, estado persistido, comportamento observável pelo usuário. Rollback é `git revert`.
