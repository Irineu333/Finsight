# Tarefas — `transaction-as-recurring`

Os grupos são **ordenados**; dentro de um grupo, nenhuma tarefa escreve num arquivo que
outra irmã escreve, e nenhuma consome a saída de outra irmã — um subagente por tarefa
implementa o grupo inteiro em paralelo. Cada grupo declara a sua **barreira**: o que precisa
ser verdade antes de ele começar e o que é verdade quando ele termina.

A sequência entre os grupos não é escolha de estilo: `core/model` precede quem o consome
(D6), a assinatura do repositório precede os seus implementadores (D3), o caso de uso
precede a UI que o chama (D5), e os testes precedem o encerramento.

---

## 1. Fundação — `core/model` e as strings

**Barreira de entrada:** nenhuma; é o primeiro grupo.
**Barreira de saída:** o projeto compila e `./gradlew jvmTest` continua verde. Nada de
comportamento mudou ainda — só existem construções novas, sem chamador.

- [x] 1.1 Em `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/form/RecurringForm.kt`,
      adicionar `fun RecurringForm.toRecurring(createdAt: Long): Either<RecurringError, Recurring>` (D6):
      a construção validada do template, com as mesmas exigências que `SaveRecurringUseCase`
      escreve hoje à mão (`SaveRecurringUseCase.kt:37-67`) e na mesma ordem — `AMOUNT_REQUIRED`,
      `AMOUNT_ZERO`, `TITLE_OR_CATEGORY_REQUIRED`, `INVALID_DAY` (dia parseável e em `1..31`),
      `ACCOUNT_REQUIRED` (conta para receita; conta **ou** cartão para despesa) — devolvendo
      `RecurringError` cru, não a exceção. `creditCard` é descartado quando o tipo é receita.
      `isValid()` permanece como a leitura barata da UI, sobre a mesma base.
- [x] 1.2 Em `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/form/TransactionForm.kt`,
      adicionar `fun TransactionForm.asRecurringOn(date: LocalDate): RecurringForm` (D6): a
      tradução única entre os dois forms, com `dayOfMonth` = dia de `date`, e título, valor,
      categoria, conta e cartão vindos do lançamento. Nenhum modelo novo.
- [x] 1.3 Adicionar as chaves novas em **`core/resources/src/commonMain/composeResources/values/strings.xml` (pt)
      e `values-en/strings.xml` (en)**, no mesmo passo (uma chave presente em só um dos arquivos
      é um bug): o rótulo de acessibilidade do botão de recorrência no campo de data e o texto
      de apoio "repete todo dia N" (D8), este com o dia como parâmetro.

---

## 2. O contrato do repositório

**Barreira de entrada:** grupo 1 concluído (nada aqui depende dele, mas o grupo é ordenado).
**Barreira de saída:** a assinatura existe no `api`. **O projeto ainda não compila** — um
método abstrato novo deixa todos os implementadores incompletos, e é exatamente isso que o
grupo 3 fecha. Um único item, para que nenhuma tarefa dependa da saída de uma irmã.

- [x] 2.1 Em `feature/recurring/api/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IRecurringRepository.kt`,
      declarar (D3):
      `suspend fun createWithFirstCycle(recurring: Recurring, firstCycle: TransactionIntent, occurrence: RecurringOccurrence): Transaction`.
      KDoc obrigatório: as três escritas são **uma unidade de trabalho**, e a restrição já
      registrada em `IRecurringOccurrenceRepository.confirmCycle` (`:16-33`) e
      `RecurringOccurrenceRepository.confirmCycle` (`:63-74`) tem de ser repetida aqui —
      passam a ser **três `useWriterConnection` aninhados numa só corrotina**, e trocar de
      dispatcher em qualquer ponto do caminho causa deadlock. `insert` **não muda** (D3, nota).

---

## 3. Os implementadores do contrato

**Barreira de entrada:** 2.1 concluído — a assinatura está escrita, e cada tarefa aqui a
satisfaz sem depender de nenhuma irmã.
**Barreira de saída:** o projeto volta a compilar em todos os alvos e `./gradlew jvmTest`
está verde, com o comportamento existente inalterado.

