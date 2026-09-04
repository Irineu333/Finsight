---
area: mcp
severity: low
type: ux
---

# Fora de uma instalação, a seção oferece um comando de lançamento que não pode funcionar

## Cenário

**DADO** o app rodando por `./gradlew :app:desktop:run`, sem launcher empacotado
**QUANDO** o usuário abre a seção do servidor MCP com o servidor habilitado
**ENTÃO** o bloco `command` + `args` mostra o binário `java` da própria JVM com `--mcp` — algo
como `{"command": "/Library/Java/…/bin/java", "args": ["--mcp"]}` —, copiável e indistinguível
do bloco correto; um cliente configurado com ele sobe uma JVM sem classpath e sem classe
principal, e a sessão morre sem chegar ao `initialize`
**DEVERIA** ou não oferecer bloco algum onde não há executável a lançar, ou dizer que aquele
caminho não é o de uma instalação

## Mecânica

`McpLaunchCommand.ofThisProcess()` lê `jpackage.app-path`, que só o launcher empacotado define,
e cai para `ProcessHandle.current().info().command()` quando ela não existe. O fallback é
honesto sobre o que responde — é de fato o executável deste processo — mas o que ele responde
numa execução por Gradle é o `java` da JVM, e um `java` sozinho não relança o app.

O design D9 escolheu esse fallback deliberadamente, e numa instalação empacotada a propriedade
existe e o caminho é o certo (`verifyMcpLauncher` o mantém verdadeiro). O que ninguém decidiu é
o que a **tela** faz com a diferença: `McpUiState` desenha o bloco sempre que
`launchCommand != null`, e o fallback nunca é nulo.

O caso `null` já tem tratamento — sem comando, o caminho avançado deixa de ser recolhido —, o
que mostra que a tela sabe lidar com a ausência. É a presença enganosa que não tem tratamento.

## Evidência

- `feature/mcp/api/src/jvmMain/.../McpLaunchCommand.jvm.kt` — `ofThisProcess()` e o fallback em
  `ProcessHandle.current().info().command()`
- `feature/mcp/impl/src/commonMain/.../ui/screen/mcp/McpUiState.kt` — `launch` é derivado da
  simples presença de `launchCommand`
- `app/desktop/build/reports/mcp/verify-mcp-launcher.txt` — no binário empacotado
  `jpackage.app-path` aponta para `Finsight.app/Contents/MacOS/Finsight`, que é o caminho útil

## Consequência

Só alcança quem roda o app fora de uma instalação — desenvolvedor, ou quem for demonstrar a
seção a partir do código. Não atinge o usuário do app instalado, e é por isso que fica em `low`.
O custo é o tempo de quem copia um bloco que parece pronto, configura um cliente e depura uma
sessão que morre por um motivo que a tela não deu.

## Sugestão

Deixar `ofThisProcess()` responder `null` quando `jpackage.app-path` estiver ausente, e a tela
tratar isso como já trata a ausência nas outras plataformas. Perde-se o caminho ilustrativo em
desenvolvimento e ganha-se a garantia de que todo bloco exibido é um bloco que funciona. Não
vinculante — a alternativa é rotular o bloco, que preserva a ilustração ao custo de mais texto
numa seção que a mudança acabou de enxugar.
