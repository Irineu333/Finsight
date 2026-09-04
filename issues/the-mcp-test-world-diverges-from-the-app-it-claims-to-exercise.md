---
area: mcp
severity: low
type: test
---

# O mundo de teste do MCP diverge do app que ele afirma exercer

## Invariante

O mundo de teste do MCP exerce o app. Onde não puder exercer, não afirma.

Hoje é falso em **15 dos 43 dublês**, de três formas que pedem correções diferentes: regra
alcançável e não consultada, regra escrita com predicado diferente do dono, e as duas portas do
razão desligadas — esta última não é reimplementação de regra nenhuma.

## Mecânica

Substituir a implementação é obrigatório: `feature/mcp/impl` não pode depender de outro `impl`, e a
KDoc do topo de `AgentWorldOperations.kt` justifica isso corretamente.

Reescrever a regra não era, e é aqui que o diagnóstico intuitivo erra. **Não é que a regra esteja
fora de alcance.** Os predicados puros do `core/model` — `Invoice.acceptsFullSettlement`,
`isClosableOn`, `Recurring.cycleNumberIn`, `Category.Type.isAccept` — são visíveis dos dois lados, e
o dublê não os consultou. A hipótese seguinte, de que uma guarda que precisa de repositório não
caberia numa `api`, também não se sustenta neste repositório: `StartRecurringFromTransactionUseCase`
é classe concreta na `api` de recurring, com `IRecurringRepository` e `Clock` no construtor.

Isso muda o que uma correção pode prometer. Extrair mais regra para lugares alcançáveis não fecha
nada, porque o que falta não é alcance: **nada obriga um dublê a chamar o dono**, e essa é a
mecânica inteira. Um dublê é livre para responder o que quiser, e o compilador concorda.

A terceira forma não é regra. `AgentWorld` constrói **um único** `TransactionRepository`, com as
duas portas do razão em no-op, sob uma KDoc que se justifica com *"nada aqui escreve"* — e o entrega
também aos dublês de escrita.

## Evidência

### Escala

- `AgentWorldOperations.kt` — 20 dublês, 914 linhas
- `AgentWorldWrites.kt` — 23 dublês, 826 linhas
- 15 deles divergem da produção

*A contagem dos 15 vem do parecer de arquitetura que abriu esta revisão; os dois totais de dublê e
cada caso nomeado abaixo foram reconferidos no disco, o resto não.*

### As portas desligadas

- `AgentWorld.kt` — um único `TransactionRepository(…)`, com `writeGuard = DimensionWriteGuard.None`
  e `removalHook = TransactionRemovalHook.None`, sob a KDoc *"the two ports take their no-op forms
  because nothing here writes"*
- o mesmo valor é passado a dez pontos do arquivo, entre eles os dublês de escrita
- `CreditCardsModule.kt:68` e `:72` — `InvoiceWriteGuard` e o hook de remoção, que a produção
  registra
- `InvoiceError.kt` — atribui ao write boundary, e não às telas, a recusa de lançar em fatura
  fechada

### Regra alcançável e não consultada

- `Invoice.acceptsFullSettlement` (`core/model` — `Invoice.kt:70`) — ausente de
  `WorldPayInvoicePayment`, que roda `ValidateInvoicePaymentUseCase` no lugar; esse lê
  `Invoice.isPayable`, que inclui `RETROACTIVE`, e o dublê aceita o que a produção recusa
- `Invoice.isClosableOn` (`Invoice.kt:85`) — ausente de `WorldCloseInvoice`
- `Recurring.cycleNumberIn` — `WorldConfirmRecurring` calcula a numeração do ciclo à mão
- as guardas de data de `ConfirmRecurringUseCaseImpl` e `AdjustBalanceUseCaseImpl` — o mundo tem
  relógio (`AgentWorld.kt`), e `WorldAdjustBalance` sequer o recebe no construtor

### Predicado diferente do dono

- `WorldDeleteCategory` (`AgentWorldWrites.kt:418`) — `ensure(category.systemKey == null ||
  !accountRepository.hasYieldingAccount())`, onde `ResolveCategoryRetirabilityUseCaseImpl.kt:45`
  decide por `systemKey == SystemCategoryKey.YIELD && hasYieldingAccount()`. O dublê recusa para
  **qualquer** categoria de sistema; a produção, só para a de rendimento
- `WorldAddInstallment` (`AgentWorldWrites.kt:692`) — `val first = round((total - share *
  (installments - 1)) * 100) / 100` põe o resíduo de arredondamento na **primeira** parcela,
  enquanto `AddInstallmentUseCaseImpl.kt:177-180` o põe na **última** (`if (index == count - 1)
  share + remainder else share`), com a KDoc de produção dizendo por quê

### O precedente que derruba a hipótese de alcance

- `StartRecurringFromTransactionUseCase.kt:6-8` — classe concreta na `api`, com
  `IRecurringRepository` e `Clock` injetados

## Consequência

A faixa `low` mede consequência para quem usa o app, e esta não tem nenhuma: em produção o Koin
injeta os use cases reais e as duas portas registradas. O que se perde é o valor probatório da
suíte, em três graus:

- as três guardas de data fechadas em 03/09 não são exercidas por teste de protocolo nenhum;
- a suíte de escrita inteira roda com a recusa de lançar em fatura fechada **desligada**, sob um
  comentário que afirma que ali não se escreve;
- um teste escrito sobre `WorldPayInvoicePayment` afirmaria que se quita integralmente uma fatura
  retroativa — o oposto do que o app faz — e passaria verde.

O `issues/README.md` já registra *"uma suíte verde não é evidência"* como lição paga mais de uma
vez. Aqui ela vale para quinze dublês e para todo o caminho de escrita do módulo.

## Sugestão

A saída que torna a divergência **impossível** em vez de detectável já é possível hoje, sem mexer em
fronteira de módulo: mover os testes que afirmam algo sobre o app para `app/shared/src/jvmTest`,
onde as implementações reais já estão. `mcpModule` está em `appModules` (`AppModules.kt:22`),
`mcpPlatformModule` monta `McpToolDependencies` com os `*UseCaseImpl` de produção,
`McpServerController` é interface pública da `feature/mcp/api`, e `AppLedgerHarness` já sobe
`appModules` inteiro sobre um Room real — `SettingsHostsTheMcpSectionTest` já faz `startKoin {
modules(appModules) }`. Não sobra segunda cópia para divergir, e as portas ficam plugadas porque
quem as registra é o módulo Koin da feature.

Fica uma condição: os testes de protocolo puro — token, sessões, ciclo de vida, permissões,
`McpSurfaceIsClosedTest` — usam tipos `internal` do módulo e não podem sair. Ali o dublê continua
legítimo desde que **não possa recusar**, o que se garante por varredura de fonte exigindo zero
`ensure`/`require`/`throw` em `AgentWorld*.kt`, no idioma que `RegistrationToolsGoThroughUseCasesTest`
e irmãos já usam. Vale saber que a política escrita hoje é o contrário disso
(`AgentWorldWrites.kt:101-102`).

O custo é real e não deve ser escondido: a semeadura muda de natureza, porque os ids passam a vir do
Room em vez de serem escolhidos à mão nas listas do mundo.

E há o que nenhuma das saídas alcança: oito sítios de tool repetem a recusa de direção de categoria
de propósito e com justificativa escrita, de modo que um teste do protocolo hoje passa pela cópia da
tool e não pela regra. Isso continua verdadeiro depois de qualquer das mudanças acima.

Não vinculante — quem corrige decide.
