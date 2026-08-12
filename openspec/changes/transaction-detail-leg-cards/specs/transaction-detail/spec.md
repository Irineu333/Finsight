## ADDED Requirements

### Requirement: O detalhe de uma operação exibe um card por perna monetária

O detalhe de uma transação SHALL exibir um card para cada perna monetária (`ASSET` ou
`LIABILITY`) da transação, e SHALL exibi-lo mesmo quando houver apenas uma. As pernas não
monetárias — a categoria, a reconciliação, a conversão — MUST NOT ganhar card: elas não
carregam dinheiro que o usuário reconheça como seu.

O critério SHALL ser a **quantidade de pernas monetárias**, e MUST NOT ser a quantidade de
moedas. Uma transferência entre duas contas na mesma moeda exibe dois cards, com o mesmo
valor nos dois — a repetição é a afirmação de que nada se perdeu no caminho, que é
exatamente o que a operação entre moedas *não* afirma.

Cada card SHALL responder, de uma vez, três perguntas: **qual** conta ou cartão, **quanto**,
e **o que aconteceu** com aquele dinheiro. O valor de um card SHALL ser denominado pela
moeda da conta daquela perna, sem conversão e sem recair na moeda base.

Um card cuja conta ou cartão ainda esteja ativo SHALL oferecer um atalho para **onde aquele
dinheiro está**: o card de uma conta abre a conta; o de um cartão abre o **extrato da fatura**
daquele cartão, e não o cadastro do cartão. A perna do passivo é a fatura — é a dimensão que
ela carrega, e o card já a nomeia dentro de si —, então quem toca nela quer ver o que mais
entrou naquela fatura, não os dados do cartão.

O extrato SHALL abrir **na fatura que o card nomeia**, e MUST NOT abrir na fatura corrente do
cartão. O detalhe sabe exatamente em qual fatura aquela perna caiu, e abrir noutra é responder
errado — não apenas responder pouco: quem vinha de uma compra de março passa a olhar uma
fatura que não a contém e conclui que ela sumiu.

Um card de fachada arquivada MUST NOT oferecer atalho, porque a tela de destino não a lista
mais.

#### Scenario: Transferência entre moedas exibe as duas figuras
- **WHEN** o detalhe de uma transferência de R$ 550,00 para US$ 100,00 é aberto
- **THEN** ele exibe dois cards, um com R$ 550,00 na conta de origem e outro com US$ 100,00 na conta de destino, e nenhuma das duas figuras é convertida

#### Scenario: Pagamento de fatura em outra moeda exibe as duas figuras
- **WHEN** o detalhe de um pagamento feito de uma conta em reais sobre uma fatura em dólar é aberto
- **THEN** ele exibe o que saiu da conta em reais e o que foi abatido da fatura em dólar, cada um na sua moeda

#### Scenario: Transferência na mesma moeda exibe dois cards
- **WHEN** o detalhe de uma transferência entre duas contas em reais é aberto
- **THEN** ele exibe dois cards com o mesmo valor, porque o critério é a quantidade de pernas monetárias

#### Scenario: Gasto simples exibe um card
- **WHEN** o detalhe de um gasto em conta é aberto
- **THEN** ele exibe um único card, o da conta de onde o dinheiro saiu

#### Scenario: Compra em cartão exibe um card
- **WHEN** o detalhe de uma compra em cartão é aberto
- **THEN** ele exibe um único card, o do cartão, porque a única perna monetária é o passivo

#### Scenario: Perna de categoria não vira card
- **WHEN** o detalhe de um gasto categorizado é aberto
- **THEN** a perna nominal da categoria não produz card algum

#### Scenario: O card do cartão abre a fatura
- **WHEN** o card do cartão de uma compra é tocado
- **THEN** o extrato da fatura daquele cartão é aberto, e não a tela de cadastro do cartão

#### Scenario: O extrato abre na fatura da operação
- **WHEN** o card do cartão de uma compra lançada numa fatura antiga é tocado
- **THEN** o extrato abre naquela fatura, com a compra entre as suas transações, e não na fatura corrente do cartão

