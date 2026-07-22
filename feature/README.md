# Arquitetura de Features: api/impl

> Este diretório abriga os módulos de feature do projeto, organizados no padrão **api/impl**.
> Este documento define a estrutura, as regras de dependência e o padrão de entry point.
> Toda nova feature deve seguir estas regras — elas são impostas pelos convention plugins do `build-logic`.

---

## Estrutura

Cada feature é um par de módulos Gradle:

```
feature/
└── <nome>/
    ├── api/    ← contratos públicos da feature
    └── impl/   ← implementação completa da feature
```

| Módulo | Contém | Não contém |
|---|---|---|
| **api** | Rotas de navegação **externamente navegáveis** (data classes), interfaces de repositório, interfaces de use cases públicos, entry point de UI (`<Nome>Entry`) | Qualquer implementação |
| **impl** | Telas, ViewModels, modais, use cases (públicos e privados), implementações de repositório, mappers, rotas de destinos internos, módulo Koin da feature | Tipos consumidos por outras features |

**Critério de triagem:** só entra na `api` o que **outro módulo consome**. Tudo o mais é detalhe de implementação e vive no `impl`. Na dúvida, comece no `impl` — promover para a `api` depois é barato; o inverso quebra consumidores.

---

## Regras de dependência

1. **api não depende de api**
2. **impl não depende de impl**
3. **api não depende de impl**
4. **A direção natural é: impl depende de api** (de qualquer feature) **e de `:core:*`**

As regras produzem uma **topologia estrela**: as apis só enxergam o core, e os impls
cruzam livremente para qualquer api. Ciclos entre features tornam-se *impossíveis por
construção* — não existe caminho de volta.

```
                ┌────────────────────────────────┐
                │   :core:ledger  ◄── :core:model│
                │   (razão)          (fachadas)  │
                └──────────────▲─────────────────┘
          ┌──────────┬────────┴──┬───────────┐
    ┌─────┴────┐ ┌───┴────┐ ┌────┴───┐ ┌─────┴──┐
    │trans:api │ │acct:api│ │card:api│ │ rec:api│   ← nenhuma seta entre elas
    └─────▲────┘ └───▲────┘ └────▲───┘ └─────▲──┘
          │          │           │           │
     ═════╪══════════╪═══════════╪═══════════╪═════
          │  (impls cruzam livremente para qualquer api)
    ┌─────┴────┐ ┌───┴─────┐ ┌───┴────────────┐
    │trans:impl│ │card:impl│ │ dashboard:impl │ ...
    └──────────┘ └─────────┘ └────────────────┘
```

Exemplo real: `transactions:impl → creditcards:api` e `creditcards:impl → transactions:api`
coexistem sem ciclo, porque as apis não se enxergam.

### Dependências permitidas por tipo de módulo

| De \ Para | `:core:*` | `feature:*:api` | `feature:*:impl` |
|---|---|---|---|
| **feature:\*:api** | ✅ | ❌ | ❌ |
| **feature:\*:impl** | ✅ | ✅ (qualquer) | ❌ |
| **:app:shared** (shell) | ✅ | ✅ | ✅ (é o agregador) |

O `:app:shared` é o único módulo que enxerga os `impl` — é ele quem faz o wiring do Koin
(`appModules`) e registra os grafos de navegação. O framework iOS vive em `:app:ios`.

---

## Domínio compartilhado

Os modelos de domínio e os tipos de erro vivem no core, **não** nas apis das features —
e o core está partido em dois, pela linha que importa:

| Módulo | Contém | Enxerga |
|---|---|---|
| **`:core:ledger`** | O razão: `Account`, `Entry`, `Transaction`, `AccountType`, as entities/DAOs dessas tabelas, `IEntryRepository`/`ITransactionRepository` e a fronteira de escrita | Room, datetime, serialization, Koin — e **nenhum outro módulo do projeto** |
| **`:core:model`** | As fachadas: `Category`, `CreditCard`, `Invoice`, `Installment`, `Recurring`, `Budget`, os formulários e os erros delas | `:core:ledger` |

