## ADDED Requirements

### Requirement: Operação como conjunto de entries balanceadas
Uma operação SHALL ser composta por um conjunto de `Entry`, cada `Entry` referenciando uma `Account`, com um `amount` assinado (na menor unidade da moeda) e uma `currency`. Para cada moeda presente em uma operação, a soma dos `amount` das entries daquela moeda SHALL ser exatamente zero. Uma operação MUST NOT ter menos de duas entries.

#### Scenario: Despesa balanceada
- **WHEN** o usuário registra uma despesa de 50 na categoria "Alimentação" a partir da conta corrente
- **THEN** a operação contém duas entries que somam zero: `EXPENSE:Alimentação` debitada e `ASSET:Conta` creditada

#### Scenario: Transferência balanceada
- **WHEN** o usuário transfere 100 da conta A para a conta B
- **THEN** a operação contém `ASSET:B` debitada e `ASSET:A` creditada, somando zero

#### Scenario: Pagamento de fatura balanceado
- **WHEN** o usuário paga 50 da fatura do cartão a partir da conta corrente
- **THEN** a operação contém `LIABILITY:Cartão` debitada e `ASSET:Conta` creditada, somando zero

### Requirement: Invariante de soma zero validada na escrita
O sistema SHALL validar a invariante de soma zero por moeda em um único ponto na fronteira de escrita e MUST NOT persistir qualquer operação cujas entries não somem zero em alguma moeda. A falha SHALL ser reportada com um erro tipado (via `Either`), não com exceção silenciosa nem correção automática.

#### Scenario: Operação desbalanceada é rejeitada
- **WHEN** uma tentativa de criar uma operação cujas entries não somam zero é submetida ao repositório
- **THEN** a persistência falha com um erro tipado indicando o desbalanceamento, e nada é gravado

#### Scenario: Operação balanceada é persistida
- **WHEN** uma operação cujas entries somam zero em cada moeda é submetida
- **THEN** a operação e suas entries são gravadas atomicamente

### Requirement: Convenção de sinal débito-positivo
O razão SHALL adotar a convenção débito-positivo: `amount` positivo representa débito e negativo representa crédito. O débito SHALL aumentar o saldo natural de contas `ASSET` e `EXPENSE`; o crédito SHALL aumentar o saldo natural de contas `LIABILITY`, `INCOME` e `EQUITY`. Essa convenção MUST NOT ser exposta ao usuário; a inversão de sinal para exibição pertence à camada de apresentação.

#### Scenario: Compra no cartão aumenta a dívida
- **WHEN** uma compra de 50 no cartão é registrada
- **THEN** a entry `EXPENSE:categoria` é debitada (+50) e a entry `LIABILITY:Cartão` é creditada (−50), aumentando o saldo natural do passivo

### Requirement: Tipo de operação derivado dos tipos de conta
O sistema SHALL derivar o rótulo de uma operação (despesa, receita, transferência, pagamento) a partir dos tipos das contas envolvidas nas suas entries, e MUST NOT persistir esse rótulo como estado independente. `ASSET`→`EXPENSE` SHALL ser despesa; `INCOME`→`ASSET` receita; `ASSET`→`ASSET` transferência; `ASSET`→`LIABILITY` pagamento.

#### Scenario: Rótulo derivado de uma transferência
- **WHEN** uma operação tem duas entries, ambas em contas `ASSET`
- **THEN** o sistema a apresenta como transferência sem consultar nenhum campo de tipo persistido

#### Scenario: Rótulo derivado de um pagamento de fatura
- **WHEN** uma operação move valor de uma conta `ASSET` para uma conta `LIABILITY`
- **THEN** o sistema a apresenta como pagamento

### Requirement: Ajuste de saldo como operação balanceada
O ajuste de saldo SHALL ser registrado como uma operação balanceada de duas entries — a conta ajustada e uma conta `EQUITY` de reconciliação — em vez de um lançamento sem contrapartida. O comportamento idempotente por data e conta SHALL ser preservado: um novo ajuste na mesma data e conta atualiza o ajuste existente, e um ajuste que se anula é removido.

#### Scenario: Ajuste cria par balanceado
- **WHEN** o usuário ajusta o saldo de uma conta para um valor maior que o atual
- **THEN** o sistema registra uma operação com a conta debitada e a conta `EQUITY:Reconciliação` creditada pela diferença, somando zero

#### Scenario: Ajuste idempotente na mesma data
- **WHEN** já existe um ajuste na mesma conta e data e um novo ajuste é aplicado
- **THEN** o ajuste existente é atualizado (ou removido, se o resultado se anula) em vez de criar um segundo ajuste
