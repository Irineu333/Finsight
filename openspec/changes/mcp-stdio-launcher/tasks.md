> Ordem deliberada: o spike de dependência vem primeiro porque é o único ponto que pode mudar a
> forma da ponte (design D8, Riscos). A posse do banco e o montador comum (grupos 2 e 3) não
> alteram comportamento algum e podem ser mesclados sozinhos. O modo stdio (grupo 4) já entrega
> "funciona com o app fechado" sem a ponte; a ponte (grupo 5) entrega "funciona com o app aberto".
> A seção e a distribuição fecham a mudança.

## 1. Spike de dependência

- [ ] 1.1 Adicionar `io.modelcontextprotocol:kotlin-sdk-client:0.14.0` ao catálogo e ao `jvmMain` de `feature/mcp/impl`; confirmar que resolve com Ktor 3.4.3 e Kotlin 2.3.10 sem elevar nenhuma versão do app. Se não fechar, registrar o desvio de D8 (cliente HTTP mínimo sobre `ktor-client-core`) antes de seguir.
- [ ] 1.2 Exercitar em teste, contra o `McpServerHarness` existente, um `Client` do SDK com `StreamableHttpClientTransport` apresentando o token: `initialize` → `tools/list` → `tools/call` e a recepção de `notifications/tools/list_changed`.
- [ ] 1.3 Provar em teste que `startKoin { modules(appModules) }` seguido da resolução de `McpToolDependencies`, `McpServerSettings` e `AgentActivityJournal` não carrega classe alguma de Compose nem inicializa Firebase (D5).

## 2. Posse do banco (`mcp-stdio-mode`: "Há no máximo um dono do banco por vez")

- [ ] 2.1 `DatabaseOwnership` em `:core:database` (`jvmMain`): lock exclusivo por `FileChannel.tryLock` num arquivo ao lado do banco, com `acquire(timeout)`, `tryAcquire()` e liberação; o caminho do arquivo derivado de `defaultDatabasePath()`.
- [ ] 2.2 A janela toma a posse em `main()` antes de montar o grafo Koin, com a espera limitada de D10, e a segura até `exitApplication()`.
- [ ] 2.3 Testes: toma e recusa entre dois processos (subprocesso JVM), solta ao fechar, a espera termina quando o outro solta, e a espera respeita o limite.

## 3. Montador comum do servidor (design D8)

- [ ] 3.1 Extrair de `DesktopMcpServerController` a montagem do `Server` por sessão — `newServer()`, `register(tool)`, `grantedToolList()`, o `instructionsProvider` — para `McpSessionFactory` (`jvmMain`), recebendo journal, settings e a lista de ferramentas.
- [ ] 3.2 `DesktopMcpServerController` passa a consumir o montador; comportamento do HTTP inalterado, provado pela suíte existente de `feature/mcp/impl` sem alteração de um teste sequer.
- [ ] 3.3 `McpSurfaceIsClosedTest` e os testes de protocolo passam a montar pelo mesmo montador, para que valham para os dois transportes.

## 4. Sessão stdio headless (`mcp-stdio-mode`)

- [ ] 4.1 Declarar em `feature/mcp/api` o contrato da sessão stdio (`McpStdioSession`, com `suspend fun serve(input, output)` em tipos de `:core:*`), no padrão de `McpServerController`; `actual` no-op nos demais targets.
- [ ] 4.2 Implementar em `jvmMain`: `StdioServerTransport` do SDK sobre um `Server` montado por `McpSessionFactory`, um por processo, encerrando quando o stdin fecha.
- [ ] 4.3 Servidor desabilitado (D7): o processo fala o protocolo, `tools/list` vazio, toda chamada recusada nomeando o interruptor da seção. Instalação sem escolha idem. Permissões lidas de `McpServerSettings` com `sync()` antes.
- [ ] 4.4 Higiene de `stdout` (D6): guardar o stream original para o transporte e trocar `System.out` por `System.err` antes de qualquer outra coisa; linha de abertura em `stderr` com versão, modo e estado do servidor.
- [ ] 4.5 Execução local sob a posse: cada `tools/call` toma `DatabaseOwnership`, executa pelo journal e solta; sem a posse, não executa (a decisão de encaminhar entra no grupo 5).
- [ ] 4.6 Testes de protocolo por pipes em processo com `StdioClientTransport`: `initialize` e `tools/list` sem janela; consulta e escrita com a janela fechada deixando registro; desabilitado recusa; `println` durante a sessão não corrompe o protocolo; dois processos escrevendo ao mesmo tempo aplicam as duas escritas.
- [ ] 4.7 Teste de que a migração pendente roda pelo mesmo caminho da janela, com a cópia pré-migração, quando o primeiro a abrir o banco é o modo stdio.

