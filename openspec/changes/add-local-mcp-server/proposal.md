## Why

Um agente de IA não tem como falar com o Finsight. Perguntar "quanto gastei em julho?" ou
lançar uma despesa exige abrir o app e operar a tela — e o app já sabe responder todas essas
perguntas, com regras de razão que nenhuma outra ferramenta consegue reproduzir corretamente.

Expor isso por um servidor MCP local, embutido no app desktop, dá a qualquer agente acesso ao
domínio real: os agregados vêm do razão em vez de serem somados por um LLM sobre uma lista, e
toda escrita passa pelos use cases que já governam fatura, parcelamento, recorrência e
consolidação de moeda.

## What Changes

- **Servidor MCP embutido no `:app:desktop`**, que sobe e desce com o processo do app. Sem
  segundo binário: escuta em loopback, exige token, e a UI reage em tempo real porque a
  escrita passa pelo mesmo `AppDatabase` — o `invalidationTracker` do Room faz o resto.
- **56 ferramentas em quatro famílias** — perguntas (agregados que o app calcula), catálogo
  (listas mapeadas com o total do razão junto), registro (CRUD onde é CRUD) e operações
  (pagar, fechar, confirmar, transferir, arquivar). Levantadas em
  `docs/mcp-tool-surface.md`, com o use case que decide cada regra. Entre elas, duas que a
  simulação com um agente real provou faltar: **patrimônio líquido** (a soma das contas não
  desconta a fatura aberta, e as duas figuras são indistinguíveis pelo valor) e **comparação
  entre períodos**, hoje uma subtração que sobra para o agente.
- **Permissões em quatro eixos** — ler, registrar e editar, apagar, operar — configuradas na
  tela do app. A permissão decide **quais ferramentas existem** no `tools/list`, não é um
  `if` dentro de cada uma; mudá-la emite `notifications/tools/list_changed`. E o handshake
  **declara o que está retido**: sem isso, um agente conclui que a capacidade não existe e
  responde ao usuário que a operação é impossível — o que a simulação registrou acontecendo.
- **Seção de configurações dedicada**, que liga/desliga o servidor, mostra o endereço e o
  token, e ensina a configurar um cliente MCP.
- **7 use cases novos**, extraídos de ViewModels que hoje escrevem direto no repositório
  (categoria, orçamento) ou concentram uma decisão de domínio na UI (o despacho entre
  parcelamento, recorrência e transação simples, hoje em `AddTransactionViewModel:299-340`).
  O ViewModel passa a consumi-los, para não existirem duas verdades sobre a mesma operação.
- **35 use cases promovidos** de `impl` para `api`, no padrão que `ArchiveAccountUseCase` já
  usa (interface na `api`, `Impl` no `impl`).
- **Identidade por id como forma canônica de use case.** Hoje sete recebem id, catorze
  recebem o agregado, e `PayInvoicePaymentUseCase` recebe os dois na mesma assinatura. O id
  passa a carregar a implementação; a forma por agregado vira sobrecarga de uma linha que
  delega. **Não é breaking**: a mudança é aditiva e nenhum dos 24 chamadores existentes muda.
