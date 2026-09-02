---
area: transversal
severity: low
type: data
version: 1.10.0
---

# Os schemas exportados de v12 e v13 sumiram, e a fixture de v12 perdeu a âncora

## Invariante

Uma fixture de teste de migração é o schema antigo **real**, e não uma reconstrução.

A regra é do próprio projeto, escrita em `V7Schema.kt`: *"uma fixture que não é o schema
antigo real não prova nada sobre a migração real"*. Ela não vale mais para v12.

## Mecânica

`exportSchema = true` grava um `N.json` por versão compilada. `12.json` e `13.json`
existiram — `git ls-tree` os mostra em `92c52ac54` e `e6532294b` — e desapareceram no merge
`46a92c61d`, que trouxe `main` para `feature/implement-multi-currency`. Só `14.json`
sobreviveu.

O rastro está nos KDoc: `V10Schema.kt` e `V11Schema.kt` dizem *"verbatim from
`schemas/…/10.json`"*, e esses arquivos existem. `V12Schema.kt` teve de trocar a frase por
*"Frozen history, in the mould of…"* — sem âncora, porque não havia mais contra o que
conferir.

## Evidência

- `core/database/schemas/com.neoutils.finsight.database.AppDatabase/` — só `7.json`,
  `10.json`, `11.json` e `14.json`
- `core/database/src/jvmTest/.../V12Schema.kt` — o KDoc sem "verbatim from"
- `core/database/src/jvmTest/.../V10Schema.kt` e `V11Schema.kt` — o KDoc com ele
- `core/database/src/commonMain/.../AppDatabase.kt` — `version = 14, exportSchema = true`
- `build-logic/.../RoomLibraryConventionPlugin.kt` — `schemaDirectory("$projectDir/schemas")`
- histórico: `git ls-tree -r --name-only 92c52ac54 -- core/database/schemas` (tem `12.json`),
  o mesmo em `e6532294b` (tem `13.json`), e em `46a92c61d` (não tem nenhum dos dois)

## Consequência

Nenhum dano ao usuário: as migrações existem e estão registradas, e um aparelho em v12 ou
v13 continua migrável. O que se perde é a capacidade de **provar** que a fixture v12 é o
schema v12, e a de usar `MigrationTestHelper` para essas duas versões.

## Sugestão

Regenerar os dois arquivos — por checkout dos commits que os introduziram — e devolver a
`V12Schema.kt` a referência "verbatim". Não vinculante.
