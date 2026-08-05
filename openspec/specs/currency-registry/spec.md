# currency-registry Specification

## Purpose
TBD - created by archiving change enable-currency-registration. Update Purpose after archive.
## Requirements
### Requirement: O conjunto de moedas oferecidas é dado, e tem fonte única

O conjunto de moedas que o app oferece SHALL ser **dado gravado**, e MUST NOT ser lista
embarcada no código. Ele SHALL ter exatamente uma fonte, e toda tela, formulário e
componente que ofereça, nomeie ou simbolize uma moeda SHALL ler dela.

MUST NOT existir a distinção, em tempo de execução, entre moeda "embarcada" e moeda "do
usuário": há linhas, e todas se comportam do mesmo modo. O que o app embarca é o conteúdo
inicial dessas linhas, e nada além disso.

A consequência é que não há dois conjuntos a unir, nem regra de precedência entre eles: um
código existe ou não existe, e o usuário pode editar qualquer linha, inclusive uma que a
semeadura escreveu.

O razão MUST NOT conhecer este conjunto. Ele persiste o código da moeda e nada mais, e
nenhuma leitura, escrita ou validação sua SHALL consultar estas linhas — o que mantém
intacto o limite que `currency-consolidation` já exige.

#### Scenario: Uma fonte só
- **WHEN** o código é inspecionado
- **THEN** não existe lista de moedas declarada em código de produção, e todo consumidor lê do repositório

#### Scenario: A linha semeada é editável como qualquer outra
- **WHEN** o usuário edita o símbolo de uma moeda que a semeadura escreveu
- **THEN** a edição é aceita, e a linha passa a valer com o símbolo novo

#### Scenario: O razão continua alheio
- **WHEN** o razão é inspecionado
- **THEN** nenhuma query, escrita ou validação sua nomeia a tabela de moedas

### Requirement: A semente é o que o app precisa antes de existir dado do usuário

O app SHALL embarcar um conjunto inicial de moedas, e ele SHALL ser justificado por um
critério declarado: **o mercado de origem do app, mais as moedas em que uma figura é
legível por alguém de fora dele**. A semente MUST NOT ser uma tentativa de cobrir os
mercados onde o app é usado — a moeda do próprio usuário chega pela sua linha do locale, e
não pela semente.

A semente SHALL conter BRL, USD, EUR, GBP, CHF e CNY. Ela MUST NOT conter moeda de zero ou
três casas decimais, o que exclui JPY apesar de ela pertencer ao mesmo grupo de moedas mais
transacionadas.

A moeda de último recurso SHALL pertencer à semente. Sem isso o último recurso apontaria
para uma linha que pode não existir, que é a única forma de a resolução da moeda base não
ter resposta.

#### Scenario: A semente é enxuta e declarada
- **WHEN** o conjunto embarcado é inspecionado
- **THEN** ele contém seis moedas, e o critério que as escolhe está registrado junto delas

#### Scenario: O último recurso existe como linha
- **WHEN** a semeadura termina
- **THEN** a moeda de último recurso é uma das linhas gravadas

#### Scenario: A semente não cobre o mercado do usuário
- **WHEN** um usuário cujo dispositivo indica peso argentino abre o app pela primeira vez
- **THEN** o peso argentino existe por ser a moeda do locale, e não por o app o ter embarcado

### Requirement: A semeadura não faz ninguém perder a moeda que já usa

A semeadura SHALL gravar, numa única operação: as moedas da semente, **toda moeda já
denominada por uma conta existente**, e a **moeda indicada pelo locale do dispositivo**.

Ela SHALL acontecer **quando o conjunto de moedas passa a existir**, e MUST NOT ser
condicionada a uma migração. Um banco que já existe chega lá pela migração; uma instalação
nova cria o seu esquema sem migração alguma, e teria de ser semeada assim mesmo — do
contrário o único usuário sem moeda nenhuma seria justamente o que acabou de instalar o
app, que é o caso que esta capability existe para atender.

Os dois gatilhos SHALL executar a **mesma** escrita, e MUST NOT ser duas escritas
equivalentes: o que a regra abaixo proíbe é um segundo caminho que possa divergir, não um
segundo momento em que o mesmo caminho é percorrido. A escrita SHALL ser idempotente, e
executá-la sobre uma linha que já existe MUST NOT alterá-la.

