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
