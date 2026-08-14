## ADDED Requirements

### Requirement: O servidor MCP vive no processo do app, e o banco tem um dono só

O servidor MCP SHALL executar dentro do mesmo processo que a aplicação desktop, sobre a mesma
instância de Koin e o mesmo banco que a UI usa.

Nenhum caminho — cliente, atalho, arquivo de descoberta ou ponte de transporte — SHALL poder
iniciar uma segunda instância da aplicação enquanto uma já estiver executando. Duas instâncias
sobre `~/.finance/finsight.db` teriam rastreadores de invalidação independentes, e uma escrita
feita por uma delas não apareceria na outra até um reinício.

#### Scenario: Escrita do agente aparece na tela aberta
- **WHEN** uma tool de escrita conclui com sucesso e a janela do app está exibindo uma
  superfície afetada por ela
- **THEN** a superfície reflete a mudança sem nenhuma ação do usuário, pelo mesmo mecanismo
  reativo que reflete uma escrita feita pela própria UI

#### Scenario: Segunda instância é recusada
- **WHEN** algo tenta iniciar a aplicação enquanto outra instância já é dona do banco
- **THEN** a segunda tentativa não abre o banco, e o processo já existente permanece o único dono

### Requirement: O transporte é loopback autenticado

O servidor SHALL escutar exclusivamente em `127.0.0.1`, numa porta efêmera obtida no momento
em que é ligado. Ele MUST NOT escutar em nenhum endereço alcançável de fora da máquina.

Toda requisição SHALL apresentar o token vigente; requisição sem token ou com token inválido
SHALL ser recusada sem alcançar o domínio.

#### Scenario: Endereço não roteável
- **WHEN** o servidor está ligado
- **THEN** o socket está associado apenas ao loopback, e nenhuma tentativa vinda de outra
  máquina alcança o servidor

#### Scenario: Requisição sem token
- **WHEN** uma requisição chega sem token ou com um token que não é o vigente
- **THEN** ela é recusada, nenhuma tool é executada e nenhuma leitura do banco acontece

### Requirement: O anúncio em disco existe enquanto o servidor existe

Enquanto ligado, o servidor SHALL publicar um arquivo de descoberta em `~/.finance/`, ao lado
do banco, contendo o endereço em que escuta, o token vigente e o identificador do processo.
O arquivo SHALL ter permissão restrita ao dono.

Ao ser desligado — por toggle, por encerramento do app ou por falha ao subir — o arquivo
SHALL ser removido. Um anúncio órfão aponta clientes para uma porta morta ou para uma porta
herdada por outro processo.

#### Scenario: Ligar publica o anúncio
- **WHEN** o servidor passa a escutar
- **THEN** o arquivo de descoberta existe com a porta e o token vigentes

#### Scenario: Desligar remove o anúncio
- **WHEN** o servidor deixa de escutar, por qualquer motivo
- **THEN** o arquivo de descoberta não existe mais

#### Scenario: Girar o token atualiza o anúncio
- **WHEN** o token é girado com o servidor ligado
- **THEN** o arquivo de descoberta passa a conter o token novo, e o anterior deixa de ser aceito

### Requirement: O servidor não depende de janela

O servidor MUST NOT depender de nenhum elemento de interface para funcionar: não SHALL ser
iniciado a partir de um escopo de composição, não SHALL ler estado de janela e MUST NOT exigir
interação visual para concluir uma tool.

Aprovação de uma operação, quando existir, SHALL viver no protocolo ou na política de
permissão — nunca num modal.

#### Scenario: Tool conclui com a janela minimizada
- **WHEN** uma tool é chamada e a janela do app está minimizada ou fora de foco
- **THEN** ela conclui normalmente, sem esperar por nenhuma interação

#### Scenario: Nenhuma tool abre modal
- **WHEN** a execução de qualquer tool é inspecionada
- **THEN** ela não apresenta modal, diálogo ou qualquer elemento que pressuponha um observador
