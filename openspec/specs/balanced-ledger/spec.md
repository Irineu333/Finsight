# balanced-ledger Specification

## Purpose

O razão de partidas dobradas: uma transação é um conjunto de entries assinadas com moeda, sujeitas à invariante `Σ = 0` por moeda, validada num único ponto de escrita. Internamente o sinal é débito-positivo; a natureza da transação e a direção de cada perna são **derivadas** dos tipos de conta, nunca persistidas. Nenhuma feature reimplementa regra derivável daqui. Constrói sobre o plano de contas (`chart-of-accounts`) e é a fonte da qual `ledger-reporting` deriva toda leitura de dinheiro.
## Requirements
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

### Requirement: Convenção de sinal débito-positivo
O razão SHALL adotar a convenção débito-positivo: `amount` positivo representa débito e negativo representa crédito. O débito SHALL aumentar o saldo natural de contas `ASSET` e `EXPENSE`; o crédito SHALL aumentar o saldo natural de contas `LIABILITY`, `INCOME` e `EQUITY`. Essa convenção MUST NOT ser exposta ao usuário; a inversão de sinal para exibição pertence à camada de apresentação.

#### Scenario: Compra no cartão aumenta a dívida
- **WHEN** uma compra de 50 no cartão é registrada
- **THEN** a entry `EXPENSE:categoria` é debitada (+50) e a entry `LIABILITY:Cartão` é creditada (−50), aumentando o saldo natural do passivo

### Requirement: Tipo de operação derivado dos tipos de conta
O sistema SHALL derivar o rótulo de uma transação a partir dos tipos das contas envolvidas nas suas entries, e MUST NOT persistir esse rótulo como estado independente. A derivação SHALL ser uma função **total** sobre o conjunto `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`: uma contrapartida `EQUITY` SHALL produzir `ADJUSTMENT`; `ASSET`→`EXPENSE` SHALL ser despesa; `INCOME`→`ASSET` receita; `ASSET`→`LIABILITY` pagamento; `ASSET`→`ASSET` transferência.

A presença de uma contrapartida `EQUITY` SHALL ser avaliada **antes de qualquer outro caso**, e não apenas antes do caso de transferência: um ajuste pode ocorrer tanto sobre uma conta (`{ASSET, EQUITY}`) quanto sobre uma fatura de cartão (`{LIABILITY, EQUITY}`), e neste segundo caso qualquer avaliação que teste `LIABILITY` primeiro produziria `PAYMENT`. Um ajuste MUST NOT ser rotulado como transferência nem como pagamento, independentemente de a conta ajustada ser `ASSET` ou `LIABILITY`.

SHALL existir uma única derivação **de rótulo de operação** no sistema. Isso MUST NOT ser confundido com a **direção da perna** sob a perspectiva exibida (despesa/receita/ajuste), que é uma derivação distinta, com propósito distinto, e que SHALL coexistir: a interface exibe as duas simultaneamente — um pagamento de fatura mostra a direção "despesa" da perna da conta **e** o rótulo "pagamento" da operação. Cada uma SHALL ter uma única implementação; nenhuma SHALL ser reimplementada em linha pelos consumidores.

#### Scenario: Rótulo derivado de uma transferência
- **WHEN** uma transação tem duas entries, ambas em contas `ASSET`
- **THEN** o sistema a apresenta como transferência sem consultar nenhum campo de tipo persistido

#### Scenario: Rótulo derivado de um pagamento de fatura
- **WHEN** uma transação move valor de uma conta `ASSET` para uma conta `LIABILITY`
- **THEN** o sistema a apresenta como pagamento

#### Scenario: Rótulo derivado de um ajuste de saldo de conta
- **WHEN** uma transação tem uma entry em conta `ASSET` e a contrapartida em conta `EQUITY` de reconciliação
- **THEN** o sistema a apresenta como ajuste, e MUST NOT apresentá-la como transferência

#### Scenario: Rótulo derivado de um ajuste de saldo de fatura
- **WHEN** uma transação tem uma entry na conta `LIABILITY` de um cartão e a contrapartida em conta `EQUITY` de reconciliação
- **THEN** o sistema a apresenta como ajuste, e MUST NOT apresentá-la como pagamento

#### Scenario: Derivação é total
- **WHEN** qualquer transação válida do razão tem seu rótulo derivado
- **THEN** o resultado pertence a `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`, sem caso não coberto

### Requirement: Ajuste de saldo como operação balanceada
O ajuste de saldo SHALL ser registrado como uma operação balanceada de duas entries — a conta ajustada e uma conta `EQUITY` de reconciliação — em vez de um lançamento sem contrapartida. O comportamento idempotente por data e conta SHALL ser preservado: um novo ajuste na mesma data e conta atualiza o ajuste existente, e um ajuste que se anula é removido.

