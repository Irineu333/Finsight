# Design — redesign-budgets-screen

## Contexto

As decisões abaixo saíram de uma prancha de layout que comparou o card atual (~232 dp) com três
formas de linha densa e três candidatos a número principal, desenhados em escala real
(1 dp = 1,25 px, viewport 360 dp) e nas cores de `budgetProgressColor` calculadas termo a
termo. Cada decisão registra aqui o que foi trocado por quê, **e contra o que** — as opções
recusadas estão nomeadas junto de cada uma, para que discordar de uma delas mais tarde não
exija refazer a comparação do zero.

A prancha está preservada em **`assets/layout-plate.html`**, abrível direto no navegador: o
desenho final com uma linha para cada estado que o domínio produz, a anatomia das suas sete
peças e o registro das nove decisões com o que foi descartado em cada uma. Ela é um **registro
de decisão, e não uma especificação** — onde ela e a spec divergirem,
`specs/budget-list-row/spec.md` é quem manda. As suas cores são lidas de `Color.kt` e
`Theme.kt`, de modo que ela envelhece junto com o tema em vez de fingir permanência.

## Decisões

### D1 — O progresso é um anel em torno do ícone, e não uma barra

**Escolhido:** anel de ~36 dp com traço de ~3 dp, envolvendo o chip da categoria.

O anel ocupa a altura do chip que a linha já teria de ter. É essa propriedade — e não a
estética — que torna o resto possível: com o progresso custando **zero altura adicional**, os
dois slots de texto da grade 2×2 ficaram livres para o teto e as categorias. Uma barra abaixo
do nome consumiria um deles e a linha subiria para ~64 dp.

**O que se perde, declaradamente.** Arco é uma codificação visual pior que comprimento: barras
alinhadas no mesmo eixo se comparam de relance, anéis não. Numa lista longa, comparar
orçamentos entre si fica mais difícil do que ficaria com a barra. A **ordenação por progresso**
(D6) é a compensação deliberada disso: a lista passa a estar *pré-comparada*, e o trabalho que
o olho faria varrendo comprimentos é feito pela ordem.

**O segundo custo, e como foi coberto.** Um anel satura ao completar a volta: cheio, ele não
distingue 100% de 300%. Isso deixaria o estado mais grave da linha sem representação — e é a
razão de o estouro passar a ser afirmado por glifo e por aritmética (D4), e não pelo
indicador.

### D2 — O número principal é o teto, e o painel não segue a mesma regra

A tela de orçamentos é onde um orçamento é criado, editado e apagado. O teto é a **definição**
do orçamento; o gasto é o acompanhamento dele, e o acompanhamento tem superfície própria no
painel. Essa divisão é deliberada e está registrada na spec: `BudgetProgressCard` continua com
o par gasto/limite, e **não** deve ser alinhado a esta change.

Uma segunda propriedade decidiu o empate: o teto é a única figura da linha **imune à
consolidação**. Foi digitado, na moeda escolhida na criação (`currency-consolidation`), e nada
nele depende de taxa. Com ele no lugar de destaque, a linha nunca fica sem um número íntegro.

**Três candidatos foram recusados**, todos figuras de acompanhamento e não de definição: o
**par gasto/limite**, que é o que o painel já faz e repete a proporção que o anel desenha; o
**restante** — "faltam R$ 240" —, a mais acionável das três, mas que deixa de existir quando o
gasto tem parcela não precificada (`remainingAmount` é nulo) e leva a linha a ficar sem figura
de referência; e o **percentual**, que é o indicador escrito por extenso, some junto com ele
e, não sendo dinheiro, faria a linha deixar de falar de dinheiro.

### D3 — Categorias como ícones empilhados, sem tint

Ícones foram escolhidos sobre nomes por uma razão de layout que só apareceu ao desenhar:
a pilha tem **largura constante**, então o lado esquerdo da linha não disputa espaço com o
lado direito. Com nomes, título e categorias truncariam em linhas diferentes ao mesmo tempo.

