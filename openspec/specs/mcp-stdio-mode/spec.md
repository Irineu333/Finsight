# mcp-stdio-mode Specification

## Purpose

O modo `--mcp` do executável instalado: o mesmo binário que abre a janela, lançado por um
cliente MCP, fala o protocolo por stdio e não abre janela alguma. Cobre o que ele faz com a
janela fechada (serve o banco sozinho) e aberta (encaminha para o servidor embutido, para que a
escrita aconteça no processo que tem os `Flow`s), a regra de posse única do banco e o lock do
sistema operacional que a impõe, a recusa quando o servidor está desligado nas configurações, e
a higiene de `stdout`, que carrega o protocolo e nada mais.

## Requirements

### Requirement: O executável instalado fala MCP por stdio quando lançado com `--mcp`

O mesmo executável que abre a janela do app, quando lançado com o argumento `--mcp`, SHALL falar o
protocolo MCP pelo transporte stdio e MUST NOT abrir janela alguma. O processo SHALL viver
enquanto o cliente mantiver o stdin aberto e SHALL encerrar quando ele fechar. O projeto MUST NOT
distribuir um segundo executável, script ou serviço para isso: o modo é do artefato que o usuário
já instala.

O processo `--mcp` MUST NOT inicializar nada que só a janela usa — a interface gráfica e os
serviços de nuvem — porque nada disso é necessário para servir o banco, e cada um deles é um
custo por sessão e uma fonte de saída indevida.

#### Scenario: Lançado pelo cliente
- **WHEN** um cliente MCP lança o executável instalado com `--mcp`
- **THEN** o processo completa `initialize` e responde `tools/list` pelo stdio, sem que nenhuma janela apareça

#### Scenario: Encerra com o cliente
- **WHEN** o cliente fecha o stdin do processo
- **THEN** o processo encerra e libera o que tinha aberto

#### Scenario: Mesmo artefato
- **WHEN** o pacote de distribuição do desktop é inspecionado
- **THEN** ele contém um único executável, e o modo `--mcp` está dentro dele

#### Scenario: Sem UI e sem nuvem
- **WHEN** o processo `--mcp` inicia
- **THEN** nenhum componente gráfico é composto e nenhum serviço de nuvem é inicializado

### Requirement: Com a janela fechada, o modo stdio serve o banco sozinho

Quando não houver janela do app aberta, o processo `--mcp` SHALL abrir o mesmo banco pelo mesmo
caminho que a janela usa — as mesmas migrações, a mesma cópia pré-migração — e SHALL executar as
ferramentas localmente, com o mesmo domínio e os mesmos donos de regra. Toda escrita SHALL passar
pelos mesmos use cases e SHALL deixar o mesmo registro de atividade.

Dois clientes lançando dois processos `--mcp` com a janela fechada SHALL ser atendidos ambos,
sem corromper o banco e sem falhar por concorrência.

#### Scenario: Consulta com o app fechado
- **WHEN** nenhuma janela está aberta e um agente chama uma ferramenta de consulta
- **THEN** a resposta vem do banco, calculada pelos mesmos use cases que a janela usa

#### Scenario: Escrita com o app fechado
- **WHEN** nenhuma janela está aberta e um agente registra um lançamento
- **THEN** o lançamento é gravado pelo use case dono, e o registro de atividade ganha a entrada correspondente

#### Scenario: Dois clientes ao mesmo tempo
- **WHEN** dois clientes lançam dois processos `--mcp` com a janela fechada e ambos escrevem
- **THEN** as duas escritas são aplicadas e nenhuma das duas falha por concorrência

#### Scenario: Migração pendente
- **WHEN** o app foi atualizado, a janela ainda não foi aberta e um cliente lança `--mcp`
- **THEN** o banco é migrado pelo mesmo caminho da janela, com a cópia pré-migração, antes da primeira resposta

### Requirement: Com a janela aberta, o modo stdio encaminha para ela

Quando houver janela do app aberta, o processo `--mcp` MUST NOT executar ferramenta alguma
localmente: ele SHALL encaminhar `tools/list` e `tools/call` ao servidor embutido na janela, com
o token persistido, e SHALL repassar ao cliente a notificação de que a lista de ferramentas mudou.
A decisão entre executar e encaminhar SHALL ser tomada a cada chamada, para que a janela possa
abrir ou fechar no meio de uma sessão sem que o cliente perca a sessão.

Nos dois modos o cliente SHALL enxergar o mesmo servidor: a mesma lista de ferramentas, as mesmas
recusas, o mesmo vocabulário.

#### Scenario: Escrita chega à tela aberta
- **WHEN** a janela está aberta numa lista de transações e um agente, pelo stdio, registra um lançamento
- **THEN** o lançamento é executado pela janela e aparece na lista sem que o usuário navegue

#### Scenario: A janela abre no meio da sessão
- **WHEN** uma sessão stdio está em curso com a janela fechada e o usuário abre o app
- **THEN** as chamadas seguintes são encaminhadas à janela e a sessão do cliente continua a mesma

