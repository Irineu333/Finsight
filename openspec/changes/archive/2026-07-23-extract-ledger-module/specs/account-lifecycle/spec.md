## MODIFIED Requirements

### Requirement: Conta com lançamentos é arquivada, nunca apagada
Uma conta ou cartão que possua qualquer lançamento MUST NOT ser removida do plano de contas. O sistema SHALL arquivá-la: a conta permanece no plano de contas, com o seu tipo real preservado, marcada como arquivada, e os seus lançamentos históricos permanecem intactos e atribuídos a ela. Uma conta sem nenhum lançamento MAY ser removida, por não haver história a preservar.

Uma categoria que possua qualquer lançamento MUST NOT ser removida: SHALL ser arquivada, e as entries que carregam a sua dimensão permanecem intactas e classificadas nela. O fato "possui lançamentos" SHALL ser derivado do razão pelo mesmo mecanismo das demais fachadas — a existência de entry, consultada pela dimensão da categoria em vez de pela conta.

Uma conta arquivada MUST NOT ser oferecida na seleção de contas de um novo lançamento, e MUST NOT aparecer nas listagens de contas ativas; o mesmo vale para uma categoria arquivada nos seus seletores e listagens.

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

#### Scenario: Categoria arquivada some da sua tela
- **WHEN** uma categoria com lançamentos é removida
- **THEN** ela é arquivada na fachada e desaparece da tela de categorias e dos seletores

#### Scenario: Cartão recém-criado tem conta
- **WHEN** um cartão é criado
- **THEN** a sua conta no plano de contas existe imediatamente, e a consulta de arquivamento não precisa tratar vínculo ausente

#### Scenario: Categoria recém-criada tem dimensão
- **WHEN** uma categoria é criada
- **THEN** a sua dimensão existe imediatamente, e a consulta de "possui lançamentos" não precisa tratar vínculo ausente

#### Scenario: Conta arquivada não é selecionável
- **WHEN** o usuário registra um novo lançamento
- **THEN** contas arquivadas não são oferecidas

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
