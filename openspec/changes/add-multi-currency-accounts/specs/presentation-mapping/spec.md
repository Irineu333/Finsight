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
