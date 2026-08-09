# dashboard-balance-widgets Specification

## Purpose

Os perímetros que o dashboard oferece como widget de dinheiro, e a regra que os governa: **todo widget nomeia o perímetro do plano de contas que ele soma** — um rótulo neutro sobre uma só natureza de conta é afirmação falsa, não ambiguidade. Os três perímetros de fluxo (apenas contas `ASSET`, apenas cartões `LIABILITY`, e o neutro) existem como widgets independentes e coexistem na tela, porque o perímetro é a identidade do widget e não uma configuração dele. O neutro é derivado **somando** as leituras por natureza que o razão já expõe, nunca por um agregado dedicado — as duas despesas são disjuntas, então a soma não dupla-conta, e o pagamento de fatura é interno ao perímetro. E o que um widget de fluxo reporta é fluxo: sem abertura, sem fechamento, e com o mesmo conjunto de classes nos três, o que faz `Despesas(geral) = Despesas(contas) + Gasto no Cartão` verificável na tela. Consome as leituras do razão (`ledger-reporting`) sobre o plano de contas (`chart-of-accounts`); distingue-se de `transaction-scope`, que modela os mesmos três perímetros sob a gramática de fechamento da tela de transações.
## Requirements
### Requirement: Todo widget de saldo nomeia o seu perímetro

Um widget do dashboard que exiba saldo ou fluxo de dinheiro SHALL declarar no seu rótulo **qual perímetro do plano de contas** ele soma. Um rótulo neutro — que não qualifique o perímetro — MUST NOT ser usado por um widget que some apenas uma natureza de conta.

Em particular, um widget que some apenas contas `ASSET` MUST NOT ser apresentado como saldo ou balanço **total**, **geral** ou equivalente, uma vez que o plano de contas registra também os passivos do usuário.

Esta regra rege o **rótulo**, não a leitura: corrigir um nome enviesado MUST NOT alterar o valor que o widget já exibia.

#### Scenario: Saldo de contas não se diz total
- **WHEN** um widget exibe o saldo acumulado apenas das contas `ASSET`
- **THEN** o rótulo qualifica o perímetro como sendo o das contas, e não o do dinheiro do usuário como um todo

#### Scenario: Correção de rótulo não move valor
- **WHEN** o rótulo de um widget é corrigido para nomear o seu perímetro
- **THEN** o valor exibido permanece idêntico ao anterior

#### Scenario: Fluxo de contas não se diz balanço sem qualificação
- **WHEN** um widget exibe receitas e despesas apenas das contas `ASSET`
- **THEN** ele é distinguível, na tela, de um widget que exiba o mesmo par sobre contas e cartões

### Requirement: Os três perímetros existem como widgets independentes

O dashboard SHALL oferecer o fluxo do mês em três perímetros — apenas contas (`ASSET`), apenas cartões (`LIABILITY`) e o neutro (`ASSET` + `LIABILITY`) —, e os três SHALL poder estar presentes na tela **simultaneamente**.

O perímetro SHALL ser a identidade do widget, e MUST NOT ser uma configuração de um widget único, enquanto a identidade de um widget for o seu tipo — sob essa identidade, um widget configurável ofereceria um perímetro por vez e tornaria a coexistência impossível.

O widget neutro SHALL nascer **ausente** dos dashboards já existentes e ser adicionado pelo modo de edição, como qualquer outro widget. Nenhuma preferência salva SHALL ser reescrita.

#### Scenario: Contas, cartões e geral na mesma tela
- **WHEN** o usuário adiciona os três widgets de fluxo
- **THEN** os três aparecem simultaneamente, cada um somando o seu perímetro

#### Scenario: Dashboard existente não muda
- **WHEN** um dashboard montado antes desta capacidade é carregado
- **THEN** ele exibe exatamente os mesmos widgets, na mesma ordem e com a mesma aparência, sem o widget neutro

### Requirement: O perímetro neutro é a soma das naturezas disjuntas

O fluxo do perímetro neutro SHALL ser derivado **somando** as leituras de fluxo do razão por natureza, e MUST NOT depender de um agregado dedicado a esse perímetro.

Sendo as leituras de fluxo expressas por moeda, a soma SHALL ser a soma **por moeda** — cada moeda somada com a sua própria, sem conversão. Essa operação SHALL usar a única implementação de soma de saldos por moeda do razão (`ledger-reporting`), e MUST NOT ser realizada em linha pelo construtor do widget. Consumir essa operação MUST NOT ser entendido como depender de agregado dedicado: ela é aritmética genérica sobre dois resultados, e não uma consulta criada para este perímetro — a distinção que este requisito preserva é entre somar o que o razão já expõe e criar uma leitura nova para o perímetro neutro.

A despesa do perímetro neutro SHALL ser a soma da despesa de `ASSET` e da despesa de `LIABILITY`. A soma MUST NOT dupla-contar: os dois conjuntos são disjuntos, porque um lançamento de cartão não tem perna `ASSET`.

O pagamento de fatura MUST NOT ser reportado como despesa em nenhuma das duas parcelas, por ser movimento interno ao perímetro neutro — as suas duas pernas estão dentro dele. Um pagamento de fatura que atravesse moedas SHALL permanecer interno pela mesma razão: as suas pernas monetárias estão ambas no perímetro, e as de conversão não o tornam fluxo.

A receita do perímetro neutro SHALL ser idêntica à do perímetro de contas, uma vez que o razão não registra receita em perna de `LIABILITY`.

