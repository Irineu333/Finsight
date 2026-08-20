## 1. Spike — estratégia de troca a quente (Q1) — **concluído**

- [x] 1.1 `jvmTest` em `core/database` com o banco em **arquivo temporário** (nunca
  `inMemoryDatabaseBuilder`, que usa pool de conexão única e não exercita o pool
  1-escritor/4-leitores do WAL)
- [x] 1.2 Semear o banco vivo, criar um segundo `.db` com o mesmo schema e linhas diferentes, e
  coletar um `Flow` de DAO — feito com `Channel` + `withTimeout`, sem acrescentar Turbine a
  `core/database`
- [x] 1.3 `ATTACH` / `DELETE` / `INSERT` / `DETACH` dentro de
  `useWriterConnection { immediateTransaction { … } }`, afirmando que o `Flow` reemite **sem
  chamada manual a `refreshAsync()`** — confirmado
- [x] 1.4 Medir o risco do `sync()` — **não é alcançável por API pública**; o teste passou a afirmar
  que, qualquer que seja o lado que vença a corrida, o estado final é o restaurado
- [x] 1.5 Resultado registrado no `design.md`, Q1 fechado
- [x] 1.6 Destino de `RestoreSwapSpikeTest.kt` decidido: **vive até a seção 5 e é removido lá**,
  quando cada uma de suas três afirmações tiver equivalente contra a API definitiva — a captura em
  3.3, a reemissão do `Flow` em 5.9 e o assentamento no estado restaurado em 5.9. Removê-lo antes
  disso deixaria "a substituição não derruba quem observa o banco" (spec `database-snapshot`) sem
  nenhuma evidência, que é justamente o que 5.9 passa a cobrir

## 2. `:core:database` — abrir um banco em caminho arbitrário

- [x] 2.1 Parametrizar `getDatabaseBuilder()` por caminho nos três `Database.<plataforma>.kt`,
  mantendo o caminho atual como padrão
- [x] 2.2 Ajustar `DatabaseModule.<plataforma>.kt` para continuar provendo o builder de produção sem
  mudança de comportamento
- [x] 2.3 Teste: abrir um banco em caminho temporário sem afetar o de produção

## 3. `:core:database` — captura (D2)

- [x] 3.1 Expor a captura do conteúdo para um caminho de destino, via
  `useWriterConnection { usePrepared("VACUUM INTO ?1") }` com parâmetro ligado
- [x] 3.2 Tratar as falhas próprias do `VACUUM INTO` como erros tipados: destino já existente,
  ausência de espaço, statement em curso
- [x] 3.3 Teste: o arquivo capturado abre isoladamente, sem `-wal`/`-shm`, e preserva
  `user_version`, `sqlite_sequence` e `room_master_table`
- [x] 3.4 Teste: uma transação aberta e não confirmada em outra conexão não aparece no arquivo
  capturado
- [x] 3.5 Carimbar a origem no arquivo já capturado, dentro da própria captura (D7, D8): criar
  `snapshot_meta` com `formatVersion`, `appVersion`, `platform` e `createdAt` — nunca `@Entity`,
  nunca no `AppDatabase` —, numa conexão descartável do driver, jamais reabrindo o arquivo com Room,
  que o devolveria em WAL e com `-wal`/`-shm` ao lado. `captureInto` passa a receber a origem;
  `schemaVersion` não é gravado, porque o `user_version` já viaja no arquivo
- [x] 3.6 Teste: a tabela existe no arquivo capturado, não existe no banco de produção, e o arquivo
  capturado continua sem `-wal`/`-shm` depois de carimbado

## 4. `:core:database` — verificação de arquivo candidato (D4)

- [x] 4.1 Promover os três `verify*` de `SQLiteConnectionGuard.kt` a uma fachada pública que receba
  um caminho, sem expor `SQLiteConnection` a quem chama
- [x] 4.2 Implementar as camadas 1 a 3 do D4 revisado: `PRAGMA integrity_check` numa conexão
  descartável aberta em `SQLITE_OPEN_READONLY` (tratando os dois desfechos — exceção **ou** linha
  diferente de `ok`), presença de `room_master_table`, e `PRAGMA user_version` entre 1 e a versão do
  schema deste app. O piso de 1 não é zelo: com `user_version = 0` o Room cria o schema e semeia, e
  o arquivo é aprovado como acervo vazio
