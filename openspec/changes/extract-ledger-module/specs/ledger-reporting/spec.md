## MODIFIED Requirements

### Requirement: Gasto por categoria a partir das entries
O gasto (ou receita) de uma categoria em um período SHALL ser derivado da soma das entries que carregam a **dimensão** daquela categoria, usando o mesmo mecanismo de soma do saldo de conta. Não SHALL existir um caminho de cálculo separado para gasto por categoria.

O total das entries **sem dimensão** na conta nominal SHALL ser o total "sem categoria", derivado pelo mesmo mecanismo e sem tratamento especial. A leitura MUST NOT depender de conta dedicada para representar a ausência de classificação.

A assinatura dessa leitura no razão SHALL ser expressa em vocabulário de razão — natureza de conta, período e dimensão — e MUST NOT nomear categoria. A tradução para o vocabulário de categoria pertence à feature dona da fachada.

#### Scenario: Total gasto em uma categoria
- **WHEN** o total gasto na categoria "Alimentação" em um mês é solicitado
- **THEN** o sistema retorna a soma das entries que carregam a dimensão de "Alimentação" naquele período

#### Scenario: Total sem categoria
- **WHEN** o total de despesas sem categoria em um mês é solicitado
- **THEN** o sistema retorna a soma das entries sem dimensão na conta nominal `EXPENSE` naquele período, pelo mesmo mecanismo

#### Scenario: Reembolso reduz o gasto da categoria
- **WHEN** existe uma entry de crédito carregando a dimensão de "Alimentação" (contrapartida de um reembolso)
- **THEN** o total da categoria é reduzido por essa entry, sem tratamento especial

### Requirement: Saldo de conta a partir das entries
O saldo de qualquer conta SHALL ser calculado exclusivamente como a soma dos `amount` das entries que a referenciam, aplicando a convenção débito-positivo, sem funções de sinal específicas por tipo de lançamento. O cálculo de saldo MUST NOT depender de nenhuma função de sinal derivada de um modelo legado, nem de qualquer regra de sinal invertida específica de cartão.

SHALL existir **uma única** implementação do cálculo de saldo. MUST NOT existir uma forma alternativa que some lançamentos já carregados em memória, nem qualquer recálculo de saldo em modelo de UI ou componente de tela.

O saldo devido de uma fatura SHALL ser derivado pelo mesmo mecanismo, como a soma das entries que carregam a dimensão daquela fatura, sem consultar tabela de fachada.

O corte temporal do saldo SHALL usar a data da transação como única referência, e essa invariante SHALL permanecer verificada por teste, de modo que um consumidor futuro não a quebre em silêncio.

#### Scenario: Saldo de conta corrente
- **WHEN** o saldo de uma conta `ASSET` é solicitado
- **THEN** o sistema retorna a soma dos `amount` das entries daquela conta até a data-alvo

#### Scenario: Saldo de fatura sem sinal invertido ad-hoc
- **WHEN** o saldo devido de uma fatura de cartão é solicitado
- **THEN** o sistema o deriva da soma das entries que carregam a dimensão daquela fatura, sem aplicar um sinal invertido especial

#### Scenario: Sem cálculo de saldo em memória
- **WHEN** uma tela precisa do saldo de uma conta
- **THEN** ela o obtém do razão, e MUST NOT somá-lo a partir de uma lista de lançamentos já carregada

#### Scenario: Data do corte é inequívoca
- **WHEN** uma transação é persistida
- **THEN** a data que governa o corte de período é única para a transação e suas entries, sem possibilidade de divergência entre elas

## ADDED Requirements

### Requirement: Leituras do razão expressas em vocabulário de razão
As leituras providas pelo razão SHALL ter assinaturas expressas exclusivamente em natureza de conta, sinal, período e dimensão. Uma leitura MUST NOT nomear fatura, cartão, categoria, orçamento ou relatório na sua assinatura, mesmo quando servir exclusivamente a um deles.

Uma leitura cuja regra é derivável do razão SHALL permanecer nele, ainda que hoje carregue nome de fachada: a correção SHALL ser a renomeação, e MUST NOT ser a transferência da regra para a feature. Em particular, a classificação de fluxos pela natureza das contra-pernas de uma transação e a exclusão de transferências internas ao escopo de um relatório SHALL permanecer no razão, por serem deriváveis dele.

#### Scenario: Assinatura sem vocabulário de fachada
- **WHEN** a superfície de leitura do razão é inspecionada
- **THEN** nenhuma assinatura nomeia fatura, cartão, categoria, orçamento ou relatório

#### Scenario: Classificação por contra-perna permanece no razão
- **WHEN** uma leitura classifica fluxos pela presença de contra-perna de determinada natureza
- **THEN** ela permanece implementada no razão, e nenhuma feature reimplementa essa classificação

#### Scenario: Feature nomeia o próprio sabor
- **WHEN** uma feature precisa apresentar uma figura sob o seu vocabulário
- **THEN** ela o faz traduzindo a leitura do razão, sem duplicar o cálculo
