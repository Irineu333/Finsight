## MODIFIED Requirements

### Requirement: Patrimônio líquido a partir das entries
O patrimônio líquido SHALL ser derivado do plano de contas como a soma dos saldos das contas `ASSET` menos a soma dos saldos das contas `LIABILITY`, usando o mesmo mecanismo de saldo das demais leituras.

As contas de conversão MUST NOT entrar no patrimônio líquido. O resultado cambial já se manifesta nos saldos das próprias contas do usuário quando expressos numa mesma moeda: uma transferência de R$ 550 para US$ 100 deixa `−550 BRL` e `+100 USD`, que consolidam a zero à taxa aplicada e passam a consolidar em ganho quando a taxa se move. Incluir as contas de conversão o contaria duas vezes.

Por atravessar contas de moedas possivelmente distintas, essa leitura SHALL ser expressa **por moeda**, e MUST NOT ser reduzida pelo razão a um único número. Reduzi-la à moeda base é conversão, e pertence à camada de consolidação (`currency-consolidation`).

#### Scenario: Patrimônio líquido em uma moeda
- **WHEN** o patrimônio líquido é solicitado e todas as contas estão na mesma moeda
- **THEN** o sistema retorna um saldo naquela moeda, igual à soma dos saldos `ASSET` menos os `LIABILITY`

#### Scenario: Patrimônio líquido em várias moedas
- **WHEN** o patrimônio líquido é solicitado e existem contas em duas moedas
- **THEN** o sistema retorna o saldo de cada moeda separadamente, sem somá-los e sem aplicar taxa alguma

#### Scenario: Conversão fora do patrimônio
- **WHEN** o patrimônio líquido é solicitado após operações que atravessaram moedas
- **THEN** os saldos das contas de conversão não participam do resultado

## ADDED Requirements

### Requirement: Leitura que atravessa contas é expressa por moeda

Toda leitura de dinheiro capaz de abranger contas de moedas distintas SHALL expressar o seu resultado **por moeda**. O razão MUST NOT somar valores de moedas diferentes num mesmo número, e MUST NOT reduzir um resultado multimoeda a um único valor.

Uma leitura escopada a **uma** conta é monomoeda por construção — a moeda é atributo da conta. Ela SHALL permanecer com a forma que tem hoje, acrescida apenas da moeda em que o seu resultado está denominado.

Uma leitura escopada a uma **dimensão** SHALL ser expressa por moeda, qualquer que seja o `kind` da dimensão. O razão MUST NOT consultar o `kind` para decidir a forma do resultado: nada no razão amarra uma dimensão a uma única conta, e que a dimensão de uma fatura recaia sempre sobre um único cartão é garantia da **fachada** de cartão, não construção do razão. Uma feature que saiba que o seu resultado tem sempre uma moeda MAY tratá-lo assim; o razão MUST NOT presumi-lo.

#### Scenario: Saldo de uma conta
- **WHEN** o saldo de uma conta é solicitado
- **THEN** o resultado é um valor único, denominado na moeda daquela conta

#### Scenario: Saldo devido de uma fatura
- **WHEN** o saldo devido de uma fatura é solicitado ao razão
- **THEN** o resultado é expresso por moeda, e a feature de cartões o consome sabendo que contém uma única moeda

#### Scenario: Gasto de uma categoria com lançamentos em duas moedas
- **WHEN** o gasto de uma categoria é solicitado e ela tem lançamentos em BRL e em USD
- **THEN** o sistema retorna o total de cada moeda separadamente

#### Scenario: Totais do mês por natureza de conta
- **WHEN** os totais de receita e despesa do mês são solicitados sobre todas as contas
- **THEN** o resultado é expresso por moeda, e nenhuma soma cruza moedas

#### Scenario: Razão não ramifica por tipo de dimensão
- **WHEN** as leituras por dimensão são inspecionadas
- **THEN** nenhuma delas consulta o `kind` da dimensão para decidir a forma do seu resultado

#### Scenario: Nenhuma agregação soma moedas
- **WHEN** as agregações do razão são inspecionadas
- **THEN** nenhuma delas soma valores sem separá-los por moeda

### Requirement: O razão não converte e não conhece moeda base

O razão MUST NOT conhecer taxa de câmbio, moeda base, nem qualquer preferência de exibição de moeda. Nenhuma leitura do razão SHALL consultar uma taxa, e nenhuma dependência que forneça taxa SHALL ser injetada nele.

A frase que o razão sustenta — toda figura é `Σ entries` — SHALL permanecer literalmente verdadeira. Uma leitura que multiplicasse entries por uma taxa deixaria de sê-lo, e é por isso que a conversão vive acima do razão e não dentro dele.

A moeda que uma conta nova recebe quando nenhuma é escolhida MAY continuar sendo um valor conhecido do razão; ela é um padrão de criação, e MUST NOT ser confundida com a moeda base do usuário, que é preferência de exibição e pertence a `currency-consolidation`.

#### Scenario: Razão sem dependência de taxa
- **WHEN** as dependências do razão são inspecionadas
- **THEN** nenhuma delas fornece taxa de câmbio ou moeda base

#### Scenario: Toda figura continua sendo soma de entries
- **WHEN** qualquer leitura de dinheiro do razão é inspecionada
- **THEN** o seu resultado é uma soma de `amount` de entries, sem fator de conversão

#### Scenario: Trocar a moeda base não altera o razão
- **WHEN** o usuário troca a sua moeda base
- **THEN** nenhuma entry, conta ou leitura do razão muda; apenas as figuras consolidadas passam a ser expressas na nova base

### Requirement: A soma de saldos por moeda tem dono no razão

Combinar dois resultados por moeda num terceiro — somar os saldos de perímetros disjuntos, por exemplo — SHALL ter exatamente uma implementação, e ela SHALL pertencer ao razão, que é o dono de quanto uma figura vale.

Essa operação MUST NOT ser atribuída à camada de consolidação, que responde apenas pela conversão entre moedas, nem ao tipo de exibição, que MUST NOT combinar dois valores (`money-display`). Sem dono explícito, um consumidor que precise da soma de dois perímetros a implementaria em linha, que é exatamente a reimplementação de regra derivável que o razão proíbe.

#### Scenario: Perímetro neutro soma naturezas disjuntas
- **WHEN** um resumo precisa do total de duas naturezas de conta disjuntas, cada uma lida por moeda
- **THEN** ele obtém a soma da única implementação do razão, sem somar mapas em linha

#### Scenario: Soma respeita a separação por moeda
- **WHEN** dois resultados por moeda são somados
- **THEN** cada moeda é somada com a sua própria, e nenhuma conversão participa da operação
