# Notas de versão — Finsight

Histórico de funcionalidades e correções, versão a versão, do primeiro commit até o que
está no `main` hoje.

O projeto **não usa tags git**: a versão é declarada em `app/android/build.gradle.kts`
(`versionName` / `versionCode`), em `iosApp/project.yml` (`MARKETING_VERSION`) e em
`app/desktop/build.gradle.kts` (`packageVersion`). Cada seção abaixo cobre os commits
alcançáveis a partir do commit que subiu a versão e não alcançáveis a partir do anterior —
é isso que decide em qual release uma mudança saiu, e não a data em que ela foi escrita.
Uma versão ainda em preparação separa o que entrou por cada release candidate; quando
ela sai, as seções se consolidam.

Estado atual: **1.10.0** em produção, com um ciclo aberto depois dela e ainda sem número
declarado.

---

## Sem versão — em preparação

**Nenhum número foi subido ainda**: `versionCode` 34 e `versionName` 1.10.0 continuam nos três
lugares onde a versão vive — `app/android/build.gradle.kts:20-21`, `iosApp/project.yml:22-23` e
`app/desktop/build.gradle.kts:107`. Esquema do banco: **14 → 15**, uma migração — a menor do
projeto: um `CREATE TABLE`, um índice, e nenhuma instrução que leia ou escreva o que já existia.
São os **277 commits** alcançáveis a partir de `feature/local-mcp-server` — que já carrega o `main`
mesclado — e não a partir de `16559741e`, o commit que subiu 1.10.0. Escritos entre 15/08/2026 e
03/09/2026, porque os ramos do servidor MCP e do backup local foram abertos durante o ciclo de
1.10.0 e fechados depois dele.

O ciclo em que o acervo passou a caber num arquivo, o app passou a guardar cópias dele sozinho, e
um agente passou a ler e escrever no razão pela mesma porta que a tela.

### O servidor MCP

- **Um servidor MCP dentro do app**, e não um segundo programa. Ele sobe quando o app abre, se o
  usuário já o ligou — a escolha é feita uma vez e toda abertura seguinte a honra —, e a porta é
  devolvida antes de o processo sair, para que uma reabertura não corra contra o socket que ela
  mesma deixou. Viver no processo do app é o que faz uma escrita do agente acordar o `Flow` que uma
  tela aberta está coletando: o que o agente lança aparece na tela, sem que nada seja recarregado.
- **Um executável, dois modos — e o agente alcança as finanças com o app fechado.** Lançado com
  `--mcp`, o mesmo programa que abre a janela não abre janela nenhuma: fala o protocolo pela entrada
  e pela saída padrão do processo que o cliente criou, e termina quando o cliente fecha a conversa.
  Não há segundo instalável, serviço em segundo plano nem item de login — nada fica residente, então
  "sobreviver a fechar o app" e "sobreviver a um reboot" são verdade por não haver o que sobreviver,
  e o que a seção manda o cliente lançar é o executável desta instalação. É também o que faz um
  cliente que só fala stdio — o Claude Desktop entre eles — conectar sem adaptador de terceiros.
- **Há um dono do banco por vez, e enquanto a janela está aberta o dono é ela.** Com o app aberto, o
  processo que o cliente lançou não escreve: ele encaminha a chamada para a janela, que é quem
  executa — e é isso que mantém a escrita do agente aparecendo na tela sem recarregar nada. Com o
  app fechado, ele executa sozinho, no mesmo banco, pelas mesmas ferramentas e com o mesmo registro
  de atividade. A troca de dono acontece no meio de uma conversa sem que o cliente perceba: quem
  responde muda, a sessão não. Quem impõe a exclusão é o sistema operacional, e não um acordo entre
  os dois processos, porque a janela já está coletando `Flow`s antes de haver porta que sondar.
- **O interruptor continua sendo do app, também com ele fechado.** O processo lançado pelo cliente lê
  a escolha gravada e a relê a cada pedido, de modo que ligar ou desligar o servidor alcança quem já
  está no meio de uma conversa. Desligado, ele ainda fala o protocolo — mas não anuncia ferramenta
  alguma e recusa qualquer chamada dizendo que o servidor está desligado nas configurações do app.
  Um processo que simplesmente morresse seria lido como um app quebrado, e não como um app que o
  dono desligou.
- **Só no desktop, e pelo eixo de plataforma.** Um servidor local é alcançado por um cliente na
  mesma máquina, e Android e iOS não têm nem esse cliente, nem um processo que o usuário deixe
  escutando, nem um executável que um cliente possa lançar — então a porta de entrada não é
  oferecida lá, e o controlador resolve para o que nunca afirma estar no ar. O eixo de plataforma,
  que até aqui só nomeava o que **não** funciona no desktop, passou a ter as duas direções, com o
  requisito escrito nos dois sentidos.
- **O perímetro é esta máquina, em três camadas.** O socket é ligado a `127.0.0.1` — o endereço, e
  nunca `localhost`, que resolveria pela configuração da máquina e poderia pôr o servidor numa
  interface que ninguém pediu. Todo request apresenta o token, conferido antes do roteamento e
  portanto antes de existir transporte, sessão ou ferramenta. E `Host` e `Origin` são validados
  contra loopback, que é a defesa contra o ataque que um servidor local de fato sofre: uma página
  aberta no navegador do próprio usuário alcançando `127.0.0.1`, direto ou por DNS rebinding.
- **Uma falha ao subir é dita.** "No ar" só é publicado depois de o socket estar ligado, e um bind
  que falha vira um estado de falha que carrega a porta — nunca uma mudança silenciosa para uma
  porta livre, que deixaria um cliente configurado apontando para o nada enquanto o app relatava
  sucesso. Porta ocupada é distinguida do resto, porque é a única falha sobre a qual o usuário pode
  agir, e a saída é a mesma linha do endereço: trocar a porta reata o servidor ali mesmo.
- **A porta padrão é 8477**, escolhida fora das faixas que as ferramentas de desenvolvimento
  disputam e fora da faixa efêmera que o sistema distribui sozinho. O token são 256 bits sorteados,
  cunhado na primeira vez que existe servidor a que apresentá-lo — resolver o controlador num teste,
  ou numa plataforma que nunca escuta, não deixa segredo nenhum na máquina — e guardado em claro de
  propósito: o alcance dele é o loopback da máquina em que está escrito, e quem lê o arquivo já lê
  o banco, que também não é cifrado.
- **A seção ensina a conectar, e a ordem dela é requisito.** Com o servidor desligado existem apenas
  o que ele é e o interruptor: endereço, token, permissões e instruções só significam alguma coisa
  depois que há a que se conectar, e ligá-lo não pode ser precedido de nenhuma outra decisão. Ligado,
  ela mostra **o comando**: o caminho absoluto deste executável com `--mcp`, no bloco `command` +
  `args` que a maior parte dos clientes cola e também numa linha só, para quem configura por linha
  de comando — dito como convenção e não como contrato, porque o servidor fala o protocolo e cada
  cliente guarda esses valores onde quiser —, e a frase de que ele funciona com o app aberto ou
  fechado. O caminho entra no bloco como texto JSON: o executável do Windows mora atrás de
  contrabarras, e copiado como está faria o cliente recusar o arquivo inteiro em vez da linha. O
  endereço e o token, que só respondem com a janela aberta, ficam recolhidos sob "avançado", para o
  cliente que prefere uma `url` — e desdobrá-los não revela o token: o bloco os esconde sob a mesma
  máscara da linha acima dele e copia o segredo inteiro, porque uma captura de tela não pode revelar
  o que a máscara logo acima protege e configurar um cliente pede o token de verdade. Quantos
  clientes estão conectados **agora** e o botão que os desconecta sem baixar o servidor continuam à
  vista.
- A seção **é um grafo dentro do de Ajustes**, como o backup: estar sob Ajustes e ser alcançada por
  Ajustes passam a ser o mesmo fato, que é o que mantém Ajustes selecionado na cromagem enquanto ela
  e o histórico de atividade estão abertos.

### A superfície de ferramentas

- **58 ferramentas em quatro famílias, que são as quatro partes de uma tela** com um agente no lugar
  de quem lê: as figuras do topo (perguntas, 10), a lista abaixo delas (catálogo, 10), o formulário
  (registro, 23) e os botões (operações, 15).
- **Corrigir uma transferência e corrigir um pagamento parcial de fatura**, que a pessoa faz na tela
  e o agente não alcançava. As duas reescrevem as pernas da operação e **preservam a identidade
  dela** — não é apagar e registrar outra —, consultam as mesmas regras que o registro consulta, e
  o que a chamada não nomeia é carregado da operação como ela está no razão. Ficam no eixo de
  **operar**, e não no de registrar: reescrever duas pernas monetárias move dinheiro tanto quanto
  escrevê-las da primeira vez. Com elas, a recusa de `update_transaction` sobre uma transferência ou
  um pagamento passa a nomear a ferramenta que os corrige, em vez de terminar em não.