#### Scenario: Conta arquivada não oferece atalho
- **WHEN** o detalhe exibe o card de uma conta arquivada
- **THEN** o card mostra o nome e o valor, e não oferece navegação para a tela de contas

### Requirement: O verbo de um card deriva do razão, não da fachada

O que aconteceu com o dinheiro de uma perna SHALL ser derivado do par `(tipo de conta,
sinal da perna)`, com um único override: uma transação que tenha perna `EQUITY` é um ajuste,
e todos os seus cards SHALL usar o verbo de ajuste.

| condição | verbo |
| --- | --- |
| a transação tem perna `EQUITY` | *ajustou* |
| `ASSET`, perna negativa | *saiu de* |
| `ASSET`, perna positiva | *entrou em* |
| `LIABILITY`, perna negativa | *lançou em* |
| `LIABILITY`, perna positiva | *abateu de* |

A derivação MUST NOT consultar `TransactionLabel`. O verbo é uma afirmação sobre o razão, e
derivá-lo dos mesmos fatos de que a natureza é derivada é o que impede os dois de divergirem.
A tabela SHALL ser total sobre as pernas monetárias que o razão é capaz de produzir, e SHALL
ter um dono único, consumido tanto pelo detalhe de uma transação quanto pelo de um ajuste.

#### Scenario: Pagamento de fatura nomeia as duas pernas corretamente
- **WHEN** o detalhe de um pagamento de fatura é aberto
- **THEN** o card da conta diz que o dinheiro saiu dela e o card do cartão diz que a fatura foi abatida, e nenhum dos dois chama o cartão de origem do dinheiro

#### Scenario: Compra em cartão lança na fatura
- **WHEN** o detalhe de uma compra em cartão é aberto
- **THEN** o card do cartão diz que a compra foi lançada nele, porque a perna do passivo é negativa

#### Scenario: Ajuste usa o verbo de ajuste em qualquer conta
- **WHEN** o detalhe de um ajuste de saldo de conta e o de um ajuste de fatura são abertos
- **THEN** ambos usam o verbo de ajuste, porque a transação tem perna `EQUITY`

#### Scenario: O verbo não consulta a natureza
- **WHEN** a derivação do verbo é inspecionada
- **THEN** ela lê apenas o tipo de conta da perna, o sinal da perna e a presença de perna `EQUITY`

### Requirement: A fatura e a parcela vivem dentro do card da perna a que pertencem

A fatura de uma transação, com o seu status, e a parcela que ela integra SHALL ser exibidas
**dentro** do card da perna do passivo, e MUST NOT ser exibidas como linhas irmãs das linhas
de contexto da operação. Ambas são atributos daquela perna: a fatura é a dimensão que a perna
do passivo carrega, e o total de uma parcela é denominado pela conta daquele mesmo cartão.

Uma transação sem perna de passivo MUST NOT exibir nem uma nem outra.

#### Scenario: Compra parcelada mostra fatura e parcela no card do cartão
- **WHEN** o detalhe de uma compra parcelada em cartão é aberto
- **THEN** a fatura, o seu status e a parcela aparecem dentro do card do cartão, e não entre as linhas de contexto

#### Scenario: Pagamento mostra a fatura abatida no card do cartão
- **WHEN** o detalhe de um pagamento de fatura é aberto
- **THEN** a fatura abatida aparece dentro do card do cartão, junto do valor abatido

#### Scenario: Gasto em conta não exibe fatura
- **WHEN** o detalhe de um gasto em conta é aberto
- **THEN** nenhuma fatura é exibida, porque não há perna de passivo

### Requirement: O conector entre dois cards é a seta, e a taxa quando há uma

Havendo dois cards, o detalhe SHALL exibir **entre** eles um conector com uma seta, e SHALL
centralizá-lo. A seta afirma que aqueles são os dois extremos de um mesmo movimento — o que
vale para toda transferência e todo pagamento, em moeda única ou não —, e é dela que a
operação recebe a forma de uma travessia em vez de uma lista de dois cards.

