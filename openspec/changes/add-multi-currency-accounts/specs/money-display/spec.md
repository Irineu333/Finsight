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
- resultado numa **única** moeda, qualquer que ela seja → exato, exibido naquela moeda, com ou sem taxa conhecida, porque não havia nada a reconciliar e nenhuma conversão ocorreu;
- resultado em duas ou mais moedas → aproximado, reduzido à base até onde as taxas permitirem.

A exatidão SHALL viajar junto do valor, no mesmo tipo que já carrega a política de sinal e a moeda, pelo mesmo argumento: um valor que perde a sua marca no caminho até a tela é indistinguível de um valor exato, e a falha é silenciosa.

A marca MUST NOT ser expressa apenas por cor. Ela SHALL ter um significante textual, resolvido no mesmo ponto que já resolve o sinal, de modo que a mesma regra produza o mesmo texto em toda superfície — inclusive naquelas sem cor, como o documento exportado. Esta é a mesma exigência que o sistema já aplica a estado exibido: cor sozinha falha para quem não a lê.

Uma figura MUST NOT ser convertida quando o resultado que a originou tem uma única moeda, mesmo que ela difira da base. Converter ali troca um valor exato por um aproximado sem reconciliar nada, e é perda pura.

Decorre da derivação que, para um usuário de uma moeda só — **qualquer** moeda, não apenas a base —, nenhuma figura do app é aproximada e a marca não aparece em superfície alguma, sem que exista caminho de código, ramo de compatibilidade ou configuração que a desligue.

#### Scenario: Patrimônio com contas em duas moedas
- **WHEN** o patrimônio total é exibido, o usuário tem contas em BRL e em USD, e a taxa do dólar é conhecida
- **THEN** a figura aparece na moeda base com marca de aproximação

#### Scenario: Saldo de conta nunca é aproximado
- **WHEN** o saldo de uma conta em USD é exibido
- **THEN** ele aparece em dólar, exato e sem marca, porque nenhuma conversão participou dele

#### Scenario: Figura em moeda única não é convertida, mesmo com taxa
- **WHEN** a moeda base é o real, uma figura contém apenas dólares e existe taxa cadastrada para o dólar
- **THEN** ela é exibida em dólar, exata e sem marca, porque não havia mais de uma moeda a reconciliar

#### Scenario: Usuário de moeda única diferente da base
- **WHEN** todas as contas e cartões estão em dólar, a base é o real, e qualquer tela do app é exibida — dashboard incluído
- **THEN** toda figura aparece em dólar, exata, e a marca de aproximação não aparece em lugar algum

#### Scenario: Usuário de uma moeda só não vê marca alguma
- **WHEN** todas as contas do usuário estão numa mesma moeda e qualquer tela do app é exibida
- **THEN** nenhuma figura recebe marca de aproximação, qualquer que seja essa moeda

#### Scenario: A marca sobrevive à ausência de cor
- **WHEN** uma figura aproximada é exibida numa superfície sem cor, como o relatório exportado
- **THEN** a marca continua legível, por ser textual

#### Scenario: A tela não decide a marca
- **WHEN** um modelo de UI é montado a partir de uma figura consolidada
- **THEN** a marca vem derivada de quem produziu a figura, e a tela não a define nem a remove

#### Scenario: Mesma figura, mesma marca em toda superfície
- **WHEN** a mesma figura aproximada aparece numa lista, num resumo e no relatório exportado
- **THEN** as três exibem a marca de aproximação, por consumirem o mesmo valor

### Requirement: A moeda de uma figura é a da própria figura, nunca a base por omissão

Uma figura monetária SHALL ser exibida na moeda em que ela **está**, e a moeda base SHALL ser usada apenas onde houve consolidação. Um valor que o razão devolveu numa única moeda — o saldo de uma conta, o devido de uma fatura, uma linha de extrato, uma parcela — MUST NOT ser exibido na moeda base quando esta difere da sua.

