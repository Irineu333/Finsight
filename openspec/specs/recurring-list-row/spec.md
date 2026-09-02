# recurring-list-row Specification

## Purpose
TBD - created by archiving change redesign-recurring-screen. Update Purpose after archive.
## Requirements
### Requirement: A linha discrimina, e não antecipa a ficha

A linha da lista de recorrentes SHALL afirmar o que separa uma recorrência da seguinte: a sua
**identidade**, a sua **figura**, o **dia** do ciclo e a **origem** — a conta ou o cartão em
que ela posta.

A linha MUST NOT repetir a ficha de detalhe. Tudo o que o detalhe já enuncia — o tipo, o
valor, o dia, o estado, a conta ou o cartão e a categoria — está a um toque de distância,
rotulado, e a linha que os antecipa todos paga altura para não acrescentar nada. O critério da
linha é *o que distingue esta das demais*, e não *o que é importante saber*.

Em particular, a linha MUST NOT carregar uma legenda que nomeie a natureza do número que
exibe: numa tela cujo assunto é recorrência, toda linha tem uma figura mensal, e o rótulo é
constante.

A identidade SHALL ceder largura antes da figura quando o espaço não comportar as duas.

#### Scenario: Dois templates de mesmo nome
- **WHEN** duas recorrências têm o mesmo rótulo e postam em contas diferentes
- **THEN** a linha de cada uma nomeia a sua origem, e as duas são distinguíveis sem abrir o detalhe

#### Scenario: Rótulo longo
- **WHEN** o rótulo da recorrência não cabe na largura disponível
- **THEN** o rótulo é truncado e a figura permanece integralmente legível

### Requirement: A lista tem uma linha só

Todas as seções da tela de recorrentes — pendente, a lançar, lançada e ignorada — SHALL ser
desenhadas pelo **mesmo componente de linha**, com o mesmo módulo: o mesmo tamanho e o mesmo
raio de chip, o mesmo espaçamento interno, a mesma altura e a mesma geometria de colunas.

Uma linha lida do template e uma linha lida do razão são duas **fontes**, e não dois desenhos.
A escolha da fonte é do view model; o componente recebe o que a linha afirma e MUST NOT saber
que existem duas. Um segundo componente para uma das seções faz da lista dois blocos de telas
diferentes empilhados, e deixa a altura da lista sem dono — duas definições que divergem no
primeiro ajuste de um dos lados.

A distribuição SHALL ser a mesma em toda linha: a **origem** na segunda linha da coluna de
identidade, e o par (**figura**, **linha do tempo**) na coluna oposta.

A altura SHALL ser a mesma em **toda variante**: com categoria e sem, denominada e não, lida do
template e lida do razão. É essa constância que permite reordenar a lista sem que uma linha
salte.

#### Scenario: Duas seções na mesma tela
- **WHEN** a lista exibe uma seção de ciclos pendentes e uma de ciclos lançados
- **THEN** as linhas das duas seções têm a mesma altura, o mesmo chip e a mesma distribuição de colunas

#### Scenario: Altura constante entre fontes
- **WHEN** uma recorrência sem categoria e sem denominação está na mesma lista de um ciclo lançado com categoria
- **THEN** as duas linhas medem a mesma altura

### Requirement: Nenhum estado da linha é carregado apenas por cor

Todo estado que a linha afirma SHALL ser legível sem depender da cor: um ícone, um texto, ou
os dois.

Isso vale, nomeadamente, para o estado que a linha carrega:

- **origem inutilizável** — quando a conta ou o cartão que a recorrência nomeia foi removido ou
  arquivado, e portanto ela não consegue postar. É o estado mais grave que a linha pode afirmar.

O estado **arquivada** deixa de ser afirmado por esta linha. Uma recorrência arquivada não gera
ciclo em mês algum e não aparece na lista; no destino próprio em que ela permanece alcançável,
todas estão arquivadas, e uma marca que toda linha carrega não distingue linha alguma da
vizinha — que é o critério pelo qual a linha decide o que afirmar.

A direção do lançamento — despesa ou receita — SHALL igualmente ser legível sem cor, e quando
carregada por um ícone SHALL ter descrição de conteúdo que a nomeie.

#### Scenario: Recorrência sem conta utilizável
- **WHEN** a conta que a recorrência nomeia foi removida
- **THEN** a linha afirma a origem inutilizável por ícone ou texto, e não apenas por um tom mais apagado

#### Scenario: Direção do lançamento
- **WHEN** a linha exibe uma recorrência de despesa
- **THEN** a direção é legível sem cor, e a descrição de conteúdo do ícone que a carrega nomeia a natureza

### Requirement: A figura da linha não exibe sinal

