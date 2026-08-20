## ADDED Requirements

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

A verificação SHALL cobrir, no mínimo: a assinatura de banco SQLite, a integridade estrutural do
arquivo, a versão de schema declarada, a passagem pela cadeia de migrações do app, e os invariantes
do razão — soma zero de entries por transação e moeda, ausência de dimensão órfã e ausência de
violação de chave estrangeira.

Os invariantes do razão SHALL ser verificados pelo mesmo código que as migrações do banco já usam.
MUST NOT existir uma segunda implementação de "o razão está equilibrado".

#### Scenario: Arquivo corrompido não contamina a produção
- **WHEN** um arquivo estruturalmente corrompido é submetido à verificação
- **THEN** ele é reprovado, e nenhum tratamento de corrupção é disparado contra o banco em uso

#### Scenario: A verificação usa o verificador existente
- **WHEN** os invariantes do razão são verificados num arquivo candidato
- **THEN** são executadas as mesmas verificações aplicadas ao final das migrações de schema, e não
  uma reimplementação delas

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
- **THEN** o banco satisfaz soma zero por transação e moeda, não tem dimensão órfã e não tem
  violação de chave estrangeira

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

#### Scenario: A derivação é conferida
- **WHEN** a substituição conclui
- **THEN** a integridade referencial é verificada, de modo que uma ordem derivada incorretamente
  seja detectada em vez de gravar um acervo inconsistente
