---
area: recurring
severity: high
type: data
verdict: fixed
---

# `confirm_recurring` grava no razão um lançamento na direção errada

**Verificado em:** 2026-08-19, `feature/local-mcp-server`, por uma revisão adversarial em quatro
lentes do commit `e58abf948`

## O que está errado

A regra "uma categoria classifica uma direção só" foi fechada nas cinco tools que montam um
formulário — [004](2026-08-18-transaction-form-drops-arguments-silently.md),
[016](2026-08-18-update-transaction-drops-the-category-silently.md),
[020](2026-08-19-create-installment-drops-the-category-silently.md),
[021](2026-08-19-update-recurring-stores-an-incoherent-template.md). `confirm_recurring` não monta
formulário nenhum, e é a **única escrita da superfície que chega ao razão sem um** — foi o recorte
que a escondeu de quatro rodadas de correção.

`feature/mcp/impl/.../tool/RecurringOperationTools.kt` não contém uma única ocorrência de
`isAccept`: a categoria é resolvida por identidade (`:133-138`) e entregue ao use case (`:158`).

## Evidência

A cadeia, inteira:

| Passo | Arquivo | O que faz |
|---|---|---|
| 1 | `RecurringOperationTools.kt:133-138` | resolve `category_id` só por identidade |
| 2 | `ConfirmRecurringUseCaseImpl.kt:121,145` | `contra = contraLegFor(recurring.type, category)` |
| 3 | `core/model/.../extension/Category.kt:39-43` | a natureza da contra-perna vem **da categoria**: `ContraLeg(category?.type?.accountType ?: …)` |
| 4 | `core/ledger/.../LedgerEntryWriter.kt` | `Σ = 0` fecha, e `DimensionKind.CATEGORY.landsOn` aceita `{INCOME, EXPENSE}` — nada refuta |
| 5 | `core/ledger/.../extension/Ledger.kt:56-64` | `deriveTransactionLabel` vê `INCOME in types` e rotula **receita** |

Com um template de despesa e uma categoria de receita, as pernas ficam `{ASSET −, INCOME +}`: o
dinheiro **sai** da conta e o lançamento é lido como **receita**.

Medido sobre o servidor real, com o razão real:

```
confirm_recurring {"id":1,"date":"2026-03-10","category_id":99}
isError = false
{"transaction":{"nature":"income","title":"Netflix","account":"Nubank",
  "category":"Salário","category_id":99,…},
 "note":"Confirmed for 2026-03. The template is unchanged…"}
entries: Nubank/ASSET = -3990 (dim=null), Receitas/INCOME = +3990 (dim=999)
label = INCOME
```

Nenhum dado legado é necessário: funciona numa instalação limpa, hoje.

## Por que ALTA

É o critério da faixa, literalmente: grava no razão algo diferente do que foi pedido e responde que
deu certo. O saldo da conta cai, o mês reporta uma receita que não houve, a categoria de receita
acumula um valor que saiu do bolso, e nada na resposta permite notar. É a primeira ALTA desde a
[001](2026-08-18-create-transaction-accepts-negative-amount.md).

## O que torna o achado mais grave do que parece

`ConfirmRecurringUseCaseImpl.kt:75-76` diz de si mesmo:

> *"The one write of the app that reaches the ledger without a form to hold the rule, so the rule is
> held here"*

A regra de valor positivo está lá, pela [001](2026-08-18-create-transaction-accepts-negative-amount.md).
A de direção não. O comentário descreve a responsabilidade correta e o código a cumpre pela metade.

## Correção sugerida

Duas camadas, como nas outras cinco:

- **O dono:** `ConfirmRecurringUseCase` recusa a combinação, com um `RecurringError` próprio. É onde
  o comentário acima já promete que a regra mora, e é o que protege a sheet de confirmação junto com
  a tool. **Não** em `contraLegFor`: ele é uma derivação pura, e filtrar ali seria voltar ao descarte
  silencioso que esta família inteira recusa.
- **A tool:** a recusa que nomeia os argumentos, como as outras cinco fazem, para o agente saber o
  que mudar.

O teste tem de olhar as **entradas do razão**, não a recusa: uma asserção sobre o resultado passa
enquanto as pernas estiverem invertidas, porque `Σ = 0` continua valendo.

## Relacionado

A sheet de confirmação re-oferece a categoria incoerente:
`ConfirmRecurringViewModel.offeredCategories` (`:263-274`) devolve a seleção à lista quando ela não
está entre as filtradas — a KDoc explica isso como continuidade para uma categoria **arquivada**, e o
ramo não distingue os dois casos. `OfferedCategoriesTest.kt:57-73` cobre a arquivada-mas-coerente e
não cobre a incoerente.

## Desfecho

Corrigida em 2026-08-19, nas duas camadas que a issue prescreve, com os testes escritos antes e
verificados vermelhos: o de domínio não compilava (faltava o erro), e os dois sobre o protocolo
falhavam contra o razão real com `"nature":"income"` num template de despesa.

