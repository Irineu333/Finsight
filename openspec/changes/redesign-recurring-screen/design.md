## Context

`RecurringScreen` é a única lista do app cujo item foi desenhado como ficha. O card mede
~180dp — 40dp de padding onde a casa usa 24, 32dp de vãos entre três blocos empilhados, 18dp
de legenda —, contra ~72dp de `CategoryCard` e `TransactionCard` e ~64dp dos cards analíticos.
Cabem 3 itens numa viewport de 640dp, e o conteúdo dos três blocos é exatamente o que
`ViewRecurringModal` já enuncia, rotulado e navegável.

A tela também não sabe o que é um mês. `GetUnhandledRecurringUseCase` e
`GetPendingRecurringUseCase` moram em `feature/recurring/api` desde a change de arquivamento e
são consumidos **apenas** pelo dashboard, num componente que o usuário pode desligar. Quem
chega aqui pelo "ver todas" da seção de pendências aterrissa numa tela em que a palavra
"pendente" não existe.

A metade do resumo que é **fato** não existe em lugar nenhum e não pode ser derivada do
template: `ConfirmRecurringUseCase.invoke` recebe `amount`, `account`, `creditCard`, `title` e
`category` sobrescrevíveis por ciclo, e o fluxo `recurring/lifecycle.yaml` exercita exatamente
isso — template de 940, confirmação de 865, razão com 865.

## Goals / Non-Goals

**Goals:**

- Levar a linha de ~180dp para ~64dp sem perder o que discrimina uma recorrência da seguinte.
- Dar à tela uma resposta sobre o mês: quanto das contas fixas já foi lançado, quanto ainda
  falta, e quantos ciclos foram tratados.
- Manter fato e projeção distinguíveis, e prestar contas do ciclo ignorado, que não é nenhum
  dos dois.
- Pagar as dívidas que este trabalho encostaria: `currencyOf` duplicado, `hasUsableSource`
  sinalizado só por cor, e a resolução de moeda item a item dentro do `combine`.

**Non-Goals:**

- Oferecer confirmar ou ignorar na linha da lista. Fica para a change seguinte, por decisão
  explícita: esta é sobre leitura.
- Tornar a **lista** mensal. Só o card tem mês.
- Trocar a ordenação da lista de data de criação para dia do ciclo.
- Reparar `BuildTransactionError.RecurringMonthLocked`, declarado sem produtor.
- Promover a linha para `core/ui`.

## Decisions

### D1 — A linha adota o módulo de 40dp/raio 8, e não o de 48dp/raio 12

O chip do ícone existe em dois módulos na casa: **48dp/raio 12** nas linhas de identidade
(`CategoryCard`, `TransactionCard`, e o `PendingRecurringCard` do dashboard) e **40dp/raio 8**
nos cards analíticos (`CategorySpendingCard`, `BudgetProgressCard`). A linha de recorrentes
adota o segundo.

Não é economia de 8dp — é filiação. A tela de recorrentes não é uma lista de coisas a
identificar; é uma lista de regras que o usuário mantém. Adotar o módulo analítico é o que faz
a linha **não** ser o card do dashboard reeditado, que era o pedido explícito. Além disso o
`PendingRecurringCard` responde a outra pergunta — *"confirma este ciclo?"* — e por isso não
tem dia nem origem: o dia dele é hoje.

Alternativa descartada: glifo nu de 24dp sem container, que daria 48dp de linha e 11 itens.
Toda linha de lista deste app tem container tingido; os 16dp são o preço de pertencer à casa.

### D2 — A linha é uma grade 2×2, não uma linha com subtítulo

Esquerda, dois níveis: **rótulo** e **origem**. Direita, dois níveis: **figura** e **dia**.

O que isso compra sobre a alternativa (dia e origem juntos na linha secundária) é que nome de
cartão é longo — "Nubank Ultravioleta", "Inter Gold" — e numa única linha secundária ele
disputa espaço com o dia e é truncado **depois** dele. Em colunas separadas o dia é sempre
integral. E o par (figura, dia) lido junto é a única coisa na tela que enuncia a **regra** — é
o que a recorrência *é*.

`weight(1f, fill = false)` vai no rótulo e nunca na figura, como `CategorySpendingCard` já
normatiza.

### D3 — O nome da categoria sai da linha; a ordenação fica

São duas mudanças de conteúdo possíveis e só uma entra.

O **nome da categoria** sai: ele só é exibido quando o template tem título *e* categoria,
sobrevive como cor e glifo no chip, e o detalhe o nomeia. É a única informação que se perde de
verdade — o resto do que se corta é redundância (a legenda, os dois badges) ou ênfase (28sp →
16sp).

