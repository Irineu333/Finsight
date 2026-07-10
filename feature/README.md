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
                │   :core:model   :core:common   │
                │   (kernel compartilhado)       │
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

Os modelos de domínio (`Transaction`, `Account`, `Invoice`...) e os tipos de erro vivem em
`:core:model`, **não** nas apis das features. Motivo: os agregados são emaranhados
(`Transaction` embute `Account`, `CreditCard`, `Invoice` e `Category`), e qualquer api que
os mencionasse em assinaturas arrastaria os demais — violando a regra 1.

Chamamos isso de **dependência cruzada de domínio público**: modelos que precisariam
existir simultaneamente na api de várias features. O kernel compartilhado (`:core:model`)
mitiga o problema por ora. O split do domínio por feature é uma evolução futura, com duas
saídas possíveis (a decidir quando chegar a hora):

- **Referência por ID** — `Transaction.accountId: Long` em vez de `account: Account`;
- **Kernel mínimo permanente** — `:core:model` permanece apenas com os agregados emaranhados.

> A regra "api não depende de api" descarta deliberadamente a terceira opção
> (modelos nas apis referenciando-se entre si). Restrição intencional.

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

### Os três tipos de acesso cross-feature

| Acesso | Mecanismo |
|---|---|
| **Navegação** | Rota (`@Serializable`) externamente navegável vive na `api`; o consumidor obtém o `NavHostController` de `LocalNavController` (`:core:navigation`) e chama `navigate(Rota)`. O registro do `NavGraph` é feito pelo `impl` e agregado pelo `:app:shared` |
| **Modais** | Método no entry point retornando `Modal` (tipo de `:core:designsystem`) |
| **Composable embutido** | Método no entry point retornando conteúdo `@Composable` — caso raro; só se surgir necessidade real |

### Mecanismo de registro de navegação

Cada `impl` expõe uma **extension `NavGraphBuilder.<feature>Graph()`** que registra os
`composable<Rota>` da feature (as telas permanecem `internal` ao `impl`). Dentro de cada
`composable`, o `NavHostController` vem de `LocalNavController` — nenhum grafo recebe o controller
como parâmetro. O `:app:shared` — único módulo que enxerga os `impl` — agrega essas extensions no
`AppNavHost`:

```kotlin
// feature/support/impl — ui/navigation/SupportGraph.kt
fun NavGraphBuilder.supportGraph() {
    navigation<SupportRoute>(startDestination = SupportListRoute) {
        composable<SupportListRoute> {
            val navController = LocalNavController.current
            SupportScreen(...)
        }
        composable<SupportIssueRoute> { ... }
    }
}

// :app:shared — AppNavHost
NavHost(...) {
    navigation<HomeRoute>(startDestination = DashboardRoute) {
        dashboardGraph()
        transactionsGraph()
    }
    supportGraph()
}
```

Só `SupportRoute` vive na `api`, porque só ela é destino de outra feature; `SupportListRoute` e
`SupportIssueRoute` são alcançáveis apenas de dentro do próprio `impl` e residem nele, agrupadas sob
o subgrafo `navigation<SupportRoute>`. Uma feature que não é destino de ninguém — como o `dashboard`,
montado só pelo shell — não cria módulo `api` para hospedar sua rota.
*Alternativa descartada:* registrar grafos via Koin — indireção desnecessária, já que o shell
enxerga os `impl` por definição.

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
  - `HomeRoute` (o subgrafo das abas) e `NavigationItem` — o único lugar do projeto autorizado a
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
