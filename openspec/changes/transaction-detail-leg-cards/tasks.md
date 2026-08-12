# Tasks — transaction-detail-leg-cards

## 1. Vocabulário novo e a linha de contexto com dono

*Barreira de entrada:* nenhuma — o repositório compila e a suíte está verde no estado atual.
*Barreira de saída:* as chaves novas existem nos **dois** idiomas e `DetailRow` existe em
`:core:ui`; nada as consome ainda, então nenhum comportamento muda e o projeto continua
compilando. As duas tarefas escrevem arquivos disjuntos e nenhuma lê a saída da outra. Este
grupo vem primeiro porque o mapper do grupo 2 nomeia as chaves em tempo de compilação.

- [x] 1.1 Acrescentar as chaves novas a `core/resources/src/commonMain/composeResources/values/strings.xml` (pt) **e** a `core/resources/src/commonMain/composeResources/values-en/strings.xml` (en), na mesma tarefa — uma chave presente em só um dos dois é bug conhecido do projeto. São dois conjuntos: (a) os **cinco verbos** da tabela do requisito *O verbo de um card deriva do razão, não da fachada* / D2 — `view_transaction_leg_verb_adjusted` (*Ajustou* / *Adjusted*), `view_transaction_leg_verb_left` (*Saiu de* / *Left*), `view_transaction_leg_verb_entered` (*Entrou em* / *Entered*), `view_transaction_leg_verb_charged` (*Lançou em* / *Charged to*), `view_transaction_leg_verb_settled` (*Abateu de* / *Settled from*); (b) os **cinco rótulos de natureza** do cabeçalho (requisito *O cabeçalho do detalhe exibe a natureza da operação* / D4) — `view_transaction_nature_expense`, `_income`, `_transfer`, `_payment`, `_adjustment`. Os rótulos de natureza são chaves próprias e **não** reaproveitam `view_transaction_type_income/expense/adjustment` (linhas 512-514), que são vocabulário de **direção de perna** e morrem com ela. Não remover chave alguma aqui: a limpeza das que ficam órfãs (`view_transaction_amount_label`, `view_transaction_applied_rate_label`, `view_transaction_origin_label`, `view_transaction_origin_account`, `view_transaction_origin_credit_card`, `view_transaction_source_account_label`, `view_transaction_destination_account_label`, `view_transaction_account_label`, `view_transaction_card_label`, `view_transaction_installment_label`, `view_transaction_type_*`, `view_adjustment_adjusted_value_label`, `view_adjustment_type_row_label`, `view_adjustment_account_label`, `view_adjustment_card_label`, `view_adjustment_credit_card_label`) é do grupo 6, quando já se sabe quais sobraram sem leitor.
- [x] 1.2 Criar `core/ui/src/commonMain/kotlin/com/neoutils/finsight/ui/component/DetailRow.kt` com o `DetailRow(label, value, modifier, valueColor, valueTestTag, onClick)` público, transcrito de `ViewTransactionModal.kt:510-550` — hoje duplicado ali e em `ViewAdjustmentModal.kt:308`. D5 exige dono único porque as linhas de contexto que restam (data, recorrência) são consumidas pelas duas modais. Comportamento idêntico ao atual, inclusive o ícone `OpenInNew` quando há `onClick` e o `testTag` opcional no valor. Não apagar ainda as cópias privadas — isso é do grupo 6.

## 2. O modelo de perna e o seu mapper em `core:ui`

*Barreira de entrada:* grupo 1 concluído — o mapper resolve o verbo em `UiText`/`Res.string` e
precisa das chaves já existentes.
*Barreira de saída:* `:core:ui` compila e `TransactionLegUi` mais o seu mapper existem, sem
consumidor algum. **Grupo de uma tarefa por imposição de D5:** o modelo precisa existir antes
do componente (grupo 3) e antes dos estados que o montam (grupo 4), e ambos o nomeiam.

