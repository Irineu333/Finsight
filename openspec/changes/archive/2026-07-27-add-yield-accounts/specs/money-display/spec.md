## ADDED Requirements

### Requirement: O rótulo de uma linha de fluxo declara o que está dentro do número

Numa **decomposição fixa de um total** — um conjunto de linhas de fluxo nomeadas pelo app, exibidas sobre um resultado que elas explicam —, o par de rótulos das linhas de entrada e de saída SHALL declarar se o rendimento está segregado:

- onde o **rendimento tem linha própria**, o par SHALL ser **entrada/saída**;
- onde o rendimento **está contido** na linha de entrada, o par SHALL ser **receita/despesa**, ficando subentendido que receita engloba rendimento.

O rótulo SHALL, portanto, responder *o que está dentro do número*, e não meramente nomear a tela em que ele aparece. Duas superfícies que exibem valores diferentes para o mesmo período MUST NOT fazê-lo sob o mesmo rótulo: é o rótulo que torna a diferença legível em vez de contraditória.

Essa regra SHALL ter **dono único** e MUST NOT ser re-derivada por tela. Ela atravessa superfícies de donos distintos — o cartão de uma conta, o resumo de um escopo, o relatório e o painel —, e alojá-la em qualquer uma obrigaria as demais a reimplementá-la.

A regra SHALL acompanhar a **forma do número**, não a tela: uma superfície que passe a segregar rendimento SHALL passar a usar o par entrada/saída, e uma que deixe de segregar SHALL voltar ao par receita/despesa, sem decisão caso a caso.

Dois usos ficam **fora** do alcance da regra, e MUST NOT ser renomeados por ela:

- **Seletor de tipo, filtro e formulário.** Ali a palavra nomeia um *tipo de lançamento* que o usuário escolhe, não o conteúdo de uma soma; a pergunta "isso inclui rendimento?" não tem sentido, porque inclui sempre. Esse eixo SHALL seguir o vocabulário de tipo de transação, uniformemente **receita/despesa**, e MUST NOT misturar um termo de um par com um termo do outro.
- **Agrupamento por dimensão.** Um total agrupado por categoria separa o rendimento por construção, já que ele é uma categoria. Se isso contasse como segregação, todo agrupamento por categoria adotaria entrada/saída e a regra deixaria de distinguir coisa alguma.

#### Scenario: Superfície que segrega rendimento
- **WHEN** um resumo exibe uma linha própria de rendimento sobre um total
- **THEN** as linhas de fluxo correspondentes são rotuladas como entrada e saída

#### Scenario: Superfície que não segrega rendimento
- **WHEN** um resumo exibe o rendimento contido na sua linha de entrada
- **THEN** as linhas de fluxo correspondentes são rotuladas como receita e despesa

#### Scenario: A diferença entre duas telas é legível
- **WHEN** o mesmo período é exibido em uma superfície que segrega e em outra que não segrega
- **THEN** a primeira exibe entrada e rendimento em linhas separadas, a segunda exibe receita contendo ambos, e os rótulos explicam por que os números diferem

#### Scenario: Filtro de tipo não adota o par segregado
- **WHEN** o usuário abre um filtro ou seletor de tipo de lançamento
- **THEN** as opções são rotuladas como receita e despesa, e MUST NOT combinar um termo de um par com o do outro

#### Scenario: Agrupamento por categoria não é segregação
- **WHEN** um total de receitas é exibido agrupado por categoria, com o rendimento aparecendo na sua própria categoria
- **THEN** o rótulo do agrupamento permanece o do par não segregado, por a separação decorrer da dimensão e não de uma linha de fluxo

#### Scenario: A regra segue o número, não a tela
- **WHEN** uma superfície que hoje não segrega rendimento passa a exibi-lo em linha própria
- **THEN** os seus rótulos de fluxo passam a ser entrada e saída, sem que a decisão seja tomada tela a tela
