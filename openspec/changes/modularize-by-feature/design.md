## Context

O projeto é KMP (Android/iOS/Desktop) com Compose Multiplatform, Room, Koin e Firebase. Atualmente existe um único módulo `:composeApp` com ~50 use cases, ~12 repositórios, ~35 modais e ~13 screens organizados por camada (domain/, database/, ui/). A UI já está organizada por feature em `ui/screen/`, mas domínio e banco são planos.

Restrições relevantes:
- Room exige que todos os `@Entity` estejam visíveis ao `@Database` no momento da compilação via KSP — impossibilita entidades distribuídas por módulos sem multi-module Room (complexo em KMP)
- KSP em KMP requer 6 configurações por módulo (commonMainMetadata, Android, iOS x3, JVM)
- iOS produz um único framework estático — todos os módulos KMP são linked juntos

## Goals / Non-Goals

**Goals:**
- Isolar compilação: mudança em `:feature:transactions:impl` não recompila `:feature:accounts:impl`
- Estabelecer boundaries de domínio explícitos via módulos Gradle
- Eliminar dependências acidentais entre features
- Preparar base para build incremental real no Gradle

**Non-Goals:**
- Multi-module Room (cada feature com seu próprio banco) — complexidade não justificada agora
- Dynamic feature modules (carregamento lazy) — não se aplica a KMP
- Mudanças de comportamento do produto — refactoring estrutural puro

## Decisions

### D1: Padrão api/impl por feature

**Decisão:** Cada feature tem dois módulos: `:feature:X:api` (contrato público) e `:feature:X:impl` (implementação). Nenhum `:impl` depende de outro `:impl`.

**Rationale:** Isola compilação incremental e torna dependências cruzadas explícitas e verificadas em tempo de build.

**Alternativa considerada:** Módulo único por feature (sem separação api/impl). Mais simples, mas permite dependências impl-to-impl acidentais e reduz o ganho de build incremental.

---

### D2: Cada feature é seu próprio domínio — modelos puros em `:feature:X:api`

**Decisão:** Modelos de domínio, interfaces de repositório e interfaces de use cases ficam no `:api` da feature dona. Modelos cujo domínio é genuinamente cross-feature passam a guardar **apenas IDs** das outras features (não objetos resolvidos), permitindo que cada model viva no `:api` da sua feature dona sem violar D10 (ver D14).

**Regra fundamental — api não depende de api:** `feature:X:api` **jamais** depende de `feature:Y:api`. Dois motivos críticos: (1) dependências cíclicas entre `:api` são detectadas pelo Gradle apenas em runtime de configuração, bloqueiam o build inteiro e são difíceis de rastrear à medida que a base de código cresce; (2) qualquer mudança em `Y:api` força recompilação de `X:api` e de todos os seus dependentes, colapsando o isolamento incremental que justifica a modularização. Ver D10 para detalhes e tabela de dependências permitidas.

**Modelos por feature (após D14):**
- `:feature:accounts:api` → `Account`
- `:feature:categories:api` → `Category`
- `:feature:creditCards:api` → `CreditCard`, `Invoice` (com `creditCardId: Long` em vez de `creditCard: CreditCard`)
- `:feature:transactions:api` → `Transaction` (IDs only), `Operation` (IDs only), `OperationPerspective`, `OperationInstallment`, `OperationRecurring`, `TransactionForm`
- `:feature:recurring:api` → `Recurring` (IDs only), `RecurringOccurrence`
- `:feature:budgets:api` → `Budget` (com `categoryIds: List<Long>`)

**O que NÃO vai para `:api`:** erros, exceções, interfaces de repositório, use cases — esses permanecem em cada `:feature:X:api` (e não em `:core`); UI models (`OperationUi`, `InvoiceUi`, etc.) ficam em `:feature:X:ui` (D14).

**Por que IDs only:** quando `Transaction.account: Account?` referencia `Account` de `accounts:api`, `transactions:api → accounts:api` viola D10. Substituir por `accountId: Long?` quebra a dependência. A hidratação (resolver IDs em objetos para display) acontece no tier `:ui` via `XxUi` + `IXxUiMapper` (D14). Use cases puros que precisam de objetos resolvidos consultam o repositório.

