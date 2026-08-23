# Tasks — `unify-invoice-payment`

> **Ordenação de segurança (a regra que governa a ordem dos grupos).** A troca da porta — um
> botão por superfície, alcançando o sheet unificado — é o **penúltimo** grupo, e isso é
> deliberado: até ele, tudo o que os grupos anteriores constroem é inalcançável pela interface e
> os dois caminhos atuais continuam sendo os únicos oferecidos. Abrir a porta antes de o sheet
> existir poria na tela um botão sem destino; adicionar a fatura retroativa à oferta antes de o
> domínio aceitá-la poria na tela um botão que falha.
>
> **Cada grupo termina com o projeto compilando.** A única troca não aditiva é a do grupo 6:
> `CreditCardsEntry` perde dois métodos e `CreditCardCardVariant.Dashboard` perde um callback,
> o que quebra **três chamadores** (`CreditCardsScreen`, `InvoiceTransactionsScreen`,
> `DashboardComponentContent:687`/`:696`) e o componente de `core/ui`. Todos são corrigidos
> dentro do próprio grupo 6, que não termina até `./gradlew jvmTest` passar. O compilador acusa
> cada um; nenhum fica para trás em silêncio.
>
> **Nenhuma migração, em nenhum grupo.** Nada de schema muda, `AppDatabase` não troca de versão,
> nenhum dado gravado é reinterpretado, e a fronteira de escrita do razão não recebe regra nova.

## 1. O predicado de oferta ganha dono no domínio

- [x] 1.1 Acrescentar a `Invoice` (`core/model`) os três predicados de oferta, ao lado de `isPayable`/`isClosable`: o que aceita **pagamento parcial** (`OPEN`, `RETROACTIVE`), o que aceita **quitação total** (`CLOSED`) e a união dos dois, que é o filtro do seletor de faturas. KDoc objetivo, descrevendo o estado atual — sem narrar a mudança.
- [x] 1.2 Ampliar o KDoc de `isPayable` para dizer a pergunta que ele responde — *quem pode ser marcada `PAID`* — e que `RETROACTIVE` está nele porque `CloseInvoiceUseCase:64` quita a retroativa zerada ao fechá-la. Nenhuma alteração de valor: estreitá-lo quebraria aquele caminho.
- [x] 1.3 Escrever `InvoicePaymentOfferTest` em `core/model/src/commonTest/.../domain/model/`, ao lado de `InvoiceReopenableTest`: os cinco status contra os três predicados, e a asserção explícita de que `isPayable` continua incluindo `RETROACTIVE`.
- [x] 1.4 `./gradlew jvmTest` verde.

## 2. O adiantamento ganha a guarda de status que nunca teve

- [x] 2.1 Acrescentar a `InvoiceError` o caso de recusa por status que não aceita pagamento parcial, junto dos demais do bloco de pagamento. **Sem string nova**: `toUiText` cai no genérico por declaração — só erros que uma ação do usuário alcança ganham mensagem própria, e nenhum caminho da interface oferece este.
- [x] 2.2 Acrescentar `ensure(...)` sobre o predicado de pagamento parcial em `AdvanceInvoicePaymentUseCase`, depois da resolução da fatura e **antes** da janela de datas, para que a recusa nomeie a razão real em vez de a data.
- [x] 2.3 Estender a suíte do caso de uso: fatura `CLOSED` recusada pela nova guarda; fatura `RETROACTIVE` aceita, com data dentro da janela passada do ciclo; fatura `OPEN` inalterada.
- [x] 2.4 `./gradlew jvmTest` verde — nenhum teste existente muda de resultado, porque nenhuma tela chamava o caso de uso fora de `OPEN`.

## 3. A forma da escrita ganha um dono

