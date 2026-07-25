## Why

O dashboard **dá nome neutro a leituras que só enxergam contas**.

São quatro widgets de saldo/balanço hoje (`DashboardComponentType.kt:7-33`):

| widget | título pt-BR | leitura | perímetro real |
|---|---|---|---|
| `TOTAL_BALANCE` | **Saldo Total** | `balanceUpTo(mês)` | só `ASSET` |
| `CONCRETE_BALANCE_STATS` | **Balanço** | `assetMonthFlows(mês)` | só `ASSET` |
| `CREDIT_CARD_BALANCE_STATS` | Balanço do Cartão | `liabilityMonthFlows(mês)` | só `LIABILITY` |
| `PENDING_BALANCE_STATS` | Balanço Pendente | recorrências pendentes | nenhum (projeção) |

Os dois primeiros nomes não qualificam nada, e é isso que o usuário relata como *"um parece ser neutro"*. Não é ambiguidade de rótulo: é afirmação falsa. Quem tem R$ 5.000 em conta e R$ 4.000 de fatura aberta lê **"Saldo Total: R$ 5.000"** — o dashboard responde *"quanto eu tenho"* com um número que ignora metade do plano de contas.

E o perímetro que resolveria isso **não existe em lugar nenhum do dashboard**. Os widgets cobrem contas-fluxo, contas-estoque e cartões-fluxo; nenhum cobre contas + cartões. O usuário que quer saber *"quanto saiu do meu dinheiro esse mês"* precisa somar dois cards de cabeça.

O agravante é que a resposta já está pronta duas camadas abaixo. O razão lê qualquer natureza, e a tela de transações **já modelou os três perímetros** (`transaction-scope`) — `TransactionScope { ALL, ACCOUNTS, CARDS }` e `balanceOverview(scope, month)`. Só que ambos são `internal` em `feature/transactions/impl`, inalcançáveis pelo dashboard pela regra `impl ⊄ impl`. O dashboard não tem como consumir o que já foi resolvido, então segue sem a leitura.

## What Changes

- **Novo widget `OVERALL_BALANCE_STATS` — "Balanço Geral"**: o par de cards `Receitas` / `Despesas` sobre o perímetro `ASSET` + `LIABILITY`.

  ```
  Receitas  = assetMonthFlows(mês).income
  Despesas  = assetMonthFlows(mês).expense + liabilityMonthFlows(mês).expense
  ```

  Os dois conjuntos de despesa são **disjuntos** — uma compra no cartão não tem perna `ASSET` —, então agregá-los não dupla-conta. E `assetMonthFlows` já exclui pagamento de fatura, então ele não entra na soma nem por dentro.

- **O perímetro é o tipo do widget, não uma configuração dele.** A identidade de um widget é a `key` do tipo (`DashboardViewModel.kt:337-342`), logo existe no máximo uma instância de cada — um widget único com escopo configurável daria **um** perímetro por vez, e contas e cartões deixariam de poder conviver na tela. Decisão explícita do usuário: os três coexistem.

- **`TOTAL_BALANCE` passa a se chamar "Saldo em Contas"**, no rótulo do card e no título da lista de edição. A leitura não muda em nada — só deixa de mentir sobre o próprio perímetro.

- **O ajuste continua fora.** Os três widgets de fluxo leem `income`/`expense` e descartam o `adjustment` que os mesmos `AssetMonthFlows`/`LiabilityMonthFlows` já trazem. Isso é **consistente entre os três** e mantém o widget como um par de fluxos, não como um resumo que fecha. A consequência aceita: os números do dashboard **não reconciliam** com o `SummaryCard` de transações num mês que tenha ajuste.

- **Os widgets de fluxo ganham cabeçalho opcional** (`SHOW_HEADER`, que o maquinário de config já suporta), **desligado por padrão nos três** e oferecido no modal de opções. O problema que ele resolve é o de dois widgets de fluxo *empilhados* ficarem indistinguíveis — mesmo par de cards, mesmos rótulos, e a receita é *literalmente o mesmo número* nos dois, só a despesa difere. Como nenhum dashboard nasce com dois deles (ver o item seguinte), ligá-lo por padrão seria pagar o título antes de existir a ambiguidade; quem juntar mais de um liga o de que precisa. Nenhum dashboard já montado muda de aparência.

- **Os rótulos dos cards do widget de contas dizem de quem é o dinheiro**: `Entradas na conta` / `Saídas na conta`, contra `Receitas` / `Despesas` no neutro. Isso não distingue os *valores* — a receita segue idêntica nos dois —, distingue o perímetro dentro do próprio card, sem depender do cabeçalho.

- **O dashboard inicial abre com o perímetro neutro no lugar do de contas.** Um dashboard recém-instalado traz `OVERALL_BALANCE_STATS`, não `CONCRETE_BALANCE_STATS`; o de contas fica a um toque no modo de edição. Trazer os dois por padrão poria lado a lado dois cards com a mesma receita, que é justamente a ambiguidade acima. Isso rege **só quem instala agora** — dashboards já montados seguem intocados, como diz "Sem migração de preferências".

### Fora de escopo

