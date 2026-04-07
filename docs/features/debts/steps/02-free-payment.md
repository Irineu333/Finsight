# Etapa 02 — Registrar pagamento livre

> Parte do plano: [Dívidas](../plan.md)

---

## O que fazer

Implementar o registro de pagamento manual sobre uma dívida ativa. Um pagamento gera uma transação de DESPESA na conta selecionada e reduz o saldo devedor. Se o saldo chegar a zero, a dívida passa automaticamente para status PAGA.

**Use case (domínio):**
- `PayDebtUseCase` — recebe `debt: Debt`, `amount: Double`, `date: LocalDate`, `account: Account`:
  1. Valida: `amount <= 0` → `DebtPaymentError.InvalidAmount`; `amount > saldo devedor atual` → `DebtPaymentError.ExceedsBalance`; `account` ausente → `DebtPaymentError.NoAccountSelected`
  2. Cria uma `Operation` de `Transaction.Type.EXPENSE` via `IOperationRepository.createOperation()` com `debtId` preenchido e `sourceAccountId = account.id`
  3. Recalcula o saldo devedor (via `CalculateDebtBalanceUseCase`) incluindo o novo pagamento
  4. Se o novo saldo == 0: chama `IDebtRepository.update(debt.copy(status = Debt.Status.PAID))`
  5. Retorna `Either<DebtPaymentError, Unit>`

**UI — PayDebtModal:**
- Modal `ModalBottomSheet` com: campo de valor (padrão = saldo devedor atual, editável), seletor de data (padrão = hoje), seletor de conta (obrigatório)
- Validação inline: valor inválido → "Valor inválido"; sem conta → "Selecione uma conta de origem"; valor acima do saldo → "Valor inválido"
- Ao confirmar com sucesso: fecha o modal

**UI — DebtsScreen / DebtCard:**
- Adicionar botão/ação "Registrar pagamento" em cada card de dívida ativa (ex: botão ou swipe action)
- Ao tocar: abre `PayDebtModal(debt = ...)` via `LocalModalManager`
- Após pagamento: lista reativa atualiza automaticamente (Flow); se dívida passou para PAGA, some da lista ativa e aparece em "Pagas"

**DI:**
- `UseCaseModule` — adicionar `PayDebtUseCase` como `factory {}`

**Strings:**
- `debts_payment_title`, `debts_payment_amount`, `debts_payment_date`, `debts_payment_account`, `debts_payment_confirm`, `debts_payment_error_invalid_amount`, `debts_payment_error_no_account`, `debts_register_payment`

---

## Arquivos afetados

**Criar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/PayDebtUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/payDebt/PayDebtModal.kt`

**Modificar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsScreen.kt` — adicionar ação de pagamento no card
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsAction.kt` — adicionar `PayDebt(debt: DebtWithBalance)`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsViewModel.kt` — tratar `PayDebt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/UseCaseModule.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

---

## Critério de aceite

**Validação manual:**
1. Com uma dívida ativa de R$ 1.200,00 → tocar em "Registrar pagamento" → modal abre com valor padrão R$ 1.200,00
2. Alterar para R$ 300,00, selecionar conta, confirmar → saldo devedor na lista passa para R$ 900,00; navegar para Transações → despesa de R$ 300,00 aparece na conta selecionada
3. Tentar confirmar sem selecionar conta → erro "Selecione uma conta de origem", modal não fecha
4. Tentar confirmar com valor R$ 0,00 → erro "Valor inválido"
5. Tentar confirmar com valor acima do saldo devedor (ex: R$ 2.000,00 numa dívida de R$ 900,00) → erro "Valor inválido"
6. Registrar pagamento que zera o saldo (R$ 900,00 restantes → pagar R$ 900,00) → modal fecha; dívida some da lista ativa; aparece em "Mostrar pagas"; transação de despesa gerada normalmente
7. Com dívida PAGA: não deve haver botão de registrar pagamento visível

**Revisão de código:**
- [ ] `PayDebtUseCase` usa `IOperationRepository.createOperation()` com `Transaction.Type.EXPENSE` e `debtId` preenchido
- [ ] A transição para PAGA ocorre dentro do use case, não no ViewModel
- [ ] O saldo devedor é recalculado após o pagamento via `CalculateDebtBalanceUseCase` (não mantido em estado local)
- [ ] `PayDebtModal` estende `ModalBottomSheet`, usa `LocalModalManager`
- [ ] A lista reativa (Flow) atualiza automaticamente sem chamar `getAll()` manualmente

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
