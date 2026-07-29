## MODIFIED Requirements

### Requirement: O tipo de exibição não responde "quanto vale"

O tipo de exibição SHALL responder apenas como um valor se lê. Ele MUST NOT combinar dois valores — soma, subtração, multiplicação — nem converter entre moedas: quanto uma figura vale é do razão, que é o dono único desse cálculo, e reduzi-la a outra moeda é da camada de consolidação.

O tipo SHALL carregar a **moeda** em que o seu valor está denominado. Isso não é cálculo: é a legenda sem a qual o número não se lê, e omiti-la produziria o mesmo modo de falha que a política de sinal existe para impedir — uma figura correta acompanhada do símbolo errado.

Uma política SHALL poder transformar o seu próprio valor para leitura — módulo, negação, limite em zero —, o que é apresentação de um único número e não cálculo.

O tipo SHALL expor o valor numérico com o seu sinal, para que decisões que dependem do sinal — cor, tom, ordenação — o leiam da mesma fonte que o texto exibido.

A formatação SHALL usar a moeda carregada pelo valor para escolher símbolo e denominação, e o locale do dispositivo apenas para o **formato** — separadores e posição do símbolo. A moeda exibida MUST NOT ser derivada do locale.

#### Scenario: O tipo de exibição não oferece cálculo
- **WHEN** o tipo de exibição é inspecionado
- **THEN** ele não expõe operação entre dois valores nem conversão de moeda, e uma tela que precise de total, saldo ou diferença os recebe já calculados

#### Scenario: Moeda acompanha o valor
- **WHEN** um modelo de UI expõe um valor monetário
- **THEN** valor, política e moeda vêm no mesmo tipo, e não é possível expor um sem os outros

#### Scenario: Símbolo não vem do locale
- **WHEN** um saldo em dólar é exibido num dispositivo com locale brasileiro
- **THEN** o símbolo exibido é o do dólar, e o formato — separadores e posição — segue o locale

#### Scenario: Magnitude de dívida é apresentação
- **WHEN** uma linha responde quanto se deve, a partir de um saldo no sinal do razão
- **THEN** a política apresenta a magnitude da dívida, e um cartão em crédito lê zero em vez de dívida negativa

#### Scenario: Cor e texto concordam
- **WHEN** uma apresentação escolhe cor ou tom pelo sinal de um valor
- **THEN** ela lê o sinal do mesmo valor que será exibido, e não de uma segunda fonte

## ADDED Requirements

### Requirement: Uma figura aproximada é sempre exibida como aproximada

Um valor monetário cuja obtenção passou por conversão de moeda SHALL ser exibido com marca de aproximação, em **toda** superfície que o apresente. Uma figura exata MUST NOT receber a marca.

Se um valor é exato ou aproximado SHALL ser **derivado** do resultado por moeda que o originou, e MUST NOT ser declarado pela tela nem marcado à mão por quem monta um modelo de UI:

- resultado sem moeda alguma (zero) → exato;
- resultado numa única moeda, igual à moeda base → exato;
- resultado numa única moeda diferente da base → aproximado;
- resultado em duas ou mais moedas → aproximado.

A exatidão SHALL viajar junto do valor, no mesmo tipo que já carrega a política de sinal e a moeda, pelo mesmo argumento: um valor que perde a sua marca no caminho até a tela é indistinguível de um valor exato, e a falha é silenciosa.

Decorre da derivação que, para um usuário cujas contas estejam todas na moeda base, nenhuma figura do app é aproximada e a marca não aparece em superfície alguma — sem que exista caminho de código, ramo de compatibilidade ou configuração que a desligue.

#### Scenario: Patrimônio com contas em duas moedas
- **WHEN** o patrimônio total é exibido e o usuário tem contas em BRL e em USD
- **THEN** a figura aparece na moeda base com marca de aproximação

#### Scenario: Saldo de conta nunca é aproximado
- **WHEN** o saldo de uma conta em USD é exibido
- **THEN** ele aparece em dólar, exato e sem marca, porque nenhuma conversão participou dele

#### Scenario: Usuário de uma moeda só não vê marca alguma
- **WHEN** todas as contas do usuário estão na moeda base e qualquer tela do app é exibida
- **THEN** nenhuma figura recebe marca de aproximação

#### Scenario: A tela não decide a marca
- **WHEN** um modelo de UI é montado a partir de uma figura consolidada
- **THEN** a marca vem derivada de quem produziu a figura, e a tela não a define nem a remove

#### Scenario: Mesma figura, mesma marca em toda superfície
- **WHEN** a mesma figura aproximada aparece numa lista, num resumo e no relatório exportado
- **THEN** as três exibem a marca de aproximação, por consumirem o mesmo valor
