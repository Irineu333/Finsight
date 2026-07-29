## ADDED Requirements

### Requirement: A moeda base é preferência de exibição, não fato contábil

O sistema SHALL manter uma **moeda base** do usuário, usada exclusivamente para reduzir a uma única figura os valores que o razão devolve por moeda. Ela SHALL ser preferência de exibição, e MUST NOT ser propriedade de conta, de entry, de transação ou de qualquer dado do razão.

Trocar a moeda base MUST NOT alterar dado algum já gravado, e MUST NOT exigir migração ou reprocessamento: as figuras consolidadas SHALL ser recalculadas na leitura seguinte, retroativamente e por inteiro. Nenhum valor convertido SHALL ser persistido.

Trocar a moeda base MUST NOT invalidar o acervo de taxas. A taxa da base anterior contra a nova SHALL ser a inversa da que já existe, e as demais SHALL ser re-expressas por triangulação sobre as taxas de mesma data. Isso é derivação, não migração: nenhuma linha gravada muda.

A moeda base MUST NOT ser confundida com a moeda padrão que uma conta nova recebe quando nenhuma é escolhida: aquela é um padrão de criação e pertence ao razão.

#### Scenario: Troca de moeda base é imediata e retroativa
- **WHEN** o usuário troca a moeda base de BRL para USD
- **THEN** toda figura consolidada passa a ser expressa em dólar, incluindo períodos passados, sem migração e sem alterar nenhuma entry

#### Scenario: Troca de moeda base preserva as taxas
- **WHEN** o usuário troca a moeda base e existiam taxas cadastradas contra a base anterior
- **THEN** as taxas continuam utilizáveis, derivadas por inversão e triangulação, sem que nenhuma linha gravada seja alterada

#### Scenario: Nenhum valor convertido persistido
- **WHEN** os dados gravados são inspecionados
- **THEN** não existe coluna, campo ou tabela guardando valor convertido para a moeda base

### Requirement: A conversão acontece fora do razão

A conversão de um resultado por moeda numa figura na base SHALL acontecer acima do razão, consumindo o que ele devolve. O razão MUST NOT ser consultado com uma moeda base, MUST NOT receber taxa por parâmetro e MUST NOT ter dependência que forneça taxa.

A camada de consolidação SHALL ter dono único: a redução de um resultado por moeda a uma figura na base SHALL ter exatamente uma implementação, consumida por toda feature que exiba figura consolidada. Nenhuma tela, ViewModel ou modelo de UI SHALL converter por conta própria.

A responsabilidade desta camada SHALL ser a **conversão entre moedas**, e apenas ela. Combinar dois resultados por moeda — somar perímetros disjuntos, por exemplo — MUST NOT pertencer a esta camada: é aritmética sobre saldos, e o seu dono é o razão (`ledger-reporting`).

#### Scenario: Dashboard consome a consolidação
- **WHEN** o dashboard exibe o patrimônio total
- **THEN** ele obtém o resultado por moeda do razão e o reduz pela única implementação de consolidação

#### Scenario: Nenhuma conversão em linha
- **WHEN** o código é inspecionado
- **THEN** não existe multiplicação por taxa em tela, ViewModel ou modelo de UI

#### Scenario: Somar perímetros não é consolidar
- **WHEN** a interface da camada de consolidação é inspecionada
- **THEN** ela não expõe soma de dois resultados por moeda

### Requirement: A taxa é local, datada e única por moeda

O sistema SHALL manter um histórico de taxas de conversão por moeda **para a moeda base**, cada taxa com a sua **data** e a sua **origem**. O sistema MUST NOT manter uma matriz de pares: só se converte para a base, e uma conversão cruzada, se necessária, SHALL ser derivada das taxas para a base.

A consolidação de uma figura referente a um instante ou período SHALL usar **a última taxa em ou antes daquela data**. Uma figura de um período passado MUST NOT ser recalculada à taxa corrente: o passado não SHALL se mover sozinho quando a taxa muda.

A taxa gravada localmente SHALL ser a única autoridade usada em qualquer conversão. O usuário SHALL poder cadastrar e corrigir taxas a qualquer momento, e uma taxa informada pelo usuário SHALL prevalecer sobre uma derivada de operação na mesma data.

Uma fonte externa MAY oferecer um valor **sugerido** dentro da tela que edita a taxa, e MUST NOT ser consultada em nenhum outro ponto. Nenhuma leitura, tela ou figura do app SHALL depender de rede, apresentar estado de carregamento ou falhar por indisponibilidade em razão de conversão de moeda.

O sistema SHALL apresentar, junto de onde as taxas são editadas, a data e a origem de cada uma.

#### Scenario: Conversão usa a taxa local
- **WHEN** uma figura consolidada é calculada
- **THEN** ela usa a taxa gravada localmente, sem consultar nenhuma fonte externa

#### Scenario: Figura de período passado usa a taxa da época
- **WHEN** o patrimônio de um mês passado é consolidado e existem taxas anteriores e posteriores àquele mês
- **THEN** a conversão usa a última taxa em ou antes daquela data, e o valor não muda quando uma taxa mais recente é cadastrada

#### Scenario: Sugestão só na edição
- **WHEN** o usuário abre a tela de edição de uma taxa
- **THEN** um valor sugerido pode ser oferecido, e ele só passa a valer se o usuário o confirmar

