---
area: mcp
severity: low
type: ux
verdict: fixed
---

# Toda transferência registrada pelo agente nasce sem título, e o parâmetro não existe no schema

## Cenário

**DADO** duas contas do usuário
**QUANDO** um agente registra uma transferência entre elas por `transfer`, dizendo ou não por que
o dinheiro se moveu
**ENTÃO** o lançamento é gravado sem título, e as telas que o listam o mostram sem nome
**DEVERIA** aceitar o título como parâmetro opcional, do mesmo jeito que o formulário da tela o
oferece e que o use case já o recebe

## Mecânica

O título é parâmetro do domínio e chega até lá pela tela: `TransferBetweenAccountsUseCase.invoke()`
declara `title: String? = null`, documentado como *"why the money moved, as the user stated it"*, e
`TransferBetweenAccountsViewModel` normaliza o campo do formulário para `null` quando vazio antes
de repassá-lo.

`TransferTool` é o único consumidor que não o menciona: `inputSchema` declara `from_account_id`,
`to_account_id`, `amount`, `destination_amount` e `date`, e a chamada a `transferBetweenAccounts`
omite o argumento, deixando o default. Não há recusa nem normalização envolvida — o parâmetro
simplesmente não atravessa a superfície.

A ferramenta não foi escrita incompleta: `8f087f3aa` (16/08) a criou com os cinco parâmetros que a
operação tinha então, e ela nunca mais foi editada. O título entrou seis dias depois, em
`c48076142` (22/08), que tocou trinta e um arquivos entre domínio, tela, `core/ui` e relatório —
e nenhum em `feature/mcp`. É o caso mais simples de
`capabilities-the-app-offers-and-the-agent-cannot-reach.md`: nada relaciona uma capacidade nova à
superfície, então nada ficou vermelho.

## Evidência

- `TransferBetweenAccountsUseCase.invoke()` — `title: String? = null`, com KDoc próprio
- `TransferBetweenAccountsAction.Submit.title` — o campo na tela
- `TransferBetweenAccountsViewModel.submit()` — `title.trim().takeIf { it.isNotEmpty() }`
- `TransferTool.inputSchema` — cinco parâmetros, nenhum é o título
- `TransferTool.call()` — chama `transferBetweenAccounts(...)` sem `title`
- `8f087f3aa` (16/08) — cria `AccountOperationTools.kt`; é o único commit que o arquivo tem
- `c48076142` (22/08) — acrescenta o título ao app, sem tocar `feature/mcp`

## Consequência

Um agente instruído a registrar "transferência para a poupança da viagem" grava um lançamento que
a pessoa encontra depois sem nenhuma indicação do motivo, e a informação que ela deu na frase se
perde na chamada. O contorno é abrir o app e editar pela tela, que é justamente o caminho que o
agente não tem: `UpdateTransactionTool` recusa transferências.

## Sugestão

Acrescentar `title` ao `inputSchema` e repassá-lo. A normalização de vazio para ausente já é do
ViewModel e não pertence à ferramenta: uma string vazia vinda do protocolo é a mesma decisão que
`update_recurring` já toma para apagar um título.

Não vinculante — quem corrige decide.

## Desfecho

**Causa real** — a descrita: `TransferBetweenAccountsUseCase.invoke()` declara
`title: String? = null` desde `c48076142`, documentado como *"why the money moved, as the user
stated it"* (`feature/accounts/api/.../TransferBetweenAccountsUseCase.kt:28-37`), e `TransferTool`
era o único chamador que não o mencionava — nem no `inputSchema`, nem na chamada. Nada recusava e
nada avisava; o parâmetro simplesmente não atravessava a superfície.

**Mudança** — `AccountOperationTools.kt`, duas linhas de efeito: um `"title" to text(…)` no
`inputSchema` de `transfer` (opcional, como na tela) e `title = arguments.string("title")` na
chamada ao use case.

Nenhuma normalização foi escrita na ferramenta, e isso é a decisão e não um esquecimento:
`JsonObject?.string` já responde `null` a uma string em branco
(`ToolSupport.kt:105-106`, `takeIf { it.isNotBlank() }`), que é o mesmo lugar de onde
`update_recurring` tira o "" que apaga um título (`WriteSupport.kt:197`, `stringOr`). A diferença
entre as duas é só a pergunta que cada uma faz: um `update` precisa distinguir *não falei* de
*este não tem*, e por isso lê `names`; uma criação não tem título anterior para tirar, então
ausente e vazio querem dizer a mesma coisa. O `trim().takeIf` do ViewModel segue sendo do
ViewModel.

**Prova** — teste novo em `OperationsFamilyOverTheProtocolTest`, `a transfer is titled by what the
call stated, and by nothing when it stated nothing`: duas transferências pelo protocolo, uma com
`"title":"Reserva da viagem"` e outra com `"title":"   "`, e o que se assere é a linha no ledger
real — a primeira com o título, a segunda com `null`. Vermelho antes da correção
(`expected:<Reserva da viagem> but was:<null>`), verde depois; as outras 19 do arquivo seguem
verdes. Rodado com
`./gradlew :feature:mcp:impl:jvmTest --tests "com.neoutils.finsight.mcp.OperationsFamilyOverTheProtocolTest"`.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