A moeda base MUST NOT ser usada como valor de recurso para uma figura cuja moeda é conhecível. Onde a moeda é derivável da conta ou da fachada que originou a figura, é dela que a exibição SHALL vir; recair na base "porque estava à mão" é o modo de falha que este requisito existe para impedir.

Este requisito precisa ser afirmado separadamente porque a sua violação é **invisível na configuração mais comum**: para um usuário cujas contas estão todas na moeda base, exibir a base e exibir a moeda da conta produzem exatamente o mesmo texto. Um teste que só exercite essa configuração MUST NOT ser considerado prova de conformidade; a verificação SHALL usar uma conta cuja moeda difira da base.

#### Scenario: Saldo de conta estrangeira não é exibido na base
- **WHEN** a moeda base é o real e o saldo de uma conta em dólar é exibido
- **THEN** ele aparece em dólar, e o símbolo do real não aparece em lugar algum daquela figura

#### Scenario: Extrato de conta estrangeira não é exibido na base
- **WHEN** os lançamentos de uma conta em dólar são listados
- **THEN** cada valor aparece em dólar, sem conversão e sem a moeda base

#### Scenario: Fatura de cartão estrangeiro não é exibida na base
- **WHEN** o devido e as parcelas de um cartão em dólar são exibidos
- **THEN** aparecem em dólar, pela moeda do cartão

#### Scenario: Verificação exige moeda diferente da base
- **WHEN** a conformidade com este requisito é verificada
- **THEN** a verificação usa uma conta cuja moeda difere da base, porque com moedas iguais a violação não é observável

### Requirement: Onde a figura multitermo não cabe, a degradação é declarada

Uma superfície de largura fixa, ou cuja gramática própria não admita mais de um termo — o medidor de limite, o rótulo de uma barra de progresso, o contador de parcelas —, SHALL exibir apenas o termo na moeda base, com a marca de aproximação e a indicação de que existe parcela não convertida. Ela MUST NOT truncar, quebrar ou omitir silenciosamente um termo.

Cada superfície com essa limitação SHALL declará-la; a decisão MUST NOT ser deixada ao layout. Um número que perde uma parcela por o texto não caber é indistinguível de um número completo, e a falha é silenciosa — que é exatamente o modo de falha que a marca de aproximação existe para impedir.

Uma superfície que **possa** exibir mais de um termo SHALL exibi-los todos.

**Guardar string já formatada MUST NOT ser confundido com ter gramática própria.** O documento exportado guarda texto em vez de figura, mas uma célula de tabela guarda o que recebe e a página tem largura para mais de um termo — logo ele cai na regra acima, e não na degradação: ele SHALL exibir **todos** os termos. O que uma superfície sem afordância de toque deve, em lugar da degradação, é dizer **o que a marca significa**: um documento que contenha figura aproximada SHALL declará-lo em texto próprio, derivado das figuras que ele contém, e um documento em que nada é aproximado MUST NOT receber essa declaração.

#### Scenario: Contador de parcelas não recebe dois termos
- **WHEN** um valor multitermo alcançaria um contador de parcelas
- **THEN** apenas o termo na moeda base é exibido, com a marca de aproximação, e a existência de parcela não convertida é indicada

#### Scenario: Documento exportado exibe todos os termos e declara a marca
- **WHEN** um relatório é exportado com figuras que contêm parcela não convertida
- **THEN** cada figura exibe **todos** os seus termos, o termo convertido leva a marca, e o documento declara em texto próprio o que a marca significa

#### Scenario: Documento sem nada aproximado não declara nada
- **WHEN** um relatório é exportado e nenhuma das suas figuras é aproximada
- **THEN** a declaração não aparece, para que um usuário de uma moeda só não leia a explicação de algo que nunca lhe aconteceu

