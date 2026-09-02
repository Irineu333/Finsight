---
area: app
severity: low
type: ux
---

# O botão de ação continua recebendo toques enquanto some

## Cenário

**DADO** uma aba primária em janela `COMPACT`, com o botão encaixado no centro da bottom bar
**QUANDO** o usuário navega para uma tela que esconde o botão e, durante a saída dele, toca a
bottom bar perto do centro
**ENTÃO** o toque abre o formulário de transação em vez de trocar de aba
**DEVERIA** ir para o item da barra: o botão está invisível

## Mecânica

`fadeOut()` anima **alpha**, e só. O `FloatingActionMenu` segue composto e clicável durante toda a
saída — uma mola `StiffnessMediumLow`, algo na casa de 300ms — enquanto desliza por cima da barra.

O catálogo tem exatamente **duas** abas primárias, então cada item ocupa metade da largura e a
fronteira entre elas é o centro, que é onde o botão de 56dp está encaixado.

## Evidência

- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `exit = fadeOut() + slideOutVertically(...)`
  no `AnimatedVisibility` do botão do canto
- `feature/shell/impl/.../ui/navigation/AppNavCatalog.kt` — duas entradas com `primaryTab = true`
- `core/designsystem/.../ui/component/FloatingActionMenu.kt` — o botão e o seu `onClick`

## Consequência

Uma janela de ~300ms por transição em que um botão invisível rouba o toque destinado à navegação.
O usuário fecha o modal e toca de novo.

## Sugestão

Tirar o botão do caminho junto com a opacidade — um `graphicsLayer` não basta, é preciso deixar de
aceitar ponteiro. Não vinculante.
