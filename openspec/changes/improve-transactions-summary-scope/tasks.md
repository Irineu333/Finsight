# Tasks

## 1. Razão: leitura simétrica (`core/ledger`)

- [x] 1.1 Generalizar `EntryDao.assetsBalanceUpToMonth` para receber a natureza de conta (`type`), preservando o resultado atual quando chamada com `ASSET`
- [x] 1.2 Expor em `IEntryRepository` o saldo acumulado até um mês por `AccountType`, e fazer `balanceUpTo(target, accountId = null)` delegar a ela com `ASSET` — um caminho só (D4)
- [x] 1.3 Acrescentar o ramo `eq = 1` a `EntryDao.liabilityMonthTotals` e o campo `adjustment` a `LiabilityMonthFlows`, espelhando `assetMonthTotals`/`AssetMonthFlows` (D3)
- [x] 1.4 Atualizar os fakes de `IEntryRepository` nos testes de `creditcards`, `dashboard` e `transactions` (ou nascer o método com implementação default para não quebrá-los)
- [x] 1.5 Teste: saldo acumulado de `LIABILITY` até o mês pelo mesmo mecanismo de `ASSET`
- [x] 1.6 Teste: ajuste de fatura é reportado em `LiabilityMonthFlows.adjustment`, com sinal preservado
- [x] 1.7 Verificar que os testes existentes de saldo (`EntryRepositoryTest`, dashboard) permanecem verdes sem mudança de expectativa

## 2. Escopo e estado (`feature/transactions/impl`)

- [x] 2.1 Criar `TransactionScope` (três valores) como estado de tela e `TransactionsAction.SelectScope`, com valor inicial geral (D7)
- [x] 2.2 Recortar a lista pelo escopo: presença de perna da natureza do perímetro (D6 — sem tocar no mapeamento do item)
- [x] 2.3 Substituir `BalanceOverview` por uma `sealed` de três variantes tipadas, uma por escopo (D5)
- [x] 2.4 Compor as linhas do escopo "contas" a partir dos agregados atuais — sem alteração de valor, incluindo a linha "Faturas"
- [x] 2.5 Compor as linhas do escopo "cartões": dívida inicial/final por natureza, gastos, pagamentos e ajustes
- [x] 2.6 Compor as linhas do escopo geral: líquido inicial/final, entradas, saídas agregando conta e cartão, ajustes agregados e pagamento como linha informativa fora da soma (D1)
- [x] 2.7 Remover `TransactionsUiState.CreditCardOverview` (código morto)
- [x] 2.8 Restringir o filtro de alvo ao escopo geral (D7)

## 3. UI (`core/designsystem`, `core/resources`, `feature/transactions/impl`)

- [x] 3.1 Dar ao `MonthSelector` um modo **sem as setas `‹ ›` e com o `▾`** — hoje as setas são incondicionais e `showPickerChevron` controla apenas o `▾` (D8)
- [x] 3.2 Adicionar as strings novas (rótulos dos três escopos, dívida inicial/final, gastos, pagamentos, líquido inicial/final) em todos os idiomas
- [x] 3.3 Levar período e escopo para o topo do `SummaryCard`, como dois chips lado a lado com a **mesma interação** (ambos abrem menu ao toque)
- [x] 3.4 Fazer o `SummaryCard` renderizar a variante de resumo do escopo, incluindo o estilo da linha informativa (sem sinal, tom secundário)
- [x] 3.5 Remover o `topBar` de `TransactionsScreen`, preservando o `statusBarsPadding` que hoje vive nele
- [x] 3.6 Revisar a animação de troca de conteúdo do card: os chips permanecem ancorados enquanto o corpo muda de escopo

## 4. Testes de comportamento

- [x] 4.1 Identidade `saldo(P, mês) = saldo(P, mês anterior) + Σ fluxos exibidos`, um teste por escopo, incluindo o mês com pagamento de fatura (contas) e com ajuste de fatura (cartões e geral)
- [x] 4.2 Neutralidade do pagamento de fatura no escopo geral: o líquido é idêntico com e sem ele — construindo o caso a partir de um pagamento real (ver nota de D3)
- [x] 4.3 Transferência entre contas não altera o fechamento do escopo "contas"
- [x] 4.4 A lista de cada escopo contém exatamente as transações com perna do perímetro
- [x] 4.5 Um filtro de lista (categoria) não altera nenhuma linha do resumo
- [x] 4.6 O escopo "cartões" recorta por data do lançamento, não por ciclo de fatura
- [x] 4.7 Adaptar `TransactionsViewModelCharacterizationTest` para caracterizar o escopo "contas", valor a valor, como rede de segurança da change

## 5. Fechamento

- [ ] 5.1 Verificar a tela no app (Android ou Desktop) nos três escopos, conferindo a aritmética de cada card contra a lista
- [x] 5.2 Decidir Q1 (o escopo persiste entre visitas?) e Q2 (rótulo do escopo geral) e registrar o resultado no `design.md`
