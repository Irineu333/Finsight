# 020 — `create_installment` descarta a categoria em silêncio, e responde "Recorded"

**Área:** creditcards / mcp · **Tipo:** correção · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial das correções
da [016](016-update-transaction-drops-the-category-silently.md) e da
[017](017-installment-opens-invoices-before-refusing.md)

## O que está errado

É a mesma regra da [004](004-transaction-form-drops-arguments-silently.md) e da
[016](016-update-transaction-drops-the-category-silently.md), na terceira das três tools que
montam um `TransactionForm`. A 004 fechou a criação, a 016 fechou a edição, e `create_installment`
ficou aberta — apesar de ter sido a tool mexida pela 017, no mesmo dia.

## Evidência

`feature/mcp/impl/.../tool/InstallmentWriteTools.kt:82`

```kotlin
val category = arguments.long("category_id")?.let { categoryRepository.require(it) }
```

Nenhuma guarda de compatibilidade: o arquivo inteiro não contém uma ocorrência de `isAccept`. A
categoria vai direto para `TransactionForm.from` (`:84-96`), que a descarta em
`core/model/.../form/TransactionForm.kt:81`. O `type` aqui é sempre `EXPENSE`, fixo no código, então
a incompatível é qualquer categoria de receita.

## Cenário de falha

`create_installment(card_id: 1, amount: 900, count: 3, category_id: <uma categoria de receita>)`

Três lançamentos são gravados sem classificação nenhuma, e a resposta diz
`"Recorded as 3 instalments, one per invoice they land on."` com `is_uncategorized: true` no mesmo
payload. Medido pela revisão, sobre o servidor real.

## Correção sugerida

A recusa que o `CreateTransactionTool` já faz (`TransactionWriteTools.kt:203-213`), nomeando a
categoria e a direção. Não mexer em `TransactionForm.from`, pela razão da 004.

## Correção aplicada

A recusa que a [004](004-transaction-form-drops-arguments-silently.md) instalou na criação e
a [016](016-update-transaction-drops-the-category-silently.md) na edição, agora em
`CreateInstallmentTool.call`, antes do formulário. Com uma diferença que a tool impõe: aqui a direção
não é parâmetro — um parcelamento é sempre despesa —, então a guarda compara contra a constante e a
razão diz *"a split is always an expense"* em vez de nomear um `type` que a chamada não deu.

O `summary` subiu para antes da guarda, sem um segundo formato: é a mesma expressão que já existia,
lida antes porque a recusa precisa dela.

A descrição do campo `category_id` no schema passou a dizer que a categoria é de despesa e que uma de
receita é recusada. A KDoc da classe e o `description` da tool não foram tocados — o `PERIMETER` já
dizia *"only for expenses"* e continua verdadeiro.

Conferido que o teste morde: antes da correção, `create_installment` com uma categoria de receita
respondia sucesso com `is_uncategorized: true` no payload, sob
`"Recorded as 3 instalments, one per invoice they land on."`, e três lançamentos entravam no ledger
sem classificação.

## Onde a issue estava imprecisa

Um deslize de uma linha: ela aponta a recusa modelo em `TransactionWriteTools.kt:203-213`, e o bloco
começa em `:202`, no `if`. O resto foi conferido contra a árvore em `1234c4264`, antes da correção:
`InstallmentWriteTools.kt:82` era a resolução sem guarda, o arquivo não tinha nenhuma ocorrência de
`isAccept`, e `TransactionForm.kt:81` era o descarte. As duas primeiras deixaram de valer com a
correção, que é o que este arquivo registra.

## O que ficou para trás

A linha de `create_transaction` em `docs/mcp-tool-surface.md:191` continua sem mencionar as recusas
que a 004 instalou; ela documenta só as ligadas a `installments`, e ficou assimétrica com a de
`update_transaction`. É lacuna anterior a esta issue, e a 004 já está arquivada.
