## MODIFIED Requirements

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
