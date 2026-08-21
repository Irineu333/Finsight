---
area: accounts
severity: high
type: ux
---

# Digitar na data do ajuste de saldo troca o formulário por um spinner que não volta

## Cenário

**DADO** a folha "ajustar saldo" aberta, com `21/08/2026` no campo de data
**QUANDO** o usuário toca no campo e apaga um caractere, deixando `21/08/202`
**ENTÃO** o `uiState` vira `Loading`, o ramo `Content` inteiro sai da composição — campo
de data, campo de saldo e botão — e a folha passa a mostrar só o título e um
`CircularProgressIndicator`, para sempre
**DEVERIA** manter o formulário na tela e apenas deixar o valor de referência onde estava
enquanto a data não parseia

## Mecânica

O ViewModel guarda a data como **texto**; `adjustmentDate` a mapeia com
`toLocalDateOrNull()`, que devolve `null` em qualquer texto que não seja `dd/MM/yyyy`
completo. `currentBalance` retorna `null` nesse caso, e `uiState` traduz `balance == null`
em `Loading`.

O ponto de não-retorno: o campo de data e o `LaunchedEffect` que reporta o que se digita
vivem **dentro** do ramo `is Content`. O estado inválido destrói o único produtor capaz de
sair dele.

E não é um estado de beira: `dayMonthYear` é estrito, então todo estado intermediário de
digitação — de um a sete dígitos — é inválido. Editar a data digitando não é arriscado, é
impossível.

O KDoc do próprio campo afirma o comportamento pretendido — *"A date still being typed
parses to nothing and the reference value stays where it was"* — e é exatamente o que o
código não faz. A folha irmã faz: em `TransferBetweenAccountsModal` o texto inválido
simplesmente não propaga.

## Evidência

- `feature/accounts/impl/.../editAccountBalance/EditAccountBalanceViewModel.kt` — a cadeia
  `date` → `adjustmentDate` → `currentBalance` → `uiState`, e o KDoc de `date` que promete
  o contrário
- `feature/accounts/impl/.../editAccountBalance/EditAccountBalanceModal.kt` — o
  `when (val state = uiState)`: o `rememberTextFieldState` e o `LaunchedEffect` da data
  estão sob `is Content`
- `core/common/.../util/DateFormats.kt` — `dayMonthYear`, `dd/MM/yyyy` estrito
- `core/common/.../util/DateInputTransformation.kt` — só formata dígitos, não valida nem
  completa
- `feature/accounts/impl/.../transferBetweenAccounts/TransferBetweenAccountsModal.kt` — o
  contraste: `runCatching { dayMonthYear.parse(text) }.getOrNull()?.let { ... }`
- `EditAccountBalanceViewModelTest` — só exercita datas completas e válidas

## Consequência

Ajustar saldo por data digitada é inatingível, e quem tenta fica com uma folha que parece
travada carregando. O único jeito de sair é fechar e reabrir, e nada em tela diz isso.

## Sugestão

Guardar no ViewModel o último `LocalDate` válido, como a transferência faz, e deixar o
texto ser estado da UI. `Loading` deveria significar "ainda não li o saldo", não "o que
você digitou não parseia". Não vinculante.