- [x] 3.1 Implementar `createWithFirstCycle` em
      `feature/recurring/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/RecurringRepository.kt`
      e ligar a dependência nova em `feature/recurring/impl/src/commonMain/kotlin/com/neoutils/finsight/di/RecurringModule.kt`
      (os dois arquivos na mesma tarefa, porque o parâmetro novo do construtor e o `single { … }`
      que o preenche são a mesma mudança): `RecurringRepository` passa a receber
      `IRecurringOccurrenceRepository`; o método abre `database.useWriterConnection { connection ->
      connection.immediateTransaction { … } }`, insere o template pelo `dao.insert` (que já
      devolve o `Long` — `RecurringDao.kt:60-61`) e delega o resto ao `confirmCycle` **existente**,
      passando `firstCycle`/`occurrence` com o `recurringId` recém-criado; o `confirmCycle`
      reentra como `SAVEPOINT` e continua fazendo o seu *re-entry check* — que aqui é
      trivialmente satisfeito e vale como defesa em profundidade (D3). `confirmCycle` **não é
      tocado**. Repetir no KDoc do método a restrição de dispatcher.
- [x] 3.2 Atualizar os fakes de `IRecurringRepository` em `feature/creditcards`:
      `src/commonTest/.../ui/screen/creditCards/CreditCardsEmptyStateTest.kt:240`,
      `src/commonTest/.../ui/screen/invoiceTransactions/InvoiceTransactionsFakes.kt:183` e
      `src/commonTest/.../domain/usecase/DeleteCreditCardUseCaseTest.kt:94` — o método novo é
      irrelevante para esses testes, então `throw NotImplementedError()`, como os fakes já
      fazem com o que não exercitam.
- [x] 3.3 Atualizar o fake de `IRecurringRepository` em
      `feature/accounts/impl/src/commonTest/kotlin/com/neoutils/finsight/domain/usecase/RetireAccountGuardsTest.kt:171`.
- [x] 3.4 Atualizar o fake de `IRecurringRepository` em
      `feature/budgets/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/viewBudget/ViewBudgetViewModelTest.kt:107`.
- [x] 3.5 Atualizar os fakes de `IRecurringRepository` em `feature/categories`:
      `src/commonTest/.../ui/modal/viewCategory/ViewCategoryViewModelTest.kt:211` e
      `src/commonTest/.../domain/usecase/DeleteCategoryGuardsTest.kt:148`.
- [x] 3.6 Atualizar os fakes de `IRecurringRepository` em `feature/recurring`:
      `src/commonTest/kotlin/com/neoutils/finsight/RecurringFakes.kt:44` e
      `src/commonTest/.../ui/modal/viewRecurring/ViewRecurringViewModelTest.kt:50`.

---

## 4. O domínio da recorrência

**Barreira de entrada:** grupos 1 e 3 concluídos — `RecurringForm.toRecurring` existe (4.1 e
4.2 dependem dele) e `createWithFirstCycle` já tem implementação (4.2 a chama). Esta é a
sequência que o design impõe: o método do repositório antes do caso de uso (D3/D5).
**Barreira de saída:** o projeto compila, `./gradlew jvmTest` verde, e a criação da
recorrência a partir de um `TransactionIntent` está disponível — ainda sem nenhuma tela que a
acione.

- [x] 4.1 Em `feature/recurring/impl/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/SaveRecurringUseCase.kt`,
      montar o `RecurringForm` e delegar a `toRecurring(createdAt)`, no lugar das validações
      escritas à mão (`:37-67`), mapeando o `RecurringError` devolvido para
      `RecurringException` (D6). O comportamento observável não muda: os mesmos erros, na
      mesma ordem, cobertos pelos testes que já existem. `createdAt` continua caindo no
      relógio quando o chamador não informa.
