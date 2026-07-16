## MODIFIED Requirements

### Requirement: Operação como conjunto de entries balanceadas
Uma transação SHALL ser o agregado que **possui** um conjunto de `Entry`, cada `Entry` referenciando uma `Account`, com um `amount` assinado (na menor unidade da moeda), uma `currency` e, quando pertencente ao sub-razão de um cartão, a fatura que a acolhe. Para cada moeda presente em uma transação, a soma dos `amount` das entries daquela moeda SHALL ser exatamente zero. Uma transação MUST NOT ter menos de duas entries.

As entries SHALL ser a **única** representação das pernas de uma transação: o sistema MUST NOT manter um modelo de perna paralelo espelhando o razão, e MUST NOT persistir a mesma operação em dois modelos. As entries de uma transação SHALL ser legíveis como objetos de domínio — hidratadas com sua `Account` — e não apenas como agregados numéricos.

#### Scenario: Despesa balanceada
- **WHEN** o usuário registra uma despesa de 50 na categoria "Alimentação" a partir da conta corrente
- **THEN** a transação contém duas entries que somam zero: `EXPENSE:Alimentação` debitada e `ASSET:Conta` creditada

#### Scenario: Transferência balanceada
- **WHEN** o usuário transfere 100 da conta A para a conta B
- **THEN** a transação contém `ASSET:B` debitada e `ASSET:A` creditada, somando zero

#### Scenario: Pagamento de fatura balanceado
- **WHEN** o usuário paga 50 da fatura do cartão a partir da conta corrente
- **THEN** a transação contém `LIABILITY:Cartão` debitada e `ASSET:Conta` creditada, somando zero

#### Scenario: Entries legíveis como objetos
- **WHEN** uma transação é lida do repositório
- **THEN** suas entries são retornadas hidratadas com suas `Account`, permitindo derivar rótulo e editabilidade sem consultar nenhum modelo legado

#### Scenario: Sem modelo de perna paralelo
- **WHEN** uma transação é persistida
- **THEN** apenas suas entries são gravadas, e nenhum modelo de perna legado é espelhado

### Requirement: Tipo de operação derivado dos tipos de conta
O sistema SHALL derivar o rótulo de uma transação a partir dos tipos das contas envolvidas nas suas entries, e MUST NOT persistir esse rótulo como estado independente. A derivação SHALL ser uma função **total** sobre o conjunto `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`: uma contrapartida `EQUITY` SHALL produzir `ADJUSTMENT`; `ASSET`→`EXPENSE` SHALL ser despesa; `INCOME`→`ASSET` receita; `ASSET`→`LIABILITY` pagamento; `ASSET`→`ASSET` transferência. A contrapartida `EQUITY` SHALL ser avaliada antes do caso de transferência, de modo que um ajuste nunca seja rotulado como transferência.

SHALL existir uma única derivação de rótulo no sistema. MUST NOT coexistir uma segunda classificação derivada, parcial ou paralela, para o mesmo fim.

#### Scenario: Rótulo derivado de uma transferência
- **WHEN** uma transação tem duas entries, ambas em contas `ASSET`
- **THEN** o sistema a apresenta como transferência sem consultar nenhum campo de tipo persistido

#### Scenario: Rótulo derivado de um pagamento de fatura
- **WHEN** uma transação move valor de uma conta `ASSET` para uma conta `LIABILITY`
- **THEN** o sistema a apresenta como pagamento

#### Scenario: Rótulo derivado de um ajuste de saldo
- **WHEN** uma transação tem uma entry em conta `ASSET` e a contrapartida em conta `EQUITY` de reconciliação
- **THEN** o sistema a apresenta como ajuste, e MUST NOT apresentá-la como transferência

#### Scenario: Derivação é total
- **WHEN** qualquer transação válida do razão tem seu rótulo derivado
- **THEN** o resultado pertence a `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`, sem caso não coberto

## ADDED Requirements

### Requirement: Editabilidade derivada das pernas monetárias
A editabilidade de uma transação SHALL ser derivada da contagem das suas entries em contas **monetárias** (`ASSET`/`LIABILITY`): uma transação com exatamente uma perna monetária SHALL ser editável; com mais de uma, MUST NOT ser editável, devendo ser removida e refeita. A editabilidade MUST NOT ser persistida nem derivada da contagem total de entries, já que toda transação balanceada tem ao menos duas.

#### Scenario: Despesa é editável
- **WHEN** uma despesa em conta (`ASSET` + `EXPENSE`) é exibida
- **THEN** ela é editável, por ter exatamente uma perna monetária

#### Scenario: Compra no cartão é editável
- **WHEN** uma compra no cartão (`LIABILITY` + `EXPENSE`) é exibida
- **THEN** ela é editável, por ter exatamente uma perna monetária

#### Scenario: Ajuste é editável
- **WHEN** um ajuste de saldo (`ASSET` + `EQUITY`) é exibido
- **THEN** ele é editável, por ter exatamente uma perna monetária

#### Scenario: Transferência não é editável
- **WHEN** uma transferência (`ASSET` + `ASSET`) é exibida
- **THEN** ela não é editável, por ter duas pernas monetárias

#### Scenario: Pagamento de fatura não é editável
- **WHEN** um pagamento de fatura (`ASSET` + `LIABILITY`) é exibido
- **THEN** ele não é editável, por ter duas pernas monetárias

### Requirement: Classificação de entrada distinta da de exibição
O vocabulário com que o usuário **registra** um lançamento (despesa, receita, ajuste) SHALL pertencer à camada de apresentação e MUST NOT ser persistido como estado da transação. O sistema SHALL traduzir esse vocabulário de entrada em entries balanceadas no momento da escrita, e SHALL derivar o vocabulário de exibição das entries no momento da leitura. O vocabulário de entrada MUST NOT ser unificado com o de exibição, por serem conjuntos distintos.

#### Scenario: Entrada vira entries
- **WHEN** o usuário registra uma despesa escolhendo categoria e conta
- **THEN** o sistema traduz a intenção em entries balanceadas, sem gravar a escolha "despesa" como campo

#### Scenario: Exibição vem das entries
- **WHEN** a mesma transação é exibida
- **THEN** o rótulo é derivado das entries, e não lido de um campo persistido
