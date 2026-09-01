---
area: creditcards
severity: critical
type: data
---

# As datas de uma fatura seguem o cartão de hoje, não o ciclo em que ela foi emitida

## Cenário

**DADO** um cartão com `closingDay=5` e `dueDay=20`, com faturas `CLOSED` e `PAID` no
histórico
**QUANDO** o usuário edita o cartão para `closingDay=28` porque o banco mudou o ciclo
**ENTÃO** todas as faturas passadas passam a exibir outras datas de abertura, fechamento
e vencimento; uma fatura `CLOSED` pode passar a fechar no futuro, uma `PAID` fica com
`paidAt` fora da própria janela, e abrir "Pagar fatura" ou "Antecipar pagamento" nessas
faturas lança durante a composição
**DEVERIA** o ciclo de uma fatura já emitida ser fato gravado: a mudança vale do ciclo
seguinte em diante, e as faturas existentes mantêm as datas com que foram fechadas e pagas

## Mecânica

`InvoiceEntity` persiste apenas os **meses** — `openingMonth`, `closingMonth`, `dueMonth`.
Os **dias** não são persistidos em lugar nenhum: `Invoice.openingDate` e
`Invoice.closingDate` saem de `Invoice.window`, que monta `InvoiceWindow(closingDay =
creditCard.closingDay)`, e `Invoice.dueDate` é `dueMonth.safeOnDay(creditCard.dueDay)`.
O `creditCard` aí é a linha viva, relida de `credit_cards` a cada leitura. Editar o cartão
reescreve retroativamente a data de todo o histórico.

`UpdateCreditCardUseCase` valida só o nome, e o formulário aplica o `block` com
`closingDay` e `dueDay` sem nenhuma guarda — embora o mesmo formulário saiba travar um
campo em edição, e o faça para a moeda (`canChangeCurrency = !isEditMode`).

O crash é aritmética de `coerceIn`, que exige `min <= max`: com o fechamento empurrado
para depois de hoje, `maxDate` fica antes de `closingDate` e a faixa nasce vazia.

Pior que as datas: `CreditCard.duePostponed` (`dueDay < closingDay`) decide se `dueMonth`
é igual a `closingMonth` ou o mês seguinte. Inverter essa relação faz `dueMonthFor()` e
`invoiceWindowFor()` passarem a responder outro mês, enquanto as faturas gravadas mantêm
o mapeamento antigo — e os índices únicos de `InvoiceEntity` passam a poder colidir.

## Evidência

- `core/database/.../entity/InvoiceEntity.kt` — persiste `openingMonth`/`closingMonth`/
  `dueMonth`; nenhum campo de dia, e três índices únicos por `(creditCardId, <mês>)`
- `core/model/.../model/Invoice.kt` — `Invoice.openingDate` / `.closingDate` / `.dueDate`,
  todas derivadas em leitura a partir de `creditCard`
- `core/model/.../model/InvoiceWindow.kt` — `Invoice.window` lê `creditCard.closingDay`;
  `CreditCard.duePostponed` / `invoiceWindowFor()` / `dueMonthFor()`
- `feature/creditcards/impl/.../usecase/UpdateCreditCardUseCase.kt` — valida só o nome;
  o KDoc trata apenas da imutabilidade da moeda
- `feature/creditcards/impl/.../creditCardForm/CreditCardFormViewModel.kt` — o ramo de
  edição aplica `it.copy(closingDay = ..., dueDay = ...)`; `canChangeCurrency = !isEditMode`
  é a trava que existe para o campo vizinho
- `feature/creditcards/impl/.../payInvoice/PayInvoiceModal.kt` —
  `currentDate.coerceIn(invoice.closingDate, maxDate)` com
  `maxDate = invoice.dueDate.coerceAtMost(currentDate)`
- `feature/creditcards/impl/.../advancePayment/AdvancePaymentModal.kt` — mesma construção
  sobre `openingDate` / `closingDate`

## Consequência

O histórico contábil já liquidado passa a mentir sobre quando fechou e quando venceu, e
não há como recuperar a verdade: o dado nunca foi gravado. A máquina de estados perde a
premissa de que `CLOSED` implica fechamento já ocorrido, e as duas telas de pagamento
deixam de abrir.

## Sugestão

Persistir os dias na `InvoiceEntity` (ou congelar as três datas) e fazer `Invoice.window`
ler o gravado. Alternativa mais barata: recusar a mudança dos dias quando o cartão já tem
faturas. Em qualquer caso os dois `coerceIn` precisam virar construção total — hoje eles
transformam um dado inconsistente em crash. Não vinculante.
