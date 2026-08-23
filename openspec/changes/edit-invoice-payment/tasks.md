## 1. O teto que desconsidera a própria operação

- [ ] 1.1 Adicionar `excluding: Long? = null` a `CalculateInvoiceUseCase`, computando o devido sobre as entries da dimensão que **não** pertencem àquela transação — compondo `dimensionOwedByCurrency` e `getEntriesByTransaction`, sem consulta nova no `IEntryRepository`. Atenção à unidade: entries são `Long` em centavos, o devido é `Double` em unidades.
- [ ] 1.2 Documentar em KDoc que o default `null` é o devido corrente — o caso de toda leitura que não é uma correção — e por que aqui o default é seguro, ao contrário de `contra` em `updateTransaction`.
- [ ] 1.3 Teste: fatura de R$ 800 com um pagamento de R$ 300 registrado. Sem `excluding`, o devido é R$ 500; com `excluding` daquela transação, é R$ 800.
- [ ] 1.4 Teste: `excluding` de uma transação que nada tem naquela fatura não altera o devido (o caso da fatura trocada).
- [ ] 1.5 Confirmar que os chamadores existentes de `CalculateInvoiceUseCase` seguem compilando e lendo o devido corrente.

## 2. A validação com dono único

- [ ] 2.1 Extrair de `AdvanceInvoicePaymentUseCase` as regras que a correção também aplica — valor > 0, contrapartida > 0 quando informada, fatura existe, `acceptsPartialPayment`, data na janela do ciclo, data não futura, devido > 0, valor ≤ teto — para um validador compartilhado, no molde de `ValidateTransferUseCase`.
- [ ] 2.2 Fazer `AdvanceInvoicePaymentUseCase` consumir o validador extraído, sem mudança de comportamento.
- [ ] 2.3 Rodar `AdvanceInvoicePaymentUseCaseTest` e confirmar que passa sem alteração — é o teste de caracterização desta extração.
- [ ] 2.4 Passar o teto ao validador pela leitura de 1.1, para que criação e correção o obtenham do mesmo dono.

## 3. `UpdateAdvanceInvoicePaymentUseCase`

- [ ] 3.1 Criar o caso de uso irmão de `AdvanceInvoicePaymentUseCase`: mesmas validações via 2.1, `excluding = transactionId` no teto, e reescrita em vez de criação.
- [ ] 3.2 Fazer `WriteInvoicePaymentUseCase` servir também à reescrita, mantendo o dono único da forma — duas pernas, dimensão só na do cartão — e a colheita de taxa depois da escrita.
- [ ] 3.3 Preservar o título que a transação carrega ao chamar `updateTransaction`, em vez de passar `null`.
- [ ] 3.4 Registrar o caso de uso em `UseCaseModule`.
- [ ] 3.5 Teste: corrigir o valor de um parcial altera o devido da fatura e preserva o id da transação.
- [ ] 3.6 Teste: corrigir apontando para outra fatura devolve o valor ao devido da anterior e o desconta da nova.
- [ ] 3.7 Teste: corrigir para R$ 700 um pagamento de R$ 300 numa fatura de R$ 800 é aceito — o cenário que o teto corrente recusava.
- [ ] 3.8 Teste: a correção sobre fatura que não aceita pagamento parcial é recusada, ainda que nenhuma tela a ofereça.
- [ ] 3.9 Teste: correção entre moedas reescreve as duas pernas monetárias, cada moeda soma zero, e a taxa vai ao acervo.

## 4. O modo de correção no ViewModel