**Alternativa rejeitada:** `:core:domain` centralizado com todos os modelos. Se torna um "deus módulo" sem boundary real, com ownership diluído e recompilação em cascata. Eliminado em §21 (ver D14).

---

### D3: Room centralizado em `:core:database`

**Decisão:** `AppDatabase`, todas as entities e DAOs ficam em `:core:database`. Mappers e repository implementations pertencem a cada `feature:X:impl` — migram junto com o domínio de cada feature nas seções 8–16.

**Rationale:** KSP Room em KMP multi-módulo é instável. O `@Database` precisa ver todos os `@Entity`. Centralizar entities e DAOs elimina esse problema. Mappers e repos ficam em `:impl` porque implementam contratos de domínio da feature e não são infraestrutura Room pura.

**Alternativa considerada (Opção A):** Entities em cada `:feature:impl`, `@Database` no `:app`. Possível com Room 2.6+ mas aumenta complexidade de build e tem bugs conhecidos em KMP com KSP cross-module.

**Caminho de melhoria:** Quando Room multi-module KMP estabilizar, migrar entities para cada `:feature:impl` e `@Database` para `:app`.

---

### D4: Quebra do ciclo recurring ↔ transactions

**Decisão:** `Recurring` define seu próprio `Recurring.Type { INCOME, EXPENSE }`, independente de `Transaction.Type`. O mapper em `:core:database` faz a conversão.

**Rationale:** `recurring:api` com dep em `transactions:api` e `transactions:api` com dep em `recurring:api` (via `OperationRecurring`) cria ciclo insolúvel no grafo de módulos Gradle.

**Evidência:** `RecurringEntity.Type` já existe como enum próprio com `{ EXPENSE, INCOME }` — o padrão já existe na camada de banco, só precisa subir para o domínio.

---

### D5: `Category.iconKey: String` e `Budget.iconKey: String`

**Decisão:** Remover `CategoryLazyIcon` (tipo Compose) dos modelos de domínio. Substituir por `iconKey: String`. O `CategoryLazyIcon` é construído na camada de UI a partir da key.

**Rationale:** `categories:api` não pode depender de Compose. Domínio não deve conhecer tipos de UI.

---

### D6: Convention plugins em `build-logic/`

**Decisão:** Três convention plugins Kotlin DSL cobrem todos os módulos:
- `kmp-library.gradle.kts` — targets KMP + Android library (para `:core:*` e `:feature:X:api`)
- `kmp-compose.gradle.kts` — aplica `kmp-library` + Compose (intermediário, usado por `kmp-feature`)
- `kmp-feature.gradle.kts` — aplica `kmp-compose` + Koin + Arrow + Navigation (para `:feature:X:impl`)

`:core:database` aplica `kmp-library` e configura Room/KSP manualmente no próprio `build.gradle.kts` — um plugin dedicado adicionaria complexidade desnecessária para um único módulo.

Módulos `:feature:X:api` usam `kmp-library` diretamente — são módulos KMP puros sem Compose ou Koin. Não há `kmp-feature-api` dedicado: `kmp-library` já atende sem sobrecarga.

**Rationale:** Com ~25 módulos, repetir a configuração KMP (targets, source sets) em cada `build.gradle.kts` é impraticável. Convention plugins eliminam a repetição.

---

### D7: Features terminais — `:api` puro, entry points em `:ui`

**Decisão:**
- `support` tem apenas `:impl` (nenhum outro módulo o consome).
- `dashboard` tem `:api` apenas para tipos puros consumidos cross-feature (ex: `DashboardSection` se houver). `DashboardEntry` mora em `:feature:dashboard:ui` (D11/D14) — `:dashboard:api` continua sem Compose nem Navigation.
- `transactions:api` mantém models e contratos puros; `TransactionsEntry` mora em `:feature:transactions:ui` (D11/D14) — `:transactions:api` é puro Kotlin (`kmp-library`).
- `home` tem `:api` mínimo expondo `HomeRoute`, `HomeChrome*` (data) e o `NavigationDispatcher` (consumido por features que disparam navegação para rotas top-level — ver D13). `AppRoute` vive em `:app` (D13).