- [x] 4.3 Implementar a camada 4: abrir o candidato com Room apontando para o temporário, deixando
  a cadeia de migrações e o `checkIdentity` rodarem
- [x] 4.4 Implementar a camada 5: os três guardas de invariante sobre o candidato já migrado
- [x] 4.5 Garantir que toda a verificação use conexão descartável e isolada, nunca a conexão de
  produção
- [x] 4.6 Devolver um resultado tipado que distinga as causas de recusa — em especial "versão de
  schema mais nova que a do app" das demais
- [x] 4.7 Ler o carimbo de origem (`snapshot_meta`) do candidato e as contagens do acervo, tolerando
  a ausência da tabela — origem anulável. As contagens atravessam a fronteira **tipadas por
  fachada**, nunca como nomes de tabela: a feature não conhece tabela alguma (D7)
- [x] 4.8 Testes, um por camada: arquivo que não é banco, banco corrompido, `user_version` maior,
  identidade de schema divergente, razão desequilibrado, dimensão órfã, violação de FK
- [x] 4.9 Testes dos quatro arquivos que a verificação anterior aprovaria, cada um recusado com
  causa distinguível de "arquivo inválido": **arquivo de zero byte**, **banco SQLite válido sem
  tabelas**, **banco de outro aplicativo** e **`.db` principal copiado sem o `-wal`**. Sem os
  quatro, a regressão volta sem ninguém perceber — o desfecho dela é uma restauração bem-sucedida
  que apaga o acervo
- [x] 4.10 Teste: a verificação não cria arquivo em caminho inexistente — com as flags padrão o
  `open` do SQLite **cria** o arquivo, e o arquivo criado passaria em `integrity_check` — e não
  toca o banco em uso. Note que ela *altera* o candidato por construção: a camada 4 roda a cadeia
  de migrações sobre ele, e é por isso que o D4 manda copiá-lo para um temporário antes (camada 0)
- [x] 4.12 Teste: um backup escrito por um schema anterior é **aceito**, com a cadeia de migrações
  rodando sobre ele na camada 4 — o caminho que faz D1 preferir "o formato é o próprio banco" a um
  dump, e que nenhum teste de migração exercita, porque todos aplicam a migração sem o Room
- [x] 4.11 Teste do lado oposto, para que 4.9 não vire uma recusa por volume: **um backup legítimo
  de um acervo vazio é aceito** — zero contas e zero transações é o estado de toda instalação nova,
  e restaurá-lo apaga tudo, que é o que restaurar significa. A verificação recusa por identidade do
  arquivo, nunca pelo tamanho do que ele contém

## 5. `:core:database` — substituição de conteúdo (D5, D6)

- [x] 5.1 Derivar a ordem topológica das tabelas a partir do `sqlite_master` **de `main`** — nunca do
  anexado, que traz `snapshot_meta` junto — e de `PRAGMA foreign_key_list`, sem lista fixa
- [x] 5.2 Implementar a substituição: `ATTACH` fora da transação, `DELETE` de filhos para pais e
  `INSERT … SELECT` de pais para filhos dentro de `immediateTransaction`, `DETACH` após o `COMMIT`
- [x] 5.3 Excluir `room_master_table` e `sqlite_sequence` da cópia, e o carimbo de origem pela mesma
  constante que a captura usa para criá-lo
- [x] 5.4 Não usar `defer_foreign_keys` nem `foreign_keys = OFF` — a ordem topológica é o mecanismo
  (design, D6)
- [x] 5.5 Garantir o `DETACH` em `finally`, inclusive quando a transação reverte
- [x] 5.6 Teste: falha no meio da substituição deixa o acervo anterior integralmente intacto
- [x] 5.7 Teste: após a substituição, os três invariantes valem
- [x] 5.8 Teste: uma entidade nova com FK é contemplada sem alteração no código de substituição
- [x] 5.9 Teste: um `Flow` de DAO em coleta reemite com o acervo restaurado, sem `refreshAsync()`
  manual e sem fechar o banco, e as instâncias já injetadas continuam operando — a afirmação que o
  spike de Q1 sustenta hoje, refeita contra a API definitiva
