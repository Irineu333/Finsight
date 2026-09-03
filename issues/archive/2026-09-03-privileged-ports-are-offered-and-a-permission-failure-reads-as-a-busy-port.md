---
area: mcp
severity: low
type: ux
verdict: fixed
---

# Portas privilegiadas são oferecidas, e a recusa por permissão é relatada como "porta em uso"

## Cenário

**DADO** o servidor MCP desligado e o modal de porta aberto num desktop sem privilégios
**QUANDO** o usuário digita `80` e aplica
**ENTÃO** o campo e a seção dizem que a porta está em uso por outro programa — e nenhuma porta
abaixo de 1024 vai funcionar, feche-se o que se fechar
**DEVERIA** não oferecer a faixa que o processo não consegue tomar; ou, oferecendo-a, dizer que a
recusa foi de permissão

## Mecânica

Duas decisões independentes se somam. `McpServerController.VALID_PORTS` é `1..65535` — todo o
espaço, faixa privilegiada inclusa —, e é dela que as duas superfícies que coletam o número
derivam o que aceitam. Do outro lado, `DesktopMcpServerController.classify()` responde
`PORT_IN_USE` para qualquer `BindException` na cadeia de causas, e sua KDoc justifica isso
afirmando que `BindException` é a resposta tipada da plataforma para *porta já ocupada*.

Essa premissa é falsa: a JVM também mapeia `EACCES` para `BindException`. Então o bind recusado
por privilégio chega ao usuário como um conflito com outro programa, e a única saída que esse
diagnóstico sugere — fechar quem está segurando a porta — não leva a lugar nenhum.

## Evidência

- `McpServerController.VALID_PORTS` (`feature/mcp/api`) — `IntRange = 1..65535`, e a KDoc diz que a
  faixa pertence ao contrato justamente para nenhuma superfície discordar dela
- `EditPortModal` — `port in McpServerController.VALID_PORTS`, nas duas checagens
- `McpViewModel` — `if (action.port !in McpServerController.VALID_PORTS) return`
- `DesktopMcpServerController.classify()` — `if (cause is BindException) return
  McpServerFailure.PORT_IN_USE`, e a KDoc logo acima que a sustenta
- `DesktopMcpServerController` — `_state.value = McpServerState.Failed(port = port, cause =
  failure.classify())`, o único produtor do estado que a tela lê

*O mapeamento de `EACCES` para `BindException` foi medido pela revisão que originou o registro
(macOS, sem privilégios: `java.net.BindException: Permission denied` em `ServerSocket.bind` e em
`ServerSocketChannel.bind`); não foi remedido nesta revalidação.*

## Consequência

Uma porta que o app oferece e não consegue tomar, com uma explicação sobre a qual o usuário não
tem como agir. Não engana sobre dinheiro e não impede a tarefa — qualquer porta acima de 1023
funciona — mas o caminho até essa descoberta não está em lugar nenhum da tela.

## Sugestão

Estreitar `VALID_PORTS` para `1024..65535` é a parte que importa: a faixa é o contrato, e o que
ela não oferece não precisa de diagnóstico. Depois, ou estreitar `classify()` para distinguir as
duas falhas, ou corrigir sua KDoc, que hoje enuncia uma garantia que a plataforma não dá. Não
vinculante.

## Desfecho

**Causa real** — as duas decisões independentes que o registro descrevia, confirmadas contra o
disco antes de qualquer mudança. `McpServerController.VALID_PORTS` era `IntRange = 1..65535`, e
as três checagens do app derivavam dela, nenhuma com faixa própria: `EditPortModal:68` (o erro sob
o campo), `canApplyPort` (`EditPortModal:155`, o botão) e `McpViewModel:99` (a ação). Do outro
lado, `DesktopMcpServerController.classify()` percorria a cadeia de causas e devolvia
`PORT_IN_USE` para qualquer `BindException`, sob uma KDoc que sustentava isso afirmando ser
`BindException` *a resposta tipada da plataforma para porta já ocupada* — o tipo não é dela
sozinha.

