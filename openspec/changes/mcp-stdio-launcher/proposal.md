## Why

O servidor MCP só existe enquanto a janela do app está aberta: ele é um Ktor dentro do processo
da UI, sobe no `main()` do desktop e morre com ele. Um agente que precisa das finanças com o app
fechado, ou depois de um reboot, encontra uma porta que ninguém escuta — e a seção de
configurações precisa avisar isso, porque a falha do lado do agente não aponta para a causa.

A decisão foi deliberada (design D1 da mudança arquivada): o `invalidationTracker` do Room é do
processo, então um segundo processo escrevendo no banco não acorda `Flow` algum da tela aberta.
O fato continua verdadeiro — o rastreador cria `TEMP TABLE room_table_modification_log` e gatilhos
temporários na própria conexão, e o `refresh()` público só relê essa tabela. O que muda é a
conclusão: "a janela precisa executar a escrita **enquanto está aberta**" não implica "o servidor
precisa ser a janela". Separando **quem fala MCP** de **quem é dono do banco**, o servidor pode
existir sem a janela e continuar reativo com ela.

A forma escolhida é a que o ecossistema já usa. A spec do MCP diz que clientes *"SHOULD support
stdio whenever possible"* e reserva o HTTP para servidores independentes que atendem várias
conexões; dos oito agentes locais pesquisados, sete lançam um `command` stdio nativamente, e o
Claude Desktop **só** aceita isso para servidor local — hoje ele não chega ao Finsight sem um
adaptador de terceiros. Nenhum app desktop com MCP embutido (JetBrains, Figma, Blender, Unity,
Docker Desktop, Obsidian, Xcode) responde com a GUI fechada; onde isso é exigido, o padrão é um
processo separado que lê o dado direto do disco. É exatamente o que um modo `--mcp` do próprio
executável faz.

## What Changes

- **O executável instalado ganha um modo `--mcp`.** O mesmo launcher que abre a janela, chamado
  com esse argumento, fala MCP por **stdio** e não abre janela alguma. O cliente lança e encerra
  o processo, como faz com qualquer servidor stdio. Sem segundo instalável, sem serviço em segundo
  plano, sem item de login: não há nada residente para sobreviver ao fechamento ou ao reboot, e o
  binário lançado é sempre o instalado.
- **Dois comportamentos, uma regra.** Com a janela **fechada**, o processo sobe o mesmo grafo Koin
  sem UI — banco, use cases, as mesmas ferramentas, o mesmo registro de atividade — e serve
  sozinho. Com a janela **aberta**, ele é uma **ponte** para o servidor HTTP embutido que já
  existe, para que a escrita aconteça no processo que tem os `Flow`s. A regra que sustenta os dois:
  **há no máximo um dono do banco por vez, e enquanto a janela está aberta o dono é ela**; quem não
  é dono encaminha em vez de executar.
- **A posse do banco é um lock de arquivo do sistema operacional**, tomado pela janela antes de
  abrir o banco e conferido pelo modo stdio antes de cada execução local. A troca de dono é
  imposta pelo kernel, não por convenção — e cobre a janela entre o Koin subir e a porta ser
  vinculada, que uma sondagem da porta deixaria aberta.
- **O app continua sendo a autoridade.** O interruptor, os quatro eixos de permissão, o token e o
  registro de atividade seguem sendo decididos na seção de configurações e persistidos onde já
  são. Um servidor desabilitado faz o modo `--mcp` recusar cada chamada dizendo o motivo, e não
  anunciar ferramenta alguma. "O app sobe e derruba" vira autoridade sobre o que o servidor pode
  fazer, não sobre quem dispara o processo.
- **As instruções de conexão passam a ser o comando.** A seção mostra o caminho absoluto do
  executável instalado com `--mcp`, no formato que os clientes usam (`command` + `args`), copiável,
  e diz que ele funciona com o app aberto ou fechado. O HTTP em loopback continua existindo como
  backend da ponte e como caminho avançado para clientes que preferem `url`; deixa de ser o único.
- **A frase "o servidor só responde com o app aberto" sai da seção**, porque deixa de ser verdade.
- **Higiene de stdout.** Em `--mcp`, a primeira instrução do processo redireciona `System.out` para
  `stderr`: um `println` de qualquer biblioteca corromperia a sessão em silêncio. `stderr` é
  também o canal de diagnóstico, porque os clientes o exibem.
- **O servidor HTTP e o controlador não mudam de comportamento.** O que sai de
  `DesktopMcpServerController` é a montagem do `Server` por sessão com o registro das ferramentas,
  para ser compartilhada pelos dois transportes.

## O que fica de fora

**Serviço em segundo plano** (LaunchAgent, chave `Run`, `systemd --user`) — atenderia clientes
só-HTTP com o app fechado e a leitura literal de "o app sobe o servidor". Descartado: três
integrações de SO com ressalvas conhecidas (permissão de item de login e notarização no macOS,
`linger` no Linux, ausência de supervisão no Windows), supervisão de crash, versão defasada de um
daemon residente depois de uma atualização, e a mesma passagem de bastão que o modo stdio já
exige. Nenhum dos agentes pesquisados precisa dele.

