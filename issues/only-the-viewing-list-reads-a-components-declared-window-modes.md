---
area: dashboard
severity: medium
type: ux
---

# O modo de edição oferece um componente que a janela nunca desenha

## Cenário

**DADO** o app numa janela de 600dp ou mais — todo desktop, tablet, e celular em paisagem
**QUANDO** o usuário abre o modo de edição do dashboard
**ENTÃO** "Ações rápidas" está na lista de **ativos**, com pré-visualização e alça de arrastar,
e continua lá depois de confirmar — mas o dashboard nunca a desenha
**DEVERIA** não ser oferecida nessa janela, que é exatamente o que o próprio componente declara:
`modes = setOf(WindowMode.COMPACT)`

O cenário não exige que o usuário faça nada: `GetDashboardPreferencesUseCase.defaultPreferences()`
põe `QUICK_ACTIONS` no layout padrão, então esse é o estado de toda instalação nova de desktop.

## Mecânica

A regra tem um dono declarado — `DashboardComponentType.modes`, cujo KDoc diz "the window modes
this component is shown in. A component is **offered** in every mode unless it says otherwise" —
e dois consumidores. Só um lê.

`DashboardViewingContent()` lê: `state.items.filter { mode in it.modes }`, com o `mode` vindo de
`windowMode()`.

`DashboardViewModel.buildEditingState()` não lê. Monta `activeItems` a partir das preferências
salvas sem filtro nenhum, e `availableItems` a partir de `DashboardComponentType.entries`
filtrando apenas `isDeprecated` e as chaves já presentes. O `modes` não aparece em nenhum dos dois.

A busca por `modes` no projeto devolve quatro lugares: a declaração, o repasse
(`DashboardComponentVariant.modes`), o único consumidor (`DashboardViewingContent`) e o teste. O
modo de edição não está entre eles.

## Evidência

- `feature/dashboard/impl/.../dashboard/DashboardComponentType.kt` — `modes` e o seu KDoc;
  `QUICK_ACTIONS` é a única entrada que estreita a lista (`setOf(WindowMode.COMPACT)`)
- `feature/dashboard/impl/.../dashboard/DashboardViewingContent.kt` —
  `val items = state.items.filter { mode in it.modes }`
- `feature/dashboard/impl/.../dashboard/DashboardViewModel.kt` — `buildEditingState()`:
  `activeItems` sem filtro, `availableItems` filtrando só `isDeprecated` e `presentKeys`
- `feature/dashboard/impl/.../usecase/GetDashboardPreferencesUseCase.kt` —
  `defaultPreferences()` inclui `QUICK_ACTIONS`
- `core/designsystem/.../ui/util/WindowSize.kt` — `windowMode()`: `COMPACT` só abaixo de 600dp
- `feature/dashboard/impl/src/commonTest/.../DashboardComponentModesTest.kt` — o KDoc que enuncia
  a regra que o modo de edição quebra

## Consequência

A lista de "ativos" do editor não corresponde ao que a tela desenha, e nada explica a diferença:
o usuário arrasta, reordena e confirma um cartão que não aparece.

Dois desdobramentos a mais. **"Adicionar todos"** (`DashboardAction.AddAllComponents`) grava um
componente que aquela janela não mostra. E **o dashboard pode se declarar vazio com um componente
ativo**: `DashboardViewModel.viewingState` decide `Empty` vs `Viewing` sobre a lista **não
filtrada**, e o filtro por modo só acontece depois, em `DashboardViewingContent` — com apenas
"Ações rápidas" ativa numa janela larga, o estado é `Viewing` com um item, o filtro esvazia a
lista e a tela cai no `DashboardEmptyContent` ("adicione componentes") enquanto o editor lista um
ativo.

## Sugestão

Passar o `WindowMode` corrente para `buildEditingState()` — o `DashboardScreen` já compõe dentro
do alcance de `windowMode()` — e filtrar os dois lados por ele.

Um cuidado que a correção precisa ter: `confirmEdit()` grava **exatamente** `activeItems`, então
filtrar `activeItems` por modo apagaria a posição de "Ações rápidas" do dashboard de quem edita
no desktop e volta ao celular. Ou o filtro vale só para `availableItems` e o ativo fora de modo é
marcado visualmente, ou `confirmEdit()` precisa reconciliar o que filtrou com o que estava salvo.
Não vinculante.