**Rationale:** Após D14, `:api` perde Compose/Navigation completamente — entry points (que são Composable) descem para `:ui`. Isso restaura `:api` ao papel de "contrato puro de domínio" e elimina o último motivo para módulos `:api` arrastarem dependências de UI.

---

### D8: Use cases cross-feature via interface em `:api`

**Decisão:** Use cases usados por outras features expõem interface em `:api`. O `:impl` implementa. Koin faz o binding.

**Use cases cross-feature identificados:**
- `transactions:api` → `IBuildTransactionUseCase`, `ICalculateBalanceUseCase`
- `creditCards:api` → `IGetOrCreateInvoiceForMonthUseCase`
- `accounts:api` → `IEnsureDefaultAccountUseCase`

**Rationale:** Mantém a regra "nenhum impl vê outro impl". A feature consumidora depende da interface, o Gradle verifica em compile time.

---

### D9: Analytics events por feature ficam em `:impl`

**Decisão:** As subclasses de `Event` específicas de cada feature (ex: `CreateTransaction`, `EnterDashboardEditMode`) ficam em `:feature:X:impl`. Apenas `Analytics`, `Crashlytics` e a base `Event` ficam em `:core:analytics`.

**Rationale:** Nenhuma outra feature precisa conhecer os eventos de outra. São detalhes de implementação.

---

### D10: Regra estrutural — `feature:X:api` não depende de `feature:Y:api`

**Regra:** Nenhum módulo `:api` pode ter outro módulo `:api` de feature como dependência — nem `implementation`, nem `api`.

**Por quê é uma regra, não uma diretriz:**
- **Dependência cíclica:** se `A:api` depende de `B:api` e `B:api` depende de `A:api` (direta ou indiretamente), o Gradle falha com "circular dependency" em tempo de configuração — o build não funciona. Com muitas features dependendo umas das outras via `:api`, ciclos surgem naturalmente à medida que o produto evolui, e detectar a causa torna-se difícil. Proibir `api → api` elimina a classe inteira de problemas.
- **Recompilação em cascata:** um `:api` que depende de outro `:api` força a recompilação de todos os módulos dependentes quando `Y:api` muda — colapsando o isolamento incremental que é a razão de ser da modularização.
- **Acoplamento de contrato:** se `transactions:api` depende de `accounts:api`, qualquer mudança na interface de `accounts` quebra o contrato de `transactions` em compile time — mesmo que `transactions` não tenha mudado nada.

**Consequência:** Modelos cross-feature carregam IDs (não objetos) e vivem cada um no `:api` da sua feature dona (ver D2 e D14). Hidratação para display acontece em `:feature:X:ui` via `XxUi` + `IXxUiMapper`. Interfaces de repositório e use cases permanecem em cada `:feature:X:api`. O `:impl` pode depender de outros `:api` quando precisar de contratos (ex: `transactions:impl` usa `IAccountRepository` de `accounts:api`).

**Tabela de dependências permitidas (após D14):**
| De \ Para           | `:core:*` | `:feature:X:api` | `:feature:X:ui` | `:feature:X:impl` |
|---------------------|-----------|------------------|-----------------|-------------------|
| `:core:*`           | ✅ acíclico | ❌             | ❌              | ❌                |
| `:feature:X:api`    | ✅         | ❌               | ❌              | ❌                |
| `:feature:X:ui`     | ✅         | ✅ (qualquer feature) | ❌         | ❌                |
| `:feature:X:impl`   | ✅         | ✅               | ✅ (qualquer feature) | ❌          |
| `:app`              | ✅         | ✅               | ✅              | ✅                |

**Regras adicionais introduzidas em D14:**
- `:ui ↮ :ui` proibido — mesma razão de `api ↮ api` (evita ciclos no grafo de UI cross-feature).
- `:ui` pode ler `:api` de qualquer feature → `OperationUi` em `transactions:ui` compõe `Account` direto de `accounts:api`.
- `:impl` pode ler `:ui` de qualquer feature → `dashboard:impl` renderiza `OperationCard` de `transactions:ui` direto, sem entry point para cards.

