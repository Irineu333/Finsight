# transaction-as-recurring Specification

## Purpose
Como uma transação em lançamento pode dar origem à recorrência da qual ela própria é o
primeiro ciclo, o que essa criação escreve, e o que ela garante sobre o mês da transação.

Uma despesa que se repete é descoberta **no momento em que é lançada**, e é ali que ela
pode ser registrada — sem um segundo formulário pedindo o que o usuário acabou de digitar.
O que a criação escreve não é só o template: é o template, a transação como seu ciclo 1 e
a ocorrência que fecha aquele mês, os três como uma unidade de trabalho.

## Requirements
### Requirement: Uma transação pode nascer recorrente

O formulário de adicionar transação SHALL oferecer uma opção que marca a transação em lançamento como recorrente, e essa opção MUST NOT exigir nenhum campo adicional do usuário: o valor, o título, a categoria, o destino e o dia da repetição são os que ele já preencheu.

A opção SHALL ser discreta — um controle no campo de data, junto ao seletor de calendário — porque ela qualifica um lançamento que continua sendo o assunto principal da tela, e não uma segunda tarefa.

Marcar a opção MUST NOT escrever nada: é estado do formulário, desfeito ao desmarcar ou ao abandonar a tela. A criação da recorrência acontece **ao salvar a transação**, e só então.

#### Scenario: A opção não pede nada além do que a transação já tem

- **WHEN** o usuário preenche uma transação válida e liga a opção de recorrência
- **THEN** nenhum campo adicional é apresentado, e o botão de salvar continua habilitado

#### Scenario: Marcar e desmarcar não deixa rastro

- **WHEN** o usuário liga a opção, desliga em seguida e salva a transação
- **THEN** a transação é lançada como qualquer outra e nenhuma recorrência é criada

#### Scenario: Abandonar a tela com a opção ligada não cria nada

- **WHEN** o usuário liga a opção e fecha o formulário sem salvar
- **THEN** nenhuma recorrência e nenhuma transação são criadas

### Requirement: A transação lançada é o ciclo 1 da recorrência criada

Ao salvar uma transação marcada como recorrente, o sistema SHALL criar a recorrência e lançar aquela transação como o seu **primeiro ciclo** — não como um lançamento avulso ao lado de um template recém-criado.

A transação SHALL carregar o vínculo com a recorrência (`recurringId` e `recurringCycle = 1`) e SHALL exibir esse vínculo como qualquer transação nascida da confirmação de um ciclo. O sistema SHALL registrar a ocorrência daquele mês como confirmada, apontando para a transação lançada.

O ciclo MUST NOT ser um número fixado à parte: ele é o resultado da mesma fórmula que a confirmação de um ciclo já usa, aplicada a um template ancorado na data da própria transação.

#### Scenario: A transação exibe a recorrência que a originou

- **WHEN** o usuário abre o detalhe de uma transação salva com a opção de recorrência ligada
- **THEN** o vínculo com a recorrência é exibido, como no de uma transação vinda de uma confirmação

#### Scenario: A recorrência aparece na lista de recorrentes

- **WHEN** o usuário salva uma transação com a opção ligada e abre a tela de recorrentes
- **THEN** a recorrência criada está lá, com o valor, o título, a categoria e o destino da transação, e com o dia do mês igual ao dia da data lançada

### Requirement: O mês da transação não volta a ser cobrado

O mês em que a transação foi lançada MUST NOT aparecer como ciclo pendente da recorrência recém-criada.

A pendência de um ciclo é decidida pela ausência de ocorrência naquele mês combinada ao dia do template já ter chegado. Como o dia do template é o dia da própria transação, o mês corrente satisfaria a condição imediatamente — e confirmar o que já foi lançado escreveria a **mesma despesa duas vezes no ledger**. A ocorrência confirmada gravada no ato do salvamento é o que fecha essa porta, e não é opcional.

#### Scenario: Nenhuma pendência logo após o lançamento

- **WHEN** o usuário salva uma transação com a opção de recorrência ligada e consulta as recorrências pendentes do mês
- **THEN** a recorrência recém-criada não está entre elas

#### Scenario: O mês seguinte é cobrado normalmente

- **WHEN** chega o dia do mês seguinte correspondente ao dia da recorrência
- **THEN** a recorrência aparece como pendente, como qualquer outra