O mapeamento de `EACCES` para `BindException` **não foi remedido aqui**: a evidência é a medição
registrada na abertura do bug. O que foi verificado nesta rodada é o outro lado — que a faixa
oferecia a região privilegiada e que `classify()` responde uma coisa só para o tipo inteiro.

**Mudança** — três, nesta ordem de importância:

1. `McpServerController.VALID_PORTS` passa a `1024..65535`
   (`feature/mcp/api/.../McpServerController.kt:140`), com a KDoc dizendo por que a faixa começa
   ali (`:129-139`). As três checagens continuam derivando dela e não foram tocadas: estreitar o
   contrato estreitou as três de uma vez, que é o motivo de a faixa viver no contrato.
2. `mcp_port_error_invalid` passa a nomear a faixa nova, nos **dois** arquivos —
   `values/strings.xml:1065` (*"entre 1024 e 65535"*) e `values-en/strings.xml:1064`
   (*"between 1024 and 65535"*). Nenhuma chave nasceu nem sumiu.
3. A KDoc de `classify()` (`DesktopMcpServerController.kt:370-379`) deixa de enunciar a garantia
   falsa. Ela passa a dizer que o tipo não pertence só à porta ocupada — a JVM levanta o mesmo
   para um endereço recusado por privilégio — e que o que mantém a leitura de pé é o outro lado do
   contrato: sem porta privilegiada na faixa, o bind que uma permissão recusaria está fora de
   alcance antes de haver o que classificar. `classify()` em si não mudou; distinguir as duas
   falhas exigiria ler a mensagem da exceção, que depende de plataforma e de idioma, e a faixa já
   remove o caso sistemático.

**Prova** — `feature/mcp/api/src/commonTest/.../McpServerPortRangeTest.kt`, novo, sobre a faixa
que é o contrato: `no privileged port is offered` (o começo em 1024, e 80 e 443 fora),
`the range ends where port numbers end` e `the port the server binds unless it is moved is one the
range offers` (o `DEFAULT_PORT` dentro dela, que é o que impede estreitar a faixa por cima do
padrão).

**Vermelho antes**: com `VALID_PORTS` devolvido a `1..65535` e só isso,
`McpServerPortRangeTest > no privileged port is offered` falha em
`McpServerPortRangeTest.kt:21` — *"The range reaches below 1024, where an unprivileged process is
refused the bind."* Restaurada a faixa, verde.

As duas superfícies ganharam o caso pelo seu próprio lado: `EditPortModalTest`
(`a privileged port is never applied` — 80 e 1023 recusados, 80 recusado mesmo **depois de uma
falha**, e 1024 aceito, para a recusa não ter levado junto a primeira porta que o app consegue
tomar) e `McpViewModelTest` (`what is not a port never reaches the server` ganhou
`ChangePort(80)`, no bloco que já afirmava que nada fora da faixa chega ao servidor).

Suítes rodadas, todas verdes na última passagem:
`./gradlew :feature:mcp:api:jvmTest` — 5 testes em 2 classes;
`./gradlew :feature:mcp:impl:jvmTest` — 275 testes em 33 classes, das quais
`EditPortModalTest` 6/6 e `McpViewModelTest` 17/17;
`./gradlew :app:desktop:jvmTest` — 11 testes em 3 classes.

Registrado porque afetou a leitura das saídas: a árvore estava sendo editada por outros agentes
ao mesmo tempo, e uma passagem intermediária da suíte do `impl` acusou
`TransactionListingTest > a row the mapper cannot read is dropped, and the count is of what came
back` — de quem mexia em `ListTransactionsTool.kt`, sem relação com porta, faixa ou servidor.
Sumiu na edição seguinte deles. O mesmo motivo produziu um `compileTestKotlinJvm FAILED` e um
`UP-TO-DATE` indevido em passagens vizinhas; os números acima são da última rodada, com o
resultado lido dos XML de `build/test-results/` e não só do placar.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
