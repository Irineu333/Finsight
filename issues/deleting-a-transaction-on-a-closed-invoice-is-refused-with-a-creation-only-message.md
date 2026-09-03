---
area: creditcards
severity: medium
type: ux
---

# Apagar uma transação de fatura fechada é recusado com uma mensagem escrita para criação

## Cenário

**DADO** uma transação de cartão numa fatura com status `CLOSED` (fechada, não paga)
**QUANDO** `delete_transaction` é chamado para essa transação
**ENTÃO** a exclusão é recusada com "A closed invoice takes no new spending." — uma frase
sobre criar um gasto, não sobre remover um já existente
**DEVERIA** continuar recusando (a decisão está certa) com uma mensagem neutra quanto à
direção da operação, e a descrição da ferramenta MCP `delete_transaction` deveria citar
fatura fechada como motivo de recusa (hoje só cita fatura paga)

## Mecânica

`InvoiceError.ClosedToNewSpending` ("A closed invoice takes no new spending.") é um erro
único, reaproveitado por `InvoiceWriteGuard.ensureAccepts` para qualquer escrita que toque
uma fatura `CLOSED` e não a quite — criação, edição e remoção passam pelo mesmo portão
(`TransactionRepository.createTransaction` / `updateTransaction` / `removeRow` →
`ensureDimensionsAccept(...)`). O próprio guard já documenta a lacuna: seu comentário diz
que o predicado que junta `CLOSED`+`PAID` "happens to be right only for creating an
expense" — o texto do erro foi pensado para o caminho de criação e ficou incoerente quando
o mesmo guard dispara numa remoção (ou edição).

A ferramenta MCP `delete_transaction` não reescreve a mensagem do domínio — política
deliberada, documentada como "the reason is never rewritten here" — então repassa o texto
cru. E a própria descrição de `PERIMETER` da ferramenta só antecipa a recusa por fatura
**paga**, não por fatura **fechada**, deixando o motivo real fora do que ela anuncia de
antemão.

## Evidência

- `core/model/.../domain/error/InvoiceError.kt:60` —
  `ClosedToNewSpending("A closed invoice takes no new spending.")`
- `feature/creditcards/impl/.../domain/ledger/InvoiceWriteGuard.kt:7-17,30-41` — o guard
  único para `CLOSED`, com o comentário admitindo que o texto só está certo para criação
- `core/ledger/.../repository/TransactionRepository.kt:223-226,280,338,379-380` —
  `createTransaction`, `updateTransaction` e `removeRow` (via `deleteTransactionById`)
  passando pelo mesmo `ensureDimensionsAccept`
- `feature/transactions/impl/.../DeleteTransactionUseCaseImpl.kt:35` — nenhuma lógica
  própria de fatura; delega tudo ao repositório
- `feature/mcp/impl/.../tool/TransactionWriteTools.kt:556-561` — descrição de
  `delete_transaction` cita só conta/cartão arquivado e fatura paga, não fatura fechada
- `feature/creditcards/impl/src/commonTest/.../InvoiceWriteGuardTest.kt` e
  `feature/transactions/impl/src/jvmTest/.../InvoiceWriteGuardTest.kt:181-187,203-214` —
  nenhum teste cobre exclusão numa fatura `CLOSED` (só criação, e remoção numa fatura
  `PAID`)

## Consequência

Repro real desta sessão: tentei apagar uma compra de cartão numa fatura já fechada (não
paga) — recusado com "A closed invoice takes no new spending", que fala de gasto novo para
uma operação de remoção. Só entendi que precisava reabrir a fatura (`reopen_invoice`)
porque já sabia da causa; a mensagem sozinha aponta na direção errada, e a descrição da
ferramenta MCP não avisa desse motivo de recusa antecipadamente.

## Sugestão

Duas correções independentes e de baixo custo: (1) dar à remoção (e à edição) seu próprio
erro, ou uma mensagem neutra tipo "This invoice is closed; reopen it before changing what's
on it.", em vez de reaproveitar `ClosedToNewSpending` fora do caminho de criação; (2)
atualizar o `PERIMETER` de `delete_transaction` (`TransactionWriteTools.kt`) para citar
fatura fechada, não só paga. Não vinculante.
