## Context

A análise de 14 entries de modal revelou:

1. **Padrão já existente em `CloseInvoiceModalEntry`**: `(invoiceId: Long, closingDate: LocalDate)` — id + parâmetro transiente.
2. **Padrão de UiState com Loading**: `AccountsUiState`, `CreditCardsUiState`, `EditInvoiceBalanceUiState` já usam `sealed { Loading, [Empty,] Content }`.
3. **Re-fetch já implícito**: `ViewCategoryViewModel`, `ViewOperationViewModel`, `EditInvoiceBalanceViewModel` chamam `getXxxById` no init e usam o model passado apenas como fallback (`?: original`).
4. **Forms usam o model para prefill**: `CategoryFormViewModel`, `RecurringFormViewModel`, `AccountFormViewModel`, `CreditCardFormViewModel` leem múltiplos campos do model na inicialização.

Restrições relevantes:

- Entries vivem em `:feature:X:api` — não podem importar Compose nem outros `:api` (regra D2/D10 do projeto).
- Modais são `ModalBottomSheet` (não navigation routes), então não há serialização que force primitivos. A motivação é puramente de contrato.
- `:feature:X:impl` continua livre para usar models internamente — a restrição é apenas no `:api`.

## Goals / Non-Goals

**Goals:**

- Eliminar vazamento de tipos de domínio em `:feature:*:api` modal entries.
- Padronizar contrato: entries recebem `Long` ids + parâmetros transientes.
- Consolidar UiState pattern para modais id-driven: `sealed { Loading, Content [, Empty] }`.
- Garantir que VMs sempre operem em dado fresh re-buscado por id.

**Non-Goals:**

- Mudar contrato de modais sem entrada (ex: `DatePickerModal`, `IconPickerModal`).
- Mudar comportamento, textos ou layout dos modais — refactor de contrato puro.
- Migrar para Navigation Routes (modais continuam sendo `ModalBottomSheet` injetadas via `ModalManager`).
- Padronizar UiStates de telas (escopo é só modais).

## Decisions

### D1: Entries recebem ids, não models

**Decisão:** Toda função `create(...)` em `:api` modal entries recebe `Long` ids para entidades persistentes. Parâmetros não-entidade (datas, modos, perspectivas, tipos enum) continuam.

**Rationale:** Elimina vazamento de tipos de domínio em `:api`, evita staleness, padroniza com `CloseInvoiceModalEntry`. Re-fetch é barato (Room) e já era o comportamento implícito em vários VMs.

**Alternativa considerada — id + snapshot opcional `(id, initial: Foo? = null)`:** mais flexível e elimina o flicker de Loading quando o caller já tem o model em mão, mas dobra a surface de teste e mantém leak de model no `:api`. Descartado.

**Alternativa considerada — híbrido (id em views, model em forms):** preserva prefill instantâneo em forms, mas mantém duas convenções no mesmo `:api`. Descartado em favor de regra única; mitigamos o flicker via D3 (criação) e Loading com layout estável (D8).

---

### D2: UiState em modais segue `sealed { Loading, Content [, Empty] }`

**Decisão:** Modais cujo entry recebe `id` MUST ter UiState selada com pelo menos `Loading` e `Content`. `Empty` é adicionado quando o id pode apontar para entidade deletada e o modal precisa exibir mensagem em vez de fechar.

**Rationale:** Coerente com `AccountsUiState`, `CreditCardsUiState`, `EditInvoiceBalanceUiState`. O modelo só existe após o fetch — uma data class única forçaria fallback artificial (defaults vazios) que mente sobre o estado real.

---

### D3: Loading só vale em edit-mode para forms

**Decisão:** Form modals com id nullable (`AccountFormModalEntry(accountId: Long?)`, etc.) emitem `Loading` apenas quando `id != null` (edição). No modo criação, o estado inicial já é `Content` com defaults.

**Rationale:** Criação não tem fetch — não há razão para flicker de Loading.

```kotlin
val uiState = when {
    id != null -> flow {
        val entity = repo.getById(id)
        if (entity == null) {
            modalManager.dismiss()
            return@flow
        }
        emit(Content.fromEntity(entity))
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), initialValue = Loading)

    else -> MutableStateFlow(Content.defaults()).asStateFlow()
}
```

---

### D4: Entidade deletada — comportamento por categoria de modal

**Decisão:** Quando `getXxxById(id)` retorna `null`:

| Categoria | Comportamento | Exemplos |
| --- | --- | --- |
| **View modals** (read-only) | Emite `Empty`, modal renderiza mensagem "Não disponível" + botão fechar | `ViewCategory`, `ViewOperation`, `ViewAdjustment`, `ViewBudget`, `ViewRecurring` |
| **Form modals em edit-mode** | `modalManager.dismiss()` + `Crashlytics.recordException` | `AccountForm(id)`, `CategoryForm(id)`, `RecurringForm(id)`, `CreditCardForm(id)` |
| **Action/Confirm modals** | `modalManager.dismiss()` direto | `Pay`, `AdvancePayment`, `EditInvoiceBalance`, `ConfirmRecurring`, `CloseInvoice` |

