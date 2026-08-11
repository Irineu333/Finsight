## ADDED Requirements

### Requirement: O ajuste é um valor-alvo numa data, e não tem tipos

Ajustar um saldo SHALL ser **uma única operação**: declarar o valor que a conta — ou a fatura —
deveria ter, na data em que essa declaração vale. A data SHALL ser o único eixo que distingue um
ajuste de outro.

MUST NOT existir "ajuste de saldo inicial", "ajuste de saldo final" ou "ajuste de saldo atual"
como operações distintas, nem como tipos, nem como casos de uso separados. Um invólucro cuja
única função seja projetar uma data e delegar à operação de ajuste MUST NOT existir: a projeção
de data é um valor padrão, e um valor padrão não é uma operação.

Os pontos de entrada da interface SHALL poder ser vários — um por leitura útil do gesto —, e
SHALL diferir entre si **apenas pela data com que abrem** o ajuste. Nenhum ponto de entrada
SHALL produzir um ajuste que outro não pudesse produzir escolhendo a mesma data.

A regra que traduz um período no seu ponto no calendário — "o saldo de abertura de um mês é o
saldo ao fim do mês anterior" — SHALL ter um dono único no domínio, e a tela que oferece o
atalho MUST NOT reimplementá-la.

#### Scenario: Os atalhos diferem apenas pela data
- **WHEN** o usuário abre "editar saldo inicial" e depois "editar saldo final" no mesmo mês
- **THEN** os dois abrem o mesmo ajuste, diferindo apenas na data pré-selecionada

#### Scenario: Um atalho não alcança nada de exclusivo
- **WHEN** o usuário abre um ajuste por um atalho e altera a data para a que o outro atalho usaria
- **THEN** o resultado é idêntico, entrada por entrada no razão, ao que o outro atalho produziria

#### Scenario: Sem operação dedicada a um período
- **WHEN** o código que grava um ajuste é inspecionado
- **THEN** existe uma só operação de ajuste por alvo, parametrizada por data, e nenhuma operação
  cuja identidade seja o período ajustado

### Requirement: A data do ajuste é do usuário, com um padrão por contexto

O ajuste SHALL expor a data como campo editável, e o usuário SHALL ter a palavra final sobre ela.

A data que o ajuste abre SHALL ser derivada do contexto que o originou:

- ao ajustar o saldo de **abertura** de um mês, o último dia do mês anterior;
- ao ajustar o saldo de **fechamento** de um mês, o último dia daquele mês;
- ao ajustar uma **fatura**, a projeção do dia de hoje na janela daquela fatura.

Toda data padrão SHALL ser limitada a **hoje**. O limite SHALL ser o mesmo em todos os contextos
e SHALL ser aplicado igualmente ao seletor de data, de modo que o seletor MUST NOT oferecer uma
data que o padrão não produziria nem que a gravação recusaria.

Um ajuste MUST NOT ser gravado com data futura. Essa recusa SHALL estar no limite do seletor, e
MUST NOT depender de um erro apresentado após o envio.

#### Scenario: Saldo de fechamento de mês passado abre no fim daquele mês
- **WHEN** hoje é 11/agosto e o usuário ajusta o saldo de fechamento de março
- **THEN** a data abre em 31/março, e o ajuste gravado pertence a março

#### Scenario: Saldo de fechamento do mês corrente abre em hoje
- **WHEN** hoje é 11/agosto e o usuário ajusta o saldo de fechamento de agosto
- **THEN** a data abre em 11/agosto, e não no fim do mês, que ainda não ocorreu

#### Scenario: Saldo de abertura abre no fim do mês anterior
- **WHEN** o usuário ajusta o saldo de abertura de março
- **THEN** a data abre em 28/fevereiro

#### Scenario: A data padrão é sobrescrevível
- **WHEN** o usuário altera a data sugerida para outra data não futura
- **THEN** o ajuste é gravado na data que ele escolheu

#### Scenario: O seletor recusa o futuro
- **WHEN** o usuário abre o seletor de data de um ajuste
- **THEN** nenhuma data posterior a hoje é oferecida

### Requirement: O valor de referência é lido na data do ajuste

O valor pré-preenchido no campo SHALL ser o saldo **na data selecionada** do ajuste, e a
diferença exibida entre o valor digitado e o corrente SHALL derivar do mesmo saldo.

MUST NOT existir um período alvo paralelo à data governando o que a interface exibe: o valor
exibido e o valor sobre o qual a diferença é aplicada SHALL ser o mesmo, sempre, em todos os
pontos de entrada.

Alterar a data SHALL atualizar o valor de referência e a diferença exibida, sem ação adicional do
usuário.

