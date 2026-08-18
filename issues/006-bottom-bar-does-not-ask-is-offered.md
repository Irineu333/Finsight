# 006 — a bottom bar é a única affordance que não pergunta `isOffered`

**Área:** shell · **Tipo:** correção (latente) · **Severidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

O eixo de plataforma foi generalizado em `NavDestination.isOffered`, cujo KDoc o chama de *"the one
question every affordance asks before drawing an entry point, so the answer cannot differ between
them"*. A rail pergunta e o grid do dashboard pergunta. A bottom bar não.

## Evidência

`feature/shell/impl/.../ui/screen/home/ChromeHost.kt:77-80`

```kotlin
// The platform decides, in both directions: what has no desktop backing stays out of the rail,
// and what only the desktop can run stays out of every mobile affordance.
val railItems = destinations.filter { it.isOffered }
val bottomItems = destinations.filter { it.primaryTab }
```

- `feature/shell/api/.../NavDestination.kt:34-38` — o contrato que `bottomItems` não honra.
- `feature/dashboard/impl/.../GetDashboardPreferencesUseCase.kt:103` — `filterNot { it.isOffered }`,
  a terceira affordance, que honra.
- `NavDestination.kt:16-17` — *"It is orthogonal to the window's width — a narrow window on the
  desktop is still the desktop"*, ou seja, a bottom bar é alcançável numa plataforma que o eixo pode
  restringir.

## Por que é latente e não ativo

Nada no catálogo define `onlyOn` hoje — `feature/shell/impl/.../AppNavCatalog.kt` declara onze
destinos, nenhum com o parâmetro — e existe um teste que fixa esse fato:

`feature/shell/impl/src/jvmTest/.../PlatformAxisTest.kt:89-98`

```kotlin
@Test
fun `nothing in the catalog is hidden on the desktop today`() { … }
```

O defeito aparece com o primeiro `NavDestination(primaryTab = true, onlyOn = …)` que alguém
adicionar: numa janela estreita do desktop a bottom bar o desenharia e navegaria para uma feature
sem suporte ali.

## Correção sugerida

```kotlin
val bottomItems = destinations.filter { it.primaryTab && it.isOffered }
```

Ou, se o invariante pretendido for *"uma primary tab é oferecida em toda plataforma"*, declará-lo —
um `require` no catálogo, ou um teste afirmando
`destinations.filter { it.primaryTab }.all { it.onlyOn == null }` — para que o par não possa ser
criado silenciosamente. Hoje o KDoc promete uma garantia que o código não dá.
