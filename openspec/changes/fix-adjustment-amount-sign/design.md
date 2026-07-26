## Context

O razão grava um ajuste como qualquer outra transação: uma perna monetária assinada em convenção débito-positivo, contra uma perna `EQUITY` de reconciliação. Aumentar a dívida de um cartão em R$ 100 é um crédito na perna `LIABILITY` — `-10000` cents (`AdjustInvoiceUseCase` passa `amount = -difference`; `LedgerEntryWriter.ledgerAmount()` grava `ADJUSTMENT -> cents` sem inverter). Reduzir o saldo de uma conta é um crédito na perna `ASSET` — também negativo.

O caminho da lista perde esse sinal: `TransactionUiMapper` aplica `abs()` e o `TransactionCard` chama `formatWithSign` sobre o resultado, tornando o ramo negativo inalcançável. Todo ajuste renderiza `+`, em qualquer direção.

Sob o defeito há uma regra que o app **descobriu mas nunca escreveu**. O `SummaryCard` (`feature/transactions/impl`) resolve o sinal de cada linha por perspectiva, em três corpos — `AccountsBody`, `CardsBody`, `OverallBody` — e explica o porquê em KDoc: *"Both legs are inside this perimeter, so the payment moves nothing: shown without a sign and in a quieter tone, precisely so the column above still adds up."* O resumo da fatura, o `AccountCard` e as linhas do relatório seguem a mesma lógica, cada um com seu próprio mecanismo. A superfície de **item** — o card de transação e o relatório exportado — nunca recebeu a regra, e é onde o defeito mora.

## Goals / Non-Goals

**Goals:**

- Escrever a regra de sinal como regra, com um princípio único, e aplicá-la nas duas superfícies.
- Um ajuste exibe o sinal correto em toda superfície, coerente com a modal e com o resumo da fatura.
- A política de sinal tem **um dono**, alcançável por `:core:ui` e por qualquer `feature/*/impl`.
- Nos resumos, nenhuma mudança de comportamento — exceto o ramo de fatura do relatório, que hoje não obedece à regra e é a mudança visual 4, declarada.
- A escolha do valor de exibição sai dos componentes e volta para quem produz a figura, nas duas superfícies.

**Non-Goals:**

- Não alterar `Transaction.amount` (domínio): ele alimenta o formulário de edição (`EditTransactionModal.kt:88`), onde módulo é o correto. A modal de visualização **entra** no escopo — o usuário declarou a regra "para cards e modais", e `ViewTransactionUiState.amount` (`:61`) é a mesma superfície de item, hoje em `abs()`.
- Não introduzir tipo monetário de domínio. Cents, moeda e aritmética entre valores são do razão.
- Não dar perspectiva a quem não tem uma: a lista geral, o dashboard e os parcelamentos seguem mapeando sem `accountId`. As telas que **têm** uma entram no escopo — ver D11, que substitui o não-goal anterior.
- Não unificar as convenções do bloco de resumo da fatura, que mistura "Despesas −100" com "Total 100" (`dimensionOwed`, positivo-como-dívida).

## Decisions

### D1 — O princípio: o sinal expressa efeito sobre a perspectiva

**O sinal de um valor exibido expressa o efeito daquele valor sobre o patrimônio da perspectiva em que é lido.** Dele decorrem as duas omissões:

- **onde o rótulo já explica a direção, o sinal é ruído** — um gasto é obviamente negativo, e escrever `−` ao lado da palavra "gasto" não informa nada;
- **onde não há perspectiva, não há efeito a expressar** — um pagamento de fatura visto do patrimônio total move os dois lados dentro do mesmo perímetro, então é neutro.

E a inclusão: **o sinal aparece onde há aritmética a explicar**, para que uma coluna de valores justifique o total abaixo dela.

As tabelas de D5 são do usuário — ele as declarou célula a célula. O princípio acima é a generalização que este change extrai delas, para que casos futuros tenham de onde derivar em vez de virarem uma sexta linha arbitrária. Onde os dois divergirem, a tabela vence.

### D2 — `TransactionUi.amount` passa a ser `DisplayAmount`, não `Double` assinado

