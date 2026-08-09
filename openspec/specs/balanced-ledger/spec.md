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

A invariante MUST NOT admitir exceção alguma — **inclusive para a operação que atravessa moedas**. Uma operação assim não é uma transação desbalanceada tolerada: ela chega **incompleta** à fronteira, que a completa com pernas de conversão até que cada moeda some zero (ver o requisito de completar a intenção cruzada). Consequentemente, `Σ entries = 0` por `(transação, moeda)` SHALL permanecer verificável **lendo apenas as entries**, sem consultar as contas das pernas, o plano de contas ou qualquer tabela de fachada — que é a condição para que a verificação continue válida antes e depois de qualquer reescrita do plano de contas.

"Um único ponto" SHALL ser verdade de fato, e não apenas do ponto onde as entries são construídas: MUST NOT existir pré-validação de balanceamento em nenhum ponto anterior à fronteira. Uma pré-validação que some as pernas da intenção sem separá-las por moeda recusaria toda operação cruzada antes de a fronteira poder completá-la, e é incompatível com a moeda de uma perna derivar da sua conta — separá-las por moeda exigiria que o pré-cheque lesse o plano de contas, deixando de ser pré-cheque.

Esse mesmo ponto único SHALL validar a compatibilidade entre o `kind` da dimensão de cada perna e a natureza da conta daquela perna, e o fechamento das contas monetárias referenciadas. Nenhuma dessas validações SHALL ter implementação em qualquer outro ponto de escrita.

#### Scenario: Operação desbalanceada é rejeitada
- **WHEN** uma tentativa de criar uma operação cujas entries não somam zero é submetida ao repositório
- **THEN** a persistência falha com um erro tipado indicando o desbalanceamento, e nada é gravado

#### Scenario: Operação balanceada é persistida
- **WHEN** uma operação cujas entries somam zero em cada moeda é submetida
- **THEN** a operação e suas entries são gravadas atomicamente

#### Scenario: Operação cruzada também soma zero
- **WHEN** uma operação que envolve duas moedas é persistida
- **THEN** as entries gravadas somam zero em **cada** uma das duas moedas, e nenhuma exceção à invariante é registrada

#### Scenario: Nenhuma pré-validação recusa a operação cruzada
- **WHEN** uma intenção cujas pernas envolvem duas moedas é submetida ao repositório
- **THEN** ela alcança a fronteira de escrita e é completada, sem ser recusada por nenhuma verificação anterior

#### Scenario: Invariante verificável só pelas entries
- **WHEN** a verificação de balanceamento do razão é executada sobre um banco com operações cruzadas
- **THEN** ela agrupa por `(transação, moeda)` sobre as entries, sem consultar contas, e não encontra violação

#### Scenario: Validações concentradas num ponto
- **WHEN** o código de escrita é inspecionado
- **THEN** soma zero, compatibilidade de dimensão e fechamento de conta são verificados no mesmo ponto, e em nenhum outro — inclusive nenhum anterior

### Requirement: Convenção de sinal débito-positivo
O razão SHALL adotar a convenção débito-positivo: `amount` positivo representa débito e negativo representa crédito. O débito SHALL aumentar o saldo natural de contas `ASSET` e `EXPENSE`; o crédito SHALL aumentar o saldo natural de contas `LIABILITY`, `INCOME` e `EQUITY`. Essa convenção MUST NOT ser exposta ao usuário; a inversão de sinal para exibição pertence à camada de apresentação.

#### Scenario: Compra no cartão aumenta a dívida
- **WHEN** uma compra de 50 no cartão é registrada
- **THEN** a entry `EXPENSE:categoria` é debitada (+50) e a entry `LIABILITY:Cartão` é creditada (−50), aumentando o saldo natural do passivo

### Requirement: Tipo de operação derivado dos tipos de conta
O sistema SHALL derivar o rótulo de uma transação a partir dos tipos das contas envolvidas nas suas entries, e MUST NOT persistir esse rótulo como estado independente. A derivação SHALL ser uma função **total** sobre o conjunto `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`: uma contrapartida `EQUITY` SHALL produzir `ADJUSTMENT`; `ASSET`→`EXPENSE` SHALL ser despesa; `INCOME`→`ASSET` receita; `ASSET`→`LIABILITY` pagamento; `ASSET`→`ASSET` transferência.

