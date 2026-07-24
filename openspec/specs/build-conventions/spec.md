# build-conventions Specification

## Purpose
TBD - created by archiving change modularize-features-api-impl. Update Purpose after archive.
## Requirements
### Requirement: Convention plugins em build-logic
O projeto SHALL ter um build incluído `build-logic` com convention plugins que concentram toda a configuração comum: `kmp.library` (targets KMP: Android, iOS, Desktop), `compose.library` (Compose Multiplatform), `room.library` (targets KMP mais Room, KSP por target e diretório de schema) , `feature.api` e `feature.impl`. O `build.gradle.kts` de um módulo SHALL conter apenas a aplicação da convenção e suas dependências específicas.

A convenção `room.library` SHALL ser aplicada por todo módulo que declare entities ou DAOs do Room, e MUST NOT trazer Compose — os módulos que a aplicam são de domínio e dados, sem UI. A configuração de KSP por target SHALL residir exclusivamente nela, e MUST NOT ser repetida no `build.gradle.kts` de nenhum módulo.

#### Scenario: Criação de novo módulo de feature
- **WHEN** um novo módulo `:feature:<nome>:api` ou `:feature:<nome>:impl` é criado
- **THEN** seu `build.gradle.kts` aplica a convenção correspondente e não repete configuração de targets, compiler options ou Compose

#### Scenario: Módulo com Room não repete configuração de KSP
- **WHEN** um módulo que declara entities ou DAOs do Room é criado ou alterado
- **THEN** ele aplica `room.library`, e seu `build.gradle.kts` não contém plugins de Room/KSP, bloco de schema nem linhas de `ksp<Target>`

#### Scenario: Convenção de Room não traz Compose
- **WHEN** um módulo aplica `room.library`
- **THEN** Compose não é adicionado ao módulo

### Requirement: Verificação mecânica das regras de dependência
As convenções `feature.api` e `feature.impl` SHALL verificar as regras de dependência do module-architecture durante o build: `feature.api` só admite dependências de projeto `:core:*`; `feature.impl` admite `:core:*` e `:feature:*:api`. Violações SHALL falhar o build com mensagem indicando o módulo e a dependência proibida.

#### Scenario: api declara dependência de outra api
- **WHEN** `:feature:transactions:api` declara dependência de `:feature:accounts:api`
- **THEN** o build falha indicando a regra violada (api não depende de api)

#### Scenario: impl declara dependência de outro impl
- **WHEN** `:feature:dashboard:impl` declara dependência de `:feature:creditcards:impl`
- **THEN** o build falha indicando a regra violada (impl não depende de impl)

### Requirement: Convention plugin do shell de app
O `build-logic` SHALL prover a convenção `finsight.app.shared`, aplicada exclusivamente pelo `:app:shared`, concentrando: targets KMP (Android library, JVM, iOS), Compose Multiplatform, serialization e opt-ins comuns. A convenção SHALL ser a única que admite dependências de módulos `feature:*:impl`; o `build.gradle.kts` do `:app:shared` SHALL conter apenas a aplicação da convenção e suas dependências.

#### Scenario: Shell sob convenção
- **WHEN** o `:app:shared` é configurado
- **THEN** seu `build.gradle.kts` aplica `finsight.app.shared` e não repete configuração de targets, compiler options ou Compose

#### Scenario: Módulo comum tenta depender de impl
- **WHEN** um módulo sob convenção de library ou de feature declara dependência de um `feature:*:impl`
- **THEN** o build falha na verificação de regras, pois apenas a convenção `finsight.app.shared` admite dependências de `impl`

### Requirement: Build explícito nos módulos de plataforma
Os módulos `:app:android`, `:app:desktop` e `:app:ios` SHALL manter `build.gradle.kts` explícito, sem convention plugin — signing, empacotamento desktop e framework iOS são configurações únicas, sem segundo consumidor que justifique convenção. Configuração comum acidentalmente duplicada entre eles SHOULD ser movida para o `build-logic` apenas quando um segundo consumidor real existir.

#### Scenario: Configuração única de plataforma
- **WHEN** uma configuração existe em exatamente um módulo de plataforma (ex.: signing config, `nativeDistributions`, `export()` do framework)
- **THEN** ela reside no `build.gradle.kts` desse módulo, sem abstração no `build-logic`

### Requirement: Código de plataforma restrito por padrão
Os módulos de feature SHALL ser `commonMain` puro por padrão. Source sets de plataforma em um `impl` são exceção justificada (ex.: `report:impl` com serviços nativos de print/share) e MUST NOT existir em módulos `api`.

#### Scenario: Feature comum
- **WHEN** uma feature não possui requisito nativo
- **THEN** seus módulos contêm apenas `commonMain` (e `commonTest`), sem source sets de plataforma

