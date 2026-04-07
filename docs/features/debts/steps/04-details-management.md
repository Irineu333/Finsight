# Etapa 04 — Detalhes, edição, exclusão e reabertura

> Parte do plano: [Dívidas](../plan.md)

---

## O que fazer

Implementar a visualização detalhada de uma dívida (histórico de pagamentos e ocorrências de parcelas) e as operações de gerenciamento: editar, excluir e reabrir.

**Use cases (domínio):**
- `GetDebtDetailsUseCase` — recebe `debtId: Long`; retorna `Flow<DebtDetails>` com: `debt: Debt`, `balance: Double`, `payments: List<OperationSummary>` (operações vinculadas à dívida via `debtId`), `occurrences: List<DebtInstallmentOccurrence>`
- `UpdateDebtUseCase` — valida (nome, valor se sem pagamentos) e atualiza via `IDebtRepository.update()`; bloqueia alteração de `totalAmount` se existem pagamentos (`payments.isNotEmpty()`); retorna `Either<DebtError, Unit>`
- `DeleteDebtUseCase` — exclui a dívida via `IDebtRepository.delete(id)`; as operações vinculadas **não são excluídas** (por causa do `SET_NULL` em `debtId`); retorna `Either<Throwable, Unit>`
- `ReopenDebtUseCase` — atualiza `debt.status = ACTIVE` via `IDebtRepository.update()`; retorna `Either<Throwable, Unit>`

**Modelo de apresentação:**
- `DebtDetails` — data class com: `debt: Debt`, `balance: Double`, `paidAmount: Double`, `payments: List<OperationSummary>`, `occurrences: List<DebtInstallmentOccurrence>`
- `OperationSummary` — modelo leve com: `id`, `amount`, `date`, `accountName` (para exibição no histórico)

**UI — DebtDetailsModal (ou tela):**
- Acessado ao tocar em um card de dívida (não no botão de pagamento)
- Exibe: nome, credor, valor total, saldo devedor, data de início, data de vencimento global (se informada)
- Seção "Plano de parcelas" (se configurado): quantidade, valor estimado por parcela, dia do mês
- Seção "Histórico de pagamentos": lista de pagamentos com data, valor e conta de origem; ordenada da mais recente para a mais antiga
- Seção "Ocorrências de parcelas": lista de ocorrências (CONFIRMED / IGNORED) com ciclo, data e status
- Ações disponíveis conforme status:
  - Dívida ATIVA: botão "Editar" e botão "Excluir"
  - Dívida PAGA: botão "Reabrir" e botão "Excluir"
- Editar → abre `DebtFormModal` em modo edição; campo de valor total desabilitado se há pagamentos
- Excluir → exibe modal de confirmação antes de executar; ao confirmar, fecha o modal de detalhes
- Reabrir → executa `ReopenDebtUseCase` diretamente e fecha o modal

**UI — DebtsScreen (atualização):**
- Tocar no card (área não coberta pelos botões de pagamento) → abre `DebtDetailsModal`

**DI:**
- `UseCaseModule` — adicionar `GetDebtDetailsUseCase`, `UpdateDebtUseCase`, `DeleteDebtUseCase`, `ReopenDebtUseCase` como `factory {}`

**Strings:**
- `debts_details_title`, `debts_details_payments_history`, `debts_details_occurrences_history`, `debts_details_installment_plan`, `debts_details_edit`, `debts_details_delete`, `debts_details_reopen`, `debts_details_delete_confirm_title`, `debts_details_delete_confirm_message`, `debts_details_no_payments`, `debts_details_occurrence_confirmed`, `debts_details_occurrence_ignored`, `debts_form_title_edit`, `debts_form_total_amount_locked`

---

## Arquivos afetados

**Criar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/model/DebtDetails.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/model/OperationSummary.kt` (se não existir)
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/GetDebtDetailsUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/UpdateDebtUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/DeleteDebtUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ReopenDebtUseCase.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/debtDetails/DebtDetailsModal.kt`

**Modificar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/debtForm/DebtFormModal.kt` — suportar modo edição e desabilitar campo de valor quando há pagamentos
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsScreen.kt` — tocar no card abre `DebtDetailsModal`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsAction.kt` — adicionar `OpenDetails(debt: DebtWithBalance)`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/UseCaseModule.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

---

## Critério de aceite

**Validação manual:**
1. Tocar em um card de dívida → modal de detalhes abre com: nome, credor, valor total, saldo devedor, data de início
2. Dívida com pagamentos registrados → seção "Histórico de pagamentos" exibe cada pagamento com data, valor e conta
3. Dívida com plano de parcelas e ocorrências → seção "Ocorrências de parcelas" exibe cada ocorrência com status CONFIRMADA / IGNORADA
4. Tocar em "Editar" → `DebtFormModal` abre em modo edição com campos preenchidos
5. Editar nome e credor → salvar → detalhes atualizam com novos dados
6. Tentar alterar valor total em dívida com pagamentos → campo de valor está desabilitado (ou exibe mensagem)
7. Tocar em "Excluir" → modal de confirmação aparece; confirmar → dívida some da lista; navegar para Transações → pagamentos anteriores da dívida ainda estão presentes nas contas
8. Dívida PAGA → detalhe exibe botão "Reabrir"; tocar → status volta para ATIVA; dívida aparece na lista ativa; histórico de pagamentos preservado
9. Dívida ATIVA → detalhe não exibe botão "Reabrir"

**Revisão de código:**
- [ ] `UpdateDebtUseCase` bloqueia alteração de `totalAmount` se `payments.isNotEmpty()`, retornando `DebtError`
- [ ] `DeleteDebtUseCase` não exclui operações vinculadas (o `SET_NULL` em `debtId` preserva as transações)
- [ ] `ReopenDebtUseCase` apenas muda `status = ACTIVE`, sem apagar histórico de pagamentos
- [ ] `DebtDetailsModal` exibe modal de confirmação antes de executar exclusão (padrão do projeto)
- [ ] `GetDebtDetailsUseCase` é um Flow reativo — detalhes atualizam automaticamente após pagamento ou edição
- [ ] `OperationSummary` (ou equivalente) pertence ao domínio, sem dependências de framework

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
