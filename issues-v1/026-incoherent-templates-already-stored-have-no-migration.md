# 026 — Templates incoerentes já gravados não têm migração, e três telas respondem coisas diferentes

**Área:** recurring / database · **Tipo:** dados · **Criticidade:** média · **Status:** aberto
**Verificado em:** 2026-08-19, por uma revisão adversarial do commit `e58abf948`

## O que está errado

A [021](archive/021-update-recurring-stores-an-incoherent-template.md) fechou a escrita: nenhum
caminho persiste mais um template de receita classificado sob categoria de despesa. As linhas
gravadas **antes** dela continuam no banco. `AppDatabase` está na `version = 15`, a migração mais
nova é `Migration14To15` e a correção não acrescentou nenhuma.

O problema não é a linha existir — é que cada superfície responde uma coisa diferente sobre ela.

| Ação | O que acontece | Onde |
|---|---|---|
| ler | remontado sem filtro, incoerente | `core/database/.../mapper/RecurringMapper.kt:17-33` |
| `list_recurring` | responde `type` e `category` que se contradizem | `ListRecurringTool.kt:118` |
| abrir a sheet de edição | categoria **descartada em silêncio** na primeira composição | `RecurringFormModal.kt:110-112` |
| `update_recurring` (qualquer campo) | **recusado**, culpando um `type` que a chamada não deu | `RecurringWriteTools.kt` |
| `confirm_recurring` | **recusado**, nomeando o que o template carrega | [025](archive/025-confirm-recurring-writes-the-wrong-direction.md), corrigida |
| confirmar pela sheet | a categoria incoerente é **oferecida**, e a confirmação é recusada com uma mensagem genérica | `ConfirmRecurringViewModel.kt:84,219-222,263-274` |

A tela de edição conserta em silêncio, as duas tools recusam, e a sheet de confirmação oferece o que
o domínio recusa. Nenhuma está errada isoladamente; juntas, não há resposta que o usuário possa
acreditar.

**O razão já não é mais um dos desacordos.** Enquanto esta issue esteve aberta, confirmar um ciclo
gravava o lançamento incoerente; a [025](archive/025-confirm-recurring-writes-the-wrong-direction.md)
fechou isso em 2026-08-19, no use case e na tool. O que sobrou é o desacordo entre as superfícies, e
a sheet piorou de posição: antes ela postava, agora ela oferece um caminho que termina em recusa — e
numa mensagem que não diz qual é o problema, pela [029](029-recurring-errors-never-reach-the-user.md).

## Um caso sem saída

Um template incoerente **sem título** não pode ser editado por `update_recurring` sem que a chamada
também dê um título: a recusa da categoria carregada bloqueia qualquer edição, e a saída que ela
nomeia — `category_id: 0` — esbarra em `TITLE_OR_CATEGORY_REQUIRED`, porque a categoria era o único
nome. As saídas existem (dar `title`, ou uma categoria compatível) e o texto da recusa não as
menciona.

## Correção sugerida

A decisão é de produto e não deveria entrar de carona numa correção de código:

- **migrar** as linhas, limpando a categoria incoerente — repara o dado, e apaga uma classificação
  que o usuário escolheu, sem avisar;
- **não migrar** e ensinar as três superfícies a dizer a mesma coisa sobre a linha;
- **migrar apenas o que a leitura já corrige**, deixando a sheet e a tool concordarem com o mapper.

Seja qual for, a recusa de `update_recurring` deveria nomear as duas saídas, e não só o
`category_id: 0`. E a sheet de confirmação deveria parar de oferecer a categoria incoerente:
`offeredCategories` (`ConfirmRecurringViewModel.kt:263-274`) devolve a seleção à lista sem distinguir
a categoria **arquivada** — que é o caso para o qual o ramo existe, e a KDoc só descreve esse — da
**incoerente**, que o domínio agora recusa.

## Observação

A sheet de confirmação re-oferece a categoria incoerente:
`ConfirmRecurringViewModel.offeredCategories` (`:263-274`) devolve a seleção à lista quando ela não
está entre as filtradas. A KDoc justifica isso como continuidade para uma categoria **arquivada**, e o
ramo não separa os dois casos. `OfferedCategoriesTest.kt:57-73` cobre a arquivada-mas-coerente e não
cobre a incoerente.
