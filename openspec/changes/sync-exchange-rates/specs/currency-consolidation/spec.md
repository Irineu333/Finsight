## ADDED Requirements

### Requirement: O acervo é mantido atualizado por uma fonte remota, que escreve nele e nunca é lida por ele

O sistema SHALL manter o acervo de taxas alimentado automaticamente a partir de uma **fonte remota**, sem exigir do usuário cadastro algum. A fonte remota SHALL ser um **escritor** do acervo, ao lado da colheita de operação e do cadastro do usuário, e MUST NOT ser um caminho de leitura de coisa alguma.

Nenhuma leitura, tela ou figura do app SHALL depender de rede, apresentar estado de carregamento ou falhar por indisponibilidade em razão de conversão de moeda. Toda conversão SHALL continuar a ler exclusivamente o acervo local, que permanece a única autoridade. A garantia é sustentada pela **direção do fluxo** — a rede escreve no acervo, e o acervo é lido offline —, e MUST NOT ser sustentada por proibir que a fonte exista.

A sincronização SHALL cobrir as moedas que o app **oferece** — o registro de moedas, as arquivadas excluídas — mais qualquer moeda ainda **em uso** numa conta ou num cartão, cada uma contra a moeda base em vigor. Os cruzamentos entre duas moedas não-base MUST NOT exigir sincronização própria: eles SHALL ser alcançados pela resolução já declarada, por uma triangulação sobre a base.

Cobrir o que é **oferecido**, e não apenas o que já está em uso, é o que faz a taxa **preceder** a conta. Uma conta criada numa moeda que o app já oferece SHALL encontrar a taxa pronta; cobrir apenas o que está em uso faria toda primeira conta numa moeda nascer no pior caso e esperar a sincronização do dia seguinte, que é o estado que esta capability existe para remover. Uma moeda **arquivada** que ainda tenha conta ou cartão SHALL permanecer coberta: arquivar é sobre o que se oferece e não sobre o que se sabe, e a figura daquela conta continua precisando da taxa.

Cada observação obtida SHALL ser gravada na **direção em que será lida** — a moeda em uso precificada na base — e MUST NOT ser invertida na gravação. A direção pedida à fonte é, portanto, parte da forma de perguntar, e não algo a corrigir depois.

Cada observação SHALL ser datada com a **data que a fonte declara**, e MUST NOT ser datada com o dia em que a sincronização ocorreu. Uma fonte que não publica todos os dias responde com a data da sua última publicação, e gravar outra data inventaria uma observação sobre um dia em que ninguém observou nada. Como consequência, sincronizar mais de uma vez sobre a mesma publicação SHALL ser inócuo.

A sincronização SHALL ser disparada na abertura do app, sem que nada aguarde a sua conclusão, e SHALL ser limitada a uma vez por dia **por par**. Ela MUST NOT bloquear qualquer tela, MUST NOT exibir estado de carregamento fora da tela que apresenta o acervo, e falhar SHALL significar não escrever nada.

O limite SHALL ser por **par** — a moeda e aquilo contra o que ela foi cotada — e MUST NOT ser global nem por moeda. Um limite global tornaria uma moeda recém-cadastrada refém de uma sincronização que já ocorreu naquele dia. Um limite por moeda faria o mesmo com **todos** os pares no instante em que a moeda base mudasse: cada moeda pareceria já respondida, enquanto a linha que acabou de passar a responder — aquela contra a base nova — nunca foi buscada. Nos dois casos o resultado é o pior caso, apenas adiado.

A sincronização SHALL também ser disparada quando o conjunto de moedas oferecidas **ganhar** uma moeda e quando a **moeda base mudar**, de modo que nem cadastrar uma moeda nem trocar a base dependa de esperar um dia. Trocar a base é o caso mais forte dos dois: o acervo é *tudo precificado na base*, então a troca faz um conjunto inteiro de pares passar a ser o que responde, e nenhum deles jamais foi buscado. Isso MUST NOT ser confundido com um comando de sincronizar: o gatilho é uma mudança de estado do app, não uma ação que o usuário precisa lembrar de executar, e nada na tela o aguarda.

A sincronização MUST NOT depender de ação do usuário, e o sistema MUST NOT oferecer comando algum para dispará-la. Manter o acervo é obrigação do app; um comando manual a tornaria tarefa que o usuário precisa lembrar de executar, e criaria uma superfície que espera rede com ele olhando — a mesma forma de estado de carregamento que a disparada na abertura torna desnecessária.

