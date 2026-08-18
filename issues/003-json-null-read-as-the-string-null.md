# 003 — um `null` JSON explícito é lido como a string de quatro caracteres `"null"`

**Área:** mcp · **Tipo:** correção · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`JsonObject?.string()` faz cast para `JsonPrimitive` e pega `content`. `JsonNull` **é** um
`JsonPrimitive`, e seu `content` é o literal `"null"` — que não é em branco e portanto sobrevive ao
`takeIf`. Todo argumento lido por `string()` trata um `null` explícito como valor.

## Evidência

`feature/mcp/impl/.../tool/ToolSupport.kt:92-93`

```kotlin
internal fun JsonObject?.string(name: String): String? =
    (this?.get(name) as? JsonPrimitive)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
```

Verificado contra a versão no classpath (kotlinx-serialization-json-jvm 1.9.0):

```
$ javap -cp … kotlinx.serialization.json.JsonNull
public final class kotlinx.serialization.json.JsonNull extends kotlinx.serialization.json.JsonPrimitive

$ jshell> JsonNull.INSTANCE.getContent()
[null] blank? false
```

Os demais leitores **não** são consistentes com isso, o que torna a superfície irregular além de
errada:

- `ToolSupport.kt:95-99` — `long()` faz o mesmo cast, e então `"null".toLongOrNull()` falha → recusa.
- `ToolSupport.kt:139-144` — `flag()` idem: `"null".toBooleanStrictOrNull()` falha → recusa.
- `ToolSupport.kt:126-136` — `monthOrNull()` e `date()` são construídos sobre `string()`, então
  recusam com *"`month` must be a month as `2026-03`, but `null` was given"* em vez de cair no
  default documentado.
- `ToolSupport.kt:147-151` — `oneOf()` idem.
- `WriteSupport.kt:136` — `names()` (`this?.get(name) != null`) reporta um null explícito como
  *presente*, que é uma terceira resposta para a mesma pergunta.

## Cenário de falha

Muitos clientes MCP serializam campos opcionais omitidos como nulls explícitos.

- `create_category({"name": null, …})` → uma categoria literalmente chamada `null`.
- `update_transaction({"id": 7, "title": null})` → o título é reescrito para `"null"` em vez de ser
  mantido, e a promessa da tool de que *"what is not given keeps the value it already has"*
  (`TransactionWriteTools.kt:292`) é quebrada.
- `list_transactions({"month": null})` → recusado, em vez de usar o mês em que o app está.

## Correção sugerida

Tratar `JsonNull` como ausência no único ponto onde o wire é lido:

```kotlin
private fun JsonObject?.primitive(name: String): JsonPrimitive? =
    (this?.get(name) as? JsonPrimitive)?.takeIf { it !is JsonNull }
```

e construir `string()`, `long()`, `flag()` e `names()` sobre ele. Essa é a decisão única — *"um null
explícito significa que o chamador não disse nada"* — e ela pertence ao lado do comentário em
`ToolSupport.kt:28-29`, que já afirma que esta camada existe para não haver oito respostas
ligeiramente diferentes sobre o que um argumento ausente significa.
