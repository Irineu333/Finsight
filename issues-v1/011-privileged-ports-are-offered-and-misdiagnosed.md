# 011 — portas privilegiadas são oferecidas, e a falha do bind é diagnosticada como "porta em uso"

**Área:** mcp · **Tipo:** UX / correção · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`VALID_PORTS` é todo o espaço de portas, incluindo a faixa privilegiada que um processo desktop sem
privilégios não consegue vincular. Quando o usuário escolhe uma delas, a falha é reportada como
**`PORT_IN_USE`** — a porta não está em uso, e a orientação que decorre desse diagnóstico (fechar
quem a está segurando) não leva a lugar nenhum.

## Evidência

`feature/mcp/api/.../McpServerController.kt:129-134`

```kotlin
/**
 * The ports a socket can be asked to take. It belongs to the contract and not to whichever
 * screen collects the number …
 */
val VALID_PORTS: IntRange = 1..65535
```

`feature/mcp/impl/.../DesktopMcpServerController.kt:370-382`

```kotlin
/**
 * A port already held is the one failure the user can act on, and [BindException] is the
 * platform's own typed answer to that question …
 */
private fun Throwable.classify(): McpServerFailure {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is BindException) return McpServerFailure.PORT_IN_USE
        cause = cause.cause
    }
    return McpServerFailure.UNAVAILABLE
}
```

A premissa desse KDoc é falsa: `BindException` **não** é específica de uma porta já ocupada. A JVM
mapeia `EACCES` para ela também. Testado nesta máquina (macOS 24.5.0, sem privilégios):

```
java.net.ServerSocket.bind(127.0.0.1:80)    -> java.net.BindException: Permission denied
java.nio.channels.ServerSocketChannel.bind  -> java.net.BindException: Permission denied
```

Ou seja, `classify()` devolve `PORT_IN_USE` para uma falha de permissão, e a seção informa ao usuário
que a porta está tomada quando não está.

## Cenário de falha

O usuário digita 80 no `EditPortModal`. `isPort` passa (`EditPortModal.kt:66`), `setPort` refaz o
bind, o bind é recusado por falta de privilégio, e tanto o modal quanto a seção reportam um conflito
com outro programa. Nada na tela sugere que a faixa em si é o problema, e nenhuma porta abaixo de
1024 vai funcionar.

## Correção sugerida

Duas partes, e a primeira é a que importa:

1. `VALID_PORTS = 1024..65535`, para que a faixa ofereça apenas o que o processo consegue tomar. O
   KDoc já diz que a faixa pertence ao contrato exatamente para que nenhuma superfície discorde dela.
2. Ou estreitar o `classify()` (inspecionar a mensagem, ou checar a faixa antes e responder uma falha
   distinta) ou corrigir seu KDoc, que hoje enuncia uma garantia que a plataforma não dá.

## Observação

Isto corrige a revisão que originou a issue, que previa `UNAVAILABLE`. O teste acima mostra
`PORT_IN_USE`. A queixa de fundo — uma porta oferecida que não pode ser vinculada, reportada em
palavras sobre as quais o usuário não consegue agir — se mantém de qualquer forma.
