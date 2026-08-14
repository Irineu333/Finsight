## ADDED Requirements

### Requirement: Toda escrita originada de agente é registrada

Cada chamada de tool que escreve SHALL produzir exatamente um registro, independentemente de
quantas linhas ela tenha gravado. O registro SHALL conter o instante, o cliente que fez a
chamada, o nome da tool, os argumentos como recebidos, o desfecho e os identificadores do que
foi tocado.

Chamadas **recusadas** — por permissão ou por regra do domínio — SHALL ser registradas
também: são justamente o que alguém procura ao investigar por que algo não aconteceu.

O cliente SHALL ser registrado pelo que ele **declara sobre si em cada requisição**. O protocolo
não tem handshake e essa identificação é recomendada, não obrigatória: o campo SHALL aceitar
ausência sem que isso seja falha.

A identificação é autodeclarada e **não autenticada** — ela diz quem afirmou ser, não quem é. O
que autentica é o token, e ele é o mesmo para todos os clientes. A apresentação dessa informação
MUST NOT afirmá-la como fato verificado.

O token MUST NOT constar de nenhum campo do registro, os argumentos gravados incluídos.

Os registros SHALL ter política de retenção declarada. Eles guardam os argumentos como
recebidos — o que inclui extratos inteiros, com descrições, valores e contrapartes — e um
registro sem prazo é uma segunda cópia perpétua do histórico financeiro do usuário.

#### Scenario: Escrita bem-sucedida
- **WHEN** uma tool de escrita conclui
- **THEN** existe um registro com o desfecho de sucesso e os identificadores do que foi criado
  ou alterado

#### Scenario: Escrita recusada pelo domínio
- **WHEN** uma tool de escrita é recusada por uma regra de negócio
- **THEN** existe um registro com o desfecho de recusa e o erro que a causou, e nada foi gravado

#### Scenario: Uma chamada, um registro
- **WHEN** uma única chamada de tool grava várias linhas
- **THEN** existe um registro só, referenciando todos os identificadores tocados

#### Scenario: Cliente que não se identifica
- **WHEN** uma escrita chega sem o cliente ter declarado quem é
- **THEN** o registro é gravado com o cliente desconhecido, e a chamada não falha por isso

#### Scenario: A etiqueta não é apresentada como fato
- **WHEN** a atividade é apresentada ao usuário
- **THEN** o cliente aparece como identificação declarada, sem afirmar que foi verificada

### Requirement: Leituras não são registradas

Chamadas de tool que apenas leem MUST NOT produzir registro. O volume afogaria as escritas,
que são o motivo de o registro existir.

#### Scenario: Consulta não polui
- **WHEN** um agente faz muitas chamadas de leitura
- **THEN** o registro de atividade permanece inalterado

### Requirement: O registro é jornal da aplicação, não parte do razão

O registro SHALL viver ao lado das entidades de facade, e MUST NOT ser representado como
coluna do razão nem como dimensão. Nenhuma regra do domínio ramifica em quem originou a
escrita, e nenhuma figura é calculada de forma diferente por isso.

Nenhuma leitura de saldo, gasto, fatura ou patrimônio SHALL consultar o registro.

#### Scenario: Razão indiferente à origem
- **WHEN** um lançamento é criado por agente e outro idêntico é criado pela interface
- **THEN** as duas transações são indistinguíveis para toda leitura do razão, e produzem as
  mesmas figuras

#### Scenario: Registro removido não corrompe dado
- **WHEN** registros de atividade são removidos
- **THEN** as transações que eles descreviam permanecem íntegras e inalteradas

### Requirement: A atividade é consultável no app e leva à operação inversa

A configuração SHALL apresentar a atividade recente, atualizando-se reativamente conforme
novos registros chegam.

Um registro que tocou uma entidade alcançável na interface SHALL permitir navegar até ela, de
onde a operação inversa — quando o domínio a oferece — já está disponível. MUST NOT existir
um comando genérico de desfazer: nem toda operação tem inversa, e algumas têm inversa que não
restaura o estado anterior.

#### Scenario: Atividade aparece sem recarregar
- **WHEN** um agente escreve enquanto a tela de atividade está aberta
- **THEN** o novo registro aparece sem ação do usuário

#### Scenario: Do registro até a entidade
- **WHEN** o usuário abre um registro que criou uma transação
- **THEN** ele alcança essa transação na interface

#### Scenario: Nenhum desfazer universal
- **WHEN** a tela de atividade é inspecionada
- **THEN** não existe comando que prometa reverter qualquer registro
