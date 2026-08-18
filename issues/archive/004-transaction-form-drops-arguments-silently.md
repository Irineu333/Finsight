# 004 — `TransactionForm.from` descarta categoria incompatível e `installments` fora do cartão, e a tool responde "Recorded."

**Área:** mcp / model · **Tipo:** correção · **Criticidade:** média · **Status:** corrigida em 2026-08-18
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`TransactionForm.from` normaliza o formulário *descartando* o que não encaixa. Isso está certo para
uma tela — o seletor nunca ofereceu a combinação incompatível, e a normalização é uma rede de
segurança — mas a tool passa argumentos que o agente declarou explicitamente, e um argumento
descartado volta como sucesso.

## Evidência

`core/model/.../domain/model/form/TransactionForm.kt:80-84`

```kotlin
val target = target.takeIf { type.isExpense } ?: TransactionTarget.ACCOUNT
val category = category?.takeIf { it.type.isAccept(type) }
val creditCard = creditCard?.takeIf { target.isCreditCard }
val account = account?.takeIf { target.isAccount }
val installments = installments.takeIf { target.isCreditCard } ?: 1
```

- `core/model/.../extension/Category.kt:8-13` — `isAccept` casa `EXPENSE`↔despesa e `INCOME`↔receita,
  então uma categoria de despesa numa receita é descartada.
- `feature/mcp/impl/.../tool/TransactionWriteTools.kt:158-172` — a tool resolve `category_id` e
  `installments` da chamada e os entrega direto ao `from`.
- `TransactionWriteTools.kt:214-215` — a resposta é `"Recorded."`

Dois dos três casos são silenciosos; o terceiro não é:

| Chamada | O que acontece |
|---|---|
| `type: "income"` + `category_id` de uma categoria `EXPENSE` | categoria descartada → uma receita **sem categoria**, sem recusa |
| `account_id` + `installments: 6` | forçado a 1 → um único lançamento pelo valor total, sem recusa |
| `type: "income"` + `card_id` | `target` forçado a `ACCOUNT`, `account` fica null → recusado por `ValidateTransactionFormUseCaseImpl.kt:51` (`AccountRequired`) — mensagem confusa, mas não silenciosa |

## Cenário de falha

`create_transaction(type: "income", amount: 1200, account_id: 1, category_id: 9)`, em que a
categoria 9 se chama `Salário`… mas foi criada como categoria `EXPENSE`. A receita é registrada sem
categoria, a resposta diz `"Recorded."`, e o agente relata a classificação que pediu. O usuário
encontra o lançamento em "Sem categoria" depois, sem nada que explique por quê.

## Correção sugerida

Recusar na tool, antes de montar o formulário — a tool é a única camada que sabe que o argumento foi
*declarado* e não assumido por default:

- `category_id` cujo `Category.type` não `isAccept` o `type` do lançamento, nomeando os dois;
- `installments > 1` junto de `account_id` (parcelamento é uma affordance de cartão).

Não mexer em `TransactionForm.from`: sua normalização está correta para as telas, que é a razão de
ela ser escrita assim.
## Correção aplicada

Recusa na tool, antes de o formulário ser montado, como esta issue pedia. `TransactionForm.from`
não foi tocado: normalizar descartando está certo para as telas, e é por isso que ele é escrito
assim.

Os três casos, com a razão que cada um devolve:

| Chamada | Razão |
|---|---|
| `type: "income"` + `card_id` | ``A card takes expenses only: with `type` income, give `account_id` and not `card_id`.`` |
| `installments > 1` + `account_id` | ``Instalments are a card affordance: give `installments` above 1 with a `card_id`, not with an `account_id`.`` |
| `category_id` de direção incompatível | ``` `category_id` names "Mercado", an expense category, and `type` is income: a category classifies one direction only. Give an income category, or leave `category_id` out. ``` |

O terceiro caso desta issue — receita num cartão — não era silencioso, era pior de outra forma: o
formulário descartava o cartão, `account` ficava nulo e a recusa que chegava era *"Pick the account."*,
apontando um argumento que a chamada nunca deu. Agora a recusa nomeia o erro real.

Três testes em `RegistrationFamilyOverTheProtocolTest`, pela rede. Conferido que mordem: com o
arquivo de produção revertido, os três falham e mais nenhum.

## O que esta correção descobriu, e não corrigiu

`update_transaction` tem **dois dos três buracos**, no caminho da edição:

- **Categoria incompatível, silenciosa.** `UpdateTransactionTool.call` resolve `category_id` e o
  entrega direto ao `from`, que o descarta; nada a jusante reconfere, e a resposta diz *"Edited.
  Everything the call did not name kept the value it had."* — falso exatamente nesse caso. Há uma
  variante que `create_transaction` não pode ter: virar só o `type` para receita faz a categoria
  **já armazenada** ser descartada, também em silêncio.
- **Receita num cartão, com a recusa errada.** `card` resolve não-nulo, `account` fica nulo, e volta
  a mesma recusa pedindo a conta que ninguém nomeou.

`installments` não se aplica: `update_transaction` não tem esse parâmetro.

Registrado como [016](../016-update-transaction-drops-the-category-silently.md).
