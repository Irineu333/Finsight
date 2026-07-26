## Context

Esta é a quarta perna de uma série: `unarchive-accounts`, `unarchive-and-redesign-categories` e `unarchive-credit-cards` já trouxeram conta, cartão e categoria para o ciclo de vida descrito em `account-lifecycle`. Recorrência ficou de fora, e o *Purpose* daquela spec ainda nomeia apenas três fachadas.

Estado atual relevante:
- `Recurring.isActive` (`core/model`) espelha `recurring.isActive`; `StopRecurringUseCase`/`ReactivateRecurringUseCase` viram esse flag. **Já é um arquivamento**, com outro nome — não nasce flag nova nesta mudança.
- `DeleteRecurringUseCase` é um `catch { repository.delete(...) }` **sem guarda alguma**.
- `RecurringRepository.delete()` já é atômico e já chama `RecurringDao.detachTransactions` (anula `transactions.recurringId`/`recurringCycle`).
- `recurring_occurrences` tem **dois** foreign keys, ambos CASCADE: `recurringId` → `recurring.id` e `transactionId` → `transactions.id`.
- `budgets` **não declara foreign key nenhuma**; `budgets.recurringId` nunca é anulado.
- A tela tem dois dropdowns na topbar: `RecurringFilter { ALL, INCOME, EXPENSE }` × `RecurringStatusFilter { ACTIVE, INACTIVE, ALL }`.
- `ViewRecurringModal` oferece "Parar" com `Icons.Default.Delete`, e "Excluir" apenas quando `!isActive`.
- `core/ui` já expõe `RetireAction { DELETE, ARCHIVE }` (com rótulo e ícone próprios), `retireActionOf(mustPreserve)` e `OutlinedActionButton` — os três já consumidos por conta, cartão e categoria.

## Goals / Non-Goals

**Goals:**
- Recorrência passa a arquivar o que tem história e a recusar apagá-lo, com dono único da decisão.
- Vocabulário alinhado: arquivar/desarquivar/arquivada em domínio, use case e UI.
- Um seletor de filtro no lugar de dois, no mesmo lugar e forma da tela de Categorias.
- `ConfirmRecurringUseCase` deixa de poder duplicar um lançamento no razão.

**Non-Goals:**
- **Renomear a coluna** `recurring.isActive`. Ver D1.
- Generalizar `CategoryRetirability`/`RetireError` para servir recorrência. Ver D4.
- Reparar bancos que já divergiram antes da correção de atomicidade. Ver D8.
- Dívida transversal registrada no `proposal.md` (guard não-derivado do `AccountsViewModel`, `?: 0.0` do `CalculateBudgetProgressUseCase`, `ArchivedBadge` compartilhado, varredura completa em `getRecurringById`).

## Decisions

### D1 — A coluna continua `isActive`; o mapper inverte
O domínio e a UI passam a falar `Recurring.isArchived`; `RecurringMapper` faz `isArchived = !entity.isActive` na ida e o inverso na volta. Renomear a coluna exigiria migração (rename + inversão de default), e a alteração é pequena demais para justificá-la agora.

A inversão fica **confinada ao mapper** — que já é a camada de tradução, pois já colapsa `TransactionType.ADJUSTMENT → EXPENSE`. Consequência deliberada: quando a migração vier, ela toca `RecurringEntity`, duas linhas do mapper e o SQL/testes de migração que nomeiam a coluna (`Database.kt`, `V7Schema.kt`, `Migration3To4Test`, `Migration2To3Test`), e **nada** de domínio, use case, ViewModel ou tela.

Um KDoc no campo da entidade registra por que o nome diverge do significado — sem ele, o próximo leitor lê como bug.
- *Alternativa considerada:* manter `isActive` também no domínio e trocar só o texto da UI. Rejeitada: a mentira atravessaria o domínio inteiro, e a spec exige que o use case diga o que o botão diz.
- *Alternativa considerada:* migrar agora. Rejeitada por custo desproporcional ao ganho; registrada como dívida com dono.

