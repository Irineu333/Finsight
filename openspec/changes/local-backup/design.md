## Context

O app guarda tudo num único `AppDatabase` de 14 entidades, hoje na versão 14
(`core/database/src/commonMain/kotlin/com/neoutils/finsight/database/AppDatabase.kt:36-52`), montado
com `BundledSQLiteDriver` em **commonMain** — uma linha só para as três plataformas
(`Database.kt:53`). O driver embarca a própria SQLite, a **3.50.1** (verificada nos binários nativos
de `androidx.sqlite:sqlite-bundled:2.6.2`), e o formato de arquivo do SQLite é fixo e independente
de arquitetura. Portabilidade binária entre Android, desktop e iOS não é um risco a mitigar: é o
estado atual do projeto.

Três fatos do banco moldam todo o resto do desenho:

1. **O journal mode é WAL.** `WRITE_AHEAD_LOGGING` é o default fixo em desktop e iOS
   (`RoomDatabase.jvmNative.kt:291`); no Android o default é `AUTOMATIC`, resolvido para WAL fora de
   aparelho low-RAM (`RoomDatabase.android.kt:844-853`). O projeto não chama `setJournalMode`
   (`Database.kt:44-55`), então são esses os valores em vigor. Em WAL o pool abre **1 escritor e 4
   leitores** (`RoomConnectionManager.kt:329-341`).
2. **A invalidação depende de triggers TEMP na conexão de escrita.** O `InvalidationTracker` instala
   `room_table_modification_trigger_<tabela>_<tipo>` e a tabela TEMP
   `room_table_modification_log` apenas em conexão não read-only — os leitores ficam em
   `query_only = 1` (`ConnectionPoolImpl.kt:99`, `InvalidationTracker.kt:207,327,465-484`). Escrever
   no arquivo por fora do Room não invalida nada, e é por isso que trocar o arquivo não funciona a
   quente.
3. **Os invariantes do razão já têm um verificador.** `SQLiteConnectionGuard.kt` implementa
   `verifyLedgerBalanced` (Σ entries = 0 por `(transactionId, currency)`),
   `verifyNoOrphanDimensions` e `verifyForeignKeys`, usados juntos ao final de `Migration7To10`,
   `11To12`, `12To13` e `13To14`. São `internal` e operam sobre uma `SQLiteConnection`.

O `CLAUDE.md` estabelece que Σ = 0 é validado no único write boundary (`LedgerEntryWriter.kt:32`) e
em nenhum outro lugar. Uma importação em massa não pode passar por ele — o writer **completa
intenção** (cria conta de sistema sob demanda, posta resíduo de conversão), e reexecutá-lo sobre
dados já completos os reinterpretaria. O desenho abaixo resolve isso sem abrir porta lateral: as
linhas entram por SQL, e o mesmo invariante é **provado** sobre o resultado, com o verificador que
já existe.

## Goals / Non-Goals

**Goals:**
- Exportar todos os dados do usuário num arquivo único, consistente e portável entre as três
  plataformas.
- Restaurar substituindo todo o conteúdo, sem reiniciar o app, com as telas refletindo o novo estado
  sozinhas.
- Reprovar um arquivo inválido **antes** de qualquer alteração no banco vivo.
- Tornar o comportamento de backup igual nas três plataformas, desligando o que a plataforma faz por
  conta própria.

**Non-Goals:**
- **Mesclar** dois acervos. Não há identidade estável entre instalações — toda PK é
  `autoGenerate = true` — e mesclar seria sincronização, outro produto.
- **Backup em nuvem**, agendamento ou backup automático de qualquer espécie. O usuário exporta
  quando quer e guarda onde quiser.
- **Exportação para planilha** (CSV/XLSX). É exportação de dados, não backup: não restaura. Se vier,
  vem em outra entrega.
- **Backup das preferências** (`multiplatform-settings`): moeda base, layout do dashboard, estado de
  sincronização de taxas. O backup é dos dados, não do app como o usuário o deixou.
- Cifrar ou proteger o arquivo por senha.

## Decisions

### D1 — O formato do backup é o próprio banco SQLite

Um dump JSON foi considerado e recusado. A razão não é o custo de escrever o serializador — é que
**migração de formato não é migração de dados**. `Migration7To10.kt` tem 400 linhas e, além de
reescrever o schema, *repara* o que a v7 permitia: "a leg with no aggregate, a leg pointing at a
deleted account, a multi-leg operation that never summed to zero" (KDoc, linhas 18-21). Esse
trabalho existe apenas naquele SQL.

