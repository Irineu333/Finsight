## 1. Renomear `feature:home` → `feature:shell`

- [x] 1.1 Renomear o diretório `feature/home/` → `feature/shell/` (api + impl) e ajustar `settings.gradle.kts` (`:feature:home:api`/`:feature:home:impl` → `:feature:shell:*`)
- [x] 1.2 Renomear pacotes/imports de `...feature.home...` → `...feature.shell...` nos fontes movidos, mantendo os nomes de tipos (`HomeChromeConfig`, `HomeChromeHost`, `HomeChromeStateHolder` etc.) por ora
- [x] 1.3 Atualizar dependências Gradle que referenciam `projects.feature.home.*` (em `dashboard/impl`, `app/shared`) para `projects.feature.shell.*`
- [x] 1.4 Atualizar o Koin: renomear o módulo Koin da feature e sua inclusão em `appModules` (N/A — `feature:home` nunca teve módulo Koin em `appModules`; o binding do `NavCatalog` será criado na fase 3)
- [x] 1.5 Atualizar o `export()` do `:app:ios` de `:feature:home:api` para `:feature:shell:api`
- [x] 1.6 Atualizar `App()` (`:app:shared`) para invocar o composable de shell do novo pacote (import `ui.screen.home.HomeChromeHost` preservado — pacote do impl inalterado)
- [x] 1.7 `./gradlew check` verde — rename mecânico, sem mudança de comportamento (Android/JVM/common compilam e `AppModulesTest` passa; link de teste iOS esbarra em limite de heap do ambiente — não é regressão de código)

## 2. Achatar o `AppNavHost` (dissolver `HomeGraph`)

- [x] 2.1 No `AppNavHost`, substituir `homeGraph()` por chamadas diretas a `dashboardGraph()` e `transactionsGraph()` (importadas de seus `impl`), lado a lado com as demais features
- [x] 2.2 Trocar `startDestination` de `HomeGraph` para `DashboardGraph` (importado de `feature:dashboard:api`)
- [x] 2.3 Remover `HomeGraph` (route/marker) da `shell:api` e a extensão `homeGraph()` da `shell:impl`; remover o registro via `register()` das abas dentro do antigo subgrafo
- [x] 2.4 Remover o uso de `register()` de Dashboard/Transactions que só existia para o hosting do Home (`DashboardEntry` removido — só tinha `register()`; `TransactionsEntry` mantido pelos modais, sem `register()`; grafos tornados públicos)
- [x] 2.5 Ajustar `AppModulesTest` e demais testes que referenciam `HomeGraph`/`homeGraph()`
- [x] 2.6 `./gradlew check` + smoke test de navegação (abrir cada seção, voltar) — ainda com o `popUpTo(Dashboard)` atual (check verde em Android/JVM; smoke interativo consolidado na fase 8)

## 3. Catálogo único de destinos (`NavCatalog`)

- [x] 3.1 Criar em `feature:shell:api` o tipo `NavDestination(icon, labelRes, route, primaryTab, mobileOnly)` e a interface `NavCatalog` (referenciando apenas tipos de `:core:*`; `NavDestination : BottomNavigationItem` para alimentar as barras genéricas)
- [x] 3.2 Implementar a lista concreta em `feature:shell:impl` (`AppNavCatalog`: Dashboard/Transactions com `primaryTab = true`; Support com `mobileOnly = true`; demais features com ícones Material), construindo as rotas a partir de cada `feature:*:api`
- [x] 3.3 Adicionar dependência de todas as `feature:*:api` faltantes ao `feature/shell/impl/build.gradle.kts`
- [x] 3.4 Registrar o binding de `NavCatalog` no módulo Koin da shell (`shellModule`, agregado em `appModules`)
- [x] 3.5 `./gradlew check` verde — catálogo criado, ainda sem trocar consumidores

## 4. Migrar consumidores para o catálogo

