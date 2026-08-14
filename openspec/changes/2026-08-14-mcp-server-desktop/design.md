## Context

O desktop hoje é um `kotlin("jvm")` que só faz bootstrap: `main()` inicializa o Firebase,
chama `startKoin { modules(appModules) }` e abre uma `Window`
(`app/desktop/src/main/kotlin/com/neoutils/finsight/main.kt:22-63`). Ele depende de
`implementation(projects.app.shared)` e mais nada — não enxerga nenhuma `api` de feature
(`app/desktop/build.gradle.kts:16`). O banco é um arquivo só,
`~/.finance/finsight.db` (`core/database/src/jvmMain/.../Database.jvm.kt:8`).

Três fatos do repositório governam quase todo o desenho abaixo.

**O primeiro é onde as regras moram.** `feature/*/api` expõe 15 casos de uso;
`feature/*/impl` guarda 51, e a escrita está quase inteira lá — criar conta, transferir,
pagar fatura, fechar fatura, confirmar recorrência. Um módulo que dependa só das `api` não
consegue escrever quase nada.

**O segundo é o que a `api` é.** A tentação é ler a divisão acima como uma declaração de
internalidade, e concluir que o servidor precisa de um privilégio novo para alcançar o
`impl`. O documento normativo diz o contrário (`feature/README.md:28`):

> **Critério de triagem:** só entra na `api` o que **outro módulo consome**. Tudo o mais é
> detalhe de implementação e vive no `impl`. Na dúvida, comece no `impl` — promover para a
> `api` depois é barato; o inverso quebra consumidores.

A `api` é conquistada por demanda, não curada por doutrina — e os dados confirmam: 11 dos 15
casos de uso ali têm consumidor externo. Logo, promover o que o MCP consome **é o critério
sendo aplicado a um novo consumidor**. Inventar uma casta de "shells que enxergam `impl`"
seria o gesto oposto: criar um privilégio para não aplicar a regra que já existe.

**O terceiro é que o razão não consolida.** Toda leitura que pode cruzar contas responde
`MoneyByCurrency`; reduzir a um número é conversão, e conversão vive acima do razão, em
`:core:model`. Uma fronteira nova não pode desfazer isso por conveniência de serialização.

## Goals / Non-Goals

**Goals:**
- Uma segunda interface sobre o mesmo domínio, com os mesmos direitos da tela e **nenhuma
  regra própria**.
- Segura por padrão: desligada ao nascer, somente leitura ao ser ligada, autenticada mesmo
  em loopback, e auditável no que escreve.
- Configurável no app, com o gesto de habilitar produzindo algo utilizável — não um número
  de porta e boa sorte.
- Não fechar nenhuma porta para o servidor sobreviver ao fechamento do app depois.

**Non-Goals:**
- Sobreviver ao fechamento do app **agora** (bandeja, autostart, cold start headless).
- Desfazer genérico.
- Qualquer acesso que não seja loopback.
- Separar `:app:runtime` do shell Compose nesta mudança (ver D9).

## Decisions

### D1 — O servidor vive dentro do processo do app, e o banco continua com um dono só

A alternativa era um processo separado abrindo o mesmo `finsight.db`. Ela tem um defeito que
não é de configuração: o `InvalidationTracker` do Room **não cruza processos**. O agente
insere uma transação e a janela aberta continua mostrando o saldo antigo até um restart — um
app financeiro mentindo sobre o saldo por causa do agente é pior do que não ter agente.
Somam-se dois candidatos a executar migração no mesmo arquivo.

```
   ESCOLHIDO                              RECUSADO
┌──────────────────────────┐        ┌──────────┐  ┌──────────┐
│  Finsight (desktop)      │        │ Finsight │  │  daemon  │
│  ┌──────┐   ┌─────────┐  │        └────┬─────┘  └────┬─────┘
│  │  UI  │   │   MCP   │  │             │ Room #1     │ Room #2
│  └──┬───┘   └────┬────┘  │             ▼             ▼
│     └─────┬──────┘       │           ┌───────────────────┐
│      Koin único          │           │  finsight.db      │
│      Room único          │           └───────────────────┘
└──────────┬───────────────┘             ⚠ dois trackers
           ▼                             ⚠ duas migrações possíveis
      finsight.db
```

