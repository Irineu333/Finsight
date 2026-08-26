---
area: recurring
severity: medium
type: data
version: 1.10.0
verdict: fixed
---

# O resumo do mês projeta recorrências em meses anteriores à criação delas

## Cenário

**DADO** uma recorrência de R$ 380,00 criada em agosto de 2026
**QUANDO** o usuário recua o seletor do card de resumo para março de 2026
**ENTÃO** o bloco "ainda não lançado" soma R$ 380,00 e o contador lê "0 de 1 tratadas", sobre
um mês em que a recorrência não existia
**DEVERIA** não compor figura nem contagem em mês anterior à origem da série

## Mecânica

`GetRecurringMonthOverviewUseCase.invoke()` monta a projeção e as contagens a partir do que
`GetUnhandledRecurringUseCase.invoke()` devolve, e o predicado de lá é só
`!recurring.isArchived && id !in handledIds` — `createdAt` não entra nele.

Até esta tela o buraco não era alcançável: o único consumidor era `pendingRecurring()`, que
chama sempre com o mês corrente. O seletor do card o tornou alcançável e sem piso —
`MonthPickerDropdownMenu` navega ano a ano por `onPreviousYear = { selectedYear-- }`, sem
limite inferior.

O domínio já contradiz o card nesse terreno: `ConfirmRecurringUseCase.invoke()` numera o ciclo
por `createdAt.toYearMonth().monthsUntil(yearMonth) + 1`, que num mês anterior à criação dá
zero ou negativo — um ciclo que o card afirma estar por lançar.

## Evidência

- `feature/recurring/api/.../usecase/GetRecurringMonthOverviewUseCase.kt` — `invoke()`: a
  projeção, `handled` e `total` derivam todos do resultado de `getUnhandledRecurring(...)`
- `feature/recurring/api/.../usecase/GetUnhandledRecurringUseCase.kt` — `invoke()`, o predicado
  sem `createdAt`
- `feature/recurring/impl/.../screen/recurring/RecurringViewModel.kt` — `selectedYearMonth`,
  alimentado pelo chip do card e por nada mais
- `core/designsystem/.../component/MonthPickerDropdownMenu.kt` — a navegação de ano, sem piso
- `feature/dashboard/impl/.../DashboardComponentsBuilder.kt` — `pendingRecurring()`, o
  consumidor que sempre passa o mês corrente
- o bug irmão, pela mesma ausência de piso na origem da série:
  `a-recurring-cycle-can-be-numbered-zero-or-below`

## Consequência

O card afirma compromisso e conta ciclo sobre meses em que a série não existia, e a afirmação
escala com a base: dez recorrências criadas este ano leem dez pendências em qualquer mês do
passado. É a metade que o card chama de projeção, e ela deixa de ser sobre o mês selecionado.

## Sugestão

Filtrar por `createdAt` — dentro de `GetUnhandledRecurringUseCase`, se a regra valer também
para o dashboard, ou dentro do panorama, se for para ficar contida. É decisão de produto sobre
o que um mês anterior à origem significa, e a mesma decisão fecha o bug irmão. Não vinculante.

## Desfecho

**Causa real** — a sugestão oferecia dois lugares e o certo era o primeiro, mas por um
motivo que ela não dizia: a regra não existia em lugar nenhum. "A série começa no mês da
sua âncora" estava implícita apenas na fórmula do ciclo, em `ConfirmRecurringUseCase` e
`SkipRecurringUseCase`, e nada a enunciava. O filtro por `createdAt` não bastava também:
`GetRecurringMonthOverviewUseCase` montava a base do contador por conta própria
(`filterNot { it.isArchived }`), então a figura obedeceria ao piso e a contagem não.

**Mudança** — `Recurring` ganha `originMonth`, a âncora lida como mês, e
`generatesCycleIn(month)`, que compõe "a série já começou" com "não foi arquivada". Os
dois consumidores passam a perguntar ao mesmo membro: `GetUnhandledRecurringUseCase` no
predicado da projeção, e o panorama na base do contador — que assim não pode divergir da
projeção que ele conta.

Sem efeito no dashboard: ele pergunta sempre pelo mês corrente, e `createdAt` é o relógio
ou a data de uma transação passada, nunca futura, de modo que o piso lá é inócuo.

**Prova** — três testes, vermelhos antes e verdes depois:
`GetUnhandledRecurringUseCaseTest` — "a template is not unhandled in a month before its
own origin", com o par que impede o filtro de exagerar ("the origin month itself is the
first the template is unhandled for"); e `GetRecurringMonthOverviewUseCaseTest` — "a month
before the template existed carries neither figure nor count", que é o cenário relatado.
Suíte: `./gradlew jvmTest` verde, 1468 testes em 244 classes, e `:app:android:assembleDebug`
verde.

**Fora do desfecho** — o bug irmão `a-recurring-cycle-can-be-numbered-zero-or-below`
continua aberto. `originMonth` é agora a peça que falta para ele, mas confirmar e pular
seguem calculando a âncora por conta própria, e reescrevê-los aqui misturaria dois
desfechos.
