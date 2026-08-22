## 1. Domínio de recorrentes — dar dono ao predicado (D2)

- [x] 1.1 Em `feature/recurring/api/.../usecase/`, extrair o predicado "não arquivada e sem ocorrência no mês M" para um dono próprio, parametrizado por `YearMonth` (hoje ele existe apenas embutido em `GetPendingRecurringUseCase`)
- [x] 1.2 Reescrever `GetPendingRecurringUseCase` como esse predicado **mais** o corte de dia efetivo, sem reimplementar o conjunto de tratadas; o resultado observável não muda
- [x] 1.3 Remover o `handledRecurringIds` recalculado em linha em `DashboardComponentsBuilder.pendingRecurring()`, passando a consumir o predicado novo
- [x] 1.4 Testes: predicado inclui recorrente do mês ainda não vencida; exclui a que tem ocorrência no mês; exclui arquivada; e `GetPendingRecurringUseCase` devolve exatamente o que devolvia antes

## 2. Domínio de faturas — a leitura do perímetro (D3)

- [x] 2.1 Em `core/database/.../dao/InvoiceDao.kt`, query nova com o critério `status != 'PAID' AND dueMonth <= :month`, sem whitelist de status (é o que inclui `RETROACTIVE` e `FUTURE` vencida)
- [x] 2.2 Expor a leitura em `IInvoiceRepository` (`feature/creditcards/api`) e implementá-la em `InvoiceRepository`, com KDoc declarando a exceção local de `RETROACTIVE` e apontando a issue global aberta
- [x] 2.3 Testes: retroativa com saldo entra; fatura paga não entra; fatura de vencimento futuro não entra; fatura vencida em mês anterior entra

## 3. O devido agregado e por moeda (D4)

- [x] 3.1 Somar o devido do perímetro via `IEntryRepository.owedByDimensionByCurrency(dimensionIds)` — uma leitura para N faturas, resultado por moeda
- [x] 3.2 Ignorar fatura sem `dimensionId`; coagir saldo credor a zero **por fatura**, antes da soma, como `InvoiceUiMapperImpl` faz
- [x] 3.3 Testes: duas faturas em moedas diferentes somam cada uma com a sua; crédito de um cartão não abate dívida de outro; N faturas custam uma leitura

## 4. O widget no dashboard (D1, D7)

- [x] 4.1 `DashboardComponentType.kt` — entrada nova com `defaultConfig`: as duas chaves de fonte ligadas, `TOP_SPACING` e `HIDE_WHEN_EMPTY`
- [x] 4.2 `DashboardComponentConfig.kt` — as duas chaves de fonte e o leitor com default, no padrão dos demais
- [x] 4.3 `DashboardComponentsInput` — campo novo com as faturas do perímetro; **não** alterar `invoicesByCreditCardId`, de que `creditCardsPager` depende
- [x] 4.4 `DashboardViewModel` — alimentar o campo novo a partir da leitura da task 2.2, sem passar pelo `associateBy` das linhas 64-66
- [x] 4.5 `DashboardComponent.kt` / `DashboardComponentVariant.kt` — o par novo, no padrão `Viewing`/`Preview`
- [x] 4.6 `DashboardComponentsBuilder` — o construtor: soma as duas fontes por moeda, consolida por `input.figure(...)`, e retorna `null` apenas quando as fontes ligadas nada têm no mês
- [x] 4.7 Garantir que fontes desligadas produzem **zero exibido** e nunca `null` — o caminho de vazio-de-fontes ignora `hide_when_empty`
- [x] 4.8 `DashboardComponentContent.kt` — renderizar o par com os dois cartões, sempre ambos, zero exibido como zero
- [x] 4.9 `DashboardPreviewFactory.kt` — preview do widget novo (sem ele, o widget não aparece na vitrine)
- [x] 4.10 `DashboardComponentOptionsModal` — os dois toggles na configuração do widget

## 5. Depreciação e layout padrão (D5, D6)

- [x] 5.1 `DashboardComponentType.kt` — flag de depreciação no enum; marcar `PENDING_BALANCE_STATS`
- [x] 5.2 `DashboardViewModel.kt:300` — filtrar deprecados de `availableItems` (a vitrine), e **somente** dali
- [x] 5.3 Confirmar que `DashboardPreviewFactory.createPreview` continua atendendo `PENDING_BALANCE_STATS`: `activeItems` faz `?: return@mapNotNull null` (linhas 289-290), e sem preview o widget sumiria do modo de edição de quem o tem salvo
- [x] 5.4 `GetDashboardPreferencesUseCase.defaultPreferences()` — o widget novo assume a posição 2 com `HIDE_WHEN_EMPTY = "true"`; `PENDING_BALANCE_STATS` sai do padrão
- [x] 5.5 Testes: deprecado fora da vitrine; deprecado ainda construído e com preview quando salvo em preferência; nenhuma preferência salva reescrita

## 6. Strings (`core/resources`)

- [x] 6.1 Título do widget e os rótulos `A entrar` / `A sair` em `values/strings.xml` **e** `values-en/strings.xml` — chave ausente em um dos dois é bug
- [x] 6.2 Rótulos dos dois toggles na configuração, nos dois arquivos

## 7. Testes do widget

- [x] 7.1 Fontes e classes: recorrente de receita alimenta a entrar; fatura alimenta só a sair; recorrente de despesa alimenta a sair
- [x] 7.2 Invariância sob confirmação: confirmar recorrente de cartão não move o total; confirmar recorrente de conta reduz o total
- [x] 7.3 Configuração: ambas ligadas por padrão; classe estruturalmente zerada continua exibida; duas fontes desligadas exibem zero e o widget permanece mesmo com `hide_when_empty`
- [x] 7.4 `hide_when_empty` ainda oculta quando as fontes ligadas nada têm no mês
- [x] 7.5 Parcela do mês contada uma vez só, pela fatura
- [x] 7.6 Retroativa sem saldo: está no perímetro e contribui zero, sem mover nenhuma das duas classes
- [x] 7.7 Confirmação que aterrissa na fatura de vencimento posterior: o valor sai da figura, pelo corte de vencimento

## 8. E2E (Maestro)

- [x] 8.1 `.maestro/flows/dashboard/initial_state.yaml:48` afirma `dashboard_component_balance_stats_pending` no estado inicial — atualizar para o widget novo, cuja tag sai automática de `dashboard_component_${variant.key}` (`DashboardViewingContent.kt:80`)
- [x] 8.2 Revisar `.maestro/flows/dashboard/customization.yaml` quanto ao conteúdo da vitrine de adição, que perde um item e ganha outro

## 9. Verificação

- [x] 9.1 `./gradlew jvmTest` — suíte inteira verde, com a saída lida
- [x] 9.2 Conferir que `DashboardBalanceWidgetsCatalogTest` e `DashboardComponentModesTest` continuam válidos com o enum novo e o flag de depreciação
- [x] 9.3 Rodar os fluxos afetados no AVD exigido por `.maestro/README.md` §2 e reportar em que device a execução aconteceu — `--include-tags dashboard,recurring` (os quatro fluxos que este change toca), 4/4 verdes em `finsight_e2e` (API 36, `pixel_6`, en-US, `nokeys`). Os outros dez fluxos da suíte não foram rodados.
- [x] 9.4 `openspec validate add-month-settlement-widget --strict` — o nome da change é posicional; `--change` é opção de `status`/`instructions` e o `validate` a recusa
