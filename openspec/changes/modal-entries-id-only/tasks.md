Cada feature é um commit/PR separado. Tasks numeradas por feature, na ordem do Migration Plan do `design.md`.

## 0. Capability spec

- [x] 0.1 Spec `modal-entries/spec.md` já criada com Requirements de D1–D7 (revisar antes de iniciar tasks)

## 1. Categories

- [x] 1.1 `ViewCategoryUiState` → sealed `Loading | Error | Content(category, selectedYearMonth, totalAmount, transactionCount)`
- [x] 1.2 `ViewCategoryViewModel` recebe `categoryId: Long`; emite `Error` se `getCategoryById` retornar `null`
- [x] 1.3 `ViewCategoryModalEntry.create(categoryId: Long)` em `:feature:categories:api`
- [x] 1.4 `ViewCategoryModalEntryImpl` ajustado
- [x] 1.5 `ViewCategoryModal` lida com `Loading` (layout estável) e `Error` (mensagem + dismiss)
- [x] 1.6 `CategoryFormUiState` → sealed `Loading | Content(name, validation, selectedIcon, selectedType, isEditMode, canSubmit)`
- [x] 1.7 `CategoryFormViewModel` recebe `categoryId: Long?, initialType: Category.Type?`; só carrega quando `categoryId != null`. Em modo criação, emite `Content` com defaults imediatamente
- [x] 1.8 `CategoryFormModalEntry.create(categoryId: Long?, initialType: Category.Type?)` em `:api`
- [x] 1.9 `CategoryFormModalEntryImpl` ajustado
- [x] 1.10 `CategoryFormModal` lida com `Loading` apenas em edição
- [x] 1.11 Atualizar Koin: `viewModel { (id: Long) -> ViewCategoryViewModel(...) }` e `viewModel { (id: Long?, type: Category.Type?) -> CategoryFormViewModel(...) }`
- [x] 1.12 Atualizar todos os call-sites em screens/VMs (`entry.create(category)` → `entry.create(category.id)`)
- [x] 1.13 `./gradlew :feature:categories:impl:check`

## 2. Recurring

- [x] 2.1 Criar/converter `ViewRecurringUiState` → sealed `Loading | Error | Content(recurring, ...)`
- [x] 2.2 `ViewRecurringViewModel` recebe `recurringId: Long`
- [x] 2.3 `ViewRecurringModalEntry.create(recurringId: Long)` em `:api`
- [x] 2.4 Impl ajustado
- [x] 2.5 `ViewRecurringModal` lida com Loading/Error
- [x] 2.6 `RecurringFormUiState` → sealed `Loading | Content(...)`
- [x] 2.7 `RecurringFormViewModel` recebe `recurringId: Long?`; em modo criação inicia em `Content` com defaults
- [x] 2.8 `RecurringFormModalEntry.create(recurringId: Long?)` em `:api`
- [x] 2.9 Impl ajustado
- [x] 2.10 `RecurringFormModal` lida com Loading apenas em edição
- [x] 2.11 `ConfirmRecurringUiState` → sealed `Loading | Content(...)`; dismiss em entidade não encontrada (action modal)
- [x] 2.12 `ConfirmRecurringViewModel` recebe `recurringId: Long, targetDate: LocalDate`
- [x] 2.13 `ConfirmRecurringModalEntry.create(recurringId: Long, targetDate: LocalDate)` em `:api`
- [x] 2.14 Impl + modal ajustados
- [x] 2.15 Atualizar Koin (3 ViewModels)
- [x] 2.16 Atualizar `ViewOperationViewModel.OpenRecurring`: passa `recurringId` direto pelo evento, sem buscar `Recurring` antes
- [x] 2.17 Demais call-sites
- [x] 2.18 `./gradlew :feature:recurring:impl:check`

## 3. Accounts

- [x] 3.1 `AccountFormUiState` → sealed `Loading | Content(...)`
- [x] 3.2 `AccountFormViewModel` recebe `accountId: Long?`; em criação inicia em `Content`
- [x] 3.3 `AccountFormModalEntry.create(accountId: Long?)` em `:api`
- [x] 3.4 Impl ajustado
- [x] 3.5 `AccountFormModal` lida com Loading apenas em edição
- [x] 3.6 Atualizar Koin
- [x] 3.7 Call-sites
- [x] 3.8 `./gradlew :feature:accounts:impl:check`

## 4. Transactions

- [x] 4.1 `ViewOperationUiState` → sealed `Loading | Error | Content(operation, perspective, category, account, creditCard, invoice, sourceAccount, destinationAccount)`
- [x] 4.2 `ViewOperationViewModel` recebe `operationId: Long, perspective: OperationPerspective?`; cascata de fetches dispara após resolver `operation`
- [x] 4.3 `ViewOperationModalEntry.create(operationId: Long, perspective: OperationPerspective?)` em `:api`
- [x] 4.4 Impl + modal ajustados
- [x] 4.5 `ViewAdjustmentUiState` → sealed `Loading | Error | Content(...)`
- [x] 4.6 `ViewAdjustmentViewModel` recebe `operationId: Long`
- [x] 4.7 `ViewAdjustmentModalEntry.create(operationId: Long)` em `:api`
- [x] 4.8 Impl + modal ajustados
- [x] 4.9 Atualizar Koin (2 ViewModels)
- [x] 4.10 Call-sites em todas as features (Dashboard, Accounts, Categories, CreditCards/InvoiceTransactions, Transactions, Recurring, Budgets, Installments, Report)
- [x] 4.11 Validar Open Question 3 do `design.md`: existem outros snapshots derivados em entries de `:transactions:api` que não estão catalogados?
- [x] 4.12 `./gradlew :feature:transactions:impl:check`

