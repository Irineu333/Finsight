## Context

Hoje `object CurrencyCatalog` (`:core:model`) declara 22 moedas em código, e é a autoridade
sobre quais moedas existem. Três coisas dependem dele:

- os **formulários** — conta, cartão, orçamento, taxa e moeda base — pegam
  `CurrencyCatalog.currencies` nos seus ViewModels;
- sete **composables** — quatro em `:core:ui`, dois em `feature/settings/impl` e um em
  `feature/transactions/impl` (`ViewTransactionModal`) — chamam
  `CurrencyCatalog.symbolOf(code)` diretamente, de forma síncrona e pura;
- duas **resoluções** o consultam para reduzir um código de origem desconhecida:
  `BaseCurrencyRepository` (locale → moeda base) e `legacyRelabelCurrency` (região →
  redenominação da base legada, consumida por uma migração via a `fun interface
  LegacyRelabel`).

O razão não o conhece, e nunca conheceu: `accounts.currency` e `entries.currency` são
strings ISO, sem chave estrangeira para lugar nenhum. É isso que torna esta mudança contida
— ela acontece inteiramente acima do razão.

O acervo de taxas também não o conhece: uma linha é `(precificada, contraparte, valor, data,
origem)`, códigos crus. Consolidação, triangulação e degradação em termo próprio já
funcionam para uma moeda que o catálogo nunca viu. Nada disso precisa mudar.

Restrições que a mudança não pode afrouxar: o razão continua sem conhecer o conjunto
(`currency-consolidation`); toda moeda continua de duas casas decimais; `:core:designsystem`
continua sem ver `:core:model`; e nenhuma leitura do app pode depender de rede.

## Goals / Non-Goals

**Goals:**

- O conjunto de moedas oferecidas passa a ser dado gravado, com fonte única e sem a
  distinção, em tempo de execução, entre embarcada e cadastrada.
- A semente encolhe para seis moedas sob um critério declarado, sem que nenhum usuário
  atual perca a moeda que já usa.
- O usuário cadastra, edita, arquiva e apaga moedas, com a plataforma sugerindo símbolo e
  nome.
- A primeira execução fica **melhor** do que hoje: a moeda do dispositivo passa a existir
  em vez de cair no último recurso.
- As 44 chaves `currency_name_*` deixam de existir, e os nomes passam a ser traduzidos pelo
  sistema operacional em todo idioma que ele fala.

**Non-Goals:**

- Romper a base 100. Moeda de zero ou três casas decimais continua inadmissível, e essa é
  outra mudança, de risco diferente.
- Consultar rede para nome, símbolo, lista ou cotação. A "sugestão" é sempre local, da
  plataforma.
- Tornar o razão ciente de moeda arquivada ou de qualquer coisa sobre o conjunto oferecido.
- Reabrir a imutabilidade da moeda de uma conta ou da denominação de um limite de
  orçamento.
- Reformular a tela de taxas, o agrupamento por contraparte ou a resolução por precedência.

## Decisions

### D1 — Uma tabela, e não semente embarcada ⊕ overlay de divergências

O conjunto oferecido é uma tabela `currencies`, e a semente é o conteúdo inicial dela — não
uma lista que sobrevive no código e é sobreposta por outra fonte.

A alternativa considerada foi o **overlay**: manter `CurrencyCatalog` como `const` e ter uma
tabela só com o que o usuário acrescentou ou alterou. Ela preservaria os nomes como
`UiText.Res`, mas ao custo de manter dois conjuntos e a máquina que os concilia: a união, a
precedência entre eles ("a linha do usuário vence a embarcada"), a semântica de divergência
parcial, e um consumidor que precisa ler das duas fontes.

A tabela única apaga tudo isso de uma vez. Não há dois conjuntos a unir, então não há regra
de colisão a escrever, testar ou explicar — um código existe ou não existe. O custo que o
overlay evitava é real, mas tem solução própria dentro da tabela única, que é D2.

### D2 — O nome é nulo por padrão, e a plataforma nomeia

`CurrencyEntity(code, symbol, name: String?, isArchived)`. `name` é gravado **somente**
quando o usuário o escreveu.

Materializar a semente com nomes gravados congelaria cada nome no idioma da primeira
execução: "Dólar americano" deixaria de virar "US Dollar" ao trocar o idioma do app, em
silêncio e irreversivelmente. É exatamente a classe de defeito que `currency-consolidation`
persegue ao falar de dado gravado cujo significado muda sozinho — e aqui seria pior, porque
o dado não muda de significado: ele para de acompanhar.

