---
area: settings
severity: low
type: data
---

# O estado da sincronização de taxas usa delimitadores que o código de moeda pode conter

## Cenário

**DADO** que o formulário de moeda aceita explicitamente um código inventado — *"O código
ISO da moeda, como BRL ou CLP — ou um que você inventar"*
**QUANDO** o usuário registra um código contendo `,`, `=` ou `>` e o app sincroniza taxas
**ENTÃO** a linha gravada em preferências não sobrevive à releitura: o par é esquecido, ou
lido como um par diferente, e a lista de moedas não cobertas ganha códigos fantasma
**DEVERIA** ou recusar o código na gravação, ou serializar de forma que qualquer texto
sobreviva à ida e volta

## Mecânica

`RateSyncStateRepository.record()` monta `"$currency>$against=$millis"`, unidos por `,`, e
`read()` desfaz com `substringBefore`/`substringAfter` e `split(",")`. Nenhum dos dois
escapa nada.

Do outro lado, `SaveCurrencyUseCase` normaliza com `trim().uppercase()` e verifica só
`isBlank`, casas decimais e unicidade — nenhuma validação de **forma**. E o campo de código
é um `OutlinedTextField` cru: sem `inputTransformation`, sem limite de tamanho, sem filtro
de caracteres.

## Evidência

- `feature/settings/impl/.../repository/RateSyncStateRepository.kt` — `record()` e `read()`;
  `SEPARATOR = ","`, `ASSIGN = "="`, `PAIR = ">"`, sem escape
- `feature/settings/impl/.../usecase/SaveCurrencyUseCase.kt` — `invoke()`, as quatro
  verificações, nenhuma de forma
- `feature/settings/impl/.../currencyForm/CurrencyFormModal.kt` — o campo de código, sem
  transformação nem limite
- `core/common/.../extension/PlatformCurrency.kt` — `isTwoDecimalCurrency()` devolve `true`
  para código desconhecido, por decisão declarada
- consumidor afetado: `feature/settings/impl/.../ExchangeRatesViewModel.uiState` —
  `isBaseNotCovered = base in syncState.notCoveredCurrencies`

## Consequência

Para esses pares, o limite "uma vez por dia por par" deixa de valer — requisição a cada
abertura — e a frase "não coberta pela fonte" pode nomear moedas que não existem. Nada de
contábil é corrompido: o razão não lê esse arquivo.

## Sugestão

Validar a forma do código em `SaveCurrencyUseCase`, que é o dono único da criação — mais
barato do que escapar, e protege qualquer formato futuro de armazenamento. Não vinculante.
