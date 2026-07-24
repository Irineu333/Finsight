# Auditoria da arquitetura de tratamento de erros (seed para change futura)

> **Status:** diagnóstico registrado, **não corrigido**. Todos os desvios abaixo são
> **PRÉ-EXISTENTES** (confirmado por git contra `main` = 5f2fa69) e **app-wide** — não
> pertencem à change `ledger-single-source` e ficaram fora do escopo dela por decisão do
> usuário. Isto seeda uma change dedicada de arquitetura de erros.
>
> O que a `ledger-single-source` *tocou* nesse eixo já foi corrigido nela (§10d.1/§10e.5):
> os 5 modais que engoliam erro, `DeleteTransaction`/`DeleteInstallment`, `PayInvoiceViewModel`,
> e as guardas do razão com `toUiText` (`LedgerError`/`ClosedFacade`) — que são o padrão *certo*.

## Contrato pretendido

1. **Domínio bloqueia operação inválida com erro explícito** (sem boolean mudo / fallback silencioso).
2. **Erro ESPERADO (validação)** → retorna o **enum** via `Either.left`; **não** registra no Crashlytics.
3. **Erro INESPERADO (guard/sistema)** → lança `XxxException(enum)`; **registra** no Crashlytics no ViewModel.
4. **Enum de erro** tem `val message: String` **inglês** (log) + `toUiText()` → `UiText.Res` **i18n** (pt+en, `when` exaustivo).

## Sólido (verificado)

- Domínio recusa toda operação inválida com erro tipado (regra 1 na camada de domínio).
- 11 de 13 enums seguem `message` inglês + `toUiText()` exaustivo. `LedgerError` é o exemplar
  (guardas `Unbalanced`/`InvoiceLocked`/`ClosedAccount[Removal]` embrulham o enum; `ClosedFacade`
  varia só a mensagem, nunca a regra).
- Paridade pt/en perfeita (600/600). Padrão *certo* de validação existe: `ValidateAccountNameUseCase`
  retorna `Either<AccountError, _>` e o `*FormViewModel` consome via `toUiText()` **sem** Crashlytics.

## Desvios (todos PRÉ-EXISTENTES) — para a change futura

| # | Regra | Onde | Severidade |
|---|---|---|---|
| 1 | 4 | `AccountError.kt:50` — `CANNOT_DELETE_DEFAULT → UiText.Raw(message)` (inglês cru na UI; string `account_error_cannot_delete_default` não existe em nenhum idioma) | Alta |
| 2 | 4+1 | `InvoiceError.kt` — sem `toUiText()`; ~30 casos. VMs de fatura caem em `ledger_action_error_generic`. Causas acionáveis ("data após vencimento", "valor excede fatura", "saldo negativo") nunca chegam específicas | Alta |
| 3 | 4+1 | `BuildTransactionError.kt` — sem `toUiText()`. `AddTransactionViewModel`/`EditTransactionViewModel` não mapeiam `BuildTransactionException` → erros de formulário (valor vazio/zero, data futura, título/categoria ausentes) viram genérico | Alta |
| 4 | 2/3 | Sistêmico: `crashlytics.recordException` incondicional no `onLeft` da maioria dos VMs de operação — grava validação esperada como crash (`DeleteAccountViewModel:32`, `ArchiveAccountViewModel:47`, `TransferBetweenAccountsViewModel:93`, `RecurringFormViewModel:95`, todos os de fatura). Discriminação esperado-vs-inesperado essencialmente ausente no boundary | Alta |
| 5 | 1 | Recusa engolida (só `recordException`, sem `showError`): `CloseInvoiceViewModel:25`, `ReopenInvoiceViewModel:23`, `DeleteFutureInvoiceViewModel:24`, `RecurringFormViewModel:94`, `AddTransactionViewModel:122` (caminho de parcelamento) | Alta |
| 6 | 2 | Validação modelada como exception + `Either<Throwable>`: `BuildTransactionUseCaseImpl:34-86`, `SaveRecurringUseCase:38-65` — deveriam devolver o enum via `Either.left` (como `ValidateAccountNameUseCase`) | Média/Alta |
| 7 | 1 | `ConfirmRecurringViewModel:156,170` achatam qualquer `onLeft` em `retire_action_error_generic`, mesmo quando a causa tem `toUiText` (`ClosedAccountException` etc.) — inconsistente com `AddTransactionViewModel` | Média |
| 8 | 4 | `InstallmentError.kt:14,21` — `message` em **português** (regra pede inglês para log); `BlockedInvoice`/`InvoiceError.BlockedInvoice(status)` descartam o detalhe dinâmico no `toUiText` | Baixa |

## Prioridade sugerida (quando virar change)

2, 3, 4 primeiro (fluxos de alto tráfego: transações e faturas; comprometem UX e a utilidade do Crashlytics), depois 1 e 5, depois 6/7/8.