- [x] 2.1 Criar `core/ui/src/commonMain/kotlin/com/neoutils/finsight/ui/model/model/TransactionLegUi.kt` com o DTO plano de D5 — `verb: UiText`, `name: String`, `currencyCode: String?`, `amount: DisplayAmount`, o par fatura (rótulo + cor de status) e a parcela como campos opcionais, e `onClick: (() -> Unit)?` — e o mapper que o produz a partir de `Transaction`. Regras que o mapper implementa e que **não** podem vazar para composable alguma (`presentation-mapping` §*Mappers como única fronteira*): (a) uma perna por `Transaction.monetaryEntries` (`core/ledger/.../Transaction.kt:44`), nunca por moeda — D1 e o requisito *O detalhe de uma operação exibe um card por perna monetária*; (b) o verbo derivado de `(entry.account.type, sinal de entry.amount)` com o override de `EQUITY` sobre a **transação inteira**, exatamente a tabela de D2, **sem consultar `TransactionLabel`**; (c) o valor como `DisplayAmount.magnitude` em todo card e `EXPLICIT_SIGN` com o sinal do razão apenas quando há perna `EQUITY` — D3 e `money-display` §*A superfície de operação exibe módulo, porque o verbo entrega a direção*, sem inverter sinal por tipo de conta; (d) a ordem começando por `Transaction.primaryEntry` (`Transaction.kt:63-64`), consumindo a definição existente e **não** reimplementando "a que sai primeiro" — D6 e `presentation-mapping` §*A escolha da perna neutra tem um dono*; (e) `currencyCode` preenchido só quando as pernas monetárias não compartilham moeda, que é a regra que `ViewTransactionUiState.namesAccountCurrency` já expressa (`ViewTransactionUiState.kt:161-162`); (f) `onClick` ausente quando a fachada está arquivada, porque a tela de destino não a lista mais. O DTO **não** declara campo de tipo de domínio; a navegação chega como lambda montada pelo chamador, já que `:core:ui` não vê rota de feature alguma. KDoc objetivo, em inglês, descrevendo o estado atual.

## 3. O card e o conector de taxa

*Barreira de entrada:* grupo 2 concluído — o componente renderiza `TransactionLegUi` e não
compila sem ele.
*Barreira de saída:* `:core:ui` compila com o componente e o conector, ainda sem consumidor.
**Grupo de uma tarefa pela mesma imposição de D5:** o componente precede as duas modais.

- [x] 3.1 Criar `core/ui/src/commonMain/kotlin/com/neoutils/finsight/ui/component/TransactionLegCard.kt` com: o card que responde as três perguntas do requisito *O detalhe de uma operação exibe um card por perna monetária* — verbo em texto pequeno acima, nome e valor na mesma linha (mitigação de altura registrada em *Risks*) —, a fatura com a cor de `InvoiceStatusColor` (`core/ui/.../ui/extension/InvoiceStatusColor.kt`, já em `:core:ui`, sem dependência nova) e a parcela **dentro** do card, conforme o requisito *A fatura e a parcela vivem dentro do card da perna a que pertencem*; um parâmetro de `testTag` para o valor, que o grupo 6 usa; e o conector entre dois cards, que recebe a taxa **já formatada** pelo chamador e a seta, satisfazendo o requisito *A taxa praticada é o conector entre os dois cards*. O componente **não deriva nada**: verbo, sinal e ordem chegam prontos do mapper (D5). Sem taxa, o conector não é exibido.

## 4. Os dois estados de UI

*Barreira de entrada:* grupos 2 e 3 concluídos — os estados montam `TransactionLegUi`.
*Barreira de saída:* os dois estados expressam a composição nova. **O módulo `feature/transactions/impl` deliberadamente não compila entre o fim deste grupo e o fim do grupo 6:** as ViewModels (grupo 5) e as modais (grupo 6) ainda nomeiam campos que morrem aqui. As
assinaturas resultantes são fixadas por D4 e D8, então nenhuma tarefa dos grupos 5 e 6 precisa
ler a saída deste — o compilador aponta exatamente os sítios. As duas tarefas abaixo escrevem
arquivos distintos.

- [x] 4.1 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/viewTransaction/ViewTransactionUiState.kt`: remover de `Content` os campos `perspective` e `baseCurrency` e tudo que só eles alimentavam — `perspectiveEntry` (`:73-74`), `direction` (`:80-82`) e `amount` (`:98-107`) —, por D4 e pelo requisito *O detalhe de uma operação não declara perspectiva*; acrescentar a lista de `TransactionLegUi` montada pelo mapper do grupo 2, mantendo `transaction` como campo próprio ao lado dela (D5, o arranjo de `presentation-mapping` §*Modelos de UI sem grafo de domínio*), porque `DeleteTransactionModal` e `EditTransactionModal` continuam precisando do agregado; manter `label`, `date`, `recurring`, `appliedRate` (`:135-150`, cuja direção já é `out → into` e concorda com a ordem dos cards por construção — D6) e **intactos** os portões `isChangeable`/`isEditable`/`isRemovable` (`:185-198`), que são Non-Goal declarado; mover para dentro do card os insumos de fatura e parcela, inclusive `installmentTotal` (`:115-123`), e retirar `account`/`sourceAccount`/`destinationAccount`/`isCardTarget` conforme deixem de ter leitor. `displayTitle` continua vindo de `displayTitleOf`, mas passa a ser **nulo quando não há título próprio nem categoria**, em vez de recair no literal `"Untitled"` de `core/model/.../extension/DisplayTitle.kt:24` — requisito *O cabeçalho do detalhe exibe a natureza da operação* e D4.
- [x] 4.2 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/viewAdjustment/ViewAdjustmentUiState.kt`: substituir `signedAmount` (`:33-42`) pela mesma lista de `TransactionLegUi` do mapper, por D7 e pelo requisito *O detalhe de um ajuste usa a mesma composição* — o verbo sai do override de `EQUITY` e o sinal explícito de D3, sem que este estado reimplemente nenhum dos dois. Manter **sem alteração** `isChangeable` e `isDeletable` (`:50-66`) e a mensagem de bloqueio: D7 diz explicitamente que os portões não são unificados. Retirar `account`/`isCardTarget` conforme percam leitor no grupo 6.

