# Template do arquivo de bug

Nome do arquivo em inglês, kebab-case. Conteúdo em português. O frontmatter é
identificador — chaves e valores em inglês, sem acento, para `grep` limpo.

## Frontmatter

```yaml
---
area: creditcards      # a área afetada: creditcards, transactions, budgets, ledger,
                       # accounts, recurring, report, dashboard, support, categories,
                       # settings, designsystem, resources, common, app, transversal
severity: high         # critical | high | medium | low   → ver SEVERITY.md
type: data             # data | crash | ux | performance | a11y | i18n | concurrency | navigation
confirmed: no          # opcional, só quando o código não confirmou o relato
verdict: fixed         # só no archive: fixed | refuted | incidental | obsolete
---
```

## Forma 1 — Cenário

Para o defeito **observável por quem usa o app**: dado errado, crash, UI divergente,
fluxo travado.

```markdown
---
area: creditcards
severity: high
type: data
---

# Editar closingDay/dueDay inverte a ordem entre fechamento e vencimento

## Cenário

**DADO** um cartão com `closingDay=5` e `dueDay=10`, cuja fatura de agosto fecha em
05/08 e vence em 10/08
**QUANDO** o usuário edita o cartão para `dueDay=1`
**ENTÃO** a fatura passa a vencer em 01/08 — antes de fechar
**DEVERIA** recusar a edição, ou preservar as datas das faturas já abertas

## Mecânica

As datas da fatura são derivadas em leitura do cartão (`Invoice.closingDate`,
`Invoice.dueDate`), mas `dueMonth` foi congelado na criação conforme a regra
`dueDay < closingDay` (`OpenInvoiceUseCase`). Editar os dias reescreve retroativamente
as datas de todo o histórico, e nenhuma camada valida a relação — `CreditCard.init` e
`CreditCardForm.build()` checam apenas a faixa `1..31`.

## Evidência

- `UpdateCreditCardUseCase.execute()` — grava a alteração sem validar a relação
- `CreditCardForm.build()` — valida só a faixa
- `Invoice.closingDate` / `Invoice.dueDate` — derivadas em leitura, não persistidas

## Consequência

A fatura fica impagável: `paidAt >= closingDate` e `paidAt <= dueDate` viram
contraditórios. Abrir o modal de pagamento lança durante a composição.

*Hipótese, não verificada: `GetOrCreateInvoiceForMonthUseCaseImpl` busca por `dueMonth`
e pode passar a criar faturas duplicadas onde já existia uma.*

## Sugestão

Validar a relação em `CreditCard.init`, com erro próprio, e traduzir no formulário.
Não vinculante — quem corrige decide.
```

*(exemplo ilustrativo; as âncoras precisam ser reverificadas antes de virar um arquivo real)*

## Forma 2 — Invariante

Para o defeito que **não é observável por quem usa o app**: performance, acessibilidade
estrutural, i18n, ou um padrão que se repete em N lugares do código.

Troca-se a seção `## Cenário` por `## Invariante`, escrito como uma afirmação que
**hoje é falsa**, e a `## Evidência` lista onde ela falha:

```markdown
## Invariante

Toda agregação que percorre N contas ou N transações roda **fora da main thread**.

Hoje é falso: não há uma única chamada de `flowOn` no projeto, e todo o trabalho de
agregação acontece no dispatcher do coletor.
```

O fechamento é o mesmo do `DEVERIA`: o bug fecha quando o invariante passa a valer.

**Um bug de classe é um invariante só, com N ocorrências.** As ocorrências vivem na
`## Evidência` — não se abre um arquivo por lugar. A unidade é o comportamento, não o local.

## Desfecho — acrescentado no arquivamento

O arquivo não perde nada: a seção é **acrescentada** ao final, e o frontmatter ganha
`verdict`.

```markdown
## Desfecho

**Causa real** — o formulário não era o lugar: `CreditCard.init` já valida a faixa
`1..31` e era ali que faltava a relação. (A sugestão original apontava para o form.)

**Mudança** — `CreditCard.init` passa a exigir a relação; `CreditCardForm.build()`
traduz a exceção em erro de formulário.

**Prova** — `CreditCardInvariantTest` cobre a inversão: vermelho antes, verde depois.

**Commit** — `Fix(CreditCard): keep the due date from landing before the closing date`
```

Por veredito, o que a seção exige:

| veredito | o que escrever |
|---|---|
| `fixed` | causa real, mudança, prova, commit |
| `refuted` | o que a premissa errou e o que o código faz de fato, com âncora |
| `incidental` | onde sumiu (`git log -S`) e se há prova impedindo a volta — normalmente **não há** |
| `obsolete` | o que substituiu o código que continha o defeito |

## A pergunta-dona de cada seção

Um fato responde a uma pergunta só. Se um parágrafo não responde à pergunta da sua
seção, ele está no lugar errado — ou não deveria existir.

| seção | responde |
|---|---|
| `## Cenário` / `## Invariante` | **o que se vê** — e o que deveria valer |
| `## Mecânica` | **por que acontece** — a regra que deveria valer e onde o código a viola |
| `## Evidência` | **onde olhar** — lista de âncoras, não prosa |
| `## Consequência` | **o que se perde** |
| `## Sugestão` | **por onde começar** — não vinculante |
| `## Desfecho` | **o que de fato foi feito** — só no archive |

Não existe seção de investigação. Narrar o caminho ("procurei em X, não achei, fui em Y")
não muda a correção nem a verificação, e é o que mais engorda um arquivo escrito sem
limite de verbosidade.
