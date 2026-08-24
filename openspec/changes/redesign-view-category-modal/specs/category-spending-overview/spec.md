## ADDED Requirements

### Requirement: O detalhe de uma categoria não navega no tempo

O detalhe de uma categoria SHALL informar sobre um período que ele mesmo determina, e
MUST NOT oferecer controle de mês, de período ou de intervalo. Não SHALL existir estado
de período no detalhe, nem comando que o desloque.

O detalhe é uma superfície de leitura curta, não uma tela de análise: navegar o tempo
pertence à lista de lançamentos, que já o faz, e tê-lo nos dois lugares é a mesma decisão
tomada duas vezes.

#### Scenario: Sem controle de período
- **WHEN** o detalhe de uma categoria é aberto
- **THEN** nenhum controle de mês, seta de período ou seletor de intervalo é exibido

#### Scenario: Sem comando de deslocamento
- **WHEN** o detalhe de uma categoria está aberto
- **THEN** não existe comando que avance ou recue o período das figuras exibidas

### Requirement: Toda figura declara a janela sobre a qual fala

Cada figura de dinheiro do detalhe SHALL declarar, em texto junto ao rótulo, o período que
ela cobre. Uma figura cujo período não está dito MUST NOT ser exibida.

O detalhe SHALL informar exatamente três figuras:

1. o total do **mês corrente**;
2. a **média mensal** sobre a janela definida abaixo;
3. o **total** sobre essa mesma janela.

A janela SHALL ser a dos **meses fechados** — o mês corrente não participa dela, porque
está incompleto e puxaria a média para baixo todos os meses. Ela SHALL ter no máximo 12
meses, e SHALL ser encurtada ao número de meses decorridos desde o primeiro lançamento da
categoria quando esse número for menor. O rótulo SHALL declarar o número real de meses, e
MUST NOT dizer 12 quando a janela é menor.

A média SHALL ser o total da janela dividido pelo número de meses da janela, contando os
meses sem lançamento como zero. Como as duas figuras vêm da mesma janela, a relação
`média × meses = total` SHALL valer exatamente, e o usuário SHALL poder conferi-la.

O total SHALL ser o da janela, e MUST NOT ser o histórico completo da categoria. Um número
sem período declarado não responde nem a "é muito?" nem a "desde quando?".

#### Scenario: Janela cheia
- **WHEN** uma categoria tem lançamentos há mais de 12 meses
- **THEN** a média e o total falam de 12 meses fechados, e ambos os rótulos dizem 12

#### Scenario: Janela encurtada por categoria jovem
- **WHEN** o primeiro lançamento de uma categoria ocorreu há 5 meses fechados
- **THEN** a média e o total falam de 5 meses, os rótulos dizem 5, e a média é o total
  dividido por 5

#### Scenario: Mês sem lançamento conta como zero
- **WHEN** a janela de uma categoria contém meses sem nenhum lançamento
- **THEN** esses meses entram no divisor da média, e a média MUST NOT ser calculada apenas
  sobre os meses com movimento

#### Scenario: O mês corrente não entra na janela
- **WHEN** há lançamentos na categoria no mês corrente
- **THEN** eles compõem a figura do mês corrente e MUST NOT entrar no total nem na média
  da janela

#### Scenario: As duas figuras se conferem
- **WHEN** a média e o total da janela são exibidos
- **THEN** multiplicar a média pelo número de meses declarado reproduz o total exibido

### Requirement: O mês corrente se anuncia como parcial

A figura do mês corrente SHALL vir acompanhada de significante textual dizendo que o mês
ainda não terminou, indicando o dia decorrido e o total de dias do mês. Ela MUST NOT ser
apresentada como um mês fechado.

Sem esse anúncio, a figura do mês corrente é lida como comparável às demais, e no dia 3 ela
não é.

#### Scenario: Mês em curso
- **WHEN** o detalhe é aberto no dia 24 de um mês de 31 dias
- **THEN** a figura do mês corrente é acompanhada de texto que informa o dia decorrido e o
  total de dias do mês

#### Scenario: Último dia do mês
- **WHEN** o detalhe é aberto no último dia do mês
- **THEN** a figura continua sendo a do mês corrente e continua anunciada como parcial, pois
  o dia ainda não terminou

### Requirement: A variação compara o mês corrente contra a média, não contra o mês anterior

O detalhe SHALL exibir, junto da figura do mês corrente, a variação percentual dela **em
relação à média** da janela. Ela MUST NOT comparar contra o mês imediatamente anterior.

Um único mês anterior pode ser atípico, e usá-lo como régua transforma o desvio dele no
desvio de todos os meses seguintes. A média é uma base estável, e é sobre ela que a
pergunta "estou gastando mais do que o normal" tem resposta.