- **Nenhuma ferramenta decide regra de domínio.** O servidor é uma camada de apresentação — a mesma
  regra que a UI já obedecia, aplicada a uma segunda superfície —, então ele compõe leituras,
  resolve id em nome, formata, pagina e ordena; e consome os donos que já existiam para o que
  *decide*: o rótulo derivado, a perspectiva, a perna que denomina a figura, o sinal por tipo de
  conta, o redutor único da consolidação. Duas derivações da mesma escolha divergiriam sem que nada
  falhasse, e a divergência entre uma tela e um agente é a mais difícil de perceber: ninguém as olha
  lado a lado.
- **Nenhuma ferramenta soma o que o razão já sabe somar.** Todo agregado vem de uma leitura do
  razão, nunca de uma soma sobre os itens devolvidos — um agente não rola a tela para conferir, e
  um total que pudesse discordar do razão seria justamente o número que ele repetiria. Onde o razão
  não tem agregado para o corte pedido, o payload **declara o descompasso** em vez de escondê-lo:
  diz quais argumentos os totais refletem e de qual leitura vieram.
- **Toda figura declara a sua moeda.** Um payload com um número e sem moeda é o único que esta
  superfície nunca produz: o agente somaria reais com dólares na frase seguinte e nada discordaria
  dele. Toda figura carrega o consolidado, o detalhe por moeda exato como o razão respondeu e a data
  da taxa aplicada — e quando o acervo de taxas não alcança alguma parte, ela diz o que ficou de
  fora em vez de escolher um número.
- **A superfície é fechada por decisão**, e as duas metades são sustentadas por teste: uma
  ferramenta registrada sem estar declarada entrou sem decisão, e uma declarada sem estar registrada
  sumiu sem ninguém notar. A outra metade é a lista do que **não** é alcançado, cada linha com o
  motivo e com o grau — o que um requisito proíbe oferecer, e o que simplesmente não foi alcançado.
  Sem ela, uma capacidade esquecida e uma capacidade recusada são indistinguíveis de fora.
- **Três coisas são proibidas por requisito**, todas pelo mesmo motivo: escrever uma taxa de câmbio
  e o catálogo de moedas, trocar a moeda base, e administrar o próprio servidor. As duas primeiras
  reescrevem em silêncio toda figura consolidada do app — meses fechados inclusive — sem produzir
  lançamento que denuncie; a terceira porque um agente que amplia as próprias permissões não tem
  permissões.
- **Uma recusa nomeia a saída.** Uma recusa que só diz não ensina o agente a inventar a volta: o
  caso que desenhou esta regra é real — impedido de apagar um lançamento, um agente cogitou zerar o
  valor pela edição para "neutralizá-lo", o que teria deixado uma linha de valor zero em toda
  listagem e toda contagem. Então a recusa carrega o motivo nas palavras do próprio domínio, e onde
  o domínio permite outra coisa no lugar do que foi pedido, carrega o **nome** dela — decidido pelo
  mesmo dono que a tela consulta, para que o agente receba exatamente o que o usuário receberia.
- **O id passou a ser a forma canônica de um use case.** Antes os dois padrões coexistiam sem
  princípio, e um use case chegava a receber os dois na mesma assinatura. Agora a forma por id
  carrega a implementação e resolve a identidade no instante da execução, e a forma que recebe o
  agregado delega numa linha. Não é só simetria: um agregado carregado por uma tela e usado numa
  ação minutos depois é uma leitura vencida, e com uma segunda porta de escrita no mesmo processo,
  reler no instante da ação virou correção. Onde há lote, a forma é plural — recebe as identidades e
  devolve o resultado indexado, porque um laço sobre uma lista produzia N+1 consultas.

### As permissões

- **Quatro eixos independentes**, e não graus de um: ler, registrar e editar, apagar, e operar.
  Apagar tem eixo próprio porque remover não é uma edição intensa — conceder "registrar e editar"
  sem ele deixa um agente que cria e altera e **não** remove, e uma remoção pendurada no eixo de
  registro removeria sob uma permissão que ninguém pediu. Operar move dinheiro entre contas, o que
  registrar não faz.
- **A permissão decide quais ferramentas existem.** Não é um `if` no começo do corpo: uma capacidade
  não concedida não é anunciada, então o agente não tenta, não erra e não gasta contexto com ela. A
  recusa na execução continua valendo — o anúncio é consequência da permissão, não a única aplicação
  dela.
- **Mexer no interruptor alcança quem já está conectado.** Toda sessão aberta é avisada de que a
  lista mudou, e a lista é recalculada no instante do pedido: quem concede uma capacidade para
  desbloquear o agente com quem está falando é obedecido ali, sem precisar fazê-lo reconectar.
- **O que está retido é dito, mesmo sem ser oferecido.** Filtrar a lista faz o que promete e produz
  um efeito que o filtro sozinho não conserta: para quem conhece o app apenas pela lista, *retido* e
  *inexistente* são a mesma coisa. Numa simulação com um agente real, sobre um servidor com o eixo
  de apagar desligado, o pedido "apaga o último lançamento" produziu *"não existe ferramenta de
  exclusão neste servidor"* — falso, dito com confiança ao dono do app, e bloqueando justamente a
  ação que resolveria. Então o handshake da sessão nomeia as capacidades concedidas e as retidas, e
  diz que a retenção é escolha do usuário e onde revertê-la; e uma ferramenta chamada pelo nome sem
  permissão é recusada dizendo que a operação **existe e não está autorizada**, com a mesma
  indicação. O que se nomeia é a capacidade, nunca as ferramentas — declarar os nomes seria uma
  segunda lista chegando por outro canal, devolvendo o contexto que a filtragem economizou.
- **Um servidor ligado pela primeira vez concede leitura, e só.** Escrever espera um segundo ato
  explícito. O padrão é por eixo e não por conjunto, de modo que um quinto eixo, se um dia existir,
  nasce retido em toda instalação que já existe em vez de concedido por nada ter sido escrito para
  ele.
- Cada linha da tela **diz quantas ferramentas o interruptor entrega** — concedido, quantas dá;
  retido, quantas está segurando. O número vem do único lugar onde a superfície é contada, o mesmo
  que o socket anuncia: o que o usuário lê não tem como divergir do que o agente recebe.

### O registro de atividade

- **Toda escrita, operação e recusa de um agente fica registrada**, e o registro sobrevive ao
  fechamento do app. É o único lugar do app onde a **autoria** de uma escrita aparece: a reatividade
  entrega o resultado — a transação simplesmente surge na tela — e não diz de onde ele veio, de modo
  que um lançamento indevido feito de fora seria indistinguível de um que o usuário esqueceu de ter
  feito.
- **A porta é uma só.** Toda chamada passa pelo mesmo ponto, e é dele que o registro é escrito — uma
  chamada que cada ferramenta tivesse de lembrar de fazer sumiria do registro na primeira que
  esquecesse, ainda mudando o razão. Uma ferramenta que estoura em vez de recusar também deixa
  entrada: a pergunta do usuário é por que o agente disse que não conseguiu, e o silêncio é a única
  resposta que nunca ajuda.
- **Uma consulta nunca vira entrada.** Um agente faz dezenas de perguntas para responder a uma, e
  listá-las afogaria justamente os poucos atos que mudaram alguma coisa. A decisão é do efeito
  declarado da ferramenta e não do resultado, então uma leitura que falha continua sendo leitura.
- **Uma repetição é um segundo ato.** Nada é deduplicado, porque a duplicação que a ausência de
  idempotência permite é exatamente o que o registro existe para expor: os dois lançamentos
  idênticos aparecem lado a lado, com os seus horários, em vez de esperarem ser notados no meio de
  um extrato.
- **A entrada diz o que era verdade quando aconteceu, e leva a onde a coisa está hoje.** O resumo
  nomeia a conta, a categoria ou o cartão como se chamavam naquele instante e nunca é reescrito —
  atualizá-lo numa renomeação falsificaria um depoimento sobre o passado. A referência é a metade
  viva: dela se alcança o lançamento, e ela deliberadamente não é chave estrangeira, porque o
  registro jamais pode impedir que um lançamento seja apagado. Uma referência que não existe mais
  continua listada, e a tela diz que o alvo se foi.
- **Retenção declarada, e limpeza que não desfaz nada**: 180 dias ou 5.000 entradas, aplicadas na
  escrita — que é o que faz o registro crescer — e também na leitura, por causa do app que fica
  meses fechado: sem nada escrevendo, o teto de idade deixaria de valer sem que nada dissesse, e a
  pessoa leria atos que a política declara já removidos. A seção mostra os 5 últimos atos de relance e o histórico
  completo fica a um passo, com o botão que o esvazia — e esvaziá-lo remove o registro do que foi
  feito e **nada do que foi feito**.

### Backup local