A figura da linha SHALL ser exibida como magnitude, sem sinal, com a direção entregue pelo
rótulo e pela marca de direção da própria linha.

A linha é **superfície de item** no sentido de `money-display`: ela exibe uma única figura e
não participa de soma alguma exibida. A presença de um resumo acima da lista MUST NOT ser lida
como autorização para assinar a figura da linha — as figuras do resumo não somam as linhas, e
nenhuma coluna da tela fecha em total algum.

Isso vale igualmente para a linha de um **ciclo lançado**. A linha é a mesma para toda a lista,
e a política é sua: magnitude para despesa e para receita, qualquer que seja a fonte de onde ela
leu. A organização da lista em seções MUST NOT ser lida como autorização para assinar figura
alguma.

#### Scenario: Linha de despesa
- **WHEN** a linha exibe uma recorrência de despesa de R$ 39,90
- **THEN** o valor é exibido sem sinal negativo

#### Scenario: Resumo presente não muda a linha
- **WHEN** o resumo do mês está visível acima da lista
- **THEN** as figuras das linhas continuam sem sinal

#### Scenario: Linha de ciclo lançado
- **WHEN** a seção "lançado" exibe um ciclo de despesa de R$ 865
- **THEN** o valor é exibido sem sinal negativo

### Requirement: A linha do ciclo lançado afirma o fato, e não a regra

A linha de um ciclo lançado SHALL ler do razão tudo o que afirma sobre o mês: a figura, a
identidade, a classificação e a **origem** em que a transação efetivamente postou.

Confirmar um ciclo aceita sobrescrever a conta e o cartão para aquele mês, e a ocorrência não
guarda essa escolha — a origem verdadeira está na transação. Uma linha que nomeasse a origem do
**template** afirmaria a regra no lugar em que o usuário lê o fato, e erraria exatamente no mês
em que os dois divergem, que é o único mês em que essa informação distingue alguma coisa.

No lugar em que a linha de um template afirma o **dia projetado**, a linha de um ciclo lançado
SHALL afirmar a **data em que o fato foi registrado**.

#### Scenario: Confirmação que trocou a conta
- **WHEN** um ciclo foi confirmado numa conta diferente da que o template nomeia
- **THEN** a linha da seção lançada nomeia a conta em que a transação postou, e não a do template

#### Scenario: Confirmação fora do dia projetado
- **WHEN** um ciclo projetado para o dia 5 foi confirmado no dia 8
- **THEN** a linha da seção lançada exibe a data do registro, e não o dia projetado pelo template

#### Scenario: Lançamento em cartão
- **WHEN** o ciclo foi confirmado num cartão de crédito
- **THEN** a linha nomeia o cartão, e não a conta do plano de contas em que a perna postou

### Requirement: Um template sem denominação exibe a marca de valor irresolvível

Quando a conta ou o cartão que denomina a recorrência não pode ser resolvido, a linha MUST NOT
omitir a figura: ela SHALL exibir a marca de valor irresolvível que a exibição de dinheiro já
define.

Omitir a figura diz "não há número" por ausência, o que numa lista densa é invisível, e faz a
linha mudar de altura sem que nada explique a diferença. A marca ocupa a largura de um valor e
nada mais, mantém a altura da linha constante em toda variante, e afirma em voz alta o que a
ausência apenas insinuava.

A linha SHALL, junto, afirmar a **causa** — que a origem não é utilizável —, para que a marca
não fique sem explicação.

Este requisito vale para as linhas que leem o **template**: as dos ciclos pendentes, a lançar e
ignorados, onde não há fato registrado e a denominação depende inteiramente da conta que o
template nomeia. Ele MUST NOT ser aplicado à linha de um ciclo **lançado**: ali o dinheiro saiu,
está registrado no razão com a moeda em que foi registrado, e não há denominação a resolver —
exibir a marca afirmaria uma ausência que não existe. Que as duas sejam a mesma linha não muda
isso: o que decide é a fonte de onde ela leu, e um fato registrado nunca é irresolvível.

#### Scenario: Conta removida
- **WHEN** a conta que denominava a recorrência foi removida e o ciclo do mês não foi lançado
- **THEN** a linha exibe a marca de valor irresolvível no lugar da figura, mantém a mesma altura das demais, e afirma que a origem não é utilizável

#### Scenario: Altura constante
- **WHEN** a lista mistura recorrências com e sem denominação
- **THEN** todas as linhas de template têm a mesma altura

#### Scenario: Conta removida depois do lançamento
- **WHEN** a conta foi removida depois de o ciclo do mês ter sido confirmado
- **THEN** a linha na seção "lançado" exibe a figura registrada no razão, e não a marca de valor irresolvível
