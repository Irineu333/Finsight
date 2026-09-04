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

- [x] 4.1 `McpStdioSession` em `feature/mcp/api` (`commonMain`), no padrão de `McpServerController`, com `UnavailableMcpStdioSession` ligado nos módulos Koin de Android e iOS. **A assinatura é `serve()`, sem fluxos.** `feature/mcp/api` é `commonMain` puro e depende só de `:core:common`, `:core:navigation` e `:core:resources`; `InputStream`/`OutputStream` são da JVM e os fluxos do SDK são de `kotlinx-io`, que seria a primeira dependência externa desse `api`. Um processo tem um par de fluxos padrão e portanto uma sessão, então o parâmetro teria uma resposta certa só — e quem escolhe qual fluxo carrega o protocolo é o ponto de entrada, antes de o grafo existir (4.4). A sobrecarga `serve(input, output)` existe `internal` no `jvmMain`, e é por ela que os testes de protocolo falam.
- [x] 4.2 `DesktopMcpStdioSession` (`jvmMain`): `StdioServerTransport` sobre o `Server` de `McpSessionFactory`, um por processo, terminando quando o transporte reporta o fim do stdin.
- [x] 4.3 `McpServerOff` (`jvmMain`): com o interruptor desligado o processo fala o protocolo, `tools/list` volta vazio e **qualquer** `tools/call` é recusado nomeando a seção — inclusive um nome que não existe, porque a recusa vem antes da busca no registro e o SDK responderia *"tool not found"*, a afirmação falsa que `McpPermissionNotice` existe para evitar. São as duas frases — instruções e recusa — que o ponto de decisão devolve quando o interruptor está desligado, e não um servidor alternativo: o interruptor é lido a cada requisição, como a posse (ver a nota ao fim do grupo 5). O vocabulário é o mesmo: `McpPermissionNotice` ganhou `WHAT_THIS_IS` e `THE_SECTION`, e as frases antigas são montadas a partir deles sem mudar um byte.
- [x] 4.4 `McpStdout` (`feature/mcp/api`, `jvmMain`): `claim()` guarda o `stdout` original e põe `System.out` em `System.err`; `protocol` devolve o guardado e **falha alto** se ninguém reivindicou, que é o que torna a ordem obrigatória em vez de recomendada. Mora no `api` porque é o único módulo que o ponto de entrada (`:app:desktop`, grupo 6) e o `impl` enxergam ao mesmo tempo. A linha de abertura sai em `stderr` com versão (`jpackage.app-version`, ou `development build`), modo e estado do interruptor. **Não** diz se encontrou a janela aberta, que o design D6 menciona: por D3 a posse é decidida a cada chamada, então no arranque isso não é um fato — seria falso um milissegundo depois. Os três itens que a spec exige estão lá.
- [x] 4.5 O ponto de decisão é `McpCallSite` — *onde uma chamada é executada* —, um parâmetro novo de `McpSessionFactory` cujo padrão (`McpCallSite.Here`) é o comportamento de hoje, e portanto do HTTP. `McpStdioCallSite` toma a posse em `Dispatchers.IO` (`tryAcquire()` é bloqueante), executa e solta; sem a posse delega a `elsewhere`, hoje `NotWhileAnotherProcessOwnsTheDatabase`, que recusa sem tocar no banco — **é esse colaborador que o grupo 5 troca pela ponte**, sem reescrever a peça. A `DatabaseOwnership` é construída no `single<McpStdioSession>` de `McpModule.jvm.kt`, onde é usada, e não registrada no Koin: o único chamador que a pede por chamada é este.

