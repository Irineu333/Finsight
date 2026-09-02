---
area: recurring
severity: medium
type: data
verdict: fixed
---

# Confirmar recorrência com data de outro mês arquiva lá e deixa o mês corrente pendente

## Cenário

**DADO** uma recorrência ativa e pendente no mês corrente
**QUANDO** o usuário abre a confirmação, muda a data para um dia do mês anterior — o
seletor permite qualquer data passada — e confirma
**ENTÃO** a ocorrência é gravada no mês da data escolhida, o cartão de pendência continua
na dashboard porque o mês corrente segue sem ocorrência, e uma segunda confirmação é aceita
sem qualquer recusa
**DEVERIA** ou avisar que a confirmação foi arquivada em outro mês, ou impedir que a data
saia do mês pendente

## Mecânica

O mês da ocorrência vem da data escolhida (`date.yearMonth`), enquanto a pendência é
decidida só sobre `today.yearMonth`. A recusa de reentrada é por `(recurringId, yearMonth)`,
então meses diferentes nunca colidem — e a mesma despesa mensal é gravada duas vezes.

`DatePickerModal` aceita `minDate`; o modal de confirmação passa só `maxDate`.

## Evidência

- `feature/recurring/api/.../usecase/GetPendingRecurringUseCase.kt` — `invoke()`:
  `occurrences.filter { it.yearMonth == today.yearMonth }`
- `feature/recurring/impl/.../usecase/ConfirmRecurringUseCase.kt` — `invoke()`:
  `val yearMonth = date.yearMonth`, usado na ocorrência e no `cycleNumber`
- `feature/recurring/impl/.../repository/RecurringOccurrenceRepository.kt` —
  `confirmCycle()`: a recusa é por `getByRecurringAndMonth(recurringId, yearMonth)`
- `feature/recurring/impl/.../confirmRecurring/ConfirmRecurringModal.kt` — o
  `DatePickerModal` recebe `maxDate` e não `minDate`
- `core/designsystem/.../modal/date/DatePickerModal.kt` — `selectableDates` suporta
  `minDate`
- `feature/dashboard/impl/.../DashboardComponentContent.kt` —
  `DashboardPendingRecurringSection`, o cartão que não some

## Consequência

Despesa mensal lançada em duplicidade, com saldo e orçamento errados, e um mês marcado como
tratado que a lista de pendentes nunca mais consulta.

## Sugestão

Limitar o `DatePickerModal` da confirmação ao mês pendente, ou fazer a pendência considerar
o mês da data confirmada. A primeira também fecha
`a-recurring-cycle-can-be-numbered-zero-or-below`. Não vinculante.

## Desfecho

**Causa real** — a sugestão acertou o lugar (limitar o seletor) e errou o alcance: o
`maxDate` não era a única coisa que faltava, e o `DatePickerModal` não era o único ponto
que decidia a data. `ConfirmRecurringViewModel` clampava a data em **cinco** lugares
independentes, todos com o mesmo `takeIf { it <= currentDate } ?: currentDate` copiado —
inclusive o que monta o `SkipRecurringModal`, que herda a data da confirmação. Corrigir só
o seletor deixaria os cinco livres para produzir uma data de outro mês por outro caminho.

**Mudança** — nasce `confirmableDates(cycleMonth, today)`, ao lado do `offeredFor` que
este arquivo já publicava como regra pura: a janela é o mês do ciclo que se está
confirmando, nunca depois de hoje. O view model a calcula uma vez, a partir do
`targetDate` com que foi aberto, e os cinco pontos passam a `coerceIn` dessa janela. O
`DatePickerModal` recebe `minDate` **e** `maxDate` dela, de modo que a data de outro mês
deixa de ser escolhível em vez de ser corrigida em silêncio.

O mês passa a ser conteúdo da decisão e não consequência da data: quem abre a confirmação
diz qual ciclo está confirmando, e o campo de data não desfaz isso.

**Prova** — `ConfirmableDatesTest`, quatro casos: o mês corrente aberto até hoje, um mês
já passado aberto por inteiro, a data de outro mês trazida de volta, e o mês ainda por vir
colapsando no primeiro dia em vez de inverter a faixa. Suíte: `./gradlew jvmTest` verde,
1479 testes em 247 classes, e `:app:android:assembleDebug` verde.

**Não coberto por teste automatizado** — que o seletor de fato recusa o toque numa data de
outro mês. Isso é composição e gesto, e o molde do projeto para isso é o Maestro; não há
AVD nesta máquina. O que está provado é a regra e o clamp, que é o que impedia a gravação
duplicada.
