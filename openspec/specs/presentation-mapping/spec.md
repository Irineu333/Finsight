# presentation-mapping Specification

## Purpose

A fronteira entre o domínio e a tela. Um modelo de UI é um DTO plano — valores já resolvidos para exibição, no máximo um id — e nunca carrega grafo de domínio nem calcula nada. Toda tradução (derivação de rótulo, resolução de perspectiva, inversão de sinal por `AccountType`, escolha do valor a exibir) acontece exclusivamente em mappers. É a instância, na camada de apresentação, da regra geral declarada em `balanced-ledger`: a feature adapta ao usuário, mas não decide qual é a regra.
## Requirements
### Requirement: Modelos de UI sem grafo de domínio

Um modelo de apresentação SHALL conter apenas valores já resolvidos para exibição (textos,
valores monetários com sinal de exibição, rótulos, ids). Um modelo de apresentação MUST NOT
conter modelo de domínio como campo — nem agregado, nem entidade, nem coleção deles —
carregando no máximo o identificador do domínio que representa. Um modelo de apresentação
MUST NOT executar cálculo de domínio (soma, filtro, derivação de saldo) em construtor, `init`
ou propriedade.

A regra governa **toda superfície de apresentação**, e não apenas a tela. A UI é a primeira
instância; a superfície de ferramentas que responde a um agente é a segunda, e o consumidor
ser um programa em vez de uma pessoa MUST NOT afrouxar nenhuma das restrições acima — afrouxa
menos, porque um agente que recebe um agregado de domínio o interpretará por conta própria,
enquanto uma tela apenas deixaria de compilar.

Cada superfície SHALL ter os seus próprios modelos de apresentação, porque o que uma resolve
para exibição a outra não usa — um ícone e uma cor de tema não significam nada para um agente,
e um nome de conta por extenso é obrigatório para ele e redundante numa tela que já o mostra
no cabeçalho. Modelos distintos MUST NOT implicar decisões distintas: as duas superfícies
consomem os mesmos donos de derivação.

#### Scenario: Modelo de UI de transação
- **WHEN** a UI exibe uma transação em uma lista
- **THEN** o modelo de UI expõe id, rótulo, valor de exibição, data e categoria como valores planos, sem referenciar o agregado de domínio

#### Scenario: Modelo de UI de conta
- **WHEN** a UI exibe uma conta com seus totais do período
- **THEN** o modelo de UI expõe os totais como valores já calculados, sem receber lançamentos nem computá-los

#### Scenario: Ação da UI sobre um item
- **WHEN** o usuário aciona uma ação sobre um item exibido
- **THEN** a UI a identifica pelo id, e o domínio correspondente é resolvido fora do modelo de UI

#### Scenario: Modelo de apresentação de uma superfície não visual
- **WHEN** uma superfície que responde a um agente devolve uma transação
- **THEN** o modelo devolvido expõe valores planos e no máximo o identificador, sem carregar o agregado de domínio

#### Scenario: Superfície não visual não calcula
- **WHEN** uma superfície que responde a um agente devolve uma lista e o seu total
- **THEN** o total provém de uma leitura do domínio, e não de uma soma feita no modelo de apresentação

#### Scenario: Duas superfícies, uma derivação
- **WHEN** a mesma transação é apresentada na tela e devolvida a um agente
- **THEN** o rótulo, a perna lida e o sinal vêm dos mesmos donos de derivação, ainda que os dois modelos de apresentação sejam distintos

### Requirement: Mappers como única fronteira domínio-apresentação
A tradução de domínio para apresentação SHALL ocorrer exclusivamente em mappers. Derivação de rótulo, resolução de perspectiva, inversão de sinal por `AccountType` e escolha do valor a exibir MUST NOT ocorrer em modelo de UI nem em componente de UI. Um modelo de UI MUST NOT declarar campo de tipo de domínio. Esta regra SHALL ser verificável por inspeção dos próprios modelos de UI, e MUST NOT ser expressa como ausência de dependência de módulo: `core/ui` depende de `core/model` **por desenho** — os seus componentes existem para renderizar modelos de domínio — e `core/ui/model` é um pacote, não um módulo Gradle.

