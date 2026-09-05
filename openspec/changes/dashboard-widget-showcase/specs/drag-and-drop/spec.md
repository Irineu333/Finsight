## ADDED Requirements

### Requirement: Um host por janela desenha o arrasto acima de toda a árvore

O app SHALL prover um **host de arrasto** montado uma vez por janela, acima de todo conteúdo que
participa de arrastos — incluindo o painel de detalhe e a casca —, e SHALL desenhar o **ghost**
do item arrastado numa camada acima desse conteúdo, seguindo o ponteiro. O host SHALL expor o
estado do arrasto em curso à árvore abaixo dele. Nenhuma fonte ou alvo SHALL desenhar o ghost por
conta própria.

O motor SHALL viver num módulo `core`, MUST NOT conhecer feature alguma e MUST NOT depender de
sistema de arrasto do sistema operacional.

#### Scenario: Ghost acima do painel lateral
- **WHEN** um arrasto começa no painel de detalhe e o ponteiro passa sobre o conteúdo central
- **THEN** o ghost é desenhado por cima dos dois, sem ser recortado pela fronteira entre eles

#### Scenario: Um host, várias fontes
- **WHEN** duas superfícies distintas da mesma tela declaram fontes de arrasto
- **THEN** ambas usam o mesmo host, e um arrasto de cada vez está em curso

### Requirement: Fontes declaram o que arrastam e como o arrasto começa

Um composable SHALL poder declarar-se **fonte** de arrasto informando a carga que representa, o
ghost que o host desenha por ele e o gatilho: **imediato** (o arrasto começa no primeiro
movimento após o toque) ou por **toque longo**. Enquanto o seu arrasto está em curso, a fonte
SHALL saber disso para se apresentar como origem (por exemplo, apagada ou como slot).

Com um dispositivo apontador, o gatilho por toque longo SHALL também aceitar pressionar e mover.

#### Scenario: Gatilho imediato
- **WHEN** o usuário pressiona uma fonte de gatilho imediato e move além do limiar de toque
- **THEN** o arrasto começa e o ghost aparece sob o ponteiro

#### Scenario: Gatilho por toque longo
- **WHEN** o usuário pressiona uma fonte de gatilho por toque longo e move antes do toque longo completar
- **THEN** nenhum arrasto começa e o gesto segue para quem estiver por baixo (por exemplo, a rolagem)

#### Scenario: Fonte sabe que é a origem
- **WHEN** o arrasto de uma fonte está em curso
- **THEN** a fonte recebe essa informação e pode alterar a sua apresentação sem consultar o host

### Requirement: Alvos recebem entrada, movimento, saída e soltura por posição

Um composable SHALL poder declarar-se **alvo** de arrasto. O host SHALL determinar, a cada
movimento do ponteiro, o alvo sob ele a partir dos limites de cada alvo em coordenadas de janela,
e SHALL avisar o alvo quando o ponteiro **entra**, **se move** (com a posição relativa ao alvo),
**sai** e **solta**. Quando dois alvos se sobrepõem sob o ponteiro, o de maior prioridade
declarada SHALL receber; a prioridade igual SHALL ser resolvida pelo alvo declarado por último.

Soltar sem alvo sob o ponteiro SHALL **cancelar**: nenhum alvo é avisado de soltura, o arrasto
termina, e a fonte é avisada de que voltou.

#### Scenario: Entrada e saída
- **WHEN** o ponteiro, durante um arrasto, cruza a borda de um alvo para dentro e depois para fora
- **THEN** o alvo recebe entrada ao cruzar para dentro e saída ao cruzar para fora, uma vez cada

#### Scenario: Movimento com posição
- **WHEN** o ponteiro se move dentro de um alvo
- **THEN** o alvo recebe a posição do ponteiro relativa aos seus próprios limites

#### Scenario: Sobreposição resolve por prioridade
- **WHEN** o ponteiro está sobre dois alvos sobrepostos com prioridades distintas
- **THEN** só o de maior prioridade recebe os eventos

#### Scenario: Soltar sem alvo cancela
- **WHEN** o usuário solta com o ponteiro fora de qualquer alvo
- **THEN** nenhum alvo recebe soltura, o ghost some, e a fonte é avisada de que o arrasto terminou sem efeito

### Requirement: O índice de slot é uma função pura da posição e dos limites visíveis

O índice em que um item arrastado seria inserido numa lista SHALL ser calculado por uma função
**pura** que recebe a posição do ponteiro ao longo do eixo da lista e os limites dos itens
visíveis, e devolve um índice. Antes da metade do primeiro item visível o índice SHALL ser o desse
item; depois da metade do último, o seguinte a ele; entre dois itens, o do item cuja metade o
ponteiro ainda não passou. A função MUST NOT depender de Compose nem de estado.

#### Scenario: Antes do primeiro
- **WHEN** o ponteiro está acima da metade do primeiro item visível
- **THEN** o índice é o do primeiro item visível

#### Scenario: Depois do último
- **WHEN** o ponteiro está abaixo da metade do último item visível
- **THEN** o índice é o do último item visível mais um

#### Scenario: Entre dois
- **WHEN** o ponteiro está abaixo da metade do item i e acima da metade do item i+1
- **THEN** o índice é i+1

#### Scenario: Lista vazia
- **WHEN** não há item visível
- **THEN** o índice é zero

### Requirement: Uma lista rola por conta própria enquanto um arrasto se aproxima das bordas

Uma lista rolável que é alvo de arrasto SHALL poder rolar automaticamente enquanto o ponteiro
permanece numa faixa junto à sua borda inicial ou final, na direção dessa borda, com velocidade
que cresce com a proximidade da borda. A rolagem SHALL parar quando o ponteiro sai da faixa, quando
a lista chega ao fim, ou quando o arrasto termina.

#### Scenario: Rola perto da borda
- **WHEN** o ponteiro entra na faixa junto à borda final da lista durante um arrasto
- **THEN** a lista rola em direção ao fim enquanto o ponteiro permanecer ali

#### Scenario: Para no fim
- **WHEN** a lista chega ao seu último item enquanto rola automaticamente
- **THEN** a rolagem para, e o arrasto continua em curso
