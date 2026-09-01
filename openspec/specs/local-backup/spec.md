# local-backup Specification

## Purpose

O backup como promessa ao usuário: o que o arquivo contém e o que ele deliberadamente deixa de
fora, que restaurar substitui o acervo inteiro — e que só há como voltar atrás quando uma cópia do
estado anterior foi guardada antes —, o que é conferido antes de o banco em uso ser tocado, o que a
tela mostra antes de pedir a confirmação, e que o app não delega backup a mecanismo automático de
plataforma nenhuma. O mecanismo — capturar, verificar e substituir — é de `database-snapshot`; as
cópias que o app guarda sozinho, quando o usuário liga isso, são de `automatic-backup`. O que esta
capacidade governa é a promessa feita a quem digitou os lançamentos, incluindo a consequência de
recusar o backup automático da plataforma: recuperar os dados em outro aparelho passa a depender de
um arquivo que esteja fora do aparelho.
## Requirements
### Requirement: O backup contém os dados, e o usuário sabe disso

Um backup SHALL conter todo o acervo do usuário — plano de contas, transações, entries, dimensões,
categorias, cartões, faturas, parcelamentos, orçamentos, recorrentes, moedas e taxas de câmbio.

Um backup MUST NOT conter as preferências de exibição mantidas fora do banco — moeda base, layout do
dashboard, estado de sincronização de taxas, estado da janela. Restaurar um acervo em outra
instalação SHALL preservar os dados e deixar as preferências daquela instalação intactas.

A tela SHALL declarar o que o arquivo contém, para que a diferença entre "meus dados" e "meu app
como eu o deixei" não seja descoberta durante uma restauração.

#### Scenario: Preferência local sobrevive à restauração
- **WHEN** um usuário com moeda base `USD` restaura um backup exportado numa instalação cuja moeda
  base era `BRL`
- **THEN** todo o acervo passa a ser o do backup e a moeda base em vigor continua `USD`

#### Scenario: O acervo é restaurado por inteiro
- **WHEN** um backup é restaurado
- **THEN** cada uma das entidades do acervo reflete o conteúdo do arquivo, e nenhuma tabela de dados
  do usuário permanece com conteúdo da instalação anterior

### Requirement: Restaurar substitui todo o acervo

A restauração SHALL substituir integralmente os dados do usuário pelos do arquivo. Ela MUST NOT
mesclar, deduplicar, reconciliar ou preservar seletivamente qualquer registro anterior.

O que a tela diz sobre desfazer SHALL depender de existir ou não uma cópia do estado anterior. Sem
o gatilho preventivo em vigor, a operação SHALL ser apresentada como irreversível, e a tela MUST
NOT sugerir que o estado anterior possa ser recuperado pelo app. Com ele em vigor, a tela SHALL
dizer que uma cópia do estado atual é guardada antes da troca, e MUST NOT continuar chamando a
operação de irreversível — a substituição continua total, e é o retorno a ela que deixou de ser
impossível.

Prometer o retorno SHALL depender de a cópia ter sido escrita: uma captura preventiva que falhou
MUST NOT ser apresentada como proteção, e a restauração nesse caso volta a ser oferecida como
irreversível.

#### Scenario: Nada do acervo anterior sobrevive
- **WHEN** a instalação tem uma conta "Carteira" que não existe no backup, e o backup é restaurado
- **THEN** "Carteira" deixa de existir, e nenhuma transação a ela vinculada permanece

#### Scenario: Não há mesclagem por semelhança
- **WHEN** a instalação e o backup têm, cada um, uma categoria chamada "Mercado"
- **THEN** a categoria resultante é a do backup, e o app MUST NOT tentar unir as duas nem manter as
  duas

#### Scenario: Sem preventivo, a confirmação diz que não dá para desfazer
- **WHEN** a confirmação de restauração é exibida com o gatilho preventivo desligado
- **THEN** ela declara que a operação não pode ser desfeita

