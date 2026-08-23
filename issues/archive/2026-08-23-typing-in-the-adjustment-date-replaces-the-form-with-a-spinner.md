---
area: accounts
severity: high
type: ux
version: 1.10.0
verdict: fixed
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

## Desfecho

**Causa real** — a data era texto no ViewModel e o valor de referência era lido dela, então
"o que se digitou ainda não é uma data" e "ainda não li o saldo" eram o mesmo estado. A
sugestão original acertou o remédio; o que ela não viu é que `submit()` lia o texto uma
segunda vez e desistia em silêncio quando ele não parseava — o mesmo defeito, num caminho
que o cenário não visita.

**Mudança** — `EditAccountBalanceViewModel` passa a ter dois campos com donos distintos:
`date`, o texto que o campo edita, e `adjustmentDate`, a última data que esse texto foi —
que só se move quando `ChangeDate` parseia. `currentBalance` combina com `adjustmentDate`,
então não volta a ser `null` por causa do que se digita, e `Loading` recupera o único
sentido que lhe resta, dito no KDoc: a conta ainda não foi lida. `submit()` escreve em
`adjustmentDate.value` — a data em que o saldo de referência foi lido — em vez de reparsear
o texto, o que mantém a diferença exibida igual à escrita também aqui.

O modal não mudou: com o ramo `Content` de pé, o campo de data e o `LaunchedEffect` que o
reporta deixam de sair da composição.

**Prova** — dois testes em `EditAccountBalanceViewModelTest`: apagar `11/08/2026` caractere
a caractere mantém o estado em `Content` com o saldo de referência em 140, e digitar
`28/02/2026` o move para 100; submeter com o texto em `28/02/202` grava 75 em 28/02/2026,
em vez de nada. Vermelhos antes — `ClassCastException` de `Loading` para `Content`, e
nenhum lançamento no razão —, verdes depois; verificado restaurando o ViewModel antigo
contra a versão final dos testes. `./gradlew jvmTest` verde: 1416 testes, 0 falhas somando
os relatórios de todos os módulos. `:feature:accounts:impl:compileDebugKotlinAndroid`
compilando.

**Commit** — `Fix(Accounts): keep the balance form on screen while the date is typed`
