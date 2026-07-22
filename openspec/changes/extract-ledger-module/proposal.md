## Why

O núcleo do razão — `IEntryRepository`, `ITransactionRepository`, `LedgerEntryWriter`, `EntryRepository` — mora dentro de `feature:transactions`, uma feature. Oito das nove outras features dependem de `feature:transactions:api` não porque queiram a tela de transações, mas porque é onde o razão vive: isso é dependência de infraestrutura fantasiada de dependência de feature, e já causou dano concreto (`CalculateBudgetProgressUseCase` em `feature/budgets/api` não pode ler o razão pela regra `api ⊄ api`, então recebe o número já calculado pelo `impl`).

Em paralelo, a fachada vazou para dentro do razão: `Transaction` carrega 6 campos de fachada, `TransactionLeg` carrega 4, `LedgerEntryWriter` injeta `CategoryDao` e `CreditCardDao`, e `EntryEntity` tem FK para `invoices`. O razão deve ser a fonte de verdade com garantia contábil; as features são sabores dele, expondo fachadas úteis ao usuário.

## What Changes

### Novo módulo `:core:ledger`, com dependência invertida

- **BREAKING (build)** Criar `:core:ledger` contendo as entities `accounts`/`transactions`/`entries`/`dimensions`, seus DAOs, os modelos de domínio do razão, o writer e os repositórios. Depende apenas de Room e `:core:model` — de nenhum módulo do app.
- **BREAKING (build)** Inverter a direção: `core:database → core:ledger`. `core:database` deixa de declarar as tabelas do razão e passa a importá-las para montar o `AppDatabase` e as migrações. O `EntryDao` fica impedido *em tempo de compilação* de fazer JOIN com tabela de fachada, porque não a enxerga.
- Features passam a depender de `:core:ledger` diretamente. `feature:transactions:api` volta a expor apenas rotas, entry point e o que é da tela.

### Dimensões analíticas substituem o vazamento de fatura

- Introduzir a tabela `dimensions(id, kind)` — um espaço de identidade de dono do razão. O `kind` existe no schema para que o writer possa validar em qual perna cada dimensão pode pousar; o *domínio* do razão permanece opaco quanto ao significado de cada `kind`.
- **BREAKING (schema)** `entries.invoiceId` vira `entries.dimensionId` (FK → `dimensions`, `ON DELETE SET NULL`). Um único slot por entry.
- Fachadas passam a guardar `dimensionId`, espelhando o padrão `facade.accountId` já existente: `invoices.dimensionId`, `categories.dimensionId`.

### Categoria deixa de ser conta e vira dimensão

- **BREAKING (schema)** Categorias saem do plano de contas. `categories.accountId` é removido; `categories.dimensionId` o substitui.
- O plano de contas passa a ter **duas** contas nominais no app inteiro (uma `EXPENSE`, uma `INCOME`) em lugar de uma conta por categoria.
- **BREAKING** `SystemAccount.UNCATEGORIZED_EXPENSE` e `UNCATEGORIZED_INCOME` deixam de existir. "Sem categoria" passa a ser `dimensionId = NULL`, e não uma conta especial.
- `Category.type` (`INCOME`/`EXPENSE`) passa a ser estado primário da fachada, e é o que decide em qual conta nominal a perna posta. **Exceção explícita e documentada ao Derivation Rule**, justificada por ser declaração do usuário no momento da criação, não regra derivável do razão.
- `categories.isArchived` passa a ser próprio, deixando de ser lido da conta. Isso não perde regra: `LedgerEntryWriter` nunca aplicou closure a categorias (só `ACCOUNT` e `CREDIT_CARD` passam por `orRejectIfClosed`).

### O writer passa a falar apenas razão

- **BREAKING (API)** `TransactionLeg` e `TransactionIntent` deixam de carregar `Account`, `CreditCard`, `Invoice` e `Category` e passam a falar `accountId: Long` + `dimensionId: Long?`. Resolver fachada → id vira responsabilidade da feature, que é quem sabe o que a fachada é.
- `LedgerEntryWriter` perde `CategoryDao` e `CreditCardDao`, ficando com `EntryDao` + `AccountDao`.
- `TransactionTarget` (`{ACCOUNT, CREDIT_CARD}`) é removido: em termos de razão é apenas `type ∈ {ASSET, LIABILITY}`.
- **BREAKING (API)** `Transaction` perde `category`, `sourceAccount`, `targetCreditCard`, `targetInvoice`, `installment` e `recurring`. As features hidratam a própria fachada a partir das entries.

### Critério para o que sai do razão

