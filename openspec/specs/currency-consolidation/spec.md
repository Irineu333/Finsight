# currency-consolidation Specification

## Purpose

Como figuras de mais de uma moeda se leem como uma só. A moeda base é preferência de exibição e nunca fato contábil: ela reduz a uma figura o que o razão devolve em várias, e não aparece onde não houve o que reconciliar. A taxa é dado local, datado e de direção única, colhida das próprias operações que atravessam moedas e corrigível pelo usuário — nenhuma leitura do app espera rede. Consolida-se até onde a taxa permitir, jamais inventando um número: o que não pôde ser convertido permanece como termo próprio, e toda figura carrega, indissociável dela, se é exata ou aproximada. A conversão vive aqui, acima do razão, que não a conhece.
## Requirements
### Requirement: A moeda base é preferência de exibição, não fato contábil

O sistema SHALL manter uma **moeda base** do usuário, usada exclusivamente para reduzir a uma única figura os valores que o razão devolve em **mais de uma** moeda. Um usuário cujas contas estejam todas numa mesma moeda MUST NOT ter figura alguma expressa na base quando esta difere daquela — para ele, a base permanece sem uso. Ela SHALL ser preferência de exibição, e MUST NOT ser propriedade de conta, de entry, de transação ou de qualquer dado do razão.

**A troca da moeda base SHALL ser oferecida**, e SHALL ser a escrita de uma preferência e nada além disso. Trocá-la MUST NOT alterar linha alguma já gravada, MUST NOT exigir migração e MUST NOT exigir reprocessamento: as figuras consolidadas SHALL ser recalculadas na leitura seguinte, retroativamente e por inteiro. Nenhum valor convertido SHALL ser persistido — é o que sustenta a frase anterior.

O que torna a troca uma escrita simples é o acervo de taxas dizer, em cada linha, sobre que par de moedas ela é uma observação. Uma linha cuja denominação dependesse da preferência em vigor passaria a ser lida contra uma base em que não foi medida no instante da troca — todo número consolidado do histórico errado, sem erro, sem marca e sem forma de o usuário perceber.

A troca SHALL oferecer, por inteiro, o conjunto de moedas que o app oferece — as linhas não arquivadas do registro definido em `currency-registry`. Ela MUST NOT ser condicionada à existência de taxa que alcance a moeda escolhida, MUST NOT exigir o cadastro de taxa no seu próprio fluxo e MUST NOT ser recusada por o acervo não a alcançar. Quando o acervo não alcançar a base escolhida, as figuras SHALL degradar em termos por moeda — que é o comportamento já exigido para uma taxa ausente — e o usuário SHALL poder cadastrar as taxas depois, a qualquer momento.

A moeda base MUST NOT ser arquivada. Arquivá-la deixaria toda figura consolidada denominada numa moeda que o app declara não oferecer mais, e o arquivamento é regra de oferta e nunca de invalidação. A tentativa SHALL ser recusada com o motivo, e trocar a base SHALL ser o caminho para arquivar a que era base.

A moeda base SHALL ser resolvida, na primeira execução, a partir do **locale do dispositivo**. A moeda que o locale indica é gravada pela semeadura de `currency-registry`, então ela pertence ao conjunto oferecido por construção. O sistema SHALL recair numa moeda declarada apenas quando o dispositivo não nomear moeda alguma, ou quando a que ele nomeia não tiver duas casas decimais — casos em que não há linha a semear. Essa moeda declarada é último recurso, e MUST NOT ser tratada como padrão do produto.

Ela SHALL ser resolvida **uma única vez** e persistida. Uma alteração posterior do locale do dispositivo MUST NOT alterá-la: mudaria em silêncio toda figura consolidada do histórico por causa de uma viagem. Só uma escolha explícita do usuário SHALL movê-la.

A moeda base MUST NOT ser derivada de nenhuma conta. Derivá-la da conta padrão a tornaria propriedade de uma conta, o que este requisito proíbe, e a faria mudar quando a conta padrão mudasse.

O razão MUST NOT prover valor padrão para a moeda de uma conta. Não existe, portanto, "a moeda que uma conta nova recebe quando nenhuma é escolhida": toda conta tem a sua moeda decidida por quem a cria, e a moeda base serve apenas como **pré-seleção** oferecida no formulário.

#### Scenario: Primeira execução resolve a base pelo locale
- **WHEN** o app é aberto pela primeira vez num dispositivo cujo locale indica euro
- **THEN** a moeda base passa a ser o euro, e os totais consolidados são expressos em euro

