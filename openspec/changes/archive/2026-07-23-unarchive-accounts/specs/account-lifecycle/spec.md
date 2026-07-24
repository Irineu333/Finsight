## ADDED Requirements

### Requirement: A conta padrão não pode ser retirada

A conta **padrão** (`isDefault`) MUST NOT ser retirada — nem apagada nem arquivada — enquanto for a padrão. Apagar a conta padrão já é recusado no domínio (`AccountError.CANNOT_DELETE_DEFAULT`); arquivar a conta padrão SHALL igualmente ser recusado no domínio, com erro tipado próprio `AccountError.CANNOT_ARCHIVE_DEFAULT`. A recusa SHALL viver no domínio, não na tela: um botão desabilitado é conveniência de apresentação, não salvaguarda, e o desfecho é decidido por quem executa a operação.

Deve sempre existir uma conta padrão; retirá-la deixaria o app sem uma. O usuário SHALL resolver o **papel** de padrão antes, elegendo outra conta pelos meios que já existem (`SetDefaultAccountUseCase`) — do mesmo modo que resolve o **saldo** antes de arquivar uma conta permanente. Nenhuma das duas recusas converte a operação noutra coisa: não há arquivamento nem eleição automática de outra padrão.

O guard SHALL valer apenas para **conta**: a conta `LIABILITY` de um cartão nunca é padrão, logo o mesmo `ArchiveAccountUseCase` compartilhado por conta e cartão não altera o comportamento do cartão ao ganhar essa recusa.

A interface SHALL, para a conta padrão, não oferecer nem arquivar nem apagar, e SHALL orientar a resolver o papel de padrão antes. Isso é um **terceiro caso** da oferta de retirada — além de arquivar e apagar —, decisão de apresentação com dono único, derivada de `isDefault` além de "possui lançamentos", e MUST NOT ser re-derivada inline por cada tela.

#### Scenario: Arquivar a conta padrão é recusado
- **WHEN** o usuário tenta arquivar a conta que é a padrão
- **THEN** o domínio recusa com `AccountError.CANNOT_ARCHIVE_DEFAULT`, não escreve nada, e a interface explica que outra conta precisa ser eleita padrão antes

#### Scenario: Apagar a conta padrão é recusado
- **WHEN** o usuário tenta apagar a conta que é a padrão
- **THEN** o domínio recusa com `AccountError.CANNOT_DELETE_DEFAULT`, não remove nada, e a interface explica que outra conta precisa ser eleita padrão antes

#### Scenario: A interface não oferece retirar a conta padrão
- **WHEN** a conta padrão é exibida com a área de ações
- **THEN** a interface não oferece arquivar nem apagar, e apresenta a orientação de eleger outra conta como padrão — o terceiro caso da oferta de retirada, decidido no dono único

#### Scenario: Eleita outra padrão, a antiga pode ser retirada
- **WHEN** o usuário elege outra conta como padrão e então arquiva a conta que era padrão
- **THEN** a operação é aceita, por a conta já não ser a padrão

### Requirement: Conta arquivada pode ser desarquivada

Uma conta arquivada SHALL poder voltar à circulação por uma operação de **desarquivamento**, com use case próprio, simétrica ao arquivamento. Diferentemente do cartão — que é uma fachada sobre a sua conta —, uma conta **é** a própria linha do plano de contas (`chart-of-accounts`), onde o estado de arquivamento mora. Portanto o desarquivamento de conta SHALL **reabrir a própria conta**: o flag `accounts.isArchived` é revertido para falso, o inverso exato de `close()`, e nada mais muda — as entries da conta permanecem intactas e já existiam antes do arquivamento, que nada tocou além do flag.

O desarquivamento é uma ação **reversível e inócua**: MUST NOT ser recusado por invariante alguma e MUST NOT exigir confirmação destrutiva, ao contrário do arquivar e do apagar. Uma conta arquivada é garantidamente de **saldo zero** — arquivar conta permanente já o exige —, logo reabri-la restaura uma conta consistente e não reintroduz dinheiro preso nem carece de reconciliação. Uma vez desarquivada, a conta SHALL reaparecer nas listagens de contas ativas e voltar a ser oferecida na seleção de contas de novos lançamentos.

