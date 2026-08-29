## Why

A tela de Orçamentos gasta **~232 dp por item** para dizer três coisas, duas das quais são a
mesma. O card de `BudgetsScreen.kt:226` é uma pilha de quatro blocos de peso igual, com
`padding(20.dp)` e `spacedBy(16.dp)`: 40 dp de padding, 48 dp de vãos, 40 dp de identidade,
57 dp para o bloco do limite, 43 dp para o par gasto/restante e 8 dp de barra. O resultado
são **2,7 itens por tela**, e "Restante" é a subtração dos dois números impressos logo acima.

A inversão fica evidente ao lado do próprio app: `BudgetProgressCard.kt:93` — o widget do
dashboard, que só quer dar uma amostra — diz o mesmo em **~56 dp**. A superfície dedicada a
orçamentos é a menos eficiente do app para ver orçamentos, por um fator de 4,3.

A hierarquia tipográfica está invertida. O maior número da tela, em 28 sp, é o **limite**; o
gasto, que é o que se move, vem em 20 sp. E o único elemento expressivo — o gradiente
`Success → Warning → Error` de `budgetProgressColor` — ocupa 3% da altura do card, sob 224 dp
de texto cinza. Nada distingue um orçamento estourado de um tranquilo a meio metro de
distância, exceto 8 dp de cor.

A sobreposição com `ViewBudgetModal` é quase total: ícone, título, badge de consolidação,
limite, gasto, restante/excedido e barra estão **nos dois**. O detalhe acrescenta três coisas
— os nomes das categorias, a origem do percentual e as duas ações.

E a lista não tem hierarquia: `BudgetDao.kt:14` é `ORDER BY createdAt ASC`. Um orçamento
estourado aparece onde calhar de ter sido criado.

**O caminho que salvou a tela de Recorrentes não está inteiro disponível aqui.** Aquele
redesign resolveu a densidade com uma linha curta **mais** um card de resumo mensal. A
metade do resumo é proibida nesta tela: `budget-composition` determina que o sistema
*"MUST NOT apresentar soma alguma **entre** orçamentos"*, porque um orçamento é uma lente e
não uma fatia — duas lentes podem medir a mesma despesa, e somá-las seria dupla contagem.
Toda a densidade precisa, portanto, sair da própria linha.

## What Changes

- **A linha passa de ~232 dp para ~52 dp**, em grade 2×2 — o mesmo arranjo de `RecurringCard`,
  e pela mesma razão: com um subtítulo em linha única, a identidade truncaria antes da figura.
  **2,7 itens por tela passam a ~10,8.**

- **O progresso passa a ser um anel em torno do ícone**, ocupando a altura do chip que já
  existia. É isso que libera os dois slots de texto: o progresso deixa de custar altura.

- **O número principal da linha passa a ser o limite cadastrado**, e não o gasto. Esta é uma
  tela de **gestão** — cria, edita e apaga orçamento —, e não de acompanhamento; o teto é a
  definição, e é o que distingue "Transporte de R$ 300" de "Casa de R$ 2.500". O
  acompanhamento continua sendo pergunta do dashboard, que segue com o par gasto/limite.

- **As categorias sobem para a linha, como ícones empilhados**, no lugar da contagem muda
  "3 categorias". Elas são a segunda coisa que discrimina uma linha da vizinha: dois
  orçamentos de mesmo nome só se distinguem pelo que medem. A pilha tem **largura constante**
  e por isso não disputa espaço com a figura, e excedentes viram `+N`.

- **Os ícones de categoria são exibidos sem tint.** `categoryDisplayColor`
  (`CategoryColor.kt:39`) responde por **tipo**, não por categoria — `EXPENSE` é vermelho —, e
  um orçamento só contém categorias de despesa. Tintá-los poria três ícones vermelhos ao lado
  de um anel cuja cor vermelha significa *estourado*, num orçamento que pode estar tranquilo:
  dois vermelhos com significados opostos na mesma linha. Cor por categoria não existe no
  domínio, e criá-la é change própria.

- **O quadrante inferior direito exibe o gasto.** Com o gasto sob o teto, **o estouro fica
  legível por aritmética** — R$ 380 abaixo de R$ 300 é o estouro —, e o glifo e a cor passam
  a confirmar em vez de carregar o estado sozinhos.

- **Saem da linha:** os rótulos "Limite", "Gasto" e "Restante"; a figura do restante e do
  excedido; a contagem de categorias; e o rótulo do recorrente colado ao subtítulo. Restante e
  excedido são deriváveis dos dois números presentes, e nenhum dos dois responde a pergunta
  *o que é este orçamento*.

- **O estouro ganha um glifo com descrição de conteúdo.** Hoje ele é dito pelo rótulo
  "Excedido em", que a linha densa não tem; e um anel cheio pode ser 100% ou 300%. Com o
  limite como herói o glifo deixa de ser opcional — `R$ 300,00` sozinho nada afirma sobre ter
  estourado.

- **Um limite derivado passa a se declarar como tal.** `CalculateBudgetProgressUseCase.kt:103`
  entrega `budget.copy(amount = limit)`: num orçamento `PERCENTAGE` o valor exibido é *30% da
  receita base deste mês*, e chega à UI **indistinguível de um valor digitado**. Enquanto o
  limite era o terceiro dado da tela isso passava; como dado principal, a linha passaria a
  afirmar teto fixo onde não há. Ele ganha o glifo de recorrência que o app já usa
  (`Icons.Outlined.Autorenew`), o percentual e o nome da receita base, num marcador contido —
  o percentual sozinho não distingue 30% do salário de 30% do aluguel.

