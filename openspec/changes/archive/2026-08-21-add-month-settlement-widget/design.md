## Context

O widget compõe duas fontes que vivem em features diferentes, e a topologia dos módulos decide
onde a composição pode morar. A regra `(1) api ⊄ api` é dura, e os donos estão separados:

```
  core/model              Recurring, Invoice, Invoice.Status
  feature/recurring/api   IRecurringRepository, IRecurringOccurrenceRepository,
                          GetPendingRecurringUseCase
  feature/creditcards/api IInvoiceRepository
  feature/dashboard/impl  pode depender de qualquer feature:*:api  ← única casa possível
```

Nenhum dos dois `api` pode nomear o outro, então **não existe** um caso de uso de domínio, num
`api`, capaz de ler recorrentes e faturas ao mesmo tempo. A composição só cabe num `impl`, e o
`impl` que a quer é o do dashboard.

Isso não dispensa a regra de derivação. Ela continua valendo sobre as **regras** que a composição
consome, e é ali que este desenho tem trabalho a fazer: hoje `DashboardComponentsBuilder`
já recalcula em linha, dentro de `pendingRecurring()`, o conjunto de recorrentes tratadas no mês
— exatamente o que `GetPendingRecurringUseCase` calcula internamente. A duplicação existe; este
widget não pode virar a terceira cópia.

## Goals / Non-Goals

**Goals:**
- Dar dono de domínio a cada uma das duas regras que a figura consome, e deixar no dashboard
  apenas a composição.
- Ler o devido das faturas de forma agregada e por moeda, sem colapsar a moeda antes da
  consolidação.
- Incluir a fatura retroativa com saldo por uma exceção **nomeada e única**, sem espalhar mais uma
  enumeração de status pelo código.
- Deprecar `PENDING_BALANCE_STATS` sem que nenhum dashboard salvo perca uma posição.

**Non-Goals:**
- Consertar o predicado global de `RETROACTIVE` (limite disponível, `InstallmentUiMapper`,
  `observeUnpaidInvoices`). Continua sendo issue aberta.
- Varrer recorrentes de meses anteriores. O buraco é da capacidade de recorrentes, e ampliá-lo é
  mudança dela.
- Mexer em `PENDING_RECURRING`, que é lista com ação e não é subsumido por um par de figuras.

## Decisions

### D1 — A composição mora no dashboard; as duas regras, no domínio de cada feature

```
  feature/recurring/api    "não tratadas no mês M"          ← predicado, dono novo
  feature/creditcards/api  "a liquidar até o mês M" + devido ← leitura, dono novo
  ────────────────────────────────────────────────────────
  feature/dashboard/impl   soma as duas, consolida, monta o par   ← só isto
```

O dashboard soma dois `MoneyByCurrency` e chama o reducer. Somar e consolidar não é regra de
domínio — é o widget. Já *quais* recorrentes contam e *quais* faturas contam são regras, e cada
uma tem exatamente um dono, no `api` da feature que a possui.

**Alternativa descartada — compor tudo no builder.** É o que o código já faz para as tratadas do
mês, e é a origem da duplicação atual. Repetir ali o corte de recorrentes e o de faturas poria
três cópias da mesma regra no app.

**Alternativa descartada — um `api` novo, transversal.** Resolveria a topologia (`impl` pode ver
vários `api`), mas criaria um módulo cuja única razão de existir é hospedar uma soma, e a soma é
justamente a parte que não é regra.

### D2 — O predicado de recorrentes é "não tratada no mês", e o corte de dia sai dele

`GetPendingRecurringUseCase` faz **duas** coisas: filtra as não tratadas do mês e corta pelo dia
efetivo (`effectiveDay(dayOfMonth) <= today.day`). O widget quer a primeira e não quer a segunda.

A separação é o que este desenho pede: o predicado "não arquivada e sem ocorrência no mês" ganha
identidade própria no `api` de recorrentes, e "pendente" passa a ser ele **mais** o corte de dia.
`GetPendingRecurringUseCase` continua existindo com o mesmo resultado — quem o consome hoje não
muda —, agora expresso sobre o predicado, em vez de o reimplementar.

Efeito colateral bom: o `handledRecurringIds` recalculado em linha em `pendingRecurring()` passa
a ter para onde ir, e a duplicação atual fecha junto.

**Alternativa descartada — um parâmetro booleano em `GetPendingRecurringUseCase`.** Um caso de uso
cujo nome afirma "pendente" e que sob flag devolve o não-pendente mente sobre si mesmo.

### D3 — O corte de faturas é `status != PAID && dueMonth <= M`, sem lista de status

A inclusão da retroativa não precisa de whitelist. O critério que o perímetro quer já é
exatamente este par, e `RETROACTIVE` entra por consequência em vez de por exceção enumerada:

```
   observeUnpaidInvoices()   WHERE status NOT IN ('PAID','RETROACTIVE')   ← não serve
   este perímetro            WHERE status != 'PAID' AND dueMonth <= M     ← inclui a retroativa
```

