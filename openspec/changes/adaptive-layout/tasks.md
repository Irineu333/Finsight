## 1. Dependência

- [ ] 1.1 Adicionar `org.jetbrains.compose.material3.adaptive:adaptive` ao version catalog (`gradle/libs.versions.toml`), com a versão compatível do Compose Multiplatform em uso
- [ ] 1.2 Declarar a dependência nos módulos que a consomem (`:core:designsystem` para o rail e/ou `feature:home:impl` para a medição de largura)

## 2. Componente de rail no design system

- [ ] 2.1 Criar `NavigationRailBar` em `:core:designsystem` (`ui/component/`), irmão de `BottomNavigationBar`, consumindo a interface `BottomNavigationItem` e o esquema de cores `Primary1`
- [ ] 2.2 Expor um slot `header` no `NavigationRailBar` para o `HomeChromeHost` posicionar o FAB
- [ ] 2.3 Garantir que `:core:designsystem` continua sem nomear features (sem `NavigationItem`) — o rail recebe os itens genéricos por parâmetro

## 3. Chrome adaptativa no Home

- [ ] 3.1 Em `HomeChromeHost`, medir a largura com `currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)`
- [ ] 3.2 Ramificar o layout: janela larga → `Row { NavigationRailBar(header = FAB) ; conteúdo }`; janela estreita → `Scaffold(bottomBar, FAB central)` (comportamento atual)
- [ ] 3.3 Reutilizar `NavigationItem.entries`, `selectedItem` e o `onItemSelected` (mesmo `navigate` com `popUpTo(DashboardRoute)` + `launchSingleTop`) nos dois arranjos
- [ ] 3.4 Aplicar a visibilidade da chrome (`HomeChromeConfig` via `updateTransition`/`AnimatedVisibility`) ao rail e ao FAB no modo largo, com transição adequada ao eixo horizontal
- [ ] 3.5 Verificar que a troca entre rail e bottom bar ao cruzar o breakpoint preserva o `NavController` e a pilha

## 4. Ocultar features mobile-only no desktop

- [ ] 4.1 Em `DashboardScreen`, gate o `IconButton` de Support no `TopAppBar` com `isDesktop` (ocultar quando `true`)
- [ ] 4.2 Confirmar que a quick action de Support já está oculta no desktop (`DashboardComponentsBuilder`) e que nenhum outro ponto de entrada de Support permanece no desktop

## 5. Verificação

- [ ] 5.1 Confirmar que `:app:shared` (`App`, `AppNavHost`), rotas, grafos, `NavigationItem` e Koin permanecem intocados
- [ ] 5.2 Rodar o app no desktop e validar: rail à esquerda com FAB no header, Support ausente, e bottom bar ao estreitar a janela abaixo de 600dp
- [ ] 5.3 Rodar o app no mobile e validar que o comportamento (bottom bar, FAB central, Support presente) não regrediu
- [ ] 5.4 `./gradlew check`