O sistema MUST NOT embarcar taxas semeadas na instalação. Uma primeira execução **sem rede** SHALL cair no comportamento já definido para taxa ausente — a figura permanece com um termo por moeda —, e isso SHALL ser o limite declarado desta garantia, que é sobre o usuário com rede.

Quando a fonte remota não cobrir uma moeda em uso, o sistema SHALL dizê-lo ao usuário, e MUST NOT deixar a ausência de taxa indistinguível de uma sincronização que ainda não ocorreu. Os dois estados levam a ações diferentes — esperar, ou cadastrar à mão — e só a distinção entre eles é acionável.

#### Scenario: Usuário multimoeda tem taxa sem cadastrar nada
- **WHEN** o usuário cria uma conta em dólar tendo o real como base, com rede disponível, e abre o app
- **THEN** a taxa do par dólar/real passa a existir no acervo, e as figuras consolidadas somam em vez de empilhar termos, sem que ele tenha cadastrado ou transacionado

#### Scenario: A taxa precede a conta
- **WHEN** o app já sincronizou hoje e o usuário cria a sua primeira conta em dólar, o dólar sendo uma moeda que o app oferece
- **THEN** a taxa do par dólar/real já está no acervo, e a figura consolidada soma desde a criação — sem esperar a sincronização do dia seguinte

#### Scenario: Cadastrar uma moeda nova não espera um dia
- **WHEN** o app já sincronizou hoje e o usuário cadastra uma moeda que o registro não tinha
- **THEN** a cotação daquela moeda é buscada em seguida, e as demais não são consultadas de novo

#### Scenario: Trocar a moeda base não espera um dia
- **WHEN** o app já sincronizou hoje e o usuário troca a moeda base
- **THEN** os pares contra a base nova são buscados em seguida, na direção em que serão lidos, e o acervo não fica um dia atrás da preferência

#### Scenario: Voltar à base anterior não custa requisição
- **WHEN** o usuário troca a base e volta atrás no mesmo dia
- **THEN** nenhum par é buscado de novo, porque todos já foram respondidos naquele dia

#### Scenario: Uma moeda arquivada com conta viva continua coberta
- **WHEN** uma moeda é arquivada e ainda existe uma conta denominada nela
- **THEN** a sincronização continua a cobri-la, e a figura daquela conta continua somando

#### Scenario: A sincronização escreve, e nenhuma leitura a espera
- **WHEN** qualquer tela com figura consolidada é aberta
- **THEN** ela lê apenas o acervo local, sem requisição, sem estado de carregamento e sem falha possível por indisponibilidade

#### Scenario: Primeira execução sem rede cai no comportamento definido
- **WHEN** o app é aberto pela primeira vez sem rede, com contas em duas moedas
- **THEN** nenhuma taxa existe, a figura consolidada exibe um termo por moeda, e nenhum erro é apresentado

#### Scenario: A observação é gravada na direção em que é lida
- **WHEN** a sincronização obtém a cotação do dólar tendo o real como base
- **THEN** a linha gravada é a do dólar precificado em real, e nenhum quociente foi invertido para gravá-la

#### Scenario: A data é a da fonte, não a do dia
- **WHEN** a sincronização ocorre num domingo e a fonte declara a cotação de sexta-feira
- **THEN** a observação é gravada com a data de sexta-feira

#### Scenario: Sincronizar de novo sobre a mesma publicação não duplica
- **WHEN** a sincronização roda duas vezes sobre a mesma publicação da fonte
- **THEN** o acervo tem uma observação, e não duas

#### Scenario: Cruzamento entre duas não-base não exige sincronização própria
- **WHEN** existem contas em dólar e em euro, com o real como base, e a sincronização obteve as duas contra o real
- **THEN** a conversão entre dólar e euro é resolvida por triangulação sobre o real, sem que nenhuma observação desse par tenha sido buscada

#### Scenario: Falha de rede não escreve e não aparece na figura
- **WHEN** a sincronização falha por indisponibilidade
- **THEN** nenhuma observação é criada ou alterada, e nenhuma figura consolidada exibe erro ou carregamento

#### Scenario: Não há comando de sincronizar
- **WHEN** o usuário procura, em qualquer tela, uma forma de disparar a atualização das taxas
- **THEN** não existe nenhuma, e o acervo é mantido sem que ele precise pedir

