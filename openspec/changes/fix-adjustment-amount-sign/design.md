## Context

O razão grava um ajuste como qualquer outra transação: uma perna monetária assinada em convenção débito-positivo, contra uma perna `EQUITY` de reconciliação. Aumentar a dívida de um cartão em R$ 100 é um crédito na perna `LIABILITY` — `-10000` cents (`AdjustInvoiceUseCase` passa `amount = -difference`; `LedgerEntryWriter.ledgerAmount()` grava `ADJUSTMENT -> cents` sem inverter). Reduzir o saldo de uma conta é um crédito na perna `ASSET` — também negativo.

O caminho da lista perde esse sinal: `TransactionUiMapper` aplica `abs()` e o `TransactionCard` chama `formatWithSign` sobre o resultado, tornando o ramo negativo inalcançável. Todo ajuste renderiza `+`, em qualquer direção.

Sob o defeito há uma regra que o app **descobriu mas nunca escreveu**. O `SummaryCard` (`feature/transactions/impl`) resolve o sinal de cada linha por perspectiva, em três corpos — `AccountsBody`, `CardsBody`, `OverallBody` — e explica o porquê em KDoc: *"Both legs are inside this perimeter, so the payment moves nothing: shown without a sign and in a quieter tone, precisely so the column above still adds up."* O resumo da fatura, o `AccountCard` e as linhas do relatório seguem a mesma lógica, cada um com seu próprio mecanismo. A superfície de **item** — o card de transação e o relatório exportado — nunca recebeu a regra, e é onde o defeito mora.

## Goals / Non-Goals

**Goals:**

- Escrever a regra de sinal como regra, com um princípio único, e aplicá-la nas duas superfícies.
- Um ajuste exibe o sinal correto em toda superfície, coerente com a modal e com o resumo da fatura.
- A política de sinal tem **um dono**, alcançável por `:core:ui` e por qualquer `feature/*/impl`.
- Nos resumos, nenhuma mudança de comportamento: eles já obedecem à regra; muda o mecanismo.
- A escolha do valor de exibição sai dos componentes e volta para o mapper.

**Non-Goals:**

- Não alterar `Transaction.amount` (domínio) nem `ViewTransactionUiState.amount`: são consumidos por formulários (`EditTransactionModal`), onde módulo é o correto.
- Não introduzir tipo monetário de domínio. Cents, moeda e aritmética entre valores são do razão.
- Não corrigir a perspectiva ausente em `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` (chamam `toTransactionUi()` sem `accountId`). Defeito separado — e que **não contamina este**, porque um ajuste tem uma única perna monetária (a de reconciliação não é `isMonetary`), logo é lido igual com ou sem perspectiva.
- Não unificar as convenções do bloco de resumo da fatura, que mistura "Despesas −100" com "Total 100" (`dimensionOwed`, positivo-como-dívida).

## Decisions

### D1 — O princípio: o sinal expressa efeito sobre a perspectiva

**O sinal de um valor exibido expressa o efeito daquele valor sobre o patrimônio da perspectiva em que é lido.** Dele decorrem as duas omissões:

- **onde o rótulo já explica a direção, o sinal é ruído** — um gasto é obviamente negativo, e escrever `−` ao lado da palavra "gasto" não informa nada;
- **onde não há perspectiva, não há efeito a expressar** — um pagamento de fatura visto do patrimônio total move os dois lados dentro do mesmo perímetro, então é neutro.

E a inclusão: **o sinal aparece onde há aritmética a explicar**, para que uma coluna de valores justifique o total abaixo dela.

Este princípio é o dono da regra. As tabelas de D5 são a sua aplicação, não regras independentes.

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
| Transferência **com** perspectiva | `NATURAL` | não (hoje: `−` na saída, nada na entrada) |
| Transferência **sem** perspectiva | `MAGNITUDE` | **sim** — perde o `−` de hoje |
| Ajuste | `EXPLICIT_SIGN` | **sim** — é o defeito |

A transferência é a única exceção ao "o rótulo explica": "Transferência • R$ 100,00" não diz se o dinheiro entrou ou saiu, e as duas pontas compartilham rótulo, ícone e cor. Com perspectiva, o sinal é a única coisa que as distingue e permanece. Sem perspectiva não há para onde o dinheiro ter ido — a lista geral vê as duas pontas da mesma transação —, então não há direção a exibir. O pagamento não é exceção: "Pagamento" entrega a direção em qualquer perspectiva.

**Resumo** (linha que participa de uma soma) — o sinal expressa o efeito sobre o patrimônio da perspectiva:

| Forma | Perspectiva conta | Perspectiva cartão | Sem perspectiva |
|---|---|---|---|
| Gasto | `FORCED_NEGATIVE` | `FORCED_NEGATIVE` | `FORCED_NEGATIVE` |
| Receita | `FORCED_POSITIVE` | — | `FORCED_POSITIVE` |
| Pagamento | `FORCED_NEGATIVE` | `FORCED_POSITIVE` | `NEUTRAL` |
| Transferência | efeito na perspectiva | efeito na perspectiva | `NEUTRAL` |
| Ajuste | `EXPLICIT_SIGN` | `EXPLICIT_SIGN` | `EXPLICIT_SIGN` |
| Saldo (abertura/fim) | `NATURAL` | `OWED` | `NATURAL` |

Esta tabela **descreve o comportamento atual** de `AccountsBody`, `CardsBody`, `OverallBody`, do resumo da fatura e das linhas do relatório. Nenhum resumo muda; muda o mecanismo. A linha de transferência não tem chamador hoje — está na tabela porque a regra a cobre, não porque haja código a escrever.

O ajuste é a única forma idêntica nas duas superfícies, e é o que o torna especial: é a única transação cuja direção o rótulo não entrega.

### D6 — O tipo não faz aritmética entre valores

`DisplayAmount` MUST NOT somar, subtrair ou multiplicar dois valores, e MUST NOT conhecer moeda: quanto uma figura vale é do razão.

Uma política **pode** transformar o seu próprio valor para leitura — módulo, negação, clamp em zero. É o que `OWED` faz, e é da mesma família que `AccountType.displaySign`: apresentação de um único número, não cálculo. Sem essa distinção, `OWED` teria de ser resolvido por quem produz a figura, mexendo nos modelos de overview sem ganho — e a absorção do `SignDisplay` deixaria de ser mecânica.

### D7 — A absorção é condição, não extra

Sete sítios implementam a política hoje: `SignDisplay` (18 usos), `AccountSignDisplay` (6), o `SummaryRow` da fatura (4), os dois `when` de item (`TransactionCard`, `ReportExportLayout.exportAmount`) e quatro literais `"+${...}"`/`"-${...}"` em `ReportContextCard` e `ReportExportLayout`. Criar o tipo e converter só a superfície de item deixaria **oito**. O que justifica o tipo é ele ser o único, então a absorção entra no escopo.

Como os resumos já obedecem à regra (D5), a absorção é conversão de mecanismo, não mudança de comportamento — menos arriscada do que o número de sítios sugere. Duas armadilhas concretas: `ALWAYS_POSITIVE` aplica `absoluteValue` em `AccountCard.kt:325` e **não** aplica em `SummaryCard.kt:447`; e existem dois componentes chamados `SummaryRow` (`InvoiceTransactionsScreen.kt:728` e `SummaryCard.kt:399`), em sistemas diferentes.

### D8 — Delta de spec: sinal de perna ≠ sinal de saldo

`presentation-mapping` diz *onde* a tradução acontece, não *qual* sinal — e seu único cenário sobre sinal é o da inversão por `AccountType`. Lido isoladamente, sugere aplicar `displaySign` também à perna `LIABILITY`, o que produziria de volta o `+R$ 100,00` que este change remove. Na prática `displaySign` só é usado em saldos e totais, nunca numa perna.

O delta separa as duas leituras: *se* uma perna exibe sinal é decisão da política (`money-display`); *qual* sinal, quando exibe, é o natural do razão.

## Risks / Trade-offs

- **A transferência sem perspectiva perde o `−`, e é mudança de comportamento visível** → deliberada (D5). Na lista geral as duas pontas da mesma transferência aparecem como uma linha só, então o `−` de hoje descreve uma perna escolhida arbitrariamente — exatamente o que `presentation-mapping` já proíbe apresentar como propriedade da transação.
- **Regressão silenciosa se alguma forma ficar sem política explícita** → `CurrencyFormatter.format` não aplica `abs`, então o erro aparece na primeira renderização. Coberto por teste de não-regressão, um caso por forma.
- **`ALWAYS_POSITIVE` com semânticas divergentes entre os dois sítios** → unificar com `absoluteValue`; verificar antes se algum chamador de `SummaryCard` passa valor negativo, caso em que é mudança de comportamento e precisa ser decidida, não assumida.
- **Diferença de locale ao trocar concatenação por formatação de negativo** → posição do sinal passa a ser do `NumberFormat`. Mais correto, com precedente em produção, mas não é no-op garantido. Verificar em pt-BR.
- **Escopo: sete sítios, três features e dois módulos core** → mitigado pela ordem dos grupos em `tasks.md`: 1-2 consertam o defeito e se sustentam sozinhos; 3 é a absorção, interrompível a qualquer momento sem desfazer o conserto.
- **`TransactionUi.amount` muda de tipo** → quebra de compilação, não de runtime: dois consumidores de produção (ambos em `ReportExportLayout`) e um de teste.