A primeira formulação era um `Double` assinado mais um `showsExplicitSign: Boolean`. Dois campos independentes podem divergir: quem altera o valor e esquece a flag produz um valor certo com sinal errado — a classe de defeito que estamos consertando. Amarrados em um tipo, é impossível construir um sem o outro.

**Alternativa rejeitada — helper de formatação chamado pelos renderizadores.** Continua do lado errado da fronteira de `presentation-mapping`, e deixa `exportTone` (que precisa do **sinal**, não do texto) sem fonte de verdade.

**Alternativa rejeitada — o mapper devolver `String` já formatada.** Mesmo motivo: `exportTone` e a cor do card decidem por sinal.

**Alternativa rejeitada — `Double` assinado e nenhum tipo novo.** Seria estritamente menor, e o custo aparente é um caractere: `format(+100.0)` devolve `"R$ 100,00"`, sem o `+` que o ajuste positivo precisa. Mas o custo real é maior — sem a política junto do valor, as sete políticas que produção já usa continuariam espalhadas, e a superfície de resumo não teria como participar do mesmo dono.

### D3 — `DisplayAmount` mora em `:core:common`, ao lado de `CurrencyFormatter`

É onde `CurrencyFormatter` (`expect class`) e `LocalCurrencyFormatter` já vivem, e o único módulo alcançado tanto por `:core:ui` quanto por toda `feature/*/impl` — `feature/creditcards/impl` e `feature/transactions/impl` precisam dele.

O tipo **não** possui o formatter: `CurrencyFormatter` é `expect/actual` e chega por CompositionLocal. `DisplayAmount` possui o valor e a política; a formatação é uma extensão sobre `CurrencyFormatter`, que segue dona do locale.

Como `TransactionUi.amount` passa a expor um tipo de `:core:common` na API pública de `:core:ui`, a dependência lá vira `api(...)` — hoje é `implementation(...)` e compila por acidente, porque cada `feature/*/impl` declara `core:common` por conta própria.

### D4 — As políticas vêm do `SignDisplay`, que é a implementação de referência

`SignDisplay` (`SummaryCard.kt:426`) não é uma duplicata a absorver: é a versão **mais completa** da regra em produção, com sete casos e KDoc justificando cada um. O `private enum AccountSignDisplay` (`AccountCard.kt:359`) é o subconjunto pobre. O tipo novo nasce do primeiro, não do segundo.

| Política | Comportamento | Significado |
|---|---|---|
| `MAGNITUDE` | módulo, sem sinal | o rótulo já explica a direção |
| `NATURAL` | `format(value)` — só o negativo aparece | saldo: o negativo é a informação |
| `NEUTRAL` | sem sinal acrescentado | não move nada nesta perspectiva |
| `EXPLICIT_SIGN` | sempre `+` ou `−` | a direção não é derivável do rótulo |
| `FORCED_POSITIVE` | `+` + módulo | linha que sempre soma |
| `FORCED_NEGATIVE` | `−` + módulo | linha que sempre subtrai |
| `OWED` | módulo da dívida, zero quando não se deve | "quanto se deve", de um saldo no sinal do razão |

`NEUTRAL` e `NATURAL` têm hoje o mesmo comportamento e significados distintos; a distinção é deliberada e vem do `SignDisplay` original (`NONE`). Uma linha neutra que passasse a receber valor negativo deve continuar neutra — o que só é decidível se a intenção estiver registrada.

### D5 — A regra, aplicada: duas superfícies

**Item** (card de transação, modal, linha do relatório exportado) — o sinal só aparece onde o rótulo não entrega a direção:

| Forma | Política | Muda? |
|---|---|---|
| Gasto (conta e cartão) | `MAGNITUDE` | não |
| Receita | `MAGNITUDE` | não |
| Pagamento de fatura | `MAGNITUDE` | não |
| Transferência **com** perspectiva | `EXPLICIT_SIGN` | **sim** — a entrada ganha `+` |
| Transferência **sem** perspectiva | `MAGNITUDE` | **sim** — perde o `−` de hoje |
| Ajuste | `EXPLICIT_SIGN` | **sim** — é o defeito |

