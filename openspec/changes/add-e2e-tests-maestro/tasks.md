## 1. Fase 1 — Fundação (sem flows ainda)

- [x] 1.1 Instalar Maestro CLI localmente e validar versão (`maestro --version`); registrar versão mínima suportada
- [x] 1.2 Mapear todos os pontos onde Firebase Auth, Firestore, Crashlytics e Analytics são consumidos hoje (grep por `FirebaseAuth`, `Firestore`, `Crashlytics`, `Analytics`); listar arquivos no design para fundamentar extração de interfaces
- [x] 1.3 Definir interfaces de fronteira mínimas para Auth e Firestore onde ainda não existirem, mantendo a implementação real intacta
- [x] 1.4 Criar source set `e2eMain` (Android) com módulo Koin alternativo expondo fakes de Auth/Firestore/Crashlytics/Analytics (Auth anônimo com UID `e2e-user`; Firestore in-memory; Crashlytics/Analytics no-op)
- [x] 1.5 Criar build flavor `e2e` em `composeApp/build.gradle.kts` (Android) que ativa o source set `e2eMain` e desabilita Google Services / Crashlytics plugin para esse flavor
- [x] 1.6 Atualizar `iosApp/project.yml` (XcodeGen) com configuração `e2e` espelho, apontando para a mesma camada de fakes via Koin
- [x] 1.7 Aplicar `Modifier.semantics { testTagsAsResourceId = true }` no root composable do Android (no theme/scaffold raiz, não em cada tela)
- [x] 1.8 Validar manualmente que app builda e abre no flavor `e2e` em Android e iOS, sem erros de DI e sem chamada de rede (verificar com Network Inspector / Charles) — _Android validado em device pelo usuário; iOS adiado até a Fase 2+, quando o wiring Koin real do flavor `E2E` (XcodeGen) entrar (vide design.md, "Implementation Notes")_
- [x] 1.9 Criar estrutura `.maestro/` na raiz: `config.yaml`, `flows/{smoke,transactions,transfers,invoices,installments,recurring}/`, `helpers/`, `README.md`
- [x] 1.10 Escrever `helpers/reset-app.yaml` com `clearState`, `clearKeychain` e `launchApp` apontando para o appId do flavor `e2e`
- [x] 1.11 Escrever `.maestro/README.md` cobrindo: instalação Maestro, build do flavor `e2e` (`./gradlew :composeApp:assembleE2e`), instalação no device, comandos para rodar flow individual e suíte completa, convenção de testTag e ritual "tela mudou ⇒ revisar"
- [x] 1.12 Criar workflow `.github/workflows/e2e-android.yml` com `workflow_dispatch`: builda APK `e2e`, sobe emulador via `reactivecircus/android-emulator-runner`, instala Maestro, roda `maestro test .maestro/flows/`
- [x] 1.13 Disparar o workflow manualmente uma vez para validar que o pipeline chega até "0 flows executed" sem erro de infra — _validado pelo usuário via `workflow_dispatch` no GitHub Actions_
- [x] 1.14 Adicionar se/ção curta no `CLAUDE.md` apontando para `.maestro/README.md` e regra "tela mudou ⇒ revisar testTag e flow"

## 2. Fase 2 — Smoke (P1)

- [x] 2.1 Criar `object DashboardTestTags` com tag `dashboard-root` e aplicar em `DashboardScreen`
- [x] 2.2 Criar `object BottomNavTestTags` com tags para cada aba (`bottom-nav-dashboard`, `bottom-nav-transactions`, `bottom-nav-accounts`, `bottom-nav-credit-cards`, `bottom-nav-categories`, `bottom-nav-budgets` — ajustar conforme abas reais do `BottomNavigationBar`) e aplicar — _hoje o BottomNavigationBar expõe apenas Dashboard e Transactions; as demais áreas ganharam testTag de root via task 2.3 e entrarão no helper de bottom nav quando forem promovidas a aba real_
- [x] 2.3 Criar `object TransactionsTestTags`, `AccountsTestTags`, `CreditCardsTestTags`, `CategoriesTestTags`, `BudgetsTestTags` com tag de root para cada screen e aplicar
- [x] 2.4 Escrever `flows/smoke/01-app-launch.yaml`: chama `helpers/reset-app`, asserta `id: dashboard-root` visível
- [x] 2.5 Escrever `flows/smoke/02-bottom-nav.yaml`: percorre todas as abas via `tapOn: id:` e asserta o root da próxima screen visível em cada
- [x] 2.6 Rodar localmente em Android e iOS; ajustar testTags se algum elemento não for encontrado — _validado localmente pelo usuário; iOS segue a mesma exceção da 1.8 (wiring Koin do flavor E2E iOS adiado)_
- [x] 2.7 Disparar workflow Android no CI e confirmar suíte verde — _validado pelo usuário via `workflow_dispatch` no GitHub Actions com os smoke flows verdes_

