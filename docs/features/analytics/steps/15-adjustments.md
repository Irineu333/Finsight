# Etapa 15 — Ajustes pós-implementação

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Aplicar refinamentos semânticos identificados após a implementação das etapas anteriores.

### Ajuste 1 — Remover `is_installment` de `create_transaction`

**Contexto:** a etapa 04 introduziu `is_installment` em `create_transaction` para distinguir transações simples de parceladas. Durante a etapa 14 (eventos tipados), ficou evidente que transações parceladas já disparam `create_installments` via `AddInstallmentViewModel` — usar `create_transaction` com `is_installment: true` para o mesmo caso em `AddTransactionViewModel` é semanticamente impreciso.

**Mudança:**
- `CreateTransaction` deixa de receber `isInstallment: Boolean` e de incluir `is_installment` nos params
- `AddTransactionViewModel` — branch de parcelamento (`form.installments > 1`) passa a disparar `CreateInstallments(form, count = form.installments)` em vez de `CreateTransaction(form, isInstallment = true)`

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/analytics/event/Transactions.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionViewModel.kt`

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar transação simples → confirmar `create_transaction` sem parâmetro `is_installment`
2. Criar transação parcelada via modal de transação → confirmar `create_installments` (não `create_transaction`)

**Revisão de código:**
- [x] `CreateTransaction` sem parâmetro `isInstallment`
- [x] `AddTransactionViewModel` usa `CreateInstallments(form, count = form.installments)` no branch de parcelamento
- [x] Nenhuma referência a `is_installment` restante no código

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
