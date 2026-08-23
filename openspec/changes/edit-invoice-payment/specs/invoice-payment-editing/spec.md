## ADDED Requirements

### Requirement: O pagamento parcial de fatura é corrigível no lugar

Um pagamento **parcial** de fatura SHALL poder ser corrigido sem ser apagado e refeito. A correção SHALL alcançar tudo o que define a operação — cartão, fatura, conta pagadora, valor que liquida a fatura, valor que sai da conta e data —, e MUST NOT congelar nenhum desses campos: nenhum deles é identidade da operação, e travar qualquer um obrigaria a apagar e refazer justamente o caso que este requisito existe para dispensar.

A correção SHALL preservar a identidade da transação. As pernas são reescritas, mas a operação continua sendo a mesma — o que a distingue de apagar e recriar, que produz uma operação nova.

O pagamento **entre moedas** SHALL ser corrigível pelo mesmo caminho e sob as mesmas regras do pagamento de moeda única. Ele não é um caso à parte: a operação chega incompleta à fronteira de escrita e é completada com pernas de conversão, na correção exatamente como na criação.

A forma que a correção grava SHALL ser a mesma que a criação grava, com o mesmo dono: a saída da conta pagadora **sem dimensão**, e a entrada na conta `LIABILITY` do cartão carregando a dimensão da fatura. Dois caminhos que montassem a mesma transação em dois lugares divergiriam sem que nada acusasse.

#### Scenario: Corrigir o valor de um pagamento parcial
- **WHEN** o usuário corrige o valor de um pagamento parcial já registrado
- **THEN** a fatura e a conta passam a refletir o novo valor, e continua sendo a mesma operação

#### Scenario: Corrigir a conta pagadora
- **WHEN** o usuário troca a conta pagadora de um pagamento já registrado
- **THEN** o dinheiro deixa de constar na conta anterior e passa a constar na nova, sem que a operação seja recriada

#### Scenario: Corrigir a data
- **WHEN** o usuário corrige a data de um pagamento já registrado
- **THEN** a operação passa a pertencer à nova data em toda leitura que a alcança

#### Scenario: Corrigir a fatura que o pagamento paga
- **WHEN** o usuário aponta um pagamento já registrado para outra fatura que aceita pagamento parcial
- **THEN** o devido da fatura anterior volta a incluir o valor, o da fatura nova passa a descontá-lo, e continua sendo a mesma operação

#### Scenario: Corrigir um pagamento entre moedas
- **WHEN** o usuário corrige o valor que sai da conta num pagamento cuja conta e cartão são denominados de forma diferente
- **THEN** a operação é reescrita com as duas pernas monetárias e as pernas de conversão que a completam, e cada moeda continua somando zero

#### Scenario: A dimensão continua só na perna do cartão
- **WHEN** uma correção de pagamento é gravada
- **THEN** apenas a perna da conta `LIABILITY` do cartão carrega a dimensão da fatura

### Requirement: Criar e corrigir um pagamento usam o mesmo formulário e as mesmas regras

O sistema SHALL oferecer a correção de um pagamento pelo **mesmo formulário** que o cria, distinguindo os dois modos apenas pelo que **anuncia** — o cabeçalho e o verbo do único botão — e não pelo que oferece. Um formulário próprio para corrigir seria uma segunda gramática para a mesma operação, e as duas divergiriam.

O verbo do botão SHALL nomear o que a confirmação faz naquele modo. "Antecipar" e "pagar" são o que a criação faz; uma correção grava uma operação cujo dinheiro já se moveu, e oferecer o mesmo verbo ali afirma um segundo movimento que não vai acontecer. Em modo de correção o botão SHALL oferecer **salvar**, e continua sendo **um só** — o que muda é a palavra, não a quantidade de comandos.

