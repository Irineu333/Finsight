---
area: mcp
severity: low
type: data
verdict: fixed
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

## Desfecho

**Causa real** — a descrita, e as âncoras foram reconferidas uma a uma:
`TransactionWriteTools.kt:263` e `:574`, `InstallmentWriteTools.kt:133`, sobre
`Transaction.toAgentTransaction()` (`AgentTransactionMapper.kt:52`, `legUnder(accountId) ?: return
null`, e a KDoc em `:31-32` que declara o `null` legítimo). O caminho do dano também está no disco:
`AgentActivityJournal.kt:36-49` captura `Throwable` e responde `Outcome.REFUSED` com
`FAILURE_TEXT` (`:80`), *"The operation could not be completed."*

O que **não** confere é uma linha da evidência: `AccountOperationTools.kt:175` (`TransferTool`)
**também** usa `!!`, sob `TransactionPerspective(source.id)` — não é a forma certa que o registro
diz que é. E há um quinto sítio que o registro não conta:
`RecurringOperationTools.kt:223` (`confirm_recurring`). Os dois foram fechados depois, na mesma
passagem — ver o parágrafo sobre a evidência, abaixo.

A segunda causa é estrutural e é a que impedia qualquer saída: `AgentTransactionWriteAnswer
.transaction` era não-nulo, então o `null` do mapper não tinha para onde ir a não ser para o `!!`.

**Mudança** — quatro arquivos:

1. `AgentWrites.kt` — `transaction` passa a `AgentTransaction? = null`, com a KDoc que diz quando
   falta e por quê. Sem efeito no fio: `agentJson` roda com `explicitNulls = false`
   (`ToolSupport.kt:41-44`), então o campo continua idêntico em toda resposta que o tem.
2. `WriteSupport.kt` — `noteFor(posting, done)`, o único dono da frase que a ausência acrescenta ao
   `note`, para as três escritas não a copiarem cada uma.
3. `TransactionWriteTools.kt:264` e `:591` — o mapeamento vai para um `val posting`, entra no
   payload como está e passa por `noteFor`.
4. `InstallmentWriteTools.kt:132-136` — `postings` é mapeado uma vez (era mapeado duas: a primeira
   parcela com `!!` e a lista com `mapNotNull`), e `transaction` é `postings.firstOrNull()`.

A outra saída sugerida — resolver a perspectiva antes e recusar se ela não resolver — não fecha o
invariante: a perspectiva resolve antes da escrita, mas `toAgentTransaction` continua respondendo
`AgentTransaction?` depois dela, e alguma ramificação teria de tratar o `null` de qualquer forma.

**Prova** — teste novo, `AnAppliedWriteNeverArrivesAsARefusalTest`, três casos pelo protocolo
(`create_transaction`, `update_transaction`, `create_installment`), com apenas o use case de cada
uma trocado por um que escreve e responde uma transação sem perna monetária — o único estado em que
`primaryEntry` é `null`. Assere que a resposta **não** chega marcada como erro, que não é o texto
genérico do journal, e que a linha do log é `APPLIED`.

Vermelho antes da correção, nos três: *"the write went through and came back flagged as an error
the agent must act on: The operation could not be completed."* — que é, palavra por palavra, o dano
que o registro descreve. Verde depois.

**A `## Evidência` estava errada, e por isso o registro contava três sítios em vez de cinco.** Ela
aponta `TransferTool` como *"a forma que os três deveriam ter"*; ele mapeia sob perspectiva
explícita, mas termina em `!!` do mesmo jeito. O quinto sítio, `confirm_recurring`, não é citado em
lugar nenhum do registro. Os dois foram fechados na mesma forma —
`AccountOperationTools.kt:181-192` e `RecurringOperationTools.kt:217-228`, cada um com o mapeamento
numa `val posting` e o `note` passando por `noteFor` —, porque corrigir três de cinco deixaria a
classe aberta com a aparência de fechada, que é pior do que não tê-la tocado.

Varredura final, e é ela que sustenta o fechamento: `grep` por `toAgentTransaction(` seguido de
`!!` em todo `feature/mcp/impl/src/jvmMain` não devolve nada. Suíte do módulo: 285 testes, 0
falhas; suíte inteira (`./gradlew jvmTest`), 461 relatórios, nenhum com falha ou erro.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
