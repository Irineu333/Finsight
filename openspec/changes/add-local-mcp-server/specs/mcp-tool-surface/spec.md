## ADDED Requirements

### Requirement: Nenhuma ferramenta decide regra de domínio

Uma ferramenta MCP SHALL delegar toda decisão de domínio ao use case que a possui, e MUST NOT
reimplementar, contornar ou antecipar essa decisão. Compor leituras, traduzir para DTO plano,
resolver identidade a partir de nome, formatar dinheiro, paginar e ordenar são **adaptação**,
e são o trabalho da ferramenta. O que uma operação significa é **decisão**, e tem dono.

Quando a operação que uma ferramenta precisa oferecer não existir como use case, o use case
SHALL ser criado no módulo que possui a regra, e o consumidor que hoje a executa — tipicamente
um ViewModel — SHALL passar a consumi-lo. Uma regra não pode existir em duas cópias, uma para
a tela e outra para o agente.

Uma ferramenta que altera estado derivado de lançamentos SHALL fazê-lo pelo use case que
lança, e MUST NOT gravar o estado diretamente. Marcar uma fatura como paga sem lançar o
pagamento produz saldo incoerente com o razão, sem que nada falhe.

#### Scenario: Pagamento de fatura passa pelo use case que lança
- **WHEN** um agente paga uma fatura
- **THEN** a transação de pagamento é criada e o estado da fatura é alterado pelo use case que faz as duas coisas, e não por escrita direta do campo de status

#### Scenario: Formulário parcelado não é decidido pela ferramenta
- **WHEN** um agente registra um lançamento com mais de uma parcela
- **THEN** o despacho entre parcelamento, recorrência e transação simples é feito pelo use case que possui essa decisão, e a ferramenta apenas entrega o formulário

#### Scenario: Operação sem use case
- **WHEN** uma ferramenta precisa de uma operação que só existe dentro de um ViewModel
- **THEN** a operação é extraída para um use case no módulo dono, e o ViewModel passa a consumi-lo

### Requirement: Uma listagem carrega o agregado, e ele vem do razão

Uma ferramenta que devolve uma lista de lançamentos SHALL acompanhá-la do agregado
correspondente ao filtro aplicado. O agregado SHALL ser derivado do razão e MUST NOT ser a
soma dos itens devolvidos: uma resposta paginada devolve parte dos itens e o total de todos
eles.

A resposta SHALL declarar quantos itens correspondem ao filtro e quantos foram devolvidos, de
modo que quem a consome saiba que há mais.

O agregado e o filtro SHALL concordar: os itens devolvidos são exatamente os que compõem o
agregado exibido ao lado deles.

#### Scenario: Listagem paginada declara o total do filtro
- **WHEN** um agente lista os lançamentos de um mês com mais itens do que a página comporta
- **THEN** o agregado devolvido é o do mês inteiro, e a resposta informa quantos itens correspondem ao filtro e quantos vieram

#### Scenario: Agregado não é a soma da página
- **WHEN** a resposta de uma listagem é inspecionada
- **THEN** o agregado provém de uma leitura do razão, e não da soma dos itens devolvidos

#### Scenario: Transferência não entra no total de despesas
- **WHEN** um agente pede o resumo de despesas de um mês em que houve transferência entre contas próprias e pagamento de fatura
- **THEN** nenhum dos dois compõe o total, porque a leitura do razão já os exclui

### Requirement: Toda figura declara a sua moeda

Uma figura devolvida por uma ferramenta SHALL declarar a moeda em que é expressa. Uma figura
que atravessa contas SHALL ser acompanhada da sua decomposição por moeda e, quando reduzida a
um único número, SHALL declarar que houve consolidação e a data da taxa aplicada.

A redução SHALL ser feita pelo redutor único da camada de consolidação, e a ferramenta
MUST NOT somar valores de moedas diferentes nem escolher taxa por conta própria.

#### Scenario: Figura em moeda única
- **WHEN** uma ferramenta devolve o saldo de uma conta
- **THEN** a figura declara a moeda daquela conta

#### Scenario: Figura que atravessa contas em moedas diferentes
- **WHEN** uma ferramenta devolve o saldo total de contas denominadas em moedas diferentes
- **THEN** a resposta traz a decomposição por moeda, o valor consolidado, a moeda em que ele é expresso e a data da taxa aplicada

#### Scenario: Ausência de taxa
- **WHEN** a consolidação de uma figura depende de uma taxa que não existe no acervo
- **THEN** a resposta diz isso explicitamente, em vez de omitir a moeda ou apresentar um número aproximado como exato

