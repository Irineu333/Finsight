# Superfície MCP do Finsight

> Guia de leitura, não contrato: quem manda é o código. Descreve as ferramentas que o servidor
> MCP local do app desktop expõe, e — para cada uma — **quem decide a regra**, **o que a
> ferramenta adapta** e **qual agregado o payload carrega**.
>
> Nasceu como exploração, levantado contra `adb85e2e3` (main), e foi **reconciliado contra a
> implementação** ao fechar a change `add-local-mcp-server`: onde documento e código divergiam,
> o código venceu. As entradas, os use cases e as contagens abaixo são os que estão no disco
> hoje. A superfície em si é fechada no código — `McpToolName` e `McpSurface` —, e é lá, não
> aqui, que uma ferramenta passa a existir.

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
| Ferramentas | **56**, em 4 famílias |
| Por eixo | ler **20** · registrar e editar **15** · apagar **8** · operar **13** |
| Use cases que passaram a estar na `api` de uma feature | **42** — **34** promovidos de `impl` e **8** criados |
| Ferramentas visíveis com permissão só-leitura | **20** |

> As contagens por eixo não são mantidas à mão em lugar nenhum: `McpToolName` declara o eixo de
> cada ferramenta, `McpSurface.toolCountByAxis` deriva os números, e é ele que a tela de
> configurações exibe e que `McpSurfaceIsClosedTest` confere.

---

## Como ler as tabelas

| Marca | Significado |
|---|---|
| ✔ | o use case já estava na `api` da feature antes desta mudança |
| ⬆ | estava no `impl` e **foi promovido** (interface na `api`, `Impl` no `impl` — o padrão que `ArchiveAccountUseCase` já usava) |
| ✚ | **nasceu** nesta mudança, no dono, e o ViewModel passou a consumi-lo no mesmo passo |

As três marcas dizem de onde o use case veio; hoje **todos** estão na `api` da sua feature, que
é a única coisa que o servidor consegue alcançar.

**Padrão de resolução.** A forma canônica de todo use case é **por id** — veja a convenção
abaixo. Uma resolução que falha é uma recusa que **nomeia o que não achou**, nunca um erro
genérico.

> As assinaturas citadas nas tabelas são as que **estão no disco hoje**: a forma por id carrega a
> implementação e a sobrecarga por agregado delega nela. Os parâmetros das ferramentas também são
> os do `inputSchema` real — identidades, sempre com sufixo `_id`, nunca fachadas.

---

## Convenção — o id é a forma canônica

Antes desta mudança os dois padrões coexistiam sem princípio: uns use cases recebiam id, outros
recebiam o agregado, e `PayInvoicePaymentUseCase(invoiceId: Long, …, account: Account)` recebia
**os dois na mesma assinatura**. Não havia regra a corrigir; havia uma a escolher.

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

1. **Nenhum chamador existente mudou.** A mudança é aditiva — os pontos de produção e os
   testes deles continuaram compilando. O trabalho foi escrever sobrecargas, não converter
   use cases e seus consumidores.
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
já faz sob a regra *"N invoices custam uma leitura, não N"*. Foi o que a mudança escreveu:
`invoke(creditCardIds: Collection<Long>): Map<Long, Limit>` carrega a implementação, e as formas
por um id e por agregado delegam nela.

### `ConfirmRecurringUseCase` não é exceção

Era o único use case do repositório com defaults derivados do agregado — **seis** deles
(`amount`, `target`, `account`, `creditCard`, `title`, `category`), que só compilavam porque
`recurring` era o primeiro parâmetro. Parecia forçar a delegação na direção inversa.

Não forçou: **nenhum era exercido.** O único chamador de produção
(`ConfirmRecurringViewModel:206-219`) passava os oito argumentos explicitamente. O de `amount`
é redundante — o ViewModel já faz `?: recurring.amount` antes de chamar. E o de `title` é
ativamente perigoso, segundo o comentário do próprio chamador:

> *"Blank is an absence, not the template's title… Falling back to the template here would
> hand the user a name they had just erased."*

O default existia na assinatura e o único chamador o contornava de propósito. Um segundo
chamador que confiasse nele — o MCP — reintroduziria esse bug. Os seis saíram junto com a
conversão; os parâmetros continuam opcionais, mas o que falta agora significa *nada*, e não
"o que o template diz". Quem precisa do valor do template o lê e o passa: é o que
`confirm_recurring` faz com `title` e `category_id`, para não gravar um ciclo sem nome.

---

## Família 1 — Perguntas

**Permissão: Ler.** O app calcula; o agente recebe o número pronto. Nenhuma dessas
respostas é derivável de uma lista sem erro.

