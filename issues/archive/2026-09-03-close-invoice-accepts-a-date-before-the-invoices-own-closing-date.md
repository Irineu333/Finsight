---
area: creditcards
severity: medium
type: data
verdict: fixed
---

# Fechar fatura aceita uma data anterior ao próprio dia de fechamento

## Cenário

**DADO** uma fatura aberta com `closing_date=2026-09-20` (mês de fechamento 2026-09),
estando hoje em 2026-09-02
**QUANDO** `close_invoice` é chamado sem data explícita (logo, hoje = 2026-09-02)
**ENTÃO** a fatura fecha sem erro, 18 dias antes do dia programado — e passa a ficar
impagável: `pay_invoice` recusa em seguida com "Payment date cannot be before closing date"
até que 2026-09-20 chegue
**DEVERIA** recusar o fechamento com uma data anterior ao `closing_date` da fatura, do
mesmo jeito que a validação de pagamento já recusa pagar antes de `closingDate`
(`InvoiceError.PaymentDateBeforeClosing`)

## Mecânica

`CloseInvoiceUseCaseImpl` valida só o status (`ensure(invoice.isClosable)`) e o **mês** de
fechamento (`ensure(closedAt.yearMonth == invoice.closingMonth) { CannotCloseOutsideClosingMonth }`)
— nunca compara `closedAt` contra `invoice.closingDate`, o dia programado.

O predicado certo já existe, criado justamente para unificar essa regra:
`Invoice.isClosableOn(date) = isClosable && date >= closingDate`. Mas ele só chegou às
telas — é o que esconde o botão "fechar fatura" até o dia de fechamento chegar — e nunca ao
caso de uso de escrita. O commit que introduziu o predicado unificado descreve-o como "a
regra vencedora... consumida por InvoiceUi e InvoiceTransactionsViewModel", sem citar
`CloseInvoiceUseCase`; um commit seguinte, de auditoria adversarial, corrige
`CloseInvoiceUseCase` para "ler o predicado em vez de reenumerar em torno dele" — mas lê só
a metade sem data (`isClosable`), não `isClosableOn(closedAt)`. A unificação parou na tela.

A ferramenta MCP `close_invoice` não duplica nem afrouxa nada — repassa fielmente a regra
fraca do domínio, sem checagem própria.

## Evidência

- `feature/creditcards/impl/.../usecase/CloseInvoiceUseCaseImpl.kt:45,49-51` — valida só
  status e `yearMonth`, nunca `closingDate`
- `core/model/.../domain/model/Invoice.kt:80-85` — `isClosableOn(date)`, o predicado com
  corte de data, não usado aqui
- `feature/creditcards/impl/.../ui/.../InvoiceUiMapperImpl.kt:58` e
  `.../InvoiceTransactionsViewModel.kt:253` — onde `isClosableOn` de fato é consumido, só
  para esconder o botão na UI
- `feature/creditcards/api/.../usecase/ValidateInvoicePaymentUseCase.kt:45` —
  `date < invoice.closingDate -> InvoiceError.PaymentDateBeforeClosing.left()`, o padrão
  análogo já existente e nomeado no mesmo pacote
- `feature/creditcards/impl/.../usecase/ValidateAdvanceInvoicePaymentUseCase.kt:88` —
  `ensure(date >= invoice.openingDate && date <= invoice.closingDate) { DateOutsideInvoicePeriod }`,
  segundo exemplo do mesmo padrão
- `feature/mcp/impl/.../tool/InvoiceOperationTools.kt:263-290` — `CloseInvoiceTool` repassa
  a data ao use case sem checagem própria

## Consequência

Repro real desta sessão: fechei a fatura do Cartão Teste (`closing_date=2026-09-20`)
chamando `close_invoice` sem data explícita em 2026-09-02 — aceito sem erro. Ao tentar
pagar em seguida, `pay_invoice` recusou: "Payment date cannot be before closing date". A
fatura ficou fechada e devendo R$500, mas impagável até o dia 20/09 chegar — ou até ser
reaberta (`reopen_invoice`) e fechada de novo na data certa, um contorno que ninguém
adivinha sem já saber da causa.

## Sugestão

Trocar a validação de mês em `CloseInvoiceUseCaseImpl` por
`ensure(invoice.isClosableOn(closedAt))` (ou, se granularidade de erro importar, um
`ensure(closedAt >= invoice.closingDate)` com erro próprio, ex.
`CannotCloseBeforeClosingDate`) — lendo o predicado unificado em vez de reenumerar em
torno dele, completando a unificação que já chegou às telas. Não vinculante.

## Desfecho

**Causa real** — a descrita, e confirmada no disco: `CloseInvoiceUseCaseImpl` ia de
`ensure(invoice.isClosable)` direto para `ensure(closedAt.yearMonth == invoice.closingMonth)`,
sem nenhuma comparação entre `closedAt` e `invoice.closingDate`. O predicado unificado
`Invoice.isClosableOn` (`core/model/.../domain/model/Invoice.kt:85`) tinha só dois consumidores,
ambos de UI — a regra com corte de data nunca chegou ao caminho de escrita, que é onde o estado
é criado. Fechar antes do dia programado produzia uma fatura que
`ValidateInvoicePaymentUseCase` (`.../ValidateInvoicePaymentUseCase.kt:45`) depois se recusa a
pagar.

**Mudança** — o caso de uso passou a ler o mesmo predicado do domínio, depois das guardas de
status e de mês: `ensure(invoice.isClosableOn(closedAt)) { InvoiceException(CannotCloseBeforeClosingDate) }`
(`feature/creditcards/impl/.../usecase/CloseInvoiceUseCaseImpl.kt:53-58`). Como a metade de
status já foi garantida acima, o que o predicado ainda pode recusar ali é a data — e o erro tem
nome disso. Membro novo `InvoiceError.CannotCloseBeforeClosingDate`
(`core/model/.../domain/error/InvoiceError.kt:25`) com ramo próprio em `toUiText()` e a chave
`invoice_error_cannot_close_before_closing_date` nos dois `strings.xml` (pt e en). A guarda de
mês continua onde estava: as duas juntas descrevem a janela `[closingDate, fim do mês de
fechamento]`. A `description` e o parâmetro `date` de `close_invoice`
(`feature/mcp/impl/.../tool/InvoiceOperationTools.kt`) passaram a dizer que a data também tem de
cair em ou depois da data de fechamento — antes prometiam só o mês.

**Prova** — teste novo `closing before the closing day is refused, even inside the closing month`
em `CloseInvoiceUseCaseTest`: fatura aberta com `closingDate=2026-02-05`, fechamento pedido em
2026-02-04 (mês certo, dia anterior). Vermelho antes da correção
(`expected:<CannotCloseBeforeClosingDate> but was:<null>` — o fechamento era aceito), verde
depois, com as outras 10 do arquivo intactas (`tests="11" failures="0"`). Rodado com
`./gradlew :feature:creditcards:impl:testDebugUnitTest --tests "*.CloseInvoiceUseCaseTest"`; as
suítes de `:feature:creditcards:impl`, `:core:model`, `:feature:mcp:impl` e
`:feature:transactions:impl` seguem verdes.

**Commit** — `Fix(Domain): hold the three date rules the screens were holding alone`
