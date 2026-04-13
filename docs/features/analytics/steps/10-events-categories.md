# Etapa 10 — Eventos: Categories

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics no ViewModel de categorias, **após confirmação bem-sucedida**.

### Eventos

| Evento | Parâmetros |
|---|---|
| `create_category` | `name`: nome da categoria, `type`: `income` \| `expense` |
| `edit_category` | `name`: nome da categoria, `type`: `income` \| `expense` |
| `delete_category` | `name`: nome da categoria, `type`: `income` \| `expense` |

### Regras
- `name` é o nome da categoria — dado de contexto de uso, não dado financeiro ou pessoal.
- `type` é o tipo da categoria (`income` ou `expense`).
- Disparar após sucesso do repositório, não no clique.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/categoryForm/CategoryFormViewModel.kt` — injetar `Analytics`, disparar `create_category` ou `edit_category`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/deleteCategory/DeleteCategoryViewModel.kt` — injetar `Analytics`, disparar `delete_category`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` nos dois viewModels acima

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar categoria de despesa → confirmar `create_category` com `name` e `type: expense`.
2. Editar categoria → confirmar `edit_category` com `name` e `type` corretos.
3. Deletar categoria → confirmar `delete_category` com `name` e `type`.

**Revisão de código:**
- [x] `create_category` e `edit_category` diferenciados pelo modo do formulário
- [x] `name` e `type` presentes nos três eventos
- [x] Eventos disparados após sucesso do repositório

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