Uma conta desarquivada SHALL voltar como conta **comum**, nunca como padrão: como a conta padrão não pode ser arquivada, nenhuma conta arquivada foi a padrão, e o desarquivamento restaura a **existência** da conta, não o seu papel. Reeleger a padrão permanece uma ação separada e explícita.

A oferta de desarquivar e a de retirar (arquivar/apagar) SHALL ser mutuamente exclusivas pelo estado de arquivamento — decisão de apresentação com dono único, o mesmo já compartilhado por conta, cartão e categoria. Desarquivar SHALL ser oferecido a partir de uma **visualização da conta** alcançada pela listagem dedicada de arquivadas, e essa visualização — por só ser alcançada a partir dessa listagem — SHALL oferecer exclusivamente o desarquivar.

#### Scenario: Desarquivar uma conta arquivada
- **WHEN** o usuário desarquiva uma conta que estava arquivada
- **THEN** o flag `accounts.isArchived` é revertido para falso, a conta reaparece nas listagens ativas e volta a ser oferecida na seleção de contas de novos lançamentos, e as suas entries permanecem intactas

#### Scenario: Desarquivar não é recusado nem pede confirmação
- **WHEN** o desarquivamento de uma conta arquivada é solicitado ao domínio
- **THEN** a operação é executada sem recusa por invariante e sem modal de confirmação destrutiva, por reabrir uma conta garantidamente de saldo zero

#### Scenario: Conta desarquivada volta como comum
- **WHEN** o usuário desarquiva uma conta
- **THEN** ela volta como conta comum, não como padrão, e a conta padrão vigente permanece inalterada

#### Scenario: A visualização de uma conta arquivada oferece apenas desarquivar
- **WHEN** o usuário abre a visualização de uma conta a partir da listagem de arquivadas
- **THEN** a interface oferece a ação de desarquivar, e não oferece arquivar nem apagar

## MODIFIED Requirements

### Requirement: Conta com lançamentos é arquivada, nunca apagada
Uma conta ou cartão que possua qualquer lançamento MUST NOT ser removida do plano de contas. O sistema SHALL arquivá-la: a conta permanece no plano de contas, com o seu tipo real preservado, marcada como arquivada, e os seus lançamentos históricos permanecem intactos e atribuídos a ela. Uma conta sem nenhum lançamento MAY ser removida, por não haver história a preservar.

Uma categoria que possua qualquer lançamento MUST NOT ser removida: SHALL ser arquivada, e as entries que carregam a sua dimensão permanecem intactas e classificadas nela. O fato "possui lançamentos" SHALL ser derivado do razão pelo mesmo mecanismo das demais fachadas — a existência de entry, consultada pela dimensão da categoria em vez de pela conta.

Uma conta arquivada MUST NOT ser oferecida na seleção de contas de um novo lançamento, e MUST NOT aparecer nas listagens de contas ativas; ela SHALL, porém, permanecer **acessível** por uma listagem dedicada de arquivadas, em tela própria, de onde pode ser visualizada e desarquivada. Um cartão arquivado MUST NOT ser oferecido nos seus seletores de lançamento nem aparecer na tela de cartões ativa; ele SHALL, porém, permanecer **acessível** por uma listagem dedicada de arquivados, em tela própria, de onde pode ser visualizado e desarquivado. Uma categoria arquivada MUST NOT ser oferecida nos seus seletores de lançamento nem aparecer nas listagens ativas da sua tela; ela SHALL, porém, permanecer **acessível** por uma listagem dedicada de arquivadas na sua própria tela, de onde pode ser visualizada e desarquivada. Tornar a arquivada visível nessas listagens MUST NOT reintroduzi-la em seletor algum nem nas listagens ativas.

