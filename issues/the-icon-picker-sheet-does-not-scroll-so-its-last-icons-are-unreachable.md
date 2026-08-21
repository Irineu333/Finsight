---
area: designsystem
severity: medium
type: ux
---

# O seletor de ícones não rola, e os últimos ícones ficam inalcançáveis

## Cenário

**DADO** o formulário de categoria, cujo seletor oferece
`FeatureIconCatalog.withGeneral(categories)` — 44 + 14 ícones, dos quais 8 colidem, ou seja
**50**
**QUANDO** o usuário abre "Escolher ícone" e tenta chegar aos últimos
**ENTÃO** a folha para na borda inferior da tela e não rola: as últimas linhas ficam cortadas
e não há gesto que as alcance
**DEVERIA** o conteúdo da folha rolar quando é mais alto que ela

## Mecânica

O `Column` de `IconPickerModal` não tem `verticalScroll`, e nada acima dele o dá: o wrapper é
o `ModalBottomSheet` do Material3, que passa o conteúdo direto e ainda usa
`skipPartiallyExpanded = true`, de modo que a folha abre em altura fixa. Com tiles de 64.dp e
`Arrangement.spacedBy(8.dp)` num `FlowRow`, 50 ícones ocupam mais de 900.dp.

## Evidência

- `core/designsystem/.../modal/iconPicker/IconPickerModal.kt` — `BottomSheetContent()`: o
  `Column` com `fillMaxWidth`/`padding` e nenhum `verticalScroll`; o `FlowRow` com
  `Modifier.size(64.dp)` por tile
- `core/designsystem/.../component/ModalManager.kt` — `ModalBottomSheet.Content()`: o
  `ModalBottomSheet` do Material3 com `skipPartiallyExpanded = true`, sem scroll intermediário
- `core/common/.../util/FeatureIconCatalog.kt` — `categories` (44) + `general` (14) e
  `withGeneral()`, que faz `distinctBy(AppIcon::key)`
- chamadores com o mesmo catálogo grande:
  `feature/categories/impl/.../categoryForm/CategoryFormModal.kt`,
  `feature/accounts/impl/.../accountForm/AccountFormModal.kt`,
  `feature/budgets/impl/.../budgetForm/BudgetFormModal.kt`,
  `feature/creditcards/impl/.../creditCardForm/CreditCardFormModal.kt`

## Consequência

Parte do catálogo simplesmente não existe para o usuário — e qual parte depende da altura da
tela, então o defeito é invisível em quem desenvolve num aparelho grande.

## Sugestão

`Modifier.verticalScroll(rememberScrollState())` no `Column` do seletor. Se o problema se
repetir noutras folhas altas, o lugar é o `ModalBottomSheet` base. Não vinculante.
