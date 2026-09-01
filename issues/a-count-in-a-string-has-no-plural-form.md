---
area: resources
severity: low
type: i18n
---

# Uma contagem numa string não tem forma plural

## Invariante

Uma string que interpola uma contagem concorda com ela.

Hoje é falso: não há uma única forma plural declarada nos dois arquivos de string — todas as
chaves com `%1$d` escrevem o plural fixo, ou fogem dele com um `(s)`. Com contagem 1 o app
diz "1 issues no total".

## Mecânica

O recurso é uma `<string>` com o plural embutido no texto, e não há como o valor da contagem
mudar a palavra. Onde alguém percebeu o problema, a saída foi um parêntese —
`"%1$d taxa(s) de câmbio"` —, que é o mesmo defeito escrito de forma consciente.

## Evidência

- `core/resources/.../values/strings.xml` — `support_overview_total` = `"%1$d issues no total"`
- mesmo arquivo — `budgets_category_plural` = `"%1$d categorias"`,
  `delete_installment_message` (`"todas as %1$d parcelas"`)
- mesmo arquivo — `currencies_delete_confirm_message_rates`, com o contorno `"taxa(s)"`
- `core/resources/.../values-en/strings.xml` — as mesmas chaves, mesma forma
- `grep -c "<plurals" core/resources/.../values*/strings.xml` — zero nos dois

## Consequência

Texto errado em toda contagem de 1, nas duas línguas — e em inglês a concordância é ainda mais
visível que em português.

## Sugestão

Compose Resources tem `<plurals>` e `pluralStringResource`. Converter as chaves que
interpolam contagem; começar pelas que exibem 1 com frequência real. Não vinculante.
