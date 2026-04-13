# Etapa 06 — Eventos: Credit Cards

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics nos ViewModels de cartões de crédito e faturas, **após confirmação bem-sucedida** de cada operação.

### Eventos

| Evento | Parâmetros | ViewModel |
|---|---|---|
| `create_credit_card` | — | `CreditCardFormViewModel` (modo criar) |
| `edit_credit_card` | — | `CreditCardFormViewModel` (modo editar) |
| `delete_credit_card` | — | `DeleteCreditCardViewModel` |
| `close_invoice` | — | `CloseInvoiceViewModel` |
| `pay_invoice` | — | `PayInvoiceViewModel` |
| `reopen_invoice` | — | `ReopenInvoiceViewModel` |
| `adjust_invoice_balance` | — | `EditInvoiceBalanceViewModel` |
| `delete_future_invoice` | — | `DeleteFutureInvoiceViewModel` |
| `advance_invoice_payment` | — | `AdvancePaymentViewModel` |

Todos sem parâmetros adicionais — nenhum dado financeiro (sem valores, limites, nomes).

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/creditCardForm/CreditCardFormViewModel.kt` — injetar `Analytics`, disparar `create_credit_card` ou `edit_credit_card`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/deleteCreditCard/DeleteCreditCardViewModel.kt` — injetar `Analytics`, disparar `delete_credit_card`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/closeInvoice/CloseInvoiceViewModel.kt` — injetar `Analytics`, disparar `close_invoice`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/payInvoice/PayInvoiceViewModel.kt` — injetar `Analytics`, disparar `pay_invoice`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/reopenInvoice/ReopenInvoiceViewModel.kt` — injetar `Analytics`, disparar `reopen_invoice`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/editInvoiceBalance/EditInvoiceBalanceViewModel.kt` — injetar `Analytics`, disparar `adjust_invoice_balance`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/deleteFutureInvoice/DeleteFutureInvoiceViewModel.kt` — injetar `Analytics`, disparar `delete_future_invoice`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/advancePayment/AdvancePaymentViewModel.kt` — injetar `Analytics`, disparar `advance_invoice_payment`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` nos oito viewModels acima

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar cartão → confirmar `create_credit_card`.
2. Editar cartão → confirmar `edit_credit_card`.
3. Deletar cartão → confirmar `delete_credit_card`.
4. Fechar fatura → confirmar `close_invoice`.
5. Pagar fatura → confirmar `pay_invoice`.
6. Reabrir fatura → confirmar `reopen_invoice`.
7. Ajustar saldo da fatura → confirmar `adjust_invoice_balance`.
8. Deletar fatura futura → confirmar `delete_future_invoice`.
9. Realizar pagamento antecipado → confirmar `advance_invoice_payment`.

**Revisão de código:**
- [x] `create_credit_card` e `edit_credit_card` diferenciados pelo modo do formulário
- [x] Nenhum parâmetro contém dados financeiros
- [x] Todos os 9 eventos implementados

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
