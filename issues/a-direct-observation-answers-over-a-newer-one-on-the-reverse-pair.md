---
area: transversal
severity: medium
type: data
version: 1.10.0
---

# Uma observação direta responde por cima de outra mais nova no par invertido

## Cenário

**DADO** base BRL e um acervo com duas observações sobre o dólar: `(USD, BRL) = 5,00` de janeiro,
e `(BRL, USD) = 0,125` — ou seja, `USD = 8,00` — registrada **hoje** pelo usuário
**QUANDO** qualquer figura consolidada é lida
**ENTÃO** o app usa 5,00, a de janeiro, e a observação de hoje não move número nenhum
**DEVERIA** responder com a observação mais recente sobre o par, como o próprio acervo declara
que faz

## Mecânica

A política de leitura está escrita duas vezes, com a mesma ênfase:

- `ExchangeRateDao` — "a classificação nunca prevalece sobre a data: `ORDER BY date DESC` vem
  primeiro… Caso contrário uma correção digitada em março responderia silenciosamente por agosto,
  que é o defeito que datar o acervo existe para evitar";
- `IExchangeRateRepository` — "a classificação desempata **dentro** de uma data e nunca sobre uma
  — uma observação mais recente vence qualquer que seja a origem".

Mas essa política é aplicada **por par**, na consulta. Acima dela, `List<ExchangeRate>.resolve()`
escolhe entre *pares* e não olha data nenhuma:

```kotlin
direct(from, to)?.let { return ResolvedRate(it.rate, it.source) }
direct(to, from)?.let { return ResolvedRate(1.0 / it.rate, it.source) }
return bestPivot(from, to)
```

`direct()` é `firstOrNull { it.currency == from && it.counterCurrency == to }`. Existindo
**qualquer** linha no sentido direto, ela responde — de janeiro, de dois anos atrás, tanto faz — e
o `return` impede que a inversa seja sequer consultada. `ExchangeRateRepository.rateBetween()` faz
o mesmo mais cedo ainda, no curto-circuito `dao.rateOfPairAsOf(from, to, date)`.

O KDoc de `resolve()` justifica a ordem por **fonte de erro** ("a inversa precede o pivô porque é
a mesma observação lida ao contrário") — um argumento que não diz nada sobre datas. O caso "existe
uma observação mais nova no par invertido" não está declarado em lugar nenhum, e o resultado é
exatamente o defeito que o `ORDER BY date DESC` foi escrito para impedir, um nível acima.

Os dois sentidos existirem no acervo não é configuração exótica: é o modelo.
`HarvestExchangeRateUseCase.invoke()` grava **no sentido em que a operação aconteceu** e
`ExchangeRate` não canoniza nada, então toda operação `base → estrangeira` produz linhas
`(base, estrangeira)`, enquanto `SyncExchangeRatesUseCase.invoke()` só produz `(estrangeira, base)`.
Quando a sincronização não alcança um par — moeda não coberta pela fonte, um estado que o app tem
tela para explicar — o sentido direto congela na última observação que houve, e todas as
observações reais posteriores caem no sentido invertido, que nunca é lido.

`ExchangeRateFormUiState` afirma o contrário ao justificar por que o formulário oferece as duas
pontas: "precificar a própria base contra outra moeda é uma observação legítima, e a inversa dela
alimenta a leitura". A inversa só alimenta a leitura quando não existe **nenhuma** linha direta.

## Evidência

- `feature/settings/impl/.../repository/RateResolver.kt` — `resolve()`: os dois `direct(...)` com
  `return`, sem nenhuma comparação de data; `direct()` é `firstOrNull`; e `leg()`, que carrega a
  data (`Triple(it.rate, it.date, it.source)`) — o dado necessário está à mão e é descartado
- `feature/settings/impl/.../repository/ExchangeRateRepository.kt` — `rateBetween()`
  (curto-circuito por `dao.rateOfPairAsOf`) e `ratesAsOf()` (`observations.resolve(currency, base)`)
- `core/database/.../dao/ExchangeRateDao.kt` — o KDoc da classe e o de `rateOfPairAsOf`, que
  declaram a data acima da origem, e a razão
- `core/model/.../repository/IExchangeRateRepository.kt` — "uma observação mais recente vence
  qualquer que seja a origem"
- `feature/settings/impl/.../modal/exchangeRateForm/ExchangeRateFormUiState.kt` —
  `selectableCurrencies`: "a inversa dela alimenta a leitura"
- `core/model/.../usecase/HarvestExchangeRateUseCase.kt` — grava no sentido da operação;
  `core/model/.../usecase/SyncExchangeRatesUseCase.kt` — só grava `(currency, base)`
- `feature/settings/impl/src/jvmTest/.../ExchangeRateRepositoryResolutionTest.kt` — todos os casos
  usam uma única data, então data-contra-caminho não é exercitado por nenhum teste

## Consequência

Toda figura consolidada do app pode estar sendo calculada com uma observação arbitrariamente velha
enquanto uma mais nova sobre o mesmo par está gravada e é ignorada — e a correção que o usuário
digita para arrumar exatamente isso não move nada, sem recusa e sem aviso. A tela de taxas lista as
duas linhas, cada uma com sua data e uma delas marcada "desatualizada", e não diz qual delas os
números usaram: a única superfície que se propõe a explicar o acervo aponta para o lugar errado.

## Sugestão

Fazer a escolha do caminho respeitar a data antes da topologia: entre a direta e a inversa, a
observação mais recente; o desempate por caminho só quando as datas empatam — que é literalmente o
que o acervo já declara fazer, aplicado ao nível acima. Não vinculante.
