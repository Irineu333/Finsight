# currency-consolidation Specification

## Purpose

Como figuras de mais de uma moeda se leem como uma só. A moeda base é preferência de exibição e nunca fato contábil: ela reduz a uma figura o que o razão devolve em várias, e não aparece onde não houve o que reconciliar. A taxa é dado local, datado e de direção única, colhida das próprias operações que atravessam moedas e corrigível pelo usuário — nenhuma leitura do app espera rede. Consolida-se até onde a taxa permitir, jamais inventando um número: o que não pôde ser convertido permanece como termo próprio, e toda figura carrega, indissociável dela, se é exata ou aproximada. A conversão vive aqui, acima do razão, que não a conhece.

## Requirements

### Requirement: A moeda base é preferência de exibição, não fato contábil

O sistema SHALL manter uma **moeda base** do usuário, usada exclusivamente para reduzir a uma única figura os valores que o razão devolve em **mais de uma** moeda. Um usuário cujas contas estejam todas numa mesma moeda MUST NOT ter figura alguma expressa na base quando esta difere daquela — para ele, a base permanece sem uso. Ela SHALL ser preferência de exibição, e MUST NOT ser propriedade de conta, de entry, de transação ou de qualquer dado do razão.

**A troca da moeda base não é oferecida, e o que segue é requisito sobre o desenho e não sobre um fluxo.** O sistema MUST NOT oferecer caminho para trocá-la: ela é semeada uma vez e permanece. O que estas duas frases exigem é que trocá-la **permaneça derivação** — que nada seja gravado hoje que impeça oferecê-la depois —, e a verificação delas SHALL ser feita sobre o dado e sobre a redução, e não sobre uma tela. Um setter que existisse sem o resto seria pior do que a ausência dele: escreveria o código novo deixando todo o acervo de taxas sendo lido contra uma base em que nenhuma delas foi medida.

Trocada a moeda base, dado algum já gravado MUST NOT ser alterado, e migração ou reprocessamento MUST NOT ser exigido: as figuras consolidadas SHALL ser recalculadas na leitura seguinte, retroativamente e por inteiro. Nenhum valor convertido SHALL ser persistido — é o que sustenta a frase acima, e é verificável hoje.

Trocada a moeda base, o acervo de taxas MUST NOT ser invalidado. A taxa da base anterior contra a nova SHALL ser a inversa da que já existe, e as demais SHALL ser re-expressas por triangulação sobre as taxas de mesma data. Isso é derivação, não migração: nenhuma linha gravada muda. A derivação SHALL ser verificável sobre o acervo, sem que a troca precise ser exercida.

A moeda base SHALL ser resolvida, na primeira execução, a partir do **locale do dispositivo**. Quando a moeda do locale não pertencer ao conjunto oferecido, o sistema SHALL recair numa moeda declarada — que é último recurso, e MUST NOT ser tratada como padrão do produto.

Ela SHALL ser resolvida **uma única vez** e persistida. Uma alteração posterior do locale do dispositivo MUST NOT alterá-la: mudaria em silêncio toda figura consolidada do histórico por causa de uma viagem.

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

#### Scenario: A troca não é oferecida
- **WHEN** a interface da preferência de moeda base é inspecionada
- **THEN** ela não expõe forma de escrevê-la, e nenhuma tela oferece a troca

#### Scenario: Trocar a base seria imediato e retroativo
- **WHEN** a base em vigor é substituída por outra e as figuras consolidadas são recalculadas
- **THEN** todas passam a ser expressas na nova, incluindo as de períodos passados, sem migração e sem que nenhuma entry mude — porque nenhuma delas foi lida de valor convertido gravado

#### Scenario: O acervo de taxas sobrevive à troca
- **WHEN** existem taxas cadastradas contra a base em vigor e se pergunta o que elas valeriam contra outra
- **THEN** a da base atual contra a nova é a inversa da que já existe, as demais saem por triangulação sobre as taxas de mesma data, e nenhuma linha gravada é alterada

