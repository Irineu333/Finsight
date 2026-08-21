---
area: recurring
severity: medium
type: data
---

# A fatura de fallback da recorrência é buscada pelo mês da compra, não pelo de vencimento

## Invariante

A fatura que recebe uma confirmação no cartão é aquela cuja janela admite a data da compra.

Hoje é falso no caminho de fallback: `ConfirmRecurringUseCase` passa `date.yearMonth` — o
mês da **compra** — a um parâmetro que se chama `targetDueMonth` e significa mês de
**vencimento**.

## Mecânica

Para um cartão com vencimento postergado (`dueDay < closingDay`), a fatura cujo `dueMonth`
é o mês da compra fecha no mês **anterior**: sua janela não contém a data da compra. Se ela
não existir, é criada — e nasce `RETROACTIVE`, que aceita gasto novo, então a escrita passa
em silêncio.

A conversão correta de data para fatura existe e está escrita:
`dueMonthFor(invoiceWindowOn(date).closingMonth)`. O chamador irmão usa o parâmetro como
ele foi declarado, passando `form.invoiceDueMonth`.

## Evidência

- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` — `invoke()`:
  `getOrCreateInvoiceForMonthUseCase(targetCreditCard, yearMonth)` com
  `yearMonth = date.yearMonth`
- `feature/creditcards/api/.../usecase/GetOrCreateInvoiceForMonthUseCase.kt` — o parâmetro
  é `targetDueMonth: YearMonth`
- `feature/transactions/impl/.../usecase/BuildTransactionUseCaseImpl.kt` — o uso correto:
  passa `form.invoiceDueMonth`
- `core/model/.../model/InvoiceWindow.kt` — `invoiceWindowFor(dueMonth)`,
  `invoiceWindowOn(date)` e `dueMonthFor(closingMonth)`: a regra de conversão
- `feature/creditcards/impl/.../usecase/CreateInvoiceUseCase.kt` — o status derivado como
  `RETROACTIVE` quando o vencimento é anterior ao da fatura aberta
- `feature/recurring/impl/src/commonTest/.../ConfirmRecurringOverridesTest.kt` — o stub
  lança `NotImplementedError("every card test here passes the invoice in")`: o fallback não
  tem cobertura

## Consequência

Gasto lançado numa fatura de outro ciclo, possivelmente já vencida, e uma fatura retroativa
criada sem o usuário ter pedido.

**Alcance:** hoje o fallback só é atingível na mesma janela de
`confirming-a-recurring-can-tag-the-new-cards-leg-with-the-old-cards-invoice`, porque todo
cartão nasce com uma fatura `OPEN`. É um defeito latente — qualquer chamador futuro que
omita a fatura cai nele.

## Sugestão

Derivar o mês pela regra existente em vez de passar `date.yearMonth`. Não vinculante.
