# Etapa 03 — Parcelas pendentes

> Parte do plano: [Dívidas](../plan.md)

---

## O que fazer

Implementar o plano de parcelas na criação de dívidas e a detecção/tratamento de parcelas pendentes. Uma parcela é pendente quando a dívida está ATIVA, o dia de vencimento do mês corrente já chegou ou passou, e não existe ocorrência (CONFIRMED ou IGNORED) para aquele ciclo.

**Use cases (domínio):**
- `GetPendingDebtInstallmentsUseCase` — recebe `debts: List<Debt>`, `occurrences: List<DebtInstallmentOccurrence>`, `today: LocalDate`; retorna `Map<Long, Int>` (debtId → número da parcela pendente); calculado em memória, sem status PENDING no banco
  - Lógica de ciclo: ciclo N = meses decorridos desde `debt.startDate.yearMonth` até `today.yearMonth` + 1 (mês 1 = mês de criação)
  - Uma parcela é pendente quando: `debt.status == ACTIVE`, `installmentPlan != null`, `today.day >= installmentPlan.dayOfMonth` (ou o mês tem menos dias que o dia configurado — usar último dia do mês), `cycleNumber <= installmentPlan.count`, e não existe ocorrência com esse `(debtId, cycleNumber)`
- `ConfirmDebtInstallmentUseCase` — recebe `debt: Debt`, `cycleNumber: Int`, `amount: Double`, `date: LocalDate`, `account: Account`:
  1. Verifica se já existe ocorrência CONFIRMED para esse ciclo → erro se sim
  2. Cria Operation de EXPENSE via `IOperationRepository` com `debtId` preenchido
  3. Salva `DebtInstallmentOccurrence(status=CONFIRMED, operationId=operation.id, ...)` via `IDebtInstallmentOccurrenceRepository`
  4. Recalcula saldo devedor; se zero → atualiza debt para PAID
  5. Retorna `Either<Throwable, Unit>`
- `IgnoreDebtInstallmentUseCase` — recebe `debt: Debt`, `cycleNumber: Int`, `yearMonth: YearMonth`:
  1. Salva `DebtInstallmentOccurrence(status=IGNORED, operationId=null, ...)` via `IDebtInstallmentOccurrenceRepository`
  2. Retorna `Either<Throwable, Unit>`

**UI — DebtFormModal (atualização):**
- Adicionar seção "Plano de parcelas" com toggle; quando ativo: campo quantidade de parcelas (Int > 0) e campo dia do mês de vencimento (1–31)
- Exibir label calculado: "Estimativa: R$ X,XX por parcela" (totalAmount / count)

**UI — DebtsScreen / DebtCard (atualização):**
- Quando uma dívida tem parcela pendente: exibir badge/indicador no card (ex: "Parcela N pendente")
- Botões de ação direta: "Confirmar" e "Ignorar" visíveis no card (ou expandíveis)
- "Confirmar" → abre `ConfirmDebtInstallmentModal`
- "Ignorar" → chama `IgnoreDebtInstallmentUseCase` diretamente (sem modal de confirmação)

**UI — ConfirmDebtInstallmentModal:**
- Modal `ModalBottomSheet` com: valor (padrão = totalAmount / installmentCount, editável), data (padrão = hoje), conta (obrigatório)
- Mesmas validações de `PayDebtModal`
- Ao confirmar: executa `ConfirmDebtInstallmentUseCase`

**UI — DebtsViewModel (atualização):**
- Integrar `GetPendingDebtInstallmentsUseCase` ao Flow principal: combinar `IDebtRepository.getAll()` + `IDebtInstallmentOccurrenceRepository.getByDebt()` + `GetPendingDebtInstallmentsUseCase`
- `DebtsUiState.Content` recebe `pendingInstallments: Map<Long, PendingInstallment>` (debtId → dados da parcela pendente)

**DI:**
- `UseCaseModule` — adicionar os três novos use cases como `factory {}`

**Strings:**
- `debts_form_installment_plan`, `debts_form_installment_count`, `debts_form_installment_day`, `debts_form_installment_estimate`, `debts_installment_pending`, `debts_installment_confirm`, `debts_installment_ignore`, `debts_installment_confirm_title`, `debts_installment_number`

---

## Arquivos afetados

**Criar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/GetPendingDebtInstallmentsUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ConfirmDebtInstallmentUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/IgnoreDebtInstallmentUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/confirmDebtInstallment/ConfirmDebtInstallmentModal.kt`

**Modificar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/debtForm/DebtFormModal.kt` — adicionar seção de plano de parcelas
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsUiState.kt` — adicionar `pendingInstallments`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsAction.kt` — adicionar `ConfirmInstallment`, `IgnoreInstallment`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsViewModel.kt` — integrar use cases de parcelas
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsScreen.kt` — exibir badge de pendência e botões
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/UseCaseModule.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

---

## Critério de aceite

**Validação manual:**
1. Criar dívida com plano de 12 parcelas, dia 5, valor R$ 1.200,00 → formulário exibe "Estimativa: R$ 100,00 por parcela"
2. Com data atual em que o dia 5 já passou no mês corrente → dívida aparece na lista com indicador "Parcela 1 pendente"
3. Tocar em "Confirmar" → modal abre com valor padrão R$ 100,00 (editável), data de hoje, seletor de conta
4. Alterar valor para R$ 120,00, selecionar conta, confirmar → transação de despesa de R$ 120,00 gerada; saldo devedor reduz de R$ 1.200,00 para R$ 1.080,00; indicador de pendência some do card
5. Tocar em "Ignorar" em outra dívida com parcela pendente → nenhuma transação gerada; indicador some; saldo devedor inalterado
6. Registrar pagamento livre em dívida com parcela pendente → saldo reduz; parcela pendente continua visível no card
7. Criar dívida com plano de parcelas e pagar o valor total via pagamento livre → dívida passa para PAGA; parcelas futuras não aparecem mais como pendentes
8. Criar dívida sem plano de parcelas → nenhum indicador de pendência aparece

**Revisão de código:**
- [ ] `GetPendingDebtInstallmentsUseCase` é calculado em memória — sem coluna `status = PENDING` no banco
- [ ] Lógica de ciclo: ciclo N = meses decorridos desde `startDate.yearMonth` até `today.yearMonth` + 1
- [ ] `ConfirmDebtInstallmentUseCase` cria `Operation` + `DebtInstallmentOccurrence(CONFIRMED)`, análogo a `ConfirmRecurringUseCase`
- [ ] `IgnoreDebtInstallmentUseCase` cria apenas `DebtInstallmentOccurrence(IGNORED)`, sem `Operation`
- [ ] Dívidas PAGAS não geram parcelas pendentes (verificação em `GetPendingDebtInstallmentsUseCase`)
- [ ] `ConfirmDebtInstallmentModal` estende `ModalBottomSheet`
- [ ] O ViewModel combina os Flows reativamente — não chama `getAll()` após cada ação

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