Encolher o conjunto embarcado MUST NOT remover de um usuário a moeda em que ele já tem
conta, orçamento ou taxa. Uma moeda que sai da semente e está em uso SHALL ser gravada como
linha, e a redução do conjunto embarcado SHALL ser, para quem já a usava, indistinguível de
nada ter acontecido.

A moeda do locale SHALL ser gravada por esta mesma operação, e MUST NOT ter mecanismo
próprio de registro automático. Uma segunda **escrita** para o mesmo fim é um segundo lugar
onde a moeda do usuário pode deixar de existir — o que não se confunde com os dois gatilhos
exigidos acima, que percorrem a escrita única.

A moeda do locale SHALL ser gravada **uma única vez**, na semeadura. Uma alteração
posterior do locale MUST NOT gravar linha alguma — o que preserva a regra de
`currency-consolidation` de que uma viagem não move nada.

#### Scenario: Moeda em uso fora da semente sobrevive
- **WHEN** um usuário com conta em novo sol peruano atualiza para a versão que encolhe o conjunto embarcado
- **THEN** o novo sol peruano existe como linha, aparece nos formulários, e a conta dele continua legível como antes

#### Scenario: A moeda do dispositivo chega pela semeadura
- **WHEN** o app é instalado num dispositivo cujo locale indica zloty polonês
- **THEN** o zloty polonês é uma das linhas gravadas, e a moeda base resolve nele em vez de no último recurso

#### Scenario: Viajar depois não grava nada
- **WHEN** o usuário troca o locale do dispositivo depois da semeadura
- **THEN** nenhuma linha é criada, alterada ou removida

#### Scenario: Uma instalação nova é semeada sem migração alguma
- **WHEN** o app é instalado do zero, e o esquema é criado a partir das entidades sem que nenhuma migração rode
- **THEN** o conjunto de moedas nasce semeado, e não vazio

#### Scenario: Uma operação, não duas
- **WHEN** o código da semeadura é inspecionado
- **THEN** a semente, as moedas em uso e a moeda do locale são gravadas pela mesma operação, e o banco que atualiza e a instalação nova percorrem a mesma escrita

### Requirement: O nome de uma moeda é da plataforma, salvo quando o usuário o escreveu

Uma linha SHALL guardar nome **apenas quando o usuário o escreveu**. Quando ela não guarda
nome, o nome exibido SHALL ser o que a plataforma dá ao código, **no idioma corrente**, e
SHALL recair no próprio código quando a plataforma não souber nomeá-lo.

O nome de uma moeda MUST NOT ser chave de string do app. Gravá-lo na semeadura o
congelaria no idioma da primeira execução: trocar o idioma do app deixaria de traduzi-lo,
em silêncio, sem marca e sem forma de o usuário perceber — que é o mesmo defeito que
`currency-consolidation` já proíbe ao falar de dado gravado cujo significado muda sozinho.

O símbolo SHALL ser gravado sempre, porque é curto e é o que aparece sobre um valor. A
plataforma SHALL ser consultada para **sugeri-lo** no formulário de cadastro, e o usuário
SHALL poder substituí-lo.

O símbolo gravado SHALL ser a **única** fonte do glifo que aparece sobre um valor, e a
plataforma MUST NOT ser consultada para obtê-lo na formatação. Ela responde pelo *locale
do dispositivo*, não pela moeda: `USD` rende `US$` num aparelho em português e `$` num em
inglês, e nenhum dos dois é necessariamente o que o usuário gravou. Consultá-la ali
produziria duas consequências que este requisito proíbe — um símbolo editado não
alcançaria saldo, transação nem relatório, e o glifo de uma mesma moeda mudaria ao trocar
o idioma do app, sem ninguém ter editado nada. O locale SEGUE decidindo o **formato**:
separadores, agrupamento e a posição do símbolo, que são propriedades do idioma em que se
lê e não da moeda.

Uma moeda fora do ISO 4217 SHALL ser formatada como qualquer outra, com o seu símbolo
gravado no lugar do glifo. Imprimir o código sobre o valor daria à moeda cadastrada pelo
usuário uma forma que nenhuma outra tem, e faria o formulário e o valor discordarem sobre
a mesma moeda.

