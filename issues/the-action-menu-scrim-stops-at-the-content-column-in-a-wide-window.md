---
area: app
severity: medium
type: ux
---

# Em janela larga o scrim do menu de ações para na coluna de conteúdo

## Cenário

**DADO** uma janela `LARGE` (≥840dp), com o rail à esquerda, o conteúdo no meio e o painel de
detalhe à direita
**QUANDO** o usuário abre o menu do botão de ação, no cabeçalho do rail
**ENTÃO** só a coluna de conteúdo escurece: o rail e o painel de detalhe continuam claros e
**continuam aceitando toque** — dá para fechar o painel, mexer nos controles dele e navegar pelo
rail com o menu aberto por cima
**DEVERIA** barrar a interação com tudo o que está atrás do menu, como faz em `COMPACT`

## Mecânica

Em `COMPACT` o scrim é irmão do `Scaffold`, e o comentário ao lado diz por quê: *"the scrim has to
reach the bottom bar, or the bar goes on taking taps behind an open menu."* Em `WIDE` ele é composto
**dentro** do `Box(Modifier.weight(1f))` da coluna de conteúdo, então o `fillMaxSize()` dele é
limitado por ela. O rail e o `DetailPane` são irmãos dessa coluna, não filhos.

`aboveSharedElements` não ajuda: `renderInSharedTransitionScopeOverlay` muda **desenho**, não teste
de toque.

O comentário atual justifica o recorte por não querer escurecer o rail — a intenção é preservar o
próprio controle de expandir do botão. Mas a consequência vai além do rail e alcança o painel de
detalhe, que não tem nada a ver com essa intenção.

## Evidência

- `feature/shell/impl/.../screen/home/ChromeHost.kt` — o `FloatingActionMenuScrim` de `WIDE`, dentro
  da coluna de conteúdo
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — o de `COMPACT`, irmão do `Scaffold`
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — o `DetailPane`, irmão da coluna no mesmo `Row`

## Consequência

Um menu modal que não é modal: com ele aberto, metade da tela segue operável em `LARGE`. O usuário
pode disparar uma ação do painel achando que fechou o menu.

*Ainda: `isMenuExpanded` não depende da largura, então atravessar 600dp com o menu aberto o mantém
aberto. Recomposto do outro lado, o menu de `WIDE` aparece sem animação em `y = 0` — o
`onGloballyPositioned` do botão do rail nunca rodou nesta sessão, então `railButtonWindowY` ainda é
`0f` — e salta para a linha do botão no quadro seguinte. Hipótese quanto ao quadro exato: não
instrumentei quando o callback de posição chega.*

## Sugestão

Tirar o scrim de `WIDE` da coluna de conteúdo, como em `COMPACT`, e resolver o controle de expandir
do botão de outro jeito — desenhá-lo acima do scrim, por exemplo. Não vinculante.