### D2 — "Em uso" é **ter transação gerada** ou **ter orçamento apontando**, não ter ocorrência
O guard consulta `transactions.recurringId` e `budgets.recurringId`. **Ocorrências não entram.**

Duas razões, ambas verificadas no código:

1. **Ocorrência não é mais forte que transação.** `recurring_occurrences.transactionId` é CASCADE, então apagar a transação já destrói a ocorrência CONFIRMED dela — o histórico que um guard por ocorrência protegeria já é destrutível hoje por exclusão normal de transação. E `ConfirmRecurringUseCase` escreve a transação e a ocorrência em transações de banco separadas (corrigido em D7), então o estado *transação sem ocorrência* é alcançável: ali, um guard por ocorrência liberaria apagar um template que gerou história real no razão.
2. **Bloquear por SKIPPED é recusar o meramente inapropriado.** Um skip não escreve `transactionId`, não gera entry e não move dinheiro — é um bilhete dizendo "neste mês não teve", sem sentido depois que o template some. `account-lifecycle` é explícita: *"O domínio SHALL recusar apenas o que violaria uma invariante, e MUST NOT recusar o que é meramente inapropriado."* Um usuário que criou um template, pulou um mês e o abandonou ficaria com uma recorrência permanentemente inapagável.

Consequência aceita e documentada: apagar uma recorrência sem transações descarta, via CASCADE, as suas linhas SKIPPED.

A justificativa da trava é **própria da fachada**, e não a do razão. Nenhuma entry referencia uma recorrência: `transactions.recurringId` não tem FK, nenhuma leitura do razão o consulta, e `RecurringDao.detachTransactions` já o anula na remoção. Apagar uma recorrência não deixa lançamento órfão algum — destrói o *vínculo* entre os lançamentos e o template, o registro dos ciclos e a ligação do orçamento. Recorrência é a primeira fachada deste ciclo de vida cuja preservação não decorre da integridade referencial do plano de contas, e a spec diz isso em vez de tomar emprestada uma justificativa que não transfere.

Efeito colateral do guard: como a remoção passa a ser recusada exatamente quando alguma transação nomeia o template, o `UPDATE` de `detachTransactions` nunca mais casa uma linha. Ele **permanece** como defesa em profundidade, com KDoc registrando que por construção já não é alcançável.

O guard de orçamento entra por razão **oposta** à de categoria. Em categoria, `HAS_BUDGET` existe porque o FK CASCADE faria algo ruim silenciosamente; em recorrência, ele precisa existir porque **não há FK alguma** em `budgets` — nada mais pega. Sem ele, um orçamento PERCENTAGE fica apontando para um id morto e `CalculateBudgetProgressUseCase` cai no `?: 0.0`, zerando o limite sem erro.
- *Alternativa considerada:* guard por ocorrências (confirmadas ou puladas). Rejeitada pelos dois motivos acima.
- *Alternativa considerada:* guard por ocorrências **e** transações. Rejeitada: só acrescenta o bloqueio por SKIPPED, que é justamente o caso indevido.

### D3 — `ResolveRecurringRetirabilityUseCase` é o dono único
Espelha `ResolveCategoryRetirabilityUseCase`: um use case resolve `RecurringRetirability`, consumido tanto por `DeleteRecurringUseCase` (que recusa) quanto pelo `ViewRecurringViewModel` (que decide o que oferecer). Nenhuma tela re-deriva a regra.

Isso é o que impede a deriva que `RetireAction` documenta — e que existe hoje em `AccountsViewModel`, onde o booleano de oferta é derivado só de `hasEntries` enquanto `DeleteAccountUseCaseImpl` também recusa por `hasRecurringForAccount`, fazendo a tela oferecer um Excluir que o domínio recusa. Recorrência nasce sem esse defeito.

