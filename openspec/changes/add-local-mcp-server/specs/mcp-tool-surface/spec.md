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
