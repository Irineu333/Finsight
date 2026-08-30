## MODIFIED Requirements

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
