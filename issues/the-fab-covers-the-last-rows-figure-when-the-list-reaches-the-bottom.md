---
area: designsystem
severity: low
type: ux
---

# O FAB cobre a figura da última linha quando a lista chega ao fim

## Invariante

O que uma lista exibe é alcançável por rolagem.

Hoje é falso na cauda de toda lista com FAB flutuante: o botão fica **sobre** o último item, e
como o `LazyColumn` não reserva folga inferior alguma, não há rolagem que o libere — a lista já
está no fim.

## Cenário

**DADO** um aparelho conforme (`finsight_e2e`, API 36, 420dpi) com a janela em 840×411dp — o
breakpoint `LARGE`, onde o shell reserva o painel de detalhe de 400dp e a coluna da lista fica
em 342dp de largura por ~340dp de altura útil
**E DADO** a tela de Recorrentes com o card de resumo e uma recorrência
**QUANDO** a lista é rolada até o fim
**ENTÃO** o FAB fica sobre a coluna direita da linha, e a figura aparece como `$` seguido do
botão — o valor e o dia são ilegíveis, e nenhuma rolagem os descobre

## Mecânica

`contentPadding = PaddingValues(vertical = 16.dp)` reserva 16dp abaixo do último item, contra
os 56dp do FAB mais os 16dp da sua margem. A altura da janela decide se o caso aparece: numa
janela alta a lista curta termina muito acima do botão, e o defeito fica invisível até a lista
encher a viewport — o que numa janela de 411dp de altura acontece com **duas** linhas.

Não é da tela de Recorrentes: o `contentPadding` é o mesmo antes e depois do redesenho dela
(`git show main:…/RecurringScreen.kt`, linha 169), e `TransactionsScreen` e `CategoriesScreen`
declaram a mesma coisa. O que o redesenho mudou foi **o que fica escondido**: a figura passou
para a borda direita da linha, exatamente sob o botão, onde antes havia o meio de uma ficha de
180dp.

`ExchangeRateHistoryScreen` é a única da casa que reserva folga (`bottom = 24.dp`) — e mesmo ela
reserva menos do que o botão ocupa.

## Evidência

- `feature/recurring/impl/.../screen/recurring/RecurringScreen.kt` — `contentPadding` do
  `LazyColumn`
- `feature/transactions/impl/.../screen/transactions/TransactionsScreen.kt` — mesmo
  `contentPadding`
- `feature/categories/impl/.../screen/categories/CategoriesScreen.kt` — idem
- `feature/settings/impl/.../screen/exchangeRateHistory/ExchangeRateHistoryScreen.kt` — a
  exceção, com `bottom = 24.dp`
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — o botão, agora o único do app, é desenhado
  aqui e sobre a área de conteúdo

O **FAB duplo em `LARGE` deixou de existir**: `consolidate-contextual-fab` tirou o botão do
`Scaffold` das nove telas que o declaravam, e a casca passou a desenhar um só. O defeito desta
issue não é esse — ele é a ausência de folga inferior nas listas, e continua inteiro: o botão
único segue sobre a última linha, e nenhuma tela reserva o espaço que ele ocupa.

## Consequência

O último item de qualquer lista da casa tem a sua borda direita inalcançável, e é ali que as
telas põem dinheiro. Numa janela baixa — a janela larga do desktop, o multi-janela do Android,
o painel estreito ao lado do detalhe — a lista enche a viewport com duas linhas, e o caso deixa
de ser de cauda.

## Sugestão

Folga inferior igual ao que o botão ocupa (56dp do FAB + 16dp de margem + o `vertical` que já
existe) no `contentPadding` das listas com FAB, de preferência com um dono só em
`core/designsystem` em vez de repetida por tela. Não vinculante.
