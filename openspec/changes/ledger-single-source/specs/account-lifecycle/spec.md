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

### Requirement: Encerramento com saldo gera lançamento de baixa
Encerrar uma conta cujo saldo não seja zero SHALL registrar um lançamento de encerramento balanceado que zera esse saldo, tendo como contrapartida a conta `EQUITY` de reconciliação — a mesma usada pelos ajustes de saldo, por encerrar zerando um saldo ser uma reconciliação. O saldo MUST NOT desaparecer sem lançamento: a saída do dinheiro do patrimônio SHALL ser um lançamento **explícito, datado e balanceado**, recuperável do razão.

Esta capability MUST NOT exigir que o lançamento de baixa seja **visualmente distinguível** de um ajuste de saldo: com a contrapartida de reconciliação, as duas operações têm a mesma forma (`{monetária, EQUITY:Reconciliação}`) e a derivação de rótulo — total e sem tratamento especial, conforme `balanced-ledger` — produz `ADJUSTMENT` para ambas. Se a distinção for necessária, ela SHALL vir de estado próprio do lançamento (ex.: título), nunca de um ramo condicional nas leituras.

O patrimônio líquido após o encerramento SHALL ser idêntico ao que seria se a conta tivesse sido apagada, já que o saldo encerrado é zero. Contas encerradas SHALL continuar entrando nas leituras pelo mesmo mecanismo das demais, sem ramo condicional.

#### Scenario: Encerrar conta com saldo
- **WHEN** o usuário remove uma conta com saldo diferente de zero
- **THEN** o sistema registra um lançamento de encerramento que debita ou credita a conta pelo seu saldo contra a conta `EQUITY` de reconciliação, somando zero, e o saldo da conta passa a ser zero

#### Scenario: Saldo da conta encerrada é zero
- **WHEN** uma conta com saldo é encerrada
- **THEN** a soma das entries dessa conta passa a ser zero, e a diferença aparece como lançamento de baixa datado contra a conta de reconciliação

#### Scenario: Encerrar conta zerada não gera lançamento
- **WHEN** o usuário remove uma conta cujo saldo já é zero mas que possui lançamentos
- **THEN** a conta é encerrada sem lançamento de baixa, por não haver saldo a zerar

### Requirement: Integridade referencial do plano de contas
O sistema MUST NOT permitir que uma conta referenciada por qualquer `Entry` seja removida do plano de contas. Toda `Entry` SHALL referenciar uma conta existente, encerrada ou não. Uma tentativa de remover conta com lançamentos SHALL ser convertida em encerramento antes de alcançar o banco. Nenhuma violação de integridade do banco SHALL alcançar a interface: hoje ela alcança, porque `DeleteAccountUseCase` envolve a chamada num `either { }`, que captura *raises* do Arrow mas **não** exceções — a `SQLiteException` atravessa o `either` e o `viewModelScope` sem handler, e o `onLeft` de crashlytics nunca roda.

#### Scenario: Remoção de conta referenciada é impedida
- **WHEN** uma remoção de conta referenciada por entries é tentada
- **THEN** ela é convertida em encerramento antes de alcançar o banco, e nenhuma exceção de integridade escapa para a interface

#### Scenario: Nenhuma entry órfã de conta
- **WHEN** o plano de contas é inspecionado a qualquer momento
- **THEN** nenhuma `Entry` referencia conta inexistente
