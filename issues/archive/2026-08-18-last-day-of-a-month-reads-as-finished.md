---
area: mcp
severity: medium
type: data
verdict: fixed
---

# O último dia do mês é lido como encerrado, um dia antes da hora

**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`
**Reconferido em:** 2026-08-18, @ `32310927a` — as KDocs do tipo **discordam entre si**, e a correção
tem de mover as duas (ver *Duas KDocs, não uma*).

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

## Duas KDocs, não uma

O arquivo tem **duas** descrições de `isInProgress`, e elas não dizem a mesma coisa. A da factory
(`:38`) é a que o cálculo contradiz. A da propriedade (`AgentPeriod.kt:29`) descreve o cálculo
**corretamente**:

> Whether [to] is still in the future on the app's own clock.

Ou seja, não é um caso de código contra documentação, com um lado certo e outro errado: é a
documentação em desacordo consigo mesma, e o código seguindo uma das duas. Corrigir só a expressão
deixa `:29` sendo a afirmação falsa que `:38` é hoje — a mesma divergência, virada do avesso.

Qual das duas é a intenção fica decidido pelo KDoc da classe (`AgentPeriod.kt:11-15`), que diz para
que o campo existe: impedir que dois totais medidos sobre janelas diferentes sejam lidos lado a lado.
Um mês com um dia por correr é uma janela diferente. Logo `:38` enuncia a regra e `:29` descreve o
defeito.

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

Reescrever junto o KDoc da propriedade (`AgentPeriod.kt:29`) para *"whether [to] has not yet been
reached on the app's own clock"* — ou equivalente que inclua o próprio dia. Sem isso a correção troca
qual das duas KDocs mente, em vez de acabar com a divergência.

Vale um teste fixando a fronteira: o primeiro e o último dia do mês, e o dia seguinte.
## Desfecho

`isInProgress = to >= today` nas duas factories, e `measuredThrough` não se moveu — `minOf(to, today)`
já devolvia o próprio dia.

As **duas** KDocs foram mexidas, que era o ponto desta reconferência. A da factory (`:38`) enunciava a
regra e o cálculo a contradizia; a da propriedade (`:29`) descrevia o cálculo corretamente, ou seja,
descrevia o defeito. Corrigir só a expressão trocaria qual das duas mente. A da factory ficou como
estava, porque estava certa; a da propriedade passou a dizer que o último dia é um dia que o período
ainda percorre, e por que isso importa.

## O teste veio antes

`AgentPeriodTest` foi escrito antes da correção e nasceu vermelho em **exatamente três** dos oito
casos: o último dia do mês, uma acumulação que alcança hoje e um intervalo que termina hoje. Os
outros cinco — primeiro dia, meio do mês, dia seguinte ao fim, mês que ainda não começou, e o que
`measuredThrough` cobre — já passavam. O teste não estava falhando por estar errado; falhava onde o
defeito morava.

Não existia teste nenhum para `AgentPeriod` antes disto, e ele é chamado de **15 pontos** da
superfície do agente, em 13 arquivos. A fronteira agora está fixada nos três dias que se confundem.

## A suíte verde não era prova

Suíte cheia depois da mudança: 1658 testes, nenhuma falha. Isso **não** significava que nenhum
consumidor dependia do dia a mais — um dependia, e a suíte não tinha como ver.

`GetInvoiceTool` passava `InvoiceWindow.closingDate` como `to`. `closingDate` é *o primeiro dia que a
janela recusa*; `to` é *o último dia que o período cobre*. Dois erros de um dia que se cancelavam
enquanto `isInProgress` era `to > today`. Fazer o mês percorrer o seu último dia removeu um dos dois e
deixou o outro de pé: no dia do fechamento, o payload de `get_invoice` passou a dizer
`status: "closed"` ao lado de `is_in_progress: true`, e um `measured_through` nomeando um dia em que
nenhuma compra pode cair naquela fatura — exatamente o dia em que o usuário pergunta se a fatura já
fechou.

A suíte ficou verde porque todo caso de fronteira escrito era sobre um mês do calendário, e nenhum
sobre uma fatura. Corrigido no commit seguinte, `af780ae9f`, que dá ao período o último dia que a
janela admite (`InvoiceWindow.lastAdmittedDate`) e traz o teste do dia do fechamento.
