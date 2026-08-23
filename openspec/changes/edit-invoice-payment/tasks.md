## 1. O teto que desconsidera a própria operação

- [x] 1.1 Adicionar `excluding: Long? = null` a `CalculateInvoiceUseCase`, computando o devido sobre as entries da dimensão que **não** pertencem àquela transação — compondo `dimensionOwedByCurrency` e `getEntriesByTransaction`, sem consulta nova no `IEntryRepository`. Atenção à unidade: entries são `Long` em centavos, o devido é `Double` em unidades.
- [x] 1.2 Documentar em KDoc que o default `null` é o devido corrente — o caso de toda leitura que não é uma correção — e por que aqui o default é seguro, ao contrário de `contra` em `updateTransaction`.
- [x] 1.3 Teste: fatura de R$ 800 com um pagamento de R$ 300 registrado. Sem `excluding`, o devido é R$ 500; com `excluding` daquela transação, é R$ 800.
- [x] 1.4 Teste: `excluding` de uma transação que nada tem naquela fatura não altera o devido (o caso da fatura trocada).
- [x] 1.5 Confirmar que os ~10 chamadores existentes seguem compilando e lendo o devido corrente.

## 2. `ValidateInvoicePaymentUseCase`

- [x] 2.1 Criar `ValidatedInvoicePayment`, carregando a `Invoice` resolvida — porque "esta fatura existe" é uma das regras verificadas, e um chamador que a lesse de novo repetiria a busca cuja falha o validador já nomeou.
- [x] 2.2 Criar `ValidateInvoicePaymentUseCase` devolvendo `Either<InvoiceError, ValidatedInvoicePayment>`, movendo de `AdvanceInvoicePaymentUseCase` as regras que a correção também aplica: valor > 0, contrapartida > 0 quando informada, fatura existe, `acceptsPartialPayment`, data na janela do ciclo, data não futura, devido > 0, valor ≤ teto. A ordem das guardas é preservada — `acceptsPartialPayment` **antes** da janela da data, para que a recusa nomeie a razão real.
- [x] 2.3 Receber o teto pela leitura de 1.1, com `excluding` como parâmetro, para que criação e correção o obtenham do mesmo dono.
- [x] 2.4 Fazer `AdvanceInvoicePaymentUseCase` consumir o validador, sem mudança de comportamento.
- [x] 2.5 Registrar o validador em `UseCaseModule`.
- [x] 2.6 Rodar `AdvanceInvoicePaymentUseCaseTest` sem alterá-lo — é o teste de caracterização desta extração; qualquer ajuste nele invalida a verificação.
- [x] 2.7 Manter `PayInvoicePaymentUseCase` intocado: a quitação tem regras próprias e está fora desta change.

## 3. `UpdateAdvanceInvoicePaymentUseCase`

- [x] 3.1 Criar o caso de uso no molde de `UpdateTransferUseCase`: validação por 2.2 com `excluding = transactionId`, leitura da transação apenas para recusar corrigir o que não existe, e reescrita em vez de criação.
- [x] 3.2 Fazer `WriteInvoicePaymentUseCase` servir também à reescrita, mantendo o dono único da forma — duas pernas, dimensão só na do cartão — e a colheita de taxa depois da escrita.
- [x] 3.3 Preservar o título que a transação carrega ao chamar `updateTransaction`, em vez de passar `null`.
- [x] 3.4 Registrar o caso de uso em `UseCaseModule`.
- [x] 3.5 Teste: corrigir o valor de um parcial altera o devido da fatura e preserva o id da transação.
- [x] 3.6 Teste: corrigir apontando para outra fatura devolve o valor ao devido da anterior e o desconta da nova.
- [x] 3.7 Teste: corrigir para R$ 700 um pagamento de R$ 300 numa fatura de R$ 800 é aceito — o cenário que o teto corrente recusava.
- [x] 3.8 Teste: a correção sobre fatura que não aceita pagamento parcial é recusada, ainda que nenhuma tela a ofereça.
- [x] 3.9 Teste: correção entre moedas reescreve as duas pernas monetárias, cada moeda soma zero, e a taxa vai ao acervo.

## 4. O modo de correção no ViewModel

