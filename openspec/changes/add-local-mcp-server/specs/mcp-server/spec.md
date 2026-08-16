## ADDED Requirements

### Requirement: O servidor vive com o app, e não como segundo programa

O servidor MCP SHALL ser embutido no processo do app desktop: ele sobe quando o app sobe e
encerra quando o app encerra. O projeto MUST NOT distribuir um segundo executável, script ou
serviço para que um agente alcance o app.

Enquanto o app não estiver aberto, a superfície MCP simplesmente não existe — e isso SHALL
ser dito na seção de configurações, porque um agente que não conecta precisa saber que a
causa é o app fechado, e não uma configuração errada.

A escolha não é de conveniência: o `invalidationTracker` do Room é **do processo**. Um
segundo processo escrevendo no mesmo arquivo de banco não acorda `Flow` algum do app aberto,
e a tela do usuário continuaria exibindo números anteriores à escrita do agente.

#### Scenario: Servidor sobe com o app
- **WHEN** o app desktop é iniciado com o servidor habilitado
- **THEN** o servidor passa a aceitar conexões, sem que nenhum outro programa tenha sido executado

#### Scenario: Servidor cai com o app
- **WHEN** o app desktop é encerrado
- **THEN** o servidor deixa de aceitar conexões e libera a porta

#### Scenario: Um único artefato distribuído
- **WHEN** o pacote de distribuição do desktop é inspecionado
- **THEN** ele contém um único executável, e a superfície MCP está dentro dele

### Requirement: O perímetro é a máquina do usuário

O servidor SHALL escutar exclusivamente na interface de loopback, e MUST NOT aceitar conexão
de outra máquina. Toda requisição SHALL apresentar um token de autorização gerado pelo app;
uma requisição sem token, ou com token que não confere, SHALL ser recusada sem executar
ferramenta alguma.

O token SHALL ser persistido para que o endereço configurado num cliente continue válido
entre execuções, e SHALL poder ser regenerado pelo usuário — regenerar invalida o anterior.

#### Scenario: Conexão de fora da máquina
- **WHEN** uma conexão é tentada a partir de outro host
- **THEN** ela não é estabelecida, porque o servidor não escuta em interface externa

#### Scenario: Requisição sem token
- **WHEN** uma requisição chega sem o token, ou com um token que não confere
- **THEN** ela é recusada e nenhuma ferramenta é executada

#### Scenario: Token sobrevive ao reinício
- **WHEN** o app é encerrado e aberto novamente
- **THEN** o token continua o mesmo, e um cliente já configurado conecta sem reconfiguração

#### Scenario: Token regenerado
- **WHEN** o usuário regenera o token
- **THEN** o token anterior deixa de ser aceito

### Requirement: O que o agente escreve, a tela mostra

Uma escrita feita por ferramenta MCP SHALL atravessar o mesmo `AppDatabase` que a UI observa,
e as telas abertas SHALL refletir a mudança sem intervenção do usuário — sem recarregar,
reabrir ou navegar.

#### Scenario: Lançamento criado pelo agente aparece na tela
- **WHEN** um agente registra uma transação enquanto a lista de transações está aberta
- **THEN** a transação aparece na lista e os totais do período são recalculados

#### Scenario: Saldo reflete escrita do agente
- **WHEN** um agente registra uma despesa enquanto o dashboard está aberto
- **THEN** o saldo exibido passa a considerá-la

### Requirement: A habilitação é uma escolha que persiste

A decisão do usuário de habilitar o servidor SHALL ser persistida, e o servidor SHALL subir
sozinho nos inícios seguintes do app, sem que o usuário volte à tela de configurações.

Um servidor que exigisse ser religado a cada abertura do app seria inutilizável para o que ele
existe: o agente conectaria ou não conforme o usuário tivesse lembrado, e a falha apareceria do
lado do agente, longe da causa.

Desabilitar SHALL ser igualmente persistente: um servidor desligado permanece desligado entre
execuções.

#### Scenario: O servidor sobe sozinho no início seguinte
- **WHEN** o usuário habilita o servidor e depois fecha e reabre o app
- **THEN** o servidor sobe automaticamente, e o cliente já configurado conecta sem nenhuma ação do usuário

#### Scenario: O desligamento também persiste
- **WHEN** o usuário desabilita o servidor e reabre o app
- **THEN** o servidor não sobe, e nenhuma ferramenta é oferecida

