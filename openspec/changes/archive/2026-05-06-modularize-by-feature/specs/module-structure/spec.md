## ADDED Requirements

### Requirement: Módulos core existem com responsabilidades definidas
O projeto SHALL ter os seguintes módulos core, cada um com responsabilidade única e sem lógica de negócio:
- `:core:platform` — tipos e utilitários de detecção/acesso à plataforma (expect/actual)
- `:core:ui` — sistema de design, componentes shared, ModalManager, NavigationDispatcher
- `:core:utils` — extensões puras Kotlin sem Compose
- `:core:analytics` — interfaces de observabilidade (Analytics, Crashlytics, Event)
- `:core:auth` — interface AuthService
- `:core:database` — infraestrutura Room (AppDatabase, entities, DAOs, mappers)

#### Scenario: core:platform não contém lógica de negócio
- **WHEN** um desenvolvedor adiciona código ao módulo `:core:platform`
- **THEN** o código MUST ser exclusivamente sobre detecção ou acesso à plataforma (Platform, isDesktop, PlatformContext) e não sobre features do produto

#### Scenario: core:utils não tem dependência em Compose
- **WHEN** o módulo `:core:utils` é compilado
- **THEN** ele MUST compilar sem dependência em `compose.runtime` ou qualquer API Compose

#### Scenario: core:database não contém regras de negócio
- **WHEN** um desenvolvedor adiciona código ao módulo `:core:database`
- **THEN** o código MUST ser exclusivamente infraestrutura Room (entities, DAOs, AppDatabase) sem mappers, use cases ou validações de domínio — mappers pertencem a cada `feature:X:impl`

---

### Requirement: Cada feature tem módulos :api e :impl separados
Cada feature do produto SHALL ter dois módulos Gradle: `:feature:X:api` e `:feature:X:impl`. Features terminais (dashboard, home, support) SHALL ter apenas `:impl`.

#### Scenario: Feature com dependência cruzada usa apenas :api
- **WHEN** `:feature:installments:impl` precisa criar uma transação
- **THEN** ele MUST depender de `IBuildTransactionUseCase` de `:feature:transactions:api` e MUST NOT importar nada de `:feature:transactions:impl`

#### Scenario: Feature terminal não tem :api
- **WHEN** o grafo de módulos é verificado
- **THEN** `:feature:dashboard`, `:feature:home` e `:feature:support` MUST NOT ter um módulo `:api` correspondente

---

### Requirement: Nenhum :impl depende de outro :impl
A regra de dependência SHALL ser: `:feature:X:impl` pode depender de `:feature:Y:api` mas MUST NOT depender de `:feature:Y:impl`.

#### Scenario: Dependência impl-to-impl falha em compile time
- **WHEN** um desenvolvedor adiciona `implementation(projects.feature.transactions.impl)` em `:feature:installments:impl`
- **THEN** o build MUST falhar com erro de dependência não permitida (a ausência dessa declaração no `build.gradle.kts` garante o isolamento)

#### Scenario: Dependência via :api compila corretamente
- **WHEN** `:feature:installments:impl` declara `implementation(projects.feature.transactions.api)`
- **THEN** o build MUST compilar e `IBuildTransactionUseCase` MUST ser acessível

---

### Requirement: Grafo de dependências entre módulos é acíclico
O grafo de dependências entre módulos SHALL ser um DAG (directed acyclic graph) sem ciclos.

#### Scenario: recurring:api não depende de transactions:api
- **WHEN** o grafo de módulos é verificado
- **THEN** `:feature:recurring:api` MUST NOT ter dependência direta ou transitiva em `:feature:transactions:api`

#### Scenario: transactions:api não depende de recurring:api
- **WHEN** o grafo de módulos é verificado
- **THEN** `:feature:transactions:api` MUST NOT ter dependência direta ou transitiva em `:feature:recurring:api`

---

