## Context

O app desktop é um processo Compose/JVM que inicia o Koin no `main()` e mantém o grafo inteiro
em memória (`app/desktop/.../main.kt:22-27`). O banco é um arquivo único em
`~/.finance/finsight.db` (`core/database/.../Database.jvm.kt:8`), e `:app:shared` reexporta
todas as `feature:*:api` e `:core:*` com `api(...)`, de modo que `:app:desktop` já enxerga o
contrato público inteiro (`app/shared/build.gradle.kts:9-42`).

O domínio é rico e a maior parte das regras já tem dono: 17 use cases estão em `api`, 63 vivem
em `impl`, e a escrita direta de ViewModel para repositório sobrevive em apenas oito pontos.
As leituras agregadas que um agente precisaria — saldo, resumo do mês, gasto por categoria,
progresso de orçamento, limite disponível — já existem, e devolvem `MoneyByCurrency` sempre que
podem atravessar contas.

O levantamento completo da superfície está em `docs/mcp-tool-surface.md`, com o use case que
decide cada regra, verificado arquivo a arquivo.

**Restrições que a mudança não escolhe:**

- O `invalidationTracker` do Room é do processo. Reatividade e isolamento de processo são
  mutuamente exclusivos.
- Ktor está pinado em `3.4.3` — a última cujo stdlib não passa à frente do compilador do
  projeto (Kotlin `2.3.10`) — e o catálogo registra que ele *"vive num módulo só,
  `feature/settings/impl`"*.
- `:app:desktop` é `kotlin("jvm")` puro; não é KMP e não vê `impl` algum.
- Toda leitura que atravessa contas é `MoneyByCurrency`, e reduzir a um número é conversão,
  que vive acima do razão.

## Goals / Non-Goals

**Goals:**

- Um agente MCP lê e escreve dados do app, com o app calculando as figuras em vez de o agente
  somá-las.
- Um único binário: o app configura, sobe e derruba o servidor, e a UI reage em tempo real.
- Escrita autorizada por eixos que o usuário controla, com o padrão em só-leitura.
- Toda regra continua com um dono; o servidor não ganha nenhuma.

**Non-Goals:**

A enumeração completa está no `proposal.md`, obtida varrendo as features do app contra a
superfície. Aqui ficam apenas as exclusões cuja **razão é técnica** e informa o desenho:

- **Escrever taxa de câmbio ou trocar a moeda base.** O agente lê figuras consolidadas e recebe
  a taxa aplicada com a sua data; escrever qualquer uma das duas reescreveria em silêncio toda
  figura consolidada do app, retroativamente e sem lançamento que denuncie. A assimetria entre o
  esforço da chamada e o alcance do estrago é grande demais para uma superfície automatizada.
- **Preferência de contas excluídas do saldo total.** Mudá-la altera o número que o próprio
  agente lê depois, o que fecha um laço em que ele influencia a própria medida.
- **Suporte.** É a única superfície do app que sai da máquina; o servidor existe para ser local.
- **Administração do próprio servidor** — ligar, desligar, porta, token, permissões. Um agente
  capaz de ampliar as próprias permissões não tem permissões.
- **Dirigir a UI ou ler estado de tela.** Expor `UiState` congelaria a UI como contrato.
- **Android e iOS.**
- **Idempotência de escrita:** um agente que repete uma chamada perdida duplica o lançamento.
- **Acesso remoto** ou autenticação de múltiplos usuários.

## Decisions

### D1 — O servidor é embutido, não um processo à parte

A alternativa era um executável separado (stdio) abrindo o mesmo `finsight.db`. Rejeitada: o
`invalidationTracker` do Room é in-process, então um segundo processo escrevendo no arquivo não
acorda `Flow` algum do app aberto. O agente lançaria uma despesa e a tela do usuário continuaria
exibindo o saldo anterior — e dois writers no mesmo SQLite ainda disputariam lock.

O preço é explícito: **sem app aberto, sem servidor**. A seção de configurações diz isso, para
que uma falha de conexão não seja lida como configuração errada.

