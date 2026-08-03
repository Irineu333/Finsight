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

### D8 — A cadência é a abertura, com limite diário, e o gancho já existe

`App` já tem um `LaunchedEffect(Unit)` fazendo trabalho transversal e disparado-e-esquecido — o *user-id* em analytics e crashlytics. A sincronização entra ali, pelo mesmo padrão, com um limite por instante da última sincronização bem-sucedida.

As alternativas foram descartadas por custo desproporcional: trabalho agendado em segundo plano exige `WorkManager` no Android e uma história própria no iOS, para ganhar atualização com o app fechado — que não é o problema, porque a figura só precisa da taxa quando alguém a está olhando. E o `DashboardViewModel`, que hoje hospeda o `EnsureDefaultAccountUseCase` pelo mesmo motivo de não haver passo de inicialização, é o lugar errado: taxa não é assunto do dashboard, e amarrá-la a uma aba faz a sincronização depender de qual tela o usuário abriu.

Vale registrar que isto **cria** o primeiro passo de inicialização real do app, coisa que o design de `add-multi-currency-accounts` registrou não existir. Ele nasce com uma propriedade que o mantém inofensivo: nada na composição o aguarda, e falhar é não fazer nada.

### D9 — O instante da última sincronização é o que a tela mostra, e não um canal de erro

Persistir *"quando sincronizou com sucesso pela última vez"* é suficiente para a tela dizer o que precisa dizer, e sobrevive a reinício do app — o que um estado de erro em memória não faria. Falhou? Nada é escrito, e a tela deduz do instante antigo. Não há canal de erro, não há evento, não há estado transitório a coordenar.

O sinal fica **na tela de taxas e em nenhum outro lugar**. A proibição da spec é sobre figura consolidada, e a tela de taxas não é figura: é o acervo se explicando, e é onde o sinal de *"desatualizada há mais de 30 dias"* já vive. Sem contexto de sincronização, esse sinal é uma acusação sem réu.

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
- **O conjunto sincronizado sai de `GetAccountCurrenciesUseCase.inUse`**, que cobre contas e cartões. Um orçamento denominado numa moeda cuja conta foi apagada fica fora — caso estreito, que cai no comportamento já definido para taxa ausente.
- **A `DERIVED` perde precedência** e, com isso, alguma visibilidade: o número que o usuário efetivamente pagou deixa de ser o que consolida quando há cotação do mesmo dia. Ele continua no acervo, continua visível no histórico, e continua sendo o que responde offline.

## Open Questions

- Vale, numa change seguinte, popular o histórico pela série temporal do provedor na primeira sincronização? Daria taxa de época a figuras de meses passados, que hoje só existem nos dias em que o usuário transacionou.
