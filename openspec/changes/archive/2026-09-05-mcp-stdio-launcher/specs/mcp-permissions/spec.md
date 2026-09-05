## MODIFIED Requirements

### Requirement: A permissão decide quais ferramentas existem

Uma permissão não concedida SHALL fazer com que as ferramentas correspondentes **não sejam
anunciadas** ao cliente. A verificação MUST NOT ser apenas uma recusa no momento da execução:
uma ferramenta que o usuário não autorizou não é oferecida.

Uma ferramenta não anunciada SHALL ser recusada se invocada mesmo assim, porque o anúncio é
uma consequência da permissão e não a sua única aplicação.

A regra SHALL valer igual nos dois modos da superfície: o modo `--mcp`, com a janela fechada, lê
a escolha persistida e anuncia e recusa exatamente o que o servidor embutido anunciaria e
recusaria.

#### Scenario: Permissão de apagar negada
- **WHEN** um cliente pede a lista de ferramentas com "apagar" desabilitado
- **THEN** nenhuma ferramenta de remoção é anunciada

#### Scenario: Só leitura
- **WHEN** apenas o eixo "ler" está concedido
- **THEN** somente as ferramentas de consulta e listagem são anunciadas

#### Scenario: Invocação de ferramenta não anunciada
- **WHEN** um cliente invoca pelo nome uma ferramenta cuja permissão não foi concedida
- **THEN** a invocação é recusada e nada é alterado

#### Scenario: A mesma lista com o app fechado
- **WHEN** um cliente pede a lista de ferramentas pelo modo `--mcp` com a janela fechada
- **THEN** a lista é a que o servidor embutido anunciaria para as mesmas permissões, e uma ferramenta retida é recusada da mesma forma

### Requirement: Mudar a permissão alcança quem já está conectado

Quando o usuário alterar um eixo de permissão, o servidor SHALL notificar os clientes
conectados de que a lista de ferramentas mudou, sem exigir reconexão. Um cliente ligado pelo
modo `--mcp` com a janela aberta SHALL receber a mesma notificação, repassada pela ponte.

#### Scenario: Permissão concedida durante uma sessão
- **WHEN** o usuário concede um eixo enquanto um cliente está conectado
- **THEN** o cliente é notificado da mudança e passa a enxergar as ferramentas correspondentes

#### Scenario: Permissão revogada durante uma sessão
- **WHEN** o usuário revoga um eixo enquanto um cliente está conectado
- **THEN** o cliente é notificado e as ferramentas correspondentes deixam de ser oferecidas

#### Scenario: A notificação atravessa a ponte
- **WHEN** o usuário altera um eixo enquanto um cliente está ligado pelo modo `--mcp` com a janela aberta
- **THEN** o cliente stdio recebe a notificação e a lista seguinte reflete a mudança

### Requirement: O estado inicial não concede escrita

Antes de qualquer configuração do usuário, o servidor SHALL estar desligado. Ao ser ligado
pela primeira vez, apenas o eixo **ler** SHALL estar concedido; os eixos de escrita — registrar
e editar, apagar, operar — SHALL exigir concessão explícita. O modo `--mcp` SHALL respeitar o
mesmo estado: numa instalação onde ninguém ligou o servidor, ele não anuncia nem executa nada.

#### Scenario: Primeira execução
- **WHEN** o app é aberto pela primeira vez após a instalação
- **THEN** o servidor está desligado e nenhuma ferramenta é oferecida

#### Scenario: Primeira habilitação
- **WHEN** o usuário liga o servidor pela primeira vez, sem tocar nos eixos
- **THEN** o agente consulta e lista, e não altera nada

#### Scenario: Permissões sobrevivem ao reinício
- **WHEN** o usuário concede um eixo e reinicia o app
- **THEN** a concessão continua valendo

#### Scenario: Modo stdio numa instalação sem escolha
- **WHEN** ninguém nunca ligou o servidor e um cliente lança o executável com `--mcp`
- **THEN** nenhuma ferramenta é anunciada e nenhuma chamada é executada