#### Scenario: Superfície sem limitação exibe tudo
- **WHEN** um valor multitermo é exibido numa superfície sem restrição de largura ou gramática
- **THEN** todos os termos aparecem

#### Scenario: Nada é truncado em silêncio
- **WHEN** as superfícies que exibem dinheiro são inspecionadas
- **THEN** nenhuma delas descarta um termo por decisão de layout

### Requirement: A marca, as partes e a ausência têm cada uma o seu lugar

Uma superfície que exibe dinheiro vindo de mais de uma moeda SHALL obedecer às três invariantes abaixo, e elas são **ordenadas**: a segunda SHALL valer apenas quando a primeira não resolve, e a terceira apenas quando a segunda não cabe. Nenhuma superfície SHALL escolher entre elas por conta própria.

**1. A marca de aproximação SHALL acompanhar valor convertido, e apenas valor convertido.** Um termo que passou por uma taxa SHALL levá-la; um termo que taxa alguma tocou MUST NOT levá-la, ainda que esteja ao lado de um que passou, e ainda que a figura como um todo seja aproximada. Um valor não convertido é a resposta exata do razão, na moeda dele, e marcá-lo alega dúvida sobre um número que o sistema conhece perfeitamente.

Isso MUST NOT ser confundido com a exatidão da **figura**, que é fato distinto: uma figura que reúne parcelas que não se somam é aproximada — não é um número só, e número nenhum responde por ela — mesmo quando nenhum dos seus termos passou por taxa. Colapsar as duas perguntas numa só é o que faz um número exato aparecer marcado.

**2. Onde um valor único não puder ser resolvido, a superfície SHALL exibir as partes.** O dinheiro é conhecido; o que falta é a sua expressão numa única moeda. Uma superfície com espaço SHALL mostrar cada parcela na sua própria moeda, em vez de recair num marcador de ausência — recair ali descarta informação que o sistema tem.

**3. Onde as partes não couberem, ou não forem pertinentes, a superfície SHALL exibir a ausência de valor, discretamente.** "Não pertinente" é o caso de um número que não é soma de parcelas — o que resta de um limite, o quanto ele foi excedido —, que com a parcela ausente não existe em nenhuma moeda. A ausência SHALL ser visualmente discreta e MUST NOT ocupar mais espaço do que um valor ocuparia: a alternativa a um número errado é um marcador contido, nunca um layout quebrado nem uma linha de texto explicativa em toda superfície.

A ausência MUST NOT ser expressa como zero, e MUST NOT ser omitida deixando a linha vazia: zero é uma afirmação sobre a quantia, e uma linha vazia é indistinguível de um dado que não existe.

#### Scenario: Só o termo convertido leva a marca
- **WHEN** uma figura reúne R$ 30,00 que sempre estiveram em reais e ¥ 5.000 que nenhuma taxa alcança
- **THEN** nenhum dos dois termos exibe a marca, e a figura ainda assim é aproximada

#### Scenario: Termo convertido ao lado de termo intocado
- **WHEN** uma figura reúne uma parcela convertida à base e outra que nenhuma taxa alcança
- **THEN** apenas a parcela convertida exibe a marca

#### Scenario: Superfície com espaço exibe as partes
- **WHEN** um gasto que não pôde ser reduzido a uma moeda é exibido numa superfície com espaço
- **THEN** cada parcela aparece na sua própria moeda, e nenhum marcador de ausência é usado

#### Scenario: Superfície de gramática própria exibe a ausência
- **WHEN** o mesmo gasto alcança um rótulo de uma linha, de gramática própria
- **THEN** ele exibe o marcador de ausência, discreto, e a figura não é truncada nem quebrada

#### Scenario: Um valor que não é soma de parcelas não tem partes a exibir
- **WHEN** o quanto resta de um limite é exibido e o gasto tem parcela não precificada
- **THEN** ele exibe a ausência, porque não existe em moeda alguma, e MUST NOT exibir zero