- [x] 3.1 Criar em `feature/creditcards/impl/domain/usecase/` a peça única que grava um pagamento de fatura: recebe a fatura, a conta pagadora, o que **sai da conta**, o que **liquida a fatura** e a data; monta as duas pernas (saída sem dimensão, entrada na `LIABILITY` do cartão com a dimensão da fatura) e colhe a taxa a partir das duas pontas. O KDoc passa a ser o dono da regra — dimensão só na perna do cartão, nenhuma taxa como parâmetro, taxa escrita no acervo e nunca na transação.
- [x] 3.2 Fazer `PayInvoicePaymentUseCase` e `AdvanceInvoicePaymentUseCase` delegarem a ela, removendo os dois blocos idênticos e os comentários copiados. As guardas de cada um permanecem onde estão: é o que os mantém distintos.
- [x] 3.3 Registrar a peça em `UseCaseModule` e injetá-la nos dois casos de uso.
- [x] 3.4 `./gradlew jvmTest` verde **sem alterar** `PayInvoicePaymentUseCaseTest` nem a suíte do adiantamento: o comportamento é idêntico, e a suíte inalterada é a prova disso.

## 4. O fato e o verbo sobem para o modelo de UI

- [x] 4.1 Acrescentar a `InvoiceUi` (`core/ui`) a oferta de pagamento como fato plano: se há pagamento a oferecer, o verbo que o nomeia (`StringResource`, como `statusLabel` já faz) e se o modo é quitação total. Os campos `isOpen`/`isClosed`/`isRetroactive` permanecem — outras decisões da tela os consomem.
- [x] 4.2 Resolver esses campos em `InvoiceUiMapperImpl` a partir dos predicados do grupo 1, e não de uma enumeração de status própria.
- [x] 4.3 Acrescentar aos **dois** arquivos de strings (`values/` e `values-en/`) o que falta para os verbos derivados e para o texto que o sheet exibe sobre si mesmo em cada modo. Reaproveitar as chaves existentes onde o texto não muda; as duas afirmações que deixam de ser verdadeiras para todos os estados — `pay_invoice_message` ("O pagamento será do valor total da fatura") e `advance_payment_description` ("Pague parte da fatura antes do fechamento") — passam a ser escolhidas pelo modo.
- [x] 4.4 `./gradlew jvmTest` verde. Nada consome os campos novos ainda; o grupo é aditivo de propósito.

## 5. O sheet unificado nasce, ainda inalcançável

- [x] 5.1 Criar o pacote `ui/modal/invoicePayment/` em `feature/creditcards/impl` com `Action`, `UiState`, `ViewModel` e `Modal`, parametrizado por um `invoiceId` **opcional** (a pré-seleção).
- [x] 5.2 No ViewModel: observar os cartões; para o cartão selecionado, listar as faturas filtradas pelo predicado de união do grupo 1; ler o devido e a moeda **da fatura selecionada** por `CalculateInvoiceUseCase` (nada recebido pronto); derivar o modo, a janela de datas e `isCrossCurrency` das duas pontas correntes; e manter a sugestão do acervo como os dois ViewModels atuais a mantêm.
- [x] 5.3 Aplicar a disciplina de ordem que `AddTransactionViewModel:269` documenta: trocar o cartão **limpa a fatura antes** de assumir o cartão novo, para que o par (cartão novo, fatura antiga) não seja observável. Trocar cartão ou fatura limpa o valor e o valor de contrapartida.
- [x] 5.4 No Modal: seletor de cartão (`CreditCardSelector`), seletor de fatura (`InvoiceSelector`), seletor de conta (`AccountSelector`), o campo de valor único (entrada com teto em `OPEN`/`RETROACTIVE`, afirmação não editável em `CLOSED`), `CounterpartAmountField` quando as moedas diferem, e o campo de data limitado pela janela do modo — com o `DatePickerModal` recebendo os mesmos limites. Publicar os `testTag` do sheet e conferir `Modifier.exposeTestTags()` na raiz da composição.
- [x] 5.5 Escrever a regra de habilitação como função de topo `internal`, cobrindo os dois modos numa só — no molde de `isValidInvoicePayment`/`isValidAdvancePayment`, que ela substitui.
- [x] 5.6 Escrever a suíte da habilitação absorvendo os casos de `PayInvoiceSubmitEnablementTest` e `AdvancePaymentSubmitEnablementTest`, mais os novos: fatura fechada não confirma valor diferente do devido; fatura retroativa confirma valor parcial com data na janela passada; fatura sem dívida não confirma em nenhum modo.
- [x] 5.7 Registrar o ViewModel em `CreditCardsModule` (`viewModel {}`), recebendo o `invoiceId` opcional por `parametersOf`. Os dois ViewModels antigos continuam registrados neste grupo.
- [x] 5.8 `./gradlew jvmTest` verde.

