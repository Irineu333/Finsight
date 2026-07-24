## ADDED Requirements

### Requirement: Categoria arquivada pode ser desarquivada

Uma categoria arquivada SHALL poder voltar à circulação por uma operação de **desarquivamento**, com use case próprio, simétrica ao arquivamento: a fachada tem o seu flag `isArchived` revertido para falso e nada mais muda — as entries classificadas na sua dimensão permanecem intactas, e a sua dimensão e histórico já existiam e não são tocados. O desarquivamento SHALL residir na própria fachada de categoria, pela mesma razão que o arquivamento (categoria não é linha do plano de contas, `chart-of-accounts`, logo não há conta a reabrir).

O desarquivamento é uma ação **reversível e inócua**: MUST NOT ser recusado por invariante alguma e MUST NOT exigir confirmação destrutiva, ao contrário do arquivar e do apagar. Uma vez desarquivada, a categoria SHALL reaparecer nos seus seletores e nas listagens ativas, e SHALL voltar a ser oferecida para `Budget.categories`.

A interface SHALL oferecer o desarquivar **apenas** para uma categoria já arquivada, e o arquivar/apagar **apenas** para uma não arquivada — as duas ofertas são mutuamente exclusivas pelo estado de arquivamento, decisão de apresentação com dono único. Desarquivar é oferecido a partir da visualização da categoria, alcançada pela listagem de arquivadas.

#### Scenario: Desarquivar uma categoria arquivada
- **WHEN** o usuário desarquiva uma categoria que estava arquivada
- **THEN** o seu flag de arquivamento é revertido, ela reaparece nas listagens ativas e nos seletores de lançamento, volta a ser oferecível em orçamentos, e as suas entries permanecem classificadas na sua dimensão

#### Scenario: A visualização oferece desarquivar apenas para arquivada
- **WHEN** a categoria exibida está arquivada
- **THEN** a interface oferece a ação de desarquivar, e não oferece arquivar nem apagar

#### Scenario: A visualização oferece retirar apenas para não arquivada
- **WHEN** a categoria exibida não está arquivada
- **THEN** a interface oferece a ação de retirá-la (arquivar ou apagar, conforme o domínio), e não oferece desarquivar

## MODIFIED Requirements

### Requirement: Conta com lançamentos é arquivada, nunca apagada
Uma conta ou cartão que possua qualquer lançamento MUST NOT ser removida do plano de contas. O sistema SHALL arquivá-la: a conta permanece no plano de contas, com o seu tipo real preservado, marcada como arquivada, e os seus lançamentos históricos permanecem intactos e atribuídos a ela. Uma conta sem nenhum lançamento MAY ser removida, por não haver história a preservar.

Uma categoria que possua qualquer lançamento MUST NOT ser removida: SHALL ser arquivada, e as entries que carregam a sua dimensão permanecem intactas e classificadas nela. O fato "possui lançamentos" SHALL ser derivado do razão pelo mesmo mecanismo das demais fachadas — a existência de entry, consultada pela dimensão da categoria em vez de pela conta.

Uma conta arquivada MUST NOT ser oferecida na seleção de contas de um novo lançamento, e MUST NOT aparecer nas listagens de contas ativas. Uma categoria arquivada MUST NOT ser oferecida nos seus seletores de lançamento nem aparecer nas listagens ativas da sua tela; ela SHALL, porém, permanecer **acessível** por uma listagem dedicada de arquivadas na sua própria tela, de onde pode ser visualizada e desarquivada. Tornar a arquivada visível ali MUST NOT reintroduzi-la em seletor algum nem nas listagens ativas.

O estado de arquivamento de conta e cartão SHALL residir **exclusivamente no plano de contas**, e a fachada de cartão SHALL consumi-lo da sua conta pelo vínculo que já possui — MUST NOT existir cópia desse estado nessa fachada. Todo cartão SHALL possuir conta no plano de contas desde a sua criação, de modo que a consulta não dependa de tratamento para vínculo ausente.