Toda validação que a criação aplica SHALL valer integralmente na correção: o valor SHALL ser maior que zero e não maior que o devido; o valor que sai da conta, quando as duas pontas diferem, SHALL ser maior que zero e não SHALL ter teto, porque um teto ali seria um limite exprimido na moeda errada; a data SHALL pertencer à janela de liquidação da fatura selecionada; a fatura SHALL existir e SHALL aceitar pagamento parcial. Essas regras SHALL ter um dono único, e MUST NOT ser reimplementadas por cada um dos dois caminhos — uma cópia divergiria da outra sem que nada acusasse.

A oferta e a permissão SHALL ler o mesmo predicado: o caso de uso que grava a correção SHALL recusar o que o formulário não oferece, ainda que nenhuma tela o ofereça.

O formulário MUST NOT apagar o que ele não exibe. Um dado que a operação carrega e o formulário não oferece — o título, hoje — SHALL ser preservado pela correção.

#### Scenario: O botão de confirmar nomeia o que a confirmação faz
- **WHEN** o usuário abre a correção de um pagamento já registrado
- **THEN** o único botão do formulário oferece salvar, e não antecipar nem pagar

#### Scenario: Uma correção com valor zero é recusada
- **WHEN** o usuário tenta corrigir um pagamento para valor zero ou negativo
- **THEN** a correção é recusada, com a mesma recusa que a criação daria

#### Scenario: Uma correção acima do devido é recusada
- **WHEN** o usuário tenta corrigir um pagamento para um valor maior que o teto da fatura selecionada
- **THEN** a confirmação fica indisponível, e o domínio recusa a operação caso ela o alcance

#### Scenario: Uma correção fora da janela de liquidação é recusada
- **WHEN** o usuário tenta corrigir um pagamento para uma data que a janela da fatura selecionada não admite
- **THEN** a correção é recusada, com a mesma recusa que a criação daria

#### Scenario: A correção preserva o que o formulário não oferece
- **WHEN** o usuário corrige um pagamento que carrega um dado que o formulário não exibe
- **THEN** esse dado permanece como estava, porque o formulário não o exibiu e não o pediu

### Requirement: Na correção o modo é fixo, e o conjunto de faturas decorre dele

O conjunto de faturas oferecido SHALL derivar do modo em vigor, e MUST NOT ser uma lista de status reescrita pela tela.

Numa criação o modo ainda não existe, e o conjunto SHALL ser o das faturas que aceitam **algum** pagamento. Numa correção o modo é o da operação que já foi escrita, e o conjunto SHALL ser o das faturas que aceitam **pagamento parcial** — as que ainda recebem compras.

Uma fatura `CLOSED` MUST NOT ser oferecida ao corrigir um pagamento parcial, e essa exclusão SHALL ser por construção. Apontar um parcial para uma fatura fechada produziria uma quitação de fato que nada marcaria `PAID`, porque marcar é do pagamento que quita e a correção não o é. A fronteira de escrita MUST NOT ser o lugar onde isso é recusado: ela aceita a escrita, por ela liquidar um passivo.

Trocar o cartão SHALL limpar a fatura selecionada antes de assumir o novo cartão, como na criação, de modo que o par (cartão novo, fatura do cartão anterior) MUST NOT ser observável em momento algum.

#### Scenario: A correção não oferece fatura fechada
- **WHEN** o usuário abre a correção de um pagamento parcial e examina as faturas oferecidas
- **THEN** nenhuma fatura `CLOSED` aparece, e apenas as que aceitam pagamento parcial são listadas

#### Scenario: A criação continua oferecendo a fatura fechada
- **WHEN** o usuário abre o formulário para registrar um pagamento novo
- **THEN** as faturas `CLOSED` continuam entre as oferecidas, e o estado decide o modo

#### Scenario: O domínio recusa a correção sobre fatura que não aceita parcial
- **WHEN** uma correção de pagamento parcial é solicitada sobre uma fatura que não aceita pagamento parcial
- **THEN** o domínio a recusa, independentemente de haver ou não tela que a ofereça