- [x] 4.2 Criar `StartRecurringFromTransactionUseCase` em
      `feature/recurring/api/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/StartRecurringFromTransactionUseCase.kt`
      e registrá-lo em `feature/recurring/impl/src/commonMain/kotlin/com/neoutils/finsight/di/RecurringModule.kt`
      (os dois arquivos na mesma tarefa: a classe e o `factory { … }` que a constrói são a
      mesma mudança). Classe **concreta** no `api`, como `GetPendingRecurringUseCase` já é
      (D5), dependendo apenas de `IRecurringRepository` e de `Clock`:
      `suspend operator fun invoke(form: RecurringForm, firstCycle: TransactionIntent): Either<Throwable, Transaction>`.
      Ele (a) constrói o `Recurring` por `form.toRecurring(createdAt)` com **`createdAt` =
      data do `firstCycle`, à meia-noite, em `TimeZone.currentSystemDefault()`** (D2 — o fuso
      não é livre: `Instant.toYearMonth()` converte de volta nesse mesmo fuso); (b) monta a
      `RecurringOccurrence` do ciclo 1 — `status = CONFIRMED`, `cycleNumber = 1`,
      `yearMonth` e `effectiveDate` da data do intent, `handledAt` do relógio; (c) completa o
      intent recebido com `recurringId` e `recurringCycle = 1`; (d) chama
      `createWithFirstCycle`. O `1` não é fixado à parte: é o caso degenerado da fórmula que
      `ConfirmRecurringUseCase` já usa (`:75-78`), aplicada a um template ancorado na data da
      transação. O intent **não é remontado** — ele carrega a fatura que o usuário escolheu
      (D4).

---

## 5. Estado e ações da tela de lançamento

**Barreira de entrada:** grupos 1 e 4 concluídos — as strings existem (5.2 pode nomeá-las) e
o caso de uso existe.
**Barreira de saída:** o projeto compila e `./gradlew jvmTest` verde. O estado sabe descrever
a opção; ninguém ainda a liga nem a renderiza.

- [x] 5.1 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionAction.kt`,
      adicionar a ação que marca/desmarca a recorrência (por exemplo
      `data class ToggleRecurring(val enabled: Boolean)`), no estilo das demais.
- [x] 5.2 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionUiState.kt`,
      expor `isRecurring: Boolean` (a marca) e decidir aqui — nunca no composable — `canRepeat`
      (= `form.installments == 1`, D7) e a **precedência do texto de apoio** do campo de data
      (D8): o aviso de data fora da janela da fatura tem precedência sobre a confirmação
      "repete todo dia N"; esta só aparece com a marca ligada e sem aviso pendente. KDoc no
      espírito do que `canSubmit`/`isDateOutsideInvoice` já trazem: decisão alcançável só por
      dispositivo é decisão que teste nenhum alcança.

---

## 6. View model e modal

**Barreira de entrada:** grupo 5 concluído — a ação e os campos do `UiState` existem, e as
duas tarefas abaixo os consomem sem depender uma da outra (o modal lê `UiState`/`Action`, não
o view model).
**Barreira de saída:** o projeto compila, `./gradlew jvmTest` verde e o fluxo funciona no app.

- [x] 6.1 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionViewModel.kt`
      (+ o `viewModel { AddTransactionViewModel(…) }` em
      `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/di/TransactionsModule.kt`,
      mesma mudança): injetar `StartRecurringFromTransactionUseCase` (a dependência de
      `feature.recurring.api` já está declarada em `build.gradle.kts:22`); guardar a marca no
      `Input`; tratar a ação nova; **desligar a marca ao passar a parcelar**, e não apenas
      escondê-la (D7), na mesma disciplina de `changeType` (`:242-247`); e em `submit()`, com a
      marca ligada e `installments == 1`, chamar `buildTransactionUseCase(form)` como hoje e
      então passar o intent a `StartRecurringFromTransactionUseCase(form.asRecurringOn(data do
      lançamento), intent)` no lugar de `transactionRepository.createTransaction`, mantendo
      `onLeft`/`onRight` — erro pelo `toUiMessage()` já existente (a mensagem do erro que
      recusou, nunca uma falha genérica), sucesso com `analytics`/`modalManager.dismiss()`.
      **O caminho de quem não usa a opção fica byte a byte o de hoje**, inclusive o ramo de
      parcelamento.
- [x] 6.2 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionModal.kt`,
      transformar o `trailingIcon` do campo de data num `Row` de dois botões (D8):
      `Icons.Outlined.Autorenew` **à esquerda** do `CalendarToday` já existente, `tint =
      colorScheme.primary` quando ligado e `onSurfaceVariant` quando não,
      `contentDescription` pela chave de acessibilidade do 1.3,
      `Modifier.testTag("add_transaction_repeat")`, despachando a ação do 5.1; o botão só
      aparece/fica habilitado conforme `uiState.canRepeat`. O `supportingText` passa a
      renderizar o que o `UiState` decidiu (5.2), mantendo o aviso de fatura com precedência
      (`:297-299`). Nenhum campo novo é apresentado e o botão de salvar continua governado por
      `canSubmit`.

