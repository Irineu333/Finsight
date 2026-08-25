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

### Requirement: Nenhuma figura inclui lançamento com data futura

Toda figura do detalhe SHALL cobrir apenas lançamentos com data até o fim do mês corrente.
Um lançamento com data posterior MUST NOT entrar na figura do mês corrente, na janela, na
média, no total, nem no total histórico de uma categoria arquivada.

Lançamentos futuros são estado ordinário: uma compra parcelada registra hoje as parcelas dos
meses seguintes, e a perna nominal de cada uma carrega a dimensão da categoria. Um mês futuro
não é fechado nem é o corrente, e uma média que o incluísse não seria média de período algum.

O mês que determina o encurtamento da janela SHALL ser o do primeiro lançamento **até o fim
do mês corrente**, pela mesma regra.

#### Scenario: Parcelas futuras fora do mês corrente
- **WHEN** uma compra parcelada registra parcelas nos meses seguintes na categoria
- **THEN** a figura do mês corrente contém apenas a parcela deste mês

#### Scenario: Parcelas futuras fora da janela e da média
- **WHEN** a categoria tem lançamentos em meses posteriores ao corrente
- **THEN** eles não entram no total nem na média da janela, e o número de meses declarado não
  os conta

#### Scenario: Histórico de arquivada não alcança o futuro
- **WHEN** o detalhe de uma categoria arquivada com parcelas futuras pendentes é aberto
- **THEN** o total histórico cobre apenas até o fim do mês corrente, e o intervalo de datas
  MUST NOT terminar numa data que ainda não chegou

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

A variação MUST NOT existir quando não há resposta. Ela SHALL ser **omitida por inteiro** —
sem percentual, sem indicador e **sem texto declarando a ausência** — em cada um destes casos:

- a média da janela é zero, porque dividir por zero não é aumento infinito;
- a categoria não tem nenhum mês fechado com lançamento, de modo que não há base;
- as figuras não podem ser postas numa escala comum, porque alguma delas carrega moeda que
  nenhuma taxa alcança.

Um percentual MUST NOT ser exibido como `0%` em nenhum desses casos: zero é uma afirmação. E
uma frase dizendo que não há resposta é uma afirmação também — ocupa a linha que a variação
ocuparia, para informar que ela não existe. A ausência se mostra pela ausência: onde não há
resposta, não há linha.

Quando o mês corrente coincide **exatamente** com a média, a variação existe e é zero — o que
não é nenhum dos casos acima. Ela SHALL ser dita como coincidência com a média, e MUST NOT
afirmar direção alguma: uma compra parcelada gasta o mesmo todo mês, de modo que a igualdade
exata é leitura ordinária e não curiosidade de arredondamento.

#### Scenario: Gasto acima da média
- **WHEN** o mês corrente acumula valor superior à média da janela
- **THEN** a variação é exibida como a diferença percentual em relação à média, indicando
  que está acima

#### Scenario: Média zero
- **WHEN** a média da janela é zero
- **THEN** nenhuma variação é exibida, e nenhum texto ocupa o lugar dela

#### Scenario: Categoria criada no mês corrente
- **WHEN** a categoria não possui nenhum mês fechado com lançamento
- **THEN** nenhuma variação é exibida, e nenhum texto ocupa o lugar dela

#### Scenario: Mês exatamente na média
- **WHEN** o mês corrente coincide exatamente com a média da janela
- **THEN** a variação é dita como coincidência com a média, sem seta e sem afirmar que o mês
  está acima ou abaixo dela

#### Scenario: Escala comum indisponível
- **WHEN** o mês corrente ou a janela carrega moeda que nenhuma taxa alcança
- **THEN** nenhuma variação é exibida, e o motivo MUST NOT ser dito em texto

### Requirement: A variação não se expressa pelas cores de natureza

A variação SHALL ser expressa por **significante textual**, acompanhada de indicador de
direção e de cor. Ela MUST NOT usar as cores que o app reserva a receita e a despesa.

