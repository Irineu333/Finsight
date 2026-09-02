---
area: dashboard
severity: low
type: data
---

# O cartão de conta do dashboard é a única leitura de saldo sem corte de data

## Invariante

Duas figuras vizinhas que descrevem o mesmo dinheiro são lidas com o mesmo corte de data.

Hoje é falso. Sobre a mesma conta `ASSET` existem três leituras, e a do dashboard é a única sem
corte:

| onde | leitura | corte |
|---|---|---|
| `TOTAL_BALANCE` (dashboard) | `calculateBalanceUseCase(target, excluded)` | até o mês corrente |
| `ACCOUNTS_OVERVIEW` (dashboard, logo abaixo) | `entryRepository.balance(id)` | **nenhum** |
| tela de Contas | `accountBalanceUpTo(id, month)` | até o mês selecionado |

## Mecânica

`DashboardComponentsBuilder.totalBalance()` desce em `EntryDao.balanceUpToMonthByType` —
`WHERE a.type = 'ASSET' AND substr(o.date,1,7) <= :yearMonth`.

`DashboardComponentsBuilder.accountsOverview()`, no mesmo arquivo e na mesma tela, chama
`entryRepository.balance(account.id)`, que desce em `EntryDao.balanceOf` —
`SELECT COALESCE(SUM(amount),0) FROM entries WHERE accountId = :accountId`, sem `JOIN
transactions` e portanto sem data nenhuma. O KDoc é explícito: "All-time natural balance of an
account, across every date and currency."

Os outros dois consumidores de `balance()` são guardas de arquivamento
(`ArchiveAccountUseCaseImpl`, `ArchiveCreditCardViewModel`), onde "todo o tempo" é a pergunta
certa — "esta conta ainda guarda dinheiro, em qualquer data?". O dashboard é o único lugar que
**exibe** essa leitura.

A assimetria não nasceu do razão: antes de `4a947adf5` o cartão já somava as pernas da conta em
memória sem corte de data, e a passagem para o razão preservou o número de propósito.

## Evidência

- `feature/dashboard/impl/.../dashboard/DashboardComponentsBuilder.kt` — `totalBalance()` (corte
  por mês) contra `accountsOverview()` (`entryRepository.balance`)
- `core/ledger/.../database/dao/EntryDao.kt` — `balanceOf()` sem `JOIN transactions`, contra
  `balanceUpToMonthByType()` com `substr(o.date, 1, 7) <= :yearMonth`
- `feature/accounts/impl/.../accounts/AccountsViewModel.kt` — a mesma conta lida com
  `accountBalanceUpTo(accountId = account.id, target = month)`
- `feature/accounts/impl/.../usecase/ArchiveAccountUseCaseImpl.kt` e
  `feature/creditcards/impl/.../archiveCreditCard/ArchiveCreditCardViewModel.kt` — os dois
  consumidores para quem "todo o tempo" é a pergunta certa
- `feature/dashboard/impl/src/commonTest/.../DashboardAccountsOverviewTest.kt` — o KDoc que
  registra a escolha: "an all-time balance per account"

## Consequência

As três leituras só coincidem enquanto nenhum lançamento em conta `ASSET` tiver data posterior ao
mês corrente. Havendo um, o total do dashboard deixa de ser a soma dos cartões imediatamente
abaixo dele, e o saldo de uma conta **muda** ao tocar no cartão e chegar na tela de Contas — sem
que nada diga que são perímetros diferentes.

*Hipótese, não verificada: não há caminho direto de UI que produza esse lançamento — todo
formulário que escreve em conta limita a data a hoje (`maxDate = uiState.today`), e
`confirmableDates()` da recorrência também. O único caminho encontrado é indireto e depende de um
bug já registrado (`invoice-dates-follow-the-card-instead-of-the-cycle-they-were-issued-in`):
editar o `closingDay` do cartão depois de fechar a fatura empurra `Invoice.closingDate` para o
futuro, e `Invoice.settlementWindow()` então colapsa a janela de pagamento nessa data futura.*

## Sugestão

Escolher o dono do corte e ter um só. Se o dashboard fala do mês — é o que o seu próprio
`TopAppBar` diz, formatando `uiState.yearMonth` —, o cartão de conta passa a ler
`accountBalanceUpTo(account.id, input.targetMonth)`, igual à tela de Contas, e
`IEntryRepository.balance()` volta a ter apenas os dois consumidores de guarda. Não vinculante.
