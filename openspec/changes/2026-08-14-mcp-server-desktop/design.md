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

E o mecanismo mudou de forma que o desenho precisa absorver: na revisão vigente o servidor não
inicia requisição alguma. Ele devolve um resultado de "preciso de entrada", e o **cliente
reexecuta a requisição original** com a resposta. Ou seja, **a chamada de tool roda duas vezes**
— o que promove a chave de idempotência de conveniência a pré-requisito de qualquer confirmação,
e é mais um motivo para ela ser ligada ao resumo dos argumentos (D10). O estado que atravessa as
duas execuções é entrada controlada pelo cliente: se guardasse "os trinta itens aprovados", seria
uma autorização de escrita no razão emitida por quem chamou. Ele fica fora do escopo desta
entrega, junto com a confirmação que dependeria dele.

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

### D12 — A revisão alvo é a `2026-07-28`, e ela é sem estado

A primeira versão desta proposta foi escrita contra um MCP que não existe mais. A revisão
vigente removeu o handshake `initialize`, removeu as sessões de protocolo, tornou
`server/discover` obrigatório, substituiu as requisições iniciadas pelo servidor por um padrão
em que o cliente reexecuta a chamada, removeu `ping` e a configuração de log por método, removeu
a retomada de stream, e depreciou Roots, Sampling e Logging.

O que isso obriga a mudar aqui, além do já dito em D2 e D9:

- **Identidade do cliente não vem de handshake.** Ela chega declarada a cada requisição, é
  recomendada e não obrigatória, e é autodeclarada — não autenticada. A auditoria precisa aceitar
  ausência e apresentar a etiqueta como afirmação, não como fato. O que autentica é o token, e
  ele é um só para todos os clientes: se distinguir chamadores vier a importar, o caminho é token
  por cliente, não confiar no campo.
- **Nada de estado acumulado entre chamadas.** Onde estado for necessário, ele é um identificador
  emitido pelo servidor e passado como argumento comum.
- **Headers são conferidos contra o corpo.** Versão de protocolo, método e nome viajam também em
  header, e divergência é recusa — a defesa contra um componente decidir por um valor enquanto o
  servidor executa por outro.
- **Fechar o stream é cancelar.** Isso não é polimento: define o que acontece com um lote
  interrompido no meio, e sem resposta o razão fica num estado que ninguém consegue nomear. A
  resposta escolhida é a chave de idempotência — repetir conclui o que faltou sem duplicar o que
  entrou.

**Sobre autorização.** A especificação de autorização do MCP é opcional, e conformá-la é
recomendado para transportes HTTP; dentro dela, o servidor seria um resource server OAuth 2.1
com metadados de recurso protegido. Um token de portador estático para um servidor loopback de
usuário único é desproporcionalmente mais simples, e é a escolha. O que muda é que ela passa a
ser **registrada como desvio**, com o mínimo que torna a falha legível: `401` com o desafio de
autorização apontando os metadados do recurso, em vez de uma recusa opaca.

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
- **O SDK Kotlin de MCP pode não falar a revisão alvo.** A documentação pública do SDK não
  declara qual revisão implementa, e a `2026-07-28` é uma quebra grande (sem `initialize`, sem
  sessão, `server/discover`, o padrão de reexecução). Se o SDK ainda for da era com handshake,
  isto deixa de ser detalhe de implementação: ou se implementa o transporte à mão, ou a entrega
  fala uma revisão anterior — e aí boa parte desta spec muda. **Verificar antes de fixar a
  dependência no `libs.versions.toml`.**
- **O volume das respostas não tem número.** A forma exigida aqui — coleção por moeda que não
  colapsa, identificador junto do nome em todo objeto aninhado, proveniência de taxa, eco de
  todo default — é individualmente justificada e no conjunto produz respostas grandes. O limite
  existe como requisito, e o número precisa ser medido: a listagem de tools serializada e uma
  página típica de lançamentos são critério de aceitação, não detalhe.
- **O Compose fica no classpath** de qualquer processo que agregue `appModules`, por causa
  dos `viewModel {}` declarados nos módulos dos `impl`. Inerte hoje; é dívida conhecida para
  o dia do processo headless.