- [ ] 4.1 Aceitar `transaction: Transaction?` em `InvoicePaymentViewModel` como única distinção entre os dois modos, no molde de `TransferBetweenAccountsViewModel`.
- [ ] 4.2 Filtrar `payableInvoices` por `acceptsPartialPayment` em modo de correção e por `acceptsPayment` em modo de criação, sem enumerar status na tela.
- [ ] 4.3 Pré-selecionar cartão, fatura e conta pagadora **sem** atravessar `selectCreditCard()`, que limpa a fatura e evaporaria o valor e a data pré-preenchidos.
- [ ] 4.4 Pré-preencher valor e valor de contrapartida a partir das pernas da operação, lidos pelos donos dessa leitura no razão e não escolhidos a dedo das entries.
- [ ] 4.5 Pré-preencher a data com a que a operação registra, e **não** rodar `settlementDateFor` na abertura.
- [ ] 4.6 Manter `settlementDateFor` no caminho da **troca** de cartão ou fatura, reposicionando a data quando ela não couber na janela nova.
- [ ] 4.7 Manter a limpeza do valor e da contrapartida ao trocar cartão ou fatura, como na criação.
- [ ] 4.8 Passar `excluding` ao ler o devido em modo de correção, para que o teto exibido seja o de 1.1.
- [ ] 4.9 Rotear a submissão para `UpdateAdvanceInvoicePaymentUseCase` em modo de correção.
- [ ] 4.10 Registrar o parâmetro novo do ViewModel em `CreditCardsModule`.

## 5. O que a superfície anuncia

- [ ] 5.1 Fazer `settles` ser sempre falso em modo de correção — o modo é o da operação escrita, não o do estado da fatura.
- [ ] 5.2 Fazer `label` do botão oferecer **salvar** em modo de correção, mantendo `paymentLabel` na criação.
- [ ] 5.3 Ajustar o cabeçalho para anunciar a correção, mantendo-o parado ao trocar a seleção como já faz hoje.
- [ ] 5.4 Passar a `canSubmitInvoicePayment` o teto de 1.1, para que oferta e permissão comparem o mesmo número.
- [ ] 5.5 Aceitar a `Transaction` em `InvoicePaymentModal` e repassá-la ao ViewModel.
- [ ] 5.6 Estender `InvoicePaymentSubmitEnablementTest` com o cenário 800/300 → 700 habilitado, e com o teto da fatura trocada.

## 6. A travessia da fronteira

- [ ] 6.1 Adicionar `editInvoicePaymentModal(transaction: Transaction): Modal` a `CreditCardsEntry`, ao lado de `invoicePaymentModal(invoiceId)`, com KDoc dizendo por que são dois membros e não um nulável.
- [ ] 6.2 Implementar o membro em `CreditCardsEntryImpl`.

## 7. O detalhe da operação

- [ ] 7.1 Substituir `TransactionLabel.PAYMENT -> false` (`ViewTransactionUiState.kt:164`) pela leitura do predicado do domínio sobre a fatura que a operação nomeia, e atualizar o comentário que hoje registra a exclusão deliberada.
- [ ] 7.2 Acrescentar o braço de `PAYMENT` na escolha do formulário em `ViewTransactionModal`, alcançando `CreditCardsEntry` por injeção como já faz com `AccountsEntry`.
- [ ] 7.3 Confirmar que os gates acima permanecem intactos: fatura `CLOSED`/`PAID` esconde correção e remoção com a mensagem existente, e conta arquivada idem.
- [ ] 7.4 Teste: um pagamento parcial sobre fatura `OPEN` oferece correção; sobre `CLOSED` e sobre `PAID`, não oferece nem correção nem remoção.
- [ ] 7.5 Teste: a correção de um pagamento abre o formulário de pagamento, e não o de transferência nem o de transação.

## 8. Recursos e analytics

- [ ] 8.1 Adicionar as chaves novas — o verbo "salvar" do formulário e o título do modo de correção — em `values/strings.xml` **e** `values-en/strings.xml`.
- [ ] 8.2 Adicionar o evento de analytics irmão de `AdvanceInvoicePayment` em `core/analytics` e emiti-lo na submissão em modo de correção.

## 9. Verificação

- [ ] 9.1 Rodar `./gradlew jvmTest` e ler a saída.
- [ ] 9.2 Confirmar que `:core:ledger` não foi tocado — nem a fronteira de escrita, nem `InvoiceWriteGuard`, nem `IEntryRepository`.
- [ ] 9.3 Confirmar que nenhum caminho novo leva a `PAID` nem sai dele.
- [ ] 9.4 Exercitar no app: corrigir valor, conta, data e fatura de um parcial; verificar que a fatura anterior recupera o valor e a nova o desconta.
- [ ] 9.5 Exercitar no app: corrigir um parcial entre moedas e conferir que a taxa exibida no detalhe é a que o formulário mostrava.
