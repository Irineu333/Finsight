# 015 — dois imports não usados em `McpUiState`

**Área:** mcp (UI) · **Tipo:** código morto · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`feature/mcp/impl/.../ui/screen/mcp/McpUiState.kt:15-16`

```kotlin
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_port_error_invalid
```

Nenhum dos dois é referenciado em qualquer ponto do arquivo. `Res.string` não aparece nele, e
`addressError` agora delega a redação ao domínio (`McpUiState.kt:76-77`):

```kotlin
val addressError: UiText?
    get() = (server as? McpServerState.Failed)?.toPortFieldUiText()
```

A chave em si está viva, e seu único consumidor é o modal:
`feature/mcp/impl/.../ui/modal/editPort/EditPortModal.kt:105`.

## Por que vale uma linha

Um import de chave de recurso é uma afirmação sobre quem é dono de um texto. Deixado aqui, ele diz ao
próximo leitor que este arquivo ainda decide o que "porta inválida" diz — que é exatamente a pergunta
que leva alguém a abrir o `McpUiState`.

## Correção sugerida

Apagar as duas linhas.
