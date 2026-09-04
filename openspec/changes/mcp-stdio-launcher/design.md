## Context

O servidor MCP é um Ktor CIO em `127.0.0.1`, Streamable HTTP em `/mcp`, atrás de um token
persistido, e vive dentro do processo da janela: `main.kt` do desktop resolve
`McpServerController` do Koin, chama `start()` num `LaunchedEffect` e `stop()` no
`onCloseRequest` (`app/desktop/.../main.kt:37-53`). O controlador
(`DesktopMcpServerController`) monta um `Server` do SDK por sessão, registra as ferramentas de
`mcpTools(McpToolDependencies)`, filtra o `tools/list` pelos eixos concedidos e passa cada
chamada por `AgentActivityJournal`, que grava o registro de atividade.

**Fatos verificados que esta mudança não escolhe:**

- O `invalidationTracker` do Room é do processo. No jar `room-runtime-jvm-2.8.4` o rastreador
  cria `CREATE TEMP TABLE IF NOT EXISTS room_table_modification_log` e gatilhos temporários na
  própria conexão; `refreshAsync()` e `refresh(tables)` só releem essa tabela, e o segundo é
  `@RestrictTo(LIBRARY_GROUP)`. Um segundo processo escrevendo no arquivo não acorda `Flow`
  algum da janela.
- O Room aplica `PRAGMA busy_timeout = 3000` em toda conexão que abre
  (`RoomConnectionManager.kt:172`). Dois processos escrevendo no mesmo arquivo esperam em vez de
  receber `SQLITE_BUSY`.
- O grafo Koin é inteiro preguiçoso: nenhum `createdAtStart`, e os serviços de plataforma no JVM
  são no-op (`NoOpAnalytics`, `NoOpCrashlytics`, `NoOpAuthService`). O Firebase só é exigido
  pelo repositório de suporte (`SupportModule.jvm.kt:9`), que nenhuma ferramenta resolve.
- As preferências no JVM são `PreferencesSettings(Preferences.userRoot())` — `java.util.prefs`,
  legível por qualquer processo do usuário, com sincronização preguiçosa entre processos.
- O launcher gerado por `createDistributable` contém `ArgOptions` e define a propriedade
  `jpackage.app-path`; a documentação do jpackage diz que `--arguments` são apenas valores padrão,
  substituídos pelo que vier na linha de comando. Argumentos chegam ao `main`.
- `kotlin-sdk-server:0.14.0`, já no projeto, traz `StdioServerTransport`; o artefato irmão
  `kotlin-sdk-client` traz `StreamableHttpClientTransport`.
- O banco é `~/.finance/finsight.db`, aberto por `getDatabaseBuilder()` em `:core:database`,
  com a cópia pré-migração decidida por `PreMigrationCopyTarget` (`BackupModule.kt:158`).

**O que a pesquisa disse.** A spec do MCP recomenda stdio para servidores locais; sete dos oito
agentes pesquisados lançam `command` nativamente e o Claude Desktop não aceita HTTP local com
Bearer estático. Nenhum app desktop com MCP embutido responde com a GUI fechada. Duas opiniões
independentes, partindo de briefing neutro, recomendaram o modo stdio e desaconselharam um
serviço em segundo plano.

## Goals / Non-Goals

**Goals:**

- Um agente alcança o Finsight com a janela aberta ou fechada, depois de "Sair" e depois de um
  reboot, no macOS, Windows e Linux, sem instalar nada além do app.
- Toda escrita feita com a janela aberta continua aparecendo na tela sem que o usuário navegue.
- O app segue sendo a única autoridade sobre o que um agente pode fazer.
- O que existe — servidor HTTP, ferramentas, permissões, registro, seção — é reaproveitado, não
  reescrito.

**Non-Goals:**

- Serviço em segundo plano, item de login, bandeja.
- Atender cliente só-HTTP com a janela fechada.
- Escrever nos arquivos de configuração de clientes de terceiros.
- Remover o HTTP, a porta ou o token. Ficam como backend da ponte e caminho avançado.
- Corrigir a espera do `onCloseRequest` na thread de pintura
  (`issues/closing-the-desktop-window-waits-on-the-server-from-the-thread-that-paints.md`). O
  `main.kt` é tocado aqui, mas a issue tem solução própria e fica na sua própria mudança.

