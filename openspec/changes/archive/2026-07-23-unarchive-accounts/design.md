# Design

## Contexto

Terceira e última fachada arquivável a ganhar simétrico de desarquivar. Categoria (flag na fachada) e cartão (flag na conta do plano de contas, fachada consome) já foram. Conta é o caso mais direto: a conta **é** a linha do plano de contas, então arquivar/desarquivar é `close()`/`reopen()` na própria conta — sem indireção de fachada.

O change carrega, além do desarquivar, a **correção de uma inconsistência do arquivar**: a conta padrão é hoje protegida contra apagar no domínio, mas contra arquivar só por um `enabled = !isDefault` na tela. Este change eleva essa proteção ao domínio.

## Decisões

### D1 — O primitivo `reopen` já existe
`AccountDao.reopen(id)` (`UPDATE accounts SET isArchived = 0`) foi adicionado pelo desarquivar de cartão. Nada a fazer em `core/ledger`. Conta reusa o mesmo primitivo, sem indireção: desarquivar conta = `reopen(account.id)`.

### D2 — Guard de padrão no arquivar, erro próprio traduzido
Novo `if (account.isDefault) return AccountException(AccountError.CANNOT_ARCHIVE_DEFAULT).left()` em `ArchiveAccountUseCaseImpl` (`ArchiveAccountUseCaseImpl.kt:28`), **antes** do guard de saldo — mesma posição que o guard de `isDefault` em `DeleteAccountUseCaseImpl.kt:20`. Confirmado sem side-effect antes (o `balance()` só roda depois).

- **Erro próprio, não generalizado.** `CANNOT_ARCHIVE_DEFAULT` distinto de `CANNOT_DELETE_DEFAULT`: as mensagens ao usuário diferem ("não pode arquivar" vs "não pode apagar"). Não renomeamos para um `CANNOT_RETIRE_DEFAULT` compartilhado.
- **Traduzido (`UiText.Res`), não `Raw`.** O irmão `CANNOT_DELETE_DEFAULT` usa `UiText.Raw(message)` (inglês hardcoded, `AccountError.kt:50`) — inconsistente com todos os outros erros, que usam `UiText.Res`. O novo erro segue o padrão **bom** (`UiText.Res` + string pt/en), ficando melhor que o irmão. Alinhar `CANNOT_DELETE_DEFAULT` fica fora de escopo.
- **Seguro para cartão.** `ArchiveAccountUseCase` é compartilhado por conta e cartão (`ArchiveCreditCardUseCase.kt:26` chama-o com a conta `LIABILITY` do cartão). A `LIABILITY` nunca é `isDefault` (a padrão filtra `type = 'ASSET'`, `AccountDao.kt:44`), então o novo guard nunca morde um cartão.

### D3 — Oferta de retirada: wrapper próprio de conta, cartão/categoria intocados
Um sealed próprio de conta em `core/ui`, `AccountRetireOffer { data class Retire(val action: RetireAction); data object UnavailableDefault }`, produzido por `accountRetireOfferOf(hasMovement, isDefault)` que **delega ao `retireActionOf` existente** para o caso `Retire`. `RetireAction` (2 membros) e `retireActionOf(mustPreserve)` ficam **intocados**.

Por que **não** mexer no `retireActionOf` compartilhado (a ideia original de D3): auditado, custa caro sem ganho. O enum `RetireAction` exige `label`+`icon` **não-nulos** por membro; um terceiro membro "indisponível" não tem ação, forçando `label`/`icon` dummy e tornando os quatro `when(retireAction)` existentes não-exaustivos — poluindo `CreditCardsScreen`, `InvoiceTransactionsScreen` e `ViewCategoryModal` com um branch **inalcançável** (cartão e categoria nunca são padrão). O wrapper só-conta entrega o mesmo resultado (enum significativo, não `null`, orientado ao `UiState`, dono único) com **zero** arquivos de cartão/categoria tocados.

- `AccountUi` ganha `isDefault` (alimentado no `AccountsViewModel.kt:103`, onde `hasMovement` já é calculado do domínio `Account`, que já tem `isDefault`) e expõe `AccountRetireOffer`.
- `AccountsScreen` deixa de fazer `enabled = !account.isDefault` (`AccountsScreen.kt:371`) e passa a `when(offer)`: `Retire` → botão de ação (`action.label`/`.icon` + modal Delete/Archive); `UnavailableDefault` → orientação no lugar do botão. **Cuidado de layout** (novo, o cartão nunca enfrentou): a retirada hoje é um `OutlinedActionButton` com `weight(1f)` num `Row` de 2 botões (retirar + editar); o caso `UnavailableDefault` renderiza texto onde havia botão — o `Row` precisa degradar bem.

