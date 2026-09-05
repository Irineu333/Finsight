---
area: creditcards
severity: low
type: ux
verdict: fixed
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

## Desfecho

**Causa real** — a descrita, e ainda viva no disco: `PayInvoicePaymentUseCaseImpl.kt:52` exige
`invoice.acceptsFullSettlement`, que é `status == Status.CLOSED` e nada mais
(`core/model/.../Invoice.kt:70`), enquanto a `description` de `pay_invoice` prometia "only a
closed **or retroactive** invoice … can be paid". A recusa é a regra; a frase é que era
anterior a ela. `Invoice.isPayable` (`Invoice.kt:45-49`) inclui `RETROACTIVE`, mas responde
outra pergunta — quem pode virar `PAID` — e quem o lê é `ValidateInvoicePaymentUseCase:44`,
depois do `ensure` que já recusou.

**Mudança** — só a redação, em `InvoiceOperationTools.kt`. Antes:

> "PERIMETER: only a closed or retroactive invoice that still owes something can be paid, and
> it is paid in full. Paying part of a cycle that is still open is advance_invoice_payment;
> ending a cycle is close_invoice, which settles nothing unless the invoice already owed zero."

Depois:

> "PERIMETER: only a closed invoice that still owes something can be paid, and it is paid in
> full — a cycle without a final figure has no whole to settle. Paying part of one that is
> still taking spending is advance_invoice_payment; a retroactive invoice is settled that way,
> paid down until it owes nothing and then marked paid by close_invoice, which settles nothing
> unless the invoice already owes zero."

Cada afirmação da frase nova tem dono no domínio: "sem figura final não há todo a quitar" é a
KDoc de `acceptsFullSettlement` (`Invoice.kt:64-70`); "as que ainda recebem gasto" é a de
`AdvanceInvoicePaymentUseCaseImpl` (`OPEN` e `RETROACTIVE`, via `acceptsPartialPayment`,
`Invoice.kt:58-62`); e "fechar marca paga a que já não deve nada" é
`CloseInvoiceUseCaseImpl.kt:72-77`.

**Prova** — não há teste que caiba aqui, e a razão importa: **o dublê deste módulo não reproduz
a recusa**. `WorldPayInvoicePayment` (`feature/mcp/impl/src/jvmTest/.../AgentWorldOperations.kt:150-205`)
só consulta `ValidateInvoicePaymentUseCase`, que lê `isPayable`, e não tem o `ensure
(acceptsFullSettlement)` da produção — então um teste de protocolo sobre fatura `RETROACTIVE`
neste módulo passaria *pagando*, provando o contrário do que o app faz. E um teste sobre a
prosa da descrição seria uma busca de substring em texto que ninguém deriva.

A verificação foi manual, em duas pernas. (1) O texto antes/depois acima, lido no arquivo — é
tudo que muda. (2) A âncora de domínio, rodada agora:
`./gradlew :feature:creditcards:impl:jvmTest --tests "*PayInvoicePaymentUseCaseTest*"` —
10 testes, 0 falhas, incluindo
`` `a retroactive invoice is settled in parts, and not discharged this way` ``, que assere
`InvoiceError.InvoiceNotClosed` e que nada foi escrito. A recusa que a frase antiga contrariava
continua exatamente onde estava.

**Defeito novo, relatado e não corrigido** — o dublê `WorldPayInvoicePayment` é mais permissivo
que a produção: falta-lhe a guarda `acceptsFullSettlement`, então `pay_invoice` sobre fatura
`RETROACTIVE` tem resultados opostos no teste e no app.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
