# Superfície MCP do Finsight

> Material de exploração, não normativo. Descreve as ferramentas que um servidor MCP local
> embutido no app desktop exporia, e — para cada uma — **quem decide a regra**, **o que a
> ferramenta adapta** e **qual agregado o payload carrega**.
>
> Levantado contra o código em `adb85e2e3` (main). Cada use case citado foi lido no disco.

---

## O princípio

O servidor MCP é uma **camada de apresentação**, com um agente no lugar da tela. Isso não é
analogia: é a regra que `openspec/specs/presentation-mapping/spec.md` já declara para a UI,
aplicada a uma segunda superfície.

> *"É a instância, na camada de apresentação, da regra geral declarada em `balanced-ledger`:
> a feature adapta ao usuário, mas não decide qual é a regra."*

| ADAPTAR — é o trabalho | DECIDIR — tem dono, consuma |
|---|---|
| compor várias leituras numa resposta | o que significa pagar uma fatura |
| traduzir domínio → DTO plano | em qual fatura a compra cai |
| resolver id → nome (`"Nubank"`, não `7`) | se um formulário vira parcelamento ou recorrência |
| formatar dinheiro com moeda e sinal | se uma categoria pode ser apagada ou só arquivada |
| pôr o total ao lado da lista | qual perna é a primária |
| paginar, ordenar, filtrar | qual sinal exibir por `AccountType` |
| nomear a alternativa numa recusa | o que é despesa e o que é transferência |

Duas consequências operacionais:

1. **Nenhuma ferramenta devolve modelo de domínio.** DTOs planos, valores já resolvidos, no
   máximo um id — a mesma regra dos modelos de UI. O MCP não reusa `TransactionUi` (que
   carrega `CategoryLazyIcon` e `CategoryColor`, tipos de Compose), mas consome as mesmas
   *decisões*: `deriveTransactionLabel`, `TransactionPerspective`, `figureLegUnder`,
   `ConsolidateMoneyUseCase`.
2. **Nenhuma ferramenta soma o que o razão já sabe somar.** Todo agregado vem de
   `IEntryRepository`, nunca de `items.sumOf { }`.

---

## Números

| | |
|---|---|
| Ferramentas | **55**, em 4 famílias |
| Use cases já públicos (`api`) | **17** |
| Use cases a promover de `impl` → `api` | **35** |
| Use cases a criar | **7** |
| Sobrecargas por id a escrever | **14** (aditivas — nenhum chamador muda) |
| Ferramentas visíveis com permissão só-leitura | **19** |

---

## Como ler as tabelas

| Marca | Significado |
|---|---|
| ✔ | o use case já está na `api` da feature — consumir direto |
| ⬆ | existe no `impl`, precisa ser promovido (interface na `api`, `Impl` no `impl` — o padrão que `ArchiveAccountUseCase` já usa) |
| ✚ | não existe; precisa nascer no dono, e o ViewModel passa a usá-lo também |

**Padrão de resolução.** A forma canônica de todo use case é **por id** — veja a convenção
abaixo. Uma resolução que falha é uma recusa que **nomeia o que não achou**, nunca um erro
genérico.

> As assinaturas citadas nas tabelas são as que **estão no disco hoje**, verificadas uma a
> uma — várias recebem o agregado. A convenção acima as substitui pela forma por id; a
> sobrecarga por agregado permanece para os chamadores atuais.

---

## Convenção — o id é a forma canônica

Hoje os dois padrões coexistem sem princípio: sete use cases recebem id, catorze recebem o
agregado, e `PayInvoicePaymentUseCase(invoiceId: Long, …, account: Account)` recebe **os
dois na mesma assinatura**. Não há regra a corrigir; há uma a escolher.

**A regra:** o id carrega a implementação; o agregado é uma sobrecarga de uma linha que
delega.

