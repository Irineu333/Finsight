## Context

O acervo de taxas nasceu com dois escritores, e ambos dependem do usuário: `HarvestExchangeRateUseCase`, que colhe a taxa de toda operação que atravessa moedas, e o formulário, onde ele digita. A consequência é que um usuário multimoeda que ainda não transacionou tem acervo vazio — e acervo vazio tem comportamento definido, mas o comportamento definido é a figura empilhar termos.

A `currency-consolidation` protege uma garantia forte e a protege com uma frase larga demais:

> Uma fonte externa MAY oferecer um valor **sugerido** dentro da tela que edita a taxa, e MUST NOT ser consultada em nenhum outro ponto. Nenhuma leitura, tela ou figura do app SHALL depender de rede, apresentar estado de carregamento ou falhar por indisponibilidade em razão de conversão de moeda.

As duas frases não são a mesma. A segunda é a garantia; a primeira é uma forma de obtê-la — a única que estava sobre a mesa quando o acervo tinha dois escritores e ambos eram síncronos com uma tela. Uma sincronização que **escreve** no acervo obtém a mesma garantia por outro caminho, e um caminho melhor: a rede deixa de estar no caminho crítico de qualquer coisa que o usuário esteja olhando.

O que segue decide onde essa escrita entra, quem ela vence, e o que ela quebra de tabela.

## Goals / Non-Goals

**Goals**

- Um usuário online com contas em mais de uma moeda tem taxas sem cadastrar nada.
- A garantia de que nenhuma leitura espera rede permanece **literal**, e passa a ser sustentada pela direção do fluxo em vez de por uma proibição.
- A precedência entre as três origens é declarada, e responde sempre igual sobre o mesmo acervo.
- O que a fonte remota não cobre é dito ao usuário, e não silenciado.

**Non-Goals**

- Fazer o app funcionar offline na primeira execução. Cai no pior caso, e isso é declarado (D6).
- Fixar (*pin*) a taxa do usuário contra sincronizações futuras (D3).
- Desligar a sincronização por par, ou escolher provedor na interface. Se a fonte estiver persistentemente errada num par, o remédio de hoje é cadastrar a taxa à mão — e o de amanhã é uma change própria.
- Sincronizar sob demanda. Um botão é trivial de fazer e ensina o oposto do que esta change quer estabelecer: que manter o acervo é obrigação do app, e não tarefa que o usuário lembra de executar. Ele também criaria uma superfície que espera rede com o usuário olhando — exatamente a forma de estado de carregamento que o limite diário torna desnecessária. O que ele resolveria — *"a cotação de hoje ainda não chegou"* — o limite diário resolve sozinho na abertura seguinte.
- Backfill de histórico. A série temporal do provedor resolveria figuras de meses passados, mas é escopo independente e não serve à motivação desta change.
- Consentimento explícito para a requisição. O payload é uma lista de códigos ISO; não há dado do usuário nela.

## Decisions

### D1 — A fonte remota é o terceiro **escritor**, nunca um leitor

```
        ESCRITORES                                LEITURA (intacta)

  Harvest ────┐ DERIVED
              │                                  ratesAsOf(date)
  Sync ──────▶│  exchange_rates  ───────────────▶ RateResolver
  (novo)      │  (par, data, origem)              direto ▸ inverso ▸ 1 pivô
              │                                        ↓
  Usuário ────┘ USER                              figura consolidada
                                                  ── nunca espera rede
```

Esta é a decisão que sustenta todas as outras. A alternativa — consultar a fonte no momento em que falta uma taxa — é o que a spec proíbe e o que ela deve continuar proibindo: poria rede no caminho de um saldo, e um saldo que falha por indisponibilidade é pior do que um saldo que empilha termos, porque o segundo é honesto e o primeiro é uma tela de erro sobre dinheiro que existe.

Como consequência, **a fonte remota não tem nenhum consumidor síncrono**. Ninguém a aguarda. Ela escreve, e o `Flow` do `observeAll` faz o resto chegar às telas pelo caminho que já existe.

### D2 — `USER` ▸ `REMOTE` ▸ `DERIVED`

A `DERIVED` é o quociente das duas pontas de uma operação real, e portanto **contém o que a operação cobrou**: *spread* de câmbio, IOF, tarifa de cartão. Ela responde *"quanto me custou"*. A `REMOTE` é a cotação do par no dia, e responde *"quanto valia"*.

Consolidar é **avaliar** um patrimônio, não reconstituir um custo. Quando as duas existem para o mesmo par e o mesmo dia, a pergunta que a consolidação faz é a segunda, então a `REMOTE` vence.

