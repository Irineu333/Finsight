## Why

Pagar uma fatura e adiantá-la são a **mesma operação no razão**. `AdvanceInvoicePaymentUseCase` e `PayInvoicePaymentUseCase` montam a mesma `TransactionIntent` — perna `EXPENSE` sem dimensão na conta pagadora, perna `INCOME` na `LIABILITY` do cartão carregando a dimensão da fatura —, colhem a taxa do mesmo jeito e trazem o mesmo comentário copiado palavra por palavra. O que difere entre as duas não é a operação: é **onde a fatura está no ciclo**. Duas telas, dois casos de uso e duas suítes existem para expressar uma diferença que pertence a um predicado.

O custo aparece em dois lugares. Primeiro, a regra de quem pode pagar não tem dono: quatro superfícies repetem o par `isOpen`/`isClosed` por conta própria — `CreditCardCard.kt:334-335` (no `core/ui`, dentro do componente, não num mapper), `CreditCardsScreen.kt:559`/`:626` e `InvoiceTransactionsScreen.kt:628`/`:718` — e **nenhuma das quatro conhece `RETROACTIVE`**. Uma fatura retroativa com saldo é dívida que o app soma na previsão de liquidação do mês (`month-settlement-forecast`), exibe no cartão, e não oferece nenhum caminho para pagar. O domínio concorda que ela é devedora (`Invoice.isPayable`, `Invoice.kt:37`); só a interface não oferece.

Segundo, o pagamento só existe onde a fatura já está na tela. Cada superfície precisa resolver a fatura e o seu devido antes de abrir o sheet (`payInvoiceModal(invoice, currentBillAmount)`), de modo que pagar a fatura de outro cartão — ou a de outro mês do mesmo cartão — exige navegar até ela primeiro. A operação depende do contexto em que foi aberta, em vez de nomear o que paga.

## What Changes

- **Um único sheet de pagamento de fatura**, que nomeia a fatura em vez de herdá-la do contexto: seletor de **cartão**, seletor de **fatura**, conta pagadora, valor, valor de contrapartida quando as moedas diferem, e data. Os dois sheets atuais (`PayInvoiceModal`, `AdvancePaymentModal`) deixam de existir como caminhos separados.

- **O estado da fatura decide o modo, e é a única coisa que o decide.** Fatura `OPEN` ou `RETROACTIVE` aceita **pagamento parcial**: o valor é do usuário, com teto no devido, e a fatura permanece no status em que estava. Fatura `CLOSED` aceita **apenas quitação total**: o valor é o devido, exibido e não editável, e a quitação marca `PAID`. Um valor parcial numa fatura fechada continua inexprimível — não por omissão da tela, mas por declaração.

- **A fatura `RETROACTIVE` passa a receber pagamento**, pelo caminho parcial. É a lacuna que a unificação fecha: um ciclo passado sendo regularizado tem a dívida abatida como qualquer outro, e tudo nele é datado no passado, dentro da janela do próprio ciclo. `PAID` continua alcançável para ela apenas por `CloseInvoiceUseCase` — pagar até zerar e então fechar cai na quitação por fechamento que já existe (`CloseInvoiceUseCase:64`). O invariante **`PAID` sempre precedido de `CLOSED`** passa a valer sem exceção.

- **Um único botão de entrada por superfície, com o rótulo derivado do estado.** "Antecipar" só existe antes do fechamento; depois dele, e no passado, é sempre "pagar" — `OPEN` → antecipar, `CLOSED` e `RETROACTIVE` → pagar. Os três pares de botões de hoje (cartão do dashboard, tela de cartões, tela de lançamentos da fatura) viram um botão cada.

- **A regra ganha dono único no domínio.** `Invoice` passa a declarar quem aceita pagamento parcial e quem aceita quitação total, e as quatro superfícies consomem o predicado em vez de reenumerar status. O filtro do seletor de faturas é o mesmo predicado, de modo que `FUTURE` e `PAID` ficam de fora por construção — hoje `FUTURE` é excluída por acidente, pela janela de datas do adiantamento não admitir data alguma.

- **`AdvanceInvoicePaymentUseCase` ganha a guarda de status que nunca teve.** Hoje ele não verifica status nenhum: só a tela impede que uma fatura fechada receba pagamento parcial. Com o sheet escolhendo o caminho pelo estado, a regra passaria a morar exclusivamente na UI — a guarda no caso de uso é o que impede isso.

- **A escrita compartilhada ganha um dono.** As duas pernas e a colheita de taxa — hoje duplicadas linha a linha nos dois casos de uso — passam a ter um lugar só. Os dois casos de uso **permanecem distintos**: as guardas são genuinamente diferentes, e fundi-los sob um parâmetro booleano esconderia a decisão de negócio atrás de uma bandeira.

