## ADDED Requirements

### Requirement: Servidor MCP como módulo de app sem UI

O servidor MCP SHALL residir em `:app:mcp`, um módulo de app **sem interface**: sem tela, sem
rota, sem entry point de UI. Ele SHALL depender apenas de `feature:*:api` e de `:core:*` — os
mesmos direitos de um `impl` —, expor o seu módulo Koin e ser agregado pelo `:app:shared`
junto dos demais.

O `:app:mcp` MUST NOT depender de nenhum `feature:*:impl`. O `:app:shared` permanece o único
módulo do projeto com esse direito, e a topologia estrela permanece intacta: o servidor é um
consumidor de `api` como qualquer outro.

O `:app:desktop` SHALL apenas iniciar e encerrar o servidor junto do processo, sem conter
nenhuma lógica sua — a mesma restrição que já vale para módulos de plataforma.

#### Scenario: Servidor alcança o domínio pelas apis
- **WHEN** uma tool precisa de um comportamento que hoje vive num `impl`
- **THEN** o comportamento é promovido para a `api` da feature, e `:app:mcp` continua sem
  depender de `impl` algum

#### Scenario: Nova tool não move o shell
- **WHEN** uma tool é acrescentada ao servidor
- **THEN** o `:app:shared` não muda, porque `mcpModule` já está agregado

#### Scenario: Plataforma apenas hospeda
- **WHEN** o `:app:desktop` é inspecionado
- **THEN** ele contém somente o bootstrap que sobe e derruba o servidor, sem tools, sem
  transporte e sem regra

## MODIFIED Requirements

### Requirement: Regras de dependência entre módulos
As dependências entre módulos SHALL obedecer: (1) `api` não depende de `api` de outra feature; (2) `impl` não depende de `impl` de outra feature; (3) `api` não depende de nenhum `impl`; (4) `impl` pode depender de qualquer `api` e de módulos `:core:*`; módulos `api` só podem depender de `:core:*`. O `:app:shared` é o único módulo autorizado a depender de módulos `impl`.

O `:app:mcp` SHALL obedecer às mesmas regras de um `impl` — qualquer `api` mais `:core:*` — sem receber direito algum além disso. Um consumidor novo do domínio não justifica uma casta nova de dependência: o que ele precisa alcançar é promovido para a `api`, aplicando o critério de triagem que já vigora ("só entra na `api` o que outro módulo consome"), e a promoção é revisada como contrato público — assinatura, tipo de erro, e nenhum tipo de apresentação na fronteira.

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

#### Scenario: Consumidor não-feature não ganha privilégio
- **WHEN** o `:app:mcp` precisa de um caso de uso que hoje vive no `impl`
- **THEN** o caso de uso é promovido para a `api`, e o `:app:mcp` não passa a enxergar `impl` algum
