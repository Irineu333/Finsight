# 020 — `create_installment` descarta a categoria em silêncio, e responde "Recorded"

**Área:** creditcards / mcp · **Tipo:** correção · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial das correções
da [016](archive/016-update-transaction-drops-the-category-silently.md) e da
[017](archive/017-installment-opens-invoices-before-refusing.md)

## O que está errado

É a mesma regra da [004](archive/004-transaction-form-drops-arguments-silently.md) e da
[016](archive/016-update-transaction-drops-the-category-silently.md), na terceira das três tools que
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