#### Scenario: Locale de moeda fora da semente resolve nela assim mesmo
- **WHEN** o app é aberto pela primeira vez num dispositivo cujo locale indica uma moeda de duas casas que não pertence à semente
- **THEN** a semeadura grava essa moeda, e a base resolve nela em vez de recair no último recurso

#### Scenario: Locale sem moeda utilizável recai na declarada
- **WHEN** o dispositivo não nomeia moeda alguma, ou nomeia uma que não tem duas casas decimais
- **THEN** a moeda base recai na moeda declarada como último recurso

#### Scenario: Trocar o locale depois não move o histórico
- **WHEN** o usuário troca o locale do dispositivo depois de a moeda base já estar resolvida
- **THEN** a moeda base permanece a mesma, e nenhuma figura consolidada muda

#### Scenario: A troca é oferecida sobre o conjunto oferecido inteiro
- **WHEN** o usuário abre a preferência de moeda base
- **THEN** todas as moedas não arquivadas do registro são oferecidas, inclusive as que nenhuma taxa alcança

#### Scenario: A moeda base não pode ser arquivada
- **WHEN** o usuário tenta arquivar a moeda que está em vigor como base
- **THEN** a ação é recusada com o motivo, e a moeda permanece oferecida

#### Scenario: Trocar a base é imediato e retroativo
- **WHEN** a base em vigor é substituída por outra
- **THEN** todas as figuras consolidadas passam a ser expressas na nova, incluindo as de períodos passados, sem migração e sem que nenhuma entry mude — porque nenhuma delas foi lida de valor convertido gravado

#### Scenario: Trocar a base não grava nada além da preferência
- **WHEN** a base em vigor é substituída por outra e o acervo de taxas é inspecionado
- **THEN** nenhuma linha do acervo foi criada, alterada ou removida

#### Scenario: O acervo sobrevive à troca
- **WHEN** existem taxas cadastradas antes da troca e figuras do período são consolidadas depois dela
- **THEN** elas continuam a ser usadas, lidas na direção que cada uma declara, sem que nenhuma linha gravada mude

#### Scenario: Trocar para moeda que o acervo não alcança não é impedido
- **WHEN** o usuário escolhe como base uma moeda que nenhuma taxa do acervo alcança
- **THEN** a troca acontece, nenhuma taxa é exigida no fluxo, e as figuras consolidadas passam a exibir cada moeda como termo próprio até que uma taxa seja cadastrada

#### Scenario: A troca é reversível
- **WHEN** o usuário troca a base e em seguida volta à anterior
- **THEN** todas as figuras voltam a ser exatamente as de antes, porque nada foi convertido, gravado ou perdido

#### Scenario: Nenhum valor convertido persistido
- **WHEN** o dado gravado é inspecionado
- **THEN** não existe coluna, campo ou tabela guardando valor convertido para a moeda base

### Requirement: A conversão acontece fora do razão

A conversão de um resultado por moeda numa figura na base SHALL acontecer acima do razão, consumindo o que ele devolve. O razão MUST NOT ser consultado com uma moeda base, MUST NOT receber taxa por parâmetro e MUST NOT ter dependência que forneça taxa.

A camada de consolidação SHALL ter dono único: a redução de um resultado por moeda a uma figura na base SHALL ter exatamente uma implementação, consumida por toda feature que exiba figura consolidada. Nenhuma tela, ViewModel ou modelo de UI SHALL converter por conta própria.

A responsabilidade desta camada SHALL ser a **conversão entre moedas**, e apenas ela. Combinar dois resultados por moeda — somar perímetros disjuntos, por exemplo — MUST NOT pertencer a esta camada: é aritmética sobre saldos, e o seu dono é o razão (`ledger-reporting`).

#### Scenario: Dashboard consome a consolidação
- **WHEN** o dashboard exibe o patrimônio total
- **THEN** ele obtém o resultado por moeda do razão e o reduz pela única implementação de consolidação

#### Scenario: Nenhuma conversão em linha
- **WHEN** o código é inspecionado
- **THEN** não existe multiplicação por taxa em tela, ViewModel ou modelo de UI

#### Scenario: Somar perímetros não é consolidar
- **WHEN** a interface da camada de consolidação é inspecionada
- **THEN** ela não expõe soma de dois resultados por moeda

### Requirement: Uma operação que atravessa moedas cadastra a sua própria taxa

