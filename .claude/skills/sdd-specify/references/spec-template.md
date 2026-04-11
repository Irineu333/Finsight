# Spec: [Nome da Feature]

> A spec descreve *o que* o sistema deve fazer. Não inclui como implementar.
> Código só é válido aqui quando define padrões arquiteturais obrigatórios.

---

## Contexto

O que já existe no sistema que é relevante para essa feature:
- Quais dados, fluxos ou entidades já existem
- Quais limitações ou dependências impactam essa feature
- O que o usuário já consegue fazer hoje nessa área

---

## Objetivo

Uma frase: o problema que essa feature resolve ou o que ela habilita.

---

## Comportamentos

Descreva cada comportamento como um cenário. Cubra o caminho principal, casos de borda e erros esperados.

```
Dado [estado ou contexto inicial]
Quando [ação do usuário ou evento do sistema]
Então [resultado esperado]
     [e resultado esperado]
     [mas NÃO resultado indesejado]
```

### Caminho principal

**[Nome do cenário]**
```
Dado ...
Quando ...
Então ...
```

### Casos de borda

**[Nome do cenário]**
```
Dado ...
Quando ...
Então ...
```

### Erros esperados

**[Nome do cenário]**
```
Dado ...
Quando ...
Então ...
     e a mensagem de erro exibida é "[descrição do erro]"
```

---

## Regras de negócio

Liste as regras que governam os comportamentos acima. Sejam explícitas sobre condições, limites e validações.

- Regra 1
- Regra 2

---

## Padrões obrigatórios

Padrões arquiteturais que esta feature deve seguir, com justificativa. Inclua apenas o que não está já coberto pelo CLAUDE.md ou convenções gerais do projeto.

- **[Padrão]:** [por que é obrigatório aqui]

---

## Fora do escopo

O que explicitamente não será feito nessa feature. Previne extrapolação do agente e deixa claro o que fica para depois.

- Não inclui X
- Não inclui Y

---

## Critério de aceite

Como validar que a feature está correta e completa.

### Validação manual

Passo a passo para testar os comportamentos principais:

1. ...
2. ...
3. ...

### Revisão de código

O que verificar ao revisar a implementação:

- [ ] ...
- [ ] ...