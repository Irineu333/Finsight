## 1. Fundação: `:core:test` + version catalog

- [ ] 1.1 Adicionar `kotlinx-coroutines-test` ao `gradle/libs.versions.toml` (versão alinhada à de coroutines de runtime)
- [ ] 1.2 Adicionar `app.cash.turbine:turbine` (>=1.2.1) ao `gradle/libs.versions.toml`
- [ ] 1.3 Declarar bundle `test-kmp = [ kotlin-test, kotlinx-coroutines-test, turbine ]` no version catalog
- [ ] 1.4 Criar diretório `core/test/` com `build.gradle.kts` configurando módulo KMP (targets jvm + android + iOS); zero dependência em features
- [ ] 1.5 Incluir `:core:test` em `settings.gradle.kts`
- [ ] 1.6 Implementar `runFlowTest` em `core/test/src/commonMain/.../flow/RunFlowTest.kt` (wrapper sobre `runTest` com `StandardTestDispatcher` por padrão)
- [ ] 1.7 Implementar `assertLeftIs<L>()` e `assertRight()` em `core/test/src/commonMain/.../either/AssertEither.kt`
- [ ] 1.8 Implementar `MainDispatcherRule` em `core/test/src/jvmMain/.../dispatcher/MainDispatcherRule.kt` (TestWatcher JUnit)
- [ ] 1.9 Adicionar `core/test/README.md` com seções Responsabilidade, Conteúdo, Quando usar e Exemplos
- [ ] 1.10 Atualizar `CLAUDE.md`: adicionar `:core:test` na lista `## Modules` (subseção Core), atualizar seção "Module convention" para descrever `:fake` como sufixo opcional + regra de dependência
- [ ] 1.11 Rodar `./gradlew :core:test:build` e confirmar sucesso

## 2. Feature `creditCards`: fake + cobertura

- [ ] 2.1 Criar `feature/creditCards/fake/` com `build.gradle.kts` (KMP, depende SÓ de `:feature:creditCards:api` + `kotlinx-coroutines-core`)
- [ ] 2.2 Incluir `:feature:creditCards:fake` em `settings.gradle.kts`
- [ ] 2.3 Implementar `FakeInvoiceRepository` (state holder reativo cobrindo todos os métodos de `IInvoiceRepository`)
- [ ] 2.4 Implementar `FakeCreditCardRepository` (idem para `ICreditCardRepository`)
- [ ] 2.5 Implementar fixtures `invoiceOf(...)`, `creditCardOf(...)` em `feature/creditCards/fake/.../fixture/Fixtures.kt`
- [ ] 2.6 Adicionar `feature/creditCards/fake/README.md` listando fakes e fixtures expostos
- [ ] 2.7 Adicionar `commonTestImplementation(libs.bundles.test-kmp)` e `commonTestImplementation(projects.feature.creditCards.fake)` em `feature/creditCards/impl/build.gradle.kts`
- [ ] 2.8 `CalculateAvailableLimitUseCaseTest` — cobrir cálculo com saldo positivo, zerado e excedido
- [ ] 2.9 `CalculateInvoiceUseCaseTest` — cobrir soma de transações por status de fatura
- [ ] 2.10 `CalculateInvoiceOverviewsUseCaseTest` — cobrir agrupamento por mês e cards
- [ ] 2.11 `CreateInvoiceUseCaseTest`
- [ ] 2.12 `CreateFutureInvoiceUseCaseTest`
- [ ] 2.13 `CreateRetroactiveInvoiceUseCaseTest`
- [ ] 2.14 `GetOrCreateInvoiceForMonthUseCaseTest` — cobrir ramo "encontra existente" e "cria nova"
- [ ] 2.15 `OpenInvoiceUseCaseTest` — happy path + erros
- [ ] 2.16 `CloseInvoiceUseCaseTest` — happy path + cada `InvoiceError` (NotFound, CannotClosePaidInvoice, AlreadyClosed, CannotCloseOutsideClosingMonth, NegativeBalance) + branch retroativo + branch invoiceAmount==0
- [ ] 2.17 `PayInvoiceUseCaseTest`
- [ ] 2.18 `PayInvoicePaymentUseCaseTest`
- [ ] 2.19 `ReopenInvoiceUseCaseTest`
- [ ] 2.20 `AdvanceInvoicePaymentUseCaseTest`
- [ ] 2.21 `AdjustInvoiceUseCaseTest`
- [ ] 2.22 `DeleteFutureInvoiceUseCaseTest`
- [ ] 2.23 `AddCreditCardUseCaseTest`
- [ ] 2.24 `UpdateCreditCardUseCaseTest`
- [ ] 2.25 `DeleteCreditCardUseCaseTest`
- [ ] 2.26 `ValidateCreditCardNameUseCaseTest` — happy path + cada erro de validação
- [ ] 2.27 `CreditCardsViewModelTest` (jvmTest) — testar `uiState` para lista vazia, cards com faturas em cada status, e troca de mês
- [ ] 2.28 `InvoiceTransactionsViewModelTest` (jvmTest)
- [ ] 2.29 `CreditCardFormViewModelTest` (jvmTest) — validação de campos + actions
- [ ] 2.30 `CloseInvoiceViewModelTest`, `PayInvoiceViewModelTest`, `ReopenInvoiceViewModelTest`, `AdvancePaymentViewModelTest`, `EditInvoiceBalanceViewModelTest`, `DeleteCreditCardViewModelTest`, `DeleteFutureInvoiceViewModelTest` (jvmTest) — cobrir transição de UiState por action
- [ ] 2.31 Rodar `./gradlew :feature:creditCards:impl:check` e confirmar verde

