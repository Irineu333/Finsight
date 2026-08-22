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

- [x] 1.1 Criar `ValidateTransferUseCase` em `feature/accounts/impl/domain/usecase/`, recebendo origem, destino, valor, valor de destino e data, e devolvendo `Either<TransferError, Unit>` — as cinco regras hoje embutidas em `TransferBetweenAccountsUseCase`: valor > 0, valor de destino > 0 quando informado, contas distintas, data não futura, e as duas contas existentes. Reusa `TransferError` como está; nenhum caso de erro novo.
- [x] 1.2 Injetar `Clock` no validador e remover a propriedade de topo `currentDate` que lê `Clock.System` (design D10). O relógio passa a ser o mesmo que o formulário e o seletor de data já usam.
- [x] 1.3 Fazer `TransferBetweenAccountsUseCase` consumir o validador e **remover** as validações em linha, preservando byte a byte o erro devolvido em cada caso — a criação não muda de comportamento neste grupo.
- [x] 1.4 Registrar `ValidateTransferUseCase` como `factory {}` em `AccountsModule`, com `clock = get()`, e injetá-lo em `TransferBetweenAccountsUseCase`.
- [x] 1.5 Escrever `ValidateTransferUseCaseTest` cobrindo as cinco recusas e o caminho feliz, com relógio fixo — o que a propriedade de topo não permitia.
- [x] 1.6 Verificar que a suíte existente de transferência continua verde sem alteração: `./gradlew jvmTest`.

## 1b. A perna de destino ganha dono no razão

- [x] 1b.1 Acrescentar `List<Entry>.destinationLeg()` a `core/ledger/extension/Ledger.kt`, espelhando `sourceLeg()`: filtra `ASSET` e devolve a perna de valor **positivo**. O filtro por `ASSET` é o que exclui a perna de conversão de resíduo positivo (design D7); o KDoc diz isso, sem narrar a mudança.
- [x] 1b.2 Estender `LedgerTest` provando que, numa transferência entre moedas com as quatro pernas, `destinationLeg()` devolve a conta de destino e nunca a de conversão.

## 2. A reescrita passa a aceitar o conjunto de pernas

- [x] 2.1 Trocar, em `ITransactionRepository.updateTransaction`, `leg: TransactionLeg` por `legs: List<TransactionLeg>`, e substituir o KDoc que documentava o estreitamento (o parágrafo "⚠️ Takes a **single** leg…") por um que descreva o estado atual, sem narrar a mudança.
- [x] 2.2 Em `TransactionRepository.updateTransaction`, repassar `legs` a `rewriteEntries` em vez de `listOf(leg)`, e trocar a chamada `ensureDimensionsAccept(dimensionIds = setOfNotNull(leg.dimensionId), settlesALiability = false)` pela forma derivada `ensureDimensionsAccept(legs)`, que já existe no arquivo e é a que `createTransaction` usa. Manter intactos os dois guardas do estado anterior (`ensureDimensionsAcceptRemoval`, `ensureClosedAccountsKeepTheirBalance`) e a transação de escrita única.
- [x] 2.3 Atualizar os 3 chamadores de produção para passar `listOf(...)`: `EditTransactionViewModel:278`, `AdjustBalanceUseCase:83`, `AdjustInvoiceUseCase:78`. Nenhum deles muda de comportamento.
- [x] 2.4 Atualizar os 17 fakes de `ITransactionRepository` para a assinatura nova, mantendo cada corpo como está (`throw NotImplementedError()`, `notUnderTest()` ou a captura que o teste faz).
- [x] 2.5 Atualizar as 3 chamadas diretas em teste: `TransactionRepositoryEntriesTest:170` e `InvoiceWriteGuardTest:262,312`.
- [x] 2.6 Acrescentar a `TransactionRepositoryEntriesTest` (ou suíte equivalente sobre banco real) um caso de reescrita de **duas pernas em moeda única**, provando que as pernas antigas somem e as novas somam zero.
- [x] 2.7 Acrescentar um caso de reescrita **atravessando moedas**, provando que as pernas de conversão são recriadas por moeda e que cada moeda soma zero.
- [x] 2.8 Verificar que `settlesALiability` derivado devolve `false` para `ASSET → ASSET`, de modo que a reescrita de transferência passa pelo guarda com a mesma resposta que a constante dava.
- [x] 2.9 `./gradlew jvmTest` verde — a barreira desta change fecha aqui.

