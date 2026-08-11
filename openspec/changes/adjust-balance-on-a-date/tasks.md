# Tasks — adjust-balance-on-a-date

> Cada grupo é uma barreira. Dentro de um grupo, nenhuma tarefa lê a saída de outra e duas tarefas
> nunca escrevem o mesmo arquivo — um subagente por tarefa pode implementar o grupo inteiro de uma
> vez. Onde os artefatos impõem sequência (a leitura datada antes dos seus consumidores; a projeção
> e a string antes dos modais que as nomeiam), a sequência está registrada com o motivo.

## Grupo 1 — A leitura escalar por conta passa a cortar por data

*Barreira de entrada:* nenhuma — a árvore está limpa e a suíte é a do estado atual.
*Barreira de saída:* `:core:ledger` compila (`./gradlew :core:ledger:compileKotlinJvm`), a leitura
datada existe e o acumulado mensal deriva dela, sem segunda consulta.
**Grupo de tarefa única por imposição de D6:** a leitura datada é a leitura real e todos os
consumidores dos grupos 2 em diante — inclusive os dublês de teste — nomeiam a assinatura nova.
Enquanto ela não existir, não há o que ajustar neles.
**Consequência declarada e esperada:** ao fim deste grupo os módulos que dublam `IEntryRepository`
e `EntryDao` **não compilam** — o compilador aponta exatamente os sítios, e é o grupo 2 que os fecha.

- [ ] 1.1 Levar o corte por data à leitura escalar de conta em `:core:ledger`, nos quatro arquivos
  do caminho, que são sequencialmente dependentes entre si e por isso vivem numa tarefa só:
  - `core/ledger/src/commonMain/kotlin/com/neoutils/finsight/database/dao/EntryDao.kt:211-217` —
    `balanceUpToMonth(accountId, yearMonth)` passa a `balanceUpToDate(accountId, date)` com
    `WHERE e.accountId = :accountId AND o.date <= :date`. O `substr(o.date, 1, 7)` sai; a coluna
    já guarda a data completa e o mês era perda de resolução aplicada na consulta.
  - `core/ledger/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IEntryRepository.kt:132-136`
    — `accountBalanceUpTo(accountId, target: LocalDate)` vira o **único membro abstrato**, e a
    forma mensal permanece como membro **não abstrato** que delega a `target.lastDay`. É onde a
    derivação "mês = último dia do mês" tem dono único, e é o que mantém intactos os dois sítios
    mensais de `feature/accounts/impl/.../ui/screen/accounts/AccountsViewModel.kt:114,119`, que
    não entram no escopo desta mudança.
  - `core/ledger/src/commonMain/kotlin/com/neoutils/finsight/database/repository/EntryRepository.kt:65-66`
    — implementa a leitura datada sobre `balanceUpToDate`, mantendo `CENTS_PER_UNIT`.
  - `core/ledger/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/CalculateBalanceUseCase.kt:41-43`
    — `forAccount` ganha a sobrecarga por `LocalDate`; a sobrecarga por `YearMonth` continua
    existindo e apenas repassa, sem recalcular a borda do mês por conta própria.
  - **Não tocar** em `balanceUpToMonthByType`, `balanceUpToByCurrency` nem
    `naturalBalanceUpToByCurrency`: por D6 as leituras por moeda seguem mensais, e a assimetria é
    deliberada. Registrar isso no KDoc da leitura datada, em inglês, descrevendo o estado atual.

## Grupo 2 — Os consumidores e os dublês da leitura datada

*Barreira de entrada:* grupo 1 concluído — a assinatura nova precisa existir antes de ser
implementada ou chamada.
*Barreira de saída:* o projeto inteiro volta a compilar e `./gradlew jvmTest testDebugUnitTest`
passa, com o teste novo da consulta datada verde. As tarefas abaixo estão em módulos distintos e
em arquivos distintos; nenhuma lê a saída de outra.

- [ ] 2.1 `core/ledger/src/jvmTest/kotlin/com/neoutils/finsight/database/BalanceUpToMonthQueryTest.kt`
  — adaptar as seis chamadas a `balanceUpToMonth` (linhas 43-60 e 206) para a consulta datada,
  renomeando o arquivo/classe para o que ela passa a exercitar. Acrescentar os dois cenários que a
  spec `ledger-reporting` traz: **saldo até o dia 10** com lançamentos no dia 5 e no dia 20 do mesmo
  mês inclui o do dia 5 e exclui o do dia 20; e **o acumulado até um mês é o acumulado até o último
  dia daquele mês**, pela mesma consulta. Manter verificada a invariante de que a data da transação
  é a única referência do corte.
