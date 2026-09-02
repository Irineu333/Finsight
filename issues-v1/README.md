# Issues

Achados em aberto, um arquivo por issue, nomeados `NNN-slug.md`. O número é um id estável; a
criticidade é a ordenação.

Cada arquivo diz o que está errado, a evidência com arquivo e linha, o cenário de falha e uma
correção sugerida. Onde a verificação corrigiu ou enfraqueceu o achado, a correção está no próprio
arquivo em vez de ter sido descartada.

## Sumário por criticidade

Nenhum achado chegou a **CRÍTICO**, e **ALTO** voltou a ficar vazia com a correção da
[025](archive/025-confirm-recurring-writes-the-wrong-direction.md).

A regra "uma categoria classifica uma direção só" foi fechada em seis pontos, em quatro rodadas —
[004](archive/004-transaction-form-drops-arguments-silently.md),
[016](archive/016-update-transaction-drops-the-category-silently.md),
[020](archive/020-create-installment-drops-the-category-silently.md),
[021](archive/021-update-recurring-stores-an-incoherent-template.md),
[025](archive/025-confirm-recurring-writes-the-wrong-direction.md) — e as três primeiras rodadas
foram apresentadas como a última. **Não eram.** As cinco primeiras são as tools que montam um
formulário, e esse recorte é exatamente o que escondeu a sexta: `confirm_recurring` não monta
formulário nenhum, e é a única escrita da superfície que chega ao razão sem um. Fechar as cinco e
declarar a família encerrada foi o que fez o achado demorar quatro rodadas a aparecer.

A quarta rodada foi encerrada de outro jeito, e é a razão de a lição estar escrita aqui: em vez de
reler a lista de ocorrências conhecidas, foram enumerados **todos** os pontos que montam uma
contra-perna e verificado, um a um, o que segura a direção de cada um. São três os que carregam
categoria; dois já estavam fechados. O mapa está no fim da
[025](archive/025-confirm-recurring-writes-the-wrong-direction.md), para que a próxima dúvida tenha
o que reconferir em vez de uma afirmação.

A lição, então: quando um achado é de uma classe, o que fecha a classe é procurar onde mais ela vive
— não corrigir a ocorrência e reler a lista de ocorrências conhecidas.

| # | Issue | Área | Tipo |
|---|---|---|---|
| **MÉDIO** |
| [026](026-incoherent-templates-already-stored-have-no-migration.md) | Templates incoerentes já gravados não têm migração, e três telas discordam sobre eles | recurring / database | dados |
| **BAIXO** |
| [027](027-update-recurring-cannot-remove-a-title.md) | `update_recurring` não consegue apagar um título, e a tool vizinha documenta o contrário | mcp | correção |
| [028](028-architecture-tests-scan-worktrees.md) | Os testes de arquitetura varrem worktrees dentro do repo e acusam o código delas | build / testes | infraestrutura |
| [029](029-recurring-errors-never-reach-the-user.md) | Nenhuma mensagem de `RecurringError` chega à tela: a sheet mostra uma genérica | recurring (UI) | UX |
| [022](022-category-id-zero-means-two-things.md) | Nas criações, `category_id: 0` não é uma forma de falar, e isso não está escrito | mcp | consistência |
| [023](023-a-refused-plan-still-leaves-an-invoice-behind.md) | Uma parcela bloqueada no meio do plano deixa a primeira fatura para trás | creditcards | dados |
| [024](024-update-transaction-still-discards-two-arguments.md) | A edição ainda descarta `invoice_month` e `title` vazio, e recusa o cartão carregado como se fosse dado | mcp | correção |
| [018](018-read-by-identity-does-not-dedupe.md) | `readByIdentity` não deduplica, e sua KDoc afirma que sim | ledger | robustez (latente) |
| [019](019-transactions-has-no-index-on-date.md) | A leitura por mês varre a tabela inteira: sem índice em `date` | ledger | performance |
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
| [005](archive/005-connection-snippet-shows-the-token-in-clear-text.md) | O snippet de conexão mostra o token em texto claro | média | 2026-08-18 |
| [007](archive/007-agent-log-runs-three-queries-per-row.md) | O log completo do agente faz três queries por linha, por emissão | média | 2026-08-18 |
| [009](archive/009-last-day-of-a-month-reads-as-finished.md) | O último dia do mês é lido como encerrado | média | 2026-08-18 |
| [010](archive/010-cannot-reapply-the-configured-port.md) | A porta configurada não pode ser reaplicada depois de um bind falho | média | 2026-08-18 |
| [008](archive/008-list-transactions-loads-the-whole-table.md) | `list_transactions` carrega a tabela inteira a cada página | média | 2026-08-18 |
| [017](archive/017-installment-opens-invoices-before-refusing.md) | `create_installment` abre até doze faturas e só então recusa o valor | média | 2026-08-18 |
| [016](archive/016-update-transaction-drops-the-category-silently.md) | `update_transaction` descarta a categoria em silêncio, e recusa uma receita em cartão pelo argumento errado | média | 2026-08-18 |
| [020](archive/020-create-installment-drops-the-category-silently.md) | `create_installment` descarta a categoria em silêncio, e responde "Recorded" | média | 2026-08-19 |
| [021](archive/021-update-recurring-stores-an-incoherent-template.md) | `update_recurring` e `create_recurring` gravam um template incoerente | média | 2026-08-19 |
| [025](archive/025-confirm-recurring-writes-the-wrong-direction.md) | `confirm_recurring` grava no razão um lançamento na direção oposta à que o dinheiro andou | **alta** | 2026-08-19 |

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
- **duas** procedem e são piores do que o relatado, cada uma por um motivo diferente — na 007, a
  leitura por linha se revelou **três** queries em vez de uma; na 008, o custo por página é
  `1 + 1 + N` — uma query de entries por linha, sobre o ledger inteiro;
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
- **[009](archive/009-last-day-of-a-month-reads-as-finished.md) — a divergência é entre duas KDocs, não entre
  código e KDoc.** A da factory (`AgentPeriod.kt:38`) é a que o cálculo contradiz; a da propriedade
  (`:29`) descreve o cálculo corretamente. Corrigir só a expressão troca qual das duas mente. A
  correção sugerida agora move as duas.
- **[007](archive/007-agent-log-runs-three-queries-per-row.md) — nada a mudar.** As três queries por linha já
  estavam registradas aqui; quem ficou aquém foi o comentário do PR, que fala em uma leitura por
  linha.

Método: cada afirmação foi lida nos arquivos e linhas citados; o `bind` em porta privilegiada
([011](011-privileged-ports-are-offered-and-misdiagnosed.md)) foi sondado de novo com a JVM desta
máquina, e confirma `BindException`. Nada foi executado contra o app rodando, então
[007](archive/007-agent-log-runs-three-queries-per-row.md) e
[012](012-closing-the-desktop-window-blocks-the-event-thread.md) seguem sendo raciocínio sobre o
código, não medição.

## Relacionado

`docs/auditoria-bugs-2026-07.md` — a auditoria de julho/2026 sobre a `main`, de onde vem o formato do
sumário por criticidade. Os itens 16, 21, 22 e 23 de lá se sobrepõem a 001, 007 e 008 daqui, e cada
arquivo nomeia a sobreposição.
