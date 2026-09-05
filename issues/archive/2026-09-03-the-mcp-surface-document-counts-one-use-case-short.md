---
area: mcp
severity: low
type: data
verdict: fixed
---

# `docs/mcp-tool-surface.md` conta 42 use cases na `api` onde a árvore tem 43

## Invariante

As contagens de `docs/mcp-tool-surface.md` são as que estão no disco — é o que o próprio documento
promete no cabeçalho, ao dizer que foi *"reconciliado contra a implementação"* e que *"as entradas,
os use cases e as contagens abaixo são os que estão no disco hoje"*.

Hoje é falso em um ponto: `ValidateInvoicePaymentUseCase` está na `api` de `creditcards` e não
aparece em nenhuma das duas seções que o contariam.

## Mecânica

O documento foi reconciliado em `758f3d7d4`; `ValidateInvoicePaymentUseCase` nasceu depois, em
`5192b90d1`, quando a regra que decide se uma fatura pode ser quitada numa data deixou de ser
função de extensão e virou classe concreta na `api` — a forma que os outros `Validate*UseCase` do
repositório já tinham. O documento foi tocado por três commits posteriores e a contagem não subiu
junto.

A divisão promovidos/criados permanece verificável e só a metade "criados" está curta: dos 43
arquivos `*UseCase.kt` que existem sob `feature/*/api/src` e não existem em `main`, **34** têm
homônimo no `impl` de `main` — os promovidos que o documento conta certo — e **9** nasceram aqui,
contra os 8 que ele lista.

O homônimo confunde a conferência, e é por isso que a divergência sobrevive a uma leitura
apressada: `main` tem um `ValidateInvoicePaymentUseCase` no `impl` de `creditcards`, que é outra
regra — a do pagamento **parcial** — e que o merge renomeou para
`ValidateAdvanceInvoicePaymentUseCase`. Quem conta por nome de arquivo lê o novo como promoção de
um que já existia.

## Evidência

- `docs/mcp-tool-surface.md`, seção `## Números`, linha "Use cases que passaram a estar na `api` de
  uma feature": **42 — 34 promovidos de `impl` e 8 criados**
- `docs/mcp-tool-surface.md`, seção `## O que nasceu`: *"**Oito** use cases — sete previstos e um
  que a exploração não viu"*, com tabela de oito linhas
- `ValidateInvoicePaymentUseCase` (`feature/creditcards/api` — `domain/usecase/`), criado por
  `5192b90d1`, ausente das duas seções
- `ValidateAdvanceInvoicePaymentUseCase` (`feature/creditcards/impl` — `domain/usecase/`) — o
  homônimo de `main`, renomeado no merge; o `diff` contra a versão de `main` é o nome da classe e um
  parágrafo de KDoc, nada de comportamento
- `git diff --name-status main...HEAD -- "feature/*/api/src/*UseCase.kt"` responde 43 adições

## Consequência

Perde-se justamente a linha que o documento existe para carregar. A seção `## O que nasceu` é a que
explica **por que** cada use case foi criado, e o nono é o de razão mais própria: a regra tinha
acabado de ganhar dono, e o dono era uma função de extensão onde o repositório já tinha uma forma
para "decidir se isto é permitido".

O dano é de leitura e não de execução: o documento se declara guia e não contrato, a superfície é
fechada no código, e nenhuma contagem por eixo é mantida à mão — `McpSurface.toolCountByAxis` deriva
as dela. Esta é a única contagem do documento escrita à mão, e é a única que envelheceu.

## Sugestão

Corrigir 42 → 43 e 8 → 9 em `## Números`, e acrescentar a nona linha em `## O que nasceu`, dizendo
de onde ela saiu — a função de extensão `Invoice.paymentObstacleOn`, criada e substituída no mesmo
ciclo — e por que a duas escritas do pagamento consultam a mesma regra.

Vale considerar se a linha deve continuar existindo à mão. Uma contagem que só um humano atualiza,
num documento que se apresenta como reconciliado, é a que ninguém reconfere.

Não vinculante — quem corrige decide.

## Desfecho

**Causa real** — a do registro, com a contagem refeita e não herdada.
`git diff --name-status main...HEAD -- "feature/*/api/src/*UseCase.kt"` responde **43 adições**
(mais 5 modificações, que já estavam na `api`), contra as **42** da linha de `## Números`.

