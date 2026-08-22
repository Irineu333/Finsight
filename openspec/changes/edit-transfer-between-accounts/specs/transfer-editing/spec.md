## ADDED Requirements

### Requirement: A transferência entre contas é corrigível no lugar

Uma transferência entre contas SHALL poder ser corrigida sem ser apagada e refeita. A correção SHALL alcançar tudo o que define a operação — conta de origem, conta de destino, valor que sai, valor que chega e data —, e MUST NOT congelar nenhum desses campos: nenhum deles é identidade da operação, e travar qualquer um obrigaria a apagar e refazer justamente o caso que este requisito existe para dispensar.

A correção SHALL preservar a identidade da transação. As pernas são reescritas, mas a operação continua sendo a mesma — o que a distingue de apagar e recriar, que produz uma operação nova.

A transferência entre moedas SHALL ser corrigível pelo mesmo caminho e sob as mesmas regras da transferência de moeda única. Ela não é um caso à parte: a operação chega incompleta à fronteira de escrita e é completada com pernas de conversão, na correção exatamente como na criação.

#### Scenario: Corrigir o valor de uma transferência
- **WHEN** o usuário corrige o valor de uma transferência já registrada
- **THEN** a operação passa a valer o novo valor nas duas contas, e continua sendo a mesma operação

#### Scenario: Corrigir a conta de destino
- **WHEN** o usuário troca a conta de destino de uma transferência já registrada
- **THEN** o dinheiro deixa de constar na conta anterior e passa a constar na nova, sem que a operação seja recriada

#### Scenario: Corrigir a data
- **WHEN** o usuário corrige a data de uma transferência já registrada
- **THEN** a operação passa a pertencer à nova data em toda leitura que a alcança

#### Scenario: Corrigir uma transferência entre moedas
- **WHEN** o usuário corrige o valor que chega numa transferência entre contas de moedas diferentes
- **THEN** a operação é reescrita com as duas pernas monetárias e as pernas de conversão que a completam, e cada moeda continua somando zero

#### Scenario: Corrigir uma transferência de volta para moeda única
- **WHEN** o usuário corrige uma transferência entre moedas apontando as duas pontas para contas da mesma moeda
- **THEN** a operação passa a ter apenas as duas pernas monetárias, sem perna de conversão alguma

### Requirement: Criar e corrigir uma transferência usam o mesmo formulário e as mesmas regras

O sistema SHALL oferecer a correção de uma transferência pelo **mesmo formulário** que a cria, distinguindo os dois modos apenas pelo que **anuncia** — o título e o verbo do único botão — e não pelo que oferece. Um formulário próprio para corrigir seria uma segunda gramática para a mesma operação, e as duas divergiriam.

O verbo do botão SHALL nomear o que a confirmação faz naquele modo. "Transferir" é o que a criação faz; uma correção grava uma operação cujo dinheiro já se moveu, e oferecer o mesmo verbo ali afirma um segundo movimento que não vai acontecer. O botão continua sendo **um só** — o que muda é a palavra, não a quantidade de comandos.

Em modo de correção o formulário SHALL vir preenchido com o que a operação diz hoje: as duas contas, os dois valores quando as moedas diferem, e a data.

Toda validação que a criação aplica SHALL valer integralmente na correção: o valor que sai SHALL ser maior que zero; o valor que chega, quando informado, SHALL ser maior que zero; a conta de origem SHALL ser diferente da de destino; a data MUST NOT ser futura; e as duas contas SHALL existir. Essas regras SHALL ter um dono único, e MUST NOT ser reimplementadas por cada um dos dois caminhos — uma cópia divergiria da outra sem que nada acusasse.

O formulário de transferência SHALL oferecer apenas contas, de modo que corrigir uma transferência MUST NOT poder transformá-la em despesa, receita, ajuste ou pagamento de fatura. A impossibilidade é da forma do formulário, e não de uma guarda que alguém precise lembrar de escrever.

O que o formulário exibe em modo de correção SHALL ser o que a operação registra, e MUST NOT ser substituído por sugestão alguma. O acervo de taxas oferece um valor de destino provável enquanto o usuário cria uma transferência; numa correção esse valor já existe e é fato, de modo que a sugestão MUST NOT sobrescrevê-lo nem apagá-lo quando não houver observação daquela data.

Trocada a moeda de uma das pontas durante a correção, o valor que estava no campo SHALL ser retirado — dígitos denominados numa moeda não sobrevivem sob o símbolo de outra.

O formulário MUST NOT apagar o que ele não exibe. Um dado que a operação carrega e o formulário não oferece SHALL ser preservado pela correção.

#### Scenario: O botão de confirmar nomeia o que a confirmação faz
- **WHEN** o usuário abre a correção de uma transferência já registrada
- **THEN** o único botão do formulário oferece salvar, e não transferir

