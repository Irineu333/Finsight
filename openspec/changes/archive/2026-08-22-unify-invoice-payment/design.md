## Context

O pagamento de fatura existe hoje em dois caminhos que se encontram na fronteira de escrita como o mesmo lançamento:

```
                    AdvanceInvoicePaymentUseCase        PayInvoicePaymentUseCase
guarda de status    nenhuma                             status == CLOSED            (:64)
janela de data      [openingDate, closingDate], ≤ hoje  [closingDate, dueDate], ≤ hoje
teto do valor       amount <= devido           (:87)    valor = devido, sem campo
efeito              nenhum                              PayInvoiceUseCase → PAID
                            └──────────────┬──────────────┘
                                           ▼
                        TransactionIntent(title = null, date, legs = [
                            EXPENSE  leaving  → conta pagadora      (sem dimensão)
                            INCOME   amount   → LIABILITY do cartão (dimensionId da fatura)
                        ])  +  HarvestExchangeRateUseCase(leaving, amount, date)
```

As duas montagens são idênticas, comentário por comentário. O que difere cabe nas três primeiras linhas da tabela, e as três derivam do estado da fatura.

A oferta, por sua vez, é redecidida em quatro lugares, e nenhum deles consulta o domínio:

| Superfície | Adiantar | Pagar |
|---|---|---|
| `core/ui` — `CreditCardCard.kt:334-335` | `invoiceUi.isOpen` | `invoiceUi.isClosed` |
| `CreditCardsScreen.kt` | `:559` | `:626` |
| `InvoiceTransactionsScreen.kt` | `:628` | `:718` |
| domínio (`Invoice.kt:37`) | — | `isPayable` = `CLOSED`, `RETROACTIVE` |

A última linha é a divergência: o domínio reconhece a fatura retroativa como devedora, e a previsão de liquidação do mês soma o devido dela (`month-settlement-forecast`), mas nenhuma superfície oferece o pagamento. `RETROACTIVE` nasce de um ciclo que vence antes do da fatura aberta (`CreateInvoiceUseCase:53`), de modo que toda a sua janela está no passado.

Restrições que o desenho herda e não negocia:

- `InvoiceWriteGuard` congela `PAID` e recusa escrita não liquidante em `CLOSED`. Pagamento parcial em `RETROACTIVE` passa livre, porque o status não é nenhum dos dois.
- `CloseInvoiceUseCase:64` quita a fatura retroativa **zerada** no fechamento, chamando `PayInvoiceUseCase` com ela ainda `RETROACTIVE` — o que só passa porque `isPayable` inclui `RETROACTIVE` (`PayInvoiceUseCase:42`).
- `CloseInvoiceUseCase:81` não abre sucessora ao fechar uma retroativa; ela vira `CLOSED` e o ciclo corrente segue aberto.
- `CalculateInvoiceUseCase` responde o devido em moeda única por garantia da própria feature; o cartão declara a moeda pela sua conta `LIABILITY` (D17).

## Goals / Non-Goals

**Goals:**
- Um sheet que **nomeia** a fatura que paga, em vez de herdá-la de quem o abriu.
- O modo — parcial ou quitação total — derivado do estado da fatura e de mais nada.
- A fatura retroativa alcançável para pagamento, sem inventar um quarto caminho.
- A regra de quem aceita o quê com **um dono no domínio**, consumida pelas quatro superfícies.
- Manter a fronteira de escrita e o guarda de dimensões intocados: nenhuma regra nova no razão.

**Non-Goals:**
- Pagamento parcial de fatura fechada.
- Resolver a exclusão de `RETROACTIVE` em `observeUnpaidInvoices` (issue próprio, aberto).
- Mexer na guarda `paidAt <= dueDate` ou no caso da fatura fechada que venceu (issue próprio, aberto).
- Alterar quem fecha, reabre, ajusta ou apaga uma fatura.
- Editar um pagamento de fatura já gravado — segue recusado por declaração em `balanced-ledger`.

## Decisions

### D1 — Um sheet, dois caminhos de escrita: os casos de uso não se fundem

A unificação é da **superfície**, não da operação. Fundir os dois casos de uso exigiria um parâmetro que dissesse qual deles está sendo executado — `settles: Boolean` ou equivalente —, e uma bandeira desse tipo esconde a decisão de negócio dentro de uma assinatura, contra a regra de estilo do projeto ("reusar de forma explícita, sem esconder decisões de negócio"). As guardas também não convergem: uma exige `CLOSED`, a outra exige janela de compra e teto; escrever a união delas num corpo só produz um `if` por regra, que é a duplicação de volta com outra sintaxe.

O que **é** duplicado — as duas pernas, a ordem delas, a dimensão só na perna do cartão, e a colheita da taxa a partir das duas pontas — vira uma peça única que os dois casos de uso invocam. É a única parte idêntica hoje, e é idêntica porque é a mesma coisa: a forma no razão de um pagamento de fatura.

