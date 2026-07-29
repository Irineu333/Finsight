## Why

O esquema do razão já é multimoeda desde o primeiro dia — `accounts.currency`, `entries.currency` e o `groupBy { currency }` do `LedgerEntryWriter` estão escritos, e o `Currency.kt` diz textualmente que existem *"so that multi-currency support can be added later without a data-model rewrite"*. O que não é multimoeda é o **comportamento**: o writer grava `BASE_CURRENCY` fixo em toda entry, ignorando a conta em que a perna posta, e as ~15 agregações da `EntryDao` somam `amount` sem jamais tocar em `currency`.

Isso é correto por acidente, não por construção. No instante em que existir uma segunda moeda, `netWorth()`, `assetMonthTotals`, `balanceOf` e companhia passam a somar centavos de moedas diferentes dentro de um único `Long` — **sem erro, sem exceção, sem sintoma**. Um número errado que parece certo é exatamente a classe de defeito que o `:core:ledger` foi construído para tornar impossível, e é a única dela que sobrevive hoje no módulo. A invariante que o app inteiro sustenta (`Σ = 0` por moeda) já é verificada por transação **e por moeda** no `LedgerBalanceCheck`; é a leitura que ainda não sabe que moeda existe.

## What Changes

- **Toda conta e todo cartão declara a sua moeda, e ela é imutável a partir do primeiro lançamento.** A escolha acontece no formulário; `hasEntries(accountId)` — o mesmo fato que já decide apagar-vs-arquivar — passa a decidir também editar-vs-travar a moeda.
- **A moeda de uma perna vem da conta em que ela posta, nunca da intenção.** `TransactionLeg` não ganha campo de moeda: a fronteira de escrita já carrega a conta para verificar fechamento, e passa a ler dali a moeda da entry. Expressar "poste 100 USD numa conta BRL" deixa de ser recusado por validação e passa a ser **inexprimível**.
- **`Σ = 0` por moeda permanece absoluto, sem exceção.** A transação que atravessa moedas é completada com pernas numa **conta de conversão**, por moeda — o mesmo movimento que a fronteira já faz para completar uma intenção de uma perna só. A taxa aplicada nunca é informada nem persistida: ela é **derivada** do par de valores, como o rótulo e o sinal de exibição já são.
- **As contas de sistema passam a ser uma por moeda.** As duas nominais, a de reconciliação e a nova de conversão são resolvidas por `(type, name, currency)`. Nenhuma conta do plano — de usuário ou de sistema — mistura moedas.
- **O razão deixa de consolidar.** Toda leitura que atravessa mais de uma conta devolve valor **por moeda**; `netWorth()` deixa de devolver um número e passa a devolver o que ele sempre foi na verdade: um saldo por moeda. `:core:ledger` MUST NOT conhecer taxa de câmbio nem moeda base.
- **Nova camada de consolidação, fora do razão:** moeda base como preferência do usuário, uma taxa manual por moeda → base, com sugestão oferecida apenas dentro da tela de edição da taxa. Nenhuma leitura do app espera rede.
- **Todo valor cujo cálculo passou por conversão é exibido com marca de aproximação.** A exatidão é **derivada** da leitura, não declarada pela tela: uma leitura que devolveu uma só moeda, igual à base, é exata. Consequência deliberada — para um usuário só-BRL, a marca não aparece em lugar nenhum, e o comportamento de hoje se preserva sem flag e sem ramo de compatibilidade.
- **BREAKING (interno, sem migração de dados):** as assinaturas de leitura do `IEntryRepository` que hoje devolvem `Double` consolidado passam a devolver valor por moeda; `SystemAccount` deixa de ser resolvido por `(type, name)`. Nenhuma coluna é criada, alterada ou removida — toda linha existente já tem `currency = 'BRL'` e continua correta.

**Premissa registrada, fora de escopo:** o expoente da moeda permanece fixo em 2 (base 100), como hoje em `(amount * 100).roundToLong()` e na fronteira de leitura em `Double` "reais". O conjunto de moedas oferecidas é restrito às de duas casas decimais. Suportar JPY (0) ou KWD (3) exige refazer a fronteira `Double`↔centavos inteira, e é mudança própria.

