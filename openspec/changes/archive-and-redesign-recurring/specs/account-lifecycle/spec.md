## ADDED Requirements

### Requirement: Recorrência em uso é arquivada, nunca apagada

Uma recorrência **em uso** MUST NOT ser removida: o sistema SHALL recusar a remoção com erro tipado e oferecer o arquivamento em seu lugar. Uma recorrência está em uso quando **alguma transação a nomeia** — carrega o seu vínculo de recorrência — **ou** quando **algum orçamento aponta para ela**. Uma recorrência que não esteja em uso MAY ser removida.

A história que o arquivamento de uma recorrência preserva SHALL ser entendida como **própria da fachada**, não do razão. Nenhuma entry referencia uma recorrência: o vínculo que um lançamento carrega não tem chave estrangeira, nenhuma leitura do razão o consulta, e a remoção já o anula explicitamente. O que a remoção destrói é o **vínculo** entre os lançamentos gerados e o template que os originou, o registro dos ciclos tratados, e a ligação de um orçamento à sua receita base. Recorrência é a primeira fachada deste ciclo de vida cuja preservação não decorre da integridade referencial do plano de contas, e a recusa SHALL ser justificada por essa perda própria — MUST NOT ser justificada por lançamentos que ficariam órfãos, porque nenhum ficaria.

O critério MUST NOT ser a existência de **ocorrência**. Um ciclo pulado registra uma ocorrência sem gerar lançamento algum — não escreve transação, não produz entry e não move dinheiro —, e recusar a remoção por causa dele seria recusar o meramente inapropriado, não o que viola uma invariante. Apagar uma recorrência que não está em uso SHALL descartar as suas ocorrências puladas, por não haver ciclo gerado a que elas se refiram.

O guard de **orçamento** existe por razão oposta à da categoria: em categoria ele impede que a chave estrangeira retire a categoria do orçamento silenciosamente; em recorrência ele existe porque **não há chave estrangeira alguma** ligando orçamento a recorrência, de modo que nenhum outro mecanismo impediria o orçamento de passar a apontar para uma recorrência inexistente e ter o seu limite lido como zero.

Editar uma recorrência arquivada MUST NOT desarquivá-la. A edição SHALL preservar o estado de arquivamento, que só muda por desarquivamento explícito.

#### Scenario: Apagar recorrência que gerou lançamento é recusado
- **WHEN** o usuário tenta apagar uma recorrência que alguma transação nomeia
- **THEN** o sistema recusa com erro tipado, não remove nem arquiva nada, e a interface diz que a recorrência precisa ser arquivada

#### Scenario: Apagar recorrência usada por orçamento é recusado
- **WHEN** o usuário tenta apagar uma recorrência para a qual algum orçamento aponta
- **THEN** o sistema recusa com erro tipado, nada é removido, e a interface diz o que precisa ser resolvido antes

#### Scenario: Apagar recorrência nunca usada
- **WHEN** o usuário apaga uma recorrência que nenhuma transação nomeia e à qual nenhum orçamento aponta
- **THEN** a recorrência é removida, junto com as suas eventuais ocorrências puladas

#### Scenario: Ciclo pulado não impede a remoção
- **WHEN** o usuário apaga uma recorrência cujo único histórico é um ciclo pulado
- **THEN** a remoção é aceita, por um ciclo pulado não ter gerado lançamento algum

#### Scenario: Editar uma recorrência arquivada não a desarquiva
- **WHEN** o usuário edita uma recorrência arquivada
- **THEN** as alterações são gravadas e ela permanece arquivada

### Requirement: Arquivar recorrência interrompe a geração de ocorrências

Arquivar uma recorrência SHALL interrompê-la: a partir do arquivamento ela MUST NOT ser apresentada como pendente, MUST NOT gerar novas ocorrências, MUST NOT aparecer nas listagens ativas da sua tela e MUST NOT ser oferecida como escolha nova em seletor algum — inclusive o de **receita base de orçamento**. Os lançamentos que ela já gerou SHALL permanecer intactos e continuar vinculados a ela.

Isso MUST NOT romper vínculo já estabelecido: um orçamento que já elegeu essa recorrência como receita base SHALL continuar exibindo-a e usando-a, e SHALL poder trocá-la — a continuidade de quem já usa vale aqui como vale para as demais fachadas.