A transferência é a única exceção ao "o rótulo explica": "Transferência • R$ 100,00" não diz se o dinheiro entrou ou saiu, e as duas pontas compartilham rótulo, ícone e cor. Com perspectiva, o sinal é a única coisa que as distingue — e é **explícito nas duas pontas**, pelo mesmo motivo que o ajuste: quando o rótulo não entrega a direção, meia informação é pior que a regra inteira. Mostrar `−` na saída e nada na entrada obrigaria o usuário a inferir "sem sinal, então entrou", que é justamente o raciocínio que o sinal existe para poupar. Sem perspectiva não há para onde o dinheiro ter ido — a lista vê as duas pontas da mesma transação —, então não há direção a exibir. O pagamento não é exceção: "Pagamento" entrega a direção em qualquer perspectiva.

**Resumo** (linha que participa de uma soma) — o sinal expressa o efeito sobre o patrimônio da perspectiva:

| Forma | Perspectiva conta | Perspectiva cartão | Sem perspectiva |
|---|---|---|---|
| Gasto | `FORCED_NEGATIVE` | `FORCED_NEGATIVE` | `FORCED_NEGATIVE` |
| Receita | `FORCED_POSITIVE` | — | `FORCED_POSITIVE` |
| Pagamento | `FORCED_NEGATIVE` | `FORCED_POSITIVE` | `NEUTRAL` |
| Transferência | efeito na perspectiva | efeito na perspectiva | `NEUTRAL` |
| Ajuste | `EXPLICIT_SIGN` | `EXPLICIT_SIGN` | `EXPLICIT_SIGN` |
| Saldo (abertura/fim) | `NATURAL` | `OWED` | `NATURAL` |

Esta tabela **descreve o comportamento atual** de `AccountsBody`, `CardsBody`, `OverallBody`, do resumo da fatura e das linhas de **conta** do relatório. Esses resumos não mudam; muda o mecanismo.

**Duas exceções, ambas no ramo de fatura do relatório**, onde o comportamento atual **não** obedece à regra e a absorção é mudança visível:

| Sítio | Hoje | Pela regra |
|---|---|---|
| `ReportExportLayout.kt:86` e `ReportContextCard.kt:199` — gasto de fatura | sem sinal | `FORCED_NEGATIVE` — ganha `−` |
| `ReportExportLayout.kt:96` e `ReportContextCard.kt:235` — pagamento antecipado | sem sinal | `FORCED_POSITIVE` — ganha `+` |

Elas são declaradas como mudança visual 3, e não descobertas durante a implementação. As linhas de conta do mesmo relatório (`ReportExportLayout.kt:73,78`) já obedecem.

**Uma armadilha, na linha "Total" da fatura** (`InvoiceTransactionsScreen.kt:555`): a figura vem de `owedByDimension`, que já devolve **positivo-como-dívida** (`EntryRepository.kt:106-111`). A linha de saldo da tabela acima diz `OWED` para perspectiva de cartão, mas `OWED` calcula `max(0, -valor)` e zeraria um total já positivo. Para essa linha a política é `NATURAL`: a inversão já foi feita a montante. `OWED` serve às linhas que recebem saldo no sinal do razão (`SummaryCard.kt:200,235`), não às que recebem dívida já invertida.

A linha de transferência da tabela não tem chamador hoje — está nela porque a regra a cobre, não porque haja código a escrever.

O ajuste é a única forma idêntica nas duas superfícies, e é o que o torna especial: é a única transação cuja direção o rótulo não entrega.

### D6 — A política é resolvida por quem produz a figura, não pela composable

Nos resumos, a política é hoje escolhida **dentro do `@Composable`**: `SummaryCard.kt:134-290`, `AccountCard.kt:179-228` e o `SummaryRow` da fatura nomeiam a sua a cada linha. Trocar `SignDisplay.ALWAYS_NEGATIVE` por `DisplayAmount.forcedNegative(...)` no mesmo lugar não corrigiria nada: a decisão continuaria na UI, contra `presentation-mapping` e contra o requisito que este próprio change escreve.

