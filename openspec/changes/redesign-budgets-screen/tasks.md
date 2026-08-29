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

- [x] 2.1 Criar `BudgetCard.kt` em `feature/budgets/impl/.../ui/screen/budgets/`, nos moldes de
      `RecurringCard.kt`: grade 2×2, ~52 dp, `Card` de raio 12 e `surfaceContainer`.
- [x] 2.2 O anel: `CategoryIconBox` existente envolvido por um indicador circular de progresso
      de ~36 dp e traço ~3 dp, tingido por `budgetProgressColor(progress)`. **Sem fração, sem
      arco** — só o trilho —, e a altura da linha não muda por isso.
- [x] 2.3 A pilha de ícones de categoria: largura constante, sobreposição, sem tint (ver design
      D3), com excedente contado quando houver mais categorias do que a pilha comporta.
- [x] 2.4 O teto como figura principal, alinhado à direita da linha superior; o gasto na linha
      inferior direita, com o rótulo que o desambigua.
- [x] 2.5 O glifo de estouro com `contentDescription`, junto à identidade.
- [x] 2.6 O marcador de teto derivado — `Icons.Outlined.Autorenew` + percentual, em `Primary1`
      — exibido apenas quando `limitType == PERCENTAGE`.
- [x] 2.7 O slot do período: declarado no componente, **não renderizado** enquanto todo
      orçamento for mensal.
- [x] 2.8 `testTag`s: manter `budget_card`, `budget_limit_amount` e `budget_spent_amount`;
      criar a tag do glifo de estouro. As tags `budget_remaining_label` e
      `budget_remaining_amount` deixam de existir.

## 3. A tela

- [x] 3.1 `BudgetsScreen`: remover `BudgetProgressItem` e passar a renderizar `BudgetCard`.
- [x] 3.2 O aviso de câmbio como primeiro item da lista, exibido apenas quando alguma linha
      recair na marca de ausência, com navegação para o acervo de taxas. Decidir a casa do
      componente (design D5) antes de escrever.
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
- [x] 5.3 Rodar `./gradlew jvmTest` e ler a saída.

## 6. E2E

- [x] 6.1 Reescrever os três blocos de asserção de `.maestro/flows/budgets/lifecycle.yaml` que
      usam `budget_remaining_label` / `budget_remaining_amount`: passam a asserir o glifo de
      estouro e a relação entre `budget_limit_amount` e `budget_spent_amount`.
- [x] 6.2 Atualizar o comentário do fluxo que afirma haver duas implementações independentes da
      regra de estouro — na lista passa a haver uma (design, Riscos).
- [x] 6.3 Rodar o fluxo no AVD exigido pelo `.maestro/README.md` §2 e relatar em qual aparelho.

## 7. Verificação visual

- [x] 7.1 Conferir em aparelho, com ícones e nomes reais, se a linha se sustenta com anel +
      até três chips de categoria (design, Riscos).
- [x] 7.2 Conferir a linha com escala de fonte do sistema aumentada: pode crescer, desde que
      cresça igual em toda variante.
- [x] 7.3 Conferir a linha nos dois temas, claro e escuro.