#### Scenario: Ajuste cria par balanceado
- **WHEN** o usuário ajusta o saldo de uma conta para um valor maior que o atual
- **THEN** o sistema registra uma operação com a conta debitada e a conta `EQUITY:Reconciliação` creditada pela diferença, somando zero

#### Scenario: Ajuste idempotente na mesma data
- **WHEN** já existe um ajuste na mesma conta e data e um novo ajuste é aplicado
- **THEN** o ajuste existente é atualizado (ou removido, se o resultado se anula) em vez de criar um segundo ajuste

### Requirement: Editabilidade derivada, preservando os gates existentes
A editabilidade de uma transação SHALL ser derivada, nunca persistida, e SHALL preservar cada um dos gates hoje aplicados: uma transação MUST NOT ser editável se pertencer a uma fatura cujo status seja `CLOSED` ou `PAID`; MUST NOT ser editável se o seu rótulo for `ADJUSTMENT`; MUST NOT ser editável se possuir um número de entries em conta **monetária** (`ASSET`/`LIABILITY`) diferente de exatamente uma; e MUST NOT ser editável se pertencer a um parcelamento. Uma transação que passe em todos os gates SHALL ser editável.

A contagem MUST NOT usar o total de entries, já que toda transação balanceada tem ao menos duas.

#### Scenario: Despesa é editável
- **WHEN** uma despesa em conta (`ASSET` + `EXPENSE`) sem parcelamento é exibida
- **THEN** ela é editável

#### Scenario: Compra no cartão é editável
- **WHEN** uma compra no cartão (`LIABILITY` + `EXPENSE`) sem parcelamento é exibida
- **THEN** ela é editável

#### Scenario: Ajuste de conta não é editável
- **WHEN** um ajuste de saldo de conta (`ASSET` + `EQUITY`) é exibido
- **THEN** ele não é editável, por seu rótulo ser `ADJUSTMENT`

#### Scenario: Ajuste de fatura não é editável
- **WHEN** um ajuste de saldo de fatura (`LIABILITY` + `EQUITY`) é exibido
- **THEN** ele não é editável, por seu rótulo ser `ADJUSTMENT`

#### Scenario: Lançamento de baixa não é editável
- **WHEN** o lançamento de baixa que a migração `v7 → v9` gerou para uma conta apagada no v7 é exibido
- **THEN** ele não é editável, pelo mesmo gate de rótulo, sem regra nova — arquivar não gera baixa em runtime (`account-lifecycle`), mas a migração gera, e o dado migrado obedece às mesmas regras que o novo

#### Scenario: Transferência não é editável
- **WHEN** uma transferência (`ASSET` + `ASSET`) é exibida
- **THEN** ela não é editável, por ter duas pernas monetárias

#### Scenario: Pagamento de fatura não é editável
- **WHEN** um pagamento de fatura (`ASSET` + `LIABILITY`) é exibido
- **THEN** ele não é editável, por ter duas pernas monetárias

#### Scenario: Parcelamento não é editável
- **WHEN** uma compra pertencente a um parcelamento é exibida
- **THEN** ela não é editável, por pertencer a um parcelamento

### Requirement: Remoção de transação em fatura fechada é impedida
A remoção de uma transação SHALL ser impedida quando ela pertencer a uma fatura cujo status seja `CLOSED` ou `PAID`, e SHALL ser permitida caso contrário. Este gate SHALL usar a única definição de status editável de fatura existente, e MUST NOT ser reimplementado em linha pelos consumidores.

#### Scenario: Transação em fatura aberta pode ser removida
- **WHEN** uma transação de uma fatura `OPEN`, `FUTURE` ou `RETROACTIVE` é exibida
- **THEN** a remoção é oferecida

#### Scenario: Transação em fatura fechada não pode ser removida nem editada
- **WHEN** uma transação de uma fatura `CLOSED` ou `PAID` é exibida
- **THEN** nem remoção nem edição são oferecidas, e o motivo é comunicado ao usuário

### Requirement: Nenhuma feature reimplementa regra derivável do razão

O razão é a autoridade sobre toda regra que se possa derivar das entries e dos tipos das suas contas. Nenhum consumidor — feature, tela, ViewModel, componente ou modelo de UI — SHALL reimplementar uma regra derivável do razão. Toda regra dessa natureza SHALL ter **exatamente uma** implementação, no domínio, e os consumidores SHALL consumi-la em vez de reescrevê-la.