A inversão de sinal por `AccountType` é regra de **saldo**: ela existe para que o saldo natural de uma conta de natureza credora leia positivo. Ela MUST NOT ser aplicada ao valor de uma **perna de transação**. Quando o valor de uma perna for exibido com sinal, esse sinal SHALL ser o natural do razão — em convenção débito-positivo, o mesmo em que a perna foi gravada. *Se* uma perna exibe sinal é decisão da política de exibição (`money-display`); *qual* sinal ela exibe, quando exibe, é esta regra. As duas leituras são distintas e a spec as nomeia separadamente porque a segunda é derivável da primeira por analogia errada: invertida, uma correção que aumenta uma dívida leria positiva, ao lado de uma compra que aumenta a mesma dívida e lê negativa.

#### Scenario: Inversão de sinal para exibição
- **WHEN** um valor do razão em convenção débito-positivo é exibido
- **THEN** o mapper aplica a inversão por `AccountType`, e a UI recebe o valor já no sinal que o usuário espera

#### Scenario: Sinal de uma perna de transação
- **WHEN** o valor de uma perna de transação é exibido com sinal — inclusive a perna de passivo de um cartão
- **THEN** o sinal é o natural do razão, sem a inversão por `AccountType`, que é regra de saldo

#### Scenario: Derivação de rótulo
- **WHEN** o rótulo de uma transação é exibido
- **THEN** o mapper o deriva dos tipos de conta das entries, e a UI recebe o rótulo pronto

#### Scenario: Modelo de UI não carrega domínio
- **WHEN** um modelo de UI é inspecionado
- **THEN** nenhum de seus campos é de tipo de domínio — no máximo o identificador

#### Scenario: Escolha do valor a exibir não ocorre no componente
- **WHEN** um componente de UI renderiza o valor de uma transação
- **THEN** ele recebe do mapper o valor já resolvido com a sua política de sinal, sem ramificar por rótulo, natureza ou direção para decidir o que exibir

#### Scenario: Lista chega mapeada ao componente
- **WHEN** uma tela exibe uma lista de transações
- **THEN** o estado carrega os modelos de exibição já mapeados, e a composable não chama o mapper nem recebe o que ele precisaria para chamá-lo

#### Scenario: Agregado de domínio ao lado do modelo de exibição, não dentro dele
- **WHEN** uma tela precisa do agregado de domínio para abrir uma modal que o exige
- **THEN** ele é declarado como campo próprio do estado, nomeado como domínio, e o modelo que a lista renderiza segue sem ele

### Requirement: A escolha da perna neutra tem um dono

Quando um mapeamento for solicitado sem perspectiva, a perna pela qual a transação é lida SHALL ser a que o domínio já define como perna primária, e o mapper MUST NOT reimplementar esse critério. Duas definições da mesma escolha podem divergir sem que nada falhe — a lista passa a olhar uma perna e o detalhe outra, para a mesma transação.

O critério que define a perna primária MUST NOT comparar valores de moedas diferentes. Escolher "a de menor valor" entre pernas monetárias de moedas distintas compara grandezas incomparáveis e elege a ponta pelo número maior em vez de pelo sentido do movimento — uma transferência de R$ 550 para US$ 100 elegeria uma ponta ou a outra conforme o câmbio, não conforme o dinheiro ter saído ou entrado.

A perna primária SHALL ser a perna monetária de **valor negativo** — aquela que o dinheiro deixou. Sendo toda transação balanceada por moeda e tendo no máximo duas pernas monetárias, o critério é total e independe de moeda. Uma transação sem perna monetária negativa — uma compra em cartão, cuja única perna monetária é o passivo — SHALL continuar sendo lida pela perna que já é lida hoje, sem regra nova.

