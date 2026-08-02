# Design — `enable-base-currency-switch`

## Context

A change `add-multi-currency-accounts` construiu a camada de consolidação inteira e deixou **uma** porta fechada de propósito: a troca da moeda base. A decisão está registrada em D18/D28 e o requisito que a descreve foi escrito ao contrário do usual — não descreve um fluxo, descreve uma **proibição sobre o desenho**: *"o que estas duas frases exigem é que trocá-la permaneça derivação — que nada seja gravado hoje que impeça oferecê-la depois"*.

Essa promessa foi cumprida quase por inteiro, e é por isso que esta change é pequena onde se esperaria que fosse grande:

| o que a troca exigiria | estado |
|---|---|
| nenhum valor convertido persistido | ✅ já é assim, e há teste |
| toda figura observar a base reativamente | ✅ `ObserveConsolidationChangesUseCase` já funde o flow |
| a redução ter dono único | ✅ `ConsolidateMoneyUseCase` |
| o acervo bastar para re-exprimir | ✅ verificado em `BaseCurrencySwitchDerivationTest` |
| **o acervo dizer contra o que foi medido** | ❌ **e é aqui que a change mora** |

`ExchangeRateEntity` declara: *"It does not name the base currency."* A denominação de cada linha é implícita e global. Enquanto a base não muda, é verdade; quando muda, é mentira, e o dado não tem onde guardar a verdade:

```
Base BRL. Acervo:                  Troca para USD. As mesmas linhas,
  USD @ 14/03 = 5.50               lidas ingenuamente:
  EUR @ 14/03 = 6.00                 "1 USD = 5.50 USD"   ← absurdo silencioso
                                     "1 EUR = 6.00 USD"   ← errado por 5,5×
```

Sem erro, sem exceção, sem sintoma — a mesma classe de defeito que o `:core:ledger` foi construído para tornar impossível.

## Goals / Non-Goals

**Goals**

- Tornar a denominação de cada taxa explícita no dado.
- Oferecer a troca da moeda base como escrita de uma preferência, sem migração, sem reprocessamento e sem tocar em linha gravada.
- Declarar uma resolução determinística quando existe mais de um caminho entre duas moedas.
- Manter `ConsolidateMoneyUseCase` e toda a superfície de exibição intactos.

**Non-Goals**

- **Busca em grafo com mais de um salto.** Ver D3.
- **Cálculo de cobertura, aviso, bloqueio ou cadastro obrigatório de taxa no fluxo de troca.** Ver D6.
- **Reescrever o acervo na troca.** Continua proibido, e o teste que o proíbe permanece.
- **Fonte externa de taxa fora da tela de edição.** Inalterado.
- **Moedas com expoente diferente de 2.** D14 da change anterior permanece.
- **Histórico de moedas base já usadas.** Ver D2 — o par explícito o torna desnecessário.

## Decisions

### D1 — A taxa passa a ser uma observação sobre um **par**, e o acervo perde a base

`exchange_rates` ganha `counterCurrency`. Uma linha passa a se ler sozinha:

```
currency=USD  counterCurrency=BRL  rate=5.50   →   "1 USD = 5,50 BRL"
```

Duas leituras deste desenho foram consideradas e **são a mesma tabela**, o que é o achado que dimensiona a change:

| | acervo por moeda (silo) | par livre |
|---|---|---|
| colunas | `(base, currency, date, rate, source)` | `(from, to, date, rate, source)` |
| pares permitidos | só onde uma ponta é base já vigente | qualquer um |
| dados já gravados | idênticos | idênticos |

A diferença não é de schema — é de **restrição sobre o que pode entrar daqui pra frente**, e par livre é superconjunto estrito do silo. Como o formulário passa a escolher as duas pontas (D8), a restrição não teria como ser sustentada de qualquer forma, e sustentá-la só custaria: descartaria observações boas (D7).

