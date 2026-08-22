# Tasks — `edit-transfer-between-accounts`

> **Ordenação de segurança (a regra que governa a ordem dos grupos).** O gate que revela a
> feature ao usuário — `isEditable` admitindo `TRANSFER` — é o **penúltimo** grupo, e isso é
> deliberado: enquanto ele não muda, nada do que os grupos anteriores constroem é alcançável
> pela interface, e o caminho antigo (apagar e refazer) continua sendo o único oferecido.
> Abrir o gate antes de existir o formulário em modo de correção poria na tela um botão que
> abre uma tela que não sabe editar.
>
> **Cada grupo termina com o projeto compilando.** A única barreira real é a troca de
> assinatura de `updateTransaction` (grupo 2): ela quebra **17 fakes** de
> `ITransactionRepository`, espalhados por 6 features (`accounts` 5, `creditcards` 6,
> `recurring` 2, `transactions` 2, `budgets` 1, `report` 1) e **3 chamadores de produção**
> (`EditTransactionViewModel`, `AdjustBalanceUseCase`, `AdjustInvoiceUseCase`). Todos são
> corrigidos dentro do próprio grupo 2, que não termina até `./gradlew jvmTest` passar —
> a troca não é aditiva e não há como escondê-la atrás de um corpo default.
>
> **Nenhuma migração, em nenhum grupo.** Nada de schema muda, `AppDatabase` não troca de
> versão, e nenhum dado gravado é reinterpretado.

## 1. As validações da transferência ganham dono único

- [ ] 1.1 Criar `ValidateTransferUseCase` em `feature/accounts/impl/domain/usecase/`, recebendo origem, destino, valor, valor de destino e data, e devolvendo `Either<TransferError, Unit>` — as cinco regras hoje embutidas em `TransferBetweenAccountsUseCase`: valor > 0, valor de destino > 0 quando informado, contas distintas, data não futura, e as duas contas existentes. Reusa `TransferError` como está; nenhum caso de erro novo.
- [ ] 1.2 Fazer `TransferBetweenAccountsUseCase` consumir o validador e **remover** as validações em linha, preservando byte a byte o erro devolvido em cada caso — a criação não muda de comportamento neste grupo.
- [ ] 1.3 Registrar `ValidateTransferUseCase` como `factory {}` em `AccountsModule` e injetá-lo em `TransferBetweenAccountsUseCase`.
- [ ] 1.4 Escrever `ValidateTransferUseCaseTest` cobrindo as cinco recusas e o caminho feliz.
- [ ] 1.5 Verificar que a suíte existente de transferência continua verde sem alteração: `./gradlew jvmTest`.

## 2. A reescrita passa a aceitar o conjunto de pernas

- [ ] 2.1 Trocar, em `ITransactionRepository.updateTransaction`, `leg: TransactionLeg` por `legs: List<TransactionLeg>`, e substituir o KDoc que documentava o estreitamento (o parágrafo "⚠️ Takes a **single** leg…") por um que descreva o estado atual, sem narrar a mudança.
- [ ] 2.2 Em `TransactionRepository.updateTransaction`, repassar `legs` a `rewriteEntries` em vez de `listOf(leg)`, e trocar a chamada `ensureDimensionsAccept(dimensionIds = setOfNotNull(leg.dimensionId), settlesALiability = false)` pela forma derivada `ensureDimensionsAccept(legs)`, que já existe no arquivo e é a que `createTransaction` usa. Manter intactos os dois guardas do estado anterior (`ensureDimensionsAcceptRemoval`, `ensureClosedAccountsKeepTheirBalance`) e a transação de escrita única.
- [ ] 2.3 Atualizar os 3 chamadores de produção para passar `listOf(...)`: `EditTransactionViewModel:278`, `AdjustBalanceUseCase:83`, `AdjustInvoiceUseCase:78`. Nenhum deles muda de comportamento.
- [ ] 2.4 Atualizar os 17 fakes de `ITransactionRepository` para a assinatura nova, mantendo cada corpo como está (`throw NotImplementedError()`, `notUnderTest()` ou a captura que o teste faz).
- [ ] 2.5 Atualizar as 3 chamadas diretas em teste: `TransactionRepositoryEntriesTest:170` e `InvoiceWriteGuardTest:262,312`.
- [ ] 2.6 Acrescentar a `TransactionRepositoryEntriesTest` (ou suíte equivalente sobre banco real) um caso de reescrita de **duas pernas em moeda única**, provando que as pernas antigas somem e as novas somam zero.
- [ ] 2.7 Acrescentar um caso de reescrita **atravessando moedas**, provando que as pernas de conversão são recriadas por moeda e que cada moeda soma zero.
- [ ] 2.8 Verificar que `settlesALiability` derivado devolve `false` para `ASSET → ASSET`, de modo que a reescrita de transferência passa pelo guarda com a mesma resposta que a constante dava.
- [ ] 2.9 `./gradlew jvmTest` verde — a barreira desta change fecha aqui.

## 3. O caso de uso de correção

