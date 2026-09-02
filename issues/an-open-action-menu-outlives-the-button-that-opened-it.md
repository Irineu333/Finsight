---
area: app
severity: low
type: ux
confirmed: no
---

# Um menu de ações aberto sobrevive ao botão que o abriu

## Cenário

**DADO** o menu do botão de ação aberto, com o scrim cobrindo a tela
**QUANDO** a tela muda para um estado que esconde o botão **sem** mudar a quantidade de ações que
publica
**ENTÃO** o botão e o menu somem e o scrim continua lá, escurecendo a tela e barrando toques, sem
nada visível para fechá-lo
**DEVERIA** fechar o menu junto com o botão

## Mecânica

`isMenuExpanded` só é reposto quando o destino muda ou quando a **quantidade** de ações muda —
`LaunchedEffect(destinationId, actions.size)`. O scrim é irmão do botão, não filho: em `COMPACT`
está fora do `AnimatedVisibility` do botão e até fora do `if (fabTarget != null)`; em `WIDE` é irmão
do rail. Nenhum dos dois desaparece com o botão.

Não é um impasse: tocar o scrim ainda o dispensa.

## Evidência

- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `LaunchedEffect(destinationId, actions.size)`
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — o `FloatingActionMenuScrim` de `COMPACT`,
  irmão do `Scaffold`, fora do `if (fabTarget != null)`
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — o `FloatingActionMenuScrim` de `WIDE`, dentro
  da área de conteúdo

## O que falta para confirmar

**Uma tela que troque `actionButton` mantendo `actions.size`.** Não achei nenhuma: as que escondem o
botão publicam `emptyList()`, que a casca substitui pela ação universal — tamanho 1 — e essa troca
dispara a reposição. O buraco é real no código; o gatilho, hoje, não existe. Um estado novo em
qualquer tela que publique uma ação própria e esconda o botão ao mesmo tempo o abre.

## Sugestão

Repor `isMenuExpanded` também quando a presença que desenhou o botão deixar de valer. Não vinculante.