| Ferramenta | Entrada | Decide | Adapta / traz |
|---|---|---|---|
| `get_balance` | `month?`, `account_id?`, `exclude_account_ids?` | ✔ `CalculateBalanceUseCase` — `invoke(target, excludedAccountIds)` para o geral, `forAccount(accountId, target)` para uma conta | consolida via ✔ `ConsolidateMoneyUseCase`; resolve nomes. **Traz:** total consolidado, o mapa por moeda, e a data da taxa usada. Com `account_id`, a figura é **exata** e na moeda da conta — não passa pela consolidação |
| `get_month_summary` | `month?`, `compare_to?` | `IEntryRepository.assetMonthFlowsByCurrency` + `liabilityMonthFlowsByCurrency`, e `netBalanceUpTo` nas duas pontas do mês | **Traz:** receita, despesa, ajuste e rendimento, a posição de abertura e a de fechamento — e a nota de que transferência e pagamento de fatura estão fora, porque o razão já os exclui. Com `compare_to`, traz a variação **já calculada** e marca qual dos períodos ainda está em andamento |
| `get_category_spending` | `month?` | ✔ `CalculateCategorySpendingUseCase` | ordena, resolve nomes. **Traz:** valor e participação (%) por categoria, e o sem-categoria como linha própria |
| `get_category_income` | `month?` | ✔ `CalculateCategoryIncomeUseCase` | idem |
| `get_spending_breakdown` | `month?`, `nature?` | ✔ `CalculateCategorySpendingUseCase` + ✔ `CalculateCategoryIncomeUseCase` — o ranking e a participação são deles, não da ferramenta | lê `IEntryRepository.totalsByDimensionInMonthByCurrency` só para a decomposição por moeda, que a figura do domínio já não carrega. **Traz:** os dois lados de uma vez e o **líquido** entre eles; com `nature`, um lado só e `net` nulo |
| `get_budget_progress` | `month?` | ✔ `CalculateBudgetProgressUseCase(budgets, recurringList, transactions, month)` | **compõe**: carrega as três listas antes de chamar. **Traz:** limite, gasto, restante e % por orçamento |
| `get_pending_recurring` | `as_of?` | ✔ `GetPendingRecurringUseCase` | resolve conta/cartão/categoria. **Traz:** pendentes + total previsto |
| `get_card_overview` | `card_id?`, `include_archived?` | ⬆ `CalculateAvailableLimitUseCase` na forma plural + ⬆ `CalculateInvoiceUseCase` | resolve id → cartão. **Traz:** limite, usado, disponível, fatura aberta e o devido, mais o que segura o limite **repartido pelo ciclo que o segura** — `open_total`, `closed_total`, `future_total` e a soma exata dos três em `committed_total`. A repartição existe porque um total só não distingue o que **vence** do que está apenas **comprometido**: uma parcela segura limite desde a compra, e lida como dívida de hoje ela superestima. Responde por **cartões**; `list_invoices` responde por faturas |
| `get_report_stats` | `from`, `to`, `account_ids?`, `card_id?` | ⬆ `CalculateReportStatsUseCase` sobre `IEntryRepository.scopeStatsByCurrency` | o perímetro é um conjunto de contas **ou** um cartão — não há parâmetro `scope`, ele é derivado de qual dos dois veio. **Traz:** receita, despesa, saldo e saldo inicial do período |
| `get_net_worth` | `month?` | ✚ leitura por natureza no `IEntryRepository`: `netWorthByCurrency()` sem mês, e `naturalBalanceUpToByCurrency(mês, ASSET) + (mês, LIABILITY)` com ele | ASSET **mais** LIABILITY, não menos: o passivo é guardado a crédito, então `MoneyByCurrency.plus` é a regra inteira e tem um dono. **Existe porque a simulação provou faltar:** `get_balance` soma só as contas, e as duas figuras são indistinguíveis pelo valor |

---

## Família 2 — Catálogo

**Permissão: Ler.** Como o agente descobre ids e nomes. Toda listagem carrega o agregado
correspondente, do razão.

