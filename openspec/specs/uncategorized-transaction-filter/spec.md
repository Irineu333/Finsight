# uncategorized-transaction-filter Specification

## Purpose

O comando que recorta uma lista de lançamentos pelo eixo analítico: que o filtro de categoria seleciona um valor do eixo (uma categoria ou a ausência de classificação) e não uma categoria; o que exatamente o recorte sem classificação contém; quando o valor é oferecido e quando é omitido; como o controle se nomeia sem se passar por categoria; e que a regra vale igual em toda superfície que oferece o filtro. A definição do que é "sem classificação" vem do razão (`ledger-reporting`) e é a mesma que o detalhamento usa (`uncategorized-spending-breakdown`); o que esta capacidade governa é o que os filtros fazem com ela.

## Requirements

### Requirement: O filtro de categoria seleciona sobre o eixo, não sobre a categoria

O controle que recorta uma lista de lançamentos por categoria SHALL selecionar um valor do
**eixo analítico** — o mesmo tipo-soma que o detalhamento usa (`uncategorized-spending-breakdown`):
uma categoria, ou a ausência de classificação —, além do estado neutro que não recorta nada.

O não classificado MUST NOT ser representado no filtro como uma categoria sintética, como um
item da lista de categorias, nem como um segundo controle booleano ao lado do de categoria.
Um segundo controle poderia assumir estado contraditório com o primeiro ("categoria =
Mercado" e "sem categoria" ao mesmo tempo), e o eixo é uma decisão só.

#### Scenario: Três estados no mesmo controle
- **WHEN** o controle de categoria é aberto sobre uma lista que contém lançamento sem
  categoria
- **THEN** ele oferece o estado neutro ("todas"), cada categoria existente e um valor "sem
  categoria", e selecionar qualquer um deles desfaz o anterior

#### Scenario: O não classificado não vira categoria
- **WHEN** o valor "sem categoria" está selecionado
- **THEN** nenhuma categoria com esse nome existe no sistema, e nada permite renomeá-la,
  arquivá-la ou removê-la

### Requirement: O recorte sem classificação é a ausência de dimensão na perna nominal

Com o não classificado selecionado, a lista SHALL exibir exatamente os lançamentos que
**possuem perna nominal** (`INCOME` ou `EXPENSE`) e cuja perna nominal **não carrega
dimensão**.

Um lançamento **sem perna nominal** — transferência entre contas, pagamento de fatura,
ajuste — MUST NOT ser exibido por esse recorte. Ele não está fora de classificação: está
fora do eixo, e não compõe total sem classificação algum. Exibi-lo faria o recorte discordar
do número que ele existe para explicar.

O critério SHALL ser o mesmo que o razão usa para compor o total sem classificação; MUST NOT
existir uma segunda definição de "sem categoria" nesta tela.

#### Scenario: Despesa sem categoria entra
- **WHEN** existe uma despesa lançada sem categoria no período e o valor "sem categoria"
  está selecionado
- **THEN** ela é exibida na lista

#### Scenario: Receita sem categoria entra
- **WHEN** existe uma receita lançada sem categoria no período e o valor "sem categoria"
  está selecionado
- **THEN** ela é exibida na lista, pelo mesmo critério da despesa

#### Scenario: Transferência não é não classificado
- **WHEN** existe uma transferência entre contas no período e o valor "sem categoria" está
  selecionado
- **THEN** ela não é exibida, porque não tem perna nominal

#### Scenario: Pagamento de fatura e ajuste também não
- **WHEN** existem um pagamento de fatura e um ajuste de saldo no período e o valor "sem
  categoria" está selecionado
- **THEN** nenhum dos dois é exibido

#### Scenario: Lançamento categorizado sai
- **WHEN** uma despesa carrega categoria e o valor "sem categoria" está selecionado
- **THEN** ela não é exibida

#### Scenario: Dimensão órfã não é lavada no recorte
- **WHEN** uma perna nominal carrega uma dimensão que não resolve para categoria alguma
- **THEN** o lançamento MUST NOT ser exibido pelo recorte sem classificação, porque isso é
  falha de integridade e não ausência de classificação

### Requirement: O recorte sem classificação governa apenas a lista

Selecionar o não classificado MUST NOT alterar nenhuma linha do resumo, exatamente como já
vale para qualquer categoria (`transaction-scope`, "Alcance dos controles é posicional"). O
controle SHALL permanecer abaixo do card de resumo e SHALL compor com os demais recortes —
escopo, período, natureza, recorrência, parcelamento — por conjunção.

#### Scenario: O resumo não se move
- **WHEN** o usuário seleciona "sem categoria"
- **THEN** a lista é recortada e todas as linhas do resumo permanecem as do mês completo
  daquele escopo

#### Scenario: Composição com outro recorte
- **WHEN** "sem categoria" e o recorte de natureza "despesa" estão ativos ao mesmo tempo
- **THEN** a lista exibe apenas despesas sem categoria

#### Scenario: O escopo continua recortando
- **WHEN** "sem categoria" está ativo e o escopo é "cartões"
- **THEN** a lista exibe apenas lançamentos sem categoria que tocam o perímetro de cartões

### Requirement: O controle diz que está no não classificado, sem se passar por categoria

Com o não classificado selecionado, o controle SHALL exibir o rótulo traduzido do não
classificado, resolvido na apresentação a partir do valor do eixo — o modelo do filtro MUST
NOT carregar texto voltado ao usuário. A chave de string SHALL existir em português e em
inglês.

O controle SHALL ser visualmente distinguível de uma categoria selecionada: MUST NOT assumir
a cor de natureza que uma categoria selecionada assume, porque o não classificado não tem
natureza declarada.

#### Scenario: Rótulo nos dois idiomas
- **WHEN** o app roda em português e em inglês com o valor selecionado
- **THEN** o controle é nomeado por uma chave de string presente nos dois arquivos de
  recursos

#### Scenario: Sem cor de natureza
- **WHEN** o valor "sem categoria" está selecionado
- **THEN** o controle é exibido no estado selecionado sem a cor de receita nem a de despesa

### Requirement: O valor é oferecido apenas quando há o que recortar

O valor não classificado SHALL ser oferecido apenas quando a lista que o controle recorta
contiver ao menos um lançamento sem classificação — a lista **já recortada pelos demais
controles**, que é a que a pessoa tem diante de si. Um comando que só pode responder com uma
lista vazia não é uma oferta, e é a mesma regra pela qual o detalhamento omite a linha num
período sem nada por classificar.

O valor SHALL continuar oferecido enquanto for o recorte ativo, ainda que nada sobreviva a
ele: um item que sumisse com o recorte em vigor deixaria a lista recortada por um controle
que a pessoa já não alcança para desfazer — o que a regra de "filtro suprimido para de
recortar" existe para impedir.

#### Scenario: Mês sem nada por classificar não oferece o valor
- **WHEN** todos os lançamentos do período têm categoria, ou estão fora do eixo
- **THEN** o controle não oferece o valor "sem categoria"

#### Scenario: Outro filtro pode retirar a oferta
- **WHEN** o único lançamento sem categoria do período é uma despesa e o recorte de natureza
  está em "receita"
- **THEN** o controle não oferece o valor, porque não há o que ele encontrasse na lista

#### Scenario: O valor selecionado permanece oferecido
- **WHEN** o valor está selecionado e nada na lista sobrevive a ele
- **THEN** ele continua no controle, de modo que o recorte possa ser desfeito por onde foi
  aplicado

#### Scenario: Dimensão órfã não faz a oferta aparecer
- **WHEN** o único lançamento sem categoria resolvida do período carrega dimensão órfã
- **THEN** o controle não oferece o valor, pela mesma regra que o mantém fora do recorte

### Requirement: A regra vale em toda superfície que oferece o filtro

Toda superfície que oferece o filtro de categoria sobre uma lista de lançamentos SHALL
oferecer também o valor não classificado, com o mesmo critério de recorte, o mesmo rótulo, a
mesma posição no controle e a mesma regra de omissão. MUST NOT existir superfície em que o
filtro de categoria seja oferecido, haja o que recortar, e o não classificado não seja
oferecido.

Uma superfície a menos não é uma lacuna cosmética: quem procura o que ficou por classificar
não sabe de cor em que tela o comando existe, e a ausência do valor lê-se como "aqui não há
nada sem categoria" — uma afirmação sobre os dados que a tela não fez.

Onde o recorte é aplicado sobre o **modelo de exibição** e não sobre o lançamento, o modelo
SHALL carregar a resposta já decidida pelo domínio, e MUST NOT reconstituí-la a partir da
ausência de categoria no próprio modelo: ali "sem categoria" e "fora do eixo" são
indistinguíveis, e uma dimensão órfã apareceria como não classificada.

Os seletores de categoria de **escrita** — lançar, editar, confirmar uma recorrência, criar
um parcelamento — MUST NOT oferecer o valor: neles o não classificado já é dizível como a
ausência de escolha, e um item selecionável criaria duas formas de dizer a mesma coisa.

#### Scenario: As cinco superfícies oferecem o valor
- **WHEN** o filtro de categoria é aberto na tela de transações, na de contas, na de cartões,
  na de transações de fatura e na de parcelamentos, cada uma sobre uma lista que contém
  lançamento sem categoria
- **THEN** todas oferecem o valor "sem categoria", com o mesmo rótulo e na mesma posição

#### Scenario: O recorte concorda entre superfícies
- **WHEN** o mesmo lançamento sem categoria é alcançável por duas dessas telas
- **THEN** o recorte sem classificação o exibe nas duas, e nenhuma delas exibe um lançamento
  que a outra descarta pelo mesmo valor do eixo

#### Scenario: Dimensão órfã continua fora, mesmo sobre o modelo de exibição
- **WHEN** a superfície recorta modelos de exibição e um deles vem de uma perna nominal com
  dimensão órfã
- **THEN** ele não é exibido pelo recorte sem classificação

#### Scenario: O seletor de escrita não ganha o valor
- **WHEN** o usuário escolhe a categoria ao lançar ou editar uma transação
- **THEN** nenhum item "sem categoria" é oferecido — não escolher já é o que ele significa

### Requirement: O não classificado é limpo como qualquer outro recorte

O valor não classificado SHALL contar como filtro ativo: enquanto ele estiver selecionado, o
vazio de recorte SHALL oferecer limpar os filtros (`transaction-list-empty-states`), e
limpar os filtros SHALL devolver o eixo ao estado neutro.

#### Scenario: Vazio de recorte oferece limpar
- **WHEN** o mês não tem nenhum lançamento sem categoria e o valor está selecionado
- **THEN** a lista exibe o vazio de recorte com a ação de limpar os filtros

#### Scenario: Limpar devolve ao neutro
- **WHEN** o usuário limpa os filtros com "sem categoria" selecionado
- **THEN** o controle volta ao estado neutro e a lista deixa de ser recortada pelo eixo

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
