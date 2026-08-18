# 010 — a porta configurada não pode ser reaplicada, o que bloqueia a saída documentada de `PORT_IN_USE`

**Área:** mcp (UI) · **Tipo:** UX / correção · **Criticidade:** média · **Status:** corrigida em 2026-08-18
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`EditPortModal` desabilita o Aplicar quando a porta digitada é igual à configurada. Quando o bind
naquela porta *falhou*, reaplicá-la é justamente a ação de que o usuário precisa depois de liberá-la
— e é a ação que o `setPort` documenta ser.

## Evidência

`feature/mcp/impl/.../ui/modal/editPort/EditPortModal.kt:65-67`

```kotlin
val port = draft.toIntOrNull()
val isPort = port in McpServerController.VALID_PORTS
val canConfirm = isPort && port != current
```

`feature/mcp/api/.../McpServerController.kt:85-91`

```kotlin
/**
 * Moves the port and rebinds if the server was up, **or was up for the trying and failed**.
 *
 * This is the way out of [McpServerFailure.PORT_IN_USE]: the user resolves the clash once and
 * the client configured for the new port keeps working from then on.
 */
suspend fun setPort(port: Int)
```

`setPort` trata o caso explicitamente — `DesktopMcpServerController.kt:140-146` checa
`_state.value != McpServerState.Stopped`, condição que um estado `Failed` satisfaz, e refaz o bind. O
controller está pronto para a retentativa; a sheet é que não a envia.

## Cenário de falha

1. Servidor habilitado na 8477; outro programa já a segura. O estado é `Failed(8477, PORT_IN_USE)`, e
   a linha do endereço diz isso (`McpUiState.kt:76-77`).
2. O usuário encerra o outro programa e reabre a sheet para tentar a 8477 de novo.
3. `port != current` é falso → o Aplicar segue desabilitado, sem nenhuma explicação.
4. O único rebind que resta é desligar e religar o servidor inteiro, uma affordance em outro card e
   sem nada que a identifique como a retentativa.

## Correção sugerida

Permitir a confirmação quando o servidor não estiver rodando naquela porta:

```kotlin
val canConfirm = isPort && (port != current || isFailed)
```

passando a falha para o modal (ele já recebe `current: Int`). O propósito declarado da guarda — *"the
sheet never closes having done nothing"* (`EditPortModal.kt:47-51`) — continua satisfeito: com um
bind falho, reaplicar a mesma porta faz alguma coisa.
## Correção aplicada

`canConfirm` deixou de ser uma expressão dentro do composable e virou `canApplyPort(draft, current,
isFailed)`, uma função pura ao lado do modal — que é o que a torna afirmável por um teste. O modal
passou a receber `isFailed`, e a tela o lê de `McpUiState.hasFailedBind`, propriedade nova ao lado das
outras leituras do estado do socket.

A regra: recusar continua sendo o certo enquanto a porta é a que o servidor **segura**, porque
confirmar fecharia a sheet sem fazer nada. Depois de um bind falho ele não segura nenhuma, e o mesmo
número passa a ser a retentativa que o `setPort` documenta ser — a saída do `PORT_IN_USE` depois de o
usuário liberar a porta.

O KDoc da classe dizia que confirmar é recusado quando o número *"é o que já está em uso"*. Isso
descrevia o defeito: a porta de um bind falho não está em uso por ninguém. Reescrito.

## A sequência

O comportamento antigo foi extraído **primeiro**, sem alteração, e o teste rodado contra ele: dos
cinco casos, exatamente **um** falhou — o da porta reaplicada depois da falha. Os outros quatro
passaram, provando que a extração não mexeu em nada. Só então a condição ganhou o `|| isFailed`.

Um dos casos existe para impedir a leitura preguiçosa da regra: uma falha **não** torna um não-porta
um porta. `"0"` com `isFailed = true` continua recusado.
