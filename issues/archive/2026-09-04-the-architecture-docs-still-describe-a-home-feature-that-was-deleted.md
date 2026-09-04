---
area: transversal
severity: low
type: data
verdict: fixed
---

# `CLAUDE.md` e `feature/README.md` ainda descrevem a feature `home`, apagada há sete meses

## Invariante

A documentação de arquitetura descreve os módulos que existem.

Hoje é falso: `CLAUDE.md` e `feature/README.md` — este declarado "referência normativa" pelo
primeiro — descrevem uma feature `home` com `HomeGraph`, `homeGraph()`, `HomeChromeHost`,
`QuickActionType` e `DashboardEntry`. Nenhum desses símbolos existe no disco. O commit
`cf78b537a` apagou `feature/home/` inteiro e criou `feature/shell/` no lugar, sem tocar em
nenhum dos dois documentos.

## Mecânica

O que os documentos afirmam, e o que o código faz:

| documento | afirmação | código |
|---|---|---|
| `CLAUDE.md:79` | "Features: home (tab chrome: `HomeGraph`, `NavigationItem`, `HomeChromeHost`, FAB)" | não há `:feature:home:*` em `settings.gradle.kts`; a chrome é `ChromeHost` em `feature/shell/impl` |
| `CLAUDE.md:67` | `App` "invokes `HomeChromeHost`" | `App()` invoca `ChromeHost` |
| `feature/README.md` | lista `home` entre as features; não lista `shell` | existe `feature/shell/{api,impl}` |
| `feature/README.md` | quarto tipo de acesso cross-feature: `context(builder: NavGraphBuilder) fun register()` no entry point, "existe por causa do `home`" | nenhuma ocorrência de `fun register` / `context(builder` em `feature/` nem `app/` |
| `feature/README.md` | exemplo de `homeGraph()` aninhando `DashboardEntry.register()` e `TransactionsEntry.register()` | `AppNavHost()` chama `dashboardGraph()` e `transactionsGraph()` diretamente; `DashboardEntry` não existe |
| `feature/README.md` | "campos que guardam rotas (`NavigationItem.route`, `QuickActionType.route`)" | os campos são `NavDestination.route` (`feature/shell/api`); `QuickActionType` foi apagado |
| `feature/README.md` | `App` "com o `Scaffold` da chrome do Home: bottom bar + FAB" | `App()` não tem `Scaffold` — ele está em `ChromeHost`; e `CLAUDE.md:69` diz o contrário do README: `:app:shared` "declares no route, no `Scaffold`, no chrome" |
| `feature/README.md` | "`HomeGraph` … e `NavigationItem` — o único lugar do projeto autorizado a enumerar as features" está em `:app:shared` | a enumeração é `AppNavCatalog`, em `feature/shell/impl` |

As specs OpenSpec, ao contrário, estão em dia — e por isso servem de contraprova, não de opinião:
`openspec/specs/navigation/spec.md` exige que o `AppNavHost` contenha apenas chamadas a
`<nome>Graph()` **sem nenhum `homeGraph()`**, nomeia `ChromeHost` e `NavCatalog`, e
`openspec/specs/module-architecture/spec.md` exige que `:app:shared` não declare catálogo de
destinos nem componente de chrome. Os dois documentos de arquitetura contradizem as specs e o
código ao mesmo tempo.

## Evidência

- `CLAUDE.md` — linhas 67 e 79
- `feature/README.md` — as seções de acesso cross-feature, o exemplo de grafo e o papel do
  `:app:shared`
- `app/shared/.../ui/AppNavHost.kt` — dez chamadas `<nome>Graph()`, nenhuma `homeGraph()`
- `app/shared/.../ui/App.kt` — `App()` invoca `ChromeHost`, sem `Scaffold`
- `feature/shell/impl/.../screen/home/ChromeHost.kt` e
  `feature/shell/impl/.../navigation/AppNavCatalog.kt` — a chrome e o catálogo reais
- `feature/shell/api/.../NavDestination.kt` — o tipo que substituiu `NavigationItem` e
  `QuickActionType`
- `settings.gradle.kts` — `:feature:shell:api` / `:feature:shell:impl`; nenhum `:feature:home`
- `openspec/specs/navigation/spec.md` e `openspec/specs/module-architecture/spec.md` — as specs
  que já descrevem o estado atual
- `feature/dashboard/impl/.../dashboard/QuickActions.kt` — o KDoc de `parseHiddenActionKeys` é o
  único vestígio legítimo do nome antigo, e diz por quê: normaliza os valores `QuickActionType`
  já gravados na preferência

## Consequência

`CLAUDE.md` manda ler `feature/README.md` como referência normativa, e o `AgentInstructionsTest`
só garante que os caminhos de `AGENTS.md` resolvem — ninguém confere o conteúdo. Quem seguir o
documento vai procurar um entry point `DashboardEntry` que não existe, tentar hospedar um grafo
com um `register()` que nunca foi escrito, e mexer no `:app:shared` procurando a enumeração de
features que mora em `feature/shell/impl`.

## Sugestão

Trocar `home` por `shell` nos dois documentos e reescrever os três trechos que descrevem mecanismo
e não só nome: o quarto tipo de acesso cross-feature (`register()`) hoje não tem nenhuma
ocorrência — ou volta a ser hipotético, como a spec de navegação o mantém, ou sai; o exemplo de
`homeGraph()` vira o `AppNavHost` real; e o papel do `:app:shared` passa a bater com o que
`CLAUDE.md:69` já diz corretamente. Não vinculante.

## Desfecho

**Causa real** — o defeito era de duas idades. O `feature/README.md` foi reescrito por uma
mudança posterior ao registro (fala do `home` só no passado, ao explicar de onde veio o
mecanismo que hoje o `mcp` usa), e o quarto tipo de acesso cross-feature deixou de ser letra
morta: `context(NavGraphBuilder) fun register()` tem 9 ocorrências hoje, e `McpEntry` é uma
delas. O que sobrou vivo até o fim foi só o `CLAUDE.md`, em duas linhas.

**Mudança** — `CLAUDE.md` deixou de listar `home` entre as features e passou a listar `shell`
com os símbolos reais (`NavCatalog`, `NavDestination`, `Chrome`, `ChromeHost`,
`FeaturePlatform`) — os três que o texto citava não existiam: `HomeGraph` e `HomeChromeHost` em
lugar nenhum, e `NavigationItem` só dentro de `NavDestination.kt`. E `App` passou a ser descrito
invocando `ChromeHost`, que é o que ele invoca (`app/shared/.../ui/App.kt:155`). Na mesma
passagem o documento ganhou a feature `mcp`, que não estava em nenhuma das duas listas.

**Prova** — nenhuma: é documentação, e o `AgentInstructionsTest` só resolve caminhos. Cada
símbolo escrito foi conferido por `grep` contra o disco antes de entrar, que é o que a
correção anterior não fez.

**Commit** — `Chore(Docs): describe a surface with two modes and one owner of the archive`
(`90467df54`) e o ajuste final do `ChromeHost`.
