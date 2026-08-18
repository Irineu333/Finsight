# 016 — `update_transaction` descarta a categoria em silêncio, e recusa uma receita em cartão pelo argumento errado

**Área:** mcp / model · **Tipo:** correção · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-18, `feature/local-mcp-server`, durante a correção da
[004](archive/004-transaction-form-drops-arguments-silently.md)

## O que está errado

É o espelho da [004](archive/004-transaction-form-drops-arguments-silently.md) no caminho da edição.
A 004 fechou `create_transaction`; `update_transaction` monta o mesmo `TransactionForm.from` e não
ganhou recusa nenhuma, então continua descartando o que o chamador declarou e respondendo que
guardou.

E aqui há uma variante que a criação não pode ter: a categoria descartada pode ser a **já
armazenada**, que a chamada nunca mencionou.

## Evidência

`feature/mcp/impl/.../tool/TransactionWriteTools.kt:426-429`

```kotlin
val category = arguments.long("category_id")?.let { categoryRepository.require(it) }
    ?: stored.nominalDimensionId?.let { categoryRepository.getCategoryByDimensionId(it) }

val form = TransactionForm.from(
```

`TransactionForm.from` descarta a categoria cujo tipo não aceita a direção
(`core/model/.../form/TransactionForm.kt:81`), e nada a jusante reconfere: `UpdateTransactionUseCase`
recebe o formulário já normalizado.

A resposta então afirma o contrário do que aconteceu (`TransactionWriteTools.kt:454`):

> `"Edited. Everything the call did not name kept the value it had."`

e a descrição da tool promete o mesmo (`:345`): *"What is not given keeps the value it already has."*

O segundo caso, em `:420-424`:

```kotlin
val card = namedCard?.let { creditCardRepository.require(it) }
val account = ... ?: stored.sourceAccount.takeIf { card == null }
```

Com `card_id` numa receita, `card` resolve não-nulo, `account` fica nulo, `from` força o alvo para
`ACCOUNT`, e a recusa que chega é *"Pick the account."* — apontando um argumento que a chamada nunca
deu. É o mesmo defeito que a 004 corrigiu na criação.

## Cenário de falha

**Descarte da categoria declarada.** `update_transaction(id: 7, category_id: 9)`, com a 9 sendo uma
categoria de despesa e o lançamento 7 uma receita. A categoria é descartada, a resposta diz
`"Edited. Everything the call did not name kept the value it had."`, e o agente relata a
classificação ao usuário. O lançamento segue sem categoria.

**Descarte da categoria armazenada.** `update_transaction(id: 7, type: "income")` num lançamento de
despesa que já tinha a categoria `Mercado`. A chamada não menciona `category_id`, então a categoria
armazenada é carregada — e descartada pela normalização, porque `Mercado` é de despesa. O usuário
perde uma classificação que nunca pediu para mexer, e a resposta afirma que o que não foi nomeado
ficou como estava.

`installments` não se aplica: `update_transaction` não tem esse parâmetro.

## Correção sugerida

As mesmas duas recusas que a 004 instalou na criação, com uma diferença que importa:

- `category_id` declarado e incompatível → recusar nomeando os dois, como em `CreateTransactionTool`;
- `type` mudando de direção com uma categoria **armazenada** que a nova direção não aceita → não é
  um argumento errado, é uma consequência que o chamador não pediu. Recusar nomeando a categoria
  atual e mandando dar um `category_id` compatível (ou explicitamente nenhum) é o que informa o
  agente; descartar em silêncio é o que não informa;
- `card_id` numa receita → recusar pelo cartão, não pela conta ausente.

Não mexer em `TransactionForm.from`, pela mesma razão da 004.