- [x] 5.10 Remover `RestoreSwapSpikeTest.kt` (tarefa 1.6), uma vez que 3.3 e 5.9 estejam verdes

## 6. Feature `backup` — módulos

- [x] 6.1 Criar `feature/backup/api` com `BackupGraph` e `BackupRoute`, sob `finsight.feature.api`
- [x] 6.2 Criar `feature/backup/impl` sob `finsight.feature.impl`, com os source sets
  `androidMain`/`jvmMain`/`iosMain`
- [x] 6.3 Registrar ambos em `settings.gradle.kts`
- [x] 6.4 Definir `BackupError` com `toUiText()` no `impl`, cobrindo as causas de recusa da tarefa
  4.6 e as falhas de I/O
- [x] 6.5 Registrar `CandidateVerifier` no `databaseModule`, com o `databasePlatformModule` provendo
  a factory de builder por caminho — no Android ela fecha sobre o `Context`, que é a razão de a
  factory existir em vez de a feature montar o builder

## 7. Feature `backup` — escolha e gravação de arquivo (D9)

- [x] 7.1 Declarar em `commonMain` o serviço de arquivo — escolher um arquivo para leitura e gravar
  um arquivo — no molde de `ReportShareService`: `suspend`, `Either`, `PlatformContext`
- [x] 7.2 `actual` Android: `ActivityResultRegistry.register` de três argumentos, contratos
  `OpenDocument` e `CreateDocument`, com `unregister()` garantido e tolerância a resultado que nunca
  chega
- [x] 7.3 `actual` desktop: `JFileChooser.showOpenDialog` e `showSaveDialog`, no molde de
  `JvmReportShareService.kt`
- [x] 7.4 `actual` iOS: `UIDocumentPickerViewController(forOpeningContentTypes:asCopy:)`,
  **mantendo referência forte ao delegate** até a callback, e `startAccessingSecurityScopedResource`
  em `try/finally`
- [x] 7.5 Abrir sem filtrar por tipo (`*/*`; `UTTypeData` no iOS) — a validade é decidida pelo
  conteúdo
- [x] 7.6 Nomear o arquivo exportado como `finsight-backup-AAAA-MM-DD.db`

## 8. Feature `backup` — metadados do arquivo (D8)

- [x] 8.1 Obter a versão do app e a plataforma sem acoplar a feature aos módulos de app, e entregá-las
  à captura — a criação da tabela é do core (tarefa 3.5), a feature só fornece o que só ela sabe.
  **Não reusar `getPlatform().name`**: ele devolve `"Android 34"`, `"Java 21.0.1"`, `"iOS 17.2"` —
  com a versão embutida, para diagnóstico. Um carimbo assim jamais seria traduzível por um `when`
  exaustivo na leitura. O carimbo precisa de um identificador fechado próprio
- [x] 8.2 Exibir a origem lida pela verificação, traduzindo-a; a feature não abre o arquivo nem fala
  SQL — não há precedente disso em `feature/` e esta entrega não abre o primeiro
- [x] 8.3 Teste: o carimbo não retorna ao banco numa restauração — já coberto por
  `DatabaseRestoreTest.the file's control structures are left behind`, que afirma a ausência de
  `snapshot_meta` no banco vivo depois da troca (a cobertura de criação vive na tarefa 3.6)

## 9. Feature `backup` — tela e fluxo

- [x] 9.1 `BackupScreen` com dois grupos, exportar e restaurar, e a declaração do que o arquivo
  contém e do que ele não contém
- [x] 9.0 Promover `SettingsTileTheme` (hoje `private` em `SettingsScreen.kt`) para onde as duas
  telas a alcancem, em vez de copiá-la — ela só configura tokens do tema e não tem nada de
  settings; a segunda cópia é o momento de decidir isso, não a terceira
- [x] 9.2 Declarar na tela que o app não guarda cópias por conta própria e que a recuperação em
  outro aparelho depende do arquivo exportado
- [x] 9.3 `BackupViewModel` com `UiState` e `Action`, orquestrando capturar → gravar e escolher →
  validar → confirmar → substituir. **Apagar os temporários** nos dois fluxos, inclusive quando a
  verificação recusa e quando o usuário cancela: a captura escreve num temporário privado antes de
  ir para o destino escolhido, e o candidato é uma cópia que a verificação migra. Nenhum dos dois
  tem dono além do ViewModel, e `:core:database` não tem API de arquivo para limpá-los
