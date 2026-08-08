## MODIFIED Requirements

### Requirement: A moeda base é preferência de exibição, não fato contábil

O sistema SHALL manter uma **moeda base** do usuário, usada exclusivamente para reduzir a uma única figura os valores que o razão devolve em **mais de uma** moeda. Um usuário cujas contas estejam todas numa mesma moeda MUST NOT ter figura alguma expressa na base quando esta difere daquela — para ele, a base permanece sem uso. Ela SHALL ser preferência de exibição, e MUST NOT ser propriedade de conta, de entry, de transação ou de qualquer dado do razão.

**A troca da moeda base SHALL ser oferecida**, e SHALL ser a escrita de uma preferência e nada além disso. Trocá-la MUST NOT alterar linha alguma já gravada, MUST NOT exigir migração e MUST NOT exigir reprocessamento: as figuras consolidadas SHALL ser recalculadas na leitura seguinte, retroativamente e por inteiro. Nenhum valor convertido SHALL ser persistido — é o que sustenta a frase anterior.

O que torna a troca uma escrita simples é o acervo de taxas dizer, em cada linha, sobre que par de moedas ela é uma observação. Uma linha cuja denominação dependesse da preferência em vigor passaria a ser lida contra uma base em que não foi medida no instante da troca — todo número consolidado do histórico errado, sem erro, sem marca e sem forma de o usuário perceber.

A troca SHALL oferecer o conjunto curado de moedas por inteiro. Ela MUST NOT ser condicionada à existência de taxa que alcance a moeda escolhida, MUST NOT exigir o cadastro de taxa no seu próprio fluxo e MUST NOT ser recusada por o acervo não a alcançar. Quando o acervo não alcançar a base escolhida, as figuras SHALL degradar em termos por moeda — que é o comportamento já exigido para uma taxa ausente — e o usuário SHALL poder cadastrar as taxas depois, a qualquer momento.

A moeda base SHALL ser resolvida, na primeira execução, a partir do **locale do dispositivo**. Quando a moeda do locale não pertencer ao conjunto oferecido, o sistema SHALL recair numa moeda declarada — que é último recurso, e MUST NOT ser tratada como padrão do produto.

Ela SHALL ser resolvida **uma única vez** e persistida. Uma alteração posterior do locale do dispositivo MUST NOT alterá-la: mudaria em silêncio toda figura consolidada do histórico por causa de uma viagem. Só uma escolha explícita do usuário SHALL movê-la.

A moeda base MUST NOT ser derivada de nenhuma conta. Derivá-la da conta padrão a tornaria propriedade de uma conta, o que este requisito proíbe, e a faria mudar quando a conta padrão mudasse.

O razão MUST NOT prover valor padrão para a moeda de uma conta. Não existe, portanto, "a moeda que uma conta nova recebe quando nenhuma é escolhida": toda conta tem a sua moeda decidida por quem a cria, e a moeda base serve apenas como **pré-seleção** oferecida no formulário.

#### Scenario: Primeira execução resolve a base pelo locale
- **WHEN** o app é aberto pela primeira vez num dispositivo cujo locale indica euro
- **THEN** a moeda base passa a ser o euro, e os totais consolidados são expressos em euro

#### Scenario: Locale de moeda não oferecida recai na declarada
- **WHEN** o locale do dispositivo indica uma moeda fora do conjunto oferecido
- **THEN** a moeda base recai na moeda declarada como último recurso

#### Scenario: Trocar o locale depois não move o histórico
- **WHEN** o usuário troca o locale do dispositivo depois de a moeda base já estar resolvida
- **THEN** a moeda base permanece a mesma, e nenhuma figura consolidada muda

#### Scenario: A troca é oferecida sobre o conjunto curado inteiro
- **WHEN** o usuário abre a preferência de moeda base
- **THEN** todas as moedas do conjunto curado são oferecidas, inclusive as que nenhuma taxa alcança

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