- [ ] 2.2 `core/database/src/jvmTest/kotlin/com/neoutils/finsight/database/MigrationLedgerReadParityTest.kt:78-79`
  — as duas asserções passam a usar a consulta datada, preservando exatamente a paridade que o teste
  existe para fixar (nada de 2023-12 vira nada; janeiro/2024 continua 78000).
- [ ] 2.3 `:feature:transactions:impl` — ajustar os três dublês do módulo:
  `src/commonTest/.../database/repository/EntryRepositoryTest.kt` (o dublê de `EntryDao` na linha 79
  e a asserção da linha 20), `src/commonTest/.../ui/screen/transactions/FakeLedger.kt:72` e
  `src/commonTest/.../database/repository/LedgerEntryWriterTest.kt:481`.
- [ ] 2.4 `feature/transactions/api/src/commonTest/kotlin/com/neoutils/finsight/domain/usecase/CalculateBalanceUseCaseTest.kt`
  — dublê (linha 32) e chamada (linha 76) na forma datada, mais um caso fixando que `forAccount`
  por mês devolve o mesmo número que `forAccount` no último dia daquele mês.
- [ ] 2.5 `:feature:accounts:impl` — em
  `src/commonMain/.../domain/usecase/AdjustBalanceUseCase.kt:29-35`, ler o saldo corrente **na data
  do ajuste** (`forAccount(accountId, adjustmentDate)`), removendo o `adjustmentDate.yearMonth`: é a
  divergência do *Why* eliminada na origem, e o que torna a diferença exibida igual à gravada.
  Ajustar no mesmo passo os dublês de `src/commonTest/.../domain/usecase/AdjustBalanceUseCaseTest.kt:199`,
  `src/commonTest/.../domain/usecase/RetireAccountGuardsTest.kt:219` e
  `src/commonTest/.../ui/screen/accounts/AccountsEmptyStateTest.kt:235`.
- [ ] 2.6 `:feature:creditcards:impl` — dublês de `IEntryRepository` em
  `CreditCardsEmptyStateTest.kt:270`, `InvoiceTransactionsFakes.kt:144`,
  `DeleteCreditCardUseCaseTest.kt:117`, `AdjustInvoiceUseCaseTest.kt:227` e
  `CalculateInvoiceOverviewsUseCaseTest.kt:82`.
- [ ] 2.7 `:feature:dashboard:impl` — dublês em `DashboardTotalBalancePerimeterTest.kt:169`,
  `DashboardPendingBalanceStatsTest.kt:163`, `DashboardOverallBalanceStatsTest.kt:226` e
  `DashboardAccountsOverviewTest.kt:238`.
- [ ] 2.8 `:feature:budgets` — dublês em `impl/.../ui/modal/viewBudget/ViewBudgetViewModelTest.kt:142`,
  `impl/.../database/repository/BudgetClosedCategoryTest.kt:233` e
  `api/.../domain/usecase/CalculateBudgetProgressUseCaseTest.kt:264`.
- [ ] 2.9 `:feature:report:impl` — dublês em
  `ui/screen/report/viewer/ReportViewerViewModelCharacterizationTest.kt:335` e
  `domain/usecase/CalculateReportStatsUseCaseTest.kt:100`.
- [ ] 2.10 `:feature:categories:impl` — dublês em `ui/modal/viewCategory/ViewCategoryViewModelTest.kt:122`,
  `domain/usecase/CalculateCategorySpendingUseCaseImplTest.kt:295` e
  `domain/usecase/DeleteCategoryGuardsTest.kt:181`.

## Grupo 3 — A projeção de data com dono, e a string única

*Barreira de entrada:* grupo 2 concluído — o projeto compila e a suíte está verde.
*Barreira de saída:* o projeto continua compilando e a suíte verde; a função de projeção existe no
domínio de `:feature:accounts:impl` e a chave nova de título existe **nos dois idiomas**. Nada de
comportamento muda aqui — as duas tarefas são insumo do grupo 4, que é quem as nomeia.
As duas tarefas escrevem arquivos distintos.
**Sequência imposta:** a projeção e a chave vêm antes dos modais porque o modal do grupo 4 as
consome; e a chave é **acrescentada** aqui, não substituída — remover as três antigas enquanto o
modal ainda as nomeia quebraria a compilação. A remoção é o grupo 6.

