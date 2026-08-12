## ADDED Requirements

### Requirement: A confirmação de um ciclo é editável no que descreve a transação

A confirmação de um ciclo de recorrência SHALL permitir ao usuário ajustar o **título** e a **categoria** da transação que será lançada, além do valor, da data e do destino que já ajusta.

A recorrência é um **modelo do ciclo**, não uma sentença sobre ele: o que o usuário confirma é uma transação real, e a classificação dessa transação é o que alimenta orçamentos e relatórios. Campos desabilitados obrigavam o usuário a corrigir depois — editando a transação já lançada — ou a alterar o template e contaminar todos os ciclos seguintes.

O título SHALL abrir pré-preenchido com o título da recorrência e a categoria SHALL abrir pré-selecionada com a categoria da recorrência: o caminho de quem não quer mudar nada continua sendo confirmar sem tocar em campo algum.

#### Scenario: O modal abre com os valores do template

- **WHEN** o usuário abre a confirmação de um ciclo de uma recorrência que tem título e categoria
- **THEN** o campo de título vem preenchido com o título da recorrência e o seletor de categoria vem com a categoria da recorrência selecionada, ambos editáveis

#### Scenario: Confirmar sem editar nada preserva o comportamento anterior

- **WHEN** o usuário confirma o ciclo sem tocar em título nem em categoria
- **THEN** a transação é lançada com o título e a categoria da recorrência, exatamente como antes desta mudança

#### Scenario: O título editado vai para a transação

- **WHEN** o usuário troca o título por outro e confirma o ciclo
- **THEN** a transação lançada leva o título digitado

#### Scenario: A categoria editada vai para a transação

- **WHEN** o usuário troca a categoria por outra e confirma o ciclo
- **THEN** a transação lançada é classificada na categoria escolhida, e não na da recorrência

### Requirement: A edição vale apenas para o ciclo confirmado

A edição de título e categoria na confirmação SHALL afetar **somente** a transação daquele ciclo. O template da recorrência — seu título, sua categoria e todo o resto — MUST NOT ser alterado pela confirmação.

A confirmação seguinte SHALL voltar a sugerir os valores do template. O sistema MUST NOT oferecer propagação da edição para os próximos ciclos: quem quer mudar o modelo edita o modelo, e essa porta já existe no formulário da recorrência.

#### Scenario: O template permanece intacto

- **WHEN** o usuário confirma um ciclo com título e categoria diferentes dos da recorrência
- **THEN** a recorrência continua com o título e a categoria que tinha antes

#### Scenario: O ciclo seguinte volta a sugerir o template

- **WHEN** o usuário abre a confirmação do ciclo seguinte ao que editou
- **THEN** o título e a categoria vêm novamente os do template, sem lembrança da edição anterior

#### Scenario: Não há propagação para os próximos ciclos

- **WHEN** o usuário edita título ou categoria na confirmação
- **THEN** nenhuma opção de aplicar a alteração aos próximos ciclos é apresentada

### Requirement: Título em branco impede a confirmação

O botão de confirmar SHALL permanecer desabilitado enquanto o campo de título estiver vazio ou contiver apenas espaços, na mesma exigência que o formulário da recorrência já faz.

Um campo vazio MUST NOT ser tratado como "sem alteração": cair em silêncio no título do template daria ao usuário uma transação que ele não pediu, com um nome que ele acabou de apagar.

#### Scenario: Apagar o título desabilita Confirmar

- **WHEN** o usuário apaga todo o conteúdo do campo de título
- **THEN** o botão de confirmar fica desabilitado

#### Scenario: Só espaços não valem como título

- **WHEN** o campo de título contém apenas espaços
- **THEN** o botão de confirmar fica desabilitado

#### Scenario: Repor o título reabilita Confirmar

- **WHEN** o usuário volta a digitar um título e as demais condições de confirmação estão satisfeitas
- **THEN** o botão de confirmar volta a ficar habilitado

### Requirement: O seletor de categoria da confirmação oferece as categorias do tipo do ciclo

O seletor SHALL oferecer as categorias cujo tipo corresponde ao tipo da recorrência — categorias de receita para uma recorrência de receita, de despesa para uma de despesa — e MUST NOT oferecer categorias arquivadas como escolha nova.

O seletor SHALL permitir **remover** a categoria, deixando a transação sem classificação: a ausência de dimensão é um estado legítimo do domínio, não um erro.

Uma categoria arquivada **depois** de ter sido escolhida pela recorrência SHALL continuar aparecendo, selecionada, para que o usuário possa mantê-la ou trocá-la; desfeita a escolha, ela MUST NOT voltar a ser oferecida enquanto permanecer arquivada. É a mesma regra de continuidade que a fachada arquivada já tem em outros seletores, e não SHALL ser reimplementada aqui com outra forma.

#### Scenario: Apenas categorias do tipo da recorrência são oferecidas

- **WHEN** o usuário abre o seletor de categoria ao confirmar uma recorrência de despesa
- **THEN** apenas categorias de despesa são apresentadas

#### Scenario: Categoria arquivada não é oferecida como escolha nova

- **WHEN** existe uma categoria arquivada que a recorrência não nomeia e o usuário abre o seletor
- **THEN** ela não aparece entre as opções

#### Scenario: Categoria arquivada já nomeada pelo template continua visível

- **WHEN** o usuário abre a confirmação de uma recorrência cuja categoria foi arquivada depois de escolhida
- **THEN** ela aparece selecionada no seletor e pode ser mantida ou trocada

#### Scenario: A categoria pode ser removida

- **WHEN** o usuário limpa a seleção de categoria e confirma o ciclo
- **THEN** a transação é lançada sem categoria, e a confirmação não é recusada por isso