#### Scenario: Com preventivo, a confirmação diz que há uma cópia
- **WHEN** a confirmação de restauração é exibida com o gatilho preventivo ligado
- **THEN** ela declara que o acervo atual é guardado antes da troca, e não afirma que a operação é
  irreversível

### Requirement: Um arquivo só altera o acervo depois de aprovado por inteiro

Antes de qualquer alteração no banco em uso, o arquivo escolhido SHALL ser aprovado em todas as
verificações: ser um banco SQLite legível e íntegro, declarar uma versão de schema que este app
saiba abrir, sobreviver à cadeia de migrações do app, e satisfazer os invariantes do razão —
Σ entries = 0 por `(transactionId, currency)`, nenhuma entry apontando para dimensão inexistente e
nenhuma violação de chave estrangeira.

Um arquivo reprovado em qualquer verificação MUST NOT produzir alteração alguma no acervo em uso, e
o app SHALL permanecer utilizável exatamente como antes da tentativa.

A verificação MUST NOT ser feita sobre a conexão do banco em uso: um arquivo corrompido SHALL ser
avaliado em isolamento, para que sua corrupção não possa ser atribuída ao banco de produção.

#### Scenario: Arquivo que não é um banco
- **WHEN** o usuário escolhe um arquivo que não começa com a assinatura de um banco SQLite
- **THEN** a restauração é recusada com mensagem própria, e o acervo em uso permanece intacto

#### Scenario: Razão desequilibrado no arquivo
- **WHEN** o arquivo é um banco legível, mas contém uma transação cujas entries não somam zero numa
  moeda
- **THEN** a restauração é recusada, o acervo em uso permanece intacto, e a mensagem diz que o
  arquivo não é um backup válido sem exibir detalhes contábeis ao usuário

#### Scenario: Backup de uma versão mais nova do app
- **WHEN** o arquivo declara uma versão de schema maior que a deste app — o caso de um backup feito
  numa plataforma atualizada e restaurado numa desatualizada
- **THEN** a restauração é recusada com mensagem que atribui a causa à versão do app e pede sua
  atualização, em vez de apresentar o arquivo como inválido

### Requirement: O usuário identifica o backup antes de confirmar

Antes de executar uma restauração, o app SHALL apresentar o que sabe sobre o arquivo já aprovado:
quando foi criado, em que plataforma, por qual versão do app, e o tamanho do acervo que ele contém.

A confirmação MUST NOT ser pedida antes de o arquivo ser aprovado — perguntar sobre um arquivo que
ainda pode ser recusado transfere ao usuário uma decisão que o app ainda não pode sustentar.

#### Scenario: A confirmação identifica o arquivo
- **WHEN** um arquivo é aprovado e a confirmação é exibida
- **THEN** ela mostra a data de criação, a plataforma de origem, a versão do app que o gerou e as
  contagens do acervo, além do aviso de que a operação substitui tudo e é irreversível

#### Scenario: Arquivo antigo sem metadados
- **WHEN** um arquivo aprovado não carrega os metadados de origem
- **THEN** a confirmação ainda é exibida, informando as contagens do acervo e declarando que a
  origem é desconhecida

### Requirement: Restaurar não exige reiniciar o app

Concluída a restauração, as telas SHALL passar a refletir o acervo restaurado sem que o usuário
precise fechar, reabrir ou reiniciar o app, em todas as plataformas suportadas.

O app MUST NOT encerrar o próprio processo, derrubar a conexão do banco ou invalidar instâncias em
uso como parte da restauração.

#### Scenario: A tela reflete o novo acervo
- **WHEN** uma restauração conclui com o usuário parado numa tela que lista dados do acervo
- **THEN** aquela tela passa a exibir os dados do backup sem interação adicional

#### Scenario: Restauração no iOS
- **WHEN** uma restauração conclui no iOS, onde reiniciar o processo por conta própria não é
  admissível
- **THEN** ela conclui pelo mesmo caminho das demais plataformas, sem reinício

### Requirement: O êxito de uma operação é dito