#### Scenario: O formulário de correção chega preenchido
- **WHEN** o usuário abre a correção de uma transferência já registrada
- **THEN** as duas contas, o valor e a data aparecem como a operação os registra hoje

#### Scenario: A sugestão do acervo não substitui o valor registrado
- **WHEN** o usuário abre a correção de uma transferência entre moedas e o acervo tem uma observação daquele par e daquela data
- **THEN** o campo exibe o valor que a operação registra, e não o que a observação implica

#### Scenario: A ausência de observação não apaga o valor registrado
- **WHEN** o usuário abre a correção de uma transferência entre moedas e o acervo nada tem a dizer sobre aquela data
- **THEN** o campo continua exibindo o valor que a operação registra

#### Scenario: Trocar a moeda de destino retira o valor do campo
- **WHEN** o usuário, corrigindo uma transferência, aponta o destino para uma conta de outra moeda
- **THEN** o valor que estava no campo de destino é retirado, e não permanece sob o símbolo da moeda nova

#### Scenario: A correção preserva o que o formulário não oferece
- **WHEN** o usuário corrige uma transferência que carrega um título
- **THEN** o título permanece como estava, porque o formulário não o exibe e não o pediu

#### Scenario: Uma correção com valor zero é recusada
- **WHEN** o usuário tenta corrigir uma transferência para valor zero ou negativo
- **THEN** a correção é recusada, com a mesma recusa que a criação daria

#### Scenario: Uma correção apontando as duas pontas para a mesma conta é recusada
- **WHEN** o usuário tenta corrigir uma transferência escolhendo a mesma conta como origem e destino
- **THEN** a correção é recusada, com a mesma recusa que a criação daria

#### Scenario: Uma correção com data futura é recusada
- **WHEN** o usuário tenta corrigir uma transferência para uma data futura
- **THEN** a correção é recusada, com a mesma recusa que a criação daria

#### Scenario: A correção não muda a natureza da operação
- **WHEN** o formulário de correção de uma transferência é inspecionado
- **THEN** ele não oferece categoria, cartão nem escolha de natureza, e a operação corrigida continua sendo uma transferência

### Requirement: A correção de uma transferência não atravessa a fronteira entre features

O detalhe de uma operação SHALL alcançar o formulário de transferência sem nomear o módulo de implementação que o hospeda. A transferência nasce na tela de contas e pertence a ela; o detalhe da operação vive noutra feature, e a arquitetura do projeto proíbe que uma implementação nomeie outra.

O acesso SHALL usar o ponto de entrada público da feature dona do formulário, que é o mecanismo já existente para esse fim.

O razão MUST NOT tomar conhecimento de que uma transferência existe. A correção SHALL chegar até ele como um conjunto de pernas expressas por identidade de conta, indistinguível de qualquer outra reescrita.

#### Scenario: O detalhe abre o formulário sem nomear a implementação que o hospeda
- **WHEN** as dependências da tela de detalhe são inspecionadas
- **THEN** ela alcança o formulário de transferência pelo ponto de entrada público da feature de contas, e não pela implementação

#### Scenario: O razão continua sem saber o que é uma transferência
- **WHEN** a reescrita produzida por uma correção de transferência é inspecionada na fronteira de escrita
- **THEN** ela é um conjunto de pernas por identidade de conta, sem nada que a identifique como transferência

### Requirement: Uma transferência com perna sobre conta arquivada não é corrigível nem removível

Uma transferência SHALL ficar congelada — sem correção e sem remoção — quando **qualquer** uma das suas pernas postar em conta permanente arquivada. Basta uma das duas contas estar arquivada para congelar a operação inteira.

Isto MUST NOT ser regra própria da transferência: é a mesma regra que já congela qualquer operação sobre conta arquivada, e a transferência a herda por ter pernas como qualquer outra. Corrigir é o mais grave dos dois — retargetar uma operação antiga devolveria saldo a uma conta arquivada sem que nenhuma escrita a tocasse, que é exatamente o estado que arquivar uma conta exige não existir.

O motivo do congelamento SHALL ser comunicado ao usuário, no lugar onde as ações apareceriam.

#### Scenario: Transferência cuja conta de origem foi arquivada
- **WHEN** o detalhe de uma transferência cuja conta de origem foi arquivada depois é aberto
- **THEN** nem correção nem remoção são oferecidas, e o motivo é exibido

#### Scenario: Transferência cuja conta de destino foi arquivada
- **WHEN** o detalhe de uma transferência cuja conta de destino foi arquivada depois é aberto
- **THEN** nem correção nem remoção são oferecidas, pelo mesmo motivo e sem regra adicional

#### Scenario: Transferência entre contas ativas continua corrigível
- **WHEN** o detalhe de uma transferência cujas duas contas seguem ativas é aberto
- **THEN** correção e remoção são oferecidas
