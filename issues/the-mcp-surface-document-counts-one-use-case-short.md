---
area: mcp
severity: low
type: data
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
