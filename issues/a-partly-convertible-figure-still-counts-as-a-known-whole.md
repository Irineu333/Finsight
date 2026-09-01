---
area: model
severity: medium
type: data
version: 1.10.0
---

# Figura meio conversível recebe magnitude cheia, e o todo continua declarado conhecido

## Cenário

**DADO** base BRL, nenhuma taxa de JPY no acervo, e um mês em que "Alimentação" custou
`{BRL 700}` e "Viagem" custou `{BRL 60, JPY 5000}`
**QUANDO** o usuário abre o detalhamento por categoria, no dashboard ou no relatório
**ENTÃO** "Viagem" aparece com a figura honesta `R$ 60,00 + ¥ 5.000` **e** com 7,9%,
"Alimentação" com 92,1% — as duas somando 100% de um todo de R$ 760 que comprovadamente
não contém os ¥ 5.000 — e "Viagem" é ordenada abaixo de "Alimentação" por uma magnitude
que é só a fração precificada dela
**DEVERIA** uma figura cuja parte não pôde ser colocada na escala não ter magnitude, do
mesmo modo que a figura que não pôde ser colocada de jeito nenhum: todo desconhecido,
nenhuma porcentagem, nenhuma barra

## Mecânica

`comparativeMagnitudes()` devolve `null` só quando **nada** da figura converte. Com um termo
conversível e outro não, ela soma apenas o conversível e devolve um número — que entra em
`total`, passa por `isWholeKnown` (que só procura `null`s) e vira denominador do `shareOf`
de **todas** as outras categorias.

A regra que o próprio módulo escreve — *"One figure without a magnitude does not make a
smaller whole; it makes the whole unknown… 100% of an unknown is not a measurement"* — vale
igual para uma figura *meio* medida. O código só a aplica no caso total.

O KDoc de `CategorySpending.percentage` afirma que o `null` cobre *"or the whole itself is
not known"*; o código não produz esse `null` no caso parcial.

## Evidência

- `core/model/.../usecase/ConsolidateMoneyUseCase.kt` — `comparativeMagnitudes()`: o
  `if (convertible.isEmpty() && significant.isNotEmpty()) null else convertible.sumOf { … }`,
  cujo `else` ignora `significant - convertible`
- mesmo arquivo — `ComparativeMagnitudes.isWholeKnown` (`magnitudes.values.none { it == null }`),
  `.total` (`filterNotNull().sum()`), `.shareOf()`
- `core/model/.../model/CategorySpending.kt` — o KDoc de `percentage` que promete o `null`
- `core/model/.../usecase/SpendingBreakdown.kt` — `spendingBreakdown()` usa a mesma escala
  para ordenar e para a porcentagem
- `core/model/src/commonTest/.../SpendingBreakdownTest.kt` — `an unclassified total no rate
  reaches leaves nobody with a bar` cobre só a figura inteiramente estrangeira; o caso misto
  não tem teste
- chamadores: `feature/report/impl/.../CalculateReportCategorySpendingUseCase.kt` e
  `feature/categories/impl/.../CalculateCategorySpendingUseCaseImpl.kt`

## Consequência

Porcentagens erradas em **todas** as linhas do período — a de baixo subestimada, as demais
superestimadas — e ordenação errada, apresentadas como definitivas. O `ConsolidationBadge`
chega a `STACKED` por causa da figura de dois termos, mas ele explica a *figura*: não avisa
que a porcentagem foi calculada sobre um todo incompleto.

## Sugestão

Tratar "figura com termo não conversível" como "figura sem magnitude" — devolver `null`
quando `convertible.size < significant.size` — ou carregar a informação e fazer
`isWholeKnown` também olhar para ela. Não vinculante.