## Decisions

### D1 — Um executável, dois modos, despachados pelo argumento

`main()` do desktop lê os argumentos antes de qualquer coisa: `--mcp` entra num ponto de entrada
próprio, sem `application {}`, sem `DesktopFirebase.initialize()` e sem `Window`; qualquer outra
coisa abre a janela exatamente como hoje.

Alternativas: um segundo launcher do jpackage (o plugin do Compose não expõe `--add-launcher`, e a
spec `mcp-server` proíbe um segundo executável); uma variável de ambiente (invisível na
configuração do cliente, que é onde o usuário lê o que está lançando). O argumento é o que os
clientes já sabem passar em `args`.

Em desenvolvimento (`./gradlew :app:desktop:run`) não há launcher; o modo `--mcp` continua
funcionando pelo `main` e o caminho mostrado nas instruções é o de `ProcessHandle` (D9).

### D2 — Transporte stdio, lançado pelo cliente

O processo `--mcp` fala o protocolo por `StdioServerTransport`, um `Server` por processo, e
termina quando o cliente fecha o stdin. Não há porta, não há token na linha de comando e não há
nada residente: "sobreviver ao fechamento" e "sobreviver ao reboot" são verdadeiros porque não
existe estado a sobreviver, e o binário lançado é sempre o instalado.

Alternativa descartada: serviço headless do mesmo binário, registrado por SO. Cobre também
clientes só-HTTP com o app fechado, mas custa três integrações de SO com ressalvas (permissão de
item de login e notarização no macOS, `linger` no Linux, ausência de supervisão na chave `Run`),
supervisão de crash, um daemon com schema defasado depois de uma atualização, e a mesma passagem
de bastão que o stdio já exige. Nenhum agente pesquisado precisa dele.

### D3 — Quem fala MCP não é necessariamente quem é dono do banco

O processo `--mcp` tem dois comportamentos e decide entre eles **a cada chamada**:

```
tools/call chega pelo stdio
  ├── a janela é dona do banco (D4)?
  │     sim ──▶ encaminha para http://127.0.0.1:<porta>/mcp com o token persistido
  └── não ──▶ toma a posse, executa no grafo local, devolve a posse
```

Com a janela aberta, ela executa, e os `Flow`s acordam como acordam hoje. Com a janela fechada,
o processo `--mcp` é o dono e executa. A troca de dono no meio de uma sessão é transparente para
o cliente: a sessão stdio é a mesma, só o executor muda.

Alternativas descartadas. **Avisar a janela e reobservar**: o processo `--mcp` executaria sempre
localmente e a janela, avisada, reobservaria os `Flow`s — mas são 41 consultas `Flow` no Room e
49 funções de repositório em nove módulos a envolver, e a janela aberta passaria a conviver com
outro escritor. **Encerrar o processo `--mcp` quando a janela abre**: o cliente veria o servidor
cair no meio da conversa.

### D4 — A posse do banco é um lock de arquivo do sistema operacional

`:core:database` (`jvmMain`) ganha `DatabaseOwnership`, um lock exclusivo (`FileChannel.tryLock`)
sobre um arquivo ao lado do banco. A janela o toma **antes de montar o grafo** e o segura até
sair. O processo `--mcp` o toma **por chamada local** e o solta ao terminar; se não conseguir, a
janela está aberta ou abrindo, e a chamada vai pela ponte.

Por que um lock e não sondar a porta: entre o Koin subir na janela e a porta ser vinculada há uma
janela de tempo em que as telas já coletam `Flow`s e nenhuma sondagem diria "aberta". O lock é
tomado antes desse intervalo, e é o kernel que impõe a exclusão — no espírito do projeto de
garantir por construção e não por disciplina.

Consequências que a decisão aceita: dois processos `--mcp` com a janela fechada serializam as suas
chamadas no mesmo lock, o que é barato e dispensa raciocinar sobre `SQLITE_BUSY`; e a janela que
encontra o lock ocupado (uma chamada local em curso) espera, com limite (D10), em vez de recusar
abrir.

### D5 — O processo headless sobe o mesmo grafo Koin, sem UI e sem Firebase