Quando uma operação atravessa moedas, o sistema SHALL registrar no acervo a taxa que ela aplica, derivada dos valores das suas duas pontas, com a **data da operação**, com o **par** que ela observou e com a origem que a identifica como derivada de operação.

O par observado SHALL ser o das duas moedas da operação. O cadastro MUST NOT ser condicionado a que uma das pontas seja a moeda base: uma operação entre duas moedas não-base é uma observação tão boa quanto qualquer outra, e descartá-la seria jogar fora informação que o usuário já forneceu.

O usuário MUST NOT precisar informar uma taxa que já está implícita numa operação que ele registrou. Uma taxa informada pelo usuário para o mesmo par e a mesma data SHALL prevalecer sobre a derivada.

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

#### Scenario: A operação continua sem taxa
- **WHEN** a operação que originou a taxa é inspecionada
- **THEN** ela não possui campo de taxa, e a taxa registrada é uma linha do acervo

#### Scenario: A taxa sobrevive à operação que a originou
- **WHEN** o usuário apaga a operação cruzada que colheu uma taxa
- **THEN** a taxa permanece no acervo, e as figuras do período que dependiam dela não mudam

#### Scenario: Uma taxa colhida por engano pode ser removida
- **WHEN** o usuário remove, na tela de taxas, uma taxa colhida de uma operação que ele já apagou
- **THEN** ela deixa de existir, e as figuras que dependiam dela passam a exibir a parcela daquela moeda como termo próprio

### Requirement: Consolida-se até onde a taxa permitir, e nunca se inventa um valor

A consolidação SHALL ocorrer **apenas quando houver mais de uma moeda a reconciliar**. Um resultado com uma única moeda SHALL ser entregue naquela moeda, exato, e MUST NOT ser convertido à base — nem quando a base difere dela e a taxa é conhecida. Converter ali troca um valor exato por um aproximado sem reconciliar nada.

Quando houver duas ou mais moedas, a consolidação SHALL reduzir o resultado **até onde as taxas disponíveis permitirem**, produzindo uma figura composta de um ou mais termos: um termo na moeda base com tudo o que pôde ser convertido, e um termo próprio para cada moeda cuja taxa é desconhecida.

Uma taxa ausente MUST NOT ser tratada como `1`, MUST NOT fazer a parcela correspondente ser omitida da figura, e MUST NOT impedir a exibição das parcelas que puderam ser convertidas. O estado em que uma conta em moeda não-base existe e a sua taxa ainda não foi cadastrada é alcançável por construção, e SHALL ter comportamento definido.

A consolidação SHALL produzir, junto de cada figura, se ela é exata ou aproximada, conforme a regra definida em `money-display`. Uma figura MUST NOT ser entregue a um consumidor sem essa informação. Um resultado que não exigiu conversão alguma SHALL ser entregue como **exato**, e a consolidação MUST NOT marcá-lo como aproximado por ter passado por ela.

#### Scenario: Consolidação de uma moeda igual à base
- **WHEN** um resultado contendo apenas a moeda base é consolidado
- **THEN** a figura resultante tem um termo, é exata, e o seu valor é idêntico ao devolvido pelo razão

#### Scenario: Consolidação de duas moedas com taxa conhecida
- **WHEN** um resultado contendo duas moedas é consolidado e ambas as taxas são conhecidas
- **THEN** a figura resultante tem um termo na moeda base, é aproximada, e o seu valor é a soma das parcelas convertidas

#### Scenario: Consolidação parcial sem taxa
- **WHEN** um resultado contendo R$ 100,00 e US$ 50,00 é consolidado sem taxa cadastrada para o dólar
- **THEN** a figura resultante tem dois termos — R$ 100,00 e US$ 50,00 — e nenhuma parcela é omitida nem convertida a uma taxa presumida

#### Scenario: Moeda única permanece na sua moeda, com ou sem taxa
- **WHEN** um resultado contendo apenas dólares é consolidado com a base em real, exista ou não taxa cadastrada para o dólar
- **THEN** a figura resultante tem um termo em dólar e é exata, porque não havia mais de uma moeda a reconciliar

#### Scenario: Usuário sem nenhuma conta na base
- **WHEN** todas as contas e cartões estão em dólar e a base, resolvida pelo locale, é o real
- **THEN** nenhuma figura do app é convertida, e a moeda base não é usada em lugar algum

#### Scenario: Exatidão não é opcional
- **WHEN** a interface da camada de consolidação é inspecionada
- **THEN** não existe forma de obter uma figura consolidada sem a sua exatidão

