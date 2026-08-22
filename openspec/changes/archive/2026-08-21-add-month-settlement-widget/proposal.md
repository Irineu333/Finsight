## Why

O dashboard responde bem "o que já aconteceu" e quase nada sobre "o que ainda vai acontecer".
Todo widget de dinheiro da tela é leitura do razão sobre lançamento já escriturado —
`TOTAL_BALANCE`, os três perímetros de fluxo — e o saldo que o usuário lê não reflete o que
está prestes a sair dele.

O único widget que olha adiante, `PENDING_BALANCE_STATS`, cobre uma fatia estreita: recorrentes
**vencidas** do mês corrente e nada mais (`GetPendingRecurringUseCase`, corte
`effectiveDay(dayOfMonth) <= today.day`). Ele ignora as faturas — que é onde mora a maior parte
do dinheiro já comprometido — e ignora as recorrentes do mês que ainda não venceram. O usuário
com R$ 3.000 de fatura vencendo dia 28 não tem, em lugar nenhum da tela, uma figura que diga isso.

A fonte existe e está inteira: parcelas futuras já são pré-lançadas nas faturas dos meses que
vêm (`AddInstallmentUseCaseImpl`), e `IEntryRepository.owedByDimensionByCurrency` devolve o
devido de N faturas em uma consulta, por moeda. O que falta é o widget que soma isso.

## What Changes

- Novo widget de dashboard **"A liquidar este mês"**: um par de figuras — **A entrar** / **A
  sair** — somando os compromissos que ainda não se liquidaram e cujo vencimento não é futuro.
  A interpretação é liquidação, não competência: o que ainda vai sair (ou entrar) da conta
  dentro deste mês.
- Duas fontes, ambas configuráveis e **ambas ligadas por padrão**:
  - **Recorrentes do mês** — templates não arquivados e sem ocorrência no mês corrente,
    independentemente de o dia já ter passado. Alimenta as **duas** classes.
  - **Faturas a pagar** — faturas não pagas com `dueMonth <= mês corrente`, o que inclui as
    **vencidas de meses anteriores**. Alimenta apenas **A sair**.
- As duas fontes são **disjuntas por construção**, e isso é a propriedade que sustenta o widget:
  uma recorrente pendente não tem lançamento, logo não está no devido de fatura nenhuma. Como
  `ConfirmRecurringUseCase` resolve a fatura por `date.yearMonth` tratado como **mês de
  vencimento** (`GetOrCreateInvoiceForMonthUseCaseImpl`, `find { it.dueMonth == targetDueMonth }`),
  confirmar uma recorrente de cartão **não move o total** — ela migra de "prevista" para dentro
  da fatura do mesmo mês. Confirmar uma recorrente de conta faz o total cair, porque o dinheiro
  saiu de fato.
- Fatura `RETROACTIVE` com saldo **entra** na figura, por **exceção local** desta leitura: ela é
  exatamente o caso de dívida vencida sendo regularizada. A contradição global permanece aberta
  e fora do escopo — `observeUnpaidInvoices()` é `WHERE status NOT IN ('PAID','RETROACTIVE')`
  (`InvoiceDao.kt:36`) enquanto `Status.isPayable` e `Status.isEditable` incluem `RETROACTIVE`
  (issue `retroactive-invoice-debt-is-invisible-to-the-available-limit`).
- O widget admite `hide_when_empty`, que governa **apenas vazio-de-dados**. Vazio-de-fontes — os
  dois toggles desligados — exibe **zero** e nunca oculta, porque um widget que some enquanto o
  usuário opera a configuração dele torna a configuração inoperável.
- **BREAKING (comportamental, não de API)**: `PENDING_BALANCE_STATS` é marcado **deprecado** —
  sai da vitrine do modo de edição e sai do layout padrão. Quem já editou o dashboard mantém o
  widget e ele continua renderizando normalmente; quem **nunca editou** lê o padrão recalculado
  a cada leitura (`GetDashboardPreferencesUseCase`, `savedPrefs ?: defaultPreferences()`) e verá
  a troca acontecer. É troca por **superconjunto** — o novo widget contém o que o antigo somava
  —, e nenhuma preferência salva é reescrita.
