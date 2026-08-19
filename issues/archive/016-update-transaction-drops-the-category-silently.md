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

## Correção aplicada

As três recusas que esta issue pediu, em `UpdateTransactionTool.call`, antes de o formulário ser
montado — e na mesma ordem do `CreateTransactionTool`, o cartão antes da categoria:

1. `type` receita com um cartão em jogo — o nomeado em `card_id` ou o que o lançamento já tinha e
   que sobreviveria — é recusado pelo cartão, com a frase que a criação já usa. A recusa deixou de
   ser *"Pick the account."*, que apontava um argumento que a chamada nunca deu.
2. `category_id` declarado cujo tipo não classifica a direção resultante: recusado nomeando os dois,
   como na criação.
3. A categoria **carregada** que a nova direção não aceita: recusada como consequência, não como
   argumento errado — a razão nomeia a classificação atual, porque quem lê precisa saber o que
   estava prestes a perder sem ter pedido.

A leitura da categoria foi separada em `declaredCategory` (o que `category_id` nomeia) e
`carriedCategory` (a armazenada, carregada só quando a chamada não diz nada sobre `category_id`); o
`?:` anterior apagava justamente essa diferença, que é o que distingue as recusas 2 e 3.

`TransactionForm.from` não foi tocado, pela mesma razão da 004.

## O que a correção obrigou a acrescentar

A recusa 3 manda dar uma categoria compatível *ou explicitamente nenhuma*, e **"explicitamente
nenhuma" não era exprimível**: nesta superfície um `null` explícito é lido como ausência
(`ToolSupport.argument`), e numa edição ausência significa *mantenha o que está lá*. Sem uma forma
de dizer "nenhuma", a guarda tornaria impossível inverter a direção de um lançamento classificado —
algo que a própria sheet do app permite. A recusa apontaria para uma saída que não existe.

Então `update_transaction` passou a ler `category_id = 0` como *sem categoria*, que é como o
`confirm_recurring` já soletrava a mesma coisa, pela mesma razão. Nada que funcionava mudou de
sentido: `0` antes resolvia para uma recusa de "categoria 0 não encontrada".

Como é comportamento além do que os três testes desta issue cobrem, ganhou dois testes próprios —
inverter a direção limpando a classificação, e limpar sem mexer em mais nada. Conferido que mordem:
removida a leitura do zero, eles são os únicos dois vermelhos dos 23 da classe.

E como a decisão passou a existir em dois lugares — `CLEARED` no `confirm_recurring`, `NO_CATEGORY`
aqui, mesmo valor e mesma semântica —, a constante foi promovida a `WriteSupport.kt` e as duas tools
passaram a lê-la de lá. É uma decisão de protocolo, e duas cópias podem divergir em silêncio.

## Onde a issue estava imprecisa

Só nos números de linha, e o código citado é textualmente o que estava no disco. Contra o arquivo em
`ad6551642`: o trecho da categoria está em `:430-433`, não em `:426-429`; o do cartão e da conta em
`:424-425` e `:427-428`, não em `:420-424`; o `note` da resposta em `:458`, não em `:454`; e a
descrição da tool em `:346-348`, não em `:345`.

## O que ficou para trás

O `note` da resposta — *"Edited. Everything the call did not name kept the value it had."* — não foi
reescrito, porque com as três guardas ele voltou a ser verdadeiro: ou a edição mantém a
classificação carregada, ou é recusada. Era a frase que estava certa e o código que a desmentia.