#### Scenario: A primeira execução após a atualização
- **WHEN** o app é atualizado para uma versão com o servidor e aberto pela primeira vez
- **THEN** nada sobe e nada escuta, porque não houve escolha do usuário a persistir

### Requirement: Uma falha ao subir é dita ao usuário

Quando o servidor estiver habilitado e não conseguir subir, o app SHALL informar o usuário na
sua própria interface, dizendo o motivo e o que fazer. A falha MUST NOT ser silenciosa, e o
estado exibido MUST NOT dizer que o servidor está no ar quando ele não está.

Sem isso, o sintoma aparece apenas do outro lado — o agente não conecta — e a pessoa que precisa
agir é a única que não é avisada. O caso concreto previsto é a porta ocupada por outro programa,
e a informação útil é qual porta, com o caminho para trocá-la.

O estado do servidor SHALL ser observável na tela enquanto ela estiver aberta: uma queda depois
de o servidor ter subido também se reflete ali.

#### Scenario: Porta ocupada no início do app
- **WHEN** o servidor está habilitado e a porta configurada já está em uso por outro programa
- **THEN** o app informa que o servidor não subiu, qual porta está ocupada, e oferece trocá-la

#### Scenario: O estado exibido não mente
- **WHEN** o servidor está habilitado mas não está aceitando conexões
- **THEN** a tela não o apresenta como no ar

#### Scenario: Falha percebida fora da tela de configurações
- **WHEN** o servidor falha ao subir durante o início do app e o usuário não está na tela de configurações
- **THEN** ele ainda assim é avisado, sem depender de visitar aquela tela para descobrir

### Requirement: A configuração ensina a conectar

O app SHALL oferecer uma seção de configurações dedicada ao servidor, alcançável a partir das
configurações do app junto das demais **integrações**, onde o usuário o liga e desliga, vê se
está no ar, e obtém o endereço e o token.

Com o servidor desabilitado, a seção SHALL apresentar o que ele é e o interruptor que o liga, e
MUST NOT exigir nenhuma outra decisão para ligá-lo — endereço, token, permissões e instruções de
conexão só fazem sentido depois que existe um servidor a que se conectar.

Com o servidor habilitado, a seção SHALL apresentar os eixos de permissão, o endereço e o token,
e as instruções de configuração de um cliente MCP em forma copiável. O token SHALL ficar oculto
por padrão, revelado sob ação explícita.

As instruções MUST NOT ser específicas de um cliente: o servidor fala o protocolo, e qualquer
cliente que o fale conecta. A seção SHALL dizer que o servidor só responde com o app aberto.

#### Scenario: Encontrar a configuração
- **WHEN** o usuário abre as configurações do app
- **THEN** encontra o servidor MCP entre as integrações

#### Scenario: Primeira visita, com o servidor desabilitado
- **WHEN** o usuário abre a seção pela primeira vez
- **THEN** vê o que o servidor é e um interruptor para habilitá-lo, sem precisar decidir mais nada antes

#### Scenario: Usuário liga o servidor
- **WHEN** o usuário habilita o servidor na seção de configurações
- **THEN** o servidor passa a aceitar conexões e a seção passa a exibir os eixos de permissão, o endereço, o token e as instruções de conexão

#### Scenario: Usuário desliga o servidor
- **WHEN** o usuário desabilita o servidor
- **THEN** as conexões são encerradas e nenhuma ferramenta é executada até que ele seja habilitado de novo

#### Scenario: Instruções copiáveis
- **WHEN** o usuário abre a seção de configurações com o servidor no ar
- **THEN** ele consegue copiar o endereço e o token sem transcrevê-los à mão

#### Scenario: O token não fica exposto
- **WHEN** a seção é exibida com o servidor no ar
- **THEN** o token aparece oculto, e só é revelado sob ação explícita do usuário

### Requirement: O servidor é desktop-only

A feature do servidor MCP SHALL ser classificada como `desktop-only`, e o seu ponto de entrada
MUST NOT ser oferecido em plataformas onde ela não é suportada.

#### Scenario: Configuração do servidor no desktop
- **WHEN** o app roda no desktop
- **THEN** a seção de configurações do servidor MCP é oferecida

#### Scenario: Configuração do servidor no mobile
- **WHEN** o app roda em uma plataforma mobile
- **THEN** nenhum ponto de entrada da feature é exibido
