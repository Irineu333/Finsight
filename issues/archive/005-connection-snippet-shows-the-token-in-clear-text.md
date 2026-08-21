# 005 — o snippet de conexão mostra o token em texto claro, logo abaixo da linha que o mascara

**Área:** mcp (UI) · **Tipo:** segurança · **Criticidade:** média · **Status:** corrigida em 2026-08-18
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
## Correção aplicada

A decisão subiu do `McpScreen` para o `McpUiState`, que é onde o resto do estado da tela já decide e
onde já existe teste. São duas propriedades, o mesmo par `value`/`copied` que o `CopyableRow` já usa:

- `displayedConnectionSnippet` — o que a tela desenha, com o token sob a mesma máscara da linha acima;
- `connectionSnippet` — o que o botão copia, com o token real, revelado ou não.

O `connectionSnippet(address, token)` privado do `McpScreen` deixou de existir; a montagem do bloco
agora lê o `address` do próprio estado.

Revelar continua revelando dentro do bloco, e copiar continua entregando o token real nos dois
estados — são asserções do teste, não observação.

O `SelectionContainer` segue existindo, e o comentário acima dele foi reescrito junto: a razão que
sobrou é extrair **o endereço** do bloco isoladamente, para quem aponta um cliente que descreve o
transporte de outro jeito. O token não é mais uma delas — o que a seleção alcança é o que a linha de
cima está mostrando, mascarado até ser revelado, que é exatamente o que esta issue pedia. Copiar o
bloco continua entregando o token real.

O teste vive em `McpViewModelTest` e encoda o cenário de falha desta issue — a seção capturada num
screenshot. A prova por `git stash` aqui só produziria erro de compilação, porque antes da correção
não havia propriedade nenhuma onde asseverar; a prova real foi por **mutação**: com
`displayedConnectionSnippet` lendo `token` em vez de `displayedToken`, o teste falha com *"the block
handed the token to a screenshot the row above it had masked"*.
