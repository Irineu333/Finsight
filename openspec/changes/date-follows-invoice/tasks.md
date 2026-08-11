# Tasks — date-follows-invoice

## 1. O dono da janela no domínio

*Barreira de entrada:* nenhuma — o repositório compila e a suíte está verde no estado atual.
*Barreira de saída:* `:core:model` compila e o tipo `InvoiceWindow`, a projeção `dateOn` e as duas
derivações (`CreditCard.invoiceWindowFor`, `Invoice.window`) existem. **Este grupo tem uma única
tarefa por imposição do design (D3): o tipo precisa existir antes de qualquer consumidor, e todos
os consumidores dos grupos seguintes o nomeiam.** Nada mais muda de comportamento aqui.

- [ ] 1.1 Criar `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/InvoiceWindow.kt` com: o `data class InvoiceWindow(openingMonth, closingMonth, closingDay)`; `openingDate`/`closingDate` via `YearMonth.safeOnDay` (`core/common/.../extension/YearMonth.kt`), documentando abertura inclusiva e fechamento exclusiva; `fun dateOn(day: Int): LocalDate` exatamente como em D1 — preferir o mês de fechamento, recuar para o de abertura quando `late >= closingDate`, e `coerceAtLeast(openingDate)` para a degenerescência de fim de mês; `fun CreditCard.invoiceWindowFor(dueMonth: YearMonth): InvoiceWindow`, dona única da regra `dueDay < closingDay → closingMonth = dueMonth − 1` e `openingMonth = closingMonth − 1`; e `val Invoice.window: InvoiceWindow`, montada a partir dos meses **gravados** na fatura. KDoc objetivo, em inglês, descrevendo o estado atual — sem narrar a mudança.

## 2. Consumidores da janela em `:core:model` e teste da projeção

*Barreira de entrada:* grupo 1 concluído — as três tarefas abaixo nomeiam `InvoiceWindow`.
*Barreira de saída:* `:core:model` compila e o novo teste de projeção passa
(`./gradlew :core:model:allTests` ou `:app:shared:testDebugUnitTest` conforme o alvo disponível).
**Nota deliberada:** 2.2 acrescenta um parâmetro obrigatório a `InvoiceMonthSelection`, então os
três módulos consumidores **não compilam** entre o fim deste grupo e o fim do grupo 3 — o
compilador aponta exatamente os três sítios, e é o grupo 3 que os fecha. As três tarefas tocam
arquivos distintos e nenhuma lê a saída da outra.

- [ ] 2.1 Em `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/Invoice.kt`, fazer `openingDate` e `closingDate` (hoje linhas 27-28) delegarem a `window`, de modo que a fatura existente responda pelos meses que gravou e não por uma rederivação a partir dos dias atuais do cartão. `dueDate` fica como está — não é borda de janela.
- [ ] 2.2 Em `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/InvoiceMonthSelection.kt`, acrescentar `creditCard: CreditCard` e expor `val window = existingInvoice?.window ?: creditCard.invoiceWindowFor(dueMonth)`, preservando `isNew` e `isClosedToNewExpenses`. É o campo que permite ter janela para uma fatura que ainda não existe (o caso comum ao navegar adiante, em que `existingInvoice` é `null`).
- [ ] 2.3 Criar `core/model/src/commonTest/kotlin/com/neoutils/finsight/domain/model/InvoiceWindowTest.kt` cobrindo os cenários da spec `invoice-purchase-window`: janela de cartão que fecha no 10 e vence no 20 (fevereiro→março para vencimento em março); janela de cartão que fecha no 25 e vence no 5 (fechamento em fevereiro, janela 25/jan–25/fev); fatura existente responde pelos meses gravados; fatura inexistente tem janela derivada idêntica à que teria se criada; dia 15 na janela 10/fev–10/mar cai em 15/fevereiro; dia 5 cai em 5/março; **dia igual ao fechamento (10) cai em 10/fevereiro, a borda inclusiva**; **idempotência** — 05/março projetado na janela 10/fev–10/mar volta 05/março; **degenerescência de fim de mês** — fechamento no dia 31, janela 31/jan–28/fev, dia pedido 30, resultado dentro da janela sem estourar a borda de abertura.

