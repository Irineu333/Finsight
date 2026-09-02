---
area: support
severity: medium
type: crash
confirmed: no
---

# O chat rola para uma contagem que ele ainda não mediu

## Cenário

**DADO** a conversa de uma issue de suporte com mensagens
**QUANDO** o usuário envia uma mensagem nova
**ENTÃO** a lista rola para a **penúltima** linha e a mensagem recém-enviada fica fora da
tela; e na primeira composição, quando `layoutInfo` ainda não foi medido, o alvo é `-1`
**DEVERIA** rolar sempre para a última mensagem, e nunca calcular um índice negativo

## Mecânica

O efeito é disparado pela contagem nova e rola pela contagem **velha**:

```kotlin
LaunchedEffect(state.messages.size) {
    if (state.messages.isNotEmpty()) {
        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
}
```

`state.messages.size` é a chave; `listState.layoutInfo.totalItemsCount` é o que a última
passada de **medição** viu, que na hora em que o efeito roda ainda é a lista anterior. O
guard protege contra lista vazia, não contra `layoutInfo` não medido — e `totalItemsCount - 1`
não tem `coerceAtLeast(0)`.

## Evidência

- `feature/support/impl/.../support/SupportIssueScreen.kt` — `MessagesList()`: o
  `LaunchedEffect(state.messages.size)` e o `animateScrollToItem(totalItemsCount - 1)`
- mesmo arquivo — a `LazyColumn` abaixo, cujo `item(key = "header")` condicional faz
  `totalItemsCount` divergir de `messages.size`

## Consequência

A mensagem que o usuário acabou de enviar não aparece sem que ele role à mão. E se o índice
negativo chegar ao scroll, é exceção durante a composição.

## O que falta para confirmar

O índice fora de faixa e a chave dessincronizada são fato lido no disco. **Que o efeito
rode antes da primeira medição é hipótese**: confirmá-la pede um teste de UI que abra a tela
com a lista já populada e observe o alvo do scroll — ou, mais barato, ler qual precondição a
versão de Compose Multiplatform do projeto aplica a um índice negativo em
`animateScrollToItem`.

## Sugestão

Rolar por `state.messages.lastIndex` — a grandeza que a chave do efeito já observa — em vez
da contagem medida, e proteger o alvo com `coerceAtLeast(0)`. Não vinculante.