Este requisito governa o mapeamento **de uma perna**. Uma superfície que exibe todas as pernas monetárias não solicita esse mapeamento e não tem perna neutra a escolher; ela permanece consumidora da definição de perna primária apenas para **ordenar** o que exibe, e MUST NOT reimplementar o critério para isso.

#### Scenario: Lista sem perspectiva e detalhe concordam sobre a perna
- **WHEN** a mesma transação é exibida em duas listas sem perspectiva
- **THEN** ambas leem a mesma perna, por consumirem a mesma definição de perna primária

#### Scenario: Perna primária de uma transferência entre moedas
- **WHEN** uma transferência de R$ 550,00 para US$ 100,00 é exibida numa lista sem perspectiva
- **THEN** a perna lida é a da conta em reais, de onde o dinheiro saiu, e a escolha não depende do câmbio aplicado

#### Scenario: Critério não compara moedas
- **WHEN** a definição de perna primária é inspecionada
- **THEN** ela não compara valores de pernas em moedas diferentes

#### Scenario: A superfície de operação consome a definição para ordenar
- **WHEN** o detalhe de uma transferência entre moedas ordena os seus cards
- **THEN** o primeiro é o da perna primária, pela mesma definição que as listas consomem, e nenhuma perna é eleita como "a" perna lida

### Requirement: Uma tela declara a perspectiva que tem

Uma superfície que apresenta transações sob **uma** conta ou **um** cartão SHALL declarar essa
perspectiva ao mapear, e MUST NOT deixar que a perna lida seja escolhida pelo critério de
ausência de perspectiva. Uma superfície que não tem perspectiva única — uma lista de tudo, ou
um recorte sobre várias contas — MUST NOT inventar uma: a ausência é a resposta correta, e não
uma omissão.

Dentro de uma mesma superfície, a perna lida SHALL ter uma única definição. Filtro, item e
detalhe MUST NOT derivá-la cada um por conta própria: duas definições podem discordar, e o
filtro passa a devolver uma transação que o item apresenta na direção oposta.

Uma superfície que exibe **todas** as pernas monetárias de uma operação não lê perna alguma, e
por isso MUST NOT receber perspectiva. A concordância exigida acima é satisfeita por
continência e não por coincidência: qualquer que seja a perna que a lista leu, ela está entre
as que o detalhe exibe. Receber uma perspectiva que não usa é pior do que não recebê-la — é um
argumento que sugere um efeito que não existe.

#### Scenario: Extrato de fatura lê pela perna do cartão
- **WHEN** o pagamento de uma fatura é exibido no extrato dessa fatura
- **THEN** ele é lido pela perna do cartão, como dinheiro que entra, e não pela perna da conta de onde saiu

#### Scenario: Filtro e item concordam sobre a perna
- **WHEN** uma lista com perspectiva é filtrada por direção
- **THEN** a direção que o filtro aplica é a mesma que o item exibe, por virem da mesma definição

#### Scenario: Recorte sobre várias contas não tem perspectiva
- **WHEN** um relatório apresenta transações de várias contas
- **THEN** o mapeamento é feito sem perspectiva, porque não há uma única conta de cujo ponto de vista ler

#### Scenario: O detalhe aberto do extrato de fatura não contradiz a lista
- **WHEN** um pagamento é exibido no extrato da fatura como dinheiro que entra e o seu detalhe é aberto
- **THEN** o detalhe exibe as duas pernas, entre elas a do cartão que a lista leu, e não apresenta a operação como despesa

#### Scenario: A superfície de operação não recebe perspectiva
- **WHEN** a assinatura do ponto de entrada do detalhe de uma transação é inspecionada
- **THEN** ela não declara parâmetro de perspectiva

