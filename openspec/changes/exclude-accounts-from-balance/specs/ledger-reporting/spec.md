## MODIFIED Requirements

### Requirement: Saldo até um mês por natureza de conta

O saldo acumulado até um mês SHALL ser derivável para **qualquer** natureza do plano de contas, pelo mesmo mecanismo de soma de entries, e MUST NOT existir apenas para `ASSET`.

A leitura SHALL ser expressa em vocabulário de razão — natureza de conta e mês —, e MUST NOT nomear conta corrente, cartão ou qualquer fachada. MUST NOT existir uma segunda consulta que derive o mesmo acumulado com a natureza fixada no seu próprio texto.

A leitura SHALL admitir um conjunto de **contas a excluir** da soma, expresso por identidade de conta do plano. Excluir por identidade permanece vocabulário de razão: uma conta do plano é entidade do razão, ao contrário de uma fachada. O razão MUST NOT conhecer o **motivo** da exclusão, e a leitura MUST NOT ganhar parâmetro que nomeie preferência, widget, tela ou intenção de quem a chama.

O conjunto vazio SHALL ser o padrão e SHALL produzir exatamente o mesmo resultado que a leitura produzia sem o parâmetro — toda conta daquela natureza. A exclusão MUST NOT introduzir um segundo caminho para o acumulado: ela SHALL ser honrada **dentro** da mesma consulta parametrizada, e MUST NOT ser obtida subtraindo saldos de contas individuais de um total previamente somado, nem somando conta a conta fora do razão.

O resultado da leitura com exclusão SHALL continuar expresso **por moeda**, como toda leitura que atravessa contas, e o agrupamento por moeda MUST NOT ser realizado fora do razão em consequência da exclusão.

O saldo consolidado de `ASSET` e `LIABILITY` até um mês SHALL ser obtido pela **soma** dos saldos das duas naturezas, sem agregado adicional e sem regra de sinal própria, já que passivos são registrados em crédito.

#### Scenario: Saldo acumulado de passivos
- **WHEN** o saldo acumulado das contas `LIABILITY` até um mês é solicitado
- **THEN** o sistema retorna a soma das entries dessas contas até aquele mês, pelo mesmo mecanismo usado para `ASSET`

#### Scenario: Consolidado sem agregado novo
- **WHEN** o saldo consolidado de ativos e passivos até um mês é necessário
- **THEN** ele é obtido somando as duas leituras por natureza, e MUST NOT existir uma terceira consulta dedicada a esse total

#### Scenario: Sem caminho duplicado para ativos
- **WHEN** o saldo acumulado das contas `ASSET` até um mês é solicitado
- **THEN** ele deriva da mesma leitura parametrizada, e não de uma consulta paralela

#### Scenario: Excluir nada é o comportamento de sempre
- **WHEN** o saldo acumulado de uma natureza até um mês é solicitado com conjunto de exclusão vazio
- **THEN** o resultado é idêntico, valor a valor e moeda a moeda, ao que a leitura devolvia antes de admitir o parâmetro

#### Scenario: Conta excluída não entra na soma
- **WHEN** o saldo acumulado das contas `ASSET` até um mês é solicitado excluindo uma conta com lançamentos
- **THEN** as entries dessa conta não participam do resultado, e as das demais contas `ASSET` participam integralmente

#### Scenario: Exclusão sem segunda consulta
- **WHEN** a origem do acumulado com exclusão é inspecionada
- **THEN** ele deriva da mesma consulta parametrizada que o acumulado sem exclusão, e não de uma subtração entre um total e saldos de contas individuais

#### Scenario: Exclusão preserva a expressão por moeda
- **WHEN** o saldo acumulado é solicitado com exclusão sobre contas de moedas distintas
- **THEN** o resultado continua expresso por moeda, agrupado pelo razão, e nenhuma moeda é somada com outra

#### Scenario: Id que não corresponde a conta alguma
- **WHEN** o saldo acumulado é solicitado excluindo um identificador que não corresponde a nenhuma conta do plano
- **THEN** nenhuma conta é excluída e o resultado é o mesmo do conjunto vazio

#### Scenario: O razão não conhece o motivo da exclusão
- **WHEN** a assinatura da leitura é inspecionada
- **THEN** ela recebe identidades de conta do plano, e nenhum parâmetro que nomeie preferência de exibição, widget ou fachada
