---
area: mcp
severity: low
type: ux
verdict: fixed
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

## Desfecho

**Causa real** — a descrita. `NO_CATEGORY = 0L` e a KDoc inteira estão em `WriteSupport.kt:200-211`,
incluindo *"A creation has no such need"*; as três leituras estão em `TransactionWriteTools.kt:515`,
`RecurringWriteTools.kt:278` e `RecurringOperationTools.kt` (`confirm_recurring`); a resposta que
uma criação dá ao `0` é `ICategoryRepository.require()` (`WriteSupport.kt:272-273`) via
`AgentRefusal.notFound`, *"No category with id 0 exists."* Nenhuma das três descrições de criação
mencionava o `0`, e as três de edição mencionavam.

**Mudança** — dois lugares:

1. `WriteSupport.kt` — `NO_CATEGORY_ON_CREATION`, ao lado de `NO_CATEGORY` e com a KDoc que diz por
   que a frase existe. A regra continua com um dono só; o que muda é ela ter agora uma forma que
   atravessa a superfície em vez de morrer num `const` interno.
2. as três criações passam a interpolá-la na descrição do argumento `category_id` — que é onde o
   agente lê sobre `category_id`, e o espelho exato de onde as três edições já dizem *"pass 0 to
   leave it unclassified"*. A `description` da ferramenta não foi inflada: a condição é sobre um
   argumento, e é ao lado dele que ela se lê.

`docs/mcp-tool-surface.md` não foi tocado — é de outro dono nesta rodada. A decisão de superfície
que o registro deixa aberta (aceitar o `0` numa criação) continua aberta: nada aqui a decide, só a
publica como ela é hoje.

**Prova** — não há teste, e um aqui seria teatro: o defeito é uma descrição ausente, e assertar que
uma string contém outra string do mesmo arquivo não prova nada que a leitura não prove. A
verificação foi feita **pelo fio**, que é onde a descrição chega ao agente: um teste temporário
abriu uma sessão, chamou `tools/list` e imprimiu
`inputSchema.properties.category_id.description` das quatro ferramentas. Antes:

```
create_transaction  :: The category to classify it under, from list_categories.
create_recurring    :: The category each cycle is classified under, from list_categories.
create_installment  :: The category to classify it under, from list_categories. An expense
                       category — a split is always an expense, and an income one is refused.
update_transaction  :: … Keeps the one it has when not given; pass 0 to leave it unclassified.
```

Depois, as três criações terminam em *"Leave it out for no category: a creation carries nothing
over, so absence already says unclassified and `0` is an identity that matches nothing."*, e
`update_transaction` segue dizendo a metade dele. O teste temporário foi removido. Suíte do módulo
depois da mudança: 280 testes, 0 falhas (`./gradlew :feature:mcp:impl:jvmTest`).

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
