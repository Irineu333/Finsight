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

1. **Toda escrita é um conjunto de lançamentos que soma zero**, por moeda.
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
`{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}`. Um cartão é uma fachada ligada à sua
`Account` por `accountId`, e lê o próprio encerramento de lá — não há cópia.

Os predicados do tipo são o que os consumidores usam, nunca um `when` próprio:

| Predicado | Verdadeiro para | Serve para |
|---|---|---|
| `isDebitNatured` | `ASSET`, `EXPENSE` | aumenta com valor positivo (débito) |
| `isCreditNatured` | `LIABILITY`, `INCOME`, `EQUITY` | aumenta com valor negativo (crédito) |
| `isMonetary` | `ASSET`, `LIABILITY` | onde o dinheiro fisicamente está; é o que o usuário escolhe no formulário |
| `isPermanent` | `ASSET`, `LIABILITY`, `EQUITY` | saldo que atravessa períodos — pode ficar *encalhado* |
| `isNominal` | `INCOME`, `EXPENSE` | contas de resultado; as únicas onde uma dimensão de categoria pousa |

Além das contas e cartões do usuário, o plano guarda **apenas três linhas de sistema**
(`SystemAccount`): as duas nominais em que toda despesa e toda receita pousam, e a de
reconciliação. Elas são criadas sob demanda pela fronteira de escrita, seus nomes são
chaves de busca e **nunca são renderizados** — são invisíveis por construção, porque todo
seletor e toda listagem filtra por `ASSET`/`isMonetary`.

### `Entry` — a perna

```kotlin
data class Entry(
    val id: Long = 0,
    val transactionId: Long? = null,
    val account: Account,
    val amount: Long,          // centavos, com sinal, débito-positivo
    val currency: String = BASE_CURRENCY,
    val dimensionId: Long? = null,
)
```

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

Duas portas deixam uma fachada participar sem o razão saber que ela existe — veja
[As duas portas](#as-duas-portas).

---

## Como ler

Toda leitura passa por `IEntryRepository`. Não existe segunda via: somar pernas já
carregadas em memória, na feature, é violação de spec.

```kotlin
class MinhaViewModel(private val entryRepository: IEntryRepository) {

    suspend fun carregar(mes: YearMonth, contaId: Long) {
        val saldo = entryRepository.balanceUpTo(target = mes, accountId = contaId)
        val fluxos = entryRepository.accountFlows(month = mes, accountId = contaId)
        val patrimonio = entryRepository.netWorth()
    }
}
```

Os agregados são `suspend`, não `Flow`. Uma tela cujos números vêm deles **precisa**
observar `observeLedgerChanges()`, ou os saldos congelam enquanto o razão se move:

```kotlin
entryRepository.observeLedgerChanges()
    .map { entryRepository.netWorth() }
```

Superfície de leitura, agrupada pelo que responde:

| Pergunta | Chamada |
|---|---|
| Saldo de uma conta (ou de todas as `ASSET`) até um mês | `balanceUpTo(target, accountId?)` |
| Saldo de sempre de uma conta | `balance(accountId)` |
| Patrimônio (`Σ ASSET − Σ LIABILITY`) | `netWorth()` |
| Fluxos do mês de uma conta (receita/despesa/ajuste/pagamento) | `accountFlows(month, accountId)` |
| Fluxos do mês de todos os cartões | `liabilityMonthFlows(month)` |
| Devido de um sub-razão (fatura) | `dimensionOwed(dimensionId)` |
| Composição de um sub-razão (despesa/antecipação/ajuste) | `dimensionFlows(dimensionId)` |
| Gasto de uma dimensão no mês (categoria) | `dimensionBalanceInMonth(month, dimensionId)` |
| O mesmo, para várias dimensões | `dimensionBalancesInMonth(month, ids)` |
| Totais por dimensão num período, vistos de um conjunto de contas | `totalsByDimension(nominalType, start, end, siblingAccountIds)` |
| Os mesmos totais, escopados a sub-razões | `totalsByDimensionInScope(nominalType, scopeDimensionIds)` |
| Receita/despesa/saldo/saldo inicial de um escopo (relatório) | `scopeStats(scopeAccountIds, start, end)` |
| Tem movimento? (apagar vs. encerrar) | `hasEntries(accountId)` / `hasEntriesForDimension(dimensionId)` |
| As pernas de uma transação | `getEntriesByTransaction(id)` / `observeEntriesByTransaction(id)` |

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
3. Completa a intenção unilateral, criando a conta de sistema da natureza pedida sob
   demanda.
4. Recusa uma perna cuja conta esteja **encerrada** (`ClosedAccountException`).
5. Valida **`Σ = 0` por moeda** (`UnbalancedTransactionException`).
6. Valida a **regra de pouso**: `account.type in kind.landsOn` — uniforme, sem `when` por
   kind (`LedgerError.MisplacedDimension`).
7. Consulta o `DimensionWriteGuard` registrado.

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

## As duas portas

O razão declara dois `fun interface` que uma fachada implementa e registra no próprio
módulo Koin. São **contratos separados de propósito**: um formato cada, um implementador
cada.

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

### Registro

Ambas são **obrigatórias** no grafo do Koin. `DimensionWriteGuard.None` e
`TransactionRemovalHook.None` existem para testes cujo assunto é outro — **não** são
defaults: um app sem binding falha ao subir, em vez de perder o veto silenciosamente na
primeira escrita.

```kotlin
// no módulo Koin da feature dona
single<DimensionWriteGuard> { InvoiceWriteGuard(invoiceRepository = get()) }
single<TransactionRemovalHook> { InstallmentRemovalReconciler(...) }
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
- as duas portas — vêm de quem as reivindicar.

> O `TransactionRepository` recebe o supertipo `RoomDatabase`, não o `AppDatabase`: abrir
> uma transação de escrita é capacidade do Room, e o razão não tem por que saber de que
> schema faz parte. O binding **precisa** ser a mesma instância (`bind RoomDatabase::class`)
> — duas instâncias fariam o `TransactionRemovalHook` dar deadlock em vez de aninhar num
> savepoint.

---

## Testes

Os testes de query do módulo (`EntryCategoryQueryTest`, `InvoiceAndCardQueryTest`,
`AccountPeriodTotalsQueryTest`, `ReportStatsQueryTest`, `BalanceUpToMonthQueryTest`,
`AccountSelectionQueryTest`) rodam sobre o **`LedgerDatabase`** — exercitam os DAOs de
produção contra exatamente o schema que o módulo diz precisar. `LedgerFixture` monta o
cenário.

```bash
./gradlew :core:ledger:jvmTest
```

Os testes de migração ficam em `:core:database`.

---

## Ao mexer neste módulo

- Nenhuma assinatura pública pode nomear fatura, cartão, categoria, orçamento ou relatório.
- Todo `JOIN` é entre tabelas do razão. Se você precisa de uma tabela de fachada, a resposta
  é uma porta, não uma query.
- Um número novo entra em `IEntryRepository` como `Σ lançamentos`. Se um consumidor está
  somando pernas em memória, o número está no lugar errado.
- `build.gradle.kts` não ganha dependência de projeto. A lista vazia é a garantia.