## 5. Ponte para a janela aberta (`mcp-stdio-mode`: "Com a janela aberta, o modo stdio encaminha")

- [ ] 5.1 `McpBridge` (`jvmMain`): `Client` do SDK contra `http://127.0.0.1:<porta>/mcp` com o token persistido; encaminha `tools/list` e `tools/call` como chegam e reemite `notifications/tools/list_changed` ao cliente stdio.
- [ ] 5.2 Decisão por chamada (D3/D4): posse livre → local; posse tomada → ponte, com a espera limitada de D10 enquanto a janela ainda não aceita conexões, e a resposta "o app está iniciando, repita" quando o limite vence.
- [ ] 5.3 Janela fecha no meio da sessão: a chamada em voo volta como erro, a seguinte é local; janela abre no meio: a seguinte é encaminhada. A sessão stdio é a mesma nos dois casos.
- [ ] 5.4 Testes: escrita encaminhada acorda um `Flow` já coletado (o par de `AgentWritesReachOpenScreensTest`, agora pelo stdio); abrir e fechar a janela no meio da sessão; a lista é idêntica nos dois modos para as mesmas permissões; a notificação de permissão atravessa a ponte.

## 6. Ponto de entrada do desktop (design D1, D9)

- [ ] 6.1 `main(args)` despacha: `--mcp` → `McpMain` (sem `application {}`, sem `DesktopFirebase.initialize()`, sem `Window`); qualquer outro caso → a janela como hoje.
- [ ] 6.2 `McpMain`: higiene de `stdout`, `startKoin(appModules)`, resolve `McpStdioSession` e serve até o stdin fechar; encerra o Koin e o banco ao sair.
- [ ] 6.3 `McpServerController` expõe `launchCommand` (caminho do executável + `--mcp`), lido de `jpackage.app-path` com fallback em `ProcessHandle.current().info().command()`; no-op nos demais targets devolve nulo.
- [ ] 6.4 Teste do despacho (argumentos vazios, `--mcp`, argumento desconhecido) e do comando (propriedade presente, ausente).

## 7. Seção de configurações (`mcp-server`: "A configuração ensina a conectar")

- [ ] 7.1 `McpUiState` ganha o bloco `command` + `args` e a linha `claude mcp add`, ambos copiáveis; o bloco HTTP com endereço e token passa para um caminho avançado recolhido, mantendo a máscara do token.
- [ ] 7.2 A seção diz que o comando funciona com o app aberto ou fechado; a frase "só responde com o app aberto" sai. Strings novas e alteradas em `values/strings.xml` e `values-en/strings.xml` no mesmo passo.
- [ ] 7.3 `McpViewModelTest` e o teste de `McpUiState` cobrem o comando, o recolhimento do caminho avançado e a ausência da frase antiga.

## 8. Distribuição e verificação (design D11)

- [ ] 8.1 `McpServerReachesTheDistributionTest` passa a exigir também `io.modelcontextprotocol:kotlin-sdk-client` na distribuição.
- [ ] 8.2 Tarefa Gradle `verifyMcpLauncher` em `:app:desktop`, dependente de `createDistributable`: lança o launcher empacotado com `--mcp`, completa `initialize` → `tools/list` pelo stdio, confere que `stderr` recebeu a linha de abertura e que `jpackage.app-path` está definido. Fora de `jvmTest`, executada à mão como a suíte Maestro.
- [ ] 8.3 Executar `verifyMcpLauncher` neste macOS e registrar no relatório da mudança em que SO rodou, o tempo até `initialize` e a memória do processo.
- [ ] 8.4 `./gradlew jvmTest` verde, com a contagem de testes conferida contra a linha de base medida antes do grupo 1.

## 9. Documentação e registro

- [ ] 9.1 `ROADMAP.md`: linha do ciclo aberto para o modo stdio; `RELEASE-NOTES.md`: a nota do usuário — o comando, funciona fechado, o Claude Desktop conecta sem adaptador.
- [ ] 9.2 `feature/README.md` e o KDoc de `McpServerController`: a superfície tem dois modos e uma regra de posse; sem narrar a mudança.
- [ ] 9.3 Arquivar a nota de D1 da mudança anterior nas specs pelo `/opsx:sync` ao final, para que `openspec/specs/mcp-server` reflita "um artefato, dois modos".