#### Scenario: Compra de cartão entra na despesa neutra
- **WHEN** existe uma compra de cartão no mês e o fluxo do perímetro neutro é exibido
- **THEN** ela é somada à despesa do perímetro neutro

#### Scenario: Compra no cartão entra uma vez só
- **WHEN** existe uma compra de cartão no mês e o fluxo do perímetro neutro é exibido
- **THEN** ela é contada exatamente uma vez na despesa

#### Scenario: Pagamento de fatura não é despesa do perímetro neutro
- **WHEN** existe um pagamento de fatura no mês e o fluxo do perímetro neutro é exibido
- **THEN** ele não é somado como despesa, por ser interno ao perímetro

#### Scenario: Pagamento de fatura entre moedas continua interno
- **WHEN** existe um pagamento de fatura em moeda distinta da conta pagadora e o fluxo do perímetro neutro é exibido
- **THEN** ele não é somado como despesa, e as suas pernas de conversão não o tornam fluxo

#### Scenario: A despesa neutra é a soma das duas exibidas
- **WHEN** os três widgets de fluxo estão presentes na tela
- **THEN** a despesa do widget neutro é igual à despesa do widget de contas somada ao gasto do widget de cartões

#### Scenario: Soma por moeda, sem conversão
- **WHEN** o fluxo do perímetro neutro é derivado sobre contas em duas moedas
- **THEN** cada moeda é somada com a sua própria, e nenhuma conversão participa da derivação

#### Scenario: Sem agregado dedicado
- **WHEN** a origem do fluxo neutro é inspecionada
- **THEN** ele deriva da soma das leituras por natureza que o razão já expõe, e não de uma consulta criada para esse perímetro

### Requirement: Widget de fluxo reporta fluxo, não fechamento

Um widget de fluxo do dashboard SHALL exibir **apenas** as classes de fluxo do seu perímetro, e MUST NOT afirmar a identidade `abertura + fluxos = fechamento`: ele não exibe abertura nem fechamento, logo não deve essa identidade.

O conjunto de classes reportadas SHALL ser o **mesmo entre os três perímetros**. Em particular, o **ajuste** — a contrapartida em `EQUITY` — SHALL ficar fora dos três, ou entrar nos três; MUST NOT ser reportado em um perímetro e omitido em outro.

Enquanto o ajuste ficar fora, os valores do dashboard PODEM divergir dos de um resumo que feche a conta sobre o mesmo perímetro e o mesmo mês. Essa divergência SHALL ser consequência declarada da escolha de classes, e MUST NOT ser tratada como caso especial em uma das leituras.

#### Scenario: O widget não exibe saldo de abertura nem de fechamento
- **WHEN** um widget de fluxo é exibido
- **THEN** ele mostra apenas as classes de fluxo do perímetro, sem linha de abertura ou de fechamento

#### Scenario: Ajuste tratado igual nos três perímetros
- **WHEN** existe ajuste de saldo de conta e ajuste de saldo de fatura no mesmo mês
- **THEN** nenhum dos três widgets o reporta, e a aritmética entre eles permanece verificável

### Requirement: Widget de fluxo presente exibe o par completo de classes

Um widget de fluxo do dashboard que esteja presente na tela SHALL exibir **todas** as classes do seu perímetro, e uma classe cujo total seja zero SHALL ser exibida **como zero**, não omitida.

O conjunto de classes exibidas é determinado pela **identidade** do widget, e MUST NOT variar com os dados do mês: o mesmo widget, em meses diferentes, apresenta a mesma forma. Ausência de valor é uma leitura — R$ 0,00 —, e omitir o cartão a converte na afirmação mais forte de que aquela classe não se aplica ao perímetro, que é falsa.

A única decisão binária de visibilidade que um widget de fluxo SHALL admitir é sobre **o widget inteiro**, governada pela sua configuração de ocultar-quando-vazio. Widget presente ⇒ par completo; widget oculto ⇒ nada. MUST NOT existir estado intermediário em que parte das classes desaparece.

Esta regra é a forma interna da que exige o mesmo conjunto de classes entre os três perímetros: aquela compara widgets lado a lado, esta compara o mesmo widget ao longo do tempo.

#### Scenario: Só há receita prevista no mês
- **WHEN** o widget de recorrentes previstas está presente e só há recorrentes de receita pendentes no mês
- **THEN** os dois cartões são exibidos, com o valor previsto na receita e R$ 0,00 na despesa

#### Scenario: Só há despesa prevista no mês
- **WHEN** o widget de recorrentes previstas está presente e só há recorrentes de despesa pendentes no mês
- **THEN** os dois cartões são exibidos, com R$ 0,00 na receita e o valor previsto na despesa

#### Scenario: A forma do widget não muda com o mês
- **WHEN** o usuário navega entre um mês com as duas classes e um mês com apenas uma
- **THEN** o widget ocupa a mesma largura e exibe a mesma quantidade de cartões nos dois meses

#### Scenario: A ocultação continua sendo do widget inteiro
- **WHEN** o widget está configurado para ocultar-se quando vazio e nenhuma classe tem valor no mês
- **THEN** o widget inteiro desaparece, e nunca apenas um dos seus cartões

#### Scenario: Correção de forma não move valor
- **WHEN** um widget deixa de omitir a classe zerada
- **THEN** os valores exibidos nas classes restantes permanecem idênticos aos anteriores