#### Scenario: Moeda não coberta é dita, e não silenciada
- **WHEN** uma moeda em uso está fora da cobertura da fonte remota
- **THEN** o usuário é informado de que aquela moeda não é coberta e de que o cadastro manual é o caminho, em vez de a ausência de taxa ficar sem explicação

### Requirement: O acervo se apresenta primeiro pela taxa em vigor de cada par

O sistema SHALL apresentar o acervo em duas visões distintas: a **taxa em vigor** de cada par, e o **histórico** completo das observações.

A visão de entrada SHALL ser a da taxa em vigor: uma linha por par, com a observação que hoje responde por ele segundo a política do acervo. Ela SHALL ser a visão de entrada porque é a pergunta que o usuário leva até esta tela — *qual taxa está sendo usada* —, e porque com a manutenção automática o acervo passa a crescer todos os dias, tornando a listagem integral ilegível como apresentação primária.

Cada linha da visão em vigor SHALL declarar o par nas duas pontas, o valor, a data e a origem da observação que responde, e SHALL permitir alcançar o histórico da **moeda** daquela linha.

O que se alcança é a moeda e não o par porque o histórico filtra por moeda nomeada em **qualquer das duas pontas**: tocar numa linha do dólar precificado em real apresenta também o real precificado em dólar, que são observações distintas e são lidas como tais. Estreitar isso ao par exigiria uma dimensão de filtro que o histórico não oferece, e ela responderia pior à pergunta que leva o usuário até lá — *o que já se observou sobre esta moeda*.

A visão em vigor SHALL apresentar o estado da manutenção automática nos casos em que ele é **acionável**: que o acervo **nunca** foi atualizado, e, por moeda em uso, que ela não é coberta pela fonte remota. Esta é a única superfície do app onde o estado da sincronização SHALL aparecer; nenhuma figura consolidada SHALL exibi-lo.

O sistema MUST NOT anunciar a manutenção que funcionou. A data de cada atualização bem-sucedida é o caso ordinário, e um aviso que aparece todo dia dizendo que está tudo bem é a forma mais confiável de a tela deixar de ser lida — inclusive nos dias em que ela tivesse algo a dizer. *Nunca atualizado* é outra coisa: é o estado em que as taxas na tela são apenas as que o usuário mesmo pôs ali.

O estado da manutenção MUST NOT ocupar o lugar de um cabeçalho de grupo nem se parecer com um. Ele fala sobre **o acervo inteiro** e não sobre as linhas que o seguem, e apresentado como uma linha de texto solta acima da lista ele é lido como o cabeçalho daquelas linhas.

O sinal de taxa desatualizada SHALL conviver com o estado da sincronização, e não substituí-lo: sem saber se o app conseguiu atualizar, o usuário não tem como distinguir uma taxa velha que ele não cadastrou de uma que o app não conseguiu buscar.

#### Scenario: A entrada mostra uma linha por par
- **WHEN** o usuário abre a tela de taxas com trinta observações do par dólar/real, uma por dia
- **THEN** ele vê uma única linha para o par, com a observação que responde hoje

#### Scenario: A linha em vigor se descreve por inteiro
- **WHEN** uma linha da visão em vigor é inspecionada
- **THEN** ela declara o par nas duas pontas, o valor, a data e a origem da observação que responde

#### Scenario: O histórico da moeda é alcançável
- **WHEN** o usuário toca numa linha da visão em vigor
- **THEN** o histórico chega pré-filtrado pela moeda daquela linha, com as observações que a nomeiam em qualquer das duas pontas

#### Scenario: Nunca ter atualizado é dito na tela de taxas
- **WHEN** o usuário abre a visão em vigor e o acervo nunca foi atualizado com sucesso
- **THEN** ela diz isso

#### Scenario: A manutenção que funcionou não é anunciada
- **WHEN** o usuário abre a visão em vigor e o acervo foi atualizado com sucesso
- **THEN** nada é dito sobre a atualização, e a tela é só o acervo

#### Scenario: A entrada agrupa pela moeda contraparte
- **WHEN** o usuário abre a visão em vigor com o dólar, o euro e o iene cotados em real
- **THEN** existe um cabeçalho único, o do real, com as três linhas sob ele

