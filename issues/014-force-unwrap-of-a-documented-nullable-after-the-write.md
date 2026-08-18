# 014 — `toAgentTransaction(...)!!` força um nullable documentado *depois* de a escrita já ter sido aplicada

**Área:** mcp · **Tipo:** robustez · **Severidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

Três tools de escrita forçam um mapper cujo contrato é *"retorna null, descarte o item"*. Se ele
algum dia respondesse null, o NPE escaparia para o catch-all do journal **depois** de a escrita no
ledger já ter sido efetivada — o agente seria informado de que a operação falhou e o log registraria
`REFUSED` para algo que aconteceu.

## Evidência

Os três call sites:

- `feature/mcp/impl/.../tool/TransactionWriteTools.kt:191` — `CreateTransactionTool`
- `feature/mcp/impl/.../tool/TransactionWriteTools.kt:399` — `UpdateTransactionTool`
- `feature/mcp/impl/.../tool/AccountOperationTools.kt:169-171` — `TransferTool`

O contrato que eles atropelam (`surface/AgentTransactionMapper.kt:31-32`, repetido em `:52`):

> Returns `null` when the perspective has no leg here, so a caller drops the item instead of
> failing on a read — the same contract the screen's mapper keeps.

Onde a exceção cairia (`feature/mcp/impl/.../AgentActivityJournal.kt:36-46,80`): um
`catch (cause: Throwable)` que registra `Outcome.REFUSED` e responde
`"The operation could not be completed."`

## Avaliação: não alcançável hoje

Isto é um smell, não um defeito ativo, e a issue está registrada como tal:

- `CreateTransactionTool` e `UpdateTransactionTool` mapeiam **sem** perspectiva, então `legUnder` cai
  em `Transaction.primaryEntry` (`TransactionPerspective.kt:30-33`), que só é null quando a transação
  não tem nenhuma perna monetária (`core/ledger/.../Transaction.kt:63-64`). Os dois caminhos acabaram
  de escrever uma.
- `TransferTool` mapeia sob `TransactionPerspective(source.id)`, e a transferência que ele acabou de
  gravar lança uma perna nessa conta.

O que torna isto digno de registro é a *consequência* se a premissa deixar de valer: não um crash que
o chamador vê, mas uma escrita reportada como recusa — e um agente que lê uma recusa tenta de novo, o
que duplica o lançamento.

## Correção sugerida

Usar um fallback em vez de afirmar. O campo `transaction` da resposta é a forma do próprio payload,
então: ou torná-lo nullable com o `note` carregando o desfecho, ou mapear com uma perspectiva
explícita e recusar *antes* de escrever se ela não puder ser resolvida. Qualquer coisa menos `!!`
sobre um contrato que diz que null é resposta legítima.