## 5. CreditCards — Form

- [ ] 5.1 `CreditCardFormUiState` → sealed `Loading | Content(...)`
- [ ] 5.2 `CreditCardFormViewModel` recebe `creditCardId: Long?`; em criação inicia em `Content`
- [ ] 5.3 `CreditCardFormModalEntry.create(creditCardId: Long?)` em `:api`
- [ ] 5.4 Impl + modal ajustados
- [ ] 5.5 Atualizar Koin
- [ ] 5.6 Call-sites
- [ ] 5.7 `./gradlew :feature:creditCards:impl:check`

## 6. CreditCards — Edit invoice balance

- [ ] 6.1 `EditInvoiceBalanceUiState` revisar (já é sealed; ajustar campos se necessário)
- [ ] 6.2 `EditInvoiceBalanceViewModel` recebe `initialInvoiceId: Long`; resolve `Invoice` no init e segue lógica atual
- [ ] 6.3 `EditInvoiceBalanceModalEntry.create(invoiceId: Long)` em `:api`
- [ ] 6.4 Impl ajustado
- [ ] 6.5 Comportamento de entidade deletada: dismiss + crashlytics
- [ ] 6.6 Atualizar Koin
- [ ] 6.7 Call-sites

## 7. CreditCards — Pay / Advance (D5)

- [ ] 7.1 `PayInvoiceUiState` → sealed `Loading | Content(accounts, selectedAccount, closingDate, dueDate, currentBillAmount)` (currentBillAmount agora vive no Content, calculado pelo VM)
- [ ] 7.2 `PayInvoiceViewModel` recebe `invoiceId: Long`; calcula `currentBillAmount` via `CalculateInvoiceUseCase`
- [ ] 7.3 `PayInvoiceModalEntry.create(invoiceId: Long)` em `:api`
- [ ] 7.4 Impl + modal ajustados (consumir `currentBillAmount` do `Content`)
- [ ] 7.5 `AdvancePaymentUiState` → sealed `Loading | Content(...)`
- [ ] 7.6 `AdvancePaymentViewModel` recebe `invoiceId: Long`; calcula valores via use cases
- [ ] 7.7 `AdvancePaymentModalEntry.create(invoiceId: Long)` em `:api`
- [ ] 7.8 Impl + modal ajustados
- [ ] 7.9 Atualizar Koin (2 ViewModels)
- [ ] 7.10 Call-sites — revisar em `CreditCardsViewModel`, `InvoiceTransactionsViewModel`, `DashboardViewModel`. Confirmar que nenhum chamava com snapshot diferente do que `CalculateInvoiceUseCase` retornaria
- [ ] 7.11 Comportamento de entidade deletada: dismiss + crashlytics
- [ ] 7.12 `./gradlew :feature:creditCards:impl:check`

## 8. Budgets (D6)

- [ ] 8.1 Verificar se existe `CalculateBudgetProgressUseCase` (ou equivalente) em `:feature:budgets`. Se não:
  - [ ] 8.1.a Criar interface em `:feature:budgets:api`
  - [ ] 8.1.b Criar impl em `:feature:budgets:impl` reutilizando lógica que hoje produz `BudgetProgress` na lista
  - [ ] 8.1.c Refatorar `BudgetsViewModel` (ou builder atual) para usar o novo use case (paridade por construção)
- [ ] 8.2 `ViewBudgetUiState` → sealed `Loading | Error | Content(budgetProgress, categories, accentColor)`
- [ ] 8.3 `ViewBudgetViewModel` recebe `budgetId: Long`; reconstrói `BudgetProgress` via use case; emite `Error` se budget não existe
- [ ] 8.4 `ViewBudgetModalEntry.create(budgetId: Long)` em `:api`
- [ ] 8.5 Impl + modal ajustados (mover busca de `categories` que hoje está no `produceState` do modal para o VM)
- [ ] 8.6 Atualizar Koin
- [ ] 8.7 Call-sites
- [ ] 8.8 `./gradlew :feature:budgets:impl:check`

## 9. Verificação final

- [ ] 9.1 Grep: nenhum `:feature:*:api` modal entry importa `feature/*/model/*` (exceto `OperationPerspective`, `Category.Type`)
  - Comando sugerido: `find feature -path "*/api/*" -name "*ModalEntry.kt" -exec grep -l "feature\..*\.model\." {} \;` → deve listar apenas arquivos com import de `OperationPerspective` ou `Category.Type`
- [ ] 9.2 `./gradlew check`
- [ ] 9.3 `./gradlew allTests`
- [ ] 9.4 Smoke test manual (Android + Desktop):
  - [ ] 9.4.a Abrir cada modal afetado, verificar transição `Loading → Content`
  - [ ] 9.4.b Forçar cenário `Error` deletando entidade entre ações (debugger)
  - [ ] 9.4.c Verificar que forms em modo criação não passam por Loading
  - [ ] 9.4.d Verificar que `Pay`/`Advance`/`EditInvoiceBalance` mostram saldo idêntico ao calculado fora do modal
- [ ] 9.5 Atualizar `CLAUDE.md` se necessário (seção sobre modais)
