## Why

A tela de Recorrentes gasta ~180dp por item para não dizer nada que o detalhe já não diga.
`RecurringCard` é uma ficha de três blocos empilhados dentro de uma lista que precisa de
linhas: dos ~180dp, ~40dp carregam identidade, 40dp são padding (a casa usa 12dp em toda
linha de lista), 32dp são vãos entre blocos e 18dp são a legenda "Valor mensal" mais o seu
vão. A sobreposição com `ViewRecurringModal` é **total** — tipo, valor, dia, status,
conta/cartão e categoria estão os seis lá, rotulados e navegáveis. O resultado são **3 itens
por tela**, e uma lista que já tem duas alturas (o bloco do valor desaparece inteiro quando o
template perdeu a conta que o denominava) sem que nada explique a diferença.

E a tela não tem noção de mês, embora o domínio já seja dono de "tratado × não tratado"
(`GetUnhandledRecurringUseCase`). A consequência é uma descontinuidade: a seção de pendências
do dashboard oferece "ver todas", o usuário chega aqui vindo de uma lista de **pendências**, e
a palavra "pendente" não aparece em lugar nenhum. O que o usuário quer saber ao abrir a tela —
quanto das suas contas fixas já foi lançado e quanto ainda falta — é derivável hoje e não é
respondido por superfície alguma fora do dashboard, cujo componente é desligável.

## What Changes

- **A linha da lista passa de ~180dp para ~64dp**, em grade 2×2: à esquerda o que a
  recorrência **é** e de **onde sai** (ícone da categoria, rótulo, conta ou cartão); à direita
  **quanto** e **quando** (figura e dia). Saem a legenda "Valor mensal", o badge de tipo e o
  badge de arquivada; a direção do lançamento passa a ser um glifo com `contentDescription`,
  e o estado arquivado um glifo antes do rótulo. **3 itens por tela passam a 8.** O chip do
  ícone adota 40dp/raio 8 — a família dos cards analíticos da casa
  (`CategorySpendingCard`, `BudgetProgressCard`), não a de 48dp/raio 12 do
  `PendingRecurringCard` do dashboard, que responde a outra pergunta.

- **Um template sem denominação passa a exibir `***` em vez de omitir a figura.** A altura da
  linha passa a ser constante em toda variante, e a ausência do número passa a ser dita em voz
  alta com o vocabulário que a casa já tem, em vez de dita por ausência.

- **Nasce o card de resumo do mês**, primeiro item da lista, separando **fato** de
  **projeção** em dois blocos rotulados:
  - *lançado neste mês* — despesas e receitas fixas que **já estão no razão**, lidas das
    ocorrências confirmadas do mês pelas transações que elas apontam;
  - *ainda não lançado* — despesas e receitas fixas dos templates **sem ocorrência** no mês,
    via `GetUnhandledRecurringUseCase`, consumido e não reescrito.

- **O card presta contas do que as quatro figuras não conseguem representar.** Um ciclo
  ignorado não é lançamento nem pendência — ele é invisível nas quatro células, e essa
  aritmética está correta. O contador *"8 de 11 tratadas · 1 ignorada"* é o único lugar do
  card onde o terceiro estado é representável, e é também a única leitura livre de moeda que o
  card oferece.

- **O card carrega um seletor de mês próprio e não obedece ao filtro da lista.** Sob o recorte
  de arquivadas, "ainda não lançado" é estruturalmente vazio; sob os recortes de despesa ou
  receita, o card teria de apagar uma das duas linhas — mudar de **forma** enquanto a lista
  muda de **conteúdo**, sem o usuário poder saber qual das duas coisas o seletor fez. A tela
  **não** vira mensal: um template não tem mês, só a ocorrência dele tem.

- **O vazio de recorte deixa de ocupar a tela inteira** e passa a ser um item da própria lista.
  Hoje ele é um ramo exclusivo em `fillMaxSize`, que apagaria o card de resumo justamente
  quando o recorte não tem itens — inclusive no recorte de arquivadas numa base sem
  arquivadas, que é quando o resumo tem mais a dizer.

- **`hasUsableSource` deixa de ser sinalizado só por cor.** Hoje um template cuja conta ou
  cartão foi removido ou arquivado é indicado apenas pela troca de `onSurfaceVariant` por
  `outline` — no mesmo arquivo que, sessenta linhas acima, enuncia que cor sozinha não carrega
  estado. É o estado mais grave da linha, porque o template não consegue postar.

- **`currencyOf(recurring)` passa a ter um dono só.** Hoje são duas implementações idênticas —
  uma `internal` em `feature/recurring/impl`, outra copiada inline em
  `feature/dashboard/impl` porque a primeira não é alcançável. O resumo seria a terceira.

- **A resolução de moeda passa a acontecer uma vez por emissão.** Hoje `RecurringViewModel`
  chama `currencyOf` por item dentro do `combine`, e para um template de cartão isso é um
  `getAccountById` — N consultas por emissão. Somar o resumo sobre a mesma estrutura dobraria
  a conta.

## Capabilities

### New Capabilities