### Requirement: Perspectiva como argumento de mapeamento
Quando uma transação puder ser apresentada sob mais de um ponto de vista (a conta ou a fatura em que aparece), a perspectiva SHALL ser um argumento do mapeamento, e MUST NOT ser um campo do modelo de UI resolvido preguiçosamente na leitura. A resolução da perspectiva SHALL ocorrer no momento do mapeamento, onde a ausência de correspondência é tratável.

#### Scenario: Mesma transação em duas telas
- **WHEN** uma transferência entre contas é exibida na tela da conta de origem e na da conta de destino
- **THEN** o mapper é invocado com a perspectiva de cada conta e produz um modelo de UI distinto para cada tela

#### Scenario: Perspectiva sem correspondência
- **WHEN** um mapeamento é solicitado com uma perspectiva que não corresponde a nenhuma perna da transação
- **THEN** o mapper não produz modelo de UI para aquela transação e o chamador a omite da lista, sem lançar — e a ausência MUST NOT se manifestar como falha na leitura de uma propriedade

#### Scenario: Perspectiva de cartão é sempre construível
- **WHEN** uma perspectiva de cartão é construída para qualquer cartão, inclusive um recém-criado sem nenhum lançamento
- **THEN** ela é construível, porque todo cartão possui conta no plano de contas desde a sua criação (`account-lifecycle`), e a resolução não depende de tratamento para vínculo ausente

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

### Requirement: Qual das duas pontas de uma operação cruzada denomina a figura

Uma operação que atravessa moedas tem **duas** figuras exatas, ambas do razão: o que saiu de uma ponta e o que entrou na outra. Exibida sem perspectiva **e em uma única figura**, ela SHALL ser denominada pela ponta que já estiver na moeda base, quando houver uma. Onde nenhuma das pontas estiver na base, a leitura SHALL permanecer a que já era — a perna de saída — e MUST NOT haver conversão.

A moeda base aqui **não denomina figura alguma**: ela apenas escolhe entre dois valores que o razão já respondeu, nenhum deles convertido e nenhum deles aproximado. Isso é distinto — e MUST NOT ser confundido com — usar a base como recurso para uma figura cuja moeda é conhecível, que `money-display` proíbe. Converter na ausência de uma ponta na base compraria uma moeda que ninguém pediu ao preço de uma taxa que pode não existir.

Uma superfície que declara perspectiva MUST NOT aplicar esta escolha: a figura dela é a linha daquela conta, na moeda daquela conta, qualquer que seja a base.

Uma superfície que exibe as **duas** pontas tampouco a aplica, e MUST NOT ler a moeda base: o desempate existe para quem precisa dizer um número só, e quem diz os dois não tem o que desempatar. O alcance da preferência de moeda base SHALL encolher junto — uma superfície que deixa de desempatar SHALL deixar de nomeá-la.

A escolha SHALL ter um dono único, consumido por toda superfície sem perspectiva que exiba uma única figura. Um item de lista e o detalhe que ele abre MUST NOT exibir dinheiro diferente para a mesma operação; um detalhe que exiba **ambas** as figuras satisfaz isso por continência, porque a figura do item está entre as que ele exibe.

A perna que denomina a figura MUST NOT ser confundida com a perna pela qual a transação é **lida**: direção e sinal permanecem na perna primária. Trocar as duas juntas faria um pagamento de fatura se anunciar como receita no instante em que a figura passasse para a perna do passivo.

#### Scenario: Pagamento cruzado é denominado pela ponta na base
- **WHEN** uma fatura de cartão em reais é paga de uma conta em dólar, a moeda base é o real, e a operação é exibida como item de uma lista sem perspectiva
- **THEN** a figura exibida é a da ponta em reais, sem conversão e sem marca de aproximação, e a direção continua sendo despesa

#### Scenario: Sem ponta na base, a leitura não muda
- **WHEN** a mesma operação é exibida como item com moeda base euro
- **THEN** a figura permanece a da conta de origem, em dólar, e nenhuma taxa é aplicada