São regras deriváveis do razão, entre outras: o rótulo da operação, a direção da perna sob uma perspectiva, a editabilidade, a deletabilidade, o saldo de conta, o saldo de abertura de um período, o saldo devido de uma fatura, a natureza monetária de uma conta, a sua natureza permanente ou temporária, o estado de arquivamento de uma conta e qual ação de retirada uma tela oferece para ela. Esta é a forma **geral** da regra que as demais capabilities já declaram caso a caso — derivação de rótulo (nesta capability), cálculo de saldo e saldo de abertura (`ledger-reporting`), tradução domínio→apresentação (`presentation-mapping`), estado de arquivamento (`account-lifecycle`). Essas declarações SHALL ser lidas como instâncias desta, e MUST NOT ser tratadas como regras independentes livres para divergir.

A distinção que governa a fronteira: um consumidor MAY decidir **se** aplica uma regra — uma tela pode legitimamente não oferecer uma ação que o domínio permite. Um consumidor MUST NOT decidir **qual é** a regra. Adaptar ao usuário é da camada de apresentação; definir a verdade é do razão.

#### Scenario: Consumidor não redefine a regra
- **WHEN** uma tela precisa saber se uma transação é editável, qual o seu rótulo, ou qual o saldo de uma conta
- **THEN** ela obtém a resposta da única implementação de domínio, e MUST NOT reavaliar os tipos de conta, os status ou as entries por conta própria

#### Scenario: Tela pode não oferecer o que o domínio permite
- **WHEN** o domínio permite uma operação que uma tela decide não expor
- **THEN** isso não é divergência: a tela escolheu não oferecer a ação, sem redefinir a regra que a governa

#### Scenario: Mesma regra, mesma resposta em toda tela
- **WHEN** a mesma regra derivável é consultada a partir de telas distintas para a mesma transação
- **THEN** todas obtêm a mesma resposta, por consultarem a mesma implementação

#### Scenario: Nenhuma cópia em linha
- **WHEN** o código é inspecionado
- **THEN** não existe reimplementação em linha de regra derivável do razão — nem em `when` de tela, nem em modelo de UI, nem em predicado local que reenumere à mão o complemento de um predicado existente

### Requirement: Classificação de entrada distinta da de exibição
O vocabulário com que o usuário **registra** um lançamento (despesa, receita, ajuste) SHALL pertencer à camada de apresentação e MUST NOT ser persistido como estado da transação. O sistema SHALL traduzir esse vocabulário de entrada em entries balanceadas no momento da escrita, e SHALL derivar o vocabulário de exibição das entries no momento da leitura. O vocabulário de entrada MUST NOT ser unificado com o de exibição, por serem conjuntos distintos.

#### Scenario: Entrada vira entries
- **WHEN** o usuário registra uma despesa escolhendo categoria e conta
- **THEN** o sistema traduz a intenção em entries balanceadas, sem gravar a escolha "despesa" como campo

#### Scenario: Exibição vem das entries
- **WHEN** a mesma transação é exibida
- **THEN** o rótulo é derivado das entries, e não lido de um campo persistido

### Requirement: Migração para o razão como única fonte preserva os dados
A migração que remove o modelo legado SHALL preservar, para todo dispositivo existente, o saldo de cada conta, o saldo devido de cada fatura, o patrimônio líquido e o total de cada categoria — os valores exibidos antes e depois da migração SHALL ser idênticos. A migração MUST NOT abortar em dados legados sujos (lançamentos cujo cartão ou conta foi apagado), MUST NOT deixar `Entry` órfã de conta ou de transação, e MUST NOT remover conta do plano de contas que ainda seja referenciada por alguma `Entry`.

Nenhum estado intermediário observável SHALL existir entre a remoção do modelo legado e a renomeação do agregado: a estrutura de dados e as declarações que a descrevem SHALL mudar na mesma versão de schema, de modo que o banco nunca seja aberto contra uma descrição divergente.

#### Scenario: Saldos preservados
- **WHEN** um dispositivo com dados representativos é migrado
- **THEN** o saldo de cada conta, o devido de cada fatura, o patrimônio e os totais por categoria são idênticos aos exibidos antes da migração

#### Scenario: Dados legados sujos não abortam a migração
- **WHEN** existem lançamentos legados cuja conta ou cartão foi apagado
- **THEN** a migração conclui, e as entries resultantes permanecem balanceadas

#### Scenario: Banco nunca abre contra descrição divergente
- **WHEN** a migração renomeia a tabela do agregado
- **THEN** as declarações que a descrevem mudam na mesma versão, e nenhuma versão intermediária do app abre o banco contra um nome divergente

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