Ganho conceitual que vale registrar: a spec abre dizendo *"a moeda base é preferência de exibição e nunca fato contábil"*. Hoje isso é verdade no razão e falso no acervo, onde a base está implícita em toda linha. Com o par explícito passa a ser verdade nos dois lugares.

- *Alternativa considerada:* **um `Settings` novo com a "base do acervo"**, imutável, separado da base de exibição — a moeda-pivô eterna. Zero mudança de schema, e por isso foi a candidata mais forte. Rejeitada por corromper a escrita: depois da troca o usuário observa o mundo na base nova, e gravar exigiria converter **antes** de gravar. O acervo passaria a conter números que ninguém observou, que é exatamente o que *"o quociente pleno, nunca a forma exibida"* existe para impedir — só que agora na entrada, onde não há como perceber. Some com o problema de leitura e o reintroduz na escrita, que é pior.
- *Alternativa considerada:* **re-exprimir o acervo no momento da troca** (inversa + triangulação, gravando). Rejeitada frontalmente: é migração, destrói as observações originais, e `BaseCurrencySwitchDerivationTest::no stored row changes` existe precisamente para barrá-la.
- *Alternativa considerada:* **manter um histórico de bases já vigentes** e usá-lo para interpretar linhas antigas. Rejeitada: é a denominação implícita outra vez, só que agora com uma tabela de tradução do lado — a linha continua sem se explicar, e qualquer bug de ordenação nesse histórico reinterpreta dados gravados em silêncio.

### D2 — A direção **não** é canonicalizada na gravação

Grava-se o par como foi observado. O sistema MUST NOT ordenar as pontas nem inverter o quociente para caber numa forma canônica.

A tentação é real — canonicalizar daria uma chave única por par e mataria D3 inteiro. E é exatamente por isso que precisa ser recusada: inverter para gravar produz um número que ninguém mediu, e a spec já decidiu essa questão para o caso da exibição (*"gravar o valor exibido tornaria cada exibição uma perda de precisão acumulável"*). A entrada não merece regra mais frouxa que a saída.

**Consequência aceita:** o mesmo par pode existir nos dois sentidos, e as duas linhas podem discordar — por arredondamento e por erro de observação. Elas são duas observações distintas e a tela as mostra como tais (D9). Quem decide qual vale numa leitura é D3.

### D3 — A resolução tem três níveis, e para no primeiro salto

```
Pergunta: quanto vale 1 EUR em BRL, em 14/03?

1. DIRETA        (EUR, BRL)                     ┐  dentro de cada nível:
2. INVERSA       (BRL, EUR) → 1/r               │  USER ▸ DERIVED,
3. UM PIVÔ       (EUR, P) e (P, BRL)            ┘  data mais recente ≤ 14/03
                 nível 1 e 2 valem em cada perna

Nenhum dos três resolve → não há taxa. A figura degrada em termo próprio,
que é o comportamento definido de sempre.
```

Sem esta precedência, par livre significa que a mesma pergunta tem várias respostas que discordam, e a propriedade que este código mais protege — *saber de onde veio um número* — morre sem sintoma.

**Um salto, e não busca em grafo.** Dois saltos compõem três arredondamentos e três observações independentes num número que tela nenhuma consegue explicar. E um caminho de dois saltos é sempre um acervo em que falta a taxa óbvia: ali a resposta certa é a figura degradar, não o app inventar um caminho longo. É a mesma linha que D9 da change anterior já traça — *uma taxa ausente MUST NOT ser tratada como `1`* —, aplicada a um caminho ausente.

**O pivô precisa de desempate determinístico**, ou o mesmo acervo responde coisas diferentes conforme a ordem em que as linhas saíram do banco. A regra: vence o pivô cujas duas pernas tenham as datas mais recentes; empate resolvido pelo código ISO, crescente. O primeiro critério estende a preferência por recência que a política de data já tem; o segundo é arbitrário e existe só para ser estável — o que importa nele é ser total, não ser justo.

