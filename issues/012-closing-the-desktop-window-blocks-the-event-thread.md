# 012 — fechar a janela do desktop bloqueia a thread de eventos do AWT no ciclo de vida do servidor

**Área:** app/desktop · **Tipo:** UX · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`onCloseRequest` executa `runBlocking { mcpServer.stop() }` na thread de eventos do AWT, e `stop()`
toma o mesmo `Mutex` que `start()` segura enquanto faz o bind do socket. Entre os dois, a janela pode
ficar congelada e sem repintura por todo o tempo que o bind e o encerramento levarem.

## Evidência

`app/desktop/src/main/kotlin/com/neoutils/finsight/main.kt:52-63`

```kotlin
LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) { mcpServer.start() }
}

Window(
    onCloseRequest = {
        runBlocking { mcpServer.stop() }
        exitApplication()
    },
```

`feature/mcp/impl/.../DesktopMcpServerController.kt:82,126-133`

```kotlin
private val lifecycle = Mutex()

override suspend fun start(): Unit = lifecycle.withLock { … bringUp() }
override suspend fun stop(): Unit  = lifecycle.withLock { takeDown() }
```

`bringUp` segura o lock ao longo de `server.startSuspend(wait = false)` (`:199-202`), que suspende até
o socket estar vinculado. `takeDown` então espera
`STOP_GRACE_MILLIS + STOP_TIMEOUT_MILLIS` = 250 + 2 000 ms (`:223-235`, `:403-405`).

O trade-off está reconhecido no código (`main.kt:48-49`: *"the close below waits on the same
lifecycle from the UI thread"*), e é por isso que isto é registrado como baixo e não como defeito: a
espera é deliberada, a porta realmente precisa ser liberada antes de o processo morrer. O que não é
deliberado é fazer essa espera na thread que pinta.

## Cenário de falha

O usuário fecha a janela no primeiro segundo após o lançamento, com o bind ainda em curso. A EDT
bloqueia dentro do `runBlocking` até o bind terminar, e depois por até mais 2,25 s. A janela não
repinta nem desaparece; o sistema pode marcar o app como "não está respondendo". Um bind travado
estende isso indefinidamente.

## Correção sugerida

Manter a espera, tirá-la da EDT — esconder a janela primeiro, bloquear depois:

```kotlin
onCloseRequest = {
    window.isVisible = false            // ou um estado de "encerrando"
    runBlocking { withTimeout(SHUTDOWN_MILLIS) { runCatching { mcpServer.stop() } } }
    exitApplication()
}
```

Um `withTimeout` limitado também limita o pior caso, que é o que a saída precisa: a porta é liberada
pela morte do processo, se o encerramento gracioso não chegar lá antes.
