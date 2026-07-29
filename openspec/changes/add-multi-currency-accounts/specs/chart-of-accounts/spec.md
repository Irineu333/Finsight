## MODIFIED Requirements

### Requirement: Plano de contas unificado
O sistema SHALL representar toda conta e cartão como uma `Account` pertencente a um plano de contas único, cada `Account` com um `type` do conjunto fechado `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}` e uma `currency`. Conta corrente, poupança, dinheiro, investimento e valores a receber de terceiros SHALL ter `type = ASSET`; cartão de crédito, empréstimo e valores a pagar a terceiros SHALL ter `type = LIABILITY`; contas de reconciliação e de conversão cambial SHALL ter `type = EQUITY`. Nenhum outro tipo de conta SHALL existir.

Toda `Account` SHALL estar denominada em **exatamente uma** moeda, e isso SHALL valer para **toda** linha do plano, sem exceção para as contas de sistema. Uma conta MUST NOT acolher entries de mais de uma moeda.

O plano de contas SHALL conter **apenas o que é contábil**. Categoria MUST NOT ser uma linha do plano de contas: a classificação de um lançamento por rótulo do usuário SHALL ser expressa por dimensão, não por conta. O plano SHALL conter exatamente **duas** contas nominais **por moeda em uso** — uma de `type = EXPENSE` e uma de `type = INCOME` — sobre as quais toda perna de contrapartida nominal daquela moeda posta, qualquer que seja a sua classificação. Uma moeda que nenhuma conta usa MUST NOT ter contas nominais no plano: elas nascem sob demanda, como as demais contas de sistema.

O plano de contas SHALL distinguir as contas **monetárias** (`ASSET` e `LIABILITY` — onde o dinheiro está, e que o usuário escolhe ao registrar um lançamento) das contas de **contrapartida** (`INCOME`, `EXPENSE` e `EQUITY` — por que o dinheiro se moveu, sintetizadas pelo sistema). Essa distinção SHALL ser expressa no próprio tipo de conta, e MUST NOT ser reimplementada caso a caso pelos consumidores.

#### Scenario: Conta financeira do usuário
- **WHEN** o usuário cria uma conta corrente
- **THEN** ela é registrada no plano de contas com `type = ASSET` e a moeda escolhida

#### Scenario: Cartão de crédito como passivo
- **WHEN** o usuário cria um cartão de crédito
- **THEN** ele é registrado no plano de contas com `type = LIABILITY` e a moeda escolhida

#### Scenario: Categoria não entra no plano de contas
- **WHEN** o usuário cria uma categoria de despesa ou de receita
- **THEN** nenhuma conta é criada, e a categoria passa a existir como dimensão

#### Scenario: Plano de contas contém duas nominais por moeda
- **WHEN** o plano de contas é inspecionado, com qualquer número de categorias existentes e lançamentos em duas moedas
- **THEN** ele contém exatamente uma conta `EXPENSE` e uma conta `INCOME` **por moeda**, e nenhuma para uma moeda sem lançamentos

#### Scenario: Nenhuma conta acolhe duas moedas
- **WHEN** qualquer conta do plano — de usuário ou de sistema — tem as suas entries inspecionadas
- **THEN** todas estão na moeda daquela conta

#### Scenario: Contas monetárias e de contrapartida
- **WHEN** o sistema precisa saber quais contas de uma operação representam dinheiro
- **THEN** as contas `ASSET` e `LIABILITY` são identificadas como monetárias, e as `INCOME`, `EXPENSE` e `EQUITY` como contrapartida

### Requirement: Contas de sistema
O sistema SHALL prover, **por moeda**, uma conta de `type = EQUITY` para reconciliação de saldo usada como contrapartida de ajustes, uma conta de `type = EQUITY` para **conversão cambial**, e as duas contas nominais (`EXPENSE` e `INCOME`) sobre as quais toda contrapartida nominal daquela moeda posta. Essas contas SHALL existir de forma garantida quando um lançamento que as referencie for registrado, sendo criadas sob demanda ou semeadas, e MUST NOT ser apagáveis pelo usuário enquanto houver lançamentos que as referenciem.

