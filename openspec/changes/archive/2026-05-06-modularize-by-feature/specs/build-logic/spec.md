## ADDED Requirements

### Requirement: Convention plugins eliminam repetição de configuração KMP
O projeto SHALL ter um diretório `build-logic/` com convention plugins Kotlin DSL que encapsulam a configuração KMP repetida. Nenhum `build.gradle.kts` de módulo SHALL repetir targets KMP ou source sets manualmente.

#### Scenario: Novo módulo :api usa kmp-library sem repetir targets
- **WHEN** um novo módulo `:feature:X:api` é criado
- **THEN** seu `build.gradle.kts` MUST conter apenas `plugins { id("kmp-library") }` e suas dependências específicas, sem declarar `androidTarget()`, `iosX64()`, `jvm()` etc.

#### Scenario: core:database configura KSP manualmente
- **WHEN** o módulo `:core:database` é compilado
- **THEN** as 6 configurações de KSP Room (`kspCommonMainMetadata`, `kspAndroid`, `kspJvm`, `kspIosX64`, `kspIosArm64`, `kspIosSimulatorArm64`) MUST estar declaradas manualmente no `core/database/build.gradle.kts`

---

### Requirement: Três plugins cobrem todos os tipos de módulo
O `build-logic/` SHALL fornecer três plugins:
- `kmp-library` — para módulos `:core:*` e `:feature:X:api` (KMP library sem Compose/Koin/Arrow)
- `kmp-compose` — intermediário, aplica `kmp-library` + Compose (usado por `kmp-feature`)
- `kmp-feature` — para módulos `:feature:X:impl` (aplica `kmp-compose` + Koin + Arrow + Navigation)

#### Scenario: Módulo :api não inclui Room nem Compose
- **WHEN** um módulo `:feature:X:api` com `kmp-library` é compilado
- **THEN** Room, KSP, `compose.runtime` e `koin.core` MUST NOT ser adicionados automaticamente

#### Scenario: Plugin kmp-feature inclui Compose e Koin
- **WHEN** o plugin `kmp-feature` é aplicado
- **THEN** `compose.runtime`, `compose.material3`, `koin.core` e `koin.compose` MUST ser adicionados ao `commonMain`