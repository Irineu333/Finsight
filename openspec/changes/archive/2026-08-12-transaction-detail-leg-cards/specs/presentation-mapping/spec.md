## MODIFIED Requirements

### Requirement: A escolha da perna neutra tem um dono

Quando um mapeamento for solicitado sem perspectiva, a perna pela qual a transação é lida SHALL ser a que o domínio já define como perna primária, e o mapper MUST NOT reimplementar esse critério. Duas definições da mesma escolha podem divergir sem que nada falhe — a lista passa a olhar uma perna e o detalhe outra, para a mesma transação.

O critério que define a perna primária MUST NOT comparar valores de moedas diferentes. Escolher "a de menor valor" entre pernas monetárias de moedas distintas compara grandezas incomparáveis e elege a ponta pelo número maior em vez de pelo sentido do movimento — uma transferência de R$ 550 para US$ 100 elegeria uma ponta ou a outra conforme o câmbio, não conforme o dinheiro ter saído ou entrado.

A perna primária SHALL ser a perna monetária de **valor negativo** — aquela que o dinheiro deixou. Sendo toda transação balanceada por moeda e tendo no máximo duas pernas monetárias, o critério é total e independe de moeda. Uma transação sem perna monetária negativa — uma compra em cartão, cuja única perna monetária é o passivo — SHALL continuar sendo lida pela perna que já é lida hoje, sem regra nova.

Este requisito governa o mapeamento **de uma perna**. Uma superfície que exibe todas as pernas monetárias não solicita esse mapeamento e não tem perna neutra a escolher; ela permanece consumidora da definição de perna primária apenas para **ordenar** o que exibe, e MUST NOT reimplementar o critério para isso.

#### Scenario: Lista sem perspectiva e detalhe concordam sobre a perna
- **WHEN** a mesma transação é exibida em duas listas sem perspectiva
- **THEN** ambas leem a mesma perna, por consumirem a mesma definição de perna primária

#### Scenario: Perna primária de uma transferência entre moedas
- **WHEN** uma transferência de R$ 550,00 para US$ 100,00 é exibida numa lista sem perspectiva
- **THEN** a perna lida é a da conta em reais, de onde o dinheiro saiu, e a escolha não depende do câmbio aplicado

#### Scenario: Critério não compara moedas
- **WHEN** a definição de perna primária é inspecionada
- **THEN** ela não compara valores de pernas em moedas diferentes

#### Scenario: A superfície de operação consome a definição para ordenar
- **WHEN** o detalhe de uma transferência entre moedas ordena os seus cards
- **THEN** o primeiro é o da perna primária, pela mesma definição que as listas consomem, e nenhuma perna é eleita como "a" perna lida

### Requirement: Uma tela declara a perspectiva que tem

Uma superfície que apresenta transações sob **uma** conta ou **um** cartão SHALL declarar essa
perspectiva ao mapear, e MUST NOT deixar que a perna lida seja escolhida pelo critério de
ausência de perspectiva. Uma superfície que não tem perspectiva única — uma lista de tudo, ou
um recorte sobre várias contas — MUST NOT inventar uma: a ausência é a resposta correta, e não
uma omissão.

Dentro de uma mesma superfície, a perna lida SHALL ter uma única definição. Filtro, item e
detalhe MUST NOT derivá-la cada um por conta própria: duas definições podem discordar, e o
filtro passa a devolver uma transação que o item apresenta na direção oposta.

Uma superfície que exibe **todas** as pernas monetárias de uma operação não lê perna alguma, e
por isso MUST NOT receber perspectiva. A concordância exigida acima é satisfeita por
continência e não por coincidência: qualquer que seja a perna que a lista leu, ela está entre
as que o detalhe exibe. Receber uma perspectiva que não usa é pior do que não recebê-la — é um
argumento que sugere um efeito que não existe.

#### Scenario: Extrato de fatura lê pela perna do cartão
- **WHEN** o pagamento de uma fatura é exibido no extrato dessa fatura
- **THEN** ele é lido pela perna do cartão, como dinheiro que entra, e não pela perna da conta de onde saiu