A seta corre nesse sentido e só nesse: uma fachada projeta sobre o razão (uma recorrência
nomeia uma conta), e o razão **não consegue** nomear uma fachada — não é convenção, é o
compilador. `:core:ledger` declara um `LedgerDatabase` interno com as suas quatro tabelas,
então um `JOIN invoices` escrito num `@Query` do razão falha a compilação com
`no such table: invoices`.

Isto encerra o que este documento chamava de **dependência cruzada de domínio público**:
`Transaction` embutia `Account`, `CreditCard`, `Invoice` e `Category`, e as duas saídas
previstas — referência por identidade e kernel mínimo — acabaram sendo a mesma. Uma
transação carrega hoje identidades (`liabilityAccountId`, `nominalDimensionId`, os
escalares de parcelamento e recorrência); resolver o que elas abrem é da feature dona.

**Como uma feature fala com o razão.** Ela declara `:core:ledger` e lê por
`IEntryRepository`. Se precisar *vetar* ou *reagir a* uma escrita, implementa uma das duas
portas que o razão declara — `DimensionWriteGuard` (recusar) e `TransactionRemovalHook`
(corrigir-se depois de uma remoção) — e registra no seu módulo Koin. O razão conhece
dimensões, nunca o que elas representam.

> A regra "api não depende de api" descarta deliberadamente a opção de modelos nas apis
> referenciando-se entre si. Restrição intencional.

---

## Padrão de entry point

Todo acesso a recursos de UI de outra feature passa por **entry point** — nunca por
import direto de composable, modal ou ViewModel de outro `impl` (as regras de dependência
já impedem isso; o entry point é o caminho sancionado).

Cada `api` expõe uma interface única agrupando a superfície pública de UI da feature:

```kotlin
// feature/creditcards/api
interface CreditCardsEntry {
    fun payInvoiceModal(invoiceId: Long): Modal
    fun creditCardFormModal(creditCardId: Long? = null): Modal
}
```

O `impl` implementa e registra no módulo Koin da feature:

```kotlin
// feature/creditcards/impl
internal class CreditCardsEntryImpl(...) : CreditCardsEntry { ... }

val creditCardsModule = module {
    single<CreditCardsEntry> { CreditCardsEntryImpl(...) }
    // viewModels, use cases, repositórios...
}
```

O consumidor injeta a interface e usa via `ModalManager`:

```kotlin
// feature/dashboard/impl — enxerga apenas creditcards:api
val entry = koinInject<CreditCardsEntry>()
modalManager.show(entry.payInvoiceModal(invoice.id))
```

### Os quatro tipos de acesso cross-feature

| Acesso | Mecanismo |
|---|---|
| **Navegação** | Rota (`@Serializable`) externamente navegável vive na `api`; o consumidor obtém o `NavHostController` de `LocalNavController` (`:core:navigation`) e chama `navigate(Rota)`. O registro do `NavGraph` é feito pelo `impl` e agregado pelo `:app:shared` |
| **Modais** | Método no entry point retornando `Modal` (tipo de `:core:designsystem`) |
| **Composable embutido** | Método no entry point retornando conteúdo `@Composable` — caso raro; só se surgir necessidade real |
| **Registro de subgrafo** | `context(builder: NavGraphBuilder) fun register()` no entry point, quando uma feature **hospeda** os destinos de outra |

O quarto caso existe por causa do `home`, que aninha os grafos de `dashboard` e `transactions`
dentro do seu `navigation<HomeGraph>`. Como `impl ⊄ impl`, ele não pode chamar
`dashboardGraph()` diretamente: pede o registro ao `DashboardEntry`. As extensions
`NavGraphBuilder.<feature>Graph()` das features hospedadas ficam `internal`, invocadas apenas
pelo seu próprio `<Nome>EntryImpl`.

O `NavGraphBuilder` é um **context parameter**, não um parâmetro comum: o receiver implícito do
`navigation<>` o satisfaz, então o call site é só `entry.register()`, e o compilador impede que
`register()` seja chamado fora da construção de um grafo. Mesmo mecanismo do
`AnimatedVisibilityScopeProvider` em `:core:designsystem`.

