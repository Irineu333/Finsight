> Ordem deliberada: o spike de build vem antes de tudo (D1 do risco), e o **domínio inteiro**
> (grupos 2–4) vem antes do servidor. Os grupos 2–4 compilam, são testáveis e podem ser
> mesclados sem que exista servidor algum — se a mudança for fatiada, é aqui que ela corta.

## 1. Spike de compatibilidade — CONCLUÍDO antes da implementação

> Executado durante a proposta. As versões estão fixadas em D12 e não há incerteza de build.

- [x] 1.1 `io.modelcontextprotocol:kotlin-sdk-server:0.14.0` exige **Ktor 3.4.3**, o pino exato do projeto, e compila com Kotlin 2.3.10. A `0.15.0` foi descartada: exige Ktor 3.5.1 e `kotlin-stdlib 2.4.0`, à frente do compilador.
- [x] 1.2 Não foi necessário: nenhuma das saídas alternativas precisou ser usada.
- [x] 1.3 Ciclo do protocolo exercitado ponta a ponta — `initialize` → `tools/list` → `tools/call` — com o servidor escutando em `127.0.0.1`, revisão `2025-11-25` e `capabilities.tools.listChanged = true`.
- [x] 1.4 Impacto transitivo medido: o SDK eleva `kotlinx-serialization-json` 1.8.0 → 1.11.0 e `kotlinx-coroutines` 1.10.2 → 1.11.0 no app inteiro. Com as duas elevadas, `./gradlew jvmTest --rerun-tasks` executou sem cache e a suíte passou inteira. **A contagem "1488 testes em 21 módulos" registrada na medição não serve de linha de base:** ela somava relatórios de `app/mcp` e `feature/mcp/impl`, diretórios que o protótipo do spike deixou em disco contendo só `build/`, sem fonte alguma e sem estarem em `settings.gradle.kts`. Os módulos reais são **19**.

## 2. Identidade por id (`use-case-identity`)

> Aditivo por construção: nenhum dos 24 chamadores existentes muda. Verificação do grupo: a
> suíte passa sem que nenhum call site tenha sido tocado.

- [x] 2.1 Escrever a forma por id de `ArchiveAccountUseCase` e `DeleteAccountUseCase`, com a sobrecarga por agregado delegando na interface, e a recusa tipada de "não encontrado".
- [x] 2.2 Idem para `AdjustBalanceUseCase` e `LaunchYieldUseCase`.
- [x] 2.3 Idem para `DeleteCategoryUseCase` e `ResolveCategoryRetirabilityUseCase`.
- [x] 2.4 Idem para `DeleteTransactionUseCase`.
- [x] 2.5 Idem para `AdjustInvoiceUseCase`, `CreateInvoiceUseCase` e `GetOrCreateInvoiceForMonthUseCase`.
- [x] 2.6 Idem para `DeleteInstallmentUseCase`.
- [x] 2.7 Idem para `SkipRecurringUseCase`.
- [x] 2.8 Remover os seis defaults derivados do agregado de `ConfirmRecurringUseCase` (D8) e ajustar `ConfirmRecurringViewModel`, que já passa os oito argumentos explicitamente. Escrever a forma por id.
- [x] 2.9 Dar a `CalculateAvailableLimitUseCase` a forma plural — recebe as identidades dos cartões e devolve o mapa (D7) — e migrar a tela que hoje a chama em laço.
- [x] 2.10 Uniformizar `PayInvoicePaymentUseCase` e `AdvanceInvoicePaymentUseCase`, que hoje recebem `invoiceId: Long` e `account: Account` na mesma assinatura.
- [x] 2.11 Teste que percorre as interfaces de use case público e falha quando uma delas não oferece a forma por id, ou quando declara valor padrão derivado de outro parâmetro agregado.
- [x] 2.12 Teste que exercita as duas formas de um mesmo use case com a mesma identidade e exige resultado idêntico.

## 3. Use cases que precisam nascer (D9)

> Cada extração migra o chamador atual **no mesmo passo**. Um use case extraído sem o ViewModel
> migrado cria duas verdades sobre a mesma operação, que é o defeito que a mudança existe para
> não introduzir.

