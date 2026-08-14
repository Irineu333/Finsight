## Why

Todo o valor do Finsight está atrás de uma única porta: a tela. Consultar quanto se gastou
em mercado no semestre, conferir quais faturas fecham na semana ou lançar as trinta linhas
de um extrato são coisas que o domínio já sabe fazer — mas que só acontecem se um humano
navegar até o lugar certo e repetir o gesto. Não existe hoje nenhuma forma de um programa
perguntar ou escrever, e a consequência prática é que qualquer trabalho em lote é trabalho
manual.

O MCP (Model Context Protocol) é o contrato que agentes de IA já falam, e o desktop é o
único alvo onde ele faz sentido: é lá que os clientes MCP rodam, e é lá que existe um
processo de vida longa capaz de escutar.

O que isso pede não é um adendo à UI. É **uma segunda interface sobre o mesmo domínio** —
com os mesmos direitos e as mesmas regras que a tela tem, e nenhuma regra própria.

## What Changes

- Um módulo `:app:mcp` passa a expor o domínio como um **servidor MCP local**, escutando em
  `127.0.0.1` numa porta efêmera, agregado como qualquer módulo Koin de feature.
- O servidor **nasce desativado**. Habilitar não é ligar uma tela: é passar a escutar numa
  porta com poder sobre o razão do usuário — e ninguém ganha essa superfície sem pedir.
- Habilitado, ele nasce em **somente leitura**. Escrita é um segundo ato deliberado, porque
  os dois riscos não têm o mesmo tamanho.
- "Só local" **não é autenticação**: qualquer processo do usuário alcança o loopback. Toda
  requisição carrega um token, e o token é revogável sem desligar o servidor.
- Enquanto ligado, o app publica `~/.finance/mcp.json` (porta, token, pid) — o anúncio de
  que está de pé, que um cliente stdio usa para achar e autenticar. Desligado, **o arquivo é
  apagado**: deixá-lo para trás aponta clientes para uma porta morta, ou para uma porta que
  outro processo herdou.
- Uma nova feature `mcp` (api+impl) hospeda a tela de configuração em Settings: o toggle, o
  nível de permissão, o token com botão de girar, o trecho de configuração pronto para colar
  no cliente, e a atividade recente.
- Toda escrita originada de agente é registrada numa tabela nova, `agent_activity`, no lado
  facade do banco — uma linha por chamada de tool, com o cliente que a fez, os argumentos
  como recebidos, o desfecho e o que foi tocado.
