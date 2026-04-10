# Etapa 09 — Eventos: Recurring

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics nos ViewModels de recorrências, **após confirmação bem-sucedida** de cada operação.

### Eventos

| Evento | Parâmetros | ViewModel |
|---|---|---|
| `create_recurring` | `type`, `target`, `category` | `RecurringFormViewModel` (modo criar) |
| `edit_recurring` | `type`, `target`, `category` | `RecurringFormViewModel` (modo editar) |
| `delete_recurring` | `type`, `target`, `category` | `DeleteRecurringViewModel` |
| `confirm_recurring` | `type`, `target`, `category` | `ConfirmRecurringViewModel` (ação confirmar) |
| `skip_recurring` | `type`, `target`, `category` | `ConfirmRecurringViewModel` (ação pular) |
| `stop_recurring` | `type`, `target`, `category` | `StopRecurringViewModel` |
| `reactivate_recurring` | `type`, `target`, `category` | `ReactivateRecurringViewModel` |

**Parâmetros comuns:**
- `type`: `income` | `expense`
- `target`: `account` | `credit_card`
- `category`: nome da categoria

### Regras
- `ConfirmRecurringViewModel` gerencia dois eventos distintos (`confirm_recurring` e `skip_recurring`) — disparar o evento correto conforme a ação executada.
- Sem parâmetros financeiros (sem valores, datas, descrições).

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/recurringForm/RecurringFormViewModel.kt` — injetar `Analytics`, disparar `create_recurring` ou `edit_recurring`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/deleteRecurring/DeleteRecurringViewModel.kt` — injetar `Analytics`, disparar `delete_recurring`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/confirmRecurring/ConfirmRecurringViewModel.kt` — injetar `Analytics`, disparar `confirm_recurring` ou `skip_recurring`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/stopRecurring/StopRecurringViewModel.kt` — injetar `Analytics`, disparar `stop_recurring`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/reactivateRecurring/ReactivateRecurringViewModel.kt` — injetar `Analytics`, disparar `reactivate_recurring`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` nos cinco viewModels acima

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar recorrência de receita em conta → confirmar `create_recurring` com `type: income`, `target: account`, `category` correto.
2. Editar recorrência → confirmar `edit_recurring`.
3. Confirmar ocorrência → confirmar `confirm_recurring` com `type`, `target`, `category`.
4. Pular ocorrência → confirmar `skip_recurring` com `type`, `target`, `category`.
5. Parar recorrência → confirmar `stop_recurring`.
6. Reativar recorrência → confirmar `reactivate_recurring`.
7. Deletar recorrência → confirmar `delete_recurring`.

**Revisão de código:**
- [x] `confirm_recurring` e `skip_recurring` são disparados pela ação correta em `ConfirmRecurringViewModel`
- [x] `create_recurring` e `edit_recurring` diferenciados pelo modo do formulário
- [x] Nenhum parâmetro contém dados financeiros ou datas
- [x] Todos os 7 eventos implementados

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