### Requirement: A perspectiva decide o vocabulário da resposta

Uma ferramenta que lista lançamentos sob **uma** conta ou **um** cartão SHALL apresentar a
direção do movimento visto daquela perspectiva. Uma ferramenta que lista sem perspectiva única
SHALL apresentar a natureza derivada pelo razão, e MUST NOT apresentar a direção de uma perna
escolhida arbitrariamente como se fosse propriedade do lançamento.

A natureza SHALL ser a que o razão deriva dos tipos de conta das pernas, e a ferramenta
MUST NOT re-derivá-la a partir do sinal de um valor ou da presença de uma fachada.

#### Scenario: Listagem sem perspectiva usa a natureza
- **WHEN** um agente lista os lançamentos de um mês sem filtrar por conta
- **THEN** uma transferência entre contas próprias é apresentada como transferência, nunca como despesa

#### Scenario: Listagem com perspectiva usa a direção
- **WHEN** um agente lista os lançamentos de uma conta específica
- **THEN** a mesma transferência é apresentada como saída daquela conta

### Requirement: Uma recusa nomeia a saída

Quando o domínio recusar uma operação, a ferramenta SHALL devolver o motivo da recusa e, se
houver uma operação que o domínio permite no lugar, SHALL nomeá-la.

Uma recusa por identidade não encontrada SHALL dizer o que não foi encontrado.

#### Scenario: Remoção recusada por existir movimento
- **WHEN** um agente tenta apagar uma categoria que possui lançamentos
- **THEN** a recusa informa o motivo e nomeia a operação de arquivamento como alternativa

#### Scenario: Edição recusada por forma da operação
- **WHEN** um agente tenta editar uma transferência ou um pagamento de fatura
- **THEN** a recusa informa que a operação possui mais de uma perna monetária e por isso não é editável por essa ferramenta

#### Scenario: Identidade inexistente
- **WHEN** um agente informa o identificador de algo que não existe
- **THEN** a recusa diz qual identidade não foi encontrada, sem executar a operação

### Requirement: Uma figura declara o que ela abrange

Uma ferramenta que devolve uma figura agregada SHALL declarar o **perímetro** dela — o que
entra e, quando houver ambiguidade previsível, o que fica de fora. Uma soma de saldos de contas
e um patrimônio líquido são números diferentes e o consumidor não tem como distingui-los pelo
valor; a descrição da ferramenta e a resposta SHALL dizer qual dos dois é.

Sem isso, quem consome gasta chamadas apenas para descobrir o que já recebeu — ou, pior, relata
o número com o significado errado.

#### Scenario: Soma de contas não se confunde com patrimônio
- **WHEN** um agente obtém o total das contas do usuário
- **THEN** a resposta declara que dívidas de cartão não estão descontadas, e nomeia a leitura que as desconta

#### Scenario: Perímetro declarado na descrição e na resposta
- **WHEN** a descrição de uma ferramenta de figura agregada é lida
- **THEN** ela diz o que a figura abrange, sem exigir uma chamada para descobrir

### Requirement: Um período em andamento é dito em andamento

Uma ferramenta que responde sobre um período SHALL declarar quando esse período **ainda não
terminou** na data corrente do app.

Um mês fechado e um mês em curso não são comparáveis como iguais, e a diferença não é
recuperável do payload: quem recebe dois totais sem essa marca conclui que o gasto caiu, quando
o mês apenas não acabou.

#### Scenario: Mês corrente
- **WHEN** um agente pede o resumo do mês em que o app se encontra
- **THEN** a resposta marca o período como em andamento e informa até que data ele está apurado

#### Scenario: Comparação entre um mês fechado e um em curso
- **WHEN** dois períodos são comparados e um deles ainda não terminou
- **THEN** a resposta marca qual é o incompleto, para que a variação não seja lida como tendência

### Requirement: A ordem de uma listagem é total e determinística

Uma ferramenta que devolve uma lista paginada SHALL ordená-la por um critério **total**: quando
o critério principal empata, um desempate estável SHALL ser aplicado, de modo que duas chamadas
iguais devolvam a mesma ordem e uma paginação não repita nem omita item.

Uma listagem de lançamentos SHALL permitir ordenar por **ordem de registro**, distinta da data
do lançamento. Um usuário que pede "o último que eu registrei" não está falando da data da
compra — e a data, que tem resolução de dia, não responde essa pergunta.