#### Scenario: Filtro e item concordam sobre a perna
- **WHEN** uma lista com perspectiva é filtrada por direção
- **THEN** a direção que o filtro aplica é a mesma que o item exibe, por virem da mesma definição

#### Scenario: Recorte sobre várias contas não tem perspectiva
- **WHEN** um relatório apresenta transações de várias contas
- **THEN** o mapeamento é feito sem perspectiva, porque não há uma única conta de cujo ponto de vista ler

#### Scenario: O detalhe aberto do extrato de fatura não contradiz a lista
- **WHEN** um pagamento é exibido no extrato da fatura como dinheiro que entra e o seu detalhe é aberto
- **THEN** o detalhe exibe as duas pernas, entre elas a do cartão que a lista leu, e não apresenta a operação como despesa

#### Scenario: A superfície de operação não recebe perspectiva
- **WHEN** a assinatura do ponto de entrada do detalhe de uma transação é inspecionada
- **THEN** ela não declara parâmetro de perspectiva

### Requirement: Qual das duas pontas de uma operação cruzada denomina a figura

Uma operação que atravessa moedas tem **duas** figuras exatas, ambas do razão: o que saiu de uma ponta e o que entrou na outra. Exibida sem perspectiva **e em uma única figura**, ela SHALL ser denominada pela ponta que já estiver na moeda base, quando houver uma. Onde nenhuma das pontas estiver na base, a leitura SHALL permanecer a que já era — a perna de saída — e MUST NOT haver conversão.

A moeda base aqui **não denomina figura alguma**: ela apenas escolhe entre dois valores que o razão já respondeu, nenhum deles convertido e nenhum deles aproximado. Isso é distinto — e MUST NOT ser confundido com — usar a base como recurso para uma figura cuja moeda é conhecível, que `money-display` proíbe. Converter na ausência de uma ponta na base compraria uma moeda que ninguém pediu ao preço de uma taxa que pode não existir.

Uma superfície que declara perspectiva MUST NOT aplicar esta escolha: a figura dela é a linha daquela conta, na moeda daquela conta, qualquer que seja a base.

Uma superfície que exibe as **duas** pontas tampouco a aplica, e MUST NOT ler a moeda base: o desempate existe para quem precisa dizer um número só, e quem diz os dois não tem o que desempatar. O alcance da preferência de moeda base SHALL encolher junto — uma superfície que deixa de desempatar SHALL deixar de nomeá-la.

A escolha SHALL ter um dono único, consumido por toda superfície sem perspectiva que exiba uma única figura. Um item de lista e o detalhe que ele abre MUST NOT exibir dinheiro diferente para a mesma operação; um detalhe que exiba **ambas** as figuras satisfaz isso por continência, porque a figura do item está entre as que ele exibe.

A perna que denomina a figura MUST NOT ser confundida com a perna pela qual a transação é **lida**: direção e sinal permanecem na perna primária. Trocar as duas juntas faria um pagamento de fatura se anunciar como receita no instante em que a figura passasse para a perna do passivo.

#### Scenario: Pagamento cruzado é denominado pela ponta na base
- **WHEN** uma fatura de cartão em reais é paga de uma conta em dólar, a moeda base é o real, e a operação é exibida como item de uma lista sem perspectiva
- **THEN** a figura exibida é a da ponta em reais, sem conversão e sem marca de aproximação, e a direção continua sendo despesa

#### Scenario: Sem ponta na base, a leitura não muda
- **WHEN** a mesma operação é exibida como item com moeda base euro
- **THEN** a figura permanece a da conta de origem, em dólar, e nenhuma taxa é aplicada

#### Scenario: A perspectiva prevalece sobre a base
- **WHEN** a mesma operação é listada no extrato da conta em dólar
- **THEN** a figura é a daquela conta, em dólar

#### Scenario: Lista e detalhe não discordam
- **WHEN** a mesma operação cruzada é exibida num item de lista e aberta no detalhe
- **THEN** a figura que a lista exibe está entre as que o detalhe exibe, na mesma moeda e sem conversão

#### Scenario: O detalhe não nomeia a moeda base
- **WHEN** as dependências do detalhe de uma transação são inspecionadas
- **THEN** ele não lê a preferência de moeda base, e o inventário do alcance dessa preferência não o inclui
