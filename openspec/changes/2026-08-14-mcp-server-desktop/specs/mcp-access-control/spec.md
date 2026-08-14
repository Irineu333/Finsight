## ADDED Requirements

### Requirement: O servidor nasce desligado e só é ligado por ato explícito

O servidor MCP SHALL estar desligado por padrão, tanto numa instalação nova quanto numa
atualização de instalação existente. Ele SHALL passar a escutar apenas depois de o usuário
habilitá-lo na configuração.

Habilitar MUST NOT ser sugerido por prompt, banner ou qualquer chamada que pressione o
usuário: a capacidade abre uma porta com poder sobre o razão, e ninguém deve ganhá-la sem a
ter pedido.

#### Scenario: Instalação nova
- **WHEN** a aplicação executa pela primeira vez
- **THEN** nenhuma porta é aberta, e o servidor aparece como desligado na configuração

#### Scenario: Atualização de instalação existente
- **WHEN** um usuário que já tinha dados atualiza para a versão que traz a capacidade
- **THEN** o servidor está desligado, e nada é apresentado ao usuário até que ele procure a
  configuração

### Requirement: Desligado significa nada escutando

Com o servidor desligado, MUST NOT existir socket em escuta. Desligar MUST NOT alterar o token:
se desligar por um instante invalidasse todo cliente configurado, o usuário aprenderia a nunca
desligar, e o interruptor de segurança deixaria de ser usado.

#### Scenario: Desligar fecha
- **WHEN** o usuário desliga o servidor
- **THEN** o socket é fechado, e nenhuma requisição é atendida

#### Scenario: Religar preserva os clientes
- **WHEN** o usuário desliga e liga novamente, sem girar o token
- **THEN** o token continua o mesmo, e um cliente já configurado volta a funcionar sem ser
  reconfigurado

### Requirement: O endereço é estável entre execuções

A porta SHALL ser persistida e reusada a cada vez que o servidor sobe, e SHALL ser exibida na
configuração. Ela MUST NOT ser sorteada a cada execução.

Um endereço que muda a cada reinício quebra todo cliente configurado, porque a configuração de
um cliente MCP contém a URL. Isso reproduziria exatamente o "estado funcionando que não
funciona" que a configuração existe para evitar.

Quando a porta persistida estiver ocupada por outro processo, o servidor MUST NOT escolher outra
em silêncio: SHALL falhar ao subir e apresentar o conflito na configuração, com a possibilidade
de escolher outra porta deliberadamente.

#### Scenario: Reinício não quebra clientes
- **WHEN** o app é fechado e aberto novamente com o servidor habilitado
- **THEN** ele volta a escutar no mesmo endereço, e os clientes configurados continuam
  funcionando

#### Scenario: Porta ocupada
- **WHEN** a porta persistida está em uso por outro processo
- **THEN** o servidor não sobe, a configuração mostra o conflito, e nenhuma outra porta é
  assumida automaticamente

### Requirement: A permissão é uma decisão separada, e nasce em somente leitura

O nível de permissão SHALL ser independente do toggle de habilitação, com dois valores:
**somente leitura** e **leitura e escrita**. Ao ser habilitado pela primeira vez, o servidor
SHALL estar em somente leitura.

Em somente leitura, as tools de escrita MUST NOT ser anunciadas na listagem de tools. Anunciar
uma capacidade que será recusada faz o consumidor insistir numa operação impossível, e nada na
resposta lhe diz que o problema é permissão e não formulação.

A mudança de nível em tempo de execução SHALL ser notificada aos clientes que assinaram
mudanças na lista de tools, pelo mecanismo de assinatura do protocolo.

Ainda assim, a permissão SHALL ser aplicada também na execução: uma tool de escrita chamada em
somente leitura SHALL ser recusada antes de alcançar o domínio, nomeando a permissão como
motivo. Esconder é para o consumidor bem comportado; recusar é o que vale.

#### Scenario: Primeira habilitação
- **WHEN** o usuário habilita o servidor pela primeira vez
- **THEN** o nível vigente é somente leitura, e a listagem de tools não contém nenhuma escrita

