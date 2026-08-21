---
area: dashboard
severity: low
type: ux
---

# A janela de "próximas recorrências" não atravessa a virada do mês

## Cenário

**DADO** 28 de junho, o cartão de recorrências com `daysAhead = 7`, e uma recorrência do dia 3
**QUANDO** o usuário olha o dashboard
**ENTÃO** a recorrência do dia 3 — que vence em 6 dias — não aparece; ela só reaparece em
01/07, já como pendente
**DEVERIA** listar o que vence dentro dos próximos `daysAhead` dias, atravessando o mês

## Mecânica

A janela é calculada em dias do mês corrente, não em datas:

```kotlin
val effectiveDay = currentYearMonth.effectiveDay(recurring.dayOfMonth)
… effectiveDay > input.today.day && effectiveDay - input.today.day <= daysAhead
```

`effectiveDay` é sempre resolvido sobre `currentYearMonth`, então `3 > 28` é falso e a
recorrência é descartada antes da subtração. Nos últimos `daysAhead` dias de qualquer mês, a
lista de próximas fica sistematicamente mais curta do que deveria.

## Evidência

- `feature/dashboard/impl/.../DashboardComponentsBuilder.kt` — `pendingRecurring()`: o filtro
  `upcomingRecurring`, com `effectiveDay` sobre `currentYearMonth` e as duas comparações em
  dia do mês
- mesmo arquivo — a ordenação seguinte, `compareBy { currentYearMonth.effectiveDay(...) }`,
  que repete a mesma premissa
- `core/common/.../extension/YearMonth.kt` — `effectiveDay()`, que é dono da regra de mês
  curto e resolveria a data do mês seguinte igualmente bem

## Consequência

O cartão que existe para avisar do que vem deixa de avisar exatamente na semana em que o mês
vira — e o usuário não tem como perceber que faltou algo.

## Sugestão

Comparar datas em vez de dias: resolver a data efetiva de cada recorrência no mês corrente
**e** no seguinte, e manter a que cai dentro de `today..today + daysAhead`. Não vinculante.