### D2 — Transporte streamable HTTP em loopback

stdio é o transporte mais comum de MCP, e não serve aqui: um cliente stdio **lança** o processo
servidor, e o nosso já está rodando como aplicação gráfica. Sobra HTTP na interface de loopback,
que o SDK Kotlin oferece via `mcpStreamableHttp` sobre um engine Ktor.

Trade-off aceito: clientes que só falam stdio precisam de um adaptador de terceiros
(`mcp-remote` e similares). Não é binário nosso e não viola "um único binário" — mas é uma linha
a mais nas instruções, e ela deve estar lá.

### D3 — A feature é `feature/mcp/{api,impl}`, e o desktop apenas a liga

As alternativas eram pôr tudo em `:app:desktop` (que já vê todo o contrato público) ou criar
`:core:mcp` (impossível — `core` não pode ver feature).

Escolhemos a feature porque ela tem tela: a seção de configurações é rota, `NavGraphBuilder`,
módulo Koin e entry point — uma feature completa, não um puxadinho do shell. E porque
`feature/*/impl` pode depender de **qualquer** `api`, que é exatamente o perfil de um servidor
que precisa alcançar oito features.

A `api` declara um controlador (`start`, `stop`, estado observável) em tipos de `:core:*`. O
`impl` o implementa em `jvmMain`, com `actual` no-op nos demais targets — o padrão que
`SupportModule` já usa. `:app:desktop` obtém o controlador via Koin e o liga ao ciclo de vida do
processo; ele nunca vê o `impl`.

### D4 — A superfície é de apresentação, não espelho do domínio

Três alternativas foram descartadas:

- **CRUD uniforme sobre as oito entidades.** Não alcança metade do domínio (fatura tem ciclo de
  vida, não CRUD) e abre uma porta de corrupção silenciosa: `update_invoice(status = PAID)`
  grava "paga" sem lançar o pagamento, e o saldo passa a mentir sem que nada falhe.
- **Espelho 1:1 dos use cases.** Devolve ao agente a tarefa de compor e somar, que é justamente
  onde ele erra.
- **Ferramentas genéricas (`query`/`mutate` com `entity` + `action`).** Economizam contexto no
  `tools/list` e pagam com um `payload` sem schema, que o agente preenche por adivinhação.

A superfície é, então, o que uma tela do app já faz: mostra números no topo (perguntas), mostra
uma lista (catálogo), tem formulário (registro) e tem botões de ação (operações). Cada
ferramenta traduz e compõe; nenhuma decide.

### D5 — A permissão decide quais ferramentas existem

As quatro famílias **são** os quatro eixos. Uma permissão não concedida não vira um `if` no
início da ferramenta: as ferramentas correspondentes deixam de ser anunciadas no `tools/list`.
O agente não as tenta, não erra e não gasta contexto com elas.

`ServerCapabilities.Tools(listChanged = true)` permite notificar quem já está conectado quando o
usuário mexe no interruptor. A recusa na execução permanece, porque o anúncio é consequência da
permissão e não a sua única aplicação.

Alternativa descartada: matriz entidade × verbo, que daria quarenta interruptores numa tela.

### D6 — O id é a forma canônica; o agregado é sobrecarga que delega

Hoje sete use cases recebem id, catorze recebem o agregado, e `PayInvoicePaymentUseCase` recebe
os dois na mesma assinatura. Não há regra a corrigir, e sim uma a escolher.

O id carrega a implementação e resolve a identidade no momento da execução; a forma por agregado
vira uma linha na interface (`invoke(account) = invoke(account.id)`). A mudança é **aditiva** —
nenhum dos 24 chamadores existentes muda —, e resolver na hora da ação é mais correto do que
receber uma leitura carregada minutos antes, agora que existe uma segunda porta de escrita.

O padrão já existe no repositório: `IEntryRepository.accountBalanceUpTo(accountId, YearMonth)`
delega para a forma por dia sob o KDoc *"Not another number, so not another implementation."*