**Alternativa considerada:** um único `PayInvoiceUseCase(invoiceId, amount?, …)` com `amount = null` significando quitação. Rejeitada: o significado de "quitar" passaria a ser a ausência de um argumento, e a guarda de status ficaria condicionada a ela — a regra mais importante da operação expressa por um nulo.

### D2 — O predicado de oferta é novo; `isPayable` não é reaproveitado

É tentador estreitar `isPayable` para `CLOSED` e usá-lo como predicado da tela. Isso **quebra o fechamento da retroativa zerada**: `CloseInvoiceUseCase:64` chama `PayInvoiceUseCase` com a fatura ainda `RETROACTIVE`, e é `ensure(invoice.isPayable)` (`PayInvoiceUseCase:42`) que deixa passar.

`isPayable` responde *"esta fatura pode ser marcada `PAID`?"* — pergunta do domínio, com dois caminhos legítimos (a quitação explícita de uma fechada e o fechamento de uma retroativa zerada). O que a tela precisa é outra pergunta: *"o que esta fatura aceita receber agora?"*. São predicados distintos e ficam distintos:

```
aceita parcial      OPEN, RETROACTIVE     → AdvanceInvoicePaymentUseCase
aceita quitação     CLOSED                → PayInvoicePaymentUseCase
aceita pagamento    união dos dois        → filtro do seletor de faturas
isPayable           CLOSED, RETROACTIVE   → inalterado, uso interno do domínio
```

`FUTURE` e `PAID` ficam fora da união e, portanto, fora do seletor — por construção. Hoje `FUTURE` fica fora por acidente: nenhuma data satisfaz a janela do adiantamento, então a operação falha depois de o usuário tê-la escolhido.

### D3 — O devido é lido pelo sheet, não recebido pronto

Com a fatura selecionável, `currentBillAmount` no construtor passaria a descrever a fatura errada no instante seguinte à primeira troca. O devido passa a ser lido de dentro, reativo à fatura selecionada, por `CalculateInvoiceUseCase` — que `PayInvoiceViewModel` já invoca hoje (o `AdvancePaymentViewModel` é que recebia o número pronto).

Isso remove `DisplayAmount` da assinatura do entry point e, com ele, a obrigação do chamador de denominar a figura. A moeda passa a vir de onde ela é declarada: a conta `LIABILITY` do cartão selecionado. A decisão de exibir o campo de contrapartida (`isCrossCurrency`) continua derivada da comparação entre essa moeda e a da conta pagadora, agora com as duas pontas variáveis.

### D4 — Cartão governa fatura, fatura governa data — a mesma hierarquia, outra janela

O sheet ganha a hierarquia que `invoice-governs-date` já normatiza para os formulários de lançamento, com a disciplina de ordem que `AddTransactionViewModel:269` documenta: **limpar a fatura antes de trocar o cartão**, para que o par (cartão novo, fatura velha) nunca seja observado — esse par nomeia uma janela que nenhuma seleção representa.

A diferença é qual janela recoloca a data. No lançamento é a janela de compra (`invoice-purchase-window`); aqui é a de **liquidação**, e ela depende do modo:

```
OPEN         [openingDate,  min(closingDate, hoje)]   janela do ciclo corrente
RETROACTIVE  [openingDate,  closingDate]              janela inteira no passado
CLOSED       [closingDate,  min(dueDate, hoje)]       janela de pagamento
```

Para `RETROACTIVE` a janela está toda no passado, e é isso que se quer: um ciclo passado sendo regularizado tem o pagamento datado dentro dele. Editar a data continua não mexendo na fatura, exatamente como na hierarquia existente.

### D5 — O seletor filtra por estado, não por devido

Filtrar por "tem dívida" seria mais honesto e custa uma consulta de dimensão por fatura listada — precisamente o que o issue `dimension-balances-fan-out-into-one-query-per-dimension` registra como custo já existente. O seletor filtra pelo predicado de estado (barato, vem da lista que a tela já observa) e o sheet responde pelo devido **da fatura selecionada**: escolhida uma fatura sem dívida, o valor lido é zero e a confirmação fica indisponível, com a razão dita em tela.

### D6 — Um campo de valor que troca de modo, não de significado

O campo é sempre "quanto desta fatura está sendo pago agora". Em `OPEN`/`RETROACTIVE` ele é entrada, com teto no devido; em `CLOSED` ele é afirmação — o devido, exibido e não editável, que é o que "só aceita quitação total" quer dizer na tela.

**Alternativa considerada:** dois campos, um por modo, para nunca trocar a natureza de um controle (a preocupação registrada no `PayInvoiceModal` atual: *"This field does not change role"*). Rejeitada: aquele comentário defende que o campo do devido não vire o campo do que sai da conta — duas grandezas diferentes —, e essa distinção continua de pé, porque o valor de contrapartida segue sendo um campo próprio. Um valor, um campo; o que muda é se ele aceita digitação.

