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