#### Scenario: O estado da manutenção não é lido como cabeçalho
- **WHEN** a visão em vigor apresenta quando o acervo foi atualizado
- **THEN** aquilo se distingue de um cabeçalho de grupo, e nenhuma linha da lista aparece como se estivesse agrupada por data

#### Scenario: O estado da sincronização não vaza para as figuras
- **WHEN** qualquer tela com figura consolidada é aberta
- **THEN** nada sobre sincronização é apresentado ali

### Requirement: O histórico do acervo é filtrável

A visão de histórico SHALL listar as observações do acervo e SHALL oferecer filtros por **data**, por **moeda** e por **origem**.

Os filtros SHALL existir porque a manutenção automática torna o acervo denso: sem eles, encontrar a observação que se quer corrigir ou remover passa a depender de rolagem, e a remoção — que existe como corolário de a taxa sobreviver à operação que a originou — deixaria de ser alcançável na prática.

O filtro por origem SHALL distinguir as três: a colhida de operação, a obtida da fonte remota e a informada pelo usuário.

#### Scenario: Filtrar por moeda
- **WHEN** o usuário filtra o histórico por uma moeda
- **THEN** apenas as observações que a nomeiam em alguma das duas pontas são listadas

#### Scenario: Filtrar por origem
- **WHEN** o usuário filtra o histórico pelas observações que ele mesmo informou
- **THEN** as colhidas de operação e as obtidas da fonte remota não são listadas

#### Scenario: Filtrar por data
- **WHEN** o usuário filtra o histórico por um intervalo de datas
- **THEN** apenas as observações daquele intervalo são listadas

#### Scenario: Remover continua alcançável num acervo denso
- **WHEN** o acervo tem centenas de observações e o usuário quer remover uma colhida por engano
- **THEN** ele a alcança pelos filtros, e a remove

### Requirement: A visão em vigor agrupa as suas linhas pela moeda contraparte

A visão da **taxa em vigor** SHALL agrupar as suas linhas pela **moeda contraparte**, aquela em que as linhas do grupo estão precificadas. Cada linha SHALL se descrever por inteiro: o par nas duas pontas, o valor, a data e a origem, de modo que o seu significado não dependa do cabeçalho sob o qual ela está.

Reduzir o acervo a uma linha por par MUST NOT ser confundido com dispensar o agrupamento: a redução é sobre **quantas** linhas existem, e não diz nada sobre como elas são encabeçadas. Uma lista plana deixa a coluna de cotações sem nada declarando em que elas estão precificadas, e o que quer que esteja acima dela passa a ser lido como o cabeçalho que faltou.

A escolha da ponta é decidida pelo caso comum, e não pela simetria: no acervo ordinário toda observação é precificada na base em vigor, então agrupar pela moeda **precificada** poria cada linha num grupo de uma só e não agruparia nada. A contraparte é a ponta que de fato reúne, e o cabeçalho passa a ser a frase que o usuário veio ler.

Uma linha MUST NOT ser exibida invertida em relação à observação que a originou. Como consequência, um mesmo par PODE aparecer em dois grupos, um por sentido, quando foi observado nos dois. São observações distintas e SHALL ser exibidas como tais.

Os grupos SHALL ser ordenados pela observação mais recente de cada moeda.

#### Scenario: O acervo ordinário é um grupo só
- **WHEN** o usuário abre a visão em vigor com o dólar, o euro e o iene cotados em real
- **THEN** existe um único grupo, o do real, com as três linhas

#### Scenario: Uma contraparte fora da base abre o seu próprio grupo
- **WHEN** existe também uma taxa em vigor do iene contra o dólar
- **THEN** ela aparece num grupo do dólar, separada das que são precificadas em real

### Requirement: O histórico agrupa as observações por data

A visão de **histórico** SHALL agrupar as observações pela sua **data**, os dias mais recentes primeiro, e MUST NOT agrupá-las pela moeda contraparte.

O eixo é a data porque é ele que o histórico existe para percorrer, e porque é o único que **envelhece bem**. Com a manutenção automática gravando uma linha por par por dia, o acervo ordinário — em que tudo é precificado na base — colapsa num grupo único de centenas de linhas se for agrupado pela contraparte: exatamente o *não agrupa nada* que a contraparte foi escolhida para evitar na outra visão, alcançado pela outra ponta. A data, ao contrário, particiona o acervo na razão em que ele cresce.