### D11: Entry points em `:ui` para acesso cross-impl a telas

**Problema:** A regra D10 proíbe `:impl → :impl`. Mas `home:impl` precisa registrar rotas que renderizam telas de `dashboard` e `transactions` no seu `NavHost` interno (bottom nav). Sem mecanismo de indireção, isso forçaria `home:impl → dashboard:impl` ou `home:impl → transactions:impl`.

**Decisão:** Cada feature cuja tela precisa ser renderizada por outro `:impl` expõe um **entry point** em seu `:feature:X:ui` (não em `:api` — D14 desce o entry para o tier de UI, libertando `:api` de Compose/Navigation):

```kotlin
// feature/<x>/ui
abstract class XxxEntry {
    abstract fun NavGraphBuilder.register(navController: NavController)
}

// feature/<x>/impl
class XxxEntryImpl : XxxEntry() {
    override fun NavGraphBuilder.register(navController: NavController) {
        composable<HomeRoute.Xxx>(...) { XxxScreen(...) }
    }
}

// Koin (dentro do impl module)
single<XxxEntry> { XxxEntryImpl() }
```

O consumidor (`home:impl` ou `:app`) injeta a `XxxEntry` via Koin e chama `register` dentro do seu próprio `NavGraphBuilder` — nunca conhece a `XxxScreen` nem o `XxxViewModel`.

**Onde aplicar (entry points para telas cross-impl):**
- `dashboard:ui` → `DashboardEntry` (consumido por `home:impl`)
- `transactions:ui` → `TransactionsEntry` (consumido por `home:impl`)

**Onde NÃO aplicar:**
- Telas chamadas só pelo `AppNavHost` no `:app`. `:app` pode depender de `:impl` (D10 permite), então acessa o composable da tela diretamente.
- Cards/components cross-impl. Após D14, `:impl` pode importar `:ui` de qualquer feature direto — `dashboard:impl` chama `OperationCard` de `transactions:ui` sem entry point.

**Rationale:** Centraliza a definição de rota + composable da feature dentro da própria feature, e o entry vive no tier que naturalmente já tem Compose/Navigation. O consumidor só conhece a interface de registro.

**Alternativa rejeitada:** Mover `AppNavHost` para `home:impl` e exigir entry point em todas as features. Aumentaria boilerplate sem benefício — `:app` é o único lugar onde "depender de tudo" é aceitável e natural.

### D12: Pacote reflete o módulo (convenção híbrida feature + camadas)

**Decisão:** O pacote raiz de cada módulo Kotlin reflete seu caminho Gradle. Subpacotes organizam por papel (camada). `api` e `impl` da mesma feature compartilham o pacote raiz, separando apenas por subpacote.

**Mapeamento:**

| Módulo Gradle | Pacote raiz | Subpacotes típicos |
|---------------|-------------|--------------------|
| `:core:utils` | `com.neoutils.finsight.core.utils` | `extension`, `util`, `util.di` |
| `:core:platform` | `com.neoutils.finsight.core.platform` | — |
| `:core:analytics` | `com.neoutils.finsight.core.analytics` | `analytics`, `crashlytics`, `event`, `di` |
| `:core:auth` | `com.neoutils.finsight.core.auth` | `service`, `di` |
| `:core:database` | `com.neoutils.finsight.core.database` | `entity`, `dao`, `di` |
| `:core:ui` | `com.neoutils.finsight.core.ui` | `theme`, `component`, `modal`, `extension`, `util`, `di` |
| `:feature:<x>:api` | `com.neoutils.finsight.feature.<x>` | `model`, `repository`, `usecase`, `error`, `exception`, `nav`, `form`, `extension` |
| `:feature:<x>:ui` | `com.neoutils.finsight.feature.<x>` | `model`, `mapper`, `component`, `entry`, `extension` |
| `:feature:<x>:impl` | `com.neoutils.finsight.feature.<x>` | `screen`, `modal`, `mapper`, `di`, `event`, `usecase`, `repository` |
| `:app` | `com.neoutils.finsight.app` | — |

