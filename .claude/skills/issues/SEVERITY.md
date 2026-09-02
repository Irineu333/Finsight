# Régua de criticidade

Ancorada em **consequência**, não em impressão. O app é um razão de partidas dobradas:
número errado e persistido é o pior dano que ele sabe causar, e a régua começa por aí.

```
CRITICAL  o dado gravado fica errado ou irrecuperável: uma figura do razão deixa de
          bater, ou o app entra num estado do qual não se sai sem editar o banco

HIGH      o app cai, ou uma tarefa central fica impossível e não há contorno

MEDIUM    a tarefa é possível, mas o app engana — mostra número, rótulo ou lista que
          não corresponde ao que gravou — ou só funciona por um contorno que ninguém
          adivinha

LOW       incomoda sem enganar e sem impedir
```

## Alcance, como modificador

Um degrau, no máximo, e só um:

- o cenário exige uma configuração rara → **desce** um degrau
- atinge todo mundo em uso normal → **sobe** um degrau

Duas dimensões dariam uma matriz que ninguém consulta. A faixa continua sendo decidida
pela consequência; o alcance apenas a ajusta.

## Como decidir, em ordem

1. O dado gravado fica errado, ou o estado é irrecuperável sem tocar o banco? → `critical`
2. Cai, ou impede uma tarefa central sem contorno? → `high`
3. Engana, ou exige contorno não óbvio? → `medium`
4. Nenhum dos anteriores? → `low`
5. Aplique o modificador de alcance, se couber.

"Impede a tarefa" vale para **quem quer que use o app** — inclusive por leitor de tela.
Um botão sem `contentDescription` não é um incômodo para quem depende dele: é uma tarefa
impossível.

## Calibragem

Casos reais do projeto, com a faixa que a régua produz e o motivo:

| caso | faixa | por quê |
|---|---|---|
| editar os dias do cartão deixa a fatura impagável | `critical` | estado do qual não se sai |
| exceção escapa do `Either` e derruba o app ao excluir fatura | `high` | cai |
| pagamento de fatura contado como despesa no relatório | `medium` | engana sem impedir |
| chaves presentes em `values/` e ausentes em `values-en/` | `low` | não engana, não impede |
| `contentDescription = null` em botões de navegação | `high` | tarefa impossível por leitor de tela |
| seletor de ícones sem scroll, ícones do fim inalcançáveis | `medium` | há contorno; não é tarefa central |

As duas últimas linhas são o motivo de a régua existir. Classificadas por impressão,
elas saem invertidas — o que se enxerga na tela parece grave, e o que só afeta quem não
enxerga parece pequeno.