#### Scenario: Trocar o cartão numa correção
- **WHEN** o usuário troca o cartão dentro de uma correção
- **THEN** a fatura selecionada é substituída por uma do novo cartão, e o par intermediário não é observável

### Requirement: Abrir uma correção preserva; trocar uma seleção recalcula

O que o formulário exibe ao **abrir** em modo de correção SHALL ser o que a operação registra hoje: o cartão, a fatura, a conta pagadora, o valor, o valor que sai da conta quando as moedas diferem, e a data. Nada disso SHALL ser substituído por sugestão, por valor padrão ou por reposicionamento.

A data em particular MUST NOT ser reposicionada na abertura. A criação posiciona o dia de hoje dentro da janela da fatura porque não há data a preservar; numa correção há, e ela é a que a operação afirma.

O acervo de taxas oferece um valor provável enquanto o usuário cria um pagamento entre moedas; numa correção esse valor já existe e é fato, de modo que a sugestão MUST NOT sobrescrevê-lo nem apagá-lo quando não houver observação daquela data.

**Trocar** o cartão ou a fatura SHALL, ao contrário, recalcular: o valor e o valor de contrapartida já preenchidos SHALL ser limpos, o teto SHALL passar a ser o da fatura agora selecionada, e a data SHALL ser reposicionada na janela nova quando a atual não couber nela. Abrir não é intenção declarada; trocar é.

Trocada a moeda de uma das pontas durante a correção, o valor que estava no campo SHALL ser retirado — dígitos denominados numa moeda não sobrevivem sob o símbolo de outra.

#### Scenario: O formulário de correção chega preenchido
- **WHEN** o usuário abre a correção de um pagamento já registrado
- **THEN** o cartão, a fatura, a conta, o valor e a data aparecem como a operação os registra hoje

#### Scenario: A data registrada não é reposicionada na abertura
- **WHEN** o usuário abre a correção de um pagamento datado de um dia anterior a hoje, dentro da janela da fatura
- **THEN** a data exibida é a que a operação registra, e não a de hoje colocada na janela

#### Scenario: A sugestão do acervo não substitui o valor registrado
- **WHEN** o usuário abre a correção de um pagamento entre moedas e o acervo tem uma observação daquele par e daquela data
- **THEN** o campo exibe o valor que a operação registra, e não o que a observação implica

#### Scenario: A ausência de observação não apaga o valor registrado
- **WHEN** o usuário abre a correção de um pagamento entre moedas e o acervo nada tem a dizer sobre aquela data
- **THEN** o campo continua exibindo o valor que a operação registra

#### Scenario: Trocar a fatura limpa o valor e retoma o teto
- **WHEN** o usuário, corrigindo um pagamento, seleciona outra fatura
- **THEN** o valor e a contrapartida são limpos, e o teto passa a ser o da fatura agora selecionada

#### Scenario: Trocar para um cartão de outra moeda retira o valor do campo
- **WHEN** o usuário, corrigindo um pagamento, aponta a fatura para um cartão de outra moeda
- **THEN** o valor que estava no campo é retirado, e não permanece sob o símbolo da moeda nova

### Requirement: A quitação de uma fatura é história liquidada

A transação que quita uma fatura MUST NOT ser corrigível nem removível. Uma fatura `PAID` é história liquidada, e a operação que a produziu é parte dessa história — a proibição vale para os dois gestos igualmente, e não é omissão de escopo.

Isto MUST NOT ser regra nova nem regra própria desta superfície: ela já vale, e SHALL continuar valendo pelos mesmos donos — o estado da fatura, que retira as duas ações do detalhe da operação, e o veto do dono das dimensões na fronteira de escrita, que recusa tanto a reescrita quanto a remoção de qualquer transação sobre fatura `CLOSED` ou `PAID`. Oferecer a correção do pagamento parcial MUST NOT relaxar nenhum dos dois.

Um pagamento parcial cuja fatura foi fechada depois SHALL ficar congelado pela mesma regra, e não por uma exceção sua: o que decide é o estado da fatura no momento em que a ação seria oferecida, e não o modo em que a operação foi escrita.

