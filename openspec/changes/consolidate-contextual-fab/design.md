## Context

Hoje o botão de ação existe em dois lugares que não se conhecem. A casca desenha o seu no
`Scaffold` de `ChromeHost.kt` — `FabPosition.Center`, `offset(y = 40.dp)`, ancorado à bottom bar —
e obtém o modal por `TransactionsEntry.addTransactionModal()`. As nove telas listadas no proposal
desenham o seu no próprio `Scaffold`, na posição `End` que é o padrão do componente, cada uma
instanciando diretamente o modal do seu `impl`.

O canal para inverter isso já existe e é usado por uma tela só: `ChromeEffect(config)` publica um
`ChromeConfig` no `ChromeStateHolder`, e `DashboardScreen.kt:38` é o único chamador. O
`ChromeConfig` tem dois booleanos e é o `targetState` de um `updateTransition` na casca.

Três restrições valem para tudo o que segue, e todas foram verificadas:

1. **Não há componente pronto.** O `org.jetbrains.compose.material3:material3:1.9.0` que o Compose
   Multiplatform 1.10.1 resolve exporta `FloatingActionButton` e `ExtendedFloatingActionButton`, e
   nada mais — `FloatingActionButtonMenu` e `ToggleFloatingActionButton` do M3 Expressive não
   chegaram ao port multiplataforma.
