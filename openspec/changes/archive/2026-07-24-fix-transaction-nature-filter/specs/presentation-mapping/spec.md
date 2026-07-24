## ADDED Requirements

### Requirement: Filtrar por natureza usa a derivação do razão

Quando uma tela oferecer ao usuário um eixo de filtro pela **natureza** de uma transação, esse filtro SHALL comparar a natureza derivada pelo razão a partir dos tipos de conta das entries, e MUST NOT re-derivar a classificação a partir do sinal de uma perna, da presença de uma fachada ou de qualquer critério próprio da tela. A natureza tem um dono único no domínio; a tela consome esse dono.

Uma tela SHALL exibir e filtrar pela **mesma** propriedade: MUST NOT ocorrer de um item ser rotulado por um vocabulário e selecionado por outro.

O eixo de filtro SHALL oferecer **todas** as naturezas que o razão pode derivar. Como a derivação é total e mutuamente exclusiva — toda transação recebe exatamente uma natureza —, as opções do eixo SHALL particionar a lista: a união dos resultados das opções SHALL ser igual à lista sem filtro, e a interseção de duas opções distintas SHALL ser vazia. A ausência de filtro SHALL listar tudo, inclusive as naturezas que não são despesa nem receita.

#### Scenario: Transferência não é listada como despesa
- **WHEN** o usuário filtra a lista pela natureza "despesa"
- **THEN** uma transferência entre duas contas não aparece, por sua natureza derivada ser transferência — ainda que a perna pela qual a lista a exibe seja negativa

#### Scenario: Pagamento de fatura não é listado como despesa
- **WHEN** o usuário filtra a lista pela natureza "despesa"
- **THEN** o pagamento de uma fatura não aparece, por sua natureza derivada ser pagamento

#### Scenario: Toda natureza derivável é alcançável
- **WHEN** o eixo de filtro por natureza é aberto
- **THEN** há uma opção para cada natureza que o razão deriva, incluindo transferência e pagamento

#### Scenario: As opções particionam a lista
- **WHEN** os resultados de todas as opções do eixo são reunidos
- **THEN** o conjunto obtido é exatamente a lista sem filtro, sem item repetido e sem item ausente

#### Scenario: Sem filtro, tudo é listado
- **WHEN** nenhuma natureza é selecionada
- **THEN** a lista exibe despesas, receitas, transferências, pagamentos e ajustes

#### Scenario: Rótulo e filtro concordam
- **WHEN** um item é exibido com um rótulo de natureza e o usuário filtra por essa mesma natureza
- **THEN** o item permanece na lista

### Requirement: Natureza e direção são vocabulários distintos, separados pela perspectiva

O sistema SHALL distinguir a **natureza** de uma transação — derivada dos tipos de conta de todas as suas entries, sem perspectiva — da **direção** de uma perna — se o dinheiro saiu ou entrou, vista de uma conta específica. São perguntas diferentes e MUST NOT ser usadas uma no lugar da outra.

Uma apresentação **com** perspectiva declarada (a lista de uma conta ou de uma fatura) SHALL usar a direção, lida da perna daquela perspectiva. Uma apresentação **sem** perspectiva SHALL usar a natureza: a direção de uma perna escolhida arbitrariamente não é uma propriedade da transação e MUST NOT ser apresentada como se fosse.

A escolha de qual comportamento a interface dá a um item (qual detalhe abrir, qual cor aplicar) SHALL ser feita pelo vocabulário correspondente à presença ou ausência de perspectiva naquela tela.

#### Scenario: Lista de uma fatura usa a direção
- **WHEN** a lista de lançamentos de uma fatura é filtrada
- **THEN** o critério é a direção da perna do próprio cartão, e um pagamento é apresentado como entrada naquela perspectiva

#### Scenario: Lista neutra usa a natureza
- **WHEN** a lista geral de transações — que não declara perspectiva — é filtrada
- **THEN** o critério é a natureza derivada, e não a direção da perna de saída

#### Scenario: Direção permanece vocabulário de entrada
- **WHEN** o usuário registra um lançamento ou uma recorrência
- **THEN** a direção continua sendo a escolha que ele faz e o que a recorrência persiste, sem ser usada como natureza em nenhuma leitura sem perspectiva

### Requirement: Um agregado do razão e o seu filtro correspondente concordam

Quando uma tela exibir, lado a lado, um agregado derivado do razão e uma lista filtrável dos itens que o compõem, o filtro correspondente àquele agregado SHALL devolver exatamente as transações que o compõem. Um total exibido no cabeçalho e a lista imediatamente abaixo dele MUST NOT discordar sobre o que pertence àquele total.

#### Scenario: Despesa do mês e o filtro de despesa
- **WHEN** a tela exibe o total de despesas do mês derivado do razão e o usuário filtra a lista por despesa
- **THEN** a lista contém as transações que compõem aquele total, e a soma delas é esse total

#### Scenario: Pagamento do mês e o filtro de pagamento
- **WHEN** a tela exibe o total de pagamentos de fatura do mês derivado do razão e o usuário filtra a lista por pagamento
- **THEN** a lista contém as transações que compõem aquele total

#### Scenario: Natureza sem agregado correspondente
- **WHEN** uma natureza não compõe nenhum agregado do resumo — como a transferência, que não altera o patrimônio
- **THEN** ela permanece filtrável e listável, sem ser somada a nenhum dos totais exibidos