As pernas de **conversão** SHALL ser transparentes à derivação: uma perna em conta `CONVERSION` MUST NOT alterar o rótulo que a operação teria sem ela. Uma transferência entre contas de moedas diferentes SHALL ser rotulada `TRANSFER`, e o pagamento de uma fatura em moeda distinta da conta pagadora SHALL ser rotulado `PAYMENT` — a moeda de uma operação MUST NOT mudar o que ela **é**.

A presença de uma contrapartida `EQUITY` SHALL ser avaliada **antes de qualquer outro caso**, e não apenas antes do caso de transferência: um ajuste pode ocorrer tanto sobre uma conta (`{ASSET, EQUITY}`) quanto sobre uma fatura de cartão (`{LIABILITY, EQUITY}`), e neste segundo caso qualquer avaliação que teste `LIABILITY` primeiro produziria `PAYMENT`. Um ajuste MUST NOT ser rotulado como transferência nem como pagamento, independentemente de a conta ajustada ser `ASSET` ou `LIABILITY`. Essa precedência é a razão pela qual a conversão cambial MUST NOT ser representada como `EQUITY`: sob `EQUITY`, toda operação cruzada seria rotulada ajuste antes de qualquer outro caso ser considerado.

SHALL existir uma única derivação **de rótulo de operação** no sistema. Isso MUST NOT ser confundido com a **direção da perna** sob a perspectiva exibida (despesa/receita/ajuste), que é uma derivação distinta, com propósito distinto, e que SHALL coexistir: a interface exibe as duas simultaneamente — um pagamento de fatura mostra a direção "despesa" da perna da conta **e** o rótulo "pagamento" da operação. Cada uma SHALL ter uma única implementação; nenhuma SHALL ser reimplementada em linha pelos consumidores. A transparência das pernas de conversão SHALL valer igualmente para a derivação de direção da perna: uma perna monetária de uma operação cruzada MUST NOT ser lida como ajuste.

#### Scenario: Rótulo derivado de uma transferência
- **WHEN** uma transação tem duas entries, ambas em contas `ASSET`
- **THEN** o sistema a apresenta como transferência sem consultar nenhum campo de tipo persistido

#### Scenario: Rótulo derivado de uma transferência entre moedas
- **WHEN** uma transação tem duas entries em contas `ASSET` de moedas diferentes e duas entries em contas `CONVERSION`
- **THEN** o sistema a apresenta como transferência, e MUST NOT apresentá-la como ajuste

#### Scenario: Rótulo derivado de um pagamento de fatura
- **WHEN** uma transação move valor de uma conta `ASSET` para uma conta `LIABILITY`
- **THEN** o sistema a apresenta como pagamento

#### Scenario: Rótulo derivado de um pagamento de fatura entre moedas
- **WHEN** o pagamento de uma fatura em moeda distinta da conta pagadora é exibido
- **THEN** o sistema o apresenta como pagamento, e MUST NOT apresentá-lo como ajuste

#### Scenario: Direção da perna numa operação cruzada
- **WHEN** a perna de conta de uma transferência cruzada é exibida numa lista
- **THEN** a sua direção é derivada do sinal do seu próprio valor, e MUST NOT ser lida como ajuste

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

A contagem MUST NOT usar o total de entries, já que toda transação balanceada tem ao menos duas. As pernas de **conversão** MUST NOT entrar nessa contagem, por não serem monetárias — de modo que uma operação que atravessa moedas é recusada pelo gate por ter **duas pernas monetárias**, exatamente como a transferência e o pagamento de fatura de moeda única já são, e sem gate novo.

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

#### Scenario: Transferência entre moedas não é editável, pelo mesmo gate
- **WHEN** uma transferência entre contas de moedas diferentes, com as suas pernas de conversão, é exibida
- **THEN** ela não é editável, por ter duas pernas monetárias, e as pernas de conversão não entram na contagem

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

