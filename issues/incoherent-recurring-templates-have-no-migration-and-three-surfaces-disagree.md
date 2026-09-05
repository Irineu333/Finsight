---
area: recurring
severity: medium
type: data
---

# Templates recorrentes incoerentes não têm migração, e três superfícies respondem coisas diferentes sobre eles

## Cenário

**DADO** um template de receita gravado antes da correção que fechou a escrita, classificado sob
uma categoria de despesa
**QUANDO** o usuário abre a sheet de confirmação do ciclo
**ENTÃO** a categoria incoerente é **oferecida** na lista, ele confirma, o domínio recusa e a tela
mostra *"Algo deu errado. Tente de novo em instantes."*
**DEVERIA** não oferecer o que o domínio recusa — e, recusando, dizer qual é o problema

## Mecânica

Nenhum caminho de escrita persiste mais um template incoerente. As linhas gravadas **antes** disso
continuam no banco: `AppSchema.VERSION` é 15, a migração mais nova é `Migration14To15`, e a
correção não acrescentou nenhuma.

O problema não é a linha existir — é cada superfície responder uma coisa diferente sobre ela:

| ação | o que acontece |
|---|---|
| ler | remontada sem filtro, incoerente (`RecurringMapper.toDomain`) |
| `list_recurring` | responde `type` e `category` que se contradizem |
| abrir a sheet de edição | a categoria é **descartada em silêncio** na primeira composição |
| `update_recurring`, qualquer campo | **recusado**, culpando um `type` que a chamada não deu |
| `confirm_recurring` | **recusado**, nomeando o que o template carrega |
| confirmar pela sheet | a categoria incoerente é **oferecida**, e a confirmação é recusada |

A tela de edição conserta em silêncio, as duas tools recusam, e a sheet de confirmação oferece o
que o domínio recusa. Nenhuma está errada isoladamente; juntas, não há resposta em que acreditar.

`offeredCategories` é onde a sheet decide, e sua KDoc justifica o ramo por um caso só: continuidade
de uma categoria **arquivada** depois de o template a ter elegido. O ramo não distingue esse caso do
da categoria **incoerente**, que o domínio agora recusa.

## Evidência

- `core/database/.../migration/` — a mais nova é `Migration14To15`; `AppSchema.VERSION` = 15
- `RecurringMapper.toDomain()` (`core/database`) — remonta com `category = category`, sem filtro
- `ListRecurringTool.toAgentRecurring()` — `type = type.name.lowercase()` e `category = category?.name`,
  lado a lado
- `RecurringFormModal` — `LaunchedEffect(type) { selectedCategory = selectedCategory?.takeIf {
  it.type.isAccept(type) } }`, o descarte silencioso
- `offeredCategories()` (`feature/recurring/impl` — `ui/modal/confirmRecurring/ConfirmRecurringViewModel.kt`)
  — `else -> offered + selected`, e a KDoc que só descreve o caso arquivado
- `OfferedCategoriesTest` — cobre a arquivada-mas-coerente; não cobre a incoerente
- `RecurringWriteTools` / `RecurringOperationTools` — as duas recusas, sobre a categoria carregada

## Um caso sem saída

Um template incoerente **sem título** não pode ser editado por `update_recurring` sem que a chamada
também dê um título: a recusa da categoria carregada bloqueia qualquer edição, e a saída que ela
nomeia — `category_id` 0 — esbarra em `RecurringError.TITLE_OR_CATEGORY_REQUIRED`, porque a
categoria era o único nome. As saídas existem (dar `title`, ou uma categoria compatível) e o texto
da recusa não as menciona.

## Consequência

O usuário vê um template que a lista mostra, a edição altera calada, e a confirmação recusa — sem
nada que aponte para a categoria. O caminho de saída existe (trocar a categoria) e é invisível,
porque a mensagem que chega é genérica (*a-refusal-with-its-own-message-still-arrives-as-the-generic-one*).

## Sugestão

A decisão é de produto e não deveria entrar de carona numa correção de código: **migrar** as linhas
limpando a categoria incoerente (repara o dado, apaga uma classificação que o usuário escolheu, sem
avisar); **não migrar** e ensinar as três superfícies a dizer a mesma coisa; ou **migrar apenas o
que a leitura já corrige**, deixando sheet e tool concordarem com o mapper.

Seja qual for, duas coisas independem dela: a recusa de `update_recurring` deveria nomear as duas
saídas, e `offeredCategories` deveria separar a categoria arquivada — que é o caso para o qual o
ramo existe — da incoerente. Não vinculante.