| Ferramenta | Entrada | Decide | Adapta / traz |
|---|---|---|---|
| `list_transactions` | `month?`, `account_id?` \| `card_id?`, `category_id?`, `nature?`, `order_by?`, `limit?`, `offset?` | `ITransactionRepository.getTransactionsBetween(primeiro e último dia do mês)` — o mês é corte de SQL (`TransactionDao.getBetween`, inclusivo nas duas pontas) e as pernas vêm numa leitura em lote, não uma query por linha; `nature` e o corte por dimensão continuam em memória, porque são derivados, mas rodam sobre o mês e não sobre o histórico. Os totais vêm de três leituras do razão, uma por filtro: `totalsByDimensionByCurrency` sob uma categoria, `scopeStatsByCurrency` sob uma conta ou cartão, `assetMonthFlows + liabilityMonthFlows` sem perspectiva | mapper irmão de `toTransactionUi` — perspectiva, rótulo derivado, ponta que denomina; nenhum deles re-derivado. **O período é o mês**, não um intervalo livre: é o recorte que os agregados do razão têm. `account_id` e `card_id` juntos são recusados. **Traz:** `totals` vindo do razão, `matching`, `returned`, `has_more` e `narrowed_by` |
| `get_transaction` | `id` | `getTransactionById` — a `Transaction` já carrega as suas `Entry`, então não há segunda leitura do razão | **Traz:** todas as pernas monetárias assinadas como o razão as gravou, a natureza derivada, o vínculo com parcelamento/recorrência e a taxa **praticada pela própria operação** quando as pernas cruzam moedas — nunca uma taxa do acervo |
| `list_accounts` | `month?`, `include_archived?` | `IAccountRepository.getAllAccounts` / `…IncludingClosed` | saldo por conta via ✔ `CalculateBalanceUseCase.forAccount`. **Traz:** saldo de cada uma e o total do razão sobre **exatamente** as contas listadas |
| `list_categories` | `month?`, `type?`, `include_archived?` | `ICategoryRepository` | **Traz:** o gasto do mês por categoria |
| `list_cards` | `include_archived?` | `ICreditCardRepository` | ⬆ `CalculateAvailableLimitUseCase` na forma plural — uma leitura, não N. **Traz:** limite, usado, disponível. `used` é tudo que segura o limite, faturas ainda não abertas incluídas — não é o devido de hoje, e a descrição diz isso e manda a `get_card_overview` para separar |
| `list_invoices` | `card_id?`, `status?`, `limit?`, `offset?`, `include_archived_cards?` | `IInvoiceRepository` | devido em lote por `IEntryRepository.owedByDimensionByCurrency` — uma consulta, não N. **Traz:** devido por fatura e o total de **todas** as que casam, não só as da página |
| `get_invoice` | `id`, `order_by?`, `limit?`, `offset?` | `getInvoiceById` + entries da dimensão | **Traz:** a janela (abertura/fechamento/vencimento), o status, o extrato paginado e o devido |
| `list_installments` | `card_id?`, `status?` | `IInstallmentRepository.getAllInstallments` | resolve as transações do grupo. **Traz:** pagas, restantes e valor da parcela |
| `list_budgets` | — | `IBudgetRepository.getAllBudgets` | **não traz gasto nem progresso**, ao contrário do que este documento supunha: isso é pergunta sobre um mês, e `get_budget_progress` a responde. **Traz:** o catálogo — limite, tipo de limite e as categorias que cada um observa |
| `list_recurring` | `as_of?`, `include_archived?` | `IRecurringRepository.observeAllRecurring` | resolve conta/cartão/categoria. **Traz:** próxima ocorrência e se está pendente |

---

## Família 3 — Registro

**Permissão: Registrar e editar** — exceto as **oito** linhas marcadas, que ficam sob
**Apagar**, eixo separado. Toda `delete_*` cai nele: `mcp-permissions` define "apagar" como
*remover definitivamente*, sem qualificar entidade, e enumera em "registrar e editar" apenas
*criar e alterar*.