**Bandeja / menu bar** — fechar a janela esconderia em vez de encerrar. Não resolve "Sair" nem
reboot, e com o modo stdio deixa de ter função para o MCP.

**Cliente só-HTTP com o app fechado** — não é atendido. Nenhum dos oito agentes pesquisados está
nesse caso; o ChatGPT Desktop exige túnel público mesmo para uso local e fica fora de qualquer
desenho local.

**Escrever nos arquivos de configuração dos clientes** (`claude_desktop_config.json`,
`~/.cursor/mcp.json`, `mcp.json` do VS Code, `~/.codex/config.toml`) — o app mostra o bloco
copiável com o caminho atual; alterar arquivos de terceiros é invasivo e cada cliente muda o
formato por conta própria.

**Avisar a janela e reobservar** em vez de ponte — o processo stdio executaria sempre localmente
e a janela reobservaria os `Flow`s ao ser avisada. Descartado: são 41 consultas `Flow` no Room e
49 funções de repositório em nove módulos a envolver, e deixaria dois escritores com a janela
aberta. A ponte é um ponto só.

**Um único processo stdio como dono e servidor dos demais** com o app fechado — reintroduziria a
disputa pela porta quando a janela abre. Dois clientes com o app fechado são dois escritores num
SQLite com `busy_timeout = 3000` aplicado pelo Room em toda conexão, e sem janela não há `Flow` a
atualizar; o SQLite já resolve.

**Remover o HTTP e a porta** — fica para depois. O servidor embutido é o backend da ponte, e um
cliente que fala `url` continua podendo usá-lo com o app aberto.

## Capabilities

### New Capabilities

- `mcp-stdio-mode`: o modo `--mcp` do executável instalado — como é lançado, o que faz com a
  janela fechada (serve o banco sozinho) e aberta (ponte para o servidor embutido), a regra de
  posse única do banco e o lock que a impõe, a recusa quando o servidor está desabilitado, a
  higiene de stdout e o diagnóstico por `stderr`.

### Modified Capabilities

- `mcp-server`: o requisito "O servidor vive com o app, e não como segundo programa" passa a
  "Um único artefato, dois modos" — a superfície MCP existe com o app aberto ou fechado, e a seção
  deixa de dizer que só responde com o app aberto. "A configuração ensina a conectar" passa a
  apresentar o comando stdio como instrução principal e o endereço HTTP como caminho avançado.
  "O que um agente escreve fica registrado" ganha o cenário headless: uma escrita feita com o app
  fechado aparece no registro na próxima abertura.
- `mcp-permissions`: "Mudar a permissão alcança quem já está conectado" passa a alcançar também o
  cliente ligado pela ponte — a notificação atravessa o processo stdio. "O estado inicial não
  concede escrita" e "A permissão decide quais ferramentas existem" ganham o cenário headless: o
  modo stdio lê a escolha persistida e anuncia exatamente o que a janela anunciaria.

## Impact

**`:app:desktop`**: o `main()` passa a despachar por argumento — `--mcp` entra num ponto de
entrada sem `application {}` e sem Firebase; qualquer outra coisa abre a janela como hoje. A janela
toma o lock de posse do banco antes de montar o grafo. O caminho do executável para as instruções
vem de `jpackage.app-path`, que o launcher empacotado define (verificado no binário gerado por
`createDistributable`), com fallback em desenvolvimento.

**`feature/mcp/api`**: um contrato novo para a sessão stdio, em tipos de `:core:*`, no mesmo
padrão de `McpServerController` — o desktop chama, o `impl` implementa. `McpServerController`
ganha o que a seção precisa para as instruções: o comando de lançamento.

**`feature/mcp/impl` (`jvmMain`)**: a montagem do `Server` por sessão sai de
`DesktopMcpServerController` para um lugar comum aos dois transportes; entra a sessão stdio
(`StdioServerTransport`, já presente em `kotlin-sdk-server:0.14.0`), a ponte para o HTTP embutido
e a decisão por chamada entre local e remoto. A seção de configurações troca as instruções.

**Dependência nova**: `io.modelcontextprotocol:kotlin-sdk-client:0.14.0`, o artefato irmão do
servidor na versão já pinada, para a ponte falar Streamable HTTP com a janela. Mesmo Ktor 3.4.3.

**Banco**: nenhuma tabela nova e nenhuma migração. O modo stdio abre o mesmo `AppDatabase` pelo
mesmo builder, passa pelas mesmas migrações e pela mesma cópia pré-migração do cofre; a captura
preventiva antes de apagar acontece no processo que executa, como já acontece na janela.

**Specs**: `mcp-server` e `mcp-permissions` recebem deltas; `mcp-stdio-mode` nasce. O teste
`McpServerReachesTheDistributionTest` continua válido — é o mesmo artefato — e ganha um irmão que
prova que o launcher empacotado aceita `--mcp` e completa `initialize` → `tools/list`.

**Superfície de risco**: é a mesma porta de escrita, com um transporte a mais. Com o app fechado o
perímetro é o processo lançado pelo cliente, no usuário do cliente, sem socket; com o app aberto
a ponte apresenta o mesmo token ao mesmo servidor em loopback. O que um agente pode fazer continua
decidido pelos mesmos eixos, lidos da mesma persistência.
