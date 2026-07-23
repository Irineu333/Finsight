## MODIFIED Requirements

### Requirement: Plano de contas unificado
O sistema SHALL representar toda conta e cartão como uma `Account` pertencente a um plano de contas único, cada `Account` com um `type` do conjunto fechado `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}` e uma `currency`. Conta corrente, poupança, dinheiro, investimento e valores a receber de terceiros SHALL ter `type = ASSET`; cartão de crédito, empréstimo e valores a pagar a terceiros SHALL ter `type = LIABILITY`; contas de reconciliação SHALL ter `type = EQUITY`. Nenhum outro tipo de conta SHALL existir.

O plano de contas SHALL conter **apenas o que é contábil**. Categoria MUST NOT ser uma linha do plano de contas: a classificação de um lançamento por rótulo do usuário SHALL ser expressa por dimensão, não por conta. O plano SHALL conter exatamente **duas** contas nominais em todo o app — uma de `type = EXPENSE` e uma de `type = INCOME` — sobre as quais toda perna de contrapartida nominal posta, qualquer que seja a sua classificação.

O plano de contas SHALL distinguir as contas **monetárias** (`ASSET` e `LIABILITY` — onde o dinheiro está, e que o usuário escolhe ao registrar um lançamento) das contas de **contrapartida** (`INCOME`, `EXPENSE` e `EQUITY` — por que o dinheiro se moveu, sintetizadas pelo sistema). Essa distinção SHALL ser expressa no próprio tipo de conta, e MUST NOT ser reimplementada caso a caso pelos consumidores.

#### Scenario: Conta financeira do usuário
- **WHEN** o usuário cria uma conta corrente
- **THEN** ela é registrada no plano de contas com `type = ASSET`

#### Scenario: Cartão de crédito como passivo
- **WHEN** o usuário cria um cartão de crédito
- **THEN** ele é registrado no plano de contas com `type = LIABILITY`

#### Scenario: Categoria não entra no plano de contas
- **WHEN** o usuário cria uma categoria de despesa ou de receita
- **THEN** nenhuma conta é criada, e a categoria passa a existir como dimensão

#### Scenario: Plano de contas contém duas nominais
- **WHEN** o plano de contas é inspecionado, com qualquer número de categorias existentes
- **THEN** ele contém exatamente uma conta `EXPENSE` e uma conta `INCOME`

#### Scenario: Contas monetárias e de contrapartida
- **WHEN** o sistema precisa saber quais contas de uma operação representam dinheiro
- **THEN** as contas `ASSET` e `LIABILITY` são identificadas como monetárias, e as `INCOME`, `EXPENSE` e `EQUITY` como contrapartida

### Requirement: Fachada de categoria e cartão preservada
A interface do usuário SHALL continuar apresentando "categoria" e "cartão" como conceitos. O cartão SHALL projetar sobre a `Account` de tipo `LIABILITY` que o representa; a categoria SHALL projetar sobre a sua **dimensão**, e sobre a conta nominal correspondente à sua natureza. O usuário MUST NOT precisar conhecer os tipos contábeis, as dimensões nem os termos débito/crédito para operar o app.

A natureza `INCOME`/`EXPENSE` da categoria SHALL ser estado primário da fachada de categoria, e é o que decide em qual conta nominal a perna posta. Esta é uma **exceção explícita e documentada** à regra de que toda regra derivável tem seu dono no domínio, justificada por essa natureza ser declaração do usuário no momento da criação, e não fato derivável do razão. A exceção SHALL estar restrita a este único dado: nenhuma outra classificação contábil SHALL migrar para a fachada por analogia com ela.

O estado de arquivamento da categoria SHALL ser próprio da fachada, deixando de ser lido do plano de contas. Isso MUST NOT alterar comportamento: a fronteira de escrita nunca aplicou verificação de fechamento a categorias, por uma categoria poder ser arquivada com qualquer saldo.

#### Scenario: Usuário classifica uma despesa
- **WHEN** o usuário escolhe a categoria "Alimentação" em uma despesa
- **THEN** a UI mostra "Alimentação" como categoria, enquanto internamente a perna nominal posta na conta `EXPENSE` única, carregando a dimensão de "Alimentação"

#### Scenario: Natureza da categoria escolhe a conta nominal
- **WHEN** um lançamento é escrito com uma categoria de natureza `INCOME`
- **THEN** a perna de contrapartida posta na conta nominal `INCOME`, e com natureza `EXPENSE` posta na conta nominal `EXPENSE`

#### Scenario: Compatibilidade categoria x sentido do lançamento
- **WHEN** uma categoria de natureza `EXPENSE` é associada a um lançamento
- **THEN** o sistema SHALL aceitá-la apenas em lançamentos que aumentam despesa, preservando a regra de coerência entre a natureza da categoria e o sentido do lançamento

#### Scenario: Arquivamento de categoria não passa pela conta
- **WHEN** uma categoria é arquivada
- **THEN** o estado é gravado na própria fachada, nenhuma conta muda de estado, e a escrita de lançamentos que a referenciam continua permitida como antes

### Requirement: Contas de sistema
O sistema SHALL prover uma conta de `type = EQUITY` para reconciliação de saldo, usada como contrapartida de ajustes, e as duas contas nominais (`EXPENSE` e `INCOME`) sobre as quais toda contrapartida nominal posta. Essas contas SHALL existir de forma garantida quando um lançamento que as referencie for registrado, sendo criadas sob demanda ou semeadas, e MUST NOT ser apagáveis pelo usuário enquanto houver lançamentos que as referenciem.

O sistema MUST NOT prover conta de sistema para representar a ausência de classificação: um lançamento sem categoria SHALL ser uma perna nominal **sem dimensão**, e não uma perna numa conta de sistema dedicada. O sistema MUST NOT prover uma conta de sistema de "saldo inicial" enquanto não existir um conceito de saldo inicial exposto ao usuário: contas de sistema SHALL existir apenas quando houver um uso real que as referencie.

#### Scenario: Ajuste referencia conta de reconciliação
- **WHEN** um ajuste de saldo é registrado e ainda não existe a conta `EQUITY` de reconciliação
- **THEN** o sistema garante a existência dessa conta antes de persistir a operação

#### Scenario: Lançamento sem categoria não cria conta
- **WHEN** uma despesa sem categoria é registrada
- **THEN** a contrapartida posta na conta nominal `EXPENSE` sem dimensão, e nenhuma conta de "sem categoria" existe no plano

#### Scenario: Sem conta de saldo inicial
- **WHEN** o plano de contas é inspecionado após a migração
- **THEN** não existe conta de sistema de "saldo inicial", pois nenhuma operação a referencia