Restaurar um JSON antigo desserializando nas entities atuais **pula toda a cadeia de migrações**, e
pula em silêncio: as colunas novas recebem seus defaults e ninguém percebe. Para não pular, o JSON
teria de recriar o DDL da versão de origem (legível em `core/database/schemas/<versão>.json`),
inserir as linhas ali e então deixar o Room migrar — que é exatamente o que um arquivo `.db` faz
sozinho, com um serializador a menos no caminho.

Um segundo motivo, do mesmo peso: os invariantes do razão já são verificáveis sobre uma
`SQLiteConnection` (`SQLiteConnectionGuard.kt`). Com JSON seria preciso reimplementá-los sobre
estruturas em memória — uma segunda verdade sobre Σ = 0, que é precisamente o que `:core:ledger`
foi desenhado para impedir.

CSV foi descartado por não ser backup: não restaura.

### D2 — A captura é `VACUUM INTO`, nunca cópia de arquivo

Com WAL não checkpointado, o `.db` sozinho não é um backup velho — pode ser um arquivo inútil, com o
schema inteiro no `-wal`. `VACUUM INTO` produz um arquivo único, sem `-wal`/`-shm`, em
`journal_mode = delete`, preservando `user_version`, `application_id`, `sqlite_sequence` (12 das 14
tabelas usam `AUTOINCREMENT`) e `room_master_table`.

Executa por API pública, sem gambiarra:

```kotlin
db.useWriterConnection { txr ->
    txr.usePrepared("VACUUM INTO ?1") { it.bindText(1, tempPath); it.step() }
}
```

Restrições que a implementação precisa respeitar: o destino **não pode existir**; não roda dentro de
transação; e falha se houver statement em curso **na mesma conexão** (`cannot VACUUM - SQL
statements in progress`) — logo, não aninhar em outro `usePrepared`. Transação aberta em *outra*
conexão do pool não atrapalha, e o snapshot exclui o que ela ainda não commitou.

**Alternativas recusadas.** `PRAGMA wal_checkpoint(TRUNCATE)`: o Room descarta o código de retorno
que diria se o checkpoint foi bloqueado, e nada impede as outras quatro conexões de escreverem em
seguida — copiar-se-ia um arquivo em mutação. API *Online Backup* do SQLite: os símbolos
`sqlite3_backup_*` existem no binário nativo, mas `androidx.sqlite:sqlite:2.6.2` não expõe nenhum
binding Kotlin para eles.

O `VACUUM INTO` escreve sempre num temporário privado do app; só depois os bytes vão para o destino
escolhido pelo usuário. Em Android e iOS o destino é um `content://`/`NSURL`, não um caminho, e no
desktop `VACUUM INTO` recusaria o arquivo já criado pelo seletor.

### D3 — Restaurar substitui tudo

Não há chave natural em nenhuma tabela; mesclar exigiria inventar identidade para contas, categorias
e cartões e resolver conflito entre elas. Isso é sincronização, e sincronização é outro produto com
outro modelo de dados.

Como a operação é destrutiva e irreversível, a confirmação carrega o peso: o modal identifica o
arquivo (D8) antes de perguntar, e a tela diz que a restauração apaga o que está no app.

### D4 — A validação acontece num arquivo temporário, em conexão descartável

Cinco camadas, nesta ordem, todas antes de o banco vivo ser tocado:

```
1. os 16 bytes mágicos "SQLite format 3\0"
2. PRAGMA integrity_check
3. PRAGMA user_version <= a versão do schema deste app
4. abrir com Room apontando para o temporário → a cadeia de migrações roda,
   e checkIdentity valida o identity_hash (RoomConnectionManager.kt:276-296)
5. verifyLedgerBalanced + verifyNoOrphanDimensions + verifyForeignKeys
```

A camada 3 é o caso "backup mais novo que o app": o arquivo é portável entre plataformas, e o
desktop não tem atualização automática, então um backup feito num Android atualizado pode chegar a
um desktop atrasado. O Room recusaria o downgrade sozinho — `Database.kt:44-55` não chama
`fallbackToDestructiveMigrationOnDowngrade` — mas checar antes permite dizer *por quê* em vez de
deixar vazar um erro genérico.

**A conexão isolada não é zelo, é requisito.** O SQLite reporta corrupção de um banco anexado como
corrupção **da conexão**, o que dispararia o handler de corrupção contra o banco de produção. O
arquivo candidato nunca encosta na conexão viva antes de aprovado.