- `PENDING_RECURRING` **não** é tocado: é lista com ação de confirmar, não um total, e não é
  subsumido por um par de figuras.

**Assimetria aceita e declarada.** "Vencido" alcança qualquer mês atrás nas faturas e apenas o
mês corrente nas recorrentes, porque `GetPendingRecurringUseCase` decide tudo sobre
`today.yearMonth` e não há consulta a mês anterior em nenhum ponto do caminho. Uma recorrente de
junho nunca confirmada é invisível em agosto. Isso é a issue
`a-recurring-left-unconfirmed-vanishes-when-the-month-turns`, que permanece aberta: este widget
não a conserta e não a piora, mas herda o buraco e precisa dizê-lo.

## Capabilities

### New Capabilities
- `month-settlement-forecast`: o perímetro de **liquidação** como identidade de widget — o que
  entra na figura (as duas fontes e por que são disjuntas), o corte de vencimento que inclui o
  vencido, a exceção da fatura retroativa, a invariância do total sob confirmação, o que a
  configuração governa e o que ela não governa, e o alcance desigual do "vencido" entre as duas
  fontes.

### Modified Capabilities
- `dashboard-balance-widgets`: ganha (a) a regra de **depreciação** de um widget — o que deprecado
  significa (fora da vitrine, fora do padrão, ainda renderizado para quem o tem salvo) e sob que
  condição o layout padrão pode mudar para quem nunca editou; e (b) a distinção entre
  **vazio-de-dados** e **vazio-de-fontes** na configuração de ocultar-quando-vazio, hoje implícita
  no requisito "Total sem parcela alguma vale zero" e aplicada a um só widget.

## Impact

**Dashboard (`feature/dashboard/impl`)**
- `ui/screen/dashboard/DashboardComponentType.kt` — entrada nova; flag de depreciação no enum.
- `ui/screen/dashboard/DashboardComponentConfig.kt` — as duas chaves de fonte.
- `ui/screen/dashboard/DashboardComponent.kt` / `DashboardComponentVariant.kt` /
  `DashboardComponentContent.kt` / `DashboardPreviewFactory.kt` — o par novo.
- `ui/screen/dashboard/DashboardComponentsBuilder.kt` — o construtor da figura.
- `ui/screen/dashboard/DashboardViewModel.kt` — o catálogo do modo de edição filtra deprecados
  (`DashboardComponentType.entries.filterNot { ... }`, linha 300); e as faturas deixam de chegar
  só como `invoicesByCreditCardId`, que guarda **uma fatura por cartão** (`associateBy` sobre
  lista `openingMonth DESC`, linhas 64-66) e por isso não serve a esta leitura.
- `domain/usecase/GetDashboardPreferencesUseCase.kt` — layout padrão: entra o novo, sai o
  deprecado.

**Cartões (`feature/creditcards/api` + `impl`)**
- Uma leitura de faturas a pagar que inclua `RETROACTIVE` com saldo e não colapse a moeda —
  `CalculateInvoiceUseCase` devolve `Double` escalar via `singleOrNull()`, o que perderia o
  agrupamento por moeda antes da consolidação.

**Recorrentes (`feature/recurring/api`)**
- Consumo de `GetPendingRecurringUseCase` sem alteração; o recorte "mês inteiro, não só vencidas"
  é do widget.

**Recursos (`core/resources`)**
- Chaves novas em `values/strings.xml` **e** `values-en/strings.xml`: título do widget e os dois
  rótulos de classe.

**Fora de escopo**
- O predicado único de `RETROACTIVE` (issue aberta, dois outros consumidores).
- O backlog de recorrentes de meses anteriores (issue aberta).