## 3. Feature `transactions`: fake + cobertura

- [ ] 3.1 Criar `feature/transactions/fake/` (KMP, depende SÓ de `:feature:transactions:api`)
- [ ] 3.2 Incluir `:feature:transactions:fake` em `settings.gradle.kts`
- [ ] 3.3 Implementar `FakeOperationRepository` e `FakeTransactionRepository`
- [ ] 3.4 Implementar fixtures `operationOf(...)`, `transactionOf(...)` em `Fixtures.kt`
- [ ] 3.5 Adicionar `feature/transactions/fake/README.md`
- [ ] 3.6 Configurar `commonTestImplementation` de bundle + fakes necessários em `feature/transactions/impl/build.gradle.kts` (incluir `:feature:accounts:fake`, `:feature:creditCards:fake`, `:feature:categories:fake` quando criados)
- [ ] 3.7 `CalculateBalanceUseCaseTest` — saldo até mês X com múltiplas transações, transferências e ajustes
- [ ] 3.8 `CalculateTransactionStatsUseCaseTest` — soma income/expense/adjustment por yearMonth
- [ ] 3.9 `BuildTransactionUseCaseTest` — construção a partir de `TransactionForm` (happy + cada erro)
- [ ] 3.10 `TransactionsViewModelTest` (jvmTest) — uiState para cada combinação de filtros (categoria, tipo, target, recorrente, parcelado), troca de mês, balance overview
- [ ] 3.11 `EditTransactionViewModelTest` (jvmTest)
- [ ] 3.12 `DeleteTransactionViewModelTest` (jvmTest)
- [ ] 3.13 `AddTransactionViewModelTest` (jvmTest)
- [ ] 3.14 `ViewOperationViewModelTest` (jvmTest)
- [ ] 3.15 `ViewAdjustmentViewModelTest` (jvmTest)
- [ ] 3.16 Rodar `./gradlew :feature:transactions:impl:check`

## 4. Feature `installments`: fake + cobertura

- [ ] 4.1 Criar `feature/installments/fake/` (depende SÓ de `:feature:installments:api`)
- [ ] 4.2 Incluir em `settings.gradle.kts`
- [ ] 4.3 Implementar `FakeInstallmentRepository` + fixtures `installmentOf(...)`
- [ ] 4.4 Adicionar README
- [ ] 4.5 Configurar `commonTestImplementation` em `feature/installments/impl/build.gradle.kts` (com `:feature:transactions:fake` e `:feature:creditCards:fake`)
- [ ] 4.6 `AddInstallmentUseCaseTest` — divisão correta entre faturas, contornos de fronteira de mês, happy + erros
- [ ] 4.7 `InstallmentsViewModelTest` (jvmTest)
- [ ] 4.8 `AddInstallmentViewModelTest` (jvmTest)
- [ ] 4.9 `DeleteInstallmentViewModelTest` (jvmTest)
- [ ] 4.10 Rodar `./gradlew :feature:installments:impl:check`

## 5. Feature `recurring`: fake + cobertura

- [ ] 5.1 Criar `feature/recurring/fake/` (depende SÓ de `:feature:recurring:api`)
- [ ] 5.2 Incluir em `settings.gradle.kts`
- [ ] 5.3 Implementar `FakeRecurringRepository` + `FakeRecurringOccurrenceRepository` + fixtures
- [ ] 5.4 Adicionar README
- [ ] 5.5 Configurar `commonTestImplementation` em `feature/recurring/impl/build.gradle.kts`
- [ ] 5.6 `GetPendingRecurringUseCaseTest` — listar pendentes por data atual
- [ ] 5.7 `ConfirmRecurringUseCaseTest` — confirma ocorrência e cria transação
- [ ] 5.8 `SkipRecurringUseCaseTest`
- [ ] 5.9 `StopRecurringUseCaseTest`
- [ ] 5.10 `ReactivateRecurringUseCaseTest`
- [ ] 5.11 `SaveRecurringUseCaseTest`
- [ ] 5.12 `RecurringViewModelTest` (jvmTest)
- [ ] 5.13 `RecurringFormViewModelTest` (jvmTest)
- [ ] 5.14 `ConfirmRecurringViewModelTest`, `ReactivateRecurringViewModelTest`, `DeleteRecurringViewModelTest`, `StopRecurringViewModelTest` (jvmTest)
- [ ] 5.15 Rodar `./gradlew :feature:recurring:impl:check`