- **Exportar e restaurar o acervo num arquivo**, com o mesmo arquivo servindo nas três plataformas.
  O backup é o banco inteiro — plano de contas, transações, entries, dimensões, categorias, cartões,
  faturas, parcelamentos, orçamentos, recorrentes, moedas e taxas — e deliberadamente não leva as
  preferências que vivem fora do banco: moeda base, layout do dashboard, estado de sincronização de
  taxas, estado da janela. Restaurar numa instalação de moeda base diferente troca o acervo e deixa
  aquela preferência onde estava.
- **Um arquivo só altera o acervo depois de aprovado por inteiro**: ser um SQLite legível e
  íntegro, declarar um schema que este app saiba abrir, sobreviver à cadeia de migrações e satisfazer
  os invariantes do razão — `Σ entries = 0` por `(transação, moeda)`, nenhuma entry apontando para
  dimensão inexistente, nenhuma violação de chave estrangeira. A verificação corre numa conexão à
  parte, sobre o candidato: a corrupção de um arquivo escolhido não tem como ser atribuída ao banco
  em uso. Um arquivo de uma versão mais nova do app é recusado dizendo que o app é que está velho, e
  não que o arquivo é inválido.
- **A confirmação identifica o arquivo antes de perguntar**: data de criação, plataforma de origem,
  versão do app que o gerou e as contagens do acervo. A pergunta não é feita antes da aprovação —
  perguntar sobre um arquivo que ainda pode ser recusado transfere ao usuário uma decisão que o app
  ainda não sustenta.
- **Restaurar não reinicia o app.** A substituição roda na conexão de escrita do próprio Room, numa
  transação: `ATTACH` no arquivo aprovado, cada tabela esvaziada e refeita, `DETACH` no `finally`.
  Os flows que já estavam coletando reemitem sozinhos, e nada é fechado, reconstruído ou invalidado —
  o que também é o que faz o caminho valer no iOS, onde reiniciar o processo por conta própria não é
  admissível.
- **O app parou de delegar backup à plataforma.** No Android, `allowBackup="false"` mais os dois
  formatos de regra — `backup_rules.xml` para a API 24–30 e `data_extraction_rules.xml` para a 31+,
  este com `<cloud-backup>` e `<device-transfer>` escritos por extenso, porque uma seção ausente não
  é uma seção desligada. No iOS, o arquivo do banco é marcado com `NSURLIsExcludedFromBackupKey`. A
  consequência é dita na tela em vez de descoberta na troca de aparelho: recuperar os dados em outro
  aparelho passou a depender de um arquivo que esteja fora dele.
- **O êxito é dito, e o não-êxito não.** Uma exportação que salvou diz que salvou, e uma restauração
  diz que concluiu — nenhuma das duas deixa prova na tela. Fechar o seletor sem escolher destino não
  diz nada: quem não salvou nada não falhou em nada, e "backup concluído" ali afirmaria um arquivo
  que não existe.
- A porta fica em **Ajustes**, e as duas telas ficam sob o grafo da própria feature, pendurado no de
  Ajustes: backup não é uma seção do app, é uma porta dentro de uma — e é isso que mantém Ajustes
  selecionado na cromagem enquanto elas estão abertas.

### O cofre

- **O cofre nasce desligado**, em toda instalação, e enquanto estiver assim nenhum gatilho escreve
  nada. Ligá-lo põe os três gatilhos em vigor de uma vez, com valores que ninguém precisa escolher;
  o periódico e o preventivo podem ser desligados depois, separadamente.
- **A oferta chega onde o risco está.** A primeira ação destrutiva traz a oferta junto da
  confirmação, e aceitar ali liga o cofre inteiro sem passar por tela de ajustes. Recusar não retira
  a oferta — muda o tom dela: a que ninguém recusou chega marcada, e a recusada chega desmarcada e
  diz que foi.
- **Três gatilhos**: a abertura depois de um prazo (padrão de 3 dias, entre 1, 3, 7 e 15), a ação
  destrutiva prestes a acontecer, e a atualização que vai reescrever o banco. A tela não promete "a
  cada N dias" — nenhuma plataforma suportada garante execução fora do uso do app —, e sim que a
  cópia acontece quando o app é aberto.
- **Uma regra só decide se há o que copiar**: se alguma **linha nova** foi criada desde a última
  cópia. Não é frugalidade — uma exclusão nunca torna a cópia anterior insuficiente, porque ela é
  justamente a mais completa das duas. Vinte exclusões seguidas produzem uma cópia; abrir o app por
  dias sem lançar nada não produz nenhuma; renomear uma categoria também não. Editar um lançamento
  conta, porque reescrever as partidas dele é criar outras.
- **Quais ações são destrutivas é do domínio, por classificação.** O inventário é completo de
  propósito: uma ação não coberta está escrita como não coberta, com o motivo na sua classe — as
  exclusões que o domínio já recusa quando há histórico (conta, cartão, categoria, orçamento,
  recorrente) não disparam nada, porque nada de digitado vai junto. A tela decide *se* o gatilho
  vale, nunca *quais* ações ele cobre, e uma exclusão acrescentada ao app nasce coberta pela classe
  que receber.
- **Antes de uma migração**, com o cofre ligado, o banco é copiado antes de a cadeia rodar — numa
  conexão somente-leitura e por `VACUUM INTO`, porque abrir para perguntar se há migração pendente já
  teria migrado. Essa cópia vai para o armazenamento do próprio app, mesmo com uma pasta escolhida,
  não entra na contagem da retenção e só é substituída pela cópia da migração seguinte. Ela aparece
  no histórico de qualquer jeito: é justamente ela que se procura quando um número deixa de bater
  depois de uma atualização.
- **Dois destinos, e a tela diz o que cada um não cobre.** O armazenamento do próprio app, que é
  onde o cofre começa, ou uma pasta apontada pelo usuário — nas três plataformas. E são **três**
  frases, não duas: no desktop o armazenamento do app é uma pasta no diretório do usuário, que
  desinstalação nenhuma esvazia, então ali a tela não afirma que desinstalar leva as cópias junto.
- **Uma captura só é dada como boa depois de a cópia ser lida de volta**, pelas mesmas verificações
  do fluxo de restauração — um destino que aceita o arquivo prova que existe um arquivo com aquele
  nome, não que o conteúdo dele é um banco deste app. Uma cópia provadamente ruim não move o instante
  da última captura bem-sucedida e não remove nenhuma cópia existente; uma verificação que **não pôde
  correr** não é reprovação, porque acusar falsamente uma cópia possivelmente boa, no instante em que
  se informa que o backup deu certo, é pior que a checagem que não aconteceu.
- **O histórico tem tela própria**, lida do destino no momento em que ela abre — e não de uma tabela,
  que viajaria dentro do backup e voltaria no tempo com a restauração. Cada cópia diz quando foi
  feita, quanto ocupa e o que a distingue; a anterior a uma migração se identifica como tal. Dali uma
  cópia é restaurada, removida, ou entregue a um destino escolhido na hora, pelo mesmo caminho da
  exportação manual — o que tira do aparelho uma cópia que o cofre guardou, sem capturar outra.
- **Duas portas fora dos gatilhos**: capturar agora, e trazer para o destino um arquivo que a pessoa
  tenha em outro lugar. As duas ficam à vista em todos os estados da tela — inclusive sem nenhuma
  cópia e com o destino ilegível, que é justamente quando alguém as procura — e nenhuma escreve nada
  com o cofre desligado. Capturar agora não é recusada porque nada mudou desde a última cópia: num
  controle que a pessoa acabou de tocar, essa pré-condição produziria um botão que não faz nada.
- **Retenção configurável** — 5, 10, 20 (o padrão) ou não remover nada —, a mesma nos dois destinos,
  e aplicada só **depois** de uma captura bem-sucedida, de modo que a remoção esteja sempre ancorada
  na existência de uma cópia nova e o destino nunca fique vazio por efeito dela.
- **A pasta é apontada uma vez e reencontrada.** O mesmo apontamento serve para configurar, para
  reconectar quando o acesso se perdeu e para reencontrar um histórico numa instalação nova — apontar
  uma pasta que já tem cópias faz o histórico dela aparecer inteiro. O acesso é verificado na
  abertura, e não só na hora de escrever; perdido, o app diz e oferece as duas saídas, e enquanto a
  resposta não vem continua capturando no armazenamento próprio, declarando que é provisório.
- **Trocar de pasta copia, e nunca depende da antiga.** A migração leva as cópias mais recentes que a
  retenção comporta, não remove nada da pasta anterior — uma falha no meio deixa arquivos nos dois
  lugares, nunca em nenhum — e funciona com a pasta anterior inacessível.
- **A tela diz sempre quando foi o último backup que deu certo**, e em que destino: um cofre pode
  parar sem defeito e sem ação do usuário, e esse instante é o único meio pelo qual a pessoa
  descobre. O aviso de atraso só aparece com a verificação na abertura ligada — desligada, não há
  intervalo em vigor contra o qual atrasar.

