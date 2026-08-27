## Context

`RecurringScreen` é a única lista do app que não sabe responder à pergunta que leva o usuário
até ela. Ela lista templates ordenados por `createdAt` (`RecurringViewModel.kt:135`), e o único
lugar do app em que a palavra "pendente" aparece é o card do dashboard — de onde parte o "ver
todas" que traz o usuário para cá.

A change anterior (`redesign-recurring-screen`) desenhou o card do mês e declarou dois
non-goals que esta change reabre, ambos deliberadamente:

> - Tornar a **lista** mensal. Só o card tem mês.
> - Oferecer confirmar ou ignorar na linha da lista. Fica para a change seguinte.

O primeiro é reaberto aqui. O segundo continua fechado.

**Os quatro estados já são deriváveis do que existe.** `Recurring.generatesCycleIn(month)` diz
quais templates têm ciclo no mês; `GetUnhandledRecurringUseCase` diz quais não têm nada
registrado; `RecurringOccurrence.status` diz se o registro foi confirmação ou salto. Nada
precisa ser persistido, e o `combine` da tela já observa as ocorrências — só não as usa para a
lista.

**O que o template não sabe é o que foi lançado.** `ConfirmRecurringUseCase` aceita `amount`,
`target`, `title` e `category` sobrescritos por ciclo, e a doc de
`IRecurringOccurrenceRepository.settledIn` já registra a consequência: somar templates
confirmados produziria "um número que nunca existiu". Hoje isso é inofensivo, porque a linha da
lista não afirma nada sobre um mês. Numa seção chamada "lançado", passa a afirmar.

## Goals / Non-Goals

**Goals:**

- Dar hierarquia à lista, respondendo *o que falta lançar neste mês* sem abrir nada.
- Dar à partição um dono único no domínio, antes que "pendente" ganhe um segundo predicado.
- Corrigir o corte que separa pendente de a-lançar, que hoje só é correto no mês corrente.
- Fazer a seção de lançados dizer o que o ledger registrou, e não o que o template previa.
- Manter as arquivadas alcançáveis e reversíveis depois que elas saem da lista.

**Non-Goals:**

- Confirmar ou ignorar a partir da linha. Ver o débito em Riscos.
- Trazer ciclos vencidos de meses anteriores para o mês corrente
  (`a-recurring-left-unconfirmed-vanishes-when-the-month-turns`). A navegação por mês passa a
  dar **visibilidade** ao backlog; o backlog em si continua sem existir como conjunto.
- Fazer a janela `daysAhead` do dashboard atravessar a virada do mês
  (`the-upcoming-recurring-window-stops-at-the-end-of-the-month`). Ver D3.
- Mudar o desenho do card do mês além da remoção do rodapé.
- Promover a linha de recorrência para `core/ui`.

## Decisions

### D1 — A lista muda de objeto, e a premissa antiga continua verdadeira

O requisito vigente diz, textualmente: *"A lista MUST NOT passar a ser recortada por mês. Um
template não tem mês; apenas a ocorrência dele tem."*

A segunda frase continua verdadeira e **é o motivo** da mudança, não a vítima dela. O que muda
é o objeto da lista: ela deixa de listar templates e passa a listar os **ciclos** deles no mês
selecionado. Um ciclo tem mês por definição, e é dele que os quatro estados são propriedade.

Isso importa para a redação da delta: dizer "agora a lista tem mês" leria como uma regra
revogada por conveniência. O que aconteceu é que a lista passou a listar outra coisa.

Consequência direta: o seletor de mês do card governa a tela inteira. A separação que o
requisito protegia — *o card tem governo próprio e a lista tem o dela* — deixa de existir
porque as duas passam a responder pela mesma pergunta, sobre o mesmo mês.

### D2 — A partição tem um dono, e `GetPendingRecurringUseCase` passa a derivar dele

Um `GetRecurringCyclesUseCase(month, today)` novo em `feature/recurring/api`, construído sobre
`GetUnhandledRecurringUseCase`, devolvendo os quatro grupos.

A alternativa — a tela particionar no view model — cria um segundo predicado de "pendente", com
uma definição diferente da que o dashboard usa (D3 muda uma e não a outra). É exatamente o que
`GetUnhandledRecurringUseCase` já se organizou para impedir: *"nenhuma das metades ganha um
predicado próprio para discordar do outro"*. E é a derivation rule do projeto: uma regra
derivável do domínio tem um dono, no domínio.

`GetPendingRecurringUseCase` sobrevive como **pergunta feita a esse dono**, não como
predicado próprio — o dashboard continua consumindo a mesma interface e herda a correção.