### Requirement: O limite de um orçamento carrega a moeda escolhida na sua criação

O limite de um orçamento SHALL ser denominado, e a sua denominação SHALL derivar das **contas cadastradas**. Ela MUST NOT herdar a moeda base: a base responde em que moeda o usuário **lê totais**, e não em que moeda ele **gasta**.

A escolha SHALL ser oferecida apenas quando houver escolha a fazer, decidida pelo número de moedas distintas entre as contas cadastradas:

- **uma moeda** → o formulário MUST NOT exibir controle de moeda, e o limite SHALL assumir a moeda da conta padrão. É a única resposta possível, e não um valor padrão silencioso;
- **mais de uma moeda** → o formulário SHALL oferecer a escolha, pré-selecionada com a moeda da conta padrão.

Para um usuário de uma moeda só, o formulário de orçamento SHALL permanecer idêntico ao que era antes desta mudança.

A denominação escolhida MUST NOT ser alterada depois: reinterpretar um limite gravado reescreve em silêncio o significado de um número que o usuário digitou. Alterar a denominação SHALL exigir outro orçamento.

O progresso de um orçamento SHALL ser o gasto da sua categoria reduzido à **moeda do limite**, e SHALL ser exato quando nenhuma conversão participar dele.

A regra existe para que o custo do multimoeda seja pago apenas por quem mistura moedas. Herdar a base cobraria esse custo de um usuário com **todas** as contas numa moeda diferente da base — que por toda leitura vê figuras exatas —, pedindo-lhe um limite na moeda base e comparando moedas distintas na barra de progresso.

Um orçamento não nomeia **uma** conta, então a sua denominação MUST NOT ser derivada de uma conta específica no momento da leitura — ela é fixada na criação. O que as contas cadastradas fornecem é apenas *o que oferecer* e *o que sugerir* naquele instante.

#### Scenario: Usuário de moeda única não vê controle nem é cobrado pela base
- **WHEN** todas as contas estão em dólar, a base é o real, e o usuário cria um orçamento
- **THEN** nenhum controle de moeda é exibido, o limite nasce em dólar pela moeda da conta padrão, e o progresso é exato

#### Scenario: Usuário com várias moedas escolhe
- **WHEN** existem contas em real e em dólar e o usuário cria um orçamento
- **THEN** a escolha de moeda é oferecida, pré-selecionada com a moeda da conta padrão, e o progresso é aproximado quando a categoria tem gasto em outra moeda

#### Scenario: A sugestão não vem da base
- **WHEN** a moeda da conta padrão difere da moeda base e o usuário cria um orçamento com mais de uma moeda cadastrada
- **THEN** a pré-seleção é a da conta padrão, e não a base

#### Scenario: Progresso exato quando não houve conversão
- **WHEN** o gasto de uma categoria está inteiramente na moeda do limite do seu orçamento
- **THEN** o progresso é exato e não recebe marca de aproximação

#### Scenario: Denominação não muda depois
- **WHEN** o usuário edita um orçamento existente
- **THEN** a moeda do limite é apresentada travada, e alterá-la exige criar outro orçamento

#### Scenario: Limite não herda a base
- **WHEN** a moeda base muda e existem orçamentos gravados
- **THEN** o limite de cada um permanece na moeda em que foi criado, e o seu valor não é reinterpretado

### Requirement: A taxa é uma observação local e datada sobre um par de moedas

O sistema SHALL manter um acervo de taxas em que cada linha é uma observação sobre **um par de moedas**, com a sua **data** e a sua **origem**. Cada linha SHALL declarar as suas duas pontas, de modo a se ler sozinha — *uma unidade da moeda precificada vale tanto da moeda contraparte* — sem depender de qual preferência de exibição está em vigor.

O acervo MUST NOT conter vestígio da moeda base. Uma linha cujo significado dependesse da preferência em vigor passaria a dizer outra coisa no instante em que ela mudasse, o que é reescrever em silêncio o significado de um dado gravado.

A **direção** de uma linha SHALL ser a da observação que a originou, e MUST NOT ser canonicalizada na gravação. Ordenar as pontas e inverter o quociente para caber numa forma canônica gravaria um número que ninguém observou — o mesmo defeito que gravar a forma exibida produz, aplicado à entrada. Como consequência, o mesmo par PODE existir nos dois sentidos, como duas observações distintas.

