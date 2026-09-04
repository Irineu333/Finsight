> Ordem deliberada: a dependência e os dois testes de premissa vêm primeiro, porque tudo depois
> assume o que eles provam. A posse do banco e o montador comum (grupos 2 e 3) não alteram
> comportamento algum e podem ser mesclados sozinhos. O modo stdio (grupo 4) já entrega "funciona
> com o app fechado" sem a ponte; a ponte (grupo 5) entrega "funciona com o app aberto". A seção e
> a distribuição fecham a mudança.

> **Linha de base da suíte**, medida sobre `ccfddddd5` com a árvore limpa, antes do grupo 1:
> **2973 casos, 0 falhas**. É contra ela que 8.4 confere o total. Depois dos grupos 1 e 2 são 2988
> (10 da posse do banco, 5 das premissas).

## 1. Dependência e premissas

> As três verificações que estavam para o spike foram feitas durante a proposta e estão em
> Context do design: o artefato cliente fecha com os pinos do projeto, o launcher empacotado define
> `jpackage.app-path` e repassa `--mcp` ao `main`, e as preferências só chegam ao disco com
> `flush()`. O que resta aqui é pôr a dependência no build e transformar as premissas em testes.

- [x] 1.1 Verificado antes da implementação: `io.modelcontextprotocol:kotlin-sdk-client:0.14.0` existe no Maven Central e depende de `kotlin-sdk-core:0.14.0`, `ktor-client-core:3.4.3`, `kotlin-stdlib:2.3.21` e `kotlin-logging:8.0.4`, todos já trazidos pelo servidor. Nenhuma versão do app sobe.
- [x] 1.2 Adicionar o artefato ao catálogo e ao `jvmMain` de `feature/mcp/impl`; `./gradlew :feature:mcp:impl:dependencies --configuration jvmRuntimeClasspath` confirma que nada foi elevado.
- [x] 1.3 Exercitar em teste, contra o `McpServerHarness` existente, um `Client` do SDK com `StreamableHttpClientTransport` apresentando o token: `initialize` → `tools/list` → `tools/call` e a recepção de `notifications/tools/list_changed`.
- [x] 1.4 Provar em teste que `startKoin { modules(appModules) }` seguido da resolução de `McpToolDependencies`, `McpServerSettings` e `AgentActivityJournal` não carrega classe alguma de Compose nem inicializa Firebase (D5).
- [x] 1.5 `McpServerSettings` chama `Preferences.userRoot().flush()` depois de cada escrita e `sync()` antes de cada leitura (D7). Teste em dois processos JVM: um grava a escolha, o outro a lê na hora.
- [x] 1.6 **Não previsto pelo design.** `RemoteSourceIsNeverReadTest` (`:app:shared`) prendia o cliente Ktor a `feature/settings/impl` como módulo único, e a ponte de D8 exige um cliente dentro de `feature/mcp/impl` — sem a mudança, 1.3 não compila. A guarda passou a separar os dois sentidos de "cliente": o que sai da máquina, que só a fonte de câmbio pode ter, e o que disca o próprio loopback. A metade que carrega a garantia — só `feature/mcp/impl` declara um **servidor** — ficou intacta.

> **O que 1.6 custou, e quem paga.** A guarda antes tornava impossível um cliente Ktor fora da
> fonte de câmbio; agora `feature/mcp/impl` pode declarar um, e nada no grafo de módulos distingue
> um cliente de loopback de um que sai da máquina. A garantia perdida é reposta em 5.5, quando o
> cliente existir em produção e houver o que apontar.

> **Duas afirmações do design que a implementação corrigiu**, registradas aqui porque as tarefas
> seguintes se apoiavam nelas: (a) o artefato cliente não traz só "os mesmos" que o servidor já
> trazia — entram `kotlin-sdk-client`, `ktor-client-core` e `kotlinx-coroutines-slf4j`, nas versões
> já pinadas, e nenhuma versão sobe; (b) o atraso de até 60 s do `java.util.prefs` no macOS é sobre
> gravar o plist, não sobre a visibilidade entre processos, que ali é imediata — o `flush()` de D7
> é exigido pelo armazenamento em arquivo do JDK, que é o do Linux.

## 2. Posse do banco (`mcp-stdio-mode`: "Há no máximo um dono do banco por vez")

- [x] 2.1 `DatabaseOwnership` em `:core:database` (`jvmMain`): lock exclusivo por `FileChannel.tryLock` num arquivo ao lado do banco, com `acquire(timeout)`, `tryAcquire()` e liberação; o caminho do arquivo derivado de `defaultDatabasePath()`.
- [x] 2.2 A janela toma a posse em `main()` antes de montar o grafo Koin, com a espera limitada de D10, e a segura até `exitApplication()`.
- [x] 2.3 Testes: toma e recusa entre dois processos (subprocesso JVM), solta ao fechar, a espera termina quando o outro solta, e a espera respeita o limite.