## 5. ViewModels, DI e a quebra de api

*Barreira de entrada:* 4.1 concluída — os campos que somem daqui são os que o estado deixou de
declarar.
*Barreira de saída:* a superfície de api e a montagem por DI estão finais; `feature/accounts/impl`
não nomeia mais perspectiva ao abrir o detalhe. A compilação do projeto **só fecha no fim do
grupo 6**, quando as modais param de passar `perspective`. As cinco tarefas escrevem arquivos
distintos e todas as assinaturas envolvidas estão fixadas por D4 e D8, então nenhuma depende da
saída de uma irmã. `ViewAdjustmentViewModel` **não muda** — ele já constrói `Content` apenas com
`transaction`, `creditCard` e `invoice`.

- [x] 5.1 Em `.../viewTransaction/ViewTransactionViewModel.kt`: remover o parâmetro `perspective` (`:23`) e a injeção de `IBaseCurrencyRepository` (`:27`), com o campo `baseCurrency` (`:32`) e a sua passagem em `Content(...)` (`:55`) — D4 e D8, mais o requisito *O detalhe de uma operação não declara perspectiva* ("MUST NOT ler a moeda base"). Remover também os imports que ficam órfãos.
- [x] 5.2 Em `feature/transactions/impl/src/commonMain/kotlin/com/neoutils/finsight/di/TransactionsModule.kt:57-65`: retirar `perspective = it.getOrNull()` e `baseCurrencyRepository = get()` da construção de `ViewTransactionViewModel`. A entrada de `ViewAdjustmentViewModel` (`:49-56`) fica como está.
- [x] 5.3 Em `feature/transactions/api/src/commonMain/kotlin/com/neoutils/finsight/feature/transactions/api/TransactionsEntry.kt:9`: remover o parâmetro `perspective: TransactionPerspective?` de `viewTransactionModal` e o import de `TransactionPerspective`. É a **quebra de api** declarada no proposal, exigida pelo requisito `presentation-mapping` §*Uma tela declara a perspectiva que tem* ("a superfície de operação não recebe perspectiva", cenário *A superfície de operação não recebe perspectiva*).
- [x] 5.4 Em `feature/transactions/impl/.../impl/TransactionsEntryImpl.kt:13-14`: ajustar a implementação à assinatura nova, construindo `ViewTransactionModal(transactionId)`.
- [x] 5.5 Em `feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/accounts/AccountsScreen.kt:374-381`: passar apenas `transactionUi.id`, removendo `uiState.selectedAccountId?.let { TransactionPerspective(it) }` e o import de `TransactionPerspective` se ficar órfão. É o **único** call site que passava o argumento; os outros seis (`InstallmentsScreen.kt:369`, `CreditCardsScreen.kt:316`, `InvoiceTransactionsScreen.kt:411`, `DashboardComponentContent.kt:313`, `ReportViewerScreen.kt:306` e a própria lista de transações) já chamavam sem ele. Esta tarefa e 5.3 fecham juntas ou o projeto não compila.

## 6. As duas modais

*Barreira de entrada:* grupos 3, 4 e 5 concluídos — o componente, os estados e as assinaturas
existem.
*Barreira de saída:* `./gradlew :app:android:assembleDebug` (ou `:app:desktop:run`) compila o
projeto inteiro de novo; o *source set* de teste ainda **não** compila, e é o grupo 7 que o
fecha. As duas tarefas escrevem arquivos distintos.

