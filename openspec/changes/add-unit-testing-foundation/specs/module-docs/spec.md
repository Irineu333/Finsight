## ADDED Requirements

### Requirement: `:core:test` SHALL ter README.md descrevendo a infra
O módulo `:core:test` SHALL ter um `README.md` em `core/test/README.md` documentando:
1. **Responsabilidade** — uma frase descrevendo o papel do módulo (infra compartilhada de testes).
2. **Conteúdo** — `MainDispatcherRule` (JVM-only), `runFlowTest`, helpers de `Either`.
3. **Quando usar** — em quais source sets cada utilitário se aplica (`MainDispatcherRule` em `jvmTest`; `runFlowTest`/helpers em `commonTest`).
4. **Exemplos mínimos** — snippet de uso de `MainDispatcherRule` em VM test e `runFlowTest` com Turbine.

#### Scenario: README de :core:test existe e cobre seções obrigatórias
- **WHEN** `core/test/README.md` é lido
- **THEN** ele MUST conter as seções de Responsabilidade, Conteúdo, Quando usar e Exemplos

#### Scenario: README não documenta features
- **WHEN** `core/test/README.md` é lido
- **THEN** ele MUST NOT mencionar features específicas, modelos de domínio ou fakes — apenas infra genérica

---

### Requirement: Cada `:feature:X:fake` SHALL ter README descrevendo fakes e fixtures expostos
Quando um módulo `:feature:X:fake` é criado, ele SHALL ter um `README.md` em `feature/<x>/fake/README.md` listando:
1. **Fakes expostos** — quais interfaces de `:api` são implementadas (ex: `FakeInvoiceRepository : IInvoiceRepository`).
2. **Fixtures expostos** — funções top-level disponíveis (ex: `invoiceOf`, `creditCardOf`).
3. **Quem consome** — referência aos `:impl` que dependem deste `:fake` em testes.

#### Scenario: README de :fake lista fakes e fixtures
- **WHEN** `feature/creditCards/fake/README.md` é lido após criação do módulo
- **THEN** ele MUST listar `FakeInvoiceRepository`, `FakeCreditCardRepository`, `invoiceOf(...)`, `creditCardOf(...)`

#### Scenario: README do :fake não duplica documentação de :api
- **WHEN** o README é lido
- **THEN** ele MUST NOT redocumentar contratos do `:api` (que já vivem no README da feature) — apenas o que `:fake` adiciona

---

### Requirement: Index de Modules do CLAUDE.md inclui `:core:test` e marca `:fake` como sufixo opcional
A seção `## Modules` do `CLAUDE.md` SHALL listar `:core:test` como módulo core. A seção de "Module convention (api/impl)" SHALL ser atualizada para mencionar `:fake` como sufixo opcional adicional (junto com `:api`, `:impl`, `:ui`), com a regra de dependência específica.

#### Scenario: CLAUDE.md lista :core:test em Modules
- **WHEN** `CLAUDE.md` é lido
- **THEN** a seção `## Modules` (subseção Core) MUST conter uma entrada para `:core:test` com link para `core/test/README.md`

#### Scenario: Module convention menciona :fake e regra de dependência
- **WHEN** a seção de convenção de módulos do `CLAUDE.md` é lida
- **THEN** ela MUST mencionar que cada feature MAY ter `:fake` (singular) criado sob demanda, com a regra "`:fake` depende SOMENTE de `:api` da mesma feature" e "`:impl/commonTest` pode consumir qualquer `:fake` e `:core:test`"

#### Scenario: Index NÃO lista módulos :fake individualmente
- **WHEN** `CLAUDE.md` é lido
- **THEN** a seção `## Modules` MUST NOT ter entradas separadas para `:feature:X:fake` — apenas a convenção geral; detalhes ficam no README de cada feature