`GetRecurringMonthOverviewUseCase` passa a **consumir** a partição em vez de recalcular
`handled`, `total` e `skipped`, que hoje ele refaz com `filter { generatesCycleIn }`,
`unhandled.size` e `count { SKIPPED }`. É menos código do que hoje, não mais.

### D3 — O corte é entre datas, não entre dias do mês

O predicado atual compara dia com dia:

```kotlin
// GetPendingRecurringUseCase, hoje
today.yearMonth.effectiveDay(recurring.dayOfMonth) <= today.day
```

Ele só é correto porque o mês é sempre o corrente. Com um mês selecionável, `effectiveDay`
passa a ser resolvido sobre um mês e comparado com o dia de outro. A forma que funciona em
qualquer mês já existe em `core/common` (`YearMonth.kt:8`):

```kotlin
val cycleDate = month.safeOnDay(recurring.dayOfMonth)
// pendente ⟺ cycleDate <= today ; a lançar ⟺ cycleDate > today
```

Um predicado só, sem caso especial por mês, e a degeneração cai fora sozinha:

| mês selecionado | pendente | a lançar |
|---|---|---|
| passado | todo unhandled | vazio |
| corrente | vencidos | por vencer |
| futuro | vazio | todo unhandled |

**Esta é literalmente a correção que `the-upcoming-recurring-window-stops-at-the-end-of-the-month`
pede** ("comparar datas em vez de dias"). A issue **não fecha** com esta change: a janela
`daysAhead` do dashboard olha para frente atravessando a virada, e uma partição de *um* mês não
responde por dois. O que fica pronto é a peça; o consumo dela é de outra change.

### D4 — Seções, não abas, e sem sticky header

Abas mostram um grupo por vez e exigem contagem no rótulo para não esconder o que não está
selecionado — que é o problema de origem em outra forma. Seções mostram os quatro de uma
rolagem, que é o que "falta de organização" pede.

Sem `stickyHeader`: com quatro grupos numa lista frequentemente curta, um cabeçalho grudado no
topo ocupa altura permanente para nomear um grupo que cabe inteiro na tela. O card do mês já é
item da `LazyColumn` e rola para fora (D11 da change anterior); os cabeçalhos seguem a mesma
gramática.

### D5 — A ordem é pendente, a lançar, lançado, ignorado

Não é cronológica nem alfabética: é por **quanto cada grupo pede do usuário**. Pendente é o que
está vencido e não resolvido; a lançar é o que vem; lançado e ignorado são passado, e passado
não disputa o topo da tela.

Um grupo sem itens não é renderizado — cabeçalho e contador incluídos. Uma seção vazia é o card
do mês afirmando ausência de novo, com menos precisão do que ele já afirma.

### D6 — Dentro da seção, a ordem é a data do ciclo, crescente

Trocar `sortedBy { createdAt }` era non-goal da change anterior, e deixa de ser escolha aqui:
sob um cabeçalho "pendente", ordem de criação não diz nada — o que ordena um pendente é há
quanto tempo venceu. Crescente serve às duas seções que importam: em pendente, o mais atrasado
primeiro; em a lançar, o mais próximo primeiro. É uma regra só.

### D7 — A linha de um ciclo lançado vem do ledger, inteira

Valor, título e categoria da transação — não do template. A justificativa é a que o usuário deu
e a que `settledIn` já documenta: uma vez lançado, a transação é o fato, e o template é apenas
o que a previa.

A implementação **não** pede componente novo. `core/ui` já tem a linha de transação e o mapper
que a preenche, e `feature/creditcards` já os usa em três telas:

```
Transaction ──toTransactionUi(lookup)──▶ TransactionUi ──▶ TransactionCard
                                          │
                                          └── title = displayTitleOrNull(title, category)
                                                       ▲
                                          Recurring.label usa a MESMA função
```

Que a nomeação seja a mesma função nos dois lados é o que impede a tela de ganhar dois
vocabulários de identidade: é uma regra só, aplicada a duas fontes.

**Alternativa considerada e recusada:** manter `RecurringCard` na seção de lançados, lendo só o
valor do ledger. Ela evita a heterogeneidade visual (ver Riscos) ao preço de uma linha que diz
o nome de um lugar e o número de outro — a divergência mais difícil de perceber das duas.

**Consequência que corrige um caso existente:** um ciclo lançado tem figura mesmo quando o
template perdeu a conta. O dinheiro saiu, está no ledger, e tem moeda. A marca de valor
irresolvível — hoje exigida pela spec sempre que a denominação falha — passaria a mentir ali.
Ver D9.

### D8 — A leitura das transações é por ids, e não por id nem pelo ledger inteiro

