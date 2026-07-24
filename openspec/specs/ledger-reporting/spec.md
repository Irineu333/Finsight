# ledger-reporting Specification

## Purpose

Toda leitura de dinheiro — saldo de conta, saldo de abertura do período, saldo devido de fatura, gasto por categoria e patrimônio líquido — deriva de um mecanismo único: `Σ entries` da conta. Não há regra de sinal específica por tipo de lançamento, nem cálculo alternativo por tela: o sinal de exibição vem do `AccountType`, e cada agregado tem um só dono. Consome o razão (`balanced-ledger`) sobre o plano de contas (`chart-of-accounts`).
## Requirements
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

### Requirement: Razão como única fonte de leitura
Toda leitura de dinheiro — saldo de conta, saldo devido de fatura, gasto por categoria, patrimônio líquido e totais de período — SHALL derivar do razão. Nenhum consumidor SHALL derivar valor monetário de um modelo de lançamento legado. O grafo de objetos exibido ao usuário SHALL igualmente derivar do razão: as telas de transações, contas, categorias, orçamentos, faturas e relatórios SHALL ler entries, e MUST NOT ler um modelo de perna paralelo.

Uma leitura escopada a uma fachada SHALL derivar da identidade com que o razão a representa — a conta, para conta e cartão; a dimensão, para categoria e fatura — e MUST NOT consultar a tabela da fachada para obter valor monetário.

#### Scenario: Tela de contas lê o razão
- **WHEN** a tela de contas exibe saldos e totais do período
- **THEN** os valores derivam do razão, e não de uma soma de lançamentos legados

#### Scenario: Orçamentos leem o razão
- **WHEN** o progresso de um orçamento por categoria é calculado
- **THEN** ele deriva das entries que carregam a dimensão daquela categoria

#### Scenario: Nenhum leitor legado remanescente
- **WHEN** o código é inspecionado
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

