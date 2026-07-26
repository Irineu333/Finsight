## MODIFIED Requirements

### Requirement: Mappers como única fronteira domínio-apresentação
A tradução de domínio para apresentação SHALL ocorrer exclusivamente em mappers. Derivação de rótulo, resolução de perspectiva, inversão de sinal por `AccountType` e escolha do valor a exibir MUST NOT ocorrer em modelo de UI nem em componente de UI. Um modelo de UI MUST NOT declarar campo de tipo de domínio. Esta regra SHALL ser verificável por inspeção dos próprios modelos de UI, e MUST NOT ser expressa como ausência de dependência de módulo: `core/ui` depende de `core/model` **por desenho** — os seus componentes existem para renderizar modelos de domínio — e `core/ui/model` é um pacote, não um módulo Gradle.

A inversão de sinal por `AccountType` é regra de **saldo**: ela existe para que o saldo natural de uma conta de natureza credora leia positivo. Ela MUST NOT ser aplicada ao valor de uma **perna de transação**. Quando o valor de uma perna for exibido com sinal, esse sinal SHALL ser o natural do razão — em convenção débito-positivo, o mesmo em que a perna foi gravada. *Se* uma perna exibe sinal é decisão da política de exibição (`money-display`); *qual* sinal ela exibe, quando exibe, é esta regra. As duas leituras são distintas e a spec as nomeia separadamente porque a segunda é derivável da primeira por analogia errada: invertida, uma correção que aumenta uma dívida leria positiva, ao lado de uma compra que aumenta a mesma dívida e lê negativa.

#### Scenario: Inversão de sinal para exibição
- **WHEN** um valor do razão em convenção débito-positivo é exibido
- **THEN** o mapper aplica a inversão por `AccountType`, e a UI recebe o valor já no sinal que o usuário espera

#### Scenario: Sinal de uma perna de transação
- **WHEN** o valor de uma perna de transação é exibido com sinal — inclusive a perna de passivo de um cartão
- **THEN** o sinal é o natural do razão, sem a inversão por `AccountType`, que é regra de saldo

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

### Requirement: Uma tela declara a perspectiva que tem

Uma superfície que apresenta transações sob **uma** conta ou **um** cartão SHALL declarar essa
perspectiva ao mapear, e MUST NOT deixar que a perna lida seja escolhida pelo critério de
ausência de perspectiva. Uma superfície que não tem perspectiva única — uma lista de tudo, ou
um recorte sobre várias contas — MUST NOT inventar uma: a ausência é a resposta correta, e não
uma omissão.

Dentro de uma mesma superfície, a perna lida SHALL ter uma única definição. Filtro, item e
detalhe MUST NOT derivá-la cada um por conta própria: duas definições podem discordar, e o
filtro passa a devolver uma transação que o item apresenta na direção oposta.

#### Scenario: Extrato de fatura lê pela perna do cartão
- **WHEN** o pagamento de uma fatura é exibido no extrato dessa fatura
- **THEN** ele é lido pela perna do cartão, como dinheiro que entra, e não pela perna da conta de onde saiu

#### Scenario: Filtro e item concordam sobre a perna
- **WHEN** uma lista com perspectiva é filtrada por direção
- **THEN** a direção que o filtro aplica é a mesma que o item exibe, por virem da mesma definição

#### Scenario: Recorte sobre várias contas não tem perspectiva
- **WHEN** um relatório apresenta transações de várias contas
- **THEN** o mapeamento é feito sem perspectiva, porque não há uma única conta de cujo ponto de vista ler
