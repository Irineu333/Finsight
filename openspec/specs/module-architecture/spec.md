# module-architecture Specification

## Purpose
TBD - created by archiving change modularize-features-api-impl. Update Purpose after archive.
## Requirements
### Requirement: Estrutura de módulos por feature no padrão api/impl
Cada feature SHALL ser composta por exatamente dois módulos Gradle sob `feature/<nome>/`: `api` (contratos públicos: rotas de navegação, interfaces de repositório, interfaces de use cases públicos, entry point de UI) e `impl` (telas, ViewModels, modais, use cases, implementações de repositório, mappers e módulo Koin da feature). O módulo `api` MUST NOT conter implementações. Um tipo SHALL residir na `api` somente se for consumido por outro módulo. Uma feature cujo `impl` ainda não tem consumidor externo MAY existir sem módulo `api`; o módulo `api` SHALL ser criado assim que o primeiro tipo seu passar a ser consumido por outro módulo.

#### Scenario: Feature completa
- **WHEN** uma feature `<nome>` existe no projeto
- **THEN** existem os módulos `:feature:<nome>:api` e `:feature:<nome>:impl`, e todo tipo referenciado por outro módulo da feature `<nome>` está declarado no módulo `api`

#### Scenario: Tipo usado apenas internamente
- **WHEN** um tipo da feature é consumido apenas dentro do próprio `impl`
- **THEN** o tipo reside no `impl` (não é promovido à `api`)

#### Scenario: Contrato com implementação na api
- **WHEN** um contrato de UI compartilhado precisa de um holder de estado (ex.: `HomeChromeStateHolder` para `HomeChromeController`)
- **THEN** a interface, o `CompositionLocal` e o efeito residem na `api`, e o holder que os implementa reside no `impl`

### Requirement: Regras de dependência entre módulos
As dependências entre módulos SHALL obedecer: (1) `api` não depende de `api` de outra feature; (2) `impl` não depende de `impl` de outra feature; (3) `api` não depende de nenhum `impl`; (4) `impl` pode depender de qualquer `api` e de módulos `:core:*`; módulos `api` só podem depender de `:core:*`. O `:app:shared` é o único módulo autorizado a depender de módulos `impl`.

#### Scenario: Dependência cruzada entre impls de features distintas
- **WHEN** `transactions:impl` precisa de comportamento de creditcards e `creditcards:impl` precisa de comportamento de transactions
- **THEN** cada `impl` depende apenas da `api` da outra feature, e o grafo de módulos permanece sem ciclos

#### Scenario: Violação de regra de dependência
- **WHEN** um módulo declara uma dependência proibida (api→api, impl→impl ou api→impl)
- **THEN** o build falha na verificação de regras antes da compilação ser considerada válida

### Requirement: Domínio compartilhado em core
Os modelos de domínio e os tipos de erro SHALL residir em `:core:model`, não nas apis das features. As assinaturas públicas de apis e entry points SHALL referenciar apenas tipos de `:core:*`.

#### Scenario: Modelo emaranhado usado por várias apis
- **WHEN** duas ou mais apis precisam referenciar `Transaction` (que embute `Account`, `CreditCard`, `Invoice`, `Category`)
- **THEN** ambas referenciam o tipo de `:core:model`, sem dependência entre as apis

### Requirement: Banco de dados centralizado em core
As entities, DAOs, `AppDatabase` e converters do Room SHALL residir em `:core:database`. As implementações de repositório e seus mappers SHALL residir no `impl` da feature dona, consumindo os DAOs de `:core:database`.

#### Scenario: Feature acessa persistência
- **WHEN** o `impl` de uma feature implementa um repositório declarado na sua `api`
- **THEN** a implementação consome DAOs de `:core:database` e nenhuma entity Room aparece em assinaturas da `api`

### Requirement: Módulos de app por plataforma
Os entry points de aplicação SHALL residir em módulos dedicados sob `app/`: `:app:android` (`com.android.application` puro, não-KMP: `MainActivity`, `AndroidApp` com `startKoin`, Manifest, mipmaps, signing, google-services, crashlytics, `versionCode`/`versionName`), `:app:desktop` (`kotlin("jvm")`: `main.kt` e empacotamento `compose.desktop`/`nativeDistributions`) e `:app:ios` (KMP só-iOS: `MainViewController` e o framework `ComposeApp`). Cada módulo de plataforma SHALL depender de `:app:shared` e MUST NOT conter lógica de UI ou navegação comum.

#### Scenario: Build Android sem contaminação de plataforma
- **WHEN** o app Android é compilado
- **THEN** os plugins `com.android.application`, `googleServices` e `firebaseCrashlytics` são aplicados apenas em `:app:android`, e nenhum módulo KMP do projeto aplica plugin de application

#### Scenario: Entry point contém apenas bootstrap
- **WHEN** um módulo `:app:<plataforma>` é inspecionado
- **THEN** ele contém somente inicialização (startKoin, janela/activity/controller) e configuração de empacotamento da plataforma, delegando toda a UI a `:app:shared`

