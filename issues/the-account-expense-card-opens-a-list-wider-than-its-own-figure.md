---
area: dashboard
severity: medium
type: ux
---

# "Despesas na conta" abre a lista de transações sem o recorte que ele mesmo aplicou

## Cenário

**DADO** no mês R$ 500 de despesas em conta e R$ 300 de compras no cartão
**QUANDO** o usuário vê o widget "Balanço em Contas" marcando "Despesas na conta:
R$ 500,00" e toca nele
**ENTÃO** abre uma lista com os R$ 800, compras de cartão incluídas
**DEVERIA** abrir a lista restrita ao mesmo perímetro que produziu os R$ 500

## Mecânica

O widget lê o razão só pelas pernas `ASSET`, e uma compra de cartão não tem perna `ASSET`.
Ao navegar, porém, passa `filterTarget = null` — e o filtro que existe exatamente para esse
recorte, `TransactionTarget.ACCOUNT`, fica sem uso.

O widget vizinho "Balanço Geral" tem perímetro `ASSET + LIABILITY` e passa o mesmo `null` —
e para ele está certo. Os dois exibem números diferentes e levam ao mesmo destino: no
máximo um dos dois pode estar correto.

## Evidência

- `feature/dashboard/impl/.../DashboardComponentContent.kt` —
  `DashboardConcreteBalanceSection()`: `openTransactions(TransactionLabel.EXPENSE, null)`
- mesmo arquivo — `DashboardOverallBalanceSection()`: chamada idêntica, para outro perímetro
- `feature/dashboard/impl/.../DashboardComponentsBuilder.kt` — `concreteBalanceStats()`
  (só `ASSET`) contra `overallBalanceStats()` (`ASSET + LIABILITY`)
- `feature/transactions/impl/.../transactions/TransactionsViewModel.kt` —
  `List<Transaction>.filter(target)`: `it.hasLiabilityLeg == target.isCreditCard`, o recorte
  que falta
- `core/resources/.../values/strings.xml` — `dashboard_balance` = "Balanço em Contas" e
  `balance_card_account_expense` = "Despesas na conta": os rótulos que provam serem dois
  perímetros

## Consequência

O único caminho de "conferir esse número" leva a um conjunto maior que o número. E como a
tela de transações mostra o próprio resumo, o usuário acaba diante de um terceiro total,
diferente dos dois anteriores.

## Sugestão

`DashboardConcreteBalanceSection` passar `TransactionTarget.ACCOUNT` nos dois cartões;
`DashboardOverallBalanceSection` fica como está. Não vinculante.
