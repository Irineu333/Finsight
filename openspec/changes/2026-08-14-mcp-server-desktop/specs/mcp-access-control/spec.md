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
- **THEN** nenhuma porta é aberta, nenhum arquivo de descoberta é publicado, e o servidor
  aparece como desligado na configuração

#### Scenario: Atualização de instalação existente
- **WHEN** um usuário que já tinha dados atualiza para a versão que traz a capacidade
- **THEN** o servidor está desligado, e nada é apresentado ao usuário até que ele procure a
  configuração

### Requirement: Desligado significa nada escutando e nada anunciado

Com o servidor desligado, MUST NOT existir socket em escuta nem arquivo de descoberta. Um
estado intermediário — não escutar mas continuar anunciando — MUST NOT existir.

Desligar MUST NOT alterar o token. Se desligar por um instante invalidasse todo cliente
configurado, o usuário aprenderia a nunca desligar, e o interruptor de segurança deixaria de
ser usado.

#### Scenario: Desligar fecha e apaga
- **WHEN** o usuário desliga o servidor
- **THEN** o socket é fechado e o arquivo de descoberta é removido

#### Scenario: Religar preserva os clientes
- **WHEN** o usuário desliga e liga novamente, sem girar o token
- **THEN** o token continua o mesmo, e um cliente já configurado volta a funcionar sem ser
  reconfigurado

### Requirement: A permissão é uma decisão separada, e nasce em somente leitura

O nível de permissão SHALL ser independente do toggle de habilitação, com dois valores:
**somente leitura** e **leitura e escrita**. Ao ser habilitado pela primeira vez, o servidor
SHALL estar em somente leitura.

Em somente leitura, toda tool que escreve SHALL ser recusada antes de alcançar o domínio, e a
recusa SHALL nomear a permissão como motivo — não deve ser confundida com uma recusa do
domínio.

#### Scenario: Primeira habilitação
- **WHEN** o usuário habilita o servidor pela primeira vez
- **THEN** o nível vigente é somente leitura, e nenhuma escrita é aceita até que ele mude o
  nível deliberadamente

#### Scenario: Escrita recusada por permissão
- **WHEN** uma tool de escrita é chamada com o nível em somente leitura
- **THEN** ela é recusada por permissão, nada é gravado, e a resposta distingue essa recusa de
  uma recusa de regra de negócio

#### Scenario: Leitura nunca depende do nível
- **WHEN** uma tool de leitura é chamada em qualquer um dos dois níveis
- **THEN** ela executa normalmente

### Requirement: O token é revogável sem desligar o servidor

SHALL existir um comando explícito de girar o token. Girado, o token anterior SHALL deixar de
ser aceito imediatamente, e o novo SHALL passar a valer sem que o servidor precise ser
desligado.

O token MUST NOT ser exibido em claro por padrão na configuração.

#### Scenario: Revogação imediata
- **WHEN** o usuário gira o token e um cliente tenta usar o token anterior
- **THEN** a requisição é recusada, e o servidor continua atendendo quem usa o token novo

### Requirement: Habilitar entrega uma configuração utilizável

Ao habilitar, a configuração SHALL apresentar o que um cliente MCP precisa para conectar,
pronto para ser copiado — não apenas o número da porta.

Um servidor ligado cujo token o usuário nunca levou a lugar nenhum é um estado que se declara
funcionando sem funcionar.

#### Scenario: Primeiro on
- **WHEN** o usuário habilita o servidor
- **THEN** a tela apresenta o trecho de configuração de cliente completo, com ação de copiar

#### Scenario: Configuração acompanha o token
- **WHEN** o token é girado
- **THEN** o trecho apresentado passa a conter o token vigente