### Um botão de ação para o app inteiro

- **De dez botões para um.** Um vivia na casca e nove no `Scaffold` da sua tela — contas, cartões,
  categorias, orçamentos, recorrentes, parcelamentos, moedas, taxas e suporte —, e em janela larga
  dois botões idênticos ficavam na mesma janela: a rail carregava o da casca no `header` enquanto a
  tela desenhava o seu. Agora a casca é dona do único botão do app: a tela publica as ações que
  oferece **enquanto está em foco**, pelo canal de chrome, e a casca as desenha sem nomear a feature
  que as originou.
- **A forma é uma só, e a lista decide o que acioná-lo faz.** Nenhuma ação, e o botão não aparece;
  uma, e acioná-lo executa a ação e ele carrega a identidade dela; duas ou mais, e ele abre um menu
  que lista todas, a primeira inclusive, com rótulo visível. A quantidade não altera a silhueta. Três
  telas têm menu — contas, com três ações, cartões e categorias, com duas cada —, e nelas a primeira
  ação passou a custar dois toques.
- **A ação universal.** Uma tela que não publica nada recebe *registrar transação* em vez de ficar
  sem botão, o que estendeu ao celular o alcance que o desktop já tinha. Ficar sem botão virou um ato
  explícito na configuração de chrome, e deixou de ser a consequência de não ter ação própria — ou de
  o destino não ser uma aba primária, regra que agora vale só para a bottom bar.
- **Uma tela que ancora o próprio controle no rodapé suprime o botão** — o envio de uma conversa, o
  "Gerar" do relatório. Não é aparência: a casca desenha por cima, e o toque ia para a superfície de
  cima, de modo que o usuário mirava o controle da tela e recebia a ação universal.
- **Onde o botão fica**: central e ancorado à bottom bar quando ela está visível, no canto do
  conteúdo quando não, e `header` da rail em janela larga — exatamente onde cada um dos dez já
  estava. O caminho entre as duas posições é uma figura só, `0` na barra e `1` no canto, e é isso que
  faz a volta refazer a ida ao contrário em vez de tomar outro caminho.
- **A chrome passou a poder não ser dita.** Uma tela que ainda está lendo publica `null` em vez de um
  palpite que a leitura desmente: com o palpite a chrome se move duas vezes, com o silêncio ela se
  move uma, quando a resposta chega.
- **Pré-seleção** nas ações que abrem o formulário de outra feature: a transação aberta do menu de
  cartões nasce com o cartão em evidência, e a do menu de contas com a conta em evidência — o foco do
  pager, e não o argumento de rota, que fica velho assim que o usuário desliza.

### Arquitetura

- **`:core:database` ganhou o mecanismo de snapshot**: capturar o próprio conteúdo num arquivo,
  carimbá-lo com a origem, verificar um candidato em isolamento e fazer o banco em uso passar a valer
  o conteúdo dele. Ele não conhece cofre nem tela — a promessa feita a quem digitou os lançamentos é
  da capacidade `local-backup`, e as cópias que o app guarda sozinho são da `automatic-backup`.
- **`:core:ledger` ganhou um prelúdio de remoção.** Uma remoção de transação se anuncia **antes** de
  as linhas saírem, e o silêncio é a versão que fala: quem não passa o argumento anuncia. Calar exige
  um valor com nome próprio e um `@OptIn` que o compilador cobra no ponto de chamada — porque uma
  cópia preventiva tirada depois registra o estado já mutilado.
- **`:feature:backup`**, api e impl, com o domínio do cofre e os serviços de arquivo de cada
  plataforma. Ajustes hospeda as suas telas vendo apenas o `api`: o registro é pedido ao entry
  point, e o `impl` do backup continua invisível para todo mundo menos a casca.
- **`:feature:mcp`**, api e impl, sob a mesma regra: o `api` declara o controlador do servidor, a
  sessão stdio, os eixos de permissão, o registro de atividade e o entry point; o `impl` guarda as
  telas, as 58 ferramentas e os dois transportes, e o `jvmMain` é onde o servidor de fato existe —
  nas outras plataformas o controlador e a sessão resolvem para o que nunca sobe nada. Ajustes
  hospeda a seção vendo apenas o `api`. A montagem de uma sessão — as ferramentas registradas, o
  filtro por eixo, o registro de atividade, as instruções do aperto de mão — não nomeia porta
  nenhuma, então é uma só e vale para os dois transportes.
- **`:core:database` ganhou a posse do banco**: um lock exclusivo do sistema operacional num arquivo
  ao lado do acervo, tomado pela janela antes de o grafo existir e pelo processo headless a cada
  chamada. Ele não depende do MCP nem da UI — é a resposta a "qual processo abre este arquivo", e é
  do kernel justamente porque um acordo entre processos teria um intervalo em que ninguém saberia
  responder.
- **Uma migração, 14 → 15**, a menor do projeto: o registro de atividade vira tabela. Um
  `CREATE TABLE`, um índice, e nenhuma instrução que leia ou escreva o que já existia — a tabela
  nasce vazia e só enche quando um agente age, então um aparelho que nunca liga o servidor carrega
  oito colunas vazias e mais nada. As colunas de referência não têm chave estrangeira, de propósito.
- **Sete testes estruturais**, no lugar de regras que nada mais tem como impor: nenhum `Scaffold` de
  feature declara `floatingActionButton`; toda chave de string existe nos dois idiomas — são 1.057
  em cada, 62 delas do servidor MCP; o backup mora sob Ajustes, com as duas telas dentro do próprio
  grafo; a seção MCP também, com o grafo dela construído dentro do de Ajustes a partir dos módulos
  que o app publica; a forma canônica de todo use case público é a por id, com as isenções nomeadas
  uma a uma e conferidas nos dois sentidos; nenhum ViewModel escreve direto num repositório, exceto
  os quatro que são preferência de exibição ou o acervo de taxas, listados com o motivo; e o SDK do
  servidor está na imagem que o usuário instala — lido do manifesto do `runtimeClasspath` da
  distribuição, e não do classpath do próprio teste, porque uma dependência declarada no lugar
  errado deixa o teste e a IDE verdes e só o app instalado sem servidor.
- Suíte E2E de **19 fluxos**, quatro deles novos e do backup — alcance da tela, o cofre da chave à
  primeira cópia, o gatilho periódico e o preventivo —, mais um subfluxo para o toque a mais que as
  três telas com menu passaram a exigir. O servidor MCP **não tem fluxo**: ele é desktop-only e a
  suíte dirige o app Android. Não houve rodada da suíte neste ciclo depois do último commit; o que
  está registrado são as rodadas de cada change, nas suas tarefas.
- `./gradlew jvmTest` verde: **2.452 testes, 0 falhas**, rodado neste estado da árvore com os
  resultados anteriores apagados, para que nenhuma tarefa fosse dada por atualizada sem executar.
- Passar não é cobrir, e o que **não** é verificado está escrito nominalmente em
  `docs/mcp-tool-surface.md`, com o motivo de cada limite: nenhum teste de ferramenta exercita a
  implementação de produção de um use case de escrita (a regra de dependência impede o `impl` do MCP
  de alcançar outro `impl`), ninguém conta consultas, a validação de `Host`/`Origin` não diz nada
  sobre um navegador de verdade, o teste de loopback usa esta máquina como substituta de uma
  segunda, e **nada do que se desenha na tela** é verificado — o projeto não tem infraestrutura de
  teste de Compose, então o estado e o ViewModel da seção têm teste e a renderização deles não.
- O backlog ganhou **57 entradas** no ciclo — as do backup e da varredura de cromagem, as da revisão
  adversarial do ramo do servidor, as que só a simulação contra o app rodando encontrou, as duas que
  esta própria passagem pelas notas levantou, e a que a correção das guardas de data revelou —, das
  quais **35** foram corrigidas e arquivadas dentro do próprio ciclo. Saiu de 65 abertos e 9
  arquivados, no commit que subiu 1.10.0, para **87 abertos e 44 arquivados**. Um bug anterior ao
  ciclo foi fechado nele, e só um: `adjust-balance-has-no-domain-guard-against-a-future-date`,
  registrado em 21/08 e fechado junto das duas guardas de data irmãs, que eram a mesma classe de
  defeito. As outras 34 nasceram e morreram aqui.

### Correções

- Cruzar 600dp ou 840dp cortava a cromagem de um quadro para o outro, em vez de animá-la: a largura
  da janela particionava a casca em ramos, e um ramo não anima — insere e remove. Estreitar era pior
  que cortar, porque o slot da bottom bar compunha pela primeira vez naquele quadro e se semeava a
  partir de um pai que ainda dizia que havia barra, de modo que uma barra inteira aparecia só para
  tocar a própria saída.
