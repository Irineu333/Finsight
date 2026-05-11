## ADDED Requirements

### Requirement: Módulo `:core:test` provê infraestrutura comum de testes
O projeto SHALL ter um módulo `:core:test` (KMP, sem dependência em features) contendo:
- `MainDispatcherRule` (somente em `jvmMain`) — `TestWatcher` JUnit que troca `Dispatchers.Main` por um `TestDispatcher` no `starting` e restaura no `finished`.
- `runFlowTest { }` (em `commonMain`) — wrapper sobre `runTest` aceitando opcionalmente um `TestDispatcher`.
- Helpers de assertion sobre `Either` (em `commonMain`) — `assertLeftIs<E>()` e `assertRight()`.

#### Scenario: MainDispatcherRule troca Dispatchers.Main
- **WHEN** um teste JVM em `:feature:X:impl/jvmTest` declara `@get:Rule val rule = MainDispatcherRule()`
- **THEN** durante a execução do teste `Dispatchers.Main` MUST estar configurado para o `TestDispatcher` da rule, e após o teste MUST ser restaurado via `Dispatchers.resetMain()`

#### Scenario: runFlowTest executa bloco com TestDispatcher
- **WHEN** um teste em `commonTest` chama `runFlowTest { ... }`
- **THEN** o bloco MUST executar dentro de `runTest` com `StandardTestDispatcher` por padrão

#### Scenario: assertLeftIs detecta tipo correto de erro
- **WHEN** o teste chama `result.assertLeftIs<InvoiceError.NotFound>()` em um `Either.Left(InvoiceError.NotFound)`
- **THEN** o helper MUST retornar o erro tipado; se o `Either` for `Right` ou o `Left` for de outro tipo, a chamada MUST falhar com mensagem clara

---

### Requirement: `:core:test` não depende de feature alguma
O módulo `:core:test` SHALL NOT declarar dependência em nenhum `:feature:*:api`, `:feature:*:impl` ou `:feature:*:fake`.

#### Scenario: build.gradle.kts de :core:test não lista features
- **WHEN** o `build.gradle.kts` de `:core:test` é inspecionado
- **THEN** ele MUST NOT conter `implementation(projects.feature.*)` ou equivalente

#### Scenario: :core:test compila sem features
- **WHEN** `./gradlew :core:test:build` é executado sem nenhuma feature no path
- **THEN** o build MUST passar

---

### Requirement: Padrão `:feature:X:fake` para fakes e fixtures reutilizáveis
Quando uma feature passa a ser testada (ou outra feature precisa fakeá-la), SHALL ser criado um módulo `:feature:X:fake` (singular, main source set, KMP) com:
- `fake/Fake<Xxx>Repository.kt` — implementação reativa da interface `IXxxRepository` definida em `:feature:X:api`.
- `fixture/Fixtures.kt` (ou arquivos por modelo) — funções top-level `<modelo>Of(...)` com defaults sensatos para os modelos da feature.

O módulo SHALL existir apenas para features que estão em uso por testes. Não criar módulos `:fake` vazios.

#### Scenario: Fake implementa interface do :api
- **WHEN** `:feature:creditCards:fake` é compilado
- **THEN** `FakeInvoiceRepository` MUST implementar `IInvoiceRepository` (de `:feature:creditCards:api`) completa, sem deixar métodos com `TODO()`

#### Scenario: Fixture cria modelo com defaults
- **WHEN** um teste chama `invoiceOf()` sem argumentos
- **THEN** a função MUST retornar um `Invoice` válido com todos os campos preenchidos por defaults; argumentos nomeados sobrescrevem campos individuais

#### Scenario: :fake não é criado upfront
- **WHEN** o projeto é inspecionado e a feature `:feature:support` ainda não tem testes ou consumidores de fake
- **THEN** o módulo `:feature:support:fake` MUST NOT existir no `settings.gradle.kts`

---

### Requirement: Fakes SHALL ser state holders reativos, não mocks
Cada `FakeXxxRepository` SHALL implementar a interface com `MutableStateFlow` interno mutado pelas operações `suspend`, expondo o flow imutável nas operações `observeXxx()`. O projeto SHALL NOT usar nenhum framework de mock (MockK, Mockito, etc.) em código de teste.

#### Scenario: Mudança no fake propaga via Flow
- **WHEN** um teste atualiza o estado interno do fake (ex: `fakeRepository.invoices.value = listOf(invoice1)`) e o sujeito de teste observa o flow via `observeAll()`
- **THEN** o observador MUST receber a nova emissão sem reconfiguração explícita

#### Scenario: Build não inclui MockK ou Mockito
- **WHEN** o `gradle/libs.versions.toml` é inspecionado
- **THEN** ele MUST NOT conter entradas para `mockk`, `mockito`, `mockito-kotlin` ou similares

---

### Requirement: `:feature:X:fake` depende SOMENTE de `:feature:X:api`
A regra de dependência SHALL ser: `:feature:X:fake` pode declarar `api(projects.feature.<x>.api)` (ou `implementation`) e dependências básicas (`kotlinx-coroutines-core` para `MutableStateFlow`). MUST NOT depender de outros `:fake`, outros `:api`, ou de qualquer `:impl`.