| Ferramenta | Entrada | Decide | Adapta / traz |
|---|---|---|---|
| `create_transaction` | `type`, `amount`, `date?`, `title?`, `category_id?`, `account_id?` \| `card_id?`, `invoice_month?`, `installments?`, `is_recurring?` | ✚ **`RegisterTransactionUseCase`** — despacha para ✔ `AddInstallmentUseCase`, ✔ `StartRecurringFromTransactionUseCase` ou ✔ `BuildTransactionUseCase` + `createTransaction` | monta `TransactionForm` a partir de ids. **O despacho é inteiro do use case**: a ferramenta lê `installments > 1` apenas para recusar o que o domínio não modela — parcelas numa conta, parcelas ao lado de `is_recurring` —, nunca para decidir no que um formulário válido vai dar. **Traz:** o que foi criado — uma transação ou as N do parcelamento, e **uma** entrada no registro de atividade |
| `update_transaction` | `id`, `type?`, `amount?`, `date?`, `title?`, `category_id?`, `account_id?`, `card_id?`, `invoice_month?` | ✚ **`UpdateTransactionUseCase`** — nasceu nesta mudança, e `EditTransactionViewModel` passou a consumi-lo | **só quando editável** (uma perna monetária); `Transaction.editObstacle` é o dono da derivação e a recusa chega nas palavras do domínio. Editar o valor para algo que não seja **maior que zero** — zero ou negativo — é recusado, nomeando `delete_transaction` |
| `delete_transaction` *(Apagar)* | `id` | ✔ `DeleteTransactionUseCase(transactionId)` | resolve o lançamento antes de removê-lo, para o registro poder dizer o que era |
| `create_account` | `name`, `currency`, `is_default?`, `yields_interest?` | ⬆ `CreateAccountUseCase` | a moeda não tem padrão, por desenho — a ferramenta também não inventa uma. Ícone não entra na superfície |
| `update_account` | `id`, `name?`, `is_default?`, `yields_interest?` | ⬆ `UpdateAccountUseCase(accountId, update)` | o patch vira a lambda `(Account) -> Account`; a moeda é recusada pelo domínio |
| `delete_account` *(Apagar)* | `id` | ✔ `DeleteAccountUseCase(accountId)` | recusa quando há lançamento, **nomeando `archive_entity`** |
| `create_category` | `name`, `type` | ✚ **`CreateCategoryUseCase`** — extraído de `CategoryFormViewModel` | validação, `trim()` e `createdAt` saíram do ViewModel |
| `update_category` | `id`, `name` | ✚ **`UpdateCategoryUseCase`** — extraído de `CategoryFormViewModel` | renomeia, e só. O tipo é declaração do usuário na criação e não muda aqui — tudo que já foi classificado sob ela foi lido contra aquele eixo |
| `delete_category` *(Apagar)* | `id` | ⬆ `DeleteCategoryUseCase` — consulta ⬆ `ResolveCategoryRetirabilityUseCase` | a recusa distingue os motivos (lançamento, orçamento, recorrência) e `try_instead` sai de `retireActionOf` |
| `create_card` | `name`, `limit`, `closing_day`, `due_day`, `currency` | ⬆ `AddCreditCardUseCase(form, currency)` | monta `CreditCardForm` |
| `update_card` | `id`, `name?`, `limit?`, `closing_day?`, `due_day?` | ⬆ `UpdateCreditCardUseCase` | a moeda é **indizível**: um cartão não a declara |
| `delete_card` *(Apagar)* | `id` | ⬆ `DeleteCreditCardUseCase` | |
| `create_budget` | `title`, `category_ids`, `currency`, `limit_type?`, `amount?`, `percentage?`, `base_recurring_id?` | ✚ **`CreateBudgetUseCase`** — extraído de `BudgetFormViewModel` | |
| `update_budget` | `id`, `title?`, `category_ids?`, `limit_type?`, `amount?`, `percentage?`, `base_recurring_id?` | ✚ **`UpdateBudgetUseCase`** | a moeda fica fora do `update` |
| `delete_budget` *(Apagar)* | `id` | ✚ **`DeleteBudgetUseCase`** — extraído de `DeleteBudgetViewModel` | |
| `create_recurring` | `type`, `amount`, `day_of_month`, `title?`, `category_id?`, `account_id?` \| `card_id?` | ⬆ `SaveRecurringUseCase(id = 0, …)` | passou a devolver o `Recurring` gravado — sem isso não haveria o que responder nem o que referenciar no registro |
| `update_recurring` | `id`, `type?`, `amount?`, `day_of_month?`, `title?`, `category_id?`, `account_id?`, `card_id?` | ⬆ `SaveRecurringUseCase(id, …)` | mesmo use case, id não-zero |
| `delete_recurring` *(Apagar)* | `id` | ⬆ `DeleteRecurringUseCase` | consulta ⬆ `ResolveRecurringRetirabilityUseCase` |
| `create_installment` | `card_id`, `amount`, `count`, `date?`, `title?`, `category_id?`, `invoice_month?` | ✔ `AddInstallmentUseCase(form, installments)` | as N transações são all-or-nothing, garantia do repositório |
| `update_installment` | `id`, `count?`, `total_amount?` | ✚ **`UpdateInstallmentUseCase`** — antes só existia `IInstallmentRepository.updateInstallment` | |
| `delete_installment` *(Apagar)* | `id` | ✔ `DeleteInstallmentUseCase` | declara junto o que sumiu com o plano |
| `create_invoice` | `card_id`, `due_month` | ⬆ `CreateInvoiceUseCase(creditCardId, dueMonth)` — **não** `GetOrCreateInvoiceForMonthUseCase` | recusa duplicata de `dueMonth`, e nunca produz uma fatura aberta: isso é `open_invoice` |
| `delete_invoice` *(Apagar)* | `id` | ⬆ `DeleteFutureInvoiceUseCase` | **só futura ou retroativa** (`status.isDeletable`); a recusa diz por quê |

---

## Família 4 — Operações

**Permissão: Operar.** Movem dinheiro ou mudam ciclo de vida. É o eixo que o CRUD não
alcança — e onde está quase tudo que se faz num dia normal.

