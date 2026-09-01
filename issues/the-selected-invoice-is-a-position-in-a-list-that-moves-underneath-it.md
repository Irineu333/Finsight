---
area: creditcards
severity: medium
type: ux
---

# A fatura selecionada é uma posição numa lista que se move sozinha

## Cenário

**DADO** a tela de lançamentos da fatura, com a fatura aberta de agosto selecionada
**QUANDO** o usuário lança uma compra parcelada em 6x, que cria cinco faturas `FUTURE` de
setembro a janeiro
**ENTÃO** a tela passa a exibir a fatura de **janeiro** — outros lançamentos, outro total, e
os botões daquele status no lugar de "Fechar fatura" — sem que o usuário tenha navegado
**DEVERIA** continuar na fatura que estava selecionada, como a tela de cartões já faz com o
cartão

## Mecânica

A seleção é guardada como **posição**: `selectedInvoiceIndex` é um `Int` combinado com a lista
viva de faturas, e a lista é ordenada por `openingMonth DESC` — toda fatura de mês posterior
entra na posição 0 e empurra as demais. O índice continua o mesmo e passa a apontar para
outra fatura.

O padrão certo está no arquivo vizinho: `CreditCardsViewModel` guarda `selectedCardId`, uma
**identidade**, e recalcula o índice com `indexOfFirst { it.id == selectedCardId }` sempre que
a lista muda.

## Evidência

- `feature/creditcards/impl/.../invoiceTransactions/InvoiceTransactionsViewModel.kt` —
  `selectedInvoiceIndex = MutableStateFlow(0)`, combinado com a lista e resolvido por
  `invoices.getOrNull(index)`; as ações de seleção também gravam índice
- `core/database/.../dao/InvoiceDao.kt` — `observeInvoicesByCreditCard()`:
  `ORDER BY openingMonth DESC`
- `feature/creditcards/impl/.../usecase/AddInstallmentUseCaseImpl.kt` — `getInvoices()`, que
  cria as faturas futuras que entram no topo
- contraste: `feature/creditcards/impl/.../creditCards/CreditCardsViewModel.kt` —
  `selectedCardId` e o `indexOfFirst { it.id == selectedCardId }`

## Consequência

O usuário age sobre uma fatura sem saber que trocou de fatura — e as ações oferecidas mudam
junto, porque dependem do status. Fechar ou pagar a fatura errada está a um toque.

## Sugestão

Guardar `selectedInvoiceId` e derivar a posição do pager a partir dele, como o cartão faz.
Não vinculante.
