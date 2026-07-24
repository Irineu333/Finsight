# Tasks

## 1. Razão: leitura simétrica (`core/ledger`)

- [ ] 1.1 Generalizar `EntryDao.assetsBalanceUpToMonth` para receber a natureza de conta (`type`), preservando o resultado atual quando chamada com `ASSET`
- [ ] 1.2 Expor em `IEntryRepository` o saldo acumulado até um mês por `AccountType`, e fazer `balanceUpTo(target, accountId = null)` delegar a ela com `ASSET` — um caminho só (D4)
- [ ] 1.3 Acrescentar o ramo `eq = 1` a `EntryDao.liabilityMonthTotals` e o campo `adjustment` a `LiabilityMonthFlows`, espelhando `assetMonthTotals`/`AssetMonthFlows` (D3)
- [ ] 1.4 Teste: saldo acumulado de `LIABILITY` até o mês pelo mesmo mecanismo de `ASSET`
- [ ] 1.5 Teste: ajuste de fatura é reportado em `LiabilityMonthFlows.adjustment` e `saldo final = saldo inicial + fluxos` vale para `LIABILITY`
- [ ] 1.6 Verificar que os testes existentes de saldo (`EntryRepositoryTest`, dashboard) permanecem verdes sem mudança de expectativa

## 2. Apresentação: perspectiva por natureza (`core/ui`)

- [ ] 2.1 Permitir que `toTransactionUi` receba a perspectiva como natureza de conta, além de `accountId`, mantendo o retorno `null` quando não houver perna correspondente (D6)
- [ ] 2.2 Teste: o mesmo pagamento de fatura mapeado sob `ASSET` e sob `LIABILITY` produz sinais opostos
- [ ] 2.3 Teste: transação sem perna da natureza pedida é omitida, sem falha de leitura

## 3. Escopo na feature (`feature/transactions/api`)

- [ ] 3.1 Criar `TransactionScope` (`@Serializable`, três valores) e o seu `NavType`, espelhando `TransactionLabelNavType`
- [ ] 3.2 Trocar `TransactionsRoute.filterTarget: TransactionTarget?` por `scope: TransactionScope?` e atualizar o `typeMap` em `TransactionsGraph`
- [ ] 3.3 Remover `TransactionTargetNavType` se não restar consumidor

## 4. ViewModel e estado (`feature/transactions/impl`)

- [ ] 4.1 Adicionar o escopo ao estado da tela e `TransactionsAction.SelectScope`, com valor inicial `ALL` ou o da rota (D7)
- [ ] 4.2 Fazer a lista responder ao escopo — resolver Q1 na implementação: filtro por predicado ou pelo próprio mapeamento (D6)
- [ ] 4.3 Substituir `BalanceOverview` por um resumo como lista de linhas resolvidas (rótulo, valor, sinal, papel: abertura/fluxo/informativa/fechamento), produzido por escopo (D5)
- [ ] 4.4 Compor as linhas do escopo "contas" a partir dos agregados atuais — sem alteração de valor
- [ ] 4.5 Compor as linhas do escopo "cartões": dívida inicial/final por natureza, gastos, pagamentos e ajustes
- [ ] 4.6 Compor as linhas do escopo geral: líquido inicial/final, entradas, saídas agregando conta e cartão, ajustes agregados e pagamento como linha informativa fora da soma (D1)
- [ ] 4.7 Remover `TransactionsUiState.CreditCardOverview` (código morto)
- [ ] 4.8 Restringir o filtro de alvo ao escopo geral (D7)

## 5. UI (`feature/transactions/impl`, `core/resources`)

- [ ] 5.1 Adicionar as strings novas (rótulos dos três escopos, dívida inicial/final, gastos, pagamentos, líquido inicial/final) em todos os idiomas
- [ ] 5.2 Levar período e escopo para o topo do `SummaryCard`, como dois chips lado a lado; o de período sem as setas `‹ ›`, com o picker no toque do rótulo (D8)
- [ ] 5.3 Fazer o `SummaryCard` renderizar a lista de linhas do resumo, sem conhecer escopo, incluindo o estilo da linha informativa (sem sinal, tom secundário)
- [ ] 5.4 Remover o `topBar` de `TransactionsScreen` e ajustar o espaçamento resultante
- [ ] 5.5 Revisar a animação de troca de conteúdo do card: os chips permanecem ancorados enquanto o corpo muda de escopo

## 6. Navegação de origem (`feature/dashboard/impl`)

- [ ] 6.1 Trocar `TransactionTarget?` por `TransactionScope?` na assinatura de `openTransactions`
- [ ] 6.2 Fazer os dois cards de saldo concreto navegarem com `ACCOUNTS`, para que o resumo de destino concorde com o card de origem

## 7. Testes de comportamento

- [ ] 7.1 Identidade `fechamento = abertura + entradas − saídas + ajustes`, um teste por escopo, incluindo o mês com ajuste de fatura
- [ ] 7.2 Neutralidade do pagamento de fatura no escopo geral: o líquido é idêntico com e sem ele
- [ ] 7.3 Transferência entre contas não altera o fechamento do escopo "contas"
- [ ] 7.4 A lista de cada escopo contém exatamente as transações com perna do perímetro
- [ ] 7.5 Um filtro de lista (categoria) não altera nenhuma linha do resumo
- [ ] 7.6 Adaptar `TransactionsViewModelCharacterizationTest` para caracterizar o escopo "contas", valor a valor, como rede de segurança da change

## 8. Fechamento

- [ ] 8.1 Rodar `./gradlew allTests`
- [ ] 8.2 Verificar a tela no app (Android ou Desktop) nos três escopos, conferindo a aritmética de cada card contra a lista
- [ ] 8.3 Decidir Q2 (o escopo persiste entre visitas?) e Q3 (rótulo do escopo geral) e registrar o resultado no `design.md`
