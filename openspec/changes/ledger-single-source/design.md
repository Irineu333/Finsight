## Context

A change `balanced-ledger` construiu o razão e o pôs a **escrever** tudo (double-write) e a **ler** os números (saldo, fatura, patrimônio, gasto por categoria). O que ela deliberadamente não fez foi remover a coexistência — documentada em CAP-1..CAP-7 no seu `tasks.md`.

O estado real, verificado no código:

```
ESCRITA                          LEITURA
─────────────────────            ──────────────────────────────────────
OperationRepository              Números  ──▶ EntryDao (SQL agregado, Double)
  ├─▶ TransactionEntity[]        Objetos  ──▶ TransactionEntity[] (100% legado)
  │     (legado, perna)
  └─▶ EntryEntity[]              Entry (modelo) ──▶ retornado por NINGUÉM
        (razão, perna)           Ledger.kt helpers ──▶ só LedgerTest
```

Três fatos governam o desenho:

1. **O razão não é legível como objeto.** `IEntryRepository` só expõe agregados (`balanceUpTo`, `invoiceOwed`, `categoryTotals`) que retornam `Double`. O modelo de domínio `Entry` não é retornado por nenhum repositório de produção, e `naturalBalanceOf`/`deriveOperationLabel`/`isBalanced` são exercitados apenas por `LedgerTest`. Promover o razão a fonte de verdade do grafo é **construção**, não limpeza.
2. **O vocabulário está invertido.** `Transaction` = perna, `Operation` = agregado. O alvo (`Transaction` dona de `Entry[]`) é uma reocupação de nome, e a palavra precisa ser desocupada antes — o que **força a ordem** de todo o trabalho.
3. **A paridade dos leitores legados não tem rede.** O `tasks.md` do `balanced-ledger` admite: *"a paridade dos que continuam legados não é garantida por teste automatizado (verificada em device)"*.

> **Regra de método (imposta pela 5ª rodada).** Antes de este documento afirmar que algo é "código
> morto", "inalcançável" ou "dissolvido", os **callers** têm de ser grepados e **listados aqui**. As três
> correções que falharam na 4ª rodada eram do mesmo tipo: um `arquivo:linha` verdadeiro seguido de
> uma generalização testada contra **um** caller. `PayInvoiceUseCase` tem **quatro** callers (`isPayable`
> tem **um**, e confundir os dois foi o erro); `advancePayment` está vivo em **três** telas. Ambas as
> generalizações teriam caído com um grep de dez segundos — inclusive esta frase, que carregou a
> confusão por quatro rodadas **dentro da regra escrita para preveni-la**. Nenhuma auditoria grepou
> a própria epígrafe.
>
> **Nota de investigação (5 agentes, código real).** Cinco investigações paralelas — ciclo de vida
> de contas, gates de UI, datas/saldos, raio do legado, migração — acharam mais do que três rodadas
> de auditoria, e o que acharam não foram correções: foram **premissas falsas**. As seções abaixo
> foram reescritas contra elas. O que caiu: "espelhar `AdjustBalanceUseCase`" (ele também diverge —
> D17); "patrimônio preservado" como prova (`netWorth` é código morto — D18); "preservar a regra gate
> a gate" (os gates se contradizem entre si — D16); "`Transaction.Type` vira vocabulário de UI"
> (`Recurring` o persiste **e** os nomes das constantes são contrato Firebase — D4); "6.9 remove o
> modelo legado" (**96 arquivos / 493 refs**); "CAP-4 é divergência de data" (os 9 creates passam a
> mesma data; a divergência é de **valor** — D17).
>
> **Nota de revisão (1ª rodada).** A primeira versão deste design afirmava três fatos falsos, todos
> pegos por auditoria adversarial e corrigidos aqui: (a) que testar `EQUITY` antes do `else` corrigia
> a derivação de rótulo — não corrige, ver D3; (b) que restavam **quatro** leitores legados — são
> **seis**, ver D11; (c) que a deleção da conta fantasma era protegida pela FK — não é, o Room
> desliga FK em migração, ver D10. Os erros não estavam nas partes difíceis: estavam onde o texto
> se elogiava em vez de se provar. As afirmações abaixo foram reverificadas contra o código.

## Intenção (síntese, não citação)

> Esta seção existe porque as sete primeiras rodadas de auditoria receberam os objetivos **colados
> literalmente**, e um deles o usuário já havia emendado. Os revisores julgaram o D13 contra uma meta
> superada — não por erro deles, mas porque o enunciado que receberam estava desatualizado. A intenção
> mora aqui, é mantida aqui, e é daqui que qualquer auditoria deve ser instruída.

**O que se quer construir.** Um app de finanças pessoais cujo **motor** é um razão de partidas dobradas pleno e cuja **superfície** continua sendo a de um app simples. *"Simples a nível de usuário, completo a nível de engenharia"* não é slogan — é o critério de aceitação, e ele corta nas duas direções: o usuário nunca deve ver débito/crédito nem saber que "categoria" é uma conta `EXPENSE`; o engenheiro nunca deve encontrar um caso especial, um sinal invertido ad-hoc, ou um número que não fecha.

**O que "manter comportamento" realmente quer dizer.** Que **o usuário não perceba o refactor**. Não é congelar pixels, e nunca foi: o mesmo usuário que pediu isso também pediu *"implemente o double entry corretamente"* e *"a change termina com uma arquitetura limpa"*. A regra que concilia as duas:

| | |
|---|---|
| **Correção deliberada** | bem-vinda — é o objetivo. Encerrar em vez de apagar; unificar regras que hoje divergem por tela |
| **Deriva acidental** | defeito — número que muda em silêncio, botão que some, fluxo que quebra como efeito colateral |

A prova está no histórico: ao descobrir que apagar uma conta fazia dinheiro evaporar do patrimônio sem contrapartida, ele **não pediu para preservar — pediu para consertar**. E quando suspeitei que o delete estava quebrado, ele rodou e trouxe o `SQLiteException 787`. Ele quer o certo, e verifica.

**Vocabulário.** `Transaction` é o agregado — a palavra que o usuário do app usa. `Entry` é a perna — a palavra da contabilidade. `Operation` não deve existir: é vocabulário invertido, e o app não deve ter dois nomes para a mesma ideia. A UI recebe DTO plano; domínio não entra em modelo de UI.

**Fonte de verdade.** O razão, para os números **e** para o grafo de objetos. Não coexistência, não "metade migrado". A change termina **sem resíduo** — nenhum modelo, tabela, coluna ou helper legado sobrevive.

**Migração.** `v7 → v9` direto, porque a v8 nunca foi para produção. Não herdar dois erros para depois corrigi-los.

**Como o usuário trabalha** (importa para calibrar o que lhe é levado): decide rápido e espera execução — quando discorda de uma recomendação, ele diz e quer que se siga assim mesmo. Valoriza **verificação acima de autoridade**: elogiou quando um erro do próprio revisor foi pego em vez de propagado. Quer **mapeamento completo**, não amostragem. E as perguntas dele pegam erro de **moldura**, não de detalhe — foi ele quem notou que a decisão de encerrar tinha sido silenciosamente reenquadrada como preservação.

## Goals / Non-Goals