## ADDED Requirements

### Requirement: A taxa é uma observação local e datada sobre um par de moedas

O sistema SHALL manter um acervo de taxas em que cada linha é uma observação sobre **um par de moedas**, com a sua **data** e a sua **origem**. Cada linha SHALL declarar as suas duas pontas, de modo a se ler sozinha — *uma unidade da moeda precificada vale tanto da moeda contraparte* — sem depender de qual preferência de exibição está em vigor.

O acervo MUST NOT conter vestígio da moeda base. Uma linha cujo significado dependesse da preferência em vigor passaria a dizer outra coisa no instante em que ela mudasse, o que é reescrever em silêncio o significado de um dado gravado.

A **direção** de uma linha SHALL ser a da observação que a originou, e MUST NOT ser canonicalizada na gravação. Ordenar as pontas e inverter o quociente para caber numa forma canônica gravaria um número que ninguém observou — o mesmo defeito que gravar a forma exibida produz, aplicado à entrada. Como consequência, o mesmo par PODE existir nos dois sentidos, como duas observações distintas.

O valor gravado SHALL ser o **quociente em precisão plena** entre os dois valores da operação que o originou, e MUST NOT ser a forma exibida, arredondada. O número de casas decimais apresentado ao usuário é decisão de formatação e SHALL existir apenas na apresentação. O arredondamento aplicado ao converter SHALL ter um único dono, na redução que produz a figura consolidada.

A consolidação de uma figura referente a um instante ou período SHALL usar **a última observação em ou antes daquela data**. Uma figura de um período passado MUST NOT ser recalculada à taxa corrente: o passado não SHALL se mover sozinho quando uma taxa muda.

O acervo local SHALL ser a única autoridade usada em qualquer conversão. O usuário SHALL poder cadastrar, corrigir **e remover** taxas a qualquer momento, escolhendo as duas pontas do par, e uma taxa informada pelo usuário SHALL prevalecer sobre uma derivada de operação para o mesmo par e a mesma data.

A remoção SHALL existir como consequência de a taxa sobreviver à operação que a originou. Removida a última observação que alcançava uma moeda, as figuras que dependiam dela SHALL voltar a exibir o termo próprio daquela moeda, em vez de um valor convertido por uma taxa que ninguém mais sustenta.

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

## REMOVED Requirements

### Requirement: A taxa é local, datada e única por moeda
**Reason**: O nome e as duas primeiras frases do requisito fixavam o acervo como *"por moeda **para a moeda base**"*, com a proibição explícita de manter pares e com a direção fixa por decreto. As duas regras eram, na verdade, aproximações defensivas do princípio de que a base é preferência de exibição — e eram justamente o que tornava a denominação de cada linha implícita, e a troca da base impossível sem reescrever o acervo. Substituído por "A taxa é uma observação local e datada sobre um par de moedas", que serve o mesmo princípio de forma direta, e por "A conversão entre duas moedas tem uma resolução declarada e determinística", que assume a responsabilidade que a direção fixa vinha cobrindo por omissão.
**Migration**: Tudo o que o requisito exigia e continua valendo foi preservado literalmente no requisito que o substitui: quociente pleno, política da última data em ou antes, autoridade do acervo local, cadastro/correção/remoção pelo usuário, precedência do usuário sobre o derivado, sugestão externa restrita à tela de edição, funcionamento integral offline, exibição de data e origem e sinalização de 30 dias. O que muda é o eixo: "por moeda contra a base" passa a "sobre um par", e "direção fixa e única" passa a "direção da observação, com resolução declarada". O cenário `Uma taxa por moeda e data` — que exigia *"taxas apenas das duas moedas não-base contra a base, e nenhuma taxa entre duas moedas não-base"* — é o único que deixa de valer, e é substituído por `Cruzamento entre duas moedas não-base também ensina`, no requisito do cadastro automático.
