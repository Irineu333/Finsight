## MODIFIED Requirements

### Requirement: Saldo de conta a partir das entries

O saldo de qualquer conta SHALL ser calculado exclusivamente como a soma dos `amount` das entries que a referenciam, aplicando a convenção débito-positivo, sem funções de sinal específicas por tipo de lançamento. O cálculo de saldo MUST NOT depender de nenhuma função de sinal derivada de um modelo legado, nem de qualquer regra de sinal invertida específica de cartão.

SHALL existir **uma única** implementação do cálculo de saldo. MUST NOT existir uma forma alternativa que some lançamentos já carregados em memória, nem qualquer recálculo de saldo em modelo de UI ou componente de tela.

O saldo devido de uma fatura SHALL ser derivado pelo mesmo mecanismo, como a soma das entries que carregam a dimensão daquela fatura, sem consultar tabela de fachada.

O corte temporal do saldo SHALL usar a data da transação como única referência, e essa invariante SHALL permanecer verificada por teste, de modo que um consumidor futuro não a quebre em silêncio.

A leitura **escalar** do saldo de uma conta SHALL admitir corte por **data**, com a resolução que a data da transação já possui. O corte por dia é a leitura real; MUST NOT existir uma segunda consulta que produza o acumulado da mesma conta com resolução diferente.

O acumulado de uma conta **até um mês** SHALL ser derivado dessa leitura, como o acumulado até o último dia daquele mês. Ele MUST NOT ter implementação própria, porque não é outro número: é o mesmo número perguntado com menos precisão.

A resolução por dia SHALL permanecer restrita à leitura escalar por conta. As leituras que atravessam contas — expressas por moeda — MUST NOT ser alteradas por esta regra enquanto nenhum consumidor delas perguntar por dia, e a assimetria SHALL ser deliberada e registrada, não presumida.

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

#### Scenario: Saldo até um dia do mês
- **WHEN** o saldo de uma conta até o dia 10 de um mês é solicitado, e existem lançamentos no dia 5 e no dia 20 daquele mês
- **THEN** o resultado inclui o lançamento do dia 5 e exclui o do dia 20

#### Scenario: O acumulado até um mês deriva do acumulado até uma data
- **WHEN** o saldo de uma conta até um mês é solicitado
- **THEN** o valor é o saldo daquela conta até o último dia daquele mês, obtido pela mesma consulta, e não por uma segunda com corte mensal próprio

#### Scenario: Leituras por moeda permanecem mensais
- **WHEN** o saldo acumulado que atravessa contas é solicitado
- **THEN** ele continua expresso por moeda e cortado por mês, sem ganhar resolução por dia
