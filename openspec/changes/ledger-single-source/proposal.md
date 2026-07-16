## Why

O razão já é fonte de verdade dos **números**, mas não do **grafo de objetos**: o modelo `Entry` nunca é retornado por nenhum repositório de produção — só agregados SQL (`Double`) — e os helpers de `List<Entry>` em `Ledger.kt` são código morto fora dos testes. O app roda em double-write com leitores metade-razão/metade-legado (CAP-7), cuja paridade não é garantida por teste automatizado, e cada operação é gravada duas vezes em dois modelos que podem divergir. Enquanto a coexistência durar, todo trabalho novo paga o imposto de manter dois modelos em sincronia.

Somado a isso, o vocabulário está invertido: `Transaction` nomeia a **perna** e `Operation` o **agregado** — o oposto do que o usuário entende e do que a contabilidade usa. Terminar a adoção do razão e corrigir o vocabulário é o mesmo movimento, porque a palavra `Transaction` só pode ser reocupada depois que a perna legada morrer.

## What Changes

**Razão como única fonte de verdade**
- Tornar o razão legível como objeto: `Entry` hidratada com sua `Account` e seu `invoiceId`, observável por operação — capacidade que hoje **não existe**.
- Virar os quatro leitores que ainda derivam dinheiro do legado: `AccountUi`, `ViewCategoryViewModel`, budgets e a forma in-memory do `CalculateBalanceUseCase`.
- **BREAKING** Encerrar o double-write: parar de gravar o modelo legado e dropar a tabela `transactions`.

**`Transaction` dona das pernas, `Operation` removido**
- **BREAKING** Renomear o agregado `Operation` → `Transaction`, dono de `List<Entry>`. A `Transaction` legada (perna) é removida junto de `TransactionEntity`, `TransactionDao`, `ITransactionRepository`, `TransactionMapper` e `signedCents()`.
- **BREAKING** Migração v9: dropar a tabela `transactions` legada e renomear `operations` → `transactions` (nessa ordem — o nome precisa ser desocupado antes de ser reocupado).
- Remover `Operation.Kind` e `Transaction.Target`, ambos substituídos por derivação a partir dos tipos de conta.

**Classificação derivada, total e correta**
- Unificar as duas derivações parciais coexistentes (CAP-6) numa **função total** `TransactionLabel` sobre `{EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT}`.
- Corrigir o buraco do `EQUITY` em `deriveOperationLabel`: hoje um ajuste de saldo (`{ASSET, EQUITY}`) cai no `else` e é rotulado **`TRANSFER`**. Bug latente, sem consumidor em produção — vira visível no minuto em que a UI ler o razão.
- Preservar `Transaction.Type` `{EXPENSE, INCOME, ADJUSTMENT}` como **vocabulário de entrada na UI**, deixando de ser estado persistido (reenquadra o CAP-5: o enum não é dívida, a coluna é).

**Modelos de UI planos, mappers no comando**
- UI models passam a ser DTOs planos, sem grafo de domínio dentro (no máximo um id). `OperationUi` → `TransactionUi`; `AccountUi` perde `account: Account` e as cinco somas do seu construtor.
- Mappers passam a deter a resolução de perspectiva, a derivação do rótulo e a inversão de sinal por `AccountType`.
- `OperationPerspective` (sealed `Account`/`Card`) colapsa numa data class `TransactionPerspective(accountId, invoiceId?)` — a bifurcação existia só porque a perna legada tinha duas formas.

**Correções de fidelidade**
- `AccountType.isMonetary` (`ASSET`/`LIABILITY`) — o predicado que o modelo antigo não conseguia expressar. `isEditable` passa a ser "exatamente uma perna monetária", tradução **bijetiva** de `transactions.size == 1` nas seis formas de operação.
- Remover `SystemAccount.INITIAL_BALANCE`: conta `EQUITY` semeada pela `MIGRATION_7_8` com **zero referências em produção** — o app não tem saldo inicial de verdade.
- Renomear `initialBalance` → `openingBalance` (saldo de abertura do período, derivado) e unificar suas **três implementações independentes** (`AccountUi`, `TransactionsViewModel`, `CalculateReportStatsUseCase`) em `balanceUpTo`.
- Corrigir `AdjustInvoiceUseCase:74`, que atualiza o valor legado sem rota de razão — como `invoiceOwed` já lê o razão, editar um ajuste de fatura **já hoje** exibe número divergente.
- Fechar o CAP-4: hoje o saldo por entries corta por data da **operação** e a forma in-memory por data da **transação**; coincidem por construção, sem guarda. Virar o `AccountUi` é onde isso é exercido pela primeira vez.

**Não-escopo:** reembolso/estorno, FX/câmbio e investimentos seguem fora — o modelo já nasce preparado.

## Capabilities

### New Capabilities
- `presentation-mapping`: modelos de UI como DTOs planos sem grafo de domínio, e mappers como o único lugar onde domínio vira apresentação (resolução de perspectiva, derivação de rótulo, inversão de sinal, formatação de valor).

### Modified Capabilities
- `balanced-ledger`: o rótulo derivado passa a ser função **total** incluindo `ADJUSTMENT` (contrapartida `EQUITY`), fechando o buraco que hoje rotula ajuste como transferência; o agregado passa a se chamar `Transaction` e a ser o dono das entries; o modelo legado deixa de ser espelhado.
- `chart-of-accounts`: as contas de sistema deixam de incluir "saldo inicial" — só a de reconciliação permanece, por ser a única com uso real.
- `ledger-reporting`: o razão passa a ser a **única** fonte de leitura (fim da coexistência), incluindo o grafo de objetos e não só os agregados; o saldo de abertura do período é unificado num mecanismo único; a invariante de data do corte passa a ser garantida, não convencionada.

## Impact

- **`core/model`**: `Operation` → `Transaction` (dona de `List<Entry>`); `Transaction` legada, `Operation.Kind` e `Transaction.Target` removidos; `Entry` ganha `invoiceId`; `OperationLabel` → `TransactionLabel` (total); `AccountType.isMonetary`; `signedCents()` e `SystemAccount.INITIAL_BALANCE` removidos.
- **`core/database`**: `TransactionEntity`/`TransactionDao` removidos; `OperationEntity` → `TransactionEntity`; **migração v9** (drop + rename, nessa ordem) com teste de migração; `EntryDao` ganha hidratação de entries.
- **`core/ui`**: `OperationUi` → `TransactionUi` plano; `AccountUi` plano; `OperationPerspective` → `TransactionPerspective` (data class); `core/ui/model` deixa de importar domínio, tornando a regra de camada "Domain ← UI" verificável em vez de convencional.
- **Features tocadas**: `transactions` (repositório, writer, modais, lista), `accounts`, `creditcards` (faturas/parcelas), `categories`, `budgets`, `dashboard`, `report`, `recurring`.
- **Testes**: testes de **caracterização** capturando os números atuais **antes** de virar cada leitor legado (a paridade dos leitores legados hoje é verificada em device, não por teste — virar sem rede é como comportamento muda em silêncio); teste de migração v9; teste da função total de rótulo cobrindo `ADJUSTMENT`.
- **Risco principal**: mudança silenciosa de número em telas que hoje somam pernas em memória. Mitigado pelos testes de caracterização e pela bijeção demonstrável do `isEditable`.
- **Dependência**: assume as specs `balanced-ledger`, `chart-of-accounts` e `ledger-reporting` já sincronizadas ao main (feito). A change `balanced-ledger` permanece ativa com o gate de testes (task 6.1) em aberto.
