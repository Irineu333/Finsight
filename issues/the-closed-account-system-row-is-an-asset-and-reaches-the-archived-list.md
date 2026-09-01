---
area: ledger
severity: low
type: data
---

# A linha de sistema "Conta encerrada" é `ASSET`, aparece na lista de arquivadas e pode ser reaberta

## Cenário

**DADO** um banco que subiu de v7 e tinha contas apagadas — a migração reconstrói uma linha
`('Conta encerrada', 'ASSET', 'BRL', isArchived = 1)`
**QUANDO** o usuário abre Contas → arquivadas
**ENTÃO** "Conta encerrada" é listada como se fosse uma conta dele, e o detalhe oferece
**Desarquivar**; reaberta, ela passa a figurar em todo seletor e a aceitar lançamentos
**DEVERIA** as linhas de `SystemAccount` serem invisíveis por construção — nenhuma tela as
oferece, nenhuma as torna postáveis

## Invariante

O KDoc de `SystemAccount` afirma: *"None of these is ever rendered. They are lookup keys…
every listing and selector filters `type = 'ASSET'`, which no row here is (design D10)."*

Hoje é falso: `CLOSED_ACCOUNT` **é** `ASSET`, e `CLOSED_CARD` é `LIABILITY`.

## Mecânica

O filtro em que o invariante se apoia é por tipo, e essas duas linhas têm exatamente os
tipos que ele deixa passar. O único filtro que de fato as esconde é `isArchived = 0` — e a
tela de arquivadas é justamente a que o remove.

Que o filtro por tipo não basta já está escrito no próprio DAO: `currenciesInUse` precisa
excluí-las **por nome**.

## Evidência

- `core/ledger/.../model/SystemAccount.kt` — `CLOSED_ACCOUNT` (`ASSET`) e `CLOSED_CARD`
  (`LIABILITY`), e o KDoc do `object` com a afirmação acima
- `core/database/.../migration/Migration7To10.kt` — o `INSERT` que cria
  `'Conta encerrada', 'ASSET', 'BRL', …, isArchived = 1`
- `core/database/src/jvmTest/.../Migration7To10Test.kt` — assere exatamente essa linha
- `core/ledger/.../dao/AccountDao.kt` — `observeAllAccountsIncludingClosed()`:
  `WHERE type = 'ASSET'`, sem filtro de nome; e `currenciesInUse()`, com
  `AND name NOT IN (:systemNames)`
- `feature/accounts/impl/.../archived/ArchivedAccountsViewModel.kt` — filtra só por
  `Account::isArchived`
- `feature/accounts/impl/.../viewAccount/ViewAccountModal.kt` e `UnarchiveAccountUseCase` —
  a ação de reabrir

## Consequência

Um artefato contábil vira conta do usuário. O nome "Conta encerrada" também fica bloqueado
para ele por `ValidateAccountNameUseCase`. E, reaberta e usada, ela some de
`currenciesInUse` — excluída por nome — o que pode fazer o redutor denominar um zero na base
em vez de na moeda dela.

## Sugestão

Ou marcar essas linhas de forma que o filtro seja por natureza e não por nome — como
`CONVERSION` ganhou um tipo próprio — ou excluí-las por nome também em
`observeAllAccountsIncludingClosed` e `getAllAccountsIncludingClosed`. Em qualquer caso, o
KDoc precisa parar de afirmar o oposto do que o schema faz. Não vinculante.

*Migração, DAO, ViewModel e modal lidos; o app não foi executado com um banco v7.*
