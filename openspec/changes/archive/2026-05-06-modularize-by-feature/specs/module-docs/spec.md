## ADDED Requirements

### Requirement: Cada feature e módulo core tem README.md na raiz
Cada feature e módulo core SHALL ter um `README.md` na raiz do seu diretório (ex: `feature/accounts/README.md`, `core/database/README.md`). Módulos sem sub-diretório api/impl (ex: `:core:utils`) também SHALL ter README.md.

#### Scenario: README cobre api e impl quando ambos existem
- **WHEN** uma feature tem módulos `:api` e `:impl`
- **THEN** o `README.md` na raiz da feature MUST cobrir os dois: o que o `:api` expõe (contratos públicos) e o que o `:impl` implementa (responsabilidades internas)

#### Scenario: README de feature terminal cobre apenas impl
- **WHEN** uma feature terminal (dashboard, home, support) tem apenas `:impl`
- **THEN** o `README.md` MUST documentar a responsabilidade do módulo sem seção de contratos públicos

---

### Requirement: README de feature declara responsabilidade, contratos e dependências
O `README.md` de cada feature SHALL conter:
1. **Responsabilidade** — uma frase descrevendo o que a feature faz
2. **Contratos públicos (`:api`)** — modelos de domínio, interfaces de repositório e interfaces de use cases expostos para outras features
3. **Dependências** — quais módulos `:api` de outras features este módulo depende
4. **Implementação (`:impl`)** — use cases, screens, ViewModels e modais internos (não expostos)

#### Scenario: README lista IBuildTransactionUseCase como contrato público
- **WHEN** o README de `:feature:transactions` é lido
- **THEN** `IBuildTransactionUseCase` e `ICalculateBalanceUseCase` MUST estar listados na seção de contratos públicos do `:api`

#### Scenario: README de :feature:installments lista dependências corretas
- **WHEN** o README de `:feature:installments` é lido
- **THEN** a seção de dependências MUST listar `transactions:api` e `creditCards:api`

---

### Requirement: CLAUDE.md da raiz tem índice de módulos
O `CLAUDE.md` do projeto SHALL ter uma seção `## Modules` listando cada feature e módulo core com uma linha descritiva e link para seu `README.md`. Nenhum detalhe interno (contratos, dependências) SHALL ser duplicado no índice — esses detalhes ficam nos READMEs.

#### Scenario: Índice tem uma entrada por feature, não por sub-módulo
- **WHEN** o `CLAUDE.md` é lido
- **THEN** a seção `## Modules` MUST ter uma entrada para `feature/accounts/` (não duas entradas separadas para `feature/accounts/api/` e `feature/accounts/impl/`)

#### Scenario: CLAUDE.md não lista use cases ou modelos de domínio
- **WHEN** o `CLAUDE.md` é lido
- **THEN** a seção `## Modules` MUST NOT mencionar `IBuildTransactionUseCase`, `Account`, `Category` ou outros símbolos internos — apenas o nome e responsabilidade da feature

---

### Requirement: Seção Layers do CLAUDE.md é atualizada para refletir módulos
Após a modularização, a seção `Layers` do `CLAUDE.md` que descreve `/domain/`, `/database/`, `/ui/` SHALL ser substituída pela convenção de módulos Gradle: padrão api/impl, regras de dependência, e onde encontrar a lista autoritativa de módulos (`settings.gradle.kts`).

#### Scenario: CLAUDE.md não referencia camadas antigas após modularização
- **WHEN** o `CLAUDE.md` é lido após a migração completa
- **THEN** referências a `/domain/`, `/database/` e `/ui/` como camadas MUST NOT existir