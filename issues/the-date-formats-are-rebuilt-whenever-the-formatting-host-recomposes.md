---
area: designsystem
severity: low
type: performance
---

# Os formatos de data são reconstruídos a cada recomposição do host, e invalidam todos os leitores

## Invariante

Um valor publicado num `CompositionLocal` só muda de identidade quando muda de conteúdo.

Hoje é falso para `LocalDateFormats`: `FormattingLocalsHost` constrói `DateFormats(...)` sem
`remember`, e `DateFormats` é uma `class` sem `equals`. Toda recomposição do host cria uma
instância nova, diferente da anterior por identidade, e recompõe **todo** leitor de
`LocalDateFormats` no app.

## Mecânica

O irmão imediato mostra a intenção: o formatador de moeda logo acima é
`remember(symbols) { currencyFormatterOf(symbols) }`, precisamente para trocar de identidade
só quando a tabela troca. `DateFormats` não recebeu o mesmo cuidado, embora dependa apenas de
doze `stringResource` de mês e sete de dia da semana — que mudam junto com a configuração,
não com a recomposição.

O gatilho existe e não é hipotético: o host recompõe quando `symbols` emite, ou seja quando o
usuário edita o símbolo de uma moeda.

## Evidência

- `core/designsystem/.../component/FormattingLocalsHost.kt` — `val dateFormats = DateFormats(...)`,
  sem `remember`; e, três linhas acima, `remember(symbols) { currencyFormatterOf(symbols) }`
- `core/common/.../util/DateFormats.kt` — `class DateFormats(...)`, sem `equals`; e
  `val LocalDateFormats = compositionLocalOf<DateFormats> { … }`, que compara por igualdade
- mesmo arquivo — `abbreviatedMonthNames` e os formatos derivados, reconstruídos com a
  instância

## Consequência

Uma recomposição do host recompõe toda a árvore que lê datas — que é praticamente todo o app.
Frequência baixa, custo alto quando dispara.

## Sugestão

`remember(monthNames, dayOfWeekNames) { DateFormats(...) }`, ou dar `equals` a `DateFormats`.
Não vinculante.
