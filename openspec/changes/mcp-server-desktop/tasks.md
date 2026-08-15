# Tarefas — `mcp-server-desktop`

Os grupos são **ordenados**; dentro de um grupo, nenhuma tarefa escreve num arquivo que uma
irmã escreve, e nenhuma consome a saída de uma irmã — um subagente por tarefa implementa o
grupo inteiro em paralelo. Cada grupo declara a sua **barreira**: o que precisa ser verdade
antes de começar e o que é verdade quando termina.

Quando duas tarefas do mesmo grupo se referem a um nome que ainda não existe, o nome está
**fixado no texto da tarefa** — é o mesmo recurso que o grupo 4 de
`2026-08-13-filter-transactions-uncategorized` usou: sem nome fixado, a renomeação guiada
pelo compilador vira dependência entre irmãs.

**Testes acompanham a tarefa que cria o comportamento**, num arquivo próprio; só a
verificação final é grupo à parte. É o oposto da convenção de
`2026-08-12-transaction-as-recurring`, e a razão é o tamanho: com ~60 tarefas, um grupo de
testes no fim seria uma segunda travessia por toda a mudança.

## A sequência que os artefatos impõem

Nem toda ordem abaixo é escolha de estilo:

- **Build antes de tudo** (grupo 1): `:app:mcp` não existe como projeto Gradle enquanto o
  `settings.gradle.kts` não o incluir e o convention plugin não existir — e a verificação
  mecânica precisa cobri-lo **antes** de a primeira dependência ser escrita, senão a garantia
  do delta `build-conventions` nasce depois do que ela deveria ter impedido.
- **Promoção antes do consumo** (grupo 2 antes do 5+): `:app:mcp` MUST NOT depender de
  `impl` algum (delta `module-architecture`). Uma tool que precise de
  `TransferBetweenAccountsUseCase` só compila depois de ele estar na `api`.
- **Migração antes dos seus leitores** (grupo 2 antes do 4): a tabela `agent_activity` e a
  migração `14 → 15` precedem o DAO, o repositório e a tela que a lê.
- **Contrato antes do implementador** (grupo 3 antes do 4): a `api` da feature `mcp` declara
  o que o `impl` e o `:app:mcp` consomem; escrever as duas pontas no mesmo grupo faria uma
  tarefa depender da saída da irmã.
- **Escrita antes do ensaio** (grupo 7 antes do 8): o ensaio devolve *exatamente o que seria
  gravado*, inclusive a fatura resolvida — o resolvedor de item é o mesmo objeto, e ter dois
  seria ter duas respostas para "em qual fatura isto cai".

## O que a verificação encontrou e diverge dos artefatos

Registrado aqui porque muda o tamanho de tarefas, não o seu conteúdo:

- A contagem do `design.md` **confere**: são **51** arquivos `*UseCase.kt` no `impl`, e 15 na
  `api`. Existem outros **11** arquivos `*UseCaseImpl.kt` no `impl`, mas eles implementam
  interfaces que a `api` **já declara** (`BuildTransactionUseCaseImpl`,
  `AddInstallmentUseCaseImpl`, …) — são exatamente os que **não** precisam de promoção, e
  somá-los ao total inverteria o sentido do número.
- O `proposal.md` cita `PayInvoiceUseCase` como exemplo de escrita a promover. Quem move
  dinheiro de uma conta para a fatura é **`PayInvoicePaymentUseCase`**
  (`feature/creditcards/impl/.../usecase/PayInvoicePaymentUseCase.kt:46`);
  `PayInvoiceUseCase` apenas transiciona o status, e transição de ciclo de vida de fatura
  está **fora** desta entrega (`mcp-tool-surface`). É `PayInvoicePaymentUseCase` que sobe.
- O `proposal.md` cita "criar categoria vai direto ao repositório" como exemplo de lacuna a
  preencher. É verdade (`CategoryFormViewModel.kt:141` chama `repository.insert`), mas
  **criar categoria está fora da superfície** — logo não se cria caso de uso para ela. As
  lacunas que a superfície de fato revela são **criar** e **alterar lançamento**, ambas
  embutidas em ViewModel (`AddTransactionViewModel.kt:332`,
  `EditTransactionViewModel.kt:276-284`).
- `feature/home` não existe com esse nome: a feature de chrome chama-se **`shell`**
  (`settings.gradle.kts:56-57`). Nada nesta mudança a toca.
- O `.maestro` dirige o app **Android**; o servidor MCP é desktop. **Não há fluxo E2E nesta
  mudança**, e o grupo 11 diz isso em vez de fingir cobertura.

---

## 1. Fundação de build: os módulos novos e a verificação que passa a cobri-los

Deltas: `build-conventions` (verificação mecânica cobre `:app:mcp`), `module-architecture`
(`:app:mcp` com direitos de um `impl`).

**Barreira de entrada:** nenhuma; é o primeiro grupo.
**Barreira de saída:** `./gradlew :app:mcp:compileKotlinJvm :feature:mcp:impl:compileKotlinJvm`
compila (módulos vazios, sem fonte), `./gradlew :app:shared:compileKotlinJvm` compila, e uma
dependência de `:app:mcp` para qualquer `feature:*:impl` **falha o build**. Dentro do grupo o
projeto não configura até que todas as tarefas tenham entrado — incluir um projeto sem
`build.gradle.kts` é erro de configuração, e é isto que a barreira fecha.

- [x] 1.1 **O convention plugin e a verificação.** Criar
      `build-logic/src/main/kotlin/com/neoutils/finsight/convention/AppMcpConventionPlugin.kt`
      (`class AppMcpConventionPlugin : Plugin<Project>`) chamando `configureKotlinMultiplatform()`
      — **não** `configureCompose()`: o módulo é de app **sem UI** (delta `module-architecture`)
      — mais `org.jetbrains.kotlin.plugin.serialization`, `kotlinx-serialization-json` e
      `koin-core` em `commonMain`, e terminando em `verifyFeatureDependencyRules(isApi = false)`,
      que é literalmente a regra 4 já escrita (`Extensions.kt:103-134`: admite `:core:*` e
      qualquer `:feature:*:api`, recusa `impl`). Registrar o id `finsight.app.mcp` em
      `build-logic/build.gradle.kts` (bloco `gradlePlugin { plugins { … } }`, junto dos seis
      já existentes). E **remover `verifyAppSharedDependencyRules`** (`Extensions.kt:136-158`)
      junto da sua única chamada (`AppSharedConventionPlugin.kt:26`): ela recusa todo projeto
      que não comece por `:core:` ou `:feature:`, e a existência de `:app:mcp` a torna falsa —
      o shell passa a depender legitimamente de um módulo `:app:`. Alargá-la seria manter uma
      regra cuja lista de exceções é a própria coisa que ela deveria descrever. O que ela ainda
      guardava é quase nada: o `:app:shared` já tem licença para depender de `impl`, e a regra
      não está escrita em spec alguma. A garantia que importa — o `:app:mcp` não alcançar
      `impl` — passa a vir de `verifyFeatureDependencyRules`, no módulo certo.
      Teste em `build-logic` não existe hoje e não é criado aqui; a garantia é exercida em 11.6.
- [x] 1.2 **O grafo de módulos.** Em `settings.gradle.kts`: `include(":app:mcp")` no bloco
      `// App` (linhas 36-40) e `include(":feature:mcp:api")` / `include(":feature:mcp:impl")`
      no bloco `// Features` (linhas 55-77), na ordem alfabética que o bloco já segue.