- [ ] 3.1 Criar a projeção de data no domínio de `:feature:accounts:impl` — arquivo novo em
  `feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/domain/` —, dona única da regra
  "o saldo de abertura de um mês é o saldo ao fim do mês anterior" e da sua irmã de fechamento. Duas
  funções nomeadas, ambas na forma de D2 (`projeção_do_contexto.coerceAtMost(hoje)`): abertura de
  `M` → `(M-1).lastDay` limitado a hoje; fechamento de `M` → `M.lastDay` limitado a hoje — é o `min`
  que preserva o significado do gesto em mês passado, e o que faz o mês corrente abrir em hoje. Hoje
  entra como parâmetro (a `Clock` já é injetável), nunca lido de dentro. A `AccountsScreen`
  MUST NOT reimplementar isso: ela escolhe qual atalho oferecer, nunca qual data ele significa.
  KDoc objetivo, em inglês, sobre o estado atual.
- [ ] 3.2 Acrescentar a chave única de título do ajuste de conta a
  `core/resources/src/commonMain/composeResources/values/strings.xml` (pt) **e**
  `core/resources/src/commonMain/composeResources/values-en/strings.xml` (en), na mesma mudança —
  uma chave presente em só um dos dois é bug. As três antigas
  (`edit_account_balance_current_title`, `_final_title`, `_initial_title`, linhas 482-484 / 481-483)
  **permanecem por enquanto**; o modal ainda as nomeia. Nenhuma chave nova é necessária para o aviso
  de divergência da fatura: `transaction_date_outside_invoice` já existe nos dois arquivos
  (`values/strings.xml:386`, `values-en/strings.xml:385`) e é reaproveitada.

## Grupo 4 — Os dois modais ganham data, e o tipo de ajuste some

*Barreira de entrada:* grupos 2 e 3 concluídos — a leitura datada, a projeção e a chave nova
precisam existir antes de serem nomeadas.
*Barreira de saída:* o projeto compila, `./gradlew jvmTest testDebugUnitTest` passa,
`EditAccountBalanceModal.Type` e os dois invólucros de data não existem mais, e os dois modais
gravam na data escolhida. As três tarefas estão em módulos/arquivos distintos: 4.1 em
`:feature:accounts:impl` (main), 4.2 em `:feature:creditcards:impl` (main), 4.3 em
`:feature:accounts:impl` (commonTest, arquivo novo).

- [ ] 4.1 Ajuste de conta — campo de data, tipo apagado. Nos arquivos de
  `feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/`:
  - `ui/modal/editAccountBalance/EditAccountBalanceModal.kt` — apagar o `enum class Type`
    (linhas 260-264) e o parâmetro `type`; o construtor passa a receber a **data inicial** já
    projetada, não um tipo nem um mês. Título único (chave de 3.2), subtítulo de mês removido
    (linhas 78-85 e 144-151) — o campo de data diz a mesma coisa com mais precisão (D5).
    Acrescentar o campo de data no mesmo desenho já usado em
    `feature/transactions/impl/.../addTransaction/AddTransactionModal.kt:64-90,262-299`:
    `TextFieldState` + `snapshotFlow` para o estado, `LaunchedEffect` com guarda de igualdade para
    a sincronização reversa, `DatePickerModal` com **`maxDate = hoje`**. As tags
    `edit_account_balance_amount` e `edit_account_balance_save` permanecem exatamente como estão —
    o E2E as alcança.
  - `ui/modal/editAccountBalance/EditAccountBalanceViewModel.kt` — remover `type`, `targetMonth` e
    os dois casos de uso invólucro; o estado passa a ter a data, o saldo de referência é lido em
    `calculateBalanceUseCase.forAccount(accountId, data)` e **muda quando a data muda**, sem ação
    adicional do usuário. `submit` chama sempre `adjustBalanceUseCase(targetBalance, data, account)`.
  - `ui/modal/editAccountBalance/EditAccountBalanceUiState.kt` e `.../EditAccountBalanceAction.kt` —
    a data no estado e a ação de trocá-la.
  - `ui/screen/accounts/AccountsScreen.kt:256-273` — os dois pontos de entrada passam a **data
    padrão** vinda da projeção de 3.1 (abertura e fechamento do mês visível), não um tipo. Continuam
    sendo dois atalhos; diferem só pela data.
  - `di/AccountsModule.kt:104-105,162-176` — remover as duas `factory` dos invólucros e os
    parâmetros `type`/`targetMonth` da `viewModel`, passando a data inicial e a `Clock`.
  - Apagar `domain/usecase/AdjustFinalBalanceUseCase.kt` e `domain/usecase/AdjustOpeningBalanceUseCase.kt`
    — nenhum tem teste próprio e, depois do acima, nenhum consumidor.