## 6. A porta troca — um botão por superfície (BREAKING)

- [x] 6.1 Substituir em `CreditCardsEntry` (`feature/creditcards/api`) `payInvoiceModal(invoice, currentBillAmount)` e `advancePaymentModal(invoice, currentBillAmount)` por um método único parametrizado por identidade, com a fatura opcional; `DisplayAmount` sai da assinatura. Atualizar `CreditCardsEntryImpl`.
- [x] 6.2 Em `CreditCardCard` (`core/ui`): trocar `onPayInvoice`/`onAdvancePayment` de `CreditCardCardVariant.Dashboard` por um callback só, remover a derivação `canPayInvoice`/`canAdvanceInvoice` (`:334-335`) e renderizar **um** botão, cujo verbo vem do fato do grupo 4. É o que põe o componente em conformidade com `presentation-mapping` e com o próprio KDoc de `InvoiceUi`.
- [x] 6.3 `CreditCardsScreen`: fundir os botões de `:559` e `:626` num só, lendo o fato em vez de `invoiceUi.isOpen`/`isClosed`, e abrir o sheet unificado com a fatura em vista pré-selecionada. Preservar o `testTag` de um dos dois ou declarar o novo, e refletir a escolha no grupo 7.
- [x] 6.4 `InvoiceTransactionsScreen`: o mesmo em `:628` e `:718`; `InvoiceSummary` passa a carregar a oferta de pagamento resolvida no ViewModel, junto de `isClosable`/`canReopen`, em vez de a tela ler `summary.status`.
- [x] 6.5 `DashboardComponentContent`: um callback só em lugar de `:687`/`:696`, chamando o método novo do entry point.
- [x] 6.6 Remover os pacotes `ui/modal/payInvoice/` e `ui/modal/advancePayment/` inteiros, com os dois ViewModels, e desregistrá-los de `CreditCardsModule`. Remover `PayInvoiceSubmitEnablementTest` e `AdvancePaymentSubmitEnablementTest`, cujos casos já vivem em 5.6.
- [x] 6.7 Remover das **duas** listas de strings as chaves que ficaram sem consumidor, e apenas essas — conferindo por busca antes de apagar cada uma.
- [x] 6.8 `./gradlew jvmTest` e `./gradlew :app:android:assembleDebug` verdes.

## 7. Os fluxos Maestro alcançam o sheet novo

- [x] 7.1 Reescrever `.maestro/flows/creditcards/lifecycle.yaml` para os ids do sheet unificado, no lugar de `credit_card_advance_payment`, `advance_payment_*` e `pay_invoice_*`.
- [x] 7.2 Reescrever a parte correspondente de `.maestro/flows/report/lifecycle.yaml`, que alcança o adiantamento para produzir o dado do relatório.
- [x] 7.3 Acrescentar ao fluxo de cartões a jornada que não existia: pagar parcialmente uma fatura retroativa e conferir que o devido cai e o status permanece.
- [x] 7.4 Reinstalar o debug (`./gradlew :app:android:installDebug`) e rodar a suíte no AVD exigido pelo `.maestro/README.md` §2 — as sete verificações `adb` antes de rodar —, reportando em qual dispositivo a execução aconteceu.

## 8. Verificação final

- [x] 8.1 `./gradlew jvmTest` verde.
- [x] 8.2 `./gradlew :app:android:assembleDebug` verde.
- [x] 8.3 Exercitar à mão o caminho que a mudança abre: fatura `RETROACTIVE` com saldo → pagamento parcial → o devido cai e o status permanece → fechar → `PAID` pelo fechamento (`CloseInvoiceUseCase:64`), sem um segundo pagamento.
- [x] 8.4 Conferir que a linha de adiantamentos do resumo da fatura (`InvoiceTransactionsUiState.InvoiceSummary.advancePayment`) contabiliza o pagamento de uma retroativa sem alteração de código — ela deriva dos fluxos por dimensão do razão, não do status.
- [x] 8.5 `openspec validate unify-invoice-payment --strict` verde.