- [x] 6.1 Reescrever a composição de `feature/transactions/impl/.../viewTransaction/ViewTransactionModal.kt`. Cabeçalho: linha 1 passa a exibir a **natureza** pelas chaves de 1.1, função total sobre os cinco valores de `TransactionLabel`, eliminando o ramo `else -> direction` de `transactionColor()` (`:552-558`) e o fallback por direção do ícone (`:155-161`) — D4 e o requisito do cabeçalho; linha 2 exibe `displayTitle` e é **omitida** quando ele é nulo, em vez de recair em `"Untitled"`. Corpo: um `TransactionLegCard` por perna, na ordem do estado, com o conector de taxa entre eles quando `appliedRate` existe — a formatação do quociente permanece a que está em `:241-246` (`minFractionDigits = 2`, `maxFractionDigits = RATE_SCALE`, gramática `exchange_rates_quote`), porque a taxa muda de **lugar**, não de leitura. Apagar as linhas `Valor` (`:218-225`), `Taxa praticada` como linha (`:232-250`), `Origem` (`:259-267` — o defeito que exibia `Origem: Cartão de Crédito` num pagamento, requisito *Restam como contexto apenas os fatos que não pertencem a nenhuma perna*), `Conta origem`/`Conta destino` (`:269-304`), `Conta` (`:306-322`), `Cartão` (`:326-341`), `Fatura` (`:343-359`) e `Parcela` (`:361-376`). Restam como contexto apenas `Data` (`:254-257`) e `Recorrência` (`:378-390`), agora pelo `DetailRow` de `:core:ui` (1.2), com a cópia privada `:510-550` apagada. `testTag`: `view_transaction_amount` passa a marcar o valor do **primeiro** card (a perna primária) e o segundo card ganha tag própria — `view_transaction_secondary_amount` —, conforme a mitigação registrada em *Risks*. `DetailActions()` (`:394-433`) e `EditAndDelete` (`:435-507`) ficam **intactos** salvo o `parametersOf(transactionId, perspective)` de `:397`: portões e mensagens de bloqueio são Non-Goal.
- [x] 6.2 Reescrever a composição de `feature/transactions/impl/.../viewAdjustment/ViewAdjustmentModal.kt` sobre o mesmo card (D7): apagar a linha `Valor Ajustado` (`:131-136`), a linha `Tipo` (`:149-153`), as linhas `Conta` (`:155-171`), `Cartão` (`:173-188`) e `Fatura` (`:190-…`), que passam a viver dentro do card, e a cópia privada de `DetailRow` (`:308`), consumindo a de `:core:ui`. Restam `Data` e, se houver, a recorrência. O verbo é o de ajuste e o valor tem sinal explícito, ambos vindos do mapper. As ações e a mensagem de bloqueio ficam como estão.

## 7. Testes de unidade

*Barreira de entrada:* grupos 5 e 6 concluídos. Em particular 7.6 só é **válida** depois de 5.1
— enquanto `ViewTransactionViewModel` ainda injeta `IBaseCurrencyRepository`, remover a entrada
do inventário tornaria o teste falso; D8 diz que essa ordem é a propriedade desejável do teste,
não um obstáculo.
*Barreira de saída:* `./gradlew jvmTest` compila e passa, com a saída lida. As seis tarefas
escrevem arquivos distintos.

- [x] 7.1 Criar o teste do mapper em `core/ui/src/commonTest/kotlin/com/neoutils/finsight/ui/model/`, cobrindo os cenários do requisito *O verbo de um card deriva do razão, não da fachada* e de `money-display` §*A superfície de operação exibe módulo*: a tabela de D2 nas quatro combinações `(ASSET|LIABILITY, ±)` mais o override de `EQUITY` em ajuste de conta e de fatura; um card para gasto, receita e compra em cartão e dois para transferência e pagamento; transferência na mesma moeda produzindo dois cards com o mesmo valor; perna de categoria não produzindo card; ordem começando pela perna primária; `currencyCode` presente só quando as pernas divergem de moeda; módulo em todo card e `EXPLICIT_SIGN` apenas no ajuste, com o sinal do razão e sem inversão por tipo de conta; ausência de `onClick` quando a fachada está arquivada.
- [x] 7.2 Atualizar `feature/transactions/impl/src/commonTest/.../viewTransaction/ViewTransactionViewModelTest.kt`: o construtor perde `perspective` e `baseCurrencyRepository`, e `anAdjustmentReadsInTheDetailExactlyAsItReadsInTheList` (`:66`) passa a comparar o **card do detalhe** com o item de lista — é o teste que *Risks* §*Dois produtores da política de sinal* nomeia como cobertura da divergência inteira, e `money-display` §*O ajuste lê igual na lista e no detalhe* é o invariante que ele fixa.
- [x] 7.3 Atualizar `.../viewTransaction/ViewTransactionAppliedRateTest.kt`: os dois casos que hoje asseram o desempate por moeda base (`theFigureIsStatedByTheEndAlreadyInTheBase` `:44` e `withNeitherEndInTheBaseTheAccountStatesTheFigure` `:56`) deixam de existir no detalhe — passam a asserir que a operação cruzada produz **as duas figuras**, cada uma na moeda da sua conta e nenhuma convertida (requisito *O detalhe de uma operação exibe um card por perna monetária*, cenários de transferência e de pagamento entre moedas). Os casos de taxa (`:64`, `:84`, `:100`, `:136`) permanecem, acrescidos da asserção de que o **primeiro** card é o da perna de saída, isto é, que seta e quociente concordam (D6). `accountsAreToldApartByCurrencyOnlyWhenTwoAreOnScreen` (`:112`) migra para a marcação de moeda no card.
- [x] 7.4 Atualizar `.../viewTransaction/ViewTransactionGatesTest.kt`: apenas o necessário para acompanhar a mudança de construtor de `Content`. Os portões em si **não mudam** — é Non-Goal — e as onze asserções de `:48-125` devem continuar exatamente as mesmas.
- [x] 7.5 Atualizar `.../viewAdjustment/ViewAdjustmentViewModelTest.kt` para o estado novo, fixando o requisito *O detalhe de um ajuste usa a mesma composição*: um card com o verbo de ajuste, o nome da conta e sinal explícito; e o ajuste de fatura exibindo a fatura **dentro** do card do passivo.
- [x] 7.6 Em `app/shared/src/jvmTest/kotlin/com/neoutils/finsight/BaseCurrencyReachTest.kt`, remover a entrada `".../viewTransaction/ViewTransactionViewModel.kt"` (`:99`) e ajustar o comentário de `:85-97` que a justifica — as superfícies deixam de ser quatro. O teste assere igualdade nos dois sentidos, então a remoção é **obrigatória** e não opcional (D8, e `presentation-mapping` §*Qual das duas pontas de uma operação cruzada denomina a figura*, cenário *O detalhe não nomeia a moeda base*).

