## ADDED Requirements

### Requirement: O cofre nasce desligado, e ligá-lo põe os três gatilhos em vigor

O app MUST NOT guardar cópias do acervo por conta própria antes de o usuário pedir. O cofre SHALL
começar desligado em toda instalação, inclusive nas que já usavam o backup manual, e MUST NOT
capturar por nenhum de seus gatilhos enquanto estiver desligado.

Ligar o cofre SHALL pôr os três gatilhos em vigor de uma vez, com valores padrão. O periódico e o
preventivo SHALL poder ser desligados depois, separadamente; o gatilho anterior a uma migração não
tem configuração própria e acompanha o cofre. Nenhum deles SHALL exigir configuração antes de
funcionar.

O app SHALL oferecer o cofre no momento em que o risco que ele cobre aparece, e não apenas numa
tela de configurações: a primeira ação destrutiva do usuário SHALL trazer a oferta junto da
confirmação, de modo que aceitar ali ligue o cofre por inteiro.

#### Scenario: Instalação nova não guarda nada
- **WHEN** o app é instalado e usado sem que o usuário visite a tela de backup
- **THEN** nenhuma cópia do acervo é escrita pelo cofre, em nenhum destino

#### Scenario: Um sim liga tudo
- **WHEN** o usuário aceita a oferta apresentada junto de uma confirmação de exclusão
- **THEN** o cofre passa a valer com o gatilho periódico e o preventivo ligados, sem que ele precise
  abrir a tela de backup

#### Scenario: Um gatilho pode ser desligado sem o outro
- **WHEN** o usuário desliga o gatilho periódico
- **THEN** o preventivo continua capturando antes das ações destrutivas

### Requirement: Uma atualização que reescreva o banco é precedida de uma cópia

Com o cofre ligado, o app SHALL capturar uma cópia antes de aplicar uma cadeia de migrações de
schema, o que só ocorre numa atualização do app.

Essa cópia SHALL ir para o armazenamento próprio do app, e não para a pasta apontada pelo usuário:
na subida do app o destino externo pode não estar acessível, e a captura não SHALL depender dele.
Ela MUST NOT entrar na contagem da retenção, e SHALL ser substituída apenas pela cópia da migração
seguinte — o dano que ela existe para desfazer é uma corrupção que conclui sem erro e se descobre
dias depois.

Uma captura que falhe MUST NOT impedir a atualização de concluir nem o app de abrir.

#### Scenario: Atualização com migração
- **WHEN** o app é atualizado com o cofre ligado e a nova versão traz migração de schema
- **THEN** uma cópia do acervo anterior é capturada antes de a migração rodar

#### Scenario: Cofre desligado
- **WHEN** o app é atualizado com o cofre desligado e a nova versão traz migração de schema
- **THEN** nenhuma cópia é capturada

#### Scenario: A cópia sobrevive à retenção
- **WHEN** capturas periódicas suficientes acontecem para exceder o limite de retenção
- **THEN** a cópia anterior à última migração continua existindo

#### Scenario: Atualização sem migração
- **WHEN** o app é atualizado e a versão de schema não muda
- **THEN** nenhuma cópia é capturada por esse gatilho

### Requirement: O periódico captura na abertura, e a tela promete isso

Com o cofre ligado, o app SHALL capturar uma cópia na primeira vez que for aberto depois de
decorrido o intervalo configurado, cujo padrão SHALL ser de 3 dias.

A tela MUST NOT prometer captura em segundo plano nem periodicidade garantida: nenhuma das
plataformas suportadas assegura execução fora do uso do app, e uma promessa de "a cada N dias"
seria uma frase que o app não pode cumprir. O que a tela SHALL dizer é que a cópia acontece quando
o app é aberto.

#### Scenario: Intervalo vencido
- **WHEN** o usuário abre o app 5 dias depois da última cópia, com intervalo de 3 dias
- **THEN** uma cópia é capturada nessa abertura

#### Scenario: Intervalo não vencido
- **WHEN** o usuário abre o app 1 dia depois da última cópia, com intervalo de 3 dias
- **THEN** nenhuma cópia é capturada

