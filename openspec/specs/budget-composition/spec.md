# budget-composition Specification

## Purpose
Quais categorias um orçamento pode conter, o que o seu seletor oferece, o que uma barra de
progresso significa quando categorias se sobrepõem, e como a recusa de apagar uma categoria
em uso é redigida.

## Requirements
### Requirement: Uma categoria pode ser orçada por quantos orçamentos o usuário quiser

Uma categoria SHALL poder pertencer a qualquer número de orçamentos ao mesmo tempo. O sistema MUST NOT recusar, esconder ou desabilitar uma categoria por ela já estar em outro orçamento.

Um orçamento é uma **lente sobre o gasto**, não uma fatia dele. Ele responde a uma pergunta que o usuário escolheu fazer — "quanto estou gastando com o essencial?", "o delivery está passando de trezentos?" — e duas perguntas diferentes podem legitimamente olhar para a mesma despesa. Tratá-los como partição, com cada categoria tendo um dono, proíbe o **aninhamento** (um teto amplo contendo um sub-teto apertado) e o **recorte cruzado** (fixo × discricionário sobre as mesmas categorias), sem que nada no domínio exigisse essa proibição.

A exclusividade MUST NOT ser reintroduzida sob outra forma — nem como validação de submissão, nem como aviso que peça confirmação, nem como marcação que desaconselhe a escolha. A sobreposição é um estado normal, não uma exceção tolerada.

#### Scenario: Categoria já orçada continua sendo oferecida

- **WHEN** o usuário monta um orçamento novo e uma categoria de despesa aberta já pertence a outro orçamento
- **THEN** ela aparece no seletor junto com as demais, sem marca de indisponibilidade, e pode ser escolhida

#### Scenario: Dois orçamentos sobre a mesma categoria coexistem

- **WHEN** o usuário salva um segundo orçamento contendo uma categoria que o primeiro já contém
- **THEN** os dois são gravados e passam a ser listados, e nenhum dos dois perde a categoria

#### Scenario: Editar um orçamento não retira a categoria de outro

- **WHEN** o usuário edita um orçamento e mantém uma categoria que outro orçamento também contém
- **THEN** o vínculo do outro orçamento com essa categoria permanece intacto

### Requirement: O seletor do formulário oferece as categorias de despesa abertas, mais as já escolhidas

O seletor de categorias do formulário de orçamento SHALL oferecer as categorias de **despesa** que não estejam arquivadas, e SHALL preservar a continuidade da escolha já feita: uma categoria arquivada **depois** de ter sido adicionada ao orçamento SHALL continuar aparecendo, marcada, para que possa ser removida. Desfeita a escolha, ela MUST NOT voltar a ser oferecida enquanto permanecer arquivada.

Essa continuidade é a regra geral de fachada arquivada já escolhida, e SHALL ter a mesma forma que a receita base do orçamento usa — não ser reimplementada por seletor.

Com a exclusividade removida, esta continuidade passa a ser a **única** regra que o seletor aplica: não há mais subtração de categorias reivindicadas por terceiros, e a lista de todos os orçamentos deixa de ser insumo do formulário.

#### Scenario: Categoria arquivada depois de escolhida continua removível

- **WHEN** o usuário edita um orçamento que contém uma categoria arquivada após ter sido adicionada
- **THEN** ela aparece no seletor, marcada, e o usuário consegue desmarcá-la

#### Scenario: Categoria arquivada nunca é oferecida como escolha nova

- **WHEN** o usuário monta um orçamento e existe uma categoria de despesa arquivada que ele nunca escolheu
- **THEN** ela não aparece no seletor

#### Scenario: Categoria de receita não é oferecida

- **WHEN** o usuário abre o seletor de categorias do formulário de orçamento
- **THEN** apenas categorias de despesa são apresentadas

### Requirement: O progresso de cada orçamento é medido isoladamente

O progresso de um orçamento SHALL ser a soma dos lançamentos das suas próprias categorias no mês, comparada ao seu próprio limite, e MUST NOT ser afetado por outro orçamento conter as mesmas categorias.

Quando duas barras medem a mesma despesa, isso é o efeito pretendido da sobreposição e não uma dupla contagem: cada figura é uma resposta *contra o seu limite*, e o sistema MUST NOT apresentar soma alguma **entre** orçamentos, que é a única leitura na qual a repetição seria um erro aritmético.

#### Scenario: Uma despesa contada por dois orçamentos

- **WHEN** dois orçamentos contêm a categoria "Alimentação" e o usuário registra uma despesa nela
- **THEN** o gasto de cada um dos dois avança pelo valor cheio da despesa, cada qual contra o seu próprio limite

#### Scenario: Sobreposição não produz total agregado

- **WHEN** o usuário vê a lista de orçamentos com categorias sobrepostas entre eles
- **THEN** nenhuma figura somando os orçamentos entre si é apresentada

### Requirement: A recusa de apagar uma categoria orçada não pressupõe um único orçamento

Uma categoria que **algum** orçamento contenha MUST NOT ser apagada: o sistema SHALL recusar a exclusão com erro tipado e oferecer o arquivamento em seu lugar, como faz para os demais dependentes de categoria. O guard é o mesmo de antes — a chave estrangeira é `CASCADE`, e sem a recusa a exclusão retiraria a categoria de todo orçamento em silêncio.

A mensagem dessa recusa MUST NOT afirmar que a categoria está em **um** orçamento, já que pode estar em vários, e SHALL indicar que a remoção precisa ocorrer em todos eles. Ela SHALL ter **uma única origem**: um só erro tipado e uma só chave de string, nos dois idiomas.

#### Scenario: Apagar categoria contida em vários orçamentos é recusado

- **WHEN** o usuário tenta apagar uma categoria que três orçamentos contêm
- **THEN** a exclusão é recusada, o arquivamento é oferecido em seu lugar, e a mensagem não afirma que ela está em um único orçamento

#### Scenario: Apagar categoria contida em um orçamento é recusado com a mesma mensagem

- **WHEN** o usuário tenta apagar uma categoria que exatamente um orçamento contém
- **THEN** a exclusão é recusada com a mesma mensagem do caso anterior, que continua correta com um só orçamento
