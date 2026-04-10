# Etapa 08 — Eventos: Budgets

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics nos ViewModels de orçamentos, **após confirmação bem-sucedida**.

### Eventos

| Evento | Parâmetros |
|---|---|
| `create_budget` | `type`: `fixed` \| `percentage`, `categories`: lista de nomes separados por vírgula |
| `edit_budget` | `type`: `fixed` \| `percentage`, `categories`: lista de nomes separados por vírgula |
| `delete_budget` | — |

### Regras
- `categories` é a lista de nomes das categorias associadas ao orçamento, separados por vírgula (ex: `"Alimentação,Transporte"`).
- `type` reflete o tipo salvo (`fixed` para valor fixo, `percentage` para percentual).
- Sem parâmetros financeiros (sem valor do orçamento, sem percentual).
- Disparar após sucesso do repositório, não no clique.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/budgetForm/BudgetFormViewModel.kt` — injetar `Analytics`, disparar `create_budget` ou `edit_budget` conforme modo
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/deleteBudget/DeleteBudgetViewModel.kt` — injetar `Analytics`, disparar `delete_budget` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` nos dois viewModels acima

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar orçamento por percentual com duas categorias → confirmar `create_budget` com `type: percentage` e `categories` com os nomes corretos separados por vírgula.
2. Criar orçamento fixo → confirmar `create_budget` com `type: fixed`.
3. Editar orçamento → confirmar `edit_budget` com `type` e `categories` refletindo o estado salvo.
4. Deletar orçamento → confirmar `delete_budget` sem parâmetros.

**Revisão de código:**
- [x] `create_budget` e `edit_budget` diferenciados pelo modo do formulário
- [x] `categories` lista nomes (não IDs) separados por vírgula
- [x] Nenhum parâmetro contém valor ou percentual do orçamento
- [x] Eventos disparados após sucesso do repositório

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