A ordem inversa (`DERIVED` sobre `REMOTE`) é defensável por outro argumento — é o único número que de fato aconteceu com aquele usuário — e foi descartada por dois motivos. Primeiro, ela faria o patrimônio de um mês variar conforme o usuário tivesse ou não feito um câmbio caro naquele dia, o que é uma dependência espúria entre operação e avaliação. Segundo, ela tornaria a `REMOTE` quase inútil justamente no usuário multimoeda ativo, que é o alvo desta change.

A `DERIVED` **não** perde razão de existir: ela é a única origem que funciona offline, é a única que alcança pares fora da cobertura do provedor, e continua sendo o que evita o usuário digitar duas vezes um número que ele já deu.

### D3 — A precedência desempata **dentro do dia**, e não fixa

```
02/08   usuário cadastra   USD→BRL  5,30   USER
03/08   sync traz          USD→BRL  5,42   REMOTE

figura de 03/08  →  5,42       (a data vence; a origem só desempata empate de data)
figura de 02/08  →  5,30       (mesmo dia: USER vence)
```

A leitura alternativa de *"a taxa do usuário prevalece"* seria fixação: aquele par passa a responder 5,30 até o usuário mudar. Ela foi descartada porque exige que a origem vença a data, e é a datação que faz o acervo ser um acervo. Uma taxa cravada em março responderia por agosto **em silêncio**, e o usuário não teria de onde perceber que uma correção de meses atrás ainda governa — é exatamente o defeito que `ExchangeRateEntity` descreve como razão de existir da coluna `date`: *"sem uma data, o patrimônio de dezembro é recalculado à taxa de hoje e se move sozinho"*, aplicado ao presente em vez de ao passado.

Custo assumido, e é real: quem corrigir uma taxa vê a correção ser superada no dia seguinte. É o comportamento certo — a correção **corrigiu o dia dela**, que é sobre o que ela era uma afirmação.

Isto vale sem alterar nada na política: o `ORDER BY date DESC` já precede o desempate por origem, nos dois métodos. A mudança é só o desempate deixar de ser binário.

### D4 — Uma requisição por moeda, na direção em que a linha será lida

O provedor responde uma base e várias contrapartes numa chamada só (`?base=X&symbols=A,B,C`). A forma barata seria `base=BRL&symbols=USD,EUR` — uma requisição, `N-1` linhas.

Ela está errada, e a razão é a direção. Essa chamada devolve *"1 BRL = 0,18 USD"*, e gravar isso produz linhas `(BRL, USD)`, `(BRL, EUR)`. O acervo ordinário deixaria de ser *"tudo precificado na base"* e passaria a ser *"a base precificada em tudo"* — o que inverte o agrupamento da tela de taxas (que agrupa pela contraparte precisamente porque a base é a ponta que reúne) e faz cada linha ler ao contrário do que o usuário pergunta.

Corrigir isso na gravação seria inverter o quociente, que a spec proíbe frontalmente: *"a direção MUST NOT ser canonicalizada na gravação"*, porque gravaria um número que ninguém observou.

Então: **uma requisição por moeda em uso**, com `base=<moeda>&symbols=<base>`, produzindo a linha `(moeda, base)` na direção em que ela será lida. Com o número de moedas que D14 já mantém pequeno, é um punhado de requisições uma vez por dia.

### D5 — A data da linha é a do provedor, não a de hoje

O provedor publica em dias úteis e responde com a data da cotação, que num sábado é a de sexta. A linha SHALL ser gravada com **essa** data.

Gravar com a data de hoje inventaria uma observação sobre um dia em que ninguém observou nada — o mesmo defeito de D2 do acervo, aplicado ao eixo do tempo. E há um efeito colateral bom: a sincronização vira **idempotente** sem esforço. Rodar duas vezes no mesmo dia, ou rodar no domingo depois de ter rodado no sábado, reescreve a mesma linha `(par, data, REMOTE)` pelo `REPLACE` que a chave única já garante. Nada duplica, e nada precisa saber que já rodou.

### D6 — Offline na primeira execução cai no pior caso, e isso é dito

Não há semente embarcada. A alternativa — um retrato das cotações no momento do build — foi descartada: o app nasceria com números velhos apresentados como observações, com a data do release, e o sinal de *"desatualizada há mais de 30 dias"* passaria a disparar sozinho em toda instalação com mais de um mês de loja. Trocaria um estado honesto e já definido por um estado errado e disfarçado.

O que a change compra é uma garantia sobre o usuário **online**, e ela é escrita como tal na spec. O pior caso deixa de ser o estado inicial de quem tem rede, e permanece o estado inicial de quem não tem — onde já era, com o comportamento que já tinha.

