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

### D2: Cada feature é seu próprio domínio — com exceção dos modelos compartilhados em `:core:domain`

**Decisão:** Modelos de domínio, interfaces de repositório e interfaces de use cases ficam no `:api` da feature dona. Modelos referenciados por múltiplas features (que causariam dependência `api → api`) ficam em `:core:domain`.

**Regra fundamental — api não depende de api:** `feature:X:api` **jamais** depende de `feature:Y:api`. Dois motivos críticos: (1) dependências cíclicas entre `:api` são detectadas pelo Gradle apenas em runtime de configuração, bloqueiam o build inteiro e são difíceis de rastrear à medida que a base de código cresce; (2) qualquer mudança em `Y:api` força recompilação de `X:api` e de todos os seus dependentes, colapsando o isolamento incremental que justifica a modularização. Ver D10 para detalhes e tabela de dependências permitidas.

**Modelos em `:core:domain`:** `Account`, `Category`, `CreditCard`, `Invoice`, `Transaction`, `Operation`, `OperationInstallment`, `OperationRecurring`, `OperationPerspective`, `TransactionForm`, `Recurring`, `RecurringOccurrence` — modelos cujo domínio é referenciado por mais de uma feature. Mover para `:core:domain` elimina dependências cruzadas como `transactions:api → accounts:api / categories:api / creditCards:api` (resolvido em §10.5) e `budgets:api / home:api / dashboard:api / installments:api → transactions:api / recurring:api` (resolvido em §20).

**O que NÃO vai para `:core:domain`:** erros, exceções, interfaces de repositório, use cases — esses permanecem em cada `:feature:X:api`. `:core:domain` contém apenas modelos de dados puros cujo domínio é genuinamente compartilhado.

**Caminho de melhoria (task futura):** Substituir objetos completos (`Account?`, `Category?`, etc.) por IDs nas relações de `Transaction`/`Operation`. Isso eliminará a necessidade de `:core:domain` e desacoplará completamente os tipos de domínio por feature.

**Alternativa rejeitada:** `:core:domain` centralizado com todos os modelos. Se torna um "deus módulo" sem boundary real.

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

### D7: Features terminais — `:api` apenas quando há cross-impl

**Decisão:**
- `support` tem apenas `:impl` (nenhum outro módulo o consome).
- `dashboard` tem `:api` mínimo expondo `DashboardEntry` (consumido por `home:impl`) — ver D11.
- `home` tem `:api` mínimo expondo `HomeRoute`, `HomeChrome*` e o `NavigationDispatcher` (consumido por features que disparam navegação para rotas top-level — ver D13). `AppRoute` vive em `:app` (D13).

**Rationale:** Manter `:api` vazio é desperdício, mas onde existe consumo cross-module (D11) o `:api` é a única forma de respeitar D10 sem expor implementação.

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

**Consequência:** Modelos compartilhados entre features vivem em `:core:domain` (ver D2). Interfaces de repositório e use cases permanecem em cada `:feature:X:api`. O `:impl` pode depender de outros `:api` quando precisar de contratos (ex: `transactions:impl` usa `IAccountRepository` de `accounts:api`).

**Tabela de dependências permitidas:**
| De \ Para           | `:core:*` | `:feature:X:api` | `:feature:X:impl` |
|---------------------|-----------|------------------|-------------------|
| `:core:*`           | ✅ (acíclico) | ❌           | ❌                |
| `:feature:X:api`    | ✅         | ❌               | ❌                |
| `:feature:X:impl`   | ✅         | ✅               | ❌                |
| `:app`              | ✅         | ✅               | ✅                |

### D11: Entry points em `:api` para acesso cross-impl a telas

**Problema:** A regra D10 proíbe `:impl → :impl`. Mas `home:impl` precisa registrar rotas que renderizam telas de `dashboard` e `transactions` no seu `NavHost` interno (bottom nav). Sem mecanismo de indireção, isso forçaria `home:impl → dashboard:impl` ou `home:impl → transactions:impl`.

**Decisão:** Cada feature cuja tela precisa ser renderizada por outro `:impl` expõe um **entry point** em seu `:api`:

```kotlin
// feature/<x>/api
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

O consumidor (`home:impl`) injeta a `XxxEntry` via Koin e chama `register` dentro do seu próprio `NavGraphBuilder` — nunca conhece a `XxxScreen` nem o `XxxViewModel`.

**Onde aplicar:**
- `dashboard:api` → `DashboardEntry` (consumido por `home:impl`)
- `transactions:api` → `TransactionsEntry` (consumido por `home:impl`)

**Onde NÃO aplicar:**
- Telas chamadas só pelo `AppNavHost` no `:app`. `:app` pode depender de `:impl` (D10 permite), então acessa o composable da tela diretamente.

**Rationale:** Centraliza a definição de rota + composable da feature dentro da própria feature. O consumidor só conhece a interface de registro.

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
| `:core:domain` | `com.neoutils.finsight.core.domain` | `model` |
| `:core:database` | `com.neoutils.finsight.core.database` | `entity`, `dao`, `di` |
| `:core:ui` | `com.neoutils.finsight.core.ui` | `theme`, `component`, `modal`, `extension`, `util`, `di` |
| `:core:sharedui` | `com.neoutils.finsight.core.sharedui` | `component`, `model` |
| `:feature:<x>:api` | `com.neoutils.finsight.feature.<x>` | `model`, `repository`, `usecase`, `error`, `exception`, `nav`, `entry` |
| `:feature:<x>:impl` | `com.neoutils.finsight.feature.<x>` | `screen`, `modal`, `mapper`, `di`, `event`, `usecase`, `repository` |
| `:app` | `com.neoutils.finsight.app` | — |

**Por que api/impl compartilham raiz:** D10 já é garantida pelo Gradle (api ↮ api). Duplicar `.api` / `.impl` no nome do pacote seria ruído sem ganho — a fronteira já existe no nível de módulo. Subpacotes diferentes (api: `model`/`repository`/`usecase`; impl: `screen`/`modal`/`mapper`) evitam colisão e tornam o papel de cada arquivo legível pelo path.

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