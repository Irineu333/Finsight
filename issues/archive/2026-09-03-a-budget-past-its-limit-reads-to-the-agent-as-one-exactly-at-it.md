---
area: mcp
severity: medium
type: data
verdict: fixed
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

## Desfecho

**Causa real** — a da suposição, confirmada: o domínio já tinha tudo. `BudgetProgress.isExceeded`
(`core/model/.../domain/model/BudgetProgress.kt:48`) e `exceededAmount` (:77) existem, carregam a
marca de aproximação e são o par pelo qual `ViewBudgetModal:253-274` escolhe entre "restam" e
"excedido em". Só o mapeador do agente não os lia: `GetBudgetProgressTool.toAgentBudget()` publicava
`remaining` e `progress` — truncados de propósito em `:45` e `:56`, porque descrevem uma barra — e
parava aí. `BudgetProgress` não precisou de nenhuma alteração.

O que a sugestão não previa era **como publicar o fato quando o gasto não se resolve**. `isExceeded`
responde `false` em duas situações diferentes: "não estourou" e "não dá para saber", porque exige
`isResolved` (:43,48). Repassar esse `false` ao payload transformaria a recusa do domínio em
negação — afirmaria "não estourou" justamente onde nada permite afirmá-lo, que é a direção em que um
orçamento não pode errar, o mesmo argumento do KDoc de `hasUnpricedSpending` (:11-18).

**Mudança** — dois campos em `AgentBudget`
(`feature/mcp/impl/src/jvmMain/kotlin/com/neoutils/finsight/mcp/surface/AgentBudget.kt:31-40`):
`is_exceeded` (`Boolean?`) e `exceeded_by` (`AgentFigure?`), ambos com `@SerialName` em snake_case e
default `null` — os outros três construtores de `AgentBudget` (`ListBudgetsTool`, as duas escritas em
`BudgetWriteTools`) não falam de mês, não têm gasto e continuam sem os campos.

`GetBudgetProgressTool.toAgentBudget()` (`.../tool/GetBudgetProgressTool.kt:103-108`) publica
`isExceeded = isExceeded.takeIf { isResolved }` e
`exceededBy = exceededAmount?.takeIf { isExceeded }?.agentFigure()`. Como `agentJson` usa
`explicitNulls = false` (`ToolSupport.kt:41-44`), o gasto que nenhuma taxa alcança chega **sem** as
duas chaves, no mesmo vocabulário que `progress` já usava: o que não se sabe não aparece, em vez de
aparecer como zero ou como `false`. `remaining` e `progress` ficaram exatamente como estavam.

A descrição da ferramenta (`.../GetBudgetProgressTool.kt:52-56`) passa a nomear o par — é o único
material pelo qual o agente escolhe uma ferramenta — e a frase que já explicava a ausência de
`progress` passa a cobrir `is_exceeded` junto.

**Prova** — dois casos novos em
`feature/mcp/impl/src/jvmTest/kotlin/com/neoutils/finsight/mcp/BudgetsAndRecurringTest.kt`, ambos
pelo socket real (`overTheProtocol`), sobre R$ 300 gastos em março:

- `a budget past its limit is told apart from one that stopped exactly at it` — o cenário do bug,
  dois orçamentos sobre a mesma categoria, limites de R$ 300 e R$ 200. Afirma primeiro o que eles
  têm em comum (`remaining` 0, `progress` 1.0 nos dois) e depois o que os separa: `is_exceeded`
  `false`/`true` e `exceeded_by` R$ 100,00 no segundo. **Vermelho antes**:
  `AssertionError: spending its ceiling is not passing it expected:<false> but was:<null>`.
  Verde depois.
- `spending no rate reaches leaves the overrun unstated, rather than denied` — o mesmo gasto em USD
  com o arquivo de taxas vazio. Afirma que `progress`, `is_exceeded` e `exceeded_by` estão ausentes.
  Esse **nasceu verde** e não prova o defeito: ele existe contra a correção ingênua, um
  `is_exceeded: Boolean` não anulável, que o deixaria vermelho com `false`.

Suíte do módulo: `./gradlew :feature:mcp:impl:jvmTest` — 274 testes em 33 classes, 0 falhas.
`jvmTest` da raiz não foi rodado: o módulo é o dono das duas superfícies que mudaram, e a
alteração não sai dele.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