### D4 — Tipo e motivos próprios; `CategoryRetirability`/`RetireError` ficam intocados
`RecurringRetirability { Deletable | MustArchive(reason) }` com o seu próprio enum de motivos, ao lado de — não no lugar de — `CategoryRetirability`.

Três razões:
1. `CategoryRetirability` é consumido por **uma** fachada. Conta e cartão fazem os guards inline falando `AccountError`; não existe resolver para eles. Compartilhar promoveria um tipo de 1-de-4 para 2-de-4, não unificaria quatro.
2. Os motivos **não se sobrepõem**. Os de `RetireError` são justificados por FKs de categoria (`budget_categories.categoryId` CASCADE, `recurring.categoryId` SET_NULL) e nenhum deles pode ocorrer para uma recorrência; os de recorrência não podem ocorrer para uma categoria. Um enum compartilhado daria a cada `when` membros impossíveis — exatamente o que o KDoc de `AccountRetireOffer` diz ter evitado.
3. Os motivos **são** o conteúdo do container: sem eles, `Deletable | MustArchive` é um booleano. Compartilhar a casca enquanto cada motivo é privado é DRY aplicado a uma coincidência de forma, contra o critério do `CLAUDE.md`.

Efeito prático: as strings `retire_error_*` são redigidas para categoria ("Esta categoria tem lançamentos…"). Motivos próprios evitam neutralizá-las para "Este item…", que seria regressão de texto.

Isso **não** é passe livre para deixá-las como estão. `retire_error_has_recurring` e `account_error_has_recurring` mandam hoje "encerre-as antes de excluir", e encerrar nunca desbloqueou nada: `countByCategory`/`countByAccount`/`countByCreditCard` contam qualquer template, com ou sem flag. As duas são corrigidas aqui para nomear os caminhos que funcionam — reapontar ou excluir. Os counts **não** passam a filtrar arquivadas: arquivamento é reversível, e liberar a exclusão da categoria enquanto a recorrência está arquivada apenas adia a corrupção para o momento do desarquivamento, quando o template voltaria sem classificação.

Corrigir o texto de uma chave que existe em um idioma só é meia correção: as três `retire_error_*` nunca chegaram a `values-en`, e com elas outras cinco de categoria. Como esta mudança já está reescrevendo uma delas, as oito entram agora e a paridade entre os dois arquivos passa a ser total — verificável por diferença de chaves, não por leitura.
- *Alternativa considerada:* renomear `CategoryRetirability` → `Retirability` compartilhado. Rejeitada pelos três pontos acima.
- *Alternativa considerada:* `Retirability<out E>` genérico ou um supertipo `RetireReason`. Rejeitada: abstração nova, sem comportamento, para unificar justamente a parte que difere.
- *Reavaliar quando:* uma **terceira** fachada precisar do mesmo conjunto de motivos. Aí a generalização tem base empírica.

### D5 — Arquivar interrompe a geração; é o padrão, não exceção
Para conta, cartão e categoria, arquivar é puramente visibilidade. Para recorrência, o mesmo flag também governa a geração — `GetPendingRecurringUseCase` filtra por ele, e é isso que alimenta o card de pendências do dashboard.

A formulação que reconcilia, e que vai para a spec: **arquivar é sair de circulação; para uma recorrência, estar em circulação _é_ gerar ocorrências.** Não é exceção ao padrão, é o padrão aplicado a uma fachada cuja **presença em circulação é ativa** em vez de passiva. ("Em uso" fica reservado para o critério de retirabilidade do D2 — são coisas diferentes: uma recorrência pode estar em uso, no sentido de ter gerado lançamentos, e ao mesmo tempo arquivada.)

Há uma assimetria real que a spec registra em vez de esconder: para conta e cartão, o desarquivamento é inócuo porque o estado reaberto é garantidamente de saldo zero — nada ficou para trás. Para recorrência, os ciclos decorridos durante o arquivamento **não foram gerados e não são repostos**: a geração retoma do ciclo corrente. O desarquivar é inócuo por nada desfazer, não por repor o intervalo.

