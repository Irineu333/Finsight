---
area: mcp
severity: low
type: ux
---

# Só o código sabe que uma criação não aceita `category_id` 0

## Invariante

O que a superfície do agente aceita está escrito onde o agente lê.

Hoje é falso para o `0` de `category_id`: três tools o leem como *sem categoria* e três respondem
que a categoria 0 não existe, e a regra que separa as duas metades vive apenas numa KDoc de
`WriteSupport` — não nas descrições das tools, não no schema dos argumentos, não em
`docs/mcp-tool-surface.md`.

## Mecânica

A assimetria tem defesa, e a KDoc de `NO_CATEGORY` a enuncia: as três tools que **carregam** o que
a chamada não nomeia — `update_transaction`, `update_recurring`, `confirm_recurring` — precisam de
uma forma de dizer *nenhuma*, porque ausência ali já significa *mantenha*. Numa criação, ausência
já significa nenhuma, então o `0` não precisa existir e fica sendo uma identidade que não casa com
nada.

O que falta é o alcance dessa explicação. Ela está num `internal const` que o agente não lê,
enquanto as recusas que ele lê apontam para saídas diferentes sem dizer por quê: as três criações
dizem *"or leave `category_id` out"*, as edições dizem *"or `category_id` 0"*. Cada uma está certa;
lidas em sequência, ensinam duas regras e nenhuma condição.

## Evidência

- `NO_CATEGORY` (`mcp/tool/WriteSupport.kt`) — `= 0L`, e a KDoc que declara a regra inteira,
  incluindo "A creation has no such need"
- `TransactionWriteTools`, `RecurringWriteTools`, `RecurringOperationTools` — `?.takeIf { it !=
  NO_CATEGORY }`, as três leituras
- `ICategoryRepository.require()` (`WriteSupport.kt`) + `AgentRefusal` — `reason = "No $kind with
  id $id exists."`, a resposta que uma criação dá ao `0`
- as cinco recusas de categoria incompatível — `"…or leave \`category_id\` out."` nas criações,
  `"…or \`category_id\` 0…"` nas edições
- `docs/mcp-tool-surface.md` — não menciona o `0` em lugar nenhum

## Consequência

Um agente aprende o `0` numa edição e o leva para uma criação, onde recebe uma resposta sobre a
categoria — não sobre a tool — da qual não dá para deduzir que ali o `0` não é uma forma de falar.
O dano é pequeno: omitir `category_id` faz o que ele queria. O custo é o round trip e a regra que
ele não tem como inferir.

## Sugestão

Escrever a regra onde o agente lê — na descrição das tools de criação, ou em
`docs/mcp-tool-surface.md`. A decisão de superfície (aceitar ou não o `0` nas criações como
sinônimo de ausência) continua aberta e é secundária: hoje a resposta é não, e o defeito é ela não
estar em lugar nenhum que o chamador alcance. Não vinculante.