## 3. Fase 3 — Transações (P2)

- [x] 3.1 Adicionar testTags em `AccountFormModal` (`account-form-name`, `account-form-submit`) e em `AccountsScreen` (`accounts-fab`, `accounts-item-{id}`) — _o campo de saldo (`account-form-balance` mencionado no plano original) não existe nesse formulário; o saldo é editado em `EditAccountBalanceModal`, fora do escopo da fase 3_
- [x] 3.2 Adicionar testTags em `CategoryFormModal` (`category-form-name`, `category-form-icon`, `category-form-submit`, `category-form-type-expense`, `category-form-type-income`) e em `CategoriesScreen` (`categories-fab`, `categories-item-{id}`, `categories-create-manual` no estado vazio)
- [x] 3.3 Adicionar testTags em `AddTransactionModal` e `EditTransactionModal` via `TransactionFormTestTags` (`transaction-form-title`, `transaction-form-amount`, `transaction-form-date`, `transaction-form-type-expense`, `transaction-form-type-income`, `transaction-form-account`, `transaction-form-category`, `transaction-form-submit`); `AccountSelector` e `CategorySelector` ganharam parâmetro opcional `testTag`
- [x] 3.4 Adicionar testTags em `TransactionsScreen` (item `transactions-item-{operationId}`); o FAB foi taggeado em `HomeScreen` como `transactions-fab` (única instância no chrome do Home, válida em Dashboard e Transactions); filtros não foram cobertos porque os flows da fase 3 não os usam
- [x] 3.5 Adicionar testTags em `DashboardScreen`/`DashboardComponentContent` (`dashboard-total-balance`, `dashboard-account-balance-{accountId}`); aproveitamos para taggear também `dashboard-add-account` e `dashboard-quick-action-{type}` porque os helpers de seed precisam navegar a partir do Dashboard
- [x] 3.6 Adicionar testTags em `ViewOperationModal` (`view-transaction-edit`, `view-transaction-delete`) e `DeleteTransactionModal` (`delete-transaction-confirm`)
- [x] 3.7 Escrever `helpers/seed-account.yaml`: rola o Dashboard até a quick action de Contas, abre `AccountFormModal`, cria conta "E2E Account" e volta ao Dashboard
- [x] 3.8 Escrever `helpers/seed-category.yaml`: rola o Dashboard até a quick action de Categorias, usa o estado vazio (`categories-create-manual`), cria categoria "E2E Food" (despesa) e volta ao Dashboard
- [x] 3.9 Escrever `flows/transactions/01-create-expense.yaml`: reset → seed account → seed category → cria despesa pelo FAB do Home → assert texto na lista de Transactions e card de saldo total no Dashboard
- [x] 3.10 Escrever `flows/transactions/02-create-income.yaml`: reset → cria receita (sem categoria, basta título para `TransactionForm.isValid()`) → assert na lista
- [x] 3.11 Escrever `flows/transactions/03-edit-transaction.yaml`: reset → seed category → cria despesa "E2E Expense" → abre via `ViewOperationModal` → edita título para "E2E Edited" → assert que o novo título aparece e o antigo some
- [x] 3.12 Escrever `flows/transactions/04-delete-transaction.yaml`: reset → seed category → cria despesa → deleta via `ViewOperationModal` + `DeleteTransactionModal` → assert item ausente e Dashboard renderizando saldo total
- [x] 3.13 Rodar a suíte completa local (Android e iOS); investigar e corrigir qualquer flaky antes de merge — _Android validado em emulador (Galaxy_A54 API 17): 6/6 flows verdes em ~3min 21s. Necessário aplicar `Modifier.semantics { testTagsAsResourceId = true }` dentro do `ModalBottomSheet` (extensão `Modifier.testTagsAsResourceIdSemantics()`) porque a flag aplicada na MainActivity não propaga para popups. iOS continua adiado conforme exceção das tasks 1.8/2.6._
- [ ] 3.14 Disparar workflow no CI e confirmar 6 flows verdes (2 smoke + 4 transactions)

