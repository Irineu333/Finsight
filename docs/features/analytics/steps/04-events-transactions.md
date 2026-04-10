# Etapa 04 — Eventos: Transactions

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics nos ViewModels de criar, editar e deletar transação, **após confirmação bem-sucedida** da operação.

### Eventos

| Evento | Parâmetros |
|---|---|
| `create_transaction` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `is_installment`: `true` \| `false`, `category`: nome da categoria |
| `edit_transaction` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |
| `delete_transaction` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |

### Regras
- Disparar **após** o repositório confirmar sucesso — não no clique do botão.
- Cancelar o modal sem confirmar não deve gerar nenhum evento.
- Nenhum parâmetro contém dados financeiros (sem valores, descrições, nomes de conta/cartão).
- `target` = `account` quando a transação é em conta; `credit_card` quando em cartão.
- `is_installment` = `true` apenas em `create_transaction` parcelada.

`Analytics` é injetado nos ViewModels via Koin (`get()`) e registrado no `ViewModelModule.kt`.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionViewModel.kt` — injetar `Analytics`, disparar `create_transaction` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/editTransaction/EditTransactionViewModel.kt` — injetar `Analytics`, disparar `edit_transaction` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/deleteTransaction/DeleteTransactionViewModel.kt` — injetar `Analytics`, disparar `delete_transaction` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` nos três viewModels acima

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar transação de receita em conta → confirmar `create_transaction` com `type: income`, `target: account`, `is_installment: false`, `category` correto.
2. Criar transação de despesa parcelada em cartão → confirmar `create_transaction` com `type: expense`, `target: credit_card`, `is_installment: true`.
3. Cancelar criação → confirmar que nenhum evento foi disparado.
4. Editar transação → confirmar `edit_transaction` com `type`, `target` e `category` corretos.
5. Deletar transação → confirmar `delete_transaction` com `type`, `target` e `category` corretos.

**Revisão de código:**
- [x] `Analytics` injetado via construtor — não obtido diretamente do Koin dentro do ViewModel
- [x] Evento disparado após confirmação de sucesso do repositório, não no clique
- [x] Cancelamento não gera evento
- [x] Nenhum parâmetro contém valor financeiro, descrição ou nome de conta/cartão
- [x] `is_installment` presente apenas em `create_transaction`

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
