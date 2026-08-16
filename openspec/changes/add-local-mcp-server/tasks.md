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

- [ ] 6.1 Ciclo de vida ligado ao processo: `:app:desktop` obtém o controlador via Koin, inicia com a janela e encerra no fechamento, liberando a porta.
- [ ] 6.1a **Persistir a habilitação** e subir sozinho nos inícios seguintes, sem visita à tela. Teste: habilitar, encerrar, reabrir, e o servidor está no ar; desabilitar, reabrir, e não está. O usuário habilita **uma vez**.
- [ ] 6.1b Teste do estado de estreia: app atualizado e aberto pela primeira vez não sobe nada e não escuta nada, porque não houve escolha a persistir.
- [ ] 6.1c **Falha ao subir é dita ao usuário** na interface do app, com o motivo e o caminho — não silenciosa, e sem que o estado exibido diga "no ar" quando não está. O aviso alcança quem não está na tela de configurações.
- [ ] 6.2 Escuta restrita à interface de loopback. Teste que confirma que o socket não aceita conexão de interface externa.
- [ ] 6.2a Validação de `Host`/`Origin` e `DnsRebindingProtectionConfig` (D11) — a defesa contra uma página web aberta no navegador do usuário alcançar `127.0.0.1`. Teste com `Origin` de terceiro sendo recusado.
- [ ] 6.3 Geração, persistência e regeneração do token; recusa de requisição sem token ou com token que não confere, antes de qualquer execução.
- [ ] 6.4 Teste de que o token sobrevive ao reinício e de que regenerar invalida o anterior.
- [ ] 6.5 Porta fixa (padrão `8477`), editável e persistida. Quando ocupada, o servidor **não sobe** e o estado observável diz qual porta está em uso — sem fallback silencioso, que quebraria a configuração já feita no cliente (D10).
- [ ] 6.6 Teste de que uma escrita feita pelo servidor emite invalidação e atinge um `Flow` observado, provando a reatividade de D1.
- [ ] 6.7 Gravar no registro toda escrita, operação e recusa — e **nenhuma** leitura. Teste que exercita uma sequência de consultas e exige registro vazio, e outro que repete a mesma escrita e exige duas entradas lado a lado.
- [ ] 6.8 Expor as sessões em curso no estado observável do controlador, e a ação de encerrá-las.

## 7. Apresentação para o agente (`presentation-mapping`, `mcp-tool-surface`)

- [ ] 7.1 DTOs planos da superfície: transação, conta, cartão, fatura, parcelamento, categoria, orçamento, recorrência. Nenhum campo de tipo de domínio, no máximo o identificador.
- [ ] 7.2 O tipo de figura monetária da superfície: valor, moeda, e — quando consolidada — a marca de consolidação e a data da taxa.
- [ ] 7.3 Mapper de transação que consome `deriveTransactionLabel`, `TransactionPerspective` e `figureLegUnder`, sem re-derivar nenhum dos três.
- [ ] 7.4 Redução por `ConsolidateMoneyUseCase`, e a resposta de ausência de taxa (D16): decomposição por moeda e a limitação dita explicitamente.
- [ ] 7.5 Teste que inspeciona os DTOs da superfície e falha se algum declarar campo de tipo de domínio.
- [ ] 7.6 Teste que apresenta a mesma transação pela tela e pela superfície do agente e exige o mesmo rótulo, a mesma perna lida e a mesma ponta denominando a figura.
- [ ] 7.7 O envelope de erro: motivo da recusa, e o nome da operação alternativa quando o domínio oferece uma.
- [ ] 7.8 **Teste que fecha a superfície**: compara o conjunto de ferramentas anunciado com a lista declarada e falha nos dois sentidos — uma ferramenta a mais entrou sem decisão, uma a menos sumiu sem ninguém notar. No molde de `AgentInstructionsTest`, que já faz isso com as instruções de agente e os arquivos que elas nomeiam.
- [ ] 7.9 Teste de que nenhuma ferramenta escreve taxa de câmbio, altera a moeda base, ou reconfigura o servidor e as próprias permissões — as três exclusões cujo motivo é dano assimétrico e silencioso, não escopo.

## 8. Família Perguntas — permissão "ler"

