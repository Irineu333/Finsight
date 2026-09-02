---
area: settings
severity: low
type: data
version: 1.10.0
---

# A varredura limita as cópias que **tenta** remover, não as que remove — e cinco recusas a paralisam para sempre

## Cenário

**DADO** o cofre ligado, retenção em 5, e um destino que guarda 5 arquivos com nome deste
app que a checagem de conteúdo recusa — truncados por um processo morto durante a escrita
(o próprio `BackupVault.land` admite deixá-los de pé: *"a truncated or corrupted file …
may be left standing rather than removed"*), ou escritos por uma versão de esquema mais
nova — logo abaixo das 5 cópias mais recentes
**QUANDO** o cofre captura, e captura de novo, e de novo
**ENTÃO** nenhuma cópia é removida em nenhuma delas: as 5 recusadas ocupam a janela inteira
que a varredura examina, e o destino fica parado acima do limite indefinidamente
**DEVERIA** remover as cópias removíveis que estão além do limite — o número que a pessoa
escolheu é o número que o destino guarda

## Mecânica

A varredura monta uma sequência, descarta as `keep` mais novas e então corta em
`MAX_REMOVED_PER_SWEEP = 5`. O corte é aplicado **antes** de qualquer remoção acontecer,
sobre os *candidatos*:

```kotlin
copies.asSequence()
    .filterNot { it.name == PRE_MIGRATION_BACKUP_NAME }
    .drop(keep)
    .filterNot { … sparing … }
    .take(MAX_REMOVED_PER_SWEEP)
    .forEach { copy -> if (target.remove(copy).getOrNull() == true) copyRemoved(copy) }
```

`remove` responde `false` quando `OwnCopyCheck` não prova que o arquivo é deste app — e ele
recusa de propósito tudo que é corrompido, truncado, de outro esquema ou ilegível
(`provesOwnCopy()`), além de responder `false` para uma checagem que sequer conseguiu rodar.
Nada disso desconta do corte: as mesmas cinco entradas são escolhidas na varredura seguinte,
e na seguinte.

E como o `take` fica *depois* do `drop` — deliberadamente, para poupar as mais antigas —
a janela examinada é sempre `keep+1 .. keep+5`, a faixa em que as recusadas se acomodam.

O KDoc do método afirma o contrário do que o código faz: *"so the destination still
converges on [keep]"*.

## Evidência

- `feature/backup/impl/.../domain/vault/BackupVault.kt` — `sweep()`: `take(MAX_REMOVED_PER_SWEEP)`
  antes do `forEach`, e o KDoc que promete convergência
- `feature/backup/impl/.../domain/vault/service/OwnCopyCheck.kt` — `confirms()` e
  `provesOwnCopy()`: `CORRUPTED`, `NOT_A_DATABASE`, `SCHEMA_TOO_NEW`, `SCHEMA_MISMATCH` e
  qualquer exceção respondem `false`
- `feature/backup/impl/.../domain/vault/BackupVault.kt` — `land()`, o comentário que aceita
  deixar um arquivo truncado de pé no destino
- `feature/backup/impl/src/jvmTest/.../BackupVaultTest.kt` —
  `a single sweep never removes the whole excess at once, but retention still converges`:
  planta 15 cópias **todas removíveis**, que é o caso em que a promessa vale
- `feature/backup/impl/src/jvmTest/.../FolderVaultTest.kt` —
  `retention never touches a file the app did not write`: com **um** arquivo recusado o
  destino já termina em 6 sobre um limite de 5; o teste afirma só que o arquivo alheio
  sobreviveu, e não o tamanho do destino

## Consequência

O seletor de retenção passa a não governar nada: a folha oferece 5, 10 ou 20, e o destino
fica onde estava. Baixar o limite deixa de ter efeito, que é justamente o gesto de quem
está sem espaço.

O destino não cresce sem limite — cada captura nova empurra uma removível para dentro da
janela e ela sai —, mas estabiliza permanentemente acima do número escolhido, e tudo que
já estava abaixo das cinco recusadas nunca mais é alcançado.

## Sugestão

Contar remoções e não tentativas: percorrer os candidatos além do limite e parar depois de
`MAX_REMOVED_PER_SWEEP` remoções bem-sucedidas. Não vinculante.