## 3. O caso de uso de correção

- [x] 3.1 Criar `UpdateTransferUseCase` em `feature/accounts/impl/domain/usecase/`, recebendo o id da transação além dos mesmos parâmetros da criação, consumindo `ValidateTransferUseCase` e escrevendo por `updateTransaction` com as duas pernas (`EXPENSE` na origem, `INCOME` no destino, `contra = null`). Devolve `Either<TransferException, Unit>`, no molde do caso de uso de criação.
- [x] 3.1b Passar `title = transaction.title` na escrita, e **não** `null`: o formulário não oferece título, e reescrever a linha com `null` apagaria em silêncio um título que a tela não mostra (design D11).
- [x] 3.2 Colher a taxa pela mesma chamada a `HarvestExchangeRateUseCase` que a criação faz, depois da escrita e sem desfazê-la em caso de falha. **Não** consultar nem remover observação alguma do acervo (design D5).
- [x] 3.3 Registrar `UpdateTransferUseCase` como `factory {}` em `AccountsModule`.
- [x] 3.4 Escrever `UpdateTransferUseCaseTest`: correção de valor em moeda única; correção trocando a conta de destino; correção de data; correção atravessando moedas; e as cinco recusas, provando que valem iguais às da criação.
- [x] 3.5 Escrever o caso que prova que corrigir uma operação cruzada **para moeda única** não colhe observação nova e não remove a anterior.

## 4. O formulário ganha o modo de correção

- [x] 4.1 Dar a `TransferBetweenAccountsModal` dois construtores públicos sobre um privado — `(sourceAccount: Account)` para criar e `(transaction: Transaction)` para corrigir —, de modo que nenhum chamador consiga enunciar um estado inválido (design D8). Em modo de correção a conta de origem **não** é parâmetro: vem de `sourceLeg()`.
- [x] 4.2 Em `TransferBetweenAccountsViewModel`, derivar `isEditMode` do parâmetro recebido e semear origem (`sourceLeg()`), destino (`destinationLeg()`), valor, valor de destino e data a partir da transação, cada valor formatado na moeda da sua própria conta.
- [x] 4.2b Semear os três `TextFieldState` do modal (`amount`, `destinationAmount`, `date`) com os valores da transação em modo de correção, em vez do estado vazio e da data de hoje que a criação usa.
- [x] 4.3 Rotear o `Submit` para `UpdateTransferUseCase` em modo de correção e para `TransferBetweenAccountsUseCase` em modo de criação, mantendo o tratamento de erro e o `dismiss` atuais.
- [x] 4.4 Acrescentar `transfer_edit_title` aos **dois** arquivos de strings (`values/strings.xml` em pt e `values-en/strings.xml` em en) e usá-lo no cabeçalho quando `isEditMode`. O botão continua sendo um só.
- [x] 4.4b Acrescentar `transfer_edit_confirm` aos **dois** arquivos de strings e usá-lo no botão quando `isEditMode`: o botão continua sendo um só, mas o verbo tem de nomear o que a confirmação faz — "Transferir" numa correção afirma um movimento que já aconteceu.
- [x] 4.5 Acrescentar o evento `EditTransferBetweenAccounts` a `core/analytics` (`event/Accounts.kt`), ao lado de `TransferBetweenAccounts`, e emiti-lo na correção bem-sucedida.
- [x] 4.6 Verificar que o formulário em modo de correção continua sem oferecer categoria, cartão ou escolha de natureza — a impossibilidade de mudar a natureza é da forma, não de guarda.
- [x] 4.7 Escrever teste de ViewModel: a semeadura reflete a transação; a submissão em modo de correção chama o caso de uso de correção e não o de criação.

## 4b. O campo do valor de destino aprende o que é um valor gravado