**Por que api/ui/impl compartilham raiz:** D10/D14 já são garantidas pelo Gradle (api ↮ api, ui ↮ ui, impl ↮ impl). Duplicar `.api` / `.ui` / `.impl` no nome do pacote seria ruído sem ganho — a fronteira já existe no nível de módulo. Subpacotes diferentes (api: `model`/`repository`/`usecase`; ui: `model`/`mapper`/`component`/`entry`; impl: `screen`/`modal`/`mapper`) evitam colisão e tornam o papel de cada arquivo legível pelo path.

**Por que não `<feature>.api` / `<feature>.impl`:** Forçaria `Account` a viver em `feature.accounts.api.model.Account` e o `AccountRepository` (impl) em `feature.accounts.impl.repository.AccountRepository` — verboso e redundante com a estrutura de diretórios Gradle.

**`:app` (`namespace` Android):** O `applicationId` permanece `com.neoutils.finsight` (ID público da Play Store). Apenas o `namespace` do módulo Android e os pacotes Kotlin migram para `com.neoutils.finsight.app`. A referência `<application android:name=".AndroidApp">` resolve relativa ao namespace, então segue intacta.

**iOS:** Pacotes Kotlin não afetam nomes Obj-C do framework — esses derivam de file name + class name. Swift continua importando `ComposeApp` e usando `MainViewControllerKt.MainViewController()` sem mudança.

**Rationale:** O caminho do arquivo passa a ser previsível a partir do nome do módulo. `git grep "package com.neoutils.finsight.feature.accounts"` lista exclusivamente arquivos da feature `accounts`. Antes da convenção, o mesmo grep retornava arquivos espalhados em qualquer módulo que usasse `domain.repository`/`ui.modal`/etc.

### D13: `AppRoute` em `:app`; navegação cross-feature por eventos

**Problema:** Originalmente `AppRoute` (sealed class das rotas top-level do `AppNavHost`) vivia em `:feature:home:api` porque `:feature:home:impl` precisava conhecê-lo para fazer `navController.navigate(AppRoute.X)` a partir de `AppNavigationDispatcher` (que recebia o `NavHostController` por injeção). Como `:app` depende de `:feature:home:impl`, mover `AppRoute` para `:app` criaria ciclo via `home:impl → app`.

A consequência era ruim em duas dimensões:
1. **Ownership invertido:** `AppRoute` é um contrato do shell (`:app`), não da feature `home`. Vivia no lugar errado por restrição estrutural.
2. **Acoplamento ao framework de navegação:** `:feature:home:impl` (e qualquer impl que quisesse disparar navegação top-level) acabava precisando importar `androidx.navigation.NavHostController` para construir/usar o dispatcher.

**Decisão:** O `NavigationDispatcher` vira **canal de eventos** desacoplado do `NavHostController`:

```kotlin
// :feature:home:api
class NavigationDispatcher {
    private val _events = Channel<NavigationDestination>(Channel.BUFFERED)
    val events: Flow<NavigationDestination> = _events.receiveAsFlow()
    fun dispatch(destination: NavigationDestination) { _events.trySend(destination) }
}
```

`:app` cria o dispatcher, fornece via `LocalNavigationDispatcher`, e **consome** os eventos no próprio `AppNavHost` traduzindo `NavigationDestination → AppRoute`:

```kotlin
// :app
val navigationDispatcher = rememberNavigationDispatcher()
LaunchedEffect(navigationDispatcher, navController) {
    navigationDispatcher.events.collect { dest ->
        when (dest) {
            NavigationDestination.Categories -> navController.navigate(AppRoute.Categories)
            // ...
        }
    }
}
```

**Efeitos:**
- `AppRoute` migra para `:app` (`com.neoutils.finsight.app.route.AppRoute`) — consumido apenas por `AppNavHost`.
- `AppNavigationDispatcher` (impl que recebia `NavHostController`) deixa de existir.
- `:feature:home:impl` (e demais `:impl` consumidores) não conhecem mais `NavHostController` nem `AppRoute` — só emitem `NavigationDestination`.
- `:feature:home:api` perde a dependência `androidx.navigation.compose` (não usa mais nada do pacote).

