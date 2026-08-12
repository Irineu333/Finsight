## MODIFIED Requirements

### Requirement: Item exibe sinal apenas onde o rótulo não entrega a direção

Na superfície de **item** — o card de uma transação numa lista, a sua linha no relatório exportado — o valor SHALL ser exibido sem sinal para gasto, receita e pagamento de fatura, cujos rótulos entregam a direção.

A superfície de item é aquela em que **uma linha é uma perna**: ela exibe uma única figura e por isso depende do rótulo para dizer o sentido do movimento. O detalhe de uma operação não é mais uma superfície de item — ele exibe todas as pernas monetárias, cada uma com o seu verbo — e é governado pela superfície de **operação**, adiante.

O **ajuste** SHALL exibir sinal sempre, positivo quando aumenta o patrimônio líquido da perspectiva e negativo quando o reduz — inclusive quando a perspectiva é um cartão, onde aumentar a dívida reduz o patrimônio. É a única transação cuja direção o rótulo não entrega.

A **transferência** SHALL exibir sinal explícito nas duas pontas quando lida sob uma perspectiva, porque elas compartilham rótulo, ícone e cor e sem sinal seriam indistinguíveis; exibir o sinal em apenas uma delas obrigaria o leitor a inferir a outra por ausência. Lida sem perspectiva, ela MUST NOT exibir sinal: a mesma transação contém as duas pontas, e a direção de uma perna escolhida arbitrariamente não é propriedade da transação.

#### Scenario: Ajuste que aumenta a dívida de um cartão
- **WHEN** a dívida de uma fatura é ajustada de R$ 0,00 para R$ 100,00 e a transação resultante é exibida como item
- **THEN** o valor é exibido como negativo, porque a dívida maior reduz o patrimônio

#### Scenario: Ajuste que reduz a dívida de um cartão
- **WHEN** a dívida de uma fatura é ajustada para menos e a transação resultante é exibida como item
- **THEN** o valor é exibido como positivo

#### Scenario: Ajuste que reduz o saldo de uma conta
- **WHEN** o saldo de uma conta é ajustado para menos e a transação resultante é exibida como item
- **THEN** o valor é exibido como negativo

#### Scenario: Ajuste que aumenta o saldo de uma conta
- **WHEN** o saldo de uma conta é ajustado para mais e a transação resultante é exibida como item
- **THEN** o valor é exibido como positivo

#### Scenario: Gasto não ganha sinal
- **WHEN** um gasto em conta ou em cartão é exibido como item
- **THEN** o valor é exibido em módulo, e a direção permanece expressa pelo rótulo, pelo ícone e pela cor

#### Scenario: Pagamento não ganha sinal
- **WHEN** o pagamento de uma fatura é exibido como item, em qualquer perspectiva
- **THEN** o valor é exibido em módulo, porque o rótulo "pagamento" entrega a direção

#### Scenario: Transferência sob a perspectiva de uma conta
- **WHEN** uma transferência é exibida na lista da conta de onde o dinheiro saiu
- **THEN** o valor é exibido com sinal negativo, e na lista da conta de destino, com sinal positivo explícito

#### Scenario: Transferência sem perspectiva
- **WHEN** uma transferência é exibida em uma lista que não declara perspectiva
- **THEN** o valor é exibido sem sinal, porque a transação contém as duas pontas

#### Scenario: O detalhe não é superfície de item
- **WHEN** a mesma transferência é exibida numa lista e aberta no seu detalhe
- **THEN** a lista aplica a regra de item sobre a perna que exibe, e o detalhe aplica a regra da superfície de operação sobre cada um dos seus cards

## ADDED Requirements

### Requirement: A superfície de operação exibe módulo, porque o verbo entrega a direção

Na superfície de **operação** — aquela que apresenta a transação inteira, uma perna por card, em vez de uma perna por linha — o valor de cada card SHALL ser exibido em módulo. O verbo do card já diz o que aconteceu com aquele dinheiro, e um sinal ao lado dele é redundância que o leitor tenta interpretar.

O **ajuste** SHALL exibir sinal explícito também aqui, pela mesma razão de sempre: é a única operação cuja direção o seu verbo retém. O sinal SHALL ser o do razão, debit-positive, exatamente como a perna foi registrada, e MUST NOT ser invertido por tipo de conta.

Sinal em módulo aqui MUST NOT ser lido como abandono do princípio de que o sinal expressa efeito sobre a perspectiva: é a mesma omissão que a superfície de item aplica quando o rótulo entrega a direção, aplicada a uma evidência diferente — o verbo em vez do rótulo. Um pagamento de fatura, cujas duas pernas são benignas, exibiria `−` seguido de `+` e sugeriria que algo se perdeu entre as duas.

#### Scenario: Pagamento de fatura não exibe sinal em nenhum card
- **WHEN** o detalhe de um pagamento de fatura é aberto
- **THEN** os dois cards exibem o valor em módulo, e o sentido de cada um vem do seu verbo

#### Scenario: Transferência não exibe sinal em nenhum card
- **WHEN** o detalhe de uma transferência é aberto
- **THEN** os dois cards exibem o valor em módulo, porque "saiu de" e "entrou em" já os distinguem

#### Scenario: Ajuste exibe sinal explícito
- **WHEN** o detalhe de um ajuste que reduz o saldo de uma conta é aberto
- **THEN** o card exibe o valor com sinal negativo

#### Scenario: Ajuste de fatura mantém o sinal do razão
- **WHEN** o detalhe de um ajuste que aumenta a dívida de uma fatura é aberto
- **THEN** o card exibe o valor como negativo, porque a dívida maior reduz o patrimônio, e o sinal não é invertido pelo tipo de conta

#### Scenario: O ajuste lê igual na lista e no detalhe
- **WHEN** o mesmo ajuste é exibido como item de lista e aberto no seu detalhe
- **THEN** os dois exibem o mesmo sinal, por derivarem do mesmo valor do razão
