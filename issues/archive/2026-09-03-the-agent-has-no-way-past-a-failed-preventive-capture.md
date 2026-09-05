---
area: mcp
severity: medium
type: ux
verdict: fixed
---

# O agente não tem como passar de uma captura preventiva que falhou

## Cenário

**DADO** o cofre de backup preventivo ligado e o destino configurado inalcançável (pasta
removida, permissão revogada, etc.)
**QUANDO** um agente chama `delete_transaction`, `delete_installment` ou `delete_invoice`
**ENTÃO** a chamada falha com uma recusa genérica — `"The operation could not be completed."`,
sem menção a backup, cofre ou cópia — e não há nenhuma forma de prosseguir; só um humano abrindo
o app consegue desligar ou consertar o cofre
**DEVERIA** ou recusar descrevendo o motivo real (a cópia preventiva não pôde ser feita), ou dar
ao chamador uma forma explícita de prosseguir sem ela — a mesma decisão que a tela oferece por
um modal

## Mecânica

A captura preventiva está ligada corretamente na camada de domínio/razão, e não na tela, então o
agente herda o comportamento de graça no caminho feliz: `DeleteTransactionUseCaseImpl`,
`DeleteInstallmentUseCaseImpl` e `DeleteFutureInvoiceUseCaseImpl` chamam o repositório com
`withoutCopy = false` por padrão, o que aciona `TransactionRemovalPrelude` →
`PreventiveBackup.captureBefore(...)` antes da remoção — igual à tela.

O problema é só no caminho de falha. Quando o cofre não consegue gravar a cópia,
`PreventiveBackup.captureBefore` lança `PreventiveCaptureException` (`PreventiveBackup.kt:31-34`).
Na tela, isso é capturado por `CaptureRefusal.attempt` (`CaptureRefusal.kt:57-63`), que pergunta à
pessoa se quer prosseguir sem cópia e, se sim, chama de novo com `withoutCopy = true`. As três
tools MCP não têm esse segundo passo: `writing {}` (`WriteSupport.kt:100-105`) só captura
`BadArgument`, e nenhuma das três (`DeleteTransactionTool`, `DeleteInstallmentTool`,
`DeleteFutureInvoiceTool`) declara `without_copy`/`force` no `inputSchema`. A exceção escapa sem
tratamento até o catch-all genérico de `AgentActivityJournal.execute()`
(`AgentActivityJournal.kt:33-47`) — que o próprio comentário do código rotula como reservado para
"a tool that throws instead of refusing is a defect" — e devolve o texto fixo
`"The operation could not be completed."` com `summary = tool.name` no lugar do resumo legível
que toda outra recusa carrega.

`DeleteFutureInvoiceUseCaseImpl` (`.../DeleteFutureInvoiceUseCaseImpl.kt:22-62`) chega ao mesmo
lugar por um caminho ligeiramente diferente: não envolve a remoção num `catch { }` como as outras
duas, então a exceção (e qualquer outra que a remoção lance) já escapa do
`Either<InvoiceException, Unit>` sem precisar de um relançamento explícito.

## Evidência

- `PreventiveBackup.kt:31-34` (`captureBefore`) — o contrato e o `@throws` que documenta o
  travamento
- `CaptureRefusal.kt:57-63` (`attempt`) — como a tela responde à exceção; sem equivalente no MCP
- `DeleteTransactionUseCaseImpl.kt:43-49`, `DeleteInstallmentUseCaseImpl.kt:49-53` — o
  `onLeft { if (cause is PreventiveCaptureException) throw cause }` que garante que só um humano
  pode responder
- `DeleteFutureInvoiceUseCaseImpl.kt:22-62` — a remoção sem `catch { }` ao redor, mesmo efeito por
  um caminho diferente
- `WriteSupport.kt:100-105` (`writing`) — só trata `BadArgument`
- `AgentActivityJournal.kt:33-47` (`execute`) — o catch-all genérico e o comentário que o rotula
  como defeito
- `TransactionWriteTools.kt` (`DeleteTransactionTool.inputSchema`), `InstallmentWriteTools.kt`
  (`DeleteInstallmentTool`, ~linha 243), `InvoiceWriteTools.kt` (`DeleteFutureInvoiceTool`, ~linha
  113) — nenhum schema tem `without_copy`/`force`

## Consequência

