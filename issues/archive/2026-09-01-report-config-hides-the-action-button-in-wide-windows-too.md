---
area: report
severity: low
type: ux
verdict: fixed
---

# A configuração de relatório esconde o botão de ação também em janela larga

## Cenário

**DADO** uma janela `WIDE` (≥600dp), onde a casca desenha o botão de ação no cabeçalho do rail
de navegação — à esquerda e no topo, longe da `bottomBar` de qualquer tela
**QUANDO** o usuário abre a configuração de relatório
**ENTÃO** o botão do rail desaparece, e com ele a ação universal de registrar transação
**DEVERIA** continuar visível: a colisão que justifica escondê-lo só existe em `COMPACT`

## Mecânica

`ReportConfigScreen` publica `ChromeConfig(isFloatingActionButtonVisible = false)` sem condição
alguma. O comentário ao lado enuncia o motivo — o botão "Gerar" ocupa a `bottomBar` da tela e a
casca põe o botão de ação sobre a ponta direita dela — e esse motivo descreve apenas a janela
compacta: em `ChromeHost` a caixa que ancora o botão no canto inferior está dentro de
`if (!isWideWindow)`, e de `WIDE` para cima o botão é desenhado como `header` do
`NavigationRailBar`. Não há o que colidir, e a supressão cobra o preço sem a contrapartida.

É a mesma supressão que `SupportIssueScreen` fazia, pelo mesmo raciocínio de colisão; lá ela já
está condicionada a `isWideWindow()`.

## Evidência

- `feature/report/impl/.../screen/report/config/ReportConfigScreen.kt` — `ChromeEffect(config =
  ChromeConfig(isFloatingActionButtonVisible = false))`, incondicional
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `if (!isWideWindow) { … FloatingActionMenu }`,
  o botão do canto; e `NavigationRailBar(header = { … FloatingActionMenuButton })`, o do rail
- `feature/support/impl/.../screen/support/SupportIssueScreen.kt` — a supressão irmã, condicionada

## Consequência

Em janela larga o cabeçalho do rail fica vazio enquanto a tela está aberta, e a ação que existe
justamente para não depender de tela nenhuma some. Não engana e não impede — basta sair da tela —
mas é uma afordância perdida por um motivo que ali não se aplica.

## Sugestão

A mesma forma aplicada em `SupportIssueScreen`: publicar `ChromeConfig.Default` de `WIDE` para
cima e a supressão só abaixo dela. Não vinculante.

## Desfecho

**Causa real** — como registrado: `ChromeEffect` publicava a supressão sem condição, e o motivo
que a justifica (o botão "Gerar" ocupando a `bottomBar`) só descreve a janela compacta.

**Mudança** — `ReportConfigScreen` passa a publicar `ChromeConfig.Default` de `WIDE` para cima e
a supressão só abaixo dela, lendo `isWideWindow()` — o mesmo fato pelo qual `ChromeHost` decide
onde desenhar o botão. O comentário ao lado passa a dizer isso.

**Prova** — nenhuma automatizada. Não há teste que morda uma decisão de chrome por largura de
janela, e um teste estrutural que proibisse a supressão incondicional seria falso: o modo de
edição do dashboard publica `ChromeConfig.ContentOnly` e está certo em qualquer largura. O que
foi verificado: `:feature:report:impl:compileKotlinJvm` limpo, e a leitura de `ChromeHost`, que
desenha o botão do canto dentro de `if (!isWideWindow)` e o do rail como `header` do
`NavigationRailBar`.

**Commit** — `Fix(Report): suppress the action button only where it can collide`
