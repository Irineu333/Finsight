---
area: app
severity: low
type: ux
---

# O painel de detalhe aparece e some sem animar

## Cenário

**DADO** o desktop (`:app:desktop`) com a janela em 900dp de largura, painel de detalhe à mostra
**QUANDO** o usuário arrasta a borda até abaixo de 840dp
**ENTÃO** o painel desaparece de um quadro para o outro, e a coluna de conteúdo salta para ocupar a
largura dele
**DEVERIA** sair com a mesma animação que a barra, o rail e o botão de ação têm ao cruzar 600dp

## Mecânica

`isExtraWideWindow()` é lido como condição de um `if` em volta do `DetailPane`, dentro do `Row` do
`ChromeHost`. Um ramo insere e remove; só uma transição anima. É o mesmo defeito que
`crossing-the-rail-breakpoint-cuts-the-chrome-instead-of-animating-it` tinha para a barra e o rail,
no breakpoint de cima e no único elemento de cromagem que ficou de fora daquela correção.

`ChromeState` já é o alvo do `chromeTransition` e carrega `isWideWindow: Boolean` — não a largura
inteira. Cobrir 840dp pede que ele carregue o `WindowMode`, e não um segundo booleano: os dois
breakpoints são a mesma grandeza lida em dois pontos, e guardá-los separados é convidar a que só um
deles seja mantido.

*Hipótese, não verificada: o painel também precisa que o seu conteúdo sobreviva à saída. Uma tela
descarta o painel ao deixar de ser extra-larga (`SupportScreen` dispensa o `ChatDetail` no
`onDispose`), então animar o container pode esvaziá-lo antes de ele terminar de encolher.*

## Evidência

- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `if (isExtraWideWindow())` em volta do
  `DetailPane`, dentro do `Row`
- `feature/shell/impl/.../screen/home/ChromeState.kt` — o alvo animado, hoje com `isWideWindow` e
  não com o modo
- `core/designsystem/.../ui/util/WindowSize.kt` — `WindowMode` e os dois breakpoints
- `feature/support/impl/.../screen/support/SupportScreen.kt` — a tela que dispensa o painel ao sair

## Consequência

Invisível no celular, onde a largura não muda sem recriar a Activity. Visível no desktop e no
multi-janela do Android: a cada travessia de 840dp o conteúdo salta 400dp de largura de um quadro
para o outro, e o painel some sem aviso.

## Sugestão

Trocar `ChromeState.isWideWindow: Boolean` por `windowMode: WindowMode` e envolver o `DetailPane`
num `AnimatedVisibility` da mesma transição, com `expandHorizontally`/`shrinkHorizontally`. Não
vinculante.