### D4 — Desarquivar conta: classe concreta única, direto ao repo, sem guard
`UnarchiveAccountUseCase` → `IAccountRepository.reopen(accountId)` → `AccountRepository.reopen` → `dao.reopen`. Sem guard, sem confirmação — reversível e inócuo, saldo zero garantido pelo arquivar.

- **Classe concreta única no `impl`, sem interface no `api`.** É o padrão dos **dois** desarquivares que já existem (`UnarchiveCreditCardUseCase`, `UnarchiveCategoryUseCase` — ambos classe concreta). Só o lado archive/delete usa interface+impl. Registrado com `factory { }` simples (não `factory<Interface>`).
- **Assimetria consciente:** `ArchiveAccountUseCaseImpl` chama `accountDao.close()` **direto**, sem passar pelo repositório; o unarchive passa por `IAccountRepository.reopen`. Então `close` não tem método no repo mas `reopen` terá. Seguir o precedente de cartão (que também vai pelo repo) é aceitável — archive e unarchive não ficam estruturalmente simétricos, e tudo bem.

### D5 — Conta desarquivada volta comum, nunca padrão
Consequência de D2, não regra nova: como a padrão não pode ser arquivada, nenhuma conta arquivada foi padrão. Desarquivar não precisa (e não deve) reeleger padrão — restaura existência, não papel. `reopen` só mexe no flag `isArchived`; `isDefault` da conta permanece o que era ao arquivar (falso).

### D6 — Tela nova + acesso por overflow ao lado do MonthSelector
Tela dedicada (`ArchivedAccountsScreen`, `LazyColumn` de cards enxutos), rota **interna** ao `accounts/impl` (`ArchivedAccountsRoute : NavRoute`), espelhando `ArchivedCreditCardsScreen`. Diferente do cartão, o slot `actions` da topbar de `AccountsScreen` **já é ocupado pelo `MonthSelector`**; o acesso é um menu de overflow (`⋮`) **ao lado** dele — os dois coexistem no canto. Opção A da exploração (a mais barata: reusa string/ícone/gesto do cartão).

### D7 — `ViewAccountModal`, archived-only
Espelha **só** `ViewCreditCardModal` (`AdaptiveModal` via `LocalDetailPaneController`, que a `AccountsScreen` já usa — `AccountsScreen.kt:119`). **Não** espelhar `ViewCategoryModal`: ele tem Edit + retirar completo, não é archived-only. Detalhe enxuto (ícone + nome + tipo; sem saldo — arquivada é sempre zero) e o botão **Desarquivar**. É a "visualização" que a `account-lifecycle` já exige das outras duas fachadas: alcançada só pela lista de arquivadas, oferece exclusivamente desarquivar.

- **Fechamento no sucesso via `Event.Dismiss`, não `onDisappeared`.** `observeAccountById` (`AccountDao.kt:41`) **não** filtra `isArchived`, então o VM continua recebendo a conta após o `reopen` (a linha não some, só o flag muda) — `interceptAbsence(onDisappeared)` não dispara. O dismiss vem do `onRight` do use case, como o `ViewCreditCardViewModel` já faz. Replicar `interceptAbsence` mesmo assim, para o caso de a conta ser apagada por outra via.

## Riscos

- **Fakes de `IAccountRepository` (7, não "todos" vagos).** A nova `reopen(accountId)` quebra 7 implementações da interface: `RecordingAccountRepository` (`RetireAccountGuardsTest.kt:168`, accounts), `FakeAccountRepository` (`ArchiveCreditCardUseCaseTest.kt:66`, creditcards), `LedgerAccountRepository` (`InvoiceWriteGuardTest.kt:354`, transactions), `FakeAccountRepository` (`TransactionRepositoryEntriesTest.kt:254`, transactions), `FakeAccountRepository` (`CalculateReportStatsUseCaseTest.kt:104`, report), `object : IAccountRepository` (`ReportViewerViewModelCharacterizationTest.kt:227`, report), `object : IAccountRepository` (`RecurringRepositoryTest.kt:59`, recurring). **4 estão fora do módulo accounts** — o `:app:shared:testDebugUnitTest` só fica verde com esses módulos compilando. (O lado DAO está coberto: `RecordingAccountDao` já tem `reopen`; não confundir com o fake do **repositório**, que não tem.)
- **DI: accounts tem um só módulo.** Não existe "módulo de use cases" separado (isso é do cartão); tudo entra em `AccountsModule.kt` — `UnarchiveAccountUseCase` ao lado do `factory<ArchiveAccountUseCase>` (`:61`), os dois viewModels no bloco `viewModel { }` (após `:133`).
- **`RetireAction` compartilhado fica intocado** (ver D3) — cartão, categoria e a tela de faturas não são tocados. O único `when` a mudar é o de `AccountsScreen`, que passa a ser `when(offer)`.
- **Layout do caso `UnavailableDefault`** (ver D3) — único ponto sem precedente copiável: texto de orientação onde havia um `OutlinedActionButton` de `weight(1f)`.
