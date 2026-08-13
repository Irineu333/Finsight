## Context

Lançar uma transação e criar uma recorrência são hoje dois formulários que pedem as mesmas informações. O que falta não é modelo — é o caminho que liga um ao outro.

O que já existe e a mudança consome sem alterar:

- **O vínculo transação → recorrência é campo comum do intent.** `TransactionIntent` declara `recurringId` e `recurringCycle` (`core/model` — `TransactionIntent.kt:21-22`), e `transactions` guarda as duas colunas, com a nulificação sob dono explícito em `RecurringDao.detachTransactions` (`:26-32`).
- **Transação e ocorrência já são uma unidade de trabalho.** `IRecurringOccurrenceRepository.confirmCycle` escreve as duas dentro de `useWriterConnection { immediateTransaction { … } }` e faz o *re-entry check* lá dentro, justamente porque lê-lo fora é um TOCTOU (`RecurringOccurrenceRepository.kt:63-89`). O KDoc registra a restrição que sustenta isso: a reentrância da Room viaja num elemento de contexto de corrotina, e **trocar de dispatcher entre as escritas causaria deadlock**.
- **Repositório compondo repositório dentro do writer já é o padrão.** `RecurringOccurrenceRepository` injeta `ITransactionRepository` e o chama dentro da própria transação (`:22`, `:85`). `RecurringRepository` já abre writer transaction em `delete` (`:97-104`).
- **O intent da tela já resolve a fatura escolhida.** `BuildTransactionUseCaseImpl` valida o form e resolve `invoiceDueMonth` → `Invoice` → `dimensionId` (`:58-79`), a partir da fatura que o usuário selecionou no `InvoiceMonthNavigator`.
- **A pendência de um ciclo ignora `createdAt`.** `GetPendingRecurringUseCase` (`:21-25`) devolve todo template não arquivado cujo dia efetivo já chegou e que não tem ocorrência no mês. Um template recém-criado com o dia da transação satisfaz as duas primeiras condições **imediatamente**.
- **Os fusos coincidem.** `Instant.toYearMonth()` usa `TimeZone.currentSystemDefault()` (`core/common` — `Instant.kt:11-12`), o mesmo de `Clock.today()` (`Clock.kt:13`).

E um fato que dispensa uma regra que seria fácil inventar: as exigências de `ValidateTransactionFormUseCaseImpl` (`:30-60`) **contêm** as de `RecurringForm.isValid()` (`:20-27`) — valor não vazio e diferente de zero, título **ou** categoria (`:38-40`), conta para receita, conta ou cartão para despesa, data parseável. Logo, **todo `TransactionForm` válido e não parcelado produz um `RecurringForm` válido**, e a opção não precisa de nenhuma condição de habilitação além da exclusão com parcelamento.

## Goals / Non-Goals

**Goals:**

- Criar a recorrência a partir do que a transação já diz, sem pedir nada a mais.
- Lançar a transação como ciclo 1 da recorrência, com o vínculo e a ocorrência confirmada.
- Impedir que o mês da transação volte a ser oferecido para confirmação.
- Garantir que a criação inteira falhe junta ou persista junta.
- Manter o caminho de quem não usa a opção **byte a byte** o de hoje.

**Non-Goals:**

- Tornar uma transação **já lançada** recorrente (`EditTransaction`, `ViewTransaction`): exige lidar com template retroativo sobre uma transação existente, e é outra mudança.
- Oferecer periodicidade diferente de mensal, data de término, ou qualquer campo que a recorrência ainda não tenha.
- Alterar `ConfirmRecurringUseCase`, `confirmCycle`, o ledger ou qualquer coisa em `:core:ledger`.
- Criar uma abstração genérica de "unidade de trabalho" exposta a casos de uso (ver D3).
- Migração de banco: não há coluna nova.

## Decisions

### D1 — A transação é o ciclo 1, e o vínculo é escrito no ato

A transação salva com a opção ligada é gravada com `recurringId` do template recém-criado e `recurringCycle = 1`, e a ocorrência daquele mês é gravada como `CONFIRMED` apontando para ela.

*Por quê:* a alternativa — criar o template e deixar a transação seguir o caminho comum, sem vínculo — não é mais barata, é **quebrada**. `GetPendingRecurringUseCase` (`:21-25`) não consulta `createdAt`: o template nasceria com o dia da transação e o mês corrente apareceria como pendente no mesmo instante. O usuário confirmaria, e o ledger receberia a mesma despesa duas vezes. A única forma de calar essa pendência é escrever uma ocorrência — e uma `SKIPPED` registraria "o usuário pulou este mês" sobre um mês que ele acabou de pagar. Escrita a ocorrência confirmada, escrever também o vínculo custa dois campos e devolve o histórico íntegro.

### D2 — `createdAt` é ancorado na data da transação, e o ciclo 1 é consequência da fórmula existente

O template é criado com `createdAt = data da transação, à meia-noite, em `TimeZone.currentSystemDefault()``.

