---
area: transversal
severity: medium
type: data
---

# A diferença exibida é medida contra um saldo de referência capturado no primeiro quadro

## Cenário

**DADO** a folha "ajustar saldo" aberta numa data em que a conta tem R$ 140,00 — o campo abre com
"R$ 140,00" e nenhum rótulo de diferença
**QUANDO** o usuário troca a data para uma em que o saldo é R$ 100,00 e digita 75 como novo alvo
**ENTÃO** o rótulo ao lado do campo mostra **−R$ 65,00** (`75 − 140`), enquanto
`AdjustBalanceUseCase` grava **−R$ 25,00** (`75 − 100`)
**DEVERIA** mostrar a mesma diferença que grava — que é o que o KDoc de
`EditAccountBalanceUiState.Content` declara: "The value shown and the value the difference is
applied to are the same one, which is what keeps the displayed difference equal to the written
one."

Antes mesmo de digitar já se vê: assim que a data muda, o `LaunchedEffect` reescreve o campo para
"R$ 100,00" e o rótulo aparece marcando **−R$ 40,00** ao lado de um botão **desabilitado** — uma
diferença anunciada em vermelho que nunca será escrita.

## Mecânica

`EditAccountBalanceModal` entra no ramo `is Content` com `when (val state = uiState)` — `state` é
uma variável local, não a propriedade delegada — e deriva a diferença assim:

```kotlin
val adjustment by remember {
    derivedStateOf {
        newBalance - state.currentBalance
    }
}
```

`remember` **sem chaves** nunca recalcula: guarda o `derivedStateOf` da primeira composição do
ramo, e a lambda dele segue capturando aquele `state` — o objeto daquele quadro. `newBalance`
continua vivo, porque lê `balanceState.text`, que é estado de snapshot; mas `state.currentBalance`
é um `Double` num objeto congelado, e um campo comum não é dependência de snapshot. O rótulo é,
para sempre, `alvoAtual − primeiraReferência`.

E a referência é feita para se mover sob essa composição: `EditAccountBalanceViewModel` combina
`selectedAccount` com `adjustmentDate` e relê `calculateBalanceUseCase.forAccount(id, on)` a cada
troca de conta **ou** de data, emitindo um `Content` novo. Tudo o mais na folha lê o `state`
fresco — o `LaunchedEffect(state.currentBalance, currency)` reescreve o campo, o
`enabled = balanceState.text.isNotBlank() && newBalance != state.currentBalance` decide pelo valor
novo, e `Submit(newBalance)` chega a um use case que recalcula a referência do zero. Só o rótulo
ficou para trás.

**A folha de fatura tem exatamente a mesma linha**, nas mesmas posições do arquivo, e ali a
referência muda pelo gesto central da tela — escolher outro cartão ou outra fatura, que é para
isso que a folha tem os dois seletores.

**Regressão datada.** Antes de `13f1d91ec`, `uiState` era a propriedade delegada e a lambda fazia
`newBalance - uiState.currentBalance` — uma leitura de snapshot através do delegate, e portanto
viva. O `when (val state = uiState)` introduzido por aquele commit trocou a leitura por uma
captura, e o `remember` sem chaves congelou o que antes se movia.

## Evidência

- `feature/accounts/impl/.../editAccountBalance/EditAccountBalanceModal.kt` — `when (val state = uiState)`;
  o `remember { derivedStateOf { newBalance - state.currentBalance } }` sem chaves; e, sobre o
  `state` fresco, o `LaunchedEffect(state.currentBalance, currency)` e o
  `enabled = … newBalance != state.currentBalance`
- `feature/accounts/impl/.../editAccountBalance/EditAccountBalanceUiState.kt` — o KDoc de
  `Content` que declara o invariante violado
- `feature/accounts/impl/.../editAccountBalance/EditAccountBalanceViewModel.kt` —
  `currentBalance = combine(selectedAccount, adjustmentDate) { … }`, o que faz a referência se
  mover, e `submit()`, que manda `adjustmentDate.value`
- `feature/accounts/impl/.../usecase/AdjustBalanceUseCase.kt` —
  `difference = targetBalance - currentBalance`, com `currentBalance` relido em `adjustmentDate`
- **segunda ocorrência, a mesma linha**:
  `feature/creditcards/impl/.../editInvoiceBalance/EditInvoiceBalanceModal.kt` — o mesmo
  `remember { derivedStateOf { newBalance - state.currentBalance } }`; ali a referência muda ao
  trocar de cartão ou de fatura
  (`EditInvoiceBalanceViewModel.currentBalance = selectedInvoice.map { calculateInvoiceUseCase(it) }`)
- `issues/archive/2026-08-23-typing-in-the-adjustment-date-replaces-the-form-with-a-spinner.md` —
  o Desfecho diz "O modal não mudou" e credita ao `submit()` manter a diferença exibida igual à
  escrita; o lado do modal nunca foi conferido

## Consequência

O usuário decide quanto ajustar lendo um número que não é o que vai ser gravado, e a divergência
cresce com a distância entre a referência de abertura e a atual. Nos dois casos extremos ela chega
a ser autocontraditória: um rótulo vermelho de "−R$ 40,00" ao lado de um botão desabilitado, ou um
rótulo em zero — logo escondido — sobre um botão habilitado que vai gravar um lançamento.

O saldo gravado continua correto: quem decide é o use case, não a tela. O que se perde é a única
confirmação que o formulário oferece antes de escrever no razão.

*Hipótese, não verificada: na folha de fatura o `LaunchedEffect` é chaveado só em
`state.currentBalance`, sem a moeda, então trocar para um cartão de outra moeda cujo valor coincida
deixaria o símbolo antigo no campo até a primeira tecla. É o mesmo `LaunchedEffect`, e vale
conferir junto.*

## Sugestão

Dar chave ao `remember` (`remember(state.currentBalance)`) ou, melhor, derivar a diferença onde a
referência é estado observável — o `uiState` — em vez de capturá-la. As duas folhas são o mesmo
conserto. Não vinculante.
