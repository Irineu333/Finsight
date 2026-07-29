## MODIFIED Requirements

### Requirement: Invariante de soma zero validada na escrita
O sistema SHALL validar a invariante de soma zero por moeda em um único ponto na fronteira de escrita e MUST NOT persistir qualquer operação cujas entries não somem zero em alguma moeda. A falha SHALL ser reportada com um erro tipado (via `Either`), não com exceção silenciosa nem correção automática.

A invariante MUST NOT admitir exceção alguma — **inclusive para a operação que atravessa moedas**. Uma operação assim não é uma transação desbalanceada tolerada: ela chega **incompleta** à fronteira, que a completa com pernas de conversão até que cada moeda some zero (ver o requisito de completar a intenção cruzada). Consequentemente, `Σ entries = 0` por `(transação, moeda)` SHALL permanecer verificável **lendo apenas as entries**, sem consultar as contas das pernas, o plano de contas ou qualquer tabela de fachada — que é a condição para que a verificação continue válida antes e depois de qualquer reescrita do plano de contas.

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

#### Scenario: Invariante verificável só pelas entries
- **WHEN** a verificação de balanceamento do razão é executada sobre um banco com operações cruzadas
- **THEN** ela agrupa por `(transação, moeda)` sobre as entries, sem consultar contas, e não encontra violação

#### Scenario: Validações concentradas num ponto
- **WHEN** o código de escrita é inspecionado
- **THEN** soma zero, compatibilidade de dimensão e fechamento de conta são verificados no mesmo ponto, e em nenhum outro

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

## ADDED Requirements

### Requirement: Operação que atravessa moedas é completada com pernas de conversão

Quando as pernas de uma intenção envolvem mais de uma moeda, a fronteira de escrita SHALL completar a operação lançando, para **cada** moeda presente, o oposto do resíduo daquela moeda na **conta de conversão daquela moeda**. O resultado gravado SHALL somar zero em cada moeda, pela invariante geral e sem exceção a ela.

A regra SHALL ser uniforme, sem ramo por caso de uso: uma transferência entre contas de moedas diferentes, o pagamento de uma fatura em moeda distinta da conta pagadora e qualquer outra operação cruzada SHALL ser completadas pelo mesmo mecanismo, que MUST NOT nomear fachada alguma.

Quando **uma só** moeda estiver presente, o resíduo SHALL ser zero — o comportamento existente permanece intacto —, e uma perna avulsa SHALL continuar sendo completada pela contrapartida declarada na intenção. A conta de conversão MUST NOT ser usada para completar uma operação monomoeda: ali, resíduo não nulo é desbalanceamento, e SHALL ser recusado.

Quando duas ou mais moedas estiverem presentes, os resíduos MUST NOT ser todos do mesmo sinal. Uma intenção em que toda moeda envolvida ganha valor cria dinheiro sem origem: não é câmbio, e SHALL ser recusada com erro tipado.

#### Scenario: Transferência entre moedas soma zero nas duas
- **WHEN** o usuário transfere de uma conta em BRL para uma conta em USD, informando o valor que saiu e o valor que entrou
- **THEN** a operação é gravada com quatro entries — a conta BRL e a conversão em BRL, a conversão em USD e a conta USD — somando zero em BRL e zero em USD

#### Scenario: Pagamento de fatura em outra moeda
- **WHEN** o usuário paga uma fatura de um cartão em USD a partir de uma conta em BRL
- **THEN** a operação é completada pelo mesmo mecanismo de conversão, sem regra específica de fatura

#### Scenario: Operação monomoeda não usa conversão
- **WHEN** uma despesa é registrada numa conta e numa categoria, ambas na mesma moeda
- **THEN** nenhuma perna de conversão é gravada, e a operação permanece com as suas duas entries

#### Scenario: Desbalanceamento monomoeda continua recusado
- **WHEN** uma intenção com uma só moeda é submetida com resíduo diferente de zero
- **THEN** a persistência é recusada com erro tipado, e nenhuma perna de conversão é sintetizada para encobri-la

#### Scenario: Resíduos de mesmo sinal são recusados
- **WHEN** uma intenção cruzada é submetida em que todas as moedas envolvidas ganham valor
- **THEN** a persistência é recusada com erro tipado, e nada é gravado

### Requirement: A taxa de câmbio de uma operação é derivada, nunca persistida

A taxa aplicada numa operação que atravessa moedas SHALL ser derivável das suas próprias pernas, e MUST NOT ser persistida como estado da transação, da entry ou de qualquer modelo paralelo. A intenção de escrita MUST NOT receber taxa como parâmetro: ela informa os valores de cada ponta — o que o extrato do usuário mostra —, e a relação entre eles **é** a taxa.

Esta é a mesma decisão já tomada para o rótulo da operação e para o sinal de exibição, e pela mesma razão: um valor persistido ao lado de dois outros que o determinam é um terceiro número obrigado a concordar, sem nada garantindo que concorde.

#### Scenario: Intenção sem taxa
- **WHEN** o vocabulário de escrita é inspecionado
- **THEN** nenhuma intenção, perna ou contrapartida carrega taxa de câmbio

#### Scenario: Taxa recuperável da operação
- **WHEN** uma operação cruzada gravada é lida
- **THEN** a taxa aplicada é derivável dos valores das suas pernas, sem consultar nenhum campo persistido de taxa

#### Scenario: Saldo de conversão é o resultado cambial
- **WHEN** o saldo das contas de conversão é lido por moeda
- **THEN** ele expressa o resultado cambial acumulado das operações cruzadas, pelo mesmo mecanismo de soma de entries das demais contas