A invariante que fica escrita na spec e sobrevive a todos os degraus futuros: **exatamente um
processo é dono do banco, sempre**. Nenhum caminho pode subir uma segunda instância.

### D2 — Streamable HTTP em loopback, porta fixa, sem ponte e sem arquivo de descoberta

O transporte stdio do MCP pressupõe que o **cliente** faz spawn do servidor. Aqui isso
significaria o cliente lançando uma segunda instância do Finsight — exatamente o que D1 proíbe.
Logo, Streamable HTTP em `127.0.0.1`, que é o transporte vigente do protocolo e o que os
clientes reais já falam nativamente, com o token indo no header de autorização.

A primeira versão desta decisão previa uma ponte stdio e um `~/.finance/mcp.json` anunciando
porta e token. Os dois caem, pela mesma cadeia:

```
porta efêmera  →  a configuração colada no cliente contém a porta
               →  todo cliente quebra a cada reinício do app
               →  contradiz o próprio D8
```

Com a porta **fixa e persistida**, a configuração colada continua válida, e aí o arquivo de
descoberta perde o único leitor que teria — a ponte. E a ponte já não se justificava: nenhum
cliente de referência precisa dela, e ela seria mais um binário para empacotar e assinar cuja
única função é ler um segredo do disco e repassá-lo. Menos código, menos uma cópia do segredo
em disco, e três requisitos de ciclo de vida que existiam só para manter um arquivo honesto
desaparecem.

Porta ocupada não vira porta sorteada: o servidor falha a subir e mostra o conflito. Escolher
outra em silêncio é a mesma quebra da porta efêmera, só que mais rara e por isso mais difícil de
diagnosticar.

O que **não** cai é a validação de `Origin`, que a spec exige como `MUST` e que a primeira versão
desta proposta omitiu. Sem ela, uma página web aberta pelo usuário alcança o servidor por DNS
rebinding — e este servidor escreve no razão.

### D3 — `:app:mcp` é um `impl` sem tela

Consequência direta do segundo fato do Context. Com os casos de uso promovidos, o servidor
depende de `feature:*:api` + `:core:*`, expõe o seu módulo Koin e é agregado pelo
`:app:shared`. Isso é, item por item, a forma de um `impl` — só que sem tela, sem rota e sem
`Entry`. **Nenhuma regra de dependência muda**, e a regra 4 já o cobre.

```
:app:shared ──▶ impls (agregador; segue sendo o único)
     │
     └── agrega mcpModule
              ▲
        :app:mcp ──▶ feature:*:api + :core:*     ← direitos de um impl
```

