---
area: mcp
severity: low
type: data
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
