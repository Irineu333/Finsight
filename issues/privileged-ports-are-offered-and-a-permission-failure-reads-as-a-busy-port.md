---
area: mcp
severity: low
type: ux
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
