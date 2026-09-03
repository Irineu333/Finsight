---
area: mcp
severity: low
type: test
---

# Os dublês do mundo de teste do MCP reimplementam as regras do domínio em vez de lê-las

## Invariante

Um dublê de teste substitui **encanamento**, nunca **regra**. O que ele não pode alcançar é a
implementação do use case; o predicado do domínio, esse ele lê como qualquer outro consumidor.

Hoje é falso em pelo menos três dos vinte dublês de `AgentWorldOperations.kt`, e as três correções
de 03/09 tornaram a divergência observável: nenhuma das guardas novas chegou ao mundo de teste, de
modo que as três recusas passam verdes na suíte do agente enquanto o app as recusa.

## Mecânica

O dublê não é gratuito, e a KDoc do arquivo justifica-o corretamente: as implementações vivem no
`impl` de cada feature, e um `impl` não pode depender de outro `impl`, então `feature/mcp/impl` não
tem como instanciar `CloseInvoiceUseCaseImpl` e seus irmãos. Substituir a implementação é
obrigatório.

O que não era obrigatório é reescrever o predicado. `Invoice.isClosableOn(date)` vive em
`core/model`, que os dois módulos enxergam. Cada dublê enumera as condições de novo em vez de
perguntar a quem as sabe.

É a mesma doença dos três bugs que o originaram, um nível abaixo. Lá a regra existia no domínio e só
a tela a lia; aqui a regra existe no domínio e só o caminho de produção a lê.

## Evidência

As três ocorrências confirmadas, todas em
`feature/mcp/impl/src/jvmTest/kotlin/com/neoutils/finsight/mcp/AgentWorldOperations.kt`:

- `WorldCloseInvoice.invoke()` — quatro `ensure` copiadas (status, `isClosable`, `yearMonth`), sem
  `isClosableOn`. Contraparte: `CloseInvoiceUseCaseImpl.invoke()`, que hoje lê o predicado
- `WorldConfirmRecurring.invoke()` — recusa por `AMOUNT_NOT_POSITIVE` e pela moeda. Recebe um
  `Clock` e o consulta apenas para carimbar `handledAt`, nunca para a pergunta da data. Contraparte:
  `ConfirmRecurringUseCaseImpl.invoke()`, que hoje recusa `date > clock.today()`
- `WorldAdjustBalance.invoke()` — só `ensureNotNull(account)` e `ensure(targetBalance != current)`,
  e **não recebe `Clock` nenhum**, de modo que a guarda nova não é sequer exprimível ali sem mudar o
  construtor. Contraparte: `AdjustBalanceUseCaseImpl.invoke()`, que hoje recusa
  `adjustmentDate > clock.today()`

Os donos das regras, visíveis dos dois módulos:

- `Invoice.isClosableOn()` (`core/model` — `domain/model/Invoice.kt`)
- `RecurringError.DATE_IN_FUTURE` e `AccountError.ADJUSTMENT_DATE_IN_FUTURE` (`core/model` —
  `domain/error/`)

O mapa para reconferir, em vez de uma afirmação para confiar — os vinte dublês do arquivo, cada um
com a pergunta "que condição ele enumera que o `core/model` já sabe responder?":
`WorldPayInvoice`, `WorldPayInvoicePayment`, `WorldAdvanceInvoicePayment`, `WorldCloseInvoice`,
`WorldOpenInvoice`, `WorldReopenInvoice`, `WorldAdjustInvoice`, `WorldAdjustBalance`,
`WorldTransfer`, `WorldSetDefaultAccount`, `WorldConfirmRecurring`, `WorldSkipRecurring`,
`WorldArchiveAccount`, `WorldUnarchiveAccount`, `WorldArchiveCreditCard`,
`WorldUnarchiveCreditCard`, `WorldArchiveCategory`, `WorldUnarchiveCategory`,
`WorldArchiveRecurring`, `WorldUnarchiveRecurring`.

Três foram conferidos; dezessete não. *Hipótese, não verificada: `WorldPayInvoice` é o candidato
seguinte, por já ter passado a consultar `ValidateInvoicePaymentUseCase` — o que sugere que a porta
existe e nem todos a usam.*

## Consequência

Os testes de protocolo do MCP não alcançam nenhuma das três guardas novas: uma chamada adiantada ou
futura passa verde ali enquanto o app a recusa. Nenhum dado de usuário fica errado — a produção
resolve os use cases reais pelo Koin —, e o dano é o do "verde que não é evidência" que o
`issues/README.md` já registra como lição paga: quem acrescentar a próxima guarda vai lê-la coberta
pela suíte do agente, e não estará.

Vale para toda regra futura destas operações, não só para as três de 03/09.

## Sugestão

Fazer cada dublê ler o predicado do domínio no lugar de enumerar a condição — em `WorldCloseInvoice`
é trocar as duas `ensure` de status por `ensure(invoice.isClosableOn(closedAt))`; em
`WorldConfirmRecurring` é consultar para a data o `Clock` que ele já carrega; em `WorldAdjustBalance`
passa por acrescentar o `Clock` ao construtor, que é o mais caro dos três.

Vale mais decidir o que impede a repetição: enquanto a regra puder ser copiada num dublê, a próxima
divergência nasce silenciosa. Um caminho é extrair as guardas para funções puras no `core/model` —
como `ValidateInvoicePaymentUseCase` já é, concreta na `api` e sem colaborador — e fazer produção e
dublê chamarem a mesma. Aí não sobra o que copiar.

Não vinculante — quem corrige decide.
