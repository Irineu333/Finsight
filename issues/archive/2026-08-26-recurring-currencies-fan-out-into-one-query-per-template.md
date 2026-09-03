---
area: recurring
severity: low
type: performance
verdict: fixed
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

## Desfecho

**Causa real** — a do relato, confirmada no código: `currenciesOf()` chamava
`accountRepository.currencyOf(item)` dentro do `mapNotNull`, e o percurso passara do
subconjunto filtrado para a lista inteira.

**A sugestão estava errada, e o registro é este.** `getAllAccountsIncludingClosed()` é
`SELECT * FROM accounts WHERE type = 'ASSET'` — a fachada de contas. A conta que um cartão
projeta é `LIABILITY` e não está nela, de modo que resolver contra essa leitura tiraria a
moeda de **todo** template de cartão: a lista inteira passaria a renderizar `***` e o resumo a
contá-los como fora da soma. A leitura certa é `getAllLedgerAccounts()`, a carta de contas
inteira, que é justamente o que o KDoc dela diz existir para hidratar uma perna do razão.

**Mudança** — `currenciesOf()` lê a carta uma vez por emissão, monta `id → moeda` e resolve
cada template contra o mapa. A regra não ganhou uma segunda cópia para isso: ela passou a
receber a leitura da conta como parâmetro (`Recurring.currencyBy`, em `feature/accounts/api`),
e `IAccountRepository.currencyOf(Recurring)` é hoje essa mesma regra com a leitura por
consulta. Uma consulta por emissão, para qualquer número de templates de cartão.

**Prova** — nasceu `RecurringViewModelTest.a card template is denominated by the account
behind the card`, que nenhum teste cobria: os demais usam templates que nomeiam conta direto,
e é por isso que a troca pela fachada teria passado verde. Verificado que ele **falha** com a
fachada e passa com a carta. `FakeAccountRepository` passou a distinguir as três leituras como
o `AccountDao` as distingue — enquanto ele devolvia a mesma lista para todas, nenhum teste
podia pegar a diferença. Suíte: `./gradlew jvmTest --rerun-tasks` verde, 1489 testes em 249
classes, nenhuma falha.

**O que continua fora** — os bugs irmãos, `account-balances-fan-out-into-one-query-per-account`
e `dimension-balances-fan-out-into-one-query-per-dimension`, que são da mesma classe e seguem
abertos.