- [x] 1.3 **O catálogo de dependências.** Em `gradle/libs.versions.toml`: acrescentar o **SDK
      Kotlin de MCP 0.15.0** (`io.modelcontextprotocol:kotlin-sdk`) e o **servidor HTTP
      embarcado** — `ktor-server-core` e `ktor-server-cio`, na `ktor = "3.4.3"` já fixada
      (linha 35). Corrigir o comentário dessa versão: ele afirma hoje que o Ktor "vive num
      módulo só, `feature/settings/impl`", e passa a viver em dois — o cliente ali, o
      **servidor** em `:app:mcp` e em nenhum outro (proposal, "Dependências novas"). Registrar
      no comentário do SDK que `LATEST_PROTOCOL_VERSION = "2025-11-25"` é o que fixa a revisão
      alvo, e o gatilho de migração (D12).
- [x] 1.4 **`app/mcp/build.gradle.kts`** (novo): `plugins { id("finsight.app.mcp") }`;
      `commonMain` com `:core:common`, `:core:ledger`, `:core:model`, e as `api` das features
      que as tools alcançam (`accounts`, `budgets`, `categories`, `creditcards`, `recurring`,
      `transactions`, `mcp`); `jvmMain` com o SDK de MCP e `ktor-server-core`/`ktor-server-cio`.
      **Os targets são os mesmos de qualquer biblioteca do projeto** (Android/JVM/iOS, via
      `configureKotlinMultiplatform()`), e não só JVM: `appModules` é declarado no `commonMain`
      de `:app:shared` (`app/shared/src/commonMain/.../di/AppModules.kt:5-25`), então um módulo
      só-JVM tornaria a agregação impossível sem partir esse arquivo em expect/actual — o que
      contradiz "uma linha em `appModules`" (proposal, Impact). O transporte fica confinado ao
      `jvmMain` atrás de um `expect`, exatamente como `feature/support/impl` já faz com
      `expect val supportPlatformModule: Module` (`di/SupportModule.kt:11` + os três actuals).
- [x] 1.5 **`feature/mcp/api/build.gradle.kts`** e **`feature/mcp/impl/build.gradle.kts`**
      (novos), no molde de `feature/support` (o menor par do projeto): a `api` com
      `id("finsight.feature.api")`, `implementation(projects.core.model)` e
      `api(projects.core.navigation)`; a `impl` com `id("finsight.feature.impl")`,
      `projects.feature.mcp.api`, `:core:common`, `:core:database`, `:core:designsystem`,
      `:core:navigation`, `:core:resources`, `:core:ui`, `:core:analytics`,
      `libs.multiplatform.settings` e, em `commonTest`, `libs.multiplatform.settings.test`.
- [x] 1.6 **`app/shared/build.gradle.kts`**: `api(projects.feature.mcp.api)` e
      `implementation(projects.feature.mcp.impl)` no bloco das features (linhas 21-42), e
      `implementation(projects.app.mcp)`. Esta última só é aceita depois de 1.1 remover a
      verificação escopada ao shell; é a única dependência do shell para fora de
      `:core:`/`:feature:` no projeto, e é o que o delta `module-architecture` autoriza. **Nada é acrescentado ao `export()` de
      `app/ios/build.gradle.kts`**: nenhum código Swift nomeia a feature `mcp`, e a regra é
      export por demanda.

---

## 2. O domínio que a superfície exige — leituras ampliadas, promoções e a tabela nova