#### Scenario: App inteiro funciona offline
- **WHEN** o dispositivo está sem rede e qualquer tela com figura consolidada é aberta
- **THEN** a figura é exibida normalmente, sem erro e sem estado de carregamento

#### Scenario: Data e origem visíveis
- **WHEN** o usuário abre a tela de taxas
- **THEN** cada taxa exibe a sua data e se veio de uma operação ou do próprio usuário

#### Scenario: Uma taxa por moeda e data
- **WHEN** as taxas gravadas são inspecionadas com três moedas em uso
- **THEN** existem taxas apenas das duas moedas não-base contra a base, e nenhuma taxa entre duas moedas não-base

### Requirement: Uma operação que atravessa moedas cadastra a sua própria taxa

Quando uma operação atravessa moedas, o sistema SHALL registrar no histórico a taxa que ela aplica, derivada dos valores das suas duas pontas, com a **data da operação** e com a origem que a identifica como derivada de operação.

O usuário MUST NOT precisar informar uma taxa que já está implícita numa operação que ele registrou. Uma taxa informada pelo usuário para a mesma data SHALL prevalecer sobre a derivada.

Isso MUST NOT ser confundido com persistir a taxa **na** operação, o que `balanced-ledger` proíbe: o que se grava é uma linha do histórico de taxas, dado próprio desta capability, e a operação permanece sem campo de taxa.

#### Scenario: Câmbio alimenta o histórico
- **WHEN** o usuário registra uma transferência de R$ 550 para US$ 100 numa data
- **THEN** uma taxa de 5,50 para o dólar é registrada naquela data, com origem de operação

#### Scenario: Taxa do usuário prevalece
- **WHEN** existe uma taxa derivada de operação e o usuário cadastra outra para a mesma data
- **THEN** a do usuário é a usada nas conversões daquela data

#### Scenario: A operação continua sem taxa
- **WHEN** a operação que originou a taxa é inspecionada
- **THEN** ela não possui campo de taxa, e a taxa registrada é uma linha do histórico

### Requirement: Consolida-se até onde a taxa permitir, e nunca se inventa um valor

A consolidação SHALL reduzir um resultado por moeda **até onde as taxas disponíveis permitirem**, produzindo uma figura composta de um ou mais termos: um termo na moeda base com tudo o que pôde ser convertido, e um termo próprio para cada moeda cuja taxa é desconhecida.

Uma taxa ausente MUST NOT ser tratada como `1`, MUST NOT fazer a parcela correspondente ser omitida da figura, e MUST NOT impedir a exibição das parcelas que puderam ser convertidas. O estado em que uma conta em moeda não-base existe e a sua taxa ainda não foi cadastrada é alcançável por construção, e SHALL ter comportamento definido.

A consolidação SHALL produzir, junto de cada figura, se ela é exata ou aproximada, conforme a regra definida em `money-display`. Uma figura MUST NOT ser entregue a um consumidor sem essa informação. Um resultado que não exigiu conversão alguma SHALL ser entregue como **exato**, e a consolidação MUST NOT marcá-lo como aproximado por ter passado por ela.

#### Scenario: Consolidação de uma moeda igual à base
- **WHEN** um resultado contendo apenas a moeda base é consolidado
- **THEN** a figura resultante tem um termo, é exata, e o seu valor é idêntico ao devolvido pelo razão

#### Scenario: Consolidação de duas moedas com taxa conhecida
- **WHEN** um resultado contendo duas moedas é consolidado e ambas as taxas são conhecidas
- **THEN** a figura resultante tem um termo na moeda base, é aproximada, e o seu valor é a soma das parcelas convertidas

#### Scenario: Consolidação parcial sem taxa
- **WHEN** um resultado contendo R$ 100,00 e US$ 50,00 é consolidado sem taxa cadastrada para o dólar
- **THEN** a figura resultante tem dois termos — R$ 100,00 e US$ 50,00 — e nenhuma parcela é omitida nem convertida a uma taxa presumida

#### Scenario: Moeda única sem taxa permanece na sua moeda
- **WHEN** um resultado contendo apenas dólares é consolidado sem taxa cadastrada
- **THEN** a figura resultante tem um termo em dólar e é exata, porque nada foi convertido

#### Scenario: Exatidão não é opcional
- **WHEN** a interface da camada de consolidação é inspecionada
- **THEN** não existe forma de obter uma figura consolidada sem a sua exatidão

### Requirement: O conjunto de moedas oferecidas é curado e de duas casas decimais

O sistema SHALL oferecer ao usuário um conjunto curado de moedas, restrito às de **duas** casas decimais. O catálogo dessas moedas SHALL pertencer a esta camada, e MUST NOT ser conhecido pelo razão, que persiste apenas o código da moeda.

Essa restrição SHALL ser uma premissa registrada, e não um esquecimento: a aritmética de menor unidade do sistema assume base 100 na escrita e na leitura, e admitir moeda de zero ou três casas exige refazer essa fronteira.

#### Scenario: Seletor oferece apenas moedas de duas casas
- **WHEN** o usuário escolhe a moeda de uma conta
- **THEN** apenas moedas de duas casas decimais são oferecidas

#### Scenario: Razão não conhece o catálogo
- **WHEN** o razão é inspecionado
- **THEN** ele persiste o código da moeda sem conhecer quais moedas o app oferece
