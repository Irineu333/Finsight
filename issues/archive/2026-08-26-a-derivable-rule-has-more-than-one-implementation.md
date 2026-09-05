---
area: transversal
severity: low
type: data
verdict: fixed
---

# Regras deriváveis com dono declarado têm mais de uma implementação

## Invariante

Uma regra que se deriva do domínio tem exatamente um dono, e os consumidores a consomem.

Hoje é falso em dois pontos, os dois tocados pelo redesenho da tela de recorrentes.

**A moeda de um cartão** — "é a da conta `LIABILITY` que ele projeta" — tem três
implementações, sob dois contratos: `String?` em duas delas, `String` não-nulo na terceira. A
versão que `feature/accounts/api` publica **inlinou** a regra; a que ela substituiu delegava.

**A elevação de um agregado em centavos para a unidade maior**, sobre `CurrencyScoped`, tem
duas, cada uma com o seu `CENTS_PER_UNIT` — embora o KDoc da interface declare que ela existe
para que isso aconteça "through one path instead of one per projection".

## Mecânica

A duplicação da moeda do cartão sobreviveu à change que se propunha a removê-la, por uma
justificativa que o compilador refuta: `tasks.md` 1.2 e o commit `5a0e0a401` afirmam que
publicar a sobrecarga nula ao lado de `IAccountRepository` tornaria ambíguas as chamadas de
`feature/creditcards/impl`, que declara a não-nula no mesmo pacote. Não torna — a declaração
do próprio módulo vence a resolução, e `:feature:creditcards:impl:compileKotlinJvm` passa com
as duas no classpath.

## Evidência

- `feature/accounts/api/.../extension/RecurringCurrency.kt` —
  `IAccountRepository.currencyOf(Recurring)`, com a regra do cartão escrita inline
- `feature/recurring/impl/.../extension/RecurringCardCurrency.kt` —
  `IAccountRepository.currencyOf(CreditCard)`, `String?`
- `feature/creditcards/impl/.../extension/CardCurrency.kt` —
  `IAccountRepository.currencyOf(CreditCard)`, `String` não-nulo, com `requireNotNull`
- `core/ledger/.../repository/EntryRepository.kt` — `toMoney()`, `private`, e o
  `CENTS_PER_UNIT` do arquivo
- `feature/recurring/impl/.../repository/RecurringOccurrenceRepository.kt` — `toMoney()` e um
  segundo `CENTS_PER_UNIT`
- `core/ledger/.../dao/EntryDao.kt` — o KDoc de `CurrencyScoped`, que promete "one path"

## Consequência

Três implementações de uma regra sob dois contratos: uma correção aplicada a uma não alcança
as outras, e a divergência se manifesta como uma figura na moeda errada — exatamente o dano
que `DisplayAmount` existe para tornar impossível. A elevação de centavos duplicada leva uma
convenção do razão para dentro de um módulo de fachada, onde nada a mantém sincronizada.

## Sugestão

Publicar uma implementação nula da moeda do cartão e transformar a não-nula num invólucro
renomeado (`requireCurrencyOf`), que é rename mecânico nos pontos de chamada de um módulo só;
e publicar o `toMoney` de `CurrencyScoped`, apagando a cópia. Não vinculante.

## Desfecho

**Causa real** — as duas do relato, confirmadas no código. E o levantamento estava
**incompleto**: um grep por `getAccountById(…)?.currency`, em vez de pelo nome `currencyOf`,
achou mais **cinco** leituras inline da moeda do cartão que a evidência não enumerava —
`InvoicePaymentViewModel`, `WriteInvoicePaymentUseCase`, `DashboardComponentsBuilder`,
`AddTransactionViewModel` e `EditTransactionViewModel`. Eram oito implementações, não três.

**Mudança** — `IAccountRepository.currencyOf(CreditCard): String?` nasceu em
`feature/accounts/api` como a única leitura da regra. `currencyOf(Recurring)` passou a delegar
a ela em vez de inliná-la; a cópia de `feature/recurring/impl` foi apagada; a não-nula de
`feature/creditcards/impl` virou `requireCurrencyOf`, um invólucro que já não duplica a regra
e só declara que naquele módulo a ausência é invariante quebrada e não figura a omitir (nove
pontos de chamada renomeados); e as cinco leituras inline passaram a consumir a mesma função.

O `toMoney` de `CurrencyScoped` foi publicado em `core/ledger`
(`database/repository/CurrencyScopedMoney.kt`) — que é o "one path" que o KDoc da interface já
prometia —, e as duas cópias foram apagadas junto com o segundo `CENTS_PER_UNIT`.

**Prova** — `./gradlew jvmTest --rerun-tasks` verde, 1489 testes em 249 classes, nenhuma
falha, sem mudança de expectativa em teste algum: a unificação é comportamento idêntico em
todos os pontos, exceto onde `requireCurrencyOf` preserva deliberadamente o `requireNotNull`
que já existia.

**O que continua fora** — `ConsolidateMoneyUseCase.CENTS_PER_UNIT`, `private` no companion e
usado para converter, não para elevar um agregado do razão. É outra pergunta e não estava no
escopo deste relato.