**Por que `Channel.BUFFERED` + `receiveAsFlow()`** (e não `SharedFlow`): eventos de navegação devem ser consumidos exatamente uma vez. `SharedFlow` com replay/cache poderia re-disparar a navegação após uma recomposição/coleta tardia. `Channel` garante consumo único e drena na ordem.

**Por que isso respeita D10/D11:** D11 continua sendo o padrão para *renderizar* telas cross-impl (entry points). D13 é especificamente sobre *disparar* navegação para rotas top-level do shell — a inversão acontece no consumidor (`:app`), não no produtor (`:impl`).

**Alternativa rejeitada:** Interface `HomeNavigator` em `:feature:home:api` com um método por destino implementada em `:app`. Funcionaria mas adicionaria ~10 métodos de boilerplate sem ganho — `NavigationDestination` já é o sealed type estável que carrega o intent.

---

### D14: Tier `:feature:X:ui` para UI models e components cross-impl

**Problema:** A modularização inicial criou dois "god modules":
- **`:core:domain`** (§10.5/§20) — hospeda models cross-feature (`Account`, `Category`, `CreditCard`, `Invoice`, `Transaction`, `Operation`, `Recurring`, `Budget`) porque D10 proíbe `feature:X:api → feature:Y:api`. Os models são "ricos" (`Transaction.account: Account?`, `Operation.category: Category?`, `Invoice.creditCard: CreditCard`, `Budget.categories: List<Category>`), misturando domínio puro com conveniência de apresentação.
- **`:core:sharedui`** — hospeda Compose components renderizados por múltiplos `:impl` (`OperationCard`, `CreditCardCard`, `BudgetProgressCard`, `AccountSelector`, etc.) e seus UI models (`OperationUi`, `AccountUi`).

Ambos são consequência de D10 e partilham o mesmo problema: ownership diluído + recompilação em cascata.

**Decisão:** Introduzir tier `:feature:X:ui` por feature, com três regras:

1. **Domínio puro = só IDs.** `Transaction`, `Operation`, `Recurring`, `Budget`, `Invoice`, `TransactionForm` deixam de carregar objetos de outras features e passam a guardar apenas IDs (`accountId: Long?`, `categoryId: Long?`, `creditCardId: Long?`, `invoiceId: Long?`). Cada model volta para o `:api` da sua feature dona.

2. **Novo módulo `:feature:X:ui` por feature** (quando há derivação de display ou composição cross-feature) hospeda:
   - `XxUi` — UI model (POJO) que compõe o domínio puro com objetos resolvidos cross-feature e/ou campos derivados (datas calculadas, strings formatadas).
   - `IXxUiMapper` — interface de mapeamento `Domain → UI`. Implementação concreta mora em `:impl`, onde tem acesso aos repositórios. ViewModels consomem `IXxUiMapper` via Koin para montar listas/itens em batch antes de emitir `UiState`.
   - Compose components renderizados por outras features (`OperationCard`, `CreditCardCard`, `BudgetProgressCard`, etc.).
   - Entry points D11 (`DashboardEntry`, `TransactionsEntry`) — saem de `:api` (que volta a ser puro) e descem para `:ui`.

3. **Regras de dependência expandidas (D10):**

   | De \ Para | `:core:*` | `:feature:X:api` | `:feature:X:ui` | `:feature:X:impl` |
   |-----------|-----------|------------------|-----------------|-------------------|
   | `:core:*` | ✅ acíclico | ❌ | ❌ | ❌ |
   | `:api` | ✅ | ❌ | ❌ | ❌ |
   | `:ui` | ✅ | ✅ (qualquer feature) | ❌ | ❌ |
   | `:impl` | ✅ | ✅ | ✅ (qualquer feature) | ❌ |
   | `:app` | ✅ | ✅ | ✅ | ✅ |

   - `:api ↮ :api` proibido (D10 preservado)
   - `:ui ↮ :ui` proibido — mesma razão de D10 (evita ciclos no grafo de UI)
   - `:impl ↮ :impl` proibido (D10 preservado)
   - `:ui` pode importar `:api` de qualquer feature → `OperationUi` em `transactions:ui` compõe `Account`/`Category`/`CreditCard` direto. Para compor outros `XxUi`s, o `XxUi` rico re-implementa os campos primitivos ou aceita injeção (no caso de mappers).
   - `:impl` pode importar `:ui` de qualquer feature → `dashboard:impl` chama `OperationCard` de `transactions:ui` direto, sem entry point para cards.