- Uma leitura sai do razão **apenas quando não é derivável de `AccountType`/sinal/período/dimensão**. Nome de fachada não é critério.
- Permanecem no razão, renomeadas para vocabulário de razão: `accountPeriodTotals` e `reportStats` (a classificação por contra-perna é teoria do razão), `invoiceNaturalBalance` → soma por dimensão, `categoryTotalsWithSiblingLeg` → totais por dimensão com perna irmã.
- `transactions.categoryId` é removido por ficar redundante com a dimensão. `installmentId`, `installmentNumber`, `recurringId` e `recurringCycle` permanecem onde estão — decisão consciente de adiar, sem custo estrutural.

### Migração v9 → v10

- Criar `dimensions`; emitir uma dimensão por categoria e por fatura; reescrever `entries.invoiceId` → `dimensionId`; reescrever as pernas de categoria para a conta nominal do tipo correspondente com a dimensão da categoria; migrar as pernas em `UNCATEGORIZED_*` para a nominal com `dimensionId = NULL`; remover as contas de categoria e as contas `UNCATEGORIZED_*` do plano.
- **A migração valida `Σ = 0` por transação e por moeda antes e depois da reescrita**, como teste de migração. É a única prova de que colapsar N contas de categoria em duas nominais não desbalanceou nada.

## Capabilities

### New Capabilities
- `ledger-dimensions`: dimensões analíticas do razão — a tabela `dimensions(id, kind)`, o slot único por entry, a semântica de `dimensionId = NULL`, a regra de qual `kind` pode pousar em qual perna, e o vínculo `facade.dimensionId` espelhando `facade.accountId`.
- `ledger-module-boundary`: o contrato do módulo `:core:ledger` — o que ele contém, a direção invertida da dependência, a proibição estrutural de referenciar tabela de fachada, e o critério de derivabilidade que decide se uma leitura fica no razão ou vira extensão de feature.

### Modified Capabilities
- `chart-of-accounts`: categoria deixa de ser linha do plano de contas; o plano passa a ter duas contas nominais; as contas de sistema `UNCATEGORIZED_*` são removidas; a fachada de categoria liga-se por `dimensionId` e passa a ser dona do próprio `type` e `isArchived`.
- `balanced-ledger`: a intenção de escrita passa a falar `accountId`/`dimensionId` em vez de fachadas; o writer perde as dependências de fachada; `Transaction` deixa de carregar o grafo de fachada; a migração para o razão ganha o requisito de verificação de `Σ = 0` antes e depois.
- `ledger-reporting`: gasto por categoria passa a ser soma por dimensão, não por conta; o saldo de fatura passa a ser soma por dimensão; fica estabelecido o critério de derivabilidade para o que o razão expõe.
- `module-architecture`: novo módulo `:core:ledger` e inversão de `core:database → core:ledger`; features deixam de depender de `feature:transactions:api` para ler ou escrever no razão.

## Impact

**Módulos criados:** `:core:ledger` (novo, em `settings.gradle.kts` e possivelmente com convenção própria em `build-logic`).

**Módulos alterados:** `:core:database` (perde as tabelas do razão, ganha dependência de `:core:ledger`, hospeda a migração v10), `:core:model` (perde os modelos do razão para `:core:ledger`; `Category` muda de forma), `feature:transactions:api` e `:impl` (perdem o núcleo do razão), e as sete features consumidoras — `accounts`, `creditcards`, `categories`, `budgets`, `report`, `dashboard`, `recurring` — que trocam a dependência e passam a resolver a própria fachada → id.

**Schema:** migração v9 → v10 com reescrita de dados em `entries`, `categories`, `accounts` e `transactions`. Nova tabela `dimensions`. Colunas removidas: `entries.invoiceId`, `categories.accountId`, `transactions.categoryId`. Colunas adicionadas: `entries.dimensionId`, `categories.dimensionId`, `categories.isArchived`, `invoices.dimensionId`.

**Testes:** os testes de migração em `core/database/src/jvmTest` ganham o par de verificação de `Σ = 0`; os testes de query (`EntryCategoryQueryTest`, `InvoiceAndCardQueryTest`, `AccountPeriodTotalsQueryTest`, `ReportStatsQueryTest`, `BalanceUpToMonthQueryTest`, `MigrationLedgerReadParityTest`) mudam de módulo junto com o DAO.

**Riscos:** `closedLegBlockingChange`, `hasEntries` e `entryCountInMonth` operam por `accountId` — cada um precisa de variante por dimensão ou de decisão consciente de que categoria não os usa. A migração é irreversível na prática e reescreve história contábil, o que torna a verificação de `Σ = 0` um requisito, não um cuidado.
