---
area: mcp
severity: low
type: data
verdict: fixed
---

# A tabela "O que fica de fora" não traz backup nem restauração, que o código declara

## Invariante

A tabela da seção `## O que fica de fora`, em `docs/mcp-tool-surface.md`, lista as exclusões que
`McpSurface.exclusions` declara. É o que o próprio documento afirma ao abrir a seção — *"Varrido
contra as features do app, não amostrado. O que faltar aqui é omissão, não silêncio."* — e ao
dizer, na linha seguinte, que *"a lista vive no código"*.

Hoje é falso em duas linhas: `McpSurface.exclusions` tem dezesseis entradas e a tabela tem
quatorze exclusões, faltando *"Capturing and configuring backups"* e *"Restoring the database from
a backup"*.

## Mecânica

As duas nasceram em `04e04b627`, que fechou
`archive/2026-09-03-the-backup-feature-is-neither-offered-nor-excluded.md`. O commit tocou
`McpSurface.kt` e `CLAUDE.md` e não tocou o documento, que continua com a lista de antes.

A conferência por contagem não denuncia a falta: a tabela tem quinze linhas, e a décima quinta
— *"Arquivar orçamento e parcelamento"* — não é uma exclusão, é a declaração de que não há
capacidade a excluir, marcada na coluna de grau com *"não é retenção"*. Quatorze exclusões mais
uma não-exclusão contra dezesseis no código.

O documento afirma o contrário de si mesmo dois parágrafos antes da tabela, na seção da família 3,
onde a ausência de `without_copy` é justificada com *"configurar o cofre já está declarado fora de
escopo em `McpSurface`"* — verdade no código, ausente da tabela que deveria carregá-la.

## Evidência

- `McpSurface.exclusions` — dezesseis entradas; as duas últimas são as de backup e restauração
- `docs/mcp-tool-surface.md`, seção `## O que fica de fora` — a tabela, sem nenhuma das duas
- `docs/mcp-tool-surface.md`, seção `## Família 3 — Registro` — *"configurar o cofre já está
  declarado fora de escopo em `McpSurface`"*
- `04e04b627` — `CLAUDE.md`, `McpSurface.kt` e o arquivo da issue; nada mais
- `McpSurfaceIsClosedTest` — não compara documento e código, e não teria como: a tabela é prosa

## Consequência

O documento é o material de quem decide se uma capacidade ausente foi recusada ou esquecida, e é
a metade que se lê primeiro — a outra é uma `val` no meio de um arquivo Kotlin. Quem consultar a
tabela para saber se backup foi decidido conclui que não foi, refaz a análise que a issue de 3 de
setembro já fez e chega à mesma resposta.

É a segunda divergência do mesmo documento com o disco: a primeira está em
`the-mcp-surface-document-counts-one-use-case-short.md`, na seção `## Números`. As duas têm a mesma
forma — um número ou uma lista mantidos à mão num documento que se declara reconciliado.

## Sugestão

Acrescentar as duas linhas. E, já que é a segunda vez, considerar o que a issue irmã levanta: uma
lista que só um humano atualiza, num documento que se apresenta como varredura, é a que ninguém
reconfere. As exclusões são dados estruturados em `McpSurface.exclusions` — `capability`, `kind` e
`reason` são exatamente as três colunas da tabela.

Não vinculante — quem corrige decide.

## Desfecho

**Causa real** — a do registro, com a contagem refeita do zero em vez de herdada.
`McpSurface.exclusions` (`feature/mcp/impl/.../McpSurface.kt:126-224`) tem **16** entradas —
contadas pelos 16 `McpSurfaceExclusion(` do bloco, nas linhas 127, 135, 142, 148, 153, 159, 164,
171, 177, 182, 187, 192, 197, 202, 208 e 215 —, das quais **3** são `WITHHELD` e **13**
`OUT_OF_SCOPE`. A tabela de `## O que fica de fora` trazia **15 linhas de dados**: 14 exclusões,
mais a de *"Arquivar orçamento e parcelamento"*, que não é exclusão. Faltavam
as duas últimas do código, `"Capturing and configuring backups…"` (`:209`) e
`"Restoring the database from a backup"` (`:216`), na ordem em que o código as declara.

Confirmada também a causa apontada: `04e04b627` acrescentou as duas ao `McpSurface.kt` e ao
`CLAUDE.md` e não tocou o documento.

**Mudança** — três, todas em `docs/mcp-tool-surface.md`:

1. As duas linhas que faltavam, na posição que o código lhes dá — depois de *"Idempotência de
   escrita"* e antes da linha que não é exclusão —, com o grau e o motivo vindos de `capability`,
   `kind` e `reason`. A da restauração carrega inteira a ressalva do código: mesma forma de
   estrago que escrever taxa ou trocar a moeda base, e ainda assim `OUT_OF_SCOPE` e não
   `WITHHELD`, porque nenhum requisito escrito a proíbe.
2. Um parágrafo curto **acima** da tabela dizendo que a última linha não é uma exclusão, e que
   quem reconciliar tabela com código contando linhas precisa deixá-la de fora. Era exatamente o
   que fazia a divergência sobreviver a uma conferência por contagem, e não introduz número
   mantido à mão — que é a doença.
3. **Entrou junto, por pedido do coordenador e da mesma natureza:** a linha de
   `get_budget_progress` na tabela da Família 1 resumia o payload como *"limite, gasto, restante e
   % por orçamento"*, e o payload ganhou dois campos desde então. Conferido direto no disco:
   `AgentBudget` (`feature/mcp/impl/.../surface/AgentBudget.kt:36-40`) declara `is_exceeded`
   (`Boolean?`) e `exceeded_by` (`AgentFigure?`), publicados por
   `GetBudgetProgressTool.kt:107-108` como `isExceeded.takeIf { isResolved }` e
   `exceededAmount?.takeIf { isExceeded }`. A linha passa a nomear os dois e a dizer por que ficam
   **ausentes** — nunca `false` — enquanto alguma parte do gasto não tem taxa que a alcance.

Depois da mudança a tabela tem **17 linhas de dados**: as 16 exclusões na ordem do código e a
não-exclusão no fim. As 3 `WITHHELD` da tabela continuam sendo as 3 do código, que é o par que
`McpSurfaceIsClosedTest` já sustenta pelo lado do código.

**Prova** — não há teste, e forçar um aqui seria teatro: a tabela é prosa em português e a `val` é
Kotlin; comparar as duas exigiria construir a geração do documento, que esta rodada não faz de
propósito (a ideia está no relatório). A conferência foi mecânica e é refazível:

```bash
grep -c "^        McpSurfaceExclusion(" feature/mcp/impl/src/jvmMain/kotlin/com/neoutils/finsight/mcp/McpSurface.kt   # 16
awk '/^## O que fica de fora/,/^`McpSurfaceIsClosedTest` sustenta/' docs/mcp-tool-surface.md | grep -c "^| "          # 18 = 1 cabeçalho + 16 + 1
```

Antes da mudança a segunda linha respondia **16** (1 cabeçalho + 14 + 1). Os nomes dos campos do
orçamento foram lidos em `AgentBudget.kt` e em `GetBudgetProgressTool.kt`, não no recado que os
anunciou.

Nenhum código mudou, então nenhuma suíte é prova desta correção; as que rodaram nesta rodada estão
no desfecho do bug da porta.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