- [x] 4b.1 Dar a `CounterpartAmountField` (`core/ui`) a noção do valor **pré-existente**, o terceiro tipo de número que ela ainda não distingue da oferta e da digitação (design D9).
- [x] 4b.2 Ajustar o efeito de sincronização para que um valor pré-existente **não seja sobrescrito** pela sugestão do acervo — nem pelo ramo que escreve a observação do dia, nem pelo `clearText()` do ramo que não tem o que oferecer, que hoje apagaria o valor gravado.
- [x] 4b.3 Manter a retirada do valor pré-existente quando a **moeda do campo muda**, pela mesma razão que a oferta é retirada: os dígitos de uma moeda não sobrevivem sob o símbolo de outra.
- [x] 4b.4 Escrever teste de composição cobrindo os três casos: abrir uma correção sem observação do dia preserva o valor gravado; abrir com observação do dia preserva o valor gravado; trocar a conta de destino retira-o.
- [x] 4b.5 Verificar que a criação continua idêntica — sem valor pré-existente, o componente se comporta como hoje, oferta e retirada incluídas.

## 5. O caminho até o formulário, sem violar `impl ⊄ impl`

- [x] 5.1 Acrescentar `editTransferModal(transaction: Transaction): Modal` a `AccountsEntry` (`feature/accounts/api`) — **um só** membro, sem parâmetro opcional: quem atravessa a fronteira é apenas a correção, já que a criação nasce no `AccountsScreen`, dentro do próprio módulo (design D8).
- [x] 5.2 Implementá-lo em `AccountsEntryImpl` devolvendo `TransferBetweenAccountsModal(transaction)`.
- [x] 5.3 Verificar que `AccountsEntry` continua declarado como `single<AccountsEntry>` em `AccountsModule` e que `feature/transactions/impl` alcança a modal por `koinInject`, sem nomear `feature/accounts/impl`.

## 6. O gate se abre

- [x] 6.1 Reescrever `ViewTransactionUiState.isEditable` para decidir por rótulo: os quatro gates gerais primeiro (fatura `CLOSED`/`PAID` um nível acima, `ADJUSTMENT`, parcelamento, `isChangeable`), depois `EXPENSE`/`INCOME` com exatamente uma perna monetária, `TRANSFER` admitido, `PAYMENT` recusado por declaração própria. O KDoc passa a nomear os rótulos admitidos, e a recusa de `PAYMENT` diz que é escopo, não contagem.
- [x] 6.2 Fazer `ViewTransactionModal` escolher o modal de correção pela natureza: `TRANSFER` abre o da transferência via `AccountsEntry`; os demais continuam abrindo `EditTransactionModal`.
- [x] 6.3 Atualizar `ViewTransactionGatesTest`: inverter os dois casos de transferência (moeda única e cruzada) para editável; manter o de pagamento de fatura como não editável, agora com a razão nomeada; acrescentar o caso da transferência com perna em conta arquivada, que permanece congelada.
- [x] 6.4 Verificar na `LedgerTest` que uma transferência cruzada (`{ASSET, CONVERSION}`) deriva rótulo `TRANSFER`, que é o que faz mono e cruzada compartilharem o gate — o caso já existe e passa a ser a prova do gate novo.

## 7. Fechamento

- [x] 7.1 Escrever teste de ponta a ponta em `app/shared/src/jvmTest` corrigindo uma transferência **entre moedas**: a operação mantém a identidade, as quatro pernas são reescritas, cada moeda soma zero, os saldos das duas contas refletem os valores novos, e a observação de taxa de mesmo par e data é substituída em vez de duplicada.
- [x] 7.2 Escrever o caso de ponta a ponta em que a correção **muda a data**, provando que a observação anterior permanece na data antiga e a nova é registrada na data nova.
- [x] 7.3 Verificar que nenhuma chave de string ficou em apenas um dos dois arquivos de idioma.
- [x] 7.4 Rodar a suíte completa: `./gradlew jvmTest`.
- [ ] 7.5 Acrescentar (ou estender) um fluxo Maestro em `.maestro/` que corrige o valor de uma transferência e confere o saldo das duas contas — lendo `.maestro/README.md` §2 antes de executar, e reportando em que dispositivo a execução aconteceu.
