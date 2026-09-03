---
area: mcp
severity: low
type: ux
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
