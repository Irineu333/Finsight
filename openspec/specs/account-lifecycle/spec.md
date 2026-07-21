# account-lifecycle Specification

## Purpose

Como uma conta, um cartão ou uma categoria sai de circulação sem levar consigo a história que o razão registrou. Partidas dobradas não admitem apagar aquilo que entries referenciam: o que possui lançamentos é **arquivado** — permanece no plano de contas, com o seu tipo real, e some apenas das listagens e seletores. O estado de arquivamento mora numa única coluna do plano de contas (`chart-of-accounts`), e as fachadas o consomem da sua conta. Apagar e arquivar são ações distintas, cada uma recusando o que seria inválido, e a interface oferece a que o domínio vai executar.

## Requirements

### Requirement: Conta com lançamentos é arquivada, nunca apagada
Uma conta, cartão ou categoria que possua qualquer lançamento MUST NOT ser removida do plano de contas. O sistema SHALL arquivá-la: a conta permanece no plano de contas, com o seu tipo real preservado, marcada como arquivada, e os seus lançamentos históricos permanecem intactos e atribuídos a ela. Uma conta sem nenhum lançamento MAY ser removida, por não haver história a preservar.

Uma conta arquivada MUST NOT ser oferecida na seleção de contas de um novo lançamento, e MUST NOT aparecer nas listagens de contas ativas. O estado de arquivamento SHALL residir **exclusivamente no plano de contas**, e as fachadas de categoria e cartão SHALL consumi-lo da sua respectiva conta pelo vínculo que já possuem — MUST NOT existir cópia desse estado nas fachadas. Toda categoria e todo cartão SHALL possuir conta no plano de contas desde a sua criação, de modo que a consulta não dependa de tratamento para vínculo ausente. Apagar e arquivar SHALL ser **ações distintas**, com use cases distintos, e cada uma SHALL recusar o que seria **inválido**: apagar conta com lançamentos é recusado, e não convertido em arquivamento silencioso. Um use case que faz coisa diferente do seu nome deixa quem o chama — e o usuário lendo o botão — com expectativa errada.

O domínio SHALL recusar apenas o que violaria uma invariante, e MUST NOT recusar o que é meramente inapropriado. Arquivar uma conta sem lançamentos, por exemplo, SHALL ser permitido: não quebra nada, apenas não é a ação que uma tela ofereceria. Recusá-la faria o domínio impor uma preferência de apresentação, e faria falhar uma corrida inofensiva — o último lançamento removido entre a tela abrir e o usuário confirmar.

A interface SHALL oferecer a ação correta pelo nome, e MUST NOT oferecer a que será recusada. Ela não é a salvaguarda: o desfecho é decidido pelo domínio, e a interface que errar por uma corrida ainda encontra a recusa. Qual das duas oferecer é decisão de **apresentação**, derivada do fato "a conta possui lançamentos", e SHALL ter um dono único, consumido por conta, cartão e categoria — duas telas derivando essa apresentação em separado é o que as fez divergir em rótulo e ícone.

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
- **THEN** a conta é arquivada, por nada nisso ser inválido — ainda que a interface ofereça apagar nesse caso, e não arquivar

#### Scenario: A interface oferece a ação que vai acontecer
- **WHEN** uma conta, cartão ou categoria é exibida com a ação de retirá-la
- **THEN** o rótulo, o ícone e a modal correspondem à ação que o domínio executará, e são os mesmos nas três fachadas

#### Scenario: Categoria arquivada some da sua tela
- **WHEN** uma categoria com lançamentos é removida
- **THEN** ela é arquivada no plano de contas e desaparece da tela de categorias e dos seletores, sem que o estado seja duplicado na fachada

#### Scenario: Categoria recém-criada tem conta
- **WHEN** uma categoria ou cartão é criado
- **THEN** a sua conta no plano de contas existe imediatamente, e a consulta de arquivamento não precisa tratar vínculo ausente

#### Scenario: Conta arquivada não é selecionável
- **WHEN** o usuário registra um novo lançamento
- **THEN** contas arquivadas não são oferecidas

#### Scenario: Histórico de conta arquivada é preservado
- **WHEN** um lançamento de uma conta arquivada é exibido
- **THEN** ele mantém o seu rótulo e a sua editabilidade, derivados normalmente das suas entries, sem tratamento especial por a conta estar arquivada

#### Scenario: Fachada arquivada continua nomeada no histórico
- **WHEN** um lançamento referencia uma categoria ou cartão arquivado
- **THEN** o nome dela continua exibido — a leitura que resolve histórico enxerga as arquivadas, ao contrário da que alimenta seletores — e apenas o atalho de navegação deixa de ser oferecido

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

Remover uma fachada e a sua conta SHALL ser uma operação atômica, e na ordem que a referência impõe: a fachada aponta para a conta, logo a conta MUST NOT ser removida primeiro. Nenhuma violação de integridade do banco SHALL alcançar a interface — e quando um erro alcança o usuário, ele SHALL dizer o que fazer, não apenas que algo falhou.

#### Scenario: Remoção de conta referenciada é impedida
- **WHEN** uma remoção de conta referenciada por entries é tentada
- **THEN** ela é recusada no domínio antes de alcançar o banco, e nenhuma exceção de integridade escapa para a interface

#### Scenario: Fachada e conta são removidas juntas, na ordem certa
- **WHEN** uma categoria ou cartão sem lançamentos é apagado
- **THEN** a fachada e a sua conta são removidas na mesma transação, a fachada primeiro, sem violar a referência entre elas

#### Scenario: Nenhuma entry órfã de conta
- **WHEN** o plano de contas é inspecionado a qualquer momento
- **THEN** nenhuma `Entry` referencia conta inexistente