> **O que `DatabaseOwnership` faz e que a tarefa acima assume** (verificado no grupo 2, não está no
> design): `tryAcquire()` e `acquire()` são **bloqueantes, não `suspend`** — chame-as em
> `Dispatchers.IO`. A reivindicação é do processo, não do chamador: duas chamadas concorrentes no
> mesmo processo stdio recebem ambas a posse e executam ambas localmente, que é o certo — sucesso
> significa "não há janela". O lock volta ao kernel quando o último detentor solta.
- [x] 4.6 `McpStdioOverTheProtocolTest` (6 casos) sobre `java.nio.channels.Pipe` reais, com o `Client` e o `StdioClientTransport` do SDK: handshake e `tools/list` sem janela; consulta e escrita com a janela fechada, com a linha no registro de atividade; desabilitado não anuncia e recusa; a linha de abertura em `stderr`; e o teste do `println`, que é o único que serve pela chamada de produção (`serve()` sem argumentos) com `System.in`/`System.out` apontados para os mesmos pipes, como o SO faz. **O caso das duas escritas simultâneas é de duas sessões no mesmo processo**, que compartilham a única reivindicação do JVM — a exclusão *entre* processos é assunto de `DatabaseOwnershipTest`, no módulo que possui o lock. `TheStdioModeExecutesOnlyUnderTheOwnershipTest` cobre a outra metade com um processo de verdade (`ArchiveHolder`): sem a posse não executa nada, e a chamada seguinte da **mesma** sessão executa assim que o outro processo solta.
- [x] 4.7 `TheStdioModeOpensTheArchiveTheWindowWouldTest`: arquivo escrito por este build e deixado declarando a versão anterior — o que um app atualizado encontra —, aberto pela primeira vez dentro do `tools/call`, por `getDatabaseBuilder(path, captureInto)`, que é a função que `databasePlatformModule` chama. A cópia sai com a versão antiga e as linhas do usuário, e o arquivo termina na versão atual. Mora em `feature/mcp/impl/jvmTest` porque `appModules` vive em `:app:shared`, fora do alcance de um `impl`; o que o grafo garante (mesmo builder nos dois modos) é D5, e o que o teste acrescenta é que a abertura é **preguiçosa** — nada da sessão toca o arquivo antes de uma chamada.

## 5. Ponte para a janela aberta (`mcp-stdio-mode`: "Com a janela aberta, o modo stdio encaminha")

- [x] 5.1 `McpBridge` (`jvmMain`): `Client` do SDK contra `http://127.0.0.1:<porta>/mcp` com o token persistido; encaminha `tools/list` e `tools/call` como chegam e reemite `notifications/tools/list_changed` ao cliente stdio. `ktor-client-okhttp` passou de `jvmTest` para `jvmMain`. **A conexão dura o quanto a janela durar**, não o quanto uma chamada dura: um cliente que discasse por chamada nunca estaria ouvindo no instante em que o usuário move um eixo, e a notificação não tem repetição. É aberta na primeira requisição que precisa dela, guardada, e esquecida no `onClose` do próprio transporte — sem isso, uma janela fechada e reaberta seria atendida no transporte morto da primeira. O endereço e o token são lidos **no instante**, não lembrados do arranque.
- [x] 5.2 **As duas perguntas que não se decidem uma vez são feitas por requisição, no mesmo lugar**: o interruptor e a posse, ambos em `McpStdioCallSite`, ambos lidos do disco por `McpServerSettings.currentChoice()` (interruptor, porta e token sob um `sync()` só; os `StateFlow`s do grupo 1 ficaram intactos). O interruptor é perguntado **primeiro** — a autoridade do app não é o que sobra quando ninguém respondeu, e é o que fecha o intervalo em que a janela já gravou "desligado" e ainda não derrubou o socket. Depois a posse: livre → local; tomada → ponte; porta fechada → espera de 5 s e "o app está iniciando, repita". **As três coisas que uma sessão responde passam pelo ponto de decisão**: `McpCallSite` ganhou `list` e `instructions` além de `answer`, e `answer` passou a receber o **nome** em vez de um `McpTool` resolvido, porque um nome pode ter de ser encaminhado ou recusado sem que este processo o conheça.
- [x] 5.3 Janela fecha no meio da sessão: a chamada em voo volta como erro dizendo que não se sabe se foi aplicada, a seguinte é local; janela abre no meio: a seguinte é encaminhada. A sessão stdio é a mesma nos dois casos — o cliente não é desconectado, não refaz handshake e não é avisado.
- [x] 5.4 Testes (+14 casos). `AgentWritesReachOpenScreensTest` ganhou o par pelo stdio; `McpStdioOverTheProtocolTest` ganhou os dois do interruptor movido no meio da sessão (nos dois sentidos), que falhavam contra o código antes de 5.2; e `TheStdioModeForwardsToTheOpenWindowTest` (7) cobre abrir e fechar a janela no meio, a lista vinda da janela, a lista idêntica nos dois modos, a notificação atravessando, o servidor desabilitado recusando na hora e a janela subindo. **A janela aberta é encenada de verdade**: a posse do arquivo é de outro processo (`ArchiveHolder`) — dentro de um JVM o lock é um só e o teste devolveria a posse à sessão sob teste — e o socket é o `DesktopMcpServerController` deste processo, que é o que deixa um `Flow` aberto acordar. **Quem respondeu é lido pelo par nome-igual/corpo-diferente**: os dois lados oferecem o mesmo nome (são o mesmo build) e a resposta diz qual processo executou; a descrição diz qual anunciou. Verificado por sabotagem que os testes de lista e de notificação falham sem o encaminhamento.
- [x] 5.5 `TheClientOfThisModuleNeverLeavesTheMachineTest` (5 casos), varredura de fonte no gênero de `RegistrationToolsGoThroughUseCasesTest`, com o piso e o controle negativo que ele usa. Três afirmações fecham a questão: **um só arquivo constrói um `HttpClient`**, então há um endereço a conferir; **todo endereço escrito nos fontes de produção do módulo é loopback**, literal ou pela constante; e **a constante é `127.0.0.1`**, para que a indireção não seja onde o host entra. `LOOPBACK_HOST`/`MCP_PATH` saíram do companion privado de `DesktopMcpServerController` para constantes `internal` do mesmo arquivo — quem disca lê do que vincula, e não uma terceira cópia. Provado por sabotagem que cada metade falha: um `https://telemetry.example.com` na ponte e um segundo `HttpClient(` em `McpServerOff`.

