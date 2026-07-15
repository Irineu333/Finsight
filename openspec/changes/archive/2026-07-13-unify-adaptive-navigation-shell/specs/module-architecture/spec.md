## MODIFIED Requirements

### Requirement: Shell compartilhado em :app:shared
O `:app:shared` SHALL ser uma KMP library (sem plugin de application) contendo apenas: o composable raiz `App` (tema, contexto de plataforma, `LocalNavController`, `ModalManagerHost` e a invocação do composable de shell de `feature:shell:impl`), o `AppNavHost` raiz, e a agregação dos módulos Koin (`appModules`). Ele MUST NOT conter `Scaffold`, bottom bar, FAB, rotas de navegação nem a enumeração dos destinos do seletor. Ele SHALL ser o único módulo do projeto autorizado a depender de módulos `feature:*:impl`.

#### Scenario: Nova feature adicionada
- **WHEN** uma nova feature é integrada ao app
- **THEN** o `:app:shared` muda em no máximo dois pontos (lista de módulos Koin e registro do grafo no NavHost) e o `:app:ios` em no máximo um (`export()` da api no framework)

#### Scenario: Plataforma consome o shell
- **WHEN** um entry point de plataforma inicializa o app
- **THEN** ele chama `startKoin` com a lista `appModules` exposta por `:app:shared` (Android adiciona `androidContext`) e renderiza `App()`

#### Scenario: Shell sem indireção de navegação
- **WHEN** o `:app:shared` é inspecionado
- **THEN** ele não contém dispatcher, tradutor ou mapa de destinos de navegação — apenas a composição dos grafos providos pelas features

#### Scenario: Shell sem UI de feature
- **WHEN** o `:app:shared` é inspecionado
- **THEN** ele não declara nenhuma rota, nenhum catálogo de destinos e nenhum componente de chrome; a shell de navegação é a feature `feature:shell`, consumida via `feature:shell:api` e `feature:shell:impl`

### Requirement: Rotas de navegação declaradas por feature
Cada feature SHALL declarar suas próprias rotas `@Serializable`. A sealed class única `AppRoute` SHALL ser eliminada. As rotas *externamente navegáveis* de uma feature SHALL residir na sua `api`; as rotas alcançáveis apenas de dentro do próprio `impl` SHALL residir no `impl`. O `:app:shared` MUST NOT declarar rotas: ele referencia `DashboardGraph` a partir de `feature:dashboard:api` apenas como `startDestination` do `NavHost`.

#### Scenario: Navegação cross-feature
- **WHEN** o `impl` de uma feature navega para uma tela de outra feature
- **THEN** ele referencia a rota declarada na `api` da feature destino, sem depender do `impl` dela

#### Scenario: Rota interna promovida indevidamente
- **WHEN** uma rota declarada em um módulo `api` não é referenciada por nenhum outro módulo
- **THEN** ela é movida para o `impl` da feature dona, seguindo a regra de que um tipo só reside na `api` se for consumido por outro módulo

#### Scenario: Rota conhecida pelo shell
- **WHEN** o `AppNavHost` declara seu `startDestination`
- **THEN** ele importa `DashboardGraph` de `feature:dashboard:api`, e nenhuma rota é declarada dentro do `:app:shared`

### Requirement: Export seletivo no framework iOS
O framework iOS `ComposeApp` SHALL ser configurado no `:app:ios` e SHALL exportar (`export()`) apenas os módulos `:core:*` e `:feature:*:api`, declarados como dependências `api` do módulo — incluindo `:feature:shell:api` e `:feature:dashboard:api`. Os módulos `impl` SHALL ser linkados via `:app:shared` sem export, permanecendo invisíveis ao Swift. O `iosApp/project.yml` SHALL apontar para `:app:ios:embedAndSignAppleFrameworkForXcode`, e `baseName`/`bundleId` do framework MUST permanecer `ComposeApp`/`com.neoutils.finsight.ComposeApp`.

#### Scenario: Build do framework iOS
- **WHEN** o framework é compilado para o Xcode
- **THEN** símbolos de `:core:*` e das apis são visíveis ao Swift, símbolos dos impls não são, e o código Swift do `iosApp` permanece inalterado

#### Scenario: Nova api de feature criada
- **WHEN** um novo módulo `feature:*:api` passa a existir
- **THEN** ele é adicionado ao `export()` do `:app:ios`, sob pena de o símbolo ficar invisível ao linkar no Xcode

### Requirement: Módulos Koin providos pelos cores
Todo módulo `:core:*` que provê tipos injetáveis SHALL expor seu próprio módulo Koin (com `expect val <nome>PlatformModule` quando houver binding por plataforma), no padrão já estabelecido por `core:analytics`, `core:auth` e `core:crashlytics`. Em particular: `databaseModule` SHALL residir em `:core:database`; os bindings de `Settings`, `CurrencyFormatter` e `DebounceManager` SHALL residir em `:core:common`; o binding de `ModalManager` SHALL residir em `:core:designsystem`. O shell MUST NOT declarar bindings próprios além do binding do catálogo de destinos (`NavCatalog`), provido por `feature:shell:impl`. Um entry point resolvido fora de escopo `@Composable` (ex.: dentro de um `NavGraphBuilder`) SHALL ser obtido via `KoinPlatform.getKoin()`, e seu binding SHALL estar registrado no módulo Koin da feature que o implementa.

#### Scenario: Binding de infraestrutura com variação por plataforma
- **WHEN** um core precisa de binding específico por plataforma (ex.: builder do banco com `Context` no Android)
- **THEN** o core declara `expect val <nome>PlatformModule` com `actual` em cada source set de plataforma, e o módulo comum o inclui via `includes()`

#### Scenario: Shell agrega e provê apenas o catálogo
- **WHEN** o `:app:shared` é inspecionado
- **THEN** sua contribuição Koin é a lista de agregação dos módulos dos cores e das features, sem `single`/`factory` próprios; o único binding específico da navegação (`NavCatalog`) é provido por `feature:shell:impl`, não pelo `:app:shared`

#### Scenario: Entry point resolvido na construção do grafo
- **WHEN** uma função de grafo precisa de um entry point de outra feature dentro do lambda `NavGraphBuilder`, que não é `@Composable`
- **THEN** ele resolve o entry via `KoinPlatform.getKoin()`, e a ausência do binding falha na primeira composição do `NavHost` com erro explícito do Koin