### D5 — A troca é `ATTACH` dentro de `useWriterConnection`, sem reiniciar

```kotlin
db.useWriterConnection { conn ->
    conn.execSQL("ATTACH DATABASE ?1 AS backup")   // fora da transação
    try {
        conn.immediateTransaction {
            // DELETE  filhos → pais
            // INSERT  pais → filhos, INSERT INTO main.X SELECT * FROM backup.X
        }
    } finally {
        conn.execSQL("DETACH DATABASE backup")     // só depois do COMMIT
    }
}
```

Funciona por três razões verificadas, não por sorte:

1. o único escritor é justamente a conexão que carrega os triggers TEMP;
2. `DELETE FROM` sem `WHERE` numa tabela observada **não** usa a *truncate optimization* — ela só
   vale para tabela sem trigger —, então deleta linha a linha e o trigger dispara;
3. `useWriterConnection` chama `invalidationTracker.refreshAsync()` ao final
   (`RoomDatabase.kt:500-504`), correção do bug b/340606803 entregue no Room 2.7.0-rc02; o projeto
   está no 2.8.4.

`ATTACH` dentro de transação é permitido desde a SQLite 3.21.0, mas **`DETACH` não é** — falha com
`database backup is locked` —, então o `DETACH` vem depois do `COMMIT` e, por simetria, o `ATTACH`
vem antes do `BEGIN`. O `ATTACH` é por conexão, o que é irrelevante aqui: os leitores estão em
`query_only` e só leem `main`.

Não se copia `room_master_table` nem `sqlite_sequence`. O contador de `AUTOINCREMENT` não zera com
`DELETE`, o que apenas deixa buracos na numeração — inofensivo.

**Alternativas recusadas.** Fechar e reconstruir o `AppDatabase`: os `Flow` já coletados não param
em silêncio, **lançam** `SQLITE_MISUSE "Connection pool is closed"` (`ConnectionPoolImpl.kt:117`), e
os 13 `single<XxxDao>` de `DatabaseModule.kt:44-62` já entregaram DAOs presos à instância morta —
inclusive o `bind RoomDatabase::class` de que `:core:ledger` depende por identidade. Trocar o
arquivo e reiniciar o processo: impossível no iOS. Restaurar pelos repositórios ou use cases:
`InvoiceWriteGuard` vetaria lançar entries em fatura paga — os guardas de domínio existem para a
escrita corrente, não para a reconstituição de um acervo.

### D6 — A ordem de escrita é derivada, não escrita à mão

`PRAGMA foreign_keys = ON` roda em toda conexão (`AppDatabase_Impl.kt:184`) e o schema v14 tem 9
chaves estrangeiras. Um achado empírico decide a forma:

> `PRAGMA defer_foreign_keys = ON` **não** posterga a verificação quando a origem é
> `INSERT INTO main.X SELECT … FROM <anexado>.X`. O `COMMIT` falha com
> `FOREIGN KEY constraint failed` mesmo com o estado final consistente. O mesmo INSERT com `VALUES`
> literais, ou lendo de uma tabela de staging na mesma base, funciona.

Logo, a escrita respeita **ordem topológica**: `DELETE` de filhos para pais, `INSERT` de pais para
filhos, sem pragma algum. E a ordem é **derivada em tempo de execução** — `sqlite_master` para
listar as tabelas, `PRAGMA foreign_key_list` para cada uma, ordenação topológica sobre o grafo —, em
vez de uma lista fixa que precisaria ser lembrada a cada migração. É o mesmo princípio de D1 uma
camada abaixo: nenhuma segunda cópia do schema. `verifyForeignKeys`, que já existe, é a rede de
segurança caso a derivação erre.

`PRAGMA foreign_keys = OFF` antes do `BEGIN` funcionaria, e foi recusado: o pragma é **por conexão e
persistente**, então uma exceção entre o `OFF` e o `ON` deixaria o escritor do processo com FK
desligada até o app morrer.

### D7 — `:core:database` sabe do banco; a feature sabe do backup

`:core:database` ganha três operações, e **a palavra "backup" não aparece nele**: capturar o próprio
conteúdo num arquivo, verificar um arquivo candidato, substituir o próprio conteúdo. São capacidades
de um banco. `feature/backup` é quem chama isso de backup, porque backup é conceito de produto.

