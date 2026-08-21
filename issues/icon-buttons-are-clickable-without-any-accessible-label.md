---
area: transversal
severity: high
type: a11y
---

# Botões de ícone são publicados como nós clicáveis sem rótulo nenhum

## Invariante

Todo nó clicável que não publica texto próprio publica um `contentDescription`.

Hoje é falso em **39 lugares**: `IconButton`s cujo corpo é só um `Icon` com
`contentDescription = null` e nenhum `Text`. O nó resultante é clicável e anônimo — um
leitor de tela anuncia "botão", e nada mais.

Observável: com TalkBack ativo, ao chegar no seletor de mês o usuário ouve *"botão"*,
*"agosto de 2026"*, *"botão"*. Não há como saber qual dos dois avança o mês. Deveria
anunciar "mês anterior" e "próximo mês".

## Mecânica

`contentDescription = null` declara o ícone **decorativo**, e é a escolha certa quando o
pai já publica texto — o caso dos ícones dentro de `BalanceCard` ou `SettingsMenuLink`.
É errada quando o `IconButton` é o único nó clicável e não tem texto algum dentro.

Não há um segundo dono compensando: o app inteiro tem uma única ocorrência de
`Modifier.semantics`, e ela é `MoneyText.asOneFigure()`, um merge de descendentes que não
define rótulo. O padrão correto existe e é usado uma vez só, no FAB.

## Evidência

Sem alternativa na tela — o usuário não tem outro caminho para a ação:

- `core/designsystem/.../component/MonthSelector.kt` — mês anterior e próximo mês
- `core/designsystem/.../component/MonthPickerDropdownMenu.kt` — `YearSelector()`, ano
  anterior e próximo
- `core/designsystem/.../component/InstallmentCounter.kt` — decremento e incremento
- `core/ui/.../component/InvoiceMonthNavigator.kt` — fatura anterior e próxima
- `core/ui/.../component/CategorySelector.kt`, `MultiCategorySelector.kt`,
  `CreditCardSelector.kt` — o botão de limpar seleção (`onEmpty`)

Com o contorno do gesto de voltar do sistema — 16 barras de topo, entre elas
`SettingsScreen.kt`, `CurrenciesScreen.kt`, `ExchangeRatesScreen.kt`, `SupportScreen.kt`,
`ReportConfigScreen.kt`, `CreditCardsScreen.kt`, `AccountsScreen.kt`, `BudgetsScreen.kt`,
`CategoriesScreen.kt`, `RecurringScreen.kt`.

Os demais são botões de calendário e de fechar dentro de folhas
(`AddTransactionModal.kt`, `EditTransactionModal.kt`, `PayInvoiceModal.kt`,
`AdvancePaymentModal.kt`, `EditAccountBalanceModal.kt`,
`TransferBetweenAccountsModal.kt`, `BudgetFormModal.kt`, `ConfirmRecurringModal.kt`,
`AddInstallmentModal.kt`, `EditInvoiceBalanceModal.kt`, `ExchangeRateFormModal.kt`).

O contraexemplo, único no app:

- `feature/shell/impl/.../home/ChromeHost.kt` — `AddTransactionFab()` com
  `contentDescription = stringResource(Res.string.add_transaction_fab_description)`
- `core/designsystem/.../component/MoneyText.kt` — `asOneFigure()`, a única `semantics`
  do projeto, e ela não rotula

## Consequência

Navegar mês, ajustar o número de parcelas e limpar um seletor são tarefas **impossíveis**
por leitor de tela — não incômodas, impossíveis. É o caso que a régua de criticidade do
projeto nomeia explicitamente.

## Sugestão

Começar pelos cinco componentes compartilhados sem contorno: uma correção cada, muitas
telas de uma vez. As chaves seguem o padrão de `add_transaction_fab_description` e entram
nos dois `strings.xml`. Não vinculante.