- [x] 9.4 Modal de confirmação exibindo data, plataforma, versão do app e contagens do acervo, com o
  aviso de substituição irreversível — e só após a aprovação do arquivo
- [x] 9.5 Estado de "origem desconhecida" quando o arquivo não tem `snapshot_meta` — a tabela chama-se
  assim desde a tarefa 3.5, e a redação anterior desta task ficou com o nome antigo
- [x] 9.6 `NavGraphBuilder.backupGraph()` como subgrafo `navigation<BackupGraph>`
- [x] 9.7 Módulo Koin da feature: `viewModel {}` para a tela, `single {}`/`factory {}` conforme o
  serviço de arquivo
- [x] 9.8 `Modifier.testTag` nos elementos alcançados por E2E. O modal **não** precisa chamar
  `Modifier.exposeTestTags()` à mão: `ModalManager` já o aplica no `ModalBottomSheet` que toda
  subclasse de `ModalBottomSheet` renderiza. A exigência real é o modal estender essa classe, como
  todos os outros do app — quem se desviar disso é que perde o alcance do driver

## 10. Strings

- [x] 10.1 Chaves novas em `core/resources/.../values/strings.xml` (pt) — tela, modal, erros e o
  aviso sobre backup automático
- [x] 10.2 As mesmas chaves em `values-en/strings.xml` (en)

## 11. Entrada em Settings

- [ ] 11.1 Adicionar `implementation(projects.feature.backup.api)` em
  `feature/settings/impl/build.gradle.kts`
- [ ] 11.2 Terceiro `SettingsGroup` em `SettingsScreen.kt` com a entrada de backup, no molde dos
  `SettingsMenuLink` existentes
- [ ] 11.3 Navegar com `LocalNavController.current.navigate(BackupRoute)`

## 12. Registro no shell e nos apps

- [ ] 12.1 `backupGraph()` em `app/shared/.../AppNavHost.kt`
- [ ] 12.2 `backupModule` em `app/shared/.../di/AppModules.kt`
- [ ] 12.3 `export(projects.feature.backup.api)` **e** `api(projects.feature.backup.api)` em
  `app/ios/build.gradle.kts` — as duas listas
- [ ] 12.4 Teste em `AppModulesTest`: o `backupModule` resolve

## 13. Desligar o backup automático da plataforma (D11)

- [x] 13.1 `android:allowBackup="false"` em `app/android/src/main/AndroidManifest.xml`
- [x] 13.2 Criar `res/xml/data_extraction_rules.xml` com `<cloud-backup>` **e** `<device-transfer>`,
  ambos excluindo todos os domínios — omitir a seção habilita o modo
- [x] 13.3 Criar `res/xml/backup_rules.xml` no formato `<full-backup-content>` para API 24-30, e
  referenciar os dois arquivos no manifesto
- [x] 13.4 iOS: excluir `.db`, `-wal` e `-shm` do backup do iCloud com
  `setResourceValue(true, NSURLIsExcludedFromBackupKey, null)`, reaplicando a cada gravação
- [ ] 13.5 Verificar em aparelho ou emulador que nenhum dos dois mecanismos copia o banco

## 14. Verificação

- [ ] 14.1 `./gradlew jvmTest` verde
- [ ] 14.2 Exportar e restaurar de ponta a ponta no desktop, conferindo que as telas refletem o novo
  acervo sem reiniciar
- [ ] 14.3 Exportar no Android e restaurar no desktop, confirmando a portabilidade do arquivo
- [ ] 14.4 Fechar Q2 do design: verificar em aparelho real se o SAF anexa extensão derivada do MIME
  ao nome sugerido, e ajustar o MIME de escrita conforme o resultado
- [ ] 14.5 Fluxo Maestro em `.maestro/` cobrindo abrir a tela, exportar e recusar um arquivo
  inválido — lendo `.maestro/README.md` §2 antes de rodar, e reportando o dispositivo usado
- [ ] 14.6 Atualizar o `ROADMAP.md` com a linha da funcionalidade