**A ausência de tint não é escolha estética, é correção.** `categoryDisplayColor`
(`core/ui/.../CategoryColor.kt:39`) responde por `Category.Type`, não por categoria:
`EXPENSE` → `Expense` (#EF4444). Como `budget-composition` restringe orçamentos a categorias
de despesa, **toda** categoria de **todo** orçamento sairia na mesma cor — a mesma que o anel
usa para significar *estourado*. Um orçamento a 15% do teto exibiria um anel verde ao lado de
três marcas vermelhas.

O glifo continua distinguindo carrinho de xícara de garfo, que era o trabalho dos ícones. Na
linha, **a cor tem um dono único: o progresso**.

**Não escolhido, e por quê:** cor por categoria. Exigiria `Category` ganhar cor como estado
primário, com migração, escolha no formulário, valor padrão para as existentes e um dono da
regra — e atravessaria toda tela que renderiza categoria. É change própria e está registrada
como possibilidade, fora deste escopo.

### D4 — O estouro é dito três vezes, e nenhuma delas é a cor

Com o teto no lugar de destaque e o anel saturando, nada na linha afirmaria o estouro. Ele
passa a ser dito por:

1. **aritmética** — o gasto impresso acima do teto;
2. **glifo** — com `contentDescription` que o nomeia;
3. cor — que apenas confirma.

Os dois primeiros são independentes e nenhum é cor, o que satisfaz a regra da casa sem
depender do indicador.

### D5 — O aviso de câmbio é da lista, não da linha

O `ConsolidationBadge` sai da linha. A causa de uma figura irresolvível é **global** — falta
taxa para uma moeda —, não propriedade de um orçamento, e a mesma explicação serve a todas as
linhas afetadas. Um badge por linha custa largura em toda linha para explicar um caso raro.

**Diferença deliberada perante `recurring-list-row`,** que exige que a linha afirme a causa
junto da marca. Lá a causa é *conta removida* — um estado daquele template, permanente e
acionável naquela linha. Aqui é *falta taxa*, um estado do acervo, comum a todas. A divergência
é de natureza da causa, não de rigor.

**Decidido: nasce um componente irmão** — `ConsolidationListNotice`, ao lado de
`ConsolidationBadge` em `core/designsystem`. A segunda opção estava condicionada a o
`ConsolidationNotice` que o badge deriva servir a um conjunto sem reescrita, e ele não serve:
o badge decide *sozinho* se aparece e em qual dos três níveis, e a faixa quer exatamente um
deles. Uma figura convertida continua sendo um número só, e uma lista não deve uma faixa
permanente a ela — o que ganha uma linha inteira no topo é o único nível em que a superfície
deixou de fazer parte do seu trabalho. Então quem chama declara a condição e o componente a
enuncia, enquanto o badge segue graduando os três níveis onde uma figura sozinha os carrega.

### D6 — A ordenação acontece no ViewModel, não no DAO

Por progresso decrescente. Não pode sair do `BudgetDao`: o progresso não é propriedade do
orçamento armazenado, é resultado de uma leitura do razão reduzida à moeda do limite. Ordena-se
depois de `CalculateBudgetProgressUseCase`, no `combine` de `BudgetsViewModel`.

Progresso nulo (gasto irresolvível) vai ao fim, e não ao início: `null` ordenado como zero
poria no topo da lista, junto do que está tranquilo, aquilo sobre o que nada se sabe.

**Seções por estado ficam fora.** Um cabeçalho custa altura fixa; com o número de orçamentos
que um usuário mantém, três cabeçalhos organizam menos do que consomem. A ordenação entrega a
mesma hierarquia por zero dp. Se a base crescer muito, seções voltam a fazer sentido — e a
ordenação continua sendo a base delas.

### D7 — O teto derivado se declara com o vocabulário existente

`Icons.Outlined.Autorenew` já significa *recorrência* em `RecurringScreen`, no painel e no
formulário de transação. Reusá-lo custa nada e não ensina um símbolo novo.

Cor: `Primary1`. **Não** âmbar — um teto derivado é normal, não um aviso; e **não** `Info`,
que no app significa *editar* (o botão do próprio detalhe de orçamento).

A declaração diz *que* se renova, *de quanto* e **de quê**. O terceiro termo entrou depois de
ver a marca desenhada em escala real: "30%" sozinho não distingue 30% do salário de 30% do
aluguel, e uma linha cujo trabalho é discriminar não pode exibir a mesma marca em dois
orçamentos diferentes. O detalhe continua sendo onde a receita base é enunciada por extenso e
navegável; a linha só diz qual é.

O custo é largura, e ele está contido por prioridade declarada: o percentual não trunca, o
nome trunca, e a marca inteira tem teto de largura para não empurrar a identidade para fora.

**A posição é regra, e não arranjo.** A marca fica encostada no valor, com a folga da linha
entre ela e a identidade. Ceder largura e ficar no lugar certo são dois requisitos, e a
primeira implementação atendeu ao primeiro quebrando o segundo: a marca passou a flutuar até
o título, e ali ela afirma que *o nome* se re-deriva. É o tipo de erro que só aparece com o
desenho em mãos, e é por isso que a regra está escrita.

## Decisões tomadas por recomendação, a confirmar

Estas duas foram decididas pelo argumento e não por escolha explícita. São reversíveis sem
tocar no resto do desenho.

1. **Ordenação por progresso decrescente** (D6). A alternativa é manter a ordem de criação, que
   não responde pergunta alguma da tela.
2. **O rótulo "gastos" acompanhando a segunda figura.** Ele desambigua duas figuras monetárias
   empilhadas sem rótulo. `recurring-list-row` desaconselha legenda que nomeie a natureza da
   figura — mas aquela linha exibe **uma** figura, e esta exibe **duas**; a spec nova registra
   a distinção. A alternativa é confiar na hierarquia (grande em cima = teto) e remover o
   rótulo depois da familiaridade.

## Riscos

- **Sopa de ícones.** Chip do orçamento com anel + até três chips de categoria na mesma linha
  de 52 dp. Os mockups sustentam, mas o teste é em aparelho, com ícones reais e nomes reais.
- **Perda de cobertura E2E declarada.** `budgets/lifecycle.yaml` prova hoje que a lista troca
  o texto de `budget_remaining_label` entre "Remaining" e "Exceeded by", *e* que a ficha troca
  a própria tag — duas implementações de uma regra, cada uma capaz de quebrar sem a outra
  (o comentário do fluxo diz isso). Na lista sobra uma. O fluxo passa a asserir o glifo de
  estouro e a relação entre `budget_limit_amount` e `budget_spent_amount`.
- **52 dp com fontes grandes.** A grade 2×2 tem duas linhas de texto à esquerda dentro da
  altura do chip. Com escala de fonte do sistema aumentada, a linha cresce — aceitável, desde
  que cresça igual em toda variante.

## Não-objetivos

- Orçamentos semanais. A linha reserva o lugar do período e não o exibe. **A questão maior
  fica registrada e aberta:** o seletor de mês vive na `TopAppBar` e governa a tela inteira;
  um orçamento semanal dentro de um mês selecionado não tem semana definida, e resolver isso é
  decidir se o período é do item ou da tela.
- *Breakdown* de gasto por categoria no detalhe. Continua sendo a única coisa que a linha
  jamais poderá mostrar, e por isso o melhor candidato a próxima change deste par de telas.
- Cor por categoria (ver D3).
- Qualquer figura que **some orçamentos entre si**: `budget-composition` a proíbe, e é o que
  impede esta tela de ganhar o card de resumo que a de Recorrentes ganhou.