#### Scenario: Nenhum valor convertido persistido
- **WHEN** os dados gravados são inspecionados
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

### Requirement: A taxa é local, datada e única por moeda

O sistema SHALL manter um histórico de taxas de conversão por moeda **para a moeda base**, cada taxa com a sua **data** e a sua **origem**. O sistema MUST NOT manter uma matriz de pares: só se converte para a base, e uma conversão cruzada, se necessária, SHALL ser derivada das taxas para a base.

A **direção** SHALL ser fixa e única: a taxa expressa o número de unidades da moeda base por **uma** unidade da moeda denominada. Um histórico em que a direção varie por linha é um histórico sem autoridade, porque a inversa não carrega a mesma decisão de arredondamento.

O valor gravado SHALL ser o **quociente em precisão plena** entre os dois valores da operação que o originou, e MUST NOT ser a forma exibida, arredondada. O número de casas decimais apresentado ao usuário é decisão de formatação e SHALL existir apenas na apresentação: gravar o valor exibido tornaria cada exibição uma perda de precisão acumulável. O arredondamento aplicado ao converter SHALL ter um único dono, na redução que produz a figura consolidada.

#### Scenario: A taxa gravada não é a taxa exibida
- **WHEN** uma operação entre moedas produz um quociente com mais casas do que a tela apresenta
- **THEN** o histórico guarda o quociente pleno, a tela apresenta a forma arredondada, e nenhuma conversão posterior usa a forma arredondada

A consolidação de uma figura referente a um instante ou período SHALL usar **a última taxa em ou antes daquela data**. Uma figura de um período passado MUST NOT ser recalculada à taxa corrente: o passado não SHALL se mover sozinho quando a taxa muda.

A taxa gravada localmente SHALL ser a única autoridade usada em qualquer conversão. O usuário SHALL poder cadastrar, corrigir **e remover** taxas a qualquer momento, e uma taxa informada pelo usuário SHALL prevalecer sobre uma derivada de operação na mesma data.

A remoção SHALL existir como consequência de a taxa sobreviver à operação que a originou: sem ela, uma taxa colhida de uma operação que o usuário apagou permanece sem caminho que a alcance. Removida a única taxa de uma moeda, as figuras que dependiam dela SHALL voltar a exibir o termo próprio daquela moeda, em vez de um valor convertido por uma taxa que ninguém mais sustenta.

Uma fonte externa MAY oferecer um valor **sugerido** dentro da tela que edita a taxa, e MUST NOT ser consultada em nenhum outro ponto. Nenhuma leitura, tela ou figura do app SHALL depender de rede, apresentar estado de carregamento ou falhar por indisponibilidade em razão de conversão de moeda.

O sistema SHALL apresentar, junto de onde as taxas são editadas, a data e a origem de cada uma. A origem SHALL distinguir a taxa **colhida de uma operação** da **informada pelo usuário**.

Uma taxa cuja data seja anterior a **30 dias** SHALL ser sinalizada como desatualizada. A sinalização MUST NOT ser expressa apenas por cor: ela SHALL incluir um significante textual, pela mesma razão que a marca de aproximação o inclui. A data SHALL ser exibida esteja a taxa desatualizada ou não.

O limiar não é derivável do domínio — é opinião sobre volatilidade. Sinalizar, em vez de apenas exibir a data, existe porque a consequência de uma taxa velha é uma figura de um período passado exibida errada, e essa consequência não é visível da tela em que o usuário está.

#### Scenario: Conversão usa a taxa local
- **WHEN** uma figura consolidada é calculada
- **THEN** ela usa a taxa gravada localmente, sem consultar nenhuma fonte externa

#### Scenario: Figura de período passado usa a taxa da época
- **WHEN** o patrimônio de um mês passado é consolidado e existem taxas anteriores e posteriores àquele mês
- **THEN** a conversão usa a última taxa em ou antes daquela data, e o valor não muda quando uma taxa mais recente é cadastrada