Então a figura chega pronta. Cada produtor entrega `DisplayAmount`, e a composable só renderiza:

| Superfície | Produtor |
|---|---|
| `SummaryCard` (três corpos) | `BalanceOverviewFactory.balanceOverview()` |
| `AccountCard` | `AccountsViewModel.kt:95`, onde `AccountUi` é montado |
| Resumo da fatura | `InvoiceTransactionsViewModel.kt:170-180`, em `InvoiceSummary` |
| Relatório (contexto e exportação) | `ReportViewerUiState.Stats.Account` / `.Invoice` |

O ganho não é estético: hoje `AccountUi.income` e `AccountUi.expense` são dois `Double` cuja diferença de tratamento existe apenas na composable que os lê. Depois, a intenção viaja com o dado, e uma segunda tela que renderize o mesmo `AccountUi` não pode discordar da primeira.

### D7 — O tipo não faz aritmética entre valores

`DisplayAmount` MUST NOT somar, subtrair ou multiplicar dois valores, e MUST NOT conhecer moeda: quanto uma figura vale é do razão.

Uma política **pode** transformar o seu próprio valor para leitura — módulo, negação, clamp em zero. É o que `OWED` faz, e é da mesma família que `AccountType.displaySign`: apresentação de um único número, não cálculo. Sem essa distinção, `OWED` teria de ser resolvido por quem produz a figura, mexendo nos modelos de overview sem ganho — e a absorção do `SignDisplay` deixaria de ser mecânica.

### D8 — A absorção é condição, não extra

Sete sítios implementam a política hoje: `SignDisplay` (18 usos), `AccountSignDisplay` (6), o `SummaryRow` da fatura (4), os dois `when` de item (`TransactionCard`, `ReportExportLayout.exportAmount`) e quatro literais `"+${...}"`/`"-${...}"` em `ReportContextCard` e `ReportExportLayout`. Criar o tipo e converter só a superfície de item deixaria **oito**. O que justifica o tipo é ele ser o único, então a absorção entra no escopo.

Como os resumos já obedecem à regra (D5), a absorção é conversão de mecanismo, não mudança de comportamento — menos arriscada do que o número de sítios sugere. Duas armadilhas concretas: `ALWAYS_POSITIVE` aplica `absoluteValue` em `AccountCard.kt:325` e **não** aplica em `SummaryCard.kt:447`; e existem dois componentes chamados `SummaryRow` (`InvoiceTransactionsScreen.kt:728` e `SummaryCard.kt:399`), em sistemas diferentes.

### D9 — A tabela de item tem uma função, não duas cópias

A superfície de item tem **dois** produtores: `TransactionUiMapper` (`:core:ui`) para as listas e o relatório, e `ViewTransactionUiState` (`feature/transactions/impl`) para a modal de detalhe. Cada um resolvendo a tabela por conta própria seria o oitavo sítio — o que D8 existe para impedir.

A tabela mora numa função de `:core:ui` (`itemDisplayAmount(label, legAmountCents, hasPerspective)`), consumida pelos dois. `TransactionLabel` mais a presença de perspectiva bastam para todas as seis linhas, e `feature/transactions/impl` já depende de `:core:ui`.

### D10 — Delta de spec: sinal de perna ≠ sinal de saldo

`presentation-mapping` diz *onde* a tradução acontece, não *qual* sinal — e seu único cenário sobre sinal é o da inversão por `AccountType`. Lido isoladamente, sugere aplicar `displaySign` também à perna `LIABILITY`, o que produziria de volta o `+R$ 100,00` que este change remove. Na prática `displaySign` só é usado em saldos e totais, nunca numa perna.

O delta separa as duas leituras: *se* uma perna exibe sinal é decisão da política (`money-display`); *qual* sinal, quando exibe, é o natural do razão.

### D11 — Uma tela que tem perspectiva declara a perspectiva que tem