Quando a tabela nada disser sobre um código, o glifo SHALL ser o próprio código — o mesmo
pior caso, em qualquer superfície, para que um seletor e o valor ao lado dele não possam
divergir. Isso inclui o intervalo entre a abertura do app e a primeira leitura da tabela.

Que o símbolo seja gravado e o nome não é deliberado e a assimetria é a regra: o nome é
uma tradução, que o idioma corrente deve refazer; o símbolo é uma escolha, que o usuário
faz uma vez e pode refazer quando quiser. A semeadura grava a sugestão da plataforma no
idioma em que o app foi aberto pela primeira vez — o que fica gravado é o que aquele
usuário lia, e é editável.

Consultar a plataforma para nomear um código MUST NOT ser confundido com deixá-la decidir
**quais** moedas existem: o conjunto oferecido é a tabela, e o que a plataforma responde é
sobre uma linha que já existe. Enumerar moedas pela plataforma daria conjuntos diferentes
por sistema operacional e por versão, o que este requisito proíbe.

#### Scenario: Trocar o idioma retraduz o nome
- **WHEN** o usuário troca o idioma do app e abre o seletor de moedas
- **THEN** as moedas que ele não nomeou aparecem no idioma novo

#### Scenario: O nome que o usuário escreveu é dele
- **WHEN** o usuário cadastra uma moeda chamada "Pontos do cartão" e troca o idioma do app
- **THEN** o nome continua "Pontos do cartão"

#### Scenario: Código que a plataforma não sabe nomear
- **WHEN** uma linha sem nome tem um código que a plataforma não reconhece
- **THEN** o próprio código é exibido no lugar do nome, sem erro

#### Scenario: A plataforma sugere, não decide
- **WHEN** o código é inspecionado
- **THEN** nenhum consumidor obtém a lista de moedas oferecidas a partir da plataforma

#### Scenario: O símbolo editado aparece sobre os valores
- **WHEN** o usuário substitui o símbolo de uma moeda que a plataforma conhece
- **THEN** saldos, transações e relatórios daquela moeda passam a exibir o símbolo gravado

#### Scenario: Trocar o idioma não troca o glifo
- **WHEN** o usuário troca o idioma do app
- **THEN** o glifo sobre cada valor continua o gravado, e apenas separadores, agrupamento e a posição do símbolo acompanham o idioma novo

#### Scenario: Uma moeda cadastrada pelo usuário se parece com as outras
- **WHEN** uma moeda fora do ISO 4217 com símbolo "MI" denomina um valor
- **THEN** o valor é formatado com "MI" no lugar do glifo, no mesmo formato de qualquer outra moeda, e o código não aparece sobre ele

#### Scenario: Valor e seletor não divergem
- **WHEN** a tabela nada diz sobre um código
- **THEN** o próprio código aparece tanto sobre o valor quanto ao lado do nome da conta

### Requirement: O usuário cadastra uma moeda com código único e duas casas decimais

O sistema SHALL permitir ao usuário cadastrar uma moeda informando o seu **código** e o seu
**símbolo**, e opcionalmente o seu **nome**. Ao digitar um código que a plataforma
reconheça, o formulário SHALL sugerir símbolo e nome, e o usuário SHALL poder alterá-los.

O código SHALL ser único, e o cadastro de um código já existente SHALL ser recusado com o
motivo. O código MUST NOT ser restrito à faixa de uso privado da ISO 4217: o caso comum é o
usuário redigitar um código ISO real que não pertence à semente, e restringi-lo tornaria
impossível justamente o que este cadastro existe para permitir.

Toda moeda SHALL ter **duas** casas decimais, e o formulário MUST NOT oferecer essa escolha.
A premissa de base 100 permanece a de `currency-consolidation` e não é afrouxada aqui: uma
moeda de zero ou três casas exige refazer a fronteira entre `Double` e centavos, o que é
outra mudança.

O cadastro MUST NOT criar conta, taxa ou qualquer outro dado. Ele acrescenta uma moeda ao
que os formulários oferecem, e nada mais.

#### Scenario: Cadastro sugerido pela plataforma
- **WHEN** o usuário digita `CLP` no formulário de moeda
- **THEN** símbolo e nome são preenchidos com o que a plataforma diz, e ele pode alterá-los antes de gravar