O motivo do congelamento SHALL ser comunicado ao usuário, no lugar onde as ações apareceriam.

#### Scenario: A quitação não é corrigível
- **WHEN** o detalhe da transação que quitou uma fatura é aberto
- **THEN** nem correção nem remoção são oferecidas, e o motivo é exibido

#### Scenario: O parcial de uma fatura fechada depois é congelado
- **WHEN** o detalhe de um pagamento parcial cuja fatura foi fechada em seguida é aberto
- **THEN** nem correção nem remoção são oferecidas, pelo estado da fatura e sem regra adicional

#### Scenario: O razão recusa a reescrita sobre fatura quitada
- **WHEN** a reescrita de uma transação que carrega a dimensão de uma fatura `PAID` alcança a fronteira de escrita
- **THEN** ela é recusada, como já é hoje, sem que a oferta da correção do parcial tenha aberto exceção

### Requirement: Um pagamento sobre conta arquivada não é corrigível nem removível

Um pagamento SHALL ficar congelado — sem correção e sem remoção — quando **qualquer** uma das suas pernas postar em conta permanente arquivada, seja a conta pagadora, seja a conta `LIABILITY` do cartão.

Isto MUST NOT ser regra própria do pagamento: é a mesma regra que já congela qualquer operação sobre conta arquivada, e o pagamento a herda por ter pernas como qualquer outra. Corrigir é o mais grave dos dois — retargetar uma operação antiga devolveria saldo a uma conta arquivada sem que nenhuma escrita a tocasse.

#### Scenario: Pagamento cuja conta pagadora foi arquivada
- **WHEN** o detalhe de um pagamento cuja conta pagadora foi arquivada depois é aberto
- **THEN** nem correção nem remoção são oferecidas, e o motivo é exibido

#### Scenario: Pagamento cujo cartão foi arquivado
- **WHEN** o detalhe de um pagamento cujo cartão foi arquivado depois é aberto
- **THEN** nem correção nem remoção são oferecidas, pelo mesmo motivo e sem regra adicional

### Requirement: A correção de um pagamento não atravessa a fronteira entre features

O detalhe de uma operação SHALL alcançar o formulário de pagamento sem nomear o módulo de implementação que o hospeda. O pagamento nasce na feature de cartões e pertence a ela; o detalhe da operação vive noutra feature, e a arquitetura do projeto proíbe que uma implementação nomeie outra.

O acesso SHALL usar o ponto de entrada público da feature dona do formulário, e a correção SHALL entrar por um membro **próprio**, ao lado do que abre a criação. Um único membro cobrindo os dois modos teria de receber tudo como nulável e aceitaria estados que nada significam.

Qual formulário corrige uma operação SHALL decorrer do que a operação **é** — da sua natureza derivada das pernas —, e MUST NOT decorrer de uma contagem de pernas nem de qualquer efeito colateral de outra regra.

O razão MUST NOT tomar conhecimento de que um pagamento de fatura existe. A correção SHALL chegar até ele como um conjunto de pernas expressas por identidade de conta e dimensão, indistinguível de qualquer outra reescrita.

#### Scenario: O detalhe abre o formulário sem nomear a implementação que o hospeda
- **WHEN** as dependências da tela de detalhe são inspecionadas
- **THEN** ela alcança o formulário de pagamento pelo ponto de entrada público da feature de cartões, e não pela implementação

#### Scenario: A natureza da operação escolhe o formulário
- **WHEN** o usuário aciona a correção de uma operação cuja natureza é pagamento
- **THEN** o formulário aberto é o de pagamento de fatura, e não o de transferência nem o de transação

#### Scenario: O razão continua sem saber o que é um pagamento de fatura
- **WHEN** a reescrita produzida por uma correção de pagamento é inspecionada na fronteira de escrita
- **THEN** ela é um conjunto de pernas por identidade de conta e dimensão, sem nada que a identifique como pagamento de fatura
