# 009 — o último dia do mês é lido como encerrado, um dia antes da hora

**Área:** mcp · **Tipo:** correção · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`AgentPeriod.isInProgress` é `to > today`, então no último dia do mês ele é `false`. O mês ainda vai
acumular lançamentos o dia inteiro, e o payload o declara completo.

## Evidência

`feature/mcp/impl/.../surface/AgentPeriod.kt:60-71`

```kotlin
fun range(from: LocalDate, to: LocalDate, today: LocalDate, month: String? = null) = AgentPeriod(
    …
    isInProgress = to > today,
    measuredThrough = minOf(to, today),
)
```

Isso contradiz o KDoc da própria factory, duas linhas acima (`AgentPeriod.kt:38`):

> A calendar month, **running while today falls inside it or before it**.

Em 31 de março, hoje *está* dentro de março, e `AgentPeriod.of(YearMonth(2026, 3), today = 2026-03-31)`
responde `is_in_progress: false`.

Onde é consumido: `feature/mcp/impl/.../tool/GetMonthSummaryTool.kt:160-163`

```kotlin
incompleteSide = when {
    currentPeriod.isInProgress && comparedPeriod.isInProgress -> "both"
    currentPeriod.isInProgress -> "this_period"
    comparedPeriod.isInProgress -> "compared_period"
    …
```

## Cenário de falha

Em 31 de março o usuário pergunta *"como este mês se compara a fevereiro?"*. `incomplete_side` vem
`null`, então a resposta apresenta um mês com um dia por correr como um mês completo ao lado de outro
completo. É exatamente a falha que o KDoc do próprio tipo diz existir para evitar
(`AgentPeriod.kt:11-15`) — *"two totals side by side, one of them measured over eleven days, read as
a fall in spending"* — chegando um dia antes em vez de onze.

`upTo` (`AgentPeriod.kt:51-57`) carrega a mesma expressão e a mesma consequência.

## Correção sugerida

`isInProgress = to >= today` nas duas factories. `measuredThrough` já lê corretamente
(`minOf(to, today)` devolve hoje, que é o último dia de qualquer forma), então nada mais se move.

Vale um teste fixando a fronteira: o primeiro e o último dia do mês, e o dia seguinte.