Isto não é exceção ao arquivamento das demais fachadas. Arquivar é sair de circulação; para uma conta, um cartão ou uma categoria, estar em circulação é ser oferecível — para uma recorrência, a presença em circulação é **ativa**: ela gera. O efeito difere porque a fachada é ativa, não porque a regra é outra.

O estado de arquivamento de recorrência SHALL residir na própria fachada, pela mesma razão que o da categoria: recorrência não é linha do plano de contas (`chart-of-accounts`), logo não há conta de onde consumi-lo, e não há duplicação.

#### Scenario: Recorrência arquivada não gera pendência
- **WHEN** uma recorrência é arquivada e o seu dia de vencimento chega
- **THEN** ela não é apresentada como pendente nem oferecida para confirmação, e nenhuma ocorrência é gerada

#### Scenario: Recorrência arquivada não é oferecida como receita base
- **WHEN** o usuário monta um orçamento novo com limite percentual sobre uma receita
- **THEN** recorrências arquivadas não são oferecidas na seleção

#### Scenario: Orçamento que já usa a recorrência arquivada continua funcionando
- **WHEN** um orçamento percentual já elege uma recorrência e ela é arquivada depois
- **THEN** o orçamento continua exibindo-a como sua receita base, o seu limite segue sendo calculado sobre ela, e o usuário consegue trocá-la — a seleção não é apagada em silêncio

#### Scenario: Arquivar preserva os lançamentos gerados
- **WHEN** uma recorrência que já gerou lançamentos é arquivada
- **THEN** esses lançamentos permanecem intactos e continuam vinculados a ela, e as suas ocorrências permanecem registradas

### Requirement: Recorrência arquivada pode ser desarquivada

Uma recorrência arquivada SHALL poder voltar à circulação por uma operação de **desarquivamento**, com use case próprio, simétrica ao arquivamento: o flag de arquivamento da fachada é revertido e nada mais muda — os lançamentos gerados e as ocorrências já existiam antes do arquivamento, que nada tocou além do flag.

O desarquivamento MUST NOT ser recusado por invariante alguma. Ele SHALL, porém, ser **confirmado pelo usuário**, divergindo de conta, cartão e categoria, cujo desarquivamento é ação direta. A divergência não é de regra, é da fachada: naquelas três, voltar à circulação é recuperar visibilidade; numa recorrência é voltar a **gerar**. A confirmação existe para anunciar o alcance dessa retomada — e sobretudo o que ela **não** faz.

A inocuidade SHALL ser entendida como **nada desfazer**, não como repor o intervalo: os ciclos que decorreram durante o arquivamento não foram gerados e o desarquivamento MUST NOT gerá-los retroativamente — a geração retoma do ciclo corrente. Nisto o desarquivamento de recorrência difere do de conta e cartão, que reabrem um estado garantidamente de saldo zero e por isso nada deixam para trás. A confirmação SHALL dizer as duas coisas: que a geração retoma, e que o intervalo não volta.

Uma vez desarquivada, a recorrência SHALL voltar a ser apresentada como pendente quando for o caso, a reaparecer nas listagens ativas, e a ser oferecida como receita base de orçamento.

Uma recorrência arquivada SHALL permanecer **acessível** por uma listagem dedicada de arquivadas na sua própria tela — recorte de um seletor único, como em categoria —, de onde possa ser visualizada e desarquivada. Torná-la visível nessa listagem MUST NOT reintroduzi-la nas listagens ativas, nas pendências nem em seletor algum.

A oferta de desarquivar e a de retirar SHALL ser mutuamente exclusivas pelo estado de arquivamento, decisão de apresentação com o mesmo dono único.

#### Scenario: Desarquivar uma recorrência arquivada
- **WHEN** o usuário desarquiva uma recorrência que estava arquivada
- **THEN** o flag é revertido, ela volta a ser apresentada como pendente quando for o caso, reaparece nas listagens ativas e nos seletores, e os seus lançamentos e ocorrências permanecem intactos

#### Scenario: Desarquivar não repõe os ciclos decorridos
- **WHEN** o usuário desarquiva uma recorrência que passou meses arquivada
- **THEN** os ciclos daquele intervalo não são gerados retroativamente, e a geração retoma a partir do ciclo corrente