- [x] 4.1 Projetar a bottom bar/rail a partir de `NavCatalog` na shell, removendo o enum `NavigationItem` (fase 4: ambas as barras projetam `primaryTab`; a fase 5 diferencia rail=`!mobileOnly`)
- [x] 4.2 Em `feature:dashboard:impl`, injetar `NavCatalog` e alimentar o grid de quick actions por `destinations.filter { !it.primaryTab }`, removendo o enum `QuickActionType` (chave de persistência estável via `NavDestination.actionKey`; builder/preview/prefs-seed/modal-de-config migrados)
- [x] 4.3 Confirmar que os pontos de entrada de Support seguem gated (`mobileOnly`/`isDesktop`) em todos os lugares (rail, grid, `TopAppBar` do Dashboard)
- [x] 4.4 `./gradlew check` verde — rail/bottom bar/grid derivados de uma fonte única (Android/JVM compilam; `AppModulesTest` inclui `NavCatalog`)

## 5. Primitiva de navegação unificada

- [x] 5.1 Implementar `navigateToSection(route)` com `popUpTo(<start do host>){ saveState = true }; launchSingleTop = true; restoreState = true` (extensão privada `NavController.navigateToSection` na shell)
- [x] 5.2 Implementar `navigateToDetail(route)` como `navigate` comum (push) (detalhe é `navController.navigate(route)` usado direto pelos callers — ex.: widgets do Dashboard; sem helper dedicado para não criar código morto na shell)
- [x] 5.3 Ligar o seletor: rail (desktop) usa `destinations.filter { !it.mobileOnly }`; bottom bar (mobile) usa `destinations.filter { it.primaryTab }`, ambos via `navigateToSection`
- [x] 5.4 Ajustar a seleção do item ativo por `hasRoute<T>()` sobre a `hierarchy`, cobrindo sub-destinos
- [x] 5.5 Substituir a visibilidade gated por `isHome`: rail persistente no desktop (oculto só por `ContentOnly`); bottom bar visível só em `primaryTab` + `ContentOnly`
- [x] 5.6 Garantir que abrir "transações filtradas" de um widget do Dashboard use `navigateToDetail` (empilha na seção Dashboard) (o `openTransactions` do Dashboard já usa `navController.navigate(...)` comum)
- [x] 5.7 `./gradlew check` verde (Android/JVM)

## 6. Botão voltar e `ContentOnly`

- [x] 6.1 Implementar a regra de voltar: `(isDesktop == false && destino não-primaryTab) || (profundidade da seção > 1)` (via `LocalCanNavigateBack` em `:core:navigation`, computado no `ChromeHost` a partir de `isWideWindow`/`isOnPrimaryTab`/profundidade da pilha da seção; ~11 telas de feature passam a exibir o voltar condicionalmente. Mobile inalterado; no desktop a raiz de seção não mostra voltar)
- [x] 6.2 Re-auditar os chamadores de `HomeChromeConfig.ContentOnly` e confirmar a intenção no desktop (único chamador: modo de edição do Dashboard → `ChromeConfig.ContentOnly`, um fluxo full-screen focado, correto em ambos os form factors; sem mudança)
- [x] 6.3 (Opcional) Encurtar nomes `HomeChromeConfig`/`HomeChromeEffect` → `ChromeConfig`/`ChromeEffect` se aprovado (aprovado: `HomeChrome*` → `Chrome*`; `HomeChromeHost` → `ChromeHost`; arquivos `Chrome.kt`/`ChromeStateHolder.kt`/`ChromeHost.kt`. **Diverge da spec `navigation` que nomeia `HomeChrome*` — atualizar no sync/archive**)

## 7. Ícones e refinamento visual (após decisão de UX)

- [x] 7.1 Definir com o `ux-ui-designer` o conjunto de ícones das 7 features do rail e aplicar no catálogo (decisão do usuário: manter os provisórios Material — Savings/Category/CreditCard/AccountBalanceWallet/Autorenew/Assessment/CalendarMonth — já aplicados em `AppNavCatalog`)
- [x] 7.2 Decidir ordenação/divisor no rail (abas primárias × features) e aplicar no `NavigationRailBar` (decisão: lista corrida, sem divisor; ordem do catálogo = abas primárias primeiro, depois features. `NavigationRailBar` inalterado)
- [x] 7.3 Confirmar que o Dashboard no desktop (sem grid) não fica com buraco visual (o componente `QuickActions` retorna `null` no desktop — seção inteira omitida, sem header órfão; o rail cobre todas as features)