> **Por que 4.3 mudou de forma no meio do grupo 5** — e a linha 4.3 acima já descreve o resultado.
> O `Server` alternativo escolhido no arranque era o interruptor lido uma vez, e isso violava o
> requisito "A autoridade do app vale no modo stdio" nos dois sentidos: um processo que subiu
> **ligado** continuava executando depois de o usuário desligar o servidor (com a janela fechada,
> a posse volta a ser dele e `here()` roda), e um que subiu **desligado** recusava para sempre.
> `McpServerOff` deixou de montar um `Server` e passou a ser as duas frases — instruções e recusa —
> que o ponto de decisão devolve, o que também apagou os dois handlers duplicados que ele instalava.
> `McpSessionFactory` passou a despachar `tools/call` ela mesma, em vez de `addTool`, porque o
> ponto de decisão tem de ser consultado para **todo** nome e o registro do SDK responde sozinho
> pelos que não tem. Nada mais do despacho do SDK foi copiado: a exceção de uma ferramenta já é
> assunto do `AgentActivityJournal`, e a única frase reproduzida é `"Tool <nome> not found"`, que
> `McpPermissionsOverTheProtocolTest` exige com o servidor **ligado** — com ele desligado nenhum
> nome chega lá, porque a recusa vem antes da busca.


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
- [ ] 9.2 `feature/README.md` e o KDoc de `McpServerController`: a superfície tem dois modos e uma regra de posse; sem narrar a mudança. E registrar a exceção que 4.4 abriu: "Notas de plataforma" (`feature/README.md:271-274`) diz que source set de plataforma é exceção justificada **no `impl`**, e `McpStdout` é o primeiro num `api` — porque o ponto de entrada e a implementação estão em lados opostos do app e só se encontram ali, e um `:core:*` não pode nomear uma feature. A exceção fica escrita com a razão, ou a regra passa a ser desmentida em silêncio pelo código.
- [ ] 9.3 Arquivar a nota de D1 da mudança anterior nas specs pelo `/opsx:sync` ao final, para que `openspec/specs/mcp-server` reflita "um artefato, dois modos".