**Rationale:** `Empty` só faz sentido onde a UX é "inspecionar" (read-only). Em fluxos de ação ou edição, o estado correto é fechar — não há nada útil para o usuário fazer com fantasma.

---

### D5: `currentBillAmount` deixa de ser parâmetro de entry

**Decisão:** `PayInvoiceModalEntry` e `AdvancePaymentModalEntry` recebem só `invoiceId`. O VM chama `CalculateInvoiceUseCase(invoiceId)` para obter saldo atual.

**Rationale:** Garante valor sempre fresh, alinhado com `EditInvoiceBalanceViewModel` que já faz isso. Mudança semântica: o caller perde a possibilidade de "fixar" um snapshot, mas isso era ilusão — qualquer divergência entre o snapshot do caller e o cálculo do VM seria um bug.

**Trade-off:** Loading flash (alguns ms) ao abrir modais de pagamento. Aceitável dado o ganho de consistência.

---

### D6: `BudgetProgress` deixa de ser parâmetro

**Decisão:** `ViewBudgetModalEntry` recebe `budgetId: Long`. O VM reconstrói progresso via use case (existente ou novo `CalculateBudgetProgressUseCase`).

**Rationale:** `BudgetProgress` é um view-object com `spent`, `progress`, `recurring` resolvido — passa pelo `:api` apenas porque o caller já tinha. Reconstruir no VM mantém `:api` limpo e dado fresh.

**Risco:** Se o use case não existe extraído, esta task implica criá-lo em `:feature:budgets:api` (interface) + `:feature:budgets:impl` (concreto). Verificar antes de iniciar a task 8.

---

### D7: Parâmetros que continuam como entrada

| Parâmetro | Tipo | Por quê continua |
| --- | --- | --- |
| `OperationPerspective` | sealed class | Seleção de view, não entidade persistente |
| `Category.Type` (em `initialType`) | enum | Hint de UI, não entidade |
| `LocalDate` (closingDate, targetDate) | primitivo temporal | Contexto da chamada |

**Rationale:** Não são entidades persistentes — não há id que os identifique. Continuam como parâmetros do entry.

---

### D8: Loading state preserva dimensões do modal

**Decisão:** O conteúdo `Loading` MUST renderizar dentro de uma `Column`/`Box` com altura mínima compatível com a do `Content` final, evitando layout shift quando o fetch resolve.

**Rationale:** Modais que pulam de altura mínima para altura final causam jank em `ModalBottomSheet`. Já é prática em `EditInvoiceBalanceModal` (Title + spacer + CircularProgressIndicator dentro de Column com mesma estrutura).

## Risks / Trade-offs

- **Loading flash em forms editáveis e modais de pagamento**: os fetches são <50ms via Room, mas há um frame onde o modal está montado sem conteúdo. Mitigação: D8 (layout estável).
- **`BudgetProgress` reconstruído**: pode introduzir leve diferença numérica se o VM usar lógica ligeiramente diferente da que produziu o `BudgetProgress` original. Mitigação: extrair use case e usá-lo também na origem (lista de budgets) — paridade por construção.
- **Volume de mudanças**: 14 entries + 11 UiStates + ~30 call-sites. Alto risco de merge conflict se feito em PR único. Mitigação: migrar feature por feature em commits/PRs separados.
- **Inconsistência transitória**: durante a migração, algumas features estarão em "id-only" e outras em "model rico". Aceitável pois cada feature é autocontida.

## Migration Plan

Por feature, em ordem de complexidade crescente:

1. **Categories** (`ViewCategory`, `CategoryForm`) — VMs já re-buscam, mudança é cosmética.
2. **Recurring** (3 entries) — padrão similar.
3. **Accounts** (`AccountForm`) — form simples.
4. **Transactions** (`ViewOperation`, `ViewAdjustment`) — view modals com cascata de fetches já existente.
5. **CreditCards — Form** (`CreditCardForm`) — form simples.
6. **CreditCards — Edit balance** (`EditInvoiceBalance`) — VM já está alinhado.
7. **CreditCards — Pay/Advance** (D5) — implica recálculo via use case.
8. **Budgets** (D6) — pode implicar novo use case.

Cada feature é um commit separado para reduzir blast radius e facilitar review.

## Open Questions

1. **Existe `CalculateBudgetProgressUseCase`?** Verificar em `:feature:budgets:impl`. Se não, criá-lo como parte da task 8.
2. **`Empty` state em view modals**: usar componente compartilhado em `:core:ui` ou inline por modal? Sugestão inicial: inline na primeira iteração; extrair se houver duplicação.
3. **`Recurring` em `EditTransaction`** (se houver caso similar a `currentBillAmount`): há outros snapshots derivados sendo passados como parâmetro de entry que ainda não foram catalogados? Validar durante a task 4.
4. **DI (Koin)**: alguns ViewModels recebem `parametersOf(model)` hoje. Após migração, recebem `parametersOf(id)`. Confirmar que todos os módulos Koin (`viewModel { (id: Long) -> ... }`) estão atualizados.