## 8. Verificação end-to-end

- [~] 8.1 Testes de navegação: alternar seção preserva a pilha interna (desktop); voltar de destino empilhado retorna à seção de origem (**automatizado**: `AppModulesTest.navCatalogProjectionsAreConsistent` cobre as projeções rail/bottom/grid da fonte única + unicidade de chave. **Pendente (manual/device)**: comportamento e2e de save/restore de pilha por seção — o repo não tem harness de Compose UI test)
- [x] 8.2 Validar predictive back no Android e o comportamento de voltar no mobile (inalterado) — **validado pelo usuário no Android** (funcionou perfeitamente); iOS assumido por paridade (mesmo código `commonMain`)
- [x] 8.3 Rodar o Desktop (`./gradlew :app:desktop:run`) e validar rail persistente + navegação interna por seção — **validado pelo usuário no desktop**; correções decorrentes registradas na seção 9
- [~] 8.4 `./gradlew allTests` e `./gradlew check` verdes (**verde**: `testDebugUnitTest` + `jvmTest` de todos os módulos, e compile Android/JVM/iOS. **Não executável aqui**: `check`/`allTests` completos incluem link de binários de teste iOS que estoura o heap de 3 GiB do ambiente — limitação de infra, não regressão de código)

## 9. Correções pós-validação no desktop

Refinamentos e correções levantados ao rodar o app no desktop (após o commit inicial).

- [x] 9.1 **Rail — layout/estética**: itens roláveis (`verticalScroll`) para caber os 9 destinos em janelas baixas; header/FAB renderizado no próprio `Column` do `NavigationRailBar` (não no slot `header` do Material, que injetava folga assimétrica); espaçamento vertical de `12.dp` entre itens; padding horizontal de `8.dp` para não colar nas bordas.
- [x] 9.2 **Rótulos curtos no rail**: novas strings `nav_credit_cards` ("Cartões") e `nav_installments` ("Parcelas") — o catálogo usa nomes curtos sem tocar nos títulos de seção do Dashboard (`dashboard_credit_cards`).
- [x] 9.3 **Seleção do rail em sub-destinos**: seleção em dois tiers — rota exata na `hierarchy` (mantém itens que dividem graph, ex.: Cartões × Parcelas) e, quando o sub-destino não tem rota no catálogo (ex.: Faturas), fallback pelo dono do *start destination* da graph da seção. Corrige "Faturas marcava Dashboard".
- [x] 9.4 **Botão voltar por tela (sem estado global)**: removidos `LocalCanNavigateBack`/`rememberCanNavigateBack` e todo o cálculo no `ChromeHost`; cada tela decide sozinha via `isWideWindow()` (helper em `:core:designsystem`) — telas host escondem voltar no desktop, sub-features sempre mostram. Elimina o flicker do ícone (o valor global mudava no instante da navegação e vazava para a tela que estava saindo). Parcelamentos é a exceção: item do rail (host), sem voltar no desktop.
- [x] 9.5 **Navegação robusta (remoção de `saveState`/`restoreState`)**: a semântica de multiple-back-stacks era incompatível com as rotas parametrizadas onipresentes (`AccountsRoute(id)`, `TransactionsRoute(filtro)`, `CreditCardsRoute(id)` — `restoreState` ignora argumentos) e, misturada com `navigate()` comum espalhado em modais compartilhados, deixava o Dashboard inalcançável. Voltou-se à semântica de troca comprovada (`popUpTo(dashboard){inclusive=false}; launchSingleTop`), que normaliza a pilha para `[dashboard, host]` a cada seleção do rail. **Divergência da Decisão 1 (a registrar no sync/archive): não há preservação de pilha por seção — não havia necessidade real e não funcionava com rotas parametrizadas.**
- [x] 9.6 **Remoção da abstração `navigateToSection`**: como o rail passou a ser o único consumidor, a primitiva pública (`:core:navigation`) foi removida e o idioma (`popUpTo(start){inclusive=false}; launchSingleTop`) foi inlined no seletor do shell — some o conceito de "section" do código.
