---
area: creditcards
severity: low
type: ux
---

# reopen_invoice aponta um caminho para desfazer pagamento que o guard recusa

## Cenário

**DADO** uma fatura com status `PAID`
**QUANDO** se tenta desfazer o pagamento seguindo literalmente a descrição de
`reopen_invoice` — "undoing a payment means removing the posting that made it
(delete_transaction), not reopening the cycle" — chamando `delete_transaction` na postagem
do pagamento
**ENTÃO** a chamada é recusada com "A paid invoice cannot be changed."
(`InvoiceError.Paid`)
**DEVERIA** — a recusa está certa (uma fatura `PAID` é história liquidada por design,
`não é omissão de escopo` segundo a spec); a descrição de `reopen_invoice` é que não
deveria apontar um caminho que o próprio domínio nunca permitiu

## Mecânica

`InvoiceWriteGuard.ensureAccepts` recusa **incondicionalmente** qualquer escrita — criação,
edição ou remoção — que toque a dimensão de uma fatura `PAID`, com
`invoice.status.isPaid -> throw InvoiceException(InvoiceError.Paid)`, sem exceção para a
própria postagem que a quitou. `TransactionRepository.ensureDimensionsAcceptRemoval` passa
pela mesma porta que a criação, com o comentário já admitindo isso: "Removing a transaction
changes its sub-ledgers too, so it passes the same gate. It never *settles* one: undoing a
payment is not the payment." Nenhum dos quatro caminhos possíveis funciona: `delete_transaction`
na postagem do pagamento, `reopen_invoice` (recusa `PAID` explicitamente),
`delete_invoice` (`isDeletable` não inclui `PAID`) e `adjust_invoice` (delega ao mesmo
guard, transitivamente). Isso é intencional — `openspec/specs/invoice-payment-editing/spec.md`
formaliza que a quitação "MUST NOT ser corrigível nem removível" e que isso "não é omissão
de escopo".

Mas a descrição da ferramenta MCP `reopen_invoice` continua dizendo, para o caso de uma
fatura paga, que o caminho certo é "removing the posting that made it (delete_transaction)"
— uma frase que promete um caminho que o guard recusa igualzinho ao que `reopen_invoice`
recusa.

## Evidência

- `feature/mcp/impl/.../tool/InvoiceOperationTools.kt:386-388` — a promessa: "undoing a
  payment means removing the posting that made it (delete_transaction), not reopening the
  cycle"
- `feature/creditcards/impl/.../domain/ledger/InvoiceWriteGuard.kt:36` —
  `invoice.status.isPaid -> throw InvoiceException(InvoiceError.Paid)`, incondicional,
  sem exceção para a postagem do pagamento
- `core/ledger/.../database/repository/TransactionRepository.kt:225-230,379-381` —
  `ensureDimensionsAcceptRemoval`, aplicando o mesmo guard à remoção, com o comentário
  admitindo que "undoing a payment is not the payment"
- `feature/transactions/impl/src/jvmTest/.../InvoiceWriteGuardTest.kt:341-347` — teste
  `` `a paid invoice refuses even its own payment` ``, prova a recusa
- `openspec/specs/invoice-payment-editing/spec.md:149-169` — "não é omissão de escopo"

## Consequência

Repro real desta sessão: paguei uma fatura de teste, quis desfazer, segui a orientação da
própria ferramenta `reopen_invoice` e levei a mesma recusa que ela existe para evitar. Não
há dado errado nem estado irrecuperável — a imutabilidade é proposital — mas quem confia na
mensagem perde uma chamada tentando um caminho que nunca existiu.

## Sugestão

Remover a cláusula "undoing a payment means removing the posting that made it
(delete_transaction)" da descrição de `reopen_invoice`, deixando só que fatura paga é
história liquidada e não tem desfazer por esta API. Não vinculante.
