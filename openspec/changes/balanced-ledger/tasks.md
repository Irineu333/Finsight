## 1. Modelo de domínio (core/model)

- [x] 1.1 Criar `AccountType` (`ASSET`, `LIABILITY`, `INCOME`, `EXPENSE`, `EQUITY`) em `core/model/domain/model`
- [x] 1.2 Criar/estender `Account` com `type: AccountType` e `currency`, cobrindo conta, cartão, categoria e contas de sistema (`EQUITY`)
- [x] 1.3 Criar `Entry` (referência a `Account`, `amount` assinado em menor unidade, `currency`) substituindo o par `Transaction.Type`/`Target`
- [x] 1.4 Redefinir `Operation` como conjunto de `Entry` (mín. 2 pernas), com `Kind` derivado (extensão pura, não campo persistido)
- [x] 1.5 Criar extensão de derivação do rótulo de operação (despesa/receita/transferência/pagamento) a partir dos tipos de conta, num único ponto
- [x] 1.6 Criar extensão de saldo natural por conta (`Σ amount`) e de sinal de exibição por `AccountType`
- [x] 1.7 Remover/aposentar `signedImpact()` e a regra `Category.Type.isAccept` migrando-a para coerência de natureza de conta
- [x] 1.8 Criar erro tipado de desbalanceamento (`Σ ≠ 0`) para uso na fronteira de escrita

## 2. Persistência e migração (core/database)

- [x] 2.1 Criar `AccountEntity` (plano de contas) com `type` e `currency`, índices e FKs
- [x] 2.2 Criar `EntryEntity` (operationId, accountId, amount inteiro, currency) com índices e FK `onDelete=CASCADE` para operação
- [x] 2.3 Ajustar `OperationEntity` removendo `kind` persistido e os campos redundantes (`target*`, `sourceAccountId`) substituídos por entries <!-- FEITO: kind derivado (coluna dropada); sourceAccountId/targetCreditCardId/targetInvoiceId removidos (rebuild da tabela), derivados dos legs no mapper; observeBy deriva os filtros via EXISTS — mesmo conjunto -->
- [x] 2.4 Escrever a `Migration` versionada: promover cada conta/cartão/categoria a `AccountEntity`; semear contas `EQUITY` de sistema
- [x] 2.5 Migrar cada `Transaction` legada para entries balanceadas, sintetizando a contrapartida (categoria → `INCOME`/`EXPENSE`; ajuste → `EQUITY`)
- [x] 2.6 Converter valores `Double` para inteiro na menor unidade durante a migração
- [x] 2.7 Atualizar DAOs (`TransactionDao`/novo `EntryDao`, `OperationDao`) e mappers `Entity`↔`Domain` <!-- EntryDao/AccountDao/CreditCardDao + AccountMapper feitos; escrita de entries via LedgerEntryWriter; mapper Entry→domínio na leitura (Seção 4) -->
- [x] 2.8 Teste de migração: saldo de cada conta pós-migração idêntico ao pré-migração (base de amostra representativa)
- [x] 2.9 Teste de migração: toda operação migrada satisfaz `Σ = 0` por moeda

## 3. Escrita: construção de operações balanceadas (use cases)

- [x] 3.1 Repositório de operações: validar `Σ = 0` por moeda num único ponto, retornando `Either` com erro tipado <!-- LedgerEntryWriter valida no createOperation; erro tipado LedgerError via UnbalancedOperationException (padrão Either.catch dos use cases) -->
- [x] 3.2 Reescrever `BuildTransactionUseCase` (despesa/receita/compra-cartão) como operações de duas entries balanceadas <!-- síntese no ponto único de escrita; contra de categoria/uncategorized -->
- [x] 3.3 Reescrever `TransferBetweenAccountsUseCase` como par `ASSET`↔`ASSET`
- [x] 3.4 Reescrever `PayInvoicePaymentUseCase` como par `ASSET`↔`LIABILITY`
- [x] 3.5 Reescrever `AdjustBalanceUseCase` (e variantes final/inicial) como par contra `EQUITY:Reconciliação`, preservando idempotência por data+conta <!-- update de ajuste existente roteado por updateOperation p/ reconstruir entries -->
- [x] 3.6 Reescrever `AddInstallmentUseCase` gerando N operações balanceadas por fatura <!-- cada operação passa por createOperation -->
- [x] 3.7 Ajustar `ConfirmRecurringUseCase` para materializar ocorrências como operações balanceadas <!-- via createOperation -->
- [x] 3.8 Testes de construção: cada tipo de operação produz entries que somam zero; operação desbalanceada é rejeitada

## 4. Leitura: relatórios sobre o razão (use cases)

