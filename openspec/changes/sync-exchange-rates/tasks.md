# Tasks — `sync-exchange-rates`

> **A regra que governa a ordem dos grupos.** A change tem um produtor novo (a fonte
> remota, que escreve linhas `REMOTE` no acervo) e um leitor que precisa saber lê-las (a
> precedência de três níveis, hoje escrita como binária em dois lugares). Entre "existe
> linha `REMOTE`" e "a leitura a ordena corretamente" o intervalo tem de ter **comprimento
> zero**: uma linha `REMOTE` gravada antes do ranking seria ordenada pelo `ELSE 1` de hoje,
> empatada com a `DERIVED` do mesmo dia, e o desempate passaria a depender da ordem em que
> o SQLite devolveu as linhas — figura consolidada correta ou errada por sorteio, sem erro e
> sem marca. Por isso o valor do enum (grupo 2) precede o ranking (grupo 3), o ranking
> precede a costura (grupo 4), e **nada sincroniza até o grupo 5**, quando o binding e o
> gatilho entram juntos. Não é preferência de ordenação: é a única ordem em que nenhuma
> linha `REMOTE` existe antes de haver quem a leia.
>
> **Os grupos 1 a 4 são inteiramente inertes.** Nenhum deles faz o app sincronizar. O
> grupo 1 acrescenta ports sem implementação e dependências sem uso; o 2 acrescenta um
> valor de enum que ninguém grava; o 3 acrescenta consultas sem chamador; o 4 acrescenta
> escritores sem binding. É essa inércia que permite que cada um deles termine com
> `./gradlew allTests` verde, e é ela que torna a paralelização de cada grupo segura.
>
> **O que esta change explicitamente não faz** — declarado aqui para que nenhuma tarefa
> abaixo o invente:
> - **Nenhuma migração, nenhuma versão nova de `AppDatabase`, nenhum schema exportado.**
>   Room grava enum como texto e não emite `CHECK`, e o índice único de `exchange_rates`
>   **já** é `(currency, counterCurrency, date, source)`. O terceiro valor entra sem tocar
>   `core/database/schemas/`. Se uma tarefa se vir precisando subir a versão, ela está
>   errada.
> - **Nenhuma semente embarcada de taxas** (D6), **nenhum backfill de série temporal**
>   (Non-Goal), **nenhum desligamento de sincronização por par** (Non-Goal), **nenhum botão
>   de sincronizar sob demanda** (Non-Goal — e a spec agora proíbe expressamente que
>   exista), **nenhum consentimento/opt-in** (Non-Goal).
> - **Ktor não entra em `:core:model`.** Ali entra o *port* e mais nada; o cliente HTTP vive
>   em `feature/settings/impl`, e é a estrutura de módulos que sustenta a restrição, não a
>   disciplina (D11).
> - **`:core:ledger` não é tocado por nenhuma tarefa.** Ele não conhece taxa nem moeda base.
> - **`ConsolidateMoneyUseCase`, `MoneyByCurrency`, `DisplayAmount`,
>   `ObserveConsolidationChangesUseCase` e `HarvestExchangeRateUseCase` também não são
>   tocados.** A colheita continua exatamente como está (a spec exige que ela continue
>   acontecendo com a fonte remota ativa), e o redutor continua lendo pelas mesmas
>   assinaturas — é isso que faz "nenhuma leitura passa a esperar rede" ser verdade por
>   construção e não por promessa.
>
> **Dimensionamento apurado contra o código:**
> - `ExchangeRate.Source` e `ExchangeRateEntity.Source` são consumidos exaustivamente em
>   **dois** sítios de produção: `ExchangeRateMapper` (dois `when`, um por sentido) e
>   `ExchangeRatesScreen.SourceLabel` (dois `when`, ícone e rótulo). Todo o resto os usa
>   por valor (`Source.USER`, `Source.DERIVED`), o que **não** quebra ao acrescentar um
>   terceiro valor — daí a quebra do grupo 2 ser estreita, ao contrário da change anterior.
> - A precedência binária está escrita em **dois** lugares de `ExchangeRateDao`: o
>   `ORDER BY ... CASE source WHEN 'USER' THEN 0 ELSE 1 END` de `rateOfPairAsOf` e o
>   `x.source = 'USER' AND e.source <> 'USER'` do `NOT EXISTS` de `ratesAsOf`. Hoje elas
>   concordam por serem a mesma frase escrita duas vezes; com três níveis isso deixa de
>   bastar, e as duas passam a exprimir **o mesmo ranking**.
> - A dívida de prosa tem **seis** donos, e cada um é tocado por exatamente uma tarefa
>   abaixo: `ExchangeRate.Source` e `ExchangeRateEntity.Source` (2.1), `ExchangeRateDao`
>   (3.1), `IExchangeRateRepository` (2.2), `ExchangeRateRepository` — incluindo o `answer`
>   que rotula toda resposta implícita como `DERIVED` (4.1) — e `RateResolver`, cujo KDoc
>   descreve a resolução sem falar de origem (3.2).
> - `feature/settings/impl` tem hoje `commonMain`, `commonTest` e `jvmTest`. Os motores de
>   Ktor exigem `androidMain`, `jvmMain` e `iosMain` novos — o módulo é KMP com os três
>   alvos (`androidTarget`, `jvm`, `iosX64/iosArm64/iosSimulatorArm64`), pela convenção
>   `finsight.kmp.library`.
> - `BaseCurrencyReachTest` (`app/shared/src/jvmTest/`) fixa **por nome** a lista de
>   arquivos de produção autorizados a nomear `IBaseCurrencyRepository`. O
>   `SyncExchangeRatesUseCase` precisa da base — é contra ela que cada moeda é cotada — e
>   portanto **entra na lista**, na mesma tarefa que o cria.
>
> **Duas decisões de desenho que os artefatos não tomam, tomadas aqui e justificadas**,
> porque sem elas duas tarefas não teriam como ser escritas:
> - **O instante da última sincronização e o conjunto de moedas não cobertas chegam ao
>   domínio por um port**, `IRateSyncStateRepository`, declarado em `:core:model` e
>   implementado sobre `multiplatform-settings` em `feature/settings/impl` (1.4 e 4.3). Não
>   é escopo novo: D9 exige que o instante seja persistido e D7 exige que a moeda não
>   coberta seja dita; o port é a única forma de o `SyncExchangeRatesUseCase`, que mora em
>   `:core:model`, aplicar o limite diário e registrar as duas coisas — `:core:model` não
>   depende de `multiplatform-settings` e não deve passar a depender. É exatamente o mesmo
>   movimento que `GetAccountCurrenciesUseCase` já pratica (D11).
> - **A leitura "taxa em vigor de cada par" nasce como consulta do DAO e é exposta pelo
>   tipo concreto `ExchangeRateRepository`, não por `IExchangeRateRepository`** (3.1 e
>   4.1). A política de data e origem tem um dono só, na query — reduzir `observeAll()` na
>   ViewModel seria reimplementá-la, que a regra de derivação proíbe. E acrescentar membro
>   à interface quebraria os treze fakes que a implementam, por uma leitura que só a
>   feature de settings faz. O binding do tipo concreto entra em 5.1.

---

## 1. Preparações inertes

