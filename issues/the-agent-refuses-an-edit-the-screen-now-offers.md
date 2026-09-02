---
area: mcp
severity: low
type: ux
---

# O agente recusa uma edição que a tela oferece, e quatro pontos afirmam que os dois concordam

## Invariante

`Transaction.editObstacle` é o dono único do que pode ser editado, e a tela e a superfície do agente
leem esse mesmo dono.

Hoje é falso dos dois lados: a tela não lê `editObstacle` — decide por `TransactionLabel` e abre o
formulário de cada natureza — enquanto `UpdateTransactionUseCase` continua recusando por ele.

## Mecânica

`editObstacle` responde o que o **formulário de transação** consegue reescrever: uma perna monetária
só, mais a contra-perna. Isso vale hoje para `UpdateTransactionUseCase`, e a recusa dele está certa.

O que mudou foi a tela. `ViewTransactionUiState.isEditable` decide por label, e `editFormFor` roteia
transferência e pagamento para os formulários que sabem escrever duas pernas —
`UpdateTransferUseCase` e `UpdateAdvanceInvoicePaymentUseCase`. `ITransactionRepository.updateTransaction`
recebe uma **lista** de pernas e expressa as duas.

A superfície do agente tem só a tool que monta o formulário de transação, e nenhuma equivalente às
outras duas. A recusa é correta para a tool que existe; a justificativa escrita ao lado dela é que
não é mais.

## Evidência

Onde a equivalência é afirmada e hoje não vale:

- `TransactionWriteTools.kt` (KDoc do arquivo) — "it is the same answer that decides whether the
  screen offers the action at all"
- `UpdateTransactionTool` (KDoc) — "it is the same derivation that stops the app's own screen from
  offering the edit"
- `UpdateTransactionUseCase` (`feature/transactions/api`, KDoc) — "the same derivation the screen
  reads to decide whether to offer the action"
- `TransactionError.MULTIPLE_MONETARY_LEGS` (KDoc) — "both the screen and the surface read it", e
  "makes editing it impossible rather than merely unavailable"

O que as contradiz:

- `ViewTransactionUiState.isEditable` — decide por `label`, sem citar `editObstacle`
- `editFormFor()` (`feature/transactions/impl` — `ui/modal/viewTransaction/EditForm.kt`) — roteia
  `TRANSFER` e `PAYMENT` para os modais de outras features
- `UpdateTransactionUseCaseImpl.invoke()` — segue recusando por `editObstacle`

## Consequência

Um agente a quem se pede para corrigir uma transferência ou um pagamento parcial de fatura responde
que não é possível, enquanto a pessoa corrige os dois na tela. A `description` da tool declara esse
perímetro, então o agente não engana — ele apenas pode menos.

O custo maior é para quem lê o código depois: quatro pontos justificam o perímetro por uma
equivalência que não existe mais, e um deles chama de impossível uma edição que a tela faz.

## Sugestão

Duas saídas, e são decisões diferentes:

- estreitar as quatro afirmações para o que continua verdade — `editObstacle` é o que o *formulário
  de transação* expressa, e não o que o app permite editar; ou
- dar à superfície as duas tools que faltam, e aí a equivalência volta a valer por construção.

A primeira fecha o invariante como está escrito. A segunda é uma ampliação de escopo da superfície e
precisa passar pelo que o `openspec` já declara sobre o que ela deixa de fora. Não vinculante.