- [x] 3.1 `CreateCategoryUseCase` e `UpdateCategoryUseCase`, com a validação, o `trim()` e o `createdAt` que hoje vivem em `CategoryFormViewModel:120-151`.
- [x] 3.2 Migrar `CategoryFormViewModel` para consumi-los; ele deixa de chamar `repository.insert/update`.
- [x] 3.3 `CreateBudgetUseCase`, `UpdateBudgetUseCase` e `DeleteBudgetUseCase`, extraídos de `BudgetFormViewModel` e `DeleteBudgetViewModel`.
- [x] 3.4 Migrar os dois ViewModels de orçamento.
- [x] 3.5 `UpdateInstallmentUseCase` sobre `IInstallmentRepository.updateInstallment`.
- [x] 3.6 `RegisterTransactionUseCase`: o despacho entre parcelamento, recorrência e transação simples que hoje é um `if` em `AddTransactionViewModel:299-340`. Recebe o formulário e decide.
- [x] 3.7 Migrar `AddTransactionViewModel` para consumi-lo; o `if` sai do ViewModel.
- [x] 3.8 Teste de cada use case novo, cobrindo o caminho que o ViewModel exercitava.
- [x] 3.9 Teste que falha quando um ViewModel chama `insert`/`update`/`delete` de repositório diretamente — o guarda que impede a regressão dos oito pontos.
- [x] 3.10 **`UpdateTransactionUseCase`, que a change não previa.** D9 enumera sete extrações e editar um lançamento não está entre elas, mas `EditTransactionViewModel` escrevia direto em `transactionRepository.updateTransaction` — o oitavo ponto, registrado como pendência pelo próprio guarda da 3.9. Sem dono, a 10.2 só tinha caminhos proibidos: reimplementar a edição na ferramenta ou escrever no repositório. A regra é a forma da reescrita — `updateTransaction` apaga todas as pernas e reconstrói a partir de **uma** mais o `contra` —, então o que ela não consegue exprimir é recusado: mais de uma perna monetária (transferência e pagamento), um ajuste e uma parcela. `Transaction.editObstacle` (`core/model`) é o dono único dessa derivação, e `ViewTransactionUiState.isEditable` passou a lê-lo em vez de repeti-lo. O ViewModel foi migrado no mesmo passo e saiu da lista do guarda.

## 4. Promoção para `api`

> Interface na `api`, `Impl` no `impl` — o padrão que `ArchiveAccountUseCase` já usa. Nenhuma
> regra se move; muda a visibilidade.

- [x] 4.1 Promover os use cases de contas: `CreateAccountUseCase`, `UpdateAccountUseCase`, `AdjustBalanceUseCase`, `TransferBetweenAccountsUseCase`, `SetDefaultAccountUseCase`, `UnarchiveAccountUseCase`.
- [x] 4.2 Promover os de categorias: `ArchiveCategoryUseCase`, `UnarchiveCategoryUseCase`, `DeleteCategoryUseCase`, `ResolveCategoryRetirabilityUseCase`, e os dois criados em 3.1.
- [x] 4.3 Promover os de cartão: `AddCreditCardUseCase`, `UpdateCreditCardUseCase`, `DeleteCreditCardUseCase`, `ArchiveCreditCardUseCase`, `UnarchiveCreditCardUseCase`, `CalculateAvailableLimitUseCase`.
- [x] 4.4 Promover os de fatura: `CreateInvoiceUseCase`, `OpenInvoiceUseCase`, `CloseInvoiceUseCase`, `PayInvoiceUseCase`, `PayInvoicePaymentUseCase`, `AdvanceInvoicePaymentUseCase`, `ReopenInvoiceUseCase`, `AdjustInvoiceUseCase`, `DeleteFutureInvoiceUseCase`, `CalculateInvoiceUseCase`.
- [x] 4.5 Promover os de recorrência: `SaveRecurringUseCase`, `ConfirmRecurringUseCase`, `SkipRecurringUseCase`, `ArchiveRecurringUseCase`, `UnarchiveRecurringUseCase`, `DeleteRecurringUseCase`, `ResolveRecurringRetirabilityUseCase`.
- [x] 4.6 Promover os de orçamento e relatório: os três criados em 3.3 e `CalculateReportStatsUseCase`.
- [x] 4.7 Conferir que nenhuma interface promovida referencia tipo declarado em `impl` — a regra de `feature-entry-points` vale para toda a `api`.

## 5. Módulo e build

- [x] 5.1 Criar `feature/mcp/api` e `feature/mcp/impl` com as convenções `feature.api` e `feature.impl`, e registrá-los em `settings.gradle.kts`.
- [x] 5.2 Declarar na `api` o controlador do servidor (`start`, `stop`, estado observável) em tipos de `:core:*` e a rota `@Serializable` da tela. **Sem entry point**: `feature-entry-points` o exige de "cada feature que expõe UI a outras features", e esta não expõe — Settings apenas navega para a rota.
- [x] 5.3 Adicionar ao catálogo `io.modelcontextprotocol:kotlin-sdk-server:0.14.0` e um engine `ktor-server-*` em `3.4.3`, usados apenas no `jvmMain` do `impl`. Fixar a versão do SDK sem faixa — entre 0.14 e 0.15 o Ktor exigido mudou de minor.
- [x] 5.3a Elevar no catálogo `kotlinx-serialization-json` para `1.11.0` e `kotlinx-coroutines` para `1.11.0`, que o SDK exige e o Gradle elevaria de qualquer forma. Rodar `./gradlew jvmTest --rerun-tasks` e conferir contra a linha de base **medida imediatamente antes de elevar**, nos 19 módulos reais — nunca contra o número de 1.4, que contava diretórios sem fonte. Nenhuma falha, e nenhum teste a menos.
- [x] 5.4 Reescrever a nota do catálogo que diz que Ktor "vive num módulo só" — ela passa a distinguir o módulo que usa cliente do que usa servidor.
- [x] 5.5 Prover o `actual` no-op do controlador nos targets Android e iOS, no padrão de `SupportModule`.
- [x] 5.6 Registrar o módulo Koin da feature e agregá-lo em `appModules`; `NavGraphBuilder.mcpGraph()` no `AppNavHost`.
- [x] 5.7 Teste que confirma que o servidor é alcançável a partir do artefato de distribuição do desktop, e não apenas em execução de desenvolvimento.

## 5b. Registro de atividade — tabela e migração