| Ferramenta | Entrada | Decide | Adapta / traz |
|---|---|---|---|
| `pay_invoice` | `id`, `account_id`, `date?`, `paid_amount?` | ⬆ **`PayInvoicePaymentUseCase(invoiceId, date, accountId, paidAmount?)`** — ⚠️ **não** `PayInvoiceUseCase`, veja a armadilha 3 | resolve a conta pagadora; `paid_amount` só quando as moedas divergem. A fatura e o saldo da resposta são **lidos de volta** depois da operação |
| `advance_invoice_payment` | `id`, `amount`, `account_id`, `date?`, `paid_amount?` | ⬆ `AdvanceInvoicePaymentUseCase(invoiceId, amount, date, accountId, paidAmount?)` | pagamento parcial/antecipado; `amount` é na moeda do **cartão** |
| `close_invoice` | `id`, `date?` | ⬆ `CloseInvoiceUseCase(invoiceId, closedAt)` | |
| `open_invoice` | `card_id`, `opening_month` | ⬆ `OpenInvoiceUseCase(creditCardId, openingMonth)` | **promove** a fatura já declarada para o mês em vez de duplicá-la |
| `reopen_invoice` | `id` | ⬆ `ReopenInvoiceUseCase(invoiceId)` | recusa se já aberta |
| `adjust_invoice` | `id`, `target`, `date?` | ⬆ `AdjustInvoiceUseCase(invoiceId, target, adjustmentDate)` | lança o ajuste; não edita um campo. Ajustar para o que já se deve é recusado |
| `adjust_balance` | `account_id`, `target_balance`, `date?` | ⬆ `AdjustBalanceUseCase(targetBalance, adjustmentDate, accountId)` | idem — a diferença vira lançamento |
| `transfer` | `from_account_id`, `to_account_id`, `amount`, `destination_amount?`, `date?` | ⬆ `TransferBetweenAccountsUseCase` | `destination_amount` quando cruza moedas; a taxa é colhida, nunca informada — nenhum parâmetro do schema a menciona |
| `set_default_account` | `id` | ⬆ `SetDefaultAccountUseCase(accountId)` | |
| `confirm_recurring` | `id`, `date?`, `amount?`, `title?`, `category_id?`, `account_id?`, `card_id?`, `invoice_month?` | ⬆ `ConfirmRecurringUseCase(recurringId, date, …)` | os opcionais são a edição no ato de confirmar. **Título e categoria não citados são pré-preenchidos do template pela ferramenta e passados explicitamente** — o use case não os deriva mais do agregado, e repassar `null` gravaria o ciclo sem nome. `title` vazio e `category_id = 0` são as duas formas de apagar |
| `skip_recurring` | `id`, `date?` | ⬆ `SkipRecurringUseCase(recurringId, date)` | |
| `archive_entity` | `type`, `id` | ✔ `ArchiveAccountUseCase` · ⬆ `ArchiveCreditCardUseCase` · ⬆ `ArchiveCategoryUseCase` · ⬆ `ArchiveRecurringUseCase` | única ferramenta genérica do conjunto — arquivar é literalmente a mesma operação em quatro entidades |
| `unarchive_entity` | `type`, `id` | ⬆ `UnarchiveAccountUseCase` · ⬆ `UnarchiveCreditCardUseCase` · ⬆ `UnarchiveCategoryUseCase` · ⬆ `UnarchiveRecurringUseCase` | |

---

## Permissões

Os quatro eixos **são** as quatro famílias. A permissão não é um `if` no começo de cada
ferramenta: ela decide **quais ferramentas existem** no `tools/list`.

| Eixo | Famílias | Ferramentas |
|---|---|---|
| Ler | 1 + 2 | 20 |
| Registrar e editar | 3, menos os apagar | 15 |
| Apagar | as 8 marcadas *(Apagar)* | 8 |
| Operar | 4 | 13 |

Um agente com só-leitura vê 20 ferramentas e não tenta as outras — não erra, não gasta
contexto. `ServerCapabilities.Tools(listChanged = true)` já existe no SDK Kotlin, então mexer no
interruptor notifica o agente na hora.

**Mas ele precisa saber que há algo retido.** Numa simulação com um agente real, sobre um
protótipo com o eixo "apagar" desligado, o pedido *"apaga o último lançamento"* produziu:

> *"Não consegui. Não existe ferramenta de exclusão de lançamento no servidor."*

Falso, dito com confiança ao dono do app, e bloqueando justamente a ação que resolveria — ligar
o eixo. Esconder a ferramenta escondeu também a capacidade. Por isso o handshake da sessão
declara **quais eixos estão retidos e onde concedê-los** — a capacidade, nunca as ferramentas,
que seguem fora da lista. E uma ferramenta chamada pelo nome sem permissão é recusada
distinguindo "não autorizado" de "não existe", com a mesma indicação de onde conceder.

O mesmo agente relatou ter cogitado zerar o valor pela ferramenta de edição para "neutralizar" o
lançamento. Negar o verbo direto sem dizer por quê convida ao verbo torto: editar para zero é
recusado, nomeando `delete_transaction` e o eixo que o autoriza.

---

## Armadilhas

### 1. O total não pode ser a soma da página

```
totals.expense = items.sumOf { it.amount }        ✖  a página tem 50 de 127
totals.expense = assetMonthFlowsByCurrency(mês)   ✔
```

Um agente não rola a tela para conferir. Se o total puder discordar do razão, ele vai
reportar o total. `matching` e `returned` existem para que ele saiba que há mais.

Isto não é regra nova: `presentation-mapping` já exige que *"um total exibido no cabeçalho e
a lista imediatamente abaixo dele MUST NOT discordar sobre o que pertence àquele total"*.

**A consequência que sobrou:** o razão não tem agregado cortado por natureza — não existe
"total de transferências" —, então `nature` **corta a lista e não move os totais**. Somar a
página para preencher é justamente o que a proibição acima veta. O payload declara o
descompasso em vez de escondê-lo: `totals.narrowed_by` nomeia os argumentos que os totais
refletem, `totals.basis` diz de qual leitura do razão vieram, e `perimeter.excludes` e a
descrição repetem. A mitigação é **declarativa, não estrutural** — veja "O que a suíte não
verifica", no fim.