`ITransactionRepository` (`core/ledger`) tem `getTransactionById` — que numa lista é o N+1 que
a change anterior gastou uma decisão inteira (D10) para eliminar — e `observeAllTransactions`,
que relê o ledger inteiro e tem issue aberta por isso. Falta a leitura por conjunto de ids, e
ela entra: aditiva, sem migração, e útil a qualquer lista que parta de ids.

**Alternativa considerada:** estender o `GROUP BY` da query agregada de
`RecurringOccurrenceDao` (`e.currency` → `o.recurringId, e.currency`), que devolveria a mesma
leitura discriminada por ciclo em uma consulta só. Ela é mais barata, mas devolve **apenas
valor e moeda** — e D7 pediu título e categoria também. Se D7 fosse revertido para "só o
valor", esta é a implementação certa, e vale registrar que ela custa um `GROUP BY`.

### D9 — A marca de valor irresolvível vale nas seções sem fato, e só nelas

```
PENDENTE  ┐ template  → sem conta ⇒ marca de irresolvível  (não há fato)
A LANÇAR  ┘
LANÇADO     ledger    → sempre tem figura e moeda
IGNORADO    template  → ver D10
```

A justificativa do requisito atual — *"omitir a figura diz 'não há número' por ausência, o que
numa lista densa é invisível"* — continua inteira nas seções de template. Na seção de fato ela
não se aplica, porque não há ausência a declarar.

### D10 — A seção ignorada exibe o valor do template, sem tratamento visual próprio

É a linha que não tem fato (nenhum entry) nem promessa (o mês já foi resolvido). Ainda assim
exibe o valor do template, que é o único número que existe e responde *quanto você deixou de
lançar*.

Sem esmaecer, riscar ou marcar de qualquer outra forma: `recurring-list-row` já proíbe a linha
de carregar legenda que nomeie a natureza do número que exibe, e **o cabeçalho da seção é essa
legenda**, dita uma vez para o grupo inteiro. Se cada linha precisasse de marca própria para
afirmar seu estado, as seções não estariam fazendo o trabalho delas.

Omitir a figura foi considerado e recusado pela regra vizinha: faria a linha mudar de altura
sem que nada explicasse a diferença.

**Este é o ponto mais barato de reverter da change inteira** — é uma escolha de renderização
numa seção, sem consequência sobre domínio, consulta ou spec de outra capability.

### D11 — Arquivadas vão para uma rota própria, não para um modo da mesma tela

`generatesCycleIn` é `false` para toda arquivada, em qualquer mês: ela não tem ciclo, logo não
tem estado de ciclo, logo não cabe em seção nenhuma. E precisa continuar alcançável — é o único
caminho para `UnarchiveRecurringModal`, e `account-lifecycle` exige que arquivar seja
reversível.

Rota interna ao `impl` (o `api` declara só o que é navegável de fora), lista plana, sem card e
sem mês, com back.

**Alternativa considerada e recusada:** o seletor alternar o modo da tela. Ela faz um mesmo
controle ora recortar a lista, ora remodelar a tela — que é a crítica que a spec vigente já faz
ao card: *"mudando de forma enquanto a lista muda de conteúdo, sem que o usuário possa saber
qual das duas coisas o seletor fez"*.

### D12 — O seletor perde `ARCHIVED` e volta a ter um eixo só

`RecurringFilter` documenta hoje que mistura dois eixos "de propósito". Com as arquivadas fora,
sobra a natureza: todas / despesas / receitas, transversal às quatro seções. E `ACTIVE` vira
`ALL`, porque numa lista mensal o que aparece já é não-arquivado por construção — manter o nome
sugeriria um recorte que não existe mais.

### D13 — O card perde o rodapé, e a linha de templates sem conta fica

```
antes                            depois
Lançado    ↓ 1.240  ↑ 3.000      Lançado    ↓ 1.240  ↑ 3.000
Previsto   ↓   890  ↑     0      Previsto   ↓   890  ↑     0
──────────────────────────       2 templates sem conta
4 de 7 tratados · 1 ignorado ✂
2 templates sem conta            (o divisor sai com o contador)
```

`handled = |lançado| + |ignorado|`, `total` = soma das quatro, `skipped = |ignorado|`. Os três
viram eco do que está logo abaixo, dito em números em vez de em grupos.

Isso resolve uma questão aberta que a change anterior registrou sem decidir: *"Variante completa
(~291dp) ou compacta (~246dp)? A compacta dispensa o rodapé. Decidir com o card na mão."* As
seções são o argumento que faltava.

`undenominated` **fica**: nenhuma seção o conta, e ele fala de uma falha — template apontando
para conta que não existe mais — cuja saída é apontar o template para outro lugar.