As duas cores já têm significado fixo em toda a interface. Pintar de verde "gastou menos"
numa categoria de despesa faz a mesma cor dizer duas coisas na mesma tela, e o usuário não
tem como saber qual delas está lendo.

A proibição é sobre **aquelas duas cores**, e não sobre cor. A variação SHALL usar o par de
severidade do tema — atenção para o mês que corre acima da própria média, informação para o
que corre abaixo —, que sinaliza sem reivindicar natureza nenhuma. Texto, seta e cor SHALL
concordar, de modo que a leitura sobreviva a qualquer um dos três isolado.

#### Scenario: Queda de gasto em categoria de despesa
- **WHEN** o mês corrente de uma categoria de despesa está abaixo da média
- **THEN** a variação é dita em texto com indicador de direção e cor de severidade, e MUST
  NOT ser pintada com a cor reservada a receita

#### Scenario: Alta de gasto em categoria de despesa
- **WHEN** o mês corrente de uma categoria de despesa está acima da média
- **THEN** a variação é dita em texto com indicador de direção e cor de severidade, e MUST
  NOT ser pintada com a cor reservada a despesa

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

O intervalo SHALL ir do primeiro ao **último lançamento** da categoria, e MUST NOT ser
delimitado pela data em que ela foi arquivada. O intervalo é uma afirmação sobre o dinheiro,
e não sobre quando a categoria saiu da lista; arquivar meses depois do último gasto não
acrescenta período nenhum ao histórico. O sistema tampouco registra quando uma categoria foi
arquivada — o fechamento é um booleano —, de modo que a outra leitura não é apenas menos útil:
não existe.

Para uma categoria **sem nenhum lançamento**, o detalhe MUST NOT exibir figura zerada em
destaque — um zero em destaque é lido como falha e não como ausência. Ele MUST NOT exibir
texto no lugar dela tampouco: uma frase dizendo que não há nada é ela própria uma afirmação,
e ocupa a linha que a figura ocuparia para informar que a figura não existe. A ausência se
mostra pela ausência.

#### Scenario: Categoria ativa
- **WHEN** o detalhe de uma categoria não arquivada e com lançamentos é aberto
- **THEN** a figura de destaque é a do mês corrente

#### Scenario: Categoria arquivada
- **WHEN** o detalhe de uma categoria arquivada é aberto
- **THEN** a figura de destaque é o total histórico completo, acompanhado do intervalo de
  datas coberto, e a figura do mês corrente não é exibida em destaque

#### Scenario: O intervalo não é delimitado pelo arquivamento
- **WHEN** uma categoria foi arquivada meses depois do seu último lançamento
- **THEN** o intervalo termina na data daquele último lançamento, e não na do arquivamento

#### Scenario: Categoria sem lançamento
- **WHEN** o detalhe de uma categoria sem nenhum lançamento é aberto
- **THEN** nenhuma figura é exibida, zerada ou não, e nenhum texto explica a ausência

### Requirement: O detalhe leva aos lançamentos daquela categoria

O detalhe SHALL oferecer comando que abra a lista de lançamentos já recortada por aquela
categoria. O comando SHALL estar visível sem rolagem.

Ele é o que impede que a remoção do controle de período seja perda de função: a pergunta
"quanto gastei nisto em março" continua respondível, na superfície que já sabe respondê-la,
em vez de em duas.

O comando MUST NOT ser oferecido quando a categoria não tem lançamento algum: ele abriria uma
lista vazia, e um comando que promete nada a encontrar é oferta falsa. Ele acompanha as
figuras — onde não há figura, não há comando.

#### Scenario: Abrir os lançamentos da categoria
- **WHEN** o comando é acionado no detalhe de uma categoria
- **THEN** a lista de lançamentos abre com o eixo analítico já recortado por aquela
  categoria, sem que o usuário precise selecioná-la

#### Scenario: O comando é alcançável
- **WHEN** o detalhe de uma categoria com lançamentos é aberto
- **THEN** o comando está visível sem rolagem

#### Scenario: Sem lançamento, sem comando
- **WHEN** o detalhe de uma categoria sem nenhum lançamento é aberto
- **THEN** o comando não é oferecido
