## MODIFIED Requirements

### Requirement: Operação como conjunto de entries balanceadas
Uma transação SHALL ser o agregado que **possui** um conjunto de `Entry`, cada `Entry` referenciando uma `Account`, com um `amount` assinado (na menor unidade da moeda), uma `currency` e, quando classificada, a dimensão que a acolhe. Para cada moeda presente em uma transação, a soma dos `amount` das entries daquela moeda SHALL ser exatamente zero. Uma transação MUST NOT ter menos de duas entries.

As entries SHALL ser a **única** representação das pernas de uma transação: o sistema MUST NOT manter um modelo de perna paralelo espelhando o razão, e MUST NOT persistir a mesma operação em dois modelos. As entries de uma transação SHALL ser legíveis como objetos de domínio — hidratadas com sua `Account` — e não apenas como agregados numéricos.

A transação MUST NOT carregar o grafo de fachadas do lançamento. Conta de origem, cartão de destino, fatura, categoria, parcelamento e recorrência MUST NOT ser campos do agregado do razão: cada feature SHALL hidratar a própria fachada a partir das entries e das dimensões.

#### Scenario: Despesa balanceada
- **WHEN** o usuário registra uma despesa de 50 na categoria "Alimentação" a partir da conta corrente
- **THEN** a transação contém duas entries que somam zero: a conta nominal `EXPENSE` debitada com a dimensão de "Alimentação", e `ASSET:Conta` creditada

#### Scenario: Transferência balanceada
- **WHEN** o usuário transfere 100 da conta A para a conta B
- **THEN** a transação contém `ASSET:B` debitada e `ASSET:A` creditada, somando zero

#### Scenario: Pagamento de fatura balanceado
- **WHEN** o usuário paga 50 da fatura do cartão a partir da conta corrente
- **THEN** a transação contém `LIABILITY:Cartão` debitada com a dimensão da fatura, e `ASSET:Conta` creditada, somando zero

#### Scenario: Entries legíveis como objetos
- **WHEN** uma transação é lida do repositório
- **THEN** suas entries são retornadas hidratadas com suas `Account`, permitindo derivar rótulo e editabilidade sem consultar nenhum modelo legado

#### Scenario: Sem modelo de perna paralelo
- **WHEN** uma transação é persistida
- **THEN** apenas suas entries são gravadas, e nenhum modelo de perna legado é espelhado

#### Scenario: Agregado sem grafo de fachada
- **WHEN** o agregado de transação do razão é inspecionado
- **THEN** ele não contém campo de conta, cartão, fatura, categoria, parcelamento ou recorrência

### Requirement: Invariante de soma zero validada na escrita
O sistema SHALL validar a invariante de soma zero por moeda em um único ponto na fronteira de escrita e MUST NOT persistir qualquer operação cujas entries não somem zero em alguma moeda. A falha SHALL ser reportada com um erro tipado (via `Either`), não com exceção silenciosa nem correção automática.

Esse mesmo ponto único SHALL validar a compatibilidade entre o `kind` da dimensão de cada perna e a natureza da conta daquela perna, e o fechamento das contas monetárias referenciadas. Nenhuma dessas validações SHALL ter implementação em qualquer outro ponto de escrita.

#### Scenario: Operação desbalanceada é rejeitada
- **WHEN** uma tentativa de criar uma operação cujas entries não somam zero é submetida ao repositório
- **THEN** a persistência falha com um erro tipado indicando o desbalanceamento, e nada é gravado

#### Scenario: Operação balanceada é persistida
- **WHEN** uma operação cujas entries somam zero em cada moeda é submetida
- **THEN** a operação e suas entries são gravadas atomicamente

#### Scenario: Validações concentradas num ponto
- **WHEN** o código de escrita é inspecionado
- **THEN** soma zero, compatibilidade de dimensão e fechamento de conta são verificados no mesmo ponto, e em nenhum outro

## ADDED Requirements

### Requirement: Intenção de escrita expressa por identidade
A intenção de escrita submetida ao razão SHALL expressar cada perna por identidade de conta e, quando classificada, por identidade de dimensão. A intenção MUST NOT carregar objetos de fachada — conta, cartão, fatura ou categoria — nem qualquer noção de "alvo" que distinga conta de cartão: em termos de razão, essa distinção é apenas a natureza da conta.

Resolver uma fachada para a identidade que a representa no razão SHALL ser responsabilidade da feature dona daquela fachada. A fronteira de escrita MUST NOT consultar tabela de fachada alguma para resolver uma perna.

#### Scenario: Feature resolve a própria fachada
- **WHEN** uma feature escreve um lançamento envolvendo uma fachada sua
- **THEN** ela resolve a fachada para identidade de conta e de dimensão antes de submeter a intenção

#### Scenario: Fronteira de escrita sem dependência de fachada
- **WHEN** as dependências da fronteira de escrita são inspecionadas
- **THEN** ela acessa apenas os dados do razão, e nenhum objeto de acesso a dados de fachada

#### Scenario: Sem noção de alvo na intenção
- **WHEN** uma intenção de escrita é inspecionada
- **THEN** ela não distingue conta de cartão por um campo dedicado; a distinção emerge da natureza da conta referenciada

### Requirement: Migração para dimensões preserva e verifica o balanceamento
A migração que retira a categoria do plano de contas e introduz as dimensões SHALL preservar, para todo dispositivo existente, o saldo de cada conta, o saldo devido de cada fatura, o patrimônio líquido e o total de cada categoria — os valores exibidos antes e depois SHALL ser idênticos.

A migração SHALL verificar a invariante de soma zero por transação e por moeda **antes e depois** da reescrita das pernas nominais, e SHALL abortar a transação inteira se alguma transação não balancear em qualquer dos dois momentos. Essa verificação SHALL ser automatizada e coberta por teste de migração, e MUST NOT ser substituída por inspeção manual: a reescrita colapsa múltiplas contas de categoria em duas contas nominais e altera o `accountId` de pernas já gravadas.

A migração MUST NOT deixar `Entry` órfã de conta, de transação ou de dimensão, e MUST NOT remover do plano de contas qualquer conta ainda referenciada por alguma `Entry`.

#### Scenario: Soma zero verificada nos dois momentos
- **WHEN** a migração é executada
- **THEN** a invariante de soma zero por transação e por moeda é verificada antes e depois da reescrita das pernas nominais

#### Scenario: Desbalanceamento aborta a migração
- **WHEN** alguma transação não soma zero em alguma das verificações
- **THEN** a migração aborta integralmente e nenhuma gravação parcial permanece

#### Scenario: Figuras preservadas
- **WHEN** um dispositivo com dados representativos é migrado
- **THEN** o saldo de cada conta, o devido de cada fatura, o patrimônio e os totais por categoria são idênticos aos exibidos antes da migração

#### Scenario: Contas de categoria removidas sem órfãos
- **WHEN** a migração conclui
- **THEN** nenhuma conta de categoria permanece no plano, nenhuma `Entry` referencia conta removida, e nenhuma `Entry` referencia dimensão inexistente
