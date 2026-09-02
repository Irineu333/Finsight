---
area: recurring
severity: medium
type: ux
version: 1.10.0
verdict: fixed
---

# A linha da recorrência troca o nome da origem arquivada por "Origem indisponível"

## Cenário

**DADO** duas recorrências chamadas "Aluguel", uma no Banco A e outra no Banco B, e as duas
contas arquivadas
**QUANDO** o usuário abre a tela de Recorrentes
**ENTÃO** as duas linhas leem "Aluguel" e "Origem indisponível", indistinguíveis uma da outra
**DEVERIA** nomear a origem de cada uma — a conta arquivada existe e tem nome —, mantendo o
glifo e o tom que afirmam que ela não posta

## Mecânica

`SourceLine()` escolhe o texto por `Recurring.hasUsableSource`, que é falso tanto para a
origem **removida** quanto para a **arquivada**. No ramo falso o nome não chega a ser lido:
`creditCard.name` e `account.name` só são alcançados no ramo verdadeiro.

A distinção existe no modelo e é ignorada aqui. Removida é `account == null` — a foreign key
é `SET_NULL` —, e aí não há nome a exibir; arquivada é `account.isArchived`, e aí há. O
detalhe faz a distinção certa: `ViewRecurringModal` mantém o nome da conta ou do cartão
arquivado e retira apenas o atalho de navegação até ele.

## Evidência

- `feature/recurring/impl/.../screen/recurring/RecurringScreen.kt` — `SourceLine()`, o ramo
  `if (!recurring.hasUsableSource)`
- `core/model/.../model/Recurring.kt` — `hasUsableSource`, verdadeiro só quando alguma origem
  existe **e** não está arquivada
- `feature/recurring/impl/.../viewRecurring/ViewRecurringModal.kt` — os `DetailRow` de conta e
  de cartão, que preservam o nome e anulam só o `onClick`
- `openspec/changes/redesign-recurring-screen/specs/recurring-list-row/spec.md` — cenário
  "Dois templates de mesmo nome", que a linha deixa de satisfazer
- `git show 459ba54eb:…/RecurringScreen.kt` — o comportamento anterior, em que o nome
  permanecia e só o tom mudava para `colorScheme.outline`

## Consequência

Duas recorrências de mesmo rótulo e origens arquivadas diferentes ficam indistinguíveis na
listagem — que é o requisito para o qual a linha existe. Perde-se informação que a tela já
exibia, e o arquivamento, que o app oferece como reversível, passa a apagar o nome do vínculo
em vez de apagar apenas o caminho até ele.

## Sugestão

Separar os dois casos dentro do ramo falso: havendo origem, exibir o nome dela com o glifo e o
tom `Warning` que já afirmam o estado; não havendo, manter `recurring_source_unusable`. Não
vinculante.

## Desfecho

**Causa real** — a do relato, confirmada no código: `SourceLine()` decide o texto por
`hasUsableSource`, e o ramo falso nunca alcançava um nome. O que faltava não era um caso a
mais, era a distinção entre as duas ausências que aquele booleano funde. A removida é `null`
dos dois lados — a foreign key é `SET_NULL` — e de fato não tem nome a dar; a arquivada
existe, é nomeada, e o arquivamento é oferecido ao usuário como reversível: pode tirar o
caminho até a conta, nunca o nome dela.

**Mudança** — nasceu `Recurring.sourceName()`, dona da pergunta "como esta origem se chama",
e o ramo falso passou a lê-la: havendo nome, é ele que aparece; não havendo,
`recurring_source_unusable` fala. A frase deixou de ser a resposta do ramo e virou o último
recurso. O glifo `LinkOff` e o tom `Warning` não mudaram — eles é que afirmam *não posta*, e
continuam afirmando isso nos dois casos, de modo que nada aqui passou a depender de cor.

**Prova** — `SourceNameTest` fixa os cinco casos: conta arquivada e cartão arquivado seguem
nomeados, a origem removida devolve `null`, duas "Aluguel" em bancos arquivados diferentes
respondem nomes diferentes (o cenário do spec que a linha deixava de satisfazer), e o cartão
responde antes da conta. Suíte: `./gradlew jvmTest --rerun-tasks` verde, 1488 testes em 249
classes, nenhuma falha.

**O que continua fora** — o caso em que o **cartão existe e a conta dele não**: ali
`hasUsableSource` é verdadeiro, a linha desenha o nome do cartão com glifo e tom normais, e a
figura mesmo assim sai `***`, sem que nada explique. É outro defeito, de outra origem, e não
foi tocado aqui.
