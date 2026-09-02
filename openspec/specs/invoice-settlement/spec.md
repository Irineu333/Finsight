# invoice-settlement Specification

## Purpose

O pagamento de uma fatura como **operação única que nomeia a fatura que paga**: cartão e fatura
escolhidos na própria superfície, o devido lido da fatura selecionada, e o **modo** — pagamento
parcial ou quitação total — derivado do estado dela e de mais nada. Uma fatura que ainda recebe
compras (`OPEN` ou `RETROACTIVE`) aceita parte do devido e permanece no seu status; uma `CLOSED`
aceita apenas o total, e é a única cuja quitação marca `PAID` — de modo que `PAID` seja sempre
precedido de `CLOSED`. A regra de oferta tem dono único no domínio: as superfícies a consomem em
vez de reenumerar status, o verbo do comando deriva do estado, e o caso de uso recusa o que o
predicado exclui. A escrita é uma só no razão — saída da conta pagadora sem dimensão, entrada na
`LIABILITY` do cartão carregando a dimensão da fatura —, com a travessia de moedas completada
pela fronteira de escrita (`balanced-ledger`) e a taxa colhida no acervo
(`currency-consolidation`). A data obedece à hierarquia de `invoice-governs-date`, na janela de
liquidação que o estado da fatura decide.

## Requirements

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

### Requirement: A fatura retroativa é dívida que se paga

Uma fatura `RETROACTIVE` com saldo devedor SHALL ser alcançável para pagamento pela mesma superfície das demais. Ela é um ciclo passado em regularização, e o domínio já a reconhece como devedora — a interface MUST NOT ser o único lugar que a trata como liquidada.

O pagamento de uma retroativa SHALL ser datado dentro da janela do próprio ciclo, que está inteiramente no passado. Uma data no passado aqui não é defeito: é o que a operação afirma.

Pagar uma retroativa MUST NOT alterar o seu status, nem quando o pagamento zera o devido.

#### Scenario: Retroativa com saldo é oferecida
- **WHEN** um cartão tem uma fatura `RETROACTIVE` com saldo devedor
- **THEN** ela aparece entre as faturas pagáveis daquele cartão e aceita pagamento

#### Scenario: A data do pagamento retroativo pertence ao ciclo
- **WHEN** o usuário paga uma fatura `RETROACTIVE` cujo ciclo se encerrou há dois meses
- **THEN** as datas oferecidas são as da janela daquele ciclo, e nenhuma posterior a ela

#### Scenario: Pagar tudo não muda o status da retroativa
- **WHEN** o usuário paga integralmente o devido de uma fatura `RETROACTIVE`
- **THEN** o devido passa a zero e a fatura continua `RETROACTIVE`

### Requirement: `PAID` é sempre precedido de `CLOSED`

A quitação total de uma fatura `CLOSED` SHALL marcá-la `PAID`. Nenhum outro pagamento SHALL produzir essa transição: pagar integralmente uma fatura `OPEN` ou `RETROACTIVE` MUST NOT quitá-la, porque ela continua podendo receber lançamentos.

O único outro caminho até `PAID` SHALL continuar sendo o fechamento de uma fatura sem dívida, que já é dono dessa regra. Em consequência, toda fatura `PAID` SHALL ter passado por `CLOSED` ou pelo fechamento que a quitou — e um pagamento MUST NOT ser a exceção a isso.

Quitar uma fatura retroativa SHALL ser, portanto, o encadeamento de dois gestos com donos próprios: pagá-la até zerar e fechá-la.

#### Scenario: Quitar uma fatura fechada
- **WHEN** o usuário confirma a quitação total de uma fatura `CLOSED`
- **THEN** o valor devido sai da conta escolhida e a fatura passa a `PAID`

#### Scenario: Pagar tudo numa fatura aberta não quita
- **WHEN** o usuário paga o valor integral do devido de uma fatura `OPEN`
- **THEN** a fatura continua `OPEN` e volta a acumular com a próxima compra

#### Scenario: Retroativa zerada é quitada ao fechar
- **WHEN** o usuário paga uma fatura `RETROACTIVE` até zerar o devido e em seguida a fecha
- **THEN** ela passa a `PAID` pelo fechamento, sem um segundo pagamento

### Requirement: O pagamento nomeia a fatura que paga

A operação SHALL declarar qual fatura paga, escolhendo **cartão** e **fatura**, e MUST NOT depender da tela que a abriu para sabê-lo. Aberta a partir de uma fatura em vista, ela SHALL vir com essa fatura pré-selecionada; aberta sem contexto, SHALL permitir escolher.

O conjunto oferecido SHALL ser derivado do predicado de domínio que diz o que uma fatura aceita receber, e MUST NOT ser uma lista de status reescrita pela tela. Uma fatura `FUTURE` ou `PAID` MUST NOT ser oferecida — a primeira porque o seu ciclo não começou, a segunda porque está congelada —, e essa exclusão SHALL ser por construção, e não consequência de a operação falhar depois de escolhida.

Trocar o cartão SHALL limpar a fatura selecionada antes de assumir o novo cartão, de modo que o par (cartão novo, fatura do cartão anterior) MUST NOT ser observável em momento algum.