#### Scenario: Fake não vê impl da própria feature
- **WHEN** o `build.gradle.kts` de `:feature:creditCards:fake` é inspecionado
- **THEN** ele MUST NOT conter dependência em `projects.feature.creditCards.impl`

#### Scenario: Fake não vê api de outra feature
- **WHEN** o `build.gradle.kts` de `:feature:creditCards:fake` é inspecionado
- **THEN** ele MUST NOT conter dependência em `projects.feature.accounts.api`, `projects.feature.transactions.api`, etc.

#### Scenario: Consumidor de fake importa via :fake, não via :impl
- **WHEN** `:feature:transactions:impl/commonTest` precisa fakeear contas
- **THEN** ele MUST declarar `commonTestImplementation(projects.feature.accounts.fake)` (NUNCA `projects.feature.accounts.impl`)

---

### Requirement: Localização e nomenclatura de testes
Testes SHALL viver no módulo `:impl` da feature testada, organizados por tipo:
- UseCases puros e suspend: `commonTest/.../usecase/Xxx<UseCase>Test.kt`
- ViewModels (que usam `viewModelScope` → `Dispatchers.Main`): `jvmTest/.../screen/Xxx<ViewModel>Test.kt`

Métodos de teste SHALL ter nomes descritivos em camelCase (sem backticks) explicando o cenário e o resultado esperado.

#### Scenario: UseCase testado em commonTest
- **WHEN** um teste é criado para `CloseInvoiceUseCase`
- **THEN** o arquivo MUST estar em `feature/creditCards/impl/src/commonTest/kotlin/.../usecase/CloseInvoiceUseCaseTest.kt`

#### Scenario: ViewModel testado em jvmTest
- **WHEN** um teste é criado para `CreditCardsViewModel`
- **THEN** o arquivo MUST estar em `feature/creditCards/impl/src/jvmTest/kotlin/.../screen/CreditCardsViewModelTest.kt` e usar `MainDispatcherRule` de `:core:test`

#### Scenario: Nome de método explica cenário e resultado
- **WHEN** um método de teste é nomeado
- **THEN** o nome MUST descrever o cenário e o resultado esperado em camelCase (ex: `closeInvoiceFailsWhenInvoiceIsAlreadyClosed`), seguindo o estilo de `CalculateReportStatsUseCaseTest`

---

### Requirement: Bibliotecas de teste declaradas no version catalog
O `gradle/libs.versions.toml` SHALL declarar:
- `kotlinx-coroutines-test` (versão alinhada à de coroutines de runtime).
- `app.cash.turbine:turbine` (1.2.x ou superior).
- Bundle `test-kmp = [ kotlin-test, kotlinx-coroutines-test, turbine ]`.

Módulos `:impl` que tenham testes SHALL declarar `commonTestImplementation(libs.bundles.test-kmp)` em seu `build.gradle.kts`. Módulos `:impl` sem testes MUST NOT declarar o bundle (evita churn).

#### Scenario: Bundle test-kmp existe no catalog
- **WHEN** `gradle/libs.versions.toml` é inspecionado
- **THEN** MUST conter um bundle `test-kmp` com `kotlin-test`, `kotlinx-coroutines-test` e `turbine`

#### Scenario: Módulo com testes aplica o bundle
- **WHEN** `:feature:creditCards:impl/build.gradle.kts` é inspecionado após cobertura
- **THEN** ele MUST conter `commonTestImplementation(libs.bundles.test-kmp)`

#### Scenario: Módulo sem testes não aplica o bundle
- **WHEN** `:feature:home:impl/build.gradle.kts` é inspecionado (feature fora do escopo)
- **THEN** ele MUST NOT conter dependência em `libs.bundles.test-kmp`

---

### Requirement: Cobertura mínima nas features priorizadas
Após a fundação, SHALL existir cobertura unitária para todos os UseCases das 7 features priorizadas (`creditCards`, `transactions`, `installments`, `recurring`, `categories`, `accounts`, `budgets`) e para ViewModels não-triviais (definidos como: `combine` com 2+ sources OU `onAction` com transição de estado).

Cada UseCase SHALL ter ao menos um teste por cenário público (happy path e cada `Either.Left` retornável).

#### Scenario: UseCase com erros tem teste por erro
- **WHEN** `CloseInvoiceUseCase` retorna `InvoiceError.NotFound`, `InvoiceError.CannotClosePaidInvoice`, `InvoiceError.AlreadyClosed`, `InvoiceError.CannotCloseOutsideClosingMonth`, `InvoiceError.NegativeBalance`
- **THEN** MUST existir ao menos um teste por erro retornando esse Left, mais ao menos um teste happy path retornando Right

#### Scenario: ViewModel com filtros tem teste por filtro
- **WHEN** `TransactionsViewModel` expõe ações `SelectCategory`, `SelectType`, `SelectTarget`, `ToggleRecurring`, `ToggleInstallment`
- **THEN** MUST existir ao menos um teste para cada ação verificando a transição de `UiState`

#### Scenario: Feature fora do escopo não bloqueia
- **WHEN** `:feature:home:impl` não tem nenhum teste novo
- **THEN** a change MUST poder ser arquivada (ele está explicitamente fora do escopo)
