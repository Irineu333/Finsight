---
area: app
severity: low
type: navigation
verdict: refuted
---

# A bottom bar é a única affordance que não pergunta `isOffered`

## Invariante

Toda affordance pergunta `isOffered` antes de desenhar um ponto de entrada — o que a KDoc de
`NavDestination.isOffered` chama de *"the one question every affordance asks before drawing an
entry point, so the answer cannot differ between them"*.

A bottom bar não pergunta: `ChromeHost` filtra por `primaryTab` e nada mais. A rail pergunta
(`filter { it.isOffered }`) e o grid do dashboard pergunta (`filterNot { it.isOffered }`).

## Evidência

- `ChromeHost` (`feature/shell/impl` — `ui/screen/home/ChromeHost.kt`) — `val railItems =
  destinations.filter { it.isOffered }` e, na linha seguinte, `val bottomItems = destinations
  .filter { it.primaryTab }`
- `NavDestination.isOffered` (`feature/shell/api`) — `onlyOn?.isCurrent != false`, e a KDoc acima
- `GetDashboardPreferencesUseCase` — `.filterNot { it.isOffered }`, a terceira affordance
- `AppNavCatalog` — onze destinos, nenhum com `onlyOn`

## Consequência prevista pelo registro

Um `NavDestination(primaryTab = true, onlyOn = …)` acrescentado ao catálogo seria desenhado pela
bottom bar numa janela estreita do desktop, navegando para uma feature sem suporte ali.

## Desfecho

**O que a premissa errou** — o registro conclui que "hoje o KDoc promete uma garantia que o código
não dá", e que o defeito apareceria com o primeiro destino que combinasse `primaryTab` com
`onlyOn`. Esse par não pode existir: `AppModulesTest.navCatalogProjectionsAreConsistent` afirma
`assertTrue(destinations.none { it.primaryTab && it.onlyOn != null })`, com o comentário *"A primary
tab is restricted to no platform (tabs exist on every form factor)"*. A garantia da KDoc vale — só
que sustentada por um teste sobre o catálogo, e não pelo filtro da bottom bar.

**O que o código faz de fato** — a asserção já estava lá quando o achado foi registrado: entrou em
`57567325a` (2026-08-16), e o registro é de 2026-08-17, contra `cc6ca4ccf`, do qual `57567325a` é
ancestral. O registro cita o teste vizinho (`PlatformAxisTest.nothing in the catalog is hidden on
the desktop today`, que afirma outra coisa) e não alcançou este.

**O que continua verdade, e não é defeito** — a divergência entre a KDoc, que fala de "every
affordance", e o filtro, que não pergunta. É uma imprecisão de redação sobre um invariante que
existe e é verificado em outro lugar, não um caminho que produza comportamento errado.