#### Scenario: Desarquivar é confirmado, e a confirmação diz o que não volta
- **WHEN** o usuário aciona o desarquivar de uma recorrência arquivada
- **THEN** a interface pede confirmação, dizendo que a geração retoma a partir do ciclo corrente e que os ciclos decorridos durante o arquivamento não são repostos

#### Scenario: Desarquivar não é recusado por invariante
- **WHEN** o desarquivamento de uma recorrência arquivada é solicitado ao domínio
- **THEN** a operação é executada, sem recusa por invariante alguma

#### Scenario: A visualização de uma recorrência arquivada oferece apenas desarquivar
- **WHEN** o usuário abre a visualização de uma recorrência arquivada
- **THEN** entre as ofertas de retirada a interface apresenta apenas desarquivar, e não oferece arquivar nem apagar

#### Scenario: Arquivada é alcançável sem voltar às listagens ativas
- **WHEN** o usuário seleciona o recorte de arquivadas na tela de recorrências
- **THEN** as recorrências arquivadas são listadas e podem ser abertas, sem que passem a aparecer nos recortes ativos nem nas pendências

## MODIFIED Requirements

### Requirement: Conta com lançamentos é arquivada, nunca apagada
Uma conta ou cartão que possua qualquer lançamento MUST NOT ser removida do plano de contas. O sistema SHALL arquivá-la: a conta permanece no plano de contas, com o seu tipo real preservado, marcada como arquivada, e os seus lançamentos históricos permanecem intactos e atribuídos a ela. Uma conta sem nenhum lançamento MAY ser removida, por não haver história a preservar.

Uma categoria que possua qualquer lançamento MUST NOT ser removida: SHALL ser arquivada, e as entries que carregam a sua dimensão permanecem intactas e classificadas nela. O fato "possui lançamentos" SHALL ser derivado do razão pelo mesmo mecanismo das demais fachadas — a existência de entry, consultada pela dimensão da categoria em vez de pela conta.

Uma **recorrência** em uso MUST NOT ser removida: SHALL ser arquivada. Diferentemente das três anteriores, a sua preservação não decorre da integridade referencial do plano de contas — nenhuma entry a referencia —, e o que se preserva é o vínculo dos lançamentos gerados, o registro dos ciclos tratados e a ligação de um orçamento à sua receita base. O critério de "em uso" e a sua justificativa vivem no requisito próprio da recorrência.

Uma conta arquivada MUST NOT ser oferecida na seleção de contas de um novo lançamento, e MUST NOT aparecer nas listagens de contas ativas; ela SHALL, porém, permanecer **acessível** por uma listagem dedicada de arquivadas, em tela própria, de onde pode ser visualizada e desarquivada. Um cartão arquivado MUST NOT ser oferecido nos seus seletores de lançamento nem aparecer na tela de cartões ativa; ele SHALL, porém, permanecer **acessível** por uma listagem dedicada de arquivados, em tela própria, de onde pode ser visualizado e desarquivado. Uma categoria arquivada MUST NOT ser oferecida nos seus seletores de lançamento nem aparecer nas listagens ativas da sua tela; ela SHALL, porém, permanecer **acessível** por uma listagem dedicada de arquivadas na sua própria tela, de onde pode ser visualizada e desarquivada. Uma recorrência arquivada MUST NOT ser oferecida em seletor algum nem aparecer nas listagens ativas ou nas pendências; ela SHALL, porém, permanecer **acessível** por uma listagem dedicada de arquivadas na sua própria tela, de onde pode ser visualizada e desarquivada. Tornar a arquivada visível nessas listagens MUST NOT reintroduzi-la em seletor algum nem nas listagens ativas.

Retirar a arquivada dos seletores MUST NOT retirá-la de onde ela **já está em uso**. Quem já a usa SHALL continuar usando: um vínculo estabelecido antes do arquivamento permanece válido, permanece **visível** para o consumidor que o detém, e permanece removível por ele. O arquivamento governa a escolha **nova**, não a já feita — do contrário o consumidor exibiria um vínculo que não consegue desfazer, ou o perderia sem ter pedido.

A arquivada SHALL aparecer, nesse consumidor, **apenas por já estar escolhida**, e MUST NOT ser oferecida como opção fresca ao lado das ativas. Desfeita a escolha, ela não volta a ser oferecível enquanto permanecer arquivada. Esta continuidade SHALL valer para toda fachada arquivável e para todo consumidor que guarde um vínculo com ela — as categorias de um orçamento e a receita base de um orçamento inclusive —, e SHALL ter forma única por consumidor, não ser reimplementada por tela.

