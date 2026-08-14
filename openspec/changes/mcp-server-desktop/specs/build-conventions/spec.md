## MODIFIED Requirements

### Requirement: Verificação mecânica das regras de dependência
As convenções `feature.api` e `feature.impl` SHALL verificar as regras de dependência do module-architecture durante o build: `feature.api` só admite dependências de projeto `:core:*`; `feature.impl` admite `:core:*` e `:feature:*:api`. Violações SHALL falhar o build com mensagem indicando o módulo e a dependência proibida.

A convenção do `:app:mcp` SHALL aplicar **a mesma verificação que a `feature.impl` aplica** — `:core:*` e `:feature:*:api`, recusando qualquer `feature:*:impl`. A regra que o servidor precisa obedecer é, item por item, a regra 4 já escrita; reusá-la é o que impede que o `:app:mcp` ganhe uma verificação própria, que divergiria da original no primeiro ajuste.

A garantia de que o servidor não contorna a `api` tem de ser do build: o `:app:mcp` é o primeiro módulo fora do padrão api/impl com direitos de consumo de domínio, e uma regra que só existe por disciplina é uma regra que a próxima dependência conveniente derruba.

Nenhuma verificação SHALL restringir o conjunto de módulos de que o `:app:shared` depende. O shell é o agregador: ele já enxerga `impl` por definição, e passa a depender também do `:app:mcp`. Uma verificação escopada a ele só poderia enumerar exceções — e uma regra cuja lista de exceções é a própria coisa que ela descreve não verifica nada.

#### Scenario: api declara dependência de outra api
- **WHEN** `:feature:transactions:api` declara dependência de `:feature:accounts:api`
- **THEN** o build falha indicando a regra violada (api não depende de api)

#### Scenario: impl declara dependência de outro impl
- **WHEN** `:feature:dashboard:impl` declara dependência de `:feature:creditcards:impl`
- **THEN** o build falha indicando a regra violada (impl não depende de impl)

#### Scenario: servidor MCP declara dependência de impl
- **WHEN** o `:app:mcp` declara dependência de um `feature:*:impl`
- **THEN** o build falha indicando a regra violada, apontando que o comportamento precisa ser promovido para a `api`

#### Scenario: o shell depende do servidor
- **WHEN** o `:app:shared` declara dependência do `:app:mcp` para agregar o módulo Koin dele
- **THEN** o build passa, sem que nenhuma exceção precise ser inscrita numa lista