## 4. Fase 4 — Movimentações compostas (P3)

- [ ] 4.1 Adicionar testTags em `TransferBetweenAccountsModal` (`transfer-from`, `transfer-to`, `transfer-amount`, `transfer-submit`) e ponto de entrada
- [ ] 4.2 Adicionar testTags em modal de ajuste de saldo (verificar nome real — provavelmente em `AccountsScreen` flow): `adjustment-amount`, `adjustment-submit`
- [ ] 4.3 Adicionar testTags em `CreditCardFormModal` e `CreditCardsScreen` (`credit-card-fab`, `credit-card-form-name`, `credit-card-form-limit`, `credit-card-form-closing-day`, `credit-card-form-due-day`, `credit-card-form-submit`, `credit-card-item-{id}`)
- [ ] 4.4 Adicionar testTags em `InvoiceTransactionsScreen` para asserts (`invoice-total`, `invoice-status`)
- [ ] 4.5 Escrever `helpers/seed-credit-card.yaml`
- [ ] 4.6 Escrever `flows/transfers/01-transfer-between-accounts.yaml`: seed 2 contas com saldos → transferir → asserta saldos atualizados em ambas
- [ ] 4.7 Escrever `flows/transfers/02-balance-adjustment.yaml`: seed conta → ajustar saldo → asserta novo saldo
- [ ] 4.8 Escrever `flows/invoices/01-credit-card-expense.yaml`: seed cartão → lançar despesa no cartão → asserta despesa aparece na fatura aberta com valor correto
- [ ] 4.9 Rodar suíte completa e ajustar flaky; abrir issue para qualquer testTag faltante descoberto

## 5. Fase 5 — Fatura, parcelamento, recorrência (P4 + P5)

- [ ] 5.1 Decidir e documentar (em design.md, seção Open Questions) estratégia de tempo/data para flows de fatura: relógio injetado no flavor `e2e` ou flows agnósticos a data
- [ ] 5.2 Adicionar testTags em `CloseInvoiceModal`, `ReopenInvoiceModal`, fluxo de pagar fatura, `EditInvoiceBalanceModal`
- [ ] 5.3 Adicionar testTags em `AddInstallmentModal` (`installment-amount`, `installment-count`, `installment-submit`) e em `InstallmentsScreen` (`installment-item-{id}`)
- [ ] 5.4 Adicionar testTags em `ConfirmRecurringModal`, `ReactivateRecurringModal`, `ViewRecurringModal` e `RecurringScreen` (`recurring-fab`, `recurring-item-{id}`, `recurring-confirm`, `recurring-skip`, `recurring-stop`)
- [ ] 5.5 Escrever `flows/invoices/02-close-and-pay.yaml`: cartão com despesa → fechar fatura → pagar (debitar conta) → asserta status pago e saldo da conta debitado
- [ ] 5.6 Escrever `flows/invoices/03-reopen-paid-invoice.yaml`: estado da 02 → reabrir → asserta status volta e saldo da conta restaurado
- [ ] 5.7 Escrever `flows/installments/01-installment-distribution.yaml`: cartão → criar compra parcelada em N → asserta N parcelas distribuídas em faturas consecutivas
- [ ] 5.8 Escrever `flows/recurring/01-create-and-confirm.yaml`: criar recorrência → confirmar próxima ocorrência → asserta transação criada
- [ ] 5.9 Escrever `flows/recurring/02-skip-occurrence.yaml`: criar recorrência → pular → asserta nenhuma transação criada e próxima data avançou
- [ ] 5.10 Rodar suíte completa local + CI; medir tempo total de execução
- [ ] 5.11 Avaliar critério de promoção a gate de PR (≥ 2 semanas sem flaky, < 10 min total); se atendido, abrir change OpenSpec separada para promover

## 6. Encerramento

- [ ] 6.1 Atualizar `.maestro/README.md` com lista final de flows cobertos e tabela área → arquivos
- [ ] 6.2 Validar suíte verde 3 vezes consecutivas no CI antes de marcar a change como concluída
- [ ] 6.3 Rodar `openspec verify add-e2e-tests-maestro` e arquivar a change com `/opsx:archive` (ou sincronizar partes com `/opsx:sync` caso decisão de fasear o archive)
