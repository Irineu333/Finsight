## ADDED Requirements

### Requirement: Saldo até um mês por natureza de conta

O saldo acumulado até um mês SHALL ser derivável para **qualquer** natureza do plano de contas, pelo mesmo mecanismo de soma de entries, e MUST NOT existir apenas para `ASSET`.

A leitura SHALL ser expressa em vocabulário de razão — natureza de conta e mês —, e MUST NOT nomear conta corrente, cartão ou qualquer fachada. SHALL existir **uma única** implementação desse agregado: uma leitura que hoje assuma `ASSET` implicitamente SHALL passar a derivar dessa forma parametrizada, e não a duplicar.

O saldo consolidado de `ASSET` e `LIABILITY` até um mês SHALL ser obtido pela **soma** dos saldos das duas naturezas, sem agregado adicional e sem regra de sinal própria, já que passivos são registrados em crédito.

#### Scenario: Saldo acumulado de passivos
- **WHEN** o saldo acumulado das contas `LIABILITY` até um mês é solicitado
- **THEN** o sistema retorna a soma das entries dessas contas até aquele mês, pelo mesmo mecanismo usado para `ASSET`

#### Scenario: Consolidado sem agregado novo
- **WHEN** o saldo consolidado de ativos e passivos até um mês é necessário
- **THEN** ele é obtido somando as duas leituras por natureza, e MUST NOT existir uma terceira consulta dedicada a esse total

#### Scenario: Sem caminho duplicado para ativos
- **WHEN** o saldo acumulado das contas `ASSET` até um mês é solicitado
- **THEN** ele deriva da mesma leitura parametrizada, e não de uma consulta paralela com a natureza fixada no texto da consulta

### Requirement: Fluxos do mês simétricos entre naturezas

Os fluxos de um mês reportados para uma natureza de conta SHALL classificar **toda** entry do período, sem que exista forma de lançamento que não caia em nenhuma classe. Em particular, o ajuste — a contrapartida em `EQUITY` — SHALL ser reportado para qualquer natureza que o razão possa registrar, e MUST NOT ser reportado para uma natureza e omitido em outra.

A consequência verificável é que, para qualquer natureza, `saldo final = saldo inicial + fluxos do período`.

#### Scenario: Ajuste de fatura é reportado
- **WHEN** os fluxos do mês das contas `LIABILITY` são solicitados em um mês que contém ajuste de fatura
- **THEN** o ajuste é reportado como classe própria, com sinal preservado, e não desaparece do relatório

#### Scenario: Fluxos fecham o saldo em qualquer natureza
- **WHEN** os fluxos do mês de uma natureza são somados ao saldo acumulado até o mês anterior
- **THEN** o resultado é igual ao saldo acumulado até aquele mês