Barreira de entrada: nenhuma — as quatro tarefas partem da árvore como está.
Paralelo: 1.1, 1.2, 1.3 e 1.4, quatro subagentes, arquivos e módulos disjuntos.
Barreira de saída: o projeto compila e `./gradlew allTests` continua verde. **Nenhuma das
quatro altera comportamento**: as chaves de 1.1 não têm consumidor, as dependências de 1.2
não são importadas por nenhum `.kt`, e os dois ports de 1.3 e 1.4 não têm implementação nem
binding. É por isso que este grupo corre antes da quebra do grupo 2 em vez de depois dela.

- [x] 1.1 (paralelo) **A faixa inteira de chaves de string**, em
  `core/resources/src/commonMain/composeResources/values/strings.xml` **e**
  `core/resources/src/commonMain/composeResources/values-en/strings.xml`, na mesma tarefa e
  no mesmo passo: o rótulo da terceira origem (`exchange_rates_source_remote` — *"Cotação
  automática"* / *"Automatic quote"*), os dois estados da manutenção automática
  (*"Atualizado em %1$s"* e *"Ainda não atualizado"*), a moeda não coberta com o que fazer a
  respeito (*"%1$s não é coberta pela atualização automática — cadastre a taxa à mão"*), o
  título e o vazio da visão de histórico, e os rótulos dos três filtros (data, moeda,
  origem) com as suas opções. **Esta tarefa é a dona da faixa, e existe precisamente para
  que 2.1, 6.1 e 7.1 não disputem `strings.xml`** — é o único arquivo que elas teriam em
  comum. Os prefixos são disjuntos (`exchange_rates_*`, `exchange_rate_history_*`) e caem
  nas secções que o arquivo já organiza assim. Uma chave que faltar depois entra pelos dois
  arquivos na tarefa que a descobrir: a regra do projeto é que uma chave presente num só é
  bug, e ela não tem exceção.
- [x] 1.2 (paralelo) **Ktor entra no projeto**, em `gradle/libs.versions.toml` e em
  `feature/settings/impl/build.gradle.kts`, e em nenhum outro módulo. No catálogo: a versão
  (a estável mais recente compatível com Kotlin 2.3.10 e `kotlinx-serialization-json`
  1.8.0 — **confirmar na documentação corrente, não pela memória**) e os artefatos
  `ktor-client-core`, `ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json`, `ktor-client-okhttp` (Android e JVM),
  `ktor-client-darwin` (iOS) e `ktor-client-mock` (teste). No módulo: `ktor-client-core` +
  negociação + serialização em `commonMain`, o motor de cada plataforma nos
  `androidMain`/`jvmMain`/`iosMain` **novos** (o módulo só tem `commonMain`, `commonTest` e
  `jvmTest` hoje), `ktor-client-mock` em `commonTest`, e o plugin
  `alias(libs.plugins.kotlinSerialization)`, que o módulo ainda não aplica e de que os DTOs
  de 4.2 precisarão. Verificação: `./gradlew :app:android:assembleDebug` e
  `./gradlew :app:desktop:run` compilam, e **nenhum outro `build.gradle.kts` menciona
  Ktor** — a restrição de D11 é estrutura de módulo, e é aqui que ela se paga.
- [x] 1.3 (paralelo) **O port da fonte remota**, arquivo novo em
  `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IRemoteRateSource.kt`,
  sem cliente HTTP algum e sem nomear provedor: `suspend fun quote(currency: String,
  against: String): RemoteQuote`, com `RemoteQuote` sendo o resultado de três formas —
  **observada** (a data que a fonte declara e o quociente em precisão plena), **não
  coberta** (a fonte recusou o código explicitamente) e **indisponível** (falha de
  transporte). As três formas são o próprio D7 no tipo: são dois estados diferentes com
  ações diferentes do usuário — esperar, ou cadastrar à mão —, e só a distinção entre eles é
  acionável; colapsá-las num `null` devolveria o usuário ao pior caso sem lhe dizer por quê.
  O KDoc registra que este port é um **escritor** do acervo e nunca um caminho de leitura
  (D1), e que a direção pedida — `currency` cotada em `against` — é parte da forma de
  perguntar e não algo a corrigir na gravação (D4). Sem implementação e sem binding.
- [x] 1.4 (paralelo) **O port do estado da sincronização**, arquivo novo em
  `core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IRateSyncStateRepository.kt`:
  um `observe(): StateFlow<RateSyncState>` e um `suspend fun record(state: RateSyncState)`,
  com `RateSyncState` carregando **o instante da última sincronização bem-sucedida** (nulo
  antes da primeira) e **o conjunto de moedas que a fonte declarou não cobrir**. O KDoc diz
  por que é isto e não um canal de erro (D9): persistir o sucesso sobrevive a reinício do
  app, ao passo que um estado de erro em memória não sobreviveria; falhou, nada é escrito, e
  a tela deduz do instante antigo — não há evento, não há estado transitório a coordenar.
  Registra também que este estado tem **uma única superfície**, a tela de taxas, e que
  nenhuma figura consolidada o exibe. Sem implementação e sem binding.

---

## 2. A terceira origem no dado

Barreira de entrada: 1.1 (o `when` da tela precisa da chave do rótulo `REMOTE`; sem ela a
tarefa 2.1 inventaria uma chave num arquivo que 1.1 possui).
Paralelo: 2.1 e 2.2, dois subagentes, arquivos disjuntos.
Barreira de saída: o projeto compila e `./gradlew allTests` verde. **Nenhuma linha `REMOTE`
existe** — nada a grava ainda —, então o valor novo é inerte e a precedência continua
respondendo exatamente o que respondia.

- [x] 2.1 (paralelo) **`REMOTE` nos dois enums, e os quatro `when` que ele quebra.** É um
  corte só porque a quebra é de compilação: acrescentar o valor torna não-exaustivos os
  `when` que já existem, e fechá-los noutra tarefa deixaria a árvore sem compilar entre as
  duas.
  - **`core/model/.../domain/model/ExchangeRate.kt`** — `Source` ganha `REMOTE`, entre
    `DERIVED` e `USER` ou depois, tanto faz (a ordem de declaração não é a precedência, e o
    KDoc diz isso). O KDoc do enum deixa de descrever uma ordem binária e passa a declarar
    `USER` ▸ `REMOTE` ▸ `DERIVED`, com a razão de D2 dita por inteiro: a `DERIVED` é o
    quociente de uma operação real e **contém o que a operação cobrou** — *spread*, IOF,
    tarifa —, então ela responde *quanto custou*; a `REMOTE` responde *quanto valia*, e
    consolidar é **avaliar** um patrimônio, não reconstituir um custo. E a frase que impede
    a leitura errada: a precedência desempata **apenas dentro da mesma data**, nunca sobre
    ela.
  - **`core/database/.../entity/ExchangeRateEntity.kt`** — o mesmo valor e a mesma prosa no
    `Source` da entidade. O comentário do índice único ganha a frase que agora vale para
    três: é por as três origens poderem coexistir no mesmo `(currency, counterCurrency,
    date)` que a precedência significa alguma coisa, e é por isso que a chave inclui
    `source` **desde antes desta change** — nenhuma migração, nenhuma versão nova de
    `AppDatabase`, nenhum schema exportado alterado.
  - **`feature/settings/impl/.../database/mapper/ExchangeRateMapper.kt`** — o terceiro ramo
    nos dois `when`, um por sentido.
  - **`feature/settings/impl/.../ui/screen/exchangeRates/ExchangeRatesScreen.kt`** — os dois
    `when` de `SourceLabel` (ícone e rótulo) ganham o ramo `REMOTE`, com a chave que 1.1
    declarou e um ícone que o distinga dos outros dois sem cor (a tela já trata a origem por
    ícone + palavra, nunca por cor, e isso não muda). **Nenhuma outra linha deste arquivo é
    tocada aqui** — a divisão em duas telas é 6.1 e 7.1.

  Verificação: compila nas três plataformas; `./gradlew allTests` verde sem que nenhum teste
  mude de asserção — se um passar a afirmar coisa diferente, o corte está errado, porque
  todo o resto do código usa o enum por valor e não exaustivamente.
- [x] 2.2 (paralelo) **A política que `IExchangeRateRepository` promete deixa de ser
  binária**, em `core/model/.../domain/repository/IExchangeRateRepository.kt`, e **só a
  prosa muda**: nenhuma assinatura entra, sai ou se altera — é isso que mantém os treze
  fakes de teste e os sete consumidores de produção intactos. O KDoc da interface troca
  *"the user's winning over a derived one of the same date"* pelo ranking de três, e troca a
  frase que confina a rede ao formulário — *"An external source may fill the field in as a
  suggestion, inside the screen that edits a rate and nowhere else"* — pelo que a change
  estabelece: uma fonte remota **escreve** neste acervo, como qualquer outro escritor, e
  continua não sendo consultada por nenhuma leitura; a sugestão dentro do formulário
  permanece permitida e continua valendo só se o usuário a confirmar. A garantia que a
  frase antiga protegia é dita como ela realmente é — **nenhuma leitura espera rede** —, e
  registra-se que ela passa a ser sustentada pela **direção do fluxo** em vez de por uma
  proibição.

---

## 3. A precedência de três níveis, ainda sem consumidor

Barreira de entrada: 2.1 (o valor `REMOTE` tem de existir nos dois enums antes do ranking
que o nomeia — um `CASE source WHEN 'REMOTE'` sobre um enum que não tem esse valor é uma
query que nunca casa, e um teste que a exercitasse não teria como construir a linha).
Paralelo: 3.1 e 3.2, dois subagentes, arquivos e módulos disjuntos.

**O que torna as duas independentes é a mesma decisão de desenho que a change anterior
tomou, e ela continua valendo:** o `RateResolver` é definido sobre `List<ExchangeRate>` —
domínio puro —, e não sobre o que o DAO devolve. A política de data e origem tem um dono, na
query; a política de caminhos tem outro, no resolvedor; a costura entre os dois é 4.1 e só
ela.

Barreira de saída: compila e `allTests` verde. **Ambas são aditivas**: a consulta nova de
3.1 não tem chamador e a função nova de 3.2 não tem consumidor — a antiga continua no lugar
até 4.1 —, e a mudança de ranking, embora seja mudança de comportamento, é observavelmente
inerte porque nenhuma linha `REMOTE` existe.

- [x] 3.1 (paralelo) **`core/database/.../dao/ExchangeRateDao.kt`: o ranking de três, nos
  dois lugares, e a consulta da taxa em vigor.**
  - `rateOfPairAsOf` — o `CASE source WHEN 'USER' THEN 0 ELSE 1 END` vira um ranking
    explícito de três níveis (`USER` 0, `REMOTE` 1, `DERIVED` 2), **depois** do
    `ORDER BY date DESC` que já existe e continua precedendo o desempate. É esta ordem, e
    não outra, que realiza D3: a data vence a origem, sempre; a origem desempata apenas
    entre observações da mesma data.
  - `ratesAsOf` — o `NOT EXISTS` troca `x.source = 'USER' AND e.source <> 'USER'` pela
    **comparação do mesmo ranking** (`x` bate `e` quando é mais recente, ou quando é da
    mesma data e tem ranking estritamente menor). As duas expressões passam a ser o mesmo
    ranking escrito de duas formas obrigatoriamente concordantes — hoje elas se sustentam
    por serem a mesma frase escrita duas vezes, e com três níveis isso deixa de bastar.
  - Uma consulta nova, `Flow`, que devolve **uma linha por par** já resolvida pela política,
    em ou antes de uma data — a matéria-prima da visão *em vigor* de 7.1. É o mesmo
    predicado de `ratesAsOf`, e é por ser o mesmo que a tela não reimplementa política
    alguma: reduzir `observeAll()` na ViewModel seria dar um segundo dono a uma regra
    derivada, que a regra de derivação proíbe. **Sem chamador nesta tarefa.**
  - O KDoc da política, nos dois métodos, deixa de dizer *"the user's own winning over a
    derived one"* e passa a declarar as três origens em ordem, com a razão de a remota
    vencer a colhida (avaliar, não reconstituir um custo) e com a frase que impede a
    fixação: a precedência **não** prevalece sobre a data.

  Verificação: em `core/database/src/jvmTest/.../ExchangeRateDaoTest.kt`, sobre banco real —
  a `USER` vence as duas no mesmo dia; a `REMOTE` vence a `DERIVED` no mesmo dia; **a data
  vence a origem** (uma `REMOTE` de hoje vence uma `USER` de ontem, e a figura de ontem
  continua respondendo pela `USER`); os dois métodos concordam sobre o mesmo acervo, par a
  par; e a consulta em vigor devolve uma linha por par e não uma por moeda.
- [x] 3.2 (paralelo) **`feature/settings/impl/.../database/repository/RateResolver.kt`: a
  resposta implícita passa a saber que origem ela tem** (D10). Hoje o resolvedor devolve
  `Double?` e o repositório rotula tudo `DERIVED`, o que era legítimo enquanto o campo
  significava *"não é do usuário"* e deixa de ser com três origens. Acrescentar — **sem
  remover a função atual, que 4.1 é quem substitui** — uma resolução que devolve o
  quociente **junto da origem**: a da observação lida quando há uma só (a inversa é a
  **mesma** observação lida ao contrário, então conserva a origem dela) e a **mais fraca**
  das duas numa triangulação, o que é bem definido precisamente porque D2 declarou uma ordem
  total. A precedência de caminhos não muda em nada — direta ▸ inversa ▸ **um** pivô, nunca
  dois, com o mesmo desempate determinístico (pernas mais recentes, empate pelo código ISO
  crescente) — e `null` continua não podendo virar `1`. O KDoc ganha o parágrafo da origem,
  incluindo o motivo de ela não ser gravável: a resposta implícita continua sem `id` e
  continua não podendo voltar ao acervo. Testes em
  `feature/settings/impl/src/commonTest/.../RateResolverTest.kt`: a inversa conserva a
  origem da observação (inclusive quando ela é `REMOTE`), a triangulação declara a mais
  fraca das duas pernas, e os cenários de caminho existentes continuam idênticos.

---

## 4. Os escritores, e a costura da leitura

Barreira de entrada: 3.1 e 3.2 (4.1 consome as duas), 1.2 (4.2 precisa de Ktor no módulo) e
1.3/1.4 (4.2 e 4.3 implementam os dois ports; 4.4 os consome).
Paralelo: 4.1, 4.2, 4.3 e 4.4, quatro subagentes, arquivos disjuntos.
Barreira de saída: compila e `allTests` verde — **e o app continua não sincronizando**.
Nada deste grupo tem binding: o cliente Ktor não é construído por ninguém, o estado de
sincronização não é resolvido por ninguém, e o use case não tem `factory`. É o grupo 5 que
liga a chave, e é essa separação que faz cada uma destas quatro poder ser escrita ao mesmo
tempo.

- [x] 4.1 (paralelo) **`feature/settings/impl/.../database/repository/ExchangeRateRepository.kt`
  passa a dizer a verdade sobre a origem, e a expor a taxa em vigor.** Trocar `answer` — que
  rotula toda resposta implícita como `ExchangeRate.Source.DERIVED` — pela origem que 3.2
  agora devolve, e passar `rateAsOf`, `ratesAsOf` e `rateBetween` a usar a resolução nova,
  removendo a antiga do `RateResolver` no mesmo passo (é este o único consumidor dela). O
  KDoc do `answer` perde *"The origin is DERIVED for the same reason — nobody typed it"* e
  passa a dizer o que passou a valer: `id = 0` continua sendo o que impede a resposta de
  voltar ao acervo, e a origem passa a ser a das observações que a produziram. Acrescentar
  `observeInForce()` sobre a consulta nova de 3.1 — **membro do tipo concreto, não de
  `IExchangeRateRepository`**, com o KDoc dizendo por quê: é uma leitura que só a tela de
  taxas faz, e pô-la na interface obrigaria os treze fakes que a implementam a responder uma
  pergunta que os seus módulos não fazem. **As assinaturas da interface não mudam**, e é por
  isso que `ConsolidateMoneyUseCase`, as ViewModels e toda tela que exibe figura não são
  tocadas por nenhuma linha desta change. Verificação em
  `feature/settings/impl/src/jvmTest/.../ExchangeRateRepositoryResolutionTest.kt`, sobre
  banco real: a resposta pela inversa de uma linha `REMOTE` declara `REMOTE`; a triangulação
  entre uma perna `USER` e uma `REMOTE` declara `REMOTE`, e entre `REMOTE` e `DERIVED`
  declara `DERIVED`; e a resposta implícita continua sem `id`.
- [x] 4.2 (paralelo) **A fonte remota sobre Ktor**, arquivos novos em
  `feature/settings/impl/.../network/` (o cliente e os DTOs da resposta), implementando
  `IRemoteRateSource` sobre **Frankfurter** — gratuito, sem chave, sem SLA. Uma requisição
  **por moeda em uso**, com `base=<moeda>&symbols=<base>`, e a razão está no KDoc porque ela
  é contra-intuitiva (D4): a chamada barata seria uma só, `base=<base>&symbols=<moedas>`,
  mas ela devolve *"1 real vale 0,18 dólar"* e gravaria linhas `(base, moeda)` — o acervo
  deixaria de ser *"tudo precificado na base"* e viraria *"a base precificada em tudo"*,
  invertendo o agrupamento da tela e fazendo cada linha ler ao contrário do que o usuário
  pergunta. Corrigir isso na gravação seria inverter o quociente, que a spec proíbe
  frontalmente. O mapeamento devolve **as três formas** de `RemoteQuote` de 1.3: observada
  (com a **data que a resposta declara**, nunca a de hoje — D5), não coberta (código
  recusado explicitamente pela fonte) e indisponível (qualquer falha de transporte ou
  resposta ilegível). Nenhuma exceção escapa deste arquivo: indisponibilidade é um valor de
  retorno, porque falhar aqui tem de significar *não fazer nada*. Testes em `commonTest` com
  `MockEngine`: uma resposta de sexta lida num domingo produz a data de sexta; um código
  recusado produz *não coberta*, e um 5xx ou um corpo ilegível produzem *indisponível*; e a
  URL montada pede a direção certa.
- [x] 4.3 (paralelo) **O estado da sincronização, persistido**, arquivo novo em
  `feature/settings/impl/.../database/repository/RateSyncStateRepository.kt`, implementando
  `IRateSyncStateRepository` sobre `multiplatform-settings`, que este módulo já usa (é o
  mesmo mecanismo de `BaseCurrencyRepository`, e as chaves são disjuntas das dele). Guarda o
  instante da última sincronização **bem-sucedida** e o conjunto de moedas declaradas não
  cobertas, e expõe os dois por `StateFlow`, de modo que a tela de 7.1 os observe sem
  precisar de evento. Nada mais: não há canal de erro e não há estado transitório (D9).
  Teste em `commonTest` com o `Settings` de teste que o módulo já usa: gravar e reabrir
  devolve o que foi gravado, e um repositório recém-criado antes da primeira sincronização
  responde *nunca sincronizou* em vez de uma data qualquer.
- [x] 4.4 (paralelo) **`SyncExchangeRatesUseCase`**, arquivo novo em
  `core/model/.../domain/usecase/`, concreto e ao lado do `HarvestExchangeRateUseCase` que
  já mora ali (D11). Ele compõe, e não decide nada que já tenha dono: pede a
  `GetAccountCurrenciesUseCase.inUse` o conjunto de moedas em uso — contas **e** cartões —,
  descarta a base em vigor, pergunta a cada uma delas à `IRemoteRateSource` **na direção em
  que a linha será lida**, e grava cada observação pelo `IExchangeRateRepository.save` que
  todo escritor usa, com `Source.REMOTE` e **com a data que a fonte declarou**. Ao fim,
  registra em `IRateSyncStateRepository` o instante e as moedas recusadas. As regras que ele
  aplica, cada uma com o seu porquê no KDoc:
  - **Limite de uma vez por dia**, contra o instante persistido — a cadência é a abertura do
    app, disparada e esquecida, sem trabalho em segundo plano, sem `WorkManager`, sem
    permissão nova e sem nada que o iOS não faça (D8).
  - **Falhar é não escrever nada.** Indisponibilidade não grava linha, não carimba o
    instante e não lança para o chamador: quem o dispara não o aguarda, e uma exceção que
    subisse pelo `LaunchedEffect` seria a única forma de a rede alcançar uma tela.
  - **Idempotência sai de graça de D5**, e o KDoc diz por quê: como a linha carrega a data
    da publicação, rodar duas vezes no mesmo dia — ou no domingo depois de ter rodado no
    sábado — reescreve a mesma `(par, data, REMOTE)` pelo `REPLACE` que a chave única já
    garante. Nada duplica, e nada precisa saber que já rodou.
  - **Uma moeda não coberta não é uma falha** e não impede as outras: ela é registrada como
    tal, porque é o que torna a distinção acionável para o usuário (D7).

  Acrescentar o arquivo à lista de `allowed` de
  `app/shared/src/jvmTest/.../BaseCurrencyReachTest.kt`, com o comentário dizendo por que é
  legítimo: ele não denomina figura alguma — o que ele faz com a base é **perguntar contra o
  quê cotar**, que é a mesma natureza da pré-seleção que já autoriza os formulários de conta
  e de cartão. Testes em `core/model/src/commonTest/`, com fakes dos três ports: a data
  gravada é a da fonte e não a de hoje; a direção gravada é `(moeda em uso, base)`; rodar
  duas vezes no mesmo dia faz uma requisição só; uma falha não grava e não carimba o
  instante; uma moeda recusada é registrada como não coberta e as demais são gravadas assim
  mesmo; e a base **não** é cotada contra si mesma.

---

## 5. A sincronização passa a acontecer

Barreira de entrada: o grupo 4 inteiro.

**Tarefa única, e a indivisibilidade é real e não de gosto.** Um binding sem gatilho e um
gatilho sem binding são as duas metades do mesmo passo, e a segunda falha em tempo de
execução: `App` resolvendo por Koin um tipo que ninguém bindou não quebra a compilação,
quebra o app do usuário. O teste que prova o passo — `AppModulesTest`, se ele verificar
resolubilidade por tipo — é um só, e ele não teria como passar sobre metade do trabalho.

Barreira de saída: `allTests` verde e **o app sincroniza na abertura**, uma vez por dia,
sem que nada na composição aguarde. A partir daqui existem linhas `REMOTE` no acervo — e é
por isso que este grupo vem **depois** do ranking do grupo 3 e da costura do grupo 4, e não
antes: um produtor não pode preceder o leitor.

- [x] 5.1 (barreira; depende do grupo 4 inteiro) **O binding e o gatilho, num passo:**
  - **`core/model/.../di/ModelModule.kt`** — `factory { SyncExchangeRatesUseCase(...) }`, ao
    lado do `HarvestExchangeRateUseCase`, que é onde a camada de consolidação já declara os
    seus.
  - **`feature/settings/impl/.../di/SettingsModule.kt`** — o `HttpClient` (construído em
    `commonMain`, com o motor vindo do classpath de cada plataforma), o binding de
    `IRemoteRateSource` sobre a implementação de 4.2, o de `IRateSyncStateRepository` sobre
    a de 4.3, e o binding do **tipo concreto** `ExchangeRateRepository` ao lado do
    `single<IExchangeRateRepository>` que já existe, resolvido a partir dele, para que as
    ViewModels dos grupos 6 e 7 alcancem `observeInForce()` sem que a interface o carregue.
  - **`app/shared/src/commonMain/.../ui/App.kt`** — o `LaunchedEffect(Unit)` que já faz
    trabalho transversal disparado-e-esquecido (o *user-id* em analytics e crashlytics)
    passa a disparar também a sincronização, **sem aguardá-la** e sem que nada na composição
    dependa dela (D8). Registrar no comentário o que isto é: o primeiro passo de
    inicialização real do app, que nasce inofensivo por construção — nada o aguarda, e
    falhar é não fazer nada. **Não** é o `DashboardViewModel`: taxa não é assunto do
    dashboard, e amarrá-la a uma aba faria a sincronização depender de qual tela o usuário
    abriu.
  - **`app/shared/src/jvmTest/.../AppModulesTest.kt`** — se ele verificar resolubilidade por
    tipo, os bindings novos entram na lista; se não, o arquivo não é tocado.

  Verificação: `./gradlew allTests` verde, `:app:android:assembleDebug` e `:app:desktop:run`
  compilam, e **nenhuma tela ganhou estado de carregamento** — a composição não observa nada
  da sincronização.

---

## 6. O histórico, e os dois gates

Barreira de entrada: 5.1 (a tela de histórico convive com linhas `REMOTE` reais, e os dois
gates afirmam sobre a fiação que 5.1 acabou de fazer).
Paralelo: 6.1, 6.2 e 6.3, três subagentes, arquivos disjuntos — nenhuma delas acrescenta
chave de string, porque 1.1 já as declarou nos dois arquivos.

**O histórico vem antes da visão em vigor, e a razão é a navegação:** a visão em vigor
alcança o histórico de um par, então quem navega precisa de uma rota que já exista. Inverter
a ordem faria 7.1 nomear a saída de uma irmã.

Barreira de saída: `allTests` verde; o histórico existe, é filtrável por data, moeda e
origem, e os dois gates provam por inspeção e por banco real o que a spec exige.

- [x] 6.1 (paralelo) **A visão de histórico**, arquivos novos em
  `feature/settings/impl/.../ui/screen/exchangeRateHistory/` (UiState, ViewModel, Screen),
  mais a rota e o registro no grafo em
  `feature/settings/impl/.../ui/navigation/SettingsGraph.kt`. A rota é **interna ao
  `impl`** — nenhuma outra feature navega para o histórico, e a `api` declara apenas o que é
  externamente navegável —, e carrega opcionalmente o par pelo qual chega pré-filtrada,
  porque é assim que 7.1 a alcançará. O conteúdo é o que a listagem atual já sabe fazer, e
  ela **muda de dono, não de forma**: as observações agrupadas pela **moeda contraparte** —
  a ponta que de fato reúne, porque no acervo ordinário toda linha é precificada na base, e
  a manutenção automática a torna ainda mais ordinária ao gravar exatamente nessa direção —,
  cada linha descrevendo-se por inteiro (par nas duas pontas, valor, data, origem), nenhuma
  exibida invertida em relação à observação que a originou, grupos ordenados pela observação
  mais recente de cada moeda, e a regra dos 30 dias intacta. O que é novo são os **três
  filtros** — data (intervalo), moeda (nomeada em qualquer das duas pontas) e origem (as
  três, distinguidas) —, e o KDoc registra por que eles não são enfeite: a manutenção
  automática torna o acervo denso, e sem filtros a remoção — que existe como corolário de a
  taxa sobreviver à operação que a originou — deixaria de ser alcançável na prática. A
  edição e a remoção continuam pelo `ExchangeRateFormModal`, que **não muda**. Testes de
  ViewModel: cada filtro isoladamente, os três compostos, o agrupamento e a ordem dos
  grupos, e o mesmo par nos dois sentidos aparecendo em dois grupos.
- [x] 6.2 (paralelo) **O gate estrutural: a fonte remota é escritor e não leitor**, arquivo
  novo em `app/shared/src/jvmTest/`, no molde de inspeção de `BaseCurrencyReachTest` e de
  `RateIsNeverWrittenTest`. Ele fixa por nome os únicos arquivos de produção autorizados a
  nomear `IRemoteRateSource` — a declaração, a implementação Ktor, o
  `SyncExchangeRatesUseCase` e o `SettingsModule` que o binda — e falha quando um quinto
  aparece. É a garantia de D1 escrita como propriedade do que existe, e não como promessa:
  nenhum caminho de leitura, nenhuma ViewModel de figura e nenhum redutor pode alcançar a
  rede, e a lista é o que torna essa frase verificável. Acrescentar, no mesmo arquivo, a
  afirmação recíproca sobre Ktor: **nenhum `build.gradle.kts` fora de
  `feature/settings/impl` declara dependência de Ktor**, que é a metade estrutural de D11 —
  um `:core:network` foi descartado precisamente para que a restrição fosse módulo em vez de
  disciplina.
- [x] 6.3 (paralelo) **O gate de ponta a ponta da sincronização**, arquivo novo em
  `app/shared/src/jvmTest/`, no molde de `CrossCurrencyEndToEndTest`: banco real, contas em
  três moedas, uma `IRemoteRateSource` de teste, e os cenários da spec que só um teste de
  ponta a ponta alcança — o usuário multimoeda passa a ter taxa **sem cadastrar nada**, e a
  figura consolidada soma em vez de empilhar termos; a linha gravada é a da moeda em uso
  precificada na base, e nenhum quociente foi invertido para gravá-la; a data é a que a
  fonte declarou; rodar duas vezes sobre a mesma publicação deixa **uma** observação e não
  duas; o cruzamento entre duas não-base é resolvido por triangulação sobre a base **sem que
  nenhuma observação desse par tenha sido buscada**; uma falha de transporte não cria nem
  altera observação alguma; e a colheita de operação continua acontecendo no mesmo par,
  permanecendo no acervo para quando a remota não alcançar aquela data. Acrescentar o caso
  que fecha D6 pelo lado declarado: **primeira execução sem rede** não tem taxa nenhuma, a
  figura exibe um termo por moeda, e nenhum erro é apresentado — é o limite da garantia, e
  ele é afirmado em vez de ficar implícito.

---

## 7. A visão em vigor, que passa a ser a entrada

Barreira de entrada: 6.1 (a rota do histórico tem de existir para ser alcançada) e 4.3/5.1
(o instante da última sincronização tem de estar sendo escrito antes de a tela o ler — a
escrita precede a leitura, e é por isso que esta tela é a última coisa da change).

**Tarefa única, e os quatro arquivos que ela toca formam um corte só:** o UiState muda de
forma, o ViewModel muda de fonte, e as duas superfícies que os renderizam — a tela cheia e o
painel adaptativo — compartilham o mesmo `ExchangeRatesContent`. Uma segunda tarefa que
tocasse qualquer um deles não seria irmã independente; seria um conflito.

Barreira de saída, e é a final da change: `./gradlew allTests` verde; a entrada do acervo é
a taxa em vigor de cada par; o estado da sincronização aparece **ali e em nenhum outro
lugar**; e nenhuma figura consolidada exibe carregamento, erro ou qualquer sinal de
sincronização.

- [x] 7.1 (barreira; depende de 6.1) **A tela de taxas passa a apresentar a taxa em vigor**,
  em `ExchangeRatesUiState.kt`, `ExchangeRatesViewModel.kt`, `ExchangeRatesScreen.kt` e
  `ExchangeRatesDetail.kt`:
  - O UiState deixa de carregar grupos de observações e passa a carregar **uma linha por
    par**, vinda do `observeInForce()` de 4.1 — a observação que hoje responde por aquele
    par segundo a política do acervo, e não uma redução feita aqui. Cada linha declara o par
    nas duas pontas, o valor, a data e a **origem** da observação que responde, e a regra
    dos 30 dias continua valendo sobre ela, com o significante textual que já existe.
  - O UiState ganha o **estado da manutenção automática**: quando o acervo foi atualizado
    com sucesso pela última vez (ou que ainda não foi), do `IRateSyncStateRepository`; e,
    **por moeda em uso** (de `GetAccountCurrenciesUseCase.inUse`), se ela não é coberta pela
    fonte. Os dois estados são apresentados como dois, porque as ações que eles levam são
    diferentes — esperar, ou cadastrar à mão — e só a distinção entre eles é acionável (D7).
    O sinal de desatualizada **convive** com o de sincronização e não o substitui: sem saber
    se o app conseguiu atualizar, o usuário não tem como distinguir uma taxa velha que ele
    não cadastrou de uma que o app não conseguiu buscar (D9).
  - A tela passa a alcançar o **histórico** — o inteiro, e o daquela moeda ao tocar numa linha,
    pela rota que 6.1 declarou. O `+` que cadastra uma taxa continua onde está, e o
    `ExchangeRateFormModal` continua o mesmo.
  - `ExchangeRatesDetail` acompanha pelo `ExchangeRatesContent` compartilhado, que continua
    sendo o único lugar onde *o que um acervo é* está escrito; o painel adaptativo segue
    funcionando como hoje.
  - **Nada disto vaza para figura alguma.** O KDoc registra a fronteira exatamente como a
    spec a desenha: a proibição de estado de carregamento é sobre **figura consolidada** —
    um saldo não pode ter *spinner* nem falhar —, e esta tela não é figura: é o acervo se
    explicando. Nenhuma outra superfície do app exibe estado de sincronização.

  Testes de ViewModel: uma linha por par sobre um acervo com trinta observações do mesmo
  par; a linha declara par, valor, data e origem; o instante da última sincronização é
  apresentado, e a sua ausência é apresentada como *ainda não atualizado* e não como uma
  data qualquer; e uma moeda em uso recusada pela fonte aparece como não coberta, distinta
  de uma que apenas não tem taxa ainda.

---

## 8. Conferência final

Barreira de entrada: 7.1. Barreira de saída: `./gradlew allTests` verde e nada abaixo
encontra arquivo — se encontrar, a correção é na tarefa que deixou passar, não aqui.

- [x] 8.1 Passada de conferência (primeira volta; a segunda é 9.4), **verificação e não edição**: nenhuma chave de string
  existe em apenas um dos dois `strings.xml`; nenhuma das seis prosas que afirmavam origem
  binária ou rede confinada ao formulário sobrevive (`ExchangeRate.Source`,
  `ExchangeRateEntity.Source`, `ExchangeRateDao` nos dois métodos, `IExchangeRateRepository`,
  `ExchangeRateRepository.answer`, `RateResolver`); `core/database/schemas/` está **byte a
  byte** como antes da change e `AppDatabase` continua na mesma versão; nenhum
  `build.gradle.kts` fora de `feature/settings/impl` menciona Ktor; e nenhuma tela com
  figura consolidada ganhou estado de carregamento, erro ou menção a sincronização.

---

## 9. A taxa passa a preceder a conta

Descoberto em teste manual depois do grupo 8: sincronizar apenas as moedas **em uso** faz
a taxa seguir a conta em vez de precedê-la, e o limite diário **global** prende a moeda
recém-cadastrada até o dia seguinte. São as duas metades do mesmo defeito, e o conserto de
uma sem a outra não conserta nada — cobrir o oferecido sem o limite por moeda deixa a moeda
nova bloqueada; o limite por moeda sem cobrir o oferecido deixa a conta nova esperando o
gatilho seguinte. Ver D8b e D8c.

Barreira de entrada: 8.1. Barreira de saída: `./gradlew jvmTest` verde, `assembleDebug`
compila, e uma conta criada numa moeda oferecida encontra a taxa pronta.

- [x] 9.1 **O estado da sincronização passa a ser por moeda**, em
  `core/model/.../domain/repository/IRateSyncStateRepository.kt` e em
  `feature/settings/impl/.../database/repository/RateSyncStateRepository.kt`. `RateSyncState`
  troca o instante único por um instante **por moeda**, e o que a tela mostra passa a ser
  **derivado** dele — o mais recente — em vez de um campo próprio, porque dois campos seriam
  dois donos da mesma frase e divergiriam na primeira sincronização parcial. O KDoc registra
  por que o limite não pode ser global (D8b). Teste em `commonTest`: gravar e reabrir devolve
  o mapa; o instante apresentado é o mais recente; e um repositório novo continua respondendo
  *nunca sincronizou*.
- [x] 9.2 **O conjunto passa a ser o oferecido ∪ o em uso, e o limite a ser por moeda**, em
  `core/model/.../domain/usecase/SyncExchangeRatesUseCase.kt`. Ele passa a consultar também
  `ICurrencyRepository` — declarada no mesmo módulo, sem dependência nova — e a pular por
  moeda, não por rodada. Uma moeda recusada **carimba o instante assim mesmo**: a resposta
  foi definitiva, e não carimbar a faria ser perguntada em toda abertura para sempre. Uma
  indisponível não carimba, para tentar de novo. Testes em `core/model/src/commonTest/`: uma
  moeda oferecida e sem conta é coberta; uma arquivada com conta viva é coberta; uma arquivada
  sem conta não é; a que já respondeu hoje não é perguntada de novo; e a nunca perguntada é,
  mesmo com as outras já sincronizadas hoje.
- [x] 9.3 **O gatilho passa a incluir o registro ganhando uma moeda**, em
  `app/shared/src/commonMain/.../ui/App.kt`: o trabalho transversal disparado-e-esquecido
  passa a **observar** as moedas oferecidas e a redisparar a sincronização quando o conjunto
  muda. Com o limite por moeda de 9.2 o redisparo é inócuo — tudo que já respondeu hoje é
  pulado —, e é isso que o torna seguro. Registrar no comentário que isto **não** é o comando
  de sincronizar que os Non-Goals recusam (D8c): é mudança de estado do app, ninguém a
  aguarda, e nada na composição depende dela.
- [x] 9.4 **Segunda passada de conferência**, verificação e não edição: nenhuma tela ganhou
  estado de carregamento ou botão de sincronizar; `RemoteSourceIsNeverReadTest` continua
  fixando quatro arquivos; o gate de ponta a ponta cobre o caso relatado — sincronizou hoje,
  cria conta agora, a taxa já está lá.

---

## 10. Trocar a base deixa de deixar o acervo um dia atrás

Descoberto em teste manual depois do grupo 9, e é D8b uma volta acima: **o limite guardava
menos informação do que a pergunta que ele governa.** Ele era por moeda; o que se busca é
um par. Trocada a base, toda moeda parecia respondida enquanto a linha que passou a
responder — a que é contra a base nova — nunca fora buscada. E, como no grupo 9, o limite
certo não basta sem o gatilho: nada observava a base. Ver D8d.

Barreira de entrada: 9.4. Barreira de saída: `./gradlew jvmTest` verde, `assembleDebug`
compila, e trocar a base busca os pares novos no mesmo dia.

- [x] 10.1 **O limite passa a ser por par**, em
  `core/model/.../domain/repository/IRateSyncStateRepository.kt`,
  `feature/settings/impl/.../database/repository/RateSyncStateRepository.kt` e
  `core/model/.../domain/usecase/SyncExchangeRatesUseCase.kt`. Entra `RatePair(currency,
  against)` e `syncedAt` passa a ser chaveado por ele; a persistência vira `FROM>TO=millis`.
  O KDoc diz por que a chave é o par e não a moeda. Testes: gravar e reabrir devolve o mapa;
  trocar a base busca na direção nova no mesmo dia; trocar a base e voltar não busca nada.
- [x] 10.2 **A regra de quando a manutenção vence passa a morar no use case**, em
  `SyncExchangeRatesUseCase.whenDue()` — abertura, registro ganhando moeda, base mudando —,
  e `App` passa a apenas coletá-la. É isso que impede a shell de nomear
  `IBaseCurrencyRepository` e de virar a primeira tela do app a fazê-lo, que é o que
  `BaseCurrencyReachTest` existe para barrar. Renomear uma moeda não deve a nada. Testes de
  `whenDue` sobre os três gatilhos e sobre o não-gatilho.
- [x] 10.3 **Terceira passada de conferência**: `BaseCurrencyReachTest` continua com a mesma
  lista; `RemoteSourceIsNeverReadTest` continua fixando quatro arquivos; nenhuma tela ganhou
  estado de carregamento ou comando de sincronizar.

---

## 11. A entrada volta a agrupar, e o estado da manutenção sai do lugar de cabeçalho

Descoberto em teste manual depois do grupo 10, e são **dois** erros somados que produzem um
sintoma só. A tarefa 7.1 substituiu a listagem agrupada por uma lista plana de uma linha por
par — confundindo *quantas linhas existem* com *como elas são encabeçadas* — e pôs o estado
da manutenção exatamente no lugar visual que o cabeçalho de grupo ocupava, com a mesma cor,
o mesmo recuo e tipografia quase igual. Como esse estado carrega uma data, a tela passou a
afirmar que as taxas estavam agrupadas por dia. Nenhum teste pegou porque nenhum afirmava
nada sobre agrupamento na visão em vigor — a 7.1 removeu essa asserção junto com o
comportamento.

Barreira de saída: `./gradlew jvmTest` verde e a entrada do acervo lê como lia antes —
agrupada pela moeda contraparte —, com o estado da manutenção distinguível de um cabeçalho.

- [x] 11.1 **O agrupamento volta à visão em vigor**, em `ExchangeRatesUiState.kt`,
  `ExchangeRatesViewModel.kt` e `ExchangeRatesScreen.kt`: entra
  `ExchangeRateInForceGroup`, com a mesma chave e a mesma ordem que o histórico usa — é a
  mesma pergunta sobre as mesmas linhas. Testes de ViewModel que **fixam** o agrupamento, que
  é o que faltava para a regressão ter sido pega.
- [x] 11.2 **O estado da manutenção sai da lista e ganha um ícone**, para deixar de ocupar
  e de parecer o lugar de um cabeçalho: ele fala sobre o acervo inteiro, não sobre as linhas
  que o seguem. O ícone é o que o torna estruturalmente um estado antes de qualquer palavra
  ser lida.
- [x] 11.3 **Conferência**: nenhuma chave de string nova (`exchange_rates_group_header` já
  existia); `ConsolidationBoundaryTest` volta a listar o `ExchangeRatesViewModel`, que torna
  a ler a data e a contraparte para agrupar.

---

## 12. A manutenção que funcionou deixa de ser anunciada

Pedido depois do grupo 11, e ele corrige uma premissa de D9 e não só uma label. D9 dizia que
o instante da última sincronização bem-sucedida devia aparecer, porque sem ele o selo
*"Desatualizada"* seria acusação sem réu. O argumento continua válido para o caso em que a
manutenção **nunca** rodou — ali as taxas na tela são só as que o usuário pôs — e não se
sustenta para o caso em que ela rodou: anunciar todo dia que está tudo bem é a forma mais
confiável de a tela deixar de ser lida, inclusive no dia em que ela tiver algo a dizer.

- [x] 12.1 **A label "Atualizado em" sai**, em `ExchangeRatesScreen.kt` e nos dois
  `strings.xml` (a chave `exchange_rates_sync_updated_at` some dos dois no mesmo passo). O
  *"Ainda não atualizado"* fica, e com ele o ícone que impede a linha de ser lida como
  cabeçalho. `RateSyncStatus.lastSyncedOn` **permanece** no UiState: ele é o que decide se há
  algo a dizer, e o estado continua honesto sobre o que sabe.
- [x] 12.2 **A spec passa a exigir só o acionável**: *nunca atualizado* e *moeda não
  coberta*, com a proibição de anunciar a manutenção que funcionou, e os dois cenários
  correspondentes.

---

## 13. O acervo passa a ter uma apresentação só

Pedido depois do grupo 12. O acervo abria no painel de detalhe em janelas extra-largas, ao
lado da tela de configurações que levava a ele, e passa a existir **só como rota**.

A razão é o que a change fez do acervo: ele deixou de ser uma lista para olhar e virou um
lugar onde se **trabalha** — filtrar, alcançar o histórico de um par, corrigir, remover. Uma
segunda apresentação disso era um segundo conjunto de estados a manter verdadeiro sem
responder melhor a nenhuma pergunta.

Nenhuma spec exige o painel para o acervo: `adaptive-detail-pane` enumera as superfícies
adaptativas — os detalhes `view*` e as configurações do widget do dashboard — e o acervo não
está entre elas; a noção de detalhe *pane-only* que ele usava continua de pé e continua com
dono, no chat do suporte.

- [x] 13.1 **`ExchangeRatesDetail` é removido**, e com ele o ramo por largura de janela em
  `SettingsScreen` (o `DisposableEffect` que dispensava o painel ao sair de Configurações, o
  `detailController` e o `isExtraWideWindow` locais). `ExchangeRatesContent` deixa de ser
  compartilhado e passa a `private`, com o KDoc dizendo por que a apresentação é uma só.
  `isExtraWideWindow` e o mecanismo de painel **não** ficam órfãos: seguem em uso pela shell
  e pelo suporte.

---

## 14. O histórico passa a agrupar por data

Pedido depois do grupo 13, e o argumento é derivado desta própria change: com a manutenção
automática gravando uma linha por par **por dia**, agrupar o histórico pela contraparte
degenera. No acervo ordinário tudo é precificado na base, então meses de histórico colapsam
num grupo único de centenas de linhas — exatamente o *não agrupa nada* que a contraparte foi
escolhida para evitar na outra visão, alcançado pela outra ponta. A data particiona o acervo
na razão em que ele cresce, e é o eixo que o histórico existe para percorrer.

A visão **em vigor** não muda: lá são poucas linhas, e o cabeçalho dizendo em que elas estão
precificadas é a frase que o usuário veio ler.

- [x] 14.1 **O agrupamento do histórico vira por data**, em `ExchangeRateHistoryUiState.kt`,
  `ExchangeRateHistoryViewModel.kt` e `ExchangeRateHistoryScreen.kt`: o grupo passa a ser
  chaveado pela data, os dias mais recentes primeiro, e o cabeçalho passa a ser a data
  formatada — sem chave de string nova, porque a data não precisa de moldura. Dentro do dia,
  ordem **total e estável** (contraparte, moeda, id), para que duas leituras do mesmo acervo
  listem o dia igual. `exchange_rates_group_header` **continua com dono**, na visão em vigor.
- [x] 14.2 **A spec é reestruturada**: o requisito "A tela de taxas agrupa as observações
  pela moeda contraparte" nomeava um critério único para uma tela que virou duas, então vai
  para `REMOVED` e é substituído por dois — um por visão. As duas garantias que ele realmente
  protegia (cada linha se descrever por inteiro; nenhuma linha exibida invertida) são
  reafirmadas literalmente nos dois.

---

## 15. Os filtros do histórico encolhem, e limpar sai da barra

Pedido depois do grupo 14, e são duas coisas pequenas com a mesma raiz: a barra de filtros é
**chrome acima daquilo que o usuário veio ler**, e estava cobrando espaço por informação que
não dava.

- [x] 15.1 **Cada chip passa a carregar uma palavra só**: a dimensão enquanto não filtra
  nada (*Data*, *Moeda*, *Origem*), o valor quando filtra. Antes carregava as duas ao mesmo
  tempo — *Data | Qualquer data* —, o que dobrava a largura para dizer duas vezes a mesma
  coisa, sendo a segunda a menos informativa.
- [x] 15.2 **O botão de limpar sai da barra e passa a viver no vazio**, que é a convenção do
  app: uma ação permanente ao lado dos filtros é chrome que o usuário paga toda vez que abre
  a tela, para desfazer algo que ainda não fez. No resultado vazio ela é a resposta à
  pergunta que a tela está fazendo, e só aparece quando há filtro ativo.
- [x] 15.3 **Três chaves órfãs saem dos dois `strings.xml`**
  (`exchange_rate_history_filter_date_any`, `_date_start`, `_date_end`): declaradas em 1.1
  por antecipação e nunca consumidas — o seletor de intervalo traz os próprios rótulos, e a
  dimensão passou a ser o rótulo do chip sem filtro.

---

## 16. Qual das duas pontas não é coberta passa a ser perguntado, e não adivinhado

Descoberto na verificação do grupo 15. `quote(currency, against)` devolve `NotCovered`
quando o provedor recusa, e a implementação atribuía a recusa à `currency` — a primeira
ponta. A recusa, porém, nomeia **um par**, e não diz sobre qual das duas pontas ela é.

No caso ordinário adivinhar a primeira acerta. No caso que importa, erra sobre todas as
moedas de uma vez: se a moeda **base** é a não coberta, todo par é recusado, e a tela
passaria a afirmar *"o dólar não é coberto"*, *"o euro não é coberto"* — uma frase falsa por
moeda que o usuário tem — quando a verdadeira é uma só, sobre a base. É alcançável porque o
registro de moedas é editável e qualquer moeda dele pode virar a base.

D7 exige que a distinção seja **acionável**, e uma lista de acusações à ponta errada não é:
o conselho *"cadastre à mão"* sai certo por acidente, com o diagnóstico errado.

Barreira de saída: `./gradlew jvmTest` verde, e uma base fora da cobertura produz uma frase
sobre a base e nenhuma sobre as moedas.

- [x] 16.1 **A cobertura passa a ser perguntada**, em `IRemoteRateSource` (`coverage()`) e
  em `FrankfurterRateSource` (`/v1/currencies`). `null` é *cobertura desconhecida* e não
  *cobre nada*: com o endpoint inalcançável, o uso cai no caminho antigo, de perguntar par a
  par. Ela **economiza** requisições — moeda fora da cobertura é resolvida sem cotação.
- [x] 16.2 **A atribuição passa a ser correta**, em `SyncExchangeRatesUseCase`: base fora da
  cobertura registra a **base** como não coberta, carimba os pares (uma recusa é resposta
  definitiva, e não carimbar faria a rodada se repetir a cada abertura) e não gasta cotação
  alguma. Base coberta limpa a frase, e só quando a cobertura é conhecida — limpá-la com a
  fonte inalcançável derrubaria uma afirmação verdadeira no primeiro soluço de rede.
- [x] 16.3 **A tela diz a frase certa**, em `ExchangeRatesUiState.kt`,
  `ExchangeRatesViewModel.kt` e `ExchangeRatesScreen.kt`: `isBaseNotCovered` é estado
  próprio e **substitui** a lista, em vez de ser mais um item dela. Chave nova
  `exchange_rates_base_not_covered` nos dois `strings.xml`.
- [x] 16.4 **O gate do estado da manutenção**, em `RemoteSourceIsNeverReadTest`: a spec diz
  que a tela de taxas é a **única** superfície onde o estado da sincronização aparece, e
  nada no compilador dizia isso. Passa a ser fixado por nome, como o do port remoto.
- [x] 16.5 **O gatilho do registro é dito como é**: `whenDue()` reage ao conjunto de moedas
  oferecidas **mudar**, não só a *ganhar* uma. Ganhar é o caso que tem de disparar; estreitar
  o sinal exigiria guardar o conjunto anterior para diferenciar, e economizaria requisições
  que o limite por par já não cobra. O KDoc passa a dizer o que o código faz.