```kotlin
interface ArchiveAccountUseCase {
    suspend operator fun invoke(accountId: Long): Either<Throwable, Unit>

    /** A mesma operação identificada pelo que o chamador já tem em mãos. */
    suspend operator fun invoke(account: Account) = invoke(account.id)
}
```

Três razões:

1. **Nenhum chamador existente muda.** A mudança é aditiva — os 24 pontos de produção e os
   testes deles continuam compilando. O trabalho é escrever 14 sobrecargas, não converter
   14 use cases e seus consumidores.
2. **Revalidar contra o estado atual deixa de ser custo.** Um agregado carregado por uma tela
   às 14:03 e usado numa ação às 14:07 é uma leitura com validade vencida. Com uma segunda
   porta de escrita no mesmo processo, reler no instante da ação é correção, não overhead.
3. **O padrão já existe no repositório, com justificativa escrita.**
   `IEntryRepository.accountBalanceUpTo(accountId, target: YearMonth)` delega para a forma
   por dia sob o KDoc *"Not another number, so not another implementation."* — e
   `PayInvoiceUseCase` e `OpenInvoiceUseCase` já têm as duas sobrecargas.

**Onde há lote, a forma é plural.** `CalculateAvailableLimitUseCase(creditCard)` chamado num
laço sobre uma lista de cartões produz N+1 consultas. A resposta não é receber o agregado — é
receber a coleção de ids e devolver um mapa, como `IEntryRepository.owedByDimensionByCurrency`
já faz sob a regra *"N invoices custam uma leitura, não N"*.

### `ConfirmRecurringUseCase` não é exceção

É o único use case do repositório com defaults derivados do agregado — cinco deles
(`amount`, `account`, `creditCard`, `title`, `category`), que só compilam porque `recurring`
é o primeiro parâmetro. Parecia forçar a delegação na direção inversa.

Não força: **nenhum é exercido.** O único chamador de produção
(`ConfirmRecurringViewModel:206-219`) passa os oito argumentos explicitamente. O de `amount`
é redundante — o ViewModel já faz `?: recurring.amount` antes de chamar. E o de `title` é
ativamente perigoso, segundo o comentário do próprio chamador:

> *"Blank is an absence, not the template's title… Falling back to the template here would
> hand the user a name they had just erased."*

O default existe na assinatura e o único chamador o contorna de propósito. Um segundo
chamador que confiasse nele — o MCP — reintroduziria esse bug. Os cinco saem junto com a
conversão.

**Permissão: Ler.** O app calcula; o agente recebe o número pronto. Nenhuma dessas
respostas é derivável de uma lista sem erro.

| Ferramenta | Entrada | Decide | Adapta / traz |
|---|---|---|---|
| `get_balance` | `month?`, `account?`, `exclude_accounts?` | ✔ `CalculateBalanceUseCase` — `invoke(target, excluded)` para o geral, `forAccount(id, target)` para uma conta | consolida via ✔ `ConsolidateMoneyUseCase`; resolve nomes. **Traz:** total consolidado, o mapa por moeda, e a data da taxa usada |
| `get_month_summary` | `month` | `IEntryRepository.assetMonthFlowsByCurrency` | **Traz:** receita, despesa, ajuste e rendimento — e a nota de que transferência e pagamento de fatura estão fora, porque o razão já os exclui |
| `get_category_spending` | `month` | ✔ `CalculateCategorySpendingUseCase(forYearMonth)` | ordena, resolve nomes. **Traz:** valor e participação (%) por categoria |
| `get_category_income` | `month` | ✔ `CalculateCategoryIncomeUseCase` | idem |
| `get_spending_breakdown` | `month`, `nature` | `IEntryRepository.totalsByDimensionInMonthByCurrency` | traduz dimensão → categoria; a chave `null` vira **"sem categoria"** explícito. **Traz:** total, participação, e o não-classificado como linha própria |
| `get_budget_progress` | `month?` | ✔ `CalculateBudgetProgressUseCase(budgets, recurringList, transactions, month)` | **compõe**: carrega as três listas antes de chamar. **Traz:** limite, gasto, restante e % por orçamento |
| `get_pending_recurring` | `month?` | ✔ `GetPendingRecurringUseCase` | resolve conta/cartão/categoria. **Traz:** pendentes + total previsto do mês |
| `get_card_overview` | `card?` | ⬆ `CalculateAvailableLimitUseCase(creditCard)` + ⬆ `CalculateInvoiceUseCase(invoice)` | resolve id → cartão. **Traz:** limite, usado, disponível, fatura aberta e o devido |
| `get_report_stats` | `scope`, `from`, `to` | ⬆ `CalculateReportStatsUseCase` sobre `IEntryRepository.scopeStatsByCurrency` | resolve o escopo em ids de conta. **Traz:** receita, despesa, saldo e saldo inicial do período |