Quando a operação atravessa moedas, a taxa que ela praticou SHALL ser exibida **no conector**,
e MUST NOT ser exibida como uma linha de contexto da operação. A taxa é uma relação entre as
duas pernas, e exibi-la onde ela é uma relação é o que a torna legível. Ela SHALL ser a
relação entre as **duas moedas da operação**, e MUST NOT ser expressa contra a moeda base:
quem lê quer saber o que uma ponta comprou da outra, não o que qualquer uma delas vale numa
terceira que não participou.

A ordem dos cards SHALL ser a perna primária primeiro — a perna monetária de valor negativo,
aquela que o dinheiro deixou —, que é a mesma direção em que a taxa é derivada. O sentido do
conector e o sentido do quociente SHALL concordar por construção, e MUST NOT ser afirmados
separadamente.

Uma operação em moeda única MUST NOT exibir taxa alguma — não há segunda moeda por que
dividir —, e o seu conector é a seta sozinha.

#### Scenario: A seta e a taxa concordam
- **WHEN** o detalhe de uma transferência de R$ 550,00 para US$ 100,00 é aberto
- **THEN** o primeiro card é o da conta em reais, a taxa exibida entre eles é a de uma unidade de real expressa em dólar, e o segundo card é o da conta em dólar

#### Scenario: A taxa não é expressa contra a moeda base
- **WHEN** o detalhe da mesma transferência é aberto por alguém cuja moeda base é o euro
- **THEN** a taxa continua sendo a de real em dólar, porque é a relação entre as duas pontas da operação

#### Scenario: Operação em moeda única exibe a seta e nenhuma taxa
- **WHEN** o detalhe de uma transferência entre duas contas em reais é aberto
- **THEN** a seta aparece entre os dois cards e nenhuma taxa aparece com ela

#### Scenario: Operação de uma perna não exibe conector
- **WHEN** o detalhe de um gasto em conta é aberto
- **THEN** nenhum conector é exibido, porque não há segundo card a que ligar

### Requirement: O cabeçalho do detalhe exibe a natureza da operação

O cabeçalho do detalhe SHALL exibir a **natureza** da transação — gasto, receita,
transferência, pagamento de fatura ou ajuste —, e MUST NOT exibir a direção de uma perna. A
cor e o ícone do cabeçalho SHALL ser função da natureza, total sobre os seus cinco valores.

A segunda linha do cabeçalho SHALL exibir o título que a transação tem: o seu próprio, ou o
nome da sua categoria quando ela não tem título.

Uma transferência e um pagamento de fatura ordinariamente não têm nem um nem outro, e SHALL
ser nomeados pela **forma** que têm — "entre contas", "pagamento de fatura". Isso não é um
literal de reserva: é um fato da operação, tão derivado do razão quanto a natureza acima dele,
e localizado como qualquer outro texto. As duas linhas SHALL ser lidas como uma frase só —
"transferência / entre contas" —, e por isso o nome SHALL dizer o que a natureza não disse:
uma segunda linha que repete a primeira gasta a linha sem informar.

Uma operação cuja forma **não** tem nome próprio além da natureza — um gasto, uma receita ou
um ajuste sem título e sem categoria — MUST NOT recair num literal de reserva: a linha SHALL
ser **omitida**. Nomear uma ausência é a única coisa que o cabeçalho não faz.

#### Scenario: Transferência se anuncia como transferência
- **WHEN** o detalhe de uma transferência é aberto a partir de qualquer tela
- **THEN** o cabeçalho diz "transferência", e não "despesa" nem "receita"

#### Scenario: Pagamento se anuncia como pagamento
- **WHEN** o detalhe de um pagamento de fatura é aberto a partir do extrato daquela fatura
- **THEN** o cabeçalho diz "pagamento", concordando com a lista de onde foi aberto, e a sua segunda linha diz "pagamento de fatura"