A consequência que motiva a divisão: **a feature não conhece nenhuma tabela**. Se conhecesse — a
lista das 14, a ordem de FK, quais tabelas internas pular —, cada entidade nova exigiria lembrar de
atualizar uma feature distante. É o mesmo custo permanente que fez D1 recusar o JSON, aplicado à
fronteira de módulo, e é o que a *Derivation rule* do `CLAUDE.md` pede: a regra derivável do domínio
tem um dono só, no domínio.

Dois ajustes que isso exige no core: os três `verify*` são `internal` e ganham fachada pública (sem
expor `SQLiteConnection` a quem chama); e `getDatabaseBuilder()` fixa o nome do arquivo nos três
`Database.<plataforma>.kt`, precisando aceitar um caminho para abrir o candidato.

Os erros ficam do lado da feature: `:core:database` já tem `exception/` próprio
(`MigrationAbortedException`, `UnbalancedLedgerException`) e não conhece `UiText`. A feature define
`BackupError` com `toUiText()`, como o `CLAUDE.md` estabelece.

### D8 — Metadados numa tabela que só existe no arquivo exportado

Um `.db` puro carrega uma única informação sobre si — `user_version` —, suficiente para a máquina
decidir se aceita e insuficiente para o **usuário** decidir se quer. Num fluxo destrutivo e
irreversível, o modal precisa dizer *qual* backup é: data, plataforma de origem, versão do app.

A tabela **não entra no `AppDatabase`**: uma entidade nova custaria uma migração de schema em
produção para guardar dado que só faz sentido dentro de um arquivo. Ela é criada na exportação,
depois do `VACUUM INTO`, direto no arquivo resultante; na restauração é lida com SQL cru e nunca
copiada, porque a cópia é tabela a tabela.

Campos: `formatVersion`, `schemaVersion`, `appVersion`, `platform`, `createdAt`. Os contadores que o
modal exibe (quantas transações, contas, cartões) saem de `SELECT COUNT(*)` no arquivo já validado —
não precisam ser gravados, e gravá-los criaria uma segunda verdade.

O `formatVersion` parece redundante hoje, já que o formato é a própria SQLite, e é o campo que mais
se paga: se a convenção mudar — compressão, cifra, outro recorte de tabelas —, um inteiro no arquivo
permite recusar com uma frase clara em vez de falhar de modo obscuro.

### D9 — Três `actual` à mão para escolher arquivo, não uma biblioteca

O app já sabe **escrever** arquivo nas três plataformas (`ReportShareService.kt` e seus `actual`),
com o padrão `suspend` + `Either` + `PlatformContext`. **Ler** não existe em lugar nenhum.

- **Android**: `ActivityResultRegistry.register(key, contract, callback)` — a sobrecarga de três
  argumentos (`ActivityResultRegistry.kt:161`), que **não** tem a checagem de lifecycle que faz a de
  quatro estourar depois de `STARTED`. O projeto já está pronto para isso sem saber:
  `ProvidePlatformContext.android.kt:11` constrói o `PlatformContext` a partir de
  `LocalActivityResultRegistryOwner.current`, então o objeto guardado **é** um
  `ActivityResultRegistryOwner`; só o tipo declarado esconde. Contrato `OpenDocument`, sem permissão
  nenhuma, e `unregister()` obrigatório para não vazar a callback.
- **Desktop**: `JFileChooser.showOpenDialog`, cópia carbono de `JvmReportShareService.kt:24-47`.
- **iOS**: `UIDocumentPickerViewController(forOpeningContentTypes:asCopy:)` — o construtor não
  depreciado, confirmado no Kotlin/Native 2.3.10, com `asCopy = true` entregando cópia no sandbox.
  O resultado chega por delegate, então é preciso **manter referência forte ao delegate** até a
  callback; é o erro clássico e não há precedente disso nos `actual` existentes.

**FileKit foi avaliado e recusado**, por incompatibilidades concretas: `iosX64` deixou de ser
publicado a partir da 0.14.2 e o projeto compila `iosX64`
(`build-logic/.../Extensions.kt:39`); a 0.14.1, último teto possível, traz `kotlin-stdlib:2.3.21` —
à frente do compilador 2.3.10 do projeto — e força `androidx.activity:activity-ktx:1.13.0` sobre o
1.12.4 pinado; exige `FileKit.init()` nos módulos de app; carrega JNA e dbus no desktop; e na 0.14.1
o seletor JVM roda na main thread e trava o Compose Desktop. Para uma tela que abre um seletor duas
vezes na vida do app, ~50 linhas por plataforma custam menos.

### D10 — Extensão `.db`, seleção sem filtro, validação por conteúdo