**A representabilidade do ciclo ignorado muda de dono, e a delta precisa dizer isso.** O
requisito atual justifica o contador afirmando ser *"o único lugar do resumo em que um ciclo
ignorado é representável"*. A seção cumpre isso melhor — nomeia quais, não só quantos. Sem
dizê-lo, a remoção lê como perda.

### D14 — O mês sem ciclo algum é um item da lista, não um ramo da tela

Um mês anterior ao `originMonth` de todos os templates não tem ciclo nenhum, e nenhuma das
quatro seções renderiza. O vazio entra como item da `LazyColumn`, abaixo do card, pela mesma
razão que o vazio de recorte já entra (D11 da change anterior): é justamente quando o card mais
tem a dizer, e um ramo que ocupa a tela o apagaria.

O vazio de **base** — nenhum template no banco — continua tomando a tela inteira, inalterado.

## Risks / Trade-offs

- **Duas linhas diferentes na mesma lista** (`RecurringCard` nas seções de template,
  `TransactionCard` na de fato) → é a intenção, não um efeito colateral: são duas classes de
  coisa, e o card do mês já separa fato de previsão com a mesma gramática. O risco real é a
  seção de lançados parecer um pedaço de outra tela; mitigação é o cabeçalho da seção, que a
  nomeia.
- **A identidade de um ciclo lançado pode divergir da do template** — "Aluguel" confirmado como
  "Aluguel + condomínio" aparece com o outro nome, e a mesma recorrência lê diferente conforme
  o mês → é o preço aceito de D7, tomado com a alternativa na mesa. A mitigação existente é que
  os dois nomes saem da mesma função (`displayTitleOrNull`), então a divergência é de dado, não
  de regra.
- **A seção "pendente" não tem porta** → débito deliberado desta change, herdado da anterior. A
  tela passa a dizer o que falta melhor do que qualquer lugar do app e continua sendo a única
  que não deixa agir sobre isso; o caminho segue sendo o card do dashboard, que o usuário pode
  desligar. Registrado para ser a change seguinte.
- **O `combine` já tem cinco fontes e ganha uma leitura de transações** → a leitura é por
  conjunto de ids (D8), uma consulta por emissão, e substitui parte do trabalho que
  `GetRecurringMonthOverviewUseCase` fazia (D2). Ainda assim, é uma fonte a mais num fluxo que
  reemite muito, e vale medir antes de fechar.
- **Um mês futuro exibe só "a lançar"** → correto e esperado: `confirmableDates` colapsa a
  janela de confirmação no primeiro dia de um mês que não chegou, e nenhuma superfície oferece
  confirmar ali. A seção "ignorado" também tende a ficar vazia lá, mas **por ausência de
  superfície, não por regra**: `SkipRecurringUseCase` só exige que a série tenha começado. A
  lista não deve assumir que ela é vazia — renderiza se houver.
- **Nenhuma altura desta change foi medida** → as seções acrescentam cabeçalhos a uma lista que
  já tinha o card no topo; a densidade da primeira tela cai antes de a rolagem começar.
  Conferir com a tela na mão, especialmente no painel estreito da janela larga.
- **Os fluxos Maestro afirmam `recurring_card_amount` em cinco pontos** → a tag continua no
  `Text` da figura nas seções de template; na seção de lançados a linha é outro componente, com
  outra tag. Conferir quais dos cinco pontos caem em qual seção antes de mexer.

## Migration Plan

Sem migração de banco. Nenhuma entidade muda de forma, e a única leitura nova sobre o schema é
por ids em `transactions`, uma tabela e uma chave que já existem.

A FK `CASCADE` de `recurring_occurrences.transactionId` está no schema v14 exportado
(`core/database/schemas/com.neoutils.finsight.database.AppDatabase/14.json`) — reconfirmada aqui,
tendo sido a resposta que o arquivamento de `redesign-recurring-screen` já havia dado à questão
aberta que aquele design carregava. Esta change **depende** dela: não existe ciclo confirmado
apontando para transação removida, então a leitura da seção de lançados nunca fica sem linha.
Apagar a transação apaga a ocorrência, o ciclo volta a ser unhandled, e a linha reaparece em
"pendente" sozinha.

## Open Questions

- **A rota do arquivo entra pelo overflow da top bar, por um ícone próprio, ou pelo fim da
  lista?** Decidir com a top bar na mão — ela já carrega o seletor de natureza.
- **A seção de lançados mostra a data real da transação ou o dia do ciclo?** `TransactionCard`
  mostra a data; as outras seções dizem "dia 12". Provavelmente a data, por coerência com D7,
  mas isso é escolha do componente e não foi verificada contra o layout.
- **Quantos dos cinco pontos Maestro que afirmam `recurring_card_amount` caem na seção de
  lançados?** Precisa ser levantado antes de escrever as tasks de fluxo, não depois.