Cada linha SHALL continuar se descrevendo por inteiro — o par nas duas pontas, o valor e a origem —, de modo que o cabeçalho de data não precise dizer nada sobre moeda alguma. Uma linha MUST NOT ser exibida invertida em relação à observação que a originou: esta visão é também o ponto de edição, e editar uma linha invertida abriria a correção de um número que ninguém observou.

Como consequência, o mesmo par observado nos dois sentidos no mesmo dia SHALL aparecer no mesmo grupo, como duas linhas distintas — o que elas são —, e cada uma SHALL declarar a direção em que foi observada.

A ordem dentro de um dia SHALL ser total e estável, para que duas leituras do mesmo acervo listem o mesmo dia na mesma ordem.

#### Scenario: O histórico é particionado por dia
- **WHEN** o usuário abre o histórico com observações de três dias distintos
- **THEN** existem três grupos, um por dia, o mais recente primeiro

#### Scenario: O acervo ordinário não colapsa num grupo só
- **WHEN** o histórico contém trinta dias de cotações do dólar, do euro e do iene, todas contra o real
- **THEN** existem trinta grupos, e não um

#### Scenario: O mesmo par nos dois sentidos aparece no mesmo dia
- **WHEN** existem, no mesmo dia, uma observação do dólar contra o real e outra do real contra o dólar
- **THEN** as duas aparecem no grupo daquele dia, cada uma na direção em que foi observada

#### Scenario: Editar alcança a observação original
- **WHEN** o usuário toca numa linha para corrigi-la
- **THEN** o formulário abre com o par na direção em que a observação foi feita

## MODIFIED Requirements

### Requirement: A taxa é uma observação local e datada sobre um par de moedas

O sistema SHALL manter um acervo de taxas em que cada linha é uma observação sobre **um par de moedas**, com a sua **data** e a sua **origem**. Cada linha SHALL declarar as suas duas pontas, de modo a se ler sozinha — *uma unidade da moeda precificada vale tanto da moeda contraparte* — sem depender de qual preferência de exibição está em vigor.

O acervo MUST NOT conter vestígio da moeda base. Uma linha cujo significado dependesse da preferência em vigor passaria a dizer outra coisa no instante em que ela mudasse, o que é reescrever em silêncio o significado de um dado gravado.

A **direção** de uma linha SHALL ser a da observação que a originou, e MUST NOT ser canonicalizada na gravação. Ordenar as pontas e inverter o quociente para caber numa forma canônica gravaria um número que ninguém observou — o mesmo defeito que gravar a forma exibida produz, aplicado à entrada. Como consequência, o mesmo par PODE existir nos dois sentidos, como duas observações distintas.

O valor gravado SHALL ser o **quociente em precisão plena** entre os dois valores da operação que o originou, e MUST NOT ser a forma exibida, arredondada. O número de casas decimais apresentado ao usuário é decisão de formatação e SHALL existir apenas na apresentação. O arredondamento aplicado ao converter SHALL ter um único dono, na redução que produz a figura consolidada.

A consolidação de uma figura referente a um instante ou período SHALL usar **a última observação em ou antes daquela data**. Uma figura de um período passado MUST NOT ser recalculada à taxa corrente: o passado não SHALL se mover sozinho quando uma taxa muda.

O acervo local SHALL ser a única autoridade usada em qualquer conversão. O usuário SHALL poder cadastrar, corrigir **e remover** taxas a qualquer momento, escolhendo as duas pontas do par.

A **origem** de uma observação SHALL ser uma de três: colhida de operação, obtida de fonte remota, ou informada pelo usuário. Havendo mais de uma observação para o mesmo par **e a mesma data**, a precedência SHALL ser, nesta ordem: a **informada pelo usuário**, a **obtida de fonte remota**, a **colhida de operação**.

A remota SHALL preceder a colhida de operação porque uma taxa colhida carrega o que a operação cobrou — *spread*, imposto, tarifa — e responde *quanto custou*, enquanto a cotação responde *quanto valia*. Consolidar é avaliar, e não reconstituir um custo. A colhida de operação MUST NOT ser descartada por isso: ela é a única que responde sem rede e a única que alcança pares fora da cobertura da fonte.

A precedência por origem SHALL desempatar **apenas entre observações da mesma data**, e MUST NOT prevalecer sobre a data. Uma observação de data mais recente SHALL vencer uma de data anterior seja qual for a origem de qualquer das duas. Em particular, uma taxa informada pelo usuário MUST NOT fixar o par contra observações posteriores: ela corrigiu o dia sobre o qual era uma afirmação, e uma correção que governasse em silêncio todo o futuro é o defeito que a datação do acervo existe para impedir.