- A configuração de relatório escondia o botão de ação também em janela larga, onde ele é o `header`
  da rail e não há com o que colidir.
- O card de um problema no Suporte não acusava o toque dentro dos próprios limites: o clique estava
  no modifier do chamador, que o `Surface` encadeia antes do `clip`, então o ripple pintava os cantos
  quadrados — e um `Card` sem `onClick` não entrega interação alguma à própria elevação.
- Remover uma taxa de câmbio não tinha confirmação nenhuma — era a única exclusão de trabalho
  digitado no app sem uma.
- A exclusão de uma fatura chamava de "futura" uma fatura retroativa, justamente a que já carrega
  transações lançadas, e não dizia que elas vão junto; agora diz quantas, exceto quando não há
  nenhuma. A remoção também passou a ser uma transação só, em vez de uma por linha.
- Uma compra parcelada recusada pelo razão registrava a recusa no Crashlytics e parava, deixando a
  folha aberta e muda — enquanto a compra única idêntica, vinte e nove linhas abaixo, se explicava.
- `removeAllNaming` não tinha chamador nenhum, e chamá-la teria removido as taxas de uma moeda
  **fora** da transação que a exclusão abre para que as duas remoções aconteçam ou nenhuma.
- Três figuras que um driver E2E só conseguia afirmar como presentes, nunca como o número que dizem,
  porque a `testTag` estava no contêiner e não no nó que renderiza o texto.
- **Um pagamento de fatura era recusado depois de o dinheiro sair.** Pagar é duas escritas — o
  lançamento que tira o valor da conta e a fatura marcada como paga —, e o que recusava o pagamento
  estava sendo descoberto dentro da segunda. Um pagamento datado antes do fechamento voltava
  recusado com a conta já menor por ele, o saldo da fatura já quitado no razão e o status ainda
  dizendo "fechada, sem data de pagamento" — e não havia com o que devolver. A correção não foi
  reordenar as verificações, e sim dar um dono a elas: uma validação que as duas operações
  consultam, a primeira antes de escrever qualquer coisa e a segunda como guarda própria, para que a
  resposta antes e a resposta depois não possam divergir. Encontrado dirigindo o servidor de verdade
  contra o app de verdade; a tela alcança o mesmo use case pela mesma ordem, e só não tropeçava
  porque assume a data de hoje.
- **Um ciclo recorrente confirmado sob uma categoria da direção oposta era gravado invertido**, e a
  resposta dizia que deu certo: um template de despesa confirmado com uma categoria de receita
  tirava o dinheiro da conta e era lido de volta como receita — o mês passava a reportar uma entrada
  que não houve. A regra "uma categoria classifica uma direção só" já era imposta em cinco pontos, e
  todos os cinco montam um formulário; a confirmação de um ciclo é a única escrita do app que chega
  ao razão sem um, e foi esse recorte que a escondeu por quatro rodadas. A sheet de confirmação
  reoferece uma categoria incoerente quando ela já está selecionada, então a tela chegava lá também.
- **Um valor não positivo atravessava o formulário e chegava ao razão**, onde o sinal do tipo o
  virava do avesso — a despesa pedida aumentava o saldo e diminuía o gasto do mês. O domínio nunca
  tinha exigido um valor positivo; a omissão era invisível porque nenhuma tela tem campo capaz de
  expressar um negativo, e um chamador que não seja tela tem. Cinco portas levavam ali, e a regra
  agora está no domínio, em três pontos que fecham as cinco, em vez de repetida em cada uma.
- **Uma compra parcelada recusada abria as faturas antes de recusar.** O valor era conferido no
  último passo do caminho, depois de a primeira fatura ser resolvida e de uma ser aberta por mês
  faltante: nada entrava no razão e a estrutura de faturas ficava para trás, de uma compra que nunca
  aconteceu. A validação subiu para a primeira instrução, sem copiar a regra para dentro do
  parcelamento — a ordem era o defeito inteiro, e ela vale para qualquer recusa daquela validação,
  não só a do valor.
- **O interruptor tinha quatro aparências e um estado desligado invisível.** O padrão do Material
  pinta o thumb desmarcado em `outline`, que no esquema escuro do app é o mesmo valor do trilho — um
  eixo desligado parecia uma pílula vazia, e não um interruptor no não. Cada tela que notou
  consertou onde estava, uma delas com uma cor literal que deixou de seguir o tema. Agora existe
  `FinsightSwitch` no design system, com o marcado, o desmarcado e o desabilitado declarados — um
  interruptor indisponível também precisa dizer para que lado está —, e nenhuma cor literal
  sobreviveu.

Não estão listadas as correções que reparam código escrito neste mesmo ciclo e que build nenhuma
carregou: as entradas acima descrevem o comportamento que ficou.

---

## 1.10.0 — 29/08/2026

`versionCode` 34, depois de 31, 32 e 33 nos três release candidates do ciclo. Aberto em
08/08/2026 e fechado em 29/08/2026. Esquema do banco: **10 → 14**. 415 commits — a maior
versão do projeto.

A versão em que o app deixou de ser mono-moeda.

### Multimoeda e consolidação

- **Moeda por conta e por cartão.** Toda conta do plano de contas declara uma moeda, sem
  valor padrão e imutável. Uma conta sem moeda deixou de ser representável.
- **Intenção entre moedas completada no razão.** Uma transação que cruza moedas chega
  incompleta e a fronteira de escrita a completa, lançando o resíduo de cada moeda na conta
  de conversão daquela moeda. `CONVERSION` ganhou tipo próprio, para que `EQUITY` continue
  significando "ajuste".
- **Leitura por moeda.** Nenhuma leitura soma duas moedas num número só: toda leitura que
  pode cruzar contas responde `MoneyByCurrency`. Só as leituras de uma conta única
  continuam escalares.
- **Moeda base como preferência de exibição**, com um único redutor acima do razão. O
  `:core:ledger` não conhece taxa nem moeda base.
- **Marca de aproximação** com três severidades: uma figura diz quando passou por uma taxa,
  qual termo passou, e quando não há taxa que a sustente (`***` no lugar de um zero
  inventado). A marca chegou aos widgets do dashboard, aos cards de orçamento e à lista.
- **Arquivo local de taxas de câmbio**, datado por par, editável nos ajustes e agrupado por
  data. A taxa é dinheiro e lê pelo formatador de dinheiro do app.
- **Troca de moeda base**, dando a cada taxa o seu par — o arquivo passou a agrupar pela
  moeda contraparte.
- **Registro de moedas pelo usuário**: o app parou de embarcar uma lista pronta; o usuário
  registra e arquiva as moedas que usa. O seed nasce com a tabela, traz a moeda do
  dispositivo e toda moeda em que já existe conta.
- **Sincronização de taxas** a partir de uma fonte remota (Ktor), que escreve no mesmo
  arquivo local, limitada por par, e que passa a dever uma rodada quando a base muda.
- **Limite de orçamento denominado na criação**, e não derivado da base.
- **Rendimento de conta**: o que o dinheiro rendeu sozinho é um lançamento próprio,
  separado das demais receitas, com folha própria para escolher a conta em que cai.
- **Detalhe da transação** passou a dizer a taxa que a operação aplicou e a nomear a moeda
  de cada conta quando duas dividem a tela.
- **Relabel das contas legadas** por região, para que um usuário fora do Brasil não herde
  contas em BRL. É redenominação, não conversão: contas, lançamentos e orçamentos mudam de
  moeda na mesma transação, e `Σ = 0` por moeda continua valendo.

### Novidades

- **Faturas retroativas**: a fatura de qualquer mês pode ser criada na tela de faturas, e a
  fatura escolhida é quem posiciona a data — nunca o contrário. A tela avisa quando a data
  cai fora do período da fatura.
- **Pagamento de fatura unificado**: um único formulário que nomeia a fatura que paga —
  seletor de cartão, de fatura e de conta pagadora —, com o estado da fatura decidindo o
  modo: aberta ou retroativa aceita pagamento parcial; fechada aceita apenas a quitação
  total. A **fatura retroativa passou a ser pagável**, fechando a lacuna em que o app
  somava a dívida na previsão do mês e não oferecia caminho para pagá-la. A regra de quem
  aceita pagamento ganhou dono único no domínio, no lugar das quatro superfícies que
  reenumeravam status por conta própria, e o campo de valor passou a oferecer o teto em
  vez de pedir que ele fosse digitado.
- **Correção de pagamento parcial**: um pagamento parcial de fatura passou a ser corrigido
  no lugar, pelo mesmo formulário que o registra. O modo é fixo na correção — corrigir um
  parcial é reafirmar um parcial —, e o teto do valor passou a desconsiderar a própria
  operação: um parcial de R$ 300 numa fatura de R$ 800 pode ser corrigido para R$ 700, o
  que o devido corrente recusava. A quitação total continua história liquidada, sem
  correção e sem remoção.