**Goals:**
- O razão como única fonte de verdade, dos agregados **e** do grafo de objetos.
- `Transaction` como agregado dono de `List<Entry>`; `Operation` e a perna legada removidos.
- Comportamento idêntico ao usuário **onde ele não for deliberadamente corrigido** — o que muda é o nome, não a tela. **Duas exceções sancionadas, que não são regressões e sim o objetivo:**
  1. **Encerrar em vez de apagar** (D13) — decisão explícita do usuário: *"implemente o double entry **corretamente**"*. Apagar conta com lançamentos hoje **crasha** (`SQLiteException 787`, confirmado em runtime) e, no v7, fazia dinheiro evaporar do patrimônio sem contrapartida. Encerrar muda a tela: a conta sai das listas mas permanece no plano de contas, e um lançamento de baixa datado passa a existir no histórico. O patrimônio final coincide, mas **isso é consequência, não justificativa**.
  2. **As divergências de §4b** (D16) — não existe um comportamento único a preservar: `isClosable` tem regra diferente por tela, `isPayable` nunca é oferecida na UI, `ViewAdjustmentModal` apaga sem gate. Derivar do razão produz **uma** regra, e ela muda pelo menos uma tela. Nove decisões de produto pendentes.

  *Fora dessas duas, qualquer mudança de comportamento é regressão.* O que **não** está sancionado, e continua sendo defeito a evitar: efeitos colaterais das exceções acima — ex.: meses anteriores à baixa passarem a incluir a conta encerrada via `assetsBalanceUpToMonth` (D18/risco #1). Sancionar "encerrar" não sanciona isso.
- Modelos de UI planos; mappers como a única fronteira domínio→apresentação.
- Terminar com uma arquitetura sem resíduo: nenhum modelo, tabela, coluna ou helper legado sobrevive à change.

**Non-Goals:**
- Reembolso/estorno, FX/câmbio, investimentos (habilitados, não implementados).
- Mudar o modelo de entrada do usuário: o form continua oferecendo despesa/receita/ajuste e conta/cartão.
- Otimizar leitura (projeção CQRS, query agrupada do dashboard do CAP-1) — decisão de valor, não desta change.
- Fechar o CAP-3 (`ensureSystemAccount` check-then-act) — risco próprio, deliberadamente separado.

## Decisions

### D1 — A colisão de nome força a ordem; o rename é o último passo, não o primeiro

`Operation` só pode virar `Transaction` depois que a `Transaction` legada morrer. Isso não é preferência: é o compilador. E a mesma colisão existe no SQL — `operations` só vira `transactions` depois do `DROP TABLE transactions`.

```
A. Razão legível + agregados     Entry hidratada, observeEntries, agregados por conta/mês (D12)
   └─▶ B. Virar os 6 leitores    AccountUi, ViewCategory, budgets, CalculateBalance, dashboard, relatório
       └─▶ C. Fim do double-write, drop da tabela legada + v9 única (D14)
           └─▶ D. Rename Operation→Transaction + TransactionUi   ← o objetivo, e o mais barato

Fora da cadeia (paralelizáveis a qualquer momento):
  · correções independentes (AdjustInvoice, isMonetary, rótulo total)
  · UI plana (§5), exceto AccountUi, que depende de B
```

A cadeia A→B→C→D é real, mas **não é uma linha reta**: §1 e §2 são independentes entre si, §5 só depende de B no ponto do `AccountUi`, e a correção do `AdjustInvoiceUseCase` não depende de nada. A primeira versão deste design vendia mais serialização do que as dependências exigem — o que importa porque a change é grande e a paralelização é o que a torna executável.

**Alternativa considerada:** renomear cedo (`Transaction` legada → `Leg`/`LegacyLeg`) para desbloquear D antes. Rejeitada: renomeia ~15 sites para um nome descartável, e a churn é paga duas vezes.

### D2 — A editabilidade é uma regra de **cinco** gates, não um; `isEditable` é só um deles — e há uma regra de **remoção** que ninguém tinha olhado

`Operation.isEditable = transactions.size == 1` **não é** a regra de editabilidade. Ler o `ViewOperationModal` **inteiro** (502 linhas, em vez de trechos) revelou dois níveis de porteiro, não um.

**Nível 1 — status da fatura (`:353-370`), que gate *Editar* E *Apagar*:**

```kotlin
content.transaction.invoice?.let { invoice ->
    when (invoice.status) {
        FUTURE, OPEN, RETROACTIVE -> EditAndDelete(content)
        CLOSED, PAID -> Text("mensagem de fatura fechada")   // NEM editar NEM apagar
    }
} ?: run { EditAndDelete(content) }
```

Um lançamento em fatura `CLOSED`/`PAID` **não pode ser editado nem apagado**. As rodadas 1-3 não mencionaram remoção em nenhum artefato — a spec fala só de editabilidade. **Existe uma regra de deletabilidade e ela nunca foi especificada.**

E há duplicação: esse `when` é `Invoice.Status.isEditable` (`Invoice.kt:68-71`) **reimplementado inline**, e `InvoiceTransactionsUiState:39` (`canEdit = status.isRetroactive || status.isOpen || status.isFuture`) é uma **terceira** cópia do mesmo predicado. Mesma doença de `initialBalance` (3 impls) e do saldo (3 impls): o predicado existe, e os consumidores o reescrevem.

**Nível 2 — os quatro gates de *Editar*, que existem em DUAS cópias (`:417-421` e `:388-392`):** a segunda governa a largura do botão Apagar (`fillMaxWidth()` vs `weight(1f)`). Ler o arquivo inteiro não impediu de perder a cópia 30 linhas acima — a task 5.5 tem de consertar **as duas**.

```kotlin
when {
    uiState.transaction.type == Transaction.Type.ADJUSTMENT -> Unit            // ajuste: sem Editar
    !uiState.operation.isEditable -> Unit                                      // ← o único que as rodadas 1-2 viram
    uiState.operation.installment != null -> Unit                              // parcelado: sem Editar
    uiState.transaction.target == CREDIT_CARD && creditCard == null -> Unit    // perna órfã de cartão: sem Editar
    else -> { /* botão Editar */ }
}
```

As rodadas 1 e 2 deste design encontraram os call sites de `isEditable`, concluíram que ele *era* a regra, e construíram sobre isso uma tabela de bijeção que afirmava **"ajuste é editável ✓"** — falso nas duas formas de ajuste, porque o primeiro ramo já esconde o botão. A afirmação "a bijeção preserva comportamento **por construção**" foi feita duas vezes sobre uma regra lida pela metade. A lição não é sobre contabilidade: é que **um predicado de domínio não é a regra de UI só porque tem o nome dela**.

A regra real, e a sua tradução para o razão:

| Gate hoje | Porteia | No razão | Cobre |
|---|---|---|---|
| `invoice.status ∈ {CLOSED, PAID}` | **editar + apagar** | inalterado (`Invoice.Status.isEditable`, usado em vez de reescrito) | fatura fechada/paga |
| `type == ADJUSTMENT` | editar | `label == ADJUSTMENT` (D3) | ajuste de conta, de fatura **e** a baixa (D13) |
| `!isEditable` (`size == 1`) | editar | `entries.count { it.account.type.isMonetary } != 1` | transferência, pagamento |
| `installment != null` | editar | inalterado | parcelamento |
| perna órfã de cartão (`creditCard == null`) | editar | **permanece necessário até §6.9** | testa a **fachada `CreditCard`**, não a `Account` — apagar `credit_cards` não apaga a `accounts` (a FK aponta ao contrário), então "nenhuma entry referencia conta inexistente" **já é verdade hoje** e nada tem a ver com este gate. Ele dissolve por outro motivo: §6.9 remove `Transaction.creditCard` |

Sobre o gate que sobrevive como contagem, a tradução é bijetiva porque, no modelo antigo, **categoria e contrapartida de ajuste eram fantasmas** — nunca foram linhas em `transactions`. A contagem antiga já era, sem saber, a contagem de pernas *de dinheiro*:

| Forma | Legado | Razão | Pernas monetárias | 1 perna? |
|---|---|---|---|---|
| Despesa/receita em conta | 1 | ASSET + EXPENSE/INCOME | 1 | ✓ |
| Compra no cartão | 1 | LIABILITY + EXPENSE | 1 | ✓ |
| Ajuste de saldo (conta) | 1 | ASSET + EQUITY | 1 | ✓ |
| Ajuste de fatura (cartão) | 1 | LIABILITY + EQUITY | 1 | ✓ |
| Lançamento de baixa (D13) | — | ASSET encerrada + EQUITY | 1 | ✓ |
| Transferência | 2 | ASSET + ASSET | 2 | ✗ |
| Pagamento / antecipação | 2 | ASSET + LIABILITY | 2 | ✗ |

As três primeiras colunas casam nas sete formas. Os ajustes e a baixa passam neste gate e são barrados pelo **gate do rótulo** — que é o mesmo comportamento de hoje, onde eles passam em `isEditable` e são barrados pelo ramo `ADJUSTMENT`. **A estrutura da regra é preservada gate a gate, não só o resultado.**

A enumeração vem dos use cases reais: `AdjustBalanceUseCase`, `AdjustInvoiceUseCase`, `TransferBetweenAccountsUseCase`, `PayInvoicePaymentUseCase`, `AdvanceInvoicePaymentUseCase` (7º use case, esquecido nas rodadas 1 e 2 — mesma forma do pagamento, mas `AccountUi.kt:55` tem `advancePayment = 0.0` **hardcoded**, e os agregados do D12 precisam cobri-lo), `AddInstallmentUseCase` e `ConfirmRecurringUseCase`.

Introduz-se `AccountType.isMonetary` (`ASSET`/`LIABILITY`), ao lado de `isDebitNatured`:

```
ASSET, LIABILITY          → onde o dinheiro ESTÁ       ← o usuário escolhe. Eram as pernas.
INCOME, EXPENSE, EQUITY   → por que ele SE MOVEU       ← o writer sintetiza. Eram fantasmas.
```

**Ressalva do D13:** "fantasma" descreve o modelo **legado**, não o alvo. Com o encerramento, `EQUITY:Reconciliação` passa a ser a perna que **registra** a saída do patrimônio numa baixa — real e auditável, não sintetizada. A caracterização vale para a genealogia, não para o papel futuro.

**Alternativa considerada:** inventar uma regra nova de editabilidade. Rejeitada — e a rejeição agora é mais forte: a regra existente já cobre os casos que pareciam precisar de regra nova, incluindo a baixa do D13.

### D20 — Não existe mapper puro `intenção → entries`: o writer **é** a fronteira

O D4 desenha `UI (form) → mapper: intenção → entries → Transaction { entries }` como o coração da change, e a §9 abriu três tasks disputando essa responsabilidade sem dizer quem é dono. Ao tentar especificá-las, o objeto prescrito **não pode existir**: resolver a contrapartida de uma perna tem **efeito colateral**. `LedgerEntryWriter.ensureCategoryAccount:115-132` faz `accountDao.insert(...)` + `categoryDao.update(...)`; `ensureCardAccount:134-148` e `ensureSystemAccount:150-155` idem — as contas de categoria, cartão e sistema **nascem sob demanda na primeira escrita** (não há seed no fresh-install).

Logo a fronteira é:

```
TransactionForm (Type, Target, conta/cartão, categoria, valor, data)   ← vocabulário de entrada (D4)
      │  BuildTransactionUseCase — valida e normaliza a intenção. SEM DAO.
      ▼
Intent { conta monetária escolhida, contrapartida pretendida, valor, data, invoice? }
      │  LedgerEntryWriter — resolve as contas (efeito colateral: ensure*), monta e valida as entries
      ▼
List<Entry>  Σ=0 por moeda
```

`LedgerEntryWriter` **é** o tradutor intenção→entries; não há mapper puro a criar. O que muda nele é o **tipo de entrada**: hoje `List<Transaction>` (perna legada, com `Type`/`Target`/`signedCents`), passa a ser a intenção. `BuildTransactionUseCase` já é hoje `(form: TransactionForm) → Either<Throwable, Transaction>` — ou seja, já é o normalizador da intenção; o que muda é o seu tipo de saída.

**Consequência para o CAP-3:** como o `ensure*` continua sendo check-then-act sem índice único, e ele fica **no** writer, o CAP-3 permanece exatamente onde estava — nem melhora nem piora. Segue Non-Goal, agora com o motivo escrito.

### D16 — Não existe "o comportamento atual": os gates se contradizem entre si hoje

O objetivo #2 do usuário é *"manter comportamento a nível de experiência de usuário"*. A investigação de gates mostra que **isso pressupõe um comportamento único que não existe**. A mesma ação tem regras diferentes conforme a tela:

| Divergência | Evidência | Consequência |
|---|---|---|
| `Invoice.isPayable` (`CLOSED\|RETROACTIVE`, `Invoice.kt:35-39`) é usada **no domínio** (`PayInvoiceUseCase:42`) e **nunca na UI** (`InvoiceTransactionsScreen:543`, `CreditCardsScreen:477` usam `isClosed`; `CreditCardCard:302` usa `== Invoice.Status.CLOSED` cru — 5ª cópia do predicado) | — | **divergência real, não código morto.** `PayInvoiceUseCase` tem **4 callers** (`PayInvoiceViewModel:70`, `CloseInvoiceUseCase:53`, `CloseInvoiceUseCase:72`, `PayInvoicePaymentUseCase:75`) — o ramo `RETROACTIVE` é **vivo e load-bearing** via `CloseInvoiceUseCase:53`, que é o que faz fechar fatura retroativa funcionar. O domínio permite pagar retroativa; a UI nunca oferece |
| `CloseInvoiceUseCase` **paga em vez de fechar** em dois ramos | `:52-57` (retroativa → `payInvoiceUseCase`) e `:71-76` (fatura zerada → idem), sob o botão rotulado "fechar fatura" | o domínio nunca consulta `isClosable`; gateia por `!= PAID`, `!= CLOSED` e `closedAt.yearMonth == closingMonth`, aceitando fechar `FUTURE`, que nenhuma UI oferece |
| Reabrir: domínio ⊃ UI | `ReopenInvoiceUseCase:25,29` permite CLOSED/FUTURE/RETROACTIVE; UI só `isClosed` | idem |
| `Invoice.isClosable` | `InvoiceUi:25` ignora a data p/ `RETROACTIVE`; `InvoiceTransactionsViewModel:147` exige | **o botão "Fechar" tem regra diferente** no dashboard e na tela de fatura |
| Remoção de lançamento | `ViewOperationModal:353-370` bloqueia em `CLOSED`/`PAID` (reenumerando à mão o complemento exato de `Invoice.Status.isBlocked`); **`ViewAdjustmentModal:228-256` não bloqueia nada**; e **todo gate de remoção é só de UI** — `DeleteTransactionViewModel:19-23` e `DeleteInstallmentViewModel:23-30` apagam sem gate algum (só `DeleteFutureInvoiceUseCase:24` tem gate de domínio) | dois modais, duas regras — e a regra não existe no domínio |
| Bloco de ações de fatura | 3 cópias (`InvoiceTransactionsScreen`, `CreditCardsScreen`, `CreditCardCard`) com gates divergentes | só uma oferece "apagar fatura futura" |
| Cor/rótulo de `Transaction.Type` | `INCOME` → `BillPaymentColor` + "pagamento" (`InvoiceTransactionsScreen:790,802`); `INCOME` → `Income` + "receita" (`InstallmentsScreen:713,725`); `AccountsScreen:625` não tem arm de cor para `ADJUSTMENT` mas **renderiza o rótulo** em `:637` — chip com rótulo e sem cor | o mesmo enum é 3 coisas, e `TypeFilterChip` tem **5 cópias** (+ `CreditCardsScreen:662,674`, `TransactionsScreen:293-295`) |
| `InstallmentUiMapper:42-44` | chama o campo de `isDeletable` mas o calcula com `status.isEditable` | colide com `Invoice.Status.isDeletable`, que existe e significa outra coisa |

**Consequência para esta change:** "preservar gate a gate" é irrealizável enquanto os gates discordam. Ao derivar a regra do razão, uma única regra sai — e ela necessariamente **muda o comportamento de pelo menos uma das telas divergentes**. Qual das versões é a correta é **decisão de produto**, não de refactor, e precisa ser tomada **antes** do apply, caso a caso. Esta é a maior mudança de escopo que a investigação produziu, e não é técnica.

### D17 — Os dois `Adjust*UseCase` estão quebrados, de doenças diferentes; e o CAP-4 mirava o alvo errado

A rodada 1 identificou `AdjustInvoiceUseCase:74` como bug e prescreveu *"espelhar `AdjustBalanceUseCase:76-85`"*. **`AdjustBalanceUseCase` também está quebrado:** quando `operationId != null` ele chama **só** `updateOperation`, e `OperationRepository.updateOperation:292-307` **nunca toca a tabela `transactions`** — logo o razão recebe o valor novo e a linha legada mantém o valor velho. E como o próprio use case calcula `difference = targetBalance − currentBalance` a partir do **razão**, o legado **nunca converge**: a divergência é permanente. A prescrição mandava copiar um bug.

Isso também reenquadra o **CAP-4**: os **9** sites de criação passam a mesma data para os dois modelos (verificado um a um) — não há divergência de data no create. A divergência real é de **valor**, no update, pelo caminho acima. A task de "garantir a invariante de data na escrita" perseguia um problema que não existe, e ignorava o que existe.

### D18 — `netWorth` e `advancePayment` são código morto; specs e agregados apoiados neles não provam nada

- **`netWorth` não tem consumidor de produção** (só impl, interface e testes). O cenário de spec *"Patrimônio preservado"*, usado como prova de que encerrar não muda comportamento, **testa uma query que ninguém chama**. Também explica por que a ausência de corte de data em `netWorthCents` (`EntryDao:76-81`) nunca produziu bug.
- **`advancePayment` é `0.0` hardcoded** (`AccountUi:55`) e `AccountCard:212-217` só renderiza `if (advancePayment != 0.0)` — **a linha nunca aparece**. E `AccountUi` **estruturalmente não consegue** distinguir antecipação de pagamento comum: as duas pernas `ASSET` carregam `invoice != null`. **Correção (5ª rodada): `advancePayment` só é morto no `AccountUi`.** Callers grepados: **vivo e renderizado** em `InvoiceTransactionsViewModel:104,141` → `InvoiceTransactionsScreen:412`; `ReportViewerViewModel:85,97` → `ReportContextCard:235` e `ReportExportLayout:94`; `CalculateInvoiceOverviewsUseCase:23,35,45`. **Morto** em exatamente dois lugares: `AccountUi:55` (`0.0` hardcoded → `AccountCard:212-217` inalcançável) e `TransactionsUiState:48,52` (nunca atribuído). A versão anterior mandava "remover `advancePayment`" — isso apagaria uma feature viva em três telas. O que sai são os dois sites mortos; o resto não se toca. E o agregado do D12 **não** deve ser criado para ele: `AccountUi` estruturalmente não distingue antecipação de pagamento (ambas as pernas `ASSET` carregam `invoice != null`), então acendê-lo no `AccountUi` seria decisão de produto, não preservação.
- **`Ledger.kt:21,26`** (`displaySign`/`displayBalance`) também só são exercitados por `LedgerTest` — nenhuma UI os consome. A inversão de sinal por `AccountType` que o D5 atribui aos mappers **ainda não existe em lugar nenhum**.

### D19 — A FK está ligada em runtime e desligada durante a migração; ambas verificadas

Ponto de confusão resolvido em definitivo, porque três afirmações se apoiavam nele:

- O código **gerado** (`AppDatabase_Impl.kt`, todas as plataformas) tem `onOpen { PRAGMA foreign_keys = ON }`. `RoomConnectionManager.kt:136-140` (`configurationConnection`) o reaplica **por conexão**. → **FK ligada em runtime**, o que explica o `SQLiteException 787` observado e confirma que `ON DELETE SET NULL` dispara: **as órfãs do v7 são NULL-shaped**, e a premissa da migração está correta.
- `RoomConnectionManager.kt:105-130` (`configureDatabase`): `BEGIN EXCLUSIVE` → `onMigrate` → `END` → **`onOpen`**. O `onOpen` roda **depois** da migração, logo durante ela a FK está no default do SQLite (**OFF**). → o `DROP TABLE operations` do passo 0 **não cascateia**; a migração **não** apaga transações.
- Corolário: o `PRAGMA foreign_keys=OFF/ON` da `MIGRATION_7_8` (`Database.kt:242,268`) é **inócuo** — é no-op dentro da transação do Room, e a FK já estava off. O D10 acertou que a FK não protege o `DELETE` da conta fantasma, e acertou pelo motivo errado.

### D15 — São **dois** eixos de exibição, não um: o rótulo da transação e a direção da perna

O D3 (rodadas 1-3) prescrevia *"uma única derivação de rótulo; MUST NOT coexistir uma segunda classificação"*, fundindo `Operation.Kind` e `Transaction.Type` em `TransactionLabel`. **Ler o modal inteiro refuta isso:** `:169-189` exibe os dois **ao mesmo tempo, em linhas diferentes**.

```kotlin
Text(when (uiState.transaction.type) {        // eixo 1 — direção da PERNA da perspectiva
    INCOME -> "Receita";  EXPENSE -> "Despesa";  ADJUSTMENT -> "Ajuste"
}, color = uiState.operationColor())

Text(when (uiState.operation.kind) {          // eixo 2 — natureza da OPERAÇÃO (é o título)
    PAYMENT -> "Pagamento de cartão";  TRANSFER -> "Transferência";  else -> operation.label
})
```

Um pagamento de fatura mostra, simultaneamente, **"Despesa"** (a perna da conta sai) e o título **"Pagamento de cartão"**. Uma transferência mostra **"Despesa"** e o título **"Transferência"**. Os eixos são ortogonais: o primeiro é uma propriedade da **perna sob a perspectiva atual**; o segundo, da **operação inteira**.

A cor (`operationColor()`, `:495-501`) é single-axis e segue o eixo 2 quando ele existe (`PAYMENT`→`InvoicePayment`, `TRANSFER`→`Info`), caindo no eixo 1 caso contrário — isto é, ela é exatamente o `TransactionLabel` do D3.

Logo o D3 estava **certo para a cor e errado como unificação**. O razão precisa derivar **duas** coisas:

| Derivação | De onde | Para quê |
|---|---|---|
| `TransactionLabel` (operação) | tipos de conta de **todas** as entries | cor, título, ícone, gate de edição |
| direção da perna (`EXPENSE`/`INCOME`/`ADJUSTMENT`) | sinal da entry **da perspectiva** + contrapartida `EQUITY` | o texto de tipo, o filtro da lista |

Ambas são deriváveis — `deriveTransactionType` (já escrito e testado) faz a segunda. O erro não era técnico: era declarar "uma só" sem ter lido o que a tela mostra. **O CAP-6 dizia que as duas derivações "convergem"; elas não convergem nem se fundem — elas coexistem, com propósitos distintos, e é a spec desta change que estava errada ao proibir a segunda.**

### D3 — Uma função total de rótulo, com `EQUITY` avaliado **antes de qualquer outro caso**

Hoje coexistem duas derivações parciais (CAP-6), e **nenhuma cobre a união**:

```
Transaction.Type   { EXPENSE, INCOME, ADJUSTMENT }          ← por perna. Sem PAYMENT/TRANSFER.
OperationLabel     { EXPENSE, INCOME, TRANSFER, PAYMENT }   ← por operação. Sem ADJUSTMENT.
```

`deriveOperationLabel` (`Ledger.kt:36-41`) testa, nesta ordem: `EXPENSE` → `INCOME` → `LIABILITY` → `else`. Isso produz **dois** erros, não um:

| Operação | Entries | Rótulo atual | Por quê |
|---|---|---|---|
| Ajuste de saldo | `{ASSET, EQUITY}` | `TRANSFER` ❌ | nenhum caso casa → `else` |
| Ajuste de fatura | `{LIABILITY, EQUITY}` | `PAYMENT` ❌ | `LIABILITY` casa antes do `else` |

O segundo é a lição: **testar `EQUITY` "antes do `else`" não conserta nada**, porque o `else` nunca é alcançado. A ordem correta é `EQUITY → EXPENSE → INCOME → LIABILITY → else` — `EQUITY` **antes de qualquer outro caso**, porque a presença de uma contrapartida de reconciliação determina a natureza da operação independentemente de onde o dinheiro esteja.

Verificado em `LedgerEntryWriter.kt:94-104`: `realAccountId` para `target == CREDIT_CARD` devolve a conta `LIABILITY` do cartão, e `contraAccountId` para `type == ADJUSTMENT` devolve `EQUITY:Reconciliação` — logo o ajuste de fatura é `{LIABILITY, EQUITY}` por construção.

`TransactionLabel = { EXPENSE, INCOME, ADJUSTMENT, TRANSFER, PAYMENT }`, função total.

### D4 — `Transaction.Type` **e** `Transaction.Target` sobrevivem como vocabulário de entrada

O CAP-5 concluiu que `Type` "não é dívida removível: é entrada do usuário e classificação de exibição". Correto sobre o **papel**, errado sobre **onde ele mora**. O mesmo vale para `Target`, e a 1ª versão deste design aplicou o argumento a um e não ao outro, sem justificar a assimetria:

| | O que é | Onde mora hoje | Destino |
|---|---|---|---|
| `Transaction.Type` | despesa/receita/ajuste — o usuário escolhe no form | campo da perna + coluna | vocabulário de UI; a coluna morre com a tabela |
| `Transaction.Target` | conta/cartão — o usuário escolhe no form (`TargetSelector`) e **filtra pela rota** | campo derivado da perna + `TransactionsRoute(filterTarget)` `@Serializable` + `TransactionTargetNavType` | vocabulário de UI; a derivação morre |

`Target` está em **42 arquivos**, incluindo um contrato de navegação serializável. Só a sua **materialização como campo da perna** é redundante: `ASSET` vs `LIABILITY` já a determina. Removê-lo do domínio é limpeza; removê-lo do app seria mudar a UX.

```
UI (form + rota)  Type {EXPENSE, INCOME, ADJUSTMENT} · Target {ACCOUNT, CARD}   ← input. Fica.
    │  mapper: intenção → entries (escolhe a conta monetária e a contrapartida)
    ▼
Transaction { entries }                          ← domínio. Sem classificação persistida. Σ=0.
    │  mapper: entries → TransactionLabel        ← função TOTAL (D3)
    ▼
TransactionUi { label, direction, amount, … }    ← exibição. **Dois** eixos derivados (D15), não um.
```

O vocabulário de input é **subconjunto** do de exibição — achatar os dois seria o erro simétrico ao atual.

### D5 — Modelos de UI são DTOs planos; a perspectiva vira argumento do mapper

Nenhum modelo de UI carrega grafo de domínio — no máximo um id. Isso mata o `requireNotNull` de `OperationUi.transaction` **dissolvendo-o**: a resolução deixa de ser um `by lazy` que estoura na leitura e passa a acontecer no mapper, onde falhar é tratável.

```
HOJE                                       ALVO
──────────────────────────────             ──────────────────────────
AccountUi(                                 AccountUi(          ← plano
  account: Account,        ← domínio         id, name,
  transactions: List<...>, ← domínio         openingBalance, balance, …
) { …5 somas no construtor }               )

OperationUi(                               TransactionUi(      ← plano
  operation: Operation,    ← domínio         id,
  perspective: …,                            label: TransactionLabel,   ← eixo 2 (operação)
) { transaction by lazy requireNotNull }     direction: …,              ← eixo 1 (perna da perspectiva)
                                             amount, date, …
                                           )
```

`AccountUi` é o caso exemplar: guarda domínio e faz **seis** somas num construtor secundário (`AccountUi:27,30,35,42,47,54` — a versão anterior dizia cinco em três artefatos). Consequência estrutural: `core/ui/model` deixa de importar domínio, e a regra de camada "Domain ← UI" vira **fato verificável** em vez de convenção.

### D6 — `TransactionPerspective` colapsa para data class, com fallback para cartão sem conta

`OperationPerspective` bifurca em `Account(accountId)` e `Card(creditCardId, invoiceId?)` **só porque a perna legada tinha duas formas**. Uma `Entry` tem uma forma: `accountId` + `invoiceId?`. As variantes colapsam em `TransactionPerspective(accountId, invoiceId? = null)`, com o cartão entrando via `CreditCard.accountId`.

**Ressalva que a 1ª versão omitiu:** `CreditCard.accountId` é **nullable** (`CreditCard.kt:19`) — a conta do cartão é criada sob demanda (`ensureCardAccount`, `LedgerEntryWriter:133-148`) e a FK é `ON DELETE SET NULL`. Um cartão sem nenhuma operação tem `accountId == null` e a perspectiva é inconstruível, coisa que `OperationPerspective.Card(creditCardId)` não sofria. Fallback declarado: **um cartão sem conta de razão não tem entries, logo a perspectiva resolve para vazio** — semanticamente correto (não há o que mostrar), mas precisa ser explícito no mapper e coberto por teste, e não um NPE.

### D7 — Listas hidratam o domínio e mapeiam; sem projeção de leitura

As telas continuam hidratando `Transaction` (com entries + accounts) e mapeando para DTO, em vez de o DAO projetar linha plana direto.

**Alternativa considerada:** projeção de leitura (domínio só na escrita, CQRS-lite). Rejeitada **por ora**: `observeAllOperations` já hidrata as pernas hoje, então a forma escolhida não é regressão de custo nem de estrutura; a projeção compraria complexidade e um segundo modelo de leitura para resolver um problema de performance que não foi medido.

### D8 — `SystemAccount.INITIAL_BALANCE` é fantasma e sai; `initialBalance` vira `openingBalance`

Duas coisas diferentes carregam o nome "saldo inicial":

| | O que é | Referências em produção |
|---|---|---|
| `SystemAccount.INITIAL_BALANCE` | conta `EQUITY` semeada pela `MIGRATION_7_8` | **zero**. Callers grepados de `SystemAccount.*` em produção: `RECONCILIATION` (`LedgerEntryWriter:104`), `UNCATEGORIZED_EXPENSE` (`:108`), `UNCATEGORIZED_INCOME` (`:112`) — **três**, não uma, como a versão anterior generalizava sem grepar. `INITIAL_BALANCE` e `REMOVED_ACCOUNT`: zero |
| `AccountUi.initialBalance` | saldo até o mês anterior — derivado, corte por data | `AccountCard`, `SummaryCard`, `TransactionsUiState`, `ReportStats`… |

A migração semeou uma conta para um conceito que o app não tem.

O `initialBalance` real é o *saldo de abertura do período*, com **três implementações independentes**: `AccountUi.kt:25`, `CalculateBalanceUseCase.kt:19-23` e `CalculateReportStatsUseCase.kt:41`. (A 1ª versão deste design citava `TransactionsViewModel:73` como a terceira — errado: aquilo é um *call site* do use case, não uma implementação. O número acertava, o dedo apontava para o lugar errado.) Renomear para `openingBalance` diz a verdade e libera "saldo inicial" caso um dia vire feature.

### D9 — Testes de caracterização antes de virar cada leitor

Cada leitor legado é virado em dois passos: **(1)** teste que captura os números atuais (produzidos pelo caminho legado) com dados representativos; **(2)** troca da implementação, com o teste inalterado passando. Sem isso, "manter comportamento" é fé, não engenharia.

### D10 — ~~Migração v9 atômica com deleção verificada~~ **REVOGADA PELO D14**

> Esta decisão previa `RENAME COLUMN` e a deleção da conta fantasma `'Saldo Inicial'`. O **D14** a tornou
> sem objeto: a `MIGRATION_7_9` não semeia `'Saldo Inicial'` (logo não há o que deletar) e `entries.transactionId`
> nasce com o nome final (logo não há `RENAME COLUMN`). Mantida apenas como registro; **nada nela é normativo**.
> Os resíduos que ela deixou nas specs foram removidos.

#### (histórico) Migração v9: uma versão, atômica, com deleção verificada por contagem

A 1ª versão deste design separava o rename da tabela (§6) do rename da entity (§7). Isso **quebra o app**: entre os dois passos o Room valida o schema contra uma entity que aponta para uma tabela inexistente, e o banco não abre. O Room valida em **runtime**, não em compile time — a "ordem forçada pelo compilador" do D1 não governa aqui. Os três renames são um único passo:

```sql
-- 1. a tabela legada morre                 DROP TABLE transactions;
-- 2. só então o nome fica livre            ALTER TABLE operations RENAME TO transactions;
-- 3. a FK do razão acompanha               ALTER TABLE entries RENAME COLUMN operationId TO transactionId;
-- 4. o fantasma sai — se e só se órfão      DELETE FROM accounts WHERE type='EQUITY' AND name='Saldo Inicial'
--                                             AND NOT EXISTS (SELECT 1 FROM entries WHERE accountId = accounts.id);
```

…acompanhados, no mesmo commit, do rename de `OperationEntity` → `TransactionEntity` (e do seu `tableName`), `OperationDao` → `TransactionDao` e `Entry.operationId` → `transactionId`, com **um único** `9.json` exportado. Sem o passo 3, ou nasce uma `MIGRATION_9_10` não prevista, ou a varredura de resíduo (8.2) falha por construção — `entries.operationId` carrega o nome que a change promete eliminar.

**Correção da mitigação do passo 4.** A 1ª versão afirmava que a deleção era segura *"porque a FK `NO_ACTION` abortaria se houvesse referência"*. **Falso:** o Room desliga FK durante migrações, e a própria `MIGRATION_7_8` o faz explicitamente (`Database.kt:242` `PRAGMA foreign_keys=OFF`, `:268` `=ON`). Com FK desligada o `DELETE` passaria em silêncio e deixaria `entries.accountId` pendurado — o oposto da proteção prometida. A garantia tem de estar no próprio SQL (`NOT EXISTS`, acima) e num assert de contagem no teste, nunca na FK.

### D13 — Encerrar em vez de apagar: remover a mentira em vez de ensinar o modelo a conviver com ela

A 2ª rodada de auditoria expôs que `'Conta removida'` é semeada como **`EQUITY`** (`Database.kt:330`) e **recebe pernas**: `Database.kt:339-353` roteia para ela, via `COALESCE`, toda perna órfã de conta/cartão apagado. Logo uma despesa órfã migrada tem entries `{EQUITY:'Conta removida', EXPENSE:categoria}` — e a regra "`EQUITY` ⇒ `ADJUSTMENT`" do D3 a rotularia **ajuste** e a tornaria **não editável**, quando hoje é despesa editável. Regressão real, em dados reais.

A 1ª reação foi acomodar o caso especial (predicado por identidade de conta, ou um tipo `TOMBSTONE`). **Decisão do usuário: remover a causa.** A raiz é que o app **apaga** conta com lançamentos, o que partidas dobradas não admite:

```
v7   apagar conta ──▶ FK SET_NULL ──▶ transações órfãs (accountId=NULL)
                                       ──▶ dinheiro EVAPORA do patrimônio, sem contrapartida, sem registro
v8   idem, e a migração precisou dar destino às órfãs ──▶ hack 'Conta removida' EQUITY
                                       ──▶ preserva o sumiço, ao custo de operações semanticamente falsas
                                           (uma despesa cujo dinheiro veio "do nada")
v9   ENCERRAR ──▶ a conta fica no plano de contas, com seu tipo real, marcada encerrada
                  ──▶ saldo ≠ 0 gera LANÇAMENTO DE BAIXA contra EQUITY
                  ──▶ o dinheiro sai do patrimônio de forma explícita, datada e auditável
```

**O encerramento corrige o comportamento — essa é a instrução, não um efeito colateral a justificar.** As sete primeiras versões deste design invertiam isso, argumentando que encerrar *"preserva o comportamento"* porque o patrimônio final coincide. Isso era encaixar uma decisão de **correção** num objetivo de **preservação** que o usuário já tinha emendado ao dizer *"implemente o double entry **corretamente**: encerrar em vez de apagar"*. A coincidência do patrimônio é consequência bem-vinda, não a razão. O que muda, deliberadamente: a conta permanece no plano de contas, um lançamento de baixa datado passa a existir, e o delete deixa de crashar. E o efeito de segunda ordem é o que importa para esta change: com a conta encerrada mantendo o seu tipo real (`ASSET`/`LIABILITY`), as pernas órfãs voltam a ter uma **perna monetária de verdade**, e os predicados do D2/D3 voltam a ser **puros por `AccountType`** — `isMonetary` não precisa conhecer nome de conta, e `EQUITY` volta a significar exclusivamente "contrapartida sintetizada". O caso especial não é acomodado: ele deixa de existir.

**Achado colateral que corrobora:** `entries.accountId` tem FK `NO_ACTION` (`EntryEntity.kt:24-27`) e o Room mantém `foreign_keys=ON` em runtime. Apagar uma conta com lançamentos na v8 provavelmente **já lança violação de constraint** — e não há nenhum teste de `DeleteAccountUseCase` (existem apenas o use case, o `DeleteAccountViewModel` e o binding Koin). A `balanced-ledger` verossimilmente quebrou o delete de conta sem que a verificação manual em device notasse. Encerrar não é só o correto: é o conserto.

**Contra qual conta `EQUITY` a baixa lança?** Contra **`RECONCILIATION`**, a que já existe. Encerrar uma conta zerando o saldo *é* uma reconciliação, e reusá-la evita três coisas: (i) expandir o conjunto de contas de sistema, que o delta de `chart-of-accounts` desta mesma change restringe; (ii) tornar o **CAP-3** (`ensureSystemAccount` check-then-act, `LedgerEntryWriter:150-155`) load-bearing, sendo que fechá-lo é Non-Goal declarado; (iii) um seed novo, que hoje **não existe no fresh-install** — as contas de sistema nascem preguiçosamente, e um device migrado tem 5 enquanto um novo tem no máximo 3. A versão anterior desta change exigia uma "conta `EQUITY` de baixa" que nenhuma task criava e que a spec irmã proibia: contradição resolvida por reuso.

**Alternativas consideradas e rejeitadas:** (a) tipo `TOMBSTONE` — expande o conjunto fechado de `AccountType` para carregar um hack; (b) predicados por identidade de conta (`nome == 'Conta removida'`) — frágil e contamina o modelo para sempre; (c) declarar a regressão aceitável — decisão de valor que o usuário rejeitou.

### D21 — Estado de encerramento mora em `accounts`; as fachadas consomem da sua conta

**Decisão do usuário**, e ela corrige a recomendação anterior deste design. Desde a v8, categoria e cartão **são** contas: `categories.accountId` (`CategoryEntity:33`) e `credit_cards.accountId` (`CreditCardEntity:34`) apontam para a linha de `accounts` que o razão movimenta. A fachada é a cara que o usuário vê; a conta é a coisa.

```
categories  id=5  "Alimentação"  accountId=105  ──▶  accounts  id=105  "Alimentação"  EXPENSE
credit_cards id=2 "Nubank"       accountId=102  ──▶  accounts  id=102  "Nubank"       LIABILITY
                                                     accounts  id=1    "Conta corrente" ASSET
```

Logo o encerramento é **um único campo em `accounts`**, e `CategoryDao`/`CreditCardDao` — que hoje só leem das fachadas — passam a consumir a conta pelo `accountId` que já têm.

**A recomendação anterior era "três flags", e estava errada por um motivo que este documento catalogou por oito rodadas:** seria a terceira cópia do mesmo fato, livre para divergir — a doença de `isClosable` (regra diferente por tela), de `initialBalance` (três implementações) e do predicado de fatura (quatro formas). Não se cura duplicação prescrevendo duplicação. O argumento de "não acoplar os DAOs ao plano de contas" também não se sustenta: o acoplamento **já existe** e chama-se `accountId`; fingir que não é a ficção.

**Consequência que a decisão força — criação eager.** Hoje `accountId` é **nullable** nas duas fachadas, porque a conta nasce preguiçosamente na primeira escrita (`ensureCategoryAccount:115-132`, `ensureCardAccount:134-148`). Uma categoria nunca usada não tem conta, e o `JOIN` a perderia. Se categoria **é** uma conta, ela tem conta desde que nasce:

| | Preguiçoso (hoje) | Eager (alvo) |
|---|---|---|
| `JOIN` da fachada | `LEFT JOIN` + `NULL` = "não encerrada" — caso especial | `INNER JOIN` |
| **D6 / task 5.2** | existe só porque `CreditCard.accountId` é nullable | **desaparece** |
| **CAP-3** (`ensure*` check-then-act) | vale p/ categoria, cartão e sistema | encolhe p/ **só contas de sistema** |
| `ensureCategoryAccount`/`ensureCardAccount` | inserem sob demanda | viram lookup |

Custo baixo: os passos 4 e 5 da migração **já** promovem toda categoria e cartão existentes e preenchem o `accountId`. Falta a criação nova ser eager e a coluna virar `NOT NULL` na v9.

### D22 — Categoria entra; orçamento perde a categoria encerrada, não a si mesmo

**Decisões do usuário:** categoria entra no escopo do encerramento; encerrar remove a categoria do orçamento; e **orçamento que ficou sem nenhuma categoria viva permanece visível**.

**O que a investigação achou, e que reformula a pergunta.** Um orçamento **não pertence a uma categoria** — `Budget.categories` é `List<Category>`, materializada em `budget_categories` (M2M). E ele **não tem histórico**: `CalculateBudgetProgressUseCase:36-38` filtra por `today.yearMonth`, isto é, é um alvo **mensal vivo**, não um registro. Logo:
- "esconder o orçamento quando a categoria encerra" **destrói algo vivo** — um orçamento de [Alimentação, Transporte] segue válido para Transporte;
- "preservar o registro" **não tem objeto** — não existe registro a preservar.

**Bug corrente descoberto aqui.** `budgets.categoryId` é `NOT NULL` com `CASCADE` (`BudgetEntity:14-21`) e **nunca é lido**: `BudgetMapper:26` só o escreve (`domain.categories.firstOrNull()?.id ?: 0`) e `BudgetRepository:29` monta as categorias **só** da M2M. Ou seja, apagar a categoria que por acaso é a primeira da lista **destrói o orçamento inteiro**, mesmo com outras categorias vivas — por causa de uma coluna que ninguém consulta. E o `?: 0` grava `categoryId = 0` para orçamento sem categoria, apontando para categoria inexistente sob FK `NOT NULL` — só não estoura porque hoje não acontece. A string do modal (`strings.xml:335`) promete apenas que as transações sobrevivem; do orçamento não fala.

**Regra:** encerrar uma categoria a remove das categorias do orçamento **por leitura** (o consumidor filtra pelo estado da conta, como na D21), não por escrita — nada é destruído e um eventual reabrir volta a funcionar sozinho. O orçamento sobrevive ajustado enquanto tiver categoria viva; sem nenhuma, **permanece visível** com progresso zero — é do usuário, e é ele quem decide corrigir ou remover. O app não apaga o que é dele.

**Schema (v9):** `budgets.categoryId` **sai** — é resíduo write-only, e é o `CASCADE` dela que causa a destruição.

### D23 — Fatura fechada é imutável **exceto para o próprio pagamento**; a invariante mora no ponto de escrita

**Correção do usuário, e ela pegou uma falha fatal.** A primeira versão deste desenho dizia "nenhuma operação que toque uma fatura **bloqueada** pode ser criada, atualizada ou removida". `isBlocked` = `CLOSED || PAID` (`Invoice.kt:65-66`), e `PayInvoicePaymentUseCase:37` exige `status == CLOSED` e cria uma operação que **toca a fatura** (`:47-61`). A guarda **tornaria impossível pagar fatura** — o fluxo central de um app de cartão. E o texto se gabava de "cobrir create/update/delete de uma vez" sem enumerar o que atravessa a fronteira: o padrão desta sessão, cometido na proposta feita para curá-lo.

**A invariante tem três estados, não dois:**

| Status | Regra | Evidência |
|---|---|---|
| `OPEN` / `FUTURE` / `RETROACTIVE` | livre — gastos, ajustes, antecipação | antecipar só em `OPEN` (`CreditCardCard:303`); ajustar só em não-bloqueada (`EditInvoiceBalanceViewModel:42` filtra `isEditable`) |
| **`CLOSED`** | imutável **exceto o pagamento** — que é o próximo passo esperado | `PayInvoicePaymentUseCase:37` exige exatamente esse status |
| **`PAID`** | imutável, ponto final — e nem reabrir | `ReopenInvoiceUseCase:29` bloqueia reabrir fatura paga |

**`isBlocked` é o predicado errado para esta invariante.** Ele funde dois estados que se comportam diferente. Os cinco lugares que hoje o usam (`GetOrCreateInvoiceForMonthUseCaseImpl:31`, `AddInstallmentUseCaseImpl:81`, `BuildTransactionUseCaseImpl:90,94` — este último partido em dois `ensure` que levantam o mesmo erro) funcionam **por acidente**: todos são caminhos de **criação de gasto**, onde `CLOSED` e `PAID` de fato coincidem. Aplicá-lo à remoção quebra o pagamento.

**A regra, expressa das entries** — com a máquina que a própria change constrói (D3):

```
fatura PAID     → nenhuma operação, nunca
fatura CLOSED   → só operação cujo rótulo derivado é PAYMENT
demais          → livre
```

**Onde mora:** no ponto único de escrita, junto do `Σ = 0`. Mesma forma — invariante estrutural validada uma vez, na fronteira que todos atravessam. Hoje a criação está coberta em três lugares, a remoção em dois (um **faltando**: `ViewAdjustmentModal:220-236`) e a atualização em **nenhum**.

**Camadas:** UI **não oferece**; domínio **não permite**. A guarda não substitui o gate de UI — ela garante o dado. Os caminhos de manutenção não a disparam (verificado um a um: `AdjustInvoiceUseCase:67` só alcança fatura não-bloqueada; `AdjustBalanceUseCase:69` é ajuste de conta, sem fatura; `DeleteFutureInvoiceUseCase:31` opera em `FUTURE|RETROACTIVE`) — e se dispararem, é bug a montante e a guarda o pega. Backstop, como o `Σ=0`.

**A objeção que eu mesmo levantei se dissolve pela D13.** "Gate no domínio torna todo cartão indeletável" era verdade **hoje**, porque `DeleteCreditCardUseCase:17` apaga operações em bloco por SQL. Com a D13 o cartão é **encerrado**, não apagado — o bulk delete deixa de existir. A objeção já estava resolvida por uma decisão anterior do usuário, e eu não conectei as duas.

### D14 — Migração única `v7 → v9`: não herdar dois erros para depois corrigi-los

A v8 **não foi para produção**. Portanto nenhum dispositivo real precisa passar pelo estado v8, e a `MIGRATION_7_8` — com o hack `'Conta removida'` e o seed fantasma `'Saldo Inicial'` — não precisa existir no caminho. Em vez de `v7 → v8 (erra) → v9 (corrige)`, a change entrega **uma migração `v7 → v9`** que já nasce certa:

- constrói o plano de contas e o razão (o que a v8 fazia bem);
- **não** semeia `'Saldo Inicial'` (fantasma, zero uso — D8);
- **não** semeia `'Conta removida'` nem roteia pernas para `EQUITY` (D13);
- reconstrói as contas apagadas do v7 como contas **encerradas** com o seu tipo, com lançamento de baixa zerando o saldo — preservando o patrimônio do v7 e dando registro ao que evaporava;
- **cria a coluna de encerramento em `accounts`** (ver abaixo);
- dropa a tabela legada `transactions` e renomeia `operations` → `transactions`, com `entries.transactionId` já nascendo com o nome final — sem rename de coluna, sem FK pendurada, sem índice órfão (elimina inteiramente a classe de problema do D10).

**A coluna de encerramento não tem outra casa senão a v9.** `AccountEntity` não a tem e o banco está em `version = 8`: adicioná-la é mudança de schema, logo exige bump + migração. Como o D14 elimina o caminho v8, **não existe uma `MIGRATION_8_9` onde ela caiba** — ou ela nasce na v9, ou seria preciso uma segunda versão que o D14 nega. Consequência de ordem, que a versão anterior escondia ao declarar que §0 "precede tudo": **o encerramento depende da migração**, e §0 se divide em duas metades — as decisões e o trabalho sem schema podem vir antes; a coluna, o `AccountDao` e os use cases de encerramento vêm **depois** da v9. Este é exatamente o acoplamento schema-runtime que o D10 existe para prevenir, detectado para o rename de tabela e perdido para a coluna nova.

**Limite conhecido:** o v7 já apagou as contas — o nome original não existe mais em lugar nenhum. A migração só pode reconstruir uma conta encerrada genérica (ex.: `'Conta encerrada'`, `ASSET`), não o nome que o usuário deu. Isso é irreversível e anterior a esta change; o que ela garante é que daqui em diante **nenhuma conta nova se perde assim**.

**Custo aceito:** dispositivos de desenvolvimento em v8 não têm caminho para a v9 e precisam de reinstalação. Nenhum usuário é afetado.

### D11 — São **onze** leitores legados; as contagens "quatro" e "seis" contavam só `signedCents`

A 1ª versão contava quatro; a 2ª disse seis "com base no `grep signedCents`". **Ambas erradas pelo mesmo motivo**: contaram `signedCents` e ignoraram `sumOf { it.amount }` sobre a `Transaction` legada. A varredura por **ambos** os padrões dá **onze** leitores de produção:

| Leitor | O que faz | Estava previsto? |
|---|---|---|
| `AccountUi:27,30,35,42,47,54` | saldo, abertura, receita, despesa, ajuste, pagamento de fatura (**seis** somas — três por `signedCents`, três por `amount` cru; a versão anterior listava só as três primeiras, dentro do próprio D11 que existe para não fazer isso) | sim |
| `CalculateBalanceUseCase:23` | forma in-memory (CAP-2) | sim |
| `ViewCategoryViewModel` | soma `amount` cru | sim |
| budgets | progresso por categoria | sim |
| **`DashboardComponentsBuilder:216,156,157,181,186`** | **saldo por conta (impl. própria) + pendentes + totais do mês** | **não** |
| **`CalculateReportStatsUseCase:26,30,32,41`** | **saldo, receita, despesa, abertura do relatório** | **não** |
| **`CalculateTransactionStatsUseCase:21-23`** (`transactions/api`) | receita/despesa/ajuste do mês | **não** |
| **`CalculateInvoiceOverviewsUseCase:22,25,28,39`** | overviews de fatura | **não** |
| **`InvoiceTransactionsViewModel:102,106,110`** | somas da tela de fatura | **não** |
| **`ReportViewerViewModel:84,87,90`** | somas do relatório por fatura | **não** |
| **`TransactionsViewModel:72`** | total da lista | **não** |

Pior que a contagem: o **relatório inteiro** depende de tipos que a change remove. `income`/`expense` (`:24-30`) filtram por `Transaction.Type`; `isInternalTransferFor` (`:100`) depende de `Operation.Kind.TRANSFER` **e** `Transaction.Target.ACCOUNT`. Reescrevê-lo sobre o razão é trabalho de tamanho próprio, não um checkbox — e sem ele a 6.6 não compila.

### D12 — O razão precisa de agregados por conta/período que hoje não existem

`AccountUi` expõe `income`, `expense`, `adjustment`, `invoicePayment` (além de saldo e abertura), e `ViewCategoryViewModel:59` expõe `transactionCount`. O `IEntryRepository` só tem `balanceUpTo`/`balanceInMonth`/`invoiceOwed`/`categoryTotals` — **nenhum deles entrega esses recortes**, e eles não são deriváveis de saldo.

Sem agregados novos, virar o `AccountUi` só teria duas saídas, ambas ruins: hidratar todas as operações e somar em memória — o que **viola o requisito "Sem cálculo de saldo em memória"** que esta própria change declara — ou manter o legado. Logo: `EntryDao`/`IEntryRepository` ganham agregados por conta e período (receita, despesa, ajuste, pagamento de fatura) e contagem por categoria, **em §2, antes** de qualquer virada em §4.

## Risks / Trade-offs

- **[Mudança silenciosa de número]** Virar os seis leitores do somatório em memória para o razão pode alterar valores exibidos sem erro nem crash. → **Mitigação:** D9 (caracterização antes da troca). É o risco #1 desta change.
- **[CAP-4 deixa de ser teórico]** `AccountUi` filtra por `transaction.date`; `balanceUpTo` corta por **data da operação**. Coincidem por construção hoje, sem guarda. → **Mitigação:** teste que divirja as datas de propósito e falhe; então garantir a invariante na escrita.
- **[Migração destrutiva]** A v9 dropa a tabela `transactions` — o legado deixa de existir e não há rollback de dados. → **Mitigação:** C só acontece depois de B verificado; o razão já contém tudo (backfill da v8, testado com órfãos); teste v8→v9 com dados representativos antes do merge.
- **[Rename de grande superfície]** D toca dezenas de arquivos. → **Mitigação:** manter em commits separados de qualquer mudança de comportamento. **Exceção obrigatória:** os renames de entity/tabela/coluna do D10 andam **junto** da migração, porque separá-los quebra o Room.
- **[Bug já em produção]** `AdjustInvoiceUseCase:74` atualiza o valor legado sem rota de razão; como `invoiceOwed` já lê o razão, o número **já diverge hoje**. → **Mitigação:** corrigir cedo, com teste — é bug corrente, não dívida da coexistência.
- **[Trade-off: `Entry` ganha `invoiceId` no domínio]** Expor o `invoiceId` vaza um detalhe de sub-razão de cartão para o modelo. → Aceito: é o que torna a `Entry` uma perna completa.
- **[Trade-off: specs main aspiracionais no intervalo]** As specs sincronizadas do `balanced-ledger` já afirmam coisas que só ficam verdadeiras ao fim desta change. → Aceito conscientemente; os deltas aqui declaram a versão final.
- **[Processo: o rigor destes artefatos não é auto-verificável]** A 1ª versão passou por revisão minha e continha três fatos falsos, cada um sustentando uma decisão. → **Mitigação:** auditoria adversarial independente contra o código a cada revisão material, e nenhuma afirmação de fato sem `arquivo:linha` verificada.

## Open Questions

- **[RESOLVIDA — era falso bloqueador]** *"Um lançamento de baixa é editável?"* A investigação do `ViewOperationModal:417-421` mostrou que a regra de editabilidade já tem um gate `ADJUSTMENT` que barra a baixa. Não havia decisão a tomar: a resposta estava no código, e a pergunta só existiu porque o D2 tinha lido a regra pela metade. Registrada como lembrete de que "questão aberta" às vezes é investigação não feita.
- **Onde `Type` e `Target` passam a morar?** O domínio não os persiste, mas `Target` está numa rota `@Serializable` (`TransactionsRoute`) com `TransactionTargetNavType`: o enum precisa de um endereço estável que `feature/transactions/api` possa serializar. `core/model` (classificação de fronteira) ou `feature/transactions/api` (vocabulário de rota)? D4 fixa o papel, não o endereço.
- **O filtro `filterTarget` da rota vira filtro por `AccountType`?** Seria a expressão nativa no razão (`ASSET` vs `LIABILITY`), mas muda um contrato de navegação serializável — possivelmente com deep links salvos.
- **Ordem de B:** virar `CalculateBalanceUseCase` primeiro simplifica os outros, ou são independentes? A investigar ao aplicar.
