# 021 — `update_recurring` grava um template incoerente, e `create_recurring` recusa pelo argumento errado

**Área:** recurring / mcp · **Tipo:** dados · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial das correções
da [016](archive/016-update-transaction-drops-the-category-silently.md) e da
[017](archive/017-installment-opens-invoices-before-refusing.md)

## O que está errado

A [016](archive/016-update-transaction-drops-the-category-silently.md) fechou a edição de um
lançamento. A edição de um **template** tem os dois mesmos defeitos, e o primeiro é pior do que o
descarte que a 016 descreve: aqui nem o descarte acontece.

`SaveRecurringUseCaseImpl` monta o formulário pelo **construtor**, não por `RecurringForm.from`:

`feature/recurring/impl/.../SaveRecurringUseCaseImpl.kt:50-57`

```kotlin
// The rules a template has to satisfy live with the form (one owner); what is
// decided here is only what the form has no way to know — ...
val recurring = RecurringForm(
    type = type,
    ...
    category = category,
).toRecurring(...)
```

O comentário afirma que as regras vivem com o formulário, e é justamente o `from` que é pulado —
`RecurringForm.from` é quem aplica `category?.takeIf { it.type.isAccept(type) }`
(`core/model/.../form/RecurringForm.kt:98`). Pelo construtor, nada filtra.

## Cenário de falha

**Template incoerente.** `update_recurring(id: X, type: "income")` sobre um template classificado
sob uma categoria de despesa: a chamada **é aceita**, e o registro fica com um template de receita
classificado sob categoria de despesa. A resposta devolve `"category":"Mercado"` e diz `"Edited."`
Não é o descarte silencioso da 016 — é um estado que o domínio não modela, persistido. Medido pela
revisão, sobre o servidor real.

**Recusa pelo argumento errado.** `create_recurring(type: "income", card_id: X)` responde
`"Account is required."` — a mesma recusa apontando um argumento não dado que a 004 corrigiu na
criação de lançamento e a 016 na edição.

## Correção sugerida

Duas coisas independentes:

- decidir se `SaveRecurringUseCaseImpl` deve passar por `RecurringForm.from` (e então o comentário
  fica verdadeiro), ou se o filtro pertence a outro lugar — mas hoje o comentário descreve um dono
  que o caminho não visita;
- as recusas da 016 em `update_recurring` e `create_recurring`, incluindo o caso da categoria
  **carregada** e o do cartão numa receita.

`update_recurring` também não tem como limpar uma categoria: é a outra edição da superfície em que
ausência significa *mantenha o que está lá*, e `category_id: 0` responde `"No category with id 0
exists."` Ver [022](022-category-id-zero-means-two-things.md).
