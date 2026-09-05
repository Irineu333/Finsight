---
area: designsystem
severity: low
type: ux
---

# O design system tem duas decisões sobre a aparência de um interruptor, e o backup usa a fraca

## Invariante

Existe **uma** decisão sobre como um interruptor deste app se parece, e todo interruptor a
consome.

Hoje é falso. Duas correções independentes para o mesmo defeito — o thumb desligado invisível —
nasceram em ramos diferentes, e o merge guardou as duas: `FinsightSwitchDefaults.colors()`, que
declara doze cores, e `finsightSwitchColors()`, que declara uma. As duas telas do backup consomem
a segunda; as outras sete chamadas do app consomem a primeira.

## Mecânica

`finsightSwitchColors()` sobrescreve **apenas** `uncheckedThumbColor`; as outras onze cores caem
no padrão do Material. `FinsightSwitchDefaults.colors()` sobrescreve seis do estado habilitado e
seis do desabilitado. As duas concordam exatamente onde o defeito original estava — o thumb
desligado, `onSurfaceVariant` nas duas — e divergem em todo o resto do estado marcado:

| | `finsightSwitchColors()` (herda o Material) | `FinsightSwitchDefaults.colors()` |
|---|---|---|
| `checkedThumbColor` | `onPrimary` | `primary` |
| `checkedTrackColor` | `primary` | `primary` a 35% |
| `checkedBorderColor` | `Color.Transparent` | `primary` |
| `uncheckedTrackColor` | `surfaceContainerHighest` | `surfaceVariant` |

A figura e o fundo estão trocados entre as duas: num, o thumb é claro sobre trilho de acento
sólido; no outro, o thumb é o acento sobre trilho de acento translúcido, com borda.

Os dois KDoc se declaram donos. `FinsightSwitch` diz ser *"the app's switch, and the one place its
colours are decided"*, e `SwitchColors.kt` abre com *"What a switch of this app looks like"*.

Nada impede a divergência: a regra que `0a3c1fa0a` estabeleceu não tem teste, e foi por isso que o
merge conseguiu desfazê-la em silêncio — o app compila, roda e passa a suíte com as duas.

## Evidência

- `FinsightSwitchDefaults.colors()` (`core/designsystem` — `ui/component/FinsightSwitch.kt`),
  criado por `0a3c1fa0a` — as doze cores
- `finsightSwitchColors()` (`core/designsystem` — `ui/theme/SwitchColors.kt`), criado por
  `a445a8942` — só `uncheckedThumbColor`
- `ColorScheme.defaultSwitchColors` (`androidx.compose.material3.SwitchDefaults`) — de onde vêm as
  onze que `finsightSwitchColors()` não declara
- `BackupScreen.kt` — `SettingsSwitch(switchColors = finsightSwitchColors())`, na `row(key =
  "vault")`; é o caso que `FinsightSwitchDefaults` existe para atender, por ser um componente de
  terceiro que só aceita as cores
- `VaultSettingsModal.kt` — `Switch(colors = finsightSwitchColors())`, construído à mão dentro da
  própria linha; é o único `androidx.compose.material3.Switch` do app fora do design system, e
  poderia ser `FinsightSwitch`
- As outras sete chamadas, todas em `FinsightSwitch`: `McpScreen`,
  `DashboardComponentOptionsModal`, `AccountFormModal` (duas) e `SectionsCard` (três)

## Consequência

O mesmo controle tem duas silhuetas no mesmo app, e a diferença aparece exatamente onde o
interruptor está **ligado** — que é o estado em que a maioria deles é vista. Ninguém compara as
duas telas lado a lado, que é o motivo de isso ter atravessado um merge sem ser notado.

O estado desabilitado também diverge, e ali a diferença é maior: `FinsightSwitchDefaults` dita as
seis cores, e o padrão do Material compõe `onSurface` sobre `surface` com opacidade. Nenhum dos
dois interruptores do backup é desabilitado hoje — as duas chamadas não passam `enabled` —, então
essa metade não é observável.

Nada do defeito original volta: as duas fontes concordam sobre o thumb desligado, que era o que
`a445a8942` e `0a3c1fa0a` foram escritos para consertar.

## Sugestão

Uma fonte só. `finsightSwitchColors()` delegando a `FinsightSwitchDefaults.colors()`, ou apagada,
com `BackupScreen` passando `FinsightSwitchDefaults.colors()` e `VaultSettingsModal` trocando o
`Switch` cru por `FinsightSwitch`.

E um teste estrutural, do feitio dos que já vivem em `:app:shared`: nenhum `Switch` do Material
construído fora de `core/designsystem`, e nenhuma segunda função de cores de interruptor. É o que
faltava para que a decisão de `0a3c1fa0a` não pudesse ser desfeita por um merge — a mesma
observação que o `README` do backlog já registra sobre o que fecha uma classe de bug.

Não vinculante — quem corrige decide.
