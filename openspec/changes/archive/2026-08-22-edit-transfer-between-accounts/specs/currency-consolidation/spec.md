## MODIFIED Requirements

### Requirement: Uma operação que atravessa moedas cadastra a sua própria taxa

Quando uma operação atravessa moedas, o sistema SHALL registrar no acervo a taxa que ela aplica, derivada dos valores das suas duas pontas, com a **data da operação**, com o **par** que ela observou e com a origem que a identifica como derivada de operação.

O par observado SHALL ser o das duas moedas da operação. O cadastro MUST NOT ser condicionado a que uma das pontas seja a moeda base: uma operação entre duas moedas não-base é uma observação tão boa quanto qualquer outra, e descartá-la seria jogar fora informação que o usuário já forneceu.

O usuário MUST NOT precisar informar uma taxa que já está implícita numa operação que ele registrou. Para o mesmo par e a mesma data, uma taxa informada pelo usuário e uma obtida de fonte remota SHALL prevalecer sobre a derivada, nessa ordem.

A observação derivada MUST NOT deixar de ser registrada por a fonte remota existir. Ela é a única que existe sem rede, a única que alcança pares fora da cobertura da fonte, e continua a ser o que dispensa o usuário de digitar um número que ele já deu.

Isso MUST NOT ser confundido com persistir a taxa **na** operação, o que `balanced-ledger` proíbe: o que se grava é uma linha do acervo de taxas, dado próprio desta capability, e a operação permanece sem campo de taxa.

**Corrigir uma operação cruzada colhe a taxa nova e MUST NOT revogar a anterior.** A correção observa uma taxa como a criação observa, e a registra da mesma forma. O que ela MUST NOT fazer é apagar observação alguma: a taxa não pertence à operação — é afirmação sobre um dia, e sobrevive à operação que a revelou. Como **apagar** a operação já deixa a observação de pé, uma correção que a removesse inverteria a regra pelo caminho mais estreito, e apagaria uma observação que pode ter sido corroborada por outra operação do mesmo dia.

Disso decorre, sem regra adicional, o que acontece em cada correção. Corrigir apenas os valores, mantendo par e data, produz uma observação de **mesmo par, mesma data e mesma origem** — que, pela unicidade que o acervo já declara, **substitui** a anterior. Corrigir a data, ou apontar uma das pontas para outra moeda, produz observação em chave distinta, e a anterior SHALL permanecer no acervo, alcançável pela tela de taxas como qualquer outra. Corrigir uma operação cruzada de modo que ela deixe de atravessar moedas MUST NOT registrar observação alguma, e MUST NOT remover a que a operação havia colhido.

O sistema MUST NOT manter vínculo entre uma operação e a observação que ela originou. Um vínculo assim tornaria a taxa propriedade da operação, que é exatamente o que esta capability e `balanced-ledger` negam; a remoção de uma observação colhida por engano SHALL continuar sendo ato do usuário na tela de taxas.

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

#### Scenario: Corrigir o valor de uma operação cruzada substitui a observação
- **WHEN** o usuário corrige o valor de uma operação cruzada mantendo a data e as duas contas
- **THEN** a observação daquele par e daquela data passa a ser a nova, e não existem duas observações derivadas concorrentes

#### Scenario: Corrigir a data de uma operação cruzada deixa a observação anterior de pé
- **WHEN** o usuário corrige a data de uma operação cruzada
- **THEN** a taxa nova é registrada na data nova, a anterior permanece na data antiga, e a precedência por data resolve cada figura pela observação do seu próprio período

#### Scenario: Corrigir uma ponta para outra moeda deixa a observação anterior de pé
- **WHEN** o usuário corrige uma operação cruzada apontando uma das pontas para uma conta de terceira moeda
- **THEN** a observação do par novo é registrada, e a do par anterior permanece no acervo

#### Scenario: Corrigir uma operação cruzada para moeda única não colhe nem revoga
- **WHEN** o usuário corrige uma operação cruzada de modo que as duas pontas passem a ser da mesma moeda
- **THEN** nenhuma observação nova é registrada, e a que a operação havia colhido permanece no acervo

#### Scenario: A correção não conhece a observação que a operação colheu
- **WHEN** o caminho de correção de uma operação cruzada é inspecionado
- **THEN** ele não consulta nem referencia linha alguma do acervo para decidir o que fazer com ela
