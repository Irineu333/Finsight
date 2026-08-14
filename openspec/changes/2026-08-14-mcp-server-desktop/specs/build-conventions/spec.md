## MODIFIED Requirements

### Requirement: Verificação mecânica das regras de dependência
As convenções `feature.api` e `feature.impl` SHALL verificar as regras de dependência do module-architecture durante o build: `feature.api` só admite dependências de projeto `:core:*`; `feature.impl` admite `:core:*` e `:feature:*:api`. Violações SHALL falhar o build com mensagem indicando o módulo e a dependência proibida.

A mesma verificação SHALL cobrir o `:app:mcp`, que admite `:core:*` e `:feature:*:api` e MUST NOT alcançar nenhum `feature:*:impl`. A garantia de que o servidor não contorna a `api` tem de ser do build: o `:app:mcp` é o primeiro módulo fora do padrão api/impl com direitos de consumo de domínio, e uma regra que só existe por disciplina é uma regra que a próxima dependência conveniente derruba.

#### Scenario: api declara dependência de outra api
- **WHEN** `:feature:transactions:api` declara dependência de `:feature:accounts:api`
- **THEN** o build falha indicando a regra violada (api não depende de api)

#### Scenario: impl declara dependência de outro impl
- **WHEN** `:feature:dashboard:impl` declara dependência de `:feature:creditcards:impl`
- **THEN** o build falha indicando a regra violada (impl não depende de impl)

#### Scenario: servidor MCP declara dependência de impl
- **WHEN** o `:app:mcp` declara dependência de um `feature:*:impl`
- **THEN** o build falha indicando a regra violada, apontando que o comportamento precisa ser promovido para a `api`
