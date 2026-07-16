## Context

A change `balanced-ledger` construiu o razão e o pôs a **escrever** tudo (double-write) e a **ler** os números (saldo, fatura, patrimônio, gasto por categoria). O que ela deliberadamente não fez foi remover a coexistência — documentada em CAP-1..CAP-7 no seu `tasks.md`.

O estado real, verificado no código:

```
ESCRITA                          LEITURA
─────────────────────            ──────────────────────────────────────
OperationRepository              Números  ──▶ EntryDao (SQL agregado, Double)
  ├─▶ TransactionEntity[]        Objetos  ──▶ TransactionEntity[] (100% legado)
  │     (legado, perna)
  └─▶ EntryEntity[]              Entry (modelo) ──▶ retornado por NINGUÉM
        (razão, perna)           Ledger.kt helpers ──▶ só LedgerTest
```

Três fatos governam o desenho:

1. **O razão não é legível como objeto.** `IEntryRepository` só expõe agregados (`balanceUpTo`, `invoiceOwed`, `categoryTotals`) que retornam `Double` via SQL. O modelo de domínio `Entry` não é retornado por nenhum repositório de produção, e `naturalBalanceOf`/`deriveOperationLabel`/`isBalanced` são exercitados apenas por `LedgerTest`. Promover o razão a fonte de verdade do grafo é **construção**, não limpeza.
2. **O vocabulário está invertido.** `Transaction` = perna, `Operation` = agregado. O alvo (`Transaction` dona de `Entry[]`) é uma reocupação de nome, e a palavra precisa ser desocupada antes — o que **força a ordem** de todo o trabalho.
3. **A paridade dos leitores legados não tem rede.** O `tasks.md` admite: *"a paridade dos que continuam legados não é garantida por teste automatizado (verificada em device)"*.

## Goals / Non-Goals

**Goals:**
- O razão como única fonte de verdade, dos agregados **e** do grafo de objetos.
- `Transaction` como agregado dono de `List<Entry>`; `Operation` e a perna legada removidos.
- Comportamento idêntico ao usuário — o que muda é o nome, não a tela.
- Modelos de UI planos; mappers como a única fronteira domínio→apresentação.
- Terminar com uma arquitetura sem resíduo: nenhum modelo, tabela, coluna ou helper legado sobrevive à change.

**Non-Goals:**
- Reembolso/estorno, FX/câmbio, investimentos (habilitados, não implementados).
- Mudar o modelo de entrada do usuário: o form continua oferecendo despesa/receita/ajuste.
- Otimizar leitura (projeção CQRS, query agrupada do dashboard do CAP-1) — decisão de valor, não desta change.
- Fechar o CAP-3 (`ensureSystemAccount` check-then-act) — risco próprio, deliberadamente separado.

## Decisions

### D1 — A colisão de nome força a ordem; o rename é o último passo, não o primeiro

`Operation` só pode virar `Transaction` depois que a `Transaction` legada morrer. Isso não é preferência: é o compilador. E a mesma colisão existe no SQL — `operations` só vira `transactions` depois do `DROP TABLE transactions`.

```
A. Razão legível como objeto     Entry hidratada (+account, +invoiceId), observeEntries
   └─▶ B. Virar os 4 leitores    AccountUi, ViewCategoryViewModel, budgets, CalculateBalance
       └─▶ C. Fim do double-write, drop da tabela legada
           └─▶ D. Rename Operation→Transaction + TransactionUi   ← o objetivo, e o mais barato
```

**Alternativa considerada:** renomear cedo (`Transaction` legada → `Leg`/`LegacyLeg`) para desbloquear D antes. Rejeitada: renomeia ~15 sites para um nome descartável, e a churn é paga duas vezes. Cada etapa acima compila e é verificável sozinha; D é mecânico e quase todo por refactor de IDE.

### D2 — `isEditable` continua derivável do número de pernas, contando as pernas monetárias

