# `:core:ledger` — o razão de partidas dobradas

> O razão é a fonte de verdade, com garantia contábil; as features são sabores dessa
> verdade, e as fachadas, o açúcar.

> Este documento descreve **o que o razão é** e **como se fala com ele**.
> Ele é a referência normativa do módulo: o que estiver aqui vale para todo consumidor.
> Para as regras de dependência entre módulos, veja `feature/README.md`.

---

## O que é

Dinheiro neste app é modelado como um **razão de partidas dobradas balanceado**, e esse é
o único modelo. Não existe saldo guardado numa coluna, não existe "tipo de transação"
persistido, não existe uma segunda forma de calcular um número.

Três frases resumem o módulo inteiro:

1. **Toda escrita é um conjunto de lançamentos que soma zero**, por moeda — **sem
   exceção**, a transação que atravessa moedas inclusive: ela não desbalanceia o razão,
   chega *incompleta* à fronteira e é completada lá.
2. **Toda leitura é `Σ lançamentos`** — saldo, saldo inicial, devido de fatura, gasto por
   categoria e patrimônio compartilham um só mecanismo.
3. **O razão não conhece nenhuma fachada.** Ele não sabe o que é uma fatura, um cartão,
   uma categoria, um orçamento ou um relatório — e não *consegue* saber (veja
   [A fronteira](#a-fronteira-do-módulo-não-é-convenção-é-o-compilador)).

O módulo não depende de nenhum outro módulo do projeto. Só de Room, datetime,
serialization e Koin.

---

## Vocabulário

### `Account` + `AccountType` — o plano de contas

Toda conta e todo cartão do usuário é uma `Account` com um `type` do conjunto **fechado**
`{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY, CONVERSION}`. Um cartão é uma fachada ligada
à sua `Account` por `accountId`, e lê o próprio encerramento de lá — não há cópia.

Toda `Account` declara **uma** moeda, sem default e imutável: uma conta sem moeda é
inexprimível, e trocá-la depois reescreveria em silêncio o significado de toda perna já
gravada. O razão sabe que moeda existe e nada sobre qual — a opinião sobre quais o app
oferece vive na camada de consolidação.

`CONVERSION` é a conta que absorve o resíduo de uma transação que atravessa moedas, e tem
**tipo próprio** justamente para não ser `EQUITY`: aqui `EQUITY` já significa "o usuário
reconciliou algo à mão", e emprestá-lo à conversão faria toda transferência cruzada ser
lida como ajuste — na derivação de rótulo, nos predicados `eq` da `EntryDao` e na
idempotência de `AdjustBalanceUseCase`. Ela é de natureza credora, não é monetária (não
aparece em formulário algum), não é nominal e é permanente vacuamente — nunca é
arquivada. O seu saldo é o **resultado cambial realizado**, com o sinal invertido por ser
credora.

Os predicados do tipo são o que os consumidores usam, nunca um `when` próprio:

| Predicado | Verdadeiro para | Serve para |
|---|---|---|
| `isDebitNatured` | `ASSET`, `EXPENSE` | aumenta com valor positivo (débito) |
| `isCreditNatured` | `LIABILITY`, `INCOME`, `EQUITY`, `CONVERSION` | aumenta com valor negativo (crédito) |
| `isMonetary` | `ASSET`, `LIABILITY` | onde o dinheiro fisicamente está; é o que o usuário escolhe no formulário |
| `isPermanent` | `ASSET`, `LIABILITY`, `EQUITY`, `CONVERSION` | saldo que atravessa períodos — pode ficar *encalhado* |
| `isNominal` | `INCOME`, `EXPENSE` | contas de resultado; as únicas onde uma dimensão de categoria pousa |

Além das contas e cartões do usuário, o plano guarda quatro linhas de sistema
(`SystemAccount`) **por moeda em uso**: as duas nominais em que toda despesa e toda
receita pousam, a de reconciliação e a de conversão. Elas são resolvidas por
`(type, name, currency)` — a natureza deixou de ser chave desde que `EQUITY` passou a ter
duas —, criadas sob demanda pela fronteira de escrita; os seus nomes são chaves de busca e
**nunca são renderizados** — são invisíveis por construção, porque todo seletor e toda
listagem filtra por `ASSET`/`isMonetary`.

### `Entry` — a perna

```kotlin
data class Entry(
    val id: Long = 0,
    val transactionId: Long? = null,
    val account: Account,
    val amount: Long,          // centavos, com sinal, débito-positivo
    val dimensionId: Long? = null,
) {
    // Derivada, não declarada: a moeda de uma perna é a da conta em que ela posta, e
    // "poste 100 USD numa conta BRL" não tem como ser dito.
    val currency: String get() = account.currency
}
```

A **coluna** `entries.currency` continua existindo, e não por inércia: `LedgerBalanceCheck`
verifica a invariante agrupando por `(transactionId, currency)` sem tocar em `accounts`, e
a spec exige que ela seja verificável lendo apenas as entries.

`amount` é **signed `Long` em centavos**, convenção débito-positivo: positivo debita a
conta, negativo credita. Para toda moeda presente numa transação, a soma das pernas é
exatamente zero.

### `Transaction` — o conjunto balanceado

Uma `Transaction` é uma linha (título, data, metadados de agrupamento) mais suas pernas.
Ela **não carrega fachada nenhuma** — nem categoria, nem conta, nem cartão, nem fatura,
nem parcelamento, nem recorrência. O que ela expõe são identidades, e cada feature resolve
a fachada que precisa a partir delas:

```kotlin
transaction.liabilityAccountId   // o cartão é a conta da perna LIABILITY
transaction.liabilityDimensionId // a fatura é a dimensão dessa perna
transaction.nominalDimensionId   // a categoria é a dimensão da perna nominal
transaction.label                // derivado, nunca persistido
```

É por isso que o razão pode ser lido sem nenhuma fachada disponível — e por isso renomear
uma categoria não re-emite todas as transações do app.

### `Dimension` + `DimensionKind` — o eixo analítico

Uma perna pode carregar **uma** `dimensionId`: o eixo pelo qual ela é classificada, o
sub-razão a que pertence dentro da sua conta. O total de uma fachada é `Σ lançamentos que
carregam a sua dimensão`.

```kotlin
enum class DimensionKind(val landsOn: Set<AccountType>) {
    INVOICE(setOf(AccountType.LIABILITY)),
    CATEGORY(setOf(AccountType.INCOME, AccountType.EXPENSE)),
}
```

`landsOn` é **o único dado** que o razão tem sobre um kind — a regra de pouso. `INVOICE` é
um rótulo legível para quem lê o schema, não um conceito que o razão manipula: nenhuma
query ramifica por kind.

> **"Sem categoria" é a *ausência* de dimensão**, nunca uma conta ou dimensão balde.
> Nos agregados por dimensão, a chave `null` é o total não classificado.

---

## A fronteira do módulo: não é convenção, é o compilador

`:core:ledger` declara um `LedgerDatabase` **interno**, listando só as suas quatro tabelas
(`accounts`, `transactions`, `entries`, `dimensions`). O app nunca abre esse banco — o
real é o `AppDatabase`, em `:core:database`. Ele existe para que o KSP valide todo
`@Query` deste módulo contra um schema em que `invoices`, `categories` e `credit_cards`
**não existem**:

```
JOIN invoices  →  erro de compilação: no such table: invoices
```

A visibilidade do Kotlin já impede um DAO de importar uma entity de fachada; só o
`LedgerDatabase` impede o nome da tabela de aparecer **dentro de uma string SQL**, onde o
compilador de outra forma nunca olharia.

Três portas deixam uma fachada participar sem o razão saber que ela existe — veja
[As três portas](#as-três-portas).

---

## Como ler

Toda leitura passa por `IEntryRepository`. Não existe segunda via: somar pernas já
carregadas em memória, na feature, é violação de spec.

**Toda leitura capaz de atravessar contas devolve `MoneyByCurrency`** — um valor por
moeda, nunca um número só. Só as leituras escopadas a **uma conta** permanecem
escalares, porque ali a moeda é atributo da conta e o chamador já a conhece. Reduzir
uma figura multimoeda a um número é **conversão**, e conversão mora acima do razão.

```kotlin
class MinhaViewModel(
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) {

    suspend fun carregar(mes: YearMonth, contaId: Long) {
        // Escopada a uma conta → escalar. A moeda é `conta.currency`.
        val saldoDaConta = entryRepository.accountBalanceUpTo(contaId, mes)

        // Atravessa contas → por moeda.
        val saldoDeTodas = entryRepository.balanceUpToByCurrency(mes)
    }
}
```

### Como uma feature obtém a figura de uma leitura por moeda

Uma `MoneyByCurrency` não é exibível: é o que o razão sabe. Quem a transforma em
figura é **o redutor**, `ConsolidateMoneyUseCase` (`:core:model`), e ele é o único:

```kotlin
val figura = consolidateMoney(
    money = entryRepository.balanceUpToByCurrency(mes),
    on = hoje,
    policy = DisplayAmount::natural,   // a política de sinal é do chamador
)
```

A moeda e a exatidão **não** são do chamador — o redutor as deriva. A `policy` é, porque
só o chamador sabe se aquilo é saldo, magnitude ou dívida.

### Quando a feature sabe que o mapa tem uma chave só

Fatura, parcela e saldo de conta são monomoeda — mas **por garantia da fachada**, não
por construção do razão. Nada no razão amarra uma dimensão a uma única conta, e presumir
o contrário exigiria que ele consultasse `DimensionKind` na leitura, que é justamente o
conhecimento de fachada que ele não pode ter. A redução acontece **na feature**, onde a
garantia está escrita:

```kotlin
// A fachada de cartão garante que a dimensão de uma fatura cai numa conta só.
val devido = entryRepository.dimensionOwedByCurrency(faturaId).singleOrNull()
```

`singleOrNull()` devolve `CurrencyAmount` — o valor **e** a moeda —, para que a
denominação não precise ser buscada em outro lugar e não possa divergir.

### Somar dois resultados por moeda é do razão

`asset.expense + liability.expense` é aritmética sobre saldos, não conversão. O dono é
`MoneyByCurrency.plus`, e é a única implementação:

```kotlin
val total = assetFlows.expense + liabilityFlows.expense   // cada moeda com a sua
```

**Nunca** em linha na feature, e nunca na camada de consolidação, que responde só por
conversão entre moedas.

### Reatividade

Os agregados são `suspend`, não `Flow`. Uma tela cujos números vêm deles **precisa**
observar `observeLedgerChanges()`, ou os saldos congelam enquanto o razão se move — e,
se a figura for consolidada, `ObserveConsolidationChangesUseCase`, que funde aquele
gatilho com a moeda base e o acervo de taxas (cadastrar uma taxa não escreve em
`entries`, então sozinho ele não bastaria):

```kotlin
observeConsolidationChanges()
    .map { consolidateMoney(entryRepository.balanceUpToByCurrency(mes), hoje, DisplayAmount::natural) }
```

### Superfície de leitura

Escalares — escopadas a **uma conta**, onde a moeda é atributo dela:

| Pergunta | Chamada |
|---|---|
| Saldo de uma conta até um mês | `accountBalanceUpTo(accountId, target)` |
| Saldo de sempre de uma conta | `balance(accountId)` |
| Fluxos do mês de uma conta (receita/despesa/ajuste/pagamento) | `accountFlows(month, accountId)` — carrega a moeda da conta |
| Tem movimento? (apagar vs. encerrar) | `hasEntries(accountId)` / `hasEntriesForDimension(dimensionId)` |
| As pernas de uma transação | `getEntriesByTransaction(id)` / `observeEntriesByTransaction(id)` |

Por moeda — **toda** leitura que atravessa contas, e **toda** leitura por dimensão:

| Pergunta | Chamada |
|---|---|
| Saldo de todas as `ASSET` até um mês | `balanceUpToByCurrency(target)` |
| Saldo de todas as contas de uma natureza até um mês | `naturalBalanceUpToByCurrency(target, type)` |
| Fluxos do mês de todos os cartões | `liabilityMonthFlowsByCurrency(month)` |
| Fluxos do mês de todas as contas | `assetMonthFlowsByCurrency(month)` |
| Devido de um sub-razão (fatura) | `dimensionOwedByCurrency(dimensionId)` |
| O mesmo, para vários sub-razões, numa consulta | `owedByDimensionByCurrency(ids)` |
| Composição de um sub-razão (despesa/antecipação/ajuste) | `dimensionFlowsByCurrency(dimensionId)` |
| O mesmo, para vários | `flowsByDimensionByCurrency(ids)` |
| Gasto de uma dimensão no mês (categoria) | `dimensionBalanceInMonthByCurrency(month, dimensionId)` |
| O mesmo, para várias dimensões | `dimensionBalancesInMonthByCurrency(month, ids)` |
| Totais por dimensão num período, vistos de um conjunto de contas | `totalsByDimensionByCurrency(nominalType, start, end, siblingAccountIds)` |
| Os mesmos totais, escopados a sub-razões | `totalsByDimensionInScopeByCurrency(nominalType, scopeDimensionIds)` |
| Receita/despesa/saldo/saldo inicial de um escopo (relatório) | `scopeStatsByCurrency(scopeAccountIds, start, end)` |

O patrimônio (`Σ ASSET − Σ LIABILITY`, por moeda, sem as contas de conversão) **não** é
membro desta interface: vive em `EntryDao.netWorthCents()`, que é onde ele tem chamador.

Valores voltam na **unidade maior** (reais), não em centavos. `adjustment` é signed; os
demais são magnitudes positivas.

Para exibir um saldo natural com o sinal que o usuário espera, use `AccountType.displaySign`
— não invente a regra de sinal na tela.

---

## Como escrever

O chamador expressa **intenção por identidade**. Resolver "este cartão" ou "esta
categoria" para um id é trabalho de quem é dono da fachada; completar e balancear a
intenção é do razão.

```kotlin
data class TransactionIntent(
    val title: String?,
    val date: LocalDate,
    val recurringId: Long? = null,
    val recurringCycle: Int? = null,
    val installmentId: Long? = null,
    val installmentNumber: Int? = null,
    val legs: List<TransactionLeg>,
    val contra: ContraLeg? = null,   // obrigatório quando `legs` tem uma perna só
)

data class TransactionLeg(
    val type: TransactionType,   // EXPENSE | INCOME | ADJUSTMENT — a escolha do usuário
    val amount: Double,          // sempre positivo; o sinal sai do `type`
    val accountId: Long,
    val dimensionId: Long? = null,
)

data class ContraLeg(
    val nature: AccountType,     // EXPENSE | INCOME | EQUITY
    val dimensionId: Long? = null,
)
```

### Uma despesa na conta

```kotlin
TransactionIntent(
    title = form.title,
    date = date,
    legs = listOf(
        TransactionLeg(
            type = TransactionType.EXPENSE,
            amount = 42.90,
            accountId = account.id,
        )
    ),
    // A regra "em que nominal a perna pousa" tem um dono só, em :core:model.
    contra = contraLegFor(form.type, form.category),
)
```

### Uma despesa no cartão

```kotlin
TransactionIntent(
    title = form.title,
    date = date,
    legs = listOf(
        TransactionLeg(
            type = TransactionType.EXPENSE,
            amount = 42.90,
            // O cartão *é* a conta LIABILITY; a fatura *é* a dimensão dessa perna.
            accountId = creditCard.accountId,
            dimensionId = invoice.dimensionId,
        )
    ),
    contra = contraLegFor(form.type, form.category),
)
```

Uma intenção **de duas pernas** (transferência, pagamento de fatura) já balanceia sozinha
e ignora `contra`.

Depois é `ITransactionRepository`:

```kotlin
transactionRepository.createTransaction(intent)
transactionRepository.createTransactions(intents)   // tudo-ou-nada (um parcelamento)
transactionRepository.deleteTransactionById(id)
transactionRepository.deleteTransactionsByIds(ids)  // tudo-ou-nada
```

> ⚠️ `updateTransaction` recebe **uma** perna e reescreve tudo: apaga as pernas antigas e
> reconstrói a partir dela mais o `contra`. Isso só é correto para transação com exatamente
> uma perna monetária (despesa ou receita) — por isso a edição só é oferecida quando
> `isEditable` vale. `contra` **não tem default**, de propósito: esquecê-lo transformava a
> reescrita numa transação desbalanceada, recusada na fronteira, com a edição silenciosamente
> revertida.

### O que a fronteira de escrita (`LedgerEntryWriter`) faz por você

Num ponto só, para toda escrita do app:

1. Recusa um conjunto **vazio** de pernas (uma partida dobrada tem duas, por definição).
2. Traduz o `TransactionType` em sinal contábil — **o único lugar** onde isso acontece.
3. Lê a **moeda de cada perna da conta em que ela posta** — `TransactionLeg` não tem campo
   de moeda, e a conta já está carregada para a verificação de encerramento.
4. Completa a intenção unilateral, criando a conta de sistema da natureza **e da moeda**
   pedidas sob demanda.
5. Completa a intenção **cruzada**: agrupa as pernas por moeda e lança o oposto do resíduo
   de cada uma na conta `CONVERSION` daquela moeda, **por diferença e sempre por último**
   (é onde todo o arredondamento do sistema se concentra) e **sem dimensão**. Recusa
   quando os resíduos são todos do mesmo sinal — isso não é câmbio, é dinheiro criado do
   nada.
6. Recusa uma perna cuja conta esteja **encerrada** (`ClosedAccountException`).
7. Valida **`Σ = 0` por moeda** (`UnbalancedTransactionException`), aqui e **em nenhum
   outro ponto**: o pré-cheque plano que existia antes da fronteira foi removido, porque
   somar pernas cruas sem moeda recusaria toda intenção cruzada antes de ela poder ser
   completada.
8. Valida a **regra de pouso**: `account.type in kind.landsOn` — uniforme, sem `when` por
   kind (`LedgerError.MisplacedDimension`).
9. Consulta o `DimensionWriteGuard` registrado.

Falhou qualquer uma, nada é escrito.

Além disso, o repositório recusa **remover ou reapontar** movimento de uma conta permanente
encerrada (`ClosedAccountRemoval`) — encerrar exige saldo zero, e desfazer o movimento
reabriria um saldo numa conta que não aparece em seletor nenhum. A regra é derivada em
`List<Entry>.closedLegBlockingChange()`, com dono único, para que a fronteira que recusa e
a tela que não oferece não possam discordar.

---

## Dimensões: ciclo de vida

A fachada é dona da sua dimensão. Ela **emite** na criação e **remove** na remoção, na
mesma transação de escrita:

```kotlin
// criação da fachada
val dimensionId = dimensionDao.emit(DimensionKind.CATEGORY)   // ou INVOICE
// ... grava a fachada com esse dimensionId

// remoção da fachada
dimensionDao.deleteById(dimensionId)
```

Remover a linha de `dimensions` é o que desliga as pernas que a carregavam: o
`ON DELETE SET NULL` em `entries.dimensionId` faz o resto. Os lançamentos continuam
válidos e balanceados — apenas deixam de ser classificados.

---

## As três portas

O razão declara três `fun interface` que uma fachada implementa e registra no próprio
módulo Koin. São **contratos separados de propósito**: um formato cada, um implementador
cada, um momento cada — antes de a transação de escrita abrir, dentro dela, e dentro dela
depois de as linhas sumirem.

### `DimensionWriteGuard` — recusar

"Uma escrita está chegando, tocando estas dimensões. Alguém se opõe?"

```kotlin
fun interface DimensionWriteGuard {
    suspend fun ensureAccepts(write: LedgerWrite)   // recusa lançando erro tipado
}

data class LedgerWrite(
    val dimensionIds: Set<Long>,
    val settlesALiability: Boolean,   // a forma-razão de pagar uma conta
)
```

Sem valor de retorno: um veto ignorável não seria fronteira. O razão não tem regra sua
para aplicar aqui — se um sub-razão ainda aceita movimento é assunto da fachada. O que ele
possui é **onde** a pergunta é feita: um ponto só, para que duas telas não discordem sobre
o que é editável.

Exemplo real: `InvoiceWriteGuard`, em `creditcards:impl`, recusa qualquer toque numa fatura
`PAID` e recusa gasto novo numa `CLOSED` — mas deixa passar o pagamento que a liquida.

### `TransactionRemovalHook` — corrigir-se

"Esta transação foi removida" — dito **dentro** da transação de escrita que a removeu.

```kotlin
fun interface TransactionRemovalHook {
    suspend fun onRemoved(transaction: Transaction)   // `transaction` como era, pernas inclusas
}
```

O razão não tem uso para isso; uma remoção está completa quando as linhas somem. O que ele
possui é o **timing**: uma fachada cujo estado descreve aquelas linhas precisa se corrigir
atomicamente com elas.

Exemplo real: `InstallmentRemovalReconciler` recontabiliza `count`/`totalAmount` de um
parcelamento, ou apaga o parcelamento quando some a última transação.

### `TransactionRemovalPrelude` — precaver-se

"Uma remoção vai acontecer" — dito **antes** de a transação de escrita abrir.

```kotlin
fun interface TransactionRemovalPrelude {
    suspend fun beforeRemoval()   // sem argumento e sem retorno
}
```

É chamada uma vez no topo de `deleteTransactionById` e de `deleteTransactionsByIds`,
**acima** do `useWriterConnection`. Aqui o timing é o contrato inteiro, e é o único que um
chamador de `ITransactionRepository` não consegue arranjar sozinho: de fora não se enxerga
onde a transação começa, e há trabalho que só se faz fora dela — `VACUUM INTO`, por
exemplo, recusa rodar dentro de uma transação.

O anúncio é o único ponto em que o chamador tem voz, e ela é estreita: decide **se** o
anúncio é feito, nunca *o que ele significa*, que continua sendo da porta. As duas remoções
têm uma sobrecarga que recebe essa resposta como `RemovalAnnouncement`, e quem cala anuncia
— `deleteTransactionById(id)` fala, porque esquecer não pode custar o anúncio a ninguém.

Reter é o único valor que o compilador cobra duas vezes:

```kotlin
@OptIn(WithheldAnnouncement::class)                 // sem isto, não compila
suspend fun invoke(transaction: Transaction, withoutCopy: Boolean) {
    transactionRepository.deleteTransactionById(
        id = transaction.id,
        announcement = if (withoutCopy) {
            RemovalAnnouncement.Withheld            // marcado com @RequiresOptIn
        } else {
            RemovalAnnouncement.Announced
        },
    )
}
```

`RemovalAnnouncement.Withheld` carrega o marcador `@WithheldAnnouncement`, então escrevê-lo
sem o `@OptIn` correspondente é erro de compilação, e o `@OptIn` fica visível em cima do
código que remove. Um `Boolean` é recusado aqui: deixa `false` ao alcance de um descuido, não
diz no ponto de chamada qual das duas remoções foi pedida, e põe "reter é deliberado" nas mãos
de quem revisa; a garantia aqui é a mesma que separa este módulo do resto — do compilador, não
da disciplina. O razão continua sem aprender por que alguém
reteria: quem retém já resolveu esse ouvinte nos seus próprios termos, e isso é justamente o
que a porta não tem como saber sozinha.

Nenhuma das outras duas substitui. `TransactionRemovalHook` fala depois de as linhas
sumirem e de dentro da transação que as removeu — uma correção, por construção tarde demais
para ser precaução. `DimensionWriteGuard` é perguntado de dentro dessa mesma transação, e
só quando dimensões são tocadas.

Sem argumento porque o que uma remoção *é* — uma transação, um parcelamento, uma fatura —
é conhecimento de fachada que o razão não tem e não precisa repassar. Uma implementação que
se recusa a deixar a remoção seguir o faz lançando, e nada é removido porque nada começou.

Exemplo real: o cofre de backup captura uma cópia antes de a exclusão apagar o que apaga.

### Registro

`DimensionWriteGuard` e `TransactionRemovalHook` são **obrigatórias** no grafo do Koin. Os
seus `None` existem para testes cujo assunto é outro — **não** são defaults: um app sem
binding falha ao subir, em vez de perder o veto silenciosamente na primeira escrita.

`TransactionRemovalPrelude` é a exceção: o módulo a resolve com `getOrNull() ?: None`, e
ninguém a reivindicar é um grafo válido. A assimetria é a consequência de cada ausência —
faltando o veto perde-se uma regra, faltando a correção uma fachada passa a descrever
linhas que não existem mais, e faltando o prelúdio nada no razão fica errado, porque a
remoção já estava completa sem ele. Silêncio aqui é um ouvinte que nunca perguntou, não uma
garantia perdida em silêncio.

```kotlin
// no módulo Koin da feature dona
single<DimensionWriteGuard> { InvoiceWriteGuard(invoiceRepository = get()) }
single<TransactionRemovalHook> { InstallmentRemovalReconciler(...) }
factory<TransactionRemovalPrelude> { TransactionRemovalPrelude { preventive.captureBefore(...) } }
```

---

## O que é derivado, nunca persistido

| Derivação | Dono |
|---|---|
| O que uma transação **é** (despesa, receita, ajuste, transferência, pagamento) | `List<Entry>.deriveTransactionLabel()` |
| O `TransactionType` de uma perna monetária | `deriveTransactionType(legAmountCents, entries)` |
| O sinal de exibição de um saldo natural | `AccountType.displaySign` |
| A perna do cartão / da categoria / da origem | `liabilityLeg()` / `nominalLeg()` / `sourceLeg()` |
| Se as pernas balanceiam | `List<Entry>.isBalanced()` |
| A perna encerrada que impede a mudança | `List<Entry>.closedLegBlockingChange()` |
| Saldo natural de uma conta a partir de pernas em mãos | `List<Entry>.naturalBalanceOf(accountId)` |

Uma regra derivável do domínio tem **exatamente um dono**, no domínio. Um consumidor decide
*se* aplica — uma tela pode legitimamente não oferecer o que o razão permite — nunca *qual*
é a regra.

---

## Exceções documentadas

Duas, ambas deliberadas:

1. **`Category.type` é estado primário, não derivado.** "Isto é uma categoria de despesa" é
   declaração do usuário, e nada no razão a produz. (Vive em `:core:model`.)
2. **`transactions` retém as colunas de parcelamento e recorrência sem FK.** São metadados
   de agrupamento; nenhuma leitura do razão as consulta, e o caminho de remoção de cada
   fachada as anula explicitamente.

---

## DI

`ledgerModule` (em `di/LedgerModule.kt`) é agregado pelo shell como qualquer outro core.
Ele fornece `ITransactionRepository`, `IEntryRepository`, `LedgerEntryWriter`,
`TransactionMapper` e `CalculateBalanceUseCase`.

O que ele **espera** encontrar no grafo:

- os quatro DAOs e o `RoomDatabase` — vêm de `:core:database`, que monta o banco real;
- as três portas — vêm de quem as reivindicar, e só o prelúdio pode não ter reivindicante.

> O `TransactionRepository` recebe o supertipo `RoomDatabase`, não o `AppDatabase`: abrir
> uma transação de escrita é capacidade do Room, e o razão não tem por que saber de que
> schema faz parte. O binding **precisa** ser a mesma instância (`bind RoomDatabase::class`)
> — duas instâncias fariam o `TransactionRemovalHook` dar deadlock em vez de aninhar num
> savepoint.

---

## Testes

Os testes de query do módulo (`EntryCategoryQueryTest`, `InvoiceAndCardQueryTest`,
`AccountPeriodTotalsQueryTest`, `YieldSeparationQueryTest`, `ReportStatsQueryTest`,
`BalanceUpToMonthQueryTest`, `AccountSelectionQueryTest`) rodam sobre o **`LedgerDatabase`** — exercitam os DAOs de
produção contra exatamente o schema que o módulo diz precisar. `LedgerFixture` monta o
cenário.

```bash
./gradlew :core:ledger:jvmTest
```

Os testes de migração ficam em `:core:database`.

**Os fakes de `IEntryRepository` continuam sendo 21 stubs completos, um por suíte que
precisa deles, e isso é decisão e não inércia.** Extrair uma base compartilhada exigiria
um módulo novo só de testes — `commonTest` de um módulo não é visível de outro em KMP —,
e o ganho seria menor que o custo: cada fake sobrescrever **explicitamente** todo membro
abstrato é o que torna a migração para as leituras por moeda mecanicamente verificável.
Uma base com corpos padrão esconderia exatamente o que a remoção dos corpos que lançam
(13.1) existe para provar.

---

## Ao mexer neste módulo

- Nenhuma assinatura pública pode nomear fatura, cartão, categoria, orçamento ou relatório.
- Todo `JOIN` é entre tabelas do razão. Se você precisa de uma tabela de fachada, a resposta
  é uma porta, não uma query.
- Um número novo entra em `IEntryRepository` como `Σ lançamentos`. Se um consumidor está
  somando pernas em memória, o número está no lugar errado.
- `build.gradle.kts` não ganha dependência de projeto. A lista vazia é a garantia.
