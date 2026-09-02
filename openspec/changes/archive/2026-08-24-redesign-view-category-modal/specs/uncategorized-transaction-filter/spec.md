## ADDED Requirements

### Requirement: O valor do eixo pode chegar pré-selecionado por navegação

A lista de lançamentos SHALL aceitar, como estado inicial, um valor do **eixo analítico**
determinado por quem navegou até ela — uma categoria, ou a ausência de classificação. Quando
esse valor chega, o controle de categoria SHALL abrir já selecionado nele, e a lista SHALL
já vir recortada, sem exigir que o usuário repita a escolha.

O valor pré-selecionado SHALL ser um valor do mesmo eixo que o controle oferece, e MUST NOT
ser um segundo mecanismo de recorte ao lado dele. Um recorte que a tela exibisse sem que o
controle o refletisse deixaria o usuário sem como vê-lo nem como desfazê-lo.

O valor SHALL permanecer desfazível como qualquer outro: selecionar o estado neutro
("todas") SHALL desfazê-lo, e o comportamento do controle após isso MUST NOT diferir do de
uma seleção feita dentro da própria tela.

Uma categoria **arquivada** SHALL ser aceita como valor inicial, porque a lista exibe o
histórico de categorias arquivadas. Nesse caso o controle SHALL exibi-la como selecionada
ainda que ela não conste entre as opções oferecidas a quem abre o menu.

Um valor que não resolve para nenhuma categoria existente MUST NOT recortar a lista em
silêncio nem esvaziá-la: a lista SHALL abrir no estado neutro.

#### Scenario: Categoria pré-selecionada
- **WHEN** a lista de lançamentos é aberta a partir do detalhe de uma categoria
- **THEN** o controle de categoria já exibe aquela categoria como selecionada, e a lista já
  vem recortada por ela

#### Scenario: O recorte inicial é desfazível
- **WHEN** a lista foi aberta com uma categoria pré-selecionada e o usuário escolhe o estado
  neutro
- **THEN** o recorte é desfeito e a lista passa a exibir todos os lançamentos do período

#### Scenario: Categoria arquivada como valor inicial
- **WHEN** a lista é aberta a partir do detalhe de uma categoria arquivada
- **THEN** o controle a exibe como selecionada e a lista mostra o histórico dela, ainda que
  ela não seja oferecida no menu

#### Scenario: Valor que não resolve
- **WHEN** a lista é aberta com um valor de eixo que não corresponde a nenhuma categoria
- **THEN** ela abre no estado neutro, sem recorte e sem lista vazia

#### Scenario: Um só mecanismo de recorte
- **WHEN** a lista foi aberta com uma categoria pré-selecionada
- **THEN** o recorte visível na lista é exatamente o que o controle de categoria informa, e
  não existe segundo recorte por categoria fora dele
