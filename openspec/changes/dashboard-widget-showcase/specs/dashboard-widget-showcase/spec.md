## ADDED Requirements

### Requirement: O editor separa o que está no dashboard do que pode entrar

O modo de edição do dashboard SHALL apresentar duas superfícies distintas: a **lista**, com
exatamente os widgets que estão no dashboard, na ordem em que aparecem; e a **vitrine**, com os
widgets que podem ser adicionados. A lista MUST NOT conter seção, cabeçalho ou placeholder que
represente widgets ausentes do dashboard.

A vitrine SHALL ser **derivada**: o catálogo de tipos de widget não deprecados, menos os tipos que
estão na lista, na ordem do catálogo. O editor MUST NOT guardar a vitrine como estado próprio nem
persistir o que ela contém. Enquanto a identidade de um widget for o seu tipo, um tipo presente na
lista MUST NOT ser oferecido pela vitrine.

#### Scenario: Lista só com o que está no dashboard
- **WHEN** o usuário entra no modo de edição com um dashboard de três widgets
- **THEN** a lista mostra exatamente esses três, na ordem do dashboard, e nada além deles

#### Scenario: Vitrine é o catálogo menos a lista
- **WHEN** o catálogo tem N tipos não deprecados e a lista contém K deles
- **THEN** a vitrine oferece exatamente os N − K restantes, na ordem do catálogo

#### Scenario: Vitrine acompanha a lista
- **WHEN** um widget é adicionado à lista ou removido dela
- **THEN** a vitrine deixa de oferecê-lo ou volta a oferecê-lo, respectivamente, sem que nada seja salvo

#### Scenario: Dashboard completo
- **WHEN** todos os tipos não deprecados estão na lista
- **THEN** a vitrine exibe um estado vazio que diz que todos os widgets estão no dashboard

### Requirement: O arrasto é o único verbo entre a vitrine e a lista

Adicionar, remover e reordenar widgets SHALL ser feitos por arrasto, e por nenhum outro controle.
Arrastar um card da vitrine e soltá-lo sobre a lista SHALL adicionar o widget, com a configuração
padrão do tipo, **na posição do slot** que a lista abriu sob o ponteiro. Arrastar um item da lista e
soltá-lo sobre a vitrine SHALL removê-lo do dashboard, descartando a configuração daquele widget.
Arrastar um item da lista e soltá-lo noutra posição da lista SHALL reordenar. Soltar fora de
qualquer alvo SHALL devolver o item ao lugar de origem sem alterar nada.

O editor MUST NOT oferecer botão para adicionar ou remover um widget, nem comando que adicione ou
remova vários de uma vez.

Enquanto um arrasto atravessa a lista, ela SHALL abrir um **slot** na posição em que o item seria
inserido, deslocando os vizinhos, e SHALL rolar por conta própria quando o ponteiro se aproxima
das suas bordas. O slot SHALL acompanhar o ponteiro enquanto ele se move.

#### Scenario: Adicionar na posição do slot
- **WHEN** o usuário arrasta um card da vitrine até entre o segundo e o terceiro item da lista e solta
- **THEN** o widget passa a ser o terceiro item da lista, com a configuração padrão do seu tipo, e sai da vitrine

#### Scenario: Remover pela vitrine
- **WHEN** o usuário arrasta um item da lista até a vitrine e solta
- **THEN** o widget sai da lista, volta a ser oferecido pela vitrine, e a configuração que tinha é descartada

#### Scenario: Reordenar
- **WHEN** o usuário arrasta o primeiro item da lista até depois do segundo e solta
- **THEN** os dois trocam de posição e nenhum widget é adicionado ou removido

#### Scenario: Soltar em lugar nenhum
- **WHEN** o usuário solta um item fora da lista e fora da vitrine
- **THEN** a lista e a vitrine ficam exatamente como estavam antes do arrasto

#### Scenario: Sem comandos em massa
- **WHEN** o usuário está no modo de edição
- **THEN** não há controle que adicione ou remova widgets sem um arrasto, individual ou em massa

#### Scenario: A lista rola durante o arrasto
- **WHEN** um arrasto está em curso e o ponteiro chega perto da borda inferior ou superior da lista
- **THEN** a lista rola nessa direção enquanto o ponteiro permanecer ali, e o slot continua acompanhando o ponteiro

### Requirement: A vitrine vive na mesma árvore de composição que a lista

A vitrine SHALL ser composta na **mesma árvore de composição** da lista, de modo que um arrasto
iniciado numa alcance a outra. A vitrine MUST NOT ser um `ModalBottomSheet`, `Dialog`, `Popup` ou
qualquer outra raiz de composição própria.

Abaixo do breakpoint de janela extra-larga, a vitrine SHALL ser uma **sheet inline** ancorada
embaixo da tela de edição, com três posições: recolhida (peek), meia altura e expandida. Em
repouso ela SHALL estar a meia altura. Enquanto um arrasto está em curso — em qualquer sentido —
ela SHALL recolher-se ao peek, expondo a lista, e a área do peek SHALL ser o alvo de remoção; ao
terminar o arrasto ela SHALL voltar à posição em que estava.

