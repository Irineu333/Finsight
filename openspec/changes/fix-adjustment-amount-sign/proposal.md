## Why

Um ajuste de saldo é a única transação bidirecional do app — ele diz *para que lado* o saldo foi corrigido — e é a única cujo valor é exibido **sempre com `+`**, em qualquer direção. `TransactionUiMapper` descarta o sinal da perna com `abs()`, e o `TransactionCard` chama `formatWithSign` sobre um valor já em módulo: o ramo negativo é inalcançável. Aumentar a dívida de um cartão de R$ 0,00 para R$ 100,00 lê `+R$ 100,00`; reduzir o saldo de uma conta também. A modal de ajuste, a linha "Ajustes" do resumo da fatura e o próprio `EntryDao` já tratam o ajuste como valor assinado — só a lista discorda.

A causa é estrutural, não pontual: a decisão "que sinal um valor monetário mostra" está reimplementada em quatro lugares, dois deles com o mesmo defeito. Consertar só o card deixaria o relatório exportado errado e a duplicação de pé.

## What Changes

- `TransactionUiMapper` deixa de aplicar `abs()` e passa a resolver o **valor de exibição** — assinado para ajuste e transferência, magnitude para despesa, receita e pagamento —, mantendo inalterado tudo o que não é ajuste.
- **BREAKING (interno)**: `TransactionUi.amount` deixa de ser `Double` sempre positivo e passa a ser um `DisplayAmount` — valor + política de sinal amarrados. Consumidores fora de `:core:ui` são dois, ambos em `feature/report/impl`.
- Novo tipo `DisplayAmount` em `:core:common`, ao lado de `CurrencyFormatter`: dono único do valor de exibição e da política de sinal (`MAGNITUDE`, `NATURAL`, `EXPLICIT_SIGN`, `FORCED_POSITIVE`, `FORCED_NEGATIVE`). Sem aritmética e sem moeda — ele diz *como se lê*, nunca *quanto vale*, que é do razão.
- `TransactionCard` e `ReportExportLayout.exportAmount` perdem seus `when` duplicados e a concatenação manual de `"-"`; viram uma chamada de formatação.
- `ReportExportLayout.exportTone` passa a acertar o tom do ajuste no relatório exportado — hoje todo ajuste cai em `ReportTone.POSITIVE`, inclusive o que aumenta a dívida, porque o ramo que decide é `amount >= 0` e `amount` é sempre positivo (o ramo `NEGATIVE` é inalcançável).
- Absorção das duas reimplementações existentes da mesma política: o `private enum class AccountSignDisplay` de `AccountCard` e o trio de `Boolean` (`isPositive`/`isNegative`/`showSign`) do `SummaryRow` da fatura. Sem mudança de comportamento — os casos mapeiam 1:1.
- `TransactionUiMapper` passa a usar `Transaction.primaryEntry` em vez de reimplementar a escolha da perna neutra.

## Capabilities

### New Capabilities
- `money-display`: a política de sinal de um valor monetário exibido — quais políticas existem, qual delas cada tipo de figura usa, e a proibição de aritmética/moeda no tipo de exibição.

### Modified Capabilities
- `presentation-mapping`: o valor de exibição de uma **perna de transação** sai no sinal natural do razão (débito-positivo), e não invertido por `AccountType` — a inversão por `displaySign` é regra de **saldo**. Hoje a spec só exemplifica o caso da inversão, o que induz à leitura oposta.

## Impact

**Código**
- `core/common`: novo `DisplayAmount` + extensão de formatação sobre `CurrencyFormatter`.
- `core/ui`: `TransactionUiMapper` (resolução do sinal), `TransactionUi.amount` (tipo), `TransactionCard` (formatação e cor), `AccountCard` (absorve `AccountSignDisplay`).
- `feature/report/impl`: `ReportExportLayout` — `exportAmount` e `exportTone`.
- `feature/creditcards/impl`: `InvoiceTransactionsScreen.SummaryRow` (absorve os três booleanos).

**Telas a verificar** (renderizam `TransactionCard` ou a política absorvida): transações, contas, cartões/extrato da fatura, parcelamentos, dashboard, relatório exportado (tela e HTML/PDF).

**Testes**: `core/ui/.../TransactionPerspectiveTest` codifica a semântica de módulo e será atualizado.

**Fora de escopo, registrado**: `InvoiceTransactionsScreen`, `CreditCardsScreen` e `ReportViewerScreen` chamam `toTransactionUi()` sem perspectiva, então dentro da fatura a transação é lida pela perna `ASSET` e não pela do cartão. É um defeito separado, com efeito visual próprio; misturá-lo aqui tornaria o diff irrevisável.