> A tabela nova sobe a versão de `AppDatabase`. O projeto já tem o caminho: schema exportado
> pelo plugin de convenção e `MigrationSchemaEquivalenceTest` cobrindo as migrações.

- [x] 5b.1 Entidade e DAO do registro em `:core:database`: quando, operação, descrição legível, resultado, e a referência ao que foi criado ou alterado. Sem coluna de leitura — consultas não entram.
- [x] 5b.2 Migração de `AppDatabase` com a versão nova, schema exportado e `MigrationSchemaEquivalenceTest` estendido. Nenhum valor existente é alterado: a tabela nasce vazia.
- [x] 5b.3 Política de retenção declarada e aplicada — o registro não cresce sem limite. Teste que prova o descarte.
- [x] 5b.4 Limpeza pelo usuário. Teste de que limpar remove entradas e **não** altera nenhum lançamento.
- [x] 5b.5 Repositório do registro e a leitura observável que a tela consome.

## 6. Servidor no socket (`mcp-server`)

- [x] 6.1 Ciclo de vida ligado ao processo: `:app:desktop` obtém o controlador via Koin, inicia com a janela e encerra no fechamento, liberando a porta.
- [x] 6.1a **Persistir a habilitação** e subir sozinho nos inícios seguintes, sem visita à tela. Teste: habilitar, encerrar, reabrir, e o servidor está no ar; desabilitar, reabrir, e não está. O usuário habilita **uma vez**.
- [x] 6.1b Teste do estado de estreia: app atualizado e aberto pela primeira vez não sobe nada e não escuta nada, porque não houve escolha a persistir.
- [x] 6.1c **Falha ao subir é dita ao usuário** na interface do app, com o motivo e o caminho — não silenciosa, e sem que o estado exibido diga "no ar" quando não está. O aviso alcança quem não está na tela de configurações. **Fechada pelo grupo 13**: o mecanismo já existia (`App.kt` coleta `McpServerState.Failed` e abre um aviso sobre qualquer tela), e o que faltava era o destino que a mensagem nomeia — "escolha outra porta nas configurações do servidor" apontava para um editor que não existia. Com a 13.2e, o caminho existe e a mesma falha é repetida **no campo** da porta, em palavras próprias (`toPortFieldUiText`): quem está longe da seção é mandado até ela, e quem já está nela lê o erro debaixo do campo que o resolve.
- [x] 6.2 Escuta restrita à interface de loopback. Teste que confirma que o socket não aceita conexão de interface externa.
- [x] 6.2a Validação de `Host`/`Origin` e `DnsRebindingProtectionConfig` (D11) — a defesa contra uma página web aberta no navegador do usuário alcançar `127.0.0.1`. Teste com `Origin` de terceiro sendo recusado.
- [x] 6.3 Geração, persistência e regeneração do token; recusa de requisição sem token ou com token que não confere, antes de qualquer execução.
- [x] 6.4 Teste de que o token sobrevive ao reinício e de que regenerar invalida o anterior.
- [x] 6.5 Porta fixa (padrão `8477`), editável e persistida. Quando ocupada, o servidor **não sobe** e o estado observável diz qual porta está em uso — sem fallback silencioso, que quebraria a configuração já feita no cliente (D10).
- [x] 6.6 Teste de que uma escrita feita pelo servidor emite invalidação e atinge um `Flow` observado, provando a reatividade de D1.
- [x] 6.7 Gravar no registro toda escrita, operação e recusa — e **nenhuma** leitura. Teste que exercita uma sequência de consultas e exige registro vazio, e outro que repete a mesma escrita e exige duas entradas lado a lado.
- [x] 6.8 Expor as sessões em curso no estado observável do controlador, e a ação de encerrá-las.

## 7. Apresentação para o agente (`presentation-mapping`, `mcp-tool-surface`)