### D7 — Cobertura parcial é dita, e a distinção é a que o usuário pode agir

O provedor cobre cerca de trinta moedas, e o catálogo do app é mais largo. Uma moeda fora da cobertura simplesmente nunca receberia linha, e o usuário ficaria no pior caso sem nada explicando por quê.

São **dois estados diferentes**, e só a distinção entre eles é acionável:

| estado | o que o usuário faz |
|---|---|
| ainda não sincronizou / está sem rede | esperar |
| o provedor não cobre esta moeda | cadastrar à mão, e é permanente |

A resposta do provedor distingue os dois: um código desconhecido é recusado explicitamente, e indisponibilidade é falha de transporte. A tela de taxas em vigor SHALL dizer qual dos dois é o caso, por moeda.

### D7b — Qual das duas pontas não é coberta é **perguntado**, e não inferido da recusa

Descoberto na verificação, e é um erro de atribuição que a forma da resposta esconde: uma cotação nomeia **um par**, e a recusa dele não diz sobre qual das duas pontas ela é. Culpar a primeira ponta acerta no caso ordinário e erra sobre tudo ao mesmo tempo no caso que importa — se a moeda **base** é a não coberta, todo par é recusado, e a tela afirmaria *"o dólar não é coberto"*, *"o euro não é coberto"*, uma frase falsa por moeda que o usuário tem, quando a verdadeira é uma só. É alcançável: o registro de moedas é editável e qualquer moeda dele pode virar a base.

A cobertura passa a ser **perguntada** ao provedor — o conjunto de códigos que ele cota —, e a atribuição deixa de ser adivinhação. Três consequências, todas na direção certa:

- **A base fora da cobertura vira uma frase só, sobre a base**, e ela leva a duas ações que a lista não levava: cadastrar tudo à mão, ou trocar a base por uma moeda cotada.
- **Moeda fora da cobertura não gasta cotação.** A pergunta se paga: uma requisição por rodada substitui uma por moeda não coberta.
- **Desconhecido não é vazio.** Com o endpoint inalcançável, a cobertura é `null` e a rodada volta a perguntar par a par — e uma afirmação verdadeira já registrada sobre a base não é derrubada por um soluço de rede.

A alternativa — inferir a base pela rodada em que *todas* as moedas foram recusadas — foi descartada: com uma moeda só em uso as duas hipóteses são indistinguíveis, e uma inferência que falha justamente no acervo mais pobre é a que menos serve.

### D8 — A cadência é a abertura, com limite diário, e o gancho já existe

`App` já tem um `LaunchedEffect(Unit)` fazendo trabalho transversal e disparado-e-esquecido — o *user-id* em analytics e crashlytics. A sincronização entra ali, pelo mesmo padrão, com um limite por instante da última sincronização bem-sucedida.

As alternativas foram descartadas por custo desproporcional: trabalho agendado em segundo plano exige `WorkManager` no Android e uma história própria no iOS, para ganhar atualização com o app fechado — que não é o problema, porque a figura só precisa da taxa quando alguém a está olhando. E o `DashboardViewModel`, que hoje hospeda o `EnsureDefaultAccountUseCase` pelo mesmo motivo de não haver passo de inicialização, é o lugar errado: taxa não é assunto do dashboard, e amarrá-la a uma aba faz a sincronização depender de qual tela o usuário abriu.

Vale registrar que isto **cria** o primeiro passo de inicialização real do app, coisa que o design de `add-multi-currency-accounts` registrou não existir. Ele nasce com uma propriedade que o mantém inofensivo: nada na composição o aguarda, e falhar é não fazer nada.

### D8b — O conjunto é o **oferecido**, e o limite diário é **por moeda**

Descoberto em teste manual, e é o mesmo defeito visto de dois ângulos.

Sincronizar apenas as moedas **em uso** parece mais eficiente e inverte a ordem que interessa: a taxa passa a **seguir** a conta em vez de precedê-la. Toda primeira conta numa moeda nasce no pior caso, e — porque o limite diário já se cumpriu naquele dia — permanece nele até a abertura seguinte. O usuário que acabou de cadastrar a conta em dólar vê a figura empilhar termos por até 24 horas, que é literalmente o estado que esta change existe para remover.

Cobrir o que o app **oferece** conserta a ordem: o registro de moedas é pequeno e curado — seis linhas semeadas mais a do *locale* mais o que o usuário cadastrar —, então são um punhado de requisições por dia, e a taxa está no acervo antes de existir conta que precise dela. Some-se a isso qualquer moeda ainda em uso mesmo estando arquivada: arquivar é sobre o que se oferece e não sobre o que se sabe, e a conta que sobreviveu ao arquivamento continua precisando da sua taxa para a figura fechar.