- **O badge de consolidação sai da linha e a explicação vira um aviso único no topo da lista.**
  A causa é **global** — falta taxa para uma moeda —, não propriedade de um orçamento, e a
  mesma explicação serve a todas as linhas afetadas. Um badge por linha custa largura em toda
  linha para explicar um caso raro.

- **A linha declara-se superfície de gramática própria** no sentido de `money-display`: com
  parcela não precificada ela exibe a marca de ausência, e **não** as partes. Hoje o card tem
  espaço e exibe as partes (invariante 2); a linha de 52 dp não tem, e cai na invariante 3.
  A spec exige que cada superfície **declare** essa limitação e que ela *"MUST NOT ser deixada
  ao layout"* — é o que esta change faz, e é a razão de a declaração morar aqui.

- **A lista passa a ser ordenada por progresso decrescente**, no lugar da ordem de criação. O
  que precisa de ação sobe sozinho, sem custar um dp de cabeçalho, e o gradiente vira uma
  escala contínua de cima para baixo. **Seções por estado ficam fora**: com o número de
  orçamentos que a base tem hoje, três cabeçalhos custam mais altura do que organizam.

- **A linha nasce com o slot do período reservado e vazio.** Nenhum orçamento é semanal hoje —
  não há `BudgetPeriod` no domínio —, mas `R$ 300,00` é um teto folgado por mês e apertado por
  semana, e uma linha sem lugar para o período teria de ser refeita quando ele existir.

- **`ViewBudgetModal` não muda.** Com o adensamento da linha, a redundância morre por
  subtração do lado certo; o detalhe volta a ter conteúdo próprio sem uma linha de código.

**Fora de escopo, por decisão explícita:** cor por categoria (mudança de domínio, com migração
e formulário, que atravessa toda tela que renderiza categoria); o *breakdown* de gasto por
categoria no detalhe; orçamentos semanais e o que eles fazem com o seletor de mês da
`TopAppBar`, que hoje governa a tela inteira.

## Capabilities

### New Capabilities

- `budget-list-row`: o que a linha da lista de orçamentos **afirma** e o que ela delega ao
  detalhe — que ela existe para discriminar um orçamento do seguinte, e não para antecipar a
  ficha; que o seu número principal é o **teto**, porque a tela é de gestão; que as categorias
  são o segundo dado e por que os seus ícones não são tintados; que nenhum estado dela é
  carregado apenas por cor; como um limite derivado se declara; qual é a ordem da lista; e a
  sua classificação como superfície de gramática própria perante `money-display`, com o aviso
  único que a acompanha.

### Modified Capabilities

<!-- Nenhuma.

     `budget-composition` segue intacto e é premissa desta change, não objeto dela: a
     proibição de soma entre orçamentos é o que impede o card de resumo, e nada aqui a toca.

     `money-display` também não muda. Os seus requisitos já preveem as duas superfícies — a
     que exibe as partes e a de gramática própria que exibe a ausência — e já exigem que cada
     uma **declare** a qual pertence. Esta change exerce essa exigência para uma superfície
     nova; ela não altera a regra. A figura da linha igualmente não recebe sinal: são duas
     figuras de item, e nenhuma coluna desta tela fecha em total algum.

     `currency-consolidation` não rege onde o badge vive, apenas que a consolidação parcial
     seja declarada — o que o aviso único faz, para todas as linhas de uma vez. -->

## Impact

- **`feature/budgets/impl`** — nasce `BudgetCard.kt` (a linha, nos moldes de
  `RecurringCard.kt`), com o anel, a pilha de ícones, o glifo de estouro e o marcador de
  limite derivado; `BudgetsScreen` perde `BudgetProgressItem` e ganha o aviso de câmbio como
  primeiro item da lista; `BudgetsViewModel` passa a ordenar por progresso **depois** de
  `CalculateBudgetProgressUseCase` — a ordem não pode sair do DAO, que não conhece o
  progresso.
- **`core/designsystem`** — o aviso de consolidação por lista: hoje `ConsolidationBadge` é um
  `IconButton` por figura, e o aviso é uma linha para um conjunto. Avaliar se nasce ao lado
  dele ou se ele ganha uma segunda forma; a decisão fica no design.
- **`core/resources`** — chaves novas do rótulo do gasto, do `+N` da pilha, do aviso de
  câmbio, das descrições de conteúdo do glifo de estouro e do marcador derivado, e do slot de
  período; nos **dois** idiomas. **Aposentadoria de seis chaves** cujo único consumidor é o
  card que sai: `budgets_limit`, `budgets_spent`, `budgets_remaining`, `budgets_exceeded_by`,
  `budgets_category_singular` e `budgets_category_plural`.
- **`.maestro/flows/budgets/lifecycle.yaml`** — o fluxo assere `budget_remaining_label`
  (a troca "Remaining" ↔ "Exceeded by") e `budget_remaining_amount` em três pontos, e as duas
  tags deixam de existir. As asserções passam para o glifo de estouro e para
  `budget_spent_amount`, que permanece. **Há perda declarada de cobertura:** o comentário do
  próprio fluxo observa que card e ficha implementam a mesma regra por caminhos distintos,
  "cada um capaz de quebrar sem o outro"; com a mudança, na lista sobra uma implementação só.
  `budget_card`, `budget_limit_amount` e todas as `view_budget_*` seguem válidas.
- **Sem mudança de domínio, de razão ou de banco.** Nenhuma migração; nenhuma consulta nova;
  `BudgetProgress` já expõe tudo o que a linha lê.