- [x] 4.1 Aceitar `transaction: Transaction?` em `InvoicePaymentViewModel` como única distinção entre os dois modos, e derivar `isEditMode` dela, no molde de `TransferBetweenAccountsViewModel`.
- [x] 4.2 Resolver a fatura da operação pelo `dimensionId` da sua perna `LIABILITY` — é o ViewModel quem faz isso, e não o modal, porque identidade → facade exige repositório.
- [x] 4.3 Filtrar `payableInvoices` por `acceptsPartialPayment` em modo de correção e por `acceptsPayment` em modo de criação, sem enumerar status na tela.
- [x] 4.4 Pré-selecionar cartão, fatura e conta pagadora **sem** atravessar `selectCreditCard()`, que limpa a fatura e evaporaria o valor e a data pré-preenchidos.
- [x] 4.5 Pré-preencher o valor que liquida a partir de `entries.liabilityLeg()` e a conta pagadora com o que sai a partir de `entries.sourceLeg()` — os donos que o razão já tem para essa leitura. `sourceLeg()` filtra por `ASSET` antes do sinal, o que separa a conta pagadora da perna de conversão negativa.
- [x] 4.6 Pré-preencher a data com a que a operação registra, e **não** rodar `settlementDateFor` na abertura.
- [x] 4.7 Manter `settlementDateFor` no caminho da **troca** de cartão ou fatura, reposicionando a data quando ela não couber na janela nova.
- [x] 4.8 Manter a limpeza do valor e da contrapartida ao trocar cartão ou fatura, como na criação.
- [x] 4.9 Passar `excluding` ao ler o devido em modo de correção, para que o teto exibido seja o de 1.1.
- [x] 4.10 Rotear a submissão para `UpdateAdvanceInvoicePaymentUseCase` em modo de correção.
- [x] 4.11 Resolver o parâmetro novo com `getOrNull()` em `CreditCardsModule`, como `AccountsModule` faz com a transação da transferência.
- [x] 4.12 Abrir `selectedAccount` já na conta que a operação registra, e não apenas em `preselect()`: a perna a traz hidratada, enquanto cartão e fatura só chegam por busca. Sem isso o estado substitui pela conta **padrão** no intervalo, e o campo de contrapartida — que retira o que exibe quando a moeda muda — descarta o valor registrado assim que a conta verdadeira chega, aceitando no lugar a sugestão do acervo. Fazer `preselect()` só refinar, sem derrubar a conta quando o plano de contas nada responde.
- [x] 4.13 Teste: em modo de correção a conta pagadora é a da operação **antes de qualquer busca responder**, as buscas depois só refinam, e uma criação continua abrindo na conta padrão.

## 5. O que a superfície anuncia

- [x] 5.1 Fazer `settles` ser sempre falso em modo de correção — o modo é o da operação escrita, não o do estado da fatura.
- [x] 5.2 Fazer `label` do botão oferecer `invoice_payment_edit_confirm` em modo de correção, mantendo `paymentLabel` na criação.
- [x] 5.3 Fazer o cabeçalho oferecer `invoice_payment_edit_title` em modo de correção, mantendo `invoice_payment_title` na criação — e mantendo `invoice_payment_message`, que vale para os dois modos porque a fatura e a conta continuam escolhíveis.
- [x] 5.4 Passar a `canSubmitInvoicePayment` o teto de 1.1, para que oferta e permissão comparem o mesmo número.
- [x] 5.5 Dar a `InvoicePaymentModal` construtor privado e dois construtores públicos — `(invoiceId: Long?)` e `(transaction: Transaction)` —, no molde de `TransferBetweenAccountsModal`, e repassar ambos os parâmetros ao ViewModel.
- [x] 5.6 Estender `InvoicePaymentSubmitEnablementTest` com o cenário 800/300 → 700 habilitado, e com o teto da fatura trocada.
- [x] 5.7 Teste: o cabeçalho e o verbo do botão seguem o modo, e não mudam ao trocar a fatura selecionada.

## 6. A travessia da fronteira

- [x] 6.1 Adicionar `editInvoicePaymentModal(transaction: Transaction): Modal` a `CreditCardsEntry`, ao lado de `invoicePaymentModal(invoiceId)`, com KDoc dizendo por que são dois membros e não um nulável.
- [x] 6.2 Implementar o membro em `CreditCardsEntryImpl`.

## 7. O detalhe da operação

- [x] 7.1 Substituir `TransactionLabel.PAYMENT -> false` (`ViewTransactionUiState.kt:164`) pela leitura do predicado do domínio sobre a fatura que a operação nomeia, e atualizar o comentário que hoje registra a exclusão deliberada.
- [x] 7.2 Acrescentar o braço de `PAYMENT` na escolha do formulário em `ViewTransactionModal`, alcançando `CreditCardsEntry` por injeção como já faz com `AccountsEntry`.
- [x] 7.3 Confirmar que os gates acima permanecem intactos: fatura `CLOSED`/`PAID` esconde correção e remoção com a mensagem existente, e conta arquivada idem.
- [x] 7.4 Teste: um pagamento parcial sobre fatura `OPEN` oferece correção; sobre `CLOSED` e sobre `PAID`, não oferece nem correção nem remoção.
- [x] 7.5 Teste: a correção de um pagamento abre o formulário de pagamento, e não o de transferência nem o de transação.

## 8. Recursos e analytics

- [x] 8.1 Adicionar `invoice_payment_edit_title` ("Corrigir pagamento") e `invoice_payment_edit_confirm` ("Salvar") em `values/strings.xml` **e** `values-en/strings.xml`.
- [x] 8.2 Adicionar `EditAdvanceInvoicePayment : Event("edit_advance_invoice_payment")` em `core/analytics` e emiti-lo na submissão em modo de correção.

## 9. Verificação

- [x] 9.1 Rodar `./gradlew jvmTest` e ler a saída.
- [x] 9.2 Confirmar que `:core:ledger` não foi tocado — nem a fronteira de escrita, nem `InvoiceWriteGuard`, nem `IEntryRepository`.
- [x] 9.3 Confirmar que nenhum caminho novo leva a `PAID` nem sai dele.
- [x] 9.4 Exercitar no app: corrigir valor, conta, data e fatura de um parcial; verificar que a fatura anterior recupera o valor e a nova o desconta.
- [x] 9.5 Exercitar no app: corrigir um parcial entre moedas e conferir que a taxa exibida no detalhe é a que o formulário mostrava.
