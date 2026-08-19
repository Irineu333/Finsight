# mcp-permissions Specification

## Purpose

O que um agente pode fazer, controlado pelo usuário em quatro eixos independentes — ler, registrar e editar, apagar, operar. A permissão decide **quais ferramentas existem** no anúncio, e não é um `if` no começo de cada uma. Como esconder a ferramenta esconderia também a existência da capacidade, o handshake declara o que está retido e onde concedê-lo: um agente que não encontra como remover um lançamento não pode concluir que o app não sabe remover.

## Requirements

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

### Requirement: O que está retido é dito, mesmo sem ser oferecido

O servidor SHALL declarar, no handshake da sessão, quais eixos estão concedidos e quais estão
**retidos**, e SHALL dizer que um eixo retido é uma escolha do usuário, reversível na tela de
configurações do app.

Filtrar o `tools/list` esconde a ferramenta, e sem esta declaração esconde também a existência
da capacidade: um agente que não encontra como remover um lançamento conclui que o app **não
sabe** remover, e responde ao usuário que aquilo é impossível. É uma resposta falsa, dada com
confiança, sobre a própria configuração do usuário — e ela impede exatamente a ação que
resolveria o caso, que é o usuário conceder o eixo.

A declaração MUST NOT enumerar as ferramentas retidas nem os seus argumentos: o que se declara
é a **capacidade** ausente e o caminho para concedê-la, não uma segunda lista de ferramentas
por outro canal.

Uma ferramenta invocada pelo nome sem a permissão correspondente SHALL recusar distinguindo
**"não autorizado"** de **"não existe"**, com a mesma indicação de onde conceder.

#### Scenario: Sessão com um eixo retido
- **WHEN** um cliente abre sessão com o eixo "apagar" não concedido
- **THEN** o handshake declara que remover está retido por escolha do usuário e pode ser concedido nas configurações do app

#### Scenario: Agente relata a retenção em vez de negar a capacidade
- **WHEN** o usuário pede a um agente que remova um lançamento e o eixo "apagar" está retido
- **THEN** o agente dispõe da informação para dizer que a remoção existe e depende de autorização, em vez de responder que o app não a suporta

#### Scenario: Invocação nominal sem permissão
- **WHEN** uma ferramenta de um eixo retido é invocada pelo nome
- **THEN** a recusa diz que a operação existe e não está autorizada — nunca que ela é desconhecida

#### Scenario: A declaração não é uma segunda lista
- **WHEN** o handshake de uma sessão com eixos retidos é inspecionado
- **THEN** ele nomeia as capacidades retidas, sem enumerar ferramentas nem os seus argumentos

### Requirement: Uma operação retida não ganha substituto torto

As ferramentas dos eixos concedidos MUST NOT oferecer, por composição, o efeito prático de uma
operação cujo eixo está retido.

Em particular, uma ferramenta de edição MUST NOT aceitar valor nulo ou negativo como forma de
anular um lançamento: o resultado seria um registro de valor zero que permanece nas listagens e
nas contagens, o que não é a remoção que o usuário pediu e é pior do que a recusa — some do
total sem sumir do histórico, e ninguém sabe que está lá.

#### Scenario: Edição não anula por zeragem
- **WHEN** um agente tenta alterar o valor de um lançamento para zero, ou para um valor negativo
- **THEN** a alteração é recusada, com a indicação de que anular um lançamento é remoção e depende do eixo correspondente

#### Scenario: Recusa não empurra para o contorno
- **WHEN** uma remoção é recusada por falta de permissão
- **THEN** a recusa nomeia a autorização como caminho, e não uma edição que simule o efeito

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
