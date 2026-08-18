# Issues

Achados em aberto, um arquivo por issue, nomeados `NNN-slug.md`. O número é um id estável; esta
tabela é a ordenação.

Cada arquivo diz o que está errado, a evidência com arquivo e linha, o cenário de falha e uma
correção sugerida. Onde a verificação corrigiu ou enfraqueceu o achado, a correção está no próprio
arquivo em vez de ter sido descartada.

## Abertas

| # | Issue | Área | Tipo | Severidade |
|---|---|---|---|---|
| [001](001-create-transaction-accepts-negative-amount.md) | `create_transaction` aceita valor negativo | mcp | dados | **alta** |
| [002](002-is-recurring-dropped-when-splitting.md) | `is_recurring` descartado quando `installments > 1`, em silêncio | mcp / transactions | dados | média |
| [003](003-json-null-read-as-the-string-null.md) | Um `null` JSON explícito é lido como a string `"null"` | mcp | correção | média |
| [004](004-transaction-form-drops-arguments-silently.md) | Categoria incompatível e `installments` fora do cartão descartados em silêncio | mcp / model | correção | média |
| [005](005-connection-snippet-shows-the-token-in-clear-text.md) | O snippet de conexão mostra o token em texto claro | mcp (UI) | segurança | média |
| [007](007-agent-log-runs-three-queries-per-row.md) | O log completo do agente faz três queries por linha, por emissão | mcp (UI) | performance | média |
| [008](008-list-transactions-loads-the-whole-table.md) | `list_transactions` carrega a tabela inteira a cada página | mcp | performance | média |
| [009](009-last-day-of-a-month-reads-as-finished.md) | O último dia do mês é lido como encerrado | mcp | correção | média |
| [010](010-cannot-reapply-the-configured-port.md) | A porta configurada não pode ser reaplicada depois de um bind falho | mcp (UI) | UX | média |
| [006](006-bottom-bar-does-not-ask-is-offered.md) | A bottom bar não pergunta `isOffered` | shell | correção (latente) | baixa |
| [011](011-privileged-ports-are-offered-and-misdiagnosed.md) | Portas privilegiadas oferecidas, falha diagnosticada como "em uso" | mcp | UX | baixa |
| [012](012-closing-the-desktop-window-blocks-the-event-thread.md) | Fechar a janela bloqueia a thread de eventos do AWT | app/desktop | UX | baixa |
| [013](013-returned-is-counted-before-unmappable-rows-are-dropped.md) | `returned` contado antes de descartar linhas não mapeáveis | mcp | correção (latente) | baixa |
| [014](014-force-unwrap-of-a-documented-nullable-after-the-write.md) | `!!` sobre um nullable documentado, depois de a escrita ter sido aplicada | mcp | robustez | baixa |
| [015](015-unused-imports-in-mcp-ui-state.md) | Dois imports não usados em `McpUiState` | mcp (UI) | código morto | trivial |

## Procedência

Levantadas por uma revisão de `main...feature/local-mcp-server` (451 arquivos — o servidor MCP local
mais a separação api/impl dos use cases), e então verificadas uma a uma contra a árvore em
`cc6ca4ccf`, em 2026-08-17. Nenhum arquivo fora deste diretório foi modificado.

Das quinze levantadas:

- **oito** procedem exatamente como descritas — 001, 002, 003, 004, 005, 009, 010, 015;
- **duas** procedem e são piores do que o relatado — 007 e 008, onde uma leitura por linha se revelou
  três queries em vez de uma;
- **duas** procedem apenas como inconsistências latentes, inalcançáveis na árvore como ela está —
  006 e 013, cada uma dizendo no próprio arquivo por quê;
- **uma** procede com o mecanismo corrigido — 011, onde o `UNAVAILABLE` previsto é na verdade
  `PORT_IN_USE`, testado nesta máquina;
- **uma** é deliberada e está registrada como trade-off de severidade baixa — 012, cujo comentário em
  `main.kt` reconhece a espera;
- **uma** foi rebaixada de defeito a smell de robustez — 014, que não é alcançável por nenhum caminho
  de escrita atual.

Duas afirmações da revisão original foram corrigidas durante a verificação e não se repetem aqui: a
de que a resposta de `create_transaction` reporta o lançamento invertido pelo campo `direction` (ele é
omitido sem perspectiva; quem diz "expense" é o `nature`), e a de que as leituras por linha do log de
atividade rodam na main thread (as funções suspend de DAO do Room saltam para o dispatcher do banco —
o que fica na main é o coletor e uma retomada por linha).

## Relacionado

`docs/auditoria-bugs-2026-07.md` — a auditoria de julho/2026 sobre a `main`. Os itens 16, 21, 22 e 23
de lá se sobrepõem a 001, 007 e 008 daqui, e cada arquivo nomeia a sobreposição.
