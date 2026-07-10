## Context

O Home está distribuído por três módulos, e nenhum deles é o dono:

```
:app:shared                          :core:ui
  HomeGraph        (a rota)            HomeChrome  (o contrato de chrome)
  NavigationItem   (as abas)           ↑
  App.AppScaffold  (bottom bar + FAB)  └── dashboard:impl publica ContentOnly no modo Editing
  AppNavHost       (navigation<HomeGraph> { dashboardGraph(); transactionsGraph() })
```

Duas dependências prendem o Home ao shell, e ambas atravessam a regra `impl ⊄ impl`:

1. **Nesting.** `navigation<HomeGraph> { dashboardGraph(); transactionsGraph() }` chama extensões declaradas em `dashboard:impl` e `transactions:impl`.
2. **FAB.** `modalManager.show(AddTransactionModal())` instancia um modal de `transactions:impl`.

Só o `:app:shared` pode fazer isso — é o único módulo autorizado a ver `impl`s. Um `feature:home:impl` não pode. Consequentemente, extrair o Home *é* transformar essas duas dependências em contratos.

O terceiro sintoma é o `HomeChrome` em `:core:ui`. Ele nasceu ali por falta de um dono: o `Scaffold` mora no shell, o `DashboardScreen` mora numa feature, e o único módulo que ambos enxergam é um core. Com um `feature:home:api`, o dono passa a existir.

Restrições que esta mudança não pode violar:

1. `api ⊄ api`, `impl ⊄ impl`, `api ⊄ impl` — verificadas por `verifyFeatureDependencyRules` no `build-logic`.
2. Nenhum módulo `:core:*` enumera as features.
3. `NavHost` único (decisão 5 de `2026-07-10-refactor-navigation`).
4. Zero mudança de comportamento observável pelo usuário.

## Goals / Non-Goals

**Goals:**
- Dar ao Home um módulo `api`/`impl` como qualquer outra feature, com rota, chrome, abas e ação primária próprias.
- Remover `HomeChrome` de `:core:ui`, corrigindo a inversão de camada.
- Reduzir o `:app:shared` a: `App()`, `AppNavHost()` (só chamadas a `<nome>Graph()`) e `appModules`.
- Manter o grafo de navegação **estruturalmente idêntico** ao atual.

**Non-Goals:**
- Reintroduzir um `NavHost` aninhado ou um `HomeScreen` como destino. Explicitamente rejeitado — ver decisão 3.
- Preservação de estado/scroll entre abas. Continua fora de escopo, como em `refactor-navigation`.
- Deep links, transições de destino, `logScreenView` centralizado, navegação adaptativa.
- Tornar as abas plugáveis (adicionar uma aba sem tocar em `home:impl`). Ver decisão 2.

## Decisions

### 1. `Entry.register(NavGraphBuilder)` resolve o nesting

Para montar `navigation<HomeGraph>` sem ver nenhum `impl`, `home:impl` precisa de um handle para o grafo de cada aba. O projeto já tem o mecanismo: o entry point.

```kotlin
// feature/dashboard/api
interface DashboardEntry {
    context(builder: NavGraphBuilder)
    fun register()
}

// feature/home/impl
fun NavGraphBuilder.homeGraph() {
    val koin = KoinPlatform.getKoin()
    navigation<HomeGraph>(startDestination = DashboardGraph) {
        koin.get<DashboardEntry>().register()   // NavGraphBuilder vem do contexto
        koin.get<TransactionsEntry>().register()
    }
}
```

O `impl` de cada aba mantém sua `NavGraphBuilder.<nome>Graph()` como hoje; o `<Nome>EntryImpl` apenas delega a ela. Nada muda no formato do grafo — os mesmos `navigation<DashboardGraph>` e `navigation<TransactionsGraph>` são registrados nos mesmos lugares.

**Alternativas consideradas:**

