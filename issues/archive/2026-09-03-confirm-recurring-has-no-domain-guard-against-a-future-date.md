---
area: recurring
severity: medium
type: data
verdict: fixed
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

## Desfecho

**Causa real** — a descrita, e confirmada no disco: `ConfirmRecurringUseCaseImpl.invoke` ia da
resolução do template direto para `val yearMonth = date.yearMonth` e para o piso de ciclo
(`cycleNumberIn`), e a `date` seguia para `TransactionIntent.date` e para
`RecurringOccurrence.effectiveDate` sem nenhuma comparação com hoje — apesar de o caso de uso já
receber um `Clock`, usado só para carimbar `handledAt`. A rede que o comentário do próprio arquivo
descreve existia para o piso de ciclo e para a moeda, e não para a data. O único freio real era
`confirmableDates()` (`.../confirmRecurring/ConfirmRecurringViewModel.kt`), que é de UI: quem não
passa pelo seletor — a ferramenta MCP `confirm_recurring` — entregava a data crua ao domínio.

**Mudança** — a guarda passou a viver no domínio, junto das outras duas recusas que existem
porque uma confirmação é a única escrita do app que chega ao ledger sem formulário, e antes da
resolução da fatura (que cria uma como efeito colateral fora da unidade de trabalho):
`if (date > clock.today()) throw RecurringException(RecurringError.DATE_IN_FUTURE)`
(`feature/recurring/impl/.../usecase/ConfirmRecurringUseCaseImpl.kt:95`), lendo `clock.today()` do
mesmo `Clock` que já estava no construtor. Membro novo `RecurringError.DATE_IN_FUTURE`
(`core/model/.../domain/error/RecurringError.kt:79`) com KDoc dizendo por que a recusa existe,
ramo próprio no `toUiText()` exaustivo (`:92`) e a chave `recurring_error_date_in_future` nos dois
`strings.xml` (pt `:1026`, en `:1025`). A `description` de `confirm_recurring`
(`feature/mcp/impl/.../tool/RecurringOperationTools.kt`) passou a anunciar o perímetro: "A cycle is
confirmed on a day that has already come, so a date in the future is refused, exactly as it is for
create_transaction". `confirmableDates()` fica como conveniência de UI, não como guardião.

**Prova** — arquivo novo `ConfirmRecurringDateTest` (3 testes), com o relógio parado no dia que
está sendo confirmado: `a cycle dated after today is refused and nothing is written` (recusa com
`DATE_IN_FUTURE` e nada gravado), `a cycle aimed at a card is refused before an invoice is opened
for it` (`invoices.calls == 0`) e `a cycle dated today still goes through` (a borda: hoje não é
futuro). Vermelho antes da guarda — `25 tests completed, 2 failed`, ambos em
`ConfirmRecurringDateTest` (`java.lang.AssertionError`: a confirmação era aceita e devolvia
`Right`) —, verde depois. Rodado com
`./gradlew :feature:recurring:impl:testDebugUnitTest --tests "*ConfirmRecurring*"`; a suíte inteira
do módulo segue verde (142 testes, 0 falhas), assim como `:feature:mcp:impl:jvmTest`. Os quatro
arquivos de teste de confirmação que já existiam confirmavam em 2026-03-05 com o `StoppedClock`
parado em 2026-01-01 — isto é, no futuro, sem que nada notasse; agora cada um declara o relógio no
próprio dia que confirma (`StoppedClock(date.atStartOfDayIn(TimeZone.currentSystemDefault()))`).

**Commit** — `Fix(Domain): hold the three date rules the screens were holding alone`
