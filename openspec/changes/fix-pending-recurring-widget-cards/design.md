## Context

`DashboardPendingBalanceSection` (`feature/dashboard/impl` — `DashboardComponentContent.kt`) monta a linha do widget `balance_stats_pending` com dois `BalanceCard` (`PendingIncome`, `PendingExpense`), cada um sob uma condição própria:

```kotlin
val showBothCards = component.pendingIncome <= 0.0 && component.pendingExpense <= 0.0
if (component.pendingIncome > 0.0 || showBothCards) { /* card receita */ }
if (component.pendingExpense > 0.0 || showBothCards) { /* card despesa */ }
```

O efeito é uma tabela-verdade de três estados: par completo quando ambos têm valor, par completo quando **nenhum** tem, e cartão único — esticado pelo `weight(1f)` — quando exatamente um tem. O `showBothCards` existe só para recuperar o par no caso totalmente vazio, ou seja, o próprio código já reconhece que o par é a forma certa; ele apenas a abandona no caso do meio.

Os três outros widgets de fluxo (`OverallBalanceStats`, `ConcreteBalanceStats`, `CreditCardBalanceStats`) renderizam o par incondicionalmente. Nenhum deles tem equivalente do `showBothCards`.

A decisão de esconder o widget **inteiro** já mora em outro lugar: `DashboardComponentsBuilder.pendingBalanceStats` retorna `null` quando ambos são zero e `hideWhenEmpty` está ligado. As duas decisões coexistem hoje sem se conhecerem.

## Goals / Non-Goals

**Goals:**
- O widget de recorrentes previstas passa a exibir sempre os dois cartões, com R$ 0,00 na classe sem valor.
- A visibilidade condicional fica com um único dono: o builder, sobre o widget inteiro.

**Non-Goals:**
- Mudar como os valores previstos são somados (`pendingIncome`/`pendingExpense` seguem intactos).
- Mudar o padrão de `hideWhenEmpty` do tipo, ou o `defaultValue = true` que o builder usa quando a chave está ausente numa preferência antiga.
- Alinhar este widget ao `DashboardFlowStatsSection` (cabeçalho/título) usado pelos outros três — é uma diferença de apresentação preexistente, não o defeito relatado.
- Tocar os demais widgets, que já estão corretos.

## Decisions

**Remover as condições, não generalizá-las.** A correção é apagar os dois `if` e o `showBothCards`, deixando a `Row` com dois `BalanceCard` incondicionais — exatamente a forma dos outros três widgets de fluxo. A alternativa de manter a condição e trocá-la por uma flag de configuração ("esconder classe zerada") foi descartada: transformaria um defeito numa opção, e a regra do spec — mesmo conjunto de classes em todos os perímetros — deixaria de ser verificável na tela.

**A visibilidade continua sendo uma decisão só, e ela já existe.** Não se acrescenta nenhuma lógica de vazio à camada de UI para compensar a remoção; o `pendingBalanceStats` do builder já decide se o widget existe. Manter as duas decisões seria duplicar a regra em duas camadas com critérios que podem divergir — que é a origem do bug.

**Cobertura por teste no builder, não na UI.** O que muda é a árvore de composição, e o projeto não mantém testes de UI para o dashboard (`DashboardOverallBalanceStatsTest` e `DashboardAccountsOverviewTest` exercitam o builder). Um teste que garanta que o componente é emitido com `pendingIncome > 0` e `pendingExpense == 0.0` — em vez de suprimido — cobre a premissa da correção; a renderização do par passa a ser incondicional e portanto não tem ramo a testar.

## Risks / Trade-offs

- **Um cartão a mais na tela em meses parcialmente vazios** → é o comportamento pedido, e o mesmo dos outros três widgets; a linha não fica mais alta, apenas deixa de esticar um cartão sobre a largura toda.
- **Um widget que só tem receita prevista agora sempre mostra "Despesa prevista R$ 0,00"** → quem não quiser a linha zerada tem a chave `hideWhenEmpty`, que age sobre o widget inteiro — a granularidade correta.