- **`homeGraph(tabs: NavGraphBuilder.() -> Unit)`**, com o shell passando `{ dashboardGraph(); transactionsGraph() }`. Preserva resolução em tempo de compilação e dispensa `dashboard:api`. Rejeitada: `home:impl` declararia quais abas existem (`NavigationItem`) sem ser quem as registra. A decisão "o app tem estas duas abas" ficaria partida entre dois módulos, e o shell voltaria a conhecer o Home.
- **Registro por DI de uma `List<HomeTab>`**, cada feature contribuindo com ícone, label e grafo. Rejeitada: esconde a decisão de produto "quais abas existem, em que ordem" atrás do container Koin. Torna a bottom bar impossível de ler sem rodar o app. É plugabilidade que ninguém pediu (Non-Goal).
- **Entry expondo `@Composable fun Content()` por aba**, dispensando grafo. Rejeitada: `TransactionsRoute(filterType, filterTarget)` tem argumentos, e o dashboard empilha essa rota com filtros. Sem destino de navegação, o parâmetro teria de virar estado, e o back stack da transação filtrada desapareceria.

O `NavGraphBuilder` entra como **context parameter**, não como parâmetro comum nem como receiver de extensão membro. As três formas compilam; a escolhida é a única em que o call site é simplesmente `entry.register()`:

| Forma | Call site | Chamável fora de um grafo? |
|---|---|---|
| `fun register(builder: NavGraphBuilder)` | `entry.register(this)` | sim — o `this` é só um argumento |
| `fun NavGraphBuilder.register()` (extensão membro) | `with(entry) { register() }` | não |
| `context(builder: NavGraphBuilder) fun register()` | `entry.register()` | não |

A extensão membro exige `with` porque o call site precisaria dos dois receivers ao mesmo tempo (o entry como dispatch, o builder como extensão). O context parameter dispensa isso: o receiver implícito do `navigation<>` satisfaz o contexto. O projeto já usa a mesma mecânica em `AnimatedVisibilityScopeProvider` (`:core:designsystem`), resolvido pelo receiver implícito do `composable<>`, e a feature de linguagem está habilitada para todos os módulos pelo `configureKotlinMultiplatform` do `build-logic`.

Ganho colateral: nas duas últimas formas o compilador impede que `register()` seja invocado fora da construção de um grafo. A primeira forma deixava passar.

**Custo aceito:** o entry point de uma feature passa a poder referenciar `NavGraphBuilder`, tipo de `androidx.navigation` e não de `:core:*`. É dependência de biblioteca, não de projeto — `transactions:api` já declara `api(libs.androidx.navigation.compose)`. A capability `feature-entry-points` é ajustada para admitir explicitamente esse quarto tipo de acesso cross-feature.

### 2. `feature:dashboard:api` passa a existir, e é a regra que o exige

A decisão 4 de `2026-07-10-refactor-navigation` rejeitou este módulo:

> Não se cria `feature:dashboard:api` — o shell enxerga `impl`, e um módulo inteiro para um `data object` que ninguém importa seria complexidade sem consumidor.

A premissa ("ninguém importa `DashboardRoute`") era verdadeira e deixa de ser. A bottom bar navega para `DashboardRoute`, e a bottom bar sai do shell. A regra que sustentava a rejeição — *um tipo só reside na `api` se outro módulo o consome* — é a mesma que agora obriga a criação. A conclusão inverte porque o fato inverteu, não porque a regra mudou.

`feature/dashboard/api` recebe `DashboardRoute`, `DashboardGraph` e `DashboardEntry`.

`DashboardGraph` sobe para a `api` porque `home:impl` precisa nomeá-lo: `navigation<HomeGraph>(startDestination = DashboardGraph)` exige que o `startDestination` seja um **filho direto** do grafo, e o filho direto é o subgrafo do dashboard, não a tela. Apontar para `DashboardRoute` falharia em runtime — `NavGraph.findNode` procura o `startDestination` apenas entre os filhos diretos. É a mesma regra da decisão anterior aplicada uma segunda vez: o tipo sobe para a `api` porque outro módulo o referencia.

A extensão `NavGraphBuilder.dashboardGraph()` permanece `internal` no `impl`, invocada apenas por `DashboardEntryImpl`.

### 3. O `Scaffold` muda de módulo, não de posição na árvore

O `AppScaffold` de `App.kt` vira `HomeChromeHost` em `home:impl`:

```kotlin
// feature/home/impl
@Composable
fun HomeChromeHost(content: @Composable (PaddingValues) -> Unit)

// :app:shared
HomeChromeHost { padding ->
    SharedTransitionProvider {
        AppNavHost(navController, Modifier.padding(padding))
    }
}
```

