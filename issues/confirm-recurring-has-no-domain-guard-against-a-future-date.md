---
area: recurring
severity: medium
type: data
---

# Confirmar um ciclo recorrente não recusa data futura — a regra vive só na tela

## Cenário

**DADO** um recurring de receita (ciclo do dia 5, R$6.000, conta Carteira) criado em
2026-09-02
**QUANDO** `confirm_recurring` é chamado passando `date=2026-09-05` — três dias no futuro
**ENTÃO** o ciclo é confirmado sem erro e a transação é gravada no ledger datada de
2026-09-05
**DEVERIA** recusar a confirmação com data futura, como `create_transaction` recusa
(`BuildTransactionError.DateFuture`)

## Mecânica

`create_transaction` passa por `RegisterTransactionUseCaseImpl` →
`BuildTransactionUseCaseImpl` → `ValidateTransactionFormUseCaseImpl`, que recusa:
`ensure(date <= clock.today()) { BuildTransactionError.DateFuture }`.
`ConfirmRecurringUseCaseImpl.invoke` não tem guarda equivalente: a `date` recebida é usada
direto para derivar `yearMonth` e como `effectiveDate` da entrada gravada; a única checagem
envolvendo data é `cycleNumberIn`, que recusa **meses anteriores** ao início da série, não
datas **futuras**.

O próprio código admite a lacuna: o comentário do parâmetro `date` diz que o seletor de
datas da tela de confirmação é o que torna uma data futura inalcançável pelo caminho
desenhado — "the net behind it". Essa rede não existe no domínio: o único freio real é
`ConfirmRecurringViewModel.confirmableDates()`, que limita apenas o intervalo oferecido no
seletor da UI. A ferramenta MCP `confirm_recurring` não passa por esse seletor — ela lê a
data do argumento e entrega direto ao caso de uso.

## Evidência

- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCaseImpl.kt` — `invoke()`: `date`
  usada direto (`yearMonth`, `effectiveDate`), sem `ensure` contra o futuro
- `feature/transactions/impl/.../usecase/ValidateTransactionFormUseCaseImpl.kt:48` —
  `ensure(date <= clock.today()) { BuildTransactionError.DateFuture }`, a guarda que falta
  do outro lado
- `feature/recurring/impl/.../ui/modal/confirmRecurring/ConfirmRecurringViewModel.kt:308-325`
  — `confirmableDates()`, o único freio existente, e é de UI
- `feature/mcp/impl/.../tool/RecurringOperationTools.kt` — `ConfirmRecurringTool.call`,
  linha 120 (`date` lida sem checagem) e linhas 202-211 (repassada direto ao caso de uso)
- `feature/mcp/impl/.../tool/TransactionWriteTools.kt` — `CreateTransactionTool`: a
  descrição do parâmetro `date` anuncia "never in the future"; a garantia vem inteiramente
  de `ValidateTransactionFormUseCaseImpl`, não da ferramenta

## Consequência

Repro real desta sessão: o recurring "Salário" (dia 5, conta Carteira) foi confirmado com
`date=2026-09-05` estando "hoje" em 2026-09-02 — a chamada foi aceita e gravou a transação
no futuro, sem erro. Isso quebra, pela via do MCP, a suposição que o resto do app faz — e
que `create_transaction` impõe — de que nenhuma transação comum carrega data futura.
Qualquer chamador do domínio que não passe pelo seletor de datas da UI (o MCP hoje, um
teste ou um novo caso de uso amanhã) contorna a restrição por completo.

Um efeito colateral confirmado: a transação resultante some da seção "Recentes" da
dashboard. `DashboardComponentsBuilder.recents()` filtra deliberadamente
`it.date <= input.today` (regra introduzida no commit `48d79d3b5`, para excluir parcelas
futuras dali) — o filtro está correto; é a transação que não deveria ter uma data futura
para começar. Corrigido este bug, o sintoma na dashboard desaparece sem tocar em
`recents()`.

## Sugestão

Mover `ensure(date <= clock.today())` (reaproveitando ou estendendo
`BuildTransactionError.DateFuture` com um erro próprio do domínio de recurring) para
`ConfirmRecurringUseCaseImpl`, deixando `confirmableDates()` como conveniência de UI e não
como guardião. Não vinculante.