- **Ajuste de saldo datado**: um único ajuste, com alvo numa data, no lugar dos três modos
  anteriores (saldo atual / final / inicial).
- **Detalhe da transação por pernas**: a operação é composta de um card por perna
  monetária, tingido pela direção da perna, com a seta entre os cards, a categoria como
  linha de contexto e o ícone da fachada à frente do nome de cada perna. Não há mais escolha
  de uma ponta — e o ajuste, que tinha um segundo detalhe só seu (`ViewAdjustmentModal`,
  com ViewModel, estado e testes próprios), passou a abrir este, nomeado pelo alvo que
  corrige.
- **Transferência corrigível**: uma transferência passou a ser corrigida no lugar, pelo
  mesmo formulário que a registrou, em vez de apagada e refeita. As duas pontas, o valor,
  o valor de destino e a data são corrigíveis, e a transferência entre moedas entra pela
  mesma porta. O portão de edição parou de contar pernas monetárias e passou a nomear
  rótulos — o pagamento de fatura, que ficava de fora pela contagem, passou a ficar por
  declaração própria. No razão, a reescrita de uma transação passou a aceitar a mesma
  lista de pernas que a criação sempre aceitou.
- **Título na transferência**: a transferência passou a dizer por que o dinheiro se moveu.
  Com o campo veio a cadeia de nomeação — título, depois categoria, depois forma — valendo
  em toda superfície que nomeia uma operação, e com dono único: o card da lista, o
  documento exportado e o modal deixaram de derivá-la cada um por sua conta. O literal de
  reserva `"Untitled"`, inglês num app pt/en, foi removido.
- **Recorrente sem redigitar**: uma transação pode nascer recorrente no próprio lançamento,
  e a confirmação do ciclo passou a editar título e categoria. Pular um ciclo agora pergunta
  antes.
- **Recorrentes lista ciclos, não modelos.** A tela passou a listar os ciclos do mês
  selecionado, em quatro seções com contagem própria — pendente, a lançar, lançado e ignorado
  —, e a partição ganhou dono único no domínio (`RecurringCycles`), consumido tanto por quem
  lista quanto por quem projeta. O corte entre *pendente* e *a lançar* passou a comparar
  datas, e não dias do mês: a comparação anterior só era correta enquanto o mês fosse o
  corrente, e o mês virou selecionável. A linha de um ciclo lançado é lida inteira do razão —
  figura, título, categoria e origem da transação —, nunca do modelo, porque confirmar um
  ciclo permite sobrescrever os cinco. As arquivadas saíram da lista, onde não há seção em que
  caibam, e ganharam tela própria; o recorte perdeu `ARCHIVED` e ficou só com o eixo de
  natureza.
- **Resumo do mês em Recorrentes.** Um card no topo separa fato de projeção: *lançado neste
  mês*, lido das ocorrências confirmadas pelas transações que elas apontam, e *ainda não
  lançado*, dos modelos sem ocorrência no mês. Cada metade se dobra, e a que não tem movimento
  abre dobrada — o rótulo permanece dos dois jeitos, porque o que se dobra é a figura, nunca o
  nome do que ela mede. O seletor de mês nasceu no card e passou a governar a tela inteira.
- **A linha de Recorrentes de ~180dp para 64dp.** A ficha de três blocos empilhados virou uma
  linha: à esquerda o que a recorrência é e de onde sai, à direita quanto e quando. Saíram a
  legenda "Valor mensal" e os dois badges; a direção do lançamento virou glifo com
  `contentDescription`. A altura medida no aparelho é de 64dp, contra os ~180dp da ficha
  anterior — cabe quase o triplo de linhas na mesma tela. Um modelo sem denominação passou a
  exibir `***` em vez de omitir a figura, e a altura da linha ficou constante em toda variante
  — a ausência do número passou a ser dita em voz alta, e não por ausência. A lista inteira,
  inclusive a seção de lançados, é desenhada por um componente só: antes eram dois, de alturas
  e colunas diferentes lado a lado. A linha lançada não trocou de fonte com isso — segue lendo
  do razão, e mostra a origem que a **transação** registrou, não a que o modelo nomeia.
- **Orçamentos sobrepostos**: uma categoria pode ser medida por quantos orçamentos o
  usuário quiser.
- **A linha de Orçamentos de ~232dp para ~62dp.** O progresso virou um anel em torno do ícone,
  e é isso que compra a densidade: ele deixou de custar altura. O número principal da linha
  passou a ser o limite cadastrado, e não o gasto — esta é a tela que cria, edita e apaga
  orçamento, e o teto é o que distingue "Transporte de R$ 300" de "Casa de R$ 2.500"; o
  acompanhamento continua sendo pergunta do dashboard. As categorias subiram para a linha como
  ícones empilhados, de largura limitada e excedente contado (`+N`), no lugar da contagem muda
  "3 categorias". Um teto derivado de uma receita recorrente nomeia a origem numa linha
  própria, sobre o teto que ela qualifica, e é a única variação de altura da lista (~80dp). A
  lista deixou de sair na ordem de criação e passou a sair por consumo: o que precisa de ação
  sobe sozinho.
- **Categoria lida contra a própria média.** O detalhe de uma categoria perdeu o seletor de
  mês e passou a dizer três figuras sobre uma janela declarada: o gasto do mês corrente,
  anunciado como parcial no próprio rótulo ("dia 24 de 31"); a média dos últimos 12 meses
  fechados; e o total dessa mesma janela — não o histórico inteiro. A janela viaja com a
  contagem real de meses, porque uma categoria de cinco meses dividida por doze leria um
  quinto do que gasta. A variação compara o mês contra a média, em pontos percentuais, e se
  diz por texto e seta: verde e vermelho já significam receita e despesa no app. Uma
  categoria arquivada troca o destaque pelo histórico inteiro, sobre o intervalo que ele de
  fato cobre, e uma sem movimento não mostra zero algum. Nenhuma figura inclui lançamento com
  data futura. No lugar da navegação temporal removida, um atalho abre a lista de transações
  já recortada por aquela categoria.
- **Série mensal por dimensão no razão.** Um total por (mês, moeda) numa consulta só, com
  corte superior escolhido por quem chama. É ela que sustenta as três figuras da categoria:
  uma janela de doze meses passou a custar uma leitura, e não doze.
- **Gasto sem categoria**: o não classificado ganhou linha e fatia próprias na quebra por
  categoria, no dashboard e no relatório, lendo a própria natureza.
- **Filtro sem categoria**: as cinco listas que filtram por categoria passaram a recortar
  também o que não tem nenhuma, e a oferecer a opção só quando ela encontra algo.
- **A liquidar este mês**: novo widget somando recorrentes do mês e faturas por pagar em
  "A entrar" e "A sair", com cabeçalho opcional.
- **Contas fora do total**: desmarcar uma conta no widget de saldo a retira da soma, sem
  alterar a conta.
- **Ícone sugerido**: uma conta nova abre com o primeiro ícone do catálogo que nenhuma conta
  aberta esteja usando.
- Widgets do dashboard passaram a declarar em quais modos de janela aparecem.
- **Telemetria que separa arquivar de apagar.** Arquivar conta, categoria ou cartão registrava
  `delete_*` — o mesmo evento das modais de exclusão —, e reabrir não registrava nada, o que
  fazia as "exclusões" parecerem se desfazer sozinhas. Cada um ganhou o seu `archive_*` e o
  seu `unarchive_*`. Os ajustes de moeda passaram a reportar o que fazem, as duas listas de
  arquivados passaram a se anunciar como telas, e um valor de parâmetro que o Firebase corta
  em 100 caracteres — em silêncio, no meio de uma chave — passou a ser cortado onde o app sabe
  declará-lo, com um `<chave>_count` que sobrevive ao corte.
- **Backlog de bugs** em `issues/`, com regra de entrada, correção e arquivamento, e uma
  skill que o conduz. A auditoria de tratamento de erro migrou para ele, e os bugs que o
  próprio ciclo introduziu passaram a dizer em que versão nasceram. A varredura que fechou o
  ciclo registrou 15 defeitos novos; são 66 abertos e 9 arquivados.
- **Notas de versão e roadmap** reconstruídos a partir dos commits que os sustentam, com o
  que foi entregue separado do que é apenas desejado.
- Suíte E2E: três fluxos novos no ciclo — a travessia da linha da moeda, a recorrente nascida
  no próprio lançamento e o pagamento de uma fatura retroativa —, e dois ramos que fluxos
  existentes só anunciavam passaram a ser percorridos: apagar uma conta, e recortar a lista de
  transações por uma categoria arquivada. São 15 fluxos, rodados no aparelho no fim do ciclo:
  **14 verdes**, e o vermelho é `creditcards_lifecycle`, que falha conforme o dia do mês em
  que a suíte roda — defeito do fluxo, anterior à mudança que o encontrou, e já registrado em
  `issues/`.

### Correções

