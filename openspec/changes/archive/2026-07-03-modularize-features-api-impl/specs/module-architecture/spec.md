# Spec: module-architecture

## ADDED Requirements

### Requirement: Estrutura de módulos por feature no padrão api/impl
Cada feature SHALL ser composta por exatamente dois módulos Gradle sob `feature/<nome>/`: `api` (contratos públicos: rotas de navegação, interfaces de repositório, interfaces de use cases públicos, entry point de UI) e `impl` (telas, ViewModels, modais, use cases, implementações de repositório, mappers e módulo Koin da feature). O módulo `api` MUST NOT conter implementações. Um tipo SHALL residir na `api` somente se for consumido por outro módulo.

#### Scenario: Feature completa
- **WHEN** uma feature `<nome>` existe no projeto
- **THEN** existem os módulos `:feature:<nome>:api` e `:feature:<nome>:impl`, e todo tipo referenciado por outro módulo da feature `<nome>` está declarado no módulo `api`

#### Scenario: Tipo usado apenas internamente
- **WHEN** um tipo da feature é consumido apenas dentro do próprio `impl`
- **THEN** o tipo reside no `impl` (não é promovido à `api`)

### Requirement: Regras de dependência entre módulos
As dependências entre módulos SHALL obedecer: (1) `api` não depende de `api` de outra feature; (2) `impl` não depende de `impl` de outra feature; (3) `api` não depende de nenhum `impl`; (4) `impl` pode depender de qualquer `api` e de módulos `:core:*`; módulos `api` só podem depender de `:core:*`. O `:composeApp` é o único módulo autorizado a depender de módulos `impl`.

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

### Requirement: composeApp como shell agregador
O `:composeApp` SHALL conter apenas: composable raiz (`App`), NavHost raiz, dispatcher de navegação, `HomeScreen`/`HomeRoute` (abas), agregação dos módulos Koin, entry points de plataforma (MainActivity, AndroidApp, MainViewController, main.kt) e configuração do framework iOS. Código específico de plataforma no shell MUST residir no source set da respectiva plataforma.

#### Scenario: Nova feature adicionada
- **WHEN** uma nova feature é integrada ao app
- **THEN** o `:composeApp` muda em no máximo três pontos: lista de módulos Koin, registro no NavHost e `export()` da api no framework iOS

### Requirement: Export seletivo no framework iOS
O framework iOS configurado no `:composeApp` SHALL exportar (`export()`) apenas os módulos `:core:*` e `:feature:*:api`. Os módulos `impl` SHALL ser linkados sem export, permanecendo invisíveis ao Swift. A task `:composeApp:embedAndSignAppleFrameworkForXcode` MUST permanecer o alvo de integração do Xcode.

#### Scenario: Build do framework iOS
- **WHEN** o framework é compilado para o Xcode
- **THEN** símbolos de `:core:*` e das apis são visíveis ao Swift, símbolos dos impls não são, e o `iosApp/project.yml` permanece inalterado

### Requirement: Rotas de navegação declaradas por feature
Cada `api` SHALL declarar suas próprias rotas `@Serializable`. A sealed class única `AppRoute` SHALL ser eliminada; o shell conhece apenas as rotas das abas (`HomeRoute`).

#### Scenario: Navegação cross-feature
- **WHEN** o `impl` de uma feature navega para uma tela de outra feature
- **THEN** ele referencia a rota declarada na `api` da feature destino, sem depender do `impl` dela
