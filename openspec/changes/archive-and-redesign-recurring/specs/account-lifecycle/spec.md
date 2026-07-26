## ADDED Requirements

### Requirement: Recorrência em uso é arquivada, nunca apagada

Uma recorrência é a quarta fachada deste ciclo de vida, ao lado de conta, cartão e categoria, e MUST obedecer à mesma distinção entre arquivar e apagar.

Uma recorrência **em uso** MUST NOT ser removida: o sistema SHALL recusar a remoção com erro tipado e oferecer o arquivamento em seu lugar. Uma recorrência está em uso quando **alguma transação a nomeia** — carrega o seu vínculo de recorrência — **ou** quando **algum orçamento aponta para ela**. Uma recorrência que não esteja em uso MAY ser removida, por não haver história a preservar.

O critério MUST NOT ser a existência de **ocorrência**. Um ciclo pulado registra uma ocorrência sem gerar lançamento algum — não escreve transação, não produz entry e não move dinheiro —, e recusar a remoção por causa dele seria recusar o meramente inapropriado, não o que viola uma invariante. Apagar uma recorrência que não está em uso SHALL descartar as suas ocorrências puladas, por não haver, sem lançamento gerado, história do razão a preservar.

O guard de **orçamento** existe por razão oposta à da categoria: em categoria ele impede que a chave estrangeira retire a categoria do orçamento silenciosamente; em recorrência ele existe porque **não há chave estrangeira alguma** ligando orçamento a recorrência, de modo que nenhum outro mecanismo impediria o orçamento de passar a apontar para uma recorrência inexistente e ter o seu limite lido como zero.

Apagar e arquivar SHALL ser ações distintas, com use cases distintos, e nenhuma SHALL ser convertida silenciosamente na outra. Qual retirada oferecer SHALL ser decisão de apresentação com **dono único no domínio**, derivada do fato "está em uso", e as telas MUST NOT re-derivá-la inline. A interface SHALL oferecer a ação pelo nome que o domínio vai executar, com o mesmo rótulo e o mesmo ícone já usados pelas demais fachadas.

#### Scenario: Apagar recorrência que gerou lançamento é recusado
- **WHEN** o usuário tenta apagar uma recorrência que alguma transação nomeia
- **THEN** o sistema recusa com erro tipado, não remove nem arquiva nada, e a interface diz que a recorrência precisa ser arquivada

#### Scenario: Apagar recorrência usada por orçamento é recusado
- **WHEN** o usuário tenta apagar uma recorrência para a qual algum orçamento aponta
- **THEN** o sistema recusa com erro tipado, nada é removido, e a interface diz o que precisa ser resolvido antes

#### Scenario: Apagar recorrência nunca usada
- **WHEN** o usuário apaga uma recorrência que nenhuma transação nomeia e à qual nenhum orçamento aponta
- **THEN** a recorrência é removida, junto com as suas eventuais ocorrências puladas, por não haver história do razão a preservar

#### Scenario: Ciclo pulado não impede a remoção
- **WHEN** o usuário apaga uma recorrência cujo único histórico é um ciclo pulado
- **THEN** a remoção é aceita, por um ciclo pulado não ter gerado lançamento algum

#### Scenario: A interface oferece a retirada que o domínio executará
- **WHEN** uma recorrência é exibida com a ação de retirá-la
- **THEN** o rótulo, o ícone e a modal correspondem à ação que o domínio executará, resolvidos pelo dono único e não re-derivados pela tela

### Requirement: Arquivar recorrência interrompe a geração de ocorrências

Arquivar uma recorrência SHALL interrompê-la: a partir do arquivamento ela MUST NOT ser oferecida como pendente nem gerar novas ocorrências, e MUST NOT aparecer nas listagens ativas da sua tela. Os lançamentos que ela já gerou SHALL permanecer intactos e continuar vinculados a ela.

Isto MUST NOT ser lido como exceção ao arquivamento das demais fachadas. Arquivar é sair de circulação; para uma conta, um cartão ou uma categoria, estar em circulação é ser oferecível — para uma recorrência, estar em circulação **é gerar ocorrências**. O efeito difere porque a fachada é ativa, não porque a regra é outra.

O estado de arquivamento de recorrência SHALL residir na própria fachada, pela mesma razão que o da categoria: recorrência não é linha do plano de contas (`chart-of-accounts`), logo não há conta de onde consumi-lo, e não há duplicação.

#### Scenario: Recorrência arquivada não gera pendência
- **WHEN** uma recorrência é arquivada e o seu dia de vencimento chega
- **THEN** ela não é apresentada como pendente nem oferecida para confirmação, e nenhuma ocorrência é gerada

#### Scenario: Arquivar preserva os lançamentos gerados
- **WHEN** uma recorrência que já gerou lançamentos é arquivada
- **THEN** esses lançamentos permanecem intactos e continuam vinculados a ela, e as suas ocorrências permanecem registradas

### Requirement: Recorrência arquivada pode ser desarquivada

Uma recorrência arquivada SHALL poder voltar à circulação por uma operação de **desarquivamento**, com use case próprio, simétrica ao arquivamento: o flag de arquivamento da fachada é revertido e nada mais muda — os lançamentos gerados e as ocorrências já existiam antes do arquivamento, que nada tocou além do flag.

O desarquivamento é uma ação **reversível e inócua**: MUST NOT ser recusado por invariante alguma e MUST NOT exigir confirmação destrutiva, ao contrário do arquivar e do apagar. Uma vez desarquivada, a recorrência SHALL voltar a ser apresentada como pendente quando for o caso e a reaparecer nas listagens ativas.

Uma recorrência arquivada MUST permanecer **acessível** por uma listagem dedicada de arquivadas na sua própria tela — recorte de um seletor único, como em categoria —, de onde possa ser visualizada e desarquivada. Torná-la visível nessa listagem MUST NOT reintroduzi-la nas listagens ativas nem nas pendências.

A oferta de desarquivar e a de retirar SHALL ser mutuamente exclusivas pelo estado de arquivamento, decisão de apresentação com o mesmo dono único.

#### Scenario: Desarquivar uma recorrência arquivada
- **WHEN** o usuário desarquiva uma recorrência que estava arquivada
- **THEN** o flag é revertido, ela volta a ser apresentada como pendente quando for o caso, reaparece nas listagens ativas, e os seus lançamentos e ocorrências permanecem intactos

#### Scenario: Desarquivar não é recusado nem pede confirmação
- **WHEN** o desarquivamento de uma recorrência arquivada é solicitado ao domínio
- **THEN** a operação é executada sem recusa por invariante e sem modal de confirmação destrutiva

#### Scenario: A visualização de uma recorrência arquivada oferece apenas desarquivar
- **WHEN** o usuário abre a visualização de uma recorrência arquivada
- **THEN** a interface oferece a ação de desarquivar, e não oferece arquivar nem apagar

#### Scenario: Arquivada é alcançável sem voltar às listagens ativas
- **WHEN** o usuário seleciona o recorte de arquivadas na tela de recorrências
- **THEN** as recorrências arquivadas são listadas e podem ser abertas, sem que passem a aparecer nos recortes ativos nem nas pendências
