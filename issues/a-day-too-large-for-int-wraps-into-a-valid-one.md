---
area: mcp
severity: low
type: data
---

# Um dia grande demais para `Int` dá a volta e vira um dia válido

## Cenário

**DADO** um cartão que fecha no dia 20
**QUANDO** um agente chama `update_card` com `{"id":1,"closing_day":4294967301}`
**ENTÃO** o cartão passa a fechar no dia **5**, e a resposta relata a edição como aplicada
**DEVERIA** recusar o argumento pelo nome: um número que não cabe no campo não é uma edição, é
uma chamada malformada

## Mecânica

`long()` lê qualquer `Long` que caiba no tipo — é o certo, e é o que recusa `"abc"` e o que passa de
`Long.MAX_VALUE`. O `toInt()` que vem depois é uma conversão que **estreita**: `4294967301` é
`2^32 + 5`, e os 32 bits de baixo são `5`.

O invariante do modelo é o que normalmente fecharia isso, e aqui ele não vê nada de errado:
`CreditCard.init` recusa `closingDay !in 1..31` e recebe um `5` legítimo, porque o descarte
aconteceu antes de ele ser chamado. `32` também é alcançável, e esse o `init` recusa — a recusa
existe, só não é sobre o que o chamador escreveu.

A mesma classe existia em `create_installment` e `update_installment`, onde o wrap virava um
parcelamento de outro tamanho; ali a resposta foi teto (`MAX_INSTALLMENTS`), porque a superfície
já tinha decidido clampar uma contagem. Aqui teto não serve: clampar `4294967301` para `31` é
inventar um dia que ninguém pediu, e a faixa `1..31` tem dono — `CreditCard.init` — que uma tool não
pode reimplementar. O que falta é o argumento chegar ao dono **como foi escrito**.

## Evidência

- `UpdateCardTool.call()` (`CardWriteTools.kt:142-143`) — `arguments.long("closing_day")?.toInt()` e
  o mesmo para `due_day`; os dois únicos sítios da superfície que ainda estreitam sem faixa
- `CreditCard.init` (`core/model` — `CreditCard.kt:44-50`) — a faixa `1..31`, aplicada sobre o
  valor já estreitado
- `long()` (`ToolSupport.kt`) — lê `Long`, e é onde a recusa por forma mora hoje
- `requiredCount()` / `count()` — o par que fechou a mesma classe nos dois sítios de parcelamento,
  por teto e não por recusa

## Consequência

O cartão passa a fechar e vencer em dias que a chamada não pediu, e todas as datas de fatura são
derivadas desses dois campos em leitura — o histórico inteiro se move. Não é irrecuperável: o
usuário reedita o cartão, se notar. A faixa é `medium` por enganar sem impedir, e desce um degrau
porque exige um argumento absurdo que só um cliente defeituoso ou um agente confuso produz.

## Sugestão

Um leitor que estreita para `Int` recusando o que não cabe, ao lado dos outros em `ToolSupport`, e
usá-lo nos dois argumentos. A mensagem é sobre o que a tool sabe — o número não cabe —, não sobre a
faixa do dia, que continua sendo do `init` dizer. Não vinculante.