A **ordenação** fica em data de criação. Ordenar por dia do ciclo faria a coluna ler como um
calendário e é a melhor tese sobre o que a tela é — mas é decisão de produto (um template
recém-criado deixaria de aparecer no fim da lista) e não entra de carona num redesenho visual.

### D4 — A direção vira glifo; a figura continua sem sinal

A tentação era trocar a política de magnitude por sinal forçado, deixando `−R$ 39,90` carregar
a natureza e eliminando o badge. `money-display` proíbe: *"Na superfície de item — o card de
uma transação numa lista — o valor SHALL ser exibido sem sinal para gasto, receita e pagamento
de fatura, cujos rótulos entregam a direção."* E o KDoc de `FORCED_POSITIVE` diz "a line that
always adds **to the sum above it**" — não há soma acima.

Então a direção vira `TrendingDown`/`TrendingUp` a 16dp com `contentDescription` nomeando a
natureza. Custa zero string nova — reusa `recurring_expense`/`recurring_income` — e satisfaz
duas vezes a regra de que cor sozinha não carrega estado: glifo para quem vê, palavra para
quem ouve.

O mesmo vale para o estado arquivado, que passa de badge com container a glifo antes do
rótulo, também com descrição. `account-lifecycle` não exige badge: exige que a arquivada suma
das listagens ativas e permaneça acessível pelo recorte dedicado — e como as duas nunca se
misturam na mesma tela, o badge nunca discriminava duas linhas.

### D5 — `***` no lugar da ausência da figura

Hoje o bloco do valor desaparece inteiro quando o template perdeu a conta, e a linha cai de
~180dp para ~113dp. A lista já tem duas alturas, e a ausência do número é dita por ausência —
invisível numa lista densa.

`UNRESOLVED_AMOUNT` foi escrito para isto: *"ocupa a largura de um valor e nada mais, de modo
que a superfície mantenha a sua forma… a alternativa a um número errado não é um layout
quebrado."* Com ele, o chip de 40dp governa a altura em **toda** variante, e `animateItem()`
passa a animar reordenação sem salto.

Registra-se a imprecisão aceita: a marca foi escrita para "não pôde ser resolvido" (falta de
taxa) e aqui o caso é "não há moeda que o denomine". São próximos, não idênticos — e a linha
afirma a causa junto, o que é o que fecha a diferença.

### D6 — O fato é lido pelo DAO da fachada, atravessando a foreign key real

`transactions.recurringId` é metadado de agrupamento e `TransactionEntity` declara que
*"nenhuma leitura do razão o consulta"*. A leitura do fato **não** o usa.

Ela parte de `recurring_occurrences`, que é tabela da fachada, e segue a foreign key real de
`transactionId` para `transactions`, `entries` e `accounts`, agrupando por tipo de conta
nominal e por moeda. `RecurringDao` já lê `transactions` assim nas consultas de guarda, com
KDoc explicando por que um DAO da fachada pode e um do razão não; `AppDatabase` enxerga as duas
tabelas, então o JOIN compila.

Consequência que se ganha de graça: `RecurringOccurrenceEntity` declara
`ForeignKey(transactionId, onDelete = CASCADE)` e índice único sobre a coluna. Apagar a
transação apaga a ocorrência, o template volta a ser não tratado, o dinheiro sai do fato e
volta para a projeção, e o contador recua — sozinho, sem hook e sem reconciliação. Não existe
ocorrência órfã.

### D7 — O corte do mês é a ocorrência, não a data da transação

`BuildTransactionError.RecurringMonthLocked` declara que uma transação recorrente não pode
mudar de mês. Grep em todo o repositório: dois hits, ambos no próprio arquivo — a declaração e
o mapeamento para string. **Nada a produz.**

Enquanto a regra estiver morta, uma transação confirmada pode ser editada para outro mês. Se o
corte fosse pela data dela, o dinheiro migraria de mês e o contador não — e o card
contradiria a si mesmo. Cortar por `recurring_occurrences.yearMonth` faz o contador e a soma
falarem do mesmo mês por construção, sem depender de uma regra que ninguém aplica.

### D8 — A leitura do fato ganha um dono, em `feature/recurring/api`

Nasce um use case ao lado de `GetUnhandledRecurringUseCase`, com o método correspondente em
`IRecurringOccurrenceRepository`.

Não em `core/model`: nada fora da feature precisa disso, e `core/model` não pode nomear o
repositório de uma feature. Não em `core/ledger`: proibido pelo próprio `TransactionEntity`.
Não na tela: seria a regra sem dono que a derivation rule proíbe.

O retorno carrega as duas metades cruas mais as contagens, e o redutor é aplicado acima:

```
RecurringMonthOverview(
    settledExpense, settledIncome,     // MoneyByCurrency — fato
    forecastExpense, forecastIncome,   // MoneyByCurrency — projeção
    handled, total, skipped,           // contagens
    undenominated,                     // templates deixados fora da soma
)
```

