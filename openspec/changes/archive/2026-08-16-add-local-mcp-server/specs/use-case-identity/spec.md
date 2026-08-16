## ADDED Requirements

### Requirement: Um use case público é identificado por id

Um use case declarado na `api` de uma feature SHALL oferecer a sua operação identificada pelo
**id** do que ela opera. A forma por id SHALL ser a que carrega a implementação, e SHALL
resolver a identidade no momento da execução, recusando com erro tipado quando ela não existir.

A regra existe porque a identidade é o que todo chamador possui: uma tela carregou o agregado
para exibi-lo, mas uma superfície que recebe uma requisição externa tem apenas o identificador.
Sem a forma por id, cada chamador desse tipo repetiria a mesma resolução, e a recusa por
identidade inexistente teria tantas formulações quantos fossem os chamadores.

Resolver no momento da execução também é mais correto do que receber o agregado: um agregado
carregado por uma tela e usado numa ação minutos depois é uma leitura vencida, e o app passa a
ter uma segunda porta de escrita capaz de invalidá-la.

#### Scenario: Use case público oferece a forma por id
- **WHEN** a interface de um use case público é inspecionada
- **THEN** ela declara a operação recebendo o identificador do que opera

#### Scenario: Identidade inexistente
- **WHEN** um use case é invocado com um identificador que não corresponde a nada
- **THEN** ele recusa com o erro tipado de "não encontrado", sem executar a operação

### Requirement: A forma por agregado delega, e não reimplementa

Quando um use case também for oferecido recebendo o agregado, essa forma SHALL delegar à forma
por id e MUST NOT conter lógica própria. Ela existe para o chamador que já tem o agregado em
mãos, e é uma conveniência de chamada — não uma segunda implementação, que poderia divergir.

#### Scenario: Sobrecarga por agregado
- **WHEN** um use case oferece as duas formas
- **THEN** a que recebe o agregado apenas extrai o identificador e delega, sem executar regra própria

#### Scenario: Uma só regra
- **WHEN** as duas formas de um mesmo use case são exercidas com a mesma identidade
- **THEN** o resultado é idêntico, porque só existe uma implementação

### Requirement: Valor padrão de parâmetro não deriva de agregado recebido

A assinatura de um use case MUST NOT declarar valor padrão de parâmetro derivado de outro
parâmetro que seja um agregado de domínio. Um padrão assim só é expressável quando o agregado
chega na assinatura, o que amarra a forma da operação à forma de chamá-la — e anuncia um
comportamento que os chamadores podem legitimamente não querer.

Quando a ausência de um valor tiver significado, esse significado SHALL ser resolvido no corpo
do use case e documentado, e MUST NOT depender de o chamador conhecer o padrão declarado.

#### Scenario: Assinatura sem padrão derivado
- **WHEN** a assinatura de um use case é inspecionada
- **THEN** nenhum valor padrão de parâmetro é lido de outro parâmetro agregado

#### Scenario: Ausência com significado
- **WHEN** um chamador omite um valor cuja ausência significa algo
- **THEN** o significado é aplicado dentro do use case, e é o mesmo para todos os chamadores

### Requirement: Leitura em lote recebe identidades, não agregados

Quando uma leitura precisar responder sobre vários itens, ela SHALL oferecer a forma que recebe
a coleção de identidades e devolve o resultado indexado por elas. Um chamador MUST NOT executar
uma leitura por item num laço sobre uma coleção já carregada.

#### Scenario: Figura de vários itens
- **WHEN** uma superfície precisa da mesma figura para N itens
- **THEN** ela faz uma leitura que recebe as N identidades e devolve o resultado indexado, e não N leituras

#### Scenario: Item sem resultado
- **WHEN** uma identidade da coleção não possui resultado
- **THEN** ela está ausente do mapa devolvido, e o chamador a trata como o valor neutro
