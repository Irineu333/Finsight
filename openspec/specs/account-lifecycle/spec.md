# account-lifecycle Specification

## Purpose

Como uma conta, um cartão ou uma categoria sai de circulação sem levar consigo a história que o razão registrou. Partidas dobradas não admitem apagar aquilo que entries referenciam: o que possui lançamentos é **arquivado** — permanece no plano de contas, com o seu tipo real, e some apenas das listagens e seletores. O estado de arquivamento mora numa única coluna do plano de contas (`chart-of-accounts`), e as fachadas o consomem da sua conta. Apagar e arquivar são ações distintas, cada uma recusando o que seria inválido, e a interface oferece a que o domínio vai executar.
## Requirements
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

### Requirement: Arquivar conta permanente exige saldo zero
Arquivar uma conta **permanente** (`ASSET`/`LIABILITY`/`EQUITY`) cujo saldo não seja zero SHALL ser recusado, com erro tipado. O sistema MUST NOT gerar lançamento algum para zerar o saldo por conta própria: um lançamento que o usuário não pediu aparece no histórico dele como se ele o tivesse feito, e substitui a informação que só ele tem — para onde o dinheiro foi — por uma reconciliação genérica.

O usuário SHALL resolver o saldo antes, pelos meios que já existem: transferir para outra conta, registrar a despesa, ou ajustar o saldo. Cada um desses caminhos registra a intenção real; a baixa automática registrava apenas que havia um saldo incômodo.

A exigência SHALL valer apenas para conta **permanente**, e a distinção é a da própria contabilidade, não uma exceção deste app. Contas permanentes (reais) — `ASSET`, `LIABILITY`, `EQUITY` — têm saldo que representa o que **existe agora** e atravessa períodos. Contas **temporárias** (nominais) — `INCOME` e `EXPENSE` — têm saldo que é o **total de um período**, zerado apenas por lançamento de arquivamento de exercício contra o patrimônio, que este app não realiza.

Só uma conta permanente pode reter dinheiro que o arquivamento deixaria preso. Uma categoria não retém nada: o seu saldo é a soma do que já se moveu, e por isso não volta a zero — exigir zero ali tornaria impossível arquivar categoria alguma.

Isto MUST NOT ser confundido com o problema que o arquivamento resolve. Apagar uma conta com saldo fazia o dinheiro sumir do patrimônio sem registro; arquivar preserva as entries, então nada some. Exigir saldo zero fecha também o caso oposto — uma conta arquivada **com** saldo deixaria, no patrimônio, dinheiro que não aparece em conta visível alguma.

⚠️ A migração `v7 → v9` é o único lugar onde uma baixa automática permanece legítima, e por não haver alternativa: ela reconstrói contas **já apagadas** no v7, cujo dinheiro já havia deixado os livros, sem usuário a quem perguntar. Ali a baixa registra um fato passado; em runtime ela inventaria um.

#### Scenario: Arquivar conta com saldo é recusado
- **WHEN** o usuário tenta arquivar uma conta cujo saldo é diferente de zero
- **THEN** o sistema recusa a operação com erro tipado, não escreve nada, e a interface explica que o saldo precisa ser resolvido antes

#### Scenario: Arquivar conta zerada
- **WHEN** o usuário arquiva uma conta monetária cujo saldo já é zero mas que possui lançamentos
- **THEN** a conta é arquivada, sem lançamento algum, e o histórico permanece

#### Scenario: Arquivar categoria usada não depende do seu saldo
- **WHEN** o usuário arquiva uma categoria que possui lançamentos e cujo saldo acumulado é diferente de zero
- **THEN** a categoria é arquivada normalmente, por o saldo de uma conta de fluxo não representar dinheiro a resolver

### Requirement: Integridade referencial do plano de contas
O sistema MUST NOT permitir que uma conta referenciada por qualquer `Entry` seja removida do plano de contas. Toda `Entry` SHALL referenciar uma conta existente, arquivada ou não. A tentativa SHALL ser recusada no domínio, antes de alcançar o banco, e não convertida noutra operação.

Remover uma fachada e a sua conta SHALL ser uma operação atômica, e na ordem que a referência impõe: a fachada aponta para a conta, logo a conta MUST NOT ser removida primeiro.

