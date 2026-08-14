## ADDED Requirements

### Requirement: O servidor MCP vive no processo do app, e o banco tem um dono só

O servidor MCP SHALL executar dentro do mesmo processo que a aplicação desktop, sobre a mesma
instância de Koin e o mesmo banco que a UI usa.

Nenhum caminho — cliente, atalho ou ponte de transporte — SHALL poder iniciar uma segunda
instância da aplicação enquanto uma já estiver executando. Duas instâncias sobre
`~/.finance/finsight.db` teriam rastreadores de invalidação independentes, e uma escrita feita
por uma delas não apareceria na outra até um reinício.

#### Scenario: Escrita do agente aparece na tela aberta
- **WHEN** uma tool de escrita conclui com sucesso e a janela do app está exibindo uma
  superfície afetada por ela
- **THEN** a superfície reflete a mudança sem nenhuma ação do usuário, pelo mesmo mecanismo
  reativo que reflete uma escrita feita pela própria UI

#### Scenario: Segunda instância é recusada
- **WHEN** algo tenta iniciar a aplicação enquanto outra instância já é dona do banco
- **THEN** a segunda tentativa não abre o banco, e o processo já existente permanece o único dono

### Requirement: A revisão alvo é a `2025-11-25`, e a defasagem é declarada

O servidor SHALL implementar a revisão `2025-11-25` do Model Context Protocol — a mais recente
que o SDK Kotlin de MCP fala. A revisão seguinte, `2026-07-28`, existe e é sem estado: ela
remove o handshake e as sessões, exige `server/discover` e substitui as requisições iniciadas
pelo servidor. **Não** é alvo aqui porque nenhum SDK JVM a implementa, e escrever o transporte à
mão foi recusado como custo desproporcional.

Essa defasagem SHALL constar da documentação do servidor como dívida datada, com o gatilho da
migração: o SDK passar a falar a revisão seguinte. Uma defasagem escolhida e escrita é decisão;
a mesma defasagem não escrita vira uma surpresa para quem mantiver isto depois.

O servidor SHALL declarar as suas capabilities na inicialização, incluindo aviso de mudança na
lista de tools. Ele MUST NOT adotar funcionalidades que a revisão seguinte já depreciou —
Roots, Sampling e Logging — para não acumular o que a migração teria de desfazer.

#### Scenario: Versão anunciada
- **WHEN** um cliente inicializa a conexão
- **THEN** o servidor negocia a revisão `2025-11-25` e declara as capabilities que oferece,
  entre elas o aviso de mudança na lista de tools

#### Scenario: Nada que a próxima revisão já depreciou
- **WHEN** o servidor é inspecionado
- **THEN** ele não oferece Roots, Sampling nem Logging

### Requirement: O transporte é Streamable HTTP em loopback, com Origin validado

O servidor SHALL expor um **único caminho de endpoint HTTP**, aceitando `POST` para as
requisições e `GET` para o fluxo de notificações que ele inicia, e SHALL escutar exclusivamente
em `127.0.0.1`. Ele MUST NOT associar-se a todas as interfaces de rede.

O servidor SHALL validar o header `Origin` em toda conexão recebida. Se o `Origin` estiver
presente e não for reconhecido, o servidor SHALL responder `403 Forbidden` **antes de executar
qualquer tool**. Sem isso, uma página web aberta pelo usuário alcança o servidor local por DNS
rebinding — e este servidor escreve no razão.

O servidor MUST NOT atribuir sessão. A revisão permite sessões, e elas não servem a nada aqui:
um servidor local de usuário único não tem estado de conversa para guardar, e um identificador
de sessão a mais é um segredo a mais para vazar.

Toda requisição SHALL trazer o header de versão de protocolo, e versão inválida ou não suportada
SHALL ser recusada com `400`.

#### Scenario: Origin desconhecido é barrado
- **WHEN** uma requisição chega com `Origin` presente e não reconhecido
- **THEN** o servidor responde `403`, nenhuma tool executa e nenhuma leitura do banco acontece

#### Scenario: Endereço não roteável
- **WHEN** o servidor está ligado
- **THEN** o socket está associado apenas ao loopback, e nenhuma tentativa vinda de outra
  máquina o alcança

#### Scenario: Nenhuma sessão é emitida
- **WHEN** um cliente inicializa a conexão
- **THEN** nenhum identificador de sessão é atribuído, e requisições seguintes não precisam
  carregá-lo

#### Scenario: Versão não suportada
- **WHEN** uma requisição declara uma versão de protocolo que o servidor não implementa
- **THEN** a resposta é `400`

### Requirement: Desconexão não é cancelamento, e um lote interrompido tem desfecho nomeado

Nesta revisão, perder a conexão MUST NOT ser interpretado como cancelamento: o cliente cancela
por notificação explícita. O servidor SHALL tratar as duas situações separadamente.

Recebido o cancelamento, o servidor SHALL interromper o trabalho assim que praticável e MUST NOT
emitir mais nada para aquela requisição.

Em qualquer dos dois casos, uma escrita em lote interrompida no meio SHALL ter desfecho
definido: o que já foi aplicado permanece aplicado, e SHALL ser recuperável pela mesma chave de
idempotência da chamada, para que repetir conclua o que faltou em vez de duplicar o que entrou.
Um lote interrompido MUST NOT deixar o razão num estado que ninguém consegue nomear.

Operações longas — lote grande, agregado sobre período extenso — SHOULD emitir progresso.

#### Scenario: Cancelamento explícito
- **WHEN** o cliente envia a notificação de cancelamento durante uma escrita de trinta itens
- **THEN** o servidor para assim que possível, não emite mais nada para aquela requisição, e o
  que já foi gravado permanece

#### Scenario: Conexão cai sem cancelamento
- **WHEN** a conexão se perde durante uma escrita em lote, sem notificação de cancelamento
- **THEN** o servidor não trata isso como cancelamento

#### Scenario: Repetir conclui em vez de duplicar
- **WHEN** um lote interrompido é repetido com a mesma chave e os mesmos itens
- **THEN** o que já entrou não é gravado de novo, e o que faltava é concluído

### Requirement: A execução de tools é limitada por taxa

O servidor SHALL limitar a taxa de invocação de tools, conforme a revisão exige. Um agente
escreve em lote e repete por decisão própria, e um servidor local sem limite transforma um
laço de prompt num laço de escrita no razão.

O limite SHALL ser recusa nomeada e repetível, distinguível de recusa de regra do domínio.

#### Scenario: Excesso de chamadas
- **WHEN** um cliente excede o limite de invocações
- **THEN** as chamadas seguintes são recusadas com erro repetível que nomeia o limite, e nenhuma
  escrita acontece

### Requirement: O servidor não depende da interface do Finsight

O servidor MUST NOT depender de nenhum elemento de interface **do Finsight** para funcionar:
não SHALL ser iniciado a partir de um escopo de composição, não SHALL ler estado de janela e
MUST NOT exigir interação na tela do próprio app para concluir uma tool.

Isso não proíbe pedir informação ao usuário: o protocolo permite ao servidor solicitar entrada
ao cliente, e essa interação acontece na interface do cliente, não na do Finsight.

#### Scenario: Tool conclui com a janela minimizada
- **WHEN** uma tool é chamada e a janela do app está minimizada ou fora de foco
- **THEN** ela conclui normalmente, sem esperar por interação no Finsight

#### Scenario: Nenhuma tool abre modal do app
- **WHEN** a execução de qualquer tool é inspecionada
- **THEN** ela não apresenta modal, diálogo ou qualquer elemento da interface do Finsight
