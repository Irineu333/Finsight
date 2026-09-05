---
area: mcp
severity: low
type: ux
---

# O endereço que o servidor vincula é escrito uma segunda vez pela tela

## Invariante

O endereço que a seção manda um cliente usar é o endereço que o servidor vincula — um valor, um
dono.

Hoje é falso: `LOOPBACK_HOST` e `MCP_PATH` existem duas vezes, com os mesmos literais e sem
relação alguma entre as cópias. Uma governa o socket, a outra governa o texto que o usuário
copia, e nada quebra se as duas discordarem.

## Mecânica

O servidor vincula por `LOOPBACK_HOST` e roteia por `MCP_PATH` no `jvmMain`; a seção monta o
endereço exibido e o bloco JSON do caminho avançado a partir de constantes homônimas no
`commonMain`, porque `McpUiState` é `commonMain` e não enxerga `jvmMain`.

A duplicação é imposta pela estrutura — a tela é multiplataforma e o socket só existe no JVM —,
mas nada a torna consistente: mover a rota para `/mcp/v1`, ou vincular a `::1`, deixa a seção
ensinando o endereço antigo e o teste de nenhum dos dois lados falha, porque cada um afirma
contra a sua própria cópia.

O caso simétrico foi resolvido nesta mesma superfície e mostra que há saída: o argumento `--mcp`
também é lido por dois módulos que não se enxergam, e mora em `McpLaunchCommand.STDIO_ARGUMENT`,
em `feature/mcp/api` — que os dois enxergam.

## Evidência

- `feature/mcp/impl/src/jvmMain/.../DesktopMcpServerController.kt` — `LOOPBACK_HOST` e
  `MCP_PATH` como constantes `internal` de topo, ao lado do que as usa para vincular e rotear
- `feature/mcp/impl/src/commonMain/.../ui/screen/mcp/McpUiState.kt` — `LOOPBACK_HOST` e
  `MCP_PATH` no companion, usados por `address` e pelo bloco do caminho avançado
- `feature/mcp/api/.../McpLaunchCommand.kt` — `STDIO_ARGUMENT`, o precedente de um valor que
  dois módulos precisam e que mora onde ambos alcançam
- `TheClientOfThisModuleNeverLeavesTheMachineTest` — afirma que todo endereço do módulo é
  loopback, e por isso passa com as duas cópias: ambas são

## Consequência

Nada quebra hoje: as quatro constantes têm os valores certos. O que se perde é a garantia — uma
mudança no socket não alcança a tela, e o defeito só aparece do lado do usuário, que configura
um cliente contra um endereço que ninguém escuta e não tem como saber que o app lhe deu o
endereço errado.

## Sugestão

Levar as duas para `feature/mcp/api`, junto de `STDIO_ARGUMENT`, e fazer as duas pontas lerem de
lá. Não vinculante.