A divisão foi refeita pelo mesmo critério que o registro descreve — existe homônimo em `main`? —,
uma por uma sobre as 43. Por nome de arquivo, 35 têm homônimo em `main` e 8 não. Mas
**`ValidateInvoicePaymentUseCase` é um falso positivo**, e conferi os dois lados:

- `main:feature/creditcards/impl/.../ValidateInvoicePaymentUseCase.kt` é a regra do pagamento
  **parcial** — `invoke(invoiceId, amount, date, paidAmount?, excluding?)`, segura
  `IInvoiceRepository`, `CalculateInvoiceUseCase` e um `Clock`, e devolve
  `ValidatedInvoicePayment`. Nasceu na change `2026-08-22-edit-invoice-payment` (tarefa 2.2). Em
  `HEAD` ela existe, no `impl`, sob o nome `ValidateAdvanceInvoicePaymentUseCase`
  (`feature/creditcards/impl/.../ValidateAdvanceInvoicePaymentUseCase.kt`).
- `HEAD:feature/creditcards/api/.../ValidateInvoicePaymentUseCase.kt` é outra regra: se a fatura
  aceita ser **quitada** naquela data. `invoke(invoice, date, today)`, sem colaborador, devolvendo
  `Either<InvoiceError, Invoice>`. Criada por `5192b90d1`, que apagou
  `extension/InvoicePayment.kt` — a função `Invoice.paymentObstacleOn`, que por sua vez nascera em
  `4a06829f9`, no mesmo ciclo. Não está em nenhuma tarefa da change do MCP.

Descontado o homônimo: **34 promovidos e 9 criados**. Os 8 que o documento já listava batem
exatamente com os 8 sem homônimo por nome — `RegisterTransaction`, `CreateCategory`,
`UpdateCategory`, `CreateBudget`, `UpdateBudget`, `DeleteBudget`, `UpdateInstallment` e
`UpdateTransaction` —, então o nono é o `ValidateInvoicePayment`. E os "sete previstos" também se
sustentam: D9 os enumera nas tarefas 3.1 a 3.6, `UpdateTransactionUseCase` entrou pela 3.10
declarando-se imprevisto, e o `ValidateInvoicePayment` não aparece em tarefa nenhuma.

**Mudança** — três, em `docs/mcp-tool-surface.md`:

1. `## Números`: *"**42** — **34** promovidos de `impl` e **8** criados"* → *"**43** — **34**
   promovidos de `impl` e **9** criados"*.
2. `## O que nasceu`: *"**Oito** … sete previstos e um que a exploração não viu"* → *"**Nove** …
   sete previstos e dois que a exploração não viu"*. A frase seguinte dizia que *cada um* saiu de
   um ViewModel, o que já era falso para `UpdateInstallmentUseCase` (a linha dele traz "—") e
   passaria a sê-lo para o nono; virou "quase todos", com uma oração dizendo de onde vieram os
   dois que não.
3. A nona linha da tabela, dizendo de onde saiu — a função de extensão `Invoice.paymentObstacleOn`
   —, por que virou classe (o repositório já tinha a forma `Validate*UseCase` para "decidir se
   isto é permitido") e por que está na `api` como classe concreta: não segura colaborador. E o
   ponto que a torna a de razão mais própria: **as duas escritas do pagamento consultam a mesma
   regra** — `PayInvoicePaymentUseCaseImpl:24` antes de lançar, `PayInvoiceUseCaseImpl:20` como a
   sua própria guarda —, então a resposta dada antes do dinheiro sair não pode divergir da dada
   depois.

**Prova** — não há teste, e não caberia: o defeito é um número em prosa. A conferência é mecânica
e refazível:

```bash
git diff --name-status main...HEAD -- "feature/*/api/src/*UseCase.kt" | grep -c '^A'   # 43
```

E a divisão, uma linha por arquivo, comparando o basename com a árvore de `main`:

```bash
for f in $(git diff --name-status main...HEAD -- "feature/*/api/src/*UseCase.kt" | grep '^A' | cut -f2); do
  b=$(basename "$f")
  git ls-tree -r --name-only main | grep -q "/$b$" && echo "PROMOVIDO $b" || echo "NOVO $b"
done
```

35 promovidos e 8 novos por esse critério cru; 34 e 9 depois de descontar o homônimo, cujos dois
lados foram lidos inteiros (assinatura, colaboradores e retorno) em vez de comparados por nome.

Nenhum código mudou, então nenhuma suíte é prova desta correção.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