`startKoin { modules(appModules) }`, o mesmo agregado do desktop. Nada é resolvido além de
`McpToolDependencies`, `McpServerSettings`, `AgentActivityJournal` e o banco, e nada disso toca
Compose ou Firebase. O banco é aberto pelo mesmo `databasePlatformModule`: as migrações rodam por
um caminho só, a cópia pré-migração do cofre acontece como na janela, e a captura preventiva antes
de apagar acontece no processo que executa, como já acontece.

Alternativa descartada: um módulo Koin próprio do headless, "só o necessário". Seriam dois
agregados a manter em sincronia, e o teste que hoje prova que o grafo fecha
(`McpToolDependencies` resolvido de uma vez) passaria a provar só um deles.

### D6 — `stdout` pertence ao protocolo

A primeira instrução do ponto de entrada `--mcp` guarda a referência ao `stdout` original para o
transporte e troca `System.out` por `System.err`. Um `println` de qualquer biblioteca — o
`DesktopFirebasePlatform.log` faz exatamente isso — corromperia a sessão em silêncio. `stderr` é
o canal de diagnóstico: os clientes o exibem, e uma linha de abertura diz versão, modo e o que o
processo encontrou (janela aberta ou fechada, servidor habilitado ou não).

Sem arquivo de log: seria retenção, rotação e mais uma superfície. Se o suporte pedir, entra numa
mudança própria.

### D7 — A autoridade do app vale igual no modo stdio

O processo `--mcp` lê `McpServerSettings` da mesma persistência que a janela (`java.util.prefs`,
com `sync()` antes da leitura), uma vez, ao iniciar. Com o servidor **desabilitado**, o processo
ainda fala o protocolo — para que o cliente e o agente leiam um motivo em vez de um processo que
morreu — mas não anuncia ferramenta alguma e recusa qualquer chamada dizendo que o servidor está
desligado nas configurações do app. As permissões por eixo filtram o `tools/list` e recusam a
chamada exatamente como em `DesktopMcpServerController`, pelo mesmo código (D8).

Ler uma vez basta: enquanto a janela está fechada ninguém altera a escolha, e enquanto está
aberta a chamada vai pela ponte e é a janela que aplica a escolha viva.

### D8 — A montagem do servidor é uma, e a ponte encaminha o protocolo, não as ferramentas

O que hoje é `newServer()` + `register(tool)` + `grantedToolList()` em
`DesktopMcpServerController` sai para um montador comum (`McpSessionFactory`, nome indicativo):
dado o journal, as settings e a lista de ferramentas, monta um `Server` do SDK pronto para
qualquer transporte. O controlador HTTP e a sessão stdio o chamam; o teste
`McpSurfaceIsClosedTest` e os testes de protocolo passam a valer para os dois.

A ponte é um `Client` do SDK (`StreamableHttpClientTransport`) contra o servidor embutido, com o
token persistido no header. Ela encaminha `tools/list` e `tools/call` como chegam e reemite
`notifications/tools/list_changed` ao cliente stdio. Nada da semântica das 58 ferramentas é
reescrito. Com a janela fechada, o `Server` local montado por `McpSessionFactory` responde.
Nos dois casos o cliente enxerga um servidor só: a mesma lista, as mesmas recusas.

### D9 — As instruções mostram o comando com o caminho do executável instalado

A seção passa a apresentar, como instrução principal, o bloco `command` + `args` que os clientes
usam e a linha `claude mcp add finsight -- "<caminho>" --mcp`, copiáveis. O caminho vem de
`System.getProperty("jpackage.app-path")`, definido pelo launcher empacotado (verificado no
binário gerado; registrado em `JDK-8272328`), com `ProcessHandle.current().info().command()`
como fallback em desenvolvimento. No macOS o caminho é o binário dentro do `.app`
(`Finsight.app/Contents/MacOS/Finsight`); no Windows, `Finsight.exe` na instalação por usuário;
no Linux, `/opt/finsight/bin/Finsight`.

O bloco HTTP com endereço e token continua disponível, recolhido sob "avançado", para clientes
que preferem `url` com a janela aberta. A frase "só responde com o app aberto" sai; entra "funciona
com o app aberto ou fechado".

O controlador expõe o comando (`launchCommand`) porque a seção não deve descobrir propriedades do
sistema; é o mesmo padrão pelo qual ela lê a porta e o token dele.

### D10 — Limites de espera, ditos e não implícitos

