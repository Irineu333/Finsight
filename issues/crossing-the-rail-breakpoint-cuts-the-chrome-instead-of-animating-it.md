---
area: app
severity: low
type: ux
---

# Cruzar o breakpoint do rail corta a cromagem em vez de animá-la

## Invariante

Toda mudança da cromagem — barra, rail, painel de detalhe, botão de ação — é animada.

Hoje é falso ao cruzar 600dp ou 840dp: a decisão é um `if` **fora** da transição, então a barra
some e o rail aparece no mesmo quadro, sem entrada nem saída. Só as mudanças vindas de navegação
animam.

## Cenário

**DADO** o desktop (`:app:desktop`) com a janela em 700dp de largura, rail à mostra
**QUANDO** o usuário arrasta a borda até abaixo de 600dp
**ENTÃO** o rail desaparece e a bottom bar aparece de um quadro para o outro
**DEVERIA** trocar com a mesma animação que qualquer outra mudança de cromagem tem

## Mecânica

`isWideWindow` e `isExtraWideWindow()` particionam o corpo do `ChromeHost` em ramos condicionais,
enquanto `chromeTransition` só tem `ChromeConfig` como alvo. A largura da janela não faz parte do
estado que a transição anima, então mudá-la remove e insere composables em vez de transicioná-los.

Estreitar é pior que cortar. O slot `bottomBar` é composto **pela primeira vez** nesse quadro, e um
`AnimatedVisibility` filho semeia o seu estado inicial no `currentState` do pai no instante em que
compõe — que ainda é a configuração de `WIDE`, com `isBottomBarVisible = true`, porque a transição
acabou de ser realvada. Alvo falso, semente verdadeira: ele toca a **saída**. Numa seção que não é
aba primária (categorias, orçamentos, contas, cartões, recorrentes, parcelamentos, relatório,
configurações, suporte) arrastar a janela para menos de 600dp faz uma bottom bar inteira aparecer e
deslizar embora — e o conteúdo é espremido pela altura dela e solto de novo, porque o `Scaffold`
tira o padding inferior do slot medido. Alargar está certo: a semente é "sem rail" e ele entra.

Duas ocorrências a mais, do mesmo estado que não conhece a largura:

- Vindo de `WIDE`, a bottom bar nunca foi composta nesta sessão, logo `bottomBarHeight` é nulo,
  `dockedAnchor` também e `fabTarget` sai nulo — o bloco do botão é pulado por um quadro. A barra se
  mede no layout desse quadro e o botão aparece encaixado no seguinte, sem animação.
- Se a barra **já** tinha sido medida numa passagem anterior por `COMPACT`, `fabTarget` vai de `1f`
  a `0f` sem passar por nulo, o `Animatable` não é recriado e `isDrawn` continua verdadeiro — porque
  ele responde sobre a *configuração*, não sobre o botão estar composto, e em `WIDE` o botão do
  canto nunca é composto. `fabJourney` responde `Travel`: o botão aparece no canto e desliza até o
  encaixe, quando devia ser posto lá.

## Evidência

- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `if (!isWideWindow)` no slot `bottomBar`,
  `if (isWideWindow)` no rail e no menu dele, `if (isExtraWideWindow())` no painel, e `if (fabTarget
  != null)` no bloco do botão
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `bottomBarHeight` só é escrito pelo
  `onSizeChanged` da barra, que em `WIDE` não é composta
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `isButtonDrawn`, derivado de
  `fabVisibility.currentState`, que segue a configuração e não a composição
- `feature/shell/impl/.../screen/home/FabPlacement.kt` — `fabJourney`, que confia nesse `isDrawn`
- Compose 1.10.1, `androidx/compose/animation/core/Transition.kt` — `createChildTransition` semeia
  em `remember(this) { this.currentState }`
- `core/designsystem/.../ui/util/WindowSize.kt` — os dois breakpoints

## Consequência

Invisível no celular, onde a largura não muda sem recriar a Activity. Óbvio no desktop e no
multi-janela do Android, onde a largura é arrastável — e o desktop desce até 480dp de propósito
(`WindowDefaults.MinSize`, abaixo do breakpoint), então a travessia é rotina: a cada uma, uma barra
que entra só para sair, o conteúdo pulando com ela, e o botão andando até um lugar onde devia
apenas aparecer.

## Sugestão

Levar a largura para dentro do estado que a transição anima. Custa o alvo deixar de ser
`ChromeConfig` — o tipo da api — e virar um par privado da casca, e `bottomBarHeight` precisa
continuar içado acima dos dois ramos, porque é escrito de dentro do slot do `Scaffold`. Não
vinculante.