O valor gravado SHALL ser o **quociente em precisão plena** entre os dois valores da operação que o originou, e MUST NOT ser a forma exibida, arredondada. O número de casas decimais apresentado ao usuário é decisão de formatação e SHALL existir apenas na apresentação. O arredondamento aplicado ao converter SHALL ter um único dono, na redução que produz a figura consolidada.

A consolidação de uma figura referente a um instante ou período SHALL usar **a última observação em ou antes daquela data**. Uma figura de um período passado MUST NOT ser recalculada à taxa corrente: o passado não SHALL se mover sozinho quando uma taxa muda.

O acervo local SHALL ser a única autoridade usada em qualquer conversão. O usuário SHALL poder cadastrar, corrigir **e remover** taxas a qualquer momento, escolhendo as duas pontas do par, e uma taxa informada pelo usuário SHALL prevalecer sobre uma derivada de operação para o mesmo par e a mesma data.

A remoção SHALL existir como consequência de a taxa sobreviver à operação que a originou. Removida a última observação que alcançava uma moeda, as figuras que dependiam dela SHALL voltar a exibir o termo próprio daquela moeda, em vez de um valor convertido por uma taxa que ninguém mais sustenta.

Uma observação MUST NOT sobreviver à moeda de que ela fala. Apagar uma moeda, na forma que `currency-registry` define, SHALL remover toda observação que a nomeie em qualquer das duas pontas. O acervo é lido pela resolução da conversão sem consulta ao conjunto de moedas oferecidas, então uma observação órfã continuaria a ser caminho de conversão — e a produzir figuras trianguladas por uma moeda que não existe em lugar nenhum da interface. **Arquivar** uma moeda, ao contrário, MUST NOT remover observação alguma: as observações continuam válidas e continuam a ser lidas, porque arquivar é sobre o que se oferece e não sobre o que se sabe.

Uma fonte externa MAY oferecer um valor **sugerido** dentro da tela que edita a taxa, e MUST NOT ser consultada em nenhum outro ponto. Nenhuma leitura, tela ou figura do app SHALL depender de rede, apresentar estado de carregamento ou falhar por indisponibilidade em razão de conversão de moeda.

O sistema SHALL apresentar, junto de onde as taxas são editadas, o par, a data e a origem de cada uma. A origem SHALL distinguir a taxa **colhida de uma operação** da **informada pelo usuário**. Uma taxa cuja data seja anterior a **30 dias** SHALL ser sinalizada como desatualizada, com um significante textual e não apenas por cor. A data SHALL ser exibida esteja a taxa desatualizada ou não.

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

#### Scenario: Apagar a moeda apaga as suas observações
- **WHEN** uma moeda é apagada e o acervo é inspecionado
- **THEN** nenhuma observação que a nomeasse em qualquer das duas pontas permanece

#### Scenario: Arquivar a moeda preserva as suas observações
- **WHEN** uma moeda é arquivada e o acervo é inspecionado
- **THEN** todas as suas observações permanecem, e continuam a ser lidas nas conversões

#### Scenario: Sugestão só na edição
- **WHEN** o usuário abre a tela de edição de uma taxa
- **THEN** um valor sugerido pode ser oferecido, e ele só passa a valer se o usuário o confirmar

#### Scenario: App inteiro funciona offline
- **WHEN** o dispositivo está sem rede e qualquer tela com figura consolidada é aberta
- **THEN** a figura é exibida normalmente, sem erro e sem estado de carregamento

#### Scenario: Par, data e origem visíveis
- **WHEN** o usuário abre a tela de taxas
- **THEN** cada linha exibe o seu par, a sua data e se veio de uma operação ou do próprio usuário

#### Scenario: Taxa desatualizada é sinalizada
- **WHEN** uma taxa foi definida há mais de 30 dias
- **THEN** ela é sinalizada como desatualizada, com um significante textual e não apenas por cor

#### Scenario: Taxa recente exibe a data assim mesmo
- **WHEN** uma taxa foi definida hoje
- **THEN** a sua data é exibida, sem sinalização de desatualizada

### Requirement: A conversão entre duas moedas tem uma resolução declarada e determinística

Havendo mais de um caminho entre duas moedas no acervo, o sistema SHALL resolver a conversão por uma precedência declarada, e MUST NOT deixar o resultado depender da ordem em que as linhas foram lidas. A mesma pergunta, sobre o mesmo acervo e a mesma data, SHALL ter sempre a mesma resposta.

A precedência SHALL ser, nesta ordem: a observação **direta** do par; a observação **inversa**, lida ao contrário; e **uma** triangulação por moeda pivô. Dentro de cada nível SHALL valer a política que governa o acervo — a última observação em ou antes da data, com a do usuário prevalecendo sobre a derivada de operação.

