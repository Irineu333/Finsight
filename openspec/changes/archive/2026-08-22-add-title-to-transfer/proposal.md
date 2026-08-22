## Why

Uma transferência entre contas acontece por razões diferentes — separar uma reserva, acertar
uma dívida com alguém, remanejar dinheiro para pagar uma fatura —, e hoje o app não tem onde
guardar nenhuma delas. O formulário não oferece título, os dois use cases gravam `null`
(`TransferBetweenAccountsUseCase.kt:62`), e duas transferências de mesmo valor entre as mesmas
contas ficam indistinguíveis na lista.

O campo já existe no razão (`Transaction.title`, `TransactionIntent.title`): o que falta é o
formulário oferecê-lo e a lista mostrá-lo. E mostrá-lo exige desfazer uma inversão: o card da
lista deixa o rótulo da forma **vencer** o título (`TransactionCard.kt:163-169`), enquanto o
detalhe deixa o título vencer o rótulo (`ViewTransactionModal.kt:216-223`). Sem desfazer isso,
o título seria digitado e nunca exibido.

## What Changes

- O formulário de transferência ganha um campo de **título opcional**, nos seus dois modos
  (registrar e corrigir), e os dois use cases passam a gravá-lo.
- A precedência de nomeação — **título > categoria > forma** — passa a valer em toda superfície
  que nomeia uma operação, e não só no detalhe. O card da lista e o documento exportado são
  invertidos para segui-la.
- O literal de reserva `"Untitled"` é **removido**. Ele é texto em inglês num app pt/en, e é
  inalcançável hoje apenas porque o card invertido intercepta antes de chegar nele — inverter a
  cadeia sem fornecer o terceiro elo o tornaria visível na tela mais usada do app.
- `displayTitleOf` é aposentada em favor de `displayTitleOrNull`, que já existe e já é a dona
  dos dois primeiros elos. Cada superfície passa a declarar o seu terceiro elo.
- Onde um invariante do domínio garante que o terceiro elo é inalcançável, ele passa a ser
  **afirmado e a falhar alto**, em vez de mascarado por um literal.
- **Fora de escopo, deliberadamente:** o ajuste de saldo continua sem campo de título. O motivo
  está declarado em `design.md`.

## Capabilities

### New Capabilities
- `operation-naming`: como uma operação é nomeada em qualquer superfície — a precedência
  título > categoria > forma, o dono único dos dois primeiros elos, o terceiro elo como
  responsabilidade da superfície que o exibe, e a recusa de nomear uma ausência com um
  literal de reserva.

### Modified Capabilities
- `transfer-editing`: o formulário passa a oferecer título, de modo que o requisito "o
  formulário não apaga o que não exibe" deixa de ter o título como exemplo — um título
  apagado passa a ser intenção declarada do usuário, e não perda silenciosa.

## Impact

**Formulário e escrita da transferência**
- `feature/accounts/impl/.../transferBetweenAccounts/` — `TransferBetweenAccountsModal`,
  `TransferBetweenAccountsAction`, `TransferBetweenAccountsViewModel`
- `feature/accounts/impl/.../usecase/TransferBetweenAccountsUseCase.kt:62`
- `feature/accounts/impl/.../usecase/UpdateTransferUseCase.kt:58-63` — o comentário que
  justifica a preservação sai junto com a razão que o motivava

**Nomeação**
- `core/model/.../extension/DisplayTitle.kt` — `displayTitleOf` e o literal removidos
- `core/ui/.../component/TransactionCard.kt:157-171` — cadeia invertida
- `core/model/.../domain/model/Recurring.kt:17` — invariante afirmado, e
  `form/RecurringForm.kt:41` alinhado a ele (`isNotBlank`, título aparado)
- `feature/creditcards/impl/.../mapper/InstallmentUiMapper.kt:58` — terceiro elo próprio
- `feature/report/impl/.../viewer/ReportExportLayout.kt:224-232` — o documento exportado, quinta
  superfície que nomeia uma operação, com a mesma cadeia invertida do card

**Strings**
- `core/resources/.../values/strings.xml` e `values-en/strings.xml` — a chave do campo novo,
  a forma da parcela, e a forma do gasto e da receita (o terceiro elo que faltava ao card)

**Testes**
- `app/shared/.../ComposeAppCommonTest.kt:15,52` — dois testes que fixam um estado que o
  dono da regra impede, e que passam a provar que a violação falha
- `feature/accounts/impl/.../TransferBetweenAccountsViewModelTest.kt` e
  `UpdateTransferUseCaseTest.kt` — o teste que fixava a preservação do título passa a provar
  que a correção grava o que o formulário diz
- `app/shared/.../EditTransferEndToEndTest.kt`
- `core/ui/.../TransactionUiNamingTest.kt` e
  `feature/creditcards/impl/.../InstallmentUiNamingTest.kt` — a precedência e a ausência sem
  literal de reserva
- `.maestro/flows/accounts/lifecycle.yaml` — o campo novo é opcional e alcançado por `id:`
