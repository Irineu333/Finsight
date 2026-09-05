---
area: app
severity: low
type: ux
---

# Fechar a janela do desktop espera o servidor MCP na thread que pinta

## Cenário

**DADO** o app de desktop recém-aberto, com o servidor MCP ligado e o bind do socket ainda em curso
**QUANDO** o usuário fecha a janela
**ENTÃO** a janela para de repintar e continua na tela até o bind terminar e o encerramento
gracioso completar — o sistema pode marcar o app como "não está respondendo"
**DEVERIA** sair da tela primeiro e esperar depois: a espera é legítima, a thread em que ela
acontece não

## Mecânica

`onCloseRequest` roda `runBlocking { mcpServer.stop() }` na thread de eventos do AWT. `stop()`
toma o mesmo `Mutex` (`lifecycle`) que `start()` segura enquanto faz o bind, e `start()` só o
solta depois de `server.startSuspend(wait = false)` retornar — o que suspende até o socket estar
vinculado. Quando o lock enfim é obtido, o encerramento ainda espera
`STOP_GRACE_MILLIS + STOP_TIMEOUT_MILLIS`, hoje 250 ms + 2 000 ms.

A espera em si é deliberada e o código diz por quê: a porta precisa estar livre antes de o
processo morrer, ou um relançamento corre com o socket que ele mesmo deixou. O que não é
deliberado é fazê-la sem timeout e na thread que desenha — o comentário ao lado do `start()`
reconhece que "the close below waits on the same lifecycle from the UI thread" sem tirar
conclusão disso.

## Evidência

- `app/desktop/.../main.kt` — `onCloseRequest = { runBlocking { mcpServer.stop() }; exitApplication() }`
- `DesktopMcpServerController` — `private val lifecycle = Mutex()`, com `start()` e `stop()` ambos
  em `lifecycle.withLock`
- `DesktopMcpServerController.bringUp()` — `server.startSuspend(wait = false)` dentro do lock
- `DesktopMcpServerController` — `STOP_GRACE_MILLIS = 250L`, `STOP_TIMEOUT_MILLIS = 2_000L`

## Consequência

Até ~2,25 s de janela congelada no caso normal, e indefinidamente se o bind travar — `runBlocking`
não tem timeout. Não engana e não perde dado; é a última impressão que o app deixa a cada sessão.

## Sugestão

Manter a espera e tirá-la da EDT: esconder a janela (ou entrar num estado de "encerrando") antes
de bloquear, e limitar o pior caso com um `withTimeout`. A porta é liberada pela morte do processo
de qualquer forma, se o encerramento gracioso não chegar lá antes. Não vinculante.
