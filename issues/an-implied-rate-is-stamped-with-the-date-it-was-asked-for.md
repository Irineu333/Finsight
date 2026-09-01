---
area: transversal
severity: medium
type: data
version: 1.10.0
---

# Uma taxa implicada é carimbada com a data da pergunta, e o formulário a trata como observação de hoje

## Cenário

**DADO** base BRL, um sábado, e a observação `USD/BRL` mais recente do acervo datada da
sexta-feira (o Frankfurter publica em dia útil, e a sincronização grava a data da publicação)
**QUANDO** o usuário registra uma transferência **de** uma conta BRL **para** uma conta USD,
com a data de hoje
**ENTÃO** o campo "Entra em …" é **pré-preenchido** com o valor convertido pela taxa de sexta,
sem dizer de que dia ela é — e, submetido, o app grava no acervo
`(BRL, USD, sábado, 0,1818, colhida de operação)`, uma observação que ninguém fez naquele dia
**DEVERIA** comportar-se como o caminho contrário já se comporta: a mesma transferência **de**
USD **para** BRL, no mesmo sábado e com a mesma taxa, deixa o campo vazio e oferece o valor
como *placeholder* com a frase "pela taxa de 28/08"

## Mecânica

`ExchangeRateRepository.rateBetween()` tem dois caminhos e só um preserva a data da observação:

- **direto** — `dao.rateOfPairAsOf(from, to, date)` devolve a **linha armazenada**, com a data
  em que foi observada;
- **inverso e pivô** — `resolve()` devolve um `ResolvedRate` (só número e origem, sem data), e
  `answer(from, to, date, resolved)` monta o `ExchangeRate` com `date = date`, que é a data
  **da consulta**.

O KDoc de `ExchangeRate.date` diz "The day this rate is an observation about". Para o inverso e
o pivô isso é falso: a data devolvida é a que o chamador perguntou.

O único consumidor dessa data é o que não pode errá-la.
`SuggestCrossCurrencyAmountUseCase.invoke()` devolve `asOf = rate.date`, e
`CounterpartAmountField()` decide com ela: `val sameDay = suggestion != null && suggestion.asOf == date`.
`sameDay` liga o pré-preenchimento; `!sameDay` manda o valor para o *placeholder* com a data ao
lado. Como o inverso sempre devolve `asOf == on`, e `on` é a data da operação, `sameDay` é
**sempre verdadeiro** nesse caminho — o pré-preenchimento nunca é recusado.

A regra que isso quebra está escrita, e está escrita porque o laço é o problema (tarefa 11.5 de
`2026-08-01-add-multi-currency-accounts`): "pré-preencher com uma cotação de duas semanas atrás
gravaria a taxa velha como taxa nova, em silêncio e em laço". O que é digitado ali **vira** taxa
colhida — `TransferBetweenAccountsUseCase` e `WriteInvoicePaymentUseCase.harvest()` chamam
`HarvestExchangeRateUseCase` com os dois valores logo depois da escrita.

O sentido das linhas gravadas é o que torna o caso comum e não excepcional: a sincronização só
escreve `(moeda, base)` (`SyncExchangeRatesUseCase.invoke()`), então toda operação
`base → estrangeira` — transferência para fora, pagamento de fatura de cartão estrangeiro — cai
no inverso.

## Evidência

- `feature/settings/impl/.../repository/ExchangeRateRepository.kt` — `rateBetween()`, cujo
  curto-circuito direto devolve `mapper.toDomain(it)` e cujo ramo seguinte chama
  `answer(from, to, date, resolved)`; e `answer()`, onde `date = date` é a data da consulta
- `core/model/.../model/ExchangeRate.kt` — `date`: "The day this rate is an observation about"
- `core/model/.../usecase/SuggestCrossCurrencyAmountUseCase.kt` — `invoke()`: `asOf = rate.date`
- `core/ui/.../component/CrossCurrencyAmountFields.kt` — `CounterpartAmountField()`: `sameDay`,
  o ramo `if (sameDay && suggestion != null)` do `LaunchedEffect`, o `placeholder` e o
  `supportingText` `cross_currency_implied_by_rate`
- `core/model/.../usecase/HarvestExchangeRateUseCase.kt` — `invoke()` grava a linha
- `feature/accounts/impl/.../usecase/TransferBetweenAccountsUseCase.kt` e
  `feature/creditcards/impl/.../usecase/WriteInvoicePaymentUseCase.harvest()` — os chamadores
- `core/model/.../usecase/SyncExchangeRatesUseCase.kt` — `invoke()` só grava `(currency, base)`
- **o fake diverge do real no campo em disputa**:
  `core/model/src/commonTest/.../SuggestCrossCurrencyAmountUseCaseTest.kt`, `Archive.rateBetween()`
  faz `stored.copy(...)` mantendo `stored.date`. O comentário acima dele diz que "a fake that only
  answered the direct one would let this suite pass while the real archive said something else" —
  o fake responde o inverso, e ainda assim diz outra coisa que o acervo real
- `feature/settings/impl/src/jvmTest/.../ExchangeRateRepositoryResolutionTest.kt` — cobre número e
  origem do inverso e do pivô; **nenhum teste afirma a data**, e todos usam uma única data

## Consequência

Três, em ordem de peso.

**O valor de destino da operação.** O campo pré-preenchido é o que vai para o razão se o usuário
aceitar. O *placeholder* existe para obrigá-lo a digitar o que o banco de fato moveu; metade dos
pares perde essa proteção, e o razão passa a registrar um valor derivado de uma cotação velha que
o app nunca datou na tela.

**Uma observação fabricada no acervo.** A colheita grava `(base, estrangeira, hoje, taxa velha,
DERIVED)` — uma afirmação sobre um dia em que nada foi observado, que reaparece na tela de taxas
com a data de hoje. Cada nova operação no mesmo sentido recarimba o mesmo número num dia mais
novo, então o par nunca chega aos 30 dias que `ExchangeRatesViewModel` marcaria como
desatualizado: o app esconde que suas taxas envelheceram.

**Assimetria inexplicável.** O mesmo acervo, no mesmo dia, com a mesma taxa, pré-preenche num
sentido e oferece *placeholder* no outro.

## Sugestão

Carregar a data da observação no `ResolvedRate` — a mais antiga entre as pernas, no caso do pivô
— e usá-la em `answer()` no lugar da data da consulta. É o mesmo dado que `RateResolver.leg()` já
lê para escolher o pivô e descarta em seguida. Não vinculante.