*Por quê:* `ConfirmRecurringUseCase` deriva o ciclo como `createdAt→mês .monthsUntil(mês do lançamento) + 1` (`:75-78`). Ancorando na data da transação, essa mesma fórmula rende `0 + 1 = 1` — o `1` não é um número fixado à parte, é o caso degenerado da regra que já existe. Ancorar no relógio quebraria isso: uma transação datada do mês anterior daria ciclo `0`, e a ocorrência cairia num mês diferente do da transação.

O fuso não é escolha livre: `Instant.toYearMonth()` (`Instant.kt:12`) converte de volta em `currentSystemDefault()`, então ancorar em qualquer outro fuso reintroduziria deslize de mês na virada.

*Consequência aceita:* `RecurringDao.observeAll` ordena por `createdAt ASC` (`:42-43`), então uma recorrência criada a partir de um lançamento retroativo aparece na lista **antes** de outras criadas antes dela. É consistente com o que `createdAt` passa a significar, e uma segunda coluna só para a ordem não se paga.

### D3 — A unidade de trabalho pertence ao `IRecurringRepository`

```kotlin
suspend fun createWithFirstCycle(
    recurring: Recurring,             // id = 0
    firstCycle: TransactionIntent,    // recurringId/recurringCycle preenchidos aqui
    occurrence: RecurringOccurrence,  // recurringId preenchido aqui
): Transaction
```

A implementação abre o writer, insere o template pelo DAO (que já devolve o `Long` — `RecurringDao.kt:60-61`) e delega o resto ao `confirmCycle` **existente**, que reentra como `SAVEPOINT`:

```
RecurringRepository.createWithFirstCycle          BEGIN
├─ dao.insert(template) ─────────────────────→ recurringId
└─ occurrenceRepository.confirmCycle(…)           SAVEPOINT
   ├─ re-entry check
   ├─ transactionRepository.createTransaction()   SAVEPOINT
   └─ save(occurrence.copy(transactionId = …))
```

*Por quê aqui:* o agregado que nasce é a **recorrência**; o ciclo é consequência. O `IRecurringOccurrenceRepository` cuida de ciclos de um template que já existe, e a assimetria é o que mantém as duas responsabilidades legíveis. O grafo ganha uma aresta e nenhum ciclo — `RecurringRepository → RecurringOccurrenceRepository → TransactionRepository` —, e a composição de repositórios dentro do writer é o padrão que `RecurringOccurrenceRepository` já pratica (`:22`, `:85`).

*Por quê `confirmCycle` não é tocado:* ele já resolve exatamente o problema para o qual foi escrito, e o *re-entry check* que ele faz continua rodando aqui — trivialmente satisfeito num template novo, e defesa em profundidade de graça.

*Alternativa considerada:* um `unitOfWork { }` genérico exposto a casos de uso, compondo repositórios arbitrariamente. Rejeitada: vazaria transação de banco para a camada de domínio e **espalharia a armadilha do dispatcher** — a restrição hoje documentada em um KDoc passaria a valer em toda chamada de todo caso de uso, sem nada que a lembre.

*Alternativa considerada:* compensar (`insert` → falhou → `delete`). Rejeitada: a compensação também pode falhar, e o banco já sabe fazer isso direito.

*Alternativa considerada:* inverter a ordem (transação primeiro). Rejeitada: a falha deixaria uma transação apontando para um `recurringId` inexistente — pior que o problema original.

*Consequência que precisa de KDoc:* passam a ser **três** `useWriterConnection` aninhados numa só corrotina. A restrição de não trocar de dispatcher, hoje registrada em `confirmCycle` (`:68-73`), tem de ser repetida no método novo, e é o que o teste de atomicidade contra banco real defende.

*Nota:* `IRecurringRepository.insert` **não muda**. O `Long` de que o novo método precisa vem do DAO, dentro do próprio repositório.

### D4 — O intent vem de `BuildTransactionUseCase`, não é remontado

O caso de uso novo recebe o `TransactionIntent` que a tela já construiu, e apenas o completa com `recurringId`/`recurringCycle`.

*Por quê:* reusar `ConfirmRecurringUseCase` seria tentador — ele monta um intent a partir de um `Recurring` — mas ele resolve a fatura por `date.yearMonth` via `getOrCreateInvoiceForMonthUseCase` (`:98-100`), enquanto a tela de lançamento tem a fatura que **o usuário escolheu** no `InvoiceMonthNavigator`, que pode ser outra. Remontar o intent seria descartar a escolha do usuário e recalcular pior. O intent do form é o autoritativo.

### D5 — Um caso de uso concreto em `feature/recurring/api`

```kotlin
class StartRecurringFromTransactionUseCase(
    private val repository: IRecurringRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        form: RecurringForm,
        firstCycle: TransactionIntent,
    ): Either<Throwable, Transaction>
}
```