O estado de arquivamento de conta e cartão SHALL residir **exclusivamente no plano de contas**, e a fachada de cartão SHALL consumi-lo da sua conta pelo vínculo que já possui — MUST NOT existir cópia desse estado nessa fachada. Todo cartão SHALL possuir conta no plano de contas desde a sua criação, de modo que a consulta não dependa de tratamento para vínculo ausente.

O estado de arquivamento de **categoria** e de **recorrência** SHALL residir na própria fachada. Nenhuma das duas é linha do plano de contas (`chart-of-accounts`), logo não há conta de onde consumir o estado, e não há duplicação: a fachada é o dono único. Isso MUST NOT alterar comportamento — o arquivamento dessas fachadas nunca dependeu de saldo nem foi verificado na fronteira de escrita.

Apagar e arquivar SHALL ser **ações distintas**, com use cases distintos, e cada uma SHALL recusar o que seria **inválido**: apagar conta, categoria ou recorrência em uso é recusado, e não convertido em arquivamento silencioso. Um use case que faz coisa diferente do seu nome deixa quem o chama — e o usuário lendo o botão — com expectativa errada.

O domínio SHALL recusar apenas o que violaria uma invariante, e MUST NOT recusar o que é meramente inapropriado. Arquivar uma conta sem lançamentos, por exemplo, SHALL ser permitido: não quebra nada, apenas não é a ação que uma tela ofereceria.

A interface SHALL oferecer a ação correta pelo nome, e MUST NOT oferecer a que será recusada. Ela não é a salvaguarda: o desfecho é decidido pelo domínio. Qual retirada oferecer — arquivar ou apagar — é decisão de **apresentação** derivada do fato "possui lançamentos" (ou, para a recorrência, "está em uso"), com um dono único por fachada, consumido por conta, cartão, categoria e recorrência, e as telas MUST NOT re-derivá-la inline. Para **conta**, a oferta depende também de `isDefault`: quando a conta é a padrão, nenhuma retirada é oferecida — um **terceiro caso** (ver "A conta padrão não pode ser retirada"). Esse terceiro caso é próprio da conta (só a conta tem padrão) e SHALL ter o seu próprio dono único, envolvendo — sem alterar — o dono compartilhado do arquivar-vs-apagar; MUST NOT ser re-derivado inline por tela.

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
- **WHEN** uma conta, cartão, categoria ou recorrência é exibida com a ação de retirá-la
- **THEN** o rótulo, o ícone e a modal correspondem à ação que o domínio executará, e são os mesmos nas quatro fachadas

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

#### Scenario: Recorrência arquivada some das listagens ativas mas fica acessível
- **WHEN** uma recorrência em uso é arquivada
- **THEN** ela desaparece das listagens ativas, das pendências e dos seletores, permanecendo acessível pela listagem de arquivadas da sua tela

#### Scenario: Quem já usa a arquivada continua usando
- **WHEN** uma fachada é arquivada depois de já ter sido escolhida por um consumidor — uma categoria já adicionada a um orçamento, uma recorrência já eleita como receita base
- **THEN** o vínculo permanece válido, o consumidor continua exibindo a fachada arquivada e consegue removê-la, e ela não é oferecida como opção nova ao lado das ativas

#### Scenario: Desfeita a escolha, a arquivada não volta a ser oferecida
- **WHEN** o consumidor remove o vínculo com uma fachada arquivada
- **THEN** ela deixa de aparecer naquele consumidor e não pode ser escolhida de novo enquanto permanecer arquivada

#### Scenario: Cartão recém-criado tem conta
- **WHEN** um cartão é criado
- **THEN** a sua conta no plano de contas existe imediatamente, e a consulta de arquivamento não precisa tratar vínculo ausente

#### Scenario: Categoria recém-criada tem dimensão
- **WHEN** uma categoria é criada
- **THEN** a sua dimensão existe imediatamente, e a consulta de "possui lançamentos" não precisa tratar vínculo ausente

#### Scenario: Conta arquivada não é selecionável
- **WHEN** o usuário registra um novo lançamento
- **THEN** contas arquivadas não são oferecidas