- [ ] 3.1 Criar `UpdateTransferUseCase` em `feature/accounts/impl/domain/usecase/`, recebendo o id da transação além dos mesmos parâmetros da criação, consumindo `ValidateTransferUseCase` e escrevendo por `updateTransaction` com as duas pernas (`EXPENSE` na origem, `INCOME` no destino, `contra = null`). Devolve `Either<TransferException, Unit>`, no molde do caso de uso de criação.
- [ ] 3.2 Colher a taxa pela mesma chamada a `HarvestExchangeRateUseCase` que a criação faz, depois da escrita e sem desfazê-la em caso de falha. **Não** consultar nem remover observação alguma do acervo (design D5).
- [ ] 3.3 Registrar `UpdateTransferUseCase` como `factory {}` em `AccountsModule`.
- [ ] 3.4 Escrever `UpdateTransferUseCaseTest`: correção de valor em moeda única; correção trocando a conta de destino; correção de data; correção atravessando moedas; e as cinco recusas, provando que valem iguais às da criação.
- [ ] 3.5 Escrever o caso que prova que corrigir uma operação cruzada **para moeda única** não colhe observação nova e não remove a anterior.

## 4. O formulário ganha o modo de correção

- [ ] 4.1 Parametrizar `TransferBetweenAccountsModal` pela transação a corrigir (`transaction: Transaction? = null`), mantendo a conta inicial como está para o caminho de criação.
- [ ] 4.2 Em `TransferBetweenAccountsViewModel`, derivar `isEditMode = transaction != null` e semear origem, destino, valor, valor de destino e data a partir das pernas da transação — a origem é a perna negativa, o destino a positiva, cada valor na moeda da sua conta.
- [ ] 4.3 Rotear o `Submit` para `UpdateTransferUseCase` em modo de correção e para `TransferBetweenAccountsUseCase` em modo de criação, mantendo o tratamento de erro e o `dismiss` atuais.
- [ ] 4.4 Acrescentar `transfer_edit_title` aos **dois** arquivos de strings (`values/strings.xml` em pt e `values-en/strings.xml` em en) e usá-lo no cabeçalho quando `isEditMode`. O botão continua sendo um só.
- [ ] 4.5 Acrescentar o evento `EditTransferBetweenAccounts` a `core/analytics` (`event/Accounts.kt`), ao lado de `TransferBetweenAccounts`, e emiti-lo na correção bem-sucedida.
- [ ] 4.6 Verificar que o formulário em modo de correção continua sem oferecer categoria, cartão ou escolha de natureza — a impossibilidade de mudar a natureza é da forma, não de guarda.
- [ ] 4.7 Escrever teste de ViewModel: a semeadura reflete a transação; a submissão em modo de correção chama o caso de uso de correção e não o de criação.

## 5. O caminho até o formulário, sem violar `impl ⊄ impl`

- [ ] 5.1 Acrescentar `transferModal(transaction: Transaction? = null): Modal` a `AccountsEntry` (`feature/accounts/api`), no molde do `accountFormModal` já existente.
- [ ] 5.2 Implementá-lo em `AccountsEntryImpl` devolvendo `TransferBetweenAccountsModal(...)`.
- [ ] 5.3 Verificar que `AccountsEntry` continua declarado como `single<AccountsEntry>` em `AccountsModule` e que `feature/transactions/impl` alcança a modal por `koinInject`, sem nomear `feature/accounts/impl`.

## 6. O gate se abre

- [ ] 6.1 Reescrever `ViewTransactionUiState.isEditable` para decidir por rótulo: os quatro gates gerais primeiro (fatura `CLOSED`/`PAID` um nível acima, `ADJUSTMENT`, parcelamento, `isChangeable`), depois `EXPENSE`/`INCOME` com exatamente uma perna monetária, `TRANSFER` admitido, `PAYMENT` recusado por declaração própria. O KDoc passa a nomear os rótulos admitidos, e a recusa de `PAYMENT` diz que é escopo, não contagem.
- [ ] 6.2 Fazer `ViewTransactionModal` escolher o modal de correção pela natureza: `TRANSFER` abre o da transferência via `AccountsEntry`; os demais continuam abrindo `EditTransactionModal`.
- [ ] 6.3 Atualizar `ViewTransactionGatesTest`: inverter os dois casos de transferência (moeda única e cruzada) para editável; manter o de pagamento de fatura como não editável, agora com a razão nomeada; acrescentar o caso da transferência com perna em conta arquivada, que permanece congelada.
- [ ] 6.4 Verificar na `LedgerTest` que uma transferência cruzada (`{ASSET, CONVERSION}`) deriva rótulo `TRANSFER`, que é o que faz mono e cruzada compartilharem o gate — o caso já existe e passa a ser a prova do gate novo.

## 7. Fechamento

- [ ] 7.1 Escrever teste de ponta a ponta em `app/shared/src/jvmTest` corrigindo uma transferência **entre moedas**: a operação mantém a identidade, as quatro pernas são reescritas, cada moeda soma zero, os saldos das duas contas refletem os valores novos, e a observação de taxa de mesmo par e data é substituída em vez de duplicada.
- [ ] 7.2 Escrever o caso de ponta a ponta em que a correção **muda a data**, provando que a observação anterior permanece na data antiga e a nova é registrada na data nova.
- [ ] 7.3 Verificar que nenhuma chave de string ficou em apenas um dos dois arquivos de idioma.
- [ ] 7.4 Rodar a suíte completa: `./gradlew jvmTest`.
- [ ] 7.5 Acrescentar (ou estender) um fluxo Maestro em `.maestro/` que corrige o valor de uma transferência e confere o saldo das duas contas — lendo `.maestro/README.md` §2 antes de executar, e reportando em que dispositivo a execução aconteceu.