#### Scenario: Sugestão só na edição
- **WHEN** o usuário abre a tela de edição de uma taxa
- **THEN** um valor sugerido pode ser oferecido, e ele só passa a valer se o usuário o confirmar

#### Scenario: App inteiro funciona offline
- **WHEN** o dispositivo está sem rede e qualquer tela com figura consolidada é aberta
- **THEN** a figura é exibida normalmente, sem erro e sem estado de carregamento

#### Scenario: Data e origem visíveis
- **WHEN** o usuário abre a tela de taxas
- **THEN** cada taxa exibe a sua data e se veio de uma operação ou do próprio usuário

#### Scenario: Taxa desatualizada é sinalizada
- **WHEN** uma taxa foi definida há mais de 30 dias
- **THEN** ela é sinalizada como desatualizada, com um significante textual e não apenas por cor

#### Scenario: Taxa recente exibe a data assim mesmo
- **WHEN** uma taxa foi definida hoje
- **THEN** a sua data é exibida, sem sinalização de desatualizada

#### Scenario: Uma taxa por moeda e data
- **WHEN** as taxas gravadas são inspecionadas com três moedas em uso
- **THEN** existem taxas apenas das duas moedas não-base contra a base, e nenhuma taxa entre duas moedas não-base

### Requirement: Uma operação que atravessa moedas cadastra a sua própria taxa

Quando uma operação atravessa moedas, o sistema SHALL registrar no histórico a taxa que ela aplica, derivada dos valores das suas duas pontas, com a **data da operação** e com a origem que a identifica como derivada de operação.

O usuário MUST NOT precisar informar uma taxa que já está implícita numa operação que ele registrou. Uma taxa informada pelo usuário para a mesma data SHALL prevalecer sobre a derivada.

Isso MUST NOT ser confundido com persistir a taxa **na** operação, o que `balanced-ledger` proíbe: o que se grava é uma linha do histórico de taxas, dado próprio desta capability, e a operação permanece sem campo de taxa.

#### Scenario: Câmbio alimenta o histórico
- **WHEN** o usuário registra uma transferência de R$ 550 para US$ 100 numa data
- **THEN** uma taxa de 5,50 para o dólar é registrada naquela data, com origem de operação

#### Scenario: Taxa do usuário prevalece
- **WHEN** existe uma taxa derivada de operação e o usuário cadastra outra para a mesma data
- **THEN** a do usuário é a usada nas conversões daquela data

#### Scenario: A operação continua sem taxa
- **WHEN** a operação que originou a taxa é inspecionada
- **THEN** ela não possui campo de taxa, e a taxa registrada é uma linha do histórico

#### Scenario: A taxa sobrevive à operação que a originou
- **WHEN** o usuário apaga a operação cruzada que colheu uma taxa
- **THEN** a taxa permanece no histórico, e as figuras do período que dependiam dela não mudam

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

### Requirement: O conjunto de moedas oferecidas é curado e de duas casas decimais

O sistema SHALL oferecer ao usuário um conjunto curado de moedas, restrito às de **duas** casas decimais. O catálogo dessas moedas SHALL pertencer a esta camada, e MUST NOT ser conhecido pelo razão, que persiste apenas o código da moeda.

Essa restrição SHALL ser uma premissa registrada, e não um esquecimento: a aritmética de menor unidade do sistema assume base 100 na escrita e na leitura, e admitir moeda de zero ou três casas exige refazer essa fronteira.

#### Scenario: Seletor oferece apenas moedas de duas casas
- **WHEN** o usuário escolhe a moeda de uma conta
- **THEN** apenas moedas de duas casas decimais são oferecidas

#### Scenario: Razão não conhece o catálogo
- **WHEN** o razão é inspecionado
- **THEN** ele persiste o código da moeda sem conhecer quais moedas o app oferece