Trocar o cartão ou a fatura SHALL limpar o valor e o valor de contrapartida já digitados: um valor herdado descreve outra dívida, sob outro teto e possivelmente sob outra moeda.

#### Scenario: Abrir a partir de uma fatura em vista
- **WHEN** o usuário aciona o pagamento a partir da fatura que está vendo
- **THEN** essa fatura e o cartão dela chegam pré-selecionados

#### Scenario: Pagar a fatura de outro cartão
- **WHEN** o usuário troca o cartão dentro da operação
- **THEN** a fatura selecionada é substituída por uma do novo cartão, e o valor digitado é limpo

#### Scenario: Fatura futura não é oferecida
- **WHEN** o cartão tem faturas `FUTURE` criadas por um parcelamento
- **THEN** nenhuma delas aparece entre as faturas pagáveis

#### Scenario: Fatura paga não é oferecida
- **WHEN** o cartão tem faturas já quitadas
- **THEN** nenhuma delas aparece entre as faturas pagáveis

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

### Requirement: A regra de oferta tem dono único no domínio

O que uma fatura aceita — pagamento parcial, quitação total, ou nada — SHALL ser declarado uma vez, no domínio, e consumido por toda superfície que ofereça a operação. Nenhuma tela, componente ou modelo de UI SHALL reenumerar status para chegar à mesma conclusão.

A regra SHALL valer também **abaixo** da interface: o caso de uso que grava um pagamento parcial SHALL recusar uma fatura que o predicado exclui. A oferta e a permissão MUST NOT ter donos diferentes — uma regra que só existe na tela é uma regra que a próxima tela não herda.

Os fatos que a interface exibe — se há pagamento a oferecer e com que verbo — SHALL chegar aos componentes já resolvidos pelo mapper, e MUST NOT ser derivados dentro do componente.

#### Scenario: Uma superfície nova não redecide
- **WHEN** uma superfície passa a oferecer o pagamento de fatura
- **THEN** ela lê o predicado do domínio, sem enumerar status

#### Scenario: O caso de uso recusa o que a tela não oferece
- **WHEN** um pagamento parcial é solicitado sobre uma fatura `CLOSED`
- **THEN** o domínio o recusa, independentemente de haver ou não tela que o ofereça

### Requirement: Uma porta por superfície, com o verbo derivado do estado

Cada superfície que ofereça o pagamento de uma fatura SHALL oferecê-lo por **um** comando, e não por um par. O verbo desse comando SHALL derivar do estado da fatura em vista.

"Antecipar" SHALL nomear apenas o pagamento de uma fatura ainda aberta, porque só ali existe algo a antecipar. Uma fatura `CLOSED` e uma `RETROACTIVE` SHALL ser ambas nomeadas por "pagar" — a primeira porque o ciclo terminou, a segunda porque ele terminou há mais tempo ainda.

O texto que a operação exibe sobre si mesma SHALL corresponder ao modo em vigor, e MUST NOT afirmar como fixa uma regra que vale só para um dos estados.

#### Scenario: Fatura aberta em vista
- **WHEN** a superfície exibe uma fatura `OPEN` com dívida
- **THEN** o comando oferecido é um só, e nomeia antecipar o pagamento

#### Scenario: Fatura fechada em vista
- **WHEN** a superfície exibe uma fatura `CLOSED` com dívida
- **THEN** o comando oferecido é um só, e nomeia pagar a fatura

#### Scenario: Fatura retroativa em vista
- **WHEN** a superfície exibe uma fatura `RETROACTIVE` com dívida
- **THEN** o comando oferecido é um só, e nomeia pagar a fatura

### Requirement: A escrita do pagamento tem uma forma só

Qualquer que seja o modo, o pagamento SHALL ser gravado com a mesma forma no razão: a saída da conta pagadora **sem dimensão**, e a entrada na conta `LIABILITY` do cartão carregando a dimensão da fatura. A dimensão da fatura MUST NOT ser replicada na perna da conta pagadora, nem em perna de conversão alguma.

Quando as duas pontas são denominadas de forma diferente, o que a fatura deve SHALL permanecer exato na moeda do cartão, e o que sai da conta SHALL ser o valor declarado pelo usuário; nenhuma taxa SHALL ser parâmetro da operação. A taxa que o pagamento aplicou SHALL ser derivada das duas pontas e escrita no acervo, e MUST NOT ser gravada na transação.

Essa forma SHALL ter um dono único, e MUST NOT ser reescrita por cada modo — dois modos que montam a mesma transação em dois lugares divergem sem que nada acuse.

#### Scenario: A dimensão fica só na perna do cartão
- **WHEN** um pagamento de fatura é gravado, em qualquer modo
- **THEN** apenas a perna da conta `LIABILITY` do cartão carrega a dimensão da fatura

#### Scenario: Pagamento entre moedas colhe a taxa
- **WHEN** um pagamento é feito de uma conta em reais para uma fatura em dólar
- **THEN** cada moeda soma zero, o resíduo vai para a conta de conversão sem dimensão, e a taxa implicada pelas duas pontas é registrada no acervo daquela data