## 8. Os testTags do Maestro

*Barreira de entrada:* 6.1 concluída — as tags novas só existem depois que a modal foi
reescrita.
*Barreira de saída:* os dois flows referenciam tags que existem no app. Que as asserções
**continuem válidas** é o que o grupo 9 verifica em execução real; aqui nada é presumido verde.
As duas tarefas escrevem arquivos distintos.

- [x] 8.1 Em `.maestro/flows/ledger/lifecycle.yaml`, revisar as três asserções de `view_transaction_amount` (`:262`, `:321`, `:324`). Os três pontos abrem o detalhe de um gasto em conta monomoeda, cuja perna primária é a mesma figura que a linha `Valor` exibia, então a tag continua identificando o elemento certo — mas o texto asserido (`[-]?[$]42[.,]90`, `[-]?[$]68[.,]35`) precisa concordar com a política nova, que é **módulo** por D3.
- [x] 8.2 Em `.maestro/flows/creditcards/lifecycle.yaml`, revisar as duas asserções (`:477`, `:480`). O ponto assere o detalhe de uma compra em cartão (`[-]?[$]120[.,]00`), cuja única perna monetária é o passivo — um card só, com o verbo *lançou em* —, seguido de `assertNotVisible` sobre `view_transaction_edit`/`view_transaction_delete`, que não mudam.

## 9. Limpeza das chaves órfãs

*Barreira de entrada:* grupos 6, 7 e 8 concluídos — todo consumidor de string já está na sua
forma final, que é a única condição sob a qual "não tem leitor" é uma pergunta respondível.
*Barreira de saída:* nenhuma chave sem leitor sobra em `values/strings.xml` nem em
`values-en/strings.xml`, e o projeto continua compilando. Grupo de tarefa única: as duas
línguas mudam no mesmo par de arquivos e não podem ser divididas sem criar a assimetria que a
tarefa existe para evitar.

- [x] 9.1 Remover de `core/resources/src/commonMain/composeResources/values/strings.xml` e de `core/resources/src/commonMain/composeResources/values-en/strings.xml`, **nos dois arquivos na mesma tarefa**, as chaves que os grupos 6 a 8 deixaram sem nenhum leitor — conferindo cada uma com `grep` sobre todo o repositório antes de apagar, porque algumas do mesmo prefixo (`view_transaction_invoice_label`, `view_transaction_date_label`, `view_transaction_recurring_label`) continuam em uso. As candidatas levantadas em 1.1 são `view_transaction_amount_label`, `view_transaction_applied_rate_label`, `view_transaction_origin_label`, `view_transaction_origin_account`, `view_transaction_origin_credit_card`, `view_transaction_source_account_label`, `view_transaction_destination_account_label`, `view_transaction_account_label`, `view_transaction_card_label`, `view_transaction_installment_label`, `view_transaction_type_income`, `view_transaction_type_expense`, `view_transaction_type_adjustment`, `view_adjustment_adjusted_value_label`, `view_adjustment_type_row_label`, `view_adjustment_account_label`, `view_adjustment_card_label` e `view_adjustment_credit_card_label` — candidatas, não uma lista de execução: o `grep` decide.

## 10. Verificação final

