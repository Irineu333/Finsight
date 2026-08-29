# budget-list-row Specification

## ADDED Requirements

### Requirement: A linha discrimina, e não antecipa a ficha

A linha da lista de orçamentos SHALL afirmar o que separa um orçamento do seguinte: a sua
**identidade**, o seu **teto**, **o que ele mede** e **quanto dele já foi usado**.

A linha MUST NOT repetir a ficha de detalhe. Tudo o que o detalhe já enuncia — o limite, o
gasto, o que resta, o quanto excedeu, a lista completa de categorias e a receita base — está a
um toque de distância, rotulado, e a linha que os antecipa todos paga altura para não
acrescentar nada. O critério da linha é *o que distingue esta das demais*, e não *o que é
importante saber*.

Em particular, a linha MUST NOT exibir **o que resta** nem **o quanto excedeu** como figuras
próprias: ambos são deriváveis dos dois números que ela já imprime, e nenhum dos dois responde
à pergunta *o que é este orçamento*.

A linha MUST NOT carregar legenda que nomeie a natureza de uma figura que apareça sozinha:
numa tela cujo assunto é orçamento, toda linha tem um teto, e o rótulo é constante. Um rótulo
que **desambigua duas figuras monetárias** exibidas juntas não recai nesta proibição, e SHALL
acompanhar a segunda delas.

A identidade SHALL ceder largura antes das figuras quando o espaço não comportar as duas.

#### Scenario: Dois orçamentos de mesmo nome
- **WHEN** dois orçamentos têm o mesmo título e vigiam categorias diferentes
- **THEN** a linha de cada um exibe as categorias que ele mede, e os dois são distinguíveis sem abrir o detalhe

#### Scenario: Título longo
- **WHEN** o título do orçamento não cabe na largura disponível
- **THEN** o título é truncado e o teto permanece integralmente legível

#### Scenario: O que resta não é exibido na linha
- **WHEN** um orçamento de R$ 300,00 com R$ 240,00 gastos é exibido na lista
- **THEN** a linha exibe o teto e o gasto, e não exibe uma figura para os R$ 60,00 restantes

### Requirement: O número principal da linha é o teto cadastrado

A figura de maior peso visual da linha SHALL ser o **limite** do orçamento, e não o gasto.

A tela de orçamentos é a superfície onde um orçamento é **criado, editado e apagado**: o teto
é a sua definição, e é o que distingue um orçamento de R$ 300,00 de um de R$ 2.500,00. Quanto
já se gastou é pergunta de **acompanhamento**, cuja superfície é o resumo do painel — que
segue exibindo o par gasto/limite, e MUST NOT ser alinhado a esta regra: as duas superfícies
respondem a perguntas diferentes.

O teto tem uma segunda propriedade que o qualifica para esse lugar: ele é **imune à
consolidação**. Foi digitado pelo usuário, na moeda escolhida na criação do orçamento, e nada
nele depende de taxa — é a única figura da linha que nunca se torna a marca de ausência.

#### Scenario: O teto permanece com o gasto irresolvível
- **WHEN** parte do gasto de um orçamento está numa moeda que nenhuma taxa alcança
- **THEN** o teto é exibido inteiro, e apenas o gasto recai na marca de ausência

#### Scenario: O painel não segue esta regra
- **WHEN** o resumo de orçamentos do painel é exibido
- **THEN** ele continua apresentando gasto e limite como par, porque responde a outra pergunta

### Requirement: As categorias são o segundo dado, e os seus ícones não são tintados

A linha SHALL exibir **as categorias que o orçamento mede**, e MUST NOT substituí-las por uma
contagem: "3 categorias" é verdade sobre quase todo orçamento e não distingue linha alguma da
vizinha, que é o critério pelo qual esta linha decide o que afirmar.

A representação SHALL ter **largura constante**, de modo que a identidade e a figura não
disputem espaço com um número variável de categorias; categorias além do que a largura
comporta SHALL ser declaradas por um excedente contado.

Os ícones de categoria exibidos na linha MUST NOT receber a cor de categoria do sistema. Essa
cor responde por **tipo**, e um orçamento contém apenas categorias de despesa — de modo que
todos os seus ícones sairiam na mesma cor, que é a mesma que o anel de progresso usa para
significar *estourado*. Um orçamento tranquilo exibiria então um indicador verde ao lado de
três marcas vermelhas, e um estourado exibiria quatro vermelhos de dois significados
distintos. Na linha, **a cor tem um dono único: o progresso.**