- A marca de milhar deixou de ser lida como separador decimal no campo de taxa.
- O botão de salvar do formulário de taxa deixou de cancelar o próprio salvamento.
- Um valor sugerido parou de sobreviver à troca de moeda.
- Um zero parou de guardar a moeda em que por acaso chegou.
- O `setter` da moeda base foi removido: escrevê-la sozinha era a corrupção.
- A exclusão de uma moeda e o seu arquivo de taxas viraram uma escrita só.
- A confirmação de recorrente passou a oferecer cartões também por moeda.
- A moeda de um cartão passou a ter um dono só: eram oito implementações.
- Criação de cartão falha quando a primeira fatura não abre, em vez de deixar o cartão sem
  fatura.
- A fatura aberta a partir do detalhe é a que o chamador nomeou.
- A perna do cartão leva à fatura, não ao registro do cartão.
- Uma confirmação de recorrente podia cair fora do mês do ciclo que confirma: qualquer data
  passada era escolhível, a ocorrência era registrada sob o mês da data, a pendência
  permanecia no dashboard e a mesma despesa mensal entrava duas vezes no razão.
- O ordinal de um ciclo podia ser 0 ou negativo — a conta era uma subtração sem piso — e
  chegava assim ao razão e ao detalhe ("Aluguel • 0").
- Um modelo deixou de ser projetado nos meses em que a série dele ainda não tinha começado.
- Uma origem arquivada mantém o nome na lista, em vez de virar "Origem indisponível" e tornar
  duas recorrentes de mesmo rótulo indistinguíveis.
- As moedas da lista de recorrentes são resolvidas numa consulta, e não numa por linha.
- A altura da linha de recorrente passou a ser dita por quem a mede, e não por uma aritmética
  que a antecedia.
- A marca de teto derivado ficou ancorada ao teto que qualifica, e o excedente contado ficou
  centrado nos chips que conta.
- O detalhe de uma categoria parou de afirmar uma direção que não está lá.
- O formulário rola em vez de ser espremido pelo teclado.
- O formulário de ajuste de saldo fica na tela enquanto a data é digitada, em vez de dar
  lugar a um spinner a cada tecla.
- O card de saldo mantém a mesma altura com e sem a marca de aproximação.
- A área de toque de uma categoria ficou contida na linha que a mostra.
- O seletor de mês só marca o mês quando o ano também bate.
- A hidratação de uma perna usa o mapper dono da conta.
- A recusa da conta padrão fala na língua do usuário.
- O Suporte decide o vazio pelo escopo que está na tela.
- O documento exportado do relatório diz em que língua foi escrito.
- O id do usuário pode não resolver sem abortar o app.
- Migrações reorganizadas: um arquivo por migração, cada uma documentando o que faz e em
  que versão saiu.

---

## 1.9.0 — 07/08/2026

`versionCode` 30. Esquema do banco: **7 → 10**. 394 commits.

A versão em que o app trocou o modelo de dados por um razão de partidas dobradas e se
partiu em módulos.

### Arquitetura

- **Modularização por feature no padrão api/impl**, sobre módulos `core`, com convention
  plugins em `build-logic` impondo as regras — um `build.gradle.kts` de feature virou ~5
  linhas. Todas as features foram extraídas em ondas: support, categories, recurring,
  budgets, accounts, report, transactions, creditcards, dashboard e home.
- **`:composeApp` partido em `app/{shared,android,desktop,ios}`**, cada um com uma
  responsabilidade.
- **Navegação reescrita**: o dispatcher deu lugar a `LocalNavController` e a um único
  `NavHost`; toda feature expõe um subgrafo `navigation<XxxGraph>`; marcadores
  `NavRoute`/`NavGraphRoute` tornam toda rota localizável pelas suas implementações.
- **Razão de partidas dobradas como fonte única de verdade.** Todo saldo, fatura, gasto por
  categoria e patrimônio passou a ser `Σ lançamentos`, sem regra de sinal por tipo e sem uma
  segunda forma de calcular um número. `Σ = 0` por moeda é validado num único ponto de
  escrita. O que uma transação *é* passou a ser derivado dos tipos de conta das suas pernas,
  e não persistido.
- **`:core:ledger` extraído**, sem depender de nenhum módulo do app: uma `@Query` que
  nomeie uma tabela de fachada não compila. Duas portas deixam uma fachada participar sem o
  razão saber que ela existe — `DimensionWriteGuard` e `TransactionRemovalHook`.
- **Categoria virou dimensão**, não conta do plano; o sub-razão do cartão também.
- **Política de sinal** com dono único (`DisplayAmount`), aplicada em item e em resumo, em
  transações, contas, cartões e relatório.
- **Suíte E2E com Maestro** em `.maestro/`: 12 fluxos dirigindo o app real, com dispositivo
  fixado, alcance por `testTag` e um relógio móvel no build de debug para alcançar o que
  precisa de tempo.
- Migração colapsada num único `7 → 10`, com cobertura de paridade de leitura e reparo
  explícito do que a v7 permitia (perna órfã, perna apontando para conta apagada, operação
  que nunca somava zero).

### Novidades

- **Navegação adaptativa**: navigation rail e detail pane conforme a largura da janela; o
  detail pane é reativo por id, fixa as ações no rodapé no desktop e faz crossfade entre
  conteúdos. As configurações de widget e o chat do Suporte abrem nele.
- **Desktop de verdade**: estado da janela persistido e restaurado, instalador para Windows,
  ícones, empacotamento em CI e Suporte habilitado via Firebase.
- **Arquivar em vez de apagar**, com desarquivamento, para contas, cartões, categorias e
  recorrentes — incluindo lista de arquivados, guarda contra arquivar a conta padrão, e a
  regra de que quem já usa uma fachada arquivada continua usando.
- **Redesign de Categorias**: filtro por chip no topo, seções e visão de arquivados.
- **Redesign de Recorrentes**: arquivar no lugar de parar, filtro único e confirmação
  atômica do ciclo.
- **Perímetro de saldo**: o resumo de transações e os widgets do dashboard passaram a
  nomear a que escopo se referem (contas, cartões, tudo), com um widget neutro.
- **Estados vazios** em transações, contas, cartões e faturas, dizendo o que está vazio em
  vez de deixar a lista em branco.
- Uma fatura recusada para reabertura passou a dizer o porquê, e o botão some quando a
  regra o recusa.

### Correções

- O pagamento de fatura sai da conta que o usuário escolheu, e não da conta padrão.
- Reabrir uma fatura retroativa não corrompe mais o ciclo.
- Uma fatura paga deixou de ser mutável por edição.
- Uma edição de transação parou de descartar silenciosamente a mudança inteira.
- A lista de transações filtra por natureza, não pela direção da perna.
- Telas com agregado do razão passaram a reagir a escritas.
- O FAB permanece acima da bottom bar durante as transições, e o card vizinho parou de
  pintar sobre a navigation rail.
- Toda folha modal fica acima do teclado, e devolve o espaço quando coberta.
- `TransactionLabel` virou serializável — sem isso o iOS não subia.
- Excluir uma categoria passou a ser guardado contra perda de dados em orçamentos e
  recorrentes, oferecendo arquivar quando a exclusão é recusada.
- `Perf`: leituras por dimensão passaram a ser feitas em lote, eliminando o N+1.

---

## 1.8.0 — 13/04/2026

`versionCode` 27. A versão da telemetria.

### Novidades

- **Analytics (Firebase)** com interface própria e implementação no-op: `screen_view` nas
  13 telas, `user_id` a partir do Firebase Auth, e eventos tipados cobrindo transações,
  contas, transferências, ajustes de saldo, cartões, faturas, parcelamentos, orçamentos,
  recorrentes, categorias, modo de edição do dashboard, relatórios e suporte.
- **Crashlytics** com a mesma separação: interface de domínio, implementação Firebase e
  no-op, id do usuário no arranque e reporte de exceções em ViewModels e repositórios.

### Correções

- Crash no Desktop por dependência direta do Firebase Auth, resolvido abstraindo em
  `AuthService`.
- Eventos de analytics só são registrados quando o use case tem sucesso.
- `AdjustInvoice`, `AdjustBalance`, `SetDefaultAccount` e `EnsureDefaultAccount` passaram a
  capturar todas as exceções, devolvendo `Either`.
- Permissões de advertising ID injetadas pelo Firebase/GMS removidas do Manifest do Android.

---

## 1.7.1 — 09/04/2026

`versionCode` 25. Versão de correção.

- Crash ao abrir o ajuste de saldo de uma conta.
- Crash ao obter o dispatcher de navegação a partir de um modal.

---

## 1.7.0 — 09/04/2026

`versionCode` 24. A versão do dashboard personalizável.

### Novidades

- **Modo de edição do dashboard**, entregue em quatro etapas: prévias reais dos
  componentes, lista unificada com seções de ativos e disponíveis, reordenação por arrastar
  (com alça arrastável sem long press) e modal de configuração por componente, com
  confirmar/cancelar.
