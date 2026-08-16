## ADDED Requirements

### Requirement: Quatro eixos de permissão, e o que cada um governa

O usuário SHALL controlar o que um agente pode fazer por quatro eixos independentes:

- **Ler** — consultar figuras e listar o que existe;
- **Registrar e editar** — criar e alterar lançamentos, contas, cartões, categorias,
  orçamentos, parcelamentos e recorrências;
- **Apagar** — remover definitivamente;
- **Operar** — mover dinheiro entre contas, pagar, fechar, reabrir e ajustar faturas,
  confirmar e pular recorrências, arquivar e desarquivar.

Os eixos SHALL ser independentes: conceder um MUST NOT conceder outro. **Apagar** SHALL ser um
eixo próprio, e não o grau máximo de "registrar e editar" — remover não é uma edição mais
intensa. **Operar** SHALL ser um eixo próprio, e não parte de "registrar e editar" — pagar
uma fatura move dinheiro entre contas, o que registrar não faz.

#### Scenario: Eixos são independentes
- **WHEN** o usuário concede "registrar e editar" sem conceder "apagar"
- **THEN** o agente cria e altera, e não remove

#### Scenario: Operar não vem junto com registrar
- **WHEN** o usuário concede "registrar e editar" sem conceder "operar"
- **THEN** o agente registra lançamentos, e não paga faturas nem transfere entre contas

### Requirement: A permissão decide quais ferramentas existem

Uma permissão não concedida SHALL fazer com que as ferramentas correspondentes **não sejam
anunciadas** ao cliente. A verificação MUST NOT ser apenas uma recusa no momento da execução:
uma ferramenta que o usuário não autorizou não é oferecida.

Uma ferramenta não anunciada SHALL ser recusada se invocada mesmo assim, porque o anúncio é
uma consequência da permissão e não a sua única aplicação.

#### Scenario: Permissão de apagar negada
- **WHEN** um cliente pede a lista de ferramentas com "apagar" desabilitado
- **THEN** nenhuma ferramenta de remoção é anunciada

#### Scenario: Só leitura
- **WHEN** apenas o eixo "ler" está concedido
- **THEN** somente as ferramentas de consulta e listagem são anunciadas

#### Scenario: Invocação de ferramenta não anunciada
- **WHEN** um cliente invoca pelo nome uma ferramenta cuja permissão não foi concedida
- **THEN** a invocação é recusada e nada é alterado

### Requirement: Mudar a permissão alcança quem já está conectado

Quando o usuário alterar um eixo de permissão, o servidor SHALL notificar os clientes
conectados de que a lista de ferramentas mudou, sem exigir reconexão.

#### Scenario: Permissão concedida durante uma sessão
- **WHEN** o usuário concede um eixo enquanto um cliente está conectado
- **THEN** o cliente é notificado da mudança e passa a enxergar as ferramentas correspondentes

#### Scenario: Permissão revogada durante uma sessão
- **WHEN** o usuário revoga um eixo enquanto um cliente está conectado
- **THEN** o cliente é notificado e as ferramentas correspondentes deixam de ser oferecidas

### Requirement: O estado inicial não concede escrita

Antes de qualquer configuração do usuário, o servidor SHALL estar desligado. Ao ser ligado
pela primeira vez, apenas o eixo **ler** SHALL estar concedido; os eixos de escrita — registrar
e editar, apagar, operar — SHALL exigir concessão explícita.

#### Scenario: Primeira execução
- **WHEN** o app é aberto pela primeira vez após a instalação
- **THEN** o servidor está desligado e nenhuma ferramenta é oferecida

#### Scenario: Primeira habilitação
- **WHEN** o usuário liga o servidor pela primeira vez, sem tocar nos eixos
- **THEN** o agente consulta e lista, e não altera nada

#### Scenario: Permissões sobrevivem ao reinício
- **WHEN** o usuário concede um eixo e reinicia o app
- **THEN** a concessão continua valendo