- [ ] 8.1 `get_balance`, `get_month_summary`.
- [ ] 8.2 `get_category_spending`, `get_category_income`, `get_spending_breakdown` — com o grupo sem categoria como linha própria.
- [ ] 8.3 `get_budget_progress` — compondo as três listas que `CalculateBudgetProgressUseCase` exige.
- [ ] 8.4 `get_pending_recurring`, `get_card_overview`, `get_report_stats`.
- [ ] 8.4a `get_net_worth` — ASSET menos LIABILITY, por moeda e consolidado. Hoje `IEntryRepository` não expõe essa leitura *"porque não tem consumidor em produção"*; o agente é esse consumidor, e `EntryDao.netWorthCents()` já existe agrupado por moeda.
- [ ] 8.4b Comparação entre períodos em `get_month_summary` (parâmetro do mês a comparar), devolvendo as variações já calculadas — sem deixar a subtração para o agente.
- [ ] 8.4c Toda figura agregada declara o seu perímetro, na descrição e na resposta. `get_balance` diz que a dívida de cartão não está descontada e nomeia `get_net_worth`.
- [ ] 8.4d Todo período responde se está em andamento e até que data está apurado; uma comparação marca qual dos lados é o incompleto.
- [ ] 8.5 Teste de que o total de um mês com transferência entre contas próprias e pagamento de fatura não os inclui.
- [ ] 8.6 Teste de que uma figura que atravessa contas em moedas diferentes traz decomposição, consolidado e data da taxa.

## 9. Família Catálogo — permissão "ler"

- [ ] 9.1 `list_transactions` com os filtros de período, conta, categoria, cartão e natureza, e a paginação.
- [ ] 9.2 O agregado da listagem, vindo do razão, mais `matching` e `returned`. Teste de que ele não é a soma da página.
- [ ] 9.2a Ordem total e determinística, com desempate estável quando a data empata, e a opção de ordenar por **ordem de registro** — sem ela, "o último que eu registrei" não é respondível, já que a data tem resolução de dia. Teste de que paginar não repete nem omite item.
- [ ] 9.6a Cada descrição de `list_invoices` e `get_card_overview` diz o seu recorte; a escolha entre as duas não deve exigir chamar as duas e comparar as saídas.
- [ ] 9.3 A alternância de vocabulário por perspectiva: sem conta na chamada, natureza; com conta, direção. Teste com uma transferência, nos dois modos.
- [ ] 9.4 `get_transaction` com todas as pernas monetárias e a taxa praticada quando elas cruzam moedas.
- [ ] 9.5 `list_accounts`, `list_cards`, `list_categories` — cada uma com a figura que a acompanha.
- [ ] 9.6 `list_invoices` usando a leitura em lote de devido, `get_invoice` com janela e extrato.
- [ ] 9.7 `list_installments`, `list_budgets`, `list_recurring`.

## 10. Família Registro — permissões "registrar e editar" e "apagar"

- [ ] 10.1 `create_transaction` sobre `RegisterTransactionUseCase`; teste de que um formulário parcelado produz as N transações.
- [ ] 10.2 `update_transaction`, com a recusa nomeando "mais de uma perna monetária" para transferência e pagamento.
- [ ] 10.3 `create_account`, `update_account`; `create_card`, `update_card`.
- [ ] 10.4 `create_category`, `update_category`; `create_budget`, `update_budget`.
- [ ] 10.5 `create_recurring`, `update_recurring`; `create_installment`, `update_installment`.
- [ ] 10.6 `create_invoice`, `delete_invoice` — esta recusando quando a fatura não é futura.
- [ ] 10.7 As quatro ferramentas do eixo "apagar": transação, conta, categoria, orçamento.
- [ ] 10.8 Teste de que apagar uma categoria com lançamentos é recusado **e** que a recusa nomeia o arquivamento.
- [ ] 10.9 Teste de que nenhuma ferramenta de registro escreve em repositório sem passar pelo use case dono.

## 11. Família Operações — permissão "operar"

- [ ] 11.1 `pay_invoice` sobre `PayInvoicePaymentUseCase` — **não** `PayInvoiceUseCase`, que só marca o status.
- [ ] 11.2 Teste que paga uma fatura e exige as duas consequências: a transação de pagamento existe e o saldo da conta pagadora mudou.
- [ ] 11.3 `advance_invoice_payment`, `close_invoice`, `open_invoice`, `reopen_invoice`, `adjust_invoice`.
- [ ] 11.4 `adjust_balance`, `transfer`, `set_default_account`.
- [ ] 11.5 `confirm_recurring`, `skip_recurring`.
- [ ] 11.6 `archive_entity` e `unarchive_entity`, genéricas sobre conta, cartão, categoria e recorrência.
- [ ] 11.7 Teste de que uma transferência entre contas de moedas diferentes colhe a taxa da própria operação, sem recebê-la como parâmetro.