## 3. Os três sítios de construção de `InvoiceMonthSelection`

*Barreira de entrada:* 2.2 concluída — o campo novo precisa existir antes de ser passado.
*Barreira de saída:* o projeto inteiro volta a compilar e `./gradlew allTests` passa como antes:
este grupo é mecânico e **não muda comportamento algum**. As três tarefas estão em arquivos
distintos, em módulos distintos, e o cartão já está no mesmo `combine` de cada uma.

- [ ] 3.1 `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionViewModel.kt:140` — passar `creditCard = selectedCard` na construção de `InvoiceMonthSelection` (a seleção só existe quando há mês de vencimento, e o mês de vencimento só existe quando há cartão).
- [ ] 3.2 `feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addInstallment/AddInstallmentViewModel.kt:94` — mesma passagem do cartão.
- [ ] 3.3 `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/editTransaction/EditTransactionViewModel.kt:183` — mesma passagem do cartão, **e nada além disso**: por D6 o modal de edição não recebe coletor nem sincronização reversa. Ali a data é dado que o usuário escreveu, não um padrão do sistema.

## 4. O coletor que a fatura usa para recolocar a data

*Barreira de entrada:* grupo 3 concluído — o projeto compila e a suíte está verde.
*Barreira de saída:* nos dois modais de criação, trocar a fatura ou o cartão recoloca a data no
estado (ainda sem reflexo no campo, que é o grupo 5), e `./gradlew allTests` continua passando.
As duas tarefas são o mesmo desenho em ViewModels distintas; nenhuma depende da outra.

- [ ] 4.1 Em `AddTransactionViewModel.kt`, acrescentar no `init` um coletor único sobre `combine(selectedCreditCard, selectedDueMonth, ::Pair)` que, com cartão e mês presentes, extrai o dia de `input.value.date` com `runCatching { dayMonthYear.parse(...) }` (caindo em `clock.today().day` quando o buffer está a meio de digitação), projeta com `card.invoiceWindowFor(dueMonth).dateOn(day)`, aplica `coerceAtMost(clock.today())` e atualiza `input.date`. **Não** replicar a recolocação em `SelectInvoiceMonth`, `SelectCreditCard` nem no auto-select de cartão único (D4). Comentar que o coletor depende de `(cartão, fatura)` e nunca de `input.date` — é o que torna a assimetria estrutural.
- [ ] 4.2 Em `AddInstallmentViewModel.kt`, o mesmo coletor sobre as flows equivalentes dessa ViewModel, mantendo `AddInstallmentAction.ChangeDate` como único caminho UI → estado da data.

## 5. Reflexo no campo e testes da cascata

*Barreira de entrada:* grupo 4 concluído — sem o coletor não há recolocação para refletir nem
para testar.
*Barreira de saída:* a recolocação aparece no campo de data dos dois modais de criação, digitar
não é sobrescrito por eco, e os novos testes passam junto com `./gradlew allTests`. As cinco
tarefas tocam arquivos distintos.

