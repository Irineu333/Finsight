# chart-of-accounts Specification

## Purpose

O plano de contas unificado: toda conta, cartão e categoria é uma `Account` com um `type` do conjunto **fechado** `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}`. Define "categoria" e "cartão" como fachadas projetadas sobre esse plano, ligadas à sua conta, e a conta de sistema usada como contrapartida de ajustes e baixas. É a base sobre a qual o razão (`balanced-ledger`) registra entries e da qual as leituras (`ledger-reporting`) derivam.

## Requirements

### Requirement: Plano de contas unificado
O sistema SHALL representar toda conta, cartão e categoria como uma `Account` pertencente a um plano de contas único, cada `Account` com um `type` do conjunto fechado `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}` e uma `currency`. Conta corrente, poupança, dinheiro, investimento e valores a receber de terceiros SHALL ter `type = ASSET`; cartão de crédito, empréstimo e valores a pagar a terceiros SHALL ter `type = LIABILITY`; categorias de receita SHALL ter `type = INCOME` e de despesa `type = EXPENSE`; contas de reconciliação e saldo inicial SHALL ter `type = EQUITY`. Nenhum outro tipo de conta SHALL existir.

#### Scenario: Conta financeira do usuário
- **WHEN** o usuário cria uma conta corrente
- **THEN** ela é registrada no plano de contas com `type = ASSET`

#### Scenario: Cartão de crédito como passivo
- **WHEN** o usuário cria um cartão de crédito
- **THEN** ele é registrado no plano de contas com `type = LIABILITY`

#### Scenario: Categoria como conta interna
- **WHEN** o usuário cria uma categoria de despesa
- **THEN** ela é registrada no plano de contas com `type = EXPENSE`, e uma categoria de receita com `type = INCOME`

### Requirement: Fachada de categoria e cartão preservada
A interface do usuário SHALL continuar apresentando "categoria" e "cartão" como conceitos, projetados sobre as `Account` internas de tipo `INCOME`/`EXPENSE` e `LIABILITY`. O usuário MUST NOT precisar conhecer os tipos contábeis nem os termos débito/crédito para operar o app.

#### Scenario: Usuário classifica uma despesa
- **WHEN** o usuário escolhe a categoria "Alimentação" em uma despesa
- **THEN** a UI mostra "Alimentação" como categoria, enquanto internamente a operação referencia a `Account` `EXPENSE` correspondente

#### Scenario: Compatibilidade categoria x sentido do lançamento
- **WHEN** uma categoria de tipo `EXPENSE` é associada a um lançamento
- **THEN** o sistema SHALL aceitá-la apenas em lançamentos que aumentam despesa, preservando a regra de coerência entre a natureza da conta e o sentido do lançamento (equivalente ao `isAccept` atual)

### Requirement: Contas de sistema
O sistema SHALL prover contas de `type = EQUITY` para reconciliação de saldo e saldo inicial, usadas como contrapartida de ajustes. Essas contas SHALL existir de forma garantida quando um ajuste for registrado, sendo criadas sob demanda ou semeadas, e MUST NOT ser apagáveis pelo usuário enquanto houver lançamentos que as referenciem.

#### Scenario: Ajuste referencia conta de reconciliação
- **WHEN** um ajuste de saldo é registrado e ainda não existe a conta `EQUITY` de reconciliação
- **THEN** o sistema garante a existência dessa conta antes de persistir a operação
