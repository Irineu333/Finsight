---
area: mcp
severity: low
type: ux
verdict: fixed
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

## Desfecho

**Causa real** — a descrita, e as sete âncoras conferem no disco. As quatro afirmações:
`TransactionWriteTools.kt:49-50` (*"it is the same answer that decides whether the screen offers the
action at all"*), `:342-343` (*"the same derivation that stops the app's own screen from offering
the edit"*), `UpdateTransactionUseCase.kt:14-15` (*"the same derivation the screen reads to decide
whether to offer the action"*) e `TransactionError.kt:20-24` (*"both the screen and the surface read
it"* e *"makes editing it impossible rather than merely unavailable"*). O que as contradiz:
`ViewTransactionUiState.kt:158-183`, onde `isEditable` decide por `label` e admite `TRANSFER` e
`PAYMENT` sem citar `editObstacle`; `EditForm.kt:29-32`, que roteia os dois para
`accountsEntry.editTransferModal` e `creditCardsEntry.editInvoicePaymentModal`; e
`UpdateTransactionUseCaseImpl.kt:35`, que segue recusando por `editObstacle`.

O dono da regra já estava certo e ninguém o tinha lido: a KDoc de `Transaction.editObstacle`
(`core/model` — `TransactionEditability.kt:25-30`) diz, com todas as letras, *"This is not the
question 'may the user edit this at all'"*, e nomeia `UpdateTransferUseCase` e
`UpdateAdvanceInvoicePaymentUseCase` como as duas saídas. As quatro afirmações eram cópias
desatualizadas de um texto que já tinha sido corrigido na origem.

**Mudança** — a saída estreita, as quatro afirmações reescritas para o que continua verdade, sem
ferramenta nova:

- `TransactionWriteTools.kt` (KDoc do arquivo) — `editObstacle` é o alcance do **formulário de
  transação**, e a tela é mais larga porque decide por label e abre os outros dois formulários; o
  perímetro daqui é o do formulário, não uma permissão que o domínio nega.
- `UpdateTransactionTool` (KDoc) — a regra é o que este formulário expressa, e esta superfície não
  tem ferramenta para os dois formulários que expressam o resto.
- `UpdateTransactionUseCase` (`feature/transactions/api`) — o que `editObstacle` decide é o que
  *este* formulário expressa, não se o usuário pode editar; a guarda da própria tela lê o label e é
  mais larga.
- `TransactionError.MULTIPLE_MONETARY_LEGS` — *"a shape, not a permission"*, e o "impossível" cai:
  quem lê são `editObstacle` e `UpdateTransactionUseCase`, e o que o app deixa editar é mais amplo.

A ampliação da superfície (as duas ferramentas que faltam) continua fora: é decisão de escopo, e o
`openspec` é quem a arbitra.

**Prova** — não há teste, e forçar um seria teatro: nada mudou de comportamento — as quatro
mudanças são KDoc, e a recusa de `UpdateTransactionUseCase` é a mesma antes e depois. A verificação
foi a leitura das sete âncoras contra o código no disco, citada acima linha a linha, mais a
constatação de que `TransactionEditability.kt` já dizia o que as quatro passam a dizer.

Como salvaguarda de compilação, o módulo foi rodado depois da mudança: 280 testes, 0 falhas
(`./gradlew :feature:mcp:impl:jvmTest`), incluindo `ScreenAndAgentAgreeTest`, que segue verde
porque nada do que ele compara mudou.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
