---
area: transversal
severity: low
type: data
---

# O `PRAGMA foreign_keys` de uma migração não faz nada, e a garantia de que ela depende vem de outro lugar

## Invariante

Uma migração que derruba e reconstrói tabelas referenciadas desliga a checagem de chaves
estrangeiras **ela mesma**, com o `PRAGMA` que abre o seu `migrate()`, e a religa no fim.

Hoje é falso: dentro da transação em que o Room roda uma migração, `PRAGMA foreign_keys` é
ignorado — nas duas direções. As duas migrações que abrem com `PRAGMA foreign_keys=OFF`
funcionam porque o Room já entregou a conexão com a checagem desligada, não porque o comando
surtiu efeito.

## Mecânica

`Migration3To4` e `Migration7To10` abrem com `connection.execSQL("PRAGMA foreign_keys=OFF")` e
fecham com `"PRAGMA foreign_keys=ON"`. `Migration7To10` **depende** disso: faz
`DROP TABLE categories` e `DROP TABLE credit_cards` com filhos apontando para elas —
`budgets.categoryId` e `budget_categories.categoryId` são `ON DELETE CASCADE` no schema v7, e
`invoices.creditCardId` também. Com enforcement ligado, o `DROP TABLE` do SQLite executa um
`DELETE` implícito que dispara essas ações: todo orçamento do usuário sumiria na atualização.

O SQLite documenta que `PRAGMA foreign_keys` é **no-op dentro de uma transação**. O Room roda
`migrate()` dentro de uma; logo os dois comandos são inertes em produção.

O `SQLiteConnectionGuard.verifyForeignKeys()` descreve o estado real e correto — "Enforcement is
off during a migration — it has to be, to rebuild a referenced table — so this is the only moment
the keys are actually verified" — mas atribui esse estado ao `PRAGMA` do arquivo. Quem for
escrever a próxima migração tem duas leituras possíveis do código, e a errada ("o `PRAGMA` no
topo é o que me protege; posso copiá-lo ou omiti-lo") não é contradita por teste nenhum.

`Migration7To10Test` reforça a leitura errada: afirma `assertEquals(1L, scalar("PRAGMA foreign_keys"))`
com o comentário "Enforcement is back on for whatever the app does next". Isso é verdade **só no
teste**, que chama `Migration7To10.migrate(connection)` sobre uma
`BundledSQLiteDriver().open(":memory:")` crua, fora de transação — o único modo em que os
`PRAGMA` funcionam, e um modo que a produção nunca executa.

## Evidência

- `core/database/.../migration/Migration7To10.kt` — `PRAGMA foreign_keys=OFF` na primeira linha
  de `migrate()` e `PRAGMA foreign_keys=ON` no fim; e os `DROP TABLE categories` /
  `DROP TABLE credit_cards`, as operações que dependem do enforcement estar off
- `core/database/.../migration/Migration3To4.kt` — o mesmo par de `PRAGMA`
- `core/database/.../extension/SQLiteConnectionGuard.kt` — `verifyForeignKeys()` e o KDoc que
  descreve o estado
- `core/database/src/jvmTest/.../Migration7To10Test.kt` — `assertEquals(1L, scalar("PRAGMA foreign_keys"))`,
  e o `setup()` com `BundledSQLiteDriver().open(":memory:")`: a migração é exercitada fora do
  caminho do Room

Medido com uma `Migration(13, 14)` de sonda registrada num `Room.databaseBuilder` real (sonda
apagada depois): `PRAGMA foreign_keys` na entrada do `migrate()` → **0**; depois de
`execSQL("PRAGMA foreign_keys=OFF")` → **0**; depois de `execSQL("PRAGMA foreign_keys=ON")` →
**0**, o comando é ignorado; `DROP TABLE categories` dentro da migração **não** cascateou. Numa
segunda sonda, com o banco já aberto: `PRAGMA foreign_keys` → **1**.

## Consequência

Nada quebra hoje: o Room desliga a checagem antes de chamar `migrate()` e a religa depois de
abrir, que é exatamente a propriedade de que `Migration7To10` precisa. O que existe é uma
garantia declarada no lugar errado — dois `execSQL` mortos que parecem ser a defesa contra perda
de dados, e um teste verde que afirma sobre a produção um fato que só vale no modo cru em que o
teste roda. Se um dia a checagem chegar ligada a uma migração que derruba tabela referenciada, o
`PRAGMA` que "cuida disso" não vai cuidar, e o dano é apagar silenciosamente os orçamentos do
usuário.

## Sugestão

Duas partes, independentes: (1) trocar os `PRAGMA` inertes por um comentário que diga de onde a
garantia vem de fato — o Room —, ou por uma asserção que falhe se a checagem chegar ligada; (2)
fazer o teste que hoje afirma `PRAGMA foreign_keys = 1` rodar a migração pelo
`Room.databaseBuilder`, como `MigrationSchemaEquivalenceTest` já faz, para que o que ele prova
seja sobre o caminho real. Não vinculante.