### D7 — Onde há lote, a forma é plural

`CalculateAvailableLimitUseCase(creditCard)` chamado num laço sobre uma lista produz N+1
consultas. A resposta não é receber o agregado, e sim receber a coleção de identidades e
devolver um mapa — como `IEntryRepository.owedByDimensionByCurrency` já faz sob a regra *"N
invoices custam uma leitura, não N"*.

### D8 — `ConfirmRecurringUseCase` perde os cinco defaults derivados do agregado

É o único use case do repositório cuja assinatura declara valores padrão lidos de outro
parâmetro (`amount = recurring.amount`, `title = recurring.title`, e mais três). Parecia forçar a
delegação na direção inversa — id resolvendo e chamando a forma por agregado.

Não força: nenhum é exercido. O único chamador (`ConfirmRecurringViewModel:206-219`) passa os
oito argumentos explicitamente, duplica o de `amount` antes de chamar, e contorna o de `title`
com um comentário explicando que cair no template *"would hand the user a name they had just
erased"*. O default existe e o único chamador o evita de propósito; um segundo chamador que
confiasse nele reintroduziria o bug.

### D9 — Os use cases faltantes nascem no dono, e o ViewModel passa a consumi-los

Sete operações não existem como use case: criar e editar categoria, criar/editar/apagar
orçamento, editar parcelamento, e o despacho entre parcelamento, recorrência e transação simples
(hoje um `if` em `AddTransactionViewModel:299-340`).

Cada um nasce no módulo que possui a regra, e o ViewModel que hoje a executa passa a chamá-lo no
mesmo passo. Extrair sem migrar o chamador criaria duas verdades sobre a mesma operação — a da
tela e a do agente —, que é exatamente o defeito que a mudança existe para não introduzir.

### D10 — A porta é fixa, editável, e falha visível quando ocupada

Uma porta efêmera nunca colide e obriga a reconfigurar o cliente a cada reinício — ou a
publicar um arquivo de descoberta, que reintroduz um artefato fora do binário. Descartada.

Uma porta fixa **com fallback automático** é pior do que falhar: o cliente configurado para a
porta A não encontra nada quando o servidor sobe na porta B, e o sintoma — "o agente não
conecta" — não aponta para a causa. Descartada.

A porta é fixa, tem valor padrão, é editável na tela, e **quando está ocupada o servidor não
sobe**: a tela diz qual porta está ocupada e oferece trocá-la. O usuário resolve uma vez, e o
cliente configurado continua valendo para sempre.

O padrão é `8477`, escolhido por dois critérios: fora das faixas que ferramentas de
desenvolvimento disputam (3000, 4000, 5000, 8000, 8080, 8081, 9000) e fora da faixa efêmera
que o sistema operacional aloca sozinho (49152–65535).

### D11 — O token fica em `Settings`, e o perímetro real é a validação de origem

O token é gerado pelo app, persistido em `Settings` — o mecanismo de preferência que o app já
usa — e regenerável. Guarda texto claro num local previsível, o que é aceitável para um segredo
cujo alcance é o loopback da própria máquina em que ele está gravado: quem consegue lê-lo já
tem acesso ao arquivo do banco, que não é cifrado.

Um cofre de sistema operacional traria três dependências nativas (Keychain, DPAPI, libsecret)
para proteger contra um atacante que já venceu.

O vetor que **de fato** existe contra um servidor local é outro: uma página web qualquer,
aberta no navegador do usuário, fazendo requisições para `127.0.0.1` — diretamente ou via DNS
rebinding. O SDK traz `DnsRebindingProtectionConfig` e validação de `Host`/`Origin` para
exatamente isso, e é **essa** a defesa a configurar, não o sigilo do token em disco.

### D12 — As versões estão fixadas, e foram verificadas empiricamente

O spike foi executado, e não sobrou incerteza de build:

| | |
|---|---|
| `io.modelcontextprotocol:kotlin-sdk-server` | **`0.14.0`** |
| Ktor que ela exige | **`3.4.3`** — o pino exato do projeto |
| Revisão de protocolo | `2025-11-25` |

`0.15.0`, a mais recente, foi **descartada**: exige Ktor `3.5.1` e `kotlin-stdlib 2.4.0`, que
passa à frente do compilador do projeto (`2.3.10`) — a mesma razão pela qual o Ktor já estava
pinado em 3.4.x.

Verificado: `0.14.0` compila com Kotlin 2.3.10; o servidor sobe escutando em `127.0.0.1`; e o
ciclo `initialize` → `tools/list` → `tools/call` responde corretamente, com
`capabilities.tools.listChanged = true` — o mecanismo que D5 usa para as permissões.

**O custo real não era o Ktor, e sim o que vem junto.** `kotlin-sdk-core:0.14.0` exige:

| Dependência | Projeto hoje | Exigido |
|---|---|---|
| `kotlinx-serialization-json` | 1.8.0 | **1.11.0** |
| `kotlinx-coroutines-core` | 1.10.2 | **1.11.0** |
| `kotlin-stdlib` | 2.3.10 | 2.3.21 *(mesma versão de linguagem)* |
| `kotlinx-collections-immutable`, `kotlin-logging` | — | novas, transitivas |

Como o Gradle eleva para a maior versão, adicionar o SDK **sobe serialization e coroutines no
app inteiro**. Isso foi testado antes de ser proposto: com as duas elevadas no catálogo,
`./gradlew jvmTest --rerun-tasks` executou 349 tasks sem nenhuma reaproveitada de cache, e
1488 testes em 21 módulos passaram sem falha. A elevação entra junto com a dependência, no
grupo 5, e não é um risco em aberto.

### D11 — O que a resposta faz quando falta taxa

`OfflineConsolidationTest` já prova que nenhum módulo no caminho de uma figura consolidada
alcança a rede. Quando a taxa necessária não existir no acervo, a ferramenta **diz isso** e
devolve a decomposição por moeda, em vez de omitir a moeda ou apresentar aproximação como exato.
O agente é livre para relatar a limitação; o que ele não pode é receber um número que parece
consolidado e não é.

### D13 — A permissão esconde a ferramenta, e por isso precisa anunciar a retenção

D5 estava incompleto, e a simulação (abaixo) mostrou como. Filtrar o `tools/list` cumpre o que
prometia — o agente não tenta o que não pode — mas produz um efeito que não estava previsto:
para quem só conhece o app pela lista de ferramentas, **capacidade retida e capacidade
inexistente são a mesma coisa**.

Pedido a um agente com o eixo "apagar" retido: *"apaga o último lançamento"*. A resposta foi
*"não existe ferramenta de exclusão de lançamento no servidor"* — uma afirmação falsa sobre o
app, dita com confiança ao dono dele, que bloqueia justamente a ação que resolveria o caso.

E a retenção sem anúncio empurra para o contorno: o mesmo agente relatou ter considerado usar a
ferramenta de **edição** para zerar o valor do lançamento e "neutralizá-lo", o que deixaria um
registro de R$ 0,00 nas listagens. Não executou, mas o caminho estava aberto — negar o verbo
direto sem explicar por quê convida ao verbo torto.

A correção tem duas partes, e nenhuma reabre o `tools/list`:

1. **O handshake declara as capacidades retidas** e diz que são escolha do usuário, reversível
   nas configurações. Declara a *capacidade*, nunca as ferramentas — não é uma segunda lista por
   outro canal. É o canal certo porque o cliente MCP entrega as instruções da sessão ao modelo
   antes da primeira pergunta.
2. **As ferramentas concedidas não oferecem o efeito das retidas por composição.** Editar um
   valor para zero passa a ser recusado: não é a remoção que o usuário pediu, e é pior do que a
   recusa, porque some do total sem sumir do histórico.

### D15 — O registro é persistido, e só guarda o que muda alguma coisa

