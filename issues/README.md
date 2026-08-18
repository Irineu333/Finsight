# Issues

Achados em aberto, um arquivo por issue, nomeados `NNN-slug.md`. O número é um id estável; a
criticidade é a ordenação.

Cada arquivo diz o que está errado, a evidência com arquivo e linha, o cenário de falha e uma
correção sugerida. Onde a verificação corrigiu ou enfraqueceu o achado, a correção está no próprio
arquivo em vez de ter sido descartada.

## Sumário por criticidade

Nenhum achado chegou a **CRÍTICO**, e **ALTO** está vazia desde que a
[001](archive/001-create-transaction-accepts-negative-amount.md) foi corrigida. O que resta não
corrompe número nenhum do ledger.

| # | Issue | Área | Tipo |
|---|---|---|---|
| **MÉDIO** |
| [005](005-connection-snippet-shows-the-token-in-clear-text.md) | O snippet de conexão mostra o token em texto claro | mcp (UI) | segurança |
| [007](007-agent-log-runs-three-queries-per-row.md) | O log completo do agente faz três queries por linha, por emissão | mcp (UI) | performance |
| [008](008-list-transactions-loads-the-whole-table.md) | `list_transactions` carrega a tabela inteira a cada página | mcp | performance |
| [009](009-last-day-of-a-month-reads-as-finished.md) | O último dia do mês é lido como encerrado | mcp | correção |
| [010](010-cannot-reapply-the-configured-port.md) | A porta configurada não pode ser reaplicada depois de um bind falho | mcp (UI) | UX |
| [016](016-update-transaction-drops-the-category-silently.md) | `update_transaction` descarta a categoria em silêncio, e recusa uma receita em cartão pelo argumento errado | mcp / model | correção |
| **BAIXO** |
| [006](006-bottom-bar-does-not-ask-is-offered.md) | A bottom bar não pergunta `isOffered` | shell | correção (latente) |
| [011](011-privileged-ports-are-offered-and-misdiagnosed.md) | Portas privilegiadas oferecidas, falha diagnosticada como "em uso" | mcp | UX |
| [012](012-closing-the-desktop-window-blocks-the-event-thread.md) | Fechar a janela bloqueia a thread de eventos do AWT | app/desktop | UX |
| [013](013-returned-is-counted-before-unmappable-rows-are-dropped.md) | `returned` contado antes de descartar linhas não mapeáveis | mcp | correção (latente) |
| [014](014-force-unwrap-of-a-documented-nullable-after-the-write.md) | `!!` sobre um nullable documentado, depois de a escrita ter sido aplicada | mcp | robustez |
| [015](015-unused-imports-in-mcp-ui-state.md) | Dois imports não usados em `McpUiState` | mcp (UI) | código morto |

## Corrigidas

Cada uma foi para `archive/` no mesmo commit da correção, e o arquivo diz no fim o que a correção
mudou — inclusive onde o achado estava errado.

| # | Issue | Faixa | Corrigida em |
|---|---|---|---|
| [001](archive/001-create-transaction-accepts-negative-amount.md) | Valor negativo aceito na escrita, e registrado na direção oposta — cinco tools | alta | 2026-08-18 |
| [002](archive/002-is-recurring-dropped-when-splitting.md) | `is_recurring` descartado quando `installments > 1`, em silêncio | média | 2026-08-18 |
| [003](archive/003-json-null-read-as-the-string-null.md) | Um `null` JSON explícito é lido como a string `"null"` | média | 2026-08-18 |
| [004](archive/004-transaction-form-drops-arguments-silently.md) | Categoria incompatível e `installments` fora do cartão descartados em silêncio | média | 2026-08-18 |

## O que decide a faixa

A criticidade é o dano que o achado causa quando acontece, ponderado pelo caminho até ele — não o
tamanho da correção.

- **CRÍTICO** — corrompe dados ou derruba o app por um caminho que o usuário percorre sem pedir.
- **ALTO** — grava no ledger algo diferente do que foi pedido, e a resposta diz que deu certo. Um
  número errado que ninguém tem como notar.
- **MÉDIO** — a resposta ou a tela engana sobre o que aconteceu, ou o custo cresce com o histórico,
  mas os dados no ledger estão certos.
- **BAIXO** — incômodo, inconsistência latente ou código que só atrapalha quem for ler.

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
- **uma** é deliberada e está registrada como trade-off de criticidade baixa — 012, cujo comentário
  em `main.kt` reconhece a espera;
- **uma** foi rebaixada de defeito a smell de robustez — 014, que não é alcançável por nenhum caminho
  de escrita atual.

Duas afirmações da revisão original foram corrigidas durante a verificação e não se repetem aqui: a
de que a resposta de `create_transaction` reporta o lançamento invertido pelo campo `direction` (ele é
omitido sem perspectiva; quem diz "expense" é o `nature`), e a de que as leituras por linha do log de
atividade rodam na main thread (as funções suspend de DAO do Room saltam para o dispatcher do banco —
o que fica na main é o coletor e uma retomada por linha).

## Reconferência

Reconferidos contra a árvore em `32310927a` em 2026-08-18, depois de o relatório dos quinze achados
ser publicado no PR #19. Nada mudou de veredito e nada mudou de faixa; três arquivos ficaram aquém do
que o código mostra, e dois foram reescritos:

- **[001](archive/001-create-transaction-accepts-negative-amount.md) — o achado é maior do que estava
  registrado.** São **cinco** tools que levam um valor negativo ao ledger, não uma: somam-se
  `create_installment`, `create_recurring`, `update_recurring` e — a mais curta de todas, sem
  formulário nem validador no caminho — `confirm_recurring`. E a "Correção sugerida" original errava
  os dois lados: dava `create_card` e `adjust_invoice` como desprotegidos (o primeiro **é** protegido,
  o segundo posta a diferença e aceita um alvo negativo com razão), enquanto `update_card`,
  `create_budget` e `update_budget` — que gravam um negativo fora do ledger — não eram mencionados. O
  arquivo agora traz o mapa da superfície inteira, em quatro grupos. *(A correção mostrou que a
  reconferência também errou aqui: `update_card` **é** protegido — pelo `init` de `CreditCard`, não
  pelo use case que ela leu. Está registrado no arquivo arquivado.)*
- **[009](009-last-day-of-a-month-reads-as-finished.md) — a divergência é entre duas KDocs, não entre
  código e KDoc.** A da factory (`AgentPeriod.kt:38`) é a que o cálculo contradiz; a da propriedade
  (`:29`) descreve o cálculo corretamente. Corrigir só a expressão troca qual das duas mente. A
  correção sugerida agora move as duas.
- **[007](007-agent-log-runs-three-queries-per-row.md) — nada a mudar.** As três queries por linha já
  estavam registradas aqui; quem ficou aquém foi o comentário do PR, que fala em uma leitura por
  linha.

Método: cada afirmação foi lida nos arquivos e linhas citados; o `bind` em porta privilegiada
([011](011-privileged-ports-are-offered-and-misdiagnosed.md)) foi sondado de novo com a JVM desta
máquina, e confirma `BindException`. Nada foi executado contra o app rodando, então
[007](007-agent-log-runs-three-queries-per-row.md) e
[012](012-closing-the-desktop-window-blocks-the-event-thread.md) seguem sendo raciocínio sobre o
código, não medição.

## Relacionado

`docs/auditoria-bugs-2026-07.md` — a auditoria de julho/2026 sobre a `main`, de onde vem o formato do
sumário por criticidade. Os itens 16, 21, 22 e 23 de lá se sobrepõem a 001, 007 e 008 daqui, e cada
arquivo nomeia a sobreposição.
