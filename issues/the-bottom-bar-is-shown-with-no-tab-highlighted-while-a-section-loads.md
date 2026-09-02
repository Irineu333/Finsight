---
area: app
severity: low
type: ux
---

# A bottom bar fica sem aba nenhuma marcada enquanto uma seção carrega

## Cenário

**DADO** o Dashboard em janela `COMPACT`, com a aba Dashboard marcada na bottom bar
**QUANDO** o usuário abre uma seção que não é aba primária — Categorias, Orçamentos, Cartões,
Recorrentes, Suporte — pela grade de ações rápidas
**ENTÃO** a barra continua na tela enquanto a seção lê os dados, e **nenhuma das duas abas fica
marcada**: o Dashboard perde o destaque no ato
**DEVERIA** manter o destaque enquanto a barra ainda está lá

## Mecânica

`selectedItem` passa a ser a seção nova assim que a navegação acontece, e `BottomNavigationBar`
compara `selectedItem == item` contra as duas abas primárias — uma seção que não é aba não casa com
nenhuma. O `?: bottomItems.first()` não socorre: ele só cobre `selectedItem` nulo, e aqui ele é
conhecido.

A barra fica na tela porque a casca segura a cromagem enquanto o destino não publica, e a seção
publica `null` durante a leitura de propósito. Antes disso a barra sumia no ato — o destaque vazio
existia só durante a animação de saída; agora dura a leitura inteira.

## Evidência

- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `selectedItem = selectedItem ?: bottomItems.first()`
- `core/designsystem/.../ui/component/BottomNavigationBar.kt` — `selected = selectedItem == item`
- `feature/categories/impl/.../screen/categories/CategoriesScreen.kt` — `is CategoriesUiState.Loading -> null`

## Consequência

A barra passa alguns instantes afirmando que o usuário não está em lugar nenhum. Não engana sobre
dado e não impede nada.

## Sugestão

Marcar a última aba primária por onde se passou enquanto a barra ainda estiver visível — é o que ela
está de fato mostrando. Não vinculante.
