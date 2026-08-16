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

### Requirement: A configuração ensina a conectar

O app SHALL oferecer uma seção de configurações dedicada ao servidor, onde o usuário o liga e
desliga, vê se está no ar, e obtém o endereço e o token. A seção SHALL apresentar as
instruções de configuração de um cliente MCP em forma copiável.

As instruções MUST NOT ser específicas de um cliente: o servidor fala o protocolo, e qualquer
cliente que o fale conecta.

#### Scenario: Usuário liga o servidor
- **WHEN** o usuário habilita o servidor na seção de configurações
- **THEN** o servidor passa a aceitar conexões e a seção exibe o endereço e o token

#### Scenario: Usuário desliga o servidor
- **WHEN** o usuário desabilita o servidor
- **THEN** as conexões são encerradas e nenhuma ferramenta é executada até que ele seja habilitado de novo

#### Scenario: Instruções copiáveis
- **WHEN** o usuário abre a seção de configurações com o servidor no ar
- **THEN** ele consegue copiar o endereço e o token sem transcrevê-los à mão

### Requirement: O servidor é desktop-only

A feature do servidor MCP SHALL ser classificada como `desktop-only`, e o seu ponto de entrada
MUST NOT ser oferecido em plataformas onde ela não é suportada.

#### Scenario: Configuração do servidor no desktop
- **WHEN** o app roda no desktop
- **THEN** a seção de configurações do servidor MCP é oferecida

#### Scenario: Configuração do servidor no mobile
- **WHEN** o app roda em uma plataforma mobile
- **THEN** nenhum ponto de entrada da feature é exibido
