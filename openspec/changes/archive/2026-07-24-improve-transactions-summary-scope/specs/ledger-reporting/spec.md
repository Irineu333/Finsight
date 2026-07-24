## ADDED Requirements

### Requirement: Saldo até um mês por natureza de conta

O saldo acumulado até um mês SHALL ser derivável para **qualquer** natureza do plano de contas, pelo mesmo mecanismo de soma de entries, e MUST NOT existir apenas para `ASSET`.

A leitura SHALL ser expressa em vocabulário de razão — natureza de conta e mês —, e MUST NOT nomear conta corrente, cartão ou qualquer fachada. MUST NOT existir uma segunda consulta que derive o mesmo acumulado com a natureza fixada no seu próprio texto.

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

### Requirement: Fluxos do mês simétricos entre naturezas

Quando o razão reportar os fluxos de um mês para uma natureza de conta, o conjunto de classes reportadas SHALL ser o mesmo entre naturezas que registrem as mesmas formas de lançamento. Em particular, o **ajuste** — a contrapartida em `EQUITY` — SHALL ser reportado para toda natureza em que possa ocorrer, e MUST NOT ser reportado para uma natureza e omitido em outra.

Este requisito rege a **simetria entre as leituras de fluxo**; ele MUST NOT ser lido como obrigação de que os fluxos reportados esgotem as entries do período, já que uma leitura pode legitimamente excluir movimento interno ao seu perímetro.

#### Scenario: Ajuste de fatura é reportado
- **WHEN** os fluxos do mês das contas `LIABILITY` são solicitados em um mês que contém ajuste de fatura
- **THEN** o ajuste é reportado como classe própria, com sinal preservado, e não desaparece do relatório

#### Scenario: Paridade de classes entre naturezas
- **WHEN** as leituras de fluxo mensal de `ASSET` e de `LIABILITY` são comparadas
- **THEN** ambas reportam ajuste como classe própria, e nenhuma forma de lançamento registrável naquela natureza fica sem classe
