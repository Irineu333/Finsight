## 1. Renomear `feature:home` → `feature:shell`

- [ ] 1.1 Renomear o diretório `feature/home/` → `feature/shell/` (api + impl) e ajustar `settings.gradle.kts` (`:feature:home:api`/`:feature:home:impl` → `:feature:shell:*`)
- [ ] 1.2 Renomear pacotes/imports de `...feature.home...` → `...feature.shell...` nos fontes movidos, mantendo os nomes de tipos (`HomeChromeConfig`, `HomeChromeHost`, `HomeChromeStateHolder` etc.) por ora
- [ ] 1.3 Atualizar dependências Gradle que referenciam `projects.feature.home.*` (em `dashboard/impl`, `app/shared`) para `projects.feature.shell.*`
- [ ] 1.4 Atualizar o Koin: renomear o módulo Koin da feature e sua inclusão em `appModules`
- [ ] 1.5 Atualizar o `export()` do `:app:ios` de `:feature:home:api` para `:feature:shell:api`
- [ ] 1.6 Atualizar `App()` (`:app:shared`) para invocar o composable de shell do novo pacote
- [ ] 1.7 `./gradlew check` verde — rename mecânico, sem mudança de comportamento

## 2. Achatar o `AppNavHost` (dissolver `HomeGraph`)

- [ ] 2.1 No `AppNavHost`, substituir `homeGraph()` por chamadas diretas a `dashboardGraph()` e `transactionsGraph()` (importadas de seus `impl`), lado a lado com as demais features
- [ ] 2.2 Trocar `startDestination` de `HomeGraph` para `DashboardGraph` (importado de `feature:dashboard:api`)
- [ ] 2.3 Remover `HomeGraph` (route/marker) da `shell:api` e a extensão `homeGraph()` da `shell:impl`; remover o registro via `register()` das abas dentro do antigo subgrafo
- [ ] 2.4 Remover o uso de `register()` de Dashboard/Transactions que só existia para o hosting do Home (manter os `Entry` das features se ainda consumidos por modal/ação)
- [ ] 2.5 Ajustar `AppModulesTest` e demais testes que referenciam `HomeGraph`/`homeGraph()`
- [ ] 2.6 `./gradlew check` + smoke test de navegação (abrir cada seção, voltar) — ainda com o `popUpTo(Dashboard)` atual

## 3. Catálogo único de destinos (`NavCatalog`)

- [ ] 3.1 Criar em `feature:shell:api` o tipo `NavDestination(icon, labelRes, route, primaryTab, mobileOnly)` e a interface `NavCatalog` (referenciando apenas tipos de `:core:*`)
- [ ] 3.2 Implementar a lista concreta em `feature:shell:impl` (Dashboard/Transactions com `primaryTab = true`; Support com `mobileOnly = true`; demais features com seus ícones), construindo as rotas a partir de cada `feature:*:api`
- [ ] 3.3 Adicionar dependência de todas as `feature:*:api` faltantes ao `feature/shell/impl/build.gradle.kts`
- [ ] 3.4 Registrar o binding de `NavCatalog` no módulo Koin da shell
- [ ] 3.5 `./gradlew check` verde — catálogo criado, ainda sem trocar consumidores

## 4. Migrar consumidores para o catálogo

- [ ] 4.1 Projetar a bottom bar/rail a partir de `NavCatalog` na shell, removendo o enum `NavigationItem`
- [ ] 4.2 Em `feature:dashboard:impl`, injetar `NavCatalog` e alimentar o grid de quick actions por `destinations.filter { !it.primaryTab }`, removendo o enum `QuickActionType`
- [ ] 4.3 Confirmar que os pontos de entrada de Support seguem gated (`mobileOnly`/`isDesktop`) em todos os lugares (rail, grid, `TopAppBar` do Dashboard)
- [ ] 4.4 `./gradlew check` verde — rail/bottom bar/grid derivados de uma fonte única

## 5. Primitiva de navegação unificada

- [ ] 5.1 Implementar `navigateToSection(route)` com `popUpTo(<start do host>){ saveState = true }; launchSingleTop = true; restoreState = true`
- [ ] 5.2 Implementar `navigateToDetail(route)` como `navigate` comum (push)
- [ ] 5.3 Ligar o seletor: rail (desktop) usa `destinations.filter { !it.mobileOnly }`; bottom bar (mobile) usa `destinations.filter { it.primaryTab }`, ambos via `navigateToSection`
- [ ] 5.4 Ajustar a seleção do item ativo por `hasRoute<T>()` sobre a `hierarchy`, cobrindo sub-destinos
- [ ] 5.5 Substituir a visibilidade gated por `isHome`: rail persistente no desktop (oculto só por `ContentOnly`); bottom bar visível só em `primaryTab` + `ContentOnly`
- [ ] 5.6 Garantir que abrir "transações filtradas" de um widget do Dashboard use `navigateToDetail` (empilha na seção Dashboard)
- [ ] 5.7 `./gradlew check` verde

## 6. Botão voltar e `ContentOnly`

- [ ] 6.1 Implementar a regra de voltar: `(isDesktop == false && destino não-primaryTab) || (profundidade da seção > 1)`
- [ ] 6.2 Re-auditar os chamadores de `HomeChromeConfig.ContentOnly` e confirmar a intenção no desktop (rail persistente vs. tela full-screen)
- [ ] 6.3 (Opcional) Encurtar nomes `HomeChromeConfig`/`HomeChromeEffect` → `ChromeConfig`/`ChromeEffect` se aprovado

## 7. Ícones e refinamento visual (após decisão de UX)

- [ ] 7.1 Definir com o `ux-ui-designer` o conjunto de ícones das 7 features do rail e aplicar no catálogo
- [ ] 7.2 Decidir ordenação/divisor no rail (abas primárias × features) e aplicar no `NavigationRailBar`
- [ ] 7.3 Confirmar que o Dashboard no desktop (sem grid) não fica com buraco visual

## 8. Verificação end-to-end

- [ ] 8.1 Testes de navegação: alternar seção preserva a pilha interna (desktop); voltar de destino empilhado retorna à seção de origem
- [ ] 8.2 Validar predictive back no Android e o comportamento de voltar no mobile (inalterado)
- [ ] 8.3 Rodar o Desktop (`./gradlew :app:desktop:run`) e validar rail persistente + navegação interna por seção
- [ ] 8.4 `./gradlew allTests` e `./gradlew check` verdes