A remoção SHALL existir como consequência de a taxa sobreviver à operação que a originou. Removida a última observação que alcançava uma moeda, as figuras que dependiam dela SHALL voltar a exibir o termo próprio daquela moeda, em vez de um valor convertido por uma taxa que ninguém mais sustenta.

Uma observação MUST NOT sobreviver à moeda de que ela fala. Apagar uma moeda, na forma que `currency-registry` define, SHALL remover toda observação que a nomeie em qualquer das duas pontas. O acervo é lido pela resolução da conversão sem consulta ao conjunto de moedas oferecidas, então uma observação órfã continuaria a ser caminho de conversão — e a produzir figuras trianguladas por uma moeda que não existe em lugar nenhum da interface. **Arquivar** uma moeda, ao contrário, MUST NOT remover observação alguma: as observações continuam válidas e continuam a ser lidas, porque arquivar é sobre o que se oferece e não sobre o que se sabe.

Uma fonte remota SHALL alimentar o acervo por escrita, na forma que este documento define, e MUST NOT ser consultada por nenhuma leitura, tela ou figura. Uma fonte externa MAY, além disso, oferecer um valor **sugerido** dentro da tela que edita a taxa, e ele só SHALL valer se o usuário o confirmar.

O sistema SHALL apresentar, junto de onde as taxas são editadas, o par, a data e a origem de cada uma. A origem SHALL distinguir as três. Uma taxa cuja data seja anterior a **30 dias** SHALL ser sinalizada como desatualizada, com um significante textual e não apenas por cor. A data SHALL ser exibida esteja a taxa desatualizada ou não.

#### Scenario: A linha diz sobre que par ela fala
- **WHEN** uma linha do acervo é inspecionada
- **THEN** ela declara a moeda precificada e a moeda contraparte, e o seu significado não muda quando a moeda base muda

#### Scenario: A direção não é canonicalizada
- **WHEN** o usuário observa o real contra o dólar e, depois, o dólar contra o real
- **THEN** as duas linhas existem, cada uma na direção em que foi observada, e nenhuma foi invertida para ser gravada

#### Scenario: A taxa gravada não é a taxa exibida
- **WHEN** uma operação entre moedas produz um quociente com mais casas do que a tela apresenta
- **THEN** o acervo guarda o quociente pleno, a tela apresenta a forma arredondada, e nenhuma conversão posterior usa a forma arredondada

#### Scenario: Conversão usa o acervo local
- **WHEN** uma figura consolidada é calculada
- **THEN** ela usa o acervo local, sem consultar nenhuma fonte externa

#### Scenario: Figura de período passado usa a taxa da época
- **WHEN** o patrimônio de um mês passado é consolidado e existem taxas anteriores e posteriores àquele mês
- **THEN** a conversão usa a última observação em ou antes daquela data, e o valor não muda quando uma taxa mais recente é cadastrada

#### Scenario: A remota vence a colhida de operação no mesmo dia
- **WHEN** existem, para o mesmo par e a mesma data, uma observação colhida de operação e uma obtida da fonte remota
- **THEN** a conversão daquela data usa a obtida da fonte remota

#### Scenario: A do usuário vence as duas no mesmo dia
- **WHEN** existem, para o mesmo par e a mesma data, as três origens
- **THEN** a conversão daquela data usa a informada pelo usuário

#### Scenario: A data vence a origem
- **WHEN** o usuário informa uma taxa num dia e a sincronização traz outra para o mesmo par no dia seguinte
- **THEN** a figura daquele dia seguinte usa a obtida da fonte remota, e a figura do dia da correção continua a usar a do usuário

#### Scenario: A taxa do usuário não fixa o par
- **WHEN** o usuário corrige uma taxa e a sincronização continua a rodar nos dias seguintes
- **THEN** as observações posteriores passam a responder pelas datas delas, e a correção continua a responder pela data dela

#### Scenario: Apagar a moeda apaga as suas observações
- **WHEN** uma moeda é apagada e o acervo é inspecionado
- **THEN** nenhuma observação que a nomeasse em qualquer das duas pontas permanece