#### Scenario: Mudança de nível notifica
- **WHEN** o usuário muda o nível para leitura e escrita com um cliente conectado e assinado
- **THEN** o cliente é notificado de que a lista de tools mudou, e passa a enxergar as escritas

#### Scenario: Escrita recusada por permissão
- **WHEN** uma tool de escrita é chamada com o nível em somente leitura
- **THEN** ela é recusada por permissão, nada é gravado, e a resposta distingue essa recusa de
  uma recusa de regra de negócio

#### Scenario: Leitura nunca depende do nível
- **WHEN** uma tool de leitura é chamada em qualquer um dos dois níveis
- **THEN** ela executa normalmente

### Requirement: O token é credencial de portador, e o desvio da autorização do MCP é declarado

Toda requisição SHALL apresentar o token no header de autorização, no esquema de portador. O
token MUST NOT ser aceito em query string, onde vazaria para histórico e log.

O token SHALL ser gerado por fonte criptograficamente segura, com no mínimo 128 bits de
entropia, e SHALL ser comparado em tempo constante. Ele MUST NOT ser gravado em log, em
telemetria ou no registro de atividade.

Requisição sem token ou com token inválido SHALL ser recusada com `401`, e a resposta SHALL
trazer o desafio de autorização apontando o documento de metadados do recurso protegido — para
que um cliente conforme falhe de forma legível em vez de receber uma recusa opaca.

Este esquema é um **desvio deliberado** da especificação de autorização do MCP, que é opcional e
cuja conformidade é recomendada para transportes HTTP: ela descreve o servidor como resource
server OAuth 2.1, com metadados de recurso protegido e tokens emitidos por um servidor de
autorização. Montar OAuth 2.1 para um servidor loopback de usuário único é desproporcional, e o
desvio é aceito — mas SHALL constar da documentação do servidor, para que ele seja uma escolha
registrada e não uma omissão.

#### Scenario: Token só no header
- **WHEN** uma requisição traz o token em query string
- **THEN** ela é recusada, e o token é tratado como comprometido

#### Scenario: Recusa legível
- **WHEN** uma requisição chega sem token
- **THEN** a resposta é `401` com o desafio de autorização, e não uma recusa sem indicação

#### Scenario: Token não aparece em registro algum
- **WHEN** o registro de atividade e os logs são inspecionados após uma sessão completa
- **THEN** o token não aparece em nenhum deles

### Requirement: O token é revogável sem desligar o servidor

SHALL existir um comando explícito de girar o token. Girado, o token anterior SHALL deixar de
ser aceito imediatamente, e o novo SHALL passar a valer sem que o servidor precise ser
desligado.

O token MUST NOT ser exibido em claro por padrão na configuração.

#### Scenario: Revogação imediata
- **WHEN** o usuário gira o token e um cliente tenta usar o token anterior
- **THEN** a requisição é recusada, e o servidor continua atendendo quem usa o token novo

### Requirement: Habilitar entrega uma configuração que continua válida

Ao habilitar, a configuração SHALL apresentar o que um cliente MCP precisa para conectar —
endereço do endpoint e header de autorização — pronto para ser copiado.

O que é apresentado SHALL permanecer válido enquanto o token e a porta não mudarem. Um trecho
que expira no próximo reinício é pior que nenhum, porque o usuário só descobre quando falha.

Quando o token for girado, o trecho apresentado SHALL passar a conter o token vigente.

#### Scenario: Primeiro on
- **WHEN** o usuário habilita o servidor
- **THEN** a tela apresenta a configuração de cliente completa, com ação de copiar

#### Scenario: Configuração sobrevive ao reinício
- **WHEN** o usuário cola a configuração num cliente, fecha o app e o abre novamente
- **THEN** o cliente conecta sem ser reconfigurado

#### Scenario: Configuração acompanha o token
- **WHEN** o token é girado
- **THEN** o trecho apresentado passa a conter o token vigente