- [x] 7.1 DTOs planos da superfície, em `feature/mcp/impl` `jvmMain/.../mcp/surface/`: transação, conta, cartão, fatura, parcelamento, categoria, orçamento e recorrência, mais a figura, a sua decomposição e o envelope de recusa. `@Serializable`, `snake_case` no fio, sem nenhum tipo de domínio — nem enum: `nature`, `status` e `type` viajam como texto.
- [x] 7.2 `AgentFigure`: valor, moeda, decomposição por moeda, a marca de aproximação e a data da taxa. O valor é **anulável**, porque uma figura que nenhuma taxa alcança não tem número único e nomear um dos termos seria escolher moeda à mão.
- [x] 7.3 `Transaction.toAgentTransaction` — irmão de `toTransactionUi`. Consome `deriveTransactionLabel`, `legUnder` sob `TransactionPerspective`, `figureLegUnder` e `itemDisplayAmount`, e não re-deriva nenhum. Sem perspectiva não devolve direção: a spec proíbe apresentar a direção de uma perna escolhida como propriedade do lançamento.
- [x] 7.4 `ConsolidateMoneyUseCase.agentFigure` reduz e traduz; nunca soma moedas nem escolhe taxa. Sem taxa no acervo (D16) devolve a decomposição exata e `limitation`, que nomeia as moedas sem taxa e diz em palavras o que o número deixa de fora.
- [x] 7.5 `AgentSurfaceCarriesNoDomainTest` lê as fontes do pacote e falha por campo de tipo de domínio (`core:ledger`/`core:model` `domain/**`) **e** por modelo da tela (`core:ui` `ui/model`, `DisplayAmount`, `ConsolidatedAmount`). Verificado falhando com `Account`, `TransactionLabel` e `TransactionUi` injetados.
- [x] 7.6 `ScreenAndAgentAgreeTest`: despesa, transferência pelas duas pontas e sem perspectiva, e pagamento cruzando moedas sob duas bases. Verificado falhando quando o mapper troca `figureLegUnder` por `legUnder` (BRL × USD) e quando re-deriva rótulo e perna.
- [x] 7.7 `AgentRefusal(reason, try_instead)`. O motivo são as palavras do próprio erro do domínio — nenhum tipo de erro novo nasceu, e por isso nenhuma chave de string —, e a alternativa vem de `retireActionOf`, o dono de arquivar-versus-apagar que as telas já consultam. `notFound(kind, id)` diz qual identidade não foi achada.
- [x] 7.8 `McpToolName` declara as 56 com o seu eixo e a sua família; `McpSurface.offered` declara as que o servidor anuncia hoje (vazio) e `mcpTools()` é o registro que a DI entrega. `McpSurfaceIsClosedTest` compara os dois nos dois sentidos, e os grupos 8–11 têm de escrever nos dois. **Todo `delete_*` fica no eixo apagar** (8, não 4): o requisito diz "remover definitivamente" sem qualificar entidade, e o seu cenário é literal — conceder "registrar e editar" sem "apagar" deixa um agente que cria e altera e **não remove**.
- [x] 7.9 Duas metades no mesmo teste: nenhuma das 56 se chama por taxa, moeda base ou servidor, e as três retenções constam de `McpSurface.exclusions` como `WITHHELD` com o motivo; e nenhuma fonte que declare `McpTool` segura `IExchangeRateRepository`, `McpServerController` ou `McpServerSettings`, nem chama `.set(` sobre a moeda base. Verificado falhando com uma ferramenta forjada que faz as três coisas.

## 8. Família Perguntas — permissão "ler"

- [x] 8.1 `get_balance`, `get_month_summary`.
- [x] 8.2 `get_category_spending`, `get_category_income`, `get_spending_breakdown` — com o grupo sem categoria como linha própria.
- [x] 8.3 `get_budget_progress` — compondo as três listas que `CalculateBudgetProgressUseCase` exige.
- [x] 8.4 `get_pending_recurring`, `get_card_overview`, `get_report_stats`.
- [x] 8.4a `get_net_worth` — ASSET menos LIABILITY, por moeda e consolidado. Hoje `IEntryRepository` não expõe essa leitura *"porque não tem consumidor em produção"*; o agente é esse consumidor, e `EntryDao.netWorthCents()` já existe agrupado por moeda.
- [x] 8.4b Comparação entre períodos em `get_month_summary` (parâmetro do mês a comparar), devolvendo as variações já calculadas — sem deixar a subtração para o agente.
- [x] 8.4c Toda figura agregada declara o seu perímetro, na descrição e na resposta. `get_balance` diz que a dívida de cartão não está descontada e nomeia `get_net_worth`.
- [x] 8.4d Todo período responde se está em andamento e até que data está apurado; uma comparação marca qual dos lados é o incompleto.
- [x] 8.5 Teste de que o total de um mês com transferência entre contas próprias e pagamento de fatura não os inclui.
- [x] 8.6 Teste de que uma figura que atravessa contas em moedas diferentes traz decomposição, consolidado e data da taxa.

## 9. Família Catálogo — permissão "ler"

- [x] 9.1 `list_transactions` com os filtros de período, conta, categoria, cartão e natureza, e a paginação. **O período é o mês**, não um intervalo livre como sugere `docs/`: é o recorte que os agregados do razão têm, e um intervalo forçaria a trocá-los por um que não concorda com os itens. `account_id` e `card_id` juntos são recusados — uma listagem se lê de um ponto de vista só.
- [x] 9.2 O agregado da listagem, vindo do razão, mais `matching` e `returned`. Três leituras, uma por filtro: `assetMonthFlows + liabilityMonthFlows` sem perspectiva, `scopeStats` sob uma conta ou cartão, e `totalsByDimension` sob uma categoria. **`nature` corta a lista e não move os totais** — o razão não tem agregado cortado por natureza (não existe total de transferência), e o payload diz isso em `narrowed_by`, na descrição e no perímetro, em vez de somar a página para preencher. Teste com 65 lançamentos e página de 50: o total é 1.100,00 e a soma das linhas de despesa da página é 670,00, conferido também contra `get_month_summary`.
- [x] 9.2a Ordem total e determinística, com desempate estável quando a data empata, e a opção de ordenar por **ordem de registro** — sem ela, "o último que eu registrei" não é respondível, já que a data tem resolução de dia. Teste de que paginar não repete nem omite item. As duas ordens terminam na identidade que o razão atribui, que é única por construção; `order_by` oferece o critério, e a coluna que o realiza é assunto interno da ferramenta.
- [x] 9.6a Cada descrição de `list_invoices` e `get_card_overview` diz o seu recorte; a escolha entre as duas não deve exigir chamar as duas e comparar as saídas. As duas passaram a declarar a **unidade** da resposta no mesmo vocabulário — uma responde por FATURAS, a outra por CARTÕES — e cada uma nomeia a outra. O mesmo foi feito em `list_cards`, `list_categories`, `list_budgets` e `list_recurring`, que também se sobrepõem a uma ferramenta da família 1.
- [x] 9.3 A alternância de vocabulário por perspectiva: sem conta na chamada, natureza; com conta, direção. Teste com uma transferência, nos dois modos.
- [x] 9.4 `get_transaction` com todas as pernas monetárias e a taxa praticada quando elas cruzam moedas. Cada perna vai **assinada como o razão a gravou**, sem regra de exibição: a direção já é dita, e as pernas de uma operação têm de somar zero para quem as conferir.
- [x] 9.5 `list_accounts`, `list_cards`, `list_categories` — cada uma com a figura que a acompanha. O total de `list_accounts` é a leitura do razão sobre **exatamente** as contas listadas: as arquivadas que a lista omite são nomeadas ao agregado, não subtraídas depois.
- [x] 9.6 `list_invoices` usando a leitura em lote de devido, `get_invoice` com janela e extrato. Teste com três faturas e página de duas: cada devido vem da leitura em lote e `owed_total` é o das três.
- [x] 9.7 `list_installments`, `list_budgets`, `list_recurring`. **`list_budgets` não traz gasto nem progresso**, ao contrário do que `docs/` supõe: isso é pergunta sobre um mês e `get_budget_progress` a responde — duas ferramentas com a mesma saída não teriam recorte a declarar.

