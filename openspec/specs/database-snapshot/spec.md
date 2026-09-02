# database-snapshot Specification

## Purpose

O contrato do banco consigo mesmo: capturar o próprio conteúdo num arquivo que abre sozinho,
verificar um arquivo candidato sem se expor a ele, e substituir o próprio conteúdo numa transação —
preservando os invariantes do razão, sem passar pelo ponto único de escrita e sem derrubar quem
observa o banco. Os invariantes verificados são os de `balanced-ledger` e `ledger-dimensions`,
conferidos pelo mesmo código com que as migrações encerram; a ordem de escrita é derivada do schema
gravado, nunca declarada. É o mecanismo do qual `local-backup` é o produto, e nada aqui conhece a
palavra que o produto usa.
## Requirements
### Requirement: O banco captura o próprio conteúdo num arquivo autossuficiente

O banco SHALL saber produzir um arquivo que contenha, sozinho, todo o seu conteúdo no instante da
captura. O arquivo produzido MUST NOT depender de arquivos acompanhantes para ser aberto, e MUST NOT
ser uma cópia direta do arquivo em uso — o banco opera em modo *write-ahead logging*, no qual o
arquivo principal isolado pode não conter sequer o schema.

A captura SHALL preservar a versão de schema, os contadores de chave autoincrementada e a identidade
de schema gravada pelo Room, de modo que o arquivo capturado seja indistinguível de um banco do app
para quem o abrir.

A captura MUST NOT ser feita a partir de um estado parcialmente escrito: um dado não confirmado por
transação no momento da captura SHALL ficar de fora, e nunca aparecer pela metade.

#### Scenario: O arquivo capturado abre sozinho
- **WHEN** um banco com escritas recentes ainda não consolidadas é capturado, e o arquivo resultante
  é movido isoladamente para outra máquina
- **THEN** ele abre e apresenta todo o conteúdo do instante da captura, sem nenhum arquivo
  acompanhante

#### Scenario: A identidade do schema sobrevive
- **WHEN** um arquivo capturado é aberto pelo app
- **THEN** sua versão de schema e sua identidade de schema são as do banco de origem, e o app o
  reconhece como um banco seu

#### Scenario: Escrita em curso não entra pela metade
- **WHEN** uma transação está aberta e não confirmada em outra conexão no momento da captura
- **THEN** o arquivo capturado não contém nenhuma parte dessa transação

### Requirement: Um arquivo candidato é verificado sem expor o banco em uso

A verificação de um arquivo candidato SHALL ocorrer em isolamento do banco em uso. O arquivo MUST
NOT ser anexado, aberto ou lido pela conexão que serve o app antes de ser aprovado.

O motivo é uma propriedade do mecanismo, não uma precaução: a corrupção de um banco anexado é
reportada como corrupção **da conexão**, o que faria um arquivo defeituoso disparar o tratamento de
corrupção contra o banco de produção.

A verificação SHALL cobrir, no mínimo: a integridade estrutural do arquivo, a evidência de que ele
foi escrito por este app, a versão de schema declarada, a passagem pela cadeia de migrações do app,
e os invariantes do razão — soma zero de entries por transação e moeda, ausência de dimensão órfã,
ausência de violação de chave estrangeira e dimensão pousando em tipo de conta que sua espécie
admite.

A verificação MUST NOT criar o que deveria apenas conferir: um arquivo ausente, vazio ou sem schema
SHALL ser reprovado, nunca preenchido com um schema novo e aprovado em seguida.

Os invariantes do razão SHALL ser verificados pelo mesmo código que as migrações do banco já usam.
MUST NOT existir uma segunda implementação de "o razão está equilibrado".

#### Scenario: Arquivo corrompido não contamina a produção
- **WHEN** um arquivo estruturalmente corrompido é submetido à verificação
- **THEN** ele é reprovado, e nenhum tratamento de corrupção é disparado contra o banco em uso

#### Scenario: A verificação usa o verificador existente
- **WHEN** os invariantes do razão são verificados num arquivo candidato
- **THEN** são executadas as mesmas verificações aplicadas ao final das migrações de schema, e não
  uma reimplementação delas

#### Scenario: Arquivo que é um banco, mas não deste app
- **WHEN** um arquivo SQLite íntegro que não foi escrito por este app é submetido à verificação —
  um banco sem tabelas, um banco de outro aplicativo, ou o arquivo principal de um banco cujo
  conteúdo ficou no journal
- **THEN** ele é reprovado, e MUST NOT ser tratado como um acervo vazio válido

#### Scenario: Versão de schema além da conhecida
- **WHEN** o arquivo declara versão de schema superior à do app
- **THEN** ele é reprovado antes de qualquer tentativa de abertura pelo Room, e a causa é
  distinguível de um arquivo inválido

### Requirement: O banco substitui o próprio conteúdo numa transação

A substituição do conteúdo por um arquivo aprovado SHALL ocorrer numa única transação: ou todo o
conteúdo passa a ser o do arquivo, ou nada muda.

Ao fim da substituição o banco SHALL satisfazer os mesmos invariantes exigidos de qualquer escrita —
soma zero por transação e moeda, dimensão pousando no tipo de conta correto e integridade
referencial. A substituição MUST NOT ser um caminho pelo qual dados entrem sem que esses invariantes
valham.

