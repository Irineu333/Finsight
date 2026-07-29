## MODIFIED Requirements

### Requirement: O tipo de exibição não responde "quanto vale"

O tipo de exibição SHALL responder apenas como um valor se lê. Ele MUST NOT combinar dois valores — soma, subtração, multiplicação — nem converter entre moedas: quanto uma figura vale é do razão, que é o dono único desse cálculo, e reduzi-la a outra moeda é da camada de consolidação.

O tipo SHALL carregar a **moeda** em que o seu valor está denominado. Isso não é cálculo: é a legenda sem a qual o número não se lê, e omiti-la produziria o mesmo modo de falha que a política de sinal existe para impedir — uma figura correta acompanhada do símbolo errado.

Uma figura monetária exibida SHALL poder ser composta de **mais de um termo**, cada termo um valor com a sua moeda, quando as parcelas que a compõem não puderem ser reduzidas a uma só. Justapor termos de moedas distintas MUST NOT ser entendido como combinar valores: é precisamente a **recusa** de somar o que não se soma, expressa como apresentação. O caso de um único termo SHALL ser o caso comum, e nenhuma superfície SHALL tratá-lo como especial.

Uma política SHALL poder transformar o seu próprio valor para leitura — módulo, negação, limite em zero —, o que é apresentação de um único número e não cálculo.

O tipo SHALL expor o valor numérico com o seu sinal, para que decisões que dependem do sinal — cor, tom, ordenação — o leiam da mesma fonte que o texto exibido.

A formatação SHALL usar a moeda carregada pelo valor para escolher símbolo e denominação, e o locale do dispositivo apenas para o **formato** — separadores e posição do símbolo. A moeda exibida MUST NOT ser derivada do locale. O mesmo SHALL valer para a **entrada** de um valor monetário: o campo SHALL exibir o símbolo da moeda da conta escolhida, pelo mesmo argumento, do lado da escrita.

#### Scenario: O tipo de exibição não oferece cálculo
- **WHEN** o tipo de exibição é inspecionado
- **THEN** ele não expõe operação entre dois valores nem conversão de moeda, e uma tela que precise de total, saldo ou diferença os recebe já calculados

#### Scenario: Moeda acompanha o valor
- **WHEN** um modelo de UI expõe um valor monetário
- **THEN** valor, política e moeda vêm no mesmo tipo, e não é possível expor um sem os outros

#### Scenario: Figura de dois termos
- **WHEN** uma figura consolidada contém uma parcela que não pôde ser convertida
- **THEN** ela é exibida como dois termos justapostos, cada um com a sua moeda, sem que nenhuma soma entre eles seja realizada

#### Scenario: Símbolo não vem do locale
- **WHEN** um saldo em dólar é exibido num dispositivo com locale brasileiro
- **THEN** o símbolo exibido é o do dólar, e o formato — separadores e posição — segue o locale

#### Scenario: Entrada exibe a moeda da conta
- **WHEN** o usuário digita um valor para um lançamento numa conta em dólar
- **THEN** o campo exibe o símbolo do dólar, e não o da moeda base nem o do locale

#### Scenario: Magnitude de dívida é apresentação
- **WHEN** uma linha responde quanto se deve, a partir de um saldo no sinal do razão
- **THEN** a política apresenta a magnitude da dívida, e um cartão em crédito lê zero em vez de dívida negativa

#### Scenario: Cor e texto concordam
- **WHEN** uma apresentação escolhe cor ou tom pelo sinal de um valor
- **THEN** ela lê o sinal do mesmo valor que será exibido, e não de uma segunda fonte

## ADDED Requirements

### Requirement: Uma figura aproximada é sempre exibida como aproximada

Um valor monetário cuja obtenção passou por conversão de moeda SHALL ser exibido com marca de aproximação, em **toda** superfície que o apresente. Uma figura exata MUST NOT receber a marca.

Se um valor é exato ou aproximado SHALL ser **derivado** do resultado por moeda que o originou e das taxas disponíveis, e MUST NOT ser declarado pela tela nem marcado à mão por quem monta um modelo de UI. Uma figura SHALL ser aproximada quando, e apenas quando, alguma conversão tiver participado dela:

- resultado sem moeda alguma → exato;
- resultado numa única moeda, igual à moeda base → exato;
- resultado numa única moeda diferente da base, sem taxa conhecida → exato, exibido naquela moeda, pois nada foi convertido;
- qualquer resultado em que ao menos uma parcela foi convertida → aproximado.

A exatidão SHALL viajar junto do valor, no mesmo tipo que já carrega a política de sinal e a moeda, pelo mesmo argumento: um valor que perde a sua marca no caminho até a tela é indistinguível de um valor exato, e a falha é silenciosa.

Decorre da derivação que, para um usuário cujas contas estejam todas na moeda base, nenhuma figura do app é aproximada e a marca não aparece em superfície alguma — sem que exista caminho de código, ramo de compatibilidade ou configuração que a desligue.

#### Scenario: Patrimônio com contas em duas moedas
- **WHEN** o patrimônio total é exibido, o usuário tem contas em BRL e em USD, e a taxa do dólar é conhecida
- **THEN** a figura aparece na moeda base com marca de aproximação

#### Scenario: Saldo de conta nunca é aproximado
- **WHEN** o saldo de uma conta em USD é exibido
- **THEN** ele aparece em dólar, exato e sem marca, porque nenhuma conversão participou dele

#### Scenario: Figura em moeda única sem taxa não é aproximada
- **WHEN** uma figura consolidada contém apenas dólares e não há taxa cadastrada
- **THEN** ela é exibida em dólar, sem marca, porque nada foi convertido

#### Scenario: Usuário de uma moeda só não vê marca alguma
- **WHEN** todas as contas do usuário estão na moeda base e qualquer tela do app é exibida
- **THEN** nenhuma figura recebe marca de aproximação

#### Scenario: A tela não decide a marca
- **WHEN** um modelo de UI é montado a partir de uma figura consolidada
- **THEN** a marca vem derivada de quem produziu a figura, e a tela não a define nem a remove

#### Scenario: Mesma figura, mesma marca em toda superfície
- **WHEN** a mesma figura aproximada aparece numa lista, num resumo e no relatório exportado
- **THEN** as três exibem a marca de aproximação, por consumirem o mesmo valor
