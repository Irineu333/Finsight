# ledger-module-boundary Specification

## Purpose
TBD - created by archiving change extract-ledger-module. Update Purpose after archive.
## Requirements
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

### Requirement: Separação razão/fachada imposta mecanicamente
Nenhuma consulta do razão SHALL referenciar tabela de fachada, e essa proibição MUST NOT depender de convenção ou de revisão de código: SHALL existir um mecanismo automatizado que a faça falhar.

O mecanismo preferencial SHALL ser a falha de compilação: o módulo do razão declara as próprias tabelas e objetos de acesso a dados, de modo que nenhuma tabela de fachada lhe esteja visível, e a montagem do banco de dados da aplicação e as suas migrações residem no módulo que agrega as tabelas de todas as origens, o qual depende do razão.

Quando uma limitação da ferramenta de persistência tornar esse arranjo inviável, a proibição SHALL ser garantida por um teste automatizado que inspecione as consultas do razão e falhe ao encontrar referência a tabela que não seja do razão. A limitação encontrada SHALL ser registrada por escrito. MUST NOT existir estado em que nem a compilação nem um teste garantam a proibição.

#### Scenario: Consulta do razão não alcança fachada
- **WHEN** uma consulta do razão referencia uma tabela de fachada
- **THEN** a compilação falha, ou um teste automatizado falha apontando a referência

#### Scenario: Montagem do banco depende do razão
- **WHEN** o arranjo preferencial está em vigor e o grafo de módulos é inspecionado
- **THEN** o módulo montador do banco depende do razão, e não o contrário

#### Scenario: Limitação da ferramenta não dissolve a garantia
- **WHEN** a direção preferencial se mostra inviável e a direção é revertida
- **THEN** a limitação é registrada por escrito e o teste automatizado passa a garantir a proibição, que em nenhum momento fica sem mecanismo

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

