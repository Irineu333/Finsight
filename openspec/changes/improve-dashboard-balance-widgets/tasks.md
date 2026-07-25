# Tasks

## 1. Rótulos honestos (`core/resources`)

- [x] 1.1 Trocar o valor de `dashboard_total_balance` para `Saldo em Contas` / `Balance in Accounts` (chave preservada)
- [x] 1.2 Trocar o valor de `component_total_balance` para `Saldo em Contas` / `Balance in Accounts` — é o título na lista de edição
- [x] 1.3 Adicionar `component_overall_balance_stats` (`Balanço Geral` / `Overall Balance`) em todos os idiomas
- [x] 1.4 Adicionar as três strings de cabeçalho, que **não existem hoje** — nenhum dos widgets de fluxo tem título: `dashboard_overall_balance` (`Balanço Geral`), `dashboard_balance` (`Balanço em Contas`), `dashboard_credit_card_balance` (`Balanço do Cartão`)
- [x] 1.5 Verificar que nenhum outro consumidor lê `dashboard_total_balance` / `component_total_balance` esperando o texto antigo

## 2. O widget neutro (`feature/dashboard/impl`)

- [x] 2.1 Acrescentar `OVERALL_BALANCE_STATS` a `DashboardComponentType`, com `TOP_SPACING=false`, `HIDE_WHEN_EMPTY=false` e `SHOW_HEADER=true` (D5)
- [x] 2.2 Acrescentar `DashboardComponent.OverallBalanceStats(income, expense)` e o ramo em `toViewingVariant`
- [x] 2.3 Acrescentar `DashboardComponentVariant.OverallBalanceStats` (`Viewing` + `Preview`), com `title` apontando para `component_overall_balance_stats`
- [x] 2.4 Construir o componente em `DashboardComponentsBuilder`: `income = assetMonthFlows.income`, `expense = assetMonthFlows.expense + liabilityMonthFlows.expense` (D2), honrando `hideWhenEmpty`
- [x] 2.5 Renderizar em `DashboardComponentContent` o par `BalanceCardConfig.Income` / `BalanceCardConfig.Expense`, com o cabeçalho condicional a `showHeader()` — mesma forma da seção de contas
- [x] 2.6 Registrar o preview em `DashboardPreviewFactory` para o widget aparecer na lista de disponíveis do modo de edição

## 3. Desambiguação entre os três (`feature/dashboard/impl`)

- [x] 3.1 Acrescentar `SHOW_HEADER=false` ao `defaultConfig` de `CONCRETE_BALANCE_STATS` e de `CREDIT_CARD_BALANCE_STATS` — aparência de hoje preservada (D5)
- [x] 3.2 Passar as duas seções existentes a renderizar cabeçalho condicional (`dashboard_balance` / `dashboard_credit_card_balance`), hoje ausente
- [x] 3.3 Confirmar que o `DashboardComponentOptionsModal` já expõe o toggle de cabeçalho para os três, por ser config genérica

## 4. Testes (`feature/dashboard/impl`)

- [x] 4.1 Despesa do perímetro neutro = despesa de `ASSET` + despesa de `LIABILITY`, com uma compra de cartão contada uma vez só
- [x] 4.2 Pagamento de fatura não entra na despesa do perímetro neutro por nenhuma das duas parcelas
- [x] 4.3 Receita do perímetro neutro idêntica à do perímetro de contas
- [x] 4.4 Com os três widgets presentes, `despesa(geral) == despesa(contas) + gasto(cartão)`
- [x] 4.5 `hideWhenEmpty` do widget neutro: mês sem movimento nenhum não some por padrão
- [x] 4.6 O widget novo aparece entre os disponíveis, e sobrevive a salvar/recarregar preferências
- [x] 4.7 Um dashboard salvo antes desta change carrega idêntico, sem o widget neutro e sem cabeçalho nos dois existentes
