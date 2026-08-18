# 005 — o snippet de conexão mostra o token em texto claro, logo abaixo da linha que o mascara

**Área:** mcp (UI) · **Tipo:** segurança · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`TokenRow` mascara o token até o usuário pedir para vê-lo, e explica por quê. O bloco JSON alguns dp
abaixo interpola o token **não mascarado** e é desenhado incondicionalmente, então o mascaramento não
protege nada que um screenshot da seção não capturaria de qualquer forma.

## Evidência

`feature/mcp/impl/.../ui/screen/mcp/McpScreen.kt:355`

```kotlin
val instructions = connectionSnippet(address = uiState.address, token = uiState.token)
```

`McpScreen.kt:707-719`

```kotlin
private fun connectionSnippet(address: String, token: String?): String = """
    …
          "headers": {
            "Authorization": "Bearer ${token.orEmpty()}"
          }
    …
""".trimIndent()
```

`uiState.token` é o valor bruto (`McpUiState.kt:42`). O mascarado é outra propriedade:

`McpUiState.kt:64-65`

```kotlin
/** The token as the screen shows it: masked until the user asks for it. */
val displayedToken: String? get() = token?.let { if (isTokenRevealed) it else MASK }
```

e é `TokenRow` que a usa (`McpScreen.kt:468`), sob um KDoc que nomeia a ameaça
(`McpScreen.kt:457-462`):

> A section that shows it is a target in a screenshot or a shared screen, and the user almost never
> needs to *read* it — copying is what configuring a client takes.

O bloco do snippet fica dentro de `ConnectionCard` sem nenhuma condição (`McpScreen.kt:366-403`).

## Cenário de falha

O usuário abre Configurações → MCP para compartilhar a tela pedindo ajuda, ou tira um screenshot da
seção para abrir um bug. A linha do token mostra `••••••••••••••••`; o bloco JSON abaixo mostra
`"Authorization": "Bearer <o token real>"`. Quem tiver essa string e acesso ao loopback da máquina
tem todas as capabilities concedidas — ler, registrar, remover, operar.

## Correção sugerida

Renderizar o snippet com `uiState.displayedToken` e continuar copiando o real — o bloco já tem dois
valores distintos disponíveis exatamente para esse padrão, como `CopyableRow` recebe `value` e
`copied` separados (`McpScreen.kt:520-524`).

O `SelectionContainer` (`McpScreen.kt:375`) existe para o usuário conseguir extrair o token do bloco
isoladamente; com a máscara aplicada, isso continua possível depois de revelar.