## 3. Montador comum do servidor (design D8)

- [x] 3.1 Extrair de `DesktopMcpServerController` a montagem do `Server` por sessão — `newServer()`, `register(tool)`, `grantedToolList()`, o `instructionsProvider` — para `McpSessionFactory` (`jvmMain`), recebendo journal, settings e a lista de ferramentas.
- [x] 3.2 `DesktopMcpServerController` passa a consumir o montador; comportamento do HTTP inalterado, provado pela suíte existente de `feature/mcp/impl` sem alteração de um teste sequer.
- [x] 3.3 `McpSurfaceIsClosedTest` e os testes de protocolo passam a montar pelo mesmo montador, para que valham para os dois transportes. Consequência de 3.2, sem edição de teste: os testes de protocolo chegam ao montador pelo `DesktopMcpServerController` que o `McpServerHarness` constrói, e `McpSurfaceIsClosedTest` não monta servidor algum — compara o registro (`AgentWorld().tools()`) com `McpSurface.offered`.

## 4. Sessão stdio headless (`mcp-stdio-mode`)

- [ ] 4.1 Declarar em `feature/mcp/api` o contrato da sessão stdio (`McpStdioSession`, com `suspend fun serve(input, output)` em tipos de `:core:*`), no padrão de `McpServerController`; `actual` no-op nos demais targets.
- [ ] 4.2 Implementar em `jvmMain`: `StdioServerTransport` do SDK sobre um `Server` montado por `McpSessionFactory`, um por processo, encerrando quando o stdin fecha.
- [ ] 4.3 Servidor desabilitado (D7): o processo fala o protocolo, `tools/list` vazio, toda chamada recusada nomeando o interruptor da seção. Instalação sem escolha idem. Permissões lidas de `McpServerSettings`, que já faz `sync()` antes de ler (1.5).
- [ ] 4.4 Higiene de `stdout` (D6): guardar o stream original para o transporte e trocar `System.out` por `System.err` antes de qualquer outra coisa; linha de abertura em `stderr` com versão, modo e estado do servidor.
- [ ] 4.5 Execução local sob a posse: cada `tools/call` toma `DatabaseOwnership`, executa pelo journal e solta; sem a posse, não executa (a decisão de encaminhar entra no grupo 5). Decidir aqui como o processo stdio a obtém — construída onde é usada ou ligada no Koin — porque o grupo 2 deliberadamente não a registrou.

> **O que `DatabaseOwnership` faz e que a tarefa acima assume** (verificado no grupo 2, não está no
> design): `tryAcquire()` e `acquire()` são **bloqueantes, não `suspend`** — chame-as em
> `Dispatchers.IO`. A reivindicação é do processo, não do chamador: duas chamadas concorrentes no
> mesmo processo stdio recebem ambas a posse e executam ambas localmente, que é o certo — sucesso
> significa "não há janela". O lock volta ao kernel quando o último detentor solta.
- [ ] 4.6 Testes de protocolo por pipes em processo com `StdioClientTransport`: `initialize` e `tools/list` sem janela; consulta e escrita com a janela fechada deixando registro; desabilitado recusa; `println` durante a sessão não corrompe o protocolo; dois processos escrevendo ao mesmo tempo aplicam as duas escritas.
- [ ] 4.7 Teste de que a migração pendente roda pelo mesmo caminho da janela, com a cópia pré-migração, quando o primeiro a abrir o banco é o modo stdio.

## 5. Ponte para a janela aberta (`mcp-stdio-mode`: "Com a janela aberta, o modo stdio encaminha")

- [ ] 5.1 `McpBridge` (`jvmMain`): `Client` do SDK contra `http://127.0.0.1:<porta>/mcp` com o token persistido; encaminha `tools/list` e `tools/call` como chegam e reemite `notifications/tools/list_changed` ao cliente stdio. Declarar em `jvmMain` o motor que o `HttpClient` do SDK exige — hoje `ktor-client-okhttp` está só em `jvmTest`, e o que chega ao runtime da distribuição vem do `jvmMain` de `feature/settings/impl`, o que é dependência por acidente e não por declaração.
- [ ] 5.2 Decisão por chamada (D3/D4/D7): posse livre → local; posse tomada → ponte, e a posse tem precedência sobre o que foi lido do disco. Com a porta fechada, a preferência decide: desabilitado → recusa "desligado no app" na hora; habilitado → espera limitada de D10 e a resposta "o app está iniciando, repita" quando o limite vence.
- [ ] 5.3 Janela fecha no meio da sessão: a chamada em voo volta como erro, a seguinte é local; janela abre no meio: a seguinte é encaminhada. A sessão stdio é a mesma nos dois casos.
- [ ] 5.4 Testes: escrita encaminhada acorda um `Flow` já coletado (o par de `AgentWritesReachOpenScreensTest`, agora pelo stdio); abrir e fechar a janela no meio da sessão; a lista é idêntica nos dois modos para as mesmas permissões; a notificação de permissão atravessa a ponte. E o cenário que a spec pede em separado: **servidor desabilitado com a janela aberta** recusa na hora, sem esperar pela janela.
- [ ] 5.5 Repor a garantia que 1.6 abriu mão: uma asserção de que o cliente que este módulo declara só endereça a interface de loopback — o endereço que a ponte disca vem da porta persistida e de `127.0.0.1`, e nenhum host aparece no módulo. É o mesmo gênero de guarda que `RegistrationToolsGoThroughUseCasesTest` e `AgentSurfaceCarriesNoDomainTest` já fazem por varredura, e sem ela "o cliente do mcp não sai da máquina" é disciplina, não fato.

