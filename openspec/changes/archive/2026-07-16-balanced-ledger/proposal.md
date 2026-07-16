## Why

O modelo de tipificação de transações do Finsight é híbrido: despesa, receita e compra no cartão são lançamentos de **perna única** (a contrapartida — a categoria — é fantasma, não existe no razão), enquanto transferência e pagamento de fatura usam duas pernas. Consequências: não há como **garantir estruturalmente que uma operação some zero**, o **ajuste de saldo** (`ADJUSTMENT`) é um lançamento mágico sem contrapartida que todo relatório precisa tratar como caso especial, o cálculo de fatura usa um `-signedImpact()` invertido ad-hoc, e **não há lugar natural para reembolso/estorno**. O roadmap (câmbio, múltiplas moedas, investimentos, contas de terceiros) *exige* partidas dobradas plenas — construir essas features sobre o modelo híbrido seria trabalho descartável. Esta é a mudança-fundação.

## What Changes

- **BREAKING** Introduzir um **plano de contas** unificado: toda "conta", "cartão" e "categoria" passa a ser uma `Account` com um `type` — `ASSET`, `LIABILITY`, `INCOME`, `EXPENSE` ou `EQUITY`.
- **BREAKING** Toda operação passa a ser um conjunto de **entries** (pernas) com valor **assinado** e uma **moeda** (`currency`), substituindo o par `Transaction.Type` + `Transaction.Target`.
- Estabelecer a **invariante estrutural `Σ entries = 0` por moeda**, validada na fronteira de escrita: nenhuma operação desbalanceada é persistível.
- Adotar a **convenção débito-positivo** (`+` = débito, `−` = crédito) internamente, invertida por tipo de conta na UI — o usuário nunca vê débito/crédito.
- **Derivar** o "tipo" da operação (despesa/receita/transferência/pagamento) dos tipos das contas envolvidas, em vez de persistir `Operation.Kind`.
- **Categoria vira conta `INCOME`/`EXPENSE`** internamente, preservando a fachada de "categoria" na UI.
- **Ajuste de saldo** deixa de ser um tipo especial: vira uma operação balanceada contra uma conta `EQUITY` de reconciliação.
- Unificar **saldo de conta, gasto por categoria e patrimônio líquido** num único mecanismo (`Σ entries da conta`), removendo o `-signedImpact()` invertido do cartão e o tratamento especial de `ADJUSTMENT`.
- **BREAKING** Migração Room: sintetizar a contrapartida de cada lançamento de perna única existente (categoria → entry `INCOME`/`EXPENSE`; ajuste → entry `EQUITY`), promover categorias e cartões ao plano de contas, e preservar todos os saldos.
- **Não-escopo** (mudanças futuras, habilitadas por esta): UI de reembolso; câmbio/FX e conta de trading; investimentos/commodities/lotes. O modelo de `entry` já nasce com `currency` para não haver retrabalho.

## Capabilities

### New Capabilities
- `chart-of-accounts`: plano de contas unificado — `Account` com `type` (ASSET/LIABILITY/INCOME/EXPENSE/EQUITY), a fachada de "categoria" e "cartão" como projeções sobre ele, e as contas de sistema (reconciliação/saldo inicial).
- `balanced-ledger`: o razão de partidas dobradas — operações como conjuntos de entries assinadas com moeda, a invariante `Σ = 0` por moeda validada na escrita, a convenção débito-positivo, e a derivação do tipo de operação a partir dos tipos de conta.
- `ledger-reporting`: derivação de saldo de conta, gasto por categoria e patrimônio líquido a partir do razão unificado (`Σ entries`), substituindo `signedImpact()`, o cálculo invertido de fatura e o tratamento especial de ajuste.

### Modified Capabilities
<!-- Nenhuma capability de spec existente muda em nível de requisito; as specs atuais tratam de módulos/navegação/UI, não do modelo de razão. -->

## Impact

- **Modelos (`core/model`)**: `Transaction` → `Entry` (assinado + `currency`), `Operation` (Kind derivado), novo `Account`/`AccountType`; `Category` e `CreditCard` passam a projetar sobre `Account`. `extension/Transaction.kt` (`signedImpact`) reescrito/removido.
- **Persistência (`core/database`)**: novas entidades/tabelas para plano de contas e entries; migração Room que sintetiza contrapartidas e preserva saldos (referência: `Migration3To4Test.kt`, skill `room-database`). Atenção a FKs `onDelete=CASCADE`/`SET_NULL`.
- **Use cases**: `CalculateBalanceUseCase`, `CalculateInvoiceUseCase`, `AdjustBalanceUseCase`, `TransferBetweenAccountsUseCase`, `PayInvoicePaymentUseCase`, `BuildTransactionUseCase`, `AddInstallmentUseCase` — todos reexpressos como construção/leitura de operações balanceadas.
- **Features tocadas**: `transactions`, `accounts`, `creditcards` (incl. invoices/installments), `categories`, `dashboard`, `report`, `budgets`, `recurring`.
- **UI**: inversão de sinal por tipo de conta na exibição; fachada de categoria preservada. Sem mudança de fluxo visível ao usuário nesta fase.
- **Testes**: novos testes de invariante (`Σ = 0`), de migração, e de equivalência de saldos pré/pós-migração.