#### Scenario: Categorias além da largura
- **WHEN** um orçamento contém mais categorias do que a pilha comporta
- **THEN** as que cabem são exibidas e as demais são declaradas por um excedente contado, sem que a linha mude de largura

#### Scenario: Ícones sem cor de categoria
- **WHEN** a linha de um orçamento tranquilo exibe as suas categorias
- **THEN** os ícones das categorias são neutros, e o único elemento colorido da linha é o indicador de progresso

### Requirement: Nenhum estado da linha é carregado apenas por cor

Todo estado que a linha afirma SHALL ser legível sem depender da cor: um ícone, um texto, ou
os dois.

Isso vale, nomeadamente, para o **estouro**. Com o teto como figura principal, a linha não tem
rótulo que troque de "restante" para "excedido", e o indicador de progresso satura ao atingir
o limite — cheio, ele não distingue 100% de 300%. O estouro SHALL portanto ser afirmado por um
glifo com descrição de conteúdo que o nomeie, **e** pela relação aritmética entre as duas
figuras que a linha já exibe: um gasto maior que o teto é o estouro, dito por dois meios
independentes antes de qualquer cor.

#### Scenario: Orçamento estourado
- **WHEN** o gasto de um orçamento ultrapassa o seu limite
- **THEN** a linha exibe um glifo cuja descrição de conteúdo nomeia o estouro, e a figura do gasto é maior que a do teto

#### Scenario: Estouro não depende do indicador
- **WHEN** um orçamento está exatamente no limite e outro o ultrapassou em três vezes
- **THEN** os dois são distinguíveis na linha, ainda que o indicador de progresso esteja igualmente cheio nos dois

### Requirement: Um teto derivado se declara como derivado

A linha SHALL declarar um limite que é **percentual de uma receita base**, e não valor digitado.

O valor chega à superfície já calculado, e sem essa declaração é indistinguível de um teto
fixo — o que faria a linha afirmar permanência sobre um número que se re-deriva a cada mês.
A declaração SHALL usar o mesmo glifo com que o sistema já significa *recorrência* nas demais
telas, SHALL exibir o percentual **e a receita base de que ele é fração**, e MUST NOT usar cor
que o sistema já reservou para outro significado.

**O percentual sozinho não discrimina**, que é o critério pelo qual esta linha decide o que
afirmar: "30%" é a mesma marca num orçamento que toma uma fração do salário e noutro que a
toma do aluguel, e quem mantém os dois leria a mesma declaração duas vezes. A ficha de detalhe
continua enunciando a receita base por extenso e navegando até ela; o que a linha acrescenta é
**qual** delas.

As duas partes cedem largura em ordem: o percentual é a derivação e MUST NOT ser truncado; o
nome diz *qual* derivação e SHALL ceder primeiro, em **uma linha só**, truncado quando não
couber. A declaração inteira SHALL ter largura limitada, de modo que um nome longo não empurre
a identidade do orçamento para fora da linha.

Onde a receita base não existe mais, a declaração SHALL exibir apenas o percentual, e MUST NOT
deixar o separador pendurado.

#### Scenario: Teto percentual
- **WHEN** um orçamento tem limite de 30% de uma receita base chamada "Salário"
- **THEN** a linha exibe o valor derivado do mês, acompanhado do glifo de recorrência, do percentual e do nome da receita

#### Scenario: Dois tetos derivados de receitas diferentes
- **WHEN** dois orçamentos tomam 30% de receitas base distintas
- **THEN** as duas declarações são distinguíveis sem abrir o detalhe

#### Scenario: Nome de receita longo
- **WHEN** o nome da receita base não cabe na largura reservada à declaração
- **THEN** o nome é truncado, o percentual permanece inteiro, e a identidade do orçamento conserva o seu lugar na linha

#### Scenario: Receita base removida
- **WHEN** a recorrência de que o teto deriva não existe mais
- **THEN** a declaração exibe apenas o percentual

#### Scenario: Teto fixo não recebe declaração
- **WHEN** um orçamento tem limite digitado
- **THEN** nenhum marcador de derivação acompanha o valor

### Requirement: A linha é superfície de gramática própria, e o aviso de câmbio é da lista