O `Scaffold` continua **por fora** do `NavHost`. Todos os destinos — inclusive `accounts`, `categories` — seguem renderizando dentro dele, com a chrome oculta por `HomeChromeConfig.ContentOnly`. `SharedTransitionLayout` continua envolvendo o `NavHost` inteiro, e nenhuma fronteira de `NavHost` é criada.

Isso preserva integralmente as decisões 5 e 6 do change anterior, e é o motivo de a mudança ser puramente estrutural.

**Alternativa rejeitada — `HomeScreen` com `NavHost` interno.** É a forma "mais feature" possível: um destino com Scaffold próprio hospedando as abas. Foi o desenho anterior, removido em `cd55639d`, e o `design.md` de `refactor-navigation` registra por quê:

> Um destino registrado no `NavHost` interno é inalcançável a partir do controller externo — nem o dispatcher nem um deep link conseguem abrir uma aba.

E, ao contrário do que se supunha, ele **não** preservava estado de aba: o `HomeScreen` já fazia `popUpTo(HomeRoute.Dashboard)`. Reintroduzi-lo custaria a alcançabilidade das abas em troca de nada.

`home:impl` lê o destino corrente de `LocalNavController` — `hasRoute<HomeGraph>()` na hierarquia — exatamente como o `AppScaffold` faz hoje. A chrome não precisa de um `NavHost` próprio para saber onde está.

### 4. `HomeChrome` vai para `home:api`, e a conjunção sobrevive

`HomeChromeConfig`, `HomeChromeController`, `LocalHomeChromeController`, `HomeChromeStateHolder` e `HomeChromeEffect` migram de `:core:ui` para `feature/home/api`. O `HomeChromeStateHolder` é a implementação do controller, mas vive na `api` porque `rememberHomeChromeStateHolder()` só é chamado por `home:impl` — e mover a interface sem o holder obrigaria a `api` a expor um tipo que ninguém constrói. Alternativa: manter só a interface + o `CompositionLocal` + o `Effect` na `api` e o holder no `impl`. **Escolhida esta segunda**: `home:api` fica com o contrato (`HomeChromeConfig`, `HomeChromeController`, `LocalHomeChromeController`, `HomeChromeEffect`) e `home:impl` com `HomeChromeStateHolder`/`rememberHomeChromeStateHolder`. A regra "a `api` MUST NOT conter implementações" é literal e não tem exceção a conceder aqui.

`dashboard:impl` passa a depender de `home:api` para publicar `HomeChromeConfig.ContentOnly` no modo `Editing`. Direção `impl → api`, permitida. Nenhum ciclo: `home:api` não depende de `dashboard:api`.

A visibilidade da chrome continua sendo a conjunção descrita na capability `navigation`:

```
visível = destino ∈ hierarquia(HomeGraph)  ∧  chromeConfig.isBottomBarVisible
```

### 5. Koin resolvido fora da composição, em `homeGraph()`

`NavHost(navController, startDestination, builder)` invoca o `builder: NavGraphBuilder.() -> Unit` dentro de um `remember`, fora de escopo `@Composable`. Logo `koinInject<DashboardEntry>()` não compila ali. `homeGraph()` resolve os entries via `KoinPlatform.getKoin().get<DashboardEntry>()`.

**Alternativas consideradas:**

- **`homeGraph(dashboard: DashboardEntry, transactions: TransactionsEntry)`**, com o shell fazendo `koinInject()` e passando. Rejeitada: o shell voltaria a nomear as abas do Home nos parâmetros — o mesmo vazamento que a decisão 1 evita.
- **Resolver em `HomeChromeHost`** (que é `@Composable`) e guardar num `CompositionLocal`. Rejeitada: acopla a construção do grafo à presença da chrome na árvore, uma ordem de inicialização implícita e frágil.

**Custo aceito:** troca-se uma garantia de compilação ("o grafo da aba existe") por uma de runtime. Um binding ausente vira crash na primeira composição do `NavHost`, não erro de build. Mitigado na decisão de risco correspondente.

### 6. `:app:shared` depois da mudança

