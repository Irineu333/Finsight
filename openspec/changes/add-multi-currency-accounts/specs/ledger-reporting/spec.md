## MODIFIED Requirements

### Requirement: Patrimônio líquido a partir das entries
O patrimônio líquido SHALL ser derivado do plano de contas como a soma dos saldos das contas `ASSET` menos a soma dos saldos das contas `LIABILITY`, usando o mesmo mecanismo de saldo das demais leituras.

Por atravessar contas de moedas possivelmente distintas, essa leitura SHALL ser expressa **por moeda**, e MUST NOT ser reduzida pelo razão a um único número. Reduzi-la à moeda base é conversão, e pertence à camada de consolidação (`currency-consolidation`).

#### Scenario: Patrimônio líquido em uma moeda
- **WHEN** o patrimônio líquido é solicitado e todas as contas estão na mesma moeda
- **THEN** o sistema retorna um saldo naquela moeda, igual à soma dos saldos `ASSET` menos os `LIABILITY`

#### Scenario: Patrimônio líquido em várias moedas
- **WHEN** o patrimônio líquido é solicitado e existem contas em duas moedas
- **THEN** o sistema retorna o saldo de cada moeda separadamente, sem somá-los e sem aplicar taxa alguma

## ADDED Requirements

### Requirement: Leitura que atravessa contas é expressa por moeda

Toda leitura de dinheiro capaz de abranger contas de moedas distintas SHALL expressar o seu resultado **por moeda**. O razão MUST NOT somar valores de moedas diferentes num mesmo número, e MUST NOT reduzir um resultado multimoeda a um único valor.

Uma leitura escopada a **uma** conta, ou à dimensão de uma fatura — que projeta sobre a conta de um único cartão —, é monomoeda por construção. Ela SHALL permanecer com a forma que tem hoje, acrescida apenas da moeda em que o seu resultado está denominado; ela MUST NOT ser generalizada para multimoeda por simetria com as demais.

Uma leitura escopada a uma **dimensão de categoria** SHALL ser tratada como multimoeda: uma categoria não é linha do plano de contas, não tem moeda, e as entries que carregam a sua dimensão podem estar em várias.

#### Scenario: Saldo de uma conta
- **WHEN** o saldo de uma conta é solicitado
- **THEN** o resultado é um valor único, denominado na moeda daquela conta

#### Scenario: Saldo devido de uma fatura
- **WHEN** o saldo devido de uma fatura é solicitado
- **THEN** o resultado é um valor único, denominado na moeda do cartão

#### Scenario: Gasto de uma categoria com lançamentos em duas moedas
- **WHEN** o gasto de uma categoria é solicitado e ela tem lançamentos em BRL e em USD
- **THEN** o sistema retorna o total de cada moeda separadamente

#### Scenario: Totais do mês por natureza de conta
- **WHEN** os totais de receita e despesa do mês são solicitados sobre todas as contas
- **THEN** o resultado é expresso por moeda, e nenhuma soma cruza moedas

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
