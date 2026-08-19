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

## Correção aplicada

Em três camadas, porque o defeito tinha três.

**A raiz, no domínio.** `RecurringForm.toRecurring` passou a resolver a categoria ao lado do cartão,
que ele já resolvia: `category?.takeIf { it.type.isAccept(type) }`. Agora nenhum caminho persiste um
template incoerente — nem o construtor, nem `from`, nem `StartRecurringFromTransactionUseCase` —, e a
KDoc que se declara *"the single owner of what a recurring has to satisfy to exist"* passou a ser
verdade. O filtro fica **antes** do `ensure(title.isNotEmpty() || category != null)`, de propósito:
um template cujo único nome vinha de uma categoria que a direção não carrega é recusado por não ter
nome, em vez de ser gravado sem nenhum dos dois.

A cópia do filtro em `RecurringForm.from` foi **removida**, e não por simetria: os dois consumidores
foram conferidos. `RecurringFormModal` renderiza `selectedCategory`, não `form.category`, limpa a
seleção sozinha quando a direção muda (`LaunchedEffect(type)`) e o `CategorySelector` só lista as
categorias do tipo corrente; `TransactionForm.asRecurringOn` traduz um lançamento para um template da
mesma direção. Nenhum dependia do filtro para o que exibe. O que `from` ainda resolve é o destino,
que é um fato de tela — `target` — que `toRecurring` não tem como enxergar.

**As recusas, nas duas tools.** `create_recurring` e `update_recurring` ganharam o que a
[016](archive/016-update-transaction-drops-the-category-silently.md) instalou na edição de um
lançamento, na mesma ordem — o cartão antes da categoria — e com a mesma distinção entre a categoria
**declarada** (argumento errado) e a **carregada** (consequência que o chamador não pediu). O
`arguments.long("category_id") ?: stored.category` do `update_recurring` apagava justamente essa
diferença.

**A saída, sem a qual a recusa apontaria para o nada.** `update_recurring` passou a ler
`category_id: 0` como *sem categoria*, pela constante `NO_CATEGORY` que já existia — são três tools
lendo-a agora, e a KDoc dela diz isso.

## O que foi recusado durante a revisão

A correção veio com um quarto item que não foi aceito: um *fallback* de título que promovia o nome da
categoria a título do template quando a edição tirava a classificação de um template que não tinha
título próprio. Ele existia para fazer passar um teste **mal desenhado desta sessão**, que criava o
template sem título — e o preço era gravar no template um nome que o usuário nunca escreveu,
respondendo `"Edited. Cycles already confirmed are untouched."` sem dizer que o renomeou. É a mesma
classe de defeito que esta issue e a 016 combatem, instalada enquanto se corrigia uma delas.

`RecurringError.TITLE_OR_CATEGORY_REQUIRED` é a resposta certa: um template sem título e sem
categoria não tem nome. O teste foi corrigido para dar um título ao template, e um teste novo prende
a recusa no caso sem título — `clearing the only name a template has is refused, not settled by
renaming it` —, para que ninguém a "resolva" de novo inventando um nome. Custa ao agente um round
trip a mais, e cada uma das duas recusas é verdadeira.

## O que a issue não previa

Que limpar a categoria esbarra na regra do nome. A issue tratava `category_id: 0` como uma
consequência mecânica da recusa da categoria carregada; é onde ficou a parte mais delicada da
correção, e o que ela revelou é uma regra do domínio que estava certa desde sempre.

## Onde a issue estava certa e o vermelho foi pior do que ela dizia

Ela descreve o estado incoerente para `update_recurring`. A execução mostrou que `create_recurring`
faz o mesmo: `create_recurring(type: "income", category_id: <despesa>)` respondia sucesso com
`"type":"income"` e `"category":"Mercado"` no mesmo payload. Não era descarte silencioso em lugar
nenhum dos dois — era gravação do que o domínio não modela.
