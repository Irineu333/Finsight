## ADDED Requirements

### Requirement: Modelos de UI sem grafo de domínio
Um modelo de UI SHALL conter apenas valores já resolvidos para exibição (textos, valores monetários com sinal de exibição, rótulos, ids). Um modelo de UI MUST NOT conter modelo de domínio como campo — nem agregado, nem entidade, nem coleção deles — carregando no máximo o identificador do domínio que representa. Um modelo de UI MUST NOT executar cálculo de domínio (soma, filtro, derivação de saldo) em construtor, `init` ou propriedade.

#### Scenario: Modelo de UI de transação
- **WHEN** a UI exibe uma transação em uma lista
- **THEN** o modelo de UI expõe id, rótulo, valor de exibição, data e categoria como valores planos, sem referenciar o agregado de domínio

#### Scenario: Modelo de UI de conta
- **WHEN** a UI exibe uma conta com seus totais do período
- **THEN** o modelo de UI expõe os totais como valores já calculados, sem receber lançamentos nem computá-los

#### Scenario: Ação da UI sobre um item
- **WHEN** o usuário aciona uma ação sobre um item exibido
- **THEN** a UI a identifica pelo id, e o domínio correspondente é resolvido fora do modelo de UI

### Requirement: Mappers como única fronteira domínio-apresentação
A tradução de domínio para apresentação SHALL ocorrer exclusivamente em mappers. Derivação de rótulo, resolução de perspectiva, inversão de sinal por `AccountType` e escolha do valor a exibir MUST NOT ocorrer em modelo de UI nem em componente de UI. Um modelo de UI MUST NOT declarar campo de tipo de domínio. Esta regra SHALL ser verificável por inspeção dos próprios modelos de UI, e MUST NOT ser expressa como ausência de dependência de módulo: `core/ui` depende de `core/model` **por desenho** — os seus componentes existem para renderizar modelos de domínio — e `core/ui/model` é um pacote, não um módulo Gradle. Tornar a regra verificável por dependência exigiria extrair um módulo novo, o que está fora do escopo desta change.

#### Scenario: Inversão de sinal para exibição
- **WHEN** um valor do razão em convenção débito-positivo é exibido
- **THEN** o mapper aplica a inversão por `AccountType`, e a UI recebe o valor já no sinal que o usuário espera

#### Scenario: Derivação de rótulo
- **WHEN** o rótulo de uma transação é exibido
- **THEN** o mapper o deriva dos tipos de conta das entries, e a UI recebe o rótulo pronto

#### Scenario: Modelo de UI não carrega domínio
- **WHEN** um modelo de UI é inspecionado
- **THEN** nenhum de seus campos é de tipo de domínio — no máximo o identificador

### Requirement: Perspectiva como argumento de mapeamento
Quando uma transação puder ser apresentada sob mais de um ponto de vista (a conta ou a fatura em que aparece), a perspectiva SHALL ser um argumento do mapeamento, e MUST NOT ser um campo do modelo de UI resolvido preguiçosamente na leitura. A resolução da perspectiva SHALL ocorrer no momento do mapeamento, onde a ausência de correspondência é tratável.

#### Scenario: Mesma transação em duas telas
- **WHEN** uma transferência entre contas é exibida na tela da conta de origem e na da conta de destino
- **THEN** o mapper é invocado com a perspectiva de cada conta e produz um modelo de UI distinto para cada tela

#### Scenario: Perspectiva sem correspondência
- **WHEN** um mapeamento é solicitado com uma perspectiva que não corresponde a nenhuma perna da transação
- **THEN** o mapper não produz modelo de UI para aquela transação e o chamador a omite da lista, sem lançar — e a ausência MUST NOT se manifestar como falha na leitura de uma propriedade

#### Scenario: Perspectiva de cartão é sempre construível
- **WHEN** uma perspectiva de cartão é construída para qualquer cartão, inclusive um recém-criado sem nenhum lançamento
- **THEN** ela é construível, porque todo cartão possui conta no plano de contas desde a sua criação (`account-lifecycle`), e a resolução não depende de tratamento para vínculo ausente