Sob essa configuração (cofre ligado, destino inalcançável — não é o normal, mas é exatamente o
que o recurso de backup existe para vigiar), o agente não consegue apagar nenhuma transação,
parcelamento ou fatura futura/retroativa até um humano abrir o app e resolver o cofre pela tela.
A recusa recebida não menciona backup, cofre ou cópia, então um agente instruído a diagnosticar o
problema não tem pista de por onde procurar, e pode concluir — com confiança e de forma errada —
que o dado em si é o problema.

## Sugestão

Duas saídas independentes, a primeira mais barata: traduzir `PreventiveCaptureException` num
`AgentRefusal` descritivo, como as demais recusas do domínio, em vez de deixá-la cair no catch-all
genérico — isso já tornaria o diagnóstico possível. A segunda, maior: dar às três tools uma forma
explícita de dizer "prosseguir sem cópia", espelhando a pergunta que a tela faz. Não vinculante.

## Desfecho

**Causa real** — a mecânica registrada estava certa inteira; a `## Sugestão` errou o alvo na
segunda metade. O defeito é só a **tradução ausente**: `PreventiveCaptureException` é a única
recusa do domínio que chega como exceção, `writing {}` trata apenas `BadArgument`, e nada entre a
tool e o catch-all de `AgentActivityJournal` a reconhece.

O travamento em si **não é defeito** — é o cofre fazendo o que existe para fazer. Três contratos no
código dizem que só uma pessoa pode responder "prosseguir sem cópia": `PreventiveBackup.kt:31-33`
(*"only the person told about the failure may say to go on without a copy"*), `CaptureRefusal.kt:18-19`
(*"Nobody else may answer it"*) e `McpSurface.kt:208-214`, que já declara capturar e configurar
backup como fora do escopo desta superfície. Um `without_copy` no fio — a segunda saída sugerida —
daria ao agente, uma chamada por vez, exatamente a salvaguarda que ele não pode nem configurar, e
tornaria falso o comentário que justifica o `@OptIn(WithheldAnnouncement::class)` em
`DeleteTransactionUseCaseImpl.kt:18-19` (*"The person was already offered the copy and said to go on
without it"*). Fechou-se então pelo primeiro ramo do `DEVERIA`, e a ausência do `without_copy`
passou a ser decisão declarada em vez de omissão.

**Mudança** — `removing(summary) { }` em `mcp/tool/WriteSupport.kt`, ao lado do `writing {}` cuja
lacuna ela fecha: captura `PreventiveCaptureException` e a traduz num `AgentRefusal` que diz que
nada foi removido, cita as palavras do próprio cofre (`refused.message`, sem reescrevê-las, como
`AgentRefusal.fromDomain` faz com as demais) e aponta onde a pessoa resolve. Passam por ela as três
tools que removem lançamentos — `DeleteTransactionTool`, `DeleteInstallmentTool` e
`DeleteInvoiceTool` (o arquivo do bug a chamava `DeleteFutureInvoiceTool`; a classe no disco é
`DeleteInvoiceTool`, em `InvoiceWriteTools.kt`) —, cada uma passando o resumo legível que já
calculava, o que de quebra tira o `summary = tool.name` do registro de atividade. `feature/mcp/impl`
ganhou `implementation(projects.feature.backup.api)` no `jvmMain`: sem o tipo não há o que capturar.
Nenhum `inputSchema` mudou. `docs/mcp-tool-surface.md` passa a declarar a recusa e a ausência
deliberada do `without_copy`, uma vez para a família inteira em vez de três.

**Prova** — `PreventiveCaptureReachesTheAgentTest` (novo, em `feature/mcp/impl/src/jvmTest`), pelo
fio, contra o registro de produção com só as três remoções religadas a um cofre que não grava. Seis
testes, **cinco vermelhos antes da mudança**, com a mensagem deste arquivo palavra por palavra:
`"the vault's refusal fell through to the journal's catch-all… Actual: The operation could not be
completed."` nas três tools, o `payload()` estourando por o texto não ser JSON, e `"the tool's name
stood in for the posting… Actual: delete_transaction"`. O sexto — nada é removido enquanto a cópia é
devida — nasceu verde de propósito: guarda a metade do comportamento que a correção não podia mexer.

Rodados `:feature:mcp:impl:jvmTest --tests "*.PreventiveCaptureReachesTheAgentTest"` (6 verdes) e a
suíte inteira `./gradlew jvmTest`: **2196 testes, 0 falhas, 0 erros** em 341 arquivos de resultado.
A árvore estava compartilhada com outras correções em voo (`stringOr`/título vazio, `invoice_month`),
e a suíte verde inclui as três.

**Commit** — nenhum: a mudança ficou no working tree para revisão, a pedido.
