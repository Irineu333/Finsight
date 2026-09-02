## ADDED Requirements

### Requirement: Uma operação é nomeada por título, categoria e forma, nessa ordem

Toda superfície que nomeia uma operação SHALL usar a mesma precedência: o **título** que a
operação tem; na falta dele, o nome da sua **categoria**; e na falta dos dois, a **forma** da
operação — o que ela é, derivada dos tipos de conta das suas pernas.

A precedência SHALL ser uma só. Uma superfície MUST NOT inverter a ordem para si — deixar a
forma vencer um título que a operação tem torna o título invisível em uma tela e visível em
outra, e o usuário que o escreveu não tem como saber qual das duas está certa.

A natureza da operação MUST NOT depender do texto exibido. Ela SHALL continuar legível pelos
sinais que não competem com o nome — o ícone e a cor —, de modo que nomear uma transferência
pela sua razão não a faça passar por outra coisa.

#### Scenario: Operação com título próprio
- **WHEN** uma operação que tem título é exibida em qualquer superfície que a nomeie
- **THEN** o nome exibido é o seu título, e não a sua forma nem o nome da sua categoria

#### Scenario: Operação sem título, com categoria
- **WHEN** uma operação sem título próprio, categorizada como "Mercado", é exibida
- **THEN** o nome exibido é "Mercado"

#### Scenario: Operação sem título e sem categoria
- **WHEN** uma transferência sem título e sem categoria é exibida numa lista
- **THEN** o nome exibido é a sua forma, e a lista continua a distinguindo por ícone e cor

#### Scenario: A lista e o detalhe concordam
- **WHEN** a mesma operação com título é aberta no detalhe a partir da lista
- **THEN** as duas superfícies a nomeiam pelo mesmo texto

### Requirement: Os dois primeiros elos têm dono único; o terceiro é de quem exibe

A escolha entre **título e categoria** SHALL ter um dono único, e MUST NOT ser reescrita por
cada superfície — cópias de uma mesma regra divergem sem que nada acuse. Esse dono SHALL
responder com a ausência quando a operação não tem nem um nem outro, em vez de decidir sozinho
o que fazer com ela.

A escolha do **terceiro elo** SHALL pertencer à superfície que o exibe. Ela é a única que sabe
o que já foi dito ao redor: uma lista nomeia a operação por inteiro, enquanto um cabeçalho que
já anunciou a natureza na linha acima precisa de um texto que a **complete** em vez de repeti-la.
Um terceiro elo único para todas as superfícies obrigaria uma delas a exibir um texto errado
para o seu contexto.

Pela mesma razão o terceiro elo SHALL ser resolvido onde textos localizados são resolvidos, e
MUST NOT obrigar a camada dona dos dois primeiros elos a conhecer recursos de tela.

#### Scenario: A regra entre título e categoria não é reescrita
- **WHEN** as superfícies que nomeiam uma operação são inspecionadas
- **THEN** todas consultam o mesmo dono para escolher entre título e categoria, e nenhuma reimplementa a escolha

#### Scenario: A lista nomeia a operação por inteiro
- **WHEN** uma transferência sem título aparece numa lista de operações
- **THEN** o nome exibido a identifica sozinho, sem depender de outra linha para fazer sentido

#### Scenario: O cabeçalho completa a natureza em vez de repeti-la
- **WHEN** o detalhe de uma transferência sem título é aberto, com a natureza já anunciada na primeira linha
- **THEN** a segunda linha diz o que a primeira não disse, e não repete a mesma palavra

### Requirement: Nenhuma superfície nomeia uma ausência com um literal de reserva

Uma operação sem nome MUST NOT receber um literal genérico de reserva. Uma superfície SHALL ou
nomeá-la pela sua forma, ou **omitir** a linha — nomear uma ausência é dar-lhe um nome que o
usuário não escolheu e não reconhece.

Um literal de reserva MUST NOT ser mantido como rede de segurança para um caso que as regras do
domínio impedem de acontecer. Manter um caminho que nada alcança esconde a garantia que o torna
inalcançável, e é a forma mais barata de essa garantia se perder sem que ninguém perceba.

#### Scenario: Operação sem nome numa lista
- **WHEN** uma operação que não tem título nem categoria é exibida numa lista
- **THEN** ela é nomeada pela sua forma, e nenhum literal de reserva é exibido

#### Scenario: Operação sem nome num cabeçalho que já anunciou a natureza
- **WHEN** o detalhe de um gasto sem título e sem categoria é aberto
- **THEN** a linha do nome é omitida, e nenhum literal de reserva ocupa o seu lugar

#### Scenario: O literal não sobrevive como rede de segurança
- **WHEN** o código que nomeia operações é inspecionado
- **THEN** não existe literal genérico de reserva em nenhum dos caminhos

### Requirement: Um invariante garantido pelo domínio é afirmado, não mascarado

Uma superfície SHALL afirmar o invariante que o dono de uma regra lhe garante — que ela nunca
receberá uma operação sem nome — e SHALL falhar quando ele for violado, em vez de exibir um
texto de reserva que o esconde. A falha é o que denuncia um caminho de escrita novo que passe ao
largo do dono da regra — mascará-la produziria uma tela silenciosamente errada.

Isto SHALL valer **apenas** onde a garantia é do domínio. Onde ela vier apenas de uma tela — um
botão que se desabilita, uma validação que só decide o que oferecer —, a superfície SHALL
fornecer um terceiro elo real, porque apostar a estabilidade de uma tela na disciplina de outra
não é uma garantia.

#### Scenario: Modelo cujo formulário é o dono único da regra
- **WHEN** um modelo cujo dono de regra exige título ou categoria é exibido sem nenhum dos dois
- **THEN** a leitura do seu nome falha, em vez de devolver um texto de reserva

#### Scenario: Modelo cuja garantia vem apenas da tela
- **WHEN** um modelo cuja exigência de título ou categoria só existe na habilitação de um botão é exibido sem nenhum dos dois
- **THEN** ele é nomeado pela sua forma, e a leitura não falha
