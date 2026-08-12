## Why

Hoje uma fatura só passa a existir como **efeito colateral de outra intenção**: ao cadastrar o
cartão, ao fechar a fatura anterior, ou ao submeter uma transação num mês que ainda não tem
fatura. Quem quer registrar o passado de um cartão que já usava antes de cadastrá-lo precisa
inventar uma transação só para materializar a fatura, e só então declarar quanto ela valia pelo
ajuste de saldo — que só enxerga faturas já existentes.

Falta o gesto direto: **declarar que aquele ciclo existiu**. Essa é a única informação do
conjunto que não se deriva do cartão — a janela vem de `closingDay`/`dueDay`, mas "eu já usava
esse cartão em março" é declaração do usuário e nada a produz.

## What Changes

- Nova ação **criar fatura** na tela de faturas do cartão, abrindo uma modal dedicada que cria a
  fatura de **qualquer mês de vencimento que ainda não exista** — passado ou futuro.
- A modal navega meses pelo componente que os formulários de cartão já usam, exibe a janela
  derivada do mês selecionado e **recusa o envio** num mês que já tem fatura, em vez de escondê-lo.
- Após criar, a tela navega até a fatura nova. O ajuste de saldo já está disponível no card do
  pager, então declarar o valor é o gesto seguinte, sem encadeamento automático.
- **Os três caminhos de criação viram um.** `CreateFutureInvoiceUseCase` e
  `CreateRetroactiveInvoiceUseCase` são o mesmo código com uma constante de status diferente, e
  `CreateInvoiceUseCase` é código morto registrado no Koin cujo comportamento contradiz o dono da
  janela de compra. Os três são substituídos por uma **única operação de criação, parametrizada
  pelo mês**, que classifica o status e insere.
- `GetOrCreateInvoiceForMonthUseCase` deixa de classificar e passa a **delegar** a essa operação,
  de modo que a regra "antes do vencimento da fatura aberta é retroativa, do vencimento em diante
  é futura" tenha um dono só.

## Capabilities

### New Capabilities
- `invoice-creation`: a criação de fatura como operação única parametrizada pelo mês de
  vencimento — quem pode criar, qual status resulta, o que a recusa, e o fato de que criar é
  declarar a existência do ciclo e nunca o seu valor.

### Modified Capabilities

Nenhuma. A janela continua derivada pelo dono já especificado em `invoice-purchase-window`, e
esta mudança apenas a consome; `invoice-governs-date` não é tocada, porque a criação não edita
data de lançamento algum.

## Impact

**Código removido/substituído** (`feature/creditcards/impl`):
- `domain/usecase/CreateInvoiceUseCase.kt` — morto, e o registro em `di/UseCaseModule.kt:104`
- `domain/usecase/CreateFutureInvoiceUseCase.kt` e `domain/usecase/CreateRetroactiveInvoiceUseCase.kt`
  — absorvidos pela operação única

**Código alterado**:
- `domain/usecase/GetOrCreateInvoiceForMonthUseCaseImpl.kt` — passa a delegar a criação
- `ui/screen/invoiceTransactions/` — a ação nova e a navegação até a fatura criada
- `di/UseCaseModule.kt` e o módulo Koin da modal

**Código novo**:
- a operação de criação e a modal (`ui/modal/createInvoice/`)

**Sem impacto de dados**: nenhuma migração, nenhuma coluna nova. A fatura criada usa o mesmo
`InvoiceRepository.insert`, que já cria a `Dimension` na mesma transação.

**Consumidores indiretos preservados**: transação, parcelamento e recorrente continuam criando
faturas sob demanda pelo `GetOrCreateInvoiceForMonthUseCase`, sem mudança de comportamento.

**Não resolvido aqui**: o cartão que nasce sem nenhuma fatura porque
`AddCreditCardUseCase.kt:48` descarta o resultado da abertura. A modal não o conserta — a
classificação depende da fatura aberta —, e o conserto é outra mudança.
