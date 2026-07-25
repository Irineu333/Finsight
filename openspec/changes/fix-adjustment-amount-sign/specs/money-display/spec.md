## ADDED Requirements

### Requirement: A política de sinal de um valor exibido tem dono único

Como um valor monetário é apresentado ao usuário — em módulo, no seu sinal natural, com sinal sempre explícito, ou forçado a uma direção — SHALL ser expresso por um tipo de exibição que carrega, indissociáveis, o valor e a sua política de sinal. Um componente de UI MUST NOT decidir sinal por conta própria a partir de rótulo, natureza, direção ou tipo de conta.

O conjunto de políticas SHALL ser fechado e SHALL cobrir apenas casos com chamador existente: módulo sem sinal, sinal natural (negativo aparece, positivo não), sinal sempre explícito, e as duas formas forçadas — sempre positivo e sempre negativo.

Valor e política MUST NOT ser campos independentes de um modelo de UI: um valor construído sem a sua política, ou alterado sem ela, é o modo de falha que este requisito existe para tornar impossível.

#### Scenario: Componente não escolhe sinal
- **WHEN** um componente de UI renderiza um valor monetário
- **THEN** ele aplica a política que acompanha o valor, sem consultar rótulo, natureza, direção ou tipo de conta para decidir o sinal

#### Scenario: Política acompanha o valor
- **WHEN** um modelo de UI expõe um valor monetário
- **THEN** valor e política vêm no mesmo tipo, e não é possível expor um sem o outro

#### Scenario: Duas telas, mesma figura, mesmo resultado
- **WHEN** a mesma transação é exibida em uma lista na tela e no relatório exportado
- **THEN** ambas produzem o mesmo texto de valor, por consumirem a mesma política, e não por coincidência entre duas implementações

### Requirement: O tipo de exibição não responde "quanto vale"

O tipo de exibição SHALL responder apenas como um valor se lê. Ele MUST NOT expor aritmética (soma, subtração, multiplicação) nem moeda: quanto uma figura vale, em que unidade e em que moeda é do razão, que é o dono único desse cálculo.

O tipo SHALL expor o valor numérico com o seu sinal, para que decisões que dependem do sinal — cor, tom, ordenação — o leiam da mesma fonte que o texto exibido.

#### Scenario: Cor e texto concordam
- **WHEN** uma apresentação escolhe cor ou tom pelo sinal de um valor
- **THEN** ela lê o sinal do mesmo valor que será exibido, e não de uma segunda fonte

#### Scenario: O tipo de exibição não oferece cálculo
- **WHEN** o tipo de exibição é inspecionado
- **THEN** ele não expõe soma, subtração, multiplicação nem moeda, e uma tela que precise de total, saldo ou diferença os recebe do razão já calculados

### Requirement: O valor exibido de uma perna de transação segue a forma do razão

O valor exibido de uma transação em uma lista SHALL ser resolvido no mapeamento, a partir da forma derivada pelo razão, e SHALL usar: sinal explícito para o **ajuste**; sinal natural para a **transferência**; módulo para **despesa**, **receita** e **pagamento de fatura**.

O ajuste é a única transação bidirecional — ele declara para que lado um saldo foi corrigido —, e por isso é a única cujo sinal SHALL ser sempre visível. As demais carregam a direção no rótulo, no ícone e na cor, e MUST NOT ganhar sinal que hoje não têm.

#### Scenario: Ajuste que aumenta a dívida de um cartão
- **WHEN** a dívida de uma fatura é ajustada de R$ 0,00 para R$ 100,00 e a transação resultante é exibida em uma lista
- **THEN** o valor é exibido como negativo, porque a perna do cartão é um crédito

#### Scenario: Ajuste que reduz a dívida de um cartão
- **WHEN** a dívida de uma fatura é ajustada para menos e a transação resultante é exibida em uma lista
- **THEN** o valor é exibido como positivo

#### Scenario: Ajuste que reduz o saldo de uma conta
- **WHEN** o saldo de uma conta é ajustado para menos e a transação resultante é exibida em uma lista
- **THEN** o valor é exibido como negativo

#### Scenario: Ajuste que aumenta o saldo de uma conta
- **WHEN** o saldo de uma conta é ajustado para mais e a transação resultante é exibida em uma lista
- **THEN** o valor é exibido como positivo

#### Scenario: Despesa não ganha sinal
- **WHEN** uma despesa em conta ou em cartão é exibida em uma lista
- **THEN** o valor é exibido em módulo, e a direção permanece expressa pelo rótulo, pelo ícone e pela cor

#### Scenario: Pagamento de fatura não ganha sinal
- **WHEN** o pagamento de uma fatura é exibido em uma lista
- **THEN** o valor é exibido em módulo

#### Scenario: Transferência mantém o sinal natural
- **WHEN** uma transferência é exibida pela ponta de saída
- **THEN** o valor é exibido como negativo, pelo sinal natural da perna e sem que a apresentação componha o sinal com o texto

### Requirement: O ajuste lê igual em toda superfície

O sinal exibido de um ajuste SHALL ser o mesmo em toda superfície que o apresenta — lista, detalhe, resumo de fatura e relatório exportado —, por derivar do mesmo valor do razão. Uma superfície MUST NOT apresentar um ajuste com sinal oposto ao de outra.

#### Scenario: Lista e detalhe concordam
- **WHEN** um ajuste é aberto no detalhe a partir da lista em que aparece
- **THEN** o sinal exibido nas duas superfícies é o mesmo

#### Scenario: Lista e resumo da fatura concordam
- **WHEN** uma fatura exibe a sua linha de ajustes acima da lista de lançamentos
- **THEN** o ajuste na lista tem o mesmo sinal que a linha de resumo

#### Scenario: Tom do ajuste no relatório exportado
- **WHEN** um ajuste que piora o saldo é exportado no relatório
- **THEN** ele recebe o tom negativo, e não o positivo