Ele constrói o `Recurring` (D6), monta a ocorrência do ciclo 1 (`CONFIRMED`, `yearMonth` e `effectiveDate` da data do intent, `handledAt` do relógio) e chama `createWithFirstCycle`.

*Por quê classe concreta no `api`:* `GetPendingRecurringUseCase` já é exatamente isso — um caso de uso concreto no `api`, registrado no Koin do `impl` (`RecurringModule.kt:52`). Ele depende só de `IRecurringRepository` e do relógio, ambos visíveis do `api`. Um par interface/impl aqui seria cerimônia sem consumidor.

*Por quê `transactions` não monta nada disso:* "como uma recorrência nasce" é regra de `recurring`. A tela de transações decide **se** oferece; nunca **qual** é a regra — é a regra de derivação do projeto.

*Por quê o relógio fica no caso de uso e não no repositório:* `handledAt`, `CONFIRMED` e o ciclo são decisões de domínio. O repositório só sabe preencher a identidade que ele acabou de criar — a mesma divisão que `confirmCycle` já faz ao preencher `transactionId` por conta própria.

### D6 — A construção "form → `Recurring` validado" ganha um dono único

Em `core/model`:

```kotlin
fun RecurringForm.toRecurring(createdAt: Long): Either<RecurringError, Recurring>
fun TransactionForm.asRecurringOn(date: LocalDate): RecurringForm
```

`SaveRecurringUseCase` passa a montar o `RecurringForm` e delegar a `toRecurring`, em vez de repetir as validações que hoje escreve à mão (`:37-67`).

*Por quê:* a regra já existe em duas formas — `RecurringForm.isValid()` (booleana, para a UI) e `SaveRecurringUseCase` (com erros tipados). Um terceiro chamador seria a terceira cópia, e o projeto proíbe duplicar lógica de domínio. `core/model` é onde os dois forms vivem, então é onde a tradução entre eles pertence; `isValid()` continua sendo a leitura barata da UI, agora sobre a mesma base.

### D7 — A opção é condicionada apenas ao parcelamento

`canRepeat = form.installments == 1`. Nenhuma validação paralela: a validade do lançamento já é decidida por `canSubmit`, e o teorema do **Context** garante que ela basta.

Passar a parcelar **desliga** a marca, em vez de apenas escondê-la — a mesma disciplina de `changeType`, que descarta a categoria que o novo tipo não aceita em vez de mantê-la fora de vista (`AddTransactionViewModel.kt:242-247`).

*Por quê:* um estado que a tela não mostra mais não pode continuar valendo no salvamento. Esconder criaria a recorrência de um lançamento que o usuário decidiu parcelar.

### D8 — O controle mora no campo de data, e o texto de apoio tem precedência definida

O `trailingIcon` do campo de data passa a ser um `Row` de dois: `Icons.Outlined.Autorenew` à esquerda do `CalendarToday` já existente, `tint = primary` quando ligado e `onSurfaceVariant` quando não, com `testTag("add_transaction_repeat")`.

*Por quê o campo de data:* é a data que define o dia da repetição, e pôr o controle nela é o que permite não pedir um campo a mais. *Por quê `Autorenew`:* é o ícone que já significa recorrência no app (`RecurringScreen.kt:272`, `AppNavCatalog.kt:81`) — consistência que não custa nada.

O `supportingText` do campo já é usado pelo aviso de data fora da janela da fatura (`AddTransactionModal.kt:297-299`). **O aviso tem precedência**: ele diz algo que o usuário talvez não queira, enquanto a confirmação "repete todo dia N" ecoa algo que ele acabou de decidir.

Como `canSubmit` e `isDateOutsideInvoice` já fazem, `canRepeat` e a precedência do texto são decididos no `UiState` — decisão alcançável só por dispositivo é decisão que teste nenhum alcança.

## Risks / Trade-offs

- **Três `useWriterConnection` aninhados numa corrotina** → Uma troca de dispatcher em qualquer ponto do caminho causa deadlock. Mitigado por KDoc no método novo (repetindo a restrição de `confirmCycle:68-73`) e por teste de atomicidade contra banco real, que é o único que pega isso.
- **Lançamento retroativo altera a ordem da lista de recorrentes** (`ORDER BY createdAt ASC`) → Aceito em D2. Uma coluna separada só para ordenação não se paga.
- **Lançamento retroativo deixa o mês corrente pendente logo em seguida** → É o comportamento correto, e vai para a spec como cenário para que não seja lido como defeito.
- **A opção pode ser lida como "parcelar em vezes indefinidas"** → Mitigado pelo texto de apoio, que nomeia o dia ("repete todo dia 12"), e pela exclusão mútua com o contador de parcelas, que impede a combinação sem sentido.
- **`SaveRecurringUseCase` muda sem que a feature peça** → É o preço de não criar a terceira cópia da validação (D6). O comportamento observável não muda: os mesmos `RecurringError`, na mesma ordem, cobertos pelos testes que já existem.