A moeda de uma perna SHALL derivar da conta em que ela posta, e a intenção MUST NOT declará-la. Uma perna denominada em moeda diferente da sua conta SHALL ser **inexprimível** — não recusada por validação, mas impossível de enunciar —, e a fronteira de escrita SHALL ler a moeda da conta que já carrega para verificar fechamento, sem leitura adicional.

Resolver uma fachada para a identidade que a representa no razão SHALL ser responsabilidade da feature dona daquela fachada. A fronteira de escrita MUST NOT consultar tabela de fachada alguma para resolver uma perna.

#### Scenario: Feature resolve a própria fachada
- **WHEN** uma feature escreve um lançamento envolvendo uma fachada sua
- **THEN** ela resolve a fachada para identidade de conta e de dimensão antes de submeter a intenção

#### Scenario: Moeda não é declarada pela intenção
- **WHEN** uma intenção de escrita é inspecionada
- **THEN** nenhuma das suas pernas carrega moeda, e a moeda de cada entry gravada é a da conta referenciada

#### Scenario: Perna em moeda divergente é inexprimível
- **WHEN** o vocabulário da intenção de escrita é inspecionado
- **THEN** não existe forma de expressar uma perna cuja moeda difira da conta em que ela posta

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

### Requirement: Confirmar um ciclo de recorrência é uma única unidade de trabalho

Confirmar um ciclo de recorrência escreve duas coisas: a transação no razão e a ocorrência que registra que aquele ciclo foi tratado. As duas SHALL ser gravadas na **mesma transação de banco** — ou ambas persistem, ou nenhuma persiste.

Um ciclo de recorrência MUST NOT ser confirmado mais de uma vez. A escrita parcial é o que torna a dupla confirmação alcançável: com a transação gravada e a ocorrência não, o ciclo volta a ser apresentado como pendente, e a recusa de reentrada — que consulta a ocorrência — não encontra o registro que faltou escrever, de modo que uma segunda confirmação grava um **lançamento duplicado** no razão.

A escrita da fatura eventualmente necessária para o ciclo MAY permanecer fora dessa unidade de trabalho: uma fatura criada e não usada é dano menor que uma estrutura de fatura desfeita, e menor que um lançamento duplicado.

#### Scenario: Falha ao registrar a ocorrência não deixa o lançamento gravado
- **WHEN** a confirmação de um ciclo grava a transação mas falha ao registrar a ocorrência
- **THEN** a transação é desfeita junto com a ocorrência, o razão permanece sem o lançamento, e o ciclo continua pendente de forma consistente

#### Scenario: Confirmar o mesmo ciclo duas vezes não duplica o lançamento
- **WHEN** o usuário confirma um ciclo que já foi confirmado
- **THEN** a operação é recusada e nenhum segundo lançamento é gravado no razão

### Requirement: Operação que atravessa moedas é completada com pernas de conversão

Quando as pernas de uma intenção envolvem mais de uma moeda, a fronteira de escrita SHALL completar a operação lançando, para **cada** moeda presente, o oposto do resíduo daquela moeda na **conta de conversão daquela moeda**. O resultado gravado SHALL somar zero em cada moeda, pela invariante geral e sem exceção a ela.

A regra SHALL ser uniforme, sem ramo por caso de uso: uma transferência entre contas de moedas diferentes, o pagamento de uma fatura em moeda distinta da conta pagadora e qualquer outra operação cruzada SHALL ser completadas pelo mesmo mecanismo, que MUST NOT nomear fachada alguma.

A perna de conversão SHALL ser a **última calculada**, recebendo o resíduo por diferença, e MUST NOT ser calculada de forma independente e depois comparada com as demais. É o que concentra todo o erro de arredondamento do câmbio num único lugar por construção, em vez de deixá-lo emergir como desbalanceamento de centavos.

