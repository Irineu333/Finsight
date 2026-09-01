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

Morar no armazenamento do app MUST NOT torná-la invisível para quem escolheu uma pasta: o histórico
SHALL listá-la qualquer que seja o destino em vigor, lendo-a de onde ela está. Uma cópia que o app
escreve e não mostra não protege ninguém — é justamente ela que se procura quando um número deixou
de bater depois de uma atualização. Trocar de destino MUST NOT levá-la junto.

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

#### Scenario: A cópia aparece mesmo com uma pasta escolhida
- **WHEN** o histórico é aberto com as cópias indo para uma pasta apontada pelo usuário
- **THEN** a cópia anterior à última migração é listada e pode ser restaurada, mesmo estando no
  armazenamento do app, e trocar de pasta não a copia para lá

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
é necessária se **linha nova foi criada** desde ela. Editar um lançamento entra nessa conta, porque
reescrever suas partidas é criar outras.

Uma exclusão MUST NOT, por si só, tornar a cópia anterior insuficiente — ela é justamente a mais
completa das duas. Uma sequência de exclusões sem inclusões entre elas SHALL produzir uma única
cópia.

Uma alteração no lugar, que não cria linha nenhuma — renomear uma categoria, mudar o valor de um
orçamento —, MUST NOT obrigar a uma cópia nova. Nada que o usuário digitou desaparece com ela: a
cópia anterior continua contendo todos os lançamentos, e o rótulo que mudou é redigitável em um
toque. É o que permite medir o acervo por uma marca que só sobe, sem que uma tabela de controle
viaje dentro do backup.

#### Scenario: Exclusões em sequência
- **WHEN** o usuário exclui três transações seguidas, sem lançar nada entre elas
- **THEN** uma única cópia é capturada, antes da primeira exclusão

#### Scenario: Inclusão entre duas exclusões
- **WHEN** o usuário exclui uma transação, lança três novas e exclui outra
- **THEN** duas cópias são capturadas, e a segunda contém as três transações lançadas

#### Scenario: Aberturas sem lançamento
- **WHEN** o usuário abre o app por vários dias além do intervalo, sem lançar nada
- **THEN** nenhuma cópia nova é capturada, e a existente continua sendo a mais recente

#### Scenario: Alteração que não cria linha
- **WHEN** o usuário apenas renomeia uma categoria e abre o app depois do intervalo
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

A frase é do destino, e o armazenamento próprio do app não é o mesmo destino nas três
plataformas: no desktop ele é uma pasta no diretório do usuário, que nenhuma desinstalação
esvazia. O app MUST NOT dizer de um destino que ele morre com o app onde ele não morre. Isso
não reabre a fala por plataforma que o desenho recusa para a pasta apontada — lá o que se
ignora é o provedor que o app não tem como consultar; aqui é uma propriedade do
armazenamento em que o próprio app escreve, conhecida em tempo de compilação.

Onde as cópias fiquem numa pasta apontada pelo usuário, o app SHALL escrever direto nela, sem
subpasta própria no caminho, e MUST NOT tratar como suas quaisquer outros arquivos da pasta
escolhida: o nome filtra o que é candidato, e o conteúdo — verificado pelo mesmo gate do fluxo de
restauração — é o que autoriza mexer nele antes de qualquer remoção.

#### Scenario: O degrau padrão diz que morre com o app
- **WHEN** o usuário liga o cofre sem escolher pasta, numa plataforma móvel
- **THEN** a tela declara que as cópias ficam dentro do app e que desinstalar o app as leva junto

#### Scenario: O degrau padrão do desktop não é dito como o das móveis
- **WHEN** o usuário liga o cofre sem escolher pasta, no desktop
- **THEN** a tela **não** afirma que desinstalar o app leva as cópias junto — o armazenamento
  próprio do app ali é uma pasta no diretório do usuário, e nenhuma desinstalação a esvazia —
  e declara, em vez disso, que cobrir a perda do computador depende de uma pasta sincronizada
  ou do arquivo exportado

