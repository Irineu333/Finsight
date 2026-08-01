## MODIFIED Requirements

### Requirement: O perímetro neutro é a soma das naturezas disjuntas

O fluxo do perímetro neutro SHALL ser derivado **somando** as leituras de fluxo do razão por natureza, e MUST NOT depender de um agregado dedicado a esse perímetro.

Sendo as leituras de fluxo expressas por moeda, a soma SHALL ser a soma **por moeda** — cada moeda somada com a sua própria, sem conversão. Essa operação SHALL usar a única implementação de soma de saldos por moeda do razão (`ledger-reporting`), e MUST NOT ser realizada em linha pelo construtor do widget. Consumir essa operação MUST NOT ser entendido como depender de agregado dedicado: ela é aritmética genérica sobre dois resultados, e não uma consulta criada para este perímetro — a distinção que este requisito preserva é entre somar o que o razão já expõe e criar uma leitura nova para o perímetro neutro.

A despesa do perímetro neutro SHALL ser a soma da despesa de `ASSET` e da despesa de `LIABILITY`. A soma MUST NOT dupla-contar: os dois conjuntos são disjuntos, porque um lançamento de cartão não tem perna `ASSET`.

O pagamento de fatura MUST NOT ser reportado como despesa em nenhuma das duas parcelas, por ser movimento interno ao perímetro neutro — as suas duas pernas estão dentro dele. Um pagamento de fatura que atravesse moedas SHALL permanecer interno pela mesma razão: as suas pernas monetárias estão ambas no perímetro, e as de conversão não o tornam fluxo.

A receita do perímetro neutro SHALL ser idêntica à do perímetro de contas, uma vez que o razão não registra receita em perna de `LIABILITY`.

#### Scenario: Compra de cartão entra na despesa neutra
- **WHEN** existe uma compra de cartão no mês e o fluxo do perímetro neutro é exibido
- **THEN** ela é somada à despesa do perímetro neutro

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