*Barreira de entrada:* grupos 1 a 9 concluídos.
*Barreira de saída:* a suíte unitária rodou verde **com a saída lida**, a suíte Maestro rodou num
device conforme e o device foi **reportado**, o diff não excede o escopo declarado, e o
comportamento foi exercido no app real — não apenas em teste (CLAUDE.md §Behaviors: verificar é
parte da tarefa). As tarefas 10.1, 10.2 e 10.4 não editam arquivo algum.

- [x] 10.1 Rodar `./gradlew jvmTest` e **ler a saída**; reportar o resultado real e qualquer desvio de ambiente, não a expectativa. Acrescentar os `compileKotlinIosSimulatorArm64` dos módulos tocados, porque `jvmTest` não compila Kotlin/Native e uma quebra de `expect`/`actual` ou de API indisponível no alvo iOS passaria despercebida.
- [ ] 10.2 Reinstalar o APK de debug (`./gradlew :app:android:installDebug`) e rodar `maestro test .maestro` **de verdade**, depois de cumprir os sete `adb` de `.maestro/README.md` §2 (AVD `pixel_6` API 36, em inglês, teclado on-screen, sem teclado de hardware). O design diz explicitamente que a validade das asserções migradas em 8.1 e 8.2 "precisa ser verificada numa execução real, não presumida". Reportar **em qual device** a execução aconteceu; um "12/12 verde" sem isso é uma afirmação sem lastro.
- [x] 10.3 Conferir no diff que o escopo declarado foi respeitado — nenhum toque em `:core:ledger`, nos repositórios ou no caminho de escrita; `itemDisplayAmount` (`core/ui/.../ui/model/model/ItemDisplayAmount.kt`) e `toTransactionUi` (`.../TransactionUiMapper.kt`) inalterados, inclusive as suas regras de sinal; `isEditable`/`isRemovable`/`isChangeable` e as mensagens de bloqueio inalterados; nenhuma ênfase visual de perspectiva introduzida (D4 a recusa como hipótese); toda chave nova presente **nos dois** `strings.xml` e nenhuma chave removida que ainda tenha leitor.
- [ ] 10.4 Exercitar no app (`./gradlew :app:desktop:run` ou `:app:android:installDebug`) os casos que só a interface responde: transferência entre moedas — duas figuras exatas, taxa no conector, primeiro card o da perna de saída; pagamento de fatura aberto **pelo extrato daquela fatura** — cabeçalho dizendo *pagamento*, concordando com a lista, e nenhuma linha dizendo que a origem foi o cartão; transferência sem título nem categoria — cabeçalho de uma linha, sem `"Untitled"`; ajuste de saldo e ajuste de fatura — verbo de ajuste e sinal explícito, com a fatura dentro do card; conta arquivada — card sem atalho.

## 11. Ajustes após review

*Barreira de entrada:* grupos 1 a 10 concluídos e o comportamento visto por quem revisou.
*Barreira de saída:* cada ajuste tem a sua evidência no mesmo lugar que a regra original — a
derivação no mapper, a cobertura no teste do mapper —, e a suíte unitária continua verde.

Este grupo não estava no plano: ele registra o que o review do resultado pediu, e existe para
que o motivo de cada ajuste sobreviva ao commit que o aplicou.

- [x] 11.1 O card era `colorScheme.surfaceContainer`, que é a **mesma cor da folha** que o
  contém: sobre o modal ele não se lia como card algum. Tingi-lo pela direção da perna —
  vermelho para o que saiu, verde para o que entrou, âmbar para o ajuste, as mesmas três cores
  que a superfície de item já lê. Fundo com a cor em 12% de alpha; verbo, ícone de atalho e
  valor na cor cheia. Isso resolve, junto, o que o módulo sozinho não distinguia: num
  pagamento de fatura os dois cards passam a ter cores opostas.
- [x] 11.2 A cor **não** é escolhida no componente. `TransactionLegUi` ganha `tone: LegTone`
  (`OUTGOING`/`INCOMING`/`ADJUSTMENT`), resolvido pelo mapper a partir da mesma evidência do
  verbo — o sinal da perna, com o override de `EQUITY` —, porque escolhê-la no card seria um
  `when` sobre um `UiText` e uma segunda derivação da direção (`presentation-mapping`
  §*Mappers como única fronteira*, e o requisito de que o componente não derive nada). A
  consequência a registrar: uma compra em cartão lê vermelha **pela perna**, já que o razão
  creditou o passivo, e não por ser despesa — um estorno no mesmo cartão leria verde.
- [x] 11.3 Cobrir a tabela nova em `TransactionLegMapperTest`
  (`theToneRepeatsTheVerbsOwnEvidence`): pagamento produzindo `OUTGOING` e `INCOMING`, e o
  ajuste produzindo `ADJUSTMENT` — o mesmo par de casos que fixa o verbo, sobre o outro eixo.