## 10. Família Registro — permissões "registrar e editar" e "apagar"

- [x] 10.1 `create_transaction` sobre `RegisterTransactionUseCase`; teste de que um formulário parcelado produz as N transações. O despacho fica inteiro no use case — a ferramenta não lê `installments > 1`. Um formulário de 12 parcelas sobre o protocolo produz 12 lançamentos no razão, um por fatura, e **uma** entrada no registro: uma compra é uma decisão do usuário, não doze.
- [x] 10.2 `update_transaction`, com a recusa nomeando "mais de uma perna monetária" para transferência e pagamento. A regra é de `UpdateTransactionUseCase` (task 3.10) e chega ao agente nas palavras do domínio; a ferramenta não a repete. Editar o valor para **zero** também é recusado, nomeando `delete_transaction` — o que a 12.4c vai exigir, deixado pronto aqui porque é a recusa da edição e não o eixo de permissão.
- [x] 10.3 `create_account`, `update_account`; `create_card`, `update_card`. A moeda é obrigatória nos dois `create_*` e não tem padrão — a mesma razão pela qual o use case não tem: nada pode criar conta em moeda que ninguém escolheu. Em `update_account` a moeda é recusada pelo domínio; em `update_card` ela é **indizível**, porque um cartão não a declara.
- [x] 10.4 `create_category`, `update_category`; `create_budget`, `update_budget`. O tipo de categoria e a moeda de orçamento estão fora dos `update_*`, e a descrição diz por quê. Ícone não entra na superfície: o que o agente cria nasce com o padrão do app.
- [x] 10.5 `create_recurring`, `update_recurring` (o mesmo `SaveRecurringUseCase`, id zero ou não); `create_installment`, `update_installment`. **`SaveRecurringUseCase` passou a devolver o `Recurring` gravado** e `IRecurringRepository.insert` a devolver a identidade: sem isso `create_recurring` não teria o que responder e o registro de atividade não teria como referenciar o que criou.
- [x] 10.6 `create_invoice`, `delete_invoice` — esta recusando quando a fatura não é futura. `Invoice.Status.isDeletable` é a regra e `DeleteFutureInvoiceUseCase` a aplica; a recusa diz "only future or retroactive invoices can be deleted", nas palavras do próprio domínio.
- [x] 10.7 As demais ferramentas do eixo "apagar": transação, conta, categoria, orçamento, **cartão, recorrência e parcelamento**. Com `delete_invoice` (10.6), são as **oito** que o eixo concede: `mcp-permissions` define "apagar" como *remover definitivamente*, sem qualificar entidade, e enumera em "registrar e editar" apenas *criar e alterar* — de modo que toda `delete_*` cai neste eixo. As três últimas não constavam de task alguma, e sem elas a superfície declarada teria ferramenta que nenhum passo constrói. Cada uma resolve o que vai sumir **antes** de removê-lo, para que o registro possa dizer o que foi, e declara o que foi junto (as parcelas de um plano, os lançamentos de uma fatura futura).
- [x] 10.8 Teste de que apagar uma categoria com lançamentos é recusado **e** que a recusa nomeia o arquivamento. As duas metades: o motivo é `RetireError.HAS_TRANSACTIONS` inteiro, e `try_instead` é `archive_entity`, decidido por `retireActionOf` — o mesmo dono que as telas consultam. O mesmo formato vale para conta e cartão que se moveram.
- [x] 10.9 Teste de que nenhuma ferramenta de registro escreve em repositório sem passar pelo use case dono. `RegistrationToolsGoThroughUseCasesTest`, no molde do guarda dos ViewModels: varre as fontes do pacote `mcp/tool` atrás de verbo de escrita sobre repositório ou DAO — **sem lista de exceções, porque não há exceção legítima** — e exige que toda ferramenta `CHANGES` segure o use case por onde escreve. Verificado falhando: uma ferramenta forjada chamando `categoryRepository.insert` derrubou as duas metades.

