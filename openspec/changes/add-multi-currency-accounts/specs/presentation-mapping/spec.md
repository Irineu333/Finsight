## MODIFIED Requirements

### Requirement: A escolha da perna neutra tem um dono

Quando um mapeamento for solicitado sem perspectiva, a perna pela qual a transação é lida SHALL ser a que o domínio já define como perna primária, e o mapper MUST NOT reimplementar esse critério. Duas definições da mesma escolha podem divergir sem que nada falhe — a lista passa a olhar uma perna e o detalhe outra, para a mesma transação.

O critério que define a perna primária MUST NOT comparar valores de moedas diferentes. Escolher "a de menor valor" entre pernas monetárias de moedas distintas compara grandezas incomparáveis e elege a ponta pelo número maior em vez de pelo sentido do movimento — uma transferência de R$ 550 para US$ 100 elegeria uma ponta ou a outra conforme o câmbio, não conforme o dinheiro ter saído ou entrado.

A perna primária SHALL ser a perna monetária de **valor negativo** — aquela que o dinheiro deixou. Sendo toda transação balanceada por moeda e tendo no máximo duas pernas monetárias, o critério é total e independe de moeda. Uma transação sem perna monetária negativa — uma compra em cartão, cuja única perna monetária é o passivo creditado — SHALL continuar sendo lida pela perna que já é lida hoje, sem regra nova.

#### Scenario: Lista sem perspectiva e detalhe concordam sobre a perna
- **WHEN** a mesma transação é exibida em uma lista sem perspectiva e aberta no detalhe
- **THEN** ambas leem a mesma perna, por consumirem a mesma definição de perna primária

#### Scenario: Perna primária de uma transferência entre moedas
- **WHEN** uma transferência de R$ 550,00 para US$ 100,00 é exibida numa lista sem perspectiva
- **THEN** a perna lida é a da conta em reais, de onde o dinheiro saiu, e a escolha não depende do câmbio aplicado

#### Scenario: Critério não compara moedas
- **WHEN** a definição de perna primária é inspecionada
- **THEN** ela não compara valores de pernas em moedas diferentes

## ADDED Requirements

### Requirement: Qual das duas pontas de uma operação cruzada denomina a figura

Uma operação que atravessa moedas tem **duas** figuras exatas, ambas do razão: o que saiu de uma ponta e o que entrou na outra. Exibida sem perspectiva, ela SHALL ser denominada pela ponta que já estiver na moeda base, quando houver uma. Onde nenhuma das pontas estiver na base, a leitura SHALL permanecer a que já era — a perna de saída — e MUST NOT haver conversão.

A moeda base aqui **não denomina figura alguma**: ela apenas escolhe entre dois valores que o razão já respondeu, nenhum deles convertido e nenhum deles aproximado. Isso é distinto — e MUST NOT ser confundido com — usar a base como recurso para uma figura cuja moeda é conhecível, que `money-display` proíbe. Converter na ausência de uma ponta na base compraria uma moeda que ninguém pediu ao preço de uma taxa que pode não existir.

Uma superfície que declara perspectiva MUST NOT aplicar esta escolha: a figura dela é a linha daquela conta, na moeda daquela conta, qualquer que seja a base.

A escolha SHALL ter um dono único, consumido por toda superfície sem perspectiva. Um item de lista e o detalhe que ele abre MUST NOT exibir dinheiro diferente para a mesma operação.

A perna que denomina a figura MUST NOT ser confundida com a perna pela qual a transação é **lida**: direção e sinal permanecem na perna primária. Trocar as duas juntas faria um pagamento de fatura se anunciar como receita no instante em que a figura passasse para a perna do passivo.

#### Scenario: Pagamento cruzado é denominado pela ponta na base
- **WHEN** uma fatura de cartão em reais é paga de uma conta em dólar, a moeda base é o real, e a operação é exibida sem perspectiva
- **THEN** a figura exibida é a da ponta em reais, sem conversão e sem marca de aproximação, e a direção continua sendo despesa

#### Scenario: Sem ponta na base, a leitura não muda
- **WHEN** a mesma operação é exibida com moeda base euro
- **THEN** a figura permanece a da conta de origem, em dólar, e nenhuma taxa é aplicada

#### Scenario: A perspectiva prevalece sobre a base
- **WHEN** a mesma operação é aberta a partir do extrato da conta em dólar
- **THEN** a figura é a daquela conta, em dólar

#### Scenario: Lista e detalhe não discordam
- **WHEN** a mesma operação cruzada é exibida num item de lista e aberta no detalhe
- **THEN** os dois exibem a mesma figura, na mesma moeda, por consumirem a mesma escolha

### Requirement: O detalhe de uma operação declara a taxa que ela praticou

O detalhe de uma operação cujas duas pernas monetárias estejam denominadas em moedas diferentes SHALL exibir a taxa que ela praticou, derivada das próprias pernas. A taxa exibida MUST NOT vir do acervo: o acervo responde pela conversão de figuras, enquanto esta é a razão entre o que saiu e o que entrou **nesta** operação, e as duas podem legitimamente divergir na mesma data.

A direção SHALL ser a mesma que o formulário de escrita usa ao revelar a segunda ponta — uma unidade da moeda de origem expressa na moeda de destino —, porque a taxa lida depois é a mesma que foi mostrada enquanto se digitava, e duas gramáticas para o mesmo quociente fazem o usuário suspeitar do número. Ela SHALL ser exibida com casas decimais suficientes para não arredondar a zero, pela mesma razão pela qual o acervo guarda o quociente pleno.

Uma operação em moeda única MUST NOT exibir a linha, e uma com uma só perna monetária tampouco — não há segunda ponta por que dividir, e uma linha ausente é a resposta certa para uma pergunta que não se colocou.

#### Scenario: Transferência entre moedas informa a taxa praticada
- **WHEN** o detalhe de uma transferência de R$ 550,00 para US$ 100,00 é aberto
- **THEN** ele exibe a taxa que a operação praticou, derivada das duas pernas e sem consultar o acervo

#### Scenario: Pagamento de fatura em outra moeda informa a taxa praticada
- **WHEN** o detalhe de um pagamento de fatura cujas pernas monetárias estão em moedas diferentes é aberto
- **THEN** ele exibe a taxa da mesma forma, pela mesma leitura

#### Scenario: Operação em moeda única não exibe taxa
- **WHEN** o detalhe de uma operação cujas pernas estão todas na mesma moeda é aberto
- **THEN** nenhuma taxa é exibida