---

## Família 2 — Catálogo

**Permissão: Ler.** Como o agente descobre ids e nomes. Toda listagem carrega o agregado
correspondente, do razão.

| Ferramenta | Entrada | Decide | Adapta / traz |
|---|---|---|---|
| `list_transactions` | `month?` \| `from`+`to`, `account?`, `category?`, `card?`, `nature?`, `limit`, `offset` | `ITransactionRepository.observeTransactionsBy` | mapper irmão de `toTransactionUi` — perspectiva, rótulo derivado, ponta que denomina. **Traz:** `summary` vindo do razão, `matching` e `returned` |
| `get_transaction` | `id` | `getTransactionById` + `IEntryRepository.getEntriesByTransaction` | **Traz:** todas as pernas monetárias, a natureza derivada, o vínculo com parcelamento/recorrência e a taxa praticada quando as pernas cruzam moedas |
| `list_accounts` | `include_archived?` | `IAccountRepository.getAllAccounts` / `…IncludingClosed` | saldo por conta via ✔ `CalculateBalanceUseCase.forAccount`. **Traz:** saldo de cada uma e o total consolidado |
| `list_categories` | `type?`, `include_archived?` | `ICategoryRepository` | **Traz:** opcionalmente o gasto do mês por categoria |
| `list_cards` | `include_archived?` | `ICreditCardRepository` | ⬆ `CalculateAvailableLimitUseCase`. **Traz:** limite, usado, disponível |
| `list_invoices` | `card?`, `status?` | `IInvoiceRepository` | devido em lote por `IEntryRepository.owedByDimensionByCurrency` — uma consulta, não N. **Traz:** devido por fatura e o total |
| `get_invoice` | `id` | `getInvoiceById` + entries da dimensão | **Traz:** a janela (abertura/fechamento/vencimento), o status, o extrato e o devido |
| `list_installments` | `card?` | `IInstallmentRepository.getAllInstallments` | resolve as transações do grupo. **Traz:** pagas, restantes e valor da parcela |
| `list_budgets` | — | `IBudgetRepository.getAllBudgets` | junta com o progresso. **Traz:** limite, gasto e % |
| `list_recurring` | `include_archived?` | `IRecurringRepository.observeAllRecurring` | resolve conta/cartão/categoria. **Traz:** próxima ocorrência e se está pendente |

---

## Família 3 — Registro

**Permissão: Registrar e editar** — exceto as quatro linhas marcadas, que ficam sob
**Apagar**, eixo separado.

