# recurring-archive Specification

## Purpose

O destino que mantém as recorrências arquivadas alcançáveis e reversíveis depois que elas deixam a lista mensal. Uma recorrência arquivada não gera ciclo em mês algum, logo não tem estado de ciclo e não cabe em seção alguma da lista (`recurring-cycle-status`) — ela vive num destino próprio, sem mês, sem resumo e sem seções, e é lá que desarquivar continua sendo oferecido. Com ela fora do caminho, o seletor de recorte da lista mensal volta a ter um eixo só: o da natureza.

## Requirements

### Requirement: Uma recorrência arquivada permanece alcançável fora da lista mensal

Uma recorrência arquivada SHALL permanecer alcançável por um destino próprio na feature, mesmo
não pertencendo a nenhuma seção da lista mensal — ela não gera ciclo em mês algum, portanto não
tem estado de ciclo.

O destino é obrigatório, não uma conveniência: arquivar é oferecido ao usuário como
**reversível**, e sem ele a recorrência arquivada deixaria de ter qualquer caminho de volta.

O destino SHALL oferecer desarquivar, pelo mesmo caminho que a tela oferecia antes.

#### Scenario: Recorrência arquivada some da lista mensal
- **WHEN** o usuário arquiva uma recorrência
- **THEN** ela deixa de aparecer em qualquer seção da lista, em qualquer mês

#### Scenario: A arquivada continua reversível
- **WHEN** o usuário abre o destino de arquivadas e escolhe uma
- **THEN** desarquivar é oferecido, e a recorrência volta a gerar ciclos

### Requirement: O arquivo não tem mês nem estado de ciclo

O destino de arquivadas SHALL exibir uma lista simples, sem resumo de mês, sem seletor de mês e
sem seções por estado de ciclo.

Nada disso teria sentido ali: uma arquivada não gera ciclo, logo não tem estado, logo não há mês
que a recorte nem resumo que a some.

A lista MUST NOT afirmar em cada linha que a recorrência está arquivada. Ali todas estão, então
a marca não distingue linha alguma da vizinha — que é o critério pelo qual a linha decide o que
afirmar.

#### Scenario: O arquivo não é recortado por mês
- **WHEN** o usuário abre o destino de arquivadas
- **THEN** não há seletor de mês, resumo ou seção de estado, e todas as recorrências arquivadas são listadas

#### Scenario: A marca de arquivada não se repete
- **WHEN** a lista de arquivadas é exibida
- **THEN** nenhuma linha carrega marca individual de "arquivada"

### Requirement: O recorte da lista mensal tem um eixo só

O seletor de recorte da lista mensal SHALL oferecer apenas o eixo da **natureza** — todas,
despesas, receitas — e MUST NOT oferecer um recorte de arquivadas.

Com as arquivadas em destino próprio, o seletor deixa de misturar dois eixos de propósito
diferentes: recortar a lista e trocar o que a tela é. Um mesmo controle que ora estreita o
conteúdo, ora remodela a tela, não deixa o usuário saber qual das duas coisas ele acabou de
fazer.

O recorte por natureza SHALL ser transversal às seções, estreitando cada uma sem alterar a
organização por estado.

#### Scenario: Recorte por natureza
- **WHEN** o usuário recorta a lista por despesas
- **THEN** cada seção passa a exibir apenas ciclos de despesa, e a organização por estado permanece

#### Scenario: Não há recorte de arquivadas
- **WHEN** o usuário abre o seletor de recorte
- **THEN** as opções são todas, despesas e receitas, e arquivadas não é uma delas