- *Alternativa considerada:* **Dijkstra/BFS sobre o grafo de moedas**, com o caminho mais curto ou o de menor erro acumulado. Rejeitada: transforma "de onde veio este número" numa pergunta que exige rodar o algoritmo para responder, e o ganho é um caso que na prática significa acervo incompleto.
- *Alternativa considerada:* **inversa depois do pivô**, em vez de antes. Rejeitada: a inversa é a **mesma observação** lida ao contrário, e o pivô são duas outras. Preferir duas observações a uma seria preferir mais fontes de erro.

### D4 — A triangulação mora no repositório, e a camada de consolidação não aprende que a base troca

```
ConsolidateMoneyUseCase        ← INTACTO. Zero linhas.
        │  ratesAsOf(date)
        ▼
IExchangeRateRepository        ← contrato INTACTO: "a taxa em vigor para a moeda"
        │                        (que é, e sempre foi, contra a base)
        ▼
ExchangeRateRepository (impl)  ← AQUI. Aplica D3 e devolve tudo já contra a base.
        │
        ▼
ExchangeRateDao                ← devolve observações; a política de data continua
                                 sendo a query, com um dono só.
```

O `IExchangeRateRepository` **já** promete taxas contra a base. Hoje isso é verdade por acidente — só existe uma base. O trabalho é torná-lo verdade por construção, e o lugar onde isso se faz é o impl, que é o único ponto que pode saber ao mesmo tempo o que está gravado e qual preferência está em vigor.

O impl ganha dependência de `IBaseCurrencyRepository`. É aceitável: os dois vivem em `feature/settings/impl`, e é a dependência que substitui a suposição implícita que existe hoje.

**Corolário:** a resolução é exposta também na forma geral, por par arbitrário — `ratesAsOf` é o caso particular "todas contra a base". Isso não é escopo novo, é o mesmo resolvedor com a pergunta feita por inteiro, e é o que faz `SuggestCrossCurrencyAmountUseCase` deixar de ser cego entre duas não-base sem ganhar código próprio.

### D5 — O setter volta a ser honesto, e não existe caso de uso

O `IBaseCurrencyRepository` carrega hoje um bloco de comentário explicando por que **não** tem setter: *"escreveria o código novo deixando todo o acervo sendo lido contra uma base em que nenhuma delas foi medida"*. Com D1, essa frase deixa de valer — toda linha diz contra o que foi medida, e nenhuma muda de significado quando a preferência muda.

Trocar a base passa a ser:

```kotlin
settings.putString(KEY, novo)
_currency.value = novo
```

E mais nada. Nenhuma re-expressão na escrita, nenhuma migração, nenhuma linha tocada. A re-expressão inteira é leitura, e ela já tem dono (D4).

**Não existe `SwitchBaseCurrencyUseCase.`** Não há caso de uso: há uma preferência que se escreve. Criar um seria dar dono a uma operação que não faz nada, e a `Derivation rule` do projeto — *"uma regra que pode ser derivada do domínio tem exatamente um dono, no domínio"* — não pede o inverso, que seria inventar domínio onde não há.

### D6 — A troca é simples, e não cobra nada adiantado

Catálogo inteiro, sem confirmação, sem cálculo de cobertura, sem bloquear moeda que o acervo não alcança, sem exigir cadastro de taxa no fluxo. O usuário cadastra depois.

O que se está aceitando, dito por inteiro: trocar para uma moeda que o acervo nunca precificou faz **toda** figura consolidada degradar em termos por moeda, retroativamente e em todas as datas. Isso é aceitável por três razões, e a terceira é a que decide:

1. É comportamento **já definido e já testado** — a spec exige que *"o estado em que uma conta em moeda não-base existe e a sua taxa ainda não foi cadastrada tenha comportamento definido"* justamente por ser alcançável por construção. A troca só o alcança por outra porta.
2. **Nada é destruído e a troca é reversível** a qualquer momento, porque nenhuma linha muda.
3. **A recuperação é uma taxa por data, não uma por moeda.** Todo o acervo legado está denominado na base antiga, então uma única linha nova — a base antiga contra a nova, naquela data — reabre a triangulação de todas as outras daquela data:

```
EUR→USD = (BRL por EUR) ÷ (BRL por USD)   ┐  a linha nova é o único
JPY→USD = (BRL por JPY) ÷ (BRL por USD)   ┘  denominador que faltava, para todas
```

Confirmação também não: ela pediria ao usuário para autorizar algo que não destrói nada e que ele desfaz sozinho.

**Ressalva, registrada e aceita:** a política de leitura é *a última taxa em ou antes da data*, então uma taxa cadastrada hoje não recupera janeiro — janeiro precisa de linha datada de janeiro. O formulário já tem seletor de data, então é possível; mas é opt-in, e quem não fizer verá o período anterior à troca em termos por moeda indefinidamente. Não é defeito: é o app não sabendo o que de fato não sabe.

- *Alternativa considerada:* **calcular e exibir a cobertura antes de confirmar** ("o acervo alcança esta moeda desde 12/02"). Rejeitada por decisão de produto: constrói máquina — e um número que o usuário precisa interpretar — para um estado que já se explica sozinho quando acontece.
- *Alternativa considerada:* **oferecer o cadastro da taxa dentro do fluxo de troca**. Rejeitada pela mesma razão, e por transformar uma escolha de duas batidas num formulário.

### D7 — Todo cruzamento passa a ensinar

`HarvestExchangeRateUseCase` descarta hoje a observação quando nenhuma das duas pontas é a base — `HarvestExchangeRateUseCaseTest::a crossing between two non-base currencies teaches nothing`. Essa guarda nunca foi uma regra de domínio: era a consequência de a linha não conseguir dizer sobre que par ela falava. Com D1 ela perde a razão de existir e sai, junto com o teste que a fixava.

Não é escopo acrescentado — é escopo que **deixa de ser artificialmente removido**. Manter a guarda com o par explícito seria escrever código para jogar fora uma observação boa.

### D8 — O formulário escolhe as duas pontas, com a base como default

`ExchangeRateFormViewModel` filtra hoje `CurrencyCatalog.currencies.filter { it.code != base }` e presume a base como contraparte. Passa a oferecer as duas pontas, com a base em vigor pré-selecionada numa delas, e a única restrição passa a ser `from ≠ to`.

O filtro atual deixa de fazer sentido em ambas as pontas: precificar a própria base contra outra moeda é observação legítima, e a sua inversa alimenta a leitura por D3.

**Consequência aceita:** é possível cadastrar um par que não serve para nada — EUR/JPY num usuário de base BRL sem nenhuma ponte. A linha fica inerte, não errada. Barrá-la exigiria que o formulário soubesse resolver caminhos, que é conhecimento de D4, e para prevenir um dado inofensivo.

### D9 — A listagem agrupa pela **moeda contraparte**, e a linha se descreve inteira

Uma taxa tem duas pontas, então "agrupar por moeda" precisa escolher uma ou duplicar. **A escolha é decidida pelo caso comum**, e não pela simetria das duas pontas.

No acervo ordinário — um usuário, uma base, tudo precificado nela — as duas escolhas não são simétricas de forma alguma:

```
agrupar pela precificada          agrupar pela contraparte
  USD                               Cotados em BRL
    1 USD = 5,50 BRL                  1 USD = 5,50 BRL · 14/03 · você
  EUR                                 1 EUR = 6,00 BRL · 14/03 · operação
    1 EUR = 6,00 BRL                  1 JPY = 0,037 BRL · 02/03 · você
  JPY                               Cotados em USD
    1 JPY = 0,037 BRL                 1 JPY = 0,0068 USD · 20/07 · você
```