## Capabilities

### New Capabilities
- `currency-consolidation`: a moeda base como preferência de exibição, a taxa de câmbio manual como dado local e único, a conversão acontecendo fora do razão, e a marca de aproximação derivada de a leitura ter convertido ou não.

### Modified Capabilities
- `chart-of-accounts`: toda `Account` tem exatamente **uma** moeda, imutável a partir do primeiro lançamento; o requisito de que o plano contém "exatamente **duas** contas nominais em todo o app" passa a ser duas **por moeda em uso**; a conta de conversão entra como quarta conta de sistema, com a mesma garantia de existência sob demanda das demais.
- `balanced-ledger`: a invariante ganha a declaração explícita de que **não admite exceção**, incluindo a transação que atravessa moedas; a fronteira de escrita passa a completar a intenção cruzada com pernas de conversão; a moeda de cada perna SHALL derivar da conta, e a taxa SHALL ser derivada, nunca persistida.
- `ledger-reporting`: toda leitura que possa atravessar moedas devolve valor **por moeda**; o razão MUST NOT consolidar e MUST NOT conhecer taxa; as leituras escopadas a uma única conta ou à dimensão de uma fatura permanecem monomoeda por construção.
- `money-display`: o tipo de exibição passa a carregar, indissociáveis do valor, também a **moeda** em que ele está denominado e se ele é **exato ou aproximado** — pelo mesmo argumento que já tornou a política de sinal indissociável.

## Impact

- **`core/ledger`** — `Currency.kt` (`BASE_CURRENCY` passa a ser o *default de conta nova*, não a moeda do app); `SystemAccount` ganha `CONVERSION`; `LedgerEntryWriter` lê a moeda da conta de cada perna, resolve a conta de sistema por `(nature, currency)` e completa a intenção cruzada; `AccountDao.getByTypeAndName` ganha a moeda na chave; `EntryDao` ganha `GROUP BY e.currency` nas agregações que atravessam contas; `IEntryRepository` e os seus tipos de retorno (`AccountFlows`, `DimensionFlows`, `LiabilityMonthFlows`, `AssetMonthFlows`, `ScopeStats`) passam a expressar valor por moeda; `LedgerError` ganha o caso da intenção cruzada cujos resíduos não se opõem.
- **`core/database`** — `LedgerBalanceCheck` **não muda**: já agrupa por `(transactionId, currency)`. Nenhuma migração de schema.
- **`core/common`** — `CurrencyFormatter` passa a receber a moeda a formatar em vez de derivá-la do locale do dispositivo (três `actual`: JVM, Android, iOS); `DisplayAmount` ganha moeda e exatidão.
- **novo módulo de consolidação** (ou `:core:model`, ver design D5) — a moeda base, o repositório de taxas, a conversão de um saldo por moeda em figura aproximada na base.
- **`feature/accounts/impl`** — seletor de moeda no `AccountFormModal`, travado a partir do primeiro lançamento; `TransferBetweenAccountsUseCase` passa a aceitar o valor de destino quando as moedas diferem.
- **`feature/creditcards/impl`** — seletor de moeda no `CreditCardFormModal`; `CreditCardRepository` deixa de gravar `BASE_CURRENCY` fixo na conta do cartão; o pagamento de fatura passa a aceitar o par de valores quando a conta e o cartão divergem de moeda.
- **`feature/dashboard/impl`, `feature/report/impl`, `feature/budgets/impl`, `feature/transactions/impl`** — consomem figuras que podem vir aproximadas e passam a exibir a marca.
- **`core/resources`** — nomes de moeda, rótulos da tela de taxa, o texto que explica uma figura aproximada e a sua data de atualização.
- **`core/ui`, `core/designsystem`** — os componentes que hoje chamam `LocalCurrencyFormatter.format(...)` passam a formatar a partir do `DisplayAmount` completo.

**Fora de escopo** (dívida registrada): moedas de expoente ≠ 2; taxa histórica por data (a consolidação usa sempre a taxa corrente); busca automática de taxa em caminho de leitura; tela de ganho/perda cambial — o saldo das contas de conversão a torna derivável, mas expô-la é mudança própria; orçamento denominado em moeda não-base.
