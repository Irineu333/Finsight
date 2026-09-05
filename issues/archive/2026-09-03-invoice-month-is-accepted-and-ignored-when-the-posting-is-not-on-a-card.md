---
area: mcp
severity: medium
type: data
verdict: fixed
---

# `invoice_month` é aceito e ignorado quando o lançamento não está num cartão

## Cenário

**DADO** um lançamento numa conta
**QUANDO** o agente chama `update_transaction(id, invoice_month: "2026-04")` — ou move o lançamento
para uma conta e nomeia o mês de fatura na mesma chamada
**ENTÃO** a resposta é *"Edited. Everything the call did not name kept the value it had."*, e o mês
de fatura, que a chamada nomeou, não foi a lugar nenhum
**DEVERIA** recusar o argumento, dizendo que ele só existe para um lançamento em cartão

## Mecânica

`invoiceDueMonth` atravessa `TransactionForm.from` intacto — não há normalização dele ali. Quem o
ignora é `BuildTransactionUseCaseImpl`: no ramo da conta o `return@either` monta a `TransactionIntent`
com título, data, uma perna e a contra-perna, e nunca lê `form.invoiceDueMonth`. O campo é lido só
depois, no ramo do cartão.

Por isso a recusa que a tool instalou para os argumentos que o **formulário** descarta não alcança
este: o descarte não acontece no formulário. E a nota de sucesso, escrita para prometer que nada foi
perdido, é exatamente a frase errada para o caso.

## Evidência

- `TransactionWriteTools` (`update_transaction`) — `invoiceDueMonth = arguments.monthOrNull
  ("invoice_month") ?: …`, montado no form sem checar o destino
- `TransactionWriteTools` — `note = "Edited. Everything the call did not name kept the value it
  had."`, a resposta que segue
- `BuildTransactionUseCaseImpl.invoke()` — `if (form.target.isAccount) { … return@either
  TransactionIntent(…) }`, o ramo que retorna sem tocar em `form.invoiceDueMonth`
- `TransactionForm.from()` (`core/model` — `domain/model/form/TransactionForm.kt`) —
  `invoiceDueMonth = invoiceDueMonth`, sem normalização
- `TransactionWriteTools` — `"invoice_month" to text("Move a card posting to the invoice falling
  due in this month, as \`2026-04\`.")`, a descrição que a tool oferece

## Consequência

O agente pede uma coisa, é informado de que tudo que ele não nomeou foi preservado, e o que ele
nomeou é que sumiu. Não move dinheiro — o lançamento numa conta não tem fatura — mas é a superfície
afirmando um resultado que não teve.

## Sugestão

Recusar `invoice_month` quando o destino resolvido não for um cartão, ao lado das recusas que a
mesma tool já faz. Não vinculante.

## Desfecho

**Causa real** — a sugestão acertou o lugar, e o que ela não dizia é por que as recusas irmãs não
alcançavam este argumento. Todas elas se antecipam a um descarte de `TransactionForm.from`; este
descarte não é do formulário. O formulário carrega `invoiceDueMonth` intacto e quem o perde é o
ramo de conta de `BuildTransactionUseCaseImpl.invoke()`, que monta a `TransactionIntent` e retorna
sem nunca lê-lo — um passo além de onde toda recusa da tool estava. Daí a nota de sucesso ser
exatamente a frase errada: *"Edited. Everything the call did not name kept the value it had."*
descreve o que sobrevive à omissão, e o mês tinha sido nomeado.

O título do arquivo nomeia a classe, não a tool, e a classe tinha **duas** ocorrências: a mesma
lacuna existia em `create_transaction`, que aceitava `invoice_month` ao lado de `account_id` e
respondia *"Recorded."* — sem teste algum. Corrigida junto, porque arquivar só a metade deixaria o
título deste arquivo verdadeiro no dia seguinte.

**Mudança** — `TransactionWriteTools`:
- `update_transaction` recusa `invoice_month` quando o destino que a edição **resolve** não é um
  cartão. Decide o destino resolvido, não o que o lançamento tinha, e por isso cobre também mover
  para uma conta e nomear o mês na mesma chamada. A recusa nomeia só argumentos que a chamada deu:
  `account_id` quando a chamada moveu o lançamento, `card_id` quando ele já estava numa conta.
- `create_transaction` recusa `invoice_month` ao lado de `account_id`, espelhando a recusa de
  `installments` numa conta, logo acima dela.
- As duas descrições de `invoice_month` no schema passam a declarar a recusa, e a linha de cada
  tool em `docs/mcp-tool-surface.md` registra que este é o argumento cujo descarte não é do
  formulário.

**Prova** — três testes novos em `RegistrationFamilyOverTheProtocolTest`, pela protocolo contra o
razão real, vermelhos antes e verdes depois:
`an invoice_month on a posting that sits in an account is refused, not dropped`,
`an invoice_month named while the posting moves off the card is refused` e
`an invoice_month on an account is refused, naming the month and the account`.
No vermelho os três respondiam sucesso com `"is_on_card": false` e a nota de tudo-preservado.
Suíte: `./gradlew jvmTest` verde; `:feature:mcp:impl:jvmTest` 270 testes, 0 falhas.

**Commit** — não commitado: a mudança fica na working tree para revisão, ao lado da de outros
agentes no mesmo arquivo.
