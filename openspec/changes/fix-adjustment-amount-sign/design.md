## Context

O razão grava um ajuste como qualquer outra transação: uma perna monetária assinada em convenção débito-positivo, contra uma perna `EQUITY` de reconciliação. Aumentar a dívida de um cartão em R$ 100 é um crédito na perna `LIABILITY` — `-10000` cents (`AdjustInvoiceUseCase` passa `amount = -difference`; `LedgerEntryWriter.ledgerAmount()` grava `ADJUSTMENT -> cents` sem inverter). Reduzir o saldo de uma conta é um crédito na perna `ASSET` — também negativo.

Esse sinal natural **já é** o sinal que o usuário espera: "piorou" é negativo dos dois lados. Três consumidores o respeitam hoje — a modal de ajuste (`ViewAdjustmentUiState.signedAmount`), a linha "Ajustes" do resumo da fatura (via `DimensionFlows.adjustment`) e o próprio `EntryDao`, que documenta em três lugares que `adjustment` é *signed* e todo o resto é magnitude positiva.

O caminho da lista é o único que discorda, e discorda por perda de informação, não por regra divergente: `TransactionUiMapper` aplica `abs()` e o `TransactionCard` chama `formatWithSign` sobre o resultado. O ramo negativo de `formatWithSign` é inalcançável.

Sob esse defeito há um segundo, estrutural: "que sinal um valor monetário mostra" tem quatro implementações independentes — um `private enum` correto escondido em `AccountCard`, o mesmo conceito como três `Boolean` soltos no `SummaryRow` da fatura, e dois `when` gêmeos (card e relatório) que já divergiram e carregam o bug. `presentation-mapping` (`spec.md`, requisito "Mappers como única fronteira domínio-apresentação") proíbe que a escolha do valor de exibição aconteça em componente de UI — os quatro sítios a violam.

## Goals / Non-Goals

**Goals:**

- Um ajuste exibe o sinal correto em toda lista e no relatório exportado, coerente com a modal e com o resumo da fatura.
- A política de sinal de um valor exibido tem **um dono**, alcançável por `:core:ui` e por qualquer `feature/*/impl`.
- Nenhuma mudança visual fora do ajuste: despesa, receita, pagamento e transferência renderizam exatamente como hoje.
- A escolha do valor de exibição sai dos componentes e volta para o mapper, como `presentation-mapping` exige.

**Non-Goals:**

- Não alterar `Transaction.amount` (domínio) nem `ViewTransactionUiState.amount`: são consumidos por formulários (`EditTransactionModal`), onde módulo é o correto.
- Não introduzir tipo monetário de domínio. Cents, moeda e aritmética são do razão (`Entry.amount`, `BASE_CURRENCY`).
- Não corrigir a perspectiva ausente em `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` (chamam `toTransactionUi()` sem `accountId`, então a transação é lida pela perna `ASSET` mesmo dentro da fatura). Defeito separado.
- Não unificar as convenções do bloco de resumo da fatura, que mistura "Despesas −100" com "Total 100" (`dimensionOwed`, positivo-como-dívida). Pré-existente e fora do escopo.

## Decisions

### D1 — `TransactionUi.amount` passa a ser `DisplayAmount`, não `Double` assinado

A primeira formulação era tornar `amount` um `Double` assinado e adicionar `showsExplicitSign: Boolean`. Dois campos independentes podem divergir: quem altera o valor e esquece a flag produz um valor certo com sinal errado — exatamente a classe de defeito que estamos consertando. Amarrados em um tipo, é impossível construir um sem o outro.

**Alternativa considerada — helper de formatação (`TransactionUi.displayAmount(formatter): String`) chamado pelos renderizadores.** Rejeitada: continua do lado errado da fronteira de `presentation-mapping`, e deixa `exportTone` (que precisa do **sinal**, não do texto) sem fonte de verdade.

**Alternativa considerada — o mapper devolver `String` já formatada.** Rejeitada pelo mesmo motivo: `exportTone` e a cor do card decidem por sinal. O tipo carrega o `Double`; a formatação é uma operação sobre ele.

### D2 — `DisplayAmount` mora em `:core:common`, ao lado de `CurrencyFormatter`

É onde `CurrencyFormatter` (`expect class`) e `LocalCurrencyFormatter` já vivem, e é o único módulo alcançado tanto por `:core:ui` quanto por toda `feature/*/impl` — `feature/creditcards/impl` precisa dele para o `SummaryRow`. Pôr em `:core:ui` funcionaria para os consumidores atuais, mas prenderia um conceito de vocabulário monetário dentro do módulo de componentes.

O tipo **não** possui o formatter: `CurrencyFormatter` é `expect/actual` e chega por CompositionLocal. `DisplayAmount` possui o valor e a política; a formatação é uma extensão sobre `CurrencyFormatter`, que segue dona do locale.

### D3 — As políticas são as que já foram descobertas por uso

