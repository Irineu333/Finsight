## ADDED Requirements

### Requirement: Convention plugins eliminam repetição de configuração KMP
O projeto SHALL ter um diretório `build-logic/` com convention plugins Kotlin DSL que encapsulam a configuração KMP repetida. Nenhum `build.gradle.kts` de módulo SHALL repetir targets KMP, source sets ou configuração de KSP manualmente.

#### Scenario: Novo módulo :api usa plugin sem repetir targets
- **WHEN** um novo módulo `:feature:X:api` é criado
- **THEN** seu `build.gradle.kts` MUST conter apenas `plugins { id("kmp-feature-api") }` e suas dependências específicas, sem declarar `androidTarget()`, `iosX64()`, `jvm()` etc.

#### Scenario: Plugin kmp-database configura KSP automaticamente
- **WHEN** o plugin `kmp-database` é aplicado a um módulo
- **THEN** as 6 configurações de KSP Room (`kspCommonMainMetadata`, `kspAndroid`, `kspJvm`, `kspIosX64`, `kspIosArm64`, `kspIosSimulatorArm64`) MUST ser adicionadas automaticamente

---

### Requirement: Quatro plugins cobrem todos os tipos de módulo
O `build-logic/` SHALL fornecer exatamente quatro plugins:
- `kmp-core` — para módulos `:core:*` (library KMP sem Android Application)
- `kmp-feature-api` — para módulos `:feature:X:api` (KMP leve, sem Room/Firebase)
- `kmp-feature-impl` — para módulos `:feature:X:impl` (KMP com Compose, Koin, Arrow)
- `kmp-database` — para `:core:database` (KMP com Room e KSP 6 targets)

#### Scenario: Plugin kmp-feature-api não inclui Room
- **WHEN** o plugin `kmp-feature-api` é aplicado
- **THEN** Room e KSP MUST NOT ser adicionados como dependências

#### Scenario: Plugin kmp-feature-impl inclui Compose e Koin
- **WHEN** o plugin `kmp-feature-impl` é aplicado
- **THEN** `compose.runtime`, `compose.material3`, `koin.core` e `koin.compose` MUST ser adicionados ao `commonMain`