#### Scenario: Arquivar a moeda preserva as suas observações
- **WHEN** uma moeda é arquivada e o acervo é inspecionado
- **THEN** todas as suas observações permanecem, e continuam a ser lidas nas conversões

#### Scenario: Sugestão só passa a valer se confirmada
- **WHEN** o usuário abre a tela de edição de uma taxa e um valor sugerido é oferecido
- **THEN** ele só passa a valer se o usuário o confirmar

#### Scenario: App inteiro funciona offline
- **WHEN** o dispositivo está sem rede e qualquer tela com figura consolidada é aberta
- **THEN** a figura é exibida normalmente, sem erro e sem estado de carregamento

#### Scenario: Par, data e origem visíveis
- **WHEN** o usuário abre a tela de taxas
- **THEN** cada linha exibe o seu par, a sua data e se veio de uma operação, da fonte remota ou do próprio usuário

#### Scenario: Taxa desatualizada é sinalizada
- **WHEN** uma taxa foi definida há mais de 30 dias
- **THEN** ela é sinalizada como desatualizada, com um significante textual e não apenas por cor

#### Scenario: Taxa recente exibe a data assim mesmo
- **WHEN** uma taxa foi definida hoje
- **THEN** a sua data é exibida, sem sinalização de desatualizada

### Requirement: Uma operação que atravessa moedas cadastra a sua própria taxa

Quando uma operação atravessa moedas, o sistema SHALL registrar no acervo a taxa que ela aplica, derivada dos valores das suas duas pontas, com a **data da operação**, com o **par** que ela observou e com a origem que a identifica como derivada de operação.

O par observado SHALL ser o das duas moedas da operação. O cadastro MUST NOT ser condicionado a que uma das pontas seja a moeda base: uma operação entre duas moedas não-base é uma observação tão boa quanto qualquer outra, e descartá-la seria jogar fora informação que o usuário já forneceu.

O usuário MUST NOT precisar informar uma taxa que já está implícita numa operação que ele registrou. Para o mesmo par e a mesma data, uma taxa informada pelo usuário e uma obtida de fonte remota SHALL prevalecer sobre a derivada, nessa ordem.

A observação derivada MUST NOT deixar de ser registrada por a fonte remota existir. Ela é a única que existe sem rede, a única que alcança pares fora da cobertura da fonte, e continua a ser o que dispensa o usuário de digitar um número que ele já deu.

Isso MUST NOT ser confundido com persistir a taxa **na** operação, o que `balanced-ledger` proíbe: o que se grava é uma linha do acervo de taxas, dado próprio desta capability, e a operação permanece sem campo de taxa.

#### Scenario: Câmbio alimenta o acervo
- **WHEN** o usuário registra uma transferência de R$ 550 para US$ 100 numa data
- **THEN** uma observação do par dólar/real é registrada naquela data, com origem de operação

#### Scenario: Cruzamento entre duas moedas não-base também ensina
- **WHEN** um usuário de base real registra uma transferência entre uma conta em dólar e uma conta em euro
- **THEN** a observação do par dólar/euro é registrada, e passa a poder ser usada como caminho de conversão

#### Scenario: Taxa do usuário prevalece
- **WHEN** existe uma taxa derivada de operação e o usuário cadastra outra para o mesmo par e a mesma data
- **THEN** a do usuário é a usada nas conversões daquela data

#### Scenario: A colheita continua acontecendo com a fonte remota ativa
- **WHEN** o usuário registra uma operação cruzada num par que a sincronização já cobre
- **THEN** a observação derivada é registrada assim mesmo, e permanece disponível para quando a remota não alcançar aquela data

#### Scenario: A operação continua sem taxa
- **WHEN** a operação que originou a taxa é inspecionada
- **THEN** ela não possui campo de taxa, e a taxa registrada é uma linha do acervo

#### Scenario: A taxa sobrevive à operação que a originou
- **WHEN** o usuário apaga a operação cruzada que colheu uma taxa
- **THEN** a taxa permanece no acervo, e as figuras do período que dependiam dela não mudam

#### Scenario: Uma taxa colhida por engano pode ser removida
- **WHEN** o usuário remove, na tela de taxas, uma taxa colhida de uma operação que ele já apagou
- **THEN** ela deixa de existir, e as figuras que dependiam dela passam a exibir a parcela daquela moeda como termo próprio

### Requirement: A conversão entre duas moedas tem uma resolução declarada e determinística