À esquerda, cada grupo tem exatamente uma linha: o agrupamento não agrupa. À direita ele reúne, e o cabeçalho diz a frase que o usuário veio ler — *cotados em BRL*. É o código sozinho, e não o nome do catálogo ao lado dele: toda linha do grupo termina nesse mesmo código, então soletrar a moeda no cabeçalho diria uma terceira vez o que as linhas já dizem. O segundo grupo só existe quando existe observação fora do eixo da base, que é justamente quando separar tem informação.

**Consequência aceita:** depois de uma troca, o mesmo par aparece em dois grupos, um por sentido. Não é defeito — são duas observações distintas, em direções distintas, e esta tela mostra observações. O que evita a confusão é a linha se descrever por inteiro em vez de exibir só o número sob um cabeçalho.

**Ordem dos grupos:** moeda com observação mais recente primeiro, que é a extensão natural do `ORDER BY date DESC` que o DAO já faz.

- *Alternativa considerada:* **agrupar pela moeda precificada** — *"quanto vale 1 dólar"*. Foi a primeira escolha, e caiu no diagrama acima: ela responde uma pergunta que o acervo comum não tem, porque ali a moeda precificada é sempre distinta e a contraparte é sempre a mesma.
- *Alternativa considerada:* **agrupar pelo par** (`EUR / BRL` como cabeçalho). É a resposta mais limpa à ambiguidade e foi a recomendação inicial. Preterida por decisão de produto: o usuário procura por moeda, não por par — e, no caso comum, agrupar por par tem o mesmo defeito de não agrupar nada.
- *Alternativa considerada:* **exibir cada linha sob as duas pontas**, invertida numa delas. Rejeitada por uma razão dura: esta tela também é o **editor**, e tocar numa linha invertida abriria a edição de um número que ninguém observou — ou abriria a observação original noutra direção da que o usuário tocou. As duas saídas são piores que a duplicação por sentido.

### D10 — A migração recebe a base como código puro, pelo caminho que já existe

`AppDatabase` vai de `11` para `12`. `migration1112` acrescenta `counterCurrency`, troca o índice único de `(currency, date, source)` para `(currency, counterCurrency, date, source)` e preenche a coluna nova com a base semeada.

O preenchimento é **exato**, e não uma aproximação: toda linha existente foi medida contra a base em vigor, que nunca teve como mudar. É a mesma qualidade que o preenchimento da moeda do limite de orçamento teve na `migration1011`.

A base mora no `Settings` e não no banco, e `core/database` não pode alcançá-la. O caminho já existe e é o mesmo que D30 da change anterior abriu para a reetiquetagem legada: uma `fun interface` resolvida onde a preferência é visível, entregando à migração um código puro — *"o módulo de baixo recebe o que não pode nomear"*.

## Riscos / Trade-offs

- **A mesma pergunta pode ter mais de uma resposta.** É o preço do par livre, mitigado por D3, mas não eliminado: um usuário que cadastre observações contraditórias verá um número que talvez não espere. A defesa é a tela mostrar as observações como foram feitas (D9), de forma que a contradição seja visível onde ela existe.
- **As duas pernas de uma triangulação podem resolver para datas diferentes.** A política é *a última em ou antes*, aplicada por perna: `BRL→EUR` de 14/03 sobre `BRL→USD` de 02/03 é um resultado legítimo e é o melhor disponível. Fica declarado em vez de acidental.
- **O histórico anterior à troca fica degradado até o usuário cadastrar taxas retroativas.** Aceito em D6, e é a consequência direta de a troca não cobrar nada adiantado.
- **A leitura fica mais cara.** Onde hoje há uma query e um `associate`, passa a haver a resolução de três níveis. O custo é limitado pelo número de moedas em uso, que D14 já mantém pequeno, e nenhuma leitura passa a esperar rede — que é a garantia que de fato importa.
- **Linhas inertes são possíveis** (D8). Ocupam espaço na listagem e não fazem mal.
