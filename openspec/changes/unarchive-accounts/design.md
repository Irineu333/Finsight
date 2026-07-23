# Design

## Contexto

Terceira e última fachada arquivável a ganhar simétrico de desarquivar. Categoria (flag na fachada) e cartão (flag na conta do plano de contas, fachada consome) já foram. Conta é o caso mais direto: a conta **é** a linha do plano de contas, então arquivar/desarquivar é `close()`/`reopen()` na própria conta — sem indireção de fachada.

O change carrega, além do desarquivar, a **correção de uma inconsistência do arquivar**: a conta padrão é hoje protegida contra apagar no domínio, mas contra arquivar só por um `enabled = !isDefault` na tela. Este change eleva essa proteção ao domínio.

## Decisões

### D1 — O primitivo `reopen` já existe
`AccountDao.reopen(id)` (`UPDATE accounts SET isArchived = 0`) foi adicionado pelo desarquivar de cartão. Nada a fazer em `core/ledger`. Conta reusa o mesmo primitivo, sem indireção: desarquivar conta = `reopen(account.id)`.

### D2 — Guard de padrão no arquivar, erro próprio
Novo `if (account.isDefault) return AccountException(AccountError.CANNOT_ARCHIVE_DEFAULT).left()` em `ArchiveAccountUseCaseImpl`, **antes** do guard de saldo, simétrico ao guard que `DeleteAccountUseCaseImpl` já tem para `CANNOT_DELETE_DEFAULT`.

- **Erro próprio, não generalizado.** `CANNOT_ARCHIVE_DEFAULT` distinto de `CANNOT_DELETE_DEFAULT`: as mensagens ao usuário diferem ("não pode arquivar" vs "não pode apagar") e o padrão da base é um erro por recusa. Não renomeamos para um `CANNOT_RETIRE_DEFAULT` compartilhado.
- **Seguro para cartão.** `ArchiveAccountUseCase` é compartilhado por conta e cartão (o arquivar de cartão passa por ele por causa do guard de saldo). A conta `LIABILITY` de um cartão nunca é `isDefault` (a padrão é sempre `ASSET`), então o novo guard nunca morde um cartão.

### D3 — Oferta de retirada: enum de três casos, não `null`
`retireActionOf` passa a receber `isDefault` e a devolver um **terceiro caso nomeado** em vez de `RetireAction?`. Motivo: um enum nomeado é mais significativo que `null` — documenta a intenção ("retirada indisponível: é a conta padrão") e força a tela a tratar o caso explicitamente, em vez de decidir o significado de `null`.

- Assinatura nova: `retireActionOf(mustPreserve: Boolean, isDefault: Boolean = false)`. O default `false` mantém `CreditCardUi.retireAction` e o uso das categorias intactos — só `AccountUi` passa `isDefault`.
- O terceiro caso não carrega `label`+`icon` de ação (não é uma ação); carrega a **orientação** ("escolha outra conta como padrão antes"). A tela renderiza orientação nesse caso, em vez de um botão de ação.
- `AccountsScreen` deixa de fazer `enabled = !account.isDefault` inline: a decisão vira o terceiro caso do `retireAction`, com dono único em `core/ui`, consumido via `UiState`.

> Wrinkle a resolver na implementação: `RetireAction` hoje é um enum com `label`+`icon` por caso. O terceiro caso ("indisponível") não tem ação. Duas formas: (a) terceiro membro do enum com `label`/`icon` opcionais/de orientação; (b) o dono passa a devolver um tipo que envolve `RetireAction` + o caso "indisponível". Preferir (a) se couber sem sujar os dois consumidores existentes; senão (b). A decisão não muda o spec.

### D4 — Desarquivar conta: direto ao repo, sem guard
`UnarchiveAccountUseCase` → `IAccountRepository.reopen(accountId)` → `AccountRepository.reopen` → `dao.reopen`. Sem guard, sem confirmação — reversível e inócuo, saldo zero garantido pelo arquivar. Mesma **forma** de `ArchiveAccountUseCase` (declarado no `api`, impl no `impl`), mas outra fiação: archive tem guards (padrão, saldo), unarchive não.

### D5 — Conta desarquivada volta comum, nunca padrão
Consequência de D2, não regra nova: como a padrão não pode ser arquivada, nenhuma conta arquivada foi padrão. Desarquivar não precisa (e não deve) reeleger padrão — restaura existência, não papel. `reopen` só mexe no flag `isArchived`; `isDefault` da conta permanece o que era ao arquivar (falso).

### D6 — Tela nova + acesso por overflow ao lado do MonthSelector
Tela dedicada (`ArchivedAccountsScreen`, `LazyColumn` de cards enxutos), rota **interna** ao `accounts/impl` (`ArchivedAccountsRoute : NavRoute`), espelhando `ArchivedCreditCardsScreen`. Diferente do cartão, o slot `actions` da topbar de `AccountsScreen` **já é ocupado pelo `MonthSelector`**; o acesso é um menu de overflow (`⋮`) **ao lado** dele — os dois coexistem no canto. Opção A da exploração (a mais barata: reusa string/ícone/gesto do cartão).

### D7 — `ViewAccountModal`, archived-only
Espelha `ViewCreditCardModal`/`ViewCategoryModal` (`AdaptiveModal` via `LocalDetailPaneController`). Detalhe enxuto (ícone + nome + tipo; sem saldo — arquivada é sempre zero) e o botão **Desarquivar**. É a "visualização" que a `account-lifecycle` já exige das outras duas fachadas: alcançada só pela lista de arquivadas, oferece exclusivamente desarquivar; fecha-se sozinha no sucesso (`Event.Dismiss`).

## Riscos

- **Fakes de `IAccountRepository`.** A nova `reopen(accountId)` quebra os fakes da interface — patchar todos (grep por implementações de `IAccountRepository`). `RecordingAccountDao` (`RetireAccountGuardsTest`) já tem `reopen`, então o lado DAO está coberto.
- **Consumidores de `retireActionOf`.** A mudança de assinatura é retrocompatível pelo default `isDefault = false`, mas todo `when(retireAction)` que hoje é exaustivo sobre 2 casos passa a precisar do terceiro — o compilador aponta cada um (`CreditCardsScreen`, `InvoiceTransactionsScreen`, `AccountsScreen`, `ViewCategory*`).