## 6. Feature `categories`: fake + cobertura

- [ ] 6.1 Criar `feature/categories/fake/` (depende SÓ de `:feature:categories:api`)
- [ ] 6.2 Incluir em `settings.gradle.kts`
- [ ] 6.3 Implementar `FakeCategoryRepository` + fixtures `categoryOf(...)`
- [ ] 6.4 Adicionar README
- [ ] 6.5 Configurar `commonTestImplementation` em `feature/categories/impl/build.gradle.kts`
- [ ] 6.6 `ValidateCategoryNameUseCaseTest` — happy + cada erro
- [ ] 6.7 `CreateDefaultCategoriesUseCaseTest`
- [ ] 6.8 `CategoriesViewModelTest` (jvmTest)
- [ ] 6.9 `CategoryFormViewModelTest` (jvmTest)
- [ ] 6.10 `ViewCategoryViewModelTest`, `DeleteCategoryViewModelTest` (jvmTest)
- [ ] 6.11 Rodar `./gradlew :feature:categories:impl:check`

## 7. Feature `accounts`: fake + cobertura

- [ ] 7.1 Criar `feature/accounts/fake/` (depende SÓ de `:feature:accounts:api`)
- [ ] 7.2 Incluir em `settings.gradle.kts`
- [ ] 7.3 Implementar `FakeAccountRepository` + fixtures `accountOf(...)`
- [ ] 7.4 Adicionar README
- [ ] 7.5 Configurar `commonTestImplementation` em `feature/accounts/impl/build.gradle.kts`
- [ ] 7.6 `ValidateAccountNameUseCaseTest`
- [ ] 7.7 `CreateAccountUseCaseTest`
- [ ] 7.8 `UpdateAccountUseCaseTest`
- [ ] 7.9 `DeleteAccountUseCaseTest`
- [ ] 7.10 `SetDefaultAccountUseCaseTest`
- [ ] 7.11 `EnsureDefaultAccountUseCaseTest`
- [ ] 7.12 `AdjustBalanceUseCaseTest`
- [ ] 7.13 `AdjustInitialBalanceUseCaseTest`
- [ ] 7.14 `AdjustFinalBalanceUseCaseTest`
- [ ] 7.15 `TransferBetweenAccountsUseCaseTest` — happy + cada erro
- [ ] 7.16 `AccountsViewModelTest` (jvmTest)
- [ ] 7.17 `AccountFormViewModelTest` (jvmTest)
- [ ] 7.18 `TransferBetweenAccountsViewModelTest` (jvmTest)
- [ ] 7.19 `EditAccountBalanceViewModelTest`, `DeleteAccountViewModelTest` (jvmTest)
- [ ] 7.20 Rodar `./gradlew :feature:accounts:impl:check`

## 8. Feature `budgets`: fake + cobertura

- [ ] 8.1 Criar `feature/budgets/fake/` (depende SÓ de `:feature:budgets:api`)
- [ ] 8.2 Incluir em `settings.gradle.kts`
- [ ] 8.3 Implementar `FakeBudgetRepository` + fixtures `budgetOf(...)`
- [ ] 8.4 Adicionar README
- [ ] 8.5 Configurar `commonTestImplementation` em `feature/budgets/impl/build.gradle.kts`
- [ ] 8.6 `ValidateBudgetTitleUseCaseTest`
- [ ] 8.7 `CalculateBudgetProgressUseCaseTest` — progresso 0%, parcial, 100%, ultrapassado; múltiplas categorias
- [ ] 8.8 `BudgetsViewModelTest` (jvmTest)
- [ ] 8.9 `BudgetFormViewModelTest` (jvmTest)
- [ ] 8.10 `DeleteBudgetViewModelTest` (jvmTest)
- [ ] 8.11 Rodar `./gradlew :feature:budgets:impl:check`

## 9. Verificação final

- [ ] 9.1 Rodar `./gradlew check` na raiz e confirmar verde
- [ ] 9.2 Verificar que nenhum `:fake` depende de outro `:api`, outro `:fake` ou qualquer `:impl` (inspeção manual dos `build.gradle.kts`)
- [ ] 9.3 Verificar que `:app/build.gradle.kts` NÃO depende de nenhum `:fake`
- [ ] 9.4 Verificar que `gradle/libs.versions.toml` não contém MockK/Mockito
- [ ] 9.5 Atualizar `CLAUDE.md` (caso falte algo) e revisar coerência dos READMEs criados
- [ ] 9.6 Rodar `openspec validate add-unit-testing-foundation --strict` e corrigir issues
