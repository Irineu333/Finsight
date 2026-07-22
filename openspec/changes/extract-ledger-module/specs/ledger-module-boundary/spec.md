## ADDED Requirements

### Requirement: Razão em módulo próprio e autocontido
O razão — plano de contas, entries, transações, dimensões, a fronteira de escrita e as leituras derivadas — SHALL residir em um módulo `:core:ledger` dedicado. Esse módulo MUST NOT depender de nenhum módulo de feature, nem do módulo que monta o banco de dados da aplicação.

Nenhuma feature SHALL depender da `api` de outra feature para ler ou escrever no razão.

#### Scenario: Razão não vê o app
- **WHEN** as dependências declaradas de `:core:ledger` são inspecionadas
- **THEN** ela não contém nenhum módulo `feature:*` nem o módulo montador do banco

#### Scenario: Feature lê o razão sem passar por outra feature
- **WHEN** uma feature precisa de um saldo, de um total ou de escrever um lançamento
- **THEN** ela depende de `:core:ledger`, e não da `api` de qualquer outra feature

#### Scenario: Regra de domínio disponível para api de feature
- **WHEN** um caso de uso declarado na `api` de uma feature precisa de uma figura derivada do razão
- **THEN** ele pode depender do razão diretamente, sem violar a proibição de `api` depender de `api`

### Requirement: Separação razão/fachada imposta pelo compilador
O módulo do razão SHALL declarar as próprias tabelas e objetos de acesso a dados, de modo que nenhuma tabela de fachada esteja visível para ele em tempo de compilação. A montagem do banco de dados da aplicação e as suas migrações SHALL residir no módulo que agrega as tabelas de todas as origens, o qual SHALL depender do razão.

A independência do razão MUST NOT depender de convenção ou revisão: uma consulta do razão que referenciasse uma tabela de fachada SHALL falhar a compilação.

#### Scenario: Consulta do razão não alcança fachada
- **WHEN** uma consulta do razão tenta referenciar uma tabela de fachada
- **THEN** a compilação falha, por o tipo não estar visível no módulo

#### Scenario: Montagem do banco depende do razão
- **WHEN** o grafo de módulos é inspecionado
- **THEN** o módulo montador do banco depende do razão, e não o contrário

### Requirement: Critério de derivabilidade governa a superfície do razão
Uma leitura SHALL permanecer no razão quando for derivável exclusivamente de tipo de conta, sinal, período e dimensão. Uma leitura SHALL sair do razão apenas quando exigir vocabulário que só a fachada possui.

O nome de uma leitura MUST NOT ser usado como critério: uma leitura cujo nome evoca uma fachada mas cuja regra é derivável do razão SHALL ser renomeada para vocabulário de razão e permanecer nele. Mover tal leitura para uma feature SHALL ser tratado como duplicação de regra contábil entre módulos.

#### Scenario: Leitura derivável permanece no razão
- **WHEN** uma leitura classifica valores pela natureza das contra-pernas de uma transação
- **THEN** ela permanece no razão, com assinatura expressa em tipo de conta e sinal

#### Scenario: Nome de fachada é renomeado, não exportado
- **WHEN** uma leitura derivável do razão carrega nome de fachada
- **THEN** ela é renomeada para vocabulário de razão e permanece no módulo do razão

#### Scenario: Regra contábil não se duplica entre módulos
- **WHEN** o código é inspecionado
- **THEN** nenhuma regra derivável de tipo de conta, sinal, período ou dimensão tem implementação fora do razão