### D6 — Um seletor: `RecurringFilter { ACTIVE, EXPENSE, INCOME, ARCHIVED }`
Funde os dois enums, exatamente como `CategoryFilter`. Nove estados viram quatro; perdem-se "arquivadas de um tipo" e "todas incluindo arquivadas", perda que Categorias já aceitou conscientemente. "Ativas" em vez de "Todas" mantém o rótulo honesto.

`ACTIVE` lista as não arquivadas **sem seccionar por tipo** — diferente de Categorias. Em Categorias a seção por tipo herdou as tabs que a tela tinha; aqui a ordenação útil é a que já existe (por `createdAt`, e a arquivada some do recorte em vez de ir para o fim). Uma seção a mais seria estrutura sem demanda.

Junto vêm dois ajustes que Categorias já fez (D10 de lá): `Empty` passa a significar **banco sem recorrência alguma**, filtro vazio vira conteúdo com lista vazia e texto discreto, e o FAB deixa de depender de `Content` — hoje filtrar por Inativas num app sem inativas esconde o botão de criar.
- *Alternativa considerada:* manter os dois eixos separados. Rejeitada: o pedido é um seletor único, e é o padrão já estabelecido.

### D7 — A atomicidade mora num repositório, e o par vira `SAVEPOINT`
`ConfirmRecurringUseCase` escreve hoje em duas transações de banco independentes (`catch { createTransaction }.flatMap { catch { save(occurrence) } }`). A divergência *transação sem ocorrência* faz o mês reaparecer como pendente — `GetPendingRecurringUseCase` calcula "já tratados" só de ocorrências — e o guard de reentrada só recusa se existir ocorrência CONFIRMED, que é exatamente a linha que faltou escrever. Resultado: **lançamento duplicado no razão**.

O par passa a ser escrito dentro de um único `useWriterConnection { immediateTransaction { … } }`. Duas restrições determinam onde isso mora:
- `useWriterConnection` é usado **apenas em repositórios** neste projeto, nunca em use case. Logo a transação externa é de um repositório, e repositório injetando repositório já é idiomático aqui (`InvoiceRepository`, `BudgetRepository`, o próprio `RecurringRepository`).
- `TransactionRepository.createTransaction` **já abre a própria** `useWriterConnection { immediateTransaction { … } }`. O pool do Room é reentrante — uma chamada aninhada no mesmo contexto de corrotina reusa a conexão confinada e a transação interna vira `SAVEPOINT`, sem deadlock —, então o aninhamento é seguro e a escrita interna continua intacta.

A **checagem de reentrada entra junto**. Hoje o `require(existingOccurrence?.status != CONFIRMED)` é lido antes, fora de qualquer transação, e por isso é um TOCTOU: entre a leitura e a escrita cabe outra confirmação. O índice único `(recurringId, yearMonth)` não socorre, porque `RecurringOccurrenceRepository.save()` é um **upsert** — quando encontra linha existente ele faz `update`, sobrescrevendo em silêncio em vez de recusar. A leitura passa para dentro da mesma transação, e a operação devolve a `Transaction` criada, que é o que `ConfirmRecurringUseCase` retorna.

`GetOrCreateInvoiceForMonthUseCase` fica **fora** da transação: se entrasse, uma falha na confirmação desfaria uma fatura recém-criada. Sobrar uma fatura vazia é dano menor que desfazer estrutura de fatura, e menor ainda que duplicar lançamento.
- *Alternativa considerada:* um `TransactionCreationHook` no razão, simétrico ao `TransactionRemovalHook`. Rejeitada: a ocorrência precisa do `transaction.id` recém-criado, e a porta existente é de remoção; criar uma porta nova para um caso é desproporcional.

### D8 — Não há reparo de dados já divergidos
A atomicidade governa apenas escrita nova. Um banco que já divergiu continua reapresentando o mês como pendente e continua podendo duplicar.