- Os casos de uso que as tools consomem são **promovidos de `impl` para `api`**, aplicando o
  critério de triagem que já governa o projeto ("só entra na `api` o que outro módulo
  consome"). Nenhuma regra de dependência muda, e o `:app:shared` continua sendo o único
  módulo que enxerga um `impl`.
- A superfície da primeira entrega é **ler tudo e escrever lançamento**: nove leituras e três
  escritas. Ciclo de vida de fatura e de recorrência, criação de conta, categoria, cartão e
  orçamento, e tudo que toque moeda base ou taxas ficam inalcançáveis por agente — são as
  operações de maior custo de erro e menor frequência.
- A escrita é **em lote, ensaiável e idempotente**, não uma chamada por lançamento. Lançar um
  extrato é o pedido de primeira classe, e trinta chamadas separadas são trinta oportunidades
  de falha parcial silenciosa. O ensaio devolve o que seria gravado — inclusive em qual fatura
  cada compra cairia — sem persistir nada.

**Fora de escopo, deliberadamente:**

- **Sobreviver ao fechamento do app.** Nesta mudança o servidor vive enquanto o processo
  vive. Ícone de bandeja, início com o sistema e cold start headless são degraus posteriores
  — cada um só muda *quem mantém o processo vivo*, nunca o que o servidor é. O desenho aqui
  não pode impedi-los (ver design), mas também não os antecipa.
- **Desfazer.** A atividade responde *"o que o agente fez?"*, e cada item leva à operação
  inversa onde ela já existe. Um botão universal de desfazer prometeria o que o domínio não
  oferece: nem toda operação tem inversa, e algumas têm inversa que não restaura o estado
  anterior.
- **Acesso remoto.** O servidor escuta em loopback e só em loopback.
- **Registro de leituras.** Volume alto, valor quase nulo, e afogaria as escritas — que são
  o motivo do registro existir.

## Capabilities

### New Capabilities
- `mcp-server`: a existência do servidor, seu ciclo de vida atado ao processo, o transporte
  em loopback, o arquivo de descoberta e a garantia de que o banco continua tendo um dono só.
- `mcp-access-control`: o padrão desligado, o que "desligado" significa, os dois níveis de
  permissão, o token e sua revogação.
- `mcp-tool-surface`: o que uma tool é (um caso de uso do domínio, nunca uma regra
  reescrita), a forma do dinheiro que ela devolve, a forma do erro e o que ela recusa.
- `agent-activity-log`: o registro das escritas de agente — o que entra, o que não entra, e
  o que ele sustenta.

### Modified Capabilities
- `module-architecture`: acrescenta `:app:mcp` como módulo de app **sem UI**, com os direitos
  de dependência de um `impl` (qualquer `api` mais `:core:*`) e nenhum direito novo; e
  registra a promoção de casos de uso para `api` como aplicação do critério de triagem
  existente, não como exceção a ele.
- `build-conventions`: a verificação mecânica das regras de dependência passa a cobrir
  `:app:mcp` — a garantia de que ele não alcança `impl` algum tem de ser do build, não da
  disciplina.

## Impact

- **`app/mcp`** (novo) — biblioteca KMP-JVM: o servidor, o transporte, as tools, o módulo
  Koin. Depende de `feature:*:api` e `:core:*`.
- **`app/shared`** — uma linha em `appModules`. É a mudança inteira do shell.
- **`app/desktop`** — apenas bootstrap: sobe e derruba o servidor com o processo. Nenhuma
  lógica, conforme a regra que já vale para módulos de plataforma.
- **`feature/mcp`** (novo, api+impl) — a tela de configuração e sua rota; `SettingsGraph`
  ganha o destino.
- **`feature/*/api` e `feature/*/impl`** — promoção dos casos de uso que as tools consomem.
  Hoje 15 dos 66 casos de uso estão na `api`, e praticamente toda a escrita está no `impl`
  (`CreateAccountUseCase`, `TransferBetweenAccountsUseCase`, `PayInvoiceUseCase`,
  `ConfirmRecurringUseCase`, entre outros). Cada promoção é também uma revisão do caso de uso
  como contrato público: assinatura, tipo de erro, nada de `UiText` na fronteira.
- **`core/database`** — entidade, DAO e migração de `agent_activity`.
- **`core/resources`** — chaves novas da tela de configuração, em pt e en.
- **`core/ledger`** — o registro de origem **não** entra: nenhuma regra do domínio ramifica em
  "quem pediu", e o que não produz derivação não pertence ao razão. Mas há trabalho, e ele é do
  razão: a leitura filtrada de lançamentos existe hoje só no caminho reativo
  (`observeTransactionsBy`), e a variante `suspend` (`getAllTransactions`) não filtra. MCP é
  requisição/resposta e não consome `Flow`, então a leitura filtrada precisa de gêmea
  `suspend` — e ela precisa recortar por **período**, não pelo dia único que o filtro atual
  aceita, sob pena de a tool paginar o mês em memória e filtrar fora do razão. Faturas em
  aberto sem escopo de cartão têm a mesma lacuna, no repositório de faturas.
- **Dependências novas** — o SDK Kotlin de MCP e um servidor HTTP embarcado. Ambos entram no
  `libs.versions.toml` e ficam confinados a `:app:mcp`.
- **Ponta solta assumida:** os módulos Koin dos `impl` declaram `viewModel {}`, então o
  runtime do Compose permanece no classpath de qualquer processo que agregue `appModules`.
  Declarar não é instanciar, e isso não impede nada — mas é peso morto conhecido, e é o que
  um futuro processo headless terá de encarar.
