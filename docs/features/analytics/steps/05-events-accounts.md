# Etapa 05 — Eventos: Accounts

> Parte do plano: [Analytics](../plan.md)

---

## O que fazer

Adicionar chamadas de analytics nos ViewModels de contas, **após confirmação bem-sucedida** de cada operação.

### Eventos

| Evento | Parâmetros |
|---|---|
| `create_account` | `is_default`: `true` \| `false` |
| `edit_account` | `is_default`: `true` \| `false` |
| `delete_account` | — |
| `transfer_between_accounts` | — |
| `adjust_account_balance` | — |

### Regras
- `is_default` reflete o estado salvo — não o estado antes da edição.
- Sem parâmetros financeiros (sem saldo, sem nome da conta).
- Disparar após sucesso do repositório, não no clique.

---

## Arquivos afetados

- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/accountForm/AccountFormViewModel.kt` — injetar `Analytics`, disparar `create_account` ou `edit_account` conforme modo do formulário
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/deleteAccount/DeleteAccountViewModel.kt` — injetar `Analytics`, disparar `delete_account` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/transferBetweenAccounts/TransferBetweenAccountsViewModel.kt` — injetar `Analytics`, disparar `transfer_between_accounts` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/editAccountBalance/EditAccountBalanceViewModel.kt` — injetar `Analytics`, disparar `adjust_account_balance` após sucesso
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/ViewModelModule.kt` — adicionar `analytics = get()` nos quatro viewModels acima

---

## Critério de aceite

**Validação manual (Firebase DebugView, Android):**
1. Criar conta como padrão → confirmar `create_account` com `is_default: true`.
2. Criar conta sem ser padrão → confirmar `create_account` com `is_default: false`.
3. Editar conta → confirmar `edit_account` com `is_default` refletindo o estado salvo.
4. Deletar conta → confirmar `delete_account` sem parâmetros.
5. Transferir entre contas → confirmar `transfer_between_accounts` sem parâmetros.
6. Ajustar saldo → confirmar `adjust_account_balance` sem parâmetros.

**Revisão de código:**
- [ ] `is_default` reflete o estado salvo, não o estado anterior
- [ ] Nenhum parâmetro contém saldo ou nome de conta
- [ ] `create_account` e `edit_account` diferenciados pelo modo do formulário (criar vs. editar)
- [ ] Eventos disparados após sucesso do repositório

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