- [x] 4.1 Reescrever `CalculateBalanceUseCase` como `Σ entries` da conta até a data-alvo <!-- forma suspend (AdjustBalance) via IEntryRepository; forma pura mantida transaction-based na coexistência da UI -->
- [x] 4.2 Reescrever `CalculateInvoiceUseCase` sobre entries da conta `LIABILITY`, removendo o `-signedImpact()` invertido <!-- via entries.invoiceId; -signedImpact removido -->
- [x] 4.3 Implementar patrimônio líquido (`Σ ASSET − Σ LIABILITY`) pelo mesmo mecanismo <!-- IEntryRepository.netWorth + teste; fiação no dashboard = Seção 5 -->
- [ ] 4.4 Reescrever gasto por categoria como `Σ entries` da conta `INCOME`/`EXPENSE` <!-- pendente: requer ponte Category.accountId no domínio + mapper -->
- [x] 4.5 Remover ramos condicionais de tratamento especial de `ADJUSTMENT` em relatórios <!-- cálculo agora uniforme (signedCents/entries), sem ramo de ajuste; refs restantes são rótulos de fachada -->
- **Novo:** `signedImpact()` removido; reads unificados na convenção débito-positivo (`signedCents`/entries)
- [x] 4.6 Testes de leitura: saldo, fatura, gasto por categoria e patrimônio líquido conferem com casos conhecidos <!-- EntryRepositoryTest (fatura/patrimônio/saldo) + Migration7To8Test (paridade) -->
- **Novo (habilitado nesta seção):** `entries.invoiceId` (sub-razão de fatura) + `IEntryRepository` (mecanismo único de leitura do razão)

## 5. UI e fachada (features)

- [x] 5.1 Manter a fachada de "categoria" na UI projetando contas `INCOME`/`EXPENSE` <!-- Category.accountId (domínio+mapper) liga à conta-razão; UI de fachada inalterada; corrige wipe do link na edição -->
- [x] 5.2 Manter a fachada de "cartão"/fatura projetando conta `LIABILITY` <!-- CreditCard.accountId (domínio+mapper) idem -->
- [x] 5.3 Aplicar inversão de sinal por `AccountType` na exibição (contas credoras leem positivo) <!-- fatura via IEntryRepository.invoiceOwed (inverte LIABILITY); AccountType.displayBalance disponível; convenção débito-positivo unificada -->
- [~] 5.4 Ajustar telas que hoje filtram por `Kind`/`Type` para usar a derivação centralizada (dashboard, transactions, report, budgets) <!-- fatura entry-based; agregados de saldo na convenção débito-positivo; Transaction ainda é a unidade de UI (breakdowns por tipo) -->
- [x] 5.5 Verificar paridade visual/funcional: nenhum fluxo de usuário muda nesta fase <!-- verificado em Android/Desktop/iOS pelo usuário -->

## 6. Verificação e limpeza

- [ ] 6.1 `./gradlew allTests` e `./gradlew check` verdes <!-- testDebugUnitTest + jvmTest (todos os módulos) verdes; iOS/allTests e check completos não rodados nesta sessão -->
- [x] 6.2 Verificação manual em Android e Desktop: saldos, faturas, ajustes e relatórios idênticos ao comportamento anterior <!-- confirmado pelo usuário em Android/Desktop/iOS -->
- [~] 6.3 Remover entidades/colunas legadas (`Transaction.Type`, `Target`, `Operation.Kind`) após confirmação de paridade <!-- FEITO: signedImpact(), Operation.Kind (col. dropada) e Transaction.Target (derivado de `account != null`). RESTA Transaction.Type: diferente dos demais, NÃO é derivável — é informação primária. Uma despesa sem categoria, uma receita sem categoria e um ajuste têm todos category=null numa conta; só o Type os distingue no nível da perna. Recuperá-lo exige ler as entries da operação (sinal + tipo da conta-contra), i.e. a UI lendo entries — o redesenho do task 1.4, não uma derivação/limpeza. -->
- **Fronteira de escopo:** removido tudo que é derivável preservando comportamento — `signedImpact`, `Operation.Kind`, `Transaction.Target`, e os pointers denormalizados de `OperationEntity`. O único legado restante, `Transaction.Type`, carrega informação (a direção/ajuste de lançamentos sem categoria) que só existe nele no nível da perna; removê-lo requer migrar a UI para ler `Entry` (task 1.4) — mudança dedicada seguinte.
- [x] 6.4 Atualizar documentação de arquitetura (CLAUDE.md / feature READMEs) refletindo o razão balanceado

## Ajustes imprevistos (durante a implementação)