- Ações em massa para adicionar e remover no cabeçalho da seção.
- Novos componentes: estatísticas de saldo de cartão de crédito e receita por categoria.
- Configuração de visibilidade de cabeçalho por componente, e espaçamento superior
  configurável.
- Dica de onboarding do modo de edição para novos usuários.
- Atalho para o Suporte na topbar (oculto por padrão).

### Correções

- O modo de edição deixou de ser acionado durante a rolagem, e de esconder o chrome do app.
- Preferências: config padrão aplicada quando nada foi salvo, padrão restaurado ao
  re-adicionar um widget, dashboard vazio preservado após remover componentes e acesso ao
  modo de edição preservado nele.
- Uso da instância configurada de `Json` no repositório de preferências.
- Prévias dos componentes internacionalizadas.

---

## 1.6.0 — 31/03/2026

`versionCode` 20. A versão do suporte in-app.

### Novidades

- **Suporte in-app**: reporte de problemas por chat, com Firebase Firestore como backend —
  mensagens em subcoleção, estados de carregamento, bolhas redesenhadas, divisores por dia,
  auto-scroll para a última mensagem, indicador de resposta pendente, status (Aberto,
  Planejado, Fazendo, Feito), filtro ativo/inativo e transição de elemento compartilhado
  entre o card e o cabeçalho do chat.
- Categorias: `HorizontalPager` para alternar entre as abas de despesa e receita.

### Correções

- O compositor de resposta deixou de sobrepor a barra de navegação do sistema.
- O teclado do chat é dispensado ao tocar fora do compositor.
- Faturas editáveis são recarregadas quando o cartão muda.
- Suporte escondido no Desktop, onde o Firebase ainda não estava disponível.

---

## 1.5.0 — 18/03/2026

`versionCode` 18. Esquema do banco: **5 → 7**. A versão dos relatórios.

### Novidades

- **Relatórios**: telas de configuração e de visualização, seletor de período com opções
  rápidas, perspectivas por conta e por cartão (com perspectiva de fatura e carrossel de
  seleção múltipla), saldo inicial, receita por categoria e cabeçalho de contexto.
- **Exportação em HTML e impressão nativa** nas três plataformas, com o fluxo de
  compartilhamento nativo.
- **Limite de orçamento percentual**, atrelado a uma receita recorrente, com atalho da linha
  de percentual para a recorrente.
- Ícone próprio para cartões de crédito.
- Atalhos para cadastrar categoria ou cartão faltante direto dos modais de formulário.
- Seleção automática do cartão quando só existe um.
- Cards de resumo de receita e despesa pendentes no dashboard.
- Ao confirmar uma recorrente, é possível trocar a conta ou o cartão de destino.

### Correções

- Ajustes passaram a entrar no saldo do relatório, e transferências internas a ficar de
  fora das estatísticas por conta.
- Clique no gasto por categoria corrigido (ordem do modifier) e modal ligado.
- Desmarcar a última conta na configuração do relatório voltou a ser possível.
- Botão de confirmar do formulário de cartão deixou de ser coberto pelo teclado.
- Padding extra no topo do dashboard no iOS.
- `statusBarsPadding` na topbar de transações.

---

## 1.4.0 — 04/03/2026

`versionCode` 13. Esquema do banco: **2 → 5**. A versão das recorrentes.

### Novidades

- **Transações recorrentes**: confirmar, pular, parar e reativar, com ocorrências
  rastreadas por ciclo, filtro por status na topbar e detalhes acessíveis pelo modal da
  operação.
- Dashboard: card de saldo total e linha rápida de contas.
- Contas: seleção e persistência de ícone.
- Categorias: abas por tipo, formulário abrindo com o tipo já selecionado, seletor de
  ícones melhorado.
- Orçamentos: seletor de ícone independente da categoria.
- Filtros por parcelamento nas listagens de operações.
- Atalhos de saldo e fatura no visualizador de ajuste.
- Cinco telas convertidas para estado selado com Loading, Empty e Content.

### Correções

- Dias 29 a 31 passaram a ser suportados no fluxo de pendências.
- Confirmação em data futura bloqueada.
- `createdAt` preservado ao editar uma recorrente.
- Títulos duplicados de orçamento passaram a ser validados.
- Dispensa duplicada de bottom sheet impedida.
- Visibilidade do saldo inicial e do seletor de mês corrigida no tema claro.

### Testes

- Todas as migrações Room existentes passaram a ter testes unitários.

---

## 1.3.2 — 01/03/2026

`versionCode` 8.

- Campos da operação sincronizados ao editar uma transação.
- Verificação de título fixo substituída pela propriedade `isInvoicePayment`.

---

## 1.3.1 — 28/02/2026

`versionCode` 7.

- Conteúdo dependente de locale que ainda estava fixo no código foi resolvido em todo o app.

---

## 1.3.0 — 28/02/2026

`versionCode` 6. A versão da internacionalização.

- **Suporte a inglês** e migração de todas as strings de UI para Compose Resources.
- **Formatador de moeda sensível ao locale do dispositivo** (`expect`/`actual`), no lugar do
  formatador BRL fixo.
- Mensagens de log de erro traduzidas para inglês.
- Espaçamento entre páginas do pager de gastos aumentado no dashboard.

---

## 1.2.0 — 28/02/2026

`versionCode` 5. Esquema do banco: **1 → 2**. A versão dos orçamentos.

- **Orçamentos** com suporte a múltiplas categorias, e sinalização de cor progressiva no
  dashboard e nas telas de orçamento.
- Estados vazios nas telas de cartões e parcelamentos.
- Cores de ação padronizadas nas topbars.
- Parcelas futuras excluídas da seção "Recentes" do dashboard.

---

## 1.1.0 — 27/02/2026

`versionCode` 4.

- Limite total exibido ao lado do limite disponível no cartão.
- Navegação para cartão, fatura e parcelamento a partir das linhas de detalhe da operação.
- Filtro ativo/concluído/todos nos parcelamentos.
- Contagem e valor total de um parcelamento atualizados ao excluir uma de suas operações.
- Ícone de categoria ou de calendário no parcelamento — nunca os dois.
- Título da operação truncado em uma linha.

---

## 1.0.2 — 25/02/2026

`versionCode` 3.

- **Tema claro** seguindo a preferência do sistema.
- `operationId` preservado ao atualizar uma transação.
- Card de pagamento de fatura removido do dashboard sem deixar espaço vazio.

---

## 1.0.1 — 24/02/2026

`versionCode` 2.

- Ícone do app e configuração de assinatura de release no Android.
- Política de privacidade adicionada ao projeto.
- Operações apagadas junto com o cartão de crédito removido.
- Cálculo invertido do valor de ajuste de fatura corrigido.
- Barra de status escura no tema escuro.

---

## 1.0.0 — 22/02/2026

`versionCode` 1. Primeira versão publicada, sob o nome **Finsight**. 176 commits desde o
projeto vazio (22/11/2025).

### O que o app já fazia

- **Dashboard** com visão de saldo, gastos por categoria e resumo de cartões.
- **Transações**: lançar, ver, editar e excluir, com seletor de mês e filtros por conta,
  categoria e tipo.
- **Contas**: multi-conta, ajuste de saldo (com suporte a cheque especial) e transferência
  entre contas.
- **Cartões de crédito**: múltiplos cartões, limite, dia de fechamento e vencimento, e o
  ciclo de faturas — abrir, fechar, pagar e ajustar. Faturas futuras e lançamentos
  retroativos.
- **Parcelamentos**: criação, listagem e exclusão, com o total da compra visível na
  transação.
- **Categorias**: gestão com ícones, categorias padrão no primeiro uso e visão de gastos por
  categoria.

### Base técnica

- Kotlin Multiplatform com Compose Multiplatform (Android, Desktop e iOS), Room, Koin.
- **Arrow (Either)** adotado em formulários, transações e use cases, com abordagem
  *operation-first*.
- Suporte a iOS e migração do projeto Xcode para **XcodeGen**.
- `UiText` para texto seguro de UI, mappers e use cases isolando o domínio.

---

## Notas sobre a reconstrução deste histórico

- O repositório nunca teve tags. As fronteiras entre versões vêm dos commits que alteram
`versionName`/`versionCode`, e cada commit foi atribuído a uma versão por ancestralidade,
não por data — várias features foram escritas em branch e mescladas depois do bump
seguinte, e as datas de autoria deste repositório foram reescritas em algum momento.
- O desktop larga o sufixo de release candidate de propósito: `packageVersion` não o aceita
(`.claude/skills/bump-version/SKILL.md:22`).
- As faixas de esquema do banco vêm do KDoc de cada migração em
`core/database/src/commonMain/kotlin/com/neoutils/finsight/database/migration/`. O app
declara hoje a versão **14**.