`Operation.isEditable = transactions.size == 1` porteia o botão Editar (`ViewOperationModal:390,419`). No razão toda operação tem ≥2 entries, então uma tradução ingênua o zeraria. Mas no modelo antigo **categoria e ajuste eram contrapartidas fantasma** — nunca foram linhas em `transactions`. A contagem antiga já era, sem saber, a contagem de pernas *de dinheiro*:

| Forma | Legado | Razão | Pernas monetárias | Editável |
|---|---|---|---|---|
| Despesa/receita em conta | 1 | ASSET + EXPENSE/INCOME | 1 | ✓ |
| Compra no cartão | 1 | LIABILITY + EXPENSE | 1 | ✓ |
| Ajuste de saldo | 1 | ASSET + EQUITY | 1 | ✓ |
| Transferência | 2 | ASSET + ASSET | 2 | ✗ |
| Pagamento de fatura | 2 | ASSET + LIABILITY | 2 | ✗ |

Bijeção nas cinco formas. Introduz-se `AccountType.isMonetary` (`ASSET`/`LIABILITY`), ao lado de `isDebitNatured`:

```
ASSET, LIABILITY          → onde o dinheiro ESTÁ       ← o usuário escolhe. Eram as pernas.
INCOME, EXPENSE, EQUITY   → por que ele SE MOVEU       ← o writer sintetiza. Eram fantasmas.
```

`isEditable = entries.count { it.account.type.isMonetary } == 1`. O mesmo predicado governa a resolução de perspectiva — sinal de que é conceito, não utilitário.

**Alternativa considerada:** inventar uma regra nova (ex.: `label ∈ {EXPENSE, INCOME, ADJUSTMENT}`). Rejeitada: seria equivalente hoje, mas é uma decisão de UX disfarçada de refactor. A bijeção preserva comportamento **por construção**.

### D3 — Uma função total de rótulo, com `EQUITY` tratado explicitamente

Hoje coexistem duas derivações parciais (CAP-6), e **nenhuma cobre a união**:

```
Transaction.Type   { EXPENSE, INCOME, ADJUSTMENT }          ← por perna. Sem PAYMENT/TRANSFER.
OperationLabel     { EXPENSE, INCOME, TRANSFER, PAYMENT }   ← por operação. Sem ADJUSTMENT.
```

Pior: `deriveOperationLabel` **não trata `EQUITY`**. Um ajuste tem tipos `{ASSET, EQUITY}` — não bate `EXPENSE`, nem `INCOME`, nem `LIABILITY`, e cai no `else` → **`TRANSFER`**. Está latente só porque a função não tem consumidor em produção; vira bug visível assim que a UI ler o razão. O CAP-6 previa que as duas *"convergem"* — elas não convergem, precisam ser fundidas, e a fusão tinha um buraco.

`TransactionLabel = { EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT }`, derivado dos tipos de conta com `EQUITY` testado **antes** do `else`.

### D4 — `Transaction.Type` sobrevive como vocabulário de entrada, não como estado

O CAP-5 concluiu que o enum "não é dívida removível: é entrada do usuário e classificação de exibição". Correto sobre o **papel**, errado sobre **onde ele mora**:

```
UI (form)   { EXPENSE, INCOME, ADJUSTMENT }     ← input. É UX. Fica. Transfer/payment têm fluxos próprios.
    │  mapper: intenção → entries
    ▼
Transaction { entries }                          ← domínio. Sem classificação persistida. Σ=0.
    │  mapper: entries → TransactionLabel        ← função TOTAL (D3)
    ▼
TransactionUi { label, amount, … }               ← exibição. Derivado.
```

O vocabulário de input é **subconjunto** do de exibição — achatar os dois num enum só seria o erro simétrico ao atual. A coluna `TransactionEntity.type` (a parte que o CAP-5 identificou como realmente redundante) morre junto da tabela em C, sem migração dedicada.

### D5 — Modelos de UI são DTOs planos; a perspectiva vira argumento do mapper