Quando **uma só** moeda estiver presente, o resíduo SHALL ser zero — o comportamento existente permanece intacto —, e uma perna avulsa SHALL continuar sendo completada pela contrapartida declarada na intenção. A conta de conversão MUST NOT ser usada para completar uma operação monomoeda: ali, resíduo não nulo é desbalanceamento, e SHALL ser recusado.

Quando duas ou mais moedas estiverem presentes, os resíduos MUST NOT ser todos do mesmo sinal. Uma intenção em que toda moeda envolvida ganha valor cria dinheiro sem origem: não é câmbio, e SHALL ser recusada com erro tipado.

Uma perna de conversão MUST NOT carregar dimensão, e em particular MUST NOT herdar a dimensão da perna cujo resíduo ela absorve. Sem isso, o pagamento de fatura que atravessa moedas não persiste: a perna de passivo carrega a dimensão da fatura, cujo `kind` só aceita `LIABILITY`, e copiá-la para a perna de conversão faria a regra de pouso de dimensão recusar a transação inteira. É também o que corresponde ao significado da dimensão — o resíduo cambial não pertence ao sub-razão da fatura, e somá-lo ao devido contaria o custo de trocar moeda como dívida do cartão.

#### Scenario: Transferência entre moedas soma zero nas duas
- **WHEN** o usuário transfere de uma conta em BRL para uma conta em USD, informando o valor que saiu e o valor que entrou
- **THEN** a operação é gravada com quatro entries — a conta BRL e a conversão em BRL, a conversão em USD e a conta USD — somando zero em BRL e zero em USD

#### Scenario: Pagamento de fatura em outra moeda
- **WHEN** o usuário paga uma fatura de um cartão em USD a partir de uma conta em BRL
- **THEN** a operação é completada pelo mesmo mecanismo de conversão, sem regra específica de fatura

#### Scenario: Resíduo de arredondamento fica na perna de conversão
- **WHEN** uma operação cruzada produz resíduo de arredondamento
- **THEN** ele é absorvido pela perna de conversão, que é calculada por diferença, e a operação soma zero em cada moeda

#### Scenario: Operação monomoeda não usa conversão
- **WHEN** uma despesa é registrada numa conta e numa categoria, ambas na mesma moeda
- **THEN** nenhuma perna de conversão é gravada, e a operação permanece com as suas duas entries

#### Scenario: Despesa paga em moeda local não é cruzada
- **WHEN** o usuário registra uma despesa a partir de uma conta em BRL
- **THEN** a perna nominal posta na conta nominal em BRL, a operação é monomoeda, e nenhuma conversão participa dela

#### Scenario: Desbalanceamento monomoeda continua recusado
- **WHEN** uma intenção com uma só moeda é submetida com resíduo diferente de zero
- **THEN** a persistência é recusada com erro tipado, e nenhuma perna de conversão é sintetizada para encobri-la

#### Scenario: Resíduos de mesmo sinal são recusados
- **WHEN** uma intenção cruzada é submetida em que todas as moedas envolvidas ganham valor
- **THEN** a persistência é recusada com erro tipado, e nada é gravado

#### Scenario: Perna de conversão não herda a dimensão da fatura
- **WHEN** uma fatura em USD é paga a partir de uma conta em BRL
- **THEN** as pernas de conversão são gravadas sem dimensão, a regra de pouso de dimensão não é violada, e o devido da fatura não é alterado pelo resíduo cambial

### Requirement: Um valor de fachada é denominado pela conta que ele nomeia

Um valor monetário guardado por uma fachada — o valor de uma recorrência, o total de um parcelamento, o limite de um cartão — SHALL ser entendido como denominado na moeda da conta que aquela fachada nomeia. Ele MUST NOT ser transportado para uma conta de outra moeda como se fosse o mesmo número.

Quando uma operação permitir redirecionar a conta ou o cartão de destino no momento da execução, e a moeda do novo destino diferir da moeda em que o valor está denominado, a operação SHALL ser **recusada** com erro tipado. Ela MUST NOT converter o valor em silêncio: converter exigiria escolher uma taxa em nome do usuário no meio de uma operação que ele não pediu e não vê, e gravar sem converter registraria o número de uma moeda como se fosse de outra.

