## ADDED Requirements

### Requirement: O não classificado é um valor do eixo, não a ausência de um item

Um detalhamento por categoria SHALL representar o seu eixo analítico por um tipo-soma com dois
valores: uma categoria, ou a ausência de classificação. O não classificado MUST NOT ser modelado
como `Category` nula, como categoria de sistema, como conta-balde no plano de contas, nem como
dimensão dedicada — ele continua sendo a **ausência de dimensão** que o razão define.

Consequência que a modelagem existe para garantir: o não classificado é uma chave legítima da
família de figuras que o redutor de consolidação ordena e divide, o que uma ausência não poderia
ser.

#### Scenario: O não classificado não tem identidade de categoria
- **WHEN** uma linha "sem categoria" é exibida
- **THEN** ela não expõe id, nome persistido, ícone ou cor de categoria, e nada no sistema permite
  renomeá-la, arquivá-la ou removê-la

#### Scenario: A escrita não cria classificação
- **WHEN** uma transação é registrada sem categoria
- **THEN** a perna nominal é gravada sem dimensão, e nenhuma categoria ou conta é criada para
  representá-la

#### Scenario: Dimensão órfã não é lavada no balde
- **WHEN** uma perna nominal carrega uma dimensão que não resolve para categoria alguma
- **THEN** ela MUST NOT ser somada ao total sem categoria, porque isso é falha de integridade e não
  ausência de classificação

### Requirement: O total sem classificação participa do denominador

Quando existe movimento sem classificação no período, o total sem classificação SHALL entrar na
escala comparativa junto com as categorias — a mesma escala, construída a partir das mesmas taxas e
da mesma data que denominam as figuras exibidas. As porcentagens do detalhamento SHALL, portanto,
somar o todo do período.

MUST NOT existir uma escala construída apenas sobre as categorias resolvidas enquanto o total sem
classificação é exibido fora dela.

#### Scenario: As fatias fecham o período
- **WHEN** um mês tem R$ 700,00 em categorias e R$ 300,00 sem categoria
- **THEN** as categorias somam 70% e a linha sem categoria exibe 30%

#### Scenario: A fatia da categoria encolhe ao revelar o não classificado
- **WHEN** um período que exibia uma categoria com 100% passa a exibir também gasto sem categoria
- **THEN** a porcentagem daquela categoria diminui, e o seu valor monetário permanece idêntico

#### Scenario: Sem denominador, ninguém tem barra
- **WHEN** o total sem classificação está numa moeda que nenhuma taxa alcança
- **THEN** ele não tem porcentagem, e nenhuma linha do detalhamento tem porcentagem, pela mesma
  regra que já vale para uma categoria não mensurável — o todo não é conhecido

### Requirement: A linha sem classificação é fixada no fim do detalhamento

O total sem classificação SHALL ser posicionado como o último item do detalhamento,
independentemente da sua magnitude, e SHALL ser distinguível visualmente das categorias reais. A
ordenação por magnitude decrescente SHALL continuar valendo entre as categorias, sem que a linha sem
classificação dispute posição.

A regra de ordenação SHALL ter um único dono no domínio, consumida por toda superfície que renderiza
o detalhamento — tela e exportação.

#### Scenario: Maior que todas e ainda assim por último
- **WHEN** o total sem categoria é maior que o de qualquer categoria do período
- **THEN** ele continua sendo exibido como o último item

#### Scenario: A exportação ordena como a tela
- **WHEN** um relatório é exportado
- **THEN** a ordem dos itens e a posição da linha sem categoria são as mesmas da tela

### Requirement: A linha sem classificação existe apenas quando há o que mostrar

O total sem classificação SHALL ser omitido do detalhamento quando for zero ou inexistente, pela
mesma regra que já omite uma categoria sem movimento. Um período inteiramente classificado SHALL
produzir exatamente o detalhamento que produzia antes desta capacidade existir — mesmos itens,
mesmas porcentagens, mesma ordem.

#### Scenario: Período inteiramente classificado
- **WHEN** todo movimento nominal do período carrega dimensão
- **THEN** nenhuma linha sem categoria é exibida, e as porcentagens das categorias são as mesmas de
  antes

#### Scenario: Período sem movimento algum
- **WHEN** o período não tem movimento nominal
- **THEN** o detalhamento não ganha uma linha sem categoria zerada

### Requirement: A regra vale para despesa e receita, em toda superfície de detalhamento

O detalhamento de **receitas** por categoria SHALL exibir o seu próprio total sem classificação, no
nominal `INCOME`, com as mesmas regras de denominador, posição e omissão do detalhamento de
despesas. As regras SHALL valer igualmente no dashboard, na tela de relatório e na exportação do
relatório.

#### Scenario: Receita sem categoria
- **WHEN** existe receita sem categoria no período
- **THEN** o detalhamento de receitas exibe a sua linha sem categoria, com barra e porcentagem sobre
  o total de receitas do período

#### Scenario: Os dois detalhamentos não se misturam
- **WHEN** existem despesa e receita sem categoria no mesmo período
- **THEN** cada detalhamento exibe apenas o total sem classificação da sua própria natureza

### Requirement: O rótulo do não classificado é resolvido na apresentação

O texto que nomeia a linha sem classificação SHALL ser um recurso de string traduzido, resolvido na
camada de apresentação a partir do valor do eixo. O modelo de domínio do detalhamento MUST NOT
carregar texto voltado ao usuário.

#### Scenario: Rótulo nos dois idiomas
- **WHEN** o app roda em português e em inglês
- **THEN** a linha é nomeada por uma chave de string presente nos dois arquivos de recursos

#### Scenario: Domínio sem texto de usuário
- **WHEN** o construtor do detalhamento produz o item sem classificação
- **THEN** ele não define nenhum rótulo — quem renderiza decide o texto