#### Scenario: A janela fecha no meio da sessão
- **WHEN** uma sessão stdio está sendo encaminhada e o usuário encerra o app
- **THEN** as chamadas seguintes são executadas localmente e a sessão do cliente continua a mesma

#### Scenario: A lista é a mesma nos dois modos
- **WHEN** um cliente pede `tools/list` com a janela aberta e, depois, com ela fechada
- **THEN** as duas listas são idênticas para as mesmas permissões

#### Scenario: A janela está abrindo
- **WHEN** a janela tomou a posse do banco mas ainda não está aceitando conexões, e uma chamada chega pelo stdio
- **THEN** o processo espera a janela ficar disponível por um limite declarado e, se ela não ficar, responde ao cliente que o app está iniciando e a chamada deve ser repetida

### Requirement: Há no máximo um dono do banco por vez

A posse do banco SHALL ser um lock exclusivo de arquivo do sistema operacional. A janela SHALL
tomá-lo antes de abrir o banco e SHALL segurá-lo até encerrar. O processo `--mcp` SHALL tomá-lo
antes de cada execução local e SHALL soltá-lo ao terminar; se não conseguir tomá-lo, MUST NOT
executar localmente. Uma janela que encontra o lock tomado SHALL esperar por um limite declarado
antes de prosseguir.

#### Scenario: A janela toma a posse antes do banco
- **WHEN** a janela inicia
- **THEN** o lock é tomado antes de o banco ser aberto, e continua tomado enquanto a janela existe

#### Scenario: O modo stdio não executa sem a posse
- **WHEN** o lock está tomado pela janela e uma chamada chega pelo stdio
- **THEN** a chamada não é executada localmente

#### Scenario: A janela espera uma chamada local terminar
- **WHEN** a janela inicia enquanto um processo `--mcp` executa uma chamada local
- **THEN** a janela espera a chamada terminar e então toma a posse

### Requirement: A autoridade do app vale no modo stdio

O modo `--mcp` SHALL ser governado pelas mesmas escolhas que governam o servidor embutido: o
interruptor, os eixos de permissão, o token e o registro de atividade decididos na seção de
configurações. Com o servidor desabilitado, o processo `--mcp` SHALL falar o protocolo, MUST NOT
anunciar ferramenta alguma e SHALL recusar toda chamada dizendo que o servidor está desligado nas
configurações do app. O processo MUST NOT oferecer meio de o agente alterar nenhuma dessas
escolhas.

Uma escolha feita na seção SHALL estar gravada no disco ao término da ação, para que um processo
`--mcp` lançado em seguida a leia. Enquanto a janela é dona do banco, a posse SHALL ter
precedência sobre o que o processo leu do disco: a chamada é encaminhada e a janela aplica a
escolha viva.

#### Scenario: Servidor desabilitado
- **WHEN** o servidor está desabilitado nas configurações e um cliente lança `--mcp`
- **THEN** `tools/list` volta vazio e qualquer `tools/call` é recusado dizendo que o servidor está desligado no app

#### Scenario: Instalação sem escolha
- **WHEN** ninguém nunca habilitou o servidor nesta instalação e um cliente lança `--mcp`
- **THEN** nenhuma ferramenta é anunciada e nada é executado

#### Scenario: Registro compartilhado
- **WHEN** um agente escreve pelo stdio com a janela fechada e o usuário depois abre o app
- **THEN** a escrita aparece no registro de atividade da seção, com horário, operação e referência

#### Scenario: Escolha recém-feita
- **WHEN** o usuário liga o servidor na seção, encerra o app e um cliente lança `--mcp` em seguida
- **THEN** o processo lê o servidor como ligado e anuncia as ferramentas concedidas

#### Scenario: Servidor desabilitado com a janela aberta
- **WHEN** a janela está aberta com o servidor desabilitado e uma chamada chega pelo stdio
- **THEN** a chamada é recusada na hora dizendo que o servidor está desligado no app, sem esperar pela janela

### Requirement: `stdout` pertence ao protocolo

No modo `--mcp`, o `stdout` do processo SHALL carregar exclusivamente as mensagens do protocolo.
Qualquer outra saída do processo, inclusive a de bibliotecas, SHALL ir para `stderr`. O
diagnóstico do processo — versão, modo, se encontrou a janela aberta, se o servidor está
habilitado — SHALL ser escrito em `stderr`, porque é o que os clientes exibem.

#### Scenario: Uma biblioteca escreve em `stdout`
- **WHEN** qualquer código do processo escreve em `System.out` durante uma sessão
- **THEN** o texto vai para `stderr` e a sessão do protocolo continua íntegra

#### Scenario: Diagnóstico ao iniciar
- **WHEN** o processo `--mcp` inicia
- **THEN** `stderr` recebe uma linha com a versão, o modo em que está e se o servidor está habilitado