- **[Regressão CRÍTICA corrigida — pega em code review independente]** Ao tornar `Transaction.target` derivado, usei `creditCard != null`. Mas a perna que paga a fatura carrega `account` + `creditCard` + `invoice` (para ligar ao cartão pago), então ela derivava **CREDIT_CARD** erroneamente: o pagamento caía na conta do cartão em vez do banco (**a conta pagadora nunca era debitada**), e ambas as pernas marcavam `invoiceId`, cancelando o `invoiceOwed` (**fatura não abatia após pagar** — dado novo E migrado). Corrigido (commit `Fix(Ledger): correct invoice-payment regression`): derivação passa a `account != null` (compra no cartão tem `account=null`), `invoiceId` só na perna `LIABILITY` (writer e migração). Cobertura adicionada: pagamento no `LedgerEntryWriterTest` e compra+pagamento no `Migration7To8Test`. Também eliminou a contaminação dos overviews de cartão.
- **[Ponte de correção]** `Category.accountId`/`CreditCard.accountId` threaded pelos mappers: sem isso, editar categoria/cartão zerava o link para a conta-razão (bug latente da coexistência).
- **[Cobertura]** `core:model` ganhou `commonTest` (`LedgerTest`) cobrindo `deriveOperationLabel` (artefato da task 1.5), `isBalanced`/`Σ=0 por moeda`, `naturalBalanceOf` e a inversão de sinal por `AccountType` — antes puras e não testadas.
- **[Invariante por moeda — 2ª rodada de review]** A guarda do ponto de escrita (`LedgerEntryWriter.writeEntries`) somava um escalar plano, não `Σ=0 **por moeda**` como a spec `balanced-ledger` exige. Inofensivo hoje (toda entry usa `BASE_CURRENCY`), mas contradizia o texto da spec e a razão de o campo `currency` existir (FX futuro: `+100 BRL / −100 USD` passaria numa soma plana). Corrigido: a guarda agrupa por `currency` e exige zero em cada. (O pré-check `validate()` sobre `Transaction` permanece plano por ser mono-moeda; o ponto autoritativo é o `writeEntries`.)

## Limitações conhecidas (CAPS) — "documentado ≠ resolvido"

> Estes são débitos/limites REAIS, não "está tudo bem". Cada um traz o motivo e o custo de resolver.

- **[CAP-1 · gap de spec] Gasto por categoria ainda é legado (task 4.4).** `CalculateCategorySpendingUseCase` soma `Transaction` in-memory, não `Σ entries` da conta `INCOME`/`EXPENSE` (spec `ledger-reporting`). **Paridade-correto hoje** (entries espelham transações). Motivo de não estar feito: o use case vive em `feature/categories/api` e `IEntryRepository` em `feature/transactions/api` — a regra **api ⊄ api** proíbe a dependência direta; a correção exige mover/reestruturar o módulo, e é coerente fazê-la junto com a virada dos leitores para `Entry`. **É um gap de spec assumido, não escopo cumprido.**
- **[CAP-2 · UI-boundary] `CalculateBalanceUseCase` forma in-memory continua legada.** Usada pela lista de transações, que computa saldo da lista de `Transaction` já carregada. Mesmo limite do `Transaction.Type`: virá-la exige a tela ler `Entry`. Paridade-correta.
- **[CAP-3 · robustez, baixo risco] `LedgerEntryWriter.ensureSystemAccount` é check-then-act sem constraint de unicidade.** Duas contas `EQUITY:Reconciliação` poderiam ser criadas sob corrida (single-user, ajustes concorrentes — improvável). **O fix "óbvio" (índice `UNIQUE(name,type)`) é ELE PRÓPRIO ARRISCADO**: contas de usuário não garantem nome único, então a migração poderia crashar em dispositivos reais. O fix seguro é semear as contas de sistema também no fresh-install (hoje só a migração semeia) — mudança deliberada, deixada como débito para não empilhar risco. (Alternativa também segura: um índice UNIQUE **parcial** escopado a `type='EQUITY'`, mas o Room não o expõe via `@Index` — exigiria SQL cru + tolerância de schema.)
- **[CAP-4 · invariante por convenção] Saldo por entries usa a data da OPERAÇÃO; a forma in-memory usa a data da TRANSAÇÃO.** Coincidem por construção (todo `createOperation` passa a mesma data; `updateOperation` sincroniza), então não há divergência nos dados atuais — mas não há GUARDA que force a invariante. Se um caller futuro divergir as datas, os dois caminhos de saldo divergem por 1 mês na fronteira.
- **[CAP-5 · legado remanescente] `Transaction.Type` + a unidade de UI `Transaction`.** Ver 6.3: `Type` é informação primária (não derivável no nível da perna); sua remoção é o redesenho "UI lê `Entry`" (task 1.4), mudança dedicada seguinte.
- **[CAP-6 · duplicação] Duas derivações do "tipo" de operação coexistem:** `Operation.kind` (por `Transaction.target`, consumida pela UI) e `deriveOperationLabel` (por tipos de conta, a nativa do razão — agora testada, consumidor chega com o CAP-5). Convergem quando a UI ler `Entry`.
- **[CAP-7 · coexistência] Double-write com leitores metade-razão/metade-legado.** Fatura/saldo(ajuste)/patrimônio vêm do razão; category-spending e listas vêm do legado. São paridade-corretos por espelhamento, mas a "paridade" não é garantida por teste automatizado (foi verificada manualmente em device). Enquanto durar a coexistência, é um risco de divergência a vigiar.
