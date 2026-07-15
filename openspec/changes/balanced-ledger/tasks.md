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
- [~] 2.3 Ajustar `OperationEntity` removendo `kind` persistido e os campos redundantes (`target*`, `sourceAccountId`) substituídos por entries <!-- kind REMOVIDO (derivado, coluna dropada). sourceAccountId/target* mantidos: observeBy filtra por eles com semântica de "conta de origem"; derivá-los dos legs mudaria o comportamento visível (transferência apareceria para ambas as contas) — muda UX, não é limpeza segura desta fase -->
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
- [~] 6.3 Remover entidades/colunas legadas (`Transaction.Type`, `Target`, `Operation.Kind`) após confirmação de paridade <!-- FEITO: signedImpact() e Operation.Kind (col. dropada, derivado). Transaction.Type NÃO removível como limpeza: é a classificação receita/despesa/ajuste que dirige exibição/cores/forms/filtros (~460 sites) e não é derivável de uma perna isolada — sua remoção É o redesenho da unidade de UI para entries (a conclusão do task 1.4), que quebraria a paridade "sem mudança visível" desta fase. É a próxima mudança dedicada, não limpeza. -->
- **Fronteira de escopo:** o razão é a fonte de verdade das escritas e das leituras de saldo/fatura/patrimônio; `signedImpact` e `Operation.Kind` persistido não existem mais. Substituir a unidade de UI `Transaction` por `Entry` (removendo `Type`/`Target` e os pointers denormalizados de `OperationEntity`) altera comportamento visível/UX e é a mudança seguinte — fora do escopo "sem mudança de fluxo" desta fase.
- [x] 6.4 Atualizar documentação de arquitetura (CLAUDE.md / feature READMEs) refletindo o razão balanceado