Com `name = null`, o nome vem de `Currency.getInstance(code).getDisplayName(locale)` (JVM e
Android) e de `NSLocale.localizedString(forCurrencyCode:)` (iOS), a cada leitura, no idioma
corrente — e recai no próprio código quando a plataforma não souber. Isso é estritamente
mais cobertura do que as duas línguas que o app fala.

O **símbolo** é gravado sempre: é curto, estável entre idiomas, e é o que aparece sobre um
valor. A plataforma o sugere no formulário e o usuário pode substituí-lo.

Consultar a plataforma para **nomear um código que já é linha** não é o mesmo que enumerar
moedas por ela. A enumeração foi considerada e recusada: `Currency.getAvailableCurrencies()`
e `NSLocale.commonISOCurrencyCodes` dão conjuntos diferentes por sistema operacional e por
versão, e o mesmo usuário veria listas distintas no Android e no Desktop. Aqui não há
conjunto envolvido — há uma pergunta sobre uma linha existente, cujo pior caso degrada para
o próprio código, que é o que `CurrencyFormatter` já faz.

### D3 — A semente é seis, sob um critério que não se re-litiga

**BRL, USD, EUR, GBP, CHF, CNY.** O critério: *o mercado de origem do app, mais as moedas em
que uma figura é legível por alguém de fora dele*. As cinco últimas são as mais
transacionadas do mundo, menos JPY, que a premissa de base 100 já barra hoje.

O catálogo atual tem viés LatAm deliberado — ARS, MXN, PEN, UYU. Encolher os tira, e isso
**agora** é seguro: a moeda do próprio usuário chega pela sua linha do locale (D4), não pela
semente. O argentino não precisa que o app adivinhe ARS; o dispositivo dele o diz. Foi o
auto-registro que tornou o encolhimento barato — encolher sem ele seria uma regressão.

`FALLBACK_CURRENCY` (USD) pertence à semente por obrigação: sem isso o último recurso
apontaria para uma linha que pode não existir, que é a única forma de a resolução da moeda
base não ter resposta.

### D4 — A semeadura é a criação da tabela, e é uma operação só

```sql
INSERT OR IGNORE INTO currencies (code, symbol, name, isArchived)
  VALUES ... -- semente: BRL, USD, EUR, GBP, CHF, CNY
             -- + SELECT DISTINCT currency FROM accounts  (ninguém perde o que já usa)
             -- + a moeda do locale                       (a moeda do dispositivo)
```

Sob o overlay de D1 seriam **duas** migrações com propósitos distintos — semear e
materializar o que está em uso. Sob a tabela única são a mesma, porque o destino é o mesmo.

**A semeadura pertence ao momento em que a tabela passa a existir, e não à migração.** A
primeira versão deste desenho dizia "é uma migração", e isso deixava de fora exatamente o
usuário que o requisito nomeia: num install novo o Room cria o schema a partir das
entidades e **não roda migração alguma**, então o único usuário sem moeda nenhuma seria o
que acabou de instalar — o mesmo que o cenário "a moeda do dispositivo chega pela
semeadura" descreve. A tabela nasce por dois caminhos, e a semeadura é o que acontece
quando ela nasce:

| a tabela nasce | quem semeia |
|---|---|
| banco que já existe (v12 → v13) | a migração |
| install novo (schema criado das entidades) | o callback de criação do Room |

Isso **não** é o segundo caminho que o parágrafo seguinte proíbe, e a distinção é o que
mantém a regra de pé: são dois *gatilhos* para a **mesma** escrita — uma função só,
chamada dos dois lugares —, não duas escritas que possam divergir. Um segundo caminho
seria um segundo `INSERT`, com a sua própria noção do que semear.

A consequência de projeto mais importante: **o auto-registro do locale deixa de ser um
mecanismo.** Não há "registro automático" a desenhar, testar ou explicar — há uma
semeadura, e a moeda do dispositivo está entre o que ela semeia.

O símbolo das linhas vindas das contas em uso é sugerido pela plataforma no momento da
semeadura, e recai no próprio código quando ela não souber — o mesmo que a exibição já
faz. A moeda do locale que não tenha duas casas decimais **não** é semeada, e nesse caso a
base recai no último recurso, como hoje.

`INSERT OR IGNORE` é o que torna a escrita idempotente e a precedência trivial: o símbolo
da própria semente vence a sugestão da plataforma para o mesmo código, e rodar a semeadura
sobre uma linha que já existe não a sobrescreve.

### D5 — O símbolo chega às composables por um port em `:core:common`, não por `:core:model`

Sete composables chamam `CurrencyCatalog.symbolOf` de forma síncrona. Com o conjunto virando
tabela, elas precisam de uma fonte reativa — e não podem receber `:core:model`:
`:core:designsystem` só vê `common` e `resources`, e é onde `FormattingLocalsHost` mora.