| Ferramenta | Entrada | Decide | Adapta / traz |
|---|---|---|---|
| `create_transaction` | `type`, `amount`, `title?`, `date`, `category?`, `account?` \| `card?`, `invoice_month?`, `installments?` | ✚ **`RegisterTransactionUseCase`** — despacha para ✔ `AddInstallmentUseCase`, ✔ `StartRecurringFromTransactionUseCase` ou ✔ `BuildTransactionUseCase` + `createTransaction` | monta `TransactionForm` a partir de nomes/ids. **Traz:** o que foi criado — uma transação ou as N do parcelamento |
| `update_transaction` | `id`, campos | ✔ `ValidateTransactionFormUseCase` + `ITransactionRepository.updateTransaction` | **só quando editável** (uma perna monetária). Transferência e pagamento de fatura têm duas — a recusa diz isso |
| `delete_transaction` *(Apagar)* | `id` | ✔ `DeleteTransactionUseCase(transaction)` | resolve `id → Transaction` |
| `create_account` | `name`, `currency`, `icon?`, `is_default?`, `yields_interest?` | ⬆ `CreateAccountUseCase` | a moeda não tem padrão, por desenho — a ferramenta também não inventa uma |
| `update_account` | `id`, campos | ⬆ `UpdateAccountUseCase(accountId, update)` | o patch vira a lambda `(Account) -> Account` |
| `delete_account` *(Apagar)* | `id` | ✔ `DeleteAccountUseCase(account)` | recusa quando há lançamento, **nomeando `archive_entity`** |
| `create_category` | `name`, `type`, `icon?` | ✚ **`CreateCategoryUseCase`** — extraído de `CategoryFormViewModel:141` | hoje validação, `trim()` e `createdAt` vivem no ViewModel |
| `update_category` | `id`, `name?`, `icon?` | ✚ **`UpdateCategoryUseCase`** — extraído de `CategoryFormViewModel:130` | |
| `delete_category` *(Apagar)* | `id` | ⬆ `DeleteCategoryUseCase` — já consulta ⬆ `ResolveCategoryRetirabilityUseCase` | a recusa distingue os três motivos: tem lançamento, tem orçamento, tem recorrência |
| `create_card` | `name`, `limit`, `closing_day`, `due_day`, `currency`, `icon?` | ⬆ `AddCreditCardUseCase(form, currency)` | monta `CreditCardForm` |
| `update_card` | `id`, campos | ⬆ `UpdateCreditCardUseCase` | |
| `delete_card` *(Apagar)* | `id` | ⬆ `DeleteCreditCardUseCase` | |
| `create_budget` | `title`, `categories`, `amount`, `currency`, `limit_type?`, `percentage?` | ✚ **`CreateBudgetUseCase`** — extraído de `BudgetFormViewModel` | |
| `update_budget` | `id`, campos | ✚ **`UpdateBudgetUseCase`** | |
| `delete_budget` *(Apagar)* | `id` | ✚ **`DeleteBudgetUseCase`** — extraído de `DeleteBudgetViewModel` | |
| `create_recurring` | `type`, `amount`, `title?`, `day_of_month`, `category?`, `account?` \| `card?` | ⬆ `SaveRecurringUseCase(id = 0, …)` | |
| `update_recurring` | `id`, campos | ⬆ `SaveRecurringUseCase(id, …)` | mesmo use case, id não-zero |
| `delete_recurring` | `id` | ⬆ `DeleteRecurringUseCase` | consulta ⬆ `ResolveRecurringRetirabilityUseCase` |
| `create_installment` | `card`, `amount`, `count`, `date`, `category?`, `title?` | ✔ `AddInstallmentUseCase(form, installments)` | as N transações são all-or-nothing, garantia do repositório |
| `update_installment` | `id`, `count`, `total_amount` | ✚ **`UpdateInstallmentUseCase`** — hoje só `IInstallmentRepository.updateInstallment`, sem use case | |
| `delete_installment` | `id` | ✔ `DeleteInstallmentUseCase` | |
| `create_invoice` | `card`, `due_month` | ✔ `GetOrCreateInvoiceForMonthUseCase` ou ⬆ `CreateInvoiceUseCase(creditCard, dueMonth)` | recusa duplicata de `dueMonth` |
| `delete_invoice` | `id` | ⬆ `DeleteFutureInvoiceUseCase` | **só a futura** (`status.isDeletable`); a recusa diz por quê |