Nenhum modelo de UI carrega grafo de domínio — no máximo um id. Isso mata o `requireNotNull` de `OperationUi.transaction` **dissolvendo-o**: a resolução deixa de ser um `by lazy` que estoura na leitura e passa a acontecer no mapper, onde falhar é tratável.

```
HOJE                                       ALVO
──────────────────────────────             ──────────────────────────
AccountUi(                                 AccountUi(          ← plano
  account: Account,        ← domínio         id, name,
  transactions: List<...>, ← domínio         openingBalance, balance, …
) { …5 somas no construtor }               )

OperationUi(                               TransactionUi(      ← plano
  operation: Operation,    ← domínio         id, label, amount, date, …
  perspective: …,                          )
) { transaction by lazy requireNotNull }
```

`AccountUi` é o caso exemplar: guarda domínio e faz cinco somas num construtor secundário — a terceira cópia da matemática de saldo. O princípio não o melhora, o apaga. Consequência estrutural: `core/ui/model` deixa de importar domínio, e a regra de camada "Domain ← UI" vira **fato verificável** em vez de convenção.

### D6 — `TransactionPerspective` colapsa de sealed class para data class

`OperationPerspective` bifurca em `Account(accountId)` e `Card(creditCardId, invoiceId?)` **só porque a perna legada tinha duas formas** (campo `account` vs `creditCard`). Uma `Entry` tem uma forma: `accountId` + `invoiceId?`. As variantes colapsam em `TransactionPerspective(accountId, invoiceId? = null)`, com o cartão entrando via `CreditCard.accountId`. A hierarquia inteira era resíduo do legado.

### D7 — Listas hidratam o domínio e mapeiam; sem projeção de leitura

As telas continuam hidratando `Transaction` (com entries + accounts) e mapeando para DTO, em vez de o DAO projetar linha plana direto.

**Alternativa considerada:** projeção de leitura (domínio só na escrita, CQRS-lite). Rejeitada **por ora**: `observeAllOperations` já hidrata as pernas hoje, então a forma escolhida não é regressão de custo nem de estrutura; a projeção compraria complexidade e um segundo modelo de leitura para resolver um problema de performance que não foi medido. Fica disponível se o custo aparecer.

### D8 — `SystemAccount.INITIAL_BALANCE` é fantasma e sai; `initialBalance` vira `openingBalance`

Duas coisas diferentes carregam o nome "saldo inicial":

| | O que é | Referências em produção |
|---|---|---|
| `SystemAccount.INITIAL_BALANCE` | conta `EQUITY` semeada pela `MIGRATION_7_8` | **zero** (só `RECONCILIATION` é usada, em `LedgerEntryWriter:104`) |
| `AccountUi.initialBalance` | saldo até o mês anterior — derivado, corte por data | `AccountCard`, `SummaryCard`, `TransactionsUiState`, `ReportStats`… |

A migração semeou uma conta para um conceito que o app não tem. Sai do `SystemAccount` e da tabela na v9 — deleção segura e verificável **porque nenhuma `Entry` a referencia** (a FK `accounts` é `NO_ACTION`, então a deleção falharia se houvesse).

O `initialBalance` real é o *saldo de abertura do período*, e tem **três implementações independentes**, todas legadas — `AccountUi:25`, `TransactionsViewModel:73` (forma in-memory, CAP-2), `CalculateReportStatsUseCase:41`. Todas são `balanceUpTo(corte)` reescrito à mão. Renomear para `openingBalance` diz a verdade e libera "saldo inicial" caso um dia vire feature (abrir conta com dinheiro dentro) — que é para o que aquela conta `EQUITY` foi semeada cedo demais.

### D9 — Testes de caracterização antes de virar cada leitor

Cada um dos quatro leitores legados é virado em dois passos: **(1)** teste que captura os números atuais (produzidos pelo caminho legado) com dados representativos; **(2)** troca da implementação, com o teste inalterado passando. Sem isso, "manter comportamento" é fé, não engenharia — o próprio `tasks.md` registra que a paridade só foi verificada em device.

