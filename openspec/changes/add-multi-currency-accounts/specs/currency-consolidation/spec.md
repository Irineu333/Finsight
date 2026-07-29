## ADDED Requirements

### Requirement: A moeda base é preferência de exibição, não fato contábil

O sistema SHALL manter uma **moeda base** do usuário, usada exclusivamente para reduzir a uma única figura os valores que o razão devolve por moeda. Ela SHALL ser preferência de exibição, e MUST NOT ser propriedade de conta, de entry, de transação ou de qualquer dado do razão.

Trocar a moeda base MUST NOT alterar dado algum já gravado, e MUST NOT exigir migração ou reprocessamento: as figuras consolidadas SHALL ser recalculadas na leitura seguinte, retroativamente e por inteiro. Nenhum valor convertido SHALL ser persistido.

A moeda base MUST NOT ser confundida com a moeda padrão que uma conta nova recebe quando nenhuma é escolhida: aquela é um padrão de criação e pertence ao razão.

#### Scenario: Troca de moeda base é imediata e retroativa
- **WHEN** o usuário troca a moeda base de BRL para USD
- **THEN** toda figura consolidada passa a ser expressa em dólar, incluindo períodos passados, sem migração e sem alterar nenhuma entry

#### Scenario: Nenhum valor convertido persistido
- **WHEN** os dados gravados são inspecionados
- **THEN** não existe coluna, campo ou tabela guardando valor convertido para a moeda base

### Requirement: A conversão acontece fora do razão

A conversão de um resultado por moeda numa figura única SHALL acontecer acima do razão, consumindo o que ele devolve. O razão MUST NOT ser consultado com uma moeda base, MUST NOT receber taxa por parâmetro e MUST NOT ter dependência que forneça taxa.

A camada de consolidação SHALL ter dono único: a redução de um resultado por moeda a uma figura na base SHALL ter exatamente uma implementação, consumida por toda feature que exiba figura consolidada. Nenhuma tela, ViewModel ou modelo de UI SHALL converter por conta própria.

#### Scenario: Dashboard consome a consolidação
- **WHEN** o dashboard exibe o patrimônio total
- **THEN** ele obtém o resultado por moeda do razão e o reduz pela única implementação de consolidação

#### Scenario: Nenhuma conversão em linha
- **WHEN** o código é inspecionado
- **THEN** não existe multiplicação por taxa em tela, ViewModel ou modelo de UI

### Requirement: A taxa é local, manual e única por moeda

O sistema SHALL manter uma taxa de conversão por moeda **para a moeda base**, e MUST NOT manter uma matriz de pares: só se converte para a base, e uma conversão cruzada, se necessária, SHALL ser derivada das taxas para a base.

A taxa gravada localmente SHALL ser a única autoridade usada em qualquer conversão. O usuário SHALL poder editá-la a qualquer momento.

Uma fonte externa MAY oferecer um valor **sugerido** dentro da tela que edita a taxa, e MUST NOT ser consultada em nenhum outro ponto. Nenhuma leitura, tela ou figura do app SHALL depender de rede, apresentar estado de carregamento ou falhar por indisponibilidade em razão de conversão de moeda.

O sistema SHALL registrar quando cada taxa foi definida pela última vez, e SHALL apresentá-lo junto de onde ela é editada.

#### Scenario: Conversão usa a taxa local
- **WHEN** uma figura consolidada é calculada
- **THEN** ela usa a taxa gravada localmente, sem consultar nenhuma fonte externa

#### Scenario: Sugestão só na edição
- **WHEN** o usuário abre a tela de edição de uma taxa
- **THEN** um valor sugerido pode ser oferecido, e ele só passa a valer se o usuário o confirmar

#### Scenario: App inteiro funciona offline
- **WHEN** o dispositivo está sem rede e qualquer tela com figura consolidada é aberta
- **THEN** a figura é exibida normalmente, sem erro e sem estado de carregamento

#### Scenario: Idade da taxa é visível
- **WHEN** o usuário abre a tela de taxas
- **THEN** cada taxa exibe quando foi definida pela última vez

#### Scenario: Uma taxa por moeda
- **WHEN** as taxas gravadas são inspecionadas com três moedas em uso
- **THEN** existem duas taxas — uma por moeda não-base — e nenhuma taxa entre duas moedas não-base

### Requirement: Toda figura consolidada é produzida com a sua exatidão

A consolidação SHALL produzir, junto de cada figura, se ela é exata ou aproximada, derivando-o do resultado por moeda que a originou, conforme a regra definida em `money-display`. Uma figura consolidada MUST NOT ser entregue a um consumidor sem essa informação.

Um resultado que não exigiu conversão alguma — nenhuma moeda, ou uma única moeda igual à base — SHALL ser entregue como **exato**, e a consolidação MUST NOT marcá-lo como aproximado por ter passado por ela.

#### Scenario: Consolidação de uma moeda igual à base
- **WHEN** um resultado contendo apenas a moeda base é consolidado
- **THEN** a figura resultante é exata, e o seu valor é idêntico ao devolvido pelo razão

#### Scenario: Consolidação de duas moedas
- **WHEN** um resultado contendo duas moedas é consolidado
- **THEN** a figura resultante é aproximada, e o seu valor é a soma das parcelas convertidas pela taxa de cada moeda

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