A substituição MUST NOT passar pelo ponto único de escrita do razão: aquele ponto **completa
intenções** — cria conta de sistema sob demanda e lança resíduo de conversão —, e aplicá-lo a
lançamentos já completos os reinterpretaria. O invariante é garantido por verificação sobre o
resultado, não por reconstrução.

Estruturas internas de controle do banco — a identidade de schema e os contadores de sequência —
MUST NOT ser copiadas do arquivo.

#### Scenario: Falha no meio não deixa acervo pela metade
- **WHEN** a substituição falha depois de já ter escrito parte das tabelas
- **THEN** o banco volta integralmente ao conteúdo anterior

#### Scenario: Os invariantes valem depois da troca
- **WHEN** uma substituição conclui
- **THEN** o banco satisfaz soma zero por transação e moeda, não tem dimensão órfã, não tem
  violação de chave estrangeira e não tem dimensão pousada em tipo de conta que sua espécie não
  admite

#### Scenario: O ponto único de escrita não é usado
- **WHEN** as linhas do arquivo são gravadas
- **THEN** elas são gravadas como estão, sem que contas de sistema sejam criadas ou resíduos de
  conversão sejam lançados por causa da restauração

### Requirement: A substituição não derruba quem observa o banco

A substituição MUST NOT fechar a conexão do banco, encerrar seu pool ou invalidar as instâncias já
distribuídas por injeção de dependência.

Os fluxos reativos já em coleta SHALL ser notificados da mudança pelo mecanismo ordinário de
invalidação do banco, de modo que reemitam sozinhos. MUST NOT ser necessária qualquer ação do
usuário, nem reinício do app, para que as telas passem a refletir o novo conteúdo.

#### Scenario: Fluxo em coleta reemite
- **WHEN** um fluxo reativo sobre uma tabela está sendo coletado e essa tabela é substituída
- **THEN** o fluxo reemite com o conteúdo novo

#### Scenario: A instância do banco continua válida
- **WHEN** a substituição conclui
- **THEN** todas as instâncias já injetadas continuam operando sobre o mesmo banco, sem erro de
  conexão encerrada

### Requirement: A ordem de escrita é derivada do schema, não declarada

A ordem em que as tabelas são esvaziadas e repovoadas SHALL ser derivada das chaves estrangeiras
declaradas no próprio banco, no momento da operação.

MUST NOT existir uma lista fixa de tabelas ou de ordem mantida à mão: uma lista precisaria ser
lembrada a cada alteração de schema, e o esquecimento se manifestaria como falha de restauração
muito depois da alteração que a causou.

#### Scenario: Entidade nova não exige manutenção
- **WHEN** uma entidade com chave estrangeira é acrescentada ao banco numa versão futura
- **THEN** a substituição a contempla sem qualquer alteração no código que a executa

#### Scenario: Uma ordem derivada incorretamente não grava um acervo inconsistente
- **WHEN** a ordem derivada põe uma tabela antes de outra da qual ela depende
- **THEN** a substituição ou aborta e o banco volta ao conteúdo anterior, ou conclui com o conteúdo
  do arquivo por inteiro — nunca com um estado que o arquivo não descreve

### Requirement: O banco se copia antes de aplicar migrações, quando lhe dão um destino

Antes de aplicar migrações a um arquivo existente, o banco SHALL capturar o próprio conteúdo — com
o mesmo mecanismo que já produz um arquivo autossuficiente — **se, e somente se, um destino para
essa cópia lhe for informado**.

A captura SHALL acontecer antes de a primeira migração escrever qualquer coisa. Determinar se há
migração a aplicar SHALL ser feito lendo a versão gravada no arquivo em isolamento, sem abrir o
banco pela camada que dispara as migrações — abrir para descobrir já teria migrado.

O destino MUST NOT ser escolhido aqui, e este módulo MUST NOT consultar preferência alguma para
decidir se captura: receber um destino é a decisão, e ela pertence a quem monta o banco. Um destino
informado significa capturar; nenhum destino significa não capturar. Remover a cópia anterior é de
quem informou o destino, como toda a limpeza de arquivo deste módulo.

Uma captura que falhe MUST NOT impedir a migração de acontecer: a alternativa seria um app que não
abre. A falha SHALL ser registrada, e a migração SHALL prosseguir.

#### Scenario: Atualização com migração e destino informado
- **WHEN** o banco é aberto com um destino para a cópia e a versão do arquivo é menor que a do app
- **THEN** uma cópia do conteúdo anterior é escrita antes de a primeira migração rodar, e ela abre
  sozinha como qualquer arquivo capturado

#### Scenario: Nenhum destino informado
- **WHEN** o banco é aberto sem destino para a cópia e há migração a aplicar
- **THEN** nenhuma cópia é capturada, e a migração é aplicada normalmente

#### Scenario: Abertura sem migração pendente
- **WHEN** o banco é aberto com destino informado e a versão do arquivo é igual à do app
- **THEN** nenhuma cópia é capturada

#### Scenario: Instalação nova
- **WHEN** o banco é aberto pela primeira vez e o arquivo ainda não existe
- **THEN** nenhuma cópia é capturada, e nada é criado para ser copiado

#### Scenario: Captura que falha não trava o app
- **WHEN** a captura anterior à migração falha por falta de espaço
- **THEN** a migração é aplicada assim mesmo e o app abre
