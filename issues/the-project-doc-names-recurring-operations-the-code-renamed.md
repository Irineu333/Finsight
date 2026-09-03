---
area: transversal
severity: low
type: data
---

# `CLAUDE.md` descreve recorrências com "stop/reactivate", operações que o código renomeou

## Invariante

A lista de features do `CLAUDE.md` nomeia as operações que existem.

Hoje é falso numa linha: `CLAUDE.md:35` descreve a feature de recorrências como
*"recurring transactions (confirm/skip/stop/reactivate)"*. As quatro operações correspondentes na
`api` são `ConfirmRecurringUseCase`, `SkipRecurringUseCase`, `ArchiveRecurringUseCase` e
`UnarchiveRecurringUseCase` — não há `StopRecurringUseCase` nem `ReactivateRecurringUseCase` em
lugar nenhum do disco.

## Mecânica

A mudança `2026-07-26-archive-and-redesign-recurring` renomeou os dois pares, trocando parar e
reativar por arquivar e desarquivar — a mesma dupla que conta, cartão e categoria já usavam. Os
símbolos mudaram; a linha do `CLAUDE.md` ficou.

## Evidência

- `CLAUDE.md:35` — "confirm/skip/stop/reactivate"
- `ArchiveRecurringUseCase` e `UnarchiveRecurringUseCase` (`feature/recurring/api`) — os dois que
  existem
- `openspec/changes/archive/2026-07-26-archive-and-redesign-recurring/proposal.md` — a renomeação
- Uma busca por `stop`, `pause`, `reactivate` ou `resume` sob `feature/recurring` não devolve
  nenhum símbolo

## Consequência

Quem parte da documentação procura dois símbolos que não existem. Numa varredura de lacunas entre
o app e a superfície MCP, essa linha produziu uma suspeita falsa — "parar" e "reativar" pareciam
capacidades sem ferramenta correspondente, quando `archive_entity` e `unarchive_entity` já as
cobrem, com `type: "recurring"`. O custo é de tempo, e recai sobre quem confia no documento.

É a segunda divergência aberta no mesmo arquivo: a outra está em
`the-architecture-docs-still-describe-a-home-feature-that-was-deleted.md`, que descreve módulos
apagados. As duas fecham na mesma passada.

## Sugestão

Trocar por "confirm/skip/archive/unarchive". Vale conferir, na mesma edição, se as demais linhas
da seção `## Features` nomeiam operações que ainda existem — é a única seção do documento que lista
verbos, e nenhum teste a lê.

Não vinculante — quem corrige decide.
