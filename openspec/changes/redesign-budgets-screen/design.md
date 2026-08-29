# Design — redesign-budgets-screen

## Contexto

As decisões abaixo saíram de uma prancha de layout que comparou o card atual (~232 dp) com três
formas de linha densa e três candidatos a número principal, desenhados em escala real
(1 dp = 1,25 px, viewport 360 dp) e nas cores de `budgetProgressColor` calculadas termo a
termo. Cada decisão registra aqui o que foi trocado por quê, **e contra o que** — as opções
recusadas estão nomeadas junto de cada uma, para que discordar de uma delas mais tarde não
exija refazer a comparação do zero.

A prancha está preservada em **`assets/layout-plate.html`**, abrível direto no navegador: uma
linha para cada estado que o domínio produz, a anatomia das suas peças e o registro das nove
decisões com o que foi descartado em cada uma. Ela é um **registro de decisão, e não uma
especificação** — onde ela e a spec divergirem, `specs/budget-list-row/spec.md` é quem manda.
As suas cores são lidas de `Color.kt` e `Theme.kt`, de modo que ela envelhece junto com o tema
em vez de fingir permanência.

**Ela desenha a linha em que D1–D6 foram decididas, e não a que foi entregue.** Aquela era uma
grade 2×2 de ~52 dp, com o marcador de teto derivado reduzido a `30%` e colado ao título. D7 —
que veio depois, do desenho em escala real e não da prancha — nomeou a receita base e separou
identidade e figuras em dois blocos, e com isso a linha passou a ~62 dp, ~80 dp quando o teto
deriva. Regenerar a prancha custaria mais do que vale: o que ela documenta é **contra o que**
cada decisão foi tomada, e as alternativas recusadas não mudaram. Para o que a linha é hoje,
a spec e `BudgetCard.kt` são as fontes.

## Decisões

### D1 — O progresso é um anel em torno do ícone, e não uma barra

**Escolhido:** anel de ~36 dp com traço de ~3 dp, envolvendo o chip da categoria.

O anel ocupa a altura do chip que a linha já teria de ter. É essa propriedade — e não a
estética — que torna o resto possível: com o progresso custando **zero altura adicional**, as
duas linhas de texto de cada bloco ficaram livres para o teto, o gasto, o título e as
categorias. Uma barra abaixo do nome consumiria uma delas, e a consumiria em *toda* linha —
inclusive nas que nada têm a dizer além do teto.

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
a pilha tem **largura limitada** — três chips e um excedente contado —, então o número de
categorias de um orçamento não decide a largura de nada. Com nomes, título e categorias
truncariam em linhas diferentes ao mesmo tempo.

**Limitada, e não fixa**, e a distinção passou a importar com D7. Reservar a largura dos três
chips em toda linha faria todo orçamento pagar pelo maior deles; o que precisa ser excluído é
só o caso inverso, o de muitas categorias apertando a linha. Na linha entregue nem isso chega
a ser disputa: a pilha tem uma linha própria sob o título, e a coluna das figuras é medida
antes da identidade — de modo que a razão da regra é cumprida duas vezes, e por vias
independentes.

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

**A marca é a primeira das figuras, acima do teto** — e a linha deixou de ser uma grade pareada
para virar **dois blocos**: identidade (chip, título, categorias) de um lado, figuras (marca,
teto, gasto) do outro. Nada de um lado corresponde a nada em particular do outro, então nada é
alinhado linha a linha; o bloco das figuras cresce uma linha quando o teto é derivado e a
identidade não é rearranjada por isso.

Esse desenho saiu de três tentativas erradas, todas de pôr a marca na linha do teto. Dividindo
uma linha com identidade **e** teto, ela ou esmagava o título (largura fixa), ou flutuava até
o título ao sobrar espaço — e ali afirma que *o nome* se re-deriva. Em linha cheia própria,
empurrava título e categorias para baixo. A raiz é sempre a mesma: coisas com direitos
diferentes disputando uma linha só.

Os dois blocos se centram verticalmente um contra o outro, e o chip acompanha a identidade: o
chip é da identidade, e os dois são lidos juntos.

**O preço é altura, e só para quem deriva:** ~62 dp com teto digitado, ~80 dp com teto derivado.
Duas alturas na lista, decididas pelo tipo do teto e não pelo conteúdo, o que mantém a regra
legível.

O título passou a ler no tamanho do teto, separado dele só pelo peso: em corpo menor ele lia
como legenda do próprio orçamento.

## Decisões tomadas por recomendação

Estas duas foram decididas pelo argumento e não por escolha explícita, e ambas estão
implementadas. Ficam registradas à parte porque continuam **reversíveis sem tocar no resto do
desenho** — cada uma tem a sua alternativa nomeada, e nenhuma delas exige refazer nada.

1. **Ordenação por progresso decrescente** (D6). A alternativa é manter a ordem de criação, que
   não responde pergunta alguma da tela.
2. **O rótulo "gastos" acompanhando a segunda figura.** Ele desambigua duas figuras monetárias
   empilhadas sem rótulo. `recurring-list-row` desaconselha legenda que nomeie a natureza da
   figura — mas aquela linha exibe **uma** figura, e esta exibe **duas**; a spec nova registra
   a distinção. A alternativa é confiar na hierarquia (grande em cima = teto) e remover o
   rótulo depois da familiaridade.

## Riscos

Os dois primeiros eram riscos de desenho e foram fechados em aparelho; o registro está no
corpo dos commits `649bfc5bd`, `287ff5018`, `6812a1c02` e `e050894f4` — AVD `finsight_e2e`
(API 36, 1080×2400 @420, en-US, `nokeys`), nos dois temas e em escala de fonte 1,0 e 1,3.
O terceiro é o que a change aceita pagar e não fecha.

- **Sopa de ícones** — *fechado*. Chip do orçamento com anel + até três chips de categoria na
  mesma linha. Sustentou com ícones e nomes reais; o que a verificação mudou foi outra coisa —
  o título passou a ler no tamanho do teto, porque em corpo menor lia como legenda do próprio
  orçamento.
- **Altura com fontes grandes** — *fechado*. A identidade tem duas linhas de texto dentro da
  altura do chip, e as figuras têm duas ou três. Em escala 1,3 a linha cresce, e cresce igual
  em toda variante: quem governa a altura é a pilha das figuras, cujo número de linhas depende
  só do tipo do teto. O nome da receita base é o que trunca primeiro, sob o seu cap de largura,
  e o título segue inteiro.
- **Perda de cobertura E2E declarada** — *aceita*. `budgets/lifecycle.yaml` provava que a
  lista trocava o texto de `budget_remaining_label` entre "Remaining" e "Exceeded by", *e* que
  a ficha trocava a própria tag — duas implementações de uma regra, cada uma capaz de quebrar
  sem a outra (o comentário do fluxo dizia isso). Na lista sobra uma. O fluxo passou a asserir
  o glifo de estouro e a relação entre `budget_limit_amount` e `budget_spent_amount`, e o
  comentário foi reescrito para declarar a cobertura que ficou.

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
