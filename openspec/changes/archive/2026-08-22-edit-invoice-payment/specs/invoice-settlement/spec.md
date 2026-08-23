## MODIFIED Requirements

### Requirement: O pagamento de fatura é uma operação só, e o estado decide o modo

Pagar uma fatura SHALL ser **uma** operação, oferecida por uma superfície única, qualquer que seja o estado da fatura. O que o estado decide é o **modo** de uma operação que ainda não existe, e o estado SHALL ser a única coisa que o decide — nem a tela que abriu a operação, nem o botão que a acionou, nem uma escolha do usuário.

Uma operação **já escrita** tem o modo que tem, e ele MUST NOT ser redecidido pelo estado da fatura escolhida ao corrigi-la: corrigir um pagamento parcial é reafirmar um pagamento parcial. O que decorre disso na superfície de correção pertence a `invoice-payment-editing`.

Uma fatura que ainda recebe compras — `OPEN` ou `RETROACTIVE` — SHALL aceitar **pagamento parcial**: o valor é declarado pelo usuário, limitado ao devido, e a fatura permanece no status em que estava.

Uma fatura `CLOSED` SHALL aceitar **apenas quitação total**: o valor é o devido, exibido e não editável. Um valor parcial sobre fatura fechada MUST NOT ser exprimível por nenhum caminho da interface, e MUST NOT ser aceito pelo domínio se algum caminho o alcançar. Uma **correção** que apontasse um pagamento parcial para uma fatura fechada é um desses caminhos, e é fechada onde a correção é oferecida — não por uma guarda a mais no razão, que aceita a escrita por ela liquidar um passivo.

O campo de valor SHALL ser um só, com um significado só — quanto desta fatura está sendo pago agora —, alternando entre entrada e afirmação conforme o modo. Ele MUST NOT ser confundido com o valor que sai da conta pagadora, que é campo próprio e existe apenas quando as duas pontas são denominadas de forma diferente.

#### Scenario: Fatura aberta aceita valor parcial
- **WHEN** o usuário paga uma fatura `OPEN` que deve R$ 800 e declara R$ 300
- **THEN** R$ 300 saem da conta escolhida, o devido da fatura passa a R$ 500, e a fatura continua `OPEN`

#### Scenario: Fatura retroativa aceita valor parcial
- **WHEN** o usuário paga uma fatura `RETROACTIVE` que deve R$ 800 e declara R$ 300
- **THEN** R$ 300 saem da conta escolhida, o devido da fatura passa a R$ 500, e a fatura continua `RETROACTIVE`

#### Scenario: Fatura fechada oferece só o total
- **WHEN** o usuário abre o pagamento de uma fatura `CLOSED` que deve R$ 800
- **THEN** o valor exibido é R$ 800 e não aceita digitação, e não há caminho para confirmar um valor menor

#### Scenario: O valor parcial acima do devido é recusado
- **WHEN** o usuário declara, numa fatura que aceita parcial, um valor maior que o devido
- **THEN** a confirmação fica indisponível, e o domínio recusa a operação caso ela o alcance

#### Scenario: Corrigir um parcial não o transforma em quitação
- **WHEN** um pagamento parcial já escrito é corrigido
- **THEN** ele continua sendo um pagamento parcial, e nenhuma fatura é marcada `PAID` pela correção

### Requirement: O devido é lido da fatura selecionada

O valor devido e a moeda em que ele está SHALL ser lidos da fatura selecionada no momento em que ela está selecionada, e MUST NOT ser recebidos prontos de quem abriu a operação nem congelados na abertura. Uma fatura que o usuário troca não pode ser descrita pela figura de outra.

O devido que serve de **teto** ao valor SHALL ser computado sobre as entries da fatura que não pertencem à operação sendo escrita. Uma operação já escrita reduziu o devido que ela mesma vai declarar de novo, e um teto que a contasse recusaria a correção que a aumenta. A regra SHALL ter um dono único, e a mesma leitura SHALL servir aos três casos, sem ramo entre eles: numa criação a operação não existe e nada é desconsiderado; numa correção sobre a mesma fatura o que ela liquidou volta ao teto; numa correção que trocou de fatura a operação nada tem naquela fatura e, de novo, nada é desconsiderado.

A moeda SHALL ser a do cartão que deve, declarada pela conta `LIABILITY` dele. O campo do valor que sai da conta pagadora SHALL existir apenas quando essa moeda difere da moeda da conta escolhida, e essa condição SHALL ser derivada das duas pontas correntes — as duas variáveis, porque as duas são escolhidas na mesma superfície.

Uma fatura sem dívida MUST NOT ser pagável por esta operação: a confirmação fica indisponível e a razão é dita. Quitar uma fatura que nada deve continua sendo consequência do fechamento, e MUST NOT ganhar um segundo dono aqui.

#### Scenario: Trocar a fatura troca a figura
- **WHEN** o usuário troca de uma fatura que deve R$ 800 para outra que deve R$ 120
- **THEN** o devido exibido passa a R$ 120, com o teto e o modo da fatura agora selecionada

#### Scenario: Trocar para um cartão de outra moeda revela a contrapartida
- **WHEN** a conta pagadora é em reais e o usuário seleciona um cartão denominado em dólar
- **THEN** o campo do valor que sai da conta passa a ser exibido, denominado em reais, e o devido continua em dólar

#### Scenario: Fatura sem dívida não é pagável
- **WHEN** o usuário seleciona uma fatura cujo devido é zero
- **THEN** a confirmação fica indisponível e a operação diz que não há o que pagar

#### Scenario: O teto de uma correção não conta a própria operação
- **WHEN** o usuário corrige, numa fatura de R$ 800 cujo devido corrente é R$ 500, o pagamento de R$ 300 que produziu esse devido
- **THEN** o teto oferecido é R$ 800, e uma correção para R$ 700 é aceita

#### Scenario: O teto de uma correção que trocou de fatura é o da fatura nova
- **WHEN** o usuário aponta uma correção para outra fatura, que deve R$ 120
- **THEN** o teto passa a ser R$ 120, sem que nada seja desconsiderado, porque a operação nada liquidou naquela fatura

#### Scenario: O teto de uma criação é o devido corrente
- **WHEN** o usuário registra um pagamento novo sobre uma fatura cujo devido é R$ 500
- **THEN** o teto oferecido é R$ 500, pela mesma leitura que serve à correção