`undenominated` é a declaração que hoje não existe: o dashboard descarta o template sem conta
em silêncio. Um card cujo trabalho inteiro é um total não pode repetir isso.

### D9 — O trabalho mora numa factory, não num segundo view model

Nasce um `RecurringSummaryFactory` em `feature/recurring/impl`, nos moldes de
`BalanceOverviewFactory`: `internal suspend fun` que recebe `ConsolidateMoneyUseCase` **como
parâmetro** e devolve as quatro figuras já consolidadas. O view model continua sendo classe de
fiação.

Um segundo view model reobservaria os mesmos flows. Consolidar dentro do use case acoplaria o
dono da regra à moeda de exibição, que é decisão de apresentação.

O `combine` passa de 2 para 5 fontes, e `observeLedgerChanges()` é uma delas — não opcional: a
leitura do fato é `suspend`, e sem esse gatilho as figuras congelariam enquanto o razão anda,
que é exatamente o que o KDoc de `IEntryRepository.observeLedgerChanges` descreve.

### D10 — `currencyOf` muda de casa, e a resolução vira um mapa por emissão

Duas coisas ao mesmo tempo, porque são a mesma:

A regra "a conta ou o cartão que a recorrência nomeia é o que denomina o valor dela" tem duas
implementações idênticas hoje — uma `internal` em `feature/recurring/impl`, outra copiada
inline em `feature/dashboard/impl` justamente porque a primeira não é alcançável. O resumo
seria a terceira cópia. Sobe para `feature/recurring/api`, e a cópia do dashboard é apagada.

E `RecurringViewModel` a chama **por item, dentro do `combine`** — para template de cartão
isso é um `getAccountById`, logo N consultas por emissão. Somar o resumo sobre a mesma
estrutura dobraria. As moedas passam a ser resolvidas uma vez por emissão, num
`Map<Long, String>` que a lista e o resumo compartilham.

### D11 — O card e o vazio de recorte são itens da mesma lista

O corpo da tela hoje é um `when` de ramos exclusivos, e o ramo de recorte vazio renderiza em
`fillMaxSize` — o que **apagaria o card** sempre que o filtro não devolvesse nada, inclusive
no recorte de arquivadas numa base sem arquivadas, que é quando o resumo mais tem a dizer.

O card entra como `item(key = …)` do `LazyColumn` e o vazio de recorte também, como
`TransactionsScreen` já faz. O card rola para fora e não volta, que é o que torna aceitável
ele ser alto.

O vazio de **base** continua ocupando a tela: sem recorrência alguma não há mês a resumir, e a
oferta de criar a primeira permanece como está.

### D12 — O seletor de mês é copiado, não promovido

`MonthPickerDropdownMenu` é público em `core/designsystem` e é o que faz o trabalho. O
`PeriodChip` que o embrulha é `private` dentro de `SummaryCard.kt`. Copiam-se as ~25 linhas do
chip agora; promove-se para `core/designsystem` quando houver o terceiro uso.

Correção de premissa registrada: `InvoiceMonthNavigator` **não** serve — é um
`OutlinedTextField` acoplado a `InvoiceMonthSelection`, que colore a borda pelo status da
fatura.

### D13 — "Template sem conta" não reusa a explicação de figura aproximada

O `ConsolidationBadge` tem um estado de irresolvível, e a tentação é reusá-lo. A copy que ele
exibiria fala de **taxa não cadastrada**. "O template perdeu a conta" é outra falha, com outra
saída — apontar o template para uma conta, não cadastrar taxa. Reusar o badge diria a frase
errada com autoridade.

Então: a soma sai sem o template, e o card declara em uma linha própria quantos ficaram de
fora. E a figura **não** vira `***`, porque o resto do dinheiro é conhecido — diferente da
linha da lista (D5), onde não há resto.

### D14 — O card herda a gramática do `SummaryCard`, menos o governo

Copia-se: chip dentro do card, um badge de consolidação para o card inteiro em vez de uma nota
por figura, cada figura chegando com a política de sinal já anexada, e ausência ≠ zero para
anotações condicionais.

**Não** se copia a premissa de que o controle do card também governa a lista. Lá o KDoc diz
*"the chips govern the card and the list, while the filters under it govern only the list"*.
Aqui é o inverso deliberado: o chip governa só o card, o filtro governa só a lista. A
desambiguação é física — o filtro fica na `TopAppBar`, atrás da borda da barra; o card é o
primeiro item da lista.

### D15 — A altura do card se paga dobrando a metade vazia, não encolhendo o card