A saída é a mesma que `LegacyRelabel` já usa neste codebase: um `fun interface` declarado no
módulo de baixo, ligado onde as duas pontas são visíveis.

```
:core:common      CurrencySymbols { val symbols: Flow<Map<String, String>> }
                  LocalCurrencySymbols : (String) -> String   ← sem default
:core:designsystem FormattingLocalsHost coleta e provê, ao lado de LocalCurrencyFormatter
:app:shared        liga a implementação ao repositório, via Koin
```

Nenhum tipo de `:core:model` atravessa a fronteira — só `String`. `FormattingLocalsHost` já
faz `koinInject<CurrencyFormatter>()`, então não muda de assinatura e não nasce um segundo
host. O local não tem default, pelo mesmo motivo que `LocalCurrencyFormatter` não tem: um
default teria de fabricar uma fonte, e a única fonte é a tabela.

Quem precisa da **lista inteira** — os cinco formulários — não usa o local: recebe o
repositório no ViewModel, como já recebe todo o resto.

### D6 — Apagar leva o acervo junto; arquivar não leva nada

Apagar é recusado, com motivo acionável, quando uma **conta** ou um **orçamento** nomeia a
moeda — o formato que `DeleteAccountUseCase` e `DeleteCategoryUseCase` já têm. Uma linha do
**acervo de taxas** não bloqueia; ela é removida na mesma escrita.

"Não bloqueia" só é seguro por causa dessa segunda metade. Deixar a observação para trás
produziria três coisas:

1. **Pivô invisível.** O resolvedor lê o acervo sem consultar o conjunto oferecido, então
   `USD/PEN` + `PEN/BRL` continuariam triangulando por uma moeda que não existe em lugar
   nenhum da interface — uma figura que nenhuma tela consegue explicar.
2. **Formulário inválido.** Corrigir uma taxa abriria um formulário cuja ponta é PEN e cujo
   seletor não a contém — no único caminho pelo qual o usuário conserta um número errado.
3. **Recolagem de código reusado.** Apagar `PTS` ("pontos da livraria") e recadastrar `PTS`
   ("pontos do cartão") faria as observações antigas se colarem, em silêncio, num conceito
   diferente. Só acontece com código inventado — ISO não muda de significado.

O custo é destruir observação que o usuário fez. A mitigação é a confirmação **dizer o
número** — "3 taxas serão removidas junto" — em vez de escondê-lo.

**Arquivar não remove nada**: as observações continuam válidas e continuam sendo lidas.

### D7 — Arquivar uma moeda é regra de oferta, e tem uma linha de defesa, não duas

Todo arquivável deste app é barrado **duas** vezes: a oferta o esconde (`isArchived = 0` em
toda query de seleção) e a fronteira de escrita o recusa (`LedgerEntryWriter` recusa conta
arquivada). Uma moeda arquivada só pode ter a primeira, e isso é deliberado — o razão não
conhece o conjunto oferecido, e a moeda nem é linha que ele referencie: é um código
denormalizado em cada conta e em cada entry, sem chave estrangeira.

Consequência declarada, para não ser "corrigida" depois: uma conta numa moeda arquivada
continua ativa, continua aceitando lançamento e continua sendo consolidada. Arquivar
responde *"não me ofereça mais isto"*, não *"isto não vale mais"* — que é exatamente o que
categoria arquivada já significa para as transações antigas.

Pelo mesmo motivo, uma moeda arquivada **continua servindo de pivô** e continua aparecendo
no formulário que edita uma taxa que já a nomeia (para que a correção continue possível),
mas não é oferecida no cadastro de uma taxa nova.

A moeda **base** não é arquivável: arquivá-la denominaria toda figura consolidada numa moeda
que o app declara não oferecer. Trocar a base é o caminho para arquivar a que era base.

### D8 — Onde cada peça mora

| peça | módulo | por quê |
|---|---|---|
| `ICurrencyRepository`, `CurrencyInfo`, erros | `:core:model` | ao lado de `IBaseCurrencyRepository`; é a camada de consolidação |
| `CurrencyEntity`, DAO, migração | `:core:database` | onde as entidades de facade e toda migração já moram |
| `CurrencySymbols`, `LocalCurrencySymbols` | `:core:common` | port, para `:core:designsystem` não precisar ver `:core:model` (D5) |
| nome e símbolo sugeridos | `:core:common` | `expect`/`actual`, ao lado de `localeCurrencyCode()` |
| tela, formulário, ações | `feature/settings/impl` | já é o dono da moeda base e do acervo de taxas |
| `CurrencyPickerModal` | `:core:designsystem` | **inalterado** — já renderiza o que recebe |

