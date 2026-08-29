# Tasks — redesign-budgets-screen

## 1. Recursos

- [x] 1.1 Adicionar em `values/strings.xml` **e** `values-en/strings.xml`: rótulo do gasto na
      linha; excedente da pilha de categorias (`+%1$d`); aviso de câmbio da lista e o seu
      chamado para o acervo; `contentDescription` do glifo de estouro; `contentDescription` do
      marcador de teto derivado; rótulos de período (mensal/semanal) — estes últimos ficam
      declarados mas sem consumidor, e só serão exibidos quando houver mais de um período.
- [x] 1.2 Aposentar as chaves cujo único consumidor é o card que sai — `budgets_limit`,
      `budgets_spent`, `budgets_remaining`, `budgets_exceeded_by`, `budgets_category_singular`,
      `budgets_category_plural` — nos dois idiomas. Confirmar com busca que nenhuma outra
      superfície as referencia antes de remover.

## 2. O componente da linha

- [x] 2.1 Criar `BudgetCard.kt` em `feature/budgets/impl/.../ui/screen/budgets/`: `Card` de raio
      12 e `surfaceContainer`, com identidade e figuras em **dois blocos** — ~62 dp com teto
      digitado, ~80 dp com teto derivado (design D7, que substituiu a grade 2×2 de ~52 dp).
- [x] 2.2 O anel: `CategoryIconBox` existente envolvido por um indicador circular de progresso
      de ~42 dp e traço ~3,5 dp, tingido por `budgetProgressColor(progress)`. **Sem fração, sem
      arco** — só o trilho —, e a altura da linha não muda por isso. Desenhado à mão em vez de
      `CircularProgressIndicator`: os dois estados diferem por um arco e nada mais do anel pode
      se mover entre eles.
- [x] 2.3 A pilha de ícones de categoria: largura limitada — três chips —, sobreposição, sem
      tint (ver design D3), com excedente contado quando houver mais categorias do que a pilha
      comporta.
- [x] 2.4 O teto como figura principal, no bloco das figuras; o gasto imediatamente abaixo
      dele, com o rótulo que o desambigua. O bloco é alinhado à direita e **não tem peso**, de
      modo que é medido antes da identidade — é a identidade que cede largura.
- [x] 2.5 O glifo de estouro com `contentDescription`, junto à identidade.
- [x] 2.6 O marcador de teto derivado — `Icons.Outlined.Autorenew` + percentual + nome da
      receita base, em `Primary1` — exibido apenas quando `limitType == PERCENTAGE`, em **linha
      própria imediatamente acima do teto** que ele qualifica (design D7). O percentual não
      trunca; o nome cede primeiro, sob um cap de largura, e some sem deixar separador pendurado
      quando a recorrente não existe mais.
- [x] 2.7 O slot do período: declarado no componente, **não renderizado** enquanto todo
      orçamento for mensal.
- [x] 2.8 `testTag`s: manter `budget_card`, `budget_limit_amount` e `budget_spent_amount`;
      criar a tag do glifo de estouro. As tags `budget_remaining_label` e
      `budget_remaining_amount` deixam de existir.

## 3. A tela

- [x] 3.1 `BudgetsScreen`: remover `BudgetProgressItem` e passar a renderizar `BudgetCard`.
- [x] 3.2 O aviso de câmbio como primeiro item da lista, exibido apenas quando alguma linha
      recair na marca de ausência, com navegação para o acervo de taxas. Decidido (design D5):
      nasce como componente irmão, `ConsolidationListNotice` ao lado de `ConsolidationBadge` em
      `core/designsystem`. Emitido condicionalmente, e não composto para nada — um item que
      desenha nada ainda custa o espaçamento da lista.
- [x] 3.3 Remover o `ConsolidationBadge` por linha.
- [x] 3.4 Conferir que o `EmptyBudgetsState` e o FAB seguem intactos, e que `budgets_add`
      continua sendo a mesma tag nos dois caminhos.

## 4. Ordenação

- [x] 4.1 `BudgetsViewModel`: ordenar por `progress` decrescente **depois** de
      `CalculateBudgetProgressUseCase`, com progresso nulo ao fim (design D6). Não tocar em
      `BudgetDao`.
- [x] 4.2 Teste de unidade da ordenação: estourado antes de tranquilo independentemente de
      `createdAt`; irresolvível ao fim, e não entre os de menor consumo.

## 5. Testes

- [x] 5.1 Teste de unidade da regra de exibição do marcador derivado: `PERCENTAGE` recebe,
      `FIXED` não.
- [x] 5.2 Teste de unidade do estado irresolvível: a linha exibe a marca de ausência e o
      indicador não é desenhado.
- [x] 5.3 Rodar `./gradlew jvmTest` e ler a saída. Verde, com os 14 testes novos de
      `BudgetCardStateTest` e `BudgetListOrderTest` entre eles.

## 6. E2E

- [x] 6.1 Reescrever os três blocos de asserção de `.maestro/flows/budgets/lifecycle.yaml` que
      usam `budget_remaining_label` / `budget_remaining_amount`: passam a asserir o glifo de
      estouro e a relação entre `budget_limit_amount` e `budget_spent_amount`.
- [x] 6.2 Atualizar o comentário do fluxo que afirma haver duas implementações independentes da
      regra de estouro — na lista passa a haver uma (design, Riscos).
- [x] 6.3 Rodar o fluxo no AVD exigido pelo `.maestro/README.md` §2 e relatar em qual aparelho.
      Rodado na AVD `finsight_e2e` — API 36, 1080×2400 @420, en-US, `nokeys` —, com
      `budgets_lifecycle` verde.

## 7. Verificação visual

Toda esta seção foi conferida na AVD `finsight_e2e` (API 36, 1080×2400 @420, en-US, `nokeys`).

- [x] 7.1 Conferir em aparelho, com ícones e nomes reais, se a linha se sustenta com anel +
      até três chips de categoria (design, Riscos). Sustenta. O que a conferência mudou foi
      outra coisa: o título subiu para o tamanho do teto, porque em corpo menor lia como
      legenda do próprio orçamento.
- [x] 7.2 Conferir a linha com escala de fonte do sistema aumentada: pode crescer, desde que
      cresça igual em toda variante. Conferido em 1,0 e 1,3. Quem governa a altura é a pilha
      das figuras, cujo número de linhas depende só do tipo do teto; em 1,3 o que trunca é o
      nome da receita base, e o título segue inteiro.
- [x] 7.3 Conferir a linha nos dois temas, claro e escuro.