Em janela extra-larga, a vitrine SHALL ser um detalhe **pane-only** exibido no painel de detalhe,
aberto quando o editor entra e dispensado quando ele sai; o painel inteiro SHALL ser o alvo de
remoção. A decisão entre as duas apresentações SHALL usar o mesmo eixo de largura que decide o
painel de detalhe. Se a janela cruza esse breakpoint com o editor aberto, a apresentação SHALL
trocar sem sair do modo de edição e sem perder as alterações não confirmadas.

#### Scenario: Sheet em repouso
- **WHEN** o usuário entra no modo de edição numa janela abaixo do breakpoint extra-largo
- **THEN** a vitrine aparece como sheet inline a meia altura, com previews em largura total roláveis verticalmente

#### Scenario: Sheet recolhe durante o arrasto
- **WHEN** o usuário levanta um card da vitrine ou um item da lista
- **THEN** a sheet recolhe ao peek, a lista inteira fica visível, e o peek mostra que soltar ali remove

#### Scenario: Sheet volta ao soltar
- **WHEN** o arrasto termina, com ou sem efeito
- **THEN** a sheet volta à posição em que estava antes do arrasto

#### Scenario: Painel em janela extra-larga
- **WHEN** o usuário entra no modo de edição numa janela extra-larga
- **THEN** a vitrine ocupa o painel de detalhe à direita, nenhuma sheet é ancorada embaixo, e sair do editor dispensa o painel

#### Scenario: Arrasto atravessa as duas superfícies
- **WHEN** o usuário arrasta um card da vitrine — sheet ou painel — e o move sobre a lista
- **THEN** a lista abre o slot sob o ponteiro, sem que o ponteiro tenha sido perdido ao cruzar a fronteira entre as superfícies

#### Scenario: Cruzar o breakpoint com o editor aberto
- **WHEN** a janela é redimensionada através do breakpoint extra-largo durante a edição
- **THEN** a vitrine troca de apresentação, o editor continua aberto e as alterações feitas até ali permanecem

### Requirement: O card da vitrine é o widget verdadeiro, inerte, com alça de arrasto

Cada card da vitrine SHALL renderizar o próprio widget, na variante de **preview** com dados
fictícios, e MUST NOT reagir a toques como o widget real reagiria — nenhuma navegação, nenhuma
ação. Cada card da vitrine e cada item da lista SHALL expor uma **alça de arrasto** que inicia o
arrasto no primeiro movimento, sem toque longo; o corpo do card SHALL iniciar o arrasto por toque
longo.

Tocar num item da lista SHALL abrir as configurações daquele widget como **modal transitório**,
por cima do editor, sem usar o painel de detalhe.

#### Scenario: Preview inerte
- **WHEN** o usuário toca num card da vitrine fora da alça
- **THEN** nada navega e nenhuma ação do widget é executada

#### Scenario: Alça arrasta no primeiro movimento
- **WHEN** o usuário pressiona a alça de um card e move o ponteiro
- **THEN** o arrasto começa imediatamente, sem esperar um toque longo

#### Scenario: Corpo arrasta por toque longo
- **WHEN** o usuário pressiona e segura o corpo de um item da lista e então move
- **THEN** o arrasto começa após o toque longo

#### Scenario: Configurações por cima do editor
- **WHEN** o usuário toca num item da lista
- **THEN** as configurações do widget abrem como modal transitório, o editor permanece por baixo e, em janela extra-larga, a vitrine continua no painel

### Requirement: Apagado em modo edição significa fora desta largura de janela

No modo de edição, um widget cujo conjunto de modos de janela não inclui o modo atual SHALL
aparecer **apagado** e com uma legenda que diga que ele não aparece nesta largura de janela — tanto
como card da vitrine quanto como item da lista. Ele SHALL continuar arrastável nos dois lugares:
o dashboard é um só, e o editor edita o que vale em todos os dispositivos. Um widget ativo nessa
condição MUST NOT sumir da lista.

#### Scenario: Card apagado na vitrine
- **WHEN** a vitrine oferece um tipo que só se mostra em janela compacta e a janela é larga
- **THEN** o card aparece apagado, com a legenda, e ainda pode ser arrastado para a lista

#### Scenario: Item apagado na lista
- **WHEN** a lista contém um widget que não se mostra no modo de janela atual
- **THEN** o item aparece apagado, com a legenda, na sua posição, e ainda pode ser reordenado ou removido

### Requirement: Confirmar persiste, cancelar restaura, e o que se salva não muda

Confirmar o editor SHALL persistir a lista — chave do tipo, posição e configuração de cada widget
— no mesmo formato de antes desta capacidade; a vitrine MUST NOT deixar rastro na persistência.
Cancelar SHALL descartar toda adição, remoção, reordenação e configuração feita desde a entrada no
editor. Um layout salvo antes desta capacidade SHALL reabrir com os mesmos widgets, na mesma ordem
e com a mesma configuração.

#### Scenario: Confirmar grava a lista
- **WHEN** o usuário adiciona um widget pela vitrine, reordena e confirma
- **THEN** o dashboard mostra a lista resultante, e ela sobrevive ao reinício do app

#### Scenario: Cancelar descarta tudo
- **WHEN** o usuário remove widgets pela vitrine e cancela
- **THEN** o dashboard fica exatamente como estava antes de entrar no editor

#### Scenario: Layout anterior reabre idêntico
- **WHEN** um dashboard salvo antes desta capacidade é carregado
- **THEN** ele exibe os mesmos widgets, na mesma ordem e com a mesma configuração, sem reescrever a preferência
