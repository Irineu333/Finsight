## MODIFIED Requirements

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

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: O conjunto de moedas oferecidas é curado e de duas casas decimais

**Reason**: O conjunto oferecido deixa de ser curadoria embarcada do app e passa a ser dado
gravado, estendido pelo usuário. O requisito afirmava duas coisas que agora têm donos
distintos: *quais* moedas são oferecidas passa a ser de `currency-registry`, e *quantas
casas decimais* toda moeda tem permanece aqui, no novo requisito "Toda moeda do sistema tem
duas casas decimais", porque é premissa da aritmética de consolidação e não de curadoria.

**Migration**: A premissa de duas casas decimais e a proibição de o razão conhecer o
conjunto seguem valendo, sem afrouxamento, pelo requisito que o substitui nesta mesma
capability. A definição do conjunto oferecido, da semente e do cadastro passa a ser lida em
`currency-registry`.