Isso sozinho não basta, e é a segunda metade que fecha o buraco. Com o limite **global**, cadastrar uma moeda nova cai no mesmo caso: a sincronização de hoje já rodou, aquela moeda nunca foi consultada, e ainda assim está bloqueada até amanhã. O limite passa a ser **por moeda** — o instante persistido deixa de ser um e passa a ser um por moeda —, o que torna a frase honesta: *esta* moeda já foi buscada hoje. Uma moeda que nunca foi buscada não tem instante nenhum, logo nada a bloqueia.

Com o limite por moeda, redisparar a sincronização é de graça: tudo que já respondeu hoje é pulado, e só o que falta sai para a rede. É isso que permite o gatilho de D8c ser trivialmente seguro.

O instante que a tela mostra passa a ser **derivado**, o mais recente dos por-moeda, em vez de um campo próprio. Um segundo campo seria um segundo dono da mesma frase, e eles divergiriam na primeira sincronização parcial.

### D8c — Cadastrar uma moeda dispara a sincronização, e isso não é um comando

O gatilho fica onde o de abertura já está: `App` passa a **observar o registro** e a redisparar quando ele muda. Um ponto de gatilho e não dois — pôr a chamada na ViewModel do formulário espalharia a obrigação, e um segundo caminho de cadastro no futuro esqueceria de cumpri-la.

O sinal é *mudar* e não *ganhar*, e a diferença é deliberada: ganhar é o caso que tem de disparar, e estreitar o sinal a ele exigiria guardar o conjunto anterior para diferenciar — trabalho de estado para economizar requisições que o limite por par já não cobra. Perder ou arquivar uma moeda dispara uma rodada em que todo par restante é pulado, que é rodada nenhuma.

Isto **não** é o botão de sincronizar que os Non-Goals recusam. O que aquele Non-Goal barra é o usuário ter de lembrar de executar uma tarefa, e uma superfície que espera rede com ele olhando. Aqui o gatilho é uma mudança de estado do app, ninguém o aguarda, nada na composição depende dele, e falhar continua sendo não fazer nada.

### D8d — O limite é por **par**, e trocar a base é um gatilho

Descoberto em teste manual, e é o mesmo defeito de D8b uma volta acima: **o limite guardava menos informação do que a pergunta que ele governa.**

`syncedAt` era por moeda — *o dólar foi cotado hoje* — sem dizer contra o quê. Mas o que se busca é um **par**. Trocada a base, o acervo passa a precisar das linhas contra a base nova, e nenhuma delas jamais foi buscada; ainda assim toda moeda parecia respondida, e a chave não tinha como exprimir a diferença. O acervo ficava um dia atrás da preferência, em silêncio — as figuras continuavam certas, porque a resolução tria­ngula sobre a base antiga, o que é justamente o que fazia o defeito não aparecer.

A chave passa a ser `RatePair(currency, against)`. A frase fica honesta — *este par foi cotado hoje* — e o comportamento de trocar a base e voltar atrás no mesmo dia sai de graça e correto: aqueles pares já foram respondidos, então não se busca nada.

E, como em D8c, o limite certo não basta sem o gatilho: `App` observava o registro e não a base. Em vez de acrescentar mais uma coisa para a shell observar, **a regra de quando a manutenção vence passa a morar no use case**, em `whenDue()` — abertura, registro ganhando moeda, base mudando. Duas razões:

- **Ela é do domínio da manutenção**, não da shell. O que faz o acervo dever outra rodada é fato sobre o acervo.
- **Ela mantém `BaseCurrencyReachTest` intacto.** Para observar a troca de base, `App` teria de nomear `IBaseCurrencyRepository` e seria a primeira tela do app a fazê-lo — exatamente o que aquele *gate* existe para barrar, e por um bom motivo. Com a regra dentro do use case, que já nomeia a base legitimamente, a shell só coleta.

Coletar isso é seguro a ponto de ser entediante, e é o limite por par que o torna assim: tudo que já respondeu hoje é pulado, então uma rodada disparada por uma mudança que não importa a nada custa zero requisição.

### D9 — O instante da última sincronização é estado persistido, e não um canal de erro

Persistir *"quando sincronizou com sucesso pela última vez"* sobrevive a reinício do app, o que um estado de erro em memória não faria. Falhou? Nada é escrito, e o instante antigo continua respondendo. Não há canal de erro, não há evento, não há estado transitório a coordenar.

