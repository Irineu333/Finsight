## Context

O overlay do `SharedTransitionLayout` é uma segunda superfície de desenho: enquanto `isTransitionActive`, o conteúdo marcado é retirado da árvore normal e pintado no fim, na ordem definida por `zIndexInOverlay`. Quem entra nessa superfície abandona a ordem de desenho que o `Scaffold` estabeleceu — e hoje três participantes entram nela sem que a ordem entre eles esteja escrita em lugar nenhum.

Estado atual, conforme o fonte:

```
androidx/compose/animation/SharedTransitionScope.kt:1381
    renderers.sortBy { ...it.zIndex }     // sort ESTÁVEL
    renderers.fastForEach { it.drawInOverlay(scope) }

androidx/compose/material3/Scaffold.kt
    subcompose(TopBar); subcompose(Snackbar); subcompose(Fab); subcompose(BottomBar)
                                              └── attach do Fab ANTES do BottomBar
    layout { bottomBarPlaceable.place(...); fabPlaceable.place(...) }
             └── placement do BottomBar ANTES do Fab
```

As duas ordens são opostas. Fora do overlay vale o placement e o FAB fica por cima; dentro do overlay, com `zIndex` empatado em `1f`, o sort estável preserva a ordem de attach e a barra fica por cima.

Os três níveis envolvidos vivem em módulos que a regra de dependência impede de se nomearem:

```
  :core:ui              CreditCardCard          z = 0f (default implícito)
  :feature:shell:impl   bottom bar / rail       z = 1f
  :feature:shell:impl   FAB                     z = 1f   ← empate
```

`:core:ui` não pode nomear o shell; o shell é livre para nomear os cores, mas escrever a pilha inteira dentro dele deixaria o nível do cartão fora de vista, exatamente onde ele já está invisível hoje.

## Goals / Non-Goals

**Goals:**

- O FAB permanece integralmente visível durante qualquer transição compartilhada, em qualquer largura de janela.
- A pilha de prioridades do overlay — cartão < chrome de navegação < FAB — existe escrita, num único lugar, legível sem consultar o fonte do Compose.
- Cada participante do overlay declara seu nível explicitamente; nenhum herda posição por default do framework ou por ordem de composição.

**Non-Goals:**

- Alterar o layout, o posicionamento ou o `offset(y = 40.dp)` do FAB docked. A geometria está correta; só a ordem de pintura está errada.
- Alterar as animações de entrada/saída do chrome (`slideInVertically`/`expandVertically` na barra, `fadeIn`/`fadeOut` no FAB).
- Generalizar a escala para superfícies que não participam do overlay — modais, painel de detalhe, `DropdownMenu` seguem sob as regras de elevação do Compose, sem relação com este mecanismo.
- Introduzir novos elementos compartilhados ou mexer nos existentes.

## Decisions

### 1. Uma escala nomeada em `:core:designsystem`, e não um número solto no call site

O corretivo mínimo é passar `2f` na chamada do FAB. Rejeitado: recria exatamente o problema que causou o defeito — um número mágico num call site, cuja relação com os outros números só existe na cabeça de quem escreveu.

A escala vira um objeto nomeado com os três níveis, em `core/designsystem/.../component/SharedTransitionProvider.kt`. Esse arquivo já é dono do vocabulário do overlay (`LocalSharedTransitionScope`, `LocalAnimatedVisibilityScope`, `SharedTransitionProvider`); a ordem de pintura é a mesma matéria. E `:core:designsystem` está abaixo tanto de `:core:ui` quanto de `:feature:shell:impl`, então os dois consomem a mesma fonte sem que nenhum nomeie o outro — a única posição no grafo de módulos onde a pilha inteira cabe.

*Alternativas consideradas:* `:core:ui` (não é visível de `:core:designsystem`, e o dono do `SharedTransitionProvider` é o designsystem); um `CompositionLocal` com a ordem (dinamismo sem demanda — a pilha é estática e conhecida em tempo de compilação).

### 2. Nomes por papel, não por número

Os membros da escala nomeiam o papel do participante — o elemento compartilhado, o chrome de navegação, o FAB —, não sua altura. Um call site que lê `<escala>.Fab` diz o que aquele conteúdo é; um que lê `2f` obriga a procurar quem mais usa `1f` para descobrir o que a linha significa. Os valores concretos ficam contíguos e crescentes, e são detalhe interno da escala.

### 3. O nível do elemento compartilhado passa a ser declarado

`creditCardSharedElement` hoje não passa `zIndexInOverlay`, ficando em `0f` pelo default do Compose. Funciona, e continuaria funcionando. Mas "o cartão fica abaixo de todos" agora é uma regra do produto, não uma coincidência com o default do framework — e uma regra que ninguém escreveu é uma regra que o próximo elemento compartilhado vai quebrar sem perceber. Declarar custa um argumento e transforma o default numa decisão.

### 4. `aboveSharedElements` passa a receber o nível

O helper privado do `ChromeHost` (`ChromeHost.kt:223-229`) ganha um parâmetro de nível em vez de fixar `1f`. Ele continua sendo o único ponto do shell que fala com o overlay, e continua inerte fora de um `SharedTransitionProvider`. Seu KDoc hoje explica *por que* o chrome sobe ao overlay; passa a explicar também que a barra e o FAB não compartilham prioridade, e por quê — sem isso, o próximo leitor vê dois níveis diferentes e desfaz a correção achando que unificou uma duplicação.

### 5. A ordem no overlay espelha a ordem de placement do `Scaffold`

Escolher "FAB acima da barra" não é preferência estética: é a ordem que o `Scaffold` já pratica fora do overlay. Manter as duas iguais significa que a aparência do chrome deixa de depender de haver ou não uma transição em curso — que é a definição do defeito. Qualquer futuro elemento de chrome que entre no overlay deve ser posicionado pela mesma referência.

## Risks / Trade-offs

**[A correção depende de um sort estável no Compose]** → Não mais. Hoje o comportamento correto depende de um desempate; depois da mudança os níveis são distintos e a ordenação é determinada pelo `zIndex`, qualquer que seja a estabilidade do sort ou a ordem de `subcompose` do `Scaffold`. A mudança remove a dependência, não a cria.

**[Verificação manual]** → O defeito é de ordem de pintura durante uma animação; não há teste unitário que o capture, e o projeto não tem teste de screenshot. A validação é manual e as três condições precisam ser reproduzidas juntas: janela compacta, ao menos um cartão cadastrado, transições saindo e entrando da dashboard. Um teste que rode sem cartão cadastrado passa vacuamente.

**[Um quarto participante futuro]** → A escala tem exatamente os níveis que existem hoje. Quem adicionar um participante precisa decidir sua posição na pilha e adicioná-lo à escala — o que é o objetivo: tornar a decisão obrigatória e visível, em vez de deixá-la ser tomada por acidente pela ordem de `subcompose`.

**[O rail compartilha o nível da barra]** → Deliberado: são a mesma coisa em larguras diferentes, nunca coexistem, e o FAB do modo wide é `header` do rail — desenhado dentro dele, sem interseção. Separá-los criaria um nível sem consumidor.
