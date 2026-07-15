## 1. Modelo de domínio (core/model)

- [ ] 1.1 Criar `AccountType` (`ASSET`, `LIABILITY`, `INCOME`, `EXPENSE`, `EQUITY`) em `core/model/domain/model`
- [ ] 1.2 Criar/estender `Account` com `type: AccountType` e `currency`, cobrindo conta, cartão, categoria e contas de sistema (`EQUITY`)
- [ ] 1.3 Criar `Entry` (referência a `Account`, `amount` assinado em menor unidade, `currency`) substituindo o par `Transaction.Type`/`Target`
- [ ] 1.4 Redefinir `Operation` como conjunto de `Entry` (mín. 2 pernas), com `Kind` derivado (extensão pura, não campo persistido)
- [ ] 1.5 Criar extensão de derivação do rótulo de operação (despesa/receita/transferência/pagamento) a partir dos tipos de conta, num único ponto
- [ ] 1.6 Criar extensão de saldo natural por conta (`Σ amount`) e de sinal de exibição por `AccountType`
- [ ] 1.7 Remover/aposentar `signedImpact()` e a regra `Category.Type.isAccept` migrando-a para coerência de natureza de conta
- [ ] 1.8 Criar erro tipado de desbalanceamento (`Σ ≠ 0`) para uso na fronteira de escrita

## 2. Persistência e migração (core/database)

- [ ] 2.1 Criar `AccountEntity` (plano de contas) com `type` e `currency`, índices e FKs
- [ ] 2.2 Criar `EntryEntity` (operationId, accountId, amount inteiro, currency) com índices e FK `onDelete=CASCADE` para operação
- [ ] 2.3 Ajustar `OperationEntity` removendo `kind` persistido e os campos redundantes (`target*`, `sourceAccountId`) substituídos por entries
- [ ] 2.4 Escrever a `Migration` versionada: promover cada conta/cartão/categoria a `AccountEntity`; semear contas `EQUITY` de sistema
- [ ] 2.5 Migrar cada `Transaction` legada para entries balanceadas, sintetizando a contrapartida (categoria → `INCOME`/`EXPENSE`; ajuste → `EQUITY`)
- [ ] 2.6 Converter valores `Double` para inteiro na menor unidade durante a migração
- [ ] 2.7 Atualizar DAOs (`TransactionDao`/novo `EntryDao`, `OperationDao`) e mappers `Entity`↔`Domain`
- [ ] 2.8 Teste de migração: saldo de cada conta pós-migração idêntico ao pré-migração (base de amostra representativa)
- [ ] 2.9 Teste de migração: toda operação migrada satisfaz `Σ = 0` por moeda

## 3. Escrita: construção de operações balanceadas (use cases)

- [ ] 3.1 Repositório de operações: validar `Σ = 0` por moeda num único ponto, retornando `Either` com erro tipado
- [ ] 3.2 Reescrever `BuildTransactionUseCase` (despesa/receita/compra-cartão) como operações de duas entries balanceadas
- [ ] 3.3 Reescrever `TransferBetweenAccountsUseCase` como par `ASSET`↔`ASSET`
- [ ] 3.4 Reescrever `PayInvoicePaymentUseCase` como par `ASSET`↔`LIABILITY`
- [ ] 3.5 Reescrever `AdjustBalanceUseCase` (e variantes final/inicial) como par contra `EQUITY:Reconciliação`, preservando idempotência por data+conta
- [ ] 3.6 Reescrever `AddInstallmentUseCase` gerando N operações balanceadas por fatura
- [ ] 3.7 Ajustar `ConfirmRecurringUseCase` para materializar ocorrências como operações balanceadas
- [ ] 3.8 Testes de construção: cada tipo de operação produz entries que somam zero; operação desbalanceada é rejeitada

## 4. Leitura: relatórios sobre o razão (use cases)

- [ ] 4.1 Reescrever `CalculateBalanceUseCase` como `Σ entries` da conta até a data-alvo
- [ ] 4.2 Reescrever `CalculateInvoiceUseCase` sobre entries da conta `LIABILITY`, removendo o `-signedImpact()` invertido
- [ ] 4.3 Implementar patrimônio líquido (`Σ ASSET − Σ LIABILITY`) pelo mesmo mecanismo
- [ ] 4.4 Reescrever gasto por categoria como `Σ entries` da conta `INCOME`/`EXPENSE`
- [ ] 4.5 Remover ramos condicionais de tratamento especial de `ADJUSTMENT` em relatórios
- [ ] 4.6 Testes de leitura: saldo, fatura, gasto por categoria e patrimônio líquido conferem com casos conhecidos

## 5. UI e fachada (features)

- [ ] 5.1 Manter a fachada de "categoria" na UI projetando contas `INCOME`/`EXPENSE`
- [ ] 5.2 Manter a fachada de "cartão"/fatura projetando conta `LIABILITY`
- [ ] 5.3 Aplicar inversão de sinal por `AccountType` na exibição (contas credoras leem positivo)
- [ ] 5.4 Ajustar telas que hoje filtram por `Kind`/`Type` para usar a derivação centralizada (dashboard, transactions, report, budgets)
- [ ] 5.5 Verificar paridade visual/funcional: nenhum fluxo de usuário muda nesta fase

## 6. Verificação e limpeza

- [ ] 6.1 `./gradlew allTests` e `./gradlew check` verdes
- [ ] 6.2 Verificação manual em Android e Desktop: saldos, faturas, ajustes e relatórios idênticos ao comportamento anterior
- [ ] 6.3 Remover entidades/colunas legadas (`Transaction.Type`, `Target`, `Operation.Kind`) após confirmação de paridade
- [ ] 6.4 Atualizar documentação de arquitetura (CLAUDE.md / feature READMEs) refletindo o razão balanceado