O custo real não é churn: é que **a demanda do MCP passa a definir a superfície da `api`**.
Se as tools expuserem tudo, `api ≈ impl` nas features tocadas e a distinção perde sentido.
Isso é uma pressão saudável — força a pergunta certa cedo ("o que um agente deve poder
fazer?") — mas é o motivo de a superfície de tools ser deliberada e não automática (D4).

### D4 — Uma tool é um caso de uso do domínio, não um verbo inventado

Duas formas de desenhar a superfície:

| | Espelhar os casos de uso | Vocabulário de intenção (`record_expense`, `review_month`) |
|---|---|---|
| Fidelidade | total | a tradução vira lugar para reimplementar regra |
| Usabilidade | o agente precisa conhecer a arquitetura | mais direto |
| Regra de derivação | respeitada | **violada** — o tradutor decide *qual* regra aplica |

Vence espelhar, porque a regra de derivação do projeto não é negociável: uma regra derivável
do domínio tem exatamente um dono, no domínio. Um verbo agregador que "lança uma despesa no
cartão" teria de decidir sozinho em qual fatura ela cai — e essa decisão já tem dono
(`invoice-governs-date`, `GetOrCreateInvoiceForMonthUseCase`).

A usabilidade se resolve na descrição da tool, não na sua forma: o MCP transporta descrição e
schema, e é ali que o vocabulário do usuário entra.

O corolário é o critério de escopo, que a spec fixa: **uma tool existe para o que o usuário
consegue fazer numa tela**, menos o que depende de julgamento visual. Nada de tool que
alcance escrita sem passar por caso de uso, e nada que o razão não sancione.

### D5 — Dinheiro atravessa a fronteira por moeda; o consolidado vem com a taxa que o produziu

`get_balance` não devolve `1234.56`. Uma leitura que pode cruzar contas devolve o mapa por
moeda, exatamente como o razão a produz; só leitura escopada a uma conta é escalar. Quando o
consolidado é pedido, ele vem **com a taxa e a data da taxa** que o produziram — um número
consolidado sem a taxa é irreproduzível, e um agente fará aritmética em cima dele.

Isso não é rigor gratuito: é a mesma lei que `currency-consolidation` já impõe a toda
superfície de leitura. A fronteira MCP não ganha uma exceção por ser serializável.

### D6 — O erro que o agente recebe é o do domínio, em inglês

Os tipos de erro do projeto carregam `message: String` em inglês para log e `toUiText()` para
tela. O agente é consumidor de log, não de tela: recebe o nome do erro e a `message`, nunca a
string traduzida. Uma tool que devolvesse português a um cliente em inglês estaria vazando a
camada de apresentação por uma fronteira que não é de apresentação.

E a recusa do domínio é **resposta**, não falha de transporte: escrever numa fatura fechada
devolve o erro nomeado, e o agente aprende a regra em vez de tentar de novo.

### D7 — A auditoria é uma tabela no facade — nem coluna no razão, nem arquivo

Três candidatos, e o primeiro cai sozinho: uma coluna `origin` em `transactions` não registra
criar conta, fechar fatura ou arquivar categoria. Cobriria metade dos atos criando a ilusão
de cobertura total, que é pior do que não cobrir.

Contra pôr no razão, a regra de derivação: nenhuma regra do domínio ramifica em "quem pediu",
e nada calcula diferente porque um agente lançou. O que não produz derivação não é parte do
modelo — é jornal da aplicação.

Contra arquivo: a tela de atividade quer `Flow` reativo, que o Room dá de graça; o registro
precisa ser podado junto do dado que descreve; e qualquer reversão futura precisa ser
transacional com o que reverte.

```
agent_activity
  id, timestamp
  client        ← vem de graça no handshake `initialize` do MCP ("Claude Code 2.1")
  tool          ← "create_transaction"
  arguments     ← json, como recebido
  outcome       ← ok | recusado pelo domínio | erro
  affected      ← ids do que tocou → sustenta o crachá na linha e a navegação para a inversa
```

Uma linha por **chamada de tool**, não por linha escrita — inclusive as recusadas, que são
justamente o que alguém quer ver ao investigar. O crachá "criado por agente" numa transação é
um join, e degrada bem: registro podado, crachá some, razão intacto.

### D8 — O toggle significa "o servidor existe"; por quanto tempo o processo vive é outra chave

O risco da escada futura é o mesmo interruptor mudar de promessa sem mudar de nome:

```
hoje       "habilitado" = escuto enquanto a janela está aberta
bandeja    "habilitado" = escuto enquanto você estiver logado     ← mesmo toggle,
autostart  "habilitado" = escuto desde o boot                        outra promessa
```

Por isso as chaves nascem separadas desde já: o toggle principal significa **sempre e só** "o
servidor MCP existe", e a vida do processo é decisão de outras chaves, que ainda não existem.
Assim nenhum degrau redefine um consentimento já dado — cada um pede o seu, do tamanho certo.

Pela mesma lógica, a permissão é uma decisão à parte do toggle, e nasce em somente leitura.
São dois riscos de tamanhos muito diferentes, e leitura já entrega a maior parte do valor.

E ligar não pode ser o fim do fluxo: um toggle ligado com um token que o usuário nunca colou
em lugar nenhum é um estado "funcionando" que não funciona. O primeiro `on` entrega o trecho
de configuração do cliente pronto.

Sobre girar o token ao desligar: **não**. Girar é o gesto de revogação e deve ser só o botão
explícito. Se desligar por um minuto quebrasse todo cliente configurado, o usuário aprenderia
a nunca desligar — e o interruptor de segurança viraria o interruptor que ninguém toca.

### D9 — `:app:runtime` não entra agora, e isso é uma correção

Durante a exploração afirmei que separar o agregador Koin do shell Compose era exigência de
"funcionar com o app fechado". Verificado contra o desenho, é exigência apenas do **último**
degrau — o cold start headless disparado por um cliente. Bandeja e autostart mantêm vivo o
mesmo processo com a mesma `Window`, e não pedem separação alguma; e o MVP funciona com
`:app:shared` agregando `mcpModule` como agrega qualquer outro.

Então o corte fica para quando for necessário. O que **esta** mudança precisa garantir é não
o impedir, e isso se resume a uma proibição concreta, que a spec fixa: o servidor MUST NOT
depender da interface **do Finsight** — nada de `LaunchedEffect`, `WindowScope`, `ModalManager`
ou qualquer consentimento que pressuponha alguém olhando para a janela do app. É também o que
enterra a ideia de confirmar escrita por modal: com o app fechado como destino, aprovação vive
no protocolo ou na política (o que Settings permite), nunca na UI do Finsight.

A proibição é dessa interface, não de toda interação. O protocolo permite que o servidor
devolva um resultado pedindo entrada e que o **cliente** a colete — interação que acontece na
tela do cliente. Redigir a proibição como "nenhuma interação visual" teria banido isso junto,
que é o único caminho de consentimento que sobra.

Na revisão alvo (D12) o servidor **pode** iniciar a solicitação de entrada ao cliente, e a
confirmação por esse caminho é viável — mas fica fora desta entrega, porque depende de o cliente
declarar suporte e o desenho teria de definir o que fazer quando ele não declara. Nesta entrega
o consentimento é a política: o que Settings permite.

Vale registrar para quem migrar: na revisão seguinte esse mecanismo inverte-se. O servidor
devolve "preciso de entrada" e o **cliente reexecuta a requisição original** — ou seja, a chamada
de tool roda duas vezes. Quando a migração acontecer, a chave de idempotência deixa de ser
conveniência e passa a ser pré-requisito de qualquer confirmação. Ligá-la ao resumo dos
argumentos desde já (D10) é o que torna essa migração barata.

### D10 — Poucas tools grossas e em lote, derivadas do pedido do usuário

A primeira versão desta superfície foi derivada de baixo para cima, dos casos de uso que o
código já expunha. Como casos de uso são unitários, as tools nasceram unitárias — e "lança
essas trinta linhas do meu extrato" virava trinta chamadas, com trinta chances de falha parcial
silenciosa e nenhuma forma de o agente saber se deve reprocessar. Derivada do pedido, a mesma
necessidade é **uma** chamada com trinta itens.

Três consequências, todas na spec:

- **Ensaio antes de gravar.** É como o agente mostra "a compra do dia 28 cai na fatura de
  julho" antes de confirmar. Sem ele, todo lançamento em massa é ato de fé.
- **Chave de idempotência.** Agentes repetem chamadas por tempo esgotado, reinício de sessão e
  decisão própria. Sem chave, repetição é duplicação — e duplicação em contabilidade é dano que
  não se anuncia.
- **Desfecho por item.** Um sucesso agregado sobre trinta lançamentos não permite corrigir o
  que falhou sem reprocessar tudo.

O mesmo raciocínio produz o par que protege as leituras: **listas paginam, agregados não**. Sem
uma tool de agregação, o agente pagina, soma e apresenta como exato — errando por moeda e
contando como gasto o que o domínio não classifica como gasto. É a tool que mais protege o
domínio, e ela existe por isso, não por conveniência.

### D11 — A resposta é estruturalmente uniforme, mesmo quando isso custa concisão

Duas escolhas parecem excesso de rigor e não são.

**A coleção por moeda não colapsa** quando há uma moeda só. Se colapsasse, o consumidor
aprenderia a forma escalar — que funciona por meses — e quebraria no dia em que o usuário
abrisse uma conta em outra moeda. Consistência estrutural vale mais que concisão numa fronteira
consumida por quem infere schema a partir de exemplos.

**Taxa faltando não vira número.** O campo consolidado vem ausente com motivo, nunca com taxa
um, nunca com a cotação de hoje no lugar da datada, nunca descartando em silêncio a moeda sem
cotação. Um total ausente o agente reporta como ausente; um total errado ele reporta como
verdade, porque nada na resposta permite suspeitar dele.

Pelo mesmo motivo o **sinal** é fixado como o de exibição em toda a superfície: misturar a
convenção débito-positivo do razão com a de exibição entre respostas diferentes faz o agente
relatar gasto como receita — e é o tipo de erro que só aparece depois de ter sido dito ao
usuário.

### D12 — A revisão alvo é a `2025-11-25`, porque é a que o SDK fala

A primeira versão desta proposta foi escrita contra um MCP anterior, de memória. A segunda
rebaseou-a para a `2026-07-28`, a revisão vigente. A verificação seguinte derrubou a segunda: o
**SDK Kotlin de MCP 0.15.0**, publicado no mesmo dia daquela revisão, declara
`LATEST_PROTOCOL_VERSION = "2025-11-25"`, e o repositório tem uma issue **aberta** rastreando a
implementação da `2026-07-28`. O SDK Java está uma release atrás disso, sem publicação após
junho. Nenhum SDK JVM fala a revisão vigente.

Restavam duas saídas: escrever o transporte à mão contra a revisão nova, ou falar a
`2025-11-25` e usar o SDK. A escolha é a segunda — o transporte não é o produto, e o custo de
mantê-lo à mão não se paga num servidor local de usuário único.

O preço é dívida datada, e o gatilho dela é objetivo: **o SDK passar a falar a `2026-07-28`**.
Enquanto isso, o desenho evita acumular o que a migração teria de desfazer — nada de Roots,
Sampling ou Logging, que a revisão seguinte já depreciou, e nenhuma sessão, que ela remove.

O que muda em relação à segunda versão, e por quê:

| | `2026-07-28` (recusada) | `2025-11-25` (alvo) |
|---|---|---|
| Início | sem handshake, metadados por requisição | `initialize` com negociação de capabilities |
| Sessão | não existe | opcional — **não usamos** |
| Descoberta | `server/discover` obrigatório | não existe |
| Headers | versão + método + nome, conferidos contra o corpo | apenas a versão de protocolo |
| Cancelamento | fechar o stream **é** cancelar | fechar **não** é cancelar; há notificação própria |
| Aviso de mudança | assinatura explícita | capability de mudança de lista |
| Cache de listagem | validade e escopo obrigatórios | não existe |

A inversão do cancelamento é a que mais importa, e não por elegância: ela decide o que acontece
com um lote interrompido. Aqui, perder a conexão **não** cancela, e cancelar é ato explícito do
cliente. Nos dois casos o desfecho é o mesmo e continua sendo o da chave de idempotência —
repetir conclui o que faltou sem duplicar o que entrou.

**O que sobrevive intacto do rebase anterior**, porque já valia nesta revisão: a validação de
`Origin` (`MUST`, e omitida na primeira versão), o bind em loopback, o erro de execução de tool
distinto do erro de protocolo, `outputSchema` com conteúdo estruturado, as anotações de risco,
o aviso de mudança na lista de tools, a paginação por cursor em listagens do protocolo — e o
**limite de taxa**, que a revisão exige como `MUST` e que nenhuma versão desta proposta tinha.

**Sobre autorização.** A especificação de autorização do MCP é opcional, e conformá-la é
recomendado para transportes HTTP; dentro dela, o servidor seria um resource server OAuth 2.1
com metadados de recurso protegido. Um token de portador estático para um servidor loopback de
usuário único é desproporcionalmente mais simples, e é a escolha. O que muda é que ela passa a
ser **registrada como desvio**, com o mínimo que torna a falha legível: `401` com o desafio de
autorização apontando os metadados do recurso, em vez de uma recusa opaca.

### D13 — As perguntas de domínio foram respondidas pelo código, e a superfície as espelha

Quatro decisões que a superfície teria de tomar já estavam tomadas no domínio, e a verificação
as recuperou em vez de reinventá-las:

- **Transferência entre moedas informa os dois valores, nunca uma taxa.** O caso de uso é
  explícito: *"the rate is a quotient of the two ends and is derived"*, e a taxa é arquivada
  depois da gravação. Uma tool que aceitasse taxa criaria um segundo dono para esse número.
- **Orçamento declara a sua moeda**, sem padrão — deliberadamente, diz o próprio modelo. Não é
  sempre a base, e a tool não pode assumir.
- **Confirmar recorrência recebe a data**, usada tanto no lançamento quanto no registro da
  ocorrência. Não existe "hoje" implícito, e a tool não inventa um.
- **Compra parcelada gera todas as parcelas numa operação**, devolvendo o conjunto de
  lançamentos. Não é uma chamada por parcela.

E duas confirmações que fecham lacunas apontadas nas revisões: lançar em fatura fechada ou paga
**já é recusado** pelo domínio, com erros nomeados — logo os códigos de recusa que a superfície
enumera já existem; e ajuste de fatura e ajuste de conta são o **mesmo** mecanismo, distintos
apenas por onde a dimensão pousa, não por natureza.

## Risks / Trade-offs

- **A `api` incha.** Mitigação: a superfície de tools é deliberada (D4), e cada promoção é
  revisada como contrato público. Aceito conscientemente: é o preço de não inventar
  privilégio de dependência.
- **Loopback não é fronteira de segurança.** Qualquer processo do usuário alcança a porta.
  Mitigação: token obrigatório, revogável, e o `mcp.json` com permissão restrita ao dono.
- **Um agente escreve rápido e em lote.** Um erro de prompt vira quarenta lançamentos.
  Mitigação nesta mudança: escrita é opt-in separado, e tudo fica no registro com os ids do
  que tocou. Não mitigado: reversão em lote (fora de escopo, e honestamente sinalizado).
- **Dependências novas de rede no app.** Um servidor HTTP embarcado num app financeiro
  desktop é superfície nova mesmo desligado. Mitigação: confinado a `:app:mcp`, sem socket
  aberto enquanto o toggle estiver desligado, `Origin` validado e token obrigatório quando ligado.
- **Nasce uma revisão atrás (D12).** O alvo é a `2025-11-25` porque é o que o SDK fala; a
  `2026-07-28` já é a vigente. A dívida é datada e o gatilho é objetivo — o SDK alcançar a
  revisão nova —, e o desenho evita acumular o que a migração desfaria. Não mitigado: se o SDK
  demorar, o servidor envelhece junto, e clientes que abandonarem a revisão antiga deixam de
  conectar.
- **O volume das respostas não tem número.** A forma exigida aqui — coleção por moeda que não
  colapsa, identificador junto do nome em todo objeto aninhado, proveniência de taxa, eco de
  todo default — é individualmente justificada e no conjunto produz respostas grandes. O limite
  existe como requisito, e o número precisa ser medido: a listagem de tools serializada e uma
  página típica de lançamentos são critério de aceitação, não detalhe.
- **O Compose fica no classpath** de qualquer processo que agregue `appModules`, por causa
  dos `viewModel {}` declarados nos módulos dos `impl`. Inerte hoje; é dívida conhecida para
  o dia do processo headless.