Escrever o critério como negação de `PAID` evita a quarta enumeração de status que a issue
`retroactive-invoice-debt-is-invisible-to-the-available-limit` reclama, e deixa uma costura limpa:
quando o predicado global existir, esta leitura passa a lê-lo sem que o critério mude de sentido.

`FUTURE` com vencimento já passado entra pelo mesmo critério, e deve: as suas parcelas
pré-lançadas são dívida real e o mês de liquidação dela já chegou.

**Alternativa descartada — filtrar `observeAllInvoices()` na memória do builder.** Contraria o
precedente de aplicar o recorte dentro da leitura e carrega o histórico inteiro de faturas para
somar as poucas do perímetro.

### D4 — O devido vem agregado e por moeda, e o crédito é coagido por fatura

A leitura é `IEntryRepository.owedByDimensionByCurrency(dimensionIds)`: N faturas, uma consulta,
resultado por moeda. `CalculateInvoiceUseCase` **não** serve como fonte — ele colapsa para `Double`
via `singleOrNull()`, perdendo a moeda antes da consolidação, e custa uma leitura por fatura.

Fatura sem `dimensionId` (nulo só por causa das linhas anteriores à v10) não contribui.

Saldo credor é coagido a zero **por fatura**, antes da soma, como `InvoiceUiMapperImpl` já faz.
Coagir depois da soma deixaria o crédito de um cartão abater a dívida de outro — que é dinheiro
que vai sair de qualquer jeito.

### D5 — A troca no layout padrão é assumida, não sofrida

`savedPrefs ?: defaultPreferences()` resolve o padrão a cada leitura, então quem nunca editou o
dashboard **vai** ver a troca. É deliberado, e admissível por ser troca por superconjunto: o novo
widget contém o que `PENDING_BALANCE_STATS` somava.

O widget novo assume a posição 2, a do deprecado, e herda dela `hide_when_empty = "true"`: quem
não tem nada a liquidar continua sem um card zerado permanente na tela, exatamente como hoje.

Nenhuma preferência é escrita. Quem já editou mantém o widget antigo, que continua renderizando.

**Alternativa descartada — materializar o padrão atual para quem nunca editou.** Preservaria a
tela ao preço de congelá-la: aquele usuário nunca mais receberia melhoria alguma de layout padrão.

### D6 — Deprecação é um flag no enum, e o preview do deprecado continua vivo

`DashboardComponentType` ganha um flag, e o filtro entra em `availableItems`
(`DashboardViewModel.kt:300`), que é a vitrine.

**Cuidado que o código impõe:** `activeItems` também passa por
`dashboardPreviewFactory.createPreview(pref.key, ...)` e faz `?: return@mapNotNull null`
(`DashboardViewModel.kt:289-290`). Tirar o preview do deprecado faria o widget **sumir do modo de
edição** para quem o tem salvo — inclusive impedindo que ele o removesse ou reordenasse. O preview
do deprecado permanece; o que muda é só a vitrine.

### D7 — As faturas chegam ao builder por um caminho próprio

`invoicesByCreditCardId` não serve: é `associateBy { it.creditCard.id }` sobre lista ordenada
`openingMonth DESC` (`DashboardViewModel.kt:64-66`), ou seja **uma fatura por cartão**. Um cartão
com uma fechada vencendo agora e uma aberta perde uma das duas.

Entra um campo próprio em `DashboardComponentsInput` com as faturas do perímetro. O mapa existente
fica intacto — `creditCardsPager` depende dele.

## Risks / Trade-offs

- **O alcance desigual do "vencido" entre as fontes** → declarado no spec como propriedade da
  figura, não escondido. O widget não afirma somar todo compromisso vencido. Fechar a issue de
  backlog de recorrentes amplia a figura sem mudar nenhuma regra daqui.

- **A troca silenciosa no dashboard de quem nunca editou** → é troca por superconjunto, e o spec
  delta a submete a essa condição explícita. Nenhuma informação some; a figura alarga.

- **Uma quarta leitura enumerando semântica de status** → mitigado por D3, que expressa o critério
  como negação de `PAID` em vez de whitelist, e por concentrá-lo numa única leitura nomeada. A
  contradição global continua aberta e agora tem dois consumidores pedindo o predicado.

- **Dois widgets sobrepostos na tela para quem já editou** (`a liquidar ⊇ pendentes`) → aceito.
  Nada indica ao usuário que os dois não se somam, e a vitrine deixa de oferecer o antigo, então a
  sobreposição não se propaga para dashboards novos.

- **`FUTURE` com vencimento passado entrando na figura** → é o comportamento pretendido (dívida
  real, mês de liquidação chegado), mas é um estado que só ocorre quando o usuário deixou de
  fechar uma fatura. Se aparecer como ruído, o corte é `status != PAID`, num lugar só.

## Open Questions

- O texto exato do título. `A liquidar este mês` / `To settle this month` é a proposta; as classes
  `A entrar` / `A sair` estão decididas. Quatro chaves novas, em `values/` **e** `values-en/`.
- Ícone do widget na vitrine de edição — não levantado, segue o padrão dos demais widgets de par.
