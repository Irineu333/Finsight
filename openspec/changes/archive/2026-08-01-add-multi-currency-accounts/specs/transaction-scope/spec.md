## MODIFIED Requirements

### Requirement: Movimento interno ao perímetro não é fluxo

Um lançamento cujas pernas estejam **todas dentro** do perímetro do escopo SHALL contribuir zero para o fechamento daquele escopo, por consequência de `Σ = 0` **por moeda** por lançamento. Ele MUST NOT ser reportado como entrada, saída ou ajuste.

A qualificação "por moeda" é o que mantém a derivação válida quando um lançamento atravessa moedas. As pernas de conversão de um lançamento cruzado postam em contas de sistema, que estão **fora** de qualquer perímetro de conta ou de cartão; dentro do perímetro sobram as pernas monetárias, uma em cada moeda. Elas somam zero **em cada moeda separadamente** apenas quando lidas por moeda: consolidadas numa única figura à taxa corrente, elas somam a variação cambial desde o câmbio, e não a zero. A contribuição nula do movimento interno é, portanto, exata na leitura por moeda e aproximada na consolidada — e é a leitura por moeda que a spec afirma.

Essa classificação SHALL depender exclusivamente do perímetro, e MUST NOT ser expressa como exceção por tipo de lançamento nem por moeda. Um lançamento com perna fora do perímetro SHALL, ao contrário, ser reportado como fluxo — é o caso do pagamento de fatura visto do escopo de contas, cuja perna de passivo está fora. As pernas de conversão MUST NOT, por estarem fora do perímetro, tornar fluxo um lançamento que de outro modo seria interno: o que decide é onde estão as pernas **monetárias**.

Um movimento interno SHALL poder ser **exibido** como linha informativa, sem sinal e fora da soma.

#### Scenario: Transferência não altera o saldo do escopo de contas
- **WHEN** existe uma transferência entre duas contas no mês e o escopo é "contas"
- **THEN** ela aparece na lista e não altera entradas, saídas nem o saldo final

#### Scenario: Transferência entre moedas também é interna
- **WHEN** existe uma transferência entre duas contas de moedas diferentes, ambas no perímetro, e o escopo é "contas"
- **THEN** ela aparece na lista e não é reportada como entrada, saída ou ajuste, apesar de as suas pernas de conversão estarem fora do perímetro

#### Scenario: Pagamento de fatura é fluxo no escopo de contas
- **WHEN** existe um pagamento de fatura no mês e o escopo é "contas"
- **THEN** ele é reportado em linha própria e reduz o saldo final, porque a sua perna de passivo está fora do perímetro

#### Scenario: Pagamento de fatura abate a dívida no escopo de cartões
- **WHEN** existe um pagamento de fatura no mês e o escopo é "cartões"
- **THEN** ele é reportado como pagamento e leva o saldo do perímetro na direção do crédito, abatendo o que se deve, porque o dinheiro veio de fora do perímetro

#### Scenario: Pagamento de fatura é neutro no escopo geral
- **WHEN** existe um pagamento de fatura no mês e o escopo é o geral
- **THEN** o valor é exibido como linha informativa, sem sinal, e o líquido final é idêntico ao que seria sem esse pagamento
