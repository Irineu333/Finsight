---
area: transversal
severity: medium
type: ux
---

# O selo de consolidação anuncia a data da consulta como se fosse a data da taxa aplicada

## Cenário

**DADO** base BRL, uma conta em USD, e a única taxa USD/BRL cadastrada em 05/07/2026
**QUANDO** o usuário abre o dashboard em 21/08/2026 e toca no selo `≈` do saldo total
**ENTÃO** lê *"O que pôde ser convertido usou a taxa de 31 de agosto de 2026"* — uma data
no futuro, em que nenhuma taxa existe
**DEVERIA** dizer 05/07/2026, a observação que de fato multiplicou o valor — e que, tendo
mais de 30 dias, a própria tela de taxas marcaria como desatualizada

## Mecânica

`ConsolidateMoneyUseCase` grava `asOf = on`, e `on` é a data **da pergunta**, não a da
observação. O acervo responde "a última observação em ou antes de `on`", então `asOf` e a
taxa realmente usada só coincidem por acaso.

No dashboard `on` é `targetMonth.lastDay` — futuro em todo dia que não seja o último do mês.
No relatório é `endDate`, a data-fim do período.

O app já tem o dono correto dessa regra, e ele discorda deste:
`SuggestCrossCurrencyAmountUseCase` usa `asOf = rate.date`, a data da própria observação, e
`CrossCurrencyAmountFields` a imprime na mesma forma de frase. São dois donos para "a data
da taxa", com respostas diferentes.

A string afirma a data da taxa, não a da pergunta: *"usou a taxa de %1$s"*.

## Evidência

- `core/model/.../usecase/ConsolidateMoneyUseCase.kt` — `invoke()`:
  `asOf = on.takeIf { convertedSomething }`; e `ReducedAmount.asFigure()`:
  `asOf = on.takeIf { isApproximate }`
- `feature/dashboard/impl/.../DashboardComponentsBuilder.kt` — `on = targetMonth.lastDay`
- `core/designsystem/.../component/ConsolidationBadge.kt` — repassa
  `figures.approximateFigure()?.asOf` ao `ConsolidationInfoModal`
- `core/resources/.../values/strings.xml` e `values-en/strings.xml` —
  `money_approximate_converted`
- contraexemplo correto: `core/model/.../usecase/SuggestCrossCurrencyAmountUseCase.kt`
  (`asOf = rate.date`) e `core/ui/.../component/CrossCurrencyAmountFields.kt`
- `core/model/.../repository/IExchangeRateRepository.kt` — `ratesAsOf(date)` devolve
  `Map<String, ExchangeRate>`: a data real está disponível e é descartada

## Consequência

A única superfície que se propõe a explicar de onde veio um número consolidado nomeia uma
data em que nada foi observado — às vezes futura. Quem for conferir a taxa citada em "Ver
taxas" não a encontra, e uma taxa velha demais deixa de ser visível exatamente onde o app
explica a aproximação.

## Sugestão

Propagar a data mais antiga entre as taxas efetivamente aplicadas, em vez de carimbar a
data da consulta. Isso resolve os dois lados de uma vez. Não vinculante.