Deltas: `mcp-tool-surface` ("a lacuna que ela revela é preenchida no domínio", "leitura
ampliada no dono"), `agent-activity-log`, `module-architecture`.

**Barreira de entrada:** grupo 1 concluído (ordem; nenhuma tarefa aqui depende do módulo novo).
**Barreira de saída:** `./gradlew jvmTest` verde, `./gradlew :app:android:assembleDebug`
compila. Nenhum comportamento de tela muda: as promoções movem arquivo, e os dois casos de uso
novos passam a ser o único dono do que dois ViewModels faziam à mão. Cada tarefa toca arquivos
de uma feature só, e o módulo Koin de cada feature é escrito por **uma** tarefa.

- [x] 2.1 **`:core:ledger` — a leitura filtrada `suspend`, por período.** Em
      `core/ledger/src/commonMain/kotlin/com/neoutils/finsight/database/dao/TransactionDao.kt`,
      `.../domain/repository/ITransactionRepository.kt` e
      `.../database/repository/TransactionRepository.kt`: hoje existe `observeBy(date,
      dimensionId, accountId)` (`TransactionDao.kt:56-69`) — reativa e com **igualdade exata de
      dia** — e `getAll()` sem filtro (`:27-28`). MCP é requisição/resposta e não consome `Flow`
      (proposal, Impact), e paginar o mês em memória tiraria o recorte do dono
      (`mcp-tool-surface`). Acrescentar a gêmea `suspend` com recorte por **período**:
      `suspend fun getTransactionsBy(startDate: LocalDate? = null, endDate: LocalDate? = null,
      dimensionId: Long? = null, accountId: Long? = null): List<Transaction>`, com a mesma forma
      de predicado nulo-neutro do `observeBy` e o mesmo `ORDER BY o.date DESC, o.id DESC`.
      `observeBy` **não muda** — nenhum dos seus cinco chamadores é tocado. Teste novo em
      `core/ledger/src/jvmTest/.../TransactionDaoPeriodFilterTest.kt`: extremos inclusivos,
      cada filtro isolado e combinados, e a concordância com `observeBy` quando
      `startDate == endDate`.
- [x] 2.2 **`creditcards` — faturas em aberto sem escopo de cartão.** Em
      `core/database/src/commonMain/kotlin/com/neoutils/finsight/database/dao/InvoiceDao.kt`,
      `feature/creditcards/api/.../domain/repository/IInvoiceRepository.kt` e
      `feature/creditcards/impl/.../database/repository/InvoiceRepository.kt`: a consulta
      estritamente `OPEN` existe só com `creditCardId` (`InvoiceDao.kt:25-29`); sem cartão só
      há `observeUnpaidInvoices()` (que inclui `CLOSED`/`FUTURE`) e `getAllInvoices()`, que os
      chamadores filtram em memória (`InvoiceWriteGuard.kt:31`). Acrescentar
      `suspend fun getOpenInvoices(): List<Invoice>` sobre
      `SELECT * FROM invoices WHERE status = 'OPEN' ORDER BY openingMonth DESC`. Teste em
      `feature/creditcards/impl/src/commonTest/.../InvoiceRepositoryOpenInvoicesTest.kt`.
- [x] 2.3 **`accounts` — promoções.** Mover para
      `feature/accounts/api/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/`:
      `TransferBetweenAccountsUseCase` (`impl/.../TransferBetweenAccountsUseCase.kt:47` —
      `invoke(sourceAccountId, destinationAccountId, amount, date, destinationAmount = null):
      Either<TransferException, Transaction>`) e `AdjustBalanceUseCase`
      (`impl/.../AdjustBalanceUseCase.kt:23`). Ajustar
      `feature/accounts/impl/.../di/AccountsModule.kt` (linhas 97 e 103). Cada promoção é
      revisada como **contrato público** (`module-architecture`): assinatura, tipo de erro, e
      **nenhum `UiText` na fronteira** — conferir e registrar em KDoc. As duas dependem apenas
      de `:core:*` e da própria `api`, então vão como **classe concreta** na `api` (padrão 2 do
      `feature/README.md`: interface só quando há dependência interna). `CreateAccountUseCase`
      **não sobe** — criar conta está fora da superfície (`mcp-tool-surface`).
- [x] 2.4 **`creditcards` — promoções.** Mover para
      `feature/creditcards/api/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/`:
      `PayInvoicePaymentUseCase` (`impl/.../PayInvoicePaymentUseCase.kt:46`),
      `AdjustInvoiceUseCase` (`:22`), `CalculateInvoiceUseCase` (`:37`),
      `CalculateAvailableLimitUseCase` (`:42`) e `CalculateInvoiceOverviewsUseCase` (`:39`) —
      os dois primeiros porque as tools de escrita os invocam, os três últimos porque o
      panorama e a listagem de faturas os leem. As que dependerem de caso de uso interno viram
      **interface na `api` + `Impl` no `impl`** (padrão 2 do `feature/README.md`, como
      `GetOrCreateInvoiceForMonthUseCase` já é). Ajustar
      `feature/creditcards/impl/.../di/UseCaseModule.kt` (linhas 31, 39, 65, 42, 37). **Não
      sobem** `CloseInvoiceUseCase`, `OpenInvoiceUseCase`, `ReopenInvoiceUseCase`,
      `PayInvoiceUseCase`, `CreateInvoiceUseCase`, `AddCreditCardUseCase` — ciclo de vida de
      fatura e criação de cartão estão fora da entrega, e caso de uso não alcançado por tool
      permanece no `impl` (`mcp-tool-surface`, cenário "Caso de uso não alcançado permanece
      interno").
- [x] 2.5 **`transactions` — os dois casos de uso que faltavam.** Criar em
      `feature/transactions/api/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/`:
      `CreateTransactionUseCase` e `UpdateTransactionUseCase` (interfaces), com `Impl` em
      `feature/transactions/impl/.../domain/usecase/`, registrados em
      `feature/transactions/impl/.../di/TransactionsModule.kt`. Hoje **não existe caso de uso
      para nenhuma das duas**: `AddTransactionViewModel.kt:320-332` faz
      `buildTransactionUseCase(form)` e `transactionRepository.createTransaction(intent)`, e
      `EditTransactionViewModel.kt:276-284` faz `buildTransactionUseCase(form)` e
      `transactionRepository.updateTransaction(id, title, date, leg, contra)`. É exatamente o
      cenário "Regra que vivia embutida numa tela" do delta: a cópia embutida **deixa de
      existir** e os dois ViewModels passam a consumir o caso de uso —
      `MUST NOT existir caso de uso que só o servidor MCP usa`.
      `UpdateTransactionUseCase` recebe o `TransactionForm` inteiro e repete no KDoc a restrição
      já escrita em `ITransactionRepository.updateTransaction` (perna monetária única).
      **A restrição a categoria, descrição e data é da tool, não do caso de uso** (8.2): o que
      `mcp-tool-surface` proíbe é *oferecer* valor e conta a um agente, e a tela de edição
      oferece — `EditTransactionModal` liga `ChangeAmount`, `ChangeType`, `ChangeTarget`,
      `SelectCreditCard` e `SelectAccount`. Um caso de uso de três campos não serviria a ela, e
      o ViewModel manteria a cópia embutida, contrariando o `MUST NOT` acima. É a regra de
      derivação do projeto: o consumidor decide *se* oferece uma operação, nunca *qual* ela é.
      Testes em `feature/transactions/impl/src/commonTest/.../CreateTransactionUseCaseTest.kt` e
      `UpdateTransactionUseCaseTest.kt`; os testes existentes dos dois ViewModels continuam
      passando sem mudança de comportamento observável.
- [x] 2.6 **`:core:database` — `agent_activity`, a migração e os seus testes.** Delta
      `agent-activity-log`: o registro vive **ao lado das entidades de facade** e nunca no
      razão. Criar
      `core/database/src/commonMain/kotlin/com/neoutils/finsight/database/entity/AgentActivityEntity.kt`
      com `id`, `timestamp`, `client` (**anulável** — a ausência não é falha, e a próxima
      revisão do protocolo torna a identificação opcional), `tool`, `arguments` (json como
      recebido), `outcome` e `affected`; o DAO em `.../dao/AgentActivityDao.kt` com um `Flow`
      da atividade recente (a tela é reativa) e a **poda por retenção** (o registro guarda
      extratos inteiros — política de retenção declarada é requisito). Registrar em
      `AppDatabase.kt` (entidade + `abstract fun agentActivityDao()`, **`version = 15`**),
      criar `.../migration/Migration14To15.kt` e acrescentá-la ao `addMigrations(...)` de
      `.../database/Database.kt:39-51`, e ligar `single<AgentActivityDao> { … }` em
      `core/database/src/commonMain/kotlin/com/neoutils/finsight/di/DatabaseModule.kt` (junto
      dos bindings das linhas 47-60). Testes: `core/database/src/jvmTest/.../Migration14To15Test.kt`
      no molde de `Migration13To14Test.kt` (partir de `V12_SCHEMA`, replicar `12→13` e `13→14`
      reais, depois migrar), e acrescentar `Migration14To15` às cadeias de
      `MigrationSchemaEquivalenceTest.kt` (`:49-54` e as demais) — é ele que roda a validação
      de schema do próprio Room. O `core/database/schemas/…/15.json` é gerado pelo KSP e entra
      no commit.
- [x] 2.7 **As chaves de string, em pt e en no mesmo passo.** Acrescentar a
      `core/resources/src/commonMain/composeResources/values/strings.xml` (pt, o padrão) **e**
      `values-en/strings.xml` (en) as chaves da tela de configuração do MCP, no bloco dos
      `settings_*` (pt: linhas 872-878 e 916-917): título e subtítulo do tile em Settings;
      título da tela; o toggle "o servidor MCP existe" e o seu texto de apoio; os dois níveis
      de permissão (somente leitura / leitura e escrita) com a explicação de que a lista de
      tools de escrita some no primeiro; o endereço e o estado (no ar / desligado / **porta
      ocupada**, com o conflito nomeado); o token oculto por padrão, o botão de girar e o
      aviso do que girar quebra; o trecho de configuração de cliente, a ação de copiar e as
      **três coisas que o usuário não tem como deduzir** (`mcp-access-control`, "As instruções
      de conexão são completas"): que o acesso é local, que em somente leitura o agente não
      enxergará escrita alguma — apontando o controle que muda o nível —, e qual revisão do
      protocolo o servidor fala; o
      cabeçalho da atividade recente, o rótulo de cliente **declarado e não verificado**
      (`agent-activity-log`: "a etiqueta não é apresentada como fato"), e os três desfechos
      (sucesso / recusado / erro). Uma chave presente em só um dos arquivos é um bug.

---

## 3. Os contratos da feature `mcp` (`feature/mcp/api`)

Deltas: `mcp-access-control`, `agent-activity-log`, `module-architecture`.

**Barreira de entrada:** grupos 1 e 2 concluídos — os módulos existem e `agent_activity` está
no banco.
**Barreira de saída:** `./gradlew :feature:mcp:api:compileKotlinJvm` compila. Só existem
contratos, sem implementador — é o grupo 4 que os fecha. Três tarefas, cada uma num arquivo
novo, nenhuma lendo a saída da outra.

- [x] 3.1 **O estado do servidor.** `feature/mcp/api/src/commonMain/kotlin/com/neoutils/finsight/feature/mcp/api/McpServerSettings.kt`:
      `enum class McpPermission { READ_ONLY, READ_WRITE }`, `data class McpServerSettings(val
      isEnabled: Boolean, val permission: McpPermission, val port: Int, val token: String)` e
      `interface IMcpServerSettingsRepository` com `observe(): StateFlow<McpServerSettings>`,
      `setEnabled(Boolean)`, `setPermission(McpPermission)`, `setPort(Int)` e
      `rotateToken(): String`. KDoc obrigatório, porque cada uma destas é uma decisão da spec e
      não uma preferência: o servidor **nasce desligado** e, ligado, **nasce em somente
      leitura** — são dois riscos de tamanhos diferentes e duas chaves separadas (D8); a porta
      é **persistida e reusada**, nunca sorteada, porque a configuração colada num cliente
      contém a URL; e **desligar não gira o token**, senão o interruptor de segurança vira o
      que ninguém toca. O toggle significa **sempre e só** "o servidor MCP existe": a vida do
      processo é decisão de outras chaves, que não existem nesta entrega (D8).
- [x] 3.2 **A atividade.** `feature/mcp/api/.../feature/mcp/api/AgentActivity.kt`:
      `enum class AgentActivityOutcome { OK, REFUSED, FAILED }`, `data class AgentActivity(val
      id: Long, val timestamp: Instant, val client: String?, val tool: String, val arguments:
      String, val outcome: AgentActivityOutcome, val affected: List<String>)` e
      `interface IAgentActivityRepository` com `observeRecent(limit: Int): Flow<List<AgentActivity>>`,
      `record(activity: AgentActivity)` e `prune(olderThan: Instant)`. KDoc: **uma linha por
      chamada de tool**, não por linha escrita, recusadas incluídas; `client` é anulável e
      **autodeclarado, não autenticado**; o **token MUST NOT** constar de campo algum,
      `arguments` inclusive; leituras não produzem registro.
- [x] 3.3 **A rota e o entry point.** `feature/mcp/api/.../feature/mcp/api/McpRoute.kt`:
      `@Serializable data object McpRoute : NavRoute`, e `McpEntry.kt` com
      `interface McpEntry { context(builder: NavGraphBuilder) fun register() }`. A rota vive na
      `api` porque **`settings:impl` a nomeia** (critério de triagem: só entra na `api` o que
      outro módulo consome), e o entry point existe porque `SettingsGraph` ganha o destino
      (proposal, Impact) e `impl ⊄ impl` — é o quarto tipo de acesso cross-feature do
      `feature/README.md` ("Registro de subgrafo"), com `NavGraphBuilder` como **context
      parameter**, e não um `mcpGraph()` solto no `AppNavHost`.

---

## 4. As implementações da feature `mcp` (`feature/mcp/impl`, ainda sem tela)

**Barreira de entrada:** grupo 3 concluído — as interfaces existem, e cada tarefa aqui
satisfaz uma delas sem depender de irmã. Esta é a sequência que o desenho impõe: contrato
antes de implementador.
**Barreira de saída:** `./gradlew :feature:mcp:impl:jvmTest` verde; o estado do servidor
persiste entre execuções e a atividade é gravável e observável — sem nenhuma tela e sem
nenhum socket.

- [x] 4.1 **`McpServerSettingsRepository`** em
      `feature/mcp/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/McpServerSettingsRepository.kt`,
      sobre `com.russhwolf.settings.Settings` — o mecanismo de preferência do projeto (o único
      binding é `single<Settings> { Settings() }` em `core/common/.../di/CommonModule.kt:15`),
      no molde de `RateSyncStateRepository` (`feature/settings/impl/.../RateSyncStateRepository.kt`),
      que já guarda vários valores escalares sob `KEY_*` com `StateFlow` na leitura e `put` na
      escrita. Padrões: desligado, `READ_ONLY`, porta fixa persistida na primeira execução.
      O token é gerado por fonte **criptograficamente segura, ≥ 128 bits**, e comparado em
      **tempo constante** — nenhuma das duas coisas existe em `kotlin.random`, então entram
      como `expect fun` com actual JVM sobre `java.security.SecureRandom` /
      `MessageDigest.isEqual`. Elas vivem em **`:core:common`** (`security/Secrets.kt`,
      `secureRandomHex` + `constantTimeEquals`), e não neste módulo: quem **verifica** o token é
      o `BearerTokenAuth` do `:app:mcp` (6.2), que não enxerga `impl` algum — pôr o utilitário
      aqui deixaria os dois lados da comparação com donos diferentes. O actual do Android é o
      real, porque `java.security` existe lá e uma limitação fabricada não protege ninguém; o do
      iOS **lança**, porque "inalcançável" não pode significar segredo mais fraco em silêncio.
      O token **MUST NOT** ir para log. Teste em
      `feature/mcp/impl/src/commonTest/.../McpServerSettingsRepositoryTest.kt` com `MapSettings`:
      padrões ao nascer; porta sobrevive à releitura; desligar **não** muda o token; girar
      troca o token e o antigo não volta.
- [x] 4.2 **`AgentActivityRepository`** em
      `feature/mcp/impl/.../database/repository/AgentActivityRepository.kt` sobre o
      `AgentActivityDao` do grupo 2, mais o mapper entity↔model em
      `feature/mcp/impl/.../database/mapper/AgentActivityMapper.kt`. `observeRecent` devolve o
      `Flow` do Room (é o que sustenta "a atividade aparece sem recarregar"). Teste em
      `feature/mcp/impl/src/jvmTest/.../AgentActivityRepositoryTest.kt` contra
      `Room.inMemoryDatabaseBuilder`: uma chamada grava um registro só, a recusada também é
      gravada, cliente ausente não faz a gravação falhar, e a poda remove registro sem tocar
      nas transações que ele descrevia.
- [x] 4.3 **O módulo Koin da feature**, em
      `feature/mcp/impl/src/commonMain/kotlin/com/neoutils/finsight/di/McpModule.kt`:
      `val mcpFeatureModule = module { single<IMcpServerSettingsRepository> { … };
      single<IAgentActivityRepository> { … } }`. Os nomes das duas classes estão fixados em 4.1
      e 4.2; este arquivo é escrito só aqui, e ganha o `single<McpEntry>` e o `viewModel` no
      grupo 9.

---

## 5. O contrato da camada de tools (`:app:mcp`)

Delta: `mcp-tool-surface` — a forma do dinheiro, a forma do erro, a reprodutibilidade, a
paginação e as anotações. Nenhuma tool ainda.

**Barreira de entrada:** grupos 1 a 4 concluídos.
**Barreira de saída:** `./gradlew :app:mcp:jvmTest` verde. Existe **um** lugar que decide
como dinheiro, erro, página e default atravessam a fronteira — e é o que torna as 13 tarefas
do grupo 7 independentes entre si.

- [x] 5.1 **A forma do dinheiro**, em
      `app/mcp/src/commonMain/kotlin/com/neoutils/finsight/mcp/contract/MoneyPayload.kt`: todo
      valor atravessa como objeto com **moeda, inteiro na menor unidade e escala**, mais um
      texto formatado opcional **declarado como exclusivo de exibição**. Uma leitura que pode
      cruzar contas devolve **coleção por moeda que não colapsa com um elemento só** (D11: o
      consumidor aprenderia a forma escalar e quebraria na primeira conta em outra moeda); só
      leitura escopada a uma conta é escalar. O consolidado é campo **irmão**, nunca
      substituto, e carrega **taxa, data da taxa e se está defasada**; faltando taxa vem
      **ausente com motivo** — nunca taxa um, nunca a cotação de hoje no lugar da datada,
      nunca descarte silencioso. O consolidado vem de `ConsolidateMoneyUseCase`
      (`core/model/.../usecase/ConsolidateMoneyUseCase.kt`), que é **o único lugar do app onde
      uma taxa multiplica alguma coisa** — a fronteira MCP não ganha exceção por ser
      serializável (D5). O **sinal é o de exibição** em toda a superfície — despesa negativa,
      receita positiva —; a convenção débito-positivo do razão **MUST NOT** vazar. Atenção:
      `AccountType.displaySign` **não** produz isso sozinho. Ele é `if (isDebitNatured) 1 else
      -1` (`core/ledger/.../extension/Ledger.kt:20`) e existe para fazer conta credora ler
      positivo, o que deixa despesa **e** receita positivas — é o que `SpendingBreakdown` e
      `ViewCategoryViewModel` consomem, e não muda. O sinal desta superfície tem dono próprio
      (`DisplaySign`, um só lugar): conta **monetária** (`ASSET`/`LIABILITY`) lê com
      `displaySign`; conta **nominal** lê com `-1`, que é o sinal que a perna monetária da
      mesma transação carrega. Nenhum ponto de chamada decide sinal.
      Teste `MoneyPayloadTest.kt`: uma moeda continua coleção; taxa ausente não vira número;
      despesa negativa e receita positiva.
- [x] 5.2 **A forma do desfecho**, em `.../mcp/contract/ToolOutcome.kt`: recusa de regra do
      domínio é **erro de execução da tool**, marcado como tal dentro de um resultado de
      sucesso do transporte — nunca um resultado comum com objeto de erro dentro, porque hosts
      que não veem a marcação relatam ao usuário que a operação foi feita. O desfecho carrega
      **classe** (regra de domínio, não encontrado, entrada inválida, conflito,
      indisponibilidade, falha interna), **código estável e enumerado**, **mensagem em inglês
      para log** e **se pode ser repetida** — regra de domínio sempre não repetível,
      indisponibilidade e falha interna repetíveis. A mensagem é a `message: String` dos tipos
      de erro do projeto; `toUiText()` **MUST NOT** atravessar (D6). Aviso é campo
      estruturado, não prosa. Ausência de taxa **não é erro**: é aviso num resultado
      bem-sucedido. Teste `ToolOutcomeTest.kt`.
- [x] 5.3 **Página, teto e eco de defaults**, em `.../mcp/contract/Page.kt` e
      `.../mcp/contract/AssumedDefaults.kt`: paginação por **cursor opaco** (não deslocamento
      numérico, que duplica e pula itens diante de escrita concorrente), com o **total de
      registros que satisfazem o filtro**; limite acima do teto é **recusado com erro que
      nomeia o teto**, nunca truncado em silêncio; **todo default assumido é ecoado** — data
      de referência, período, recorte de arquivados —, arquivados ficam de fora por omissão e
      o recorte aplicado consta da resposta; datas são **civis, no fuso do usuário**, e a
      superfície **MUST NOT** interpretar período em linguagem natural. O **limite de tamanho
      de resposta** é declarado aqui e vale também para os agregados, que não paginam.
      Teste `PageTest.kt` e `AssumedDefaultsTest.kt`.
- [x] 5.4 **O registro de tools, os nomes e as anotações**, em
      `.../mcp/contract/ToolRegistry.kt`: a interface que toda tool implementa (nome, título,
      descrição, `inputSchema`, **`outputSchema` obrigatório**, anotações, execução) e o
      registro que o servidor consulta. Nomes **prefixados por `finsight_`** e contendo o
      verbo, dentro do conjunto de caracteres que o protocolo recomenda — clientes agregam
      servidores num espaço de nomes único. As anotações do protocolo (somente-leitura,
      destrutiva, idempotente, domínio aberto) são **verdadeiras**, e anotar **não substitui
      aplicar**: o registro expõe o predicado `isWrite`, que o servidor usa para esconder *e*
      para recusar. Teste `ToolRegistryTest.kt`: nenhum nome sem prefixo, nenhuma tool sem
      `outputSchema`, nenhuma anotada somente-leitura que declare escrita.

---

## 6. O servidor: transporte, autenticação e ciclo de vida

Deltas: `mcp-server`, `mcp-access-control`.

**Barreira de entrada:** grupos 4 e 5 concluídos — o estado persistido existe (o servidor o
lê) e o registro de tools existe (o servidor o lista).
**Barreira de saída:** `./gradlew :app:mcp:jvmTest` verde e o servidor **sobe, negocia
`initialize` e responde a uma listagem vazia de tools** — é essa barreira que torna o grupo 7
verificável ponta a ponta em vez de só compilável.

- [x] 6.1 **O transporte e o `Origin`**, em
      `app/mcp/src/jvmMain/kotlin/com/neoutils/finsight/mcp/transport/McpHttpTransport.kt`
      sobre `ktor-server-cio`: **um único caminho de endpoint**, `POST` para requisições e
      `GET` para o fluxo de notificações, associado **exclusivamente a `127.0.0.1`** e nunca a
      todas as interfaces. `Origin` presente e não reconhecido → **`403` antes de qualquer tool
      executar e antes de qualquer leitura do banco** (é `MUST` da revisão, e sem ela uma
      página web aberta pelo usuário alcança o servidor por DNS rebinding — e este servidor
      escreve no razão). Header de versão de protocolo obrigatório; versão inválida ou não
      suportada → **`400`**. **Nenhuma sessão é atribuída** (a revisão permite, e um
      identificador a mais é um segredo a mais para vazar). Teste
      `McpHttpTransportTest.kt`.
- [x] 6.2 **A autenticação**, em `.../mcp/transport/BearerTokenAuth.kt`: token no header de
      autorização, esquema de portador; **em query string é recusado e o token é tratado como
      comprometido**; comparação em tempo constante (o utilitário é o do 4.1). Sem token ou
      com token inválido → **`401` com o desafio de autorização apontando o documento de
      metadados do recurso protegido**, para que um cliente conforme falhe de forma legível.
      Token **MUST NOT** ir para log, telemetria ou registro de atividade. O **desvio
      deliberado** da especificação de autorização do MCP (OAuth 2.1 para um servidor loopback
      de usuário único é desproporcional) fica **escrito no KDoc**, não subentendido — é o que
      a spec exige. Teste `BearerTokenAuthTest.kt`.
- [x] 6.3 **O limite de taxa**, em `.../mcp/transport/ToolRateLimiter.kt`: a revisão exige
      como `MUST`, e nenhuma versão anterior desta proposta o tinha. Recusa **nomeada e
      repetível**, distinguível de recusa de regra do domínio (classe do 5.2), e **nenhuma
      escrita acontece** na chamada recusada. Teste `ToolRateLimiterTest.kt`.
- [x] 6.4 **A inicialização e as capabilities**, em `.../mcp/server/McpServerCapabilities.kt`:
      negociar a revisão **`2025-11-25`** — a mais recente que o SDK Kotlin fala — e declarar
      as capabilities, **incluindo o aviso de mudança na lista de tools**. **MUST NOT** ofertar
      **Roots, Sampling ou Logging**, que a revisão seguinte já depreciou, para não acumular o
      que a migração teria de desfazer. O nome que o cliente declara sobre si no `initialize`
      é capturado aqui e é o que alimenta o campo `client` do registro de atividade — e é
      **autodeclarado, não autenticado**. A **defasagem é dívida datada e fica escrita na
      documentação do servidor** (`app/mcp/README.md`, novo), com o gatilho objetivo: o SDK
      passar a falar a `2026-07-28`. Teste `McpServerCapabilitiesTest.kt`.
- [x] 6.5 **Cancelamento e interrupção**, em `.../mcp/server/CancellationHandling.kt`: nesta
      revisão **perder a conexão MUST NOT ser cancelamento** — o cliente cancela por
      notificação explícita, e é a inversão que mais importa em relação à revisão seguinte
      (D12). Recebido o cancelamento, o servidor para assim que praticável e **não emite mais
      nada para aquela requisição**. Nos dois casos, um lote interrompido tem desfecho
      definido: o que foi aplicado permanece, e repetir com a mesma chave conclui o que faltou.
      Operações longas emitem progresso. Teste `CancellationHandlingTest.kt`.
- [x] 6.6 **O ciclo de vida e o dono único do banco**, em
      `app/mcp/src/commonMain/kotlin/com/neoutils/finsight/mcp/McpServerController.kt`
      (`expect`) com actual JVM em `app/mcp/src/jvmMain/.../McpServerController.jvm.kt` e
      actuals inertes nos demais targets: `start()`/`stop()`, reagindo ao toggle e ao nível de
      permissão de `IMcpServerSettingsRepository` **sem nenhum elemento de interface do
      Finsight** — não é iniciado de escopo de composição, não lê estado de janela, e nenhuma
      tool exige interação na tela do app (D9; o que sobra de consentimento é a política, ou
      seja, o que Settings permite). Desligado, **não existe socket em escuta**. Porta ocupada
      → o servidor **não sobe** e o conflito é publicado no estado para a tela mostrar; nenhuma
      outra porta é assumida em silêncio. Mudança de nível em tempo de execução emite o **aviso
      de mudança na lista de tools**. E o **dono único do banco**: nenhum caminho pode subir
      uma segunda instância da aplicação sobre `~/.finance/finsight.db`
      (`core/database/src/jvmMain/.../Database.jvm.kt:8`) — dois `InvalidationTracker` não se
      cruzam, e o app passaria a mentir sobre o saldo. Teste
      `McpServerControllerTest.kt`.

---

## 7. As leituras, os resources, os prompts e o caminho de escrita

Deltas: `mcp-tool-surface` (a superfície mínima, as três primitivas), `agent-activity-log`.

**Barreira de entrada:** grupo 6 concluído. A dependência é de **ordem, não de conteúdo** —
as tools consomem só o contrato do grupo 5. O grupo 6 vem antes porque é ele que torna a
barreira verificável: sem servidor no ar, nenhuma tool é exercitável ponta a ponta.
**Barreira de saída:** `./gradlew :app:mcp:jvmTest` verde; um cliente MCP conectado lista as
**nove leituras** e busca os resources; as tools de escrita ainda não existem. Treze tarefas,
um arquivo de tool cada, sob `app/mcp/src/commonMain/kotlin/com/neoutils/finsight/mcp/tool/`
— nenhuma toca o arquivo de outra.

- [x] 7.1 **`finsight_get_overview`** (`GetOverviewTool.kt`): o ponto de entrada dos
      identificadores — moeda base (`IBaseCurrencyRepository`), patrimônio **por moeda**
      (`IEntryRepository.balanceUpToByCurrency`), saldo por conta, resumo de cartões
      (`CalculateInvoiceUseCase`/`CalculateAvailableLimitUseCase`, promovidos em 2.4) e
      **cobertura do acervo de taxas** (`IExchangeRateRepository`). Coleção por moeda que não
      colapsa (5.1).
- [x] 7.2 **`finsight_list_accounts`** (`ListAccountsTool.kt`) sobre `IAccountRepository`.
      **Contas de sistema nunca aparecem** — as duas nominais, a de reconciliação e a de
      conversão são criadas sob demanda pela fronteira de escrita, são mecanismo e não fato do
      usuário, e um agente que as enxergasse as citaria como destino do dinheiro dele.
- [x] 7.3 **`finsight_list_categories`** (`ListCategoriesTool.kt`) sobre `ICategoryRepository`,
      arquivadas fora por omissão e o recorte ecoado (5.3).
- [x] 7.4 **`finsight_list_transactions`** (`ListTransactionsTool.kt`) sobre o
      `getTransactionsBy` do 2.1 — recorte por **período**, conta, cartão (que é
      `creditCard.accountId`, não um filtro próprio), fatura, **categoria em três estados**
      (qualquer, uma dada, sem classificação — a ausência de dimensão, nunca um balde) e faixa
      de valor. Compra de cartão devolve **as duas datas** (a da compra e a da fatura em que
      caiu) e o filtro **declara sobre qual delas recorta**. A descrição da tool **nomeia
      `finsight_aggregate_transactions`** como o caminho para totais. Nada é **agregado** fora do
      dono da leitura, e o recorte que o razão sabe expressar — período, conta, dimensão — desce
      até ele (2.1). Dois cortes **não** descem, e é a decisão certa: "sem classificação" é o
      predicado `Transaction.matches(SpendingSubject)` e faixa de valor lê `Transaction.amount`,
      ambos **derivados das entradas e não persistidos**. Empurrá-los para SQL seria uma segunda
      derivação da mesma regra, o que a regra de derivação proíbe com mais força do que a linha
      sobre filtrar fora do dono — e é exatamente por isso que `TransactionsViewModel`,
      `InvoiceTransactionsViewModel` e `InstallmentsViewModel` já os aplicam assim. A tool
      consome os mesmos predicados do domínio; não escreve os seus.
- [x] 7.5 **`finsight_aggregate_transactions`** (`AggregateTransactionsTool.kt`): totais por
      categoria, mês, conta ou cartão, sobre `IEntryRepository.totalsByDimensionByCurrency` /
      `scopeStatsByCurrency`. **Calculado no servidor sobre o conjunto completo e sem
      paginação** — é a tool que mais protege o domínio: sem ela o agente pagina, soma e
      apresenta como exato, errando por moeda e contando como gasto o que o domínio não
      classifica como gasto (D10). Recorte que excederia o limite declarado é **recusado com
      orientação de como reformular**, nunca despejado.
- [x] 7.6 **`finsight_list_invoices`** (`ListInvoicesTool.kt`) sobre `IInvoiceRepository`,
      incluindo o `getOpenInvoices()` do 2.2, com o devido por fatura via
      `CalculateInvoiceUseCase`.
- [x] 7.7 **`finsight_list_budgets`** (`ListBudgetsTool.kt`) sobre `IBudgetRepository` +
      `CalculateBudgetProgressUseCase` (já na `api`). O orçamento **declara a sua moeda** e
      não tem moeda por omissão — a resposta a informa sempre.
- [x] 7.8 **`finsight_list_recurring`** (`ListRecurringTool.kt`) sobre `IRecurringRepository`,
      `IRecurringOccurrenceRepository` e `GetPendingRecurringUseCase` (já na `api`), incluindo
      as **ocorrências pendentes**. Somente leitura: confirmar, pular e parar são ciclo de vida
      e estão fora desta entrega.
- [x] 7.9 **`finsight_list_installments`** (`ListInstallmentsTool.kt`) sobre
      `IInstallmentRepository`.
- [x] 7.10 **Os resources**, em `.../mcp/resource/OrientationResources.kt`: panorama, contas e
      categorias **também** como resources endereçáveis, além de alcançáveis por tool. São
      documento estável cuja função é estar disponível **antes** de qualquer decisão; como
      tool dependem de o modelo escolher chamá-los, e um modelo que não chama **chuta
      identificadores**.
- [x] 7.11 **Os prompts**, em `.../mcp/prompt/UserFlowPrompts.kt`: lançar o extrato do mês e
      revisar o mês. **Um prompt é texto, não lógica** — ele não decide qual regra se aplica,
      e é por isso que oferece o vocabulário do usuário sem o risco de um verbo agregador em
      forma de tool (D4/D10). Os prompts nomeiam as tools existentes e não introduzem nenhuma.
- [x] 7.12 **O resolvedor de item**, em `.../mcp/write/TransactionItemResolver.kt`: traduz um
      item de intenção (despesa, receita, compra em cartão inclusive parcelada, transferência,
      pagamento de fatura, ajuste de conta, ajuste de fatura) no caso de uso dono e nos seus
      argumentos, **sem decidir regra nenhuma**: em qual fatura uma compra cai é resolvido pelo
      domínio, e o dono não é o que esta tarefa dizia. `GetOrCreateInvoiceForMonthUseCase` recebe
      `(creditCard, targetDueMonth)` — quem traduz **data → mês da fatura** é
      `CreditCard.invoiceWindowOn(date)` / `dueMonthFor(...)`, em `core/model/InvoiceWindow.kt`,
      e é esse o enunciado único de `invoice-governs-date`. O resolvedor consome os dois e não
      reimplementa nenhum. Identificadores são **opacos** — nome, rótulo ou texto livre
      **nunca** identificam conta, categoria, cartão, fatura ou orçamento; categoria
      inexistente é **entrada inválida que nomeia a categoria pedida**, e **nenhuma categoria é
      criada implicitamente** (um agente que criasse categoria durante importação produziria
      variações da mesma a cada extrato). Espelha o que o domínio já decidiu: transferência
      entre moedas informa **os dois valores e nenhuma taxa** (a taxa é o quociente, derivada e
      arquivada pelo próprio domínio), e **compra parcelada gera todas as parcelas numa
      operação** via `AddInstallmentUseCase`, nunca uma chamada por parcela. Nenhuma perna,
      nenhum valor assinado, nenhum rótulo de transação como entrada — o rótulo volta
      **derivado**. Teste `TransactionItemResolverTest.kt`.
- [x] 7.13 **A idempotência e o registro na execução**, em
      `.../mcp/write/IdempotencyStore.kt` e `.../mcp/write/ActivityRecorder.kt` (dois arquivos,
      uma tarefa: a chave e o registro são gravados no mesmo ponto do fluxo). A chave é
      avaliada **junto com um resumo dos argumentos**: mesma chave e mesmos argumentos não
      duplica; mesma chave com argumentos diferentes é **conflito**, porque agentes reutilizam
      strings e transformar a segunda remessa em operação nula descartaria lançamentos
      legítimos em silêncio — o desfecho mais grave que esta superfície pode produzir. Os
      registros têm **prazo de validade declarado** e **local próprio**, porque guardam a
      resposta produzida, que o registro de atividade não guarda. O `ActivityRecorder` grava
      **uma linha por chamada de tool de escrita**, recusadas incluídas, por
      `IAgentActivityRepository` (4.2), **sem o token em campo algum**; leituras não produzem
      registro. Testes `IdempotencyStoreTest.kt` e `ActivityRecorderTest.kt`.

---

## 8. As tools de escrita e o ensaio

**Barreira de entrada:** grupo 7 concluído — o resolvedor, a idempotência e o registro
existem. Esta sequência é imposta pelo desenho: o ensaio devolve **exatamente o que seria
gravado**, inclusive a fatura resolvida, e por isso consome o mesmo resolvedor; dois
resolvedores seriam duas respostas para "em qual fatura isto cai".
**Barreira de saída:** `./gradlew :app:mcp:jvmTest` verde; com o nível em **leitura e
escrita** o cliente lista as treze tools, e em **somente leitura** não lista **nenhuma das três
escritas**. O ensaio **continua listado** em somente leitura, e isto é correção desta barreira,
não desvio: ele é honestamente somente-leitura, `ToolRegistry.isPermitted` deriva a visibilidade
**só** da anotação, e o delta `mcp-access-control` exige esconder as tools *de escrita*.
Escondê-lo pediria mentir na anotação — que a spec proíbe — ou um caso especial que faria
esconder e recusar divergirem.

- [x] 8.1 **`finsight_record_transactions`** (`RecordTransactionsTool.kt`): **de um a muitos
      itens na mesma chamada** — lançar um extrato é pedido de primeira classe, e uma chamada
      por linha multiplica as chances de falha parcial silenciosa (D10). Invoca o **mesmo caso
      de uso por item** e **não ganha comportamento que a operação unitária não tenha**.
      Resposta com **desfecho por item** (gravado, recusado, ignorado por duplicidade) e
      quantos foram aplicados — sucesso agregado sem detalhe **MUST NOT** ser devolvido.
      **Duplicata provável é aviso no item e não bloqueia a gravação**: importar o mesmo
      extrato duas vezes é o erro mais comum que existe, e decidir sozinho que um lançamento
      legítimo é repetido é o erro oposto. Anotada como **não somente-leitura e idempotente**.
- [x] 8.2 **`finsight_update_transactions`** (`UpdateTransactionsTool.kt`) sobre o
      `UpdateTransactionUseCase` do 2.5: **apenas categoria, descrição e data**. Valor e conta
      **não são oferecidos** — mudar o dinheiro de um lançamento é removê-lo e criar outro, e
      uma edição que disfarçasse isso esconderia a correção.
- [x] 8.3 **`finsight_delete_transactions`** (`DeleteTransactionsTool.kt`) sobre
      `DeleteTransactionUseCase` (já na `api`), anotada como **destrutiva**.
- [x] 8.4 **`finsight_preview_transactions`** (`PreviewTransactionsTool.kt`): o ensaio, **tool
      própria** e não parâmetro booleano da escrita — uma tool que é somente-leitura ou
      destrutiva conforme um argumento não pode ser anotada com honestidade, e é pela anotação
      que o cliente decide pedir confirmação ao usuário. Devolve **exatamente o que seria
      gravado**, com as faturas resolvidas, e **não persiste nada** sob
      nenhum argumento. Anotada como **somente-leitura**. **O rótulo não vai no ensaio**: ele é
      derivado das pernas (`Transaction.label` → `deriveTransactionLabel`), que só existem depois
      da gravação, e calculá-lo antes seria uma segunda implementação da derivação. O ensaio
      devolve a intenção, a fatura resolvida e as referências; o rótulo derivado volta na
      resposta do `record`, depois de escrito. Nota de contagem: a spec enumera nove
      leituras e três escritas e, em requisito separado, exige que o ensaio seja tool própria —
      são **treze** tools anunciadas, e este é o item que fecha a conta.

---

## 9. A tela de configuração e a agregação no shell

Deltas: `mcp-access-control` (habilitar entrega configuração válida), `agent-activity-log`
(atividade consultável e reativa), `module-architecture`.

**Barreira de entrada:** grupos 2, 4 e 8 concluídos — as chaves de string existem, os
repositórios existem, e existe superfície para o toggle governar.
**Barreira de saída:** `./gradlew jvmTest` verde e `./gradlew :app:android:assembleDebug`
compila; a tela abre a partir de Settings, o toggle liga o servidor no desktop e a atividade
aparece sem recarregar.

- [ ] 9.1 **A tela**, em `feature/mcp/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/mcp/`
      — `McpScreen.kt`, `McpUiState.kt`, `McpViewModel.kt`, no molde de
      `feature/settings/impl/.../ui/screen/exchangeRates/`: o toggle, o nível de permissão, a
      porta com o conflito quando ela estiver ocupada, o token **oculto por padrão** com botão
      de girar, o **trecho de configuração de cliente pronto para colar** com ação de copiar —
      apresentado já no **primeiro `on`**, porque um toggle ligado com um token que ninguém
      colou em lugar nenhum é um estado "funcionando" que não funciona (D8) — e a **atividade
      recente**, reativa, com o cliente rotulado como **identificação declarada e não
      verificada**. **Não existe comando genérico de desfazer**: cada item leva à entidade que
      tocou, de onde a operação inversa já está disponível quando o domínio a oferece.
      `Modifier.testTag` nos controles e `Modifier.exposeTestTags()`
      (`core/designsystem/.../ui/util/ExposeTestTags.kt:20`) em qualquer raiz de composição
      nova. Toda decisão (o que mostrar, o que habilitar) vive no `UiState`, nunca no
      composable. Teste `feature/mcp/impl/src/commonTest/.../McpViewModelTest.kt`.
- [ ] 9.2 **O grafo e o entry point**, em
      `feature/mcp/impl/.../ui/navigation/McpGraph.kt` (a extension `internal fun
      NavGraphBuilder.mcpGraph()`, com o `composable<McpRoute>`) e
      `feature/mcp/impl/.../feature/mcp/impl/McpEntryImpl.kt` (`internal class McpEntryImpl :
      McpEntry`, chamando `mcpGraph()` a partir do `NavGraphBuilder` do contexto), no molde de
      `BudgetsEntryImpl`. A extension fica `internal`, invocada apenas pelo próprio
      `EntryImpl` — é a regra do `feature/README.md` para feature hospedada.
- [ ] 9.3 **O destino dentro de `SettingsGraph`**, em
      `feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/navigation/SettingsGraph.kt`
      (registrar `koin.get<McpEntry>().register()` dentro do `navigation<SettingsGraph>`,
      resolvendo por `KoinPlatform.getKoin()` porque o lambda de `NavGraphBuilder` não é
      `@Composable`) e em
      `feature/settings/impl/.../ui/screen/settings/SettingsScreen.kt` (o tile que navega para
      `McpRoute`, no `SettingsGroup` existente, com as chaves do 2.7). O tile é oferecido
      apenas onde o servidor pode existir — `isDesktop` de
      `core/common/.../Platform.kt:9`. Acrescentar `implementation(projects.feature.mcp.api)`
      em `feature/settings/impl/build.gradle.kts`.
- [ ] 9.4 **Os bindings que faltam**, em
      `feature/mcp/impl/src/commonMain/kotlin/com/neoutils/finsight/di/McpModule.kt`:
      `single<McpEntry> { McpEntryImpl() }` e `viewModel { McpViewModel(…) }` ao lado dos dois
      `single` do 4.3.
- [ ] 9.5 **A agregação no shell**, em
      `app/shared/src/commonMain/kotlin/com/neoutils/finsight/di/AppModules.kt`: acrescentar
      `mcpFeatureModule` e `mcpModule` à lista (linhas 5-25). É a mudança inteira do shell —
      `AppNavHost` **não muda**, porque o destino entra pelo `SettingsGraph` (9.3). Estender
      `app/shared/src/jvmTest/.../AppModulesTest.kt` com a resolução das duas ligações novas,
      no molde dos casos que já estão ali.
- [ ] 9.6 **O módulo Koin do `:app:mcp`**, em
      `app/mcp/src/commonMain/kotlin/com/neoutils/finsight/mcp/di/McpServerModule.kt`
      (`val mcpModule`): as treze tools, os resources, os prompts, o registro (5.4), o
      transporte, a idempotência, o `ActivityRecorder` e o `McpServerController`. Nome fixado
      aqui e consumido por 9.5.

---

## 10. O bootstrap do desktop

Delta: `module-architecture` ("plataforma apenas hospeda"), `mcp-server`.

**Barreira de entrada:** grupo 9 concluído — `mcpModule` está agregado e o controlador existe.
**Barreira de saída:** `./gradlew :app:desktop:run` sobe o app; com o servidor habilitado,
um cliente MCP conecta; fechar a janela derruba o socket.

- [ ] 10.1 **`app/desktop/src/main/kotlin/com/neoutils/finsight/main.kt`**: obter o
      `McpServerController` do Koin logo depois de `startKoin { modules(appModules) }`
      (`:23-28`) e subi-lo/derrubá-lo com o **processo**, não com a composição — **nada de
      `LaunchedEffect`** (D9 e `mcp-server`: o servidor não é iniciado a partir de escopo de
      composição e não lê estado de janela), ao contrário dos dois `LaunchedEffect` que o
      arquivo já tem para tamanho de janela. `onCloseRequest` derruba o servidor antes de
      `exitApplication`. **Nenhuma lógica** entra aqui: só o bootstrap.
- [ ] 10.2 **A instância única**, em
      `app/desktop/src/main/kotlin/com/neoutils/finsight/SingleInstanceGuard.kt` (novo). Só a
      implementação: quem a invoca é 10.1, pelo nome fixado aqui, para que as duas tarefas não
      escrevam no mesmo arquivo. Uma segunda tentativa de iniciar a aplicação **não abre o
      banco**, e o processo existente permanece o único dono de
      `~/.finance/finsight.db`: dois `InvalidationTracker` não se cruzam e haveria dois
      candidatos a executar migração no mesmo arquivo (D1). Teste
      `app/desktop/src/test/kotlin/.../SingleInstanceGuardTest.kt`.

---

## 11. Verificação

**Barreira de entrada:** grupos 1 a 10 concluídos.
**Barreira de saída:** cada item abaixo foi **executado e a saída lida**. O que não foi
verificado está dito, não implícito.

- [ ] 11.1 `./gradlew jvmTest` — a suíte inteira verde. Relatar qualquer falha com o teste e o
      arquivo, sem presumir que é pré-existente.
- [ ] 11.2 `./gradlew :core:database:jvmTest --tests "*Migration14To15*" --tests "*MigrationSchemaEquivalence*"`
      — a migração `14 → 15` roda e o Room valida o schema que a cadeia inteira produz.
- [ ] 11.3 `./gradlew :app:android:assembleDebug` e `./gradlew :app:desktop:run` — compilam e
      sobem. O Android compila **com o servidor inexistente**: os actuals não-JVM são inertes e
      nenhum socket abre.
- [ ] 11.3b **O app empacotado, não só o `run`.** `./gradlew :app:desktop:createDistributable` e
      subir o servidor **a partir da imagem produzida**. O `run` prova o classpath do Gradle; a
      promessa é outra — o usuário instala **um** app e o servidor vem junto, sem segundo
      binário (é o que D2 comprou ao derrubar a ponte stdio). Duas coisas só falham aqui: o
      `jlink` recorta o runtime pela lista explícita de `modules(...)` em
      `app/desktop/build.gradle.kts`, que foi levantada por `suggestRuntimeModules` **antes** de
      existir um servidor HTTP no classpath — rodar `./gradlew :app:desktop:suggestRuntimeModules`
      de novo e acrescentar o que faltar; e qualquer descoberta por `ServiceLoader` que a imagem
      empacotada resolva diferente do classpath de desenvolvimento. Relatar a plataforma em que
      a imagem foi gerada, e que um cliente MCP conectou nela.
- [ ] 11.4 Conferir que **cada chave nova de string existe nos dois arquivos**
      (`values/strings.xml` e `values-en/strings.xml`).
- [ ] 11.5 **Medir**, e registrar o número: a listagem de tools serializada e uma página
      típica de lançamentos. O design declara o volume das respostas como risco **sem número**
      e transforma essas duas medidas em critério de aceitação — a forma exigida (coleção por
      moeda que não colapsa, identificador junto do nome em todo objeto aninhado, proveniência
      de taxa, eco de todo default) é individualmente justificada e no conjunto produz
      respostas grandes.
- [ ] 11.6 **Provar a regra do build, não confiar nela**: acrescentar temporariamente
      `implementation(projects.feature.transactions.impl)` a `app/mcp/build.gradle.kts`,
      confirmar que o build **falha** nomeando a regra violada, e reverter. É o cenário
      "servidor MCP declara dependência de impl" do delta `build-conventions`, e ele não tem
      teste automatizado — o projeto não tem teste de arquitetura, a garantia é do convention
      plugin.
- [ ] 11.7 **Exercitar o servidor à mão** com um cliente MCP real, e relatar qual: o `initialize`
      negocia a `2025-11-25`; em somente leitura a listagem **não** contém escrita; mudar o
      nível emite o aviso de mudança de lista; requisição sem token recebe `401` com desafio;
      `Origin` desconhecido recebe `403`; girar o token derruba o cliente antigo e não o novo;
      fechar e reabrir o app **não** exige reconfigurar o cliente.
- [ ] 11.8 **Não há execução de E2E nesta mudança, e isso é decisão, não esquecimento.** A
      suíte `.maestro` dirige o app **Android**; o servidor MCP existe apenas no desktop, e a
      tela de configuração é oferecida só lá (`isDesktop`, 9.3). O que sustenta esta mudança é
      a suíte unitária (11.1-11.2) e a conferência manual de 11.7. Registrar isso no relato,
      em vez de reportar cobertura que não existe.