- [x] 11.4 Acrescentar `Categoria` como linha de contexto, acima de `Data`, com a chave nova
  `view_transaction_category_label` nos dois `strings.xml`. Ela cabe entre as linhas de
  contexto pelo mesmo critério que as define: a categoria é a dimensão da perna **nominal**,
  que não carrega dinheiro e por isso não produz card — é fato da transação e de nenhuma
  perna. Omitida quando não há categoria, porque "sem categoria" é a ausência de dimensão e
  não um balde a nomear; apagada quando arquivada, a mesma regra do ícone do cabeçalho.
  Registrada a redundância que isso cria: numa transação sem título próprio a segunda linha do
  cabeçalho já é o nome da categoria, que passa a aparecer duas vezes — deliberado, porque as
  duas respondem perguntas distintas.
- [x] 11.5 Ajustar o cabeçalho: caixa do ícone de 64dp para 52dp (raio 14dp, padding interno
  13dp) e o selo de cartão de 22dp para 20dp — o cabeçalho pesava sobre os cards, que são o
  assunto da tela. O rótulo de natureza de `PAYMENT` passa de *Pagamento de Fatura* para
  *Pagamento*, e a segunda linha passa a nomear as duas formas que ordinariamente não têm
  título nem categoria: *Pagamento de Fatura* (reaproveitando `transaction_card_payment`, o
  mesmo nome que a lista dá) e *Transferência entre Contas* (chave nova
  `view_transaction_title_transfer`, nos dois arquivos). O nome tem de dizer mais que a
  natureza acima dele, e é por isso que o rótulo encurtou: *pagamento* / *pagamento de fatura*
  informa, *pagamento de fatura* duas vezes não.
- [x] 11.6 Reescrever o requisito *O cabeçalho do detalhe exibe a natureza da operação* em
  `specs/transaction-detail/spec.md`, que mandava **omitir** a segunda linha "no caso ordinário
  de uma transferência e de um pagamento" — 11.5 a contradiz e a spec do change ainda não foi
  arquivada, então quem muda é ela. A linha que a substitui distingue os dois casos: uma
  operação cuja **forma** tem nome (transferência, pagamento) é nomeada por ele, que é fato do
  razão e não literal de reserva; uma que não tem (gasto, receita, ajuste sem título e sem
  categoria) continua omitindo a linha, porque nomear uma ausência é o que o cabeçalho não faz.
  O cenário *Transferência sem título tem cabeçalho de uma linha* vira *…é nomeada pela sua
  forma*, e um cenário novo guarda a omissão onde ela continua valendo.
- [x] 11.7 Título do cabeçalho de `headlineSmall` (24sp) para `titleMedium` (16sp), com
  `maxLines = 2` e reticências. Com o ícone menor de 11.5 e os nomes de forma de 11.5 — que
  são mais longos que um título comum —, 24sp empurrava o cabeçalho para fora do lugar em vez
  de ceder espaço aos cards.
- [x] 11.8 Aplicar 11.5 e 11.7 ao cabeçalho de `ViewAdjustmentModal`, que 11.5 esqueceu:
  caixa de 64dp para 52dp (raio 14dp, ícone de 32dp para 26dp), selo de cartão de 22dp para
  20dp e o título de `headlineSmall` para `titleMedium` com `maxLines = 2`. D7 diz que o
  ajuste usa a **mesma** composição, e dois cabeçalhos com métricas diferentes são a maneira
  pela qual as duas telas voltam a divergir. O par natureza/título do ajuste já seguia o
  arranjo de 11.5 — *Ajuste* acima, *Ajuste de Saldo* / *Ajuste de Fatura* abaixo — e por isso
  não muda.
- [x] 11.9 Encurtar o nome de forma da transferência de *Transferência entre Contas* para
  *Entre Contas* (`Between Accounts`), nos dois arquivos, e ajustar o requisito do cabeçalho
  junto: as duas linhas são lidas como uma frase — *transferência / entre contas* —, então a
  segunda deve dizer o que a primeira não disse. É a mesma regra que 11.5 aplicou ao
  pagamento, agora levada até o fim: lá o rótulo encurtou, aqui foi o título.
- [x] 11.10 Centralizar o conector e desenhar a **seta sempre** que houver dois cards, não só
  em operação entre moedas: `TransactionLegConnector` volta a receber `rate: String?` e a
  modal deixa de alternar entre conector e `Spacer`. O que a seta afirma — estes são os dois
  extremos de um mesmo movimento — é verdade de toda transferência e todo pagamento, e é ela
  que dá à operação a forma de uma travessia em vez de dois cards empilhados. A taxa continua
  sendo o que só a operação cruzada tem.