O estado de arquivamento de **categoria** SHALL residir na própria fachada. Categoria não é linha do plano de contas (`chart-of-accounts`), logo não há conta de onde consumir o estado, e não há duplicação: a fachada é o dono único. Isso MUST NOT alterar comportamento — o arquivamento de categoria nunca dependeu de saldo nem foi verificado na fronteira de escrita.

Apagar e arquivar SHALL ser **ações distintas**, com use cases distintos, e cada uma SHALL recusar o que seria **inválido**: apagar conta ou categoria com lançamentos é recusado, e não convertido em arquivamento silencioso. Um use case que faz coisa diferente do seu nome deixa quem o chama — e o usuário lendo o botão — com expectativa errada.

O domínio SHALL recusar apenas o que violaria uma invariante, e MUST NOT recusar o que é meramente inapropriado. Arquivar uma conta sem lançamentos, por exemplo, SHALL ser permitido: não quebra nada, apenas não é a ação que uma tela ofereceria.

A interface SHALL oferecer a ação correta pelo nome, e MUST NOT oferecer a que será recusada. Ela não é a salvaguarda: o desfecho é decidido pelo domínio. Qual das duas oferecer é decisão de **apresentação**, derivada do fato "possui lançamentos", e SHALL ter um dono único, consumido por conta, cartão e categoria.

#### Scenario: Arquivar conta com lançamentos
- **WHEN** o usuário arquiva uma conta que possui lançamentos
- **THEN** a conta é marcada como arquivada, permanece no plano de contas com o seu tipo, seus lançamentos continuam atribuídos a ela, e ela desaparece das listagens e seletores

#### Scenario: Apagar conta com lançamentos é recusado
- **WHEN** o usuário tenta apagar uma conta que possui lançamentos
- **THEN** o sistema recusa com erro tipado, não remove nem arquiva nada, e a interface diz que a conta precisa ser arquivada

#### Scenario: Apagar conta sem lançamentos
- **WHEN** o usuário apaga uma conta que não possui nenhum lançamento
- **THEN** a conta é removida do plano de contas, por não haver história a preservar

#### Scenario: Arquivar conta sem lançamentos é permitido
- **WHEN** o arquivamento de uma conta sem lançamentos é solicitado ao domínio
- **THEN** a conta é arquivada, por nada nisso ser inválido

#### Scenario: A interface oferece a ação que vai acontecer
- **WHEN** uma conta, cartão ou categoria é exibida com a ação de retirá-la
- **THEN** o rótulo, o ícone e a modal correspondem à ação que o domínio executará, e são os mesmos nas três fachadas

#### Scenario: Apagar categoria com lançamentos é recusado
- **WHEN** o usuário tenta apagar uma categoria que possui entries classificadas na sua dimensão
- **THEN** o sistema recusa com erro tipado e a categoria é arquivada na própria fachada, permanecendo as entries classificadas nela

#### Scenario: Categoria arquivada some das listagens ativas mas fica acessível
- **WHEN** uma categoria com lançamentos é removida
- **THEN** ela é arquivada na fachada e desaparece das listagens ativas e dos seletores, permanecendo acessível pela listagem de arquivadas da sua tela

#### Scenario: Cartão recém-criado tem conta
- **WHEN** um cartão é criado
- **THEN** a sua conta no plano de contas existe imediatamente, e a consulta de arquivamento não precisa tratar vínculo ausente

#### Scenario: Categoria recém-criada tem dimensão
- **WHEN** uma categoria é criada
- **THEN** a sua dimensão existe imediatamente, e a consulta de "possui lançamentos" não precisa tratar vínculo ausente

#### Scenario: Conta arquivada não é selecionável
- **WHEN** o usuário registra um novo lançamento
- **THEN** contas arquivadas não são oferecidas
