---
area: mcp
severity: medium
type: data
---

# Um orçamento estourado chega ao agente idêntico a um que parou exatamente no limite

## Cenário

**DADO** dois orçamentos de limite R$ 1.000, um com R$ 1.000 gastos no mês e outro com R$ 1.500
**QUANDO** um agente chama `get_budget_progress`
**ENTÃO** os dois chegam com `remaining` zerado e `progress` igual a 1.0, sem nada que os separe
**DEVERIA** dizer que o segundo estourou e em quanto, como as telas dizem

## Mecânica

As duas figuras que o payload carrega são truncadas no domínio, de propósito, porque é uma barra
que elas descrevem: `BudgetProgress.remaining` aplica `coerceAtLeast(0.0)` e `BudgetProgress.progress`
aplica `coerceIn(0.0, 1.0)`. O estouro é um fato separado, com figura própria — `isExceeded` e
`exceededAmount` —, e é assim que a tela mostra "excedido em X" no lugar de "restam X".

`AgentBudget` declara `limit`, `spent`, `remaining`, `progress`, `limitType` e `percentage`, e
nenhum dos dois campos de estouro. `GetBudgetProgressTool.toAgentBudget()` publica as versões
truncadas e para aí.

Refazer a conta do lado do agente não recupera o fato. `spent` é opcional no payload e ausente
quando não há figura; e quando parte do gasto está numa moeda que nenhuma taxa alcança, `spent` é
um **piso** — é por isso que `isExceeded` exige `isResolved`, e uma comparação ingênua afirmaria
estouro justamente onde o domínio se recusa a afirmá-lo.

## Evidência

- `BudgetProgress.remaining` — `(budget.amount - spent).coerceAtLeast(0.0)`
- `BudgetProgress.progress` — `coerceIn(0.0, 1.0)`, e `null` quando não resolvido
- `BudgetProgress.isExceeded` — `isResolved && spent > budget.amount`
- `BudgetProgress.exceededAmount` — a figura que a tela mostra
- `AgentBudget` — sem `is_exceeded` e sem `exceeded`
- `GetBudgetProgressTool.toAgentBudget()` — publica `remaining` e `progress` truncados
- `ViewBudgetModal` e `BudgetCard` — escolhem entre "restam" e "excedido em" por `isExceeded`

## Consequência

O agente responde "você está exatamente no limite" a quem estourou, que é a direção em que um
orçamento não pode errar — o mesmo argumento que o KDoc de `BudgetProgress.hasUnpricedSpending`
usa para recusar mostrar um piso como total. O erro é silencioso: nada no payload sugere que falta
informação, e as duas figuras presentes são números plausíveis.

## Sugestão

Acrescentar a `AgentBudget` o par que a tela usa — o fato e a figura —, publicados por
`toAgentBudget()` a partir de `isExceeded` e `exceededAmount`, que já existem e já carregam a marca
de aproximação. Manter `remaining` e `progress` como estão: eles descrevem a barra, e mudá-los
moveria a decisão do domínio para a ferramenta.

Não vinculante — quem corrige decide.