#### Scenario: Cadastro de código inventado
- **WHEN** o usuário cadastra `MILHAS` com símbolo e nome próprios
- **THEN** a moeda passa a ser oferecida nos formulários, com o símbolo e o nome que ele escreveu

#### Scenario: Código repetido é recusado
- **WHEN** o usuário tenta cadastrar um código que já existe
- **THEN** o cadastro é recusado com o motivo, e nenhuma linha é alterada

#### Scenario: As casas decimais não são perguntadas
- **WHEN** o formulário de cadastro é inspecionado
- **THEN** não existe controle de casas decimais, e toda moeda gravada tem duas

#### Scenario: Cadastrar não cria mais nada
- **WHEN** uma moeda é cadastrada e o dado gravado é inspecionado
- **THEN** nenhuma conta, taxa ou orçamento foi criado

### Requirement: Apagar uma moeda é recusado por quem a denomina, e leva o acervo junto

Apagar uma moeda SHALL ser recusado, **com o motivo que o usuário pode agir sobre**, quando
uma conta ou um orçamento a nomeia. É a forma que o app já dá a toda recusa de exclusão, e
a razão é a mesma: o limite de um orçamento tem denominação imutável, e a moeda de uma
conta também — apagá-la deixaria um número que ninguém consegue mais nomear.

Uma linha do acervo de taxas MUST NOT bloquear a exclusão. Ela SHALL, em vez disso, ser
**removida junto**: apagar uma moeda SHALL remover toda observação do acervo que a nomeie
em qualquer das duas pontas, na mesma escrita.

Isso é o que torna "a taxa não bloqueia" seguro, e é diferente de deixá-la para trás. Uma
observação órfã continuaria a ser **caminho de conversão** — o resolvedor lê o acervo, não o
conjunto de moedas oferecidas —, produzindo figuras trianguladas por uma moeda que não
existe em lugar nenhum da interface. Continuaria também a poder ser aberta para correção
num formulário cujo seletor não a contém, que é um estado inválido no único caminho pelo
qual o usuário conserta um número errado. E, sendo o código reaproveitável, um código
inventado que fosse apagado e recadastrado veria as observações antigas se recolarem, em
silêncio, num conceito diferente.

A exclusão SHALL declarar ao usuário, antes de acontecer, quantas observações serão
removidas junto.

#### Scenario: Conta bloqueia a exclusão
- **WHEN** o usuário tenta apagar uma moeda em que existe conta
- **THEN** a exclusão é recusada com o motivo, e nada é removido

#### Scenario: Orçamento bloqueia a exclusão
- **WHEN** o usuário tenta apagar uma moeda em que existe limite de orçamento
- **THEN** a exclusão é recusada com o motivo, e nada é removido

#### Scenario: As taxas vão junto
- **WHEN** o usuário apaga uma moeda que nenhuma conta e nenhum orçamento nomeia, e que aparece em três observações do acervo
- **THEN** a moeda e as três observações deixam de existir, na mesma escrita

#### Scenario: Uma falha no meio não deixa metade feita
- **WHEN** a remoção da moeda falha depois de as observações já terem sido apagadas
- **THEN** as observações continuam existindo, e a moeda também, porque as duas remoções são uma única unidade de trabalho

#### Scenario: A exclusão diz o que leva
- **WHEN** o usuário confirma a exclusão de uma moeda que aparece no acervo
- **THEN** o número de observações que serão removidas é apresentado antes de a exclusão acontecer

#### Scenario: Nenhum pivô sobrevive à moeda
- **WHEN** uma moeda que servia de pivô a uma triangulação é apagada
- **THEN** aquela triangulação deixa de existir, e a parcela correspondente volta a ser termo próprio

### Requirement: Uma moeda é aberta antes de ser alterada

A listagem do registro SHALL apresentar cada moeda como uma linha que **abre**, e MUST NOT
oferecer ação sobre ela na própria linha. Editar, arquivar, desarquivar e apagar SHALL
viver na tela que a linha abre — a mesma etapa intermediária que uma conta, um cartão e uma
categoria já têm.

Botão dentro de linha de lista vertical transforma cada linha numa barra de ferramentas e
põe uma ação destrutiva a um toque errado de um scroll. O padrão do app é o outro, e um
segundo padrão para o mesmo gesto é uma divergência a manter.