#### Scenario: Saldo de abertura exibe o saldo do mês anterior
- **WHEN** o usuário ajusta o saldo de abertura de março, cuja data é 28/fevereiro
- **THEN** o campo é pré-preenchido com o saldo em 28/fevereiro, e não com o saldo ao fim de março

#### Scenario: A diferença exibida é a diferença gravada
- **WHEN** o usuário digita um valor alvo em qualquer ponto de entrada do ajuste
- **THEN** a diferença exibida ao lado do campo é exatamente a diferença aplicada ao razão

#### Scenario: Mudar a data move o valor de referência
- **WHEN** o usuário altera a data do ajuste para uma data com saldo diferente
- **THEN** o valor pré-preenchido e a diferença exibida passam a refletir o saldo naquela data

### Requirement: O ajuste de fatura é datado pela ocorrência, não pela janela

A data de um ajuste de fatura SHALL exprimir **quando a correção ocorreu**, e a fatura SHALL
exprimir **onde ela se liquida**. Os dois eixos são independentes: o valor chega à fatura pela
dimensão que a perna carrega, e MUST NOT depender da data.

A janela de compra da fatura MUST NOT limitar a data de um ajuste, em nenhuma das duas direções.
O único limite SHALL ser hoje. Uma correção feita hoje sobre uma fatura antiga SHALL poder ser
datada hoje, e uma despesa esquecida SHALL poder ser datada quando ocorreu.

A distinção SHALL ser pela **natureza da perna** e não pela direção da divergência: uma compra
não pode se liquidar num ciclo que fechou antes de ela acontecer, mas uma correção não ocorre
dentro do ciclo — ela ocorre sobre ele.

Quando a data escolhida não pertence à janela da fatura selecionada, o formulário SHALL dizê-lo
pelo mesmo mecanismo dos demais formulários de cartão — discreto, junto ao campo, nunca como erro
e nunca impedindo o envio.

#### Scenario: Corrigir hoje uma fatura antiga
- **WHEN** hoje é 11/agosto e o usuário ajusta a fatura de janeiro sem alterar a data
- **THEN** o ajuste é gravado em 11/agosto, conta integralmente na fatura de janeiro, e o
  formulário sinaliza que a data está fora do período daquela fatura

#### Scenario: Datar a correção dentro do ciclo
- **WHEN** o usuário ajusta a fatura de janeiro e escolhe uma data dentro da janela dela
- **THEN** o ajuste é gravado naquela data e nenhuma divergência é sinalizada

#### Scenario: A janela não é piso
- **WHEN** o usuário escolhe, para um ajuste, uma data anterior à abertura da fatura selecionada
- **THEN** a data é aceita como escrita e o ajuste é gravado

#### Scenario: O valor não depende da data
- **WHEN** o usuário altera apenas a data de um ajuste de fatura
- **THEN** o valor devido pela fatura permanece o mesmo, porque a dimensão é o que o decide

### Requirement: Cada ajuste é um evento datado

Reabrir o ajuste de um alvo numa data que já tem ajuste SHALL **editar aquele ajuste**, e MUST NOT
acrescentar um segundo. Ajustar o mesmo alvo em outra data SHALL criar um ajuste próprio daquela
data.

O tamanho de um ajuste existente SHALL ser lido da sua própria perna no razão, de modo que
reajustar nunca acumule sobre um valor obsoleto.

Um ajuste cujo tamanho resultante seja zero SHALL ser removido, e MUST NOT permanecer como
transação de valor nulo.

Um ajuste SHALL ser um **delta** no razão, e MUST NOT ser um valor-alvo persistido: alterar o
passado depois de um ajuste SHALL propagar-se ao saldo corrente, sem reescrever o ajuste já
gravado.

#### Scenario: Reajustar na mesma data edita o mesmo lançamento
- **WHEN** o usuário ajusta o saldo de uma conta numa data e, em seguida, ajusta de novo na mesma data
- **THEN** existe um só lançamento de ajuste naquela data, com o tamanho resultante

#### Scenario: Ajustar em outra data cria outro lançamento
- **WHEN** o usuário ajusta o saldo de uma conta em duas datas diferentes
- **THEN** existem dois lançamentos de ajuste, um em cada data

#### Scenario: Reajuste de volta ao valor original remove o ajuste
- **WHEN** o usuário reajusta um saldo de forma que o ajuste daquela data resulte em zero
- **THEN** o lançamento de ajuste é removido do razão

#### Scenario: Alterar o passado propaga ao presente
- **WHEN** existe um ajuste numa data e o usuário altera lançamentos anteriores a ela
- **THEN** o saldo corrente reflete a alteração, e o ajuste gravado permanece com o mesmo valor