## 11. Família Operações — permissão "operar"

- [x] 11.1 `pay_invoice` sobre `PayInvoicePaymentUseCase` — **não** `PayInvoiceUseCase`, que só marca o status. A escolha ficou guardada por teste **estrutural** além do de comportamento: `RegistrationToolsGoThroughUseCasesTest` falha quando alguma ferramenta declara `PayInvoiceUseCase` no construtor, e falha também quando nenhuma declara `PayInvoicePaymentUseCase` — sem a segunda metade a primeira passaria sobre uma superfície que não paga fatura nenhuma. Verificado falhando com uma ferramenta forjada.
- [x] 11.2 Teste que paga uma fatura e exige as duas consequências: a transação de pagamento existe (perna na conta pagadora, perna do cartão com a dimensão da fatura) e o saldo da conta caiu de 1.000,00 para 700,00. O status vem **por último e de propósito** — é a única das três que a implementação errada também produziria. Verificado falhando: com o lançamento removido do caminho de pagamento, o teste morre em *"no payment was posted — the invoice was marked paid and the money never left"*.
- [x] 11.3 `advance_invoice_payment`, `close_invoice`, `open_invoice`, `reopen_invoice`, `adjust_invoice`. `open_invoice` **promove** a fatura já declarada para o mês em vez de duplicá-la, e é isso que o teste exige — a contagem de faturas do cartão não muda. `adjust_invoice` é exercitado duas vezes na mesma data: a segunda correção pousa no alvo em vez de acumular sobre a primeira.
- [x] 11.4 `adjust_balance`, `transfer`, `set_default_account`. Nenhuma delas ecoa o argumento: o saldo, a conta e a fatura da resposta são lidos de volta depois da operação, porque o efeito de uma operação é um *estado* e devolver o alvo faria a resposta ser verdadeira por construção.
- [x] 11.5 `confirm_recurring`, `skip_recurring`. **A ausência de título e categoria é resolvida na ferramenta, por pré-preenchimento.** D8 deixou os dois significando *nada* dentro do use case — correto para a tela, que entrega o formulário já preenchido — e uma ferramenta sem formulário que repassasse `null` lançaria o ciclo da Netflix sem título e sem categoria, relatando "confirmei". Então `title` e `category_id` não citados são preenchidos do template e passados **explicitamente**; apagar continua exprimível, porque a tela apaga: `title` vazio e `category_id` igual a `0` são as duas únicas formas de um `null` chegar ao use case daqui. Verificado falhando: com o repasse ingênuo, o ciclo é gravado sem título.
- [x] 11.6 `archive_entity` e `unarchive_entity`, genéricas sobre conta, cartão, categoria e recorrência. A prosa e o domínio do parâmetro saem da **mesma lista** (`ARCHIVABLE`), interpolada na descrição e no `enum` do schema, de modo que divergir entre as duas é impossível e não apenas desaconselhado; o teste compara os dois conjuntos nos dois sentidos. As quatro entidades têm `Archive`/`Unarchive`; **orçamento e parcelamento não têm, e não é retenção da superfície** — o app inteiro não arquiva nenhum dos dois (não existe `ArchiveBudgetUseCase` nem `isArchived` em `Budget`), então não há capacidade a declarar em `exclusions`. `type: "budget"` é recusado nomeando os quatro aceitos.
- [x] 11.7 Teste de que uma transferência entre contas de moedas diferentes colhe a taxa da própria operação, sem recebê-la como parâmetro. Duas metades: nenhum parâmetro do schema de `transfer` menciona taxa — não há onde informá-la —, e mesmo assim o acervo aprende `BRL→USD = 100/550` na data da operação, com origem `DERIVED`, colhida pelo `HarvestExchangeRateUseCase` **real**. As duas pontas declaradas (550,00 e 100,00) são asseridas como diferentes da taxa, e cada moeda do lançamento fecha em zero com o resíduo que a fronteira de escrita pôs na conta de conversão.

## 12. Permissões (`mcp-permissions`)

- [x] 12.1 Os quatro eixos persistidos, com o estado inicial: servidor desligado e, ao ligar, apenas "ler".
- [x] 12.2 O `tools/list` filtrado pelo eixo — a ferramenta não anunciada não aparece.
- [x] 12.3 Recusa na execução de ferramenta cuja permissão não foi concedida, mesmo invocada pelo nome.
- [x] 12.4 `notifications/tools/list_changed` ao alterar um eixo, alcançando quem já está conectado.
- [x] 12.4a **Declarar as capacidades retidas no handshake** (D13): quais eixos estão concedidos, quais estão retidos por escolha do usuário, e onde conceder. Nomeia a capacidade, nunca as ferramentas — não é uma segunda lista por outro canal.
- [x] 12.4b Recusa de ferramenta invocada pelo nome distingue "não autorizado" de "não existe", com a mesma indicação de onde conceder.
- [x] 12.4c Editar o valor de um lançamento para zero é recusado, nomeando a remoção e o eixo que a autoriza — sem isso, o eixo "apagar" retido tem um substituto torto que deixa registros de R$ 0,00 no histórico.
- [x] 12.4d Teste da situação que a simulação flagrou: com "apagar" retido, a sessão carrega a informação de que remover existe e depende de autorização.
- [x] 12.5 Teste dos quatro eixos como independentes: conceder um não concede outro.
- [x] 12.6 Teste de que só-leitura anuncia exatamente as ferramentas das famílias 1 e 2.