- [ ] 5.1 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionModal.kt`, acrescentar a sincronização reversa do `TextFieldState` da data: `LaunchedEffect(uiState.form.date)` com guarda de igualdade contra `date.text.toString()` antes de `date.edit { replace(0, length, ...) }` — mesmo mecanismo que o `DatePickerModal` já usa ali. Comentar por que a guarda existe (fecha o laço com o `snapshotFlow` e protege a digitação em curso). Não mexer no `maxDate = uiState.today` do seletor: por D2 a projeção nunca produz data que ele recusaria.
- [ ] 5.2 Em `feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addInstallment/AddInstallmentModal.kt`, a mesma sincronização reversa com a mesma guarda.
- [ ] 5.3 Criar teste de cascata para `AddTransactionViewModel` em `feature/transactions/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/addTransaction/` (reaproveitando os dublês de `feature/transactions/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/TestDoubles.kt`), cobrindo os cenários de `invoice-governs-date`: abrir o formulário mostra hoje inalterado; cartão único auto-selecionado não move a data; navegar para a fatura anterior leva 12/março a 12/fevereiro; navegar até a fatura de janeiro leva a 12/dezembro; trocar de cartão sob o mesmo mês de vencimento reprojeta; navegar para fatura futura **trava em hoje**; voltar para a fatura aberta devolve hoje; editar a data não altera a fatura selecionada; o dia escrito pelo usuário é o dia preservado na projeção seguinte; campo de data incompleto usa o dia de hoje como dia preservado.
- [ ] 5.4 Criar teste equivalente para `AddInstallmentViewModel` em `feature/creditcards/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/addInstallment/`, fixando que o parcelamento segue a mesma hierarquia (cenário final da spec) — trocar a fatura recoloca a data pela mesma regra.
- [ ] 5.5 Criar teste em `feature/transactions/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/editTransaction/` fixando o não-escopo: trocar a fatura no `EditTransactionViewModel` **não** altera a data da transação editada.

## 6. As cinco cópias da derivação passam a consumir a dona

*Barreira de entrada:* grupo 1 concluído (a dona existe). Na prática executado depois do grupo 5,
para que a verificação aconteça sobre um projeto que compila.
*Barreira de saída:* nenhuma das cinco classes reimplementa `dueMonth ↔ closingMonth`, o
comportamento é idêntico e os testes existentes dessas classes continuam passando — é refatoração
pura (D7). Os cinco arquivos são distintos e independentes entre si.

- [ ] 6.1 `feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/CreateFutureInvoiceUseCase.kt` — substituir o cálculo local de `closingMonth`/`openingMonth` (linhas ~33-39) por `creditCard.invoiceWindowFor(targetDueMonth)`.
- [ ] 6.2 `.../CreateRetroactiveInvoiceUseCase.kt` — mesma substituição.
- [ ] 6.3 `.../CreateInvoiceUseCase.kt` — a cópia ali está no sentido `closingMonth → dueMonth` (linha ~49); consumir a dona sem alterar o resultado, mantendo a janela `currentMonth → nextMonth` que o caso de uso já fixa.
- [ ] 6.4 `.../OpenInvoiceUseCase.kt` — idem, para a derivação a partir de `openingMonth` (linha ~49).
- [ ] 6.5 `.../AddCreditCardUseCase.kt` — a quinta cópia é a inversa (data → mês de abertura, linhas ~51-57, `if (day < closingDay) mês−1`), que por D1 é a mesma regra lida ao contrário: consumir a borda inclusiva da janela em vez de reescrevê-la, sem mudar qual fatura o cartão novo abre.

## 7. Verificação final

*Barreira de entrada:* grupos 1 a 6 concluídos.
*Barreira de saída:* a suíte roda verde com a saída lida, o escopo declarado no proposal é o
escopo do diff, e o comportamento foi exercido no app real — não apenas em teste. As três tarefas
não editam arquivo algum.

- [ ] 7.1 Rodar `./gradlew allTests` e ler a saída; reportar o resultado real, não a expectativa.
- [ ] 7.2 Conferir no diff que o escopo declarado foi respeitado: nenhuma string nova em `core/resources/.../values/strings.xml` nem em `values-en/strings.xml`; nenhuma alteração em `:core:ledger`, no banco, nas migrações, no `TransactionForm` ou no boundary de escrita; o modal de editar transação tocado apenas pelo campo novo de `InvoiceMonthSelection`.
- [ ] 7.3 Exercitar no app (`./gradlew :app:desktop:run` ou `:app:android:installDebug`) os dois modais de criação com alvo cartão: abrir e confirmar que a data é hoje; navegar uma fatura para trás e ver a data recolocada; navegar para uma fatura futura e ver a trava em hoje; digitar uma data fora da janela e confirmar que a fatura não se move e o texto não é sobrescrito.