#### Scenario: Meses fechado
- **WHEN** o app passa meses sem ser aberto
- **THEN** nenhuma cópia é capturada nesse período, e a tela não afirma que alguma foi

### Requirement: Uma cópia serve enquanto nada foi acrescentado depois dela

Nenhum gatilho SHALL capturar quando a cópia mais recente ainda representa o acervo: uma captura só
é necessária se dado foi **acrescentado** ou alterado desde ela.

Uma exclusão MUST NOT, por si só, tornar a cópia anterior insuficiente — ela é justamente a mais
completa das duas. Uma sequência de exclusões sem inclusões entre elas SHALL produzir uma única
cópia.

#### Scenario: Exclusões em sequência
- **WHEN** o usuário exclui três transações seguidas, sem lançar nada entre elas
- **THEN** uma única cópia é capturada, antes da primeira exclusão

#### Scenario: Inclusão entre duas exclusões
- **WHEN** o usuário exclui uma transação, lança três novas e exclui outra
- **THEN** duas cópias são capturadas, e a segunda contém as três transações lançadas

#### Scenario: Aberturas sem lançamento
- **WHEN** o usuário abre o app por vários dias além do intervalo, sem lançar nada
- **THEN** nenhuma cópia nova é capturada, e a existente continua sendo a mais recente

### Requirement: O preventivo captura antes da ação, ou a ação não é preventiva

Uma cópia preventiva SHALL ser capturada **antes** de a ação destrutiva alterar o banco. Uma cópia
posterior MUST NOT ser apresentada como proteção contra aquela ação: ela registra o estado já
mutilado.

O gatilho SHALL cobrir as ações que removem trabalho que o usuário não redigita — a restauração de
um backup, a exclusão de uma transação, de um parcelamento, de uma fatura, de uma moeda e de uma
cotação. Ele MUST NOT cobrir as exclusões que o domínio já recusa quando há histórico: conta,
cartão, categoria, orçamento e recorrente só são excluíveis quando nada de digitado vai junto.

Qual ação dispara SHALL ser decidido no domínio, por classificação. A tela decide apenas **se** o
gatilho vale, nunca **quais** ações ele cobre, e uma ação destrutiva acrescentada ao app depois
SHALL ser coberta por pertencer a uma classe, sem alteração de tela.

Se a captura preventiva falhar, o app SHALL dizer que falhou e MUST NOT executar a ação destrutiva
sem que o usuário decida prosseguir sem cópia.

#### Scenario: A conta apagada está no arquivo
- **WHEN** o usuário exclui um parcelamento de 12 transações com o preventivo ligado
- **THEN** existe uma cópia anterior à exclusão que contém as 12 transações

#### Scenario: Exclusão que o domínio recusa não dispara nada
- **WHEN** o usuário exclui uma categoria sem lançamentos, orçamento ou recorrente
- **THEN** nenhuma cópia é capturada, porque nada que o usuário digitou é removido

#### Scenario: Captura preventiva que falha
- **WHEN** a captura preventiva falha por falta de espaço
- **THEN** o app diz que não conseguiu guardar a cópia, e a exclusão só acontece se o usuário
  confirmar prosseguir sem ela

#### Scenario: Ação nova nasce coberta
- **WHEN** uma exclusão nova é acrescentada ao app dentro de uma classe já coberta
- **THEN** ela dispara a cópia preventiva sem que a tela de backup mude

### Requirement: As cópias ficam num destino, e a tela diz o que ele não cobre

O app SHALL guardar as cópias em um de dois destinos: seu próprio armazenamento privado, que é o
destino ao ligar o cofre, ou uma pasta apontada pelo usuário.

A tela SHALL declarar, para o destino em vigor, o que ele **não** protege. Um destino que não
sobrevive à desinstalação do app SHALL ser apresentado como tal, e o app MUST NOT apresentar o
cofre como proteção contra perda do aparelho quando as cópias ficam apenas nele.

Onde as cópias fiquem numa pasta apontada pelo usuário, o app SHALL escrever dentro de uma subpasta
própria, e MUST NOT tratar como suas quaisquer outros arquivos da pasta escolhida.

#### Scenario: O degrau padrão diz que morre com o app
- **WHEN** o usuário liga o cofre sem escolher pasta
- **THEN** a tela declara que as cópias ficam dentro do app e que desinstalar o app as leva junto