Havendo mais de um caminho entre duas moedas no acervo, o sistema SHALL resolver a conversão por uma precedência declarada, e MUST NOT deixar o resultado depender da ordem em que as linhas foram lidas. A mesma pergunta, sobre o mesmo acervo e a mesma data, SHALL ter sempre a mesma resposta.

A precedência SHALL ser, nesta ordem: a observação **direta** do par; a observação **inversa**, lida ao contrário; e **uma** triangulação por moeda pivô. Dentro de cada nível SHALL valer a política que governa o acervo — a última observação em ou antes da data, com a precedência por origem desempatando as de mesma data.

A inversa SHALL preceder a triangulação porque é a **mesma** observação lida ao contrário, enquanto a triangulação são duas outras: preferir a triangulação seria preferir mais fontes de erro a menos.

A resolução MUST NOT compor mais de uma triangulação. Um caminho de dois ou mais saltos compõe arredondamentos e observações independentes num número que nenhuma tela consegue explicar, e é sempre sintoma de um acervo em que falta a observação óbvia — caso em que a resposta correta é não haver taxa, e a figura degradar em termo próprio, e não o sistema inventar um caminho longo.

Havendo mais de um pivô possível, a escolha SHALL ser determinística: SHALL vencer o pivô cujas duas pernas tenham as datas mais recentes, e o empate SHALL ser resolvido por um critério total e estável sobre o código da moeda.

Uma taxa que o acervo **implica** em vez de conter — a inversa, ou o produto de uma triangulação — MUST NOT declarar uma origem que não seja a das observações que a produziram. Ela SHALL declarar a origem da observação lida quando há uma só, e a **mais fraca** das duas numa triangulação, o que é bem definido porque a precedência por origem é uma ordem total. Ela MUST NOT ser gravável no acervo.

Não havendo caminho algum, o sistema MUST NOT tratar a taxa como `1`, MUST NOT omitir a parcela e MUST NOT falhar: a parcela SHALL permanecer como termo próprio, que é o comportamento já exigido para uma taxa ausente.

#### Scenario: A observação direta vence a triangulação
- **WHEN** existem a observação direta de um par e também um caminho por pivô que dá resultado diferente
- **THEN** a conversão usa a observação direta

#### Scenario: A inversa vence a triangulação
- **WHEN** não existe a observação direta de um par, mas existem a inversa e um caminho por pivô
- **THEN** a conversão usa a inversa da observação existente

#### Scenario: Uma triangulação resolve o que a troca de base deixou implícito
- **WHEN** a base passa a ser o dólar e o acervo só contém observações do euro e do dólar contra o real
- **THEN** o euro contra o dólar é resolvido por uma triangulação sobre o real, sem que nenhuma linha seja criada ou alterada

#### Scenario: Dois saltos não são compostos
- **WHEN** alcançar uma moeda exigiria encadear duas triangulações
- **THEN** não há taxa, e a parcela correspondente permanece como termo próprio da sua moeda

#### Scenario: A escolha do pivô é determinística
- **WHEN** dois pivôs distintos poderiam resolver a mesma conversão
- **THEN** o escolhido é sempre o mesmo, qualquer que seja a ordem em que o acervo foi lido

#### Scenario: A inversa conserva a origem da observação
- **WHEN** a conversão é resolvida pela inversa de uma observação obtida da fonte remota
- **THEN** a resposta declara aquela origem, e não outra

#### Scenario: A triangulação declara a origem mais fraca
- **WHEN** a conversão é resolvida por um pivô cujas pernas têm origens diferentes
- **THEN** a resposta declara a mais fraca das duas

## REMOVED Requirements

### Requirement: A tela de taxas agrupa as observações pela moeda contraparte

**Reason**: O requisito nomeava um critério único de agrupamento para uma tela que passou a
ser **duas**, e o critério deixou de servir às duas. Ele foi partido nos dois requisitos que
o substituem: a visão em vigor continua agrupando pela moeda contraparte, pela razão
original e intacta; o histórico passa a agrupar por data, porque com a manutenção automática
gravando uma linha por par por dia a contraparte colapsa o acervo ordinário num grupo único
de centenas de linhas.

**Migration**: Nada se afrouxa. As duas garantias que o requisito realmente protegia — cada
linha se descrever por inteiro, e nenhuma linha ser exibida invertida em relação à
observação que a originou — são reafirmadas literalmente nos dois requisitos que o
substituem.