**Critério para criar `XxUi`:** somente quando há (a) derivação de display (datas calculadas, strings formatadas) ou (b) composição cross-feature (ex: `OperationUi` agrega `Account`/`Category`/`CreditCard`). Tipos de domínio que já são display-friendly e não cruzam features (`Account`, `Category`, `CreditCard`) são consumidos direto pela UI sem intermediário.

**Mortes:**
- `:core:domain` — apagado (todos os models voltam para suas `:feature:X:api`).
- `:core:sharedui` — apagado (componentes migram para `:feature:X:ui` da feature dona).

**Ganhos:**
- Compose deixa o `:api` (`transactions:api` e `dashboard:api` voltam para `kmp-library`, sem Compose/Navigation).
- Entry points D11 descem para `:ui` (sai do `:api` o último uso de Compose lá).
- Ownership claro: `OperationCard` mora com `Operation`; `BudgetProgressCard` mora com `Budget`.
- Build incremental melhora: mudança em `transactions:ui` recompila só consumidores de UI, não consumidores de domínio.

**Custo:** ~9 novos módulos `:ui` (um por feature com componente cross-impl). Features sem componente cross-impl (`support`) não ganham `:ui`.

**Alternativa rejeitada (Opção 1):** Manter `:core:domain` e `:core:sharedui`. Funciona mas perpetua "god modules" que crescem indefinidamente — qualquer model novo cross-feature vira mais um item lá.

**Alternativa rejeitada (Opção 2):** Mover UI models e components para `:feature:X:impl` e expor cards via entry point. Forçaria todo card cross-impl a ter entry point + injection — boilerplate inviável para ~14 components.

---

## Risks / Trade-offs

**[Build complexity]** → 25+ módulos aumentam o tempo de configuração do Gradle e a complexidade do `settings.gradle.kts`. Mitigação: convention plugins reduzem a superfície de erro. O ganho em build incremental compensa após a migração.

**[KSP + KMP]** → KSP em KMP ainda tem comportamentos inconsistentes entre versões do Kotlin/KSP. Mitigação: Room centralizado em `:core:database` limita a exposição a um único módulo com KSP Room.

**[Migração incremental com código em dois lugares]** → Durante a migração, código existirá tanto no `:composeApp` original quanto nos novos módulos. Pode causar confusão. Mitigação: migrar feature por feature, remover do `:composeApp` imediatamente após mover.

**[iOS framework linking]** → Cada módulo KMP adicionado precisa ser linked no framework estático do iOS. O tempo de linking pode aumentar. Mitigação: é custo único por módulo, não incremental por mudança de código.

## Migration Plan

Ordem incremental — cada etapa deve compilar e rodar antes de prosseguir:

1. **Preparação** — Criar `build-logic/` com convention plugins; criar estrutura de diretórios; atualizar `settings.gradle.kts`
2. **Breaking changes de domínio** — `Recurring.Type`, `Category.iconKey`, `Budget.iconKey` (ainda dentro do `:composeApp`)
3. **`:core:utils`** — mover extensões puras; sem Compose
4. **`:core:platform`** — mover `Platform`, `PlatformContext`
5. **`:core:analytics`** e **`:core:auth`** — interfaces + DI platform modules
6. **`:core:ui`** — mover componentes, theme, ModalManager, NavigationDispatcher
7. **`:core:database`** — mover Room; o `:composeApp` passa a depender de `:core:database`
8. **Features nível 0** (sem dep de outras features): `accounts`, `categories`, `creditCards` — api depois impl
9. **Features nível 1**: `installments`, `recurring`, `budgets`, `transactions`
10. **Features terminais**: `report`, `support`, `dashboard`, `home`
11. **`:app`** — `:composeApp` vira `:app`; só navigation + startKoin + entry points

**Rollback:** Cada etapa é um commit atômico. Reverter é `git revert` do commit da etapa.

## Open Questions

*(nenhuma — todas as decisões foram tomadas durante a fase de exploração)*