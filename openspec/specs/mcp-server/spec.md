# mcp-server Specification

## Purpose

O servidor MCP local embutido no app desktop: existência, ciclo de vida e perímetro. Ele sobe e desce com o processo da janela — sem segundo executável —, escuta apenas na interface de loopback e exige token. É uma das duas formas da mesma superfície: a outra é o modo `--mcp` do mesmo binário, que um cliente lança e que atende com a janela aberta ou fechada (capability `mcp-stdio-mode`). Inclui a seção de configurações que o liga, apresenta as duas formas de conectar — o comando de lançamento e o endereço HTTP com o token — em abas lado a lado, diz quem está conectado, e guarda o registro persistido do que um agente fez, nos dois modos: o único lugar do app onde a **autoria** de uma escrita aparece.

## Requirements

### Requirement: O servidor vive com o app, e não como segundo programa

A superfície MCP SHALL viver dentro do artefato que o usuário instala, em dois modos do mesmo
executável: o servidor embutido no processo da janela, que sobe quando ela sobe e encerra quando
ela encerra, e o modo `--mcp`, que o cliente lança por stdio e que funciona com a janela aberta ou
fechada (capability `mcp-stdio-mode`). O projeto MUST NOT distribuir um segundo executável,
script ou serviço para que um agente alcance o app.

Com a janela aberta, toda escrita de agente SHALL ser executada pelo processo da janela — venha
ela pelo servidor embutido ou encaminhada pelo modo `--mcp` —, porque o `invalidationTracker` do
Room é **do processo**: um segundo processo escrevendo no mesmo arquivo de banco não acorda
`Flow` algum da janela, e a tela continuaria exibindo números anteriores à escrita. Com a janela
fechada, o modo `--mcp` é o único processo e executa sozinho.

#### Scenario: Servidor sobe com o app
- **WHEN** o app desktop é iniciado com o servidor habilitado
- **THEN** o servidor embutido passa a aceitar conexões, sem que nenhum outro programa tenha sido executado

#### Scenario: Servidor cai com o app
- **WHEN** o app desktop é encerrado
- **THEN** o servidor embutido deixa de aceitar conexões e libera a porta

#### Scenario: A superfície existe com o app fechado
- **WHEN** o app desktop está encerrado e um cliente lança o executável com `--mcp`
- **THEN** o agente é atendido, sem que nenhum outro programa tenha sido instalado ou executado

#### Scenario: Um único artefato distribuído
- **WHEN** o pacote de distribuição do desktop é inspecionado
- **THEN** ele contém um único executável, e os dois modos da superfície MCP estão dentro dele

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
está no ar, e obtém o que um cliente precisa para conectar.

Com o servidor desabilitado, a seção SHALL apresentar o que ele é e o interruptor que o liga, e
MUST NOT exigir nenhuma outra decisão para ligá-lo — comando, endereço, token, permissões e
instruções de conexão só fazem sentido depois que existe um servidor a que se conectar.

Com o servidor habilitado, a seção SHALL apresentar os eixos de permissão e as instruções de
conexão em forma copiável, nas duas formas de conectar, oferecidas como **abas lado a lado**. A
instrução principal SHALL ser o **comando**: o caminho absoluto do executável instalado com
`--mcp`, no formato `command` + `args` que os clientes MCP usam, e a seção SHALL abrir nessa aba e
dizer que ele funciona com o app aberto ou fechado. O endereço HTTP e o token SHALL continuar
disponíveis na outra aba, para clientes que preferem `url`; o token SHALL ficar oculto por padrão,
revelado sob ação explícita.

Porque abas desenham os dois caminhos como iguais, a diferença entre eles SHALL estar dita em
texto: a aba do endereço MUST dizer que ele só responde com o app aberto. Havendo apenas um
caminho a oferecer — quando o processo não sabe dizer o que o lançou, e não há comando —, a seção
MUST NOT apresentar abas: uma aba só não é escolha alguma, e o endereço é exibido direto.