### D10 — Migração v9: drop antes de rename, numa transação

```sql
-- 1. a tabela legada morre                 DROP TABLE transactions;
-- 2. só então o nome fica livre            ALTER TABLE operations RENAME TO transactions;
-- 3. o fantasma sai                        DELETE FROM accounts WHERE …type='EQUITY' AND name='Saldo Inicial';
```

A ordem espelha o refactor — a mesma colisão, no SQL. `entries.operationId` referencia `operations(id)`: o rename preserva a FK no SQLite, mas o Room valida o schema exportado, então a v9 precisa de teste de migração (`Migration8To9Test`) e do `9.json`. A deleção do passo 3 é defensiva: se alguma `Entry` referenciar a conta (não deveria), a FK `NO_ACTION` aborta e nós descobrimos no teste, não no device.

## Risks / Trade-offs

- **[Mudança silenciosa de número]** Virar `AccountUi`/budgets/`ViewCategoryViewModel` do somatório em memória para o razão pode alterar valores exibidos sem erro nem crash. → **Mitigação:** D9 (caracterização antes da troca) e a bijeção demonstrável do D2. É o risco #1 desta change.
- **[CAP-4 deixa de ser teórico]** `AccountUi` filtra por `transaction.date`; `balanceUpTo` corta por **data da operação**. Coincidem por construção hoje, sem guarda. → **Mitigação:** virar o `AccountUi` é exatamente onde a invariante é exercida pela 1ª vez; adicionar teste que divirja as datas de propósito e falhe, e então garantir a invariante na escrita em vez de convencioná-la.
- **[Migração destrutiva]** A v9 dropa a tabela `transactions` — o legado deixa de existir e não há rollback de dados. → **Mitigação:** C só acontece depois de B verificado; o razão já contém tudo (backfill da v8, testado com órfãos); teste de migração v8→v9 com dados reais representativos antes do merge.
- **[Rename de grande superfície]** D toca dezenas de arquivos e polui o diff. → **Mitigação:** D é o último passo e quase todo mecânico (refactor de IDE); manter em commits separados de qualquer mudança de comportamento, para o review poder confiar no diff.
- **[Bug já em produção]** `AdjustInvoiceUseCase:74` atualiza o valor legado sem rota de razão; como `invoiceOwed` já lê o razão, o número exibido **já diverge hoje**. → **Mitigação:** corrigir cedo (em A/B), com teste — não esperar C, porque é bug corrente e não dívida da coexistência.
- **[Trade-off: `Entry` ganha `invoiceId` no domínio]** Expor o `invoiceId` (hoje só na entity) vaza um detalhe de sub-razão de cartão para o modelo. → Aceito: é o que torna a `Entry` uma perna completa; sem isso a UI de fatura continuaria dependendo do legado, e o objetivo da change não fecha.
- **[Trade-off: specs main aspiracionais no intervalo]** As specs sincronizadas do `balanced-ledger` já afirmam coisas que só ficam verdadeiras ao fim desta change (ex.: *"saldo MUST NOT depender de `signedImpact()`"*, contrariado por `AccountUi`/`CalculateReportStats`). → Aceito conscientemente: é o gap que esta change fecha, e os deltas aqui declaram a versão final. Enquanto durar, a spec descreve o alvo, não o código.

## Open Questions

- **`Transaction.Type` como input: onde o enum passa a morar?** Se o domínio não o persiste, ele é do `core/model` (classificação de fronteira) ou do `core/ui` (vocabulário de form)? D4 fixa o papel, não o endereço.
- **Ordem de B:** os quatro leitores podem ser virados em qualquer ordem, ou `CalculateBalanceUseCase` (in-memory) deve vir primeiro por ser dependência dos outros dois? A investigar ao aplicar.
- **`openspec/specs/domain-calculations/`** é um diretório vazio, sem `spec.md` — casca órfã. Limpar aqui ou fora desta change?