A tela aberta SHALL apresentar **o que denomina a moeda** — quantas contas, quantos
orçamentos e quantas observações do acervo a nomeiam — antes de oferecer qualquer ação. É
o que decide se apagar é possível, então o usuário SHALL poder lê-lo em vez de descobri-lo
sendo recusado.

Ela SHALL oferecer **uma** ação de retirada, e qual delas é a resposta da regra de
exclusão, e não uma segunda derivação: quando uma conta ou um orçamento denomina a moeda, o
que se oferece é arquivar. Uma tela MUST NOT oferecer uma ação que o domínio recusa.

A moeda base MUST NOT oferecer retirada alguma — arquivá-la é recusado, e apagá-la é
recusado pela conta que ela denomina.

#### Scenario: A linha abre, e não age
- **WHEN** o usuário vê a lista de moedas
- **THEN** nenhuma linha oferece editar, arquivar ou apagar, e tocá-la abre a moeda

#### Scenario: O que impede a exclusão é dito antes
- **WHEN** o usuário abre uma moeda em que existe conta
- **THEN** a quantidade de contas que a denominam é apresentada, e a ação oferecida é arquivar

#### Scenario: A base não oferece retirada
- **WHEN** o usuário abre a moeda que está em vigor como base
- **THEN** nenhuma ação de arquivar ou apagar é oferecida, e editar continua sendo

### Requirement: Arquivar uma moeda é regra de oferta, e só isso

O sistema SHALL permitir **arquivar** uma moeda, e o arquivamento SHALL ter o mesmo formato
que já tem para conta e categoria: a linha some de onde se oferece uma escolha, permanece
legível onde já era usada, e é reversível.

Uma moeda arquivada MUST NOT deixar de existir para o que já a nomeia. Uma conta nela
continua ativa, continua aceitando lançamento, e as suas figuras continuam sendo
consolidadas. Arquivar responde "não me ofereça mais isto", e não "isto não vale mais".

O arquivamento MUST NOT ser consultado pelo razão. Todo outro arquivável deste app é barrado
**também** na fronteira de escrita; uma moeda não pode ser, porque o razão não conhece o
conjunto de moedas oferecidas, e porque a moeda não é linha que ele referencie — é um código
denormalizado em cada conta e em cada entry, sem chave estrangeira. A moeda arquivada tem,
portanto, uma linha de defesa e não duas, **e isso é deliberado**: acrescentar o veto no
razão é romper o limite do módulo.

Uma moeda arquivada SHALL continuar disponível como **caminho de conversão**, servindo de
pivô e sendo lida do acervo como qualquer outra. As observações continuam válidas —
arquivar é sobre o que se oferece, não sobre o que se sabe.

Uma moeda arquivada SHALL continuar sendo apresentada no formulário que edita uma taxa que
já a nomeia, para que a correção dessa taxa continue possível. Ela MUST NOT ser oferecida no
cadastro de uma taxa nova.

#### Scenario: Some da oferta
- **WHEN** o usuário arquiva uma moeda e abre o formulário de conta
- **THEN** ela não é oferecida entre as moedas escolhíveis

#### Scenario: O que já existia continua
- **WHEN** existe conta numa moeda que o usuário arquiva
- **THEN** a conta continua ativa, aceita lançamento, e as suas figuras continuam sendo consolidadas

#### Scenario: O razão não é consultado sobre arquivamento
- **WHEN** o razão é inspecionado
- **THEN** nenhuma escrita sua é recusada por a moeda estar arquivada

#### Scenario: Arquivada ainda pivota
- **WHEN** uma conversão depende de uma triangulação cujo pivô é uma moeda arquivada
- **THEN** a conversão acontece, e o resultado é o mesmo de antes do arquivamento

#### Scenario: Corrigir taxa que nomeia moeda arquivada
- **WHEN** o usuário abre para correção uma taxa cuja ponta é uma moeda arquivada
- **THEN** o formulário a apresenta como valor corrente do campo, e a correção é possível

#### Scenario: Taxa nova não a oferece
- **WHEN** o usuário cadastra uma taxa nova
- **THEN** a moeda arquivada não está entre as escolhíveis

#### Scenario: Arquivar é reversível
- **WHEN** o usuário desarquiva uma moeda
- **THEN** ela volta a ser oferecida em todos os formulários, exatamente como antes