### D7 — A guarda de status entra no adiantamento

`AdvanceInvoicePaymentUseCase` não verifica status. Hoje isso é inofensivo porque a única porta é uma tela que só aparece em fatura aberta; com o sheet escolhendo o caminho por um predicado, a regra "parcial só em `OPEN`/`RETROACTIVE`" passaria a existir exclusivamente na UI. A guarda no caso de uso é o que impede que a próxima tela a chamá-lo herde a permissão silenciosa — e é a mesma disciplina que o `InvoiceWriteGuard` aplica um nível abaixo.

### D8 — O fato derivado sobe para o modelo de UI

`CreditCardCard.kt:334-335` deriva `canPayInvoice`/`canAdvanceInvoice` **dentro do componente**, o que `presentation-mapping` já proíbe ("derivação de rótulo ... MUST NOT ocorrer em modelo de UI nem em componente de UI"). O predicado do domínio é resolvido no mapper e chega ao componente como fato plano, junto com o rótulo que o botão exibe. As três superfícies passam a ler o mesmo campo, e o botão único substitui o par.

### D9 — `PAID` continua alcançável só a partir de `CLOSED`

Pagar uma retroativa não a quita: abate a dívida e a deixa `RETROACTIVE`. A quitação dela continua sendo o caminho que já existe — pagar até zerar e fechar, caindo em `CloseInvoiceUseCase:64`, que marca `PAID` no fechamento.

Isso preserva um invariante que hoje vale por coincidência e passa a valer por desenho: **toda fatura `PAID` passou por `CLOSED`** (ou pelo fechamento que a quitou). A alternativa — deixar a quitação total disponível também para `RETROACTIVE` — obrigaria a decidir se um pagamento integral quita ou não, que é exatamente a pergunta que a regra "fechada só aceita quitação total" existe para não precisar responder duas vezes.

### D10 — O ramo de devido zero não é herdado

`PayInvoiceViewModel.submit` tem um caminho para `invoiceAmount == 0.0` que marca `PAID` sem lançar nada — e ele é **inalcançável pela própria tela**: `isValidInvoicePayment` recusa `outstandingDebt <= 0.0`, então o botão está desabilitado justamente quando o ramo se aplicaria. O sheet unificado não o carrega. A quitação de fatura sem dívida continua tendo dono, e é o fechamento (`CloseInvoiceUseCase:88`).

### D11 — Os dois eventos de analytics sobrevivem

`PayInvoice` e `AdvanceInvoicePayment` continuam sendo eventos distintos, escolhidos pelo mesmo predicado que escolhe o caso de uso. Fundir os dois perderia a única série histórica que distingue as duas intenções, e a distinção continua real mesmo com uma porta só.

## Risks / Trade-offs

- **Um valor digitado sobrevive à troca de fatura ou de cartão e passa a significar outra coisa** (inclusive noutra moeda) → trocar cartão ou fatura limpa o valor e o valor de contrapartida, pela mesma razão que a correção de transferência os limpa ao trocar a moeda de uma ponta: dígitos denominados numa moeda não sobrevivem sob o símbolo de outra, e um teto herdado de outra fatura é um teto errado.

- **A quebra do `CreditCardsEntry` alcança três chamadores em duas features** (`CreditCardsScreen`, `InvoiceTransactionsScreen`, `DashboardComponentContent:687`/`:696`) mais o variant de `CreditCardCard` no `core/ui` → a troca não é aditiva e o compilador acusa todos; o grupo de tarefas que a executa não termina sem `./gradlew jvmTest` verde.

- **A cobertura E2E do pagamento vive nos flows Maestro, que dependem dos ids atuais** → os ids do sheet unificado são definidos junto com ele, e os dois flows (`creditcards/lifecycle.yaml`, `report/lifecycle.yaml`) são reescritos no mesmo grupo em que a UI muda, não depois. A suíte é rodada à mão e o dispositivo precisa ser o do `.maestro/README.md` §2.

- **A fatura retroativa passa a aceitar pagamento sem que o resto do app concorde que ela deve** — `observeUnpaidInvoices` continua excluindo `RETROACTIVE`, e o limite disponível do cartão continua sem enxergá-la (issue aberto) → a divergência é anterior a esta mudança e permanece declarada; o que muda é que o caminho de pagamento passa a concordar com o domínio e com a previsão de liquidação, reduzindo de duas para uma as leituras discordantes.

- **Um pagamento de retroativa é datado no passado e o usuário pode esperar hoje** → é a consequência deliberada de D4; a janela é exibida no campo de data e o calendário não oferece nada fora dela, como já faz nos dois sheets atuais.

- **A fatura selecionada pode mudar de status enquanto o sheet está aberto** (outra tela fecha o ciclo) → o sheet lê a fatura reativamente e o modo acompanha; a confirmação valida contra o estado corrente, e a fronteira de escrita recusa o que escapar.