#### Scenario: Transferência sem título é nomeada pela sua forma
- **WHEN** o detalhe de uma transferência sem título e sem categoria é aberto
- **THEN** a segunda linha diz "entre contas", completando a natureza da primeira em vez de repeti-la, e não é um literal de reserva

#### Scenario: Gasto sem título nem categoria tem cabeçalho de uma linha
- **WHEN** o detalhe de um gasto sem título e sem categoria é aberto
- **THEN** o cabeçalho exibe apenas a natureza, sem segunda linha e sem literal de reserva

#### Scenario: Gasto com categoria e sem título usa o nome da categoria
- **WHEN** o detalhe de um gasto sem título próprio, categorizado como "Mercado", é aberto
- **THEN** a segunda linha exibe "Mercado"

### Requirement: O detalhe de uma operação não declara perspectiva

O detalhe de uma operação MUST NOT receber nem consumir perspectiva. Exibindo todas as
pernas monetárias, ele não escolhe nenhuma, e a pergunta que a perspectiva responde — por
qual perna ler — não se coloca.

Disso decorre que o detalhe MUST NOT ler a moeda base: o desempate entre as duas pontas de
uma operação cruzada existe para superfícies que precisam exibir **uma** figura, e esta
exibe as duas, ambas exatas e ambas do razão.

#### Scenario: O detalhe lê igual de qualquer tela
- **WHEN** a mesma transferência entre moedas é aberta a partir da lista geral, do extrato da conta de origem e do extrato da conta de destino
- **THEN** as três leituras são idênticas — mesmos cards, mesmos valores, mesmo cabeçalho

#### Scenario: A moeda base não alcança o detalhe
- **WHEN** as dependências do detalhe são inspecionadas
- **THEN** ele não lê a preferência de moeda base, porque não tem desempate a fazer

### Requirement: O detalhe de um ajuste usa a mesma composição

O detalhe de um ajuste SHALL usar o mesmo card de perna, o mesmo cabeçalho e as mesmas
linhas de contexto que o detalhe de uma transação, diferindo apenas no que o razão o faz
diferir: o verbo de ajuste e o sinal explícito do valor.

Uma composição própria para o ajuste MUST NOT ser mantida em paralelo. Duas composições da
mesma coisa são o modo pelo qual as duas telas passam a divergir sem que nada falhe.

#### Scenario: Ajuste de saldo de conta usa o card de perna
- **WHEN** o detalhe de um ajuste de saldo de conta é aberto
- **THEN** ele exibe um card com o verbo de ajuste, o nome da conta e o valor com sinal explícito

#### Scenario: Ajuste de fatura mostra a fatura dentro do card
- **WHEN** o detalhe de um ajuste de fatura é aberto
- **THEN** a fatura aparece dentro do card do cartão, como em qualquer outra perna de passivo

### Requirement: Restam como contexto apenas os fatos que não pertencem a nenhuma perna

Abaixo dos cards, o detalhe SHALL exibir como linha de contexto apenas o que é fato da
transação inteira e não de uma das suas pernas — a data e a recorrência. O que pertence a
uma perna MUST NOT ser repetido como linha de contexto.

O detalhe MUST NOT exibir linha que declare se a origem de um gasto foi conta ou cartão: o
card já nomeia a conta ou o cartão de onde o dinheiro saiu, e uma linha que responda isso a
partir da natureza e da presença de perna de passivo afirma o falso para o pagamento de
fatura, cujo dinheiro sai da conta.

#### Scenario: Pagamento não afirma que o dinheiro saiu do cartão
- **WHEN** o detalhe de um pagamento de fatura é aberto
- **THEN** nenhuma linha diz que a origem foi o cartão de crédito

#### Scenario: A conta não aparece duas vezes
- **WHEN** o detalhe de um gasto em conta é aberto
- **THEN** a conta aparece apenas no seu card, e não também como linha de contexto

#### Scenario: Data e recorrência permanecem como contexto
- **WHEN** o detalhe de um gasto recorrente é aberto
- **THEN** a data e a recorrência aparecem abaixo dos cards, e a recorrência continua abrindo o seu próprio detalhe