- A janela espera o lock por até 10 s, em tentativas curtas, e depois abre de qualquer forma:
  uma chamada local mais longa que isso não é um caso real, e recusar abrir o app por causa de um
  lock seria pior que um `Flow` que perde uma atualização.
- A ponte, ao encontrar o lock tomado e a porta ainda fechada (janela abrindo), tenta conectar por
  até 5 s antes de responder ao cliente que o app está iniciando e a chamada deve ser repetida.
- Com a janela fechada no meio da sessão, a chamada que estava em voo pela ponte volta como erro
  ao cliente; a seguinte já é local.

### D11 — Verificação em três alturas

1. **Unidades**: `DatabaseOwnership` (toma, recusa, solta, espera), a decisão por chamada com
   dono falso, a recusa com servidor desabilitado, a troca de `stdout`.
2. **Protocolo**: os testes `OverTheProtocol` já falam com o servidor HTTP; a sessão stdio ganha
   o mesmo, por pipes em processo com o `StdioClientTransport` do SDK. Os cenários das specs
   viram testes aqui: escrita local, escrita encaminhada chegando a um `Flow` já coletado, janela
   abrindo e fechando no meio da sessão.
3. **Distribuição**: uma tarefa Gradle (`verifyMcpLauncher`, dependente de `createDistributable`)
   lança o launcher empacotado com `--mcp` e completa `initialize` → `tools/list`. Não entra em
   `jvmTest` porque exige a imagem; é executada à mão como a suíte Maestro, e o resultado é
   reportado dizendo em que SO rodou. `McpServerReachesTheDistributionTest` passa a exigir também
   o artefato cliente na distribuição.

## Risks / Trade-offs

- **`kotlin-sdk-client:0.14.0` pode não fechar com o pino do projeto** → o primeiro passo é um
  spike de dependência; se o artefato exigir outra versão de Ktor ou stdlib, a ponte cai para um
  cliente HTTP mínimo sobre `ktor-client-core` (já no projeto) falando Streamable HTTP à mão, o
  que é viável porque só encaminha `tools/list`, `tools/call` e uma notificação.
- **Um `println` corrompe a sessão** → D6, mais um teste que escreve em `System.out` durante uma
  sessão e prova que o protocolo continua íntegro.
- **Corrida de migração após atualização** (dois clientes iniciando juntos com a janela fechada)
  → o lock de posse serializa; a migração roda sob ele, e o segundo processo encontra o banco já
  migrado.
- **Caminho obsoleto na configuração do cliente** depois de reinstalar em outro lugar → a seção
  mostra sempre o caminho atual e o diagnóstico em `stderr` diz qual versão respondeu.
- **Boot de 1–2 s e ~100 MB por sessão de cliente** → aceito; é uma vez por sessão, não por
  chamada, e o proxy da JetBrains faz o mesmo. Medir na tarefa de distribuição; não carregar
  Compose nem Firebase no modo `--mcp`.
- **Janela abre em desenvolvimento e o cliente lançou o binário instalado** (versões diferentes)
  → a ponte fala o protocolo, não classes; a versão que responde é a da janela, e a linha de
  abertura em `stderr` diz qual é.
- **`jpackage.app-path` não é documentada no man page** → há fallback, e o teste de distribuição
  prova que o valor existe no binário gerado.
- **Um `Flow` pode perder uma escrita local feita no exato instante em que a janela abre** →
  D4 fecha a janela de tempo com o lock; o que resta é o limite de 10 s de D10, que não é um caso
  real.

## Migration Plan

Nenhuma migração de banco. A mudança é aditiva no código e nas specs:

1. Entram o lock de posse, o montador comum e a sessão stdio, sem tocar o comportamento do HTTP.
2. Entra o despacho no `main()` e o comando nas instruções da seção.
3. As strings mudam em `values/strings.xml` e `values-en/strings.xml` no mesmo passo.
4. Rollback é remover o despacho: sem `--mcp`, tudo se comporta como hoje.

## Open Questions

Nenhuma bloqueante. As duas decisões que estavam abertas foram tomadas: o HTTP fica como caminho
avançado (D9), e o diagnóstico é `stderr` sem arquivo (D6). O spike de dependência (primeira
tarefa) é o único ponto que pode mudar a forma da ponte, e o desvio está descrito em Riscos.