## 13. Tela de configurações (`mcp-server`, `platform-adaptive-features`)

- [x] 13.1 Seção de configurações **entre as integrações**, alcançável a partir das configurações do app. Com o servidor desabilitado, ela apresenta o que ele é e o interruptor — e nada mais a decidir antes de ligar. Habilitado, revela permissões, endereço, token (oculto por padrão) e instruções. O grupo **Integrações** nasceu em `SettingsScreen` com um item só, e `McpUiState.showsDetails` é o dono único da ordem — endereço, token, permissões e instruções pendem dele. **Uma exceção deliberada:** o registro de atividade aparece com o servidor desligado *se não estiver vazio*, porque desligar não apaga o que um agente fez e o rastro é o único lugar onde a autoria aparece; numa instalação que nunca subiu servidor ele está vazio, então a primeira visita continua sendo o que o servidor é e o interruptor.
- [x] 13.2 Os quatro interruptores de permissão, com o texto que diz o que cada eixo autoriza. Um `when` sobre `McpPermissionAxis` resolve título e descrição, de modo que um eixo novo não compila sem texto.
- [x] 13.2a Estado do servidor visível e verdadeiro na tela enquanto ela estiver aberta — inclusive uma queda posterior ao início. `McpViewModel` coleta `McpServerController.state`; nada na tela é derivado do interruptor. Teste: `Running` → `Failed` depois do início, com o interruptor ainda ligado, e a tela deixando de dizer "no ar".
- [x] 13.2b Cada eixo informa quantas ferramentas concede, e o retido quantas retém — um interruptor cujo efeito não é dito é concedido às cegas. O número vem de `McpServerController.toolCountByAxis`, que lê `McpSurface.toolCountByAxis` — a mesma contagem que o socket anuncia, e não uma segunda.
- [x] 13.2c Cliente conectado: a tela distingue "habilitado" de "há alguém do outro lado agora", e oferece encerrar as sessões. Duas linhas separadas — o socket e as sessões —, e o botão de encerrar só existe quando há o que encerrar.
- [x] 13.2d Atividade recente na seção, com acesso ao histórico completo e à limpeza. Cada entrada alcança o lançamento que descreve. `Reference.toTarget()` é o dono único do destino e não tem ramo `null`: um `Kind` novo sem destino não compila. O lançamento é alcançado **por identidade** (o mesmo detalhe que toda lista do app abre); as demais entidades caem na seção que as guarda, que é o quanto a referência permite — a fatura não carrega o cartão que a endereça. **A referência sem chave estrangeira** é tratada onde ela morde: antes de oferecer a porta para um lançamento, a seção pergunta ao razão se ele ainda existe, e uma entrada cujo lançamento sumiu continua listada, legível e verdadeira, dizendo apenas que o que ela criou não está mais lá.
- [x] 13.2e Porta como campo editável, com o erro de porta ocupada aparecendo **no campo** — não num alerta solto. O que o usuário digita é rascunho: validado no campo e aplicado só sob ação explícita, porque uma porta aplicada por tecla religaria o socket em "8", "84" e "847" a caminho de 8477. `McpServerState.Failed` vira texto do campo enquanto o rascunho ainda nomear a porta que falhou, e `ApplyPort` lê a mesma regra que o campo exibe (`canApplyPort`) em vez de escrever uma segunda.
- [x] 13.3 Instruções de conexão copiáveis, não específicas de um cliente, incluindo a nota sobre clientes que só falam stdio. Endereço e token têm cópia própria, e há a cópia do bloco inteiro — transporte, URL e cabeçalho `Authorization`, no vocabulário do protocolo e não no de um cliente.
- [x] 13.4 A nota de que o servidor só existe com o app aberto, dita nos dois estados da seção: é o que o servidor **é**, e não uma consequência de estar ligado.
- [x] 13.5 Classificar a feature como `desktop-only` no catálogo de destinos e ocultar o ponto de entrada nas demais plataformas. O eixo virou **simétrico e único**: `NavDestination.mobileOnly: Boolean` deu lugar a `onlyOn: FeaturePlatform?`, com `mobileOnly`/`desktopOnly`/`isOffered` derivados — dois booleanos independentes admitiriam a quarta combinação, que não quer dizer nada. `FeaturePlatform.isCurrent` é o dono da regra, e é ele que o ponto de entrada do MCP consulta: a feature não está no catálogo (ela se alcança pelas integrações), e a spec prevê exatamente esse caso — a ocultação é sobre a feature, não sobre a afordância. As duas projeções do catálogo passaram a usar `isOffered`, o que de passagem corrige a grade de ações rápidas, que escondia o que é `mobileOnly` **em toda plataforma**, mobile inclusive.
- [x] 13.6 Teste de que o catálogo distingue `mobile-only` de `desktop-only` e de que uma janela estreita no desktop não oculta a feature. `PlatformAxisTest`, em `jvmTest` de propósito: o assunto inteiro é o que `isDesktop` decide, e em `commonTest` ele afirmaria uma coisa no JVM e a oposta no Android. A ortogonalidade é dita onde pode ser: `isOffered` não recebe argumento algum e lê só `FeaturePlatform`, então nenhum chamador consegue fazer a resposta depender do tamanho da janela.
- [x] 13.7 Chaves de string em `values/strings.xml` **e** `values-en/strings.xml` — uma chave presente em um só é um defeito. 56 chaves novas nos dois arquivos (806 → 862 em cada), e um guarda que fecha o furo: `StringResourceParityTest` compara os dois conjuntos nos dois sentidos e recusa chave declarada duas vezes. O compilador não alcança isso — `Res.string.x` resolve contra o arquivo padrão, então uma chave só em português compila, roda e mostra português a um leitor de inglês.