---

## 7. Testes

**Barreira de entrada:** grupo 6 concluído — o caminho inteiro existe.
**Barreira de saída:** todos os arquivos novos passam, e `./gradlew jvmTest` está verde.
Arquivos disjuntos, portanto todas as tarefas correm em paralelo.

- [x] 7.1 **Atomicidade contra banco real**, em
      `feature/recurring/impl/src/jvmTest/kotlin/com/neoutils/finsight/database/repository/CreateWithFirstCycleAtomicityTest.kt`,
      espelhando `ConfirmCycleAtomicityTest` (mesmo `Room.inMemoryDatabaseBuilder` e o mesmo
      `RecordingTransactionRepository`): (a) template, transação e ocorrência persistem juntos,
      com a transação carregando `recurringId`/`recurringCycle = 1` e a ocorrência apontando
      para ela; (b) uma recusa na escrita da transação não deixa **nenhum** template para trás
      (spec — "Transação recusada não deixa recorrência"); (c) os três `useWriterConnection`
      aninhados numa só corrotina não travam. É o único teste que pega a armadilha do
      dispatcher.
- [x] 7.2 **Unidade do caso de uso**, em
      `feature/recurring/impl/src/commonTest/kotlin/com/neoutils/finsight/domain/usecase/StartRecurringFromTransactionUseCaseTest.kt`
      (onde `GetPendingRecurringUseCaseTest` já vive, porque o `api` não tem source set de
      teste): `createdAt` ancorado na data da transação e `dayOfMonth` igual ao dia dela; ciclo
      1 e ocorrência `CONFIRMED` no mês daquela data, também para **data retroativa**;
      `handledAt` vindo do relógio; um form inválido devolve o `RecurringError` correspondente
      sem chamar o repositório; e — com `GetPendingRecurringUseCase` sobre o que foi gravado —
      o mês da transação **não** volta como pendente, enquanto o mês corrente **volta** no caso
      retroativo com o dia já passado (spec — "O mês da transação não volta a ser cobrado" e
      "Data retroativa deixa o mês corrente pendente").
- [x] 7.3 **Unidade do view model**, em
      `feature/transactions/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/addTransaction/AddTransactionRecurringTest.kt`
      (ao lado de `AddTransactionSubmitTest`): ligar a marca não escreve nada e não muda
      `canSubmit`; ligar e desligar antes de salvar lança a transação como qualquer outra, sem
      recorrência; passar a parcelar **desliga** a marca e `canRepeat` fica falso; voltar a uma
      parcela reabilita a opção **desmarcada**; salvar com a marca ligada chama o caso de uso
      com o intent construído por `buildTransactionUseCase` (a fatura escolhida preservada,
      D4); e uma recusa mostra a mensagem do erro correspondente, não a genérica, sem fechar o
      modal.
- [x] 7.4 **O teorema, em `core/model`**, em
      `core/model/src/commonTest/kotlin/com/neoutils/finsight/domain/model/form/TransactionFormAsRecurringTest.kt`
      (ao lado de `TransactionFormCoherenceTest`): todo `TransactionForm` válido e **não
      parcelado** produz, via `asRecurringOn(date)`, um `RecurringForm` válido — cobrindo
      receita com conta, despesa com conta, despesa com cartão, título sem categoria e
      categoria sem título. É o que dispensa qualquer condição de habilitação além da exclusão
      com o parcelamento (Context, D7); se ele cair, `canRepeat` deixou de bastar.
