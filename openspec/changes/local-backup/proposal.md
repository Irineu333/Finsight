## Why

O app não oferece nenhuma forma de o usuário guardar os próprios dados. O que existe hoje é um
backup **implícito e desigual**: `app/android/src/main/AndroidManifest.xml:19` declara
`android:allowBackup="true"`, então o Android copia `finsight.db` para a nuvem do Google sem que
ninguém tenha decidido isso e sem que o usuário saiba; o iOS carrega o banco no backup do iCloud
porque `Database.ios.kt:19` o coloca em `NSDocumentDirectory`; e o desktop, que guarda o arquivo em
`~/.finance/finsight.db` (`Database.jvm.kt:8`), não tem backup nenhum. Três plataformas, três
comportamentos, nenhum sob controle de quem digitou os lançamentos.

Pior: nas duas plataformas em que existe, esse backup implícito **pode restaurar dados quebrados**.
O Room abre o banco em WAL — `WRITE_AHEAD_LOGGING` fixo em desktop e iOS
(`RoomDatabase.jvmNative.kt:291`), resolvido para WAL no Android fora de aparelho low-RAM — e tanto
o Auto Backup quanto o iCloud copiam `.db`, `-wal` e `-shm` como arquivos independentes, sem
coordenação transacional entre eles. Um banco em WAL não checkpointado pode ter o **schema inteiro**
no `-wal`: copiado sozinho, o `.db` não abre.

O dado é de partidas dobradas e o app inteiro deriva dele (`core/ledger/README.md`). Perdê-lo é
perder o histórico contábil, e não há como reconstruí-lo de outra fonte.

## What Changes

- **Nova feature `backup`** (`api`/`impl`), com tela própria e entrada em Settings, oferecendo duas
  operações: **exportar** os dados para um arquivo e **restaurar** a partir de um arquivo.
- O formato do backup é o **próprio banco SQLite**, produzido por `VACUUM INTO` — um arquivo único,
  sem `-wal`/`-shm`, que preserva `user_version`, `sqlite_sequence` e `room_master_table`. O
  arquivo recebe uma tabela `backup_meta` que **só existe nele**, nunca no banco de produção.
- A restauração **substitui todo o conteúdo** — não mescla. Não há identidade estável entre
  instalações: toda PK é `@PrimaryKey(autoGenerate = true)` (`AccountEntity.kt:12`,
  `TransactionEntity.kt:30`), e mesclar seria sincronização, não backup.
- A restauração **valida antes de tocar no banco vivo**: bytes mágicos, `integrity_check`,
  `user_version`, cadeia de migrações do Room e os três guardas de invariante que já existem em
  `SQLiteConnectionGuard.kt` — tudo num arquivo temporário, em conexão descartável e isolada. Um
  arquivo reprovado não altera nada.
- A restauração **não reinicia o app**: `ATTACH DATABASE` do arquivo validado dentro de
  `useWriterConnection { immediateTransaction { … } }`, na mesma conexão que carrega os triggers de
  invalidação do Room, de modo que os `Flow` de DAO reemitem sozinhos. É o mesmo padrão de escrita
  que oito repositórios do app já usam (`TransactionRepository.kt:226`, `InvoiceRepository.kt:225`).
- **BREAKING (comportamental, não de API)**: o backup automático da plataforma é desligado.
  `android:allowBackup="false"` mais `dataExtractionRules` e `fullBackupContent` — desde o Android
  12 o atributo sozinho não desliga a transferência device-to-device — e, no iOS, os três arquivos
  do banco passam a ser excluídos do iCloud. **O usuário que trocar de aparelho deixa de recuperar
  os dados de graça** e passa a depender do backup que ele mesmo salvou. É a decisão que torna o
  comportamento igual nas três plataformas, e a tela precisa dizer isso.
- Novo serviço de **entrada** de arquivo por plataforma. O app hoje só sabe escrever
  (`ReportShareService.kt` e seus três `actual`); ler um arquivo escolhido pelo usuário não existe
  em lugar nenhum.

## Capabilities

### New Capabilities

- `local-backup`: o backup como promessa ao usuário — o que o arquivo contém e o que não contém, que
  restaurar substitui tudo e é irreversível, o que é conferido antes de substituir, o que a tela
  mostra antes de pedir confirmação, e que o app não delega backup à plataforma.
- `database-snapshot`: o contrato do banco consigo mesmo — capturar o próprio conteúdo num arquivo
  consistente, verificar a integridade de um arquivo candidato sem se expor a ele, e substituir o
  próprio conteúdo numa transação, preservando os invariantes do razão e sem derrubar os
  observadores.

### Modified Capabilities

Nenhuma. `module-architecture` já governa a criação de uma feature `api`/`impl` sem alteração, e
`platform-adaptive-features` trata de features indisponíveis por plataforma — backup existe nas três.

## Impact

**Banco (`core/database`)**
- `database/Database.kt` — o builder passa a aceitar um caminho, para abrir um arquivo candidato
  fora do banco de produção; hoje o nome é fixo nos três `Database.<plataforma>.kt`.
- `database/extension/SQLiteConnectionGuard.kt` — os três `verify*` são `internal`; ganham uma
  fachada pública, sem expor `SQLiteConnection` a quem chama.
- Novo: captura (`VACUUM INTO`), verificação de arquivo candidato e substituição de conteúdo por
  `ATTACH`, com a ordem de escrita derivada de `PRAGMA foreign_key_list` em vez de uma lista fixa —
  uma lista precisaria ser lembrada a cada migração.

**Nova feature (`feature/backup/{api,impl}`)**
- `api` — `BackupGraph`, `BackupRoute`.
- `impl` — tela, ViewModel, modal de confirmação, módulo Koin, `backupGraph()`, erros com
  `toUiText()`, e o serviço de escolha/gravação de arquivo com `actual` para Android
  (`ActivityResultRegistry`), desktop (`JFileChooser`) e iOS (`UIDocumentPickerViewController`).

**Settings (`feature/settings/impl`)**
- `SettingsScreen.kt` — terceiro `SettingsGroup` com a entrada de backup. O módulo passa a depender
  de `feature:backup:api`, sua primeira dependência de outra feature.

**Shell e app**
- `settings.gradle.kts`, `app/shared/AppNavHost.kt`, `app/shared/di/AppModules.kt` e
  `app/ios/build.gradle.kts` (que lista as `api` em dois blocos, hoje divergentes).

**Plataforma**
- `AndroidManifest.xml` mais `res/xml/data_extraction_rules.xml` e `res/xml/backup_rules.xml`
  (`minSdk = 24` exige os dois formatos).
- `Database.ios.kt` — exclusão dos três arquivos do backup do iCloud.

**Strings**
- Chaves novas em `values/strings.xml` e `values-en/strings.xml`.
