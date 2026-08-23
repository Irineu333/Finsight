## Why

Um pagamento parcial de fatura é hoje a única operação corrigível *por natureza* que não tem
correção: o razão aceita reescrevê-la, o detalhe da transação já oferece removê-la, e mesmo assim
o botão de editar não aparece. O que o impede é uma linha —
`TransactionLabel.PAYMENT -> false` (`ViewTransactionUiState.kt:164`) — cujo próprio comentário
antecipa esta change: mantida por mais tempo, *"out of scope" quietly becomes "we forgot"*.

Sem ela, corrigir R$ 300 digitados como R$ 3.000 obriga a apagar e refazer — que é exatamente o
gesto que `transfer-editing` existe para dispensar, pela mesma razão e sobre a mesma fronteira de
escrita.

## What Changes

- O pagamento **parcial** de fatura passa a ser corrigível no lugar, pelo mesmo formulário que o
  cria, distinguido apenas pelo que anuncia — o verbo do botão passa a ser "salvar".
- A correção alcança tudo o que define a operação: cartão, fatura, conta pagadora, valor que
  liquida, valor que sai e data. Nenhum desses campos é identidade da operação, e nenhum é
  congelado.
- **O modo é fixo na correção.** Corrigir um pagamento parcial é reafirmar um pagamento parcial:
  o conjunto de faturas oferecido passa a ser o de `acceptsPartialPayment`, e não o de
  `acceptsPayment` que a criação usa. Sem isso, trocar para uma fatura `CLOSED` produziria uma
  quitação de fato que nada marcaria `PAID` — e o razão a deixaria passar, porque a escrita nova
  liquida um passivo e o guard só recusa gasto novo.
- **O teto do valor passa a desconsiderar a própria operação.** Hoje o teto é o devido corrente,
  que já foi reduzido pelo pagamento sendo corrigido: um parcial de R$ 300 numa fatura de R$ 800
  não pode ser corrigido para R$ 700, porque o devido corrente é R$ 500. A regra passa a ser *o
  devido da fatura, desconsiderando esta operação* — uma fórmula só, correta na criação (onde a
  operação não existe) e nos dois casos da correção (mesma fatura, ou fatura trocada).
- **A quitação total permanece incorrigível e irremovível**, e a change diz isso em vez de o
  silenciar: uma fatura `PAID` é história liquidada. Não é omissão de escopo — é a regra, e ela
  já é imposta em três níveis independentes que a change preserva intactos.
- O detalhe da transação passa a alcançar o formulário de pagamento pelo ponto de entrada público
  da feature de cartões, como já faz com o de transferência pela de contas.

## Capabilities

### New Capabilities
- `invoice-payment-editing`: a correção no lugar de um pagamento parcial de fatura — o mesmo
  formulário nos dois modos, o modo fixo na correção e o conjunto de faturas que dele decorre, a
  distinção entre abrir (preserva) e trocar (recalcula), a incorrigibilidade da quitação como
  regra declarada, e a travessia da fronteira entre features pelo ponto de entrada público.

### Modified Capabilities
- `invoice-settlement`: o requisito "O devido é lido da fatura selecionada" passa a definir o teto
  como o devido **desconsiderando a operação que está sendo escrita**, com dono único, em vez do
  devido corrente. O requisito "O pagamento de fatura é uma operação só, e o estado decide o modo"
  ganha a ressalva de que o estado decide o modo de uma operação **nova**; uma operação já escrita
  tem o modo que tem.

## Impact

**Domínio (`feature/creditcards/impl`)**
- `CalculateInvoiceUseCase` — passa a aceitar a operação a desconsiderar; dono único do teto.
- `UpdateAdvanceInvoicePaymentUseCase` (novo) — irmão de `AdvanceInvoicePaymentUseCase`, mesmas
  validações, `updateTransaction` em vez de `createTransaction`.
- `WriteInvoicePaymentUseCase` — passa a servir também a reescrita, mantendo o dono único da forma.

**Fronteira entre features**
- `CreditCardsEntry` (`feature/creditcards/api`) — novo membro `editInvoicePaymentModal(transaction)`.
- `ViewTransactionUiState.kt:164` / `ViewTransactionModal.kt:420` — `PAYMENT` deixa de ser recusa
  incondicional e passa a ler o predicado do domínio.

**UI (`feature/creditcards/impl` — `ui/modal/invoicePayment`)**
- `InvoicePaymentModal`, `InvoicePaymentViewModel`, `InvoicePaymentUiState`, `InvoicePaymentAction`
  e `canSubmitInvoicePayment` — ganham o modo de correção.

**Não muda**
- `:core:ledger` — nem a fronteira de escrita, nem `InvoiceWriteGuard`, nem `IEntryRepository`. A
  reescrita usa `updateTransaction`, que já existe e já é usada por `UpdateTransferUseCase`.
- O ciclo de vida da fatura — nenhum caminho novo até `PAID`, e nenhum de volta.

**Recursos**
- Duas chaves novas em `values/strings.xml` e `values-en/strings.xml` (o verbo "salvar" do
  formulário e o título do modo de correção), e um evento de analytics irmão de
  `AdvanceInvoicePayment`.