## 6. Ponto de entrada do desktop (design D1, D9)

- [ ] 6.1 `main(args)` despacha: `--mcp` → `McpMain` (sem `application {}`, sem `DesktopFirebase.initialize()`, sem `Window`); qualquer outro caso → a janela como hoje.
- [ ] 6.2 `McpMain`: higiene de `stdout`, `startKoin(appModules)`, resolve `McpStdioSession` e serve até o stdin fechar; encerra o Koin e o banco ao sair.
- [ ] 6.3 `McpServerController` expõe `launchCommand` (caminho do executável + `--mcp`), lido de `jpackage.app-path` com fallback em `ProcessHandle.current().info().command()`; no-op nos demais targets devolve nulo.
- [ ] 6.4 Teste do despacho (argumentos vazios, `--mcp`, argumento desconhecido) e do comando (propriedade presente, ausente).

## 7. Seção de configurações (`mcp-server`: "A configuração ensina a conectar")

- [ ] 7.1 `McpUiState` ganha o bloco `command` + `args` e a linha `claude mcp add`, ambos copiáveis; o bloco HTTP com endereço e token passa para um caminho avançado recolhido, mantendo a máscara do token.
- [ ] 7.2 A seção diz que o comando funciona com o app aberto ou fechado; a frase "só responde com o app aberto" sai. Strings novas e alteradas em `values/strings.xml` e `values-en/strings.xml` no mesmo passo. São **duas** as frases que a mudança torna falsas, e as tarefas só nomeavam a primeira: `mcp_app_open_note` ("o servidor só existe com o app aberto") e `mcp_instructions_stdio_note` ("clientes que só falam stdio precisam de um adaptador de terceiros"), que é justamente o que deixa de ser preciso. `StringTranslationParityTest` (`:app:shared`) segura o par pt/en.
- [ ] 7.3 `McpViewModelTest` cobre o comando, o recolhimento do caminho avançado e a ausência das duas frases antigas. Não existe `McpUiStateTest`: o estado é exercitado dentro daquele arquivo, pelo helper `subscribe()` — ou os testes novos entram ali, ou o arquivo próprio nasce nesta tarefa, e a escolha é declarada no relatório.

## 8. Distribuição e verificação (design D11)

- [ ] 8.1 `McpServerReachesTheDistributionTest` passa a exigir também `io.modelcontextprotocol:kotlin-sdk-client` na distribuição — e o motor do cliente que 5.1 declarar, porque um cliente empacotado sem motor sobre o qual falar é uma ponte que só falha na máquina do usuário.
- [ ] 8.2 Tarefa Gradle `verifyMcpLauncher` em `:app:desktop`, dependente de `createDistributable`: lança o launcher empacotado com `--mcp`, completa `initialize` → `tools/list` pelo stdio, confere que `stderr` recebeu a linha de abertura e que `jpackage.app-path` está definido. Fora de `jvmTest`, executada à mão como a suíte Maestro.
- [ ] 8.3 Executar `verifyMcpLauncher` neste macOS e registrar no relatório da mudança em que SO rodou, o tempo até `initialize` e a memória do processo.
- [ ] 8.4 `./gradlew jvmTest` verde, com a contagem de testes conferida contra os **2973 casos** da linha de base do cabeçalho: o total final é ela mais os testes que cada grupo acrescentou, e uma diferença que não se explique assim é um teste que sumiu.

## 9. Documentação e registro

- [ ] 9.1 `ROADMAP.md`: linha do ciclo aberto para o modo stdio; `RELEASE-NOTES.md`: a nota do usuário — o comando, funciona fechado, o Claude Desktop conecta sem adaptador.
- [ ] 9.2 `feature/README.md` e o KDoc de `McpServerController`: a superfície tem dois modos e uma regra de posse; sem narrar a mudança.
- [ ] 9.3 Arquivar a nota de D1 da mudança anterior nas specs pelo `/opsx:sync` ao final, para que `openspec/specs/mcp-server` reflita "um artefato, dois modos".
