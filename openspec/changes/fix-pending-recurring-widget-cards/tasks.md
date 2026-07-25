## 1. Correção do widget

- [ ] 1.1 Em `DashboardComponentContent.kt`, remover de `DashboardPendingBalanceSection` a variável `showBothCards` e os dois `if` que envolvem os `BalanceCard`, deixando os cartões `PendingIncome` e `PendingExpense` incondicionais dentro da `Row`.
- [ ] 1.2 Confirmar que `DashboardComponentsBuilder.pendingBalanceStats` segue sendo o único ponto que decide a existência do widget — nenhuma lógica de vazio foi acrescentada à UI para compensar a remoção.

## 2. Teste

- [ ] 2.1 Adicionar `DashboardPendingBalanceStatsTest` em `feature/dashboard/impl/src/commonTest`, montando o builder no mesmo molde de `DashboardOverallBalanceStatsTest`.
- [ ] 2.2 Caso "só receita prevista": recorrentes pendentes apenas de receita produzem o componente com `pendingIncome` somado e `pendingExpense == 0.0` — o componente existe, não é suprimido.
- [ ] 2.3 Caso "só despesa prevista": simétrico ao anterior, com `pendingIncome == 0.0`.
- [ ] 2.4 Caso "nada previsto": com `hideWhenEmpty` ligado o componente é `null`; com a chave em `false` ele existe com as duas classes zeradas.

## 3. Verificação

- [ ] 3.1 Rodar `./gradlew :feature:dashboard:impl:testDebugUnitTest` e garantir a suíte verde.
- [ ] 3.2 Rodar o app e conferir, num mês com apenas receita recorrente prevista, que os dois cartões aparecem lado a lado com R$ 0,00 na despesa.
- [ ] 3.3 Navegar entre um mês com as duas classes e um mês com apenas uma, confirmando que a forma do widget não muda.