- **O dono.** `RecurringError.CATEGORY_DIRECTION_MISMATCH` (`core/model` — `domain/error/`), com a
  chave nova em `values/strings.xml` e `values-en/strings.xml`. A recusa está em
  `ConfirmRecurringUseCaseImpl.kt:86-88`, imediatamente depois da regra do valor positivo e **antes**
  da resolução da fatura — a mesma posição, pela mesma razão (design D7): recusar depois deixaria
  para trás uma fatura de um ciclo que não postou. `contraLegFor` não foi tocado.
- **A tool.** `ConfirmRecurringTool` (`RecurringOperationTools.kt:138-181`) separa a categoria
  declarada da carregada do template e recusa cada uma com o texto que o caso admite. A descrição e
  o schema de `category_id` passaram a dizer a regra.

Testes: `ConfirmRecurringCoherenceTest` (7 casos, sobre as pernas do intent — três deles de
não-regressão: categoria coerente nos dois sentidos e ausência de categoria continuam postando na
natureza certa) e três casos novos na família de operações, sobre o razão real. Suíte inteira:
1698 testes, 0 falhas.

## Onde a issue estava imprecisa

Uma coisa só, e ela mudou o tamanho da correção na tool. A "Correção sugerida" fala de **uma** recusa
("a recusa que nomeia os argumentos"), no singular. São duas, e a segunda é a que importa: quando a
categoria incoerente é a do próprio template e a chamada não falou de categoria, **nenhum argumento
da chamada está errado**. Uma recusa que nomeie `category_id` ali manda o agente corrigir algo que
ele não escreveu. É a mesma assimetria que a [021](2026-08-19-update-recurring-stores-an-incoherent-template.md)
já tinha encontrado em `update_recurring`, e a issue não a transportou para cá.

O resto procede exatamente como descrito, incluindo a evidência linha a linha e o aviso de que uma
asserção sobre o resultado passaria: `Σ = 0` fecha com as pernas invertidas.

## O que ficou para trás, deliberadamente

- **A sheet ainda oferece o que o domínio agora recusa.** `ConfirmRecurringViewModel.kt:84` abre com
  `selectedCategory = recurring.category`, e `offeredCategories` (`:263-274`) devolve a seleção à
  lista quando ela não está entre as filtradas — sem distinguir a categoria arquivada da incoerente.
  Num template legado o usuário verá a categoria errada oferecida e tomará a recusa ao confirmar. O
  razão está seguro; a tela não foi mexida porque templates incoerentes já gravados são o assunto da
  [026](../incoherent-recurring-templates-have-no-migration-and-three-surfaces-disagree.md), que continua aberta.
- **A mensagem nova não chega ao usuário**, e nenhuma das outras sete chega tampouco:
  `feature/recurring` não consome `RecurringError.toUiText()` em lugar nenhum, e a sheet mostra
  `retire_action_error_generic` para qualquer `onLeft`. A chave foi acrescentada porque a convenção
  de Error Types a exige, não porque uma tela a renderize. Registrado como
  [029](../a-refusal-with-its-own-message-still-arrives-as-the-generic-one.md).
- **`LaunchYieldUseCase.kt:72-75`** escreve `ContraLeg(AccountType.INCOME, category.dimensionId)` à
  mão em vez de consumir `Category.Type.accountType`. Não pode divergir hoje —
  `EnsureYieldCategoryUseCase.kt:46` cria a categoria como `INCOME` e o tipo é imutável depois da
  criação (`UpdateCategoryUseCaseImpl` só aceita `name` e `iconKey`) —, então é cheiro de duplicação
  da regra de derivação, não um buraco aberto. Não corrigido, e registrado aqui para não ser
  redescoberto como achado.

## Como a classe foi fechada, desta vez

A varredura não foi reler a lista de ocorrências conhecidas — foi procurar onde mais a classe vive:
todo ponto que monta uma contra-perna, seguido até saber se um formulário que aplica `isAccept` fica
entre ele e o razão. A superfície inteira, conferida sítio a sítio:

| Sítio | Carrega categoria? | O que segura a direção |
|---|---|---|
| `BuildTransactionUseCaseImpl.kt:50,78` | sim, `form.category` | `TransactionForm.from` (`TransactionForm.kt:81`) |
| `ConfirmRecurringUseCaseImpl.kt:130,154` | sim | nada, até esta correção |
| `LaunchYieldUseCase.kt:72-75` | sim, a categoria de sistema `yield` | o tipo dela, imutável e criado `INCOME` |
| `AdjustBalanceUseCaseImpl.kt:79,104` | não — `ContraLeg(EQUITY)` | fora da classe |
| `AdjustInvoiceUseCaseImpl.kt:71,99` | não — `ContraLeg(EQUITY)` | fora da classe |

`Transfer` e os dois use cases de pagamento de fatura não aparecem porque não montam contra-perna
nenhuma: chegam ao razão com as pernas já balanceadas, e a única dimensão que carregam é a da
fatura. `StartRecurringFromTransactionUseCase` também não monta uma — reusa a que
`BuildTransactionUseCaseImpl` produziu, e herda o formulário que a segura.

São **três** os sítios que podem divergir, e dois já estavam fechados. O terceiro era este.

Foi o quarto recorte da mesma regra a ser declarado encerrado. Este vem com o mapa acima, para que
quem duvidar tenha o que reconferir em vez de uma afirmação.