A variação MUST NOT existir quando não há resposta. Ela SHALL ser omitida, com significante
textual dizendo por quê, em cada um destes casos:

- a média da janela é zero, porque dividir por zero não é aumento infinito;
- a categoria não tem nenhum mês fechado com lançamento, de modo que não há base;
- as figuras não podem ser postas numa escala comum, porque alguma delas carrega moeda que
  nenhuma taxa alcança.

Um percentual MUST NOT ser exibido como `0%` em nenhum desses casos: zero é uma afirmação.

#### Scenario: Gasto acima da média
- **WHEN** o mês corrente acumula valor superior à média da janela
- **THEN** a variação é exibida como a diferença percentual em relação à média, indicando
  que está acima

#### Scenario: Média zero
- **WHEN** a média da janela é zero
- **THEN** nenhum percentual é exibido, e um texto informa que não há base de comparação

#### Scenario: Categoria criada no mês corrente
- **WHEN** a categoria não possui nenhum mês fechado com lançamento
- **THEN** nenhum percentual é exibido, e um texto informa que ainda não há histórico para
  comparar

#### Scenario: Escala comum indisponível
- **WHEN** o mês corrente ou a janela carrega moeda que nenhuma taxa alcança
- **THEN** nenhum percentual é exibido, e o motivo é dito em texto

### Requirement: A variação não se expressa pelas cores de natureza

A variação SHALL ser expressa por **significante textual**, acompanhada de indicador de
direção. Ela MUST NOT usar as cores que o app reserva a receita e a despesa.

As duas cores já têm significado fixo em toda a interface. Pintar de verde "gastou menos"
numa categoria de despesa faz a mesma cor dizer duas coisas na mesma tela, e o usuário não
tem como saber qual delas está lendo.

#### Scenario: Queda de gasto em categoria de despesa
- **WHEN** o mês corrente de uma categoria de despesa está abaixo da média
- **THEN** a variação é dita em texto com indicador de direção, e MUST NOT ser pintada com
  a cor reservada a receita

#### Scenario: Alta de gasto em categoria de despesa
- **WHEN** o mês corrente de uma categoria de despesa está acima da média
- **THEN** a variação é dita em texto com indicador de direção, e MUST NOT ser pintada com
  a cor reservada a despesa

#### Scenario: A direção sobrevive sem cor
- **WHEN** a variação é exibida
- **THEN** o texto sozinho informa se o mês está acima ou abaixo da média, sem depender de
  cor para ser compreendido

### Requirement: Cada estado da categoria exibe a figura que ainda diz algo

A figura de destaque do detalhe SHALL ser a que ainda responde a alguma pergunta no estado
em que a categoria está.

Para uma categoria **ativa**, o destaque SHALL ser o mês corrente: é o único período sobre
o qual o usuário ainda pode agir.

Para uma categoria **arquivada**, o destaque SHALL ser o **total histórico completo**,
acompanhado do intervalo de datas que ele cobre. Uma categoria arquivada não tem mês
corrente com significado, e exibir o mês dela produz uma figura zerada e uma variação de
-100% que não descrevem nada.

Para uma categoria **sem nenhum lançamento**, o detalhe MUST NOT exibir figura zerada em
destaque. Ele SHALL exibir estado vazio com texto explicando que os lançamentos aparecerão
quando houver, porque um zero em destaque é lido como falha e não como ausência.

#### Scenario: Categoria ativa
- **WHEN** o detalhe de uma categoria não arquivada e com lançamentos é aberto
- **THEN** a figura de destaque é a do mês corrente

#### Scenario: Categoria arquivada
- **WHEN** o detalhe de uma categoria arquivada é aberto
- **THEN** a figura de destaque é o total histórico completo, acompanhado do intervalo de
  datas coberto, e a figura do mês corrente não é exibida em destaque

#### Scenario: Categoria sem lançamento
- **WHEN** o detalhe de uma categoria sem nenhum lançamento é aberto
- **THEN** nenhuma figura zerada é exibida em destaque, e um estado vazio explica a ausência

### Requirement: O detalhe leva aos lançamentos daquela categoria

O detalhe SHALL oferecer comando que abra a lista de lançamentos já recortada por aquela
categoria. O comando SHALL estar visível sem rolagem.

Ele é o que impede que a remoção do controle de período seja perda de função: a pergunta
"quanto gastei nisto em março" continua respondível, na superfície que já sabe respondê-la,
em vez de em duas.

#### Scenario: Abrir os lançamentos da categoria
- **WHEN** o comando é acionado no detalhe de uma categoria
- **THEN** a lista de lançamentos abre com o eixo analítico já recortado por aquela
  categoria, sem que o usuário precise selecioná-la

#### Scenario: O comando é alcançável
- **WHEN** o detalhe de uma categoria é aberto
- **THEN** o comando está visível sem rolagem