- [x] 7.5 **Fluxo E2E**, em `.maestro/flows/recurring/transaction_as_recurring.yaml`,
      reaproveitando os subflows existentes (`launch_fresh`, `create_account`,
      `record_transaction`, `open_section`): lançar uma despesa com
      `id: add_transaction_repeat` ligado, conferir que a recorrência aparece na lista de
      recorrentes com o valor, o título e o dia da data lançada, e que o mês não é oferecido
      como pendente. Elementos alcançados por `id:`, nunca por rótulo; se algum destino novo
      precisar de `testTag`, garantir que a raiz de composição publique com
      `Modifier.exposeTestTags()`.

---

## 8. Verificação

**Barreira de entrada:** grupo 7 concluído.
**Barreira de saída:** a mudança está verificada — cada item abaixo foi executado e a saída,
lida.

- [x] 8.1 `./gradlew :feature:recurring:impl:jvmTest --tests "*StartRecurringFromTransaction*" --tests "*CreateWithFirstCycle*"` — verde
- [x] 8.2 `./gradlew :app:shared:testDebugUnitTest --tests "*AddTransactionRecurring*"` — verde
- [x] 8.3 `./gradlew jvmTest` — a suíte inteira verde, sem regressão em `SaveRecurringUseCase`
      nem nos testes de recorrência já existentes (o comportamento observável de 4.1 não muda)
- [x] 8.4 `./gradlew :app:android:assembleDebug` — compila
- [x] 8.5 Conferir que **cada chave nova de string existe nos dois arquivos** (`values/strings.xml`
      e `values-en/strings.xml`)
- [x] 8.6 Conferir que a restrição de dispatcher está registrada em KDoc no método novo do `api`
      (2.1) e na implementação (3.1) — é ela que sustenta os três `useWriterConnection` aninhados
- [ ] 8.7 Rodar o fluxo `.maestro` novo: reinstalar o debug (`./gradlew :app:android:installDebug`)
      e executar num AVD `pixel_6` API 36, em inglês, com teclado na tela e sem teclado de
      hardware — conferindo antes as verificações `adb` de `.maestro/README.md` §2 e **relatando
      em qual dispositivo a execução aconteceu**

---

## 9. Ajustes de UX após o primeiro uso

Levantados testando o app no dispositivo, depois de o grupo 6 estar de pé. Não mudam o que a
mudança faz — mudam como ela se comporta na mão de quem usa, e é por isso que só apareceram aqui.

**Barreira de entrada:** grupo 6 concluído e o fluxo exercitado à mão no aparelho.
**Barreira de saída:** o app compila (`./gradlew :app:android:assembleDebug`), o fluxo `.maestro`
continua alcançando `id: add_transaction_repeat`, e nenhum teste de `AddTransactionRecurringTest`
muda — nada aqui toca estado ou decisão, só a forma.

- [x] 9.1 **Aproximar os dois ícones do campo de data.** Dois `IconButton` no `trailingIcon` ficam
      com 48dp de alvo cada, e o par lê como dois controles separados com um vão entre eles. Ambos
      passam a 40dp, num `Row` com `Arrangement.spacedBy((-4).dp)`: um par de affordances de um
      campo só, que é o que eles são. O alvo de toque continua confortável e o `testTag` não muda.
- [x] 9.2 **Tirar o solavanco ao marcar a recorrência.** O `supportingText` entra e sai com a nota,
      e a altura do campo mudava num quadro só, empurrando tudo abaixo dele. O campo de data ganha
      `Modifier.animateContentSize()`, que carrega a mudança de altura, e a nota troca dentro de um
      `AnimatedContent` com `fadeIn`/`fadeOut` — o aviso de fatura e a nota de repetição se
      substituem no lugar em vez de piscarem.
- [x] 9.3 **Animar o estado do ícone.** O `tint` do `Autorenew` passa por `animateColorAsState`, e o
      botão entra e sai por `AnimatedVisibility` quando o parcelamento o retira — ligar a marca
      passa a ser uma transição, e não uma troca de cor seca.