O estado de arquivamento de conta e cartão SHALL residir **exclusivamente no plano de contas**, e a fachada de cartão SHALL consumi-lo da sua conta pelo vínculo que já possui — MUST NOT existir cópia desse estado nessa fachada. Todo cartão SHALL possuir conta no plano de contas desde a sua criação, de modo que a consulta não dependa de tratamento para vínculo ausente.

O estado de arquivamento de **categoria** SHALL residir na própria fachada. Categoria não é linha do plano de contas (`chart-of-accounts`), logo não há conta de onde consumir o estado, e não há duplicação: a fachada é o dono único. Isso MUST NOT alterar comportamento — o arquivamento de categoria nunca dependeu de saldo nem foi verificado na fronteira de escrita.

Apagar e arquivar SHALL ser **ações distintas**, com use cases distintos, e cada uma SHALL recusar o que seria **inválido**: apagar conta ou categoria com lançamentos é recusado, e não convertido em arquivamento silencioso. Um use case que faz coisa diferente do seu nome deixa quem o chama — e o usuário lendo o botão — com expectativa errada.

O domínio SHALL recusar apenas o que violaria uma invariante, e MUST NOT recusar o que é meramente inapropriado. Arquivar uma conta sem lançamentos, por exemplo, SHALL ser permitido: não quebra nada, apenas não é a ação que uma tela ofereceria.

A interface SHALL oferecer a ação correta pelo nome, e MUST NOT oferecer a que será recusada. Ela não é a salvaguarda: o desfecho é decidido pelo domínio. Qual retirada oferecer — arquivar ou apagar — é decisão de **apresentação** derivada do fato "possui lançamentos", com um dono único consumido por conta, cartão e categoria, e as telas MUST NOT re-derivá-la inline. Para **conta**, a oferta depende também de `isDefault`: quando a conta é a padrão, nenhuma retirada é oferecida — um **terceiro caso** (ver "A conta padrão não pode ser retirada"). Esse terceiro caso é próprio da conta (só a conta tem padrão) e SHALL ter o seu próprio dono único, envolvendo — sem alterar — o dono compartilhado do arquivar-vs-apagar; MUST NOT ser re-derivado inline por tela.

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

#### Scenario: Conta arquivada some das listagens ativas mas fica acessível
- **WHEN** uma conta com lançamentos é arquivada
- **THEN** ela desaparece das listagens de contas ativas e dos seletores de lançamento, permanecendo acessível pela listagem dedicada de contas arquivadas

#### Scenario: Apagar categoria com lançamentos é recusado
- **WHEN** o usuário tenta apagar uma categoria que possui entries classificadas na sua dimensão
- **THEN** o sistema recusa com erro tipado e a categoria é arquivada na própria fachada, permanecendo as entries classificadas nela

#### Scenario: Categoria arquivada some das listagens ativas mas fica acessível
- **WHEN** uma categoria com lançamentos é removida
- **THEN** ela é arquivada na fachada e desaparece das listagens ativas e dos seletores, permanecendo acessível pela listagem de arquivadas da sua tela

#### Scenario: Cartão arquivado some da tela ativa mas fica acessível
- **WHEN** um cartão com lançamentos é arquivado
- **THEN** ele desaparece da tela de cartões ativa e dos seletores de lançamento, permanecendo acessível pela listagem dedicada de cartões arquivados

#### Scenario: Cartão recém-criado tem conta
- **WHEN** um cartão é criado
- **THEN** a sua conta no plano de contas existe imediatamente, e a consulta de arquivamento não precisa tratar vínculo ausente

#### Scenario: Categoria recém-criada tem dimensão
- **WHEN** uma categoria é criada
- **THEN** a sua dimensão existe imediatamente, e a consulta de "possui lançamentos" não precisa tratar vínculo ausente

#### Scenario: Conta arquivada não é selecionável
- **WHEN** o usuário registra um novo lançamento
- **THEN** contas arquivadas não são oferecidas