O lambda do `NavGraphBuilder` não é `@Composable`, então quem monta o grafo resolve os entry
points por `KoinPlatform.getKoin()`, não por `koinInject()`.

### Mecanismo de registro de navegação

Cada `impl` expõe uma **extension `NavGraphBuilder.<feature>Graph()`** que agrupa os
`composable<Rota>` da feature em um `navigation<<Feature>Graph>` (as telas permanecem `internal` ao
`impl`). Dentro de cada `composable`, o `NavHostController` vem de `LocalNavController` — nenhum
grafo recebe o controller como parâmetro. O `:app:shared` — único módulo que enxerga os `impl` —
agrega essas extensions no `AppNavHost`:

```kotlin
// feature/support/impl — ui/navigation/SupportGraph.kt
fun NavGraphBuilder.supportGraph() {
    navigation<SupportGraph>(startDestination = SupportListRoute) {
        composable<SupportListRoute> {
            val navController = LocalNavController.current
            SupportScreen(...)
        }
        composable<SupportIssueRoute> { ... }
    }
}

// feature/budgets/impl — mesmo com uma tela só, o subgrafo existe
fun NavGraphBuilder.budgetsGraph() {
    navigation<BudgetsGraph>(startDestination = BudgetsRoute) {
        composable<BudgetsRoute> { ... }
    }
}

// feature/home/impl — hospeda os grafos das abas sem enxergar nenhum impl
fun NavGraphBuilder.homeGraph() {
    val koin = KoinPlatform.getKoin()
    navigation<HomeGraph>(startDestination = DashboardGraph) {
        koin.get<DashboardEntry>().register()   // NavGraphBuilder vem do contexto
        koin.get<TransactionsEntry>().register()
    }
}

// :app:shared — AppNavHost: só chamadas a <nome>Graph()
NavHost(...) {
    homeGraph()
    supportGraph()
    budgetsGraph()
}
```

**Toda feature declara seu subgrafo**, mesmo as de tela única: o nó de grafo é o alvo estável de
`popUpTo` e o lugar onde transições e deep links vão morar. **O sufixo diz o que a rota é:**
`<Nome>Graph` nomeia o nó de um subgrafo, `<Nome>Route` nomeia uma tela.

Toda rota implementa um marcador de `:core:navigation` — `NavGraphRoute` para os nós de grafo,
`NavRoute` para as telas (`NavGraphRoute : NavRoute`). Os marcadores não declaram nada: existem para
que "quem são as rotas do app" e "quem navega" sejam uma busca por implementações, e para que campos
que guardam rotas (`NavigationItem.route`, `QuickActionType.route`) não sejam tipados como `Any`.

**Onde o `<Nome>Graph` mora segue o mesmo critério de triagem de qualquer tipo:** na `api` só se
outro módulo navegar até ele. `SupportGraph` e `ReportGraph` estão na `api` porque o dashboard abre
essas features pela entrada. `BudgetsGraph` e `AccountsGraph` ficam no `impl`, ao lado da extension,
porque quem navega até `budgets` e `accounts` mira a tela (`BudgetsRoute`, `AccountsRoute(id)`) e
nunca o grafo. `DashboardGraph` está na `api` porque o `home:impl` o nomeia como `startDestination`
do subgrafo de abas — o `startDestination` precisa ser um filho direto do grafo, e o filho direto é
o subgrafo, não a tela. Uma feature que não é destino de ninguém não cria módulo `api` para hospedar
rota alguma; o `dashboard` deixou de ser esse caso quando o `home` saiu do shell.
*Registro via Koin, só quando necessário:* o `:app:shared` enxerga os `impl` e chama as extensions
diretamente. Uma **feature** que hospeda o grafo de outra não pode, e aí passa pelo `register()` do
entry point. É a exceção que o `home` obriga, não a regra.

> **Entry point é opcional.** Uma feature só declara `<Nome>Entry` quando **outra** feature consome
> UI dela (modal/composable). O piloto `support` não expõe modal a terceiros (seu modal é interno),
> então **não** declara entry point — apenas rotas na `api`.

