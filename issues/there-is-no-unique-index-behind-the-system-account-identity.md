---
area: ledger
severity: low
type: data
---

# A identidade de uma conta de sistema não tem índice único que a sustente

## Invariante

Existe no máximo uma linha de sistema por `(type, name, currency)`.

Hoje isso não é garantido pelo armazenamento — só pela disciplina do escritor.
`AccountEntity` não declara índice nenhum, e o schema exportado confirma
`accounts -> "indices": []`.

## Mecânica

`SystemAccount` declara que a tripla identificadora é `(type, name, currency)`, e
`LedgerEntryWriter.ensureSystemAccount()` a implementa como *read-then-insert*: procura com
`getByTypeAndName(...) LIMIT 1` e, não achando, insere. Se duas linhas existissem, o `LIMIT 1`
devolveria a que o SQLite encontrasse primeiro, e o sub-razão daquele nominal ficaria
partido em dois — sem erro, e sem nada contra o que reconciliar.

É a mesma classe de risco que `CategoryEntity` já fechou com
`Index(value = ["systemKey"], unique = true)`, cujo KDoc descreve o dano: *"A second row
under the same key would split the dimension in two… money back in the line it had left,
with no error"*.

## Evidência

- `core/ledger/.../entity/AccountEntity.kt` — `@Entity(tableName = "accounts")`, sem
  `indices`
- `core/database/schemas/com.neoutils.finsight.database.AppDatabase/14.json` — a entidade
  `accounts` com `"indices": []`
- `core/ledger/.../dao/AccountDao.kt` — `getByTypeAndName()`, com `LIMIT 1`
- `core/ledger/.../repository/LedgerEntryWriter.kt` — `ensureSystemAccount()`, leitura
  seguida de inserção
- contraste: `core/database/.../entity/CategoryEntity.kt` — o índice único sobre `systemKey`
  e o KDoc que explica por quê

## Consequência

Uma duplicata seria permanente e silenciosa — metade das despesas numa linha, metade em
outra — e nenhum guard de migração a detectaria: `verifyLedgerBalanced` continua verdadeiro,
porque as duas linhas somam certo.

## O que falta para confirmar

A ausência do índice é fato. **Não há hoje um caminho conhecido que produza a duplicata**:
`ensureSystemAccount` só é chamado de dentro do `immediateTransaction` de
`TransactionRepository`, e `Migration7To10` cria as linhas de sistema uma única vez. É
lacuna de invariante, não defeito ativo — e é por isso que está registrado como `low`.

## Sugestão

Índice único sobre `(type, name, currency)`, com a migração correspondente. Exige decidir o
que fazer com contas de usuário homônimas do mesmo tipo e moeda. Não vinculante.