Onde a moeda da conta que a fachada nomeia é imutável, esta regra é vacuamente satisfeita e MUST NOT gerar verificação própria: um parcelamento é denominado na moeda do seu cartão, e o limite de um cartão idem, ambos imutáveis desde a criação da conta.

Uma fachada que **não nomeia conta alguma** — o orçamento, que não tem escopo de conta — MUST NOT herdar a sua denominação de uma preferência de exibição. Ela SHALL declará-la na sua criação e mantê-la (`currency-consolidation`): uma denominação herdada mudaria de significado quando a preferência mudasse, que é o modo de falha que este requisito existe para impedir.

A interface SHALL oferecer apenas os destinos que o domínio aceitaria, em vez de oferecer o que seria recusado. Quando essa restrição reduzir as opções apresentadas, a interface SHALL dizer por que a lista encolheu: uma lista silenciosamente mais curta é indistinguível de uma lista incompleta.

#### Scenario: Confirmar recorrência em conta de outra moeda é recusado
- **WHEN** o usuário confirma um ciclo de uma recorrência criada sobre uma conta em BRL, redirecionando-o para uma conta em USD
- **THEN** a operação é recusada com erro tipado, nada é gravado, e o valor não é convertido nem copiado

#### Scenario: O seletor oferece apenas o que o domínio aceita
- **WHEN** o usuário abre o seletor de conta ao confirmar um ciclo de uma recorrência denominada em USD
- **THEN** apenas contas em USD são oferecidas, e o seletor informa que a lista está restrita à moeda da recorrência

#### Scenario: Confirmar recorrência na mesma moeda é permitido
- **WHEN** o usuário confirma um ciclo redirecionando-o para outra conta de mesma moeda
- **THEN** a operação prossegue normalmente

#### Scenario: Parcelamento não precisa de verificação própria
- **WHEN** as parcelas de um parcelamento são geradas nas faturas do seu cartão
- **THEN** todas estão na moeda do cartão, sem que nenhuma verificação de moeda seja executada, porque essa moeda é imutável

#### Scenario: Medidor de limite é monomoeda
- **WHEN** o limite disponível de um cartão é exibido como a diferença entre o limite e o devido
- **THEN** as duas parcelas estão na moeda do cartão, e a subtração não atravessa moedas

### Requirement: A taxa de câmbio de uma operação é derivada, nunca persistida na operação

A taxa aplicada numa operação que atravessa moedas SHALL ser derivável das suas próprias pernas, e MUST NOT ser persistida como estado da transação, da entry ou de qualquer modelo paralelo. A intenção de escrita MUST NOT receber taxa como parâmetro: ela informa os valores de cada ponta — o que o extrato do usuário mostra —, e a relação entre eles **é** a taxa.

Esta é a mesma decisão já tomada para o rótulo da operação e para o sinal de exibição, e pela mesma razão: um valor persistido ao lado de dois outros que o determinam é um terceiro número obrigado a concordar, sem nada garantindo que concorde.

Isso MUST NOT ser confundido com o histórico de taxas mantido para consolidação (`currency-consolidation`), que é dado próprio, com data e origem, e vive fora do razão. Que uma operação cruzada **alimente** esse histórico é decisão daquela capability; que a operação **guarde** a sua taxa é o que este requisito proíbe.

#### Scenario: Intenção sem taxa
- **WHEN** o vocabulário de escrita é inspecionado
- **THEN** nenhuma intenção, perna ou contrapartida carrega taxa de câmbio

#### Scenario: Taxa recuperável da operação
- **WHEN** uma operação cruzada gravada é lida
- **THEN** a taxa aplicada é derivável dos valores das suas pernas, sem consultar nenhum campo persistido de taxa

#### Scenario: Saldo de conversão é o resultado cambial
- **WHEN** o saldo das contas de conversão é lido por moeda
- **THEN** ele expressa o resultado cambial realizado, pelo mesmo mecanismo de soma de entries das demais contas, sendo `CONVERSION` de natureza credora e portanto com o saldo bruto oposto em sinal ao ganho
