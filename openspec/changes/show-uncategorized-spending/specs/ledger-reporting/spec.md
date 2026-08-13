## MODIFIED Requirements

### Requirement: Gasto por categoria a partir das entries
O gasto (ou receita) de uma categoria em um período SHALL ser derivado da soma das entries que carregam a **dimensão** daquela categoria, usando o mesmo mecanismo de soma do saldo de conta. Não SHALL existir um caminho de cálculo separado para gasto por categoria.

O total das entries **sem dimensão** na conta nominal SHALL ser o total "sem categoria", derivado pelo mesmo mecanismo e sem tratamento especial. A leitura MUST NOT depender de conta dedicada para representar a ausência de classificação.

A assinatura dessa leitura no razão SHALL ser expressa em vocabulário de razão — natureza de conta, período e dimensão — e MUST NOT nomear categoria. A tradução para o vocabulário de categoria pertence à feature dona da fachada.

O razão SHALL oferecer essa leitura na forma **agregada**: um total por dimensão para uma natureza
de conta nominal em um mês, com a ausência de dimensão como uma chave do mesmo agregado. Um
detalhamento de N categorias SHALL custar uma leitura, não N, e o total sem classificação SHALL ser
um grupo desse agregado — nunca uma leitura à parte, que poderia divergir do resto.

O filtro por natureza de conta nominal SHALL ser obrigatório nessa leitura: sem ele, a ausência de
dimensão alcançaria toda perna não classificada do razão — de ativo, de passivo, de conversão — e o
total deixaria de ser um total de classificação.

#### Scenario: Total gasto em uma categoria
- **WHEN** o total gasto na categoria "Alimentação" em um mês é solicitado
- **THEN** o sistema retorna a soma das entries que carregam a dimensão de "Alimentação" naquele período

#### Scenario: Total sem categoria
- **WHEN** o total de despesas sem categoria em um mês é solicitado
- **THEN** o sistema retorna a soma das entries sem dimensão na conta nominal `EXPENSE` naquele período, pelo mesmo mecanismo

#### Scenario: Reembolso reduz o gasto da categoria
- **WHEN** existe uma entry de crédito carregando a dimensão de "Alimentação" (contrapartida de um reembolso)
- **THEN** o total da categoria é reduzido por essa entry, sem tratamento especial

#### Scenario: Um mês inteiro em uma leitura
- **WHEN** os totais por dimensão do nominal `EXPENSE` de um mês são solicitados
- **THEN** o sistema retorna um total por dimensão e um total para a ausência de dimensão, tudo num
  único agregado por moeda

#### Scenario: Compra de cartão sem categoria conta como sem categoria
- **WHEN** uma compra no cartão é registrada sem categoria
- **THEN** a sua perna nominal `EXPENSE`, que não carrega dimensão porque a dimensão da fatura pousa
  na perna `LIABILITY`, entra no total sem classificação do mês

#### Scenario: O resíduo de conversão fica de fora
- **WHEN** uma transação entre moedas deixa resíduo na conta `CONVERSION`, sem dimensão
- **THEN** esse resíduo não entra no total sem classificação, porque `CONVERSION` não é natureza
  nominal

#### Scenario: Perna de ativo sem dimensão fica de fora
- **WHEN** uma despesa sem categoria é paga de uma conta corrente
- **THEN** a perna `ASSET`, que também não carrega dimensão, é contada uma única vez pelo lado
  nominal e não duplica o total sem classificação