Concluída uma exportação em que o usuário escolheu um destino, ou uma restauração, o app SHALL
dizer que ela concluiu. Nenhuma das duas deixa na tela algo que sirva de prova: a exportação
entrega o arquivo a um lugar que o app não lê de volta, e a restauração fecha a própria
confirmação e devolve o usuário à mesma tela de onde partiu.

Fechar o seletor sem escolher destino MUST NOT ser apresentado como êxito. Quem não salvou nada
não falhou em nada e não realizou nada, e dizer "backup concluído" nesse caso é pior que o
silêncio, porque afirma que existe um arquivo que não existe.

#### Scenario: A exportação que salvou diz que salvou
- **WHEN** uma exportação conclui com o usuário tendo escolhido um destino
- **THEN** o app diz que o backup foi salvo

#### Scenario: A exportação que não salvou não diz nada
- **WHEN** o usuário fecha o seletor de destino sem escolher um
- **THEN** o app não diz nada, nem de êxito nem de falha

#### Scenario: A restauração diz que concluiu
- **WHEN** uma restauração conclui sem erro
- **THEN** o app diz que o acervo passou a ser o do arquivo

### Requirement: O app não delega backup à plataforma

O app MUST NOT depender de mecanismos automáticos de backup do sistema operacional para preservar o
acervo, e SHALL desativá-los onde puder — tanto o backup em nuvem quanto a transferência entre
aparelhos.

O app MUST NOT guardar cópias do acervo por conta própria enquanto o usuário não pedir, e isso
SHALL valer sem exceção — inclusive antes de o próprio app reescrever o banco numa migração de
schema. Ligado o cofre, ele passa a guardar as cópias que o usuário configurou, no destino que o
usuário escolheu, e a tela SHALL declarar o que aquele destino não cobre. Nenhuma dessas cópias
SHALL sair do aparelho por iniciativa do app.

Consequência que o requisito existe para tornar explícita: o comportamento em relação à plataforma
passa a ser o mesmo nas três, e a recuperação do acervo ao trocar de aparelho SHALL depender
exclusivamente de um arquivo que esteja fora do aparelho — exportado pelo usuário, ou escrito pelo
cofre numa pasta que o usuário mantenha sincronizada. A tela SHALL comunicar essa dependência, em
vez de deixá-la ser descoberta na troca de aparelho.

#### Scenario: Nenhum backup automático em nenhuma plataforma
- **WHEN** o app é instalado em qualquer plataforma suportada
- **THEN** nenhum mecanismo automático do sistema é usado para copiar o banco, nem para a nuvem nem
  para transferência entre aparelhos

#### Scenario: A tela diz de quem é a responsabilidade
- **WHEN** o usuário abre a tela de backup com o cofre desligado
- **THEN** ela declara que o app não guarda cópias por conta própria e que recuperar os dados em
  outro aparelho depende do arquivo exportado

#### Scenario: Com o cofre ligado, a tela diz o que o destino não cobre
- **WHEN** o usuário abre a tela de backup com o cofre ligado e as cópias no armazenamento do app
- **THEN** ela declara que o app guarda cópias, que elas ficam neste aparelho e dentro do app, e que
  desinstalar o app as leva junto

### Requirement: O arquivo é portável entre plataformas

Um backup exportado numa plataforma suportada SHALL ser restaurável em qualquer outra, desde que a
versão de schema do arquivo seja compreendida pelo app que restaura.

O app MUST NOT depender da extensão ou do tipo declarado pelo sistema de arquivos para decidir se um
arquivo é um backup: essa decisão SHALL ser tomada pelo conteúdo.

#### Scenario: Exportado no Android, restaurado no desktop
- **WHEN** um backup é exportado no Android e escolhido para restauração no desktop
- **THEN** ele é aceito e restaurado, sem conversão de formato

#### Scenario: Seleção não filtrada por tipo
- **WHEN** o usuário escolhe o arquivo, e ele está num provedor externo que declara um tipo distinto
  do declarado pelo armazenamento local
- **THEN** o arquivo continua selecionável, e sua validade é decidida pelo conteúdo após a escolha