O MIME de um arquivo no SAF é adivinhado pelo `DocumentsProvider` a partir da extensão, e provedores
externos (Drive, Dropbox, OneDrive) adivinham diferente do provedor local — filtrar por MIME faz o
arquivo aparecer esmaecido e inselecionável. Abre-se com `*/*` nas três plataformas (`UTTypeData` no
iOS) e filtra-se depois, pelas cinco camadas de D4. O nome sugerido é
`finsight-backup-AAAA-MM-DD.db`.

Uma extensão própria exigiria declarar `UTExportedTypeDeclarations` no `iosApp/project.yml` e não
compraria nada que a validação por conteúdo já não dê.

### D11 — O backup automático da plataforma é desligado nas duas plataformas onde existe

Manter o Auto Backup do Android e o iCloud do iOS enquanto o app oferece backup explícito seria ter
duas verdades sobre o assunto, e uma delas fora do controle do usuário. Além disso, os dois copiam
`.db`, `-wal` e `-shm` como arquivos independentes, sem coordenação transacional — o que eles
guardam hoje pode voltar incoerente.

- **Android**: `allowBackup="false"` **não basta**. Desde o Android 12 o atributo governa apenas o
  backup em nuvem; a transferência device-to-device é regida pela seção `<device-transfer>` de
  `dataExtractionRules`, e **omitir a seção habilita o modo por completo**. Com `minSdk = 24` são
  necessários os dois arquivos: `dataExtractionRules` (API 31+) e `fullBackupContent` (API 24-30).
- **iOS**: `NSDocumentDirectory` entra no backup do iCloud por padrão — só `/tmp` e
  `/Library/Caches` são excluídos. Exclui-se com
  `setResourceValue(true, NSURLIsExcludedFromBackupKey, null)` nos **três** arquivos, reaplicado a
  cada gravação, porque a Apple avisa que operações de arquivo podem redefinir o valor.

Esta decisão tem peso diferente nas duas plataformas, e a tela precisa reconhecer isso: no Android
remove-se um backup que o usuário não sabia que tinha; no iOS, um que funciona e que a Apple ensinou
o usuário a esperar. Depois desta entrega, **trocar de aparelho só recupera os dados se o usuário
tiver exportado**.

A exclusão no iOS foi decidida explicitamente, com esse custo à vista: a coerência entre plataformas
pesou mais, e o backup que se abre mão de manter é um que pode voltar incoerente.

## Risks / Trade-offs

- **`InvalidationTracker.sync()` é `internal` e `useWriterConnection` não o chama.** Um `Flow` que
  esteja coletando com trigger ainda pendente de instalação não seria notificado da restauração.
  → O spike de Q1 mede se é teórico ou real. Não há API pública para forçar; o contorno indireto
  seria uma escrita via DAO imediatamente antes da troca.
- **O `.also { refreshAsync() }` de `useWriterConnection` só roda em sucesso.** Uma exceção no bloco
  deixa o banco no estado anterior (a transação reverte), mas sem refresh.
  → Aceitável: se a transação reverteu, não há o que invalidar.
- **`VACUUM INTO` exige espaço livre ≈ o tamanho do banco.**
  → Falha de I/O tratada como erro de exportação, com mensagem própria.
- **Backup mais novo que o app**, cenário real do desktop sem atualização automática.
  → Camada 3 de D4 detecta pelo `user_version` e a tela pede a atualização do app.
- **O processo pode morrer com o seletor aberto no Android**, perdendo a continuation.
  → A tela tolera não receber resultado: nenhum estado é alterado até o arquivo chegar.
- **O SAF pode anexar extensão derivada do MIME ao `EXTRA_TITLE`** em `CreateDocument`, produzindo
  algo como `finsight-backup.db.bin` com `application/octet-stream`. Não verificado.
  → Q2: testar em aparelho real antes de fixar o MIME de escrita.
- **Regras de extração não alcançam ferramentas D2D proprietárias de OEM** (Smart Switch e
  similares), que não usam o framework.
  → Sem remédio do lado do app; a promessa da tela não pode ser absoluta.
- **Perda da recuperação automática ao trocar de aparelho** (D11), sobretudo no iOS.
  → Trade-off aceito em favor de comportamento igual nas três plataformas e de um backup que não
  volta incoerente. Precisa estar dito na tela, não só no changelog.
- **`feature/settings/impl` deixa de ser folha no grafo de módulos** — hoje depende só da própria
  `api`, como `support`.
  → Custo de uma linha, permitido pela regra 4 de `module-architecture`; registrado por ser um
  módulo cujo grafo é vigiado de propósito.