A inversa SHALL preceder a triangulação porque é a **mesma** observação lida ao contrário, enquanto a triangulação são duas outras: preferir a triangulação seria preferir mais fontes de erro a menos.

A resolução MUST NOT compor mais de uma triangulação. Um caminho de dois ou mais saltos compõe arredondamentos e observações independentes num número que nenhuma tela consegue explicar, e é sempre sintoma de um acervo em que falta a observação óbvia — caso em que a resposta correta é não haver taxa, e a figura degradar em termo próprio, e não o sistema inventar um caminho longo.

Havendo mais de um pivô possível, a escolha SHALL ser determinística: SHALL vencer o pivô cujas duas pernas tenham as datas mais recentes, e o empate SHALL ser resolvido por um critério total e estável sobre o código da moeda.

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

### Requirement: A tela de taxas agrupa as observações pela moeda contraparte

A tela que lista as taxas SHALL agrupar as observações pela **moeda contraparte** — aquela em que as linhas do grupo estão precificadas. Cada linha SHALL se descrever por inteiro: o par nas duas pontas, o valor, a data e a origem, de modo que o seu significado não dependa do cabeçalho sob o qual ela está.

A escolha da ponta é decidida pelo caso comum, e não pela simetria: no acervo ordinário toda observação é precificada na base em vigor, então agrupar pela moeda **precificada** poria cada linha num grupo de uma só e não agruparia nada. A contraparte é a ponta que de fato reúne, e o cabeçalho passa a ser a frase que o usuário veio ler.

Uma linha MUST NOT ser exibida invertida em relação à observação que a originou. Esta tela é também o ponto de edição, e editar uma linha invertida abriria a correção de um número que ninguém observou.

Como consequência, um mesmo par PODE aparecer em dois grupos, um por sentido, quando foi observado nos dois. São observações distintas e SHALL ser exibidas como tais.

Os grupos SHALL ser ordenados pela observação mais recente de cada moeda.

#### Scenario: O acervo ordinário é um grupo só
- **WHEN** o usuário abre a tela de taxas com observações do dólar, do euro e do iene, todas contra o real
- **THEN** existe um único grupo, o do real, com as três observações datadas

#### Scenario: Uma contraparte fora da base abre o seu próprio grupo
- **WHEN** existe também uma observação do iene contra o dólar
- **THEN** ela aparece num grupo do dólar, separada das que são precificadas em real

#### Scenario: O mesmo par nos dois sentidos aparece em dois grupos
- **WHEN** existem uma observação do dólar contra o real e outra do real contra o dólar
- **THEN** cada uma aparece no grupo da sua moeda contraparte, na direção em que foi observada

#### Scenario: Editar alcança a observação original
- **WHEN** o usuário toca numa linha para corrigi-la
- **THEN** o formulário abre com o par na direção em que a observação foi feita

### Requirement: Toda moeda do sistema tem duas casas decimais

Toda moeda que o sistema admite SHALL ter **duas** casas decimais, seja ela semeada pelo
app ou cadastrada pelo usuário. A restrição SHALL ser aplicada onde uma moeda passa a
existir — a semeadura e o cadastro, definidos em `currency-registry` —, e MUST NOT depender
de curadoria: não há mais uma lista embarcada onde ela pudesse ser exercida por omissão.

Essa restrição SHALL ser uma premissa registrada, e não um esquecimento: a aritmética de
menor unidade do sistema assume base 100 na escrita e na leitura, e admitir moeda de zero ou
três casas exige refazer essa fronteira.

O razão MUST NOT conhecer o conjunto de moedas admitidas. Ele persiste o código da moeda, e
qual conjunto o app oferece é decisão desta camada.

#### Scenario: Moeda de outra base não é admitida
- **WHEN** o usuário tenta cadastrar uma moeda cujo código a plataforma declara ter zero ou três casas decimais
- **THEN** o cadastro é recusado com o motivo, e nenhuma linha é gravada

#### Scenario: A semeadura também respeita a premissa
- **WHEN** o locale do dispositivo indica uma moeda de zero casas decimais
- **THEN** nenhuma linha é semeada para ela, e a moeda base recai no último recurso

#### Scenario: Razão não conhece o conjunto
- **WHEN** o razão é inspecionado
- **THEN** ele persiste o código da moeda sem conhecer quais moedas o app admite

