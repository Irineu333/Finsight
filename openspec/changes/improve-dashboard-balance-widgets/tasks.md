# Tasks

## 1. Rótulos honestos (`core/resources`)

- [x] 1.1 Trocar o valor de `dashboard_total_balance` para `Saldo em Contas` / `Balance in Accounts` (chave preservada)
- [x] 1.2 Trocar o valor de `component_total_balance` para `Saldo em Contas` / `Balance in Accounts` — é o título na lista de edição
- [x] 1.3 Adicionar `component_overall_balance_stats` (`Balanço Geral` / `Overall Balance`) em todos os idiomas
- [x] 1.4 Adicionar as três strings de cabeçalho, que **não existem hoje** — nenhum dos widgets de fluxo tem título: `dashboard_overall_balance` (`Balanço Geral`), `dashboard_balance` (`Balanço em Contas`), `dashboard_credit_card_balance` (`Balanço do Cartão`)
- [x] 1.5 Verificar que nenhum outro consumidor lê `dashboard_total_balance` / `component_total_balance` esperando o texto antigo
- [x] 1.6 Trocar o valor de `component_balance_stats` para `Balanço em Contas` / `Accounts Balance` — o título na lista de edição também não qualificava o perímetro

## 2. O widget neutro (`feature/dashboard/impl`)

- [x] 2.1 Acrescentar `OVERALL_BALANCE_STATS` a `DashboardComponentType`, com `TOP_SPACING=false`, `HIDE_WHEN_EMPTY=false` e `SHOW_HEADER=false` (ver 5.3)
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

## 5. Ajuste pedido depois da implementação

- [x] 5.1 O dashboard inicial (`GetDashboardPreferencesUseCase.defaultPreferences`) abre com
      `OVERALL_BALANCE_STATS` **no lugar de** `CONCRETE_BALANCE_STATS` — o perímetro de contas
      continua a um toque no modo de edição. Empilhar os dois por padrão poria lado a lado dois
      cards com a mesma receita, que é o que D5 descreve. Contraria a decisão registrada no
      proposal de o widget novo "nascer ausente"; ela vale para dashboards **já montados**, que
      seguem intocados — o default só rege quem instala agora.
- [x] 5.2 Teste: um dashboard novo traz o perímetro neutro, não o de contas
- [x] 5.4 Rótulos do widget de contas: `Entradas na conta` / `Saídas na conta`
      (`BalanceCardConfig.AccountIncome` / `AccountExpense`, derivados de `Income` / `Expense` só
      no título). D5 havia descartado diferenciar pelos rótulos porque "não resolve a receita, que
      segue idêntica nos dois" — verdade quanto ao número, mas agora o card diz de quem ele é
- [x] 5.3 `SHOW_HEADER=false` também no `defaultConfig` do widget neutro, revertendo o `true` de D5:
      aquele `true` existia para separar dois widgets de fluxo empilhados, e o default de 5.1 deixou de
      empilhá-los. Os três nascem iguais e sem cabeçalho; quem juntar mais de um liga o título no modal

## 6. Fora do escopo original, encontrado ao verificar no emulador

O APK não buildava desde `7b56d8d1d` (*Refactor(ledger): move the ledger into `:core:ledger`*),
por colisão de dex: o `LedgerDatabase` de `:core:ledger` e o `AppDatabase` de `:core:database`
declaram as mesmas quatro entidades, então o Room gerava `AccountDao_Impl`, `EntryDao_Impl`,
`TransactionDao_Impl` e `DimensionDao_Impl` nos dois módulos, com a mesma FQN. Nada disso vem
desta change — ela só foi o que fez o APK ser buildado de novo.

- [x] 6.1 Mover `LedgerDatabase` de `commonMain` para `jvmTest` em `:core:ledger`, sem `@ConstructedBy`
      nem o `expect object` (target único) e `internal` — as classes geradas deixam de ir para o artefato
- [x] 6.2 Registrar `kspJvmTest` no `build.gradle.kts` do ledger; o convention plugin `finsight.room.library`
      só cobre os source sets `Main`
- [x] 6.3 Verificar que a garantia do D9 sobrevive: um `@Query` do ledger nomeando `invoices` ainda
      falha o build, agora em `kspTestKotlinJvm` (`[ksp] … no such table: invoices`)

> Alternativa registrada, não adotada: um módulo de verificação próprio (`core:ledger-schemacheck`),
> que devolveria a checagem ao `assemble` em vez de ao `test`, ao custo de um módulo inteiro para
> uma classe.
