# ledger-reporting Specification

## Purpose

A derivação de toda leitura de dinheiro — saldo de conta, saldo devido de fatura, gasto por categoria e patrimônio líquido — a partir de um mecanismo único: `Σ entries` da conta. Substitui `signedImpact()`, o cálculo invertido ad-hoc de fatura e o tratamento especial de ajuste. Consome o razão (`balanced-ledger`) sobre o plano de contas (`chart-of-accounts`).

## Requirements

### Requirement: Saldo de conta a partir das entries
O saldo de qualquer conta SHALL ser calculado exclusivamente como a soma dos `amount` das entries que a referenciam, aplicando a convenção débito-positivo, sem funções de sinal específicas por tipo de lançamento. O cálculo de saldo MUST NOT depender de `signedImpact()` nem de qualquer regra de sinal invertida específica de cartão.

#### Scenario: Saldo de conta corrente
- **WHEN** o saldo de uma conta `ASSET` é solicitado
- **THEN** o sistema retorna a soma dos `amount` das entries daquela conta até a data-alvo

#### Scenario: Saldo de fatura sem sinal invertido ad-hoc
- **WHEN** o saldo devido de uma fatura de cartão é solicitado
- **THEN** o sistema o deriva da soma das entries da conta `LIABILITY` do cartão no período, sem aplicar um `-signedImpact()` especial

### Requirement: Gasto por categoria a partir das entries
O gasto (ou receita) de uma categoria em um período SHALL ser derivado da soma das entries da conta `EXPENSE` (ou `INCOME`) correspondente, usando o mesmo mecanismo do saldo de conta. Não SHALL existir um caminho de cálculo separado para gasto por categoria.

#### Scenario: Total gasto em uma categoria
- **WHEN** o total gasto na categoria "Alimentação" em um mês é solicitado
- **THEN** o sistema retorna a soma das entries da conta `EXPENSE:Alimentação` naquele período

#### Scenario: Reembolso reduz o gasto da categoria
- **WHEN** existe uma entry de crédito na conta `EXPENSE:Alimentação` (contrapartida de um reembolso)
- **THEN** o total gasto na categoria é reduzido por essa entry, sem tratamento especial

### Requirement: Patrimônio líquido a partir das entries
O patrimônio líquido SHALL ser derivado do plano de contas como a soma dos saldos das contas `ASSET` menos a soma dos saldos das contas `LIABILITY`, usando o mesmo mecanismo de saldo das demais leituras.

#### Scenario: Patrimônio líquido consolidado
- **WHEN** o patrimônio líquido é solicitado
- **THEN** o sistema retorna a soma dos saldos das contas `ASSET` menos a soma dos saldos das contas `LIABILITY`

### Requirement: Ajuste sem tratamento especial em relatórios
Os relatórios e leituras MUST NOT tratar ajustes como um caso especial. A contrapartida de reconciliação de um ajuste SHALL ser uma conta `EQUITY` como qualquer outra no plano de contas, entrando nas leituras pelo mesmo mecanismo de soma de entries.

#### Scenario: Ajuste entra pelo mecanismo comum
- **WHEN** um relatório de saldo é computado sobre contas que incluem lançamentos de ajuste
- **THEN** os ajustes contribuem via suas entries normais, sem ramo condicional específico para o tipo ajuste