2. **`:core:designsystem` não pode nomear feature** (`navigation`, cenário "Design system
   inspecionado"). O componente recebe dados, não conhece ações de domínio.
3. **A casca não pode enumerar features.** O catálogo concreto vive em `feature:shell:impl`, mas
   um catálogo de *ações* ali reintroduziria na casca a decisão que pertence à feature — e não
   funcionaria para a ação de categorias, que depende do filtro em vigor.

## Goals / Non-Goals

**Goals:**
- Um componente de botão de ação, um dono da posição, uma regra de visibilidade.
- A ação primária de cada tela permanece a um toque, e é a mesma de hoje.
- A decisão sobre quais ações existem permanece na feature dona.
- A troca de tela nunca exibe as ações da tela anterior, em nenhum frame.

**Non-Goals:**
- **Rever o repertório de ações.** O que cada tela oferece é o que ela já oferece, mais as quatro
  entradas de menu nomeadas no proposal. Ampliar isso é outra conversa.
- **Corrigir a folga inferior das listas.** O botão continua sobre a última linha, e
  `issues/the-fab-covers-the-last-rows-figure-when-the-list-reaches-the-bottom.md` continua aberto.
  Esta mudança não piora o caso e concentra o botão num dono só, o que torna a correção posterior
  possível de um lugar — mas fazê-la aqui misturaria dois assuntos.
- **Trocar o padrão de interação por um bottom sheet de ações.** O menu ancorado ao botão é o que
  preserva o toque único da primária.
- **Rever a bottom bar.** Ela continua restrita aos `primaryTab`.

## Decisions

### D1 — As ações viajam por um canal próprio, e não dentro do `ChromeConfig`

`ChromeConfig` é comparado por igualdade estrutural: ele é o `targetState` do `updateTransition`
que anima a chrome. Uma `List<ChromeAction>` com lambdas dentro dele nunca seria igual a si mesma
entre recomposições da tela, e a transição reiniciaria a cada frame.

`ChromeController` ganha um segundo verbo (`setActions`), e `ChromeStateHolder` guarda as ações num
`mutableStateOf` separado, lido diretamente pelo slot do botão. `ChromeConfig` fica com um campo só
e continua animando o seletor.

*Alternativa considerada:* dar às ações uma chave estável e comparar por ela. Rejeitada — resolve a
igualdade mas mantém dois assuntos com ciclos de vida diferentes no mesmo tipo, e o próximo campo
adicionado ao `ChromeConfig` reintroduz o problema.

### D2 — Toda publicação carrega a identidade do destino que a originou

Esta é a decisão que resolve o risco principal. `ChromeEffect` publica em `SideEffect`, que roda
depois da composição da tela; entre a composição da tela nova e a publicação dela, o slot do botão
pode compor com o que a tela anterior deixou.

Em vez de disputar ordem de composição, as ações são publicadas **junto com a identidade do
`NavBackStackEntry`** que as publicou, e a casca só desenha as ações cuja identidade bate com o
destino corrente. Um estado defasado é descartado por comparação, e não por sorte de ordenação: no
frame intermediário o botão fica sem ações — que é o estado correto para uma tela que ainda não
disse o que oferece — em vez de exibir as ações erradas.

*Alternativa considerada:* publicar durante a composição em vez de em `SideEffect`. Rejeitada como
mecanismo principal — escrever estado no corpo de um composable é o efeito colateral que o modelo
de composição não garante, e o resultado dependeria da ordem em que o `Scaffold` compõe os seus
slots, que não é contratual. A identidade do destino torna a correção independente disso.

### D3 — A posição do botão acompanha o seletor

Com a bottom bar visível, o botão é central e ancorado a ela; sem ela, fica no canto; em janela
larga, é o `header` da rail. Isso é **exatamente onde cada um dos dez botões já está hoje** — a
casca já usa `FabPosition.Center` nas duas abas, e as nove telas já usam o canto. A consolidação
não move nenhum botão de lugar.

*Alternativas consideradas:* posição fixa ao centro em todas as telas — o botão central é uma
convenção do FAB ancorado a uma barra, e nas nove telas sem barra ele ficaria solto. Posição fixa
no canto — muda de lugar justamente nas duas abas mais usadas, onde há memória muscular. Nenhuma
das duas compra nada que a regra acima não dê.

### D4 — O componente é nosso, em `:core:designsystem`

Um botão com corpo (ação primária) e controle de expansão, mais a lista de ações secundárias
rotuladas e o scrim. Recebe uma lista de dados — rótulo, ícone, identidade de teste e o que
executar — e não conhece feature alguma.

A forma é derivada do tamanho da lista: zero não desenha, um desenha o botão simples sem controle
de expansão, dois ou mais desenham as duas áreas. A tela não declara a forma.

*Alternativa considerada:* esperar o `FloatingActionButtonMenu` chegar ao Compose Multiplatform.
Rejeitada — não há data, e a mudança não depende dele. Quando chegar, o componente daqui pode
passar a delegar sem que nenhuma tela seja tocada, porque nenhuma tela conhece o desenho.

### D5 — O botão emite a identidade de teste que a tela declarou

Cada ação carrega o seu `testTag`. O botão da casca emite o da ação primária da tela em foco, e
cada item do menu emite o seu. Os quatro ids que os subflows Maestro usam hoje — `accounts_add`,
`credit_cards_add`, `categories_add`, `add_transaction_fab` — continuam existindo, agora declarados
pela tela em vez de pelo componente, e **nenhum subflow existente ganha um toque a mais**.

*Alternativa considerada:* um id único do tipo `chrome_fab`. Rejeitada — quebraria os seis subflows
sem necessidade, e tornaria o alvo do teste dependente de qual tela está em foco.

### D6 — A pré-seleção da transação é um tipo, e não dois nullables

`addTransactionModal()` passa a aceitar a origem em foco. Dois parâmetros nullables — um para conta
e outro para cartão — admitiriam o estado "os dois ao mesmo tempo", que não significa nada; o
projeto já rejeita essa forma explicitamente no `CreditCardsEntry` e no
`TransferBetweenAccountsModal`.

A origem é um tipo selado declarado em `feature/transactions/api`, com um caso por espécie de
origem, e o parâmetro é opcional: quem não tem contexto chama sem ele. `accounts:impl` e
`creditcards:impl` podem depender de `transactions:api` — é a regra (4) da arquitetura.

### D7 — A transferência ganha um terceiro construtor, e não um parâmetro nullable

`TransferBetweenAccountsModal` tem hoje um construtor privado e dois públicos, precisamente para
que nenhum chamador possa montar um estado sem significado. O terceiro modo segue a mesma forma:
um construtor sem argumentos, para registrar sem pré-seleção. O `initialSourceAccount` do ViewModel
passa a ser nullable, e a linha que hoje resolve a origem corrente deixa de cair em
`accounts.firstOrNull()` — sem pré-seleção, a origem fica vazia até o usuário escolher.

O KDoc que hoje afirma que a operação "nasce apontando para a conta de onde o dinheiro sai" é
reescrito para descrever os três modos. Ele descreve o estado atual, não a história.

### D8 — Categorias mantém a primária dependente do filtro

A ação primária continua abrindo o formulário com o tipo que o filtro corrente indica, como
`CategoriesScreen.kt:138` já faz, e o menu oferece o outro tipo. É o caso que prova por que as
ações são publicadas pela tela: nenhum catálogo por rota conseguiria dizer isso.

## Risks / Trade-offs

- **O menu na rail cai sobre o conteúdo** → Em janela larga o botão é o `header` da rail, no topo,
  e o menu abre ao lado, cobrindo o título da tela e a primeira linha da lista enquanto estiver
  aberto. É transitório e dispensável com um toque fora, mas é real. Mitigação possível se
  incomodar: deslocar o menu verticalmente. Não resolvido aqui.
- **O padrão fica pouco descoberto** → O controle de expansão existe em três das onze telas, e em
  nenhuma das duas abas principais. Quem usa o app pelo Dashboard e por Transações não encontra o
  menu. É consequência aceita do escopo: o valor desta mudança é a consolidação, e o menu é o que
  ela viabiliza, não o que a justifica.
- **O botão desenhado no overlay de transição** → `shared-element-transitions` exige que o botão
  seja declarado no overlay do escopo, acima da barra. O menu e o scrim são parte do botão e
  precisam da mesma declaração, ou aparecerão sob a bottom bar durante uma transição.
- **Onze telas tocadas de uma vez** → A mudança é mecânica em nove delas (remover o
  `floatingActionButton`, publicar a mesma ação), mas é ampla. Mitigação: a ordem de implementação
  abaixo mantém o app funcionando a cada passo.
- **Regressão silenciosa de visibilidade** → Remover `isFloatingActionButtonVisible` transfere a
  decisão para "publicou ações?". Uma tela que esqueça de publicar perde o botão sem erro de
  compilação. Mitigação: teste de composição por tela, verificando as ações publicadas.

## Migration Plan

1. **Componente** em `:core:designsystem`, com as três formas e o scrim — sem nenhum consumidor.
2. **Canal** em `feature:shell:api` (`setActions`, a identidade do destino) e a implementação no
   `ChromeStateHolder`, mantendo `isFloatingActionButtonVisible` ainda em pé. Nada muda na tela.
3. **Casca** passa a desenhar o novo componente a partir das ações publicadas, com Dashboard e
   Transações publicando a ação que a casca hoje assume por elas. A casca deixa de injetar
   `TransactionsEntry`. A partir daqui a flag não tem mais leitor, e é removida.
4. **As nove telas**, uma a uma: remover o `floatingActionButton` do `Scaffold` e publicar a mesma
   ação com o mesmo `testTag`. Cada tela é um passo verificável isoladamente.
5. **As ações novas**: o terceiro modo da transferência, a pré-seleção da transação, e as quatro
   entradas de menu.
6. **Maestro**: rodar a suíte no dispositivo conforme `.maestro/README.md` §2. Os subflows
   existentes devem passar sem edição — se algum precisar de um toque a mais, o passo 4 divergiu
   do que foi decidido.

Reversão: até o passo 3 a mudança é aditiva. Depois dele, reverter significa devolver a flag e o
`floatingActionButton` de cada tela — mecânico, mas por tela.

## Open Questions

- **A ação de transação em Contas pré-seleciona a conta em foco, ou a tela toda?** A rota é
  `AccountsRoute(accountId)`, e a tela pode estar sem conta aberta. Sem conta em foco a ação abre
  sem pré-seleção, o que é coerente com D6 — mas vale confirmar se ela deve mesmo aparecer nesse
  caso, ou só quando há uma conta aberta.
- **O menu fecha ao navegar?** Um item que abre modal fecha o menu junto. Nenhuma ação prevista
  navega, mas a regra deveria estar dita antes que a primeira apareça.