- **`ConfirmRecurringUseCase` perde os cinco defaults derivados do agregado.** Nenhum é
  exercido — o único chamador passa os oito argumentos explicitamente — e o de `title`
  contradiz a regra que o próprio chamador documenta ("blank is an absence, not the
  template's title").

## O que fica de fora

A lista abaixo é o resultado de varrer as features do app contra a superfície proposta, e não
uma amostra. Cada exclusão é uma decisão com motivo — o que estiver ausente daqui é omissão, e
deve ser tratado como defeito desta proposta.

**Moedas e câmbio** (`feature/settings`) — cadastrar, editar, arquivar e remover moeda; trocar a
moeda base; cadastrar, corrigir e remover taxa; disparar a sincronização de taxas. O agente
**lê** figuras consolidadas e recebe a taxa aplicada com a data, mas não escreve nenhuma das
duas. O motivo é assimetria de dano: uma taxa errada reescreve em silêncio **toda** figura
consolidada do app, inclusive as de meses fechados, e a moeda base é preferência de exibição do
usuário — nada disso é lançamento, e nenhum dos dois tem como ser conferido pelo estrago que
causa.

**Suporte** (`feature/support`) — abrir chamado e responder mensagem. É a única superfície do app
que sai da máquina (Firestore), e o servidor existe para ser local.

**Relatórios além dos números** (`feature/report`) — configurar o relatório, renderizar o
documento e exportá-lo. `get_report_stats` entrega as figuras; montar e exportar um documento é
produção de artefato visual, não dado.

**Preferências do dashboard** (`IDashboardPreferencesRepository`) — quais widgets aparecem, em que
ordem, e **quais contas ficam de fora do saldo total**. São escolhas de exibição do usuário; a
última, em particular, mudaria o número que o próprio agente lê, sem que ele tivesse pedido.

**Lançamento de rendimento** (`LaunchYieldUseCase`, spec `yield-accounts`) — creditar o rendimento
de uma conta que rende. É a fronteira mais discutível desta lista: é um lançamento como outro
qualquer. Fica fora porque não estava no escopo declarado e porque a conta que rende tem regra
própria (a categoria de rendimento é garantida pelo domínio); entra numa mudança própria, se
entrar.

**Ícones** — escolher ícone de conta, cartão ou categoria, e a sugestão automática
(`SuggestAccountIconUseCase`). O que o agente cria nasce com o ícone padrão. É cosmético e
depende de um catálogo visual que não tem tradução útil em JSON.

**Semeadura de categorias padrão** (`CreateDefaultCategoriesUseCase`) — acontece uma vez, na
primeira execução, e não é operação de usuário.

**Autenticação e conta** (`core:auth`) — entrar, sair, identidade. O servidor herda a sessão do
app; não a gerencia.

**Telemetria** (`core:analytics`, `core:crashlytics`) e **estado da janela do desktop** — não são
dados do usuário.

**Dirigir a interface** — navegar, abrir modal, acionar botão. E **ler estado de tela**: expor
`UiState` congelaria a UI como contrato.

**Android e iOS** — servidor local é coisa de desktop.

**Idempotência de escrita** — um agente que perde a resposta e repete a chamada duplica o
lançamento. Reconhecido, não resolvido, e a única mitigação hoje é o usuário ver o lançamento
aparecer na tela em tempo real.

**Uma superfície de administração do próprio servidor** — o agente não liga, desliga, reconfigura
porta, regenera token nem altera as próprias permissões. Tudo isso é do usuário, na tela do app.

## Capabilities

### New Capabilities

- `mcp-server`: existência e ciclo de vida do servidor embutido — sobe e desce com o app,
  escuta apenas em loopback, exige token, e é a única porta de agente. Inclui a seção de
  configurações que o liga, revela endereço e token, e ensina a configurar um cliente.
- `mcp-tool-surface`: o que cada ferramenta devolve. Toda listagem carrega o agregado
  correspondente vindo do razão (nunca a soma da página); toda figura que cruza contas
  carrega moeda, consolidado e a data da taxa; a perspectiva decide entre natureza e direção;
  uma recusa nomeia a alternativa que o domínio permite.
- `mcp-permissions`: os quatro eixos e o efeito de cada um sobre o `tools/list`, incluindo a
  notificação de mudança e o estado inicial.
- `use-case-identity`: um use case público é identificado por id, e a forma por agregado é
  uma sobrecarga que delega — nunca uma segunda implementação.

### Modified Capabilities

- `presentation-mapping`: a regra passa a valer para **qualquer superfície de apresentação**,
  não só a tela. O agente é a segunda instância: recebe DTO plano, sem grafo de domínio e sem
  cálculo próprio, e consome os mesmos donos de decisão (`deriveTransactionLabel`,
  `TransactionPerspective`, `figureLegUnder`, `ConsolidateMoneyUseCase`).
- `platform-adaptive-features`: o eixo de plataforma hoje só nomeia `mobile-only`. Ganha a
  direção simétrica — uma feature `desktop-only` MUST NOT ser oferecida onde não é suportada.

## Impact

**Módulos novos**: `feature/mcp/api` e `feature/mcp/impl`, com o servidor em `jvmMain` e a
tela de configurações sujeita ao eixo `desktop-only`. `:app:desktop` inicia e encerra o
servidor com o processo.

**Dependências**: `io.modelcontextprotocol:kotlin-sdk-server:0.14.0` e um engine
`ktor-server-*` em `3.4.3` — a versão que o SDK exige é exatamente o pino que o projeto já
tem. Verificado antes de propor: compila com Kotlin 2.3.10 e responde ao ciclo completo do
protocolo. A `0.15.0` está descartada (exige Ktor 3.5.1 e stdlib 2.4.0, à frente do
compilador). O catálogo registra que o Ktor *"vive num módulo só, `feature/settings/impl`"* —
a nota é reescrita para distinguir o módulo que usa cliente do que usa servidor.

O SDK eleva, no app inteiro, `kotlinx-serialization-json` de 1.8.0 para **1.11.0** e
`kotlinx-coroutines` de 1.10.2 para **1.11.0**. Também verificado: com as duas elevadas, os
1488 testes de `jvmTest` passam em 21 módulos, sem nenhuma task reaproveitada de cache.

**Domínio**: 7 use cases criados, 35 promovidos, 14 sobrecargas por id adicionadas. Os
ViewModels de categoria, orçamento e transação passam a consumir os use cases extraídos.
`ConfirmRecurringUseCase` muda de assinatura, com um chamador a ajustar.

**Superfície de risco**: é a segunda porta de escrita do app, e ela move dinheiro real.
Loopback e token são o perímetro; as permissões são o controle do usuário; e cada ferramenta
de escrita delega a decisão ao use case dono, sem exceção.
