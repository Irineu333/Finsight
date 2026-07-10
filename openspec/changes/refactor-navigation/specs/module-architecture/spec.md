## MODIFIED Requirements

### Requirement: Shell compartilhado em :app:shared
O `:app:shared` SHALL ser uma KMP library (sem plugin de application) contendo apenas: composable raiz (`App`) com o `Scaffold` da chrome do Home, NavHost raiz, a rota do subgrafo de abas (`HomeRoute`) e seu `NavigationItem`, e a agregação dos módulos Koin (`appModules`). Ele SHALL ser o único módulo do projeto autorizado a depender de módulos `feature:*:impl` e o único autorizado a enumerar as features.

#### Scenario: Nova feature adicionada
- **WHEN** uma nova feature é integrada ao app
- **THEN** o `:app:shared` muda em no máximo dois pontos (lista de módulos Koin e registro do grafo no NavHost) e o `:app:ios` em no máximo um (`export()` da api no framework)

#### Scenario: Plataforma consome o shell
- **WHEN** um entry point de plataforma inicializa o app
- **THEN** ele chama `startKoin` com a lista `appModules` exposta por `:app:shared` (Android adiciona `androidContext`) e renderiza `App()`

#### Scenario: Shell sem indireção de navegação
- **WHEN** o `:app:shared` é inspecionado
- **THEN** ele não contém dispatcher, tradutor ou mapa de destinos de navegação — apenas a composição dos grafos providos pelas features

### Requirement: Rotas de navegação declaradas por feature
Cada feature SHALL declarar suas próprias rotas `@Serializable`. A sealed class única `AppRoute` SHALL ser eliminada. As rotas *externamente navegáveis* de uma feature SHALL residir na sua `api`; as rotas alcançáveis apenas de dentro do próprio `impl` SHALL residir no `impl`. O shell conhece apenas a rota do subgrafo de abas (`HomeRoute`).

#### Scenario: Navegação cross-feature
- **WHEN** o `impl` de uma feature navega para uma tela de outra feature
- **THEN** ele referencia a rota declarada na `api` da feature destino, sem depender do `impl` dela

#### Scenario: Rota interna promovida indevidamente
- **WHEN** uma rota declarada em um módulo `api` não é referenciada por nenhum outro módulo
- **THEN** ela é movida para o `impl` da feature dona, seguindo a regra de que um tipo só reside na `api` se for consumido por outro módulo
