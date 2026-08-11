## Why

Nos modais de adicionar transação e adicionar parcelamento, escolher a fatura não move a data:
a fatura navega e a data continua em "hoje". Como a relação entre as duas não é evidente — uma
fatura não tem "um mês", ela tem uma **janela de compra** que atravessa dois meses de calendário
e vence num terceiro —, o usuário quase certamente grava a data errada. O caso mais penalizado é
o lançamento retroativo, onde acertar a configuração à mão é justamente o que é difícil.

A data não decide nada no razão (a fatura é resolvida por `form.invoiceDueMonth`, e a data só
viaja no `TransactionIntent`), mas é o que o usuário lê depois na lista de transações — e é o que
o parcelamento usa como base ao distribuir as parcelas (`base.date.plus(index, MONTH)`), premissa
hoje verdadeira apenas por acaso.

## What Changes

- A janela de compra de uma fatura — `abertura → fechamento`, com o dia de fechamento do cartão —
  ganha **um dono no domínio**, em `:core:model`, junto com a projeção de uma data nela.
- Escolher uma fatura (ou trocar o cartão, que muda a janela sob o mesmo mês de vencimento)
  **projeta a data** na janela dessa fatura, preservando o dia, e **trava em hoje**: navegar para
  uma fatura futura não empurra a data para o futuro, porque o gasto é no presente e apenas se
  paga no futuro.
- A projeção é **idempotente**: uma data já dentro da janela é devolvida inalterada. Abrir o
  modal na fatura aberta não mexe em "hoje".
- A direção inversa não existe: **editar a data nunca altera a fatura**. A projeção é uma
  sugestão do sistema; a palavra final sobre a data é do usuário.
- As quatro cópias da derivação `dueMonth → closingMonth` hoje espalhadas por
  `feature/creditcards/impl` passam a consumir a regra única.
- **Fora de escopo, por princípio:** o modal de *editar* transação não recebe a cascata. Ali a
  data é dado que o usuário escreveu, não um default; sobrescrevê-la contradiria a regra acima.

## Capabilities

### New Capabilities
- `invoice-purchase-window`: a janela de compra de uma fatura como conceito de domínio com dono
  único — sua derivação a partir do cartão e do mês de vencimento, suas bordas (abertura
  inclusiva, fechamento exclusiva), e a projeção de uma data nela preservando o dia.
- `invoice-governs-date`: a hierarquia de preenchimento nos formulários de lançamento em cartão —
  cartão governa fatura, fatura governa data, e nada governa na direção contrária —, incluindo a
  trava em hoje e a preservação da edição manual do usuário.

### Modified Capabilities

## Impact

**Código novo (`:core:model`):**
- `domain/model/InvoiceWindow.kt` — a janela e a projeção
- `CreditCard.invoiceWindowFor(dueMonth)` e `Invoice.window`
- `Invoice.openingDate` / `Invoice.closingDate` passam a delegar à janela

**Código alterado:**
- `InvoiceMonthSelection` ganha o cartão e expõe a janela — três sítios de construção
  (`AddTransactionViewModel`, `AddInstallmentViewModel`, `EditTransactionViewModel`)
- `AddTransactionViewModel` e `AddInstallmentViewModel` ganham o coletor que projeta a data
- `AddTransactionModal` e `AddInstallmentModal` ganham a sincronização reversa do campo de data
  (hoje o `TextFieldState` é inicializado uma vez e só flui UI → ViewModel)
- `CreateFutureInvoiceUseCase`, `CreateRetroactiveInvoiceUseCase`, `CreateInvoiceUseCase`,
  `OpenInvoiceUseCase` e `AddCreditCardUseCase` passam a consumir a derivação única

**Sem impacto:** razão, banco, migrações, `TransactionForm`, o boundary de escrita. Nenhuma
regra de gravação muda — o que muda é o valor sugerido num campo antes da gravação.

**Sem strings novas:** a mudança não acrescenta texto de interface.
