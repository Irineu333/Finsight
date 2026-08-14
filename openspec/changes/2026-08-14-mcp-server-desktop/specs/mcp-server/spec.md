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

### Requirement: O servidor conforma a revisão 2026-07-28 do protocolo, que é sem estado

O servidor SHALL implementar a revisão `2026-07-28` do Model Context Protocol, na qual o
protocolo **não tem handshake e não tem sessão**. Não existem `initialize` nem
`notifications/initialized`; cada requisição carrega a própria versão de protocolo e as
capabilities do cliente em `_meta`.

O servidor MUST NOT depender de estado acumulado entre chamadas para decidir o que responder.
Onde estado entre chamadas for necessário, ele SHALL ser um identificador explícito emitido pelo
servidor e passado como argumento comum de tool.

O servidor SHALL implementar `server/discover`, anunciando as versões de protocolo que fala,
as suas capabilities e a sua identidade. Ele SHALL identificar-se no `_meta` de cada resultado.

Todo resultado SHALL declarar o seu tipo, distinguindo resultado completo de resultado que ainda
pede entrada.

Resultados de listagem e de leitura de recurso SHALL declarar validade de cache e escopo de
cache. A listagem de tools SHALL ter ordem determinística, para que o cache do cliente valha.

Funcionalidades depreciadas na revisão — Roots, Sampling e Logging — MUST NOT ser adotadas.

#### Scenario: Nenhum handshake é esperado
- **WHEN** um cliente envia uma chamada de tool como primeira requisição da conexão
- **THEN** ela é atendida, sem que nenhuma inicialização prévia tenha sido exigida

#### Scenario: Descoberta antes de qualquer chamada
- **WHEN** um cliente chama `server/discover`
- **THEN** o servidor responde com as versões de protocolo suportadas, as suas capabilities e a
  sua identidade

#### Scenario: Listagem é cacheável e estável
- **WHEN** a listagem de tools é pedida duas vezes sem que nada tenha mudado
- **THEN** a ordem é a mesma, e a resposta declara por quanto tempo pode ser cacheada e em que
  escopo

### Requirement: O transporte é Streamable HTTP em loopback, com Origin validado

O servidor SHALL expor um **único caminho de endpoint HTTP** que aceita `POST`, e SHALL escutar
exclusivamente em `127.0.0.1`. Ele MUST NOT associar-se a todas as interfaces de rede.

O servidor SHALL validar o header `Origin` em toda conexão recebida. Se o `Origin` estiver
presente e não for reconhecido, o servidor SHALL responder `403 Forbidden` **antes de executar
qualquer tool**. Sem isso, uma página web aberta pelo usuário alcança o servidor local por DNS
rebinding — e este servidor escreve no razão do usuário.

`GET` e `DELETE` no endpoint SHALL responder `405`. Headers de sessão e de retomada de stream
de revisões anteriores SHALL ser ignorados: não há sessão, e streams não são retomáveis.

#### Scenario: Origin desconhecido é barrado
- **WHEN** uma requisição chega com `Origin` presente e não reconhecido
- **THEN** o servidor responde `403`, nenhuma tool executa e nenhuma leitura do banco acontece

#### Scenario: Endereço não roteável
- **WHEN** o servidor está ligado
- **THEN** o socket está associado apenas ao loopback, e nenhuma tentativa vinda de outra
  máquina o alcança

#### Scenario: Verbo antigo recusado
- **WHEN** um cliente de revisão anterior faz `GET` no endpoint esperando abrir um stream
- **THEN** o servidor responde `405`

### Requirement: Os headers de requisição são obrigatórios e conferidos contra o corpo

Toda requisição SHALL trazer o header de versão de protocolo, e o seu valor SHALL coincidir com
a versão declarada no `_meta` do corpo. Divergência SHALL ser recusada com `400` e o erro de
divergência de header.

O header que espelha o método SHALL ser exigido em toda requisição, e o que espelha o nome
SHALL ser exigido em chamada de tool, leitura de recurso e obtenção de prompt. Header ausente,
malformado ou divergente do corpo SHALL ser recusado com `400` e o mesmo erro.

Versão de protocolo não suportada SHALL ser recusada com `400` e o erro próprio, **listando as
versões que o servidor fala**. Método desconhecido SHALL responder `404` com o erro JSON-RPC de
método não encontrado.

A conferência não é burocracia: ela impede que um componente decida por um valor do header
enquanto o servidor executa por outro do corpo.

#### Scenario: Header diverge do corpo
- **WHEN** o header de nome traz uma tool diferente da nomeada no corpo
- **THEN** a requisição é recusada com `400` e o erro de divergência, sem executar nada

#### Scenario: Versão não suportada
- **WHEN** um cliente declara uma versão de protocolo que o servidor não implementa
- **THEN** a resposta é `400` com o erro próprio, listando as versões suportadas

### Requirement: Fechar o stream cancela, e um lote precisa saber o que isso significa

Fechar o stream de resposta SHALL ser tratado como cancelamento daquela requisição. O servidor
SHALL interromper o trabalho assim que praticável e MUST NOT emitir mais nada para ela.

Uma escrita em lote cancelada no meio SHALL ter desfecho definido: o que já foi aplicado
permanece aplicado, e SHALL ser recuperável pela mesma chave de idempotência da chamada, para
que repetir a chamada conclua o que faltou em vez de duplicar o que entrou. Um lote cancelado
MUST NOT deixar o razão num estado que ninguém consegue nomear.

Operações longas — lote grande, agregado sobre período extenso — SHOULD emitir progresso no
stream da própria requisição.

#### Scenario: Cliente desconecta no meio do lote
- **WHEN** o cliente fecha o stream durante uma escrita de trinta itens
- **THEN** o servidor para assim que possível, o que já foi gravado permanece, e repetir a
  chamada com a mesma chave conclui o restante sem duplicar o que já entrou

#### Scenario: Nada é emitido após o cancelamento
- **WHEN** uma requisição é cancelada pelo fechamento do stream
- **THEN** o servidor não emite nenhuma mensagem adicional para ela

### Requirement: O servidor não depende da interface do Finsight

O servidor MUST NOT depender de nenhum elemento de interface **do Finsight** para funcionar:
não SHALL ser iniciado a partir de um escopo de composição, não SHALL ler estado de janela e
MUST NOT exigir interação na tela do próprio app para concluir uma tool.

Isso não proíbe pedir informação ao usuário: o protocolo prevê que o servidor devolva um
resultado pedindo entrada, e que o **cliente** a colete e reenvie a requisição. Essa interação
acontece na interface do cliente, não na do Finsight, e é permitida.

#### Scenario: Tool conclui com a janela minimizada
- **WHEN** uma tool é chamada e a janela do app está minimizada ou fora de foco
- **THEN** ela conclui normalmente, sem esperar por interação no Finsight

#### Scenario: Nenhuma tool abre modal do app
- **WHEN** a execução de qualquer tool é inspecionada
- **THEN** ela não apresenta modal, diálogo ou qualquer elemento da interface do Finsight
