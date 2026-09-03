---
area: creditcards
severity: low
type: ux
---

# A descrição do pay_invoice promete quitar fatura retroativa, e o domínio recusa

## Cenário

**DADO** uma fatura com status `RETROACTIVE` que ainda deve algo
**QUANDO** `pay_invoice` é chamado nela
**ENTÃO** a chamada é recusada com "Invoice must be closed before payment"
(`InvoiceError.InvoiceNotClosed`)
**DEVERIA** — a recusa está certa, é a descrição da ferramenta MCP que está errada: ela
promete "only a closed **or retroactive** invoice ... can be paid", quando só fatura
fechada é paga por aqui

## Mecânica

`PayInvoicePaymentUseCaseImpl.invoke` recusa com
`ensure(invoice.acceptsFullSettlement) { InvoiceException(InvoiceError.InvoiceNotClosed) }`,
e `acceptsFullSettlement` é `status == Status.CLOSED` — deliberadamente sem `RETROACTIVE`.
É distinto de `isPayable`, que inclui `RETROACTIVE` mas responde outra pergunta (quem pode
virar `PAID` ao fechar já devendo zero).

Isso é comportamento de domínio deliberado, não um descuido: `openspec/specs/invoice-settlement/spec.md`
formaliza que pagar integralmente uma fatura `RETROACTIVE` (ou `OPEN`) não pode quitá-la —
quitar uma retroativa é o encadeamento de dois gestos com donos próprios: pagar aos poucos
(`advance_invoice_payment`) até zerar, e só então fechar (`close_invoice`). Um teste cobre
exatamente este cenário e passa, com a intenção escrita no próprio nome:
`` `a retroactive invoice is settled in parts, and not discharged this way` ``.

A frase da ferramenta MCP ("closed or retroactive") é anterior a essa regra: foi escrita no
commit que criou `InvoiceOperationTools.kt`, seis dias antes do commit que introduziu
`acceptsFullSettlement` restrito a `CLOSED` — e nunca foi revisada depois disso.

## Evidência

- `feature/creditcards/impl/.../usecase/PayInvoicePaymentUseCaseImpl.kt:50-54` —
  `ensure(invoice.acceptsFullSettlement) { InvoiceNotClosed }`
- `core/model/.../domain/model/Invoice.kt:70` —
  `acceptsFullSettlement get() = status == Status.CLOSED`
- `core/model/.../domain/error/InvoiceError.kt:38` —
  `InvoiceNotClosed("Invoice must be closed before payment")`
- `openspec/specs/invoice-settlement/spec.md` — requisito "`PAID` é sempre precedido de
  `CLOSED`"
- `feature/creditcards/impl/src/commonTest/.../PayInvoicePaymentUseCaseTest.kt:224-247` —
  `` `a retroactive invoice is settled in parts, and not discharged this way` ``, verde
- `feature/mcp/impl/.../tool/InvoiceOperationTools.kt:92-101` — descrição de `pay_invoice`,
  "closed or retroactive", escrita antes da regra do domínio ter sido apertada e nunca
  atualizada depois

## Consequência

Repro real desta sessão: chamei `pay_invoice` numa fatura retroativa esperando que
funcionasse — a própria descrição da ferramenta dizia que sim — e levei uma recusa. O
comportamento está certo; é a documentação que o chamador lê antes de decidir o que chamar
que descreve um caminho que não existe.

## Sugestão

Reescrever a frase em `InvoiceOperationTools.kt:95-96` para refletir a regra atual — algo
como "only a closed invoice that still owes something can be paid in full; a retroactive
one is paid down via `advance_invoice_payment` and settled by `close_invoice` once it
reaches zero." Não vinculante.