---

## Família 4 — Operações

**Permissão: Operar.** Movem dinheiro ou mudam ciclo de vida. É o eixo que o CRUD não
alcança — e onde está quase tudo que se faz num dia normal.

| Ferramenta | Entrada | Decide | Adapta / traz |
|---|---|---|---|
| `pay_invoice` | `id`, `date`, `from_account`, `paid_amount?` | ⬆ **`PayInvoicePaymentUseCase`** — ⚠️ **não** `PayInvoiceUseCase`, veja a armadilha 3 | resolve a conta pagadora; `paid_amount` só quando as moedas divergem |
| `advance_invoice_payment` | `id`, `amount`, `date`, `account`, `paid_amount?` | ⬆ `AdvanceInvoicePaymentUseCase` | pagamento parcial/antecipado |
| `close_invoice` | `id`, `date` | ⬆ `CloseInvoiceUseCase(invoiceId, closedAt)` | |
| `open_invoice` | `card`, `opening_month` | ⬆ `OpenInvoiceUseCase(creditCardId, openingMonth)` | |
| `reopen_invoice` | `id` | ⬆ `ReopenInvoiceUseCase(invoiceId)` | recusa se já aberta |
| `adjust_invoice` | `id`, `target`, `date` | ⬆ `AdjustInvoiceUseCase(invoice, target, adjustmentDate)` | lança o ajuste; não edita um campo |
| `adjust_balance` | `account`, `target_balance`, `date` | ⬆ `AdjustBalanceUseCase(targetBalance, adjustmentDate, account)` | idem — a diferença vira lançamento |
| `transfer` | `from`, `to`, `amount`, `date`, `destination_amount?` | ⬆ `TransferBetweenAccountsUseCase` | `destination_amount` quando cruza moedas; a taxa é colhida, nunca informada |
| `set_default_account` | `id` | ⬆ `SetDefaultAccountUseCase` | |
| `confirm_recurring` | `id`, `date`, `amount?`, `title?`, `category?` | ⬆ `ConfirmRecurringUseCase(recurring, date, amount, target)` | os opcionais são a edição no ato de confirmar |
| `skip_recurring` | `id`, `date` | ⬆ `SkipRecurringUseCase(recurring, date)` | |
| `archive_entity` | `type`, `id` | ✔ `ArchiveAccountUseCase` · ⬆ `ArchiveCreditCardUseCase` · ⬆ `ArchiveCategoryUseCase` · ⬆ `ArchiveRecurringUseCase` | única ferramenta genérica do conjunto — arquivar é literalmente a mesma operação em quatro entidades |
| `unarchive_entity` | `type`, `id` | ⬆ `UnarchiveAccountUseCase` · ⬆ `UnarchiveCreditCardUseCase` · ⬆ `UnarchiveCategoryUseCase` · ⬆ `UnarchiveRecurringUseCase` | |

---

## Permissões

Os quatro eixos **são** as quatro famílias. A permissão não é um `if` no começo de cada
ferramenta: ela decide **quais ferramentas existem** no `tools/list`.

| Eixo | Famílias | Ferramentas |
|---|---|---|
| Ler | 1 + 2 | 19 |
| Registrar e editar | 3, menos os apagar | 19 |
| Apagar | as 4 marcadas *(Apagar)* | 4 |
| Operar | 4 | 13 |

Um agente com só-leitura vê 19 ferramentas e não sabe que as outras 36 existem — não tenta,
não erra, não gasta contexto. `ServerCapabilities.Tools(listChanged = true)` já existe no SDK
Kotlin, então mexer no interruptor notifica o agente na hora.

---

## Armadilhas

### 1. O total não pode ser a soma da página

```
summary.expense = items.sumOf { it.amount }        ✖  a página tem 50 de 127
summary.expense = assetMonthFlowsByCurrency(mês)   ✔
```

