# 022 — Nas criações, `category_id: 0` não é uma forma de falar, e isso não está escrito

**Área:** mcp · **Tipo:** consistência de superfície · **Criticidade:** baixa · **Status:** aberto (metade corrigida com a 021)
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial da correção da
[016](archive/016-update-transaction-drops-the-category-silently.md)

## O que está errado

A [016](archive/016-update-transaction-drops-the-category-silently.md) precisou de uma forma de dizer
*sem categoria* numa edição, porque ausência ali significa *mantenha o que está lá* e um `null`
explícito é lido como ausência (`ToolSupport.argument`). Adotou o `0`, que o `confirm_recurring` já
usava, e a [021](archive/021-update-recurring-stores-an-incoherent-template.md) somou o
`update_recurring`. São **três** tools — exatamente as três que carregam o que a chamada não nomeia.
As outras três, todas criações, respondem que a categoria 0 não existe.

| Tool | `category_id: 0` | Onde |
|---|---|---|
| `update_transaction` | sem categoria | `TransactionWriteTools.kt:466-467` |
| `confirm_recurring` | sem categoria | `RecurringOperationTools.kt:133-136` |
| `create_transaction` | `"No category with id 0 exists."` | `TransactionWriteTools.kt:150` |
| `create_installment` | `"No category with id 0 exists."` | `InstallmentWriteTools.kt:86` |
| `create_recurring` | `"No category with id 0 exists."` | `RecurringWriteTools.kt:106` |
| `update_recurring` | sem categoria | `RecurringWriteTools.kt:260-262` |

Nas três criações a divergência tem defesa: numa criação, ausência já significa *nenhuma*, então o
`0` não precisa existir.

**A metade que não tinha defesa foi corrigida.** `update_recurring` — a outra edição da superfície,
onde ausência significa *mantenha* — passou a ler o `0` junto com a
[021](archive/021-update-recurring-stores-an-incoherent-template.md), porque sem ele a recusa que aquela
issue instalou apontaria para uma saída inexistente. São três tools lendo `NO_CATEGORY`, e as três
são exatamente as que carregam o que a chamada não nomeia.

O que resta é a pergunta de superfície abaixo, sobre as criações.

## Cenário de falha

Um agente aprende o `0` numa das três edições e o usa numa criação, onde recebe `"No category with
id 0 exists."` — uma resposta sobre a categoria, não sobre a tool, da qual não dá para deduzir que
ali o `0` simplesmente não é uma forma de falar. O dano é pequeno: numa criação, omitir
`category_id` faz o que ele queria.

As cinco recusas de categoria incompatível também mandam o agente para saídas diferentes: as três
criações dizem *"or leave `category_id` out"* (`TransactionWriteTools.kt:208`,
`InstallmentWriteTools.kt:101`, `RecurringWriteTools.kt:141`) e as duas edições dizem
*"or `category_id` 0"* (`TransactionWriteTools.kt:480`, `RecurringWriteTools.kt:272`) — correto em
cada uma, e confuso lido em sequência.

## Correção sugerida

Só resta a decisão de superfície: se as criações devem ou não aceitar o `0` como sinônimo de
ausência. Hoje a resposta é não, e ela não está escrita em lugar nenhum — nem nas descrições das
tools, nem em `docs/mcp-tool-surface.md`. Escrevê-la já resolveria a maior parte do incômodo.