- **`VACUUM` pode alterar rowids de tabelas sem `INTEGER PRIMARY KEY` explícito** — atinge
  `CurrencyEntity` (PK não gerada) e `BudgetCategoryEntity` (PK composta).
  → Inócuo: nenhuma consulta de produção referencia `rowid`.

## Migration Plan

Não há migração de schema: o `AppDatabase` continua na versão 14 e nenhuma entidade é adicionada
(D8).

O que muda para quem já usa o app:

1. Na primeira execução após a atualização, o Android para de enviar o banco ao Auto Backup e o iOS
   passa a excluí-lo do iCloud. Cópias já existentes na nuvem não são apagadas pelo app; elas
   simplesmente deixam de ser atualizadas e envelhecem.
2. Nenhum dado local é tocado. O banco continua onde está, nos três caminhos atuais.
3. A tela de backup precisa comunicar a mudança de contrato — que a recuperação ao trocar de
   aparelho passou a depender do arquivo que o usuário guarda.

**Rollback**: reverter a mudança restabelece o backup automático nas duas plataformas na execução
seguinte. Arquivos já exportados permanecem válidos e restauráveis, porque são bancos SQLite com
`user_version` próprio.

## Open Questions

### Q1 — O `Flow` reemite mesmo? — **fechado, confirmado**

Confirmado por spike executado sobre o projeto real, em
`core/database/src/jvmTest/.../RestoreSwapSpikeTest.kt` — três testes, cinco execuções
consecutivas, nenhuma falha:

1. **A troca por `ATTACH` invalida.** Com a coleta de `observeAllCategories()` já em andamento e a
   primeira emissão recebida, o ciclo `ATTACH` → `DELETE` filhos→pais → `INSERT … SELECT`
   pais→filhos → `DETACH` dentro de `useWriterConnection { immediateTransaction { … } }` produz uma
   segunda emissão com as linhas restauradas, **sem nenhuma chamada manual a `refreshAsync()`** e
   sem fechar o banco. D5 está verificado, não inferido.
2. **`VACUUM INTO` produz arquivo autossuficiente.** Executado por
   `usePrepared("VACUUM INTO ?1")` com parâmetro ligado, gera um arquivo sem `-wal`/`-shm`, com os
   bytes mágicos, `user_version = 14` e `sqlite_sequence` preservados. D2 está verificado.
3. **`ATTACH DATABASE ?1` aceita parâmetro ligado através do Room.** Era inferência; passou a fato.

Duas observações que o spike produziu e que valem para a implementação:

- **O risco do `sync()` continua aberto, e não é alcançável por API pública.** A janela que ele
  descreve — coleta iniciada, gatilhos ainda não instalados, troca acontece — não se abre apenas
  lançando o coletor e trocando em seguida: o coletor sequer executa a primeira consulta antes da
  troca. O terceiro teste passou a afirmar o que de fato importa nesse caso: qualquer que seja o
  lado que vença a corrida, o estado em que o `Flow` se assenta é o restaurado, nunca o anterior.
  Evidência a favor de o risco ser teórico: o Room registra o observador **antes** da primeira
  emissão, então receber a primeira emissão já implica gatilhos instalados — que é exatamente a
  condição do teste 1.
- **Um teste cujo último comando devolve valor falha como `InvalidTestClassError`.** `runBlocking`
  assume o tipo do último comando, e o JUnit exige retorno `Unit`. Custou uma execução; anotado para
  não custar outra.

### Q2 — O MIME da escrita no Android — **teste em aparelho**

`CreateDocument` exige MIME concreto (o construtor sem argumento está depreciado justamente porque o
curinga "breaks the automatic handling of file extensions"). Falta confirmar, em aparelho real, se o
SAF anexa extensão derivada do MIME ao nome sugerido. Só afeta o nome do arquivo salvo, não a
leitura — que é por conteúdo (D10).

### Q3 — Exportar o estado atual antes de sobrescrever?

Quando a restauração começa, a maquinaria de captura (D2) já está pronta e testada. Capturar o banco
atual para o diretório de cache imediatamente antes da troca custa uma chamada e faria "não dá para
desfazer" deixar de ser verdade.

Fica fora do escopo desta entrega por decidir o que não está decidido: por quanto tempo essa cópia
vive, se é oferecida ao usuário ou se é uma rede invisível, e o que acontece se o disco não tiver
espaço no pior momento possível. Registrado aqui porque o custo nunca será menor do que no momento
em que D2 estiver implementado.