A tela de configurações resolvia ligar, conectar e controlar. Faltava a quarta coisa que ela
precisa fazer: **contar o que aconteceu**. A reatividade entrega o resultado — a transação
aparece — e não entrega a autoria; um lançamento indevido feito por um agente é
indistinguível, na lista, de um que o usuário esqueceu de ter feito.

**Persistido, e não em memória.** Um registro que morre com o app some exatamente quando alguém
vai investigar: o usuário nota o número estranho no dia seguinte, reabre o app, e o rastro já
não existe. O custo é uma **tabela nova e uma migração** de `AppDatabase`, com schema exportado
e a paridade de migração estendida — o projeto já tem esse caminho montado.

**Só escritas, operações e recusas.** Um agente faz dezenas de consultas para responder a uma
pergunta; registrá-las afogaria o que o registro existe para mostrar. Leitura não altera nada e
não tem o que auditar. Recusa entra porque é ela que explica ao usuário por que o agente disse
que não conseguiu.

**Não é fonte de verdade contábil.** O usuário pode limpar o registro, e limpar MUST NOT tocar em
lançamento algum. O rastro é sobre quem fez; o que foi feito continua no razão, que é onde
sempre esteve.

Efeito colateral que vale nomear: é a única defesa disponível hoje contra a duplicação que a
ausência de idempotência permite. Os dois lançamentos gêmeos aparecem lado a lado, com horário,
em vez de esperarem serem notados no meio do extrato.

### D14 — O que a simulação mediu, e o que ela não mediu

Um protótipo do servidor foi construído com as versões de D12, populado com um mês sintético, e
entregue a um agente que **não recebeu esta proposta** — ele descobriu a superfície por
`tools/list`, como um cliente real. Dez pedidos em linguagem de usuário.

**Validado:**

| Resultado | O que sustenta |
|---|---|
| 15 chamadas de boa-fé, **zero erro** de argumento ou schema | ferramentas nomeadas com descrição bastam — é a evidência contra a forma genérica descartada em D4 |
| 5 dos 10 pedidos resolvidos com **uma** chamada | o agregado pronto no payload cumpre "o app calcula, não a IA" |
| Transferência entre contas próprias e pagamento de fatura **não** entraram na despesa do mês | a armadilha que derruba a leitura ingênua não pegou |
| Figura em duas moedas consolidada com a taxa e a data declaradas | a regra de moeda sobreviveu ao consumidor real |
| Recusa que nomeia a alternativa encerrou a tentativa em uma chamada | *"fechou o caso rápido em vez de ficar adivinhando"* |

**Corrigido por D13 e pelos requisitos novos de `mcp-tool-surface`:** a retenção invisível; a
figura sem perímetro declarado (duas chamadas gastas só para descobrir se a dívida de cartão
estava descontada); o período em curso comparado a um fechado sem aviso; a ordem de listagem sem
desempate, que torna "o último que eu registrei" irrespondível; a sobreposição não documentada
entre duas leituras de fatura; e a ferramenta genérica cuja prosa cita um tipo que o parâmetro
recusa.

**Não medido, e permanece em aberto:** o protótipo guarda dados em memória e não passa pelos use
cases reais nem pelo razão — nada aqui prova que a regra de fatura, de parcelamento ou de
consolidação se comporta como o app se comporta. Dois defeitos do protótipo apareceram na
simulação e são dele, não do desenho: o parcelamento lançou as N parcelas na mesma fatura,
enquanto `AddInstallmentUseCaseImpl` distribui pelas seguintes; e uma fatura paga apareceu com
devido negativo, por falta das compras dela nos dados sintéticos. A avaliação de superfície é
válida; a de comportamento não foi feita, e é o grupo 14.

## Risks / Trade-offs

- ~~O SDK MCP pode não resolver contra Ktor 3.4.3 / Kotlin 2.3.10~~ → **verificado e
  resolvido** (D12): `0.14.0` exige exatamente Ktor 3.4.3, compila com Kotlin 2.3.10 e
  responde ao ciclo completo do protocolo.