### Requirement: Shell compartilhado em :app:shared
O `:app:shared` SHALL ser uma KMP library (sem plugin de application) contendo apenas: o composable raiz `App` (tema, contexto de plataforma, `LocalNavController`, `ModalManagerHost` e a invocação de `HomeChromeHost`), o `AppNavHost` raiz, e a agregação dos módulos Koin (`appModules`). Ele MUST NOT conter `Scaffold`, bottom bar, FAB, rotas de navegação nem a enumeração das abas. Ele SHALL ser o único módulo do projeto autorizado a depender de módulos `feature:*:impl`.

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
- **THEN** ele não declara nenhuma rota, nenhum `NavigationItem` e nenhum componente de chrome; o Home é uma feature como qualquer outra, consumida via `feature:home:api` e `feature:home:impl`

### Requirement: Módulos Koin providos pelos cores
Todo módulo `:core:*` que provê tipos injetáveis SHALL expor seu próprio módulo Koin (com `expect val <nome>PlatformModule` quando houver binding por plataforma), no padrão já estabelecido por `core:analytics`, `core:auth` e `core:crashlytics`. Em particular: `databaseModule` SHALL residir em `:core:database`; os bindings de `Settings`, `CurrencyFormatter` e `DebounceManager` SHALL residir em `:core:common`; o binding de `ModalManager` SHALL residir em `:core:designsystem`. O shell MUST NOT declarar bindings próprios — apenas agregar módulos. Um entry point resolvido fora de escopo `@Composable` (ex.: dentro de um `NavGraphBuilder`) SHALL ser obtido via `KoinPlatform.getKoin()`, e seu binding SHALL estar registrado no módulo Koin da feature que o implementa.

#### Scenario: Binding de infraestrutura com variação por plataforma
- **WHEN** um core precisa de binding específico por plataforma (ex.: builder do banco com `Context` no Android)
- **THEN** o core declara `expect val <nome>PlatformModule` com `actual` em cada source set de plataforma, e o módulo comum o inclui via `includes()`

#### Scenario: Shell sem bindings
- **WHEN** o `:app:shared` é inspecionado
- **THEN** sua contribuição Koin é exclusivamente a lista de agregação dos módulos dos cores e das features, sem `single`/`factory` próprios

#### Scenario: Entry point resolvido na construção do grafo
- **WHEN** `homeGraph()` precisa de `DashboardEntry` dentro do lambda `NavGraphBuilder`, que não é `@Composable`
- **THEN** ele resolve o entry via `KoinPlatform.getKoin()`, e a ausência do binding falha na primeira composição do `NavHost` com erro explícito do Koin

### Requirement: Export seletivo no framework iOS
O framework iOS `ComposeApp` SHALL ser configurado no `:app:ios` e SHALL exportar (`export()`) apenas os módulos `:core:*` e `:feature:*:api`, declarados como dependências `api` do módulo — incluindo `:feature:home:api` e `:feature:dashboard:api`. Os módulos `impl` SHALL ser linkados via `:app:shared` sem export, permanecendo invisíveis ao Swift. O `iosApp/project.yml` SHALL apontar para `:app:ios:embedAndSignAppleFrameworkForXcode`, e `baseName`/`bundleId` do framework MUST permanecer `ComposeApp`/`com.neoutils.finsight.ComposeApp`.

#### Scenario: Build do framework iOS
- **WHEN** o framework é compilado para o Xcode
- **THEN** símbolos de `:core:*` e das apis são visíveis ao Swift, símbolos dos impls não são, e o código Swift do `iosApp` permanece inalterado

#### Scenario: Nova api de feature criada
- **WHEN** um novo módulo `feature:*:api` passa a existir
- **THEN** ele é adicionado ao `export()` do `:app:ios`, sob pena de o símbolo ficar invisível ao linkar no Xcode

### Requirement: Rotas de navegação declaradas por feature
Cada feature SHALL declarar suas próprias rotas `@Serializable`. A sealed class única `AppRoute` SHALL ser eliminada. As rotas *externamente navegáveis* de uma feature SHALL residir na sua `api`; as rotas alcançáveis apenas de dentro do próprio `impl` SHALL residir no `impl`. O `:app:shared` MUST NOT declarar rotas: ele referencia `HomeGraph` a partir de `feature:home:api` apenas como `startDestination` do `NavHost`.

#### Scenario: Navegação cross-feature
- **WHEN** o `impl` de uma feature navega para uma tela de outra feature
- **THEN** ele referencia a rota declarada na `api` da feature destino, sem depender do `impl` dela

#### Scenario: Rota interna promovida indevidamente
- **WHEN** uma rota declarada em um módulo `api` não é referenciada por nenhum outro módulo
- **THEN** ela é movida para o `impl` da feature dona, seguindo a regra de que um tipo só reside na `api` se for consumido por outro módulo

#### Scenario: Rota conhecida pelo shell
- **WHEN** o `AppNavHost` declara seu `startDestination`
- **THEN** ele importa `HomeGraph` de `feature:home:api`, e nenhuma rota é declarada dentro do `:app:shared`

