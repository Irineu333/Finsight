## MODIFIED Requirements

### Requirement: Domínio compartilhado em core
Os modelos de domínio e os tipos de erro SHALL residir em módulos `:core:*`, não nas apis das features. As assinaturas públicas de apis e entry points SHALL referenciar apenas tipos de `:core:*`.

Os modelos e regras do razão — plano de contas, entries, transações, dimensões e as regras deriváveis delas — SHALL residir em `:core:ledger`, e MUST NOT residir na `api` de nenhuma feature. Os modelos de fachada de cada feature SHALL residir em `:core:model`.

#### Scenario: Modelo emaranhado usado por várias apis
- **WHEN** duas ou mais apis precisam referenciar um modelo compartilhado
- **THEN** ambas referenciam o tipo do `:core:*` dono, sem dependência entre as apis

#### Scenario: Modelo do razão fora de feature
- **WHEN** o modelo de conta, entry, transação ou dimensão é localizado
- **THEN** ele reside em `:core:ledger`, e nenhuma `api` de feature o declara

### Requirement: Banco de dados centralizado em core
O `AppDatabase`, os converters e as migrações do Room SHALL residir em `:core:database`, que agrega as entities de todas as origens. As entities e DAOs de fachada SHALL residir em `:core:database`; as entities e DAOs do razão SHALL residir em `:core:ledger`, que MUST NOT depender de `:core:database`.

`:core:database` SHALL depender de `:core:ledger` para montar o banco, de modo que uma consulta do razão não possa referenciar tabela de fachada — o tipo não está visível em tempo de compilação. Se uma limitação da ferramenta de persistência tornar essa direção inviável, ela MAY ser revertida, desde que a limitação seja registrada por escrito e a proibição passe a ser garantida por teste automatizado, conforme `ledger-module-boundary`.

As implementações de repositório e seus mappers SHALL residir no `impl` da feature dona, consumindo os DAOs de `:core:database`; as implementações de repositório do razão SHALL residir em `:core:ledger`. Nenhuma entity Room SHALL aparecer em assinatura de `api` de feature nem na superfície pública de `:core:ledger`.

#### Scenario: Feature acessa persistência
- **WHEN** o `impl` de uma feature implementa um repositório declarado na sua `api`
- **THEN** a implementação consome DAOs de `:core:database` e nenhuma entity Room aparece em assinaturas da `api`

#### Scenario: Direção da dependência do razão
- **WHEN** o grafo de módulos é inspecionado
- **THEN** `:core:database` depende de `:core:ledger`, e `:core:ledger` não depende de `:core:database`

#### Scenario: Consulta do razão não compila contra fachada
- **WHEN** a direção preferencial está em vigor e uma consulta em `:core:ledger` tenta referenciar uma entity de fachada
- **THEN** a compilação falha, por o tipo não estar visível no módulo

#### Scenario: Direção revertida mantém a proibição garantida
- **WHEN** a direção é revertida por limitação da ferramenta de persistência
- **THEN** a limitação está registrada por escrito e um teste automatizado falha ao encontrar consulta do razão referenciando tabela de fachada

### Requirement: Regras de dependência entre módulos
As dependências entre módulos SHALL obedecer: (1) `api` não depende de `api` de outra feature; (2) `impl` não depende de `impl` de outra feature; (3) `api` não depende de nenhum `impl`; (4) `impl` pode depender de qualquer `api` e de módulos `:core:*`; módulos `api` só podem depender de `:core:*`. O `:app:shared` é o único módulo autorizado a depender de módulos `impl`.

Nenhum módulo SHALL depender da `api` de outra feature para ler ou escrever no razão: esse acesso SHALL se dar por `:core:ledger`, que é `:core:*` e portanto acessível também às `api`.

#### Scenario: Dependência cruzada entre impls de features distintas
- **WHEN** `transactions:impl` precisa de comportamento de creditcards e `creditcards:impl` precisa de comportamento de transactions
- **THEN** cada `impl` depende apenas da `api` da outra feature, e o grafo de módulos permanece sem ciclos

#### Scenario: Violação de regra de dependência
- **WHEN** um módulo declara uma dependência proibida (api→api, impl→impl ou api→impl)
- **THEN** o build falha na verificação de regras antes da compilação ser considerada válida

#### Scenario: Acesso ao razão sem passar por feature
- **WHEN** uma feature qualquer precisa de saldo, total ou escrita no razão
- **THEN** ela depende de `:core:ledger`, e não de `feature:transactions:api`

#### Scenario: Caso de uso em api consome o razão
- **WHEN** um caso de uso declarado na `api` de uma feature precisa de uma figura derivada do razão
- **THEN** ele depende de `:core:ledger` diretamente, sem receber o valor já calculado pelo `impl`
