# account-icon-suggestion Specification

## Purpose

Qual ícone o formulário de uma conta nova pré-seleciona.

O ícone existe para distinguir uma conta da outra de relance — na lista de contas, no dashboard, nos seletores de lançamento. Um formulário que abre sempre no mesmo ícone derrota esse propósito: quem cria contas sem tocar no seletor termina com uma lista de linhas idênticas. A resposta é derivada do que já está em uso, e não uma constante.

A derivação tem dono único no domínio da feature de contas: o critério de "em uso", a ordem de preferência e o desfecho com o catálogo esgotado vivem em um só lugar, e a apresentação apenas consome. O que a spec governa é a **pré-seleção**, nunca a escolha: nada é bloqueado, escondido ou recusado no seletor, e a sugestão jamais é confundida com garantia de unicidade.

## Requirements

### Requirement: O formulário de conta nova pré-seleciona um ícone ainda não usado

Ao abrir o formulário para **criar** uma conta, o sistema SHALL pré-selecionar o primeiro ícone do catálogo de contas cuja chave não esteja em uso por nenhuma conta aberta. O ícone continua sendo apenas uma **pré-seleção**: o usuário SHALL poder trocá-lo por qualquer ícone que o seletor ofereça, um já usado inclusive, e o sistema MUST NOT bloquear, esconder ou recusar uma escolha por já haver conta com aquele ícone.

A sugestão MUST NOT alterar a **edição**: o formulário de uma conta existente SHALL abrir com o ícone que a conta já tem, mesmo que outra conta use o mesmo, e nenhuma sugestão SHALL ser calculada nesse caso.

A comparação entre "em uso" e catálogo SHALL ser feita pela chave persistida do ícone (`iconKey`), não pela identidade do enum, de modo que uma chave desconhecida gravada no banco não seja confundida com nenhum item do catálogo.

#### Scenario: Segunda conta recebe ícone diferente da primeira
- **WHEN** existe uma conta aberta usando o primeiro ícone do catálogo de contas e o usuário abre o formulário de nova conta
- **THEN** o formulário abre com o primeiro ícone do catálogo que nenhuma conta aberta usa, e não com o ícone da conta existente

#### Scenario: Primeira conta de uma instalação limpa
- **WHEN** não existe nenhuma conta aberta e o usuário abre o formulário de nova conta
- **THEN** o formulário abre com o primeiro ícone do catálogo de contas

#### Scenario: O usuário escolhe um ícone já usado
- **WHEN** o usuário seleciona, no formulário de nova conta, um ícone que outra conta aberta já usa
- **THEN** a seleção é aceita, o seletor não sinaliza recusa e a conta é criada com esse ícone

#### Scenario: Editar conta não altera o ícone
- **WHEN** o usuário abre o formulário de uma conta existente cujo ícone é igual ao de outra conta
- **THEN** o formulário abre com o ícone da própria conta e nenhuma sugestão é aplicada

### Requirement: Só conta aberta ocupa um ícone

Ao apurar quais ícones estão em uso, o sistema SHALL considerar exclusivamente as contas **abertas**, e MUST NOT considerar as arquivadas. Uma conta arquivada não aparece nas listagens ativas nem nos seletores, logo o ícone dela não compete visualmente com o de uma conta nova e SHALL voltar a ser sugerível.

#### Scenario: Ícone de conta arquivada volta a ser sugerido
- **WHEN** a única conta que usava um determinado ícone do catálogo é arquivada e o usuário abre o formulário de nova conta
- **THEN** aquele ícone volta a ser candidato à sugestão, na sua posição de catálogo

#### Scenario: Conta arquivada não desloca a sugestão
- **WHEN** existem contas arquivadas ocupando os primeiros ícones do catálogo e nenhuma conta aberta usa ícone algum
- **THEN** o formulário de nova conta abre com o primeiro ícone do catálogo

### Requirement: Catálogo esgotado volta ao ícone padrão

Quando **todos** os ícones do catálogo de contas estiverem em uso por contas abertas, o sistema SHALL pré-selecionar o ícone padrão de conta (`wallet`). A pré-seleção é uma conveniência e MUST NOT ser tratada como garantia de unicidade: com o catálogo esgotado, a repetição é o desfecho correto, e o sistema MUST NOT recusar a criação nem exigir que o usuário troque o ícone.

#### Scenario: Todos os ícones do catálogo em uso
- **WHEN** existe pelo menos uma conta aberta para cada ícone do catálogo de contas e o usuário abre o formulário de nova conta
- **THEN** o formulário abre com o ícone padrão de conta e a criação prossegue normalmente

### Requirement: A sugestão é derivada uma única vez, no domínio

A escolha do ícone sugerido SHALL ter **dono único** no domínio da feature de contas — o critério de "em uso", a ordem de preferência e o comportamento no esgotamento vivem em um só lugar. A camada de apresentação SHALL consumir essa derivação e MUST NOT reimplementá-la, nem parcialmente.

A ordem de preferência SHALL ser a ordem do catálogo de contas, e a sugestão SHALL sair desse catálogo — MUST NOT sair do conjunto estendido que o seletor exibe.

Como a apuração depende de leitura do estado das contas, a sugestão SHALL ser aplicada de forma assíncrona ao abrir o formulário, e MUST NOT sobrescrever uma escolha que o usuário já tenha feito: recebida uma seleção manual antes da sugestão ficar pronta, prevalece a do usuário.

#### Scenario: A escolha do usuário vence a sugestão
- **WHEN** o usuário seleciona um ícone no formulário de nova conta antes de a sugestão ficar pronta
- **THEN** o ícone escolhido pelo usuário permanece selecionado e a sugestão é descartada

#### Scenario: A sugestão respeita a ordem do catálogo
- **WHEN** o primeiro ícone do catálogo de contas está em uso e o segundo não
- **THEN** a sugestão é o segundo ícone do catálogo, e não qualquer outro livre
