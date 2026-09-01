## MODIFIED Requirements

### Requirement: O seletor de mês governa o resumo e o filtro governa a lista

O resumo SHALL carregar um **seletor de mês**, e o mês selecionado SHALL governar as quatro
figuras.

O mês SHALL governar **também a lista**. O que a lista exibe são os **ciclos** dos templates no
mês selecionado, e um ciclo tem mês por definição — as duas metades da tela respondem pela mesma
pergunta, sobre o mesmo mês, e um seletor que movesse só metade delas seria indistinguível de um
defeito.

A premissa que antes proibia isso permanece verdadeira e é o que a torna possível: um template
não tem mês, apenas a ocorrência dele tem. O que mudou não foi a regra, foi o **objeto da
lista**, que deixou de ser o template.

O filtro da lista MUST NOT governar o resumo. Sob um recorte por natureza o resumo teria de
suprimir uma das duas linhas de cada bloco — mudando de **forma** enquanto a lista muda de
**conteúdo**, sem que o usuário possa saber qual das duas coisas o seletor fez.

#### Scenario: O filtro muda a lista e não o resumo
- **WHEN** o usuário recorta a lista por despesas
- **THEN** cada seção passa a exibir apenas ciclos de despesa e as quatro figuras permanecem inalteradas

#### Scenario: O mês muda o resumo e a lista
- **WHEN** o usuário seleciona o mês anterior no seletor do resumo
- **THEN** as quatro figuras passam a responder por aquele mês, e a lista passa a exibir os ciclos daquele mês

### Requirement: A metade lançada é lida do razão, nunca do template

As figuras do bloco lançado SHALL ser derivadas das **ocorrências confirmadas** do mês e do
dinheiro que as transações apontadas por elas de fato registraram no razão.

Elas MUST NOT ser derivadas do valor do template. Confirmar um ciclo permite ao usuário
sobrescrever o valor, a conta, o cartão, o título e a categoria daquele ciclo, e o template
permanece intacto — de modo que somar templates confirmados produziria um número que nunca
existiu.

O corte do mês SHALL ser o mês declarado pela **ocorrência**, e não a data da transação, para
que o dinheiro somado e o ciclo contado nunca discordem sobre a que mês um ciclo pertence.

A natureza de cada figura — despesa ou receita — SHALL ser derivada do razão, e MUST NOT ser
lida do tipo declarado no template.

#### Scenario: Ciclo confirmado com valor sobrescrito
- **WHEN** um template de R$ 940,00 tem o seu ciclo confirmado por R$ 865,00
- **THEN** a figura de receita fixa lançada do mês soma R$ 865,00, e não R$ 940,00

#### Scenario: Transação de um ciclo é apagada
- **WHEN** a transação gerada por um ciclo confirmado é removida
- **THEN** o valor sai da figura lançada, o template volta a compor a figura não lançada, e o ciclo volta a ser um dos que o mês ainda pode pedir

### Requirement: O resumo permanece quando o recorte da lista está vazio

O resumo SHALL permanecer visível quando o filtro da lista não devolve item algum.

O vazio de recorte MUST NOT ocupar a tela: ele SHALL ser exibido **abaixo** do resumo, no
lugar da lista. Um recorte sem itens é justamente quando o resumo tem mais a dizer — ele é a
única coisa na tela que ainda afirma alguma coisa sobre o mês —, e apagá-lo junto com a lista
deixaria o usuário sem resposta e sem contexto.

O resumo MUST NOT ser exibido quando não existe recorrência alguma cadastrada. Aí não há mês a
resumir, e a tela SHALL manter a sua oferta de criar a primeira.

#### Scenario: Recorte sem itens numa base povoada
- **WHEN** o usuário recorta a lista por receitas numa base que tem recorrências, nenhuma delas de receita
- **THEN** o resumo continua exibido e a mensagem de recorte vazio aparece abaixo dele

#### Scenario: Base sem nenhuma recorrência
- **WHEN** não existe recorrência cadastrada
- **THEN** o resumo não é exibido e a tela oferece a criação da primeira recorrência

## REMOVED Requirements

### Requirement: O contador é onde um ciclo ignorado é representável

**Reason**: O contador de ciclos sai do resumo. Ele afirmava quantos dos templates do mês foram
tratados, de quantos, e quantos o foram por terem sido ignorados — e as seções da lista passam a
afirmar as três coisas logo abaixo, com mais precisão: elas nomeiam **quais** ciclos, e não
apenas quantos. Mantido, o contador seria eco do que está imediatamente abaixo dele.

**Migration**: A representabilidade do ciclo ignorado **muda de dono, e não se perde**. Ela
passa para a seção "ignorado" da lista, especificada em `recurring-cycle-status` — que exibe cada
ciclo ignorado com o valor que ele deixou de lançar. As contagens que o contador dava passam a
ser as contagens das seções: os tratados são a soma das seções "lançado" e "ignorado", o total é
a soma das quatro, e os ignorados são a contagem da própria seção.

A anotação de **templates sem denominação** MUST permanecer no resumo. Ela não é contador de
ciclo, nenhuma seção a conta, e ela fala de uma falha — um template apontando para conta que não
existe mais — cuja saída é apontar o template para outro lugar.
