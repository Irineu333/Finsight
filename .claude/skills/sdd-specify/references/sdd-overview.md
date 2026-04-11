# Spec-Driven Development (SDD)

## O que é

Spec-Driven Development é um fluxo de desenvolvimento onde a especificação do comportamento esperado é escrita e validada *antes* da implementação. A spec é a fonte de verdade do que deve ser construído — não o código, não o histórico de commits.

O objetivo não é documentar o que foi feito, mas definir o que deve ser feito de forma clara o suficiente para que a implementação possa ser validada objetivamente.

---

## Por que usar

Desenvolvimento assistido por IA produz código rápido, mas sem uma spec clara o agente preenche lacunas com suposições. Essas suposições se acumulam silenciosamente como **débito comportamental**: o código compila, mas o comportamento está errado ou incompleto.

A spec cria um contrato explícito entre intenção e implementação, tornando cada etapa verificável.

---

## Estrutura de arquivos

```
docs/
  references/        # documentação de bibliotecas de terceiros (compartilhada)
  features/
    {feature}/
      spec.md        # o que o sistema deve fazer (decisões de produto)
      plan.md        # como entregar (decisões de engenharia)
      steps/         # etapas de implementação (decisões de engenharia)
        01-nome.md
        02-nome.md
      issues/        # bugs e melhorias descobertas durante a implementação
        01-descricao.md
```

---

## O fluxo

```
1. Identificar dependências externas e consultar docs/references/
2. Escrever spec.md
3. Revisar e ajustar a spec (remover código desnecessário, garantir clareza)
4. Atualizar docs/references/ se novos padrões de uso foram definidos
5. Escrever plan.md dividindo em etapas verificáveis
6. Implementar uma etapa por vez
7. Validar manualmente + revisar código contra o critério de aceite
8. Registrar desvios no step e no plan.md
9. Registrar issues descobertas em issues/
10. Repetir a partir do passo 6
```

A spec raramente muda durante a implementação. O plano pode e deve ser ajustado conforme a implementação avança.

---

## Separação de responsabilidades

| Spec | Plano |
|---|---|
| Comportamentos esperados | Divisão em etapas |
| Regras de negócio | Arquivos afetados |
| Padrões arquiteturais obrigatórios | Decisões técnicas de implementação |
| O que está fora do escopo | Riscos técnicos conhecidos |

**Regra prática:** se mudar durante a implementação quebra a spec, é decisão de produto — vai na spec. Se é uma escolha técnica que pode variar, vai no plano.

---

## O que não pertence à spec

- Implementações concretas de baixo nível
- Lógica interna de funções
- Queries, estruturas de dados internas, detalhes de persistência
- Qualquer coisa que possa mudar por dificuldade técnica sem alterar o comportamento observável

Código na spec deve ser restrito a padrões e convenções que precisam de consistência entre etapas. Código de implementação na spec gessa a solução e vira documentação morta quando a implementação toma outro caminho.

---

## Débito comportamental vs. débito técnico

**Débito técnico** é visível: código difícil de manter, abstrações ruins, falta de testes.

**Débito comportamental** é silencioso: o sistema faz algo diferente do que deveria, mas não há erro explícito. Entra quando:
- O agente extrapola além do que foi pedido
- Um caso de borda não estava na spec e foi ignorado
- A validação manual não cobriu todos os cenários

A spec é o principal mecanismo de prevenção de débito comportamental.

---

## Referências externas

`docs/references/` contém documentação de bibliotecas de terceiros adaptada ao contexto do projeto. Serve como contexto pré-carregado para a IA — evita buscas repetidas na internet e previne usos incorretos.

- Durante a spec, identifique quais referências são relevantes para a feature e liste-as no `plan.md`. 
- Pesquise a documentação oficial e resuma os padrões de uso relevantes para o projeto.
- Se novos padrões de uso forem definidos durante a implementação, atualize a referência correspondente.

---

## Usando IA para gerar specs

É válido usar IA para gerar o rascunho da spec. Ao revisar, remova:

- Código de baixo nível que pode variar durante a implementação
- Soluções técnicas específicas onde só o comportamento importa
- Detalhes de implementação que pertencem ao plano

Mantenha:
- Comportamentos descritos em Dado/Quando/Então
- Regras de negócio explícitas
- Padrões arquiteturais obrigatórios com justificativa
- O que está explicitamente fora do escopo

---

## Passando contexto ao agente por etapa

Em cada etapa de implementação, forneça:

- Spec completa (o agente precisa da intenção total)
- Plano completo (o agente precisa saber o que vem depois)
- Arquivo da etapa atual ("implemente apenas a etapa N")
- Referências relevantes listadas no plano
- Desvios registrados de etapas anteriores

Sem visão do todo, o agente toma decisões localmente corretas que dificultam etapas futuras.