Uma leitura que cicatrizasse — calcular "já tratados" também a partir de `transactions.recurringId`/`recurringCycle` — foi considerada e **deliberadamente deixada de fora**: a janela é estreita (crash ou kill do processo entre duas escritas consecutivas) e não há relato de ocorrência em campo. Fica registrado como candidato a revisita caso apareça.

### D9 — Renomear os use cases é parte do escopo, não cosmética
`StopRecurringUseCase` → `ArchiveRecurringUseCase`, `ReactivateRecurringUseCase` → `UnarchiveRecurringUseCase`, com os modais correspondentes. `account-lifecycle` exige: *"Um use case que faz coisa diferente do seu nome deixa quem o chama — e o usuário lendo o botão — com expectativa errada."*

Desarquivar **pede confirmação**, divergindo das outras três fachadas, que o fazem por ação direta. A divergência é a assimetria que o D5 já registra: em conta, cartão e categoria voltar à circulação é recuperar visibilidade; numa recorrência é voltar a gerar. O modal existe para dizer o alcance dessa retomada e, sobretudo, o que ela não faz — os ciclos decorridos durante o arquivamento não são repostos, a geração retoma do corrente. Sem ele essa informação não tem onde caber, e o usuário descobre o alcance da ação pelo resultado dela.

Como arquivar e excluir, o modal tem ViewModel próprio, e o `ViewRecurringViewModel` não conhece o desarquivar: as três retiradas saem dali da mesma forma, por `manager.show(...)`.
- *Alternativa considerada:* ação direta, por simetria com as outras três. Rejeitada: a simetria seria de forma, e o conteúdo difere — só aqui a ação religa um gerador. Se um dia conta ou cartão passarem a carregar consequência ativa no desarquivar, revisita-se o par.

Arquivar mantém o modal, e a sua mensagem atual já está correta ("os lançamentos já gerados continuarão vinculados"). O modal de exclusão, que agora só aparece para recorrência sem uso, precisa de texto novo: hoje diz genericamente "não pode ser desfeita" sem revelar que apagaria o vínculo; passa a dizer que não há histórico a perder.

## Risks / Trade-offs

- **A oferta de exclusão some para recorrência arquivada com história** → é o comportamento correto e o mesmo de categoria, mas remove um caminho que existe hoje. Em compensação, apagar uma recorrência criada por engano deixa de exigir o ritual parar → trocar filtro → excluir: ela é apagável direto, porque nunca gerou nada.
- **Aninhamento de transação depende da reentrância do pool do Room** → verificado na implementação (`ConnectionPoolImpl` reusa a conexão confinada via elemento de contexto de corrotina e emite `SAVEPOINT` em vez de `BEGIN`). O risco residual é perder a propagação do contexto ao trocar de dispatcher dentro do bloco — a implementação MUST NOT introduzir troca de contexto entre as duas escritas.
- **Perda de recortes de filtro** (arquivadas por tipo, todas incluindo arquivadas) → aceita, é a mesma troca que Categorias fez.
- **Inversão do flag no mapper pode passar despercebida numa leitura futura** → mitigada pelo KDoc na entidade e pela cobertura de teste do mapper nos dois sentidos.
- **Descarte de linhas SKIPPED ao apagar uma recorrência sem transações** → aceito e documentado; sem transação gerada, não há história do razão a preservar.

## Migration Plan

Sem migração de dados. `recurring.isActive` permanece com a mesma semântica gravada; muda apenas quem a lê e como a nomeia. Uma recorrência arquivada é indistinguível, no banco, de uma que foi "parada" antes desta mudança — o rollback é reverter o código.

O renome da coluna para `isArchived` fica registrado como dívida com dono, a ser feito quando houver outra migração no mesmo módulo com que se agrupar.

## Open Questions

- Nenhuma pendente. Vocabulário do seletor travado em **Ativas · Despesas · Receitas · Arquivadas**.