### Requirement: :app é o único módulo que depende de todos os :impl
O módulo `:app` SHALL depender de todos os `:feature:X:impl` para wiring de DI e navegação. Nenhum outro módulo SHALL depender de múltiplos `:impl`.

#### Scenario: Koin é inicializado em :app
- **WHEN** a aplicação inicia
- **THEN** `startKoin` MUST ser chamado em `:app` agregando os módulos Koin de todos os `:feature:X:impl`

#### Scenario: NavHost vive em :app
- **WHEN** a navegação é configurada
- **THEN** o `AppNavHost` MUST viver em `:app` e referenciar screens de cada `:feature:X:impl`

---

### Requirement: Domínio de cada feature vive em seu :api
Modelos de domínio, interfaces de repositório e interfaces de use cases cross-feature de uma feature SHALL viver no módulo `:feature:X:api` dessa feature, não em `:core:domain`.

#### Scenario: Account model está em accounts:api
- **WHEN** outro módulo precisa do modelo `Account`
- **THEN** ele MUST declarar dependência em `:feature:accounts:api` e o modelo `Account` MUST estar nesse módulo

#### Scenario: Não existe módulo :core:domain
- **WHEN** o grafo de módulos é verificado
- **THEN** MUST NOT existir nenhum módulo chamado `:core:domain` no projeto

---

### Requirement: Use cases cross-feature expõem interface em :api
Quando um use case de uma feature é necessário em outra feature, SHALL existir uma interface no `:api` da feature dona. A implementação fica em `:impl`.

#### Scenario: IBuildTransactionUseCase está em transactions:api
- **WHEN** `:feature:installments:impl` e `:feature:recurring:impl` precisam criar transações
- **THEN** `IBuildTransactionUseCase` MUST estar em `:feature:transactions:api` e `BuildTransactionUseCase` MUST estar em `:feature:transactions:impl`

#### Scenario: Koin resolve interface para implementação
- **WHEN** `AddInstallmentUseCase` é instanciado via Koin
- **THEN** `IBuildTransactionUseCase` MUST ser resolvido para `BuildTransactionUseCase` registrado pelo módulo Koin de `:feature:transactions:impl`

---

### Requirement: Category e Budget usam iconKey: String no domínio
Os modelos `Category` e `Budget` SHALL ter `iconKey: String` em vez de `CategoryLazyIcon`. A construção de `CategoryLazyIcon` SHALL ocorrer exclusivamente na camada de UI.

#### Scenario: Category não importa tipos Compose
- **WHEN** o módulo `:feature:categories:api` é compilado
- **THEN** a data class `Category` MUST NOT importar nenhum tipo de `androidx.compose` ou `com.neoutils.finsight.ui`

#### Scenario: UI constrói CategoryLazyIcon a partir de iconKey
- **WHEN** uma tela exibe o ícone de uma categoria
- **THEN** ela MUST construir `CategoryLazyIcon(category.iconKey)` localmente, sem receber o ícone pronto do modelo

---

### Requirement: Recurring.Type é enum próprio independente de Transaction.Type
`Recurring` SHALL usar `Recurring.Type { INCOME, EXPENSE }` como enum próprio. A conversão para `Transaction.Type` SHALL ocorrer apenas no mapper de `:core:database`.

#### Scenario: Recurring.Type tem apenas INCOME e EXPENSE
- **WHEN** o enum `Recurring.Type` é definido
- **THEN** ele MUST ter exatamente dois valores: `INCOME` e `EXPENSE`, sem `ADJUSTMENT`

#### Scenario: Mapper converte Recurring.Type para Transaction.Type
- **WHEN** `RecurringMapper.toDomain()` converte uma entidade
- **THEN** `RecurringEntity.Type.EXPENSE` MUST mapear para `Recurring.Type.EXPENSE` e `RecurringEntity.Type.INCOME` MUST mapear para `Recurring.Type.INCOME`