#### Scenario: Subpasta própria
- **WHEN** o usuário aponta uma pasta que contém arquivos seus
- **THEN** o app cria uma subpasta própria e escreve apenas nela, e os arquivos do usuário na pasta
  escolhida permanecem intocados

### Requirement: O usuário aponta a pasta uma vez, e o app a reencontra

Escolhida a pasta, o app SHALL escrever, listar e remover cópias nela sem pedir nada ao usuário a
cada operação.

O app SHALL oferecer o mesmo apontamento em três situações, e MUST NOT tratar nenhuma delas como
erro: configurar pela primeira vez, reconectar quando o acesso à pasta tiver sido perdido, e
reencontrar um histórico existente numa instalação nova. Apontar uma pasta que já contém cópias
SHALL fazer o histórico daquela pasta aparecer por inteiro.

O app MUST NOT depender de o acesso à pasta sobreviver à desinstalação: os arquivos sobrevivem, e o
apontamento é o que os torna alcançáveis de novo.

#### Scenario: Reinstalação reencontra o acervo
- **WHEN** o usuário reinstala o app e aponta a mesma pasta que usava antes
- **THEN** todas as cópias que estão lá aparecem no histórico, e qualquer uma delas pode ser
  restaurada

#### Scenario: Escrever não pede nada
- **WHEN** o gatilho periódico dispara com uma pasta já apontada
- **THEN** a cópia é escrita sem que nenhum seletor de arquivos seja apresentado

### Requirement: Perder o acesso à pasta é dito, e não deixa o usuário sem cópia

O app SHALL verificar o acesso à pasta ao ser aberto, e não apenas no momento de escrever, de modo
que a perda seja descoberta antes do próximo gatilho.

Perdido o acesso, o app SHALL dizer isso ao usuário e oferecer as duas saídas — reapontar a pasta,
ou passar a guardar dentro do app. Enquanto a decisão não vier, o app SHALL continuar capturando no
seu próprio armazenamento, declarando que a situação é provisória. O app MUST NOT deixar de
capturar enquanto espera uma resposta, e MUST NOT silenciar a perda.

#### Scenario: Acesso revogado
- **WHEN** o usuário revoga, no sistema, o acesso do app à pasta escolhida
- **THEN** na abertura seguinte o app informa a perda, oferece reapontar ou guardar dentro do app,
  e as capturas continuam acontecendo no armazenamento próprio

#### Scenario: A pasta foi apagada
- **WHEN** a pasta escolhida deixa de existir
- **THEN** o app informa a perda e nenhuma captura é dada como bem-sucedida num destino que não
  existe

### Requirement: O histórico é o que está na pasta

O histórico SHALL ter tela própria, alcançada a partir da tela de backup, e MUST NOT ser uma seção
dentro dela: a lista cresce com a retenção configurada, tem ações por item e é o que se consulta ao
reencontrar um acervo — nada disso cabe numa tela de configuração.

De cada cópia a lista SHALL mostrar o quanto baste para reconhecê-la sem abri-la: quando foi feita,
seu tamanho, e o que a distingue das demais. Uma cópia capturada antes de uma migração SHALL ser
identificada como tal, porque é a que alguém procura quando um número deixou de bater depois de uma
atualização.

Cada cópia SHALL poder ser restaurada e removida a partir dessa tela, e SHALL poder ser entregue a
um destino escolhido na hora, pelo mesmo caminho da exportação manual — o que permite tirar do
aparelho uma cópia que o cofre guardou, sem capturar outra.

O histórico exibido SHALL ser lido do destino no momento em que a tela é aberta. Ele MUST NOT ser
mantido numa tabela do banco: o backup contém todo o acervo, e uma restauração faria esse registro
voltar no tempo e passar a divergir da pasta.

O app SHALL tolerar que o usuário tenha apagado, movido ou renomeado arquivos por fora, e o
histórico SHALL refletir o que existe.

O nome do arquivo MUST NOT ser autoridade sobre o que ele é — o sistema de arquivos pode alterá-lo
para evitar conflito. Antes de restaurar ou de remover um arquivo, o app SHALL decidir pelo
conteúdo, com as mesmas verificações do fluxo de restauração manual.

