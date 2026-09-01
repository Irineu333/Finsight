---
area: settings
severity: low
type: data
version: 1.10.0
---

# A confirmação de restauração conta a linha de sistema "Conta encerrada" como conta do usuário

## Cenário

**DADO** uma instalação que subiu da v7 tendo contas apagadas — `Migration7To10` reconstrói
a linha `('Conta encerrada', 'ASSET', 'BRL', …, isArchived = 1)` — e cujo dono abriu 3 contas
**QUANDO** ele escolhe um arquivo para restaurar, ou toca numa cópia guardada
**ENTÃO** a linha **Contas** da folha mostra `4`
**DEVERIA** mostrar `3` — a linha de sistema é um artefato contábil, não uma conta que
alguém abriu

## Mecânica

`readCounts()` conta `accounts` por `type = 'ASSET'` e por mais nada. O KDoc justifica o
filtro dizendo que ele é "a mesma linha que o app traça para decidir o que uma lista de
contas mostra (`AccountDao`)" — e é exatamente aí que a premissa falha: o próprio
`AccountDao` já registra que o tipo não basta, e por isso `currenciesInUse` exclui as linhas
de sistema **por nome** (`AND name NOT IN (:systemNames)`).

`SystemAccount.CLOSED_ACCOUNT` é `ASSET` e `CLOSED_CARD` é `LIABILITY`, então das duas
apenas a primeira entra na conta — o cartão encerrado escapa pelo filtro de tipo, como o
KDoc de `SystemAccount` afirma que todas escapariam.

O erro é sempre de exatamente uma unidade, e sempre para mais.

## Evidência

- `core/database/.../snapshot/CandidateVerifier.kt` — `readCounts()`:
  `SELECT COUNT(*) FROM accounts WHERE type = '$USER_ACCOUNT_TYPE'`, com
  `USER_ACCOUNT_TYPE = "ASSET"`, sem exclusão por nome
- `core/database/.../migration/Migration7To10.kt` — o `INSERT` que cria
  `'Conta encerrada', 'ASSET', 'BRL', 'wallet', 0, …, 1`
- `core/ledger/.../model/SystemAccount.kt` — `CLOSED_ACCOUNT` / `CLOSED_CARD`
- `core/ledger/.../dao/AccountDao.kt` — `currenciesInUse(systemNames)`, o contraexemplo
  correto: filtra por tipo **e** por nome
- `feature/backup/impl/.../ui/modal/confirmRestore/ConfirmRestoreModal.kt` —
  `value = counts.accounts.toString()`, tag `backup_restore_confirm_accounts`
- `feature/backup/impl/.../ui/modal/storedBackupActions/StoredBackupActionsModal.kt` —
  `held?.counts?.accounts?.toString()`, tag `backup_copy_facts_accounts`

## Consequência

Os quatro números da folha existem para uma coisa só: deixar a pessoa reconhecer o próprio
acervo antes de substituí-lo por inteiro — a única operação que este app não desfaz. Um
número que não bate com o que ela vê em Contas é ruído justamente onde ela está sendo pedida
a conferir, e leva a duvidar do arquivo certo.

O mesmo número aparece na folha de uma cópia guardada, onde serve para escolher *qual*
cópia restaurar. As duas telas erram junto, porque leem o mesmo `ArchiveCounts`.

## Sugestão

Excluir as linhas de sistema por nome, como `AccountDao.currenciesInUse` já faz — ou atacar
a causa de que a issue `the-closed-account-system-row-is-an-asset-and-reaches-the-archived-list`
trata, dando a essas linhas uma natureza que o filtro por tipo enxergue. Não vinculante.