Uma conta de sistema SHALL ser identificada pela tripla `(type, nome, moeda)`. O nome SHALL permanecer chave de busca e MUST NOT ser renderizado ao usuário, para nenhuma das contas de sistema — a de conversão inclusive.

O sistema MUST NOT prover conta de sistema para representar a ausência de classificação: um lançamento sem categoria SHALL ser uma perna nominal **sem dimensão**, e não uma perna numa conta de sistema dedicada. O sistema MUST NOT prover uma conta de sistema de "saldo inicial" enquanto não existir um conceito de saldo inicial exposto ao usuário: contas de sistema SHALL existir apenas quando houver um uso real que as referencie.

#### Scenario: Ajuste referencia conta de reconciliação
- **WHEN** um ajuste de saldo é registrado numa conta em USD e ainda não existe a conta `EQUITY` de reconciliação em USD
- **THEN** o sistema garante a existência dessa conta, naquela moeda, antes de persistir a operação

#### Scenario: Conversão referencia conta de conversão
- **WHEN** uma transação que atravessa moedas é registrada e ainda não existem as contas de conversão das moedas envolvidas
- **THEN** o sistema garante a existência de uma conta de conversão **por moeda envolvida** antes de persistir a operação

#### Scenario: Conta de sistema não é renderizada
- **WHEN** uma transação com perna em conta de conversão é exibida
- **THEN** o nome da conta de conversão não aparece em nenhuma superfície

#### Scenario: Lançamento sem categoria não cria conta
- **WHEN** uma despesa sem categoria é registrada
- **THEN** a contrapartida posta na conta nominal `EXPENSE` da moeda da conta, sem dimensão, e nenhuma conta de "sem categoria" existe no plano

#### Scenario: Sem conta de saldo inicial
- **WHEN** o plano de contas é inspecionado após a migração
- **THEN** não existe conta de sistema de "saldo inicial", pois nenhuma operação a referencia

## ADDED Requirements

### Requirement: A moeda de uma conta é imutável a partir do primeiro lançamento

A moeda de uma `Account` SHALL ser escolhida na sua criação e MUST NOT ser alterada a partir do momento em que qualquer entry a referencie. Enquanto a conta não tiver lançamento algum, a sua moeda MAY ser alterada livremente.

O fato que governa a recusa SHALL ser o **mesmo** que já decide apagar-vs-arquivar uma conta — a existência de entries que a referenciam —, consultado da mesma implementação de domínio. Nenhuma tela SHALL rederivar essa condição.

A interface SHALL apresentar a moeda travada, com o motivo, em vez de oferecer uma edição que o domínio recusaria.

A regra existe porque trocar a moeda de uma conta com história não reinterpreta um dado: ela reescreve em silêncio o significado de toda entry já gravada, que passa a valer outra coisa sem que nada registre o que valia antes.

#### Scenario: Conta nova permite trocar a moeda
- **WHEN** o usuário edita uma conta que ainda não tem nenhum lançamento
- **THEN** a moeda é editável

#### Scenario: Conta com lançamento recusa a troca
- **WHEN** o usuário edita uma conta que já possui ao menos um lançamento
- **THEN** a moeda é apresentada travada, com o motivo, e uma tentativa de alterá-la é recusada pelo domínio

#### Scenario: Cartão segue a mesma regra pela sua conta
- **WHEN** o usuário edita um cartão cuja conta `LIABILITY` já possui lançamentos
- **THEN** a moeda é apresentada travada, pela mesma regra e pelo mesmo fato

#### Scenario: Oferta e execução concordam
- **WHEN** a tela decide se oferece a edição da moeda
- **THEN** ela consulta a mesma implementação que o domínio usaria para recusá-la, e nunca oferece o que seria recusado
