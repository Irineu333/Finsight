## ADDED Requirements

### Requirement: Conta com lançamentos é encerrada, nunca apagada
Uma conta, cartão ou categoria que possua qualquer lançamento MUST NOT ser removida do plano de contas. O sistema SHALL encerrá-la: a conta permanece no plano de contas, com o seu tipo real preservado, marcada como encerrada, e os seus lançamentos históricos permanecem intactos e atribuídos a ela. Uma conta sem nenhum lançamento MAY ser removida, por não haver história a preservar.

Uma conta encerrada MUST NOT ser oferecida na seleção de contas de um novo lançamento, e MUST NOT aparecer nas listagens de contas ativas. O estado de encerramento SHALL residir **exclusivamente no plano de contas**, e as fachadas de categoria e cartão SHALL consumi-lo da sua respectiva conta pelo vínculo que já possuem — MUST NOT existir cópia desse estado nas fachadas. Toda categoria e todo cartão SHALL possuir conta no plano de contas desde a sua criação, de modo que a consulta não dependa de tratamento para vínculo ausente. O usuário MUST NOT precisar distinguir "apagar" de "encerrar": a ação continua sendo uma só, e o sistema escolhe o tratamento correto conforme haja ou não história.

#### Scenario: Encerrar conta com lançamentos
- **WHEN** o usuário remove uma conta que possui lançamentos
- **THEN** a conta é marcada como encerrada, permanece no plano de contas com o seu tipo, seus lançamentos continuam atribuídos a ela, e ela desaparece das listagens e seletores

#### Scenario: Remover conta sem lançamentos
- **WHEN** o usuário remove uma conta que não possui nenhum lançamento
- **THEN** a conta é removida do plano de contas, por não haver história a preservar

#### Scenario: Categoria encerrada some da sua tela
- **WHEN** uma categoria com lançamentos é removida
- **THEN** ela é encerrada no plano de contas e desaparece da tela de categorias e dos seletores, sem que o estado seja duplicado na fachada

#### Scenario: Categoria recém-criada tem conta
- **WHEN** uma categoria ou cartão é criado
- **THEN** a sua conta no plano de contas existe imediatamente, e a consulta de encerramento não precisa tratar vínculo ausente

#### Scenario: Conta encerrada não é selecionável
- **WHEN** o usuário registra um novo lançamento
- **THEN** contas encerradas não são oferecidas

#### Scenario: Histórico de conta encerrada é preservado
- **WHEN** um lançamento de uma conta encerrada é exibido
- **THEN** ele mantém o seu rótulo e a sua editabilidade, derivados normalmente das suas entries, sem tratamento especial por a conta estar encerrada

### Requirement: Encerramento exige saldo zero
Encerrar uma conta cujo saldo não seja zero SHALL ser recusado, com erro tipado. O sistema MUST NOT gerar lançamento algum para zerar o saldo por conta própria: um lançamento que o usuário não pediu aparece no histórico dele como se ele o tivesse feito, e substitui a informação que só ele tem — para onde o dinheiro foi — por uma reconciliação genérica.

O usuário SHALL resolver o saldo antes, pelos meios que já existem: transferir para outra conta, registrar a despesa, ou ajustar o saldo. Cada um desses caminhos registra a intenção real; a baixa automática registrava apenas que havia um saldo incômodo.

Isto MUST NOT ser confundido com o problema que o encerramento resolve. Apagar uma conta com saldo fazia o dinheiro sumir do patrimônio sem registro; encerrar preserva as entries, então nada some. Exigir saldo zero fecha também o caso oposto — uma conta encerrada **com** saldo deixaria, no patrimônio, dinheiro que não aparece em conta visível alguma.

⚠️ A migração `v7 → v9` é o único lugar onde uma baixa automática permanece legítima, e por não haver alternativa: ela reconstrói contas **já apagadas** no v7, cujo dinheiro já havia deixado os livros, sem usuário a quem perguntar. Ali a baixa registra um fato passado; em runtime ela inventaria um.

#### Scenario: Encerrar conta com saldo é recusado
- **WHEN** o usuário tenta encerrar uma conta cujo saldo é diferente de zero
- **THEN** o sistema recusa a operação com erro tipado, não escreve nada, e a interface explica que o saldo precisa ser resolvido antes

#### Scenario: Encerrar conta zerada
- **WHEN** o usuário remove uma conta cujo saldo já é zero mas que possui lançamentos
- **THEN** a conta é encerrada, sem lançamento algum, e o histórico permanece

### Requirement: Integridade referencial do plano de contas
O sistema MUST NOT permitir que uma conta referenciada por qualquer `Entry` seja removida do plano de contas. Toda `Entry` SHALL referenciar uma conta existente, encerrada ou não. Uma tentativa de remover conta com lançamentos SHALL ser convertida em encerramento antes de alcançar o banco. Nenhuma violação de integridade do banco SHALL alcançar a interface: hoje ela alcança, porque `DeleteAccountUseCase` envolve a chamada num `either { }`, que captura *raises* do Arrow mas **não** exceções — a `SQLiteException` atravessa o `either` e o `viewModelScope` sem handler, e o `onLeft` de crashlytics nunca roda.

#### Scenario: Remoção de conta referenciada é impedida
- **WHEN** uma remoção de conta referenciada por entries é tentada
- **THEN** ela é convertida em encerramento antes de alcançar o banco, e nenhuma exceção de integridade escapa para a interface

#### Scenario: Nenhuma entry órfã de conta
- **WHEN** o plano de contas é inspecionado a qualquer momento
- **THEN** nenhuma `Entry` referencia conta inexistente