A variante compacta (Open Question, decidida em 10.5 pela completa) atacava a altura tirando
o rodapé — que é o único lugar do card onde um ciclo ignorado é representável. O que sobra
para cortar é o que não está dizendo nada: um bloco cujas duas figuras são zero.

Cada metade passa a ser dobrável, e a que **não tem movimento abre dobrada** — o fato no
começo de um mês, a projeção no fim dele. O rótulo fica dos dois jeitos: o que se dobra é a
figura, e um card que escondesse a palavra junto teria encolhido por um motivo invisível.

O predicado é `ConsolidatedAmount.isZero`, e ele lê **todos os termos**, não o primeiro: o
redutor devolve um termo por moeda, e `R$ 0,00 + US$ 50,00` é um mês com dinheiro dentro que
uma verificação ingênua dobraria. Vive ao lado da figura porque uma segunda superfície já
fazia a mesma pergunta à mão — a linha de fluxo de `BalanceOverviewFactory`, que some em vez
de exibir zero —, e duas leituras de "isto não afirma movimento" é uma a mais.

O estado inicial é derivado uma vez e **rederivado só quando aquele bloco cruza entre ter e
não ter movimento** (`rememberSaveable(holdsNothing)`). Sem essa chave, o bloco que o usuário
acabou de abrir se dobraria de novo assim que o seletor de mês se mexesse.

Não conflita com a regra de que zero é afirmação e ausência não é: a figura continua sendo
zero denominado pelo redutor, e continua a um toque. O que a dobra decide é o que a primeira
tela gasta, não o que o card afirma.

## Risks / Trade-offs

- **O card ocupa ~291dp e a primeira tela mostra ~4 linhas em vez de 8** → ele é item da
  `LazyColumn` e rola para fora; passada a primeira rolagem, a densidade é a do redesenho.
  Existe uma variante compacta (~246dp) que sobe o contador para a linha do chip e dispensa o
  rodapé, se a altura incomodar na prática.
- **`recurring_card_amount` é afirmado em cinco pontos dos fluxos Maestro** → a tag continua no
  `Text` da figura e **nunca** num `Row` container (um container não publica texto próprio: a
  tag seria encontrada e leria vazio, que é o defeito que uma asserção por `id` **e** `text`
  existe para pegar). E o texto continua sem sinal — onde os fluxos esperam sinal, eles o
  escrevem (`[+][$]500`, `[-][$]42[.,]90`); nas asserções desta tela, não escrevem. A semântica
  exata do matcher do Maestro não foi verificada, e a decisão não depende dela.
- **`EveryFigureCanExplainItselfTest` quebra o build** para todo arquivo de produção com
  `MoneyText(` + `figure =` sem `ConsolidationBadge`, e para todo `ConsolidationBadge(` sem
  `onSeeRates` → a dependência de `feature:settings:api` entra junto, na mesma tarefa que
  desenha o card. Não é opcional nem posterior.
- **O `combine` de 5 fontes reemite muito** → a leitura do fato é uma consulta agregada por
  mês, e as moedas são resolvidas uma vez por emissão (D10). O custo por emissão cai em
  relação ao de hoje mesmo com o resumo somado.
- **As alturas em dp são aritmética, não medição** → as partes fixas (paddings, `spacedBy`,
  chips) são exatas; as de texto assumem `lineHeight` padrão do Compose com Roboto. Em iOS e
  Desktop variam alguns dp. Nada no desenho depende de um valor exato.
- **A janela larga tem um painel de lista mais estreito** e não foi medida → a coluna direita
  da grade 2×2 é a que mais sofre num painel estreito; conferir antes de fechar o layout.

## Migration Plan

Sem migração de banco. A consulta agregada é leitura nova sobre tabelas e foreign keys que já
existem no schema v14; nenhuma entidade muda de forma.

A aposentadoria de `recurring_card_monthly_amount` é a remoção de uma chave com um único
consumidor, que sai na mesma mudança.

## Open Questions

- **Variante completa (~291dp) ou compacta (~246dp) do card?** A compacta sobe o contador para
  a linha do chip e dispensa o rodapé, devolvendo quase uma linha de lista na primeira tela.
  Decidir com o card na mão, não antes.
- **Como o Room persiste `RecurringOccurrence.Status`?** Não há converter para ele em
  `Converters.kt` — só para `YearMonth`. Assume-se suporte nativo a enum como texto, e o
  literal usado no `WHERE` da consulta agregada **precisa ser conferido** antes de escrever a
  query.
- **A foreign key `CASCADE` de `transactionId` está no schema v14 implantado?** A declaração
  na entidade foi lida; as 14 migrações não. Se a FK tiver nascido depois de uma migração que
  recriou a tabela sem ela, o comportamento auto-corretivo de D6 não vale, e o caso precisa de
  reconciliação explícita.
