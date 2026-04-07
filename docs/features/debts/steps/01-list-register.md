# Etapa 01 — Listar e cadastrar dívida

> Parte do plano: [Dívidas](../plan.md)

---

## O que fazer

Implementar o fluxo completo de criação e listagem de dívidas. Ao final desta etapa, o usuário consegue criar dívidas (com ou sem lançamento inicial) e vê-las na lista com saldo devedor e progresso.

**Use cases (domínio):**
- `ValidateDebtUseCase` — valida nome (não vazio) e valor total (> 0); retorna `Either<DebtError, Unit>`
- `CalculateDebtBalanceUseCase` — recebe `Debt` e lista de `operationIds` vinculados; retorna `Double` (saldo devedor = totalAmount − Σ pagamentos); usa `ITransactionRepository` para somar os valores
- `SaveDebtUseCase` — valida (via `ValidateDebtUseCase`), persiste via `IDebtRepository`; se `initialAccount != null`, cria uma Operation de RECEITA via `IOperationRepository` com `debtId` preenchido e data = `debt.startDate`; retorna `Either<DebtError, Debt>`
- `GetDebtsWithBalanceUseCase` — combina `IDebtRepository.getAll()` com `CalculateDebtBalanceUseCase` para emitir `List<DebtWithBalance>` (modelo de apresentação com `debt`, `balance`, `paidAmount`); é um Flow

**Modelo de apresentação:**
- `DebtWithBalance` — data class com: `debt: Debt`, `balance: Double`, `paidAmount: Double`; calculada em use case, nunca no ViewModel

**UI — DebtsScreen:**
- Atualizar `DebtsUiState` para: `Loading`, `Empty`, `Content(active: List<DebtWithBalance>, paid: List<DebtWithBalance>, showPaid: Boolean)`
- Atualizar `DebtsAction` para: `ToggleShowPaid`
- `DebtsScreen` — lista de dívidas ativas; seção de pagas colapsável (oculta por padrão, revelada ao tocar em "Mostrar pagas"); FAB para criar nova dívida; estado vazio com botão de criar
- `DebtCard` — componente que exibe: nome, credor, valor total, saldo devedor, barra de progresso de pagamento (paidAmount / totalAmount)

**UI — DebtFormModal:**
- Formulário com: nome (obrigatório), credor (opcional), valor total (obrigatório), data de início (default = hoje), data de vencimento global (opcional)
- Toggle "Registrar lançamento inicial" — quando ativo, exibe seletor de conta
- Validação inline: nome vazio → "Informe o nome da dívida"; valor inválido → "O valor deve ser maior que zero"
- Ao confirmar com sucesso: fecha o modal

**DI:**
- `UseCaseModule` — adicionar `ValidateDebtUseCase`, `CalculateDebtBalanceUseCase`, `SaveDebtUseCase`, `GetDebtsWithBalanceUseCase` como `factory {}`
- `ViewModelModule` — `DebtsViewModel` já registrado na Etapa 00; atualizar se necessário

**Strings:**
- `debts_screen_empty`, `debts_screen_create`, `debts_form_title_new`, `debts_form_name`, `debts_form_creditor`, `debts_form_total_amount`, `debts_form_start_date`, `debts_form_due_date`, `debts_form_initial_transaction`, `debts_form_select_account`, `debts_card_balance`, `debts_card_paid`, `debts_show_paid`, `debts_error_empty_name`, `debts_error_invalid_amount`

---

## Arquivos afetados

**Criar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/model/DebtWithBalance.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ValidateDebtUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/CalculateDebtBalanceUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/SaveDebtUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/GetDebtsWithBalanceUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/debtForm/DebtFormModal.kt`

**Modificar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsUiState.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsAction.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsScreen.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/UseCaseModule.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

---

## Critério de aceite

**Validação manual:**
1. Navegar para a tela de Dívidas → estado vazio com botão "Nova dívida"
2. Tocar no FAB ou no botão → modal de formulário abre
3. Confirmar sem preencher nome → erro "Informe o nome da dívida" exibido, modal não fecha
4. Preencher nome + valor zero → erro "O valor deve ser maior que zero"
5. Preencher nome "Empréstimo banco", credor "Banco X", valor 1200,00, sem lançamento inicial → confirmar → dívida aparece na lista com saldo devedor = R$ 1.200,00 e progresso 0%
6. Criar segunda dívida com lançamento inicial (conta "Conta Corrente") → dívida aparece na lista; navegar para Transações → receita de R$ [valor] aparece na conta selecionada com data = data de início da dívida
7. Tela de Dívidas com dívidas ativas → seção "Pagas" não visível por padrão; tocar em "Mostrar pagas" → seção aparece (vazia inicialmente)

**Revisão de código:**
- [ ] `CalculateDebtBalanceUseCase` calcula `totalAmount − Σ pagamentos` sem lógica no ViewModel
- [ ] `SaveDebtUseCase` usa `IOperationRepository.createOperation()` para o lançamento inicial (RECEITA), com `debtId` preenchido
- [ ] `GetDebtsWithBalanceUseCase` é um Flow reativo — o ViewModel coleta com `collectAsState` ou equivalente
- [ ] `DebtFormModal` estende `ModalBottomSheet`, usa `LocalModalManager`
- [ ] `DebtsUiState.Content` separa `active` e `paid` — o ViewModel não filtra no View
- [ ] Dívidas PAGAS só aparecem quando `showPaid = true`
- [ ] Use cases registrados como `factory {}` no Koin

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