#### Scenario: Uma pasta com arquivos do usuário
- **WHEN** o usuário aponta uma pasta que contém arquivos seus
- **THEN** o app escreve as cópias direto nela, e os arquivos do usuário permanecem intocados porque
  nenhum deles passa pelo filtro de nome e pela confirmação de conteúdo que a retenção exige antes
  de apagar qualquer coisa

### Requirement: Uma captura só é dada como boa depois de a cópia ser lida de volta

O app SHALL ler de volta o arquivo que acabou de pousar no destino e submetê-lo às mesmas
verificações do fluxo de restauração antes de tratar a captura como bem-sucedida. Um destino que
aceita o arquivo prova que existe um arquivo com aquele nome, e não que o conteúdo dele é um banco
deste app: onde o destino escreve direto sob o nome final, sem trocar um arquivo pronto de lugar, um
processo interrompido no meio da escrita deixa um arquivo truncado sob um nome que o app reconhece.

Uma cópia **provadamente** ruim MUST NOT ser dada como sucesso: o instante da última captura
bem-sucedida MUST NOT se mover por causa dela, nenhuma cópia existente SHALL ser removida por uma
captura assim, e o app SHALL pedir ao destino que retire o arquivo — pedido que o destino pode
recusar, pelo mesmo portão que impede o app de apagar o que não prova ser seu. Um arquivo ruim
deixado no destino é o preço aceito; a cópia contada como boa não é.

Uma verificação que **não pôde ser feita** MUST NOT ser tratada como reprovação — o arquivo não pôde
ser lido de volta, ou a checagem não pôde correr —, e a captura SHALL valer como valia antes desta
leitura existir. Uma acusação falsa contra uma cópia possivelmente boa, feita no momento em que a
pessoa está sendo informada de que seu backup deu certo, é pior que a checagem que não aconteceu.

#### Scenario: O que pousou não é o acervo
- **WHEN** uma cópia pousa truncada, num destino que escreve direto sob o nome final
- **THEN** a captura é relatada como falha, o instante da última cópia bem-sucedida não se move, e
  nenhuma cópia antiga é removida

#### Scenario: A verificação não pôde correr
- **WHEN** a cópia pousa e o app não consegue lê-la de volta para verificar
- **THEN** a captura continua valendo como bem-sucedida, e nada é removido por causa disso

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

### Requirement: Fora dos três gatilhos, a pessoa põe uma cópia no destino de dois jeitos

A tela do histórico SHALL oferecer capturar uma cópia agora e trazer para o destino um arquivo que a
pessoa tenha em outro lugar. As duas SHALL estar presentes em todos os estados da tela — inclusive
sem nenhuma cópia e com o destino ilegível, que é justamente quando alguém as procura — e nenhuma
das duas SHALL escrever coisa alguma com o cofre desligado, como nenhum gatilho escreve.

Capturar agora MUST NOT ser recusada porque a cópia mais recente ainda representa o acervo. Essa
pré-condição existe para a ocasião que ninguém escolheu; num controle que a pessoa acabou de tocar
ela produz um botão que não faz nada, e a frase que o explicaria não seria nem verdadeira — há
tabelas cuja escrita não move a marca do acervo. Fora essa diferença a cópia é como qualquer outra:
a retenção a conta, e ela passa a ser a cópia a que o acervo corresponde.

Um arquivo importado SHALL passar pelas mesmas verificações do fluxo de restauração antes de pousar
no destino, SHALL receber um nome desta convenção — distinguível do nome de uma cópia que este app
capturou —, e MUST NOT ser tomado como a cópia de que o acervo em uso saiu. Pousado, ele é uma cópia
guardada como as outras: listada, contada pela retenção, restaurável e removível pelas mesmas ações.

