# 022 — `category_id: 0` significa "sem categoria" em duas tools e "não existe" em quatro

**Área:** mcp · **Tipo:** consistência de superfície · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial da correção da
[016](archive/016-update-transaction-drops-the-category-silently.md)

## O que está errado

A [016](archive/016-update-transaction-drops-the-category-silently.md) precisou de uma forma de dizer
*sem categoria* numa edição, porque ausência ali significa *mantenha o que está lá* e um `null`
explícito é lido como ausência (`ToolSupport.argument`). Adotou o `0`, que o `confirm_recurring` já
usava. São duas tools; as outras quatro que aceitam `category_id` respondem que a categoria 0 não
existe.

| Tool | `category_id: 0` | Onde |
|---|---|---|
| `update_transaction` | sem categoria | `TransactionWriteTools.kt:466-467` |
| `confirm_recurring` | sem categoria | `RecurringOperationTools.kt:133-136` |
| `create_transaction` | `"No category with id 0 exists."` | `TransactionWriteTools.kt:150` |
| `create_installment` | `"No category with id 0 exists."` | `InstallmentWriteTools.kt:82` |
| `create_recurring` | `"No category with id 0 exists."` | `RecurringWriteTools.kt:103` |
| `update_recurring` | `"No category with id 0 exists."` | `RecurringWriteTools.kt:207` |

Nas três criações a divergência tem defesa: numa criação, ausência já significa *nenhuma*, então o
`0` não precisa existir. **`update_recurring` não tem defesa** — é a outra edição da superfície, onde
ausência significa *mantenha*, exatamente a condição que motivou o `0`, e ali não há como limpar uma
categoria de jeito nenhum.

## Cenário de falha

Um agente aprende o `0` lendo a descrição de `update_transaction` — *"pass 0 to leave it
unclassified"* (`TransactionWriteTools.kt:374-377`) — e o usa em `update_recurring`, onde a mesma
frase seria verdadeira. Recebe `"No category with id 0 exists."`, que é sobre a categoria e não sobre
a tool, e não tem como descobrir que a operação simplesmente não existe naquela porta.

As duas recusas de categoria incompatível também mandam o agente para saídas diferentes na mesma
situação: `create_transaction` diz *"or leave `category_id` out"* (`:208`), `update_transaction` diz
*"or `category_id` 0"* (`:480`) — correto em cada uma, e confuso lido em sequência.

## Correção sugerida

O mínimo é `update_recurring` ler o `0` como as outras duas edições, e a [021](021-update-recurring-stores-an-incoherent-template.md) é onde
essa tool já vai ser mexida. Se as criações devem ou não aceitar o `0` como sinônimo de ausência é
uma decisão de superfície: hoje a resposta é não, e ela não está escrita em lugar nenhum.