- [x] 11.11 Reescrever o requisito *A taxa praticada é o conector entre os dois cards* como
  *O conector entre dois cards é a seta, e a taxa quando há uma*, que é o que 11.10 tornou
  verdade. Acrescentar à taxa o que era prática e não estava dito: ela é a relação entre as
  **duas moedas da operação** e MUST NOT ser expressa contra a moeda base — quem lê quer saber
  o que uma ponta comprou da outra, não o que uma delas vale numa terceira que não participou.
  Cenários: o da moeda única passa a exigir a seta, um novo guarda a taxa contra a base, e o
  de uma perna só passa a falar de conector em vez de taxa.
- [x] 11.12 Pôr uma seta de movimento ao lado do verbo — deitada: para frente no que saiu,
  para trás no que entrou, `Tune` no ajuste, que corrigiu uma figura em vez de mover dinheiro
  — e tirar do card o ícone `OpenInNew`. Horizontal e não vertical porque a vertical já é do
  conector, onde significa outra coisa (o percurso entre os dois cards, não a direção de um);
  as duas usam `AutoMirrored`, então viram junto com a leitura em RTL. A seta é a terceira renderização de `tone`, ao lado da cor e do
  verbo, e vem do mesmo campo: o card continua sem derivar direção. O atalho **permanece** —
  o card inteiro segue clicável quando a fachada está ativa, e o requisito nunca exigiu um
  glifo —, mas perde a sua única marca visível, que é o que se paga pela linha mais limpa.
- [x] 11.13 Valor de 16sp para 18sp e centralizado verticalmente contra o **card**, não
  contra o nome: o card vira uma `Row` de duas colunas — verbo, nome, fatura e parcela à
  esquerda; a figura à direita, alinhada ao centro. Antes ela era o segundo item da linha do
  nome e ficava presa à primeira linha, subindo para longe do bloco de que é o total assim
  que a perna carregava fatura ou parcela. Não custa altura: as duas colunas são lado a lado,
  e a mitigação registrada em *Risks* continua valendo.
- [x] 11.14 Trocar o glifo de movimento do ajuste de `Tune` para `SwapVert` — duas flechas
  contrárias, no mesmo eixo vertical das outras duas. É a leitura certa do caso: o ajuste é a
  única perna que poderia ter ido para qualquer lado, que é exatamente a direção que o verbo
  *Ajustou* retém, e as duas flechas juntas dizem isso. `Tune` diz *isto é um ajuste*, que é
  natureza e assunto do cabeçalho; esta linha fala de movimento. O `Tune` do cabeçalho das
  duas modais e o da lista continuam como estão.
- [x] 11.15 O card do cartão passa a abrir o **extrato da fatura** (`InvoiceTransactionsRoute`)
  em vez do cadastro do cartão (`CreditCardsRoute`), nas duas modais. A perna do passivo *é* a
  fatura — é a dimensão que ela carrega, e o card já a nomeia dentro de si —, então quem toca
  nela quer ver o que mais entrou naquela fatura. Antes do grupo 6 a modal tinha as duas
  linhas, `Cartão` e `Fatura`, com destinos distintos; ao fundir tudo num card só, o destino
  do cadastro foi escolhido por inércia. Ajustado o requisito junto, que dizia "a tela daquela
  fachada", com cenário próprio.
- [x] 11.16 O extrato aberto pelo card do cartão caía sempre na fatura de índice 0 —
  `InvoiceTransactionsRoute` só levava `creditCardId`, e a tela escolhe sozinha por onde
  começar. Numa compra de fatura antiga isso não é uma resposta pouco útil, é a errada: a
  fatura aberta não contém a compra que acabou de ser lida. `InvoiceTransactionsRoute` ganha
  `invoiceId: Long?` (opcional, para a lista de cartões, que não tem fatura em mente), a tela
  e o `InvoiceTransactionsViewModel` o recebem, e a seleção inicial passa a resolvê-lo sobre a
  primeira lista de faturas que chegar — uma vez só, porque depois disso a escolha é do
  usuário. Fatura que não resolve mais deixa o padrão como está em vez de falhar. Requisito e
  cenário acrescentados.
- [x] 11.17 Dar atalho à linha de categoria, abrindo `CategoriesEntry.viewCategoryModal` pelo
  `detailController` — a mesma porta que a recorrência já usava, e a `api` de categories já
  era dependência do módulo. Oferecido **também** para categoria arquivada, ao contrário do
  card de uma perna: o motivo de negá-lo lá é a tela de destino ter deixado de listar a
  fachada, e aqui o destino é um detalhe resolvido por identidade. Requisito das linhas de
  contexto atualizado (a categoria entrou na lista em 11.4 e o atalho não estava dito) e
  cenário acrescentado.
