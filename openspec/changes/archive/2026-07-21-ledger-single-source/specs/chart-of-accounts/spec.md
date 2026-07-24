## MODIFIED Requirements

### Requirement: Contas de sistema
O sistema SHALL prover uma conta de `type = EQUITY` para reconciliação de saldo, usada como contrapartida de ajustes. Essa conta SHALL existir de forma garantida quando um ajuste for registrado, sendo criada sob demanda ou semeada, e MUST NOT ser apagável pelo usuário enquanto houver lançamentos que a referenciem. O sistema MUST NOT prover uma conta de sistema de "saldo inicial" enquanto não existir um conceito de saldo inicial exposto ao usuário: contas de sistema SHALL existir apenas quando houver um uso real que as referencie.

#### Scenario: Ajuste referencia conta de reconciliação
- **WHEN** um ajuste de saldo é registrado e ainda não existe a conta `EQUITY` de reconciliação
- **THEN** o sistema garante a existência dessa conta antes de persistir a operação

#### Scenario: Sem conta de saldo inicial
- **WHEN** o plano de contas é inspecionado após a migração
- **THEN** não existe conta de sistema de "saldo inicial", pois nenhuma operação a referencia

### Requirement: Plano de contas unificado
O sistema SHALL representar toda conta, cartão e categoria como uma `Account` pertencente a um plano de contas único, cada `Account` com um `type` do conjunto fechado `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}` e uma `currency`. Conta corrente, poupança, dinheiro, investimento e valores a receber de terceiros SHALL ter `type = ASSET`; cartão de crédito, empréstimo e valores a pagar a terceiros SHALL ter `type = LIABILITY`; categorias de receita SHALL ter `type = INCOME` e de despesa `type = EXPENSE`; contas de reconciliação SHALL ter `type = EQUITY`. Nenhum outro tipo de conta SHALL existir.

O plano de contas SHALL distinguir as contas **monetárias** (`ASSET` e `LIABILITY` — onde o dinheiro está, e que o usuário escolhe ao registrar um lançamento) das contas de **contrapartida** (`INCOME`, `EXPENSE` e `EQUITY` — por que o dinheiro se moveu, sintetizadas pelo sistema). Essa distinção SHALL ser expressa no próprio tipo de conta, e MUST NOT ser reimplementada caso a caso pelos consumidores.

#### Scenario: Conta financeira do usuário
- **WHEN** o usuário cria uma conta corrente
- **THEN** ela é registrada no plano de contas com `type = ASSET`

#### Scenario: Cartão de crédito como passivo
- **WHEN** o usuário cria um cartão de crédito
- **THEN** ele é registrado no plano de contas com `type = LIABILITY`

#### Scenario: Categoria como conta interna
- **WHEN** o usuário cria uma categoria de despesa
- **THEN** ela é registrada no plano de contas com `type = EXPENSE`, e uma categoria de receita com `type = INCOME`

#### Scenario: Contas monetárias e de contrapartida
- **WHEN** o sistema precisa saber quais contas de uma operação representam dinheiro
- **THEN** as contas `ASSET` e `LIABILITY` são identificadas como monetárias, e as `INCOME`, `EXPENSE` e `EQUITY` como contrapartida