#### Scenario: Empate na data
- **WHEN** vários lançamentos compartilham a mesma data
- **THEN** a ordem entre eles é estável e a mesma em chamadas repetidas

#### Scenario: O último registrado
- **WHEN** um agente precisa identificar o lançamento registrado mais recentemente
- **THEN** ele o obtém por uma ordenação oferecida pela ferramenta, sem inferir a partir de identificadores

#### Scenario: Paginação não perde item
- **WHEN** uma listagem é percorrida por páginas sucessivas
- **THEN** nenhum item aparece duas vezes nem deixa de aparecer

### Requirement: Uma ferramenta genérica enumera exatamente o que aceita

A descrição de uma ferramenta discriminada por tipo SHALL nomear **exatamente** os tipos que ela
aceita, e MUST NOT citar em texto livre um tipo que o parâmetro discriminador recusa — a regra
vale para toda ferramenta que opere sobre mais de uma entidade por meio de um discriminador.

A divergência entre a prosa e o domínio do parâmetro é invisível até a chamada falhar, e ensina
o consumidor a desconfiar da descrição — que é o único material de que ele dispõe para escolher.

#### Scenario: Prosa e parâmetro concordam
- **WHEN** a descrição de uma ferramenta discriminada por tipo é comparada aos valores que o parâmetro aceita
- **THEN** os dois conjuntos são idênticos

### Requirement: Ferramentas com propósito distinto dizem em que diferem

Duas ferramentas que devolvam informação sobreposta com recortes diferentes SHALL declarar, cada
uma na sua descrição, qual é o seu recorte — de modo que a escolha entre elas não exija chamar
as duas e comparar as saídas.

#### Scenario: Recortes sobrepostos
- **WHEN** duas ferramentas devolvem faturas com recortes distintos
- **THEN** cada descrição diz qual recorte oferece, e o consumidor escolhe sem experimentar

### Requirement: A superfície é fechada, e o que fica de fora é declarado

O conjunto de ferramentas oferecido SHALL ser exatamente o conjunto declarado, e uma capacidade
do app que a superfície não alcança SHALL constar de uma lista de exclusões, com o motivo. Uma
ferramenta nova MUST NOT surgir sem passar por essa declaração.

A regra existe porque a ausência não se manifesta: uma capacidade esquecida é indistinguível de
uma capacidade recusada, e ninguém percebe a diferença lendo a lista do que existe. É a mesma
razão pela qual o app já compara, por teste, as instruções de agente com os arquivos que elas
nomeiam.

Em particular, o servidor MUST NOT oferecer escrita de taxa de câmbio nem troca da moeda base:
qualquer das duas reescreve, em silêncio e retroativamente, toda figura consolidada do app —
inclusive as de períodos já fechados — sem produzir lançamento que a denuncie.

O servidor MUST NOT oferecer ferramenta que altere a sua própria configuração ou as suas próprias
permissões.

#### Scenario: A lista anunciada é a lista declarada
- **WHEN** o conjunto de ferramentas anunciado é comparado à lista declarada
- **THEN** os dois conjuntos são idênticos, sem ferramenta a mais nem a menos

#### Scenario: Taxa e moeda base ficam fora
- **WHEN** a superfície é inspecionada
- **THEN** nenhuma ferramenta escreve taxa de câmbio ou altera a moeda base

#### Scenario: O servidor não se reconfigura
- **WHEN** a superfície é inspecionada
- **THEN** nenhuma ferramenta liga, desliga, reconfigura o servidor ou amplia as permissões dele

### Requirement: A resposta é plana, e não carrega domínio

Um payload devolvido por uma ferramenta SHALL conter apenas valores já resolvidos: textos,
figuras monetárias com moeda, rótulos e identificadores. Ele MUST NOT conter modelo de domínio
como campo — nem agregado, nem entidade, nem coleção deles — carregando no máximo o
identificador do domínio que representa.

Um payload SHALL apresentar nomes onde a leitura humana os espera, e não apenas
identificadores: quem consome não deve precisar de chamadas adicionais para saber a que conta
ou categoria um lançamento pertence.

#### Scenario: Lançamento devolvido por uma ferramenta
- **WHEN** um agente obtém um lançamento
- **THEN** a resposta traz o rótulo, a figura com moeda, a data, e os nomes da conta e da categoria, sem referenciar o agregado de domínio

#### Scenario: Nenhum tipo de domínio atravessa a fronteira
- **WHEN** os tipos que compõem os payloads das ferramentas são inspecionados
- **THEN** nenhum campo é de tipo de domínio, no máximo o identificador
