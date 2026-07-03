# build-conventions Specification

## Purpose
TBD - created by archiving change modularize-features-api-impl. Update Purpose after archive.
## Requirements
### Requirement: Convention plugins em build-logic
O projeto SHALL ter um build incluído `build-logic` com convention plugins que concentram toda a configuração comum: `kmp.library` (targets KMP: Android, iOS, Desktop), `compose.library` (Compose Multiplatform), `feature.api` e `feature.impl`. O `build.gradle.kts` de um módulo de feature SHALL conter apenas a aplicação da convenção e suas dependências específicas.

#### Scenario: Criação de novo módulo de feature
- **WHEN** um novo módulo `:feature:<nome>:api` ou `:feature:<nome>:impl` é criado
- **THEN** seu `build.gradle.kts` aplica a convenção correspondente e não repete configuração de targets, compiler options ou Compose

### Requirement: Verificação mecânica das regras de dependência
As convenções `feature.api` e `feature.impl` SHALL verificar as regras de dependência do module-architecture durante o build: `feature.api` só admite dependências de projeto `:core:*`; `feature.impl` admite `:core:*` e `:feature:*:api`. Violações SHALL falhar o build com mensagem indicando o módulo e a dependência proibida.

#### Scenario: api declara dependência de outra api
- **WHEN** `:feature:transactions:api` declara dependência de `:feature:accounts:api`
- **THEN** o build falha indicando a regra violada (api não depende de api)

#### Scenario: impl declara dependência de outro impl
- **WHEN** `:feature:dashboard:impl` declara dependência de `:feature:creditcards:impl`
- **THEN** o build falha indicando a regra violada (impl não depende de impl)

### Requirement: Código de plataforma restrito por padrão
Os módulos de feature SHALL ser `commonMain` puro por padrão. Source sets de plataforma em um `impl` são exceção justificada (ex.: `report:impl` com serviços nativos de print/share) e MUST NOT existir em módulos `api`.

#### Scenario: Feature comum
- **WHEN** uma feature não possui requisito nativo
- **THEN** seus módulos contêm apenas `commonMain` (e `commonTest`), sem source sets de plataforma

