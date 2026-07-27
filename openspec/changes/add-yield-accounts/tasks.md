## 1. Persistência e migração

- [x] 1.1 Adicionar `yieldsInterest: Boolean = false` a `Account` (`:core:ledger`) e a coluna correspondente em `AccountEntity`, com KDoc registrando que ela governa apenas afordância e não participa de soma alguma (design D2)
- [x] 1.2 Adicionar `systemKey: String?` a `CategoryEntity` (`:core:database`) e a `Category` (`:core:model`), com KDoc registrando que a identificação é por chave para que o usuário possa renomear a categoria livremente (design D3)
- [x] 1.3 Escrever a migração v10 → v11: `accounts.yieldsInterest INTEGER NOT NULL DEFAULT 0` e `categories.systemKey TEXT DEFAULT NULL`, sem backfill
- [x] 1.4 Cobrir a migração com teste, verificando que contas e categorias existentes sobrevivem com os defaults e que nenhuma transação é reescrita
- [x] 1.5 Atualizar `AccountMapper` (`:core:ledger`) e `CategoryMapper` (`feature/categories/impl`) para os campos novos

## 2. Leitura no razão

- [x] 2.1 Adicionar `yield: Long` a `AccountPeriodTotals` e a `AssetMonthTotals` (`EntryDao`), documentando que a linha nova reparticiona `income` e não a acresce
- [x] 2.2 Adicionar o parâmetro `yieldDimensionId: Long?` e o flag `yl` ao subselect de `accountPeriodTotals`, subtraindo o rendimento de `income` (design D5)
- [x] 2.3 Aplicar o mesmo tratamento a `assetMonthTotals`
- [x] 2.4 Propagar o parâmetro por `EntryRepository`/`IEntryRepository`, mantendo a assinatura em vocabulário de razão — dimensão, período, natureza — sem nomear categoria ou rendimento
- [x] 2.5 Testar em `:core:ledger` que com `yieldDimensionId` nulo os totais são idênticos aos de hoje, que o rendimento sai de `income` e entra em `yield`, e que uma receita comum na mesma data e conta permanece em `income`

## 3. A categoria de sistema

- [x] 3.1 Estender `ICategoryRepository` (`feature/categories/api`) com a busca por `systemKey`
- [x] 3.2 Criar o use case que garante a existência da categoria de rendimentos sob demanda, idempotente, retornando a categoria e a sua `dimensionId`
- [x] 3.3 Remover o template "Investimentos" de `CreateDefaultCategoriesUseCase` e ajustar o seu teste
- [x] 3.4 Testar que a segunda conta declarada não cria segunda categoria, que renomear não quebra a identificação, e que sem conta declarada a categoria não existe

## 4. Retirabilidade da categoria

- [x] 4.1 Adicionar a `IAccountRepository` (`feature/accounts/api`) a consulta "existe conta com rendimento habilitado"
- [x] 4.2 Somar o quarto guard à resolução de `CategoryRetirability`, sem introduzir caso novo ao par `Deletable`/`MustArchive` (design D4)
- [x] 4.3 Acrescentar o motivo de recusa ao erro tipado de retirada, com texto que indique desligar o rendimento das contas
- [x] 4.4 Testar recusa com conta declarada, remoção liberada após desligar a última, e que arquivar a categoria não interrompe o rendimento das contas que já a usam

## 5. Escrita do rendimento

- [x] 5.1 Criar o use case de lançamento de rendimento: garante a categoria (3.2) e escreve `TransactionIntent` com perna `INCOME` na conta e `ContraLeg(AccountType.INCOME, dimensionId)`
- [x] 5.2 Testar que cada chamada cria uma transação nova, que dois rendimentos na mesma data somam, que nenhuma perna `EQUITY` é criada, e que uma receita comum na mesma data permanece intacta
- [x] 5.3 Verificar por teste que `AdjustBalanceUseCase` permanece inalterado numa conta que rende — ajuste continua gerando contrapartida de reconciliação

## 6. Configuração da conta

- [x] 6.1 Adicionar o interruptor de rendimento a `AccountFormUiState`/`Action`/`ViewModel`/`Modal`
- [x] 6.2 Ligar o interruptor à garantia da categoria (3.2), de modo que a primeira declaração a crie antes de concluir o salvamento
- [x] 6.3 Testar que declarar e desdeclarar não altera lançamento nem saldo algum

## 7. Modal de lançamento

- [x] 7.1 Criar `LaunchYieldModal` com `UiState`/`Action`/`ViewModel`: data (hoje por padrão) e valor, seguindo o padrão de `EditAccountBalanceModal`
- [x] 7.2 Registrar o modal e os use cases novos em `AccountsModule`
- [x] 7.3 Testar as transições de `UiState` e o desfecho de sucesso e de erro

## 8. A linha em Contas

- [x] 8.1 Adicionar `yield` a `AccountUi` e ao seu mapper
- [x] 8.2 Adicionar a linha de rendimento a `AccountCard` (`:core:ui`), logo após a de entradas, usando o `AccountSummaryRow` clicável que já existe, exibida quando a conta declara render — inclusive com valor zero
- [x] 8.3 Definir a cor de rendimento em `:core:designsystem`, irmã de `Income` e distinguível dela (design, questão em aberto)
- [x] 8.4 Ligar o clique da linha ao modal de lançamento

## 9. A linha em Transações

- [x] 9.1 Adicionar `yield` a `BalanceOverview.Accounts` e `.Overall`, atualizando o KDoc da identidade aritmética de cada uma
- [x] 9.2 Exibir a linha em `AccountsBody` e `OverallBody` de `SummaryCard`, e não em `CardsBody` (design D7)
- [x] 9.3 Testar que a coluna fecha com a linha nova e que ela não aparece quando nenhuma conta do perímetro declara render

## 10. Vocabulário

- [x] 10.1 Renomear `accounts_income`/`accounts_expenses` para o par segregado — "Entradas/Saídas" em pt, "Money In/Money Out" em en
- [x] 10.2 Corrigir `summary_card_income`/`summary_card_outgoing` em en, hoje o par misto "Income/Outgoing", para "Money In/Money Out"
- [x] 10.3 Renomear `balance_card_account_income`/`_expense` para o par não segregado — "Receitas/Despesas na conta" em pt, "Income/Expenses in the account" em en
- [x] 10.4 Corrigir o par misto `transactions_filter_type_income`/`_expense` ("Entrada"/"Despesa") para "Receita"/"Despesa"
- [x] 10.5 Acrescentar as strings novas — linha de rendimento, título e campos do modal, rótulo do interruptor, motivo de recusa da categoria — em pt **e** en, verificando a paridade das duas listas ao final
- [x] 10.6 Conferir que o relatório permanece com o par não segregado, sem alteração

## 11. Fechamento

- [x] 11.1 Registrar o evento de analytics de lançamento de rendimento
- [ ] 11.2 Verificar em app rodando: declarar rendimento numa conta, lançar, conferir que a linha aparece nos dois cartões, que a coluna fecha, e que o rendimento consta no relatório dentro de receitas
