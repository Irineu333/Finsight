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

### Requirement: Nenhum estado da linha é carregado apenas por cor

Todo estado que a linha afirma SHALL ser legível sem depender da cor: um ícone, um texto, ou
os dois.

Isso vale, nomeadamente, para os dois estados que a linha carrega hoje:

- **arquivada** — quando o recorte exibe recorrências arquivadas;
- **origem inutilizável** — quando a conta ou o cartão que a recorrência nomeia foi removido ou
  arquivado, e portanto ela não consegue postar. É o estado mais grave que a linha pode
  afirmar, e é o que hoje é dito apenas pela troca de um tom por outro.

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

#### Scenario: Linha de despesa
- **WHEN** a linha exibe uma recorrência de despesa de R$ 39,90
- **THEN** o valor é exibido sem sinal negativo

#### Scenario: Resumo presente não muda a linha
- **WHEN** o resumo do mês está visível acima da lista
- **THEN** as figuras das linhas continuam sem sinal

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

#### Scenario: Conta removida
- **WHEN** a conta que denominava a recorrência foi removida
- **THEN** a linha exibe a marca de valor irresolvível no lugar da figura, mantém a mesma altura das demais, e afirma que a origem não é utilizável

#### Scenario: Altura constante
- **WHEN** a lista mistura recorrências com e sem denominação
- **THEN** todas as linhas têm a mesma altura