### D9 — `legacyRelabelCurrency` não passa a consultar a tabela: o seu filtro vira a premissa

A primeira versão deste desenho dizia que ele consultaria a tabela, e que a semeadura teria
de rodar antes. **Isso é impossível, e verificá-lo desfez o problema em vez de resolvê-lo.**
O relabel é a migração `10 → 11`; a semeadura só pode ser `12 → 13`, porque o banco está na
v12. Num upgrade a partir da v10 o relabel roda antes de a tabela existir, e nenhuma ordem
resolve isso sem reescrever uma migração já publicada.

Mas ele não precisa da tabela. `legacyRelabelCurrency(region)` roda em `:core:model` e
entrega um código puro; o que ele faz com o catálogo é **barrar** uma moeda que o app não
admite. Sob o desenho novo, "o que o app admite" não é mais uma lista — é a premissa de
**duas casas decimais**, e quem a responde para um código é a plataforma (D2), sem tabela,
sem banco e sem ordem.

E a ordem se resolve sozinha na direção contrária: o relabel escreve `accounts.currency`, e
a semeadura lê `SELECT DISTINCT currency FROM accounts` (D4). O que o relabel denominar é
semeado por consequência, sem que nenhuma das duas migrações saiba da outra.

Fica **mais** correto do que era: o filtro deixa de ser "está na minha lista de 22", que era
uma curadoria a exercer o papel de uma regra, e passa a ser a regra em si.

O teste de migração continua obrigatório — cobrindo install novo e upgrade a partir de v10 e
de v12 —, mas para verificar que a semeadura recolhe o que o relabel escreveu, e não uma
ordem que não existe.

### D10 — O guarda de inércia ganha entradas, e não perde a regra que guarda

`SingleCurrencyInertiaTest` vai acusar o formulário de moeda e o repositório. Nenhum dos
dois é uma terceira porta para **denominar conta**: eles criam uma moeda, não uma conta —
mesma categoria da exceção que o formulário de orçamento e o de taxa já ocupam ali. Entram
no `expected` com o motivo escrito, como as demais.

`theOneResolver` (`CurrencyCatalog.reduce(localeCurrencyCode())`) muda de forma junto com o
catálogo, e o teste tem de continuar nomeando **a** expressão que decide uma moeda — não uma
que já não existe, o que o faria passar sem guardar nada.

## Risks / Trade-offs

- **A semeadura roda depois do relabel, e não há como inverter (D9)** → o relabel deixa de
  depender da tabela, e a semeadura recolhe o que ele escreveu via `SELECT DISTINCT currency
  FROM accounts`. Teste de migração cobrindo install novo e upgrade a partir de v10 e de v12.
- **Apagar destrói observações do acervo (D6)** → a confirmação declara quantas; e a
  exclusão só é possível quando nada é denominado na moeda, então nenhuma figura degrada.
- **O nome da plataforma pode divergir do curado** → "Brazilian Real" no lugar de "Real
  brasileiro". É um nome menos afinado em troca de tradução viva em todo idioma; e o usuário
  pode escrever o nome que quiser, o que passa a linha a guardá-lo.
- **`symbolOf` deixa de ser puro e síncrono** → o port de D5 mantém a chamada nas composables
  com a mesma forma (`String -> String`), e o custo fica na coleta única do host.
- **Usuário arquiva a moeda e continua lançando nela pela conta (D7)** → é o comportamento
  declarado, e está na spec para não ser "consertado" com um veto no razão.
- **Perda das 44 chaves `currency_name_*`** → irreversível na prática, mas nenhuma delas é
  referenciada fora do catálogo que some.
- **Regressão silenciosa em quem tem moeda fora da semente** → coberta por teste de migração
  que parte de um banco com conta em moeda que sai da semente e verifica que ela existe,
  aparece nos formulários e mantém as figuras.

## Open Questions

- CAD e AUD entram na semente (oito em vez de seis)? O critério de D3 as admitiria; ficou em
  seis por ser o corte mais defensável. Decisão reversível a custo baixo — é conteúdo de
  migração, não estrutura.
- O formulário deve **recusar** um código que a plataforma não reconhece, ou apenas deixar
  de sugerir? A spec hoje só exige unicidade e duas casas, o que permite `MILHAS`; recusar o
  desconhecido fecharia o caso (c) de uso — unidades não-monetárias — que motivou parte da
  mudança. Fica aberto: a implementação segue permitindo.
- Editar o **código** de uma moeda existente deve ser possível? Hoje o desenho não o oferece,
  porque o código está denormalizado em contas, entries, orçamentos e taxas — renomeá-lo é
  uma migração de dados, não uma edição. Vale confirmar que "apagar e cadastrar de novo" é
  resposta aceitável.