As instruções MUST NOT ser específicas de um cliente: o servidor fala o protocolo, e qualquer
cliente que o fale conecta. A aba do comando PODE oferecer, além do bloco, o mesmo comando em uma
linha para os clientes cuja linha de comando aceita um executável e seus argumentos; qual deles
SHALL ser escolha do usuário, e nenhum deles MUST ser condição para conectar — um cliente ausente
dessa lista conecta pelo bloco como qualquer outro.

#### Scenario: Encontrar a configuração
- **WHEN** o usuário abre as configurações do app
- **THEN** encontra o servidor MCP entre as integrações

#### Scenario: Primeira visita, com o servidor desabilitado
- **WHEN** o usuário abre a seção pela primeira vez
- **THEN** vê o que o servidor é e um interruptor para habilitá-lo, sem precisar decidir mais nada antes

#### Scenario: Usuário liga o servidor
- **WHEN** o usuário habilita o servidor na seção de configurações
- **THEN** o servidor embutido passa a aceitar conexões e a seção passa a exibir os eixos de permissão e as duas abas de conexão, aberta na do comando de lançamento

#### Scenario: Usuário desliga o servidor
- **WHEN** o usuário desabilita o servidor
- **THEN** as conexões são encerradas e nenhuma ferramenta é executada, em nenhum dos dois modos, até que ele seja habilitado de novo

#### Scenario: O comando é copiável e aponta para o executável instalado
- **WHEN** o usuário abre a seção com o servidor habilitado
- **THEN** ele consegue copiar o bloco `command` + `args` com o caminho absoluto do executável desta instalação, sem transcrevê-lo à mão

#### Scenario: A seção diz que funciona fechado
- **WHEN** o usuário lê as instruções de conexão
- **THEN** elas dizem que o comando funciona com o app aberto ou fechado

#### Scenario: A linha é escrita para o cliente escolhido
- **WHEN** o usuário escolhe outro cliente na aba do comando
- **THEN** a linha passa a ser a que aquele cliente aceita, com o mesmo executável e o mesmo argumento do bloco

#### Scenario: A aba do endereço continua copiável
- **WHEN** o usuário escolhe a aba do endereço HTTP
- **THEN** ele consegue copiar o endereço e o token

#### Scenario: A aba do endereço diz que precisa do app aberto
- **WHEN** o usuário lê a aba do endereço HTTP
- **THEN** ela diz que esse caminho só responde com o app aberto

#### Scenario: Sem comando a lançar, não há abas
- **WHEN** o processo não sabe dizer o que o lançou e não há comando a oferecer
- **THEN** a seção exibe o endereço direto, sem abas

#### Scenario: O token não fica exposto
- **WHEN** a aba do endereço é exibida
- **THEN** o token aparece oculto, e só é revelado sob ação explícita do usuário

### Requirement: O que um agente escreve fica registrado

Toda escrita, operação e recusa executada por um agente SHALL ser registrada, e o registro SHALL
persistir entre execuções do app. Uma entrada SHALL dizer **quando**, **qual operação**, **sobre
o quê** — em termos que o usuário reconheça, não identificadores soltos — e **qual foi o
resultado**, referenciando o que criou ou alterou, para que o usuário alcance o lançamento a
partir dali.

O registro SHALL ser o mesmo nos dois modos: uma escrita feita pelo modo `--mcp` com o app
fechado SHALL constar dele quando o usuário abrir o app.

Este é o único lugar do app onde a **autoria** de uma escrita aparece. A reatividade mostra o
resultado — a transação surge na tela —, e não mostra que ela veio de fora: sem o registro, um
lançamento indevido feito por um agente é indistinguível de um lançamento que o próprio usuário
esqueceu de ter feito.

O registro também é a única defesa hoje contra a duplicação que a ausência de idempotência
permite: um agente que repete uma chamada perdida cria dois lançamentos idênticos, e é aqui que
os dois aparecem lado a lado.