- **BREAKING** — `CreditCardsEntry` perde `payInvoiceModal(invoice, currentBillAmount)` e `advancePaymentModal(invoice, currentBillAmount)`, substituídos por um método único parametrizado por identidade, com a fatura opcional. O `DisplayAmount` sai da assinatura: o devido passa a ser lido dentro do sheet, reativo à fatura selecionada, porque uma fatura que o usuário troca não pode ter o seu valor congelado no momento da abertura.

- **Permanece inalterado, por decisão:** a guarda `paidAt <= invoice.dueDate` (`PayInvoiceUseCase:50`) fica de pé — para uma fatura retroativa a data no passado é a correta, e o caso da fatura fechada que venceu continua sendo o issue `an-overdue-invoice-can-only-be-paid-on-the-day-it-fell-due`, fora deste escopo. `Invoice.isPayable` continua incluindo `RETROACTIVE`, porque `CloseInvoiceUseCase:64` depende disso para quitar a retroativa zerada ao fechá-la. `PayInvoicePaymentUseCase` continua exigindo `CLOSED`.

- **Fora de escopo, declarado:** pagamento parcial de fatura fechada; a exclusão de `RETROACTIVE` em `observeUnpaidInvoices` (issue `retroactive-invoice-debt-is-invisible-to-the-available-limit`, que permanece aberto); e alterar quem pode fechar, reabrir ou ajustar uma fatura.

## Capabilities

### New Capabilities
- `invoice-settlement`: o pagamento de uma fatura como operação única que nomeia a sua fatura — o modo (parcial ou quitação total) derivado do estado e de mais nada, a fatura retroativa como devedora que se paga, o `PAID` sempre precedido de `CLOSED`, o predicado com dono único que as superfícies consomem, o rótulo derivado do estado, e o devido lido reativamente em vez de recebido pronto.

### Modified Capabilities
- `invoice-governs-date`: a hierarquia **cartão governa fatura, fatura governa data** deixa de valer apenas nos formulários de lançamento em cartão e passa a valer em todo formulário que escolhe uma fatura, o de pagamento incluído. A janela em que a data é recolocada passa a ser resolvida pelo formulário — a de compra (`invoice-purchase-window`) para lançamento, a de liquidação para pagamento —, e a assimetria (editar a data não mexe na fatura) vale inalterada nos dois.

## Impact

- **`core/model`** — `Invoice` ganha os predicados de oferta (quem aceita parcial, quem aceita quitação, e a união que filtra o seletor), ao lado de `isPayable`/`isClosable`, que permanecem como estão.
- **`feature/creditcards/api`** — `CreditCardsEntry` troca os dois métodos de pagamento por um, parametrizado por identidade; `DisplayAmount` deixa de aparecer na assinatura.
- **`feature/creditcards/impl`** — os dois modais e os dois ViewModels convergem num só, que lê o devido da fatura selecionada e escolhe o caso de uso pelo predicado; `AdvanceInvoicePaymentUseCase` ganha a guarda de status; a escrita comum é extraída; `CreditCardsModule`/`UseCaseModule` acompanham; `InvoiceUiMapperImpl` passa a resolver os fatos derivados do pagamento.
- **`core/ui`** — `InvoiceUi` ganha o que hoje é decidido dentro do componente; `CreditCardCard` troca `onPayInvoice`/`onAdvancePayment` por um callback só e deixa de derivar `canPayInvoice`/`canAdvanceInvoice` por conta própria (`CreditCardCard.kt:334-335`), em conformidade com `presentation-mapping`.
- **`feature/dashboard/impl`** — o terceiro chamador dos dois modais (`DashboardComponentContent.kt:687`/`:696`) acompanha a mudança do entry point e do variant do card.
- **`core/resources`** — os títulos, mensagens e rótulos derivados do estado, nos **dois** arquivos de strings (pt e en); as afirmações hoje fixas — "O pagamento será do valor total da fatura" e "Pague parte da fatura antes do fechamento" — deixam de ser verdadeiras para todos os estados e passam a ser escolhidas pelo modo.
- **`core/analytics`** — os dois eventos (`PayInvoice`, `AdvanceInvoicePayment`) permanecem e passam a ser escolhidos pelo mesmo predicado que escolhe o caso de uso.
- **`.maestro`** — `flows/creditcards/lifecycle.yaml` e `flows/report/lifecycle.yaml` alcançam o pagamento pelos ids `credit_card_advance_payment`, `advance_payment_*` e `pay_invoice_*`; a unificação os reescreve.
- **Testes** — `AdvancePaymentSubmitEnablementTest` e `PayInvoiceSubmitEnablementTest` convergem na regra única de habilitação; `PayInvoicePaymentUseCaseTest` e um novo caso cobrem a guarda de status do adiantamento; a fatura retroativa recebendo pagamento parcial ganha cobertura própria.