- **Serialization e coroutines sobem no app inteiro** (1.8.0 → 1.11.0 e 1.10.2 → 1.11.0) →
  testado antes de propor: 1488 testes em 21 módulos passam com as duas elevadas, sem nenhuma
  task reaproveitada de cache. O que resta é o que teste não pega — comportamento em runtime
  fora da suíte —, coberto pela passagem manual do grupo 14.
- **O SDK está em 0.x e muda de forma entre versões menores** → a superfície que consumimos é
  pequena (`Server`, `addTool`, `mcpStreamableHttp`) e fica atrás do controlador declarado na
  `api`. Entre 0.14 e 0.15 o Ktor exigido já pulou de 3.4.3 para 3.5.1; subir de versão passa
  a ser uma decisão consciente, não um `+`.
- **Ktor deixa de viver num módulo só** → a nota do catálogo é reescrita no mesmo commit que
  adiciona a dependência, dizendo qual módulo usa cliente e qual usa servidor.
- **Uma página web alcançando `127.0.0.1`** → validação de `Host`/`Origin` e
  `DnsRebindingProtectionConfig`, que o SDK já oferece (D11). É o vetor real contra um servidor
  local, e não o sigilo do token em disco.
- **Segunda porta de escrita concorrendo com a UI** → toda escrita atravessa a mesma fronteira
  do razão (`LedgerEntryWriter`), que já valida `Σ = 0` e as regras de dimensão. Nada do MCP
  escreve fora dela.
- **Agente repete chamada perdida e duplica lançamento** → reconhecido e não resolvido nesta
  mudança. O usuário vê o lançamento na tela em tempo real, o que reduz o tempo até a descoberta,
  mas não previne.
- **55 ferramentas ocupam contexto do cliente** → a permissão filtra o anúncio; só-leitura expõe
  19. As descrições precisam ser boas, e escrevê-las é trabalho de primeira classe, não
  acabamento.
- **Token em texto claro** → ver D10. Escopo limitado a loopback, revogável pelo usuário.
- **Uma tela que exibe token e endereço é um alvo em captura de tela e compartilhamento** → o
  token fica oculto por padrão, revelado sob ação explícita.

## Migration Plan

1. **Spike de compatibilidade** do SDK com o pino de Ktor/Kotlin. Bloqueia todo o resto.
2. **Domínio primeiro, sem o servidor**: as 14 sobrecargas por id, os 7 use cases novos com os
   ViewModels migrados, e as 35 promoções para `api`. Tudo isso é aditivo ou local, compila e é
   testável sem que exista servidor algum, e pode ser mesclado sozinho.
3. **Servidor no socket**, desligado por padrão, sem ferramenta alguma — apenas ciclo de vida,
   loopback, token e handshake.
4. **Famílias de ferramentas**, na ordem ler → registrar → operar → apagar, cada uma junto do
   eixo de permissão que a governa.
5. **Tela de configurações** e as instruções de conexão.

**Rollback**: o servidor nasce desligado e é desligável a qualquer momento; desligá-lo restaura
o comportamento anterior por completo. As mudanças do passo 2 são aditivas e não têm o que
reverter — nenhum chamador existente muda de forma.

## Open Questions

Nenhuma em aberto. As quatro que existiam foram fechadas antes da implementação começar:

| Questão | Resolução |
|---|---|
| O SDK resolve contra o pino do projeto? | Sim, na `0.14.0`. Verificado compilando e exercitando o protocolo — D12 |
| Porta fixa ou efêmera? | Fixa, editável, e falha visível quando ocupada. Padrão `8477` — D10 |
| O token vai para um cofre do sistema operacional? | Não. `Settings`, com a defesa real na validação de origem — D11 |
| A conversão para id vira change própria? | Fica aqui. Separada, ela seria uma proposta cujo único motivo é citar esta; os grupos 2–4 já são mescláveis sozinhos, que é o que a separação buscava |
