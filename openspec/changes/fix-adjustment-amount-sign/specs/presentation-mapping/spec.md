## MODIFIED Requirements

### Requirement: Mappers como única fronteira domínio-apresentação
A tradução de domínio para apresentação SHALL ocorrer exclusivamente em mappers. Derivação de rótulo, resolução de perspectiva, inversão de sinal por `AccountType` e escolha do valor a exibir MUST NOT ocorrer em modelo de UI nem em componente de UI. Um modelo de UI MUST NOT declarar campo de tipo de domínio. Esta regra SHALL ser verificável por inspeção dos próprios modelos de UI, e MUST NOT ser expressa como ausência de dependência de módulo: `core/ui` depende de `core/model` **por desenho** — os seus componentes existem para renderizar modelos de domínio — e `core/ui/model` é um pacote, não um módulo Gradle.

A inversão de sinal por `AccountType` é regra de **saldo**: ela existe para que o saldo natural de uma conta de natureza credora leia positivo. Ela MUST NOT ser aplicada ao valor de uma **perna de transação**, que SHALL ser exibido no sinal natural do razão — em convenção débito-positivo, o mesmo sinal em que a perna foi gravada. As duas leituras são distintas e a spec as nomeia separadamente porque a segunda é derivável da primeira por analogia errada: invertida, uma correção que aumenta uma dívida leria positiva, ao lado de uma compra que aumenta a mesma dívida e lê negativa.

#### Scenario: Inversão de sinal para exibição
- **WHEN** um valor do razão em convenção débito-positivo é exibido
- **THEN** o mapper aplica a inversão por `AccountType`, e a UI recebe o valor já no sinal que o usuário espera

#### Scenario: Sinal de uma perna de transação
- **WHEN** o valor de uma perna de transação é exibido — inclusive a perna de passivo de um cartão
- **THEN** o mapper o entrega no sinal natural do razão, sem aplicar a inversão por `AccountType`, que é regra de saldo

#### Scenario: Derivação de rótulo
- **WHEN** o rótulo de uma transação é exibido
- **THEN** o mapper o deriva dos tipos de conta das entries, e a UI recebe o rótulo pronto

#### Scenario: Modelo de UI não carrega domínio
- **WHEN** um modelo de UI é inspecionado
- **THEN** nenhum de seus campos é de tipo de domínio — no máximo o identificador

#### Scenario: Escolha do valor a exibir não ocorre no componente
- **WHEN** um componente de UI renderiza o valor de uma transação
- **THEN** ele recebe do mapper o valor já resolvido com a sua política de sinal, sem ramificar por rótulo, natureza ou direção para decidir o que exibir

## ADDED Requirements

### Requirement: A escolha da perna neutra tem um dono

Quando um mapeamento for solicitado sem perspectiva, a perna pela qual a transação é lida SHALL ser a que o domínio já define como perna primária, e o mapper MUST NOT reimplementar esse critério. Duas definições da mesma escolha podem divergir sem que nada falhe — a lista passa a olhar uma perna e o detalhe outra, para a mesma transação.

#### Scenario: Lista sem perspectiva e detalhe concordam sobre a perna
- **WHEN** a mesma transação é exibida em uma lista sem perspectiva e aberta no detalhe
- **THEN** ambas leem a mesma perna, por consumirem a mesma definição de perna primária