Remover uma fachada e a sua **dimensão** SHALL igualmente ser atômico, na mesma transação, e a dimensão SHALL ser removida — nunca deixada órfã. Diferentemente da conta, uma dimensão MAY ser removida ainda que entries a referenciem: a referência é anulável, e a remoção SHALL fazer essas entries passarem ao estado não classificado, preservando `amount` e conta. Uma fachada removida MUST NOT deixar entries classificadas numa dimensão que já não corresponde a nada.

Nenhuma violação de integridade do banco SHALL alcançar a interface — e quando um erro alcança o usuário, ele SHALL dizer o que fazer, não apenas que algo falhou.

#### Scenario: Remoção de conta referenciada é impedida
- **WHEN** uma remoção de conta referenciada por entries é tentada
- **THEN** ela é recusada no domínio antes de alcançar o banco, e nenhuma exceção de integridade escapa para a interface

#### Scenario: Fachada e conta são removidas juntas, na ordem certa
- **WHEN** um cartão sem lançamentos é apagado
- **THEN** a fachada e a sua conta são removidas na mesma transação, a fachada primeiro, sem violar a referência entre elas

#### Scenario: Fachada e dimensão são removidas juntas
- **WHEN** uma fachada que possui dimensão é apagada
- **THEN** a sua dimensão é removida na mesma transação, e nenhuma linha de dimensão permanece sem fachada

#### Scenario: Remoção de fachada não altera saldo
- **WHEN** uma fachada com dimensão é removida e existiam entries classificadas nela
- **THEN** essas entries passam a não classificadas, seus `amount` e contas permanecem inalterados, e todo saldo de conta permanece idêntico

### Requirement: Cartão arquivado pode ser desarquivado

Um cartão arquivado SHALL poder voltar à circulação por uma operação de **desarquivamento**, com use case próprio, simétrica ao arquivamento. Diferentemente da categoria — cujo estado de arquivamento mora na própria fachada —, o estado de um cartão mora na sua conta do plano de contas (`chart-of-accounts`), da qual a fachada o consome. Portanto o desarquivamento de cartão SHALL **reabrir a conta** que lhe dá suporte: o flag `accounts.isArchived` da sua conta `LIABILITY` é revertido para falso, o inverso exato de `close()`, e nada mais muda — as entries da conta e as faturas do cartão permanecem intactas, e já existiam antes do arquivamento, que nada tocou além do flag. A fachada de cartão MUST NOT ganhar cópia própria desse estado: continua consumindo-o da sua conta.

O desarquivamento é uma ação **reversível e inócua**: MUST NOT ser recusado por invariante alguma e MUST NOT exigir confirmação destrutiva, ao contrário do arquivar e do apagar. Um cartão arquivado é garantidamente de **saldo zero** — arquivar conta permanente já o exige —, logo reabri-lo restaura uma conta consistente e não reintroduz dinheiro preso nem carece de reconciliação. Uma vez desarquivado, o cartão SHALL reaparecer na tela de cartões ativa e voltar a ser oferecido para novos lançamentos.

A oferta de desarquivar e a de retirar (arquivar/apagar) SHALL ser mutuamente exclusivas pelo estado de arquivamento — decisão de apresentação com dono único, o mesmo já compartilhado por conta, cartão e categoria. Desarquivar SHALL ser oferecido a partir de uma **visualização do cartão** alcançada pela listagem dedicada de arquivados, e essa visualização — por só ser alcançada a partir dessa listagem — SHALL oferecer exclusivamente o desarquivar. A oferta de retirar um cartão **não** arquivado permanece na tela de cartões ativa, como já é hoje, e MUST NOT ser duplicada na visualização de arquivados.

#### Scenario: Desarquivar um cartão arquivado
- **WHEN** o usuário desarquiva um cartão que estava arquivado
- **THEN** o flag de arquivamento da sua conta `LIABILITY` é revertido para falso, o cartão reaparece na tela de cartões ativa e volta a ser oferecido para novos lançamentos, e as entries da sua conta e as suas faturas permanecem intactas

#### Scenario: Desarquivar não é recusado nem pede confirmação
- **WHEN** o desarquivamento de um cartão arquivado é solicitado ao domínio
- **THEN** a operação é executada sem recusa por invariante e sem modal de confirmação destrutiva, por reabrir uma conta garantidamente de saldo zero

#### Scenario: A visualização de um cartão arquivado oferece apenas desarquivar
- **WHEN** o usuário abre a visualização de um cartão a partir da listagem de arquivados
- **THEN** a interface oferece a ação de desarquivar, e não oferece arquivar nem apagar

