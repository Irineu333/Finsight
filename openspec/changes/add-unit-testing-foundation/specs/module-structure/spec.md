## ADDED Requirements

### Requirement: Módulo `:core:test` existe como módulo core de infra de testes
O projeto SHALL ter um módulo core adicional `:core:test` dedicado a infraestrutura compartilhada de testes (Main dispatcher rule, wrappers de coroutines/Flow, helpers de assertion). Como os demais `:core:*`, ele MUST NOT conter lógica de negócio nem depender de features.

#### Scenario: :core:test listado em settings.gradle.kts
- **WHEN** `settings.gradle.kts` é inspecionado
- **THEN** ele MUST conter `include(":core:test")`

#### Scenario: :core:test não contém lógica de feature
- **WHEN** o conteúdo de `:core:test` é inspecionado
- **THEN** ele MUST conter apenas utilitários de teste genéricos (dispatcher rules, wrappers de runTest, assertions de tipos comuns como `Either`) e MUST NOT conter fakes de feature, modelos de domínio, ou referência a `:feature:*`

---

### Requirement: Padrão `:feature:X:fake` para fakes e fixtures reutilizáveis entre módulos
Cada feature do produto MAY ter um módulo adicional `:feature:X:fake` (singular, main source set, KMP), criado sob demanda quando a feature passa a ser testada ou outra feature precisa fakeá-la. O módulo SHALL conter implementações fake de `IXxxRepository` (definidas em `:feature:X:api`) e factories de modelo (fixtures).

#### Scenario: Fake module existe quando a feature é testada
- **WHEN** a feature `:feature:creditCards` ganha cobertura de testes
- **THEN** o módulo `:feature:creditCards:fake` MUST existir e estar listado em `settings.gradle.kts`

#### Scenario: Fake module ausente para features sem teste
- **WHEN** uma feature não tem testes próprios nem é fakeada por outra
- **THEN** seu módulo `:fake` MUST NOT existir (não criar módulos vazios)

#### Scenario: :fake usa nome singular
- **WHEN** o módulo é criado
- **THEN** o nome MUST ser `fake` (singular), alinhado com `:api`/`:impl`/`:ui` — nunca `fakes`

---

### Requirement: `:feature:X:fake` depende SOMENTE de `:feature:X:api`
A regra de dependência SHALL ser: `:feature:X:fake` declara dependência apenas em `:feature:X:api` e em bibliotecas básicas (`kotlinx-coroutines-core` para `MutableStateFlow`). MUST NOT depender de outros `:fake`, outros `:api`, ou qualquer `:impl`.

Consumidores de fakes SHALL importar via `commonTestImplementation(projects.feature.<x>.fake)` no `build.gradle.kts` do `:impl` consumidor. A regra "`:impl` não depende de `:impl`" do CLAUDE.md SHALL permanecer intacta — `:fake` não cria caminho transitivo entre `:impl`s.

#### Scenario: Fake não importa outras features
- **WHEN** `build.gradle.kts` de `:feature:creditCards:fake` é inspecionado
- **THEN** ele MUST NOT conter `projects.feature.<outra>.api`, `projects.feature.<outra>.fake`, nem qualquer `projects.feature.<qualquer>.impl`

#### Scenario: Consumidor importa :fake em commonTest
- **WHEN** `:feature:transactions:impl` precisa fakeear `IAccountRepository` em testes
- **THEN** seu `build.gradle.kts` MUST declarar `commonTestImplementation(projects.feature.accounts.fake)`

#### Scenario: :app não depende de :fake
- **WHEN** `:app/build.gradle.kts` é inspecionado
- **THEN** ele MUST NOT depender de nenhum `:feature:*:fake` (módulos de fake existem apenas para o classpath de teste de outros módulos)