## 12. Permissões (`mcp-permissions`)

- [ ] 12.1 Os quatro eixos persistidos, com o estado inicial: servidor desligado e, ao ligar, apenas "ler".
- [ ] 12.2 O `tools/list` filtrado pelo eixo — a ferramenta não anunciada não aparece.
- [ ] 12.3 Recusa na execução de ferramenta cuja permissão não foi concedida, mesmo invocada pelo nome.
- [ ] 12.4 `notifications/tools/list_changed` ao alterar um eixo, alcançando quem já está conectado.
- [ ] 12.4a **Declarar as capacidades retidas no handshake** (D13): quais eixos estão concedidos, quais estão retidos por escolha do usuário, e onde conceder. Nomeia a capacidade, nunca as ferramentas — não é uma segunda lista por outro canal.
- [ ] 12.4b Recusa de ferramenta invocada pelo nome distingue "não autorizado" de "não existe", com a mesma indicação de onde conceder.
- [ ] 12.4c Editar o valor de um lançamento para zero é recusado, nomeando a remoção e o eixo que a autoriza — sem isso, o eixo "apagar" retido tem um substituto torto que deixa registros de R$ 0,00 no histórico.
- [ ] 12.4d Teste da situação que a simulação flagrou: com "apagar" retido, a sessão carrega a informação de que remover existe e depende de autorização.
- [ ] 12.5 Teste dos quatro eixos como independentes: conceder um não concede outro.
- [ ] 12.6 Teste de que só-leitura anuncia exatamente as ferramentas das famílias 1 e 2.

## 13. Tela de configurações (`mcp-server`, `platform-adaptive-features`)

- [ ] 13.1 Seção de configurações **entre as integrações**, alcançável a partir das configurações do app. Com o servidor desabilitado, ela apresenta o que ele é e o interruptor — e nada mais a decidir antes de ligar. Habilitado, revela permissões, endereço, token (oculto por padrão) e instruções.
- [ ] 13.2 Os quatro interruptores de permissão, com o texto que diz o que cada eixo autoriza.
- [ ] 13.2a Estado do servidor visível e verdadeiro na tela enquanto ela estiver aberta — inclusive uma queda posterior ao início.
- [ ] 13.2b Cada eixo informa quantas ferramentas concede, e o retido quantas retém — um interruptor cujo efeito não é dito é concedido às cegas.
- [ ] 13.2c Cliente conectado: a tela distingue "habilitado" de "há alguém do outro lado agora", e oferece encerrar as sessões.
- [ ] 13.2d Atividade recente na seção, com acesso ao histórico completo e à limpeza. Cada entrada alcança o lançamento que descreve.
- [ ] 13.2e Porta como campo editável, com o erro de porta ocupada aparecendo **no campo** — não num alerta solto.
- [ ] 13.3 Instruções de conexão copiáveis, não específicas de um cliente, incluindo a nota sobre clientes que só falam stdio.
- [ ] 13.4 A nota de que o servidor só existe com o app aberto.
- [ ] 13.5 Classificar a feature como `desktop-only` no catálogo de destinos e ocultar o ponto de entrada nas demais plataformas.
- [ ] 13.6 Teste de que o catálogo distingue `mobile-only` de `desktop-only` e de que uma janela estreita no desktop não oculta a feature.
- [ ] 13.7 Chaves de string em `values/strings.xml` **e** `values-en/strings.xml` — uma chave presente em um só é um defeito.

## 14. Verificação

- [ ] 14.1 `./gradlew jvmTest` verde, com a saída lida e reportada.
- [ ] 14.2 `./gradlew :app:desktop:run` com um cliente MCP real conectado: exercitar uma pergunta, uma listagem, um registro e uma operação, e confirmar que a UI aberta reage a cada escrita.
- [ ] 14.2a **Percorrer a jornada inteira, na ordem, num app que nunca teve o servidor**: abrir e confirmar que nada escuta; achar a seção nas integrações; ver desabilitado; habilitar e ver subir com permissões e instruções; registrar um cliente pelas instruções da própria tela; fechar e reabrir o app e confirmar que o servidor voltou sozinho; lançar uma transação pelo agente e ver aparecer na tela aberta. Reportar cada passo, e não um "funciona" agregado.
- [ ] 14.3 Conferir `docs/mcp-tool-surface.md` contra o que foi construído e corrigir as divergências — o documento é material de exploração e envelhece.
- [ ] 14.4 Registrar o que ficou sem verificação automatizada, nominalmente, em vez de deixar a suíte sugerir cobertura que não existe.