## 14. Verificação

- [x] 14.1 `./gradlew jvmTest` verde, com a saída lida e reportada. Executado com `--rerun-tasks`, sem nenhuma task reaproveitada de cache: **384 tasks executadas, 1597 testes, 0 falhas, 0 erros, 0 pulados**, em 23 módulos com resultado. `feature/mcp/impl` responde por 196 deles, todos dirigidos pelo protocolo sobre socket real contra banco real. A linha de base do início do trabalho era 1252 em 19 módulos.
- [x] 14.2 `./gradlew :app:desktop:run` com um cliente MCP real conectado: exercitar uma pergunta, uma listagem, um registro e uma operação, e confirmar que a UI aberta reage a cada escrita. Feito contra o app montado, pelo protocolo (`initialize` → `tools/list` → `tools/call`) em socket real: `tools/list` devolveu as **56**; `get_balance` trouxe o perímetro declarado; `create_account`/`create_transaction` escreveram; `list_transactions` concordou com a pergunta; `transfer` e `pay_invoice` moveram dinheiro. O razão fechou em zero nas duas moedas e o registro ganhou uma linha por escrita e **nenhuma** pelas leituras. **A reatividade foi verificada pelo dono do código, na tela, enquanto o agente escrevia** — a metade que nenhum teste alcança. Dois defeitos de domínio saíram daqui, ambos preexistentes e corrigidos: o pagamento que era recusado depois de já ter movido o dinheiro, e a fatura retroativa que o caminho de pagamento recusava contra o próprio domínio.
- [ ] 14.2a **Percorrer a jornada inteira, na ordem, num app que nunca teve o servidor**: abrir e confirmar que nada escuta; achar a seção nas integrações; ver desabilitado; habilitar e ver subir com permissões e instruções; registrar um cliente pelas instruções da própria tela; fechar e reabrir o app e confirmar que o servidor voltou sozinho; lançar uma transação pelo agente e ver aparecer na tela aberta. Reportar cada passo, e não um "funciona" agregado.
- [x] 14.3 `docs/mcp-tool-surface.md` conferido linha a linha contra o código e corrigido; onde os dois divergiam, o código venceu. As quatro tabelas passaram a trazer os parâmetros do `inputSchema` real — identidades com sufixo `_id`, e o `?` seguindo o `required` de cada schema — e o use case que a ferramenta de fato segura: `update_transaction` sobre `UpdateTransactionUseCase` (3.10) e não sobre o repositório, `create_invoice` sobre `CreateInvoiceUseCase` e não get-or-create, `list_transactions` sobre `getAllTransactions()` recortado em memória (`observeTransactionsBy` só filtra por um dia), `get_spending_breakdown` sobre os dois use cases de categoria. A família 1 ganhou o cabeçalho que lhe faltava — a tabela caía sob a seção de `ConfirmRecurringUseCase` —, `list_budgets` deixou de prometer progresso (9.7), os defaults do agregado viraram **seis** (2.8), "as quatro linhas marcadas" virou **oito** e "sete use cases a nascer" virou **oito**. As contagens por eixo (20/15/8/13) foram reconferidas contra `McpToolName`/`McpSurface` e estão certas.
- [x] 14.4 O que ficou sem verificação automatizada está nominado em **`docs/mcp-tool-surface.md` § "O que a suíte não verifica"**, com o motivo de cada limite, ao lado da superfície que descreve. São oito: os testes de ferramenta não exercitam nenhum `Impl` de escrita (a regra de dependência proíbe, e `AgentWorld*` reconstrói cada use case sobre o razão real — provam composição, delegação e recusa, não a regra própria de `AddInstallmentUseCaseImpl` nem de `AddCreditCardUseCaseImpl`); ninguém conta consultas, então a forma em lote não é verificada como sendo uma leitura; os testes de migração rodam só no JVM sobre `BundledSQLiteDriver`, como todos os do projeto; `Host`/`Origin` são exercitados por cliente HTTP e nunca por navegador, e um pedido sem `Origin` é aceito de propósito; o teste de loopback usa os endereços externos desta máquina como substitutos de uma segunda; uma sessão abandonada segue contada, porque só o `onClose` a remove; `list_transactions(nature=…)` corta a lista sem mover os totais e a mitigação é declarativa; e nada do que se desenha na tela é testado — não há infraestrutura de teste de Compose no projeto —, o que deixa oito itens do grupo 13 dependendo de olho humano. A nota fecha apontando para 14.2 e 14.2a, que existem por causa dessa lista.
