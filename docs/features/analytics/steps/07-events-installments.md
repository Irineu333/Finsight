# Etapa 07 — Eventos: Installments

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics nos ViewModels de parcelas, **após confirmação bem-sucedida**.

### Eventos

| Evento | Parâmetros |
|---|---|
| `create_installments` | `category`: nome da categoria, `installments_count`: quantidade de parcelas |
| `delete_installments` | `category`: nome da categoria, `installments_count`: quantidade de parcelas |

### Regras
- `installments_count` é um número inteiro — não contém dados financeiros.
- `category` é o nome da categoria (não ID) — é dado de contexto de uso, não dado pessoal ou financeiro.
- Disparar após sucesso do use case, não no clique.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addInstallment/AddInstallmentViewModel.kt` — injetar `Analytics`, disparar `create_installments` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/deleteInstallment/DeleteInstallmentViewModel.kt` — injetar `Analytics`, disparar `delete_installments` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` nos dois viewModels acima

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar parcelas → confirmar `create_installments` com `category` e `installments_count` corretos.
2. Deletar parcelas → confirmar `delete_installments` com `category` e `installments_count` corretos.

**Revisão de código:**
- [x] `installments_count` é o número de parcelas, não um valor monetário
- [x] `category` é o nome da categoria (não ID, não valor)
- [x] Eventos disparados após sucesso do use case

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