Esta decisão substitui o não-goal que dizia o contrário. A investigação que o motivava
estava errada em premissa, e vale registrar o erro: a justificativa para adiar era que a
**transferência** passaria a ser afetada, já que a sua política depende da perspectiva. Não
passa. Uma transferência é `ASSET → ASSET`; as duas telas de cartão têm perspectiva de
*cartão* e nunca a listam, e o relatório de contas é de **várias** contas, onde as duas
pernas caem dentro do perímetro. As três estão sem sinal por estarem certas, não por
acidente.

A perspectiva só muda a leitura de uma transação com **duas pernas monetárias**. Nas outras
cinco formas há uma perna monetária só, e a `primaryEntry` é ela de qualquer modo. Sobram
duas: a transferência, que não alcança nenhuma das três telas, e o **pagamento de fatura**
(`ASSET −X`, `LIABILITY +X`), cuja `primaryEntry` é a perna da conta. Lido do cartão, é a
perna do cartão — dinheiro que **entra**, `INCOME`.

O que torna isto escopo desta change não é a diferença visível, que é quase nula: `label` já
decide cor, ícone e título, e a política já decide o valor. É que
`InvoiceTransactionsViewModel.filter(type)` **já reimplementou a perspectiva à mão** para
poder filtrar, e diz isso no seu próprio comentário — *"a perna do cartão é o que esta tela
mostra"* —, enquanto o card que a mesma tela renderiza lê pela perna da conta. Uma tela, duas
definições de "a perna que eu mostro", capazes de discordar: filtrar por receita traz o
pagamento, e o item trazido foi mapeado como despesa.

É a mesma falha que D9 evita entre o mapper e a modal, e que a regra de derivação do projeto
proíbe em geral. Fechar a change deixando-a de pé seria escrever um requisito e violá-lo na
tela ao lado.

**Onde declarar, e onde não.** A perspectiva é declarada onde existe **uma**: o cartão, no
extrato da fatura, na tela de cartões e no ramo `CreditCardPerspective` do relatório. No ramo
`AccountPerspective` o relatório tem uma lista de contas, e "várias" não é uma perspectiva —
continua sem, e é o comportamento correto. A lista geral, o dashboard e os parcelamentos
seguem sem por não terem nenhuma. O parcelamento é o caso instrutivo: ele *tem* um cartão,
mas uma parcela é um gasto em cartão, de uma perna monetária só — declarar não mudaria nada,
e não declarar não esconde nada.

## Risks / Trade-offs

- **A transferência sem perspectiva perde o `−`, e é mudança de comportamento visível** → deliberada (D5). Na lista geral as duas pontas da mesma transferência aparecem como uma linha só, então o `−` de hoje descreve uma perna escolhida arbitrariamente — exatamente o que `presentation-mapping` já proíbe apresentar como propriedade da transação.
- **Regressão silenciosa se alguma forma ficar sem política explícita** → `CurrencyFormatter.format` não aplica `abs`, então o erro aparece na primeira renderização. Coberto por teste de não-regressão, um caso por forma.
- **`ALWAYS_POSITIVE` com semânticas divergentes entre os dois sítios** → unificar com `absoluteValue`; verificar antes se algum chamador de `SummaryCard` passa valor negativo, caso em que é mudança de comportamento e precisa ser decidida, não assumida.
- **Diferença de locale ao trocar concatenação por formatação de negativo** → **eliminado por construção**, e não deixado para a verificação manual: `FORCED_POSITIVE`/`FORCED_NEGATIVE`/`EXPLICIT_SIGN` concatenam o sinal sobre o módulo, exatamente como os sete sítios fazem hoje; só as políticas que já delegavam continuam delegando. A absorção vira no-op de texto demonstrável. Deixar o `NumberFormat` decidir a posição do negativo seria mais correto, mas é decisão própria e merece change própria.
- **Escopo: sete sítios, três features e dois módulos core** → mitigado pela ordem dos grupos em `tasks.md`: 1-2 consertam o defeito e se sustentam sozinhos; 3 é a absorção, interrompível a qualquer momento sem desfazer o conserto.
- **`TransactionUi.amount` muda de tipo** → quebra de compilação, não de runtime: dois consumidores de produção (ambos em `ReportExportLayout`) e um de teste.
