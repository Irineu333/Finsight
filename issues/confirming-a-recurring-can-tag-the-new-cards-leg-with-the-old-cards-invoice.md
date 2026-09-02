---
area: recurring
severity: high
type: concurrency
---

# Confirmar recorrência pode gravar a perna do cartão novo com a dimensão da fatura do antigo

## Cenário

**DADO** uma recorrência de despesa apontada para o cartão A, e um cartão B na mesma moeda
**QUANDO** o usuário troca o cartão para B e toca em "Confirmar" antes de as faturas de B
terminarem de carregar — ou toca em "Confirmar" ao abrir a folha, antes da primeira emissão
**ENTÃO** é gravada uma transação cuja perna `LIABILITY` posta na conta do cartão B, mas
carregando o `dimensionId` de uma fatura do cartão A: a fatura de A soma uma compra que não
é dela, e a de B não a enxerga
**DEVERIA** nunca oferecer — e, na fronteira, recusar — uma fatura que não pertence ao
cartão da perna

## Mecânica

No `init`, a troca de cartão dispara a leitura das faturas e só **depois** do `suspend`
sobrescreve `selectedInvoice`. Até o retorno, o valor continua sendo a fatura do cartão
anterior, e `confirm()` lê exatamente esse valor.

Nada abaixo corrige: `LedgerEntryWriter` valida a **natureza** da conta em que a dimensão
pousa (`DimensionKind.INVOICE.landsOn == {LIABILITY}`) — a conta de B é `LIABILITY`, então
passa. `InvoiceWriteGuard` valida o **status** da fatura, não a que cartão ela pertence.

A base de código já conhece esse perigo e o fecha no fluxo irmão, com comentário explícito:
*"Cleared first so that no pair of the new card with the old card's invoice is ever
observed"*. A recorrência não recebeu o mesmo tratamento.

## Evidência

- `feature/recurring/impl/.../confirmRecurring/ConfirmRecurringViewModel.kt` — o
  `init { selectedCreditCard.collectLatest { ... } }`: lê as faturas antes de limpar a
  seleção; e `confirm()`, que usa `uiState.value.selectedInvoice`
- `feature/transactions/impl/.../addTransaction/AddTransactionViewModel.kt` —
  `selectCreditCard()`, que zera `selectedDueMonth` **antes** de trocar o cartão, com o
  comentário que nomeia esta corrida
- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` — usa
  `invoice.dimensionId` sem verificar de que cartão a fatura é
- `core/ledger/.../repository/LedgerEntryWriter.kt` — a checagem de pouso compara só
  `account.type` com `kind.landsOn`
- `feature/creditcards/impl/.../ledger/InvoiceWriteGuard.kt` — `ensureAccepts()` ramifica
  só por `isPaid` / `isClosed`

## Consequência

Total errado nos dois cartões, e o atalho "ver fatura" no detalhe abrindo o extrato do
cartão errado. Só se desfaz se o usuário perceber e editar a transação.

## Sugestão

Zerar `selectedInvoice` no momento da troca, como em `AddTransactionViewModel`, e — como
rede — recusar em `ConfirmRecurringUseCase` uma fatura cujo cartão não seja o alvo. Não
vinculante.

*O caminho de código está verificado; a janela de corrida não foi exercitada em
dispositivo.*