- **Nenhuma leitura nova no razão.** O consolidado é obtido **somando** as duas leituras por natureza, que é o que `ledger-reporting` já exige para o saldo consolidado ("Consolidado sem agregado novo"). Um terceiro agregado dedicado seria a duplicação que aquele requisito proíbe.
- **`TransactionScope` e `BalanceOverview` não sobem para `core`.** Promovê-los é a correção certa para a duplicação da regra, mas é change própria e desproporcional aqui: o widget precisa de uma soma de dois agregados, não da gramática de fechamento.
- **Sem gramática de fechamento no dashboard.** Nada de abertura → fluxos → fechamento, linha de pagamento de fatura ou ajuste. Decisão explícita do usuário: apenas entradas e saídas.
- **Sem widget de estoque neutro.** O patrimônio líquido (`IEntryRepository.netWorth()`, que existe e **não tem nenhum consumidor em produção**) continua sem exposição. O usuário escolheu fluxo; o estoque neutro fica registrado como lacuna conhecida.
- **Sem widget de estoque de cartões.** Mesma razão.
- **O modelo de preferências não muda.** Nada de instância-com-id-próprio para permitir o mesmo tipo repetido com configs diferentes. É a resposta mais geral e destravaria outros widgets, mas é mudança grande no `DashboardViewModel` e no `DashboardComponentPreference`, contra a regra de não aumentar complexidade.
- **Sem migração de preferências.** O widget novo nasce ausente dos dashboards existentes e é adicionado pelo modo de edição, como qualquer outro. Nenhuma preferência salva é reescrita.
- **`feature/transactions` não é tocado.**

### Lacuna registrada

Depois desta change, a matriz fica:

```
                 ESTOQUE (acumulado)          FLUXO (do mês)
   CONTAS        Saldo em Contas     ✓        Balanço             ✓
   CARTÕES              ✗                     Balanço do Cartão   ✓
   NEUTRO               ✗  (= netWorth,       Balanço Geral       ✓  ← esta change
                            já no razão)
```

A coluna do estoque continua incompleta. Reavaliar quando houver demanda — `netWorth()` já está pronto.

## Capabilities

### New Capabilities
- `dashboard-balance-widgets`: os perímetros que o dashboard oferece como widget, a regra de que **todo widget nomeia o seu próprio perímetro**, a agregação do perímetro neutro por soma de naturezas disjuntas, e a consistência do que os três widgets de fluxo reportam (fluxos, sem fechamento e sem ajuste).

### Modified Capabilities
- Nenhuma. Nenhum requisito de `ledger-reporting` ou de `transaction-scope` muda: o razão não ganha leitura, e a tela de transações não é tocada.

## Impact

- **`core/ledger`** — nenhuma leitura nova: `assetMonthFlows` e `liabilityMonthFlows` já entregam tudo. O módulo acabou tocado por outra razão, alheia a esta change e descrita em `tasks.md` §6: `LedgerDatabase` desce de `commonMain` para `jvmTest`, porque as classes que o Room gera a partir dele colidiam no dex com as do `AppDatabase`. A garantia do D9 — um `@Query` do ledger nomeando uma tabela de facade não compila — passa a ser verificada em `kspTestKotlinJvm`.
- **`core/designsystem`** — `BalanceCardConfig` ganha `AccountIncome` / `AccountExpense`, derivados de `Income` / `Expense` **só no título** (tasks 5.4). O par neutro segue usando `Income` / `Expense`, que já existiam.
- **`core/ui`** — nada.
- **`core/resources`** — `component_total_balance` e `dashboard_total_balance` mudam de valor (`Saldo Total` → `Saldo em Contas`; `Total Balance` → `Balance in Accounts`), e `component_balance_stats` de `Balanço` para `Balanço em Contas`; novas strings `component_overall_balance_stats` (`Balanço Geral` / `Overall Balance`), os três cabeçalhos de widget de fluxo, e `balance_card_account_income` / `balance_card_account_expense` (`Entradas na conta` / `Saídas na conta`). Nenhuma chave é removida.
- **`feature/dashboard/impl`** — novo valor em `DashboardComponentType`; novo membro em `DashboardComponent` e em `DashboardComponentVariant`; novo ramo em `toViewingVariant`, em `DashboardComponentsBuilder.build`, em `DashboardComponentContent` e em `DashboardPreviewFactory`; `SHOW_HEADER` entra no `defaultConfig` dos três widgets de fluxo, e o `DashboardComponentOptionsModal` passa a oferecer o toggle a eles. As três primeiras são `when` exaustivos, então o compilador aponta cada ponto que falta. O `defaultPreferences` do dashboard inicial troca `CONCRETE_BALANCE_STATS` por `OVERALL_BALANCE_STATS` (tasks 5.1) — só para quem instala agora; nenhuma preferência salva é reescrita.
- **`feature/dashboard/api`** — nada. Nenhuma rota nova.
- **Testes** — que a despesa do perímetro neutro seja a soma das duas naturezas e não dupla-conte uma compra de cartão; que o pagamento de fatura não apareça em nenhuma das duas linhas; que a receita do perímetro neutro seja idêntica à do perímetro de contas; que o widget novo apareça na lista de widgets disponíveis e sobreviva a salvar/recarregar preferências.
- Sem migração de banco, sem mudança de escrita, sem mudança em nenhuma figura já exibida.