### Entry point vs. `:core:ui`

Nem todo componente visual compartilhado precisa de entry point:

- **Tem wiring próprio** (ViewModel, use cases)? → pertence a uma feature; acesso **via entry point**.
- **Apenas renderiza modelos do core** (ex.: `AccountSelector`, `OperationCard`)? → componente
  compartilhado; vive em **`:core:ui`** e é importado diretamente.

As assinaturas dos entry points só referenciam tipos do core (`:core:model`,
`Modal` de `:core:designsystem`) — o que preserva a topologia estrela.

---

## Notas de plataforma

- Todos os módulos de feature declaram os targets KMP (Android, iOS, Desktop), mas a
  regra é código `commonMain` puro. Source sets de plataforma no `impl` são exceção
  justificada (ex.: `report:impl`, com serviços nativos de print/share).
- No framework iOS (configurado no `:app:ios`), apenas `:core:*` e `feature:*:api`
  são exportados (`export()`); os `impl` são linkados via `:app:shared`, mas invisíveis ao Swift.

---

## O papel do shell (`:app:shared`) e os módulos `app/`

O app é dividido em quatro módulos de responsabilidade única sob `app/`:

- **`:app:shared`** (KMP library, convenção `finsight.app.shared`) — o **único módulo agregador**,
  reduzido a shell puro:
  - `App` (com o `Scaffold` da chrome do Home: bottom bar + FAB), `AppNavHost` (agrega os
    `xxxGraph()` de cada `impl`);
  - `HomeGraph` (o subgrafo das abas) e `NavigationItem` — o único lugar do projeto autorizado a
    enumerar as features;
  - `appModules`: a lista de agregação dos módulos Koin dos cores injetáveis + de todas as features.
- **`:app:android`** (`com.android.application`, não-KMP) — `MainActivity`, `AndroidApp` (`startKoin`),
  Manifest, mipmaps, signing/keystore, `google-services.json`, crashlytics, `versionCode`/`versionName`.
- **`:app:desktop`** (`kotlin("jvm")`) — `main.kt` + `compose.desktop` com `nativeDistributions`.
- **`:app:ios`** (KMP só-iOS) — `MainViewController` + framework `ComposeApp` com export seletivo
  de `:core:*` + `feature:*:api`.

Os singletons cross-cutting **não** vivem mais num `shellModule`: cada binding Koin fica no core dono
(`databaseModule` em `:core:database`, `commonModule` — `Settings`/`CurrencyFormatter`/`DebounceManager` —
em `:core:common`, `designsystemModule` — `ModalManager` — em `:core:designsystem`); `:app:shared`
apenas os agrega em `appModules`.

Adicionar uma feature nova mexe no app em no máximo três pontos: a lista `appModules` (`:app:shared`),
a chamada do `xxxGraph()` no `AppNavHost` (`:app:shared`) e o `export()` da api no framework (`:app:ios`).

## Padrões que emergiram na extração (além do desenho inicial)

Como as dependências entre features são **bidirecionais**, não existe ordem acíclica de
features completas — daí três padrões consolidados:

1. **Apis primeiro.** Interfaces de repositório e rotas só dependem de `:core:*`, logo são
   acíclicas e foram extraídas antes dos `impl`, desbloqueando qualquer ordem de extração.
2. **Interface para use case público com dependência interna.** Um use case consumido por
   outra feature mas que depende de use cases internos vira **interface na `api` + `Impl` no
   `impl`** (ex.: `GetOrCreateInvoiceForMonthUseCase`, `BuildTransactionUseCase`,
   `AddInstallmentUseCase`, `InvoiceUiMapper`). Use cases públicos **sem** dependência interna
   podem ser classes concretas na `api`.
3. **Mappers e models de UI compartilhados vão para o core.** Um mapper entity↔model usado por
   dois `impl` (ex.: `RecurringMapper`) vive em `:core:database`; um model de UI compartilhado
   (ex.: `InvoiceOverview`) vive em `:core:ui` — evitando arestas `impl → impl`.