`AccountSignDisplay` (`AccountCard.kt`) já enumerou os quatro casos reais em produção. A nova enumeração é a mesma, renomeada para vocabulário de valor em vez de vocabulário de conta:

| Política | Comportamento | Origem |
|---|---|---|
| `MAGNITUDE` | módulo, sem sinal | despesa, receita, pagamento |
| `NATURAL` | `format` do valor assinado — "−" só quando negativo | `SHOW_ONLY_NEGATIVE`, transferência |
| `EXPLICIT_SIGN` | `formatWithSign` — sempre "+" ou "−" | `SHOW_ALWAYS`, **ajuste** |
| `FORCED_POSITIVE` | `"+"` + módulo | `ALWAYS_POSITIVE` |
| `FORCED_NEGATIVE` | `"−"` + módulo | `ALWAYS_NEGATIVE` |

Não se inventa política nova: cada uma tem chamador existente.

### D4 — Sem aritmética, sem moeda

`DisplayAmount` não expõe `plus`, `minus`, `times` nem `currency`. No minuto em que expuser, passa a competir com o razão pela pergunta "quanto é" — e o razão é o dono único disso. Ele responde apenas "como se lê". Por isso o nome evita `Money`, que convida ao deslize.

Ele expõe o `Double` (`value`) para quem decide por sinal — `exportTone`, a cor do card — sem passar pela formatação.

### D5 — A regra de sinal por forma do razão, aplicada no mapper

`CurrencyFormatter.format` **não** aplica `abs` (verificado em `.jvm.kt`, `.android.kt`, `.ios.kt`); só `formatWithSign` o faz. Portanto o mapper precisa escolher a política explicitamente por forma, ou uma despesa passaria a ler "−R$ 100" e um pagamento de fatura "−R$ 80" — mudanças não pedidas.

| Forma | Política | Hoje | Depois |
|---|---|---|---|
| Ajuste | `EXPLICIT_SIGN` | sempre `+R$ 100` | `+`/`−` conforme a perna |
| Transferência | `NATURAL` | `-R$ 100` por concatenação manual | `-R$ 100` pelo formatter |
| Despesa (conta e cartão) | `MAGNITUDE` | `R$ 100` | `R$ 100` |
| Receita | `MAGNITUDE` | `R$ 100` | `R$ 100` |
| Pagamento de fatura | `MAGNITUDE` | `R$ 80` | `R$ 80` |

Só a primeira linha muda. A segunda muda de mecanismo, não de resultado — e trocar `"-${format(abs)}"` por `format(negativo)` devolve ao locale a decisão de onde e como o sinal aparece, que é o correto.

### D6 — A absorção é condição, não extra

Criar o tipo e converter só o card e o relatório deixaria o app com **cinco** encodings da mesma decisão em vez de quatro — pior para quem lê do que não fazer nada. Então `AccountCard` e o `SummaryRow` da fatura entram no escopo. São conversão mecânica (D3 mapeia 1:1, sem mudança de comportamento), mas ampliam a superfície de verificação visual para telas que não são alvo do bug.

### D7 — Delta de spec: sinal de perna ≠ sinal de saldo

`presentation-mapping` diz *onde* a tradução acontece, não *qual* sinal — e seu único cenário sobre sinal é o da inversão por `AccountType`. Lido isoladamente, ele sugere aplicar `displaySign` também à perna `LIABILITY`, o que produziria de volta o `+R$ 100,00` que este change remove. Na prática `displaySign` só é usado em saldos e totais (`CalculateCategorySpendingUseCaseImpl`, `ViewCategoryViewModel`, `dimensionOwed`), nunca numa perna de transação.

O delta adiciona o cenário que falta, para que a distinção seja legível sem arqueologia.

## Risks / Trade-offs

- **Regressão silenciosa em despesa/receita/pagamento se a política for esquecida em algum ramo** → `format` não aplica `abs`, então o erro é visível na primeira renderização. Coberto por testes de não-regressão, um por forma do razão (D5), antes de qualquer verificação manual.
- **Diferença de locale ao trocar concatenação por formatação de negativo** → posição do sinal e estilos contábeis (parênteses) passam a ser do `NumberFormat`. É mais correto e já há precedente em produção (`SummaryRow` formata negativo direto), mas não é no-op garantido em todo locale. Verificar em pt-BR na rodada manual.
- **Escopo maior que o bug: contas e cartões entram na verificação sem serem a causa** → aceito deliberadamente (D6). Mitigação: `tasks.md` separa a correção do defeito da absorção, em grupos independentes, de modo que a absorção possa ser interrompida sem desfazer o conserto.
- **`exportTone` depende de `amount` continuar exposto como número** → garantido por D4; um teste trava o tom `NEGATIVE` para ajuste de perna negativa, hoje inalcançável.
- **`TransactionUi.amount` muda de tipo** → quebra de compilação, não de runtime: três consumidores, todos no repositório, todos listados no `proposal.md`.