#### Scenario: Arquivo apagado por fora
- **WHEN** o usuário apaga uma cópia pelo gerenciador de arquivos e volta ao app
- **THEN** o histórico deixa de listá-la, sem erro

#### Scenario: Nenhuma cópia ainda
- **WHEN** a tela de histórico é aberta logo depois de o cofre ser ligado
- **THEN** ela declara que ainda não há cópia e diz quando a primeira acontece, sem listar nada

#### Scenario: Uma cópia guardada sai do aparelho
- **WHEN** o usuário escolhe entregar uma cópia do histórico a um destino
- **THEN** aquele arquivo é entregue como está, sem que uma nova captura seja feita

#### Scenario: Só o que é do app é removido
- **WHEN** a retenção remove cópias antigas de uma pasta que contém outros arquivos
- **THEN** apenas arquivos confirmados como cópias escritas por este app são removidos

### Requirement: A retenção nunca deixa o usuário sem nada

O app SHALL manter um número limitado de cópias por destino, e SHALL remover as mais antigas quando
esse número for excedido.

O limite SHALL ser configurável pelo usuário, incluindo a opção de não remover nada, e MUST NOT
depender do destino em vigor — o espaço é dele nos dois destinos, e um limite que a tela mostra é
um limite que ela deixa escolher.

A remoção SHALL acontecer depois de uma captura bem-sucedida, e MUST NOT ser executada em nenhum
outro momento: assim ela está sempre ancorada na existência de uma cópia nova, e o destino nunca
fica vazio por efeito da retenção.

#### Scenario: Limite excedido
- **WHEN** uma captura conclui e o destino passa a ter mais cópias que o limite
- **THEN** as mais antigas são removidas até o limite, e a recém-capturada permanece

#### Scenario: Retenção desligada
- **WHEN** o usuário escolhe não remover nada
- **THEN** nenhuma cópia é removida pelo app, por idade ou por contagem

#### Scenario: Captura falha não apaga nada
- **WHEN** uma captura falha
- **THEN** nenhuma cópia existente é removida

### Requirement: A tela diz quando foi o último backup que deu certo

A tela SHALL exibir, sempre que o cofre estiver ligado, o instante da última captura
bem-sucedida e o destino em que ela ocorreu.

O app MUST NOT apresentar o cofre como ativo sem exibir essa informação. Um cofre pode parar de
funcionar sem defeito e sem ação do usuário — acesso revogado, pasta removida, app suspenso pelo
sistema —, e esse instante é o único meio pelo qual a pessoa descobre que a proteção parou.

O app SHALL sinalizar quando esse instante for mais antigo que o intervalo configurado.

#### Scenario: O cofre parou em silêncio
- **WHEN** nenhuma captura acontece por um período maior que o intervalo configurado
- **THEN** a tela mostra o instante antigo e sinaliza que a última cópia está atrasada

#### Scenario: Nunca capturou
- **WHEN** o cofre acaba de ser ligado e nenhuma cópia foi feita ainda
- **THEN** a tela diz que ainda não há cópia, e não exibe uma data qualquer

### Requirement: Trocar de pasta copia, e nunca depende da pasta antiga

Ao trocar de pasta, o app SHALL oferecer levar o histórico para o destino novo. A migração SHALL
copiar, e MUST NOT remover as cópias da pasta anterior — uma falha no meio deixa arquivos nos dois
lugares, nunca em nenhum.

A migração SHALL levar apenas as cópias mais recentes que a retenção do destino comporta.

Trocar de pasta SHALL funcionar mesmo quando a pasta anterior estiver inacessível: nesse caso não
há o que migrar, e o app MUST NOT impedir a escolha de um destino novo por isso.

#### Scenario: Migração interrompida
- **WHEN** a cópia do histórico falha no meio
- **THEN** os arquivos já copiados permanecem no destino novo, todos permanecem no anterior, e o
  app diz o que aconteceu

#### Scenario: Troca com a pasta antiga inacessível
- **WHEN** o usuário escolhe uma pasta nova depois de perder o acesso à anterior
- **THEN** a nova pasta passa a valer, sem migração, e o cofre volta a capturar
