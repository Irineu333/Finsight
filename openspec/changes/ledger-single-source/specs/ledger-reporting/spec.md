## MODIFIED Requirements

### Requirement: Saldo de conta a partir das entries
O saldo de qualquer conta SHALL ser calculado exclusivamente como a soma dos `amount` das entries que a referenciam, aplicando a convenção débito-positivo, sem funções de sinal específicas por tipo de lançamento. O cálculo de saldo MUST NOT depender de nenhuma função de sinal derivada de um modelo legado, nem de qualquer regra de sinal invertida específica de cartão.

SHALL existir **uma única** implementação do cálculo de saldo. MUST NOT existir uma forma alternativa que some lançamentos já carregados em memória, nem qualquer recálculo de saldo em modelo de UI ou componente de tela.

O corte temporal do saldo SHALL usar a data da transação como única referência, e essa invariante SHALL ser garantida na escrita — não presumida por convenção — de modo que dois caminhos de leitura não possam divergir na fronteira do período.

#### Scenario: Saldo de conta corrente
- **WHEN** o saldo de uma conta `ASSET` é solicitado
- **THEN** o sistema retorna a soma dos `amount` das entries daquela conta até a data-alvo

#### Scenario: Saldo de fatura sem sinal invertido ad-hoc
- **WHEN** o saldo devido de uma fatura de cartão é solicitado
- **THEN** o sistema o deriva da soma das entries da conta `LIABILITY` do cartão no período, sem aplicar um sinal invertido especial

#### Scenario: Sem cálculo de saldo em memória
- **WHEN** uma tela precisa do saldo de uma conta
- **THEN** ela o obtém do razão, e MUST NOT somá-lo a partir de uma lista de lançamentos já carregada

#### Scenario: Data do corte é inequívoca
- **WHEN** uma transação é persistida
- **THEN** a data que governa o corte de período é única para a transação e suas entries, sem possibilidade de divergência entre elas

## ADDED Requirements

### Requirement: Razão como única fonte de leitura
Toda leitura de dinheiro — saldo de conta, saldo devido de fatura, gasto por categoria, patrimônio líquido e totais de período — SHALL derivar do razão. Nenhum consumidor SHALL derivar valor monetário de um modelo de lançamento legado. O grafo de objetos exibido ao usuário SHALL igualmente derivar do razão: as telas de transações, contas, categorias, orçamentos, faturas e relatórios SHALL ler entries, e MUST NOT ler um modelo de perna paralelo.

#### Scenario: Tela de contas lê o razão
- **WHEN** a tela de contas exibe saldos e totais do período
- **THEN** os valores derivam do razão, e não de uma soma de lançamentos legados

#### Scenario: Orçamentos leem o razão
- **WHEN** o progresso de um orçamento por categoria é calculado
- **THEN** ele deriva das entries da conta da categoria

#### Scenario: Nenhum leitor legado remanescente
- **WHEN** o código é inspecionado após a change
- **THEN** não existe consumidor que derive valor monetário de um modelo de lançamento legado, pois esse modelo não existe mais

### Requirement: Saldo de abertura do período
O saldo de abertura de um período SHALL ser o saldo da conta até o instante anterior ao início do período, derivado pelo **mesmo** mecanismo do saldo de conta. MUST NOT existir mais de uma implementação desse cálculo. O saldo de abertura MUST NOT ser chamado de "saldo inicial": ele é derivado do período exibido, e não representa um aporte inicial registrado.

#### Scenario: Saldo de abertura de um mês
- **WHEN** a tela de um mês exibe o saldo de abertura de uma conta
- **THEN** o valor é o saldo da conta até o fim do mês anterior, obtido pelo mecanismo comum de saldo

#### Scenario: Uma única implementação
- **WHEN** o saldo de abertura é exibido em telas distintas (conta, lista de transações, relatório)
- **THEN** todas obtêm o valor do mesmo mecanismo, e MUST NOT recalculá-lo independentemente

### Requirement: Ajuste de fatura consistente com o razão
A edição de um ajuste de saldo de fatura SHALL atualizar o razão. MUST NOT existir caminho de escrita que altere o valor de um ajuste sem atualizar as entries correspondentes, sob pena de o saldo devido exibido divergir do valor registrado.

#### Scenario: Edição de ajuste de fatura atualiza as entries
- **WHEN** o usuário altera o valor de um ajuste de saldo de uma fatura já existente
- **THEN** as entries do ajuste são reescritas, e o saldo devido exibido reflete o novo valor