#### Scenario: Capturar com o acervo inalterado
- **WHEN** o usuário toca em capturar agora sem ter lançado nada desde a última cópia
- **THEN** uma cópia nova é escrita assim mesmo, e passa a ser a mais recente

#### Scenario: Um arquivo que não é cópia deste app
- **WHEN** o usuário escolhe importar um arquivo que não passa nas verificações da restauração
- **THEN** nada é escrito no destino, e o histórico continua como estava

#### Scenario: As duas portas com o cofre desligado
- **WHEN** a tela do histórico é aberta com o cofre desligado
- **THEN** capturar e importar continuam à vista e não escrevem nada, e a tela diz que o cofre está
  desligado

### Requirement: A retenção nunca deixa o usuário sem nada

O app SHALL manter um número limitado de cópias por destino, e SHALL remover as mais antigas quando
esse número for excedido.

O limite SHALL ser configurável pelo usuário, incluindo a opção de não remover nada, e MUST NOT
depender do destino em vigor — o espaço é dele nos dois destinos, e um limite que a tela mostra é
um limite que ela deixa escolher.

A remoção SHALL acontecer depois de uma captura bem-sucedida, e MUST NOT ser executada em nenhum
outro momento: assim ela está sempre ancorada na existência de uma cópia nova, e o destino nunca
fica vazio por efeito da retenção.

Uma única varredura MUST NOT remover mais que um punhado de cópias, qualquer que seja a distância
entre o que o destino guarda e o que o limite agora permite; o destino SHALL convergir para o limite
ao longo das capturas seguintes. Uma varredura larga o bastante para absorver a diferença de uma vez
seria um segundo jeito, silencioso, de perder histórico: quem baixa o limite vê o número que
escolheu, nunca o que o destino guardava um instante antes. O punhado MUST NOT ser maior que o menor
limite que a tela oferece, de modo que nenhuma captura remova mais cópias do que a pessoa poderia
ter escolhido manter.

Apontar para uma pasta que já contém cópias que esta instalação não escreveu SHALL adiar uma
varredura, e exatamente uma: a da primeira captura que pousar naquela pasta. É o reencontro de um
acervo anterior (ver *O usuário aponta a pasta uma vez*), e decidir de quantas daquelas cópias a
retenção não tem mais espaço, antes de a pessoa ter tido a chance de ver o que está lá, seria a
retenção respondendo por ela. O adiamento SHALL valer só para o destino em que foi armado, e a
varredura seguinte SHALL rodar normalmente.

#### Scenario: Limite excedido
- **WHEN** uma captura conclui e o destino passa a ter mais cópias que o limite
- **THEN** as mais antigas são removidas até o limite, e a recém-capturada permanece

#### Scenario: Limite baixado sobre um destino cheio
- **WHEN** o usuário baixa o limite para cinco num destino que guarda vinte cópias
- **THEN** nenhuma captura remove as quinze de uma vez, e o destino chega a cinco ao longo das
  capturas seguintes

#### Scenario: Pasta reencontrada com um acervo anterior
- **WHEN** o usuário aponta para uma pasta que já contém cópias de uma instalação anterior
- **THEN** a primeira cópia capturada nela não remove nenhuma das que já estavam, e a captura
  seguinte volta a aplicar o limite

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

O app SHALL sinalizar quando esse instante for mais antigo que o intervalo configurado,
enquanto a verificação na abertura estiver ligada. Desligada, não há intervalo em vigor contra o
qual atrasar, e o aviso mandaria a pessoa abrir o app para uma cópia que abrir o app não produz.

#### Scenario: O cofre parou em silêncio
- **WHEN** a verificação na abertura está ligada e nenhuma captura acontece por um período maior
  que o intervalo configurado
- **THEN** a tela mostra o instante antigo e sinaliza que a última cópia está atrasada

#### Scenario: Sem verificação na abertura, nada está atrasado
- **WHEN** a verificação na abertura está desligada e a última captura é antiga
- **THEN** a tela mostra o instante e o destino, e não sinaliza atraso

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