```kotlin
// App.kt — só wiring de raiz
FinsightTheme { Surface { ProvidePlatformContext { FormattingLocalsHost {
    CompositionLocalProvider(LocalNavController provides navController) {
        ModalManagerHost {
            HomeChromeHost { padding ->
                SharedTransitionProvider { AppNavHost(navController, Modifier.padding(padding)) }
            }
        }
    }
} } } }

// AppNavHost.kt — só chamadas a <nome>Graph()
NavHost(navController, startDestination = HomeGraph) {
    homeGraph()          // aninha dashboard + transactions via entries
    categoriesGraph(); creditCardsGraph(); accountsGraph()
    budgetsGraph(); recurringGraph(); supportGraph(); reportGraph()
}
```

O `LaunchedEffect` de `logScreenView(selectedItem.screenName)` acompanha `NavigationItem` para `home:impl`, que passa a depender de `:core:analytics`. O `home` não expõe módulo Koin: não há o que registrar.

Sai a última exceção da capability `module-architecture`: o `:app:shared` deixa de ser "o único módulo autorizado a enumerar as features" e passa a ser apenas "o único autorizado a depender de `impl`s". Quem enumera as abas é `home:impl` — uma feature enumerando features, o que a regra `impl → qualquer api` sempre permitiu.

## Risks / Trade-offs

**Binding Koin ausente vira erro de runtime** (decisão 5) → os entries que `homeGraph()` resolve vêm de `dashboardModule` e `transactionsModule`, que `appModules` já agrega. O `home` não contribui módulo Koin nenhum: não tem ViewModel, use case nem repositório, e o `HomeChromeStateHolder` é um `remember` de composição. Mitigação: `AppModulesTest` em `:app:shared` monta um `koinApplication { modules(appModules) }` e resolve `DashboardEntry` e `TransactionsEntry`, falhando no `jvmTest` em vez de na primeira composição do `NavHost`.

**`build-logic` pode rejeitar `home:impl → dashboard:api`** → Não deve: é `impl → api`, o caso central da regra 4. Mas `home` é a primeira feature cujo `impl` depende da `api` de uma feature que *não tem* `api` hoje. Verificar `verifyFeatureDependencyRules` após criar `feature/dashboard/api` — o risco real é o `settings.gradle.kts` e o `libs.versions.toml`/`projects.feature.dashboard.api` do type-safe accessor, não a regra.

**`:app:ios` esquece o `export()`** → `feature:home:api` e `feature:dashboard:api` precisam entrar na lista de `export()` do framework `ComposeApp`. O erro só aparece no build do Xcode, não no `./gradlew check`. Mesmo risco registrado para `:core:navigation` no change anterior, e ele se materializou.

**Ordem de aplicação quebra a compilação** → Mover `HomeChrome` de `:core:ui` para `home:api` quebra `dashboard:impl` até que ele declare a dependência em `home:api`; e `home:api` não existe até que `home:impl` e `dashboard:api` existam para dar sentido a ele. Não são passos independentes. `tasks.md` fixa a ordem: criar os módulos vazios e registrá-los primeiro, mover código depois, remover do shell por último.

**A chrome depende de `LocalNavController` estar provido acima dela** → `HomeChromeHost` lê `currentBackStackEntryAsState()`. No `App()`, ele está dentro do `CompositionLocalProvider(LocalNavController)`. Se alguém aninhar `HomeChromeHost` fora desse provider, a composição falha com o erro explícito já especificado na capability `navigation`. Aceito.

**Dois módulos novos para um `data object` e uma interface** (`dashboard:api`) → É exatamente a complexidade que a decisão 4 anterior queria evitar. A diferença é que agora existe um consumidor. Se o Home algum dia voltar ao shell, `dashboard:api` deve ser removido junto.

## Migration Plan

Refactor interno. Sem migração de dados, sem mudança de API pública para as plataformas (`App()` e `appModules` mantêm assinatura), sem estado persistido afetado. A ordem que mantém o projeto compilando entre passos está em `tasks.md`. Rollback é `git revert`.

## Open Questions

Nenhuma bloqueante. Registradas para depois:

- `NavigationItem.screenName` continua sendo analytics acoplado ao enum de abas. Sai do shell junto com o enum, mas o `logScreenView` centralizado via `addOnDestinationChangedListener` segue fora de escopo.
- `HomeChromeConfig.ContentOnly` só existe porque o `Scaffold` fica por fora do `NavHost`. Se um dia o Home virar destino com `NavHost` próprio, o termo "destino ∈ hierarquia(HomeGraph)" da conjunção desaparece — mas o preço documentado na decisão 3 continua valendo.