### Requirement: O template é ancorado na data da transação

A recorrência criada a partir de uma transação SHALL ser ancorada na **data da transação**, e não no instante do salvamento. O dia do mês da recorrência SHALL ser o dia daquela data.

Uma transação lançada com data retroativa SHALL produzir uma recorrência cujo ciclo 1 é o mês daquela data. Como consequência, o mês corrente PODE aparecer como pendente logo em seguida — o que é correto: o ciclo do mês corrente de fato ainda não foi lançado.

#### Scenario: Data do mês corrente

- **WHEN** o usuário salva com a opção ligada uma transação datada de hoje
- **THEN** a recorrência tem o dia de hoje como dia do mês, e o ciclo 1 é o mês corrente

#### Scenario: Data retroativa cria o ciclo 1 no mês da data

- **WHEN** o usuário salva com a opção ligada uma transação datada de um mês anterior
- **THEN** o ciclo 1 é o mês daquela data, e a ocorrência confirmada é registrada nesse mês

#### Scenario: Data retroativa deixa o mês corrente pendente

- **WHEN** existe uma recorrência criada a partir de uma transação de um mês anterior e o dia do mês já passou
- **THEN** o mês corrente aparece como pendente de confirmação

### Requirement: Recorrência e parcelamento são mutuamente exclusivos

Um lançamento parcelado MUST NOT poder ser marcado como recorrente, e um lançamento marcado como recorrente MUST NOT poder ser parcelado. Parcelar já é repetir; as duas coisas juntas descreveriam duas repetições sobre o mesmo lançamento.

Quando o usuário passa a parcelar um lançamento que estava marcado como recorrente, a marca SHALL ser desfeita, e não apenas escondida: um estado que a tela não mostra mais não pode continuar valendo no salvamento.

#### Scenario: Parcelar desfaz a marca de recorrência

- **WHEN** o usuário liga a opção de recorrência e em seguida escolhe mais de uma parcela
- **THEN** a opção de recorrência é desligada e indisponível, e salvar produz apenas o parcelamento

#### Scenario: Voltar para uma parcela reabilita a opção

- **WHEN** o usuário volta o número de parcelas para uma
- **THEN** a opção de recorrência volta a ficar disponível, desmarcada

### Requirement: A criação é uma unidade de trabalho

Criar a recorrência, lançar a transação do ciclo 1 e registrar a ocorrência SHALL acontecer como **uma única unidade de trabalho**: ou os três persistem, ou nenhum.

Uma recorrência deixada para trás por uma transação recusada seria um modelo que o usuário não pediu, cobrando um ciclo pendente logo em seguida, enquanto a tela lhe informa que o lançamento falhou.

Quando a escrita é recusada, o sistema SHALL informar o motivo com a mensagem do erro correspondente, e não com uma falha genérica.

#### Scenario: Transação recusada não deixa recorrência

- **WHEN** o salvamento com a opção ligada é recusado pela escrita da transação (por exemplo, fatura fechada ou conta arquivada)
- **THEN** nenhuma recorrência, nenhuma transação e nenhuma ocorrência são persistidas, e o usuário vê a mensagem do erro que a recusou

#### Scenario: O formulário permanece com o que foi digitado

- **WHEN** o salvamento é recusado
- **THEN** o formulário continua aberto com os dados preenchidos, inclusive com a opção de recorrência ainda ligada

### Requirement: A recorrência criada é uma recorrência como as outras

A recorrência nascida de uma transação MUST NOT ser um tipo especial nem carregar restrição alguma que uma recorrência criada pelo formulário de recorrências não tenha. Ela SHALL ser editável, arquivável e removível pelas mesmas regras.

Em particular, a regra que exige arquivar em vez de excluir enquanto alguma transação nomeia o template continua valendo tal como está — nem mais, nem menos: excluída a transação que a originou, a recorrência volta a ser removível.

#### Scenario: Editar a recorrência criada

- **WHEN** o usuário abre a recorrência criada a partir de uma transação e altera o valor ou o dia
- **THEN** a edição é aceita como em qualquer recorrência, e a transação já lançada não é alterada

#### Scenario: Excluir a transação devolve a recorrência à condição de removível

- **WHEN** o usuário exclui a transação que originou a recorrência e em seguida tenta excluir a recorrência
- **THEN** a exclusão é permitida, porque nenhuma transação nomeia mais o template