- `recurring-month-overview`: o resumo mensal da tela de recorrentes — quais são as quatro
  figuras e o que separa as duas que são fato das duas que são projeção; de onde cada metade é
  lida e por que a metade do fato não pode sair do template; o que o contador conta e por que
  ele é o único lugar onde um ciclo ignorado é representável; que o seletor de mês governa o
  card e o filtro governa a lista, e que nenhum dos dois governa o outro; o que o card faz com
  um template que nenhuma conta denomina; e a permanência do card quando o recorte da lista
  está vazio. Reconcilia-se com `money-display`: as figuras **não participam de uma soma
  exibida**, e por isso a superfície de resumo daquela capacidade não as rege.

- `recurring-list-row`: o que a linha da lista de recorrentes **afirma** e o que ela delega ao
  detalhe — que ela existe para discriminar duas linhas parecidas, e não para antecipar a
  ficha; que nenhum estado dela é carregado apenas por cor; e o que ela exibe quando o
  template não tem moeda que o denomine.

### Modified Capabilities

<!-- Nenhuma. `money-display` rege a linha e o resumo sem alteração: a proibição de sinal em
     superfície de item já cobre a figura da linha, e o requisito de sinal em superfície de
     resumo se limita, pelos seus próprios termos, à "linha que participa de uma soma
     exibida" — o que nenhuma das quatro figuras faz. `account-lifecycle` também segue
     intacto: ele exige que a arquivada suma das listagens ativas e das pendências e
     permaneça acessível pelo recorte dedicado, e nada disso muda. -->

## Impact

- **`feature/recurring/api`** — nasce o use case dono da leitura do fato (as ocorrências
  confirmadas de um mês e o dinheiro que elas de fato lançaram, por moeda e por natureza),
  ao lado de `GetUnhandledRecurringUseCase`; `IRecurringOccurrenceRepository` ganha o método
  que ele consome; e `currencyOf(recurring)` muda de casa para cá, alcançável por quem
  precisa.
- **`feature/recurring/impl`** — `RecurringScreen` reescreve a linha e ganha o card;
  `RecurringUiState`/`RecurringViewModel` ganham o mês selecionado, o resumo e a resolução de
  moedas por emissão; nasce um `RecurringSummaryFactory` nos moldes de
  `BalanceOverviewFactory`, recebendo o redutor como parâmetro; `RecurringOccurrenceRepository`
  implementa o método novo; `RecurringCurrency.kt` é aposentado em favor da versão na `api`.
- **`feature/dashboard/impl`** — a cópia inline de `currencyOf` é apagada e passa a consumir a
  da `api`. Nenhuma mudança de comportamento.
- **`core/database`** — `RecurringDao` ganha a consulta agregada que junta
  `recurring_occurrences` a `transactions`, `entries` e `accounts`, agrupando por natureza e
  moeda. Lê `transactions` pelo DAO da fachada, como as consultas de guarda já fazem, e
  atravessa a foreign key real de `transactionId` — não a coluna de metadado de agrupamento
  que nenhuma leitura do razão pode consultar. **Sem migração de banco.**
- **`core/resources`** — chaves novas dos dois blocos, dos rótulos das figuras, do contador,
  dos ciclos ignorados, das pendências e da declaração de templates deixados fora da soma; nos
  **dois** idiomas. Aposentadoria de `recurring_card_monthly_amount`, cujo único consumidor é
  a legenda que sai.
- **`feature/recurring/impl/build.gradle.kts`** — passa a declarar `feature:settings:api`. O
  card exibe figura consolidada, e `EveryFigureCanExplainItselfTest` quebra o build para todo
  arquivo de produção que desenha uma figura sem `ConsolidationBadge` e para todo badge sem
  `onSeeRates` — a rota do arquivo de taxas mora naquele módulo.
- **`.maestro`** — as figuras do resumo ganham ids próprios; `recurring_card_amount` continua
  publicado no `Text` da figura da linha, e o seu texto continua **sem sinal**, como as cinco
  asserções existentes esperam.

**Fora de escopo** (registrado, com tarefa própria):

- **Oferecer confirmar e ignorar na linha da lista.** Hoje as duas ações só existem no
  componente desligável do dashboard: `RecurringEntry.confirmRecurringModal` tem um único
  chamador de produção, e `SkipRecurringModal` só é aberto de dentro da confirmação. O card
  fica com o letreiro ("3 pendentes"), não com a porta — uma porta única atrás de um resumo
  repetiria em outro lugar o erro que se está diagnosticando. Esta change é de experiência de
  leitura; a ação é a seguinte.
- **`BuildTransactionError.RecurringMonthLocked` declarado sem produtor.** A regra "uma
  transação recorrente não pode mudar de mês" está escrita, mapeada para string e traduzida
  nos dois idiomas, e nada no app a produz — dois hits em todo o repositório, ambos no próprio
  arquivo. É por isso que o corte do agregado do fato é pela ocorrência (`yearMonth`) e não
  pela data da transação: enquanto a regra estiver morta, uma transação confirmada pode ser
  editada para outro mês, e o contador discordaria do dinheiro.
- **`RecurringItem` carrega `Recurring` como campo**, o que `presentation-mapping` proíbe de um
  modelo de UI. Divergência anterior a esta change, que ela não introduz nem agrava — o modelo
  do resumo carrega figura consolidada e contagens, sem modelo de domínio.
- **Ordenar a lista por dia do mês** em vez de por data de criação. É a ordenação que faria a
  coluna ler como um calendário, e é decisão de produto: um template recém-criado deixaria de
  aparecer no fim da lista.