**O que a tela mostra é só o acionável, e isso corrige uma premissa desta decisão.** Ela dizia que o instante devia aparecer, porque sem ele o selo *"desatualizada há mais de 30 dias"* seria acusação sem réu. O argumento se sustenta para o caso em que a manutenção **nunca** rodou — ali as taxas na tela são apenas as que o usuário mesmo pôs, e é isso que o selo precisa de contexto para não acusar — e não se sustenta para o caso em que ela rodou: anunciar todo dia que está tudo bem é a forma mais confiável de a tela deixar de ser lida, inclusive no dia em que ela tiver algo a dizer. A manutenção que funcionou não é anunciada.

O instante **permanece no estado** assim mesmo: ele é o que decide se há algo a dizer, e o estado continua honesto sobre o que sabe ainda quando a tela cala.

O sinal fica **na tela de taxas e em nenhum outro lugar**. A proibição da spec é sobre figura consolidada, e a tela de taxas não é figura: é o acervo se explicando, e é onde o selo de desatualizada já vive.

### D10 — A resposta implícita deixa de mentir a origem

`ExchangeRateRepository.answer` constrói a taxa que o acervo **implica** — a inversa, ou o produto de uma triangulação — e a rotula `DERIVED`. Isso era legítimo enquanto o campo significava, no único ponto que o consultava, *"não é do usuário"*. Com três origens deixa de ser.

A resposta implícita SHALL carregar a origem da observação que ela leu quando há uma só (a inversa é a **mesma** observação lida ao contrário), e a **mais fraca** das duas numa triangulação — o que é bem definido precisamente porque D2 declarou uma ordem total. Nada disso é gravado: a resposta implícita continua sem `id`, e continua não podendo voltar ao acervo.

### D11 — Onde cada peça mora

```
:core:model          Source.REMOTE
                     IRemoteRateSource        ← o port; nenhum cliente HTTP
                     SyncExchangeRatesUseCase ← concreto, ao lado do Harvest

:core:database       ExchangeRateDao          ← ranking de origem nas duas queries

feature/settings/impl  cliente Ktor sobre o port
                       instante da última sincronização (multiplatform-settings, já usado)
                       telas: em vigor  |  histórico com filtros

:app:shared          dispara a sincronização no LaunchedEffect que já existe
```

O port em `:core:model` e a implementação em `feature/settings/impl` seguem exatamente o que `GetAccountCurrenciesUseCase` já pratica: o dono da regra fica na camada de consolidação, e o acesso ao dado fica com a feature. Ktor **não** entra em `:core:model` — quem depende de modelos não deve herdar um cliente HTTP.

Um `:core:network` foi descartado. Ele convidaria qualquer feature a usar rede, que é o oposto do que esta capability quer; deixando o cliente dentro de `settings/impl`, a restrição vira estrutura de módulo em vez de disciplina.

## Risks / Trade-offs

- **A tela de taxas fica ilegível se as duas telas não vierem junto.** Uma linha por par por dia enche a listagem atual — que mostra o acervo inteiro — em dias. É por isso que as telas estão nesta change e não na seguinte, embora nada nelas tenha a ver com a fonte remota.
- **Correção do usuário superada no dia seguinte** (D3). É o comportamento certo e vai surpreender alguém. Mitigação possível numa change futura: desligar a sincronização por par.
- **Dependência de um provedor externo, sem contrato.** Frankfurter é gratuito, sem chave e sem SLA. Falhar é não fazer nada, então a falha é barata — mas uma descontinuação silenciosa deixaria os usuários novos no pior caso sem sinal, e é por isso que D9 existe.
- **Primeira dependência de rede do projeto.** Ktor entra com motor por plataforma, e o app passa a fazer requisição na abertura. O payload é uma lista de códigos ISO e nada mais, mas é um fato novo sobre o app.
- **O conjunto sincronizado é o registro de moedas oferecidas mais o que está em uso** (D8b). Ele cresce com o que o usuário cadastrar, então um usuário que cadastre trinta moedas paga trinta requisições por dia. É aceitável porque o registro é curado por ele e porque falhar é barato, mas deixa de ser se o registro algum dia voltar a ser um catálogo largo — caso em que o conjunto teria de voltar a se estreitar pelo uso.
- **A `DERIVED` perde precedência** e, com isso, alguma visibilidade: o número que o usuário efetivamente pagou deixa de ser o que consolida quando há cotação do mesmo dia. Ele continua no acervo, continua visível no histórico, e continua sendo o que responde offline.

## Open Questions

- Vale, numa change seguinte, popular o histórico pela série temporal do provedor na primeira sincronização? Daria taxa de época a figuras de meses passados, que hoje só existem nos dias em que o usuário transacionou.