### 2. A mesma ferramenta muda de vocabulário conforme o filtro

| Chamada | Perspectiva | Vocabulário |
|---|---|---|
| `list_transactions(month=julho)` | ausente | **natureza** — transferência é `transfer`, nunca despesa |
| `list_transactions(month=julho, account_id=3)` | aquela conta | **direção** — a mesma transferência é saída |

Ignorar isso põe transferências entre contas próprias na lista de despesas, e o agente conclui
que se gastou o dobro. A spec proíbe literalmente (*"Transferência não é listada como
despesa"*), e a regra tem dono: `deriveTransactionLabel` e `TransactionPerspective`.

### 3. Dois use cases quase homônimos, e só um move dinheiro

```
PayInvoiceUseCase(invoiceId, paidAt)
  └─ valida as datas e grava status = PAID.  NÃO LANÇA NADA.

PayInvoicePaymentUseCase(invoiceId, date, accountId, paidAmount?)
  └─ cria a transação de pagamento E chama PayInvoiceUseCase.
```

`pay_invoice` chama o **segundo**. Chamar o primeiro marca a fatura como paga sem o dinheiro
sair da conta — o saldo passa a mentir, e nada falha. Este é o motivo concreto de a superfície
existir como documento: o nome do use case não protege quem escolhe errado.

A escolha deixou de depender de leitura: `RegistrationToolsGoThroughUseCasesTest` falha se
alguma ferramenta declarar `PayInvoiceUseCase` no construtor, **e** falha se nenhuma declarar
`PayInvoicePaymentUseCase` — sem a segunda metade, a primeira passaria sobre uma superfície que
não paga fatura nenhuma.

### 4. Toda figura que cruza contas é `MoneyByCurrency`

Um payload com `{"amount": 1234.56}` sem moeda destrói o invariante central do razão. Um com
`{"BRL": 1000, "USD": 43.21}` é honesto e o agente soma `1043` na frase seguinte. O payload
carrega **o consolidado, o detalhe por moeda e a data da taxa** — e `ConsolidateMoneyUseCase`
é quem reduz, nunca a ferramenta.

---

## O que nasceu

**Oito** use cases — sete previstos e um que a exploração não viu. Cada um saiu de um ViewModel,
e o ViewModel passou a consumi-lo no mesmo passo; senão nasceriam duas verdades sobre a mesma
operação.

| Use case | Saiu de | Por quê |
|---|---|---|
| `RegisterTransactionUseCase` | `AddTransactionViewModel:299-340` | o `if` que decide se um formulário vira parcelamento, recorrência ou transação simples era a única cópia dessa regra |
| `CreateCategoryUseCase` | `CategoryFormViewModel:141` | validação, `trim()` e `createdAt` estavam no ViewModel |
| `UpdateCategoryUseCase` | `CategoryFormViewModel:130` | idem |
| `CreateBudgetUseCase` | `BudgetFormViewModel` | escrevia direto no repositório |
| `UpdateBudgetUseCase` | `BudgetFormViewModel` | idem |
| `DeleteBudgetUseCase` | `DeleteBudgetViewModel` | idem |
| `UpdateInstallmentUseCase` | — | só existia `IInstallmentRepository.updateInstallment` |
| **`UpdateTransactionUseCase`** | `EditTransactionViewModel` | **não estava previsto.** O ViewModel escrevia direto em `transactionRepository.updateTransaction`, e sem dono `update_transaction` só teria caminhos proibidos: reimplementar a edição ou escrever no repositório. A regra é a forma da reescrita — apagar todas as pernas e reconstruir a partir de **uma** mais o `contra` —, então o que ela não exprime é recusado: mais de uma perna monetária, um ajuste, uma parcela. `Transaction.editObstacle` (`core/model`) é o dono único |

Compare `CategoryFormViewModel.submit()` com `CreateAccountUseCase.invoke()`: os dois fazem a
mesma sequência — validar, `trim`, `createdAt`, inserir. Um a faz na UI, o outro no domínio.
O segundo é o que o MCP consegue chamar.

---

## O que fica de fora

Varrido contra as features do app, não amostrado. O que faltar aqui é omissão, não silêncio.

A lista vive no código, em `McpSurface.exclusions`, e cada linha declara **por que grau** está
fora: `WITHHELD` é o que um requisito proíbe oferecer, `OUT_OF_SCOPE` é o que simplesmente não
foi alcançado. São fatos diferentes, e confundi-los faria uma proibição parecer uma pendência.

| Fica de fora | Grau | Por quê |
|---|---|---|
| **Escrever taxa de câmbio**, e o catálogo de moedas de que as taxas pendem | `WITHHELD` | Reescreve em silêncio **toda** figura consolidada do app, inclusive de meses fechados, sem lançamento que denuncie. O agente lê a taxa aplicada; não a escreve |
| Trocar a **moeda base** | `WITHHELD` | O mesmo estrago pela outra porta: tudo que o app consolida é re-denominado de uma vez, sem nada no razão registrando que mudou |
| **Administrar o próprio servidor** — porta, token, permissões | `WITHHELD` | Um agente que amplia as próprias permissões não tem permissões |
| **Suporte** (`feature/support`) | `OUT_OF_SCOPE` | Única superfície do app que sai da máquina (Firestore) |
| **Configurar, renderizar e exportar relatório** (`feature/report`) | `OUT_OF_SCOPE` | `get_report_stats` dá as figuras; montar documento é artefato visual, não dado |
| **Preferências do dashboard**, incluindo **contas fora do saldo total** | `OUT_OF_SCOPE` | Mudá-la altera o número que o próprio agente lê depois |
| **Lançar rendimento** (`LaunchYieldUseCase`) | `OUT_OF_SCOPE` | A fronteira mais discutível: é lançamento. Fora porque não estava no escopo e a conta que rende tem regra própria |
| **Ícones** de conta, cartão e categoria | `OUT_OF_SCOPE` | O que o agente cria nasce com o padrão; catálogo visual não traduz para JSON |
| **Categorias padrão** (`CreateDefaultCategoriesUseCase`) | `OUT_OF_SCOPE` | Semeadura de primeira execução, não operação de usuário |
| **Autenticação** (`core:auth`) | `OUT_OF_SCOPE` | O servidor herda a sessão do app; não a gerencia |
| **Telemetria** e **estado da janela** | `OUT_OF_SCOPE` | Não são dados do usuário |
| **Dirigir a UI** e **ler estado de tela** | `OUT_OF_SCOPE` | Expor `UiState` congelaria a UI como contrato |
| **Android e iOS** | `OUT_OF_SCOPE` | Servidor local precisa de um processo que possua um socket, e isso é o desktop |
| **Idempotência de escrita** | `OUT_OF_SCOPE` | Repetir uma chamada perdida duplica o lançamento. Reconhecido, não resolvido — por isso o registro de atividade põe as duas lado a lado em vez de esconder uma |
| **Arquivar orçamento e parcelamento** | não é retenção | O app inteiro não arquiva nenhum dos dois — não existe `ArchiveBudgetUseCase` nem `isArchived` em `Budget` —, então não há capacidade a declarar. `archive_entity` recusa `type: "budget"` nomeando os quatro que aceita |

`McpSurfaceIsClosedTest` sustenta as duas metades: compara as ferramentas registradas com
`McpSurface.offered` **nos dois sentidos** — uma a mais entrou sem decisão, uma a menos sumiu
sem ninguém notar —, recusa exclusão sem motivo escrito, e confere que as três `WITHHELD` são
exatamente as que os requisitos proíbem.

---

## O que a suíte não verifica

A suíte passa inteira, e passar não é a mesma coisa que cobrir. O que está abaixo **não** é
verificado automaticamente, e está aqui nominalmente para que a contagem de testes verdes não
sugira uma garantia que ninguém deu. Cada item traz o motivo, porque um limite sem motivo vira
pendência esquecida.

### 1. Nenhum teste de ferramenta exercita a implementação de produção de um use case de escrita

A regra de dependência impede `feature/mcp/impl` de alcançar outro `impl`, que é exatamente o
que garante que uma ferramenta só consiga chamar o dono da regra. O preço é que os testes deste
módulo **reconstroem** cada use case de escrita e de operação sobre o razão real —
`WorldRegisterTransaction`, `WorldAddInstallment`, `WorldAddCreditCard`, `WorldPayInvoicePayment`
e os demais, em `AgentWorldWrites.kt` e `AgentWorldOperations.kt`.

O que eles provam: composição (a ferramenta resolve as identidades e preenche o formulário),
delegação (não decide) e recusa — tudo contra um `AppDatabase` de verdade. O que **não** provam:
a regra própria daquele `Impl`. Morde mais em dois lugares, onde a cópia teve de reescrever uma
regra não trivial: a **distribuição das parcelas pelas faturas** de `AddInstallmentUseCaseImpl` e
a **janela do primeiro ciclo** de `AddCreditCardUseCaseImpl`. Cada `Impl` tem os seus próprios
testes na feature dona (`AddInstallmentUseCaseTest`, `AddCreditCardUseCaseTest`); o que ninguém
verifica é a ferramenta **junto com** o `Impl`, nem que a cópia continue fiel ao original.

### 2. Ninguém conta consultas

`list_invoices` lê o devido em lote por `owedByDimensionByCurrency` e `list_cards`/`get_card_overview`
usam a forma plural de `CalculateAvailableLimitUseCase`. Há teste dos **números** — cada devido e
o total das que casam —, e nenhum das **leituras**: voltar a um laço com N consultas produziria as
mesmas respostas e nenhuma falha. *"N faturas custam uma leitura, não N"* é regra escrita, não
verificada.

### 3. Os testes de migração rodam só no JVM

`Migration14To15Test` — a tabela do registro de atividade — abre `BundledSQLiteDriver().open(":memory:")`
em `jvmTest`, como **todos** os testes de migração do projeto. Nada exercita a migração sobre o
SQLite de um device Android ou de um iPhone. Limitação preexistente e herdada, não introduzida
aqui, e registrada porque a tabela nova a herda também.

### 4. A validação de `Host`/`Origin` não diz nada sobre um navegador de verdade

Os testes de perímetro mandam cabeçalhos forjados por um cliente HTTP; não há navegador na
suíte. E um pedido **sem** `Origin` é aceito — deliberadamente, porque um cliente MCP que não é
navegador não manda esse cabeçalho, e o teste `a client on this machine is let through` fixa esse
comportamento. A defesa contra uma página aberta no navegador do usuário depende, portanto, de o
navegador mandar `Origin`: verdade sobre navegadores, não sobre este código, e fora do alcance
desta suíte.

### 5. O teste de loopback usa esta máquina como substituta de uma segunda

`Loopback.externalAddresses()` devolve os endereços **não**-loopback da própria máquina — os que
outro host usaria — e o teste exige que nada responda neles e que continuem livres. Numa máquina
só com loopback (um container, um laptop com a rede desligada) a varredura vem vazia e a asserção
não afirma nada; sobra o controle sobre `127.0.0.1`. Nenhum teste conecta de outro host, porque
não há outro host.

### 6. Uma sessão abandonada continua contada

`openSessions` perde a entrada no `onClose` da sessão — o cliente encerrando, ou o transporte
caindo. Não há expiração por inatividade, e nenhum teste cobre o cliente que simplesmente some:
até o transporte perceber, a linha "há alguém do outro lado agora" pode seguir dizendo que sim.

### 7. `list_transactions(nature=…)` corta a lista e não move os totais

O razão não tem agregado cortado por natureza, e somar a página é proibido (armadilha 1). O
payload declara o descompasso — `totals.narrowed_by`, `totals.basis`, `perimeter.excludes` e a
descrição da ferramenta —, e há teste de que os totais **não** são a soma da página. O que nada
verifica é a leitura: um agente que peça `nature=transfer` e relate o `expense` ao lado dirá um
número com o significado errado, e a mitigação contra isso é **declarativa, não estrutural**.

### 8. Nada do que se vê na tela é verificado

Não existe infraestrutura de teste de Compose no projeto: `ui-test` não está no catálogo de
versões e não há um `createComposeRule` sequer. Todos os testes da seção são sobre `McpUiState` e
`McpViewModel`. Ficam por conta de olho humano:

- que o grupo **Integrações** apareça em `SettingsScreen` e leve à seção;
- que o revelado siga a ordem endereço → token → permissões → instruções (`McpUiState.showsDetails`
  é o dono e é testado; a renderização não);
- que os botões de cópia ponham no clipboard o que dizem pôr — o **valor** que cada um copia é
  estado e é afirmado em `McpViewModelTest` (`connectionSnippet` com o token real, `address`,
  `token`); o que nenhum teste alcança é a ligação entre o botão e a propriedade e a entrega em si,
  feita no composable por `LocalClipboardManager`, sem passar por `McpAction`;
- que o token apareça mascarado até ser pedido (`displayedToken` é testado; o que a tela desenha,
  não);
- que o erro de porta ocupada apareça **debaixo do campo**, e não num alerta solto;
- que uma entrada do registro, tocada, abra o lançamento que descreve (`Reference.toTarget()` é
  testado; a navegação, não);
- que o aviso de falha ao subir alcance quem não está na seção, sobre qualquer tela (`App.kt`
  coleta `McpServerState.Failed`);
- que o ponto de entrada esteja escondido no Android e no iOS — a regra (`FeaturePlatform.isCurrent`)
  é testada em `jvmTest`, onde `isDesktop` é verdadeiro, e o app rodando nas outras plataformas não é.

E a **paridade** das chaves de string é verificada nos dois arquivos (`StringResourceParityTest`);
que a tradução diga a mesma coisa que o original, não.

### 9. Uma queda do engine *depois* do bind não altera o estado publicado

`mcp-server` pede que "uma queda depois de o servidor ter subido também se reflete ali". O que
existe hoje cobre a metade que acontece: um bind que falha vira `Failed` e chega à tela, com
teste. O que não existe é a detecção de um engine que caia sozinho **depois** de ter subido — o
estado continuaria dizendo `Running`, que é o que o requisito proíbe.

Não foi implementado por escolha, e a escolha tem motivo: distinguir uma queda espontânea de uma
parada intencional exige um sinalizador no ciclo de vida que hoje está testado e funcionando, e a
queda em si não é simulável de forma confiável — a implementação entraria sem teste no caminho
crítico do servidor. Trocar um limite conhecido por um risco desconhecido não é uma troca boa.

O caso é patológico: o servidor vive **dentro** do processo do app, então um engine que morre
sozinho com a janela viva é um estado em que o app já está mal. Se aparecer na prática, o ponto
é `DesktopMcpServerController.bringUp`, assinando o monitor do engine.

> As tasks 14.2 e 14.2a da change existem por causa desta lista: são a passagem manual, com um
> cliente MCP real sobre o desktop em execução, e não são substituíveis por teste.