- [ ] 4.2 Ajuste de fatura — campo de data, projeção na troca de fatura, teto em hoje, aviso de
  divergência. Nos arquivos de
  `feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/editInvoiceBalance/`:
  - `EditInvoiceBalanceViewModel.kt` — a data entra no estado com padrão
    `window.dateOn(hoje.day).coerceAtMost(hoje)`, e é **reprojetada ao trocar de fatura ou de
    cartão** pela mesma hierarquia de `invoice-governs-date` (coletor sobre a fatura selecionada,
    nunca sobre a data — é o que torna a assimetria estrutural). `submit` grava a data escolhida no
    lugar do `currentDate` fixo (linha 119).
  - `EditInvoiceBalanceUiState.kt` — a data e o `val isDateOutsideInvoice` derivado de
    `InvoiceMonthSelection.diverges` (`core/model/.../InvoiceMonthSelection.kt:33`), pelo mesmo
    precedente dos outros três `UiState`. Derivado, não campo de construtor.
  - `EditInvoiceBalanceAction.kt` — a ação de trocar a data.
  - `EditInvoiceBalanceModal.kt` — o campo de data com `DatePickerModal`, **`maxDate = hoje` e
    nenhum piso**: por D3 um ajuste não é uma compra e não tem o fechamento como teto nem a abertura
    como piso. O aviso entra como `supportingText` com `Res.string.transaction_date_outside_invoice`,
    gated só por `uiState.isDateOutsideInvoice`, sem cor de erro e sem bloquear o envio — divergir
    não é errar, e nenhuma decisão fica na composable.
  - Se a construção de `InvoiceMonthSelection` for necessária no ViewModel, ela já exige o cartão
    (`creditCard`, `dueMonth`, `existingInvoice`), que o estado do modal já tem.
- [ ] 4.3 Criar teste da projeção de 3.1 em
  `feature/accounts/impl/src/commonTest/kotlin/com/neoutils/finsight/domain/`, cobrindo os cenários
  de `balance-adjustment`: fechamento de março com hoje em 11/agosto → 31/março; fechamento de
  agosto com hoje em 11/agosto → 11/agosto (e não o fim do mês, que ainda não ocorreu); abertura de
  março → 28/fevereiro; abertura/fechamento de mês futuro travam em hoje; fim de mês curto
  (fevereiro bissexto e não bissexto) sem estourar o dia.

## Grupo 5 — Os cenários das specs viram teste

*Barreira de entrada:* grupo 4 concluído — sem os campos de data nos ViewModels não há o que
exercitar.
*Barreira de saída:* `./gradlew jvmTest testDebugUnitTest` verde com a saída lida, e cada requisito
das três specs delta tem ao menos um teste que falharia sem a mudança. As quatro tarefas escrevem
arquivos distintos.

- [ ] 5.1 Criar teste de `EditAccountBalanceViewModel` em
  `feature/accounts/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/editAccountBalance/`,
  com `Clock` fixa, cobrindo `balance-adjustment`: o atalho de abertura de março abre em 28/fevereiro
  **e pré-preenche com o saldo em 28/fevereiro**, não com o saldo ao fim de março (é a divergência do
  *Why*, e o teste que a prova morta); o atalho de fechamento de março abre em 31/março; o de
  fechamento do mês corrente abre em hoje; alterar a data move o valor de referência e a diferença
  exibida; a diferença exibida é exatamente a diferença aplicada ao razão; abrir por um atalho e
  mover a data para a do outro produz resultado idêntico, entrada por entrada.
- [ ] 5.2 Criar teste de `EditInvoiceBalanceViewModel` em
  `feature/creditcards/impl/src/commonTest/kotlin/com/neoutils/finsight/ui/modal/editInvoiceBalance/`,
  cobrindo `balance-adjustment` e `invoice-governs-date`: ajustar hoje (11/agosto) a fatura de
  janeiro sem tocar na data grava em 11/agosto, conta integralmente na fatura de janeiro e **sinaliza
  a divergência**; escolher data dentro da janela não sinaliza nada; data anterior à abertura da
  fatura é aceita como escrita (a janela não é piso); trocar de fatura recoloca a data na janela nova
  preservando o dia e travando em hoje; alterar apenas a data não muda o valor devido pela fatura,
  porque é a dimensão que o decide; ajustar fatura já fechada com data posterior ao fechamento é
  aceito (o ajuste não tem o fechamento como teto).
