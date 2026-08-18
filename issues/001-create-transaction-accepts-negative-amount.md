# 001 — `create_transaction` aceita valor negativo

**Área:** mcp · **Tipo:** dados · **Severidade:** alta · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`create_transaction` lê `amount` sem nenhuma checagem de sinal. Um valor negativo chega intacto ao
ledger, onde `EXPENSE -> -cents` o transforma em um **crédito** na conta: o lançamento que o usuário
pediu para registrar como gasto **aumenta** o saldo e **reduz** o total de despesas do mês.
`update_transaction`, no mesmo arquivo, recusa exatamente isso e diz por quê.

## Evidência

| Onde | O que faz |
|---|---|
| `feature/mcp/impl/.../tool/TransactionWriteTools.kt:137` | `arguments.requiredMoney("amount")` — sem guarda |
| `feature/mcp/impl/.../tool/WriteSupport.kt:156-164` | `money()` aceita qualquer double, sinal incluído |
| `feature/mcp/impl/.../tool/WriteSupport.kt:167` | `asFormAmount()` → `(-40.0 * 100).roundToLong()` = `"-4000"` |
| `core/common/.../extension/MoneyFormatter.kt:3-8` | `moneyToDouble()` respeita o `-` inicial |
| `feature/transactions/impl/.../ValidateTransactionFormUseCaseImpl.kt:35` | `ensure(form.amount.moneyToDouble() != 0.0)` — só o zero é recusado |
| `feature/transactions/impl/.../BuildTransactionUseCaseImpl.kt:46,70` | perna com `amount = form.amount.moneyToDouble()` |
| `core/ledger/.../LedgerEntryWriter.kt:281-288` | `EXPENSE -> -cents`, logo `-(-4000) = +4000` na perna `ASSET` |
| `feature/mcp/impl/.../tool/TransactionWriteTools.kt:326-347` | `update_transaction` recusa `amount <= 0.0`, com um comentário que nomeia essa falha |

O próprio schema da tool já enuncia a regra que não é aplicada
(`TransactionWriteTools.kt:111-115`: *"Always positive: `type` says the direction."*).

## Cenário de falha

`create_transaction(type: "expense", amount: -40, account_id: 1, title: "x")`

1. O formulário é válido (`amount != 0.0`), então nada recusa.
2. O writer lança `+4000` na conta `ASSET` e `-4000` no nominal de `EXPENSE`.
3. `Σ = 0` se mantém, então o write boundary aceita.
4. O saldo **sobe** 40; o total de despesas do mês **cai** 40; o total da própria categoria fica
   contaminado por uma perna nominal negativa (orçamento e relatório leem a mesma soma).
5. A resposta informa `nature: "expense"`, `amount: 40` — `deriveTransactionLabel` vê uma conta
   `EXPENSE` entre as entries (`core/ledger/.../extension/Ledger.kt:56-65`) e `itemDisplayAmount`
   devolve a magnitude. Nada no payload diz que a direção inverteu.

## Correção sugerida

Recusar `amount` não positivo em `CreateTransactionTool.call`, nas mesmas palavras que
`UpdateTransactionTool` já usa. A duplicação lá é deliberada — o comentário em
`TransactionWriteTools.kt:329-332` explica que o domínio recusa o zero e não isto, *porque nenhuma
tela tem campo capaz de expressá-lo* — e um agente tem.

Recusar uma única vez para toda a superfície (em `requiredMoney`, ou num `positiveMoney` ao lado)
cobre também `create_recurring`, `create_installment`, `adjust_invoice` e o limite do cartão, que
leem o mesmo helper sem checagem de sinal.

## Observações

A lacuna de domínio por trás disso já está registrada como item 16 de
`docs/auditoria-bugs-2026-07.md` ("Valor negativo aceito no formulário de transação"), onde foi
**rebaixada para média** sob o argumento de que o sinal fica visível no campo e não há caminho
acidental até ele. A superfície MCP remove as duas atenuantes: um agente escreve `-40` num argumento
JSON, ninguém vê campo nenhum, e a tool responde "Recorded."