**Leituras MUST NOT ser registradas.** Um agente faz dezenas de consultas para responder a uma
pergunta, e listá-las afoga exatamente o que o registro existe para mostrar; uma leitura não
altera nada e não tem o que auditar.

O registro SHALL ter política de retenção declarada, e MUST NOT crescer sem limite. O usuário
SHALL poder limpá-lo, e a limpeza MUST NOT alterar nenhum lançamento — o registro é rastro do que
foi feito, e MUST NOT ser tratado como fonte de verdade contábil: apagar uma entrada não desfaz a
operação que ela descreve.

#### Scenario: Escrita de agente deixa rastro
- **WHEN** um agente registra um lançamento
- **THEN** o registro ganha uma entrada com o horário, a operação, o que foi lançado em termos legíveis e a referência ao lançamento criado

#### Scenario: Escrita com o app fechado deixa o mesmo rastro
- **WHEN** um agente registra um lançamento pelo modo `--mcp` com o app fechado, e o usuário depois abre o app
- **THEN** a entrada está no registro, indistinguível de uma feita com o app aberto

#### Scenario: Rastro sobrevive ao reinício
- **WHEN** o app é encerrado e aberto novamente
- **THEN** as entradas anteriores continuam disponíveis

#### Scenario: Consulta não vira entrada
- **WHEN** um agente executa uma sequência de consultas para responder a uma pergunta
- **THEN** nenhuma entrada é criada

#### Scenario: Recusa é registrada
- **WHEN** uma operação é recusada por falta de permissão ou pelo domínio
- **THEN** o registro guarda a tentativa e o motivo da recusa

#### Scenario: Duplicação fica visível
- **WHEN** um agente repete uma chamada de escrita já executada
- **THEN** as duas execuções aparecem no registro, lado a lado

#### Scenario: Limpar o registro não desfaz nada
- **WHEN** o usuário limpa o registro
- **THEN** as entradas somem e todos os lançamentos permanecem intactos
### Requirement: A tela diz quem está conectado e o que cada eixo libera

A seção de configurações SHALL informar se há cliente conectado no momento, e SHALL oferecer ao
usuário encerrar as sessões em curso. Estar habilitado e ter alguém do outro lado são fatos
diferentes, e só o segundo significa que algo pode estar lendo as finanças agora.

Cada eixo de permissão SHALL informar **quantas ferramentas** ele concede, e o eixo retido SHALL
informar quantas retém. Um interruptor cujo efeito não é dito é concedido às cegas.

A seção SHALL apresentar o registro de atividade recente, com acesso ao histórico completo.

#### Scenario: Cliente conectado
- **WHEN** um agente está com sessão aberta e o usuário abre a seção
- **THEN** ela informa que há cliente conectado e oferece encerrar a sessão

#### Scenario: Habilitado sem ninguém conectado
- **WHEN** o servidor está no ar e nenhum cliente tem sessão
- **THEN** a seção distingue esse estado de "há alguém conectado"

#### Scenario: O efeito de um eixo é dito
- **WHEN** o usuário lê os eixos de permissão
- **THEN** cada um informa quantas ferramentas concede, e o retido informa quantas retém

#### Scenario: Atividade recente na própria seção
- **WHEN** o usuário abre a seção depois de um agente ter lançado algo
- **THEN** a operação aparece na atividade recente, com acesso ao histórico completo

### Requirement: O servidor é desktop-only

A feature do servidor MCP SHALL ser classificada como `desktop-only`, e o seu ponto de entrada
MUST NOT ser oferecido em plataformas onde ela não é suportada.

#### Scenario: Configuração do servidor no desktop
- **WHEN** o app roda no desktop
- **THEN** a seção de configurações do servidor MCP é oferecida

#### Scenario: Configuração do servidor no mobile
- **WHEN** o app roda em uma plataforma mobile
- **THEN** nenhum ponto de entrada da feature é exibido
