## 1. Correção do widget

- [x] 1.1 Em `DashboardComponentContent.kt`, remover de `DashboardPendingBalanceSection` a variável `showBothCards` e os dois `if` que envolvem os `BalanceCard`, deixando os cartões `PendingIncome` e `PendingExpense` incondicionais dentro da `Row`.
- [x] 1.2 Confirmar que `DashboardComponentsBuilder.pendingBalanceStats` segue sendo o único ponto que decide a existência do widget — nenhuma lógica de vazio foi acrescentada à UI para compensar a remoção.

## 2. Teste

- [x] 2.1 Adicionar `DashboardPendingBalanceStatsTest` em `feature/dashboard/impl/src/commonTest`, montando o builder no mesmo molde de `DashboardOverallBalanceStatsTest`.
- [x] 2.2 Caso "só receita prevista": recorrentes pendentes apenas de receita produzem o componente com `pendingIncome` somado e `pendingExpense == 0.0` — o componente existe, não é suprimido.
- [x] 2.3 Caso "só despesa prevista": simétrico ao anterior, com `pendingIncome == 0.0`.
- [x] 2.4 Caso "nada previsto": com `hideWhenEmpty` ligado o componente é `null`; com a chave em `false` ele existe com as duas classes zeradas.

## 3. Verificação

- [x] 3.1 Rodar `./gradlew :feature:dashboard:impl:testDebugUnitTest` e garantir a suíte verde.
- [x] 3.2 Rodar o app e conferir, num mês com apenas receita recorrente prevista, que os dois cartões aparecem lado a lado com R$ 0,00 na despesa.
- [x] 3.3 Confirmar que a forma do widget não muda entre um estado com as duas classes e um com apenas uma. O dashboard não navega por mês — o widget de previstas sempre reflete o mês corrente (`today`, via `GetPendingRecurringUseCase`) —, então a verificação foi feita sobre a variação equivalente: com só a receita e depois com receita e despesa, os dois cartões mantiveram posição e largura idênticas.