- [ ] 5.3 Ampliar
  `feature/accounts/impl/src/commonTest/kotlin/com/neoutils/finsight/domain/usecase/AdjustBalanceUseCaseTest.kt`
  com os cenários de "Cada ajuste é um evento datado" que ainda não estão lá: ajustar o mesmo alvo em
  **duas datas diferentes** produz dois lançamentos, um em cada data; reajustar de volta ao valor
  original remove o lançamento da data; alterar lançamentos anteriores à data do ajuste propaga ao
  saldo corrente **sem** reescrever o ajuste gravado (o ajuste é delta, não alvo persistido). O caso
  já existente de reajuste na mesma data permanece como está.
- [ ] 5.4 Reexecutar e conferir os testes que cobrem o comportamento atual do gesto —
  `feature/accounts/impl/src/commonTest/.../BalanceAdjustmentSignTest.kt` e
  `feature/creditcards/impl/src/commonTest/.../InvoiceAdjustmentSignTest.kt` —, ajustando-os
  **apenas** onde a mudança de data os alcança. O formato do lançamento não muda (perna `ADJUSTMENT`
  + contra-perna `EQUITY`) e o sinal exibido não muda: se algum deles precisar de mais que a data,
  isso é achado a reportar, não a acomodar em silêncio.

## Grupo 6 — Limpeza do que ficou órfão

*Barreira de entrada:* grupo 5 concluído — só depois de nada mais nomear os símbolos é seguro
apagá-los.
*Barreira de saída:* o projeto compila, `./gradlew jvmTest testDebugUnitTest` continua verde e
nenhuma chave ou classe sem consumidor sobrou. As duas tarefas escrevem arquivos distintos.

- [ ] 6.1 Remover as três chaves de título obsoletas — `edit_account_balance_current_title`,
  `edit_account_balance_final_title`, `edit_account_balance_initial_title` — de
  `core/resources/src/commonMain/composeResources/values/strings.xml:482-484` **e** de
  `values-en/strings.xml:481-483`, confirmando antes por busca que nada as nomeia. As duas remoções
  na mesma mudança: uma chave presente em só um dos dois arquivos é bug, e uma ausente em só um
  também.
- [ ] 6.2 Conferir por busca se `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/exception/FutureMonthAdjustmentException.kt`
  ficou sem consumidor — pelo proposal ela fica inalcançável quando o teto vira limite do seletor,
  já que seus dois únicos usos eram os invólucros apagados em 4.1 — e removê-la se e somente se a
  busca confirmar. Se algo ainda a nomear, não remover e reportar.

## Grupo 7 — Verificação

*Barreira de entrada:* grupos 1 a 6 concluídos.
*Barreira de saída:* a suíte rodou com a saída lida, o E2E rodou num dispositivo declarado, o
comportamento foi exercido no app real e o escopo do diff é o escopo do proposal. Nenhuma tarefa
deste grupo edita arquivo algum.

- [ ] 7.1 Rodar a suíte e **ler a saída**, reportando o resultado real e não a expectativa:
  `./gradlew allTests`; se o alvo iOS não linkar nesta máquina, registrar o desvio e cair para
  `./gradlew jvmTest testDebugUnitTest` mais a compilação Kotlin/Native dos módulos tocados.
- [ ] 7.2 Reexecutar o E2E de contas — **não reescrever por presunção**: ler `.maestro/README.md` §2
  antes, checar o dispositivo pelos sete `adb` (AVD `pixel_6` API 36, em inglês, teclado na tela e
  sem teclado físico), reinstalar o debug (`./gradlew :app:android:installDebug`) e rodar
  `.maestro/flows/accounts/lifecycle.yaml`. O risco é de layout: o campo de data novo pode deslocar
  `edit_account_balance_amount`/`edit_account_balance_save` sob teclado aberto. Reportar em qual
  dispositivo a execução aconteceu.
- [ ] 7.3 Exercitar no app (`./gradlew :app:desktop:run` ou `:app:android:installDebug`): abrir os
  dois atalhos de saldo num mês passado e no mês corrente e conferir a data e o valor pré-preenchido;
  mover a data e ver o valor de referência e a diferença acompanharem; confirmar que o seletor não
  oferece data futura em nenhum dos dois modais; ajustar hoje uma fatura antiga e ver o aviso
  discreto sem bloqueio; trocar de fatura e ver a data reprojetada.
- [ ] 7.4 Conferir no diff que o escopo declarado foi respeitado: nada em migrações, no
  `AppDatabase`, no boundary de escrita (`LedgerEntryWriter`), no `InvoiceWriteGuard`, no formato do
  lançamento de ajuste, nem nas leituras por moeda (`balanceUpToByCurrency`,
  `naturalBalanceUpToByCurrency`), que seguem mensais por D6; os modais de pagar fatura e antecipar
  pagamento intocados; "Saldo Inicial" não renomeado nas telas de conta e de relatório.