Um agente não rola a tela para conferir. Se o total puder discordar do razão, ele vai
reportar o total. `matching` e `returned` existem para que ele saiba que há mais.

Isto não é regra nova: `presentation-mapping` já exige que *"um total exibido no cabeçalho e
a lista imediatamente abaixo dele MUST NOT discordar sobre o que pertence àquele total"*.

### 2. A mesma ferramenta muda de vocabulário conforme o filtro

| Chamada | Perspectiva | Vocabulário |
|---|---|---|
| `list_transactions(month=julho)` | ausente | **natureza** — transferência é `transfer`, nunca despesa |
| `list_transactions(month=julho, account=3)` | aquela conta | **direção** — a mesma transferência é saída |

Ignorar isso põe transferências entre contas próprias na lista de despesas, e o agente conclui
que se gastou o dobro. A spec proíbe literalmente (*"Transferência não é listada como
despesa"*), e a regra tem dono: `deriveTransactionLabel` e `TransactionPerspective`.

### 3. Dois use cases quase homônimos, e só um move dinheiro

```
PayInvoiceUseCase(invoiceId, paidAt)
  └─ valida as datas e grava status = PAID.  NÃO LANÇA NADA.

PayInvoicePaymentUseCase(invoiceId, date, account, paidAmount?)
  └─ cria a transação de pagamento E chama PayInvoiceUseCase.
```

`pay_invoice` chama o **segundo**. Chamar o primeiro marca a fatura como paga sem o dinheiro
sair da conta — o saldo passa a mentir, e nada falha. Este é o motivo concreto de a superfície
existir como documento: o nome do use case não protege quem escolhe errado.

### 4. Toda figura que cruza contas é `MoneyByCurrency`

Um payload com `{"amount": 1234.56}` sem moeda destrói o invariante central do razão. Um com
`{"BRL": 1000, "USD": 43.21}` é honesto e o agente soma `1043` na frase seguinte. O payload
carrega **o consolidado, o detalhe por moeda e a data da taxa** — e `ConsolidateMoneyUseCase`
é quem reduz, nunca a ferramenta.

---

## O que precisa nascer

Sete use cases. Cada um sai de um ViewModel, e o ViewModel passa a consumi-lo — senão nascem
duas verdades sobre a mesma operação.

| Use case | Sai de | Por quê |
|---|---|---|
| `RegisterTransactionUseCase` | `AddTransactionViewModel:299-340` | o `if` que decide se um formulário vira parcelamento, recorrência ou transação simples é hoje a única cópia dessa regra |
| `CreateCategoryUseCase` | `CategoryFormViewModel:141` | validação, `trim()` e `createdAt` estão no ViewModel |
| `UpdateCategoryUseCase` | `CategoryFormViewModel:130` | idem |
| `CreateBudgetUseCase` | `BudgetFormViewModel` | escreve direto no repositório |
| `UpdateBudgetUseCase` | `BudgetFormViewModel` | idem |
| `DeleteBudgetUseCase` | `DeleteBudgetViewModel` | idem |
| `UpdateInstallmentUseCase` | — | só existe `IInstallmentRepository.updateInstallment` |

Compare `CategoryFormViewModel.submit()` com `CreateAccountUseCase.invoke()`: os dois fazem a
mesma sequência — validar, `trim`, `createdAt`, inserir. Um a faz na UI, o outro no domínio.
O segundo é o que o MCP consegue chamar.

---

## O que fica de fora

- **Dirigir a UI.** Navegar, abrir modal, clicar. Escopo é dado.
- **Ler o estado da tela.** Expor `UiState` congelaria a UI como contrato.
- **Android e iOS.** Servidor local é coisa de desktop.
- **Idempotência.** Um agente que perde a resposta e repete a chamada duplica o lançamento.
  Reconhecido, adiado — melhoria futura.
- **Taxas de câmbio e moeda base.** Configuração do app, não superfície de agente.
