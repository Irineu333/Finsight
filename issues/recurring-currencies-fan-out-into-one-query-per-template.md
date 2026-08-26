---
area: recurring
severity: low
type: performance
---

# A moeda de cada recorrência é uma consulta, e a lista inteira é percorrida a cada emissão

## Invariante

Uma leitura sobre N recorrências é uma consulta, não N.

Hoje é falso na tela de Recorrentes: `RecurringViewModel.currenciesOf()` chama
`accountRepository.currencyOf(item)` **dentro** do `mapNotNull` sobre a lista, e para todo
template de cartão isso é um `getAccountById`.

## Mecânica

A resolução passou a acontecer uma vez por emissão, num mapa que a lista e o resumo
compartilham — o que evitou **dobrar** a conta, não reduzi-la. E o alcance aumentou: antes o
percurso era `filteredFor(filter, recurring).map { … }`, ou seja o subconjunto filtrado; agora
é `recurring` inteiro, arquivadas incluídas. Sob o filtro `ACTIVE`, que é o padrão, uma base
com arquivadas passou a pagar **mais** consultas por emissão do que pagava antes.

O `combine` da tela tem cinco fontes, e uma emissão de qualquer uma refaz o percurso inteiro.

## Evidência

- `feature/recurring/impl/.../screen/recurring/RecurringViewModel.kt` — `currenciesOf()`, e o
  `combine` de cinco fontes que a chama a cada emissão
- `feature/accounts/api/.../extension/RecurringCurrency.kt` — `currencyOf(Recurring)`, um
  `getAccountById` por template de cartão
- `feature/accounts/api/.../repository/IAccountRepository.kt` —
  `getAllAccountsIncludingClosed()`, a leitura única que já existe
- os bugs irmãos, da mesma classe:
  `account-balances-fan-out-into-one-query-per-account` e
  `dimension-balances-fan-out-into-one-query-per-dimension`

## Consequência

O custo de abrir a tela cresce com o número de recorrências de cartão, e volta a ser pago a
cada escrita no razão, a cada troca de filtro e a cada troca de mês do card — inclusive
resolvendo a moeda de arquivadas que a listagem não vai exibir.

## Sugestão

Um `getAllAccountsIncludingClosed()` por emissão e a resolução contra o mapa em memória: uma
consulta, e a regra continua com o seu dono. Não vinculante.
