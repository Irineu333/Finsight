---
area: mcp
severity: low
type: data
---

# Três escritas forçam um mapper que documenta `null` como resposta, depois de o razão já ter sido escrito

## Invariante

Nenhum caminho de escrita afirma (`!!`) um valor cujo contrato declara `null` legítimo.

Hoje é falso em três: `create_transaction`, `update_transaction` e `create_installment` montam a
resposta com `toAgentTransaction(…)!!`, e a KDoc desse mapper diz que ele "returns `null` when the
perspective has no leg here, so a caller drops the item instead of failing on a read".

## Mecânica

O `!!` está **depois** da escrita. Se o mapper respondesse `null`, o `NullPointerException` subiria
até o `catch (cause: Throwable)` do journal, que registra `Outcome.REFUSED` e responde
`"The operation could not be completed."` — para uma operação que aconteceu. O agente lê uma
recusa, tenta de novo, e duplica o lançamento.

Não é alcançável hoje: as duas tools de transação mapeiam **sem** perspectiva, então `legUnder`
cai em `Transaction.primaryEntry`, `null` só para uma transação sem nenhuma perna monetária — e as
três acabaram de escrever uma. O que torna o caso digno de registro não é a probabilidade, é a
forma da falha: não um crash que o chamador vê, mas uma escrita relatada como recusa.

## Evidência

- `TransactionWriteTools` — `transaction = transactions.first().toAgentTransaction(lookup =
  lookup)!!` em `CreateTransactionTool`, e o mesmo em `UpdateTransactionTool`
- `InstallmentWriteTools` — `transaction = written.first().toAgentTransaction(lookup = lookup)!!`
- `Transaction.toAgentTransaction()` (`mcp/surface/AgentTransactionMapper.kt`) — `legUnder(accountId)
  ?: return null`, e a KDoc que declara o contrato
- `AgentActivityJournal` — o `catch (cause: Throwable)` que registra `AgentActivity.Outcome.REFUSED`,
  e `FAILURE_TEXT = "The operation could not be completed."`
- `AccountOperationTools` — `TransferTool` mapeia sob `TransactionPerspective(source.id)` **sem**
  `!!`, e é a forma que os três deveriam ter

## Consequência

Uma escrita aplicada e relatada como recusada é a única falha desta superfície que o agente é
incentivado a piorar: recusa determinística pede nova tentativa, e a nova tentativa grava de novo.

## Sugestão

Ou tornar `transaction` nulo no payload, com o `note` carregando o desfecho, ou mapear sob uma
perspectiva explícita e recusar **antes** de escrever se ela não puder ser resolvida. Qualquer
coisa menos `!!` sobre um contrato que diz que `null` é resposta. Não vinculante.