A linha SHALL ser tratada como **superfície de gramática própria** no sentido de
`money-display`: quando o gasto reúne parcela que nenhuma taxa alcança, ela SHALL exibir a
marca de ausência, discreta, e MUST NOT exibir as parcelas separadas.

Esta é a declaração que aquela capacidade exige de cada superfície, e ela é feita **aqui**
porque não pode ser deixada ao layout. A ficha de detalhe permanece a superfície com espaço, e
segue exibindo as parcelas.

A explicação dessa marca MUST NOT viver em cada linha. A causa é **global** — falta taxa para
uma moeda —, não propriedade de um orçamento, e a mesma explicação serve a todas as linhas
afetadas. A lista SHALL exibir **um único aviso**, com acesso ao acervo de taxas, sempre que
alguma das suas linhas recair na marca de ausência.

Quando não há fração de progresso, o indicador MUST NOT ser desenhado como vazio — um
indicador vazio afirma "nada gasto ainda", que é exatamente o que não se sabe —, e a linha
SHALL conservar a mesma altura das demais.

#### Scenario: Gasto com parcela não precificada
- **WHEN** parte do gasto de um orçamento está numa moeda sem taxa
- **THEN** a linha exibe a marca de ausência no lugar do gasto, mantém a altura das demais, e o indicador de progresso não é desenhado

#### Scenario: Um aviso para várias linhas
- **WHEN** três orçamentos da lista têm gasto irresolvível
- **THEN** um único aviso é exibido para a lista, e nenhuma das três linhas carrega explicação própria

#### Scenario: Nenhuma linha irresolvível
- **WHEN** todos os gastos da lista puderam ser reduzidos à moeda dos seus limites
- **THEN** nenhum aviso é exibido

#### Scenario: O detalhe continua exibindo as parcelas
- **WHEN** o mesmo orçamento é aberto na ficha de detalhe
- **THEN** cada parcela aparece na sua própria moeda, e a marca de ausência não é usada

### Requirement: A lista é ordenada por progresso, e não pela ordem de criação

A lista de orçamentos SHALL ser ordenada por **proporção do teto já consumida**, em ordem
decrescente.

A ordem de criação não responde a pergunta alguma que a tela faça, e faz um orçamento
estourado aparecer onde calhar. Ordenar por progresso põe no topo o que pede ação, sem custar
altura, e faz do gradiente de cor uma escala contínua de cima para baixo — a leitura mais
rápida que a lista pode oferecer.

Um orçamento cujo progresso não é conhecido MUST NOT ser ordenado como se fosse zero: sem
fração, ele não tem posição na escala, e SHALL ser agrupado ao fim da lista.

A ordenação MUST NOT ser feita na consulta que lê os orçamentos: o progresso não é propriedade
do orçamento armazenado, e sim resultado de uma leitura do razão reduzida à moeda do limite.

A lista MUST NOT ser dividida em seções por estado de progresso. Um cabeçalho de seção custa
altura fixa, e com o número de orçamentos que um usuário mantém ele organiza menos do que
consome; a ordenação já entrega a mesma hierarquia por zero dp.

#### Scenario: Estourado no topo
- **WHEN** a lista contém um orçamento estourado criado por último e um tranquilo criado primeiro
- **THEN** o estourado aparece acima

#### Scenario: Progresso desconhecido vai ao fim
- **WHEN** um orçamento tem gasto irresolvível e outros têm progresso conhecido
- **THEN** o irresolvível aparece depois de todos os que têm progresso, e não entre os de menor consumo

### Requirement: A linha comporta o período do orçamento

A linha SHALL reservar lugar para declarar o **período** a que o teto se refere, junto à
identidade do orçamento.

Todo orçamento é mensal hoje, e um período que toda linha carrega não distingue linha alguma
da vizinha — de modo que ele MUST NOT ser exibido enquanto essa for a única possibilidade.
O lugar é reservado porque o mesmo teto significa coisas opostas em períodos diferentes:
uma figura sem o período que a qualifica é uma figura que não se pode ler.

#### Scenario: Todos os orçamentos mensais
- **WHEN** nenhum orçamento da base tem período diferente do mensal
- **THEN** nenhuma linha exibe o período

#### Scenario: Períodos coexistindo
- **WHEN** um orçamento semanal e um mensal aparecem na mesma lista
- **THEN** cada linha declara o seu período junto à identidade
