## MODIFIED Requirements

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