#### Scenario: A perspectiva prevalece sobre a base
- **WHEN** a mesma operação é listada no extrato da conta em dólar
- **THEN** a figura é a daquela conta, em dólar

#### Scenario: Lista e detalhe não discordam
- **WHEN** a mesma operação cruzada é exibida num item de lista e aberta no detalhe
- **THEN** a figura que a lista exibe está entre as que o detalhe exibe, na mesma moeda e sem conversão

#### Scenario: O detalhe não nomeia a moeda base
- **WHEN** as dependências do detalhe de uma transação são inspecionadas
- **THEN** ele não lê a preferência de moeda base, e o inventário do alcance dessa preferência não o inclui

### Requirement: O detalhe de uma operação declara a taxa que ela praticou

O detalhe de uma operação cujas duas pernas monetárias estejam denominadas em moedas diferentes SHALL exibir a taxa que ela praticou, derivada das próprias pernas. A taxa exibida MUST NOT vir do acervo: o acervo responde pela conversão de figuras, enquanto esta é a razão entre o que saiu e o que entrou **nesta** operação, e as duas podem legitimamente divergir na mesma data.

A direção SHALL ser a mesma que o formulário de escrita usa ao revelar a segunda ponta — uma unidade da moeda de origem expressa na moeda de destino —, porque a taxa lida depois é a mesma que foi mostrada enquanto se digitava, e duas gramáticas para o mesmo quociente fazem o usuário suspeitar do número. Ela SHALL ser exibida com casas decimais suficientes para não arredondar a zero, pela mesma razão pela qual o acervo guarda o quociente pleno.

Uma operação em moeda única MUST NOT exibir a linha, e uma com uma só perna monetária tampouco — não há segunda ponta por que dividir, e uma linha ausente é a resposta certa para uma pergunta que não se colocou.

#### Scenario: Transferência entre moedas informa a taxa praticada
- **WHEN** o detalhe de uma transferência de R$ 550,00 para US$ 100,00 é aberto
- **THEN** ele exibe a taxa que a operação praticou, derivada das duas pernas e sem consultar o acervo

#### Scenario: Pagamento de fatura em outra moeda informa a taxa praticada
- **WHEN** o detalhe de um pagamento de fatura cujas pernas monetárias estão em moedas diferentes é aberto
- **THEN** ele exibe a taxa da mesma forma, pela mesma leitura

#### Scenario: Operação em moeda única não exibe taxa
- **WHEN** o detalhe de uma operação cujas pernas estão todas na mesma moeda é aberto
- **THEN** nenhuma taxa é exibida

### Requirement: Um mapper por superfície, uma decisão por regra

Quando existir mais de uma superfície de apresentação, cada uma SHALL ter o seu próprio
mapper, e todos SHALL consumir as mesmas definições de domínio para as decisões que
apresentam — derivação de rótulo, escolha da perna lida, resolução de perspectiva, inversão de
sinal por `AccountType` e escolha da ponta que denomina uma figura cruzada.

Um mapper MUST NOT re-derivar por conta própria uma decisão que já tem dono, ainda que a sua
superfície seja nova. Duas derivações da mesma escolha podem divergir sem que nada falhe, e a
divergência entre uma tela e um agente é a mais difícil de perceber: ninguém as olha lado a
lado.

#### Scenario: Rótulo concorda entre superfícies
- **WHEN** a mesma transação é rotulada por duas superfícies de apresentação
- **THEN** o rótulo é o mesmo, por virem ambas da derivação do razão

#### Scenario: Mapper novo não reimplementa a perna primária
- **WHEN** o mapper de uma superfície nova precisa ler uma transação sem perspectiva
- **THEN** ele consome a definição de perna primária existente, sem reimplementar o critério

#### Scenario: Figura cruzada concorda entre superfícies
- **WHEN** uma operação que atravessa moedas é apresentada em duas superfícies como uma figura única
- **THEN** ambas exibem a mesma ponta, pela mesma definição de qual delas denomina a figura
