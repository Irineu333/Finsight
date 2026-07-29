## Context

O `:core:ledger` foi desenhado para multimoeda e nunca a exerceu. O estado atual, verificado no código:

- `AccountEntity.currency` e `EntryEntity.currency` existem, com default `"BRL"`; **toda linha de todo banco existente já tem `'BRL'`**. Nenhuma tabela existente muda.
- `LedgerEntryWriter` valida `Σ = 0` **por moeda** (`entries.groupBy { it.currency }`), mas grava `currency = BASE_CURRENCY` literal nas duas construções de `EntryEntity` (linhas 73 e 88) e na criação de conta de sistema (linha 171). O `groupBy` tem, hoje, sempre um grupo só.
- **Existe um segundo ponto de validação de soma zero**: `LedgerEntryWriter.validate(legs)` (linha 43), chamado de `TransactionRepository:219` e `:240`. É uma soma **plana, sem moeda**, sobre as pernas cruas, antes de qualquer acesso ao banco.
- `TransactionLeg` não carrega moeda. `orRejectIfClosed` **já carrega a `AccountEntity`** de cada perna para verificar fechamento — a moeda está ali, sem custo adicional de leitura.
- `SystemAccount` nomeia cinco chaves, resolvidas por `AccountDao.getByTypeAndName(type, name)` e criadas sob demanda por `ensureSystemAccount`. `systemAccountId()` resolve por **natureza**, e mapeia `EQUITY → RECONCILIATION`.
- **`EQUITY` é hoje um predicado sobrecarregado**, e é o eixo desta revisão. `Ledger.kt:57` — `AccountType.EQUITY in types -> TransactionLabel.ADJUSTMENT`, testado antes de tudo, com a spec `balanced-ledger` exigindo textualmente essa precedência. `Ledger.kt:96` `deriveTransactionType` repete o predicado. Seis agregações da `EntryDao` (linhas 224, 248, 270, 294, 327, 401) classificam por `EXISTS(... a.type = 'EQUITY') AS eq`. E `AdjustBalanceUseCase:46-48` (idem `AdjustInvoiceUseCase:36-42`) define "o ajuste existente" como *qualquer* transação daquela data, naquela conta, com perna `EQUITY`.
- `EntryDao` tem 15 agregações monetárias. **Nenhuma** nomeia `currency`.
- `LedgerBalanceCheck.unbalancedTransactions()` já agrupa por `(transactionId, currency)` e já reporta a moeda no erro. Ele está pronto e não muda.
- `netWorthCents()` filtra `type IN ('ASSET','LIABILITY')`.
- `CurrencyFormatter` é um `expect class` **sem argumentos**, `single` no Koin, que usa `NumberFormat.getCurrencyInstance()` / `NSLocale.currentLocale`. Ele não formata *uma* moeda: formata *a* moeda do dispositivo.
- `DisplayAmount` declara de si, em KDoc e na spec `money-display`, que **não conhece moeda**.
- `feature/creditcards/impl/.../CreditCardRepository:57` grava `currency = BASE_CURRENCY` ao criar a conta `LIABILITY` do cartão — o único uso de `BASE_CURRENCY` fora do `:core:ledger`.

**Precedente pesquisado.** GnuCash implementa exatamente o modelo adotado aqui, com `ACCT_TYPE_TRADING = 14` **distinto** de `ACCT_TYPE_EQUITY = 10`; hierarquia `Trading:CURRENCY:USD` criada sob demanda pelo escriturador (`Scrub.cpp::get_trading_split`) e não postável à mão. hledger faz o mesmo sob o namespace `equity:`, mas precisou de um código de tipo próprio (`V`, *Conversion*) para distinguir a conta. Beancount é o único que usa `Equity:Conversions` literal — e Martin Blais documentou que é *"a bit of a kludge"*, com o v3 planejando migrar para contas por moeda. Firefly III é o contraexemplo: abandona `Σ = 0` para transferências cruzadas (`CorrectsUnevenAmount::fixJournal` retorna cedo em `isForeignCurrencyTransfer`), e o custo está catalogado em issues de transferência corrompida, sinal invertido e orçamento contando valor estrangeiro duas vezes.

## Goals / Non-Goals

**Goals:**
- Moeda por conta e por cartão, escolhida na criação e imutável a partir do primeiro lançamento.
- `Σ = 0` por moeda **sem exceção alguma**, inclusive na transação que atravessa moedas.
- Nenhuma agregação capaz de somar moedas diferentes num mesmo número sobreviver à mudança.
- Nenhum predicado existente mudar de significado: `EQUITY` continua significando exatamente "ajuste".
- Exato onde é monomoeda; aproximado, e **visivelmente** aproximado, onde consolidou; e **nunca inventado** onde não há taxa.
- Comportamento idêntico ao de hoje para um usuário de uma moeda só, sem flag e sem ramo de compatibilidade.

**Non-Goals:**
- Moedas de expoente ≠ 2. Ver D14.
- Registrar o valor estrangeiro de uma despesa paga em moeda local. Ver D1 (a operação é monomoeda; o "quanto era em dólar" é informação de nota fiscal, não de razão).
- Rastrear ganho cambial por **par** de moedas. Ver D3.
- Tela dedicada de ganho/perda cambial. Ver D6 — o dado passa a existir; expô-lo é mudança própria.
- Orçamento denominado em moeda não-base. Ver D13.
- Alteração de qualquer tabela existente. Ver Riscos — há **uma** tabela nova (taxas), e nenhuma migração de dados.

## Decisions

### D1 — A invariante não ganha exceção: a transação cruzada é **completada**, não tolerada

Uma transação que atravessa moedas não desbalanceia o razão; ela chega **incompleta** à fronteira de escrita, exatamente como uma intenção de uma perna só chega hoje. E a fronteira já tem, como responsabilidade declarada no KDoc de `TransactionIntent`, *"completing a one-sided intent"*. A intenção cruzada é o mesmo movimento, aplicado por moeda:

```
INTENÇÃO (o que o extrato mostra)      O QUE A FRONTEIRA GRAVA

saída   -550,00  conta BRL             conta BRL       -550,00 BRL ┐ Σ BRL = 0
entrada +100,00  conta USD             CONVERSION/BRL  +550,00 BRL ┘

                                       CONVERSION/USD  -100,00 USD ┐ Σ USD = 0
                                       conta USD       +100,00 USD ┘
```

A regra, uniforme e sem ramo por caso:

1. agrupa as pernas por moeda e calcula o resíduo de cada grupo;
2. **uma só moeda presente** → o resíduo tem de ser zero, e uma perna avulsa é completada pela contrapartida já existente (`ContraLeg`). É o comportamento de hoje, intacto;
3. **duas ou mais moedas** → o resíduo de cada moeda é lançado, negado, na conta de conversão **daquela** moeda.

O passo 3 balancearia qualquer coisa, inclusive um erro de digitação, e por isso carrega a única guarda nova: **os resíduos não podem ser todos do mesmo sinal**. Se toda moeda envolvida ganha valor, a intenção cria dinheiro do nada — não é câmbio, é defeito, e a fronteira recusa.

Vale registrar **onde** o caso cruzado ocorre, porque é mais estreito do que parece: uma perna nominal posta sempre na nominal da moeda da conta monetária que a acompanha, então despesa, receita e ajuste são monomoeda por construção. Duas moedas só aparecem quando **duas contas monetárias do usuário** se encontram numa mesma transação — isto é, **transferência** e **pagamento de fatura**. Uma compra de US$ 100 paga com conta em BRL não é cruzada: o que saiu foram R$ 550, e é isso que o razão registra.

- *Alternativa considerada:* **flexibilizar a invariante** para a transação cruzada. Rejeitada por três razões. (a) A invariante deixaria de ser verificável por quem lê `entries`: `LedgerBalanceCheck`, hoje um `HAVING total <> 0` de quatro linhas rodando em toda migração, precisaria distinguir a exceção legítima do dado corrompido — e a forma de saber seria consultar as contas das pernas, que é justamente o que ele evita para permanecer válido antes e depois de qualquer reescrita do plano. (b) A spec `balanced-ledger` exige que as validações ocorram *"no mesmo ponto, e em nenhum outro"*. (c) A informação de câmbio ficaria implícita e não consultável, enquanto a conta de conversão a torna um saldo como outro qualquer. **Evidência empírica:** é o caminho do Firefly III, e o custo é um catálogo de bugs de transferência corrompida, sinal invertido e orçamento em dobro que não têm onde ser barrados.
- *Alternativa considerada:* o modelo do Firefly III — colunas `foreign_amount`/`foreign_currency` na transação. Rejeitada frontalmente: `balanced-ledger` proíbe que o sistema *"mantenha um modelo de perna paralelo espelhando o razão"*.
- *Alternativa considerada:* uma coluna `amountBase` em `entries`, no estilo dos apps de consumo. Rejeitada: criaria um **segundo** invariante que o arredondamento do câmbio quebra por construção, congelaria a taxa dentro do razão, e tornaria a troca de moeda base uma migração de dados. **Evidência empírica:** é o `native_amount` do Firefly III (v6.2.0), congelado na escrita, reparado em massa por um comando de correção, e responsável por dois bugs idênticos reportados em duplicata.

### D2 — A conta de conversão tem **tipo próprio**, e não `EQUITY`

`AccountType` deixa de ser `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}` e passa a `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY, CONVERSION}`.

Abrir um conjunto que a spec declara fechado é a decisão mais cara desta mudança, e ela não é de elegância: pôr conversão em `EQUITY` **quebra funcionalidade existente**, em quatro lugares independentes, porque neste código `EQUITY` já está alocado a um significado — "o usuário reconciliou algo à mão":

| onde | com conversão em `EQUITY` |
|---|---|
| `Ledger.kt:57` `deriveTransactionLabel` | toda transferência cruzada vira `ADJUSTMENT` em vez de `TRANSFER` |
| `Ledger.kt:96` `deriveTransactionType` | cada perna monetária lê "ajuste" na lista |
| `EntryDao` × 6, predicado `eq` | pagamento cruzado classificado como ajuste; cruzados entram em `assetMonthTotals`, que hoje exclui transferências e pagamentos |
| `AdjustBalanceUseCase:46-48` | a idempotência confunde uma transferência cruzada do dia com o ajuste daquela conta e **a reescreve** |

Nada disso é acidente de implementação corrigível em silêncio: a spec `balanced-ledger` **exige** que `EQUITY` seja avaliado *"antes de qualquer outro caso"*, e a razão dada — distinguir ajuste de transferência e de pagamento — continua válida e desejável.

Com tipo próprio, os quatro se resolvem sozinhos, sem tocar em nenhum deles: a derivação de rótulo cai por *fall-through* (`{ASSET, CONVERSION}` → `TRANSFER`; `{ASSET, LIABILITY, CONVERSION}` → `PAYMENT`), o predicado `eq` continua significando só ajuste, e a idempotência para de casar.

O precedente é unânime na mesma direção: GnuCash tem `ACCT_TYPE_TRADING = 14` distinto de `ACCT_TYPE_EQUITY = 10` — e nem seguiu a proposta original de Selinger de reusar `Income`; hledger, apesar do namespace `equity:`, precisou do código de tipo `V`; Beancount usou `Equity` literal e o autor registrou que foi gambiarra. Fora do mundo hobbyista, IAS 21 manda diferença cambial de item monetário para **resultado**, não patrimônio, e QuickBooks e Odoo a mapeiam em contas de resultado.

Propriedades do novo tipo, seguindo o precedente:
- **natureza credora** (GnuCash: débito = *Decrease*, crédito = *Increase*), portanto `isDebitNatured = false`;
- `isMonetary = false` — não é onde dinheiro está, e não é escolhível pelo usuário em formulário algum;
- `isNominal = false` — só `INCOME`/`EXPENSE` acolhem dimensão de categoria;
- `isPermanent = true`, **vacuamente**: a propriedade decide se arquivar pode encalhar saldo, e uma conta de conversão nunca é arquivada.

- *Alternativa considerada:* manter `EQUITY` e trocar o discriminante de ajuste, de "existe perna `EQUITY`" para "existe perna na conta de **reconciliação**". Rejeitada: obrigaria `deriveTransactionLabel` a consultar identidade de conta em vez dos tipos das pernas, contrariando a spec (*"derivar o rótulo a partir dos tipos das contas envolvidas"*) e transformando uma função pura sobre um `Set<AccountType>` numa que precisa resolver nomes de contas de sistema.
- *Alternativa considerada:* usar `INCOME`, como Selinger propôs originalmente. Rejeitada: uma perna nominal `INCOME` acolhe dimensão de categoria e entra em toda leitura de receita — o câmbio apareceria como receita do mês em cada tela do app.

### D3 — Uma conta de conversão **por moeda**, não por par

`CONVERSION/BRL`, `CONVERSION/USD` — como GnuCash (`Trading:CURRENCY:USD`), não como hledger (`equity:conversion:$-€:$`).

O custo é conhecido e aceito: com três ou mais moedas, `CONVERSION/BRL` acumula o resultado de todo câmbio contra o real, sem distinguir qual par o produziu. É a reclamação recorrente dos usuários de GnuCash, e é exatamente o que a granularidade por par resolve — ao preço de N² × 2 contas e de um nome de conta que carrega uma ordenação arbitrária de símbolos.

Registrado como decisão consciente e **de difícil reversão**: passar a par depois é migração de dados. Aceita porque rastrear resultado cambial por par é relatório que o app não tem, não pediu, e cuja ausência não impede nada.

### D4 — Contas de sistema por moeda, resolvidas por `(type, name, currency)`

`ensureSystemAccount` passa a resolver por `(type, name, currency)`, e `AccountDao.getByTypeAndName` ganha a moeda na chave. Uma despesa em USD posta em `EXPENSES/USD`; o resíduo BRL de um câmbio, em `CONVERSION/BRL`.

O que decide é o significado de `Account.currency`. Com uma nominal única, uma despesa em USD cairia na conta `EXPENSES` cujo `currency` é `'BRL'`, e o campo passaria a significar duas coisas: uma restrição real nas contas do usuário e um rótulo sem sentido nas linhas de sistema. Com uma por moeda, o campo significa **a mesma coisa em toda linha do plano, sem exceção** — que é a condição para D12 ("a moeda de uma conta é imutável") ser regra do plano de contas e não regra da fachada de conta.

`systemAccountId()` deixa de resolver por natureza: `EQUITY` agora tem **duas** contas de sistema por moeda — reconciliação e, sob o novo tipo, conversão —, então a natureza deixou de ser chave. Passa a resolver por `(SystemAccount, currency)`.

Como no GnuCash, essas contas são **criadas pelo escriturador sob demanda e não são postáveis à mão**: os nomes continuam sendo chaves de busca nunca renderizadas, e nenhum seletor as oferece.

`CLOSED_ACCOUNT`/`CLOSED_CARD` são artefatos da migração `v7 → v9`, existem apenas em BRL e não são tocados. Nenhuma migração: as contas de sistema existentes são `'BRL'` e continuam sendo, agora, as contas de sistema **de BRL**.

### D5 — A moeda de uma perna vem da conta, e `TransactionLeg` não ganha campo

`TransactionLeg` permanece `(type, amount, accountId, dimensionId?)`. A fronteira lê a moeda da `AccountEntity` que **já carrega** em `orRejectIfClosed`.

Isto não é economia de campo, é o que torna a classe inteira de erro inexprimível: não existe forma de dizer "poste 100 USD numa conta BRL", porque o chamador não tem onde dizer a moeda. Nenhuma validação precisa recusá-lo. E preserva intacto o requisito de que *"a intenção de escrita SHALL expressar cada perna por identidade de conta"* — a moeda é atributo da identidade, não um segundo dado ao lado dela.

### D6 — A taxa é derivada; a perna de conversão recebe o resíduo **por diferença**

O chamador de uma transação cruzada informa **os dois valores** — 550 saíram daqui, 100 entraram ali —, que é o que o extrato mostra. Nenhum parâmetro de taxa existe em nenhum ponto do caminho de escrita. A taxa aplicada (5,50) é derivável a qualquer momento das duas pernas, e por isso não é gravada — mesma decisão já tomada para o rótulo (`deriveTransactionLabel`) e para o sinal de exibição (`AccountType.displaySign`). É também o modelo do GnuCash, onde a taxa é `value / amount`, e do Firefly III, que deriva das duas linhas.

**A perna de conversão é sempre a última calculada, e recebe o resíduo por diferença** — nunca calculada de forma independente e depois comparada. É a regra que concentra todo o erro de arredondamento do sistema num único lugar, por construção, em vez de deixá-lo emergir como desbalanceamento de centavos. GnuCash, Beancount e hledger têm bugs catalogados exatamente aí: GnuCash arredonda a split de balanceamento com `GNC_HOW_RND_ROUND_HALF_UP` e ainda assim acumula defeitos de arredondamento em relatórios; Beancount precisou de um sistema de tolerâncias inferidas por moeda; a doc do hledger admite que `--infer-equity` *"expõe imprecisões nos preços registrados"*.

Efeito colateral deliberado: o saldo das contas de conversão é o **resultado cambial realizado**. Atenção ao sinal — sendo conta de natureza credora, o saldo bruto é o **negativo** do ganho (GnuCash nega em `balance-sheet.scm` antes de somar ao patrimônio, e rotula "Trading Gains"/"Trading Losses"). O dado passa a existir de graça; expô-lo em tela é mudança própria (Non-Goal).

### D7 — Um único ponto de validação: o `validate()` plano é **eliminado**

`LedgerEntryWriter.validate(legs)` soma as pernas cruas num escalar sem moeda, e é chamado de `TransactionRepository:219` e `:240` antes da fronteira. Toda intenção cruzada (−550 BRL + 100 USD ≠ 0) seria recusada ali, antes de a fronteira ter chance de completá-la.

Ele é **removido**, não consertado. Dois motivos:

1. Consertá-lo é impossível sem quebrar D5: agrupar por moeda exige conhecer a moeda de cada perna, que só existe na conta — o pré-cheque teria de virar `suspend` e ler o banco, deixando de ser pré-cheque.
2. Ele nunca deveria ter existido: `writeEntries` já valida a invariante, com moeda, e a spec exige que ela seja verificada *"no mesmo ponto, e em nenhum outro"*. Havia dois pontos, e o segundo era o errado.

A recusa de uma intenção monomoeda desbalanceada continua acontecendo, no ponto certo, com o mesmo erro tipado.

### D8 — O razão nunca consolida

Toda leitura do `IEntryRepository` capaz de atravessar contas passa a devolver valor **por moeda**. `netWorth()` deixa de devolver `Double` e passa a devolver o saldo por moeda — que é o que ele sempre foi, com uma moeda só.

O que decide é a frase que o módulo sustenta: *"every figure is `Σ entries`"*. Um `netWorth()` que consulta uma taxa deixa de ser `Σ entries` e passa a ser `Σ (entries × taxa)` — a primeira leitura do razão a depender de algo que não é o razão. Devolver por moeda mantém a frase literalmente verdadeira e empurra a aproximação para onde ela é escolha de apresentação. É o que Beancount, hledger, Ledger e o GnuCash por commodity fazem; Firefly III passou oito anos assim e só na v6.2 (2025) acrescentou consolidação como *opt-in*.

Consequência que vale registrar: **"moeda base" deixa de ser fato contábil e passa a ser o que é — preferência de exibição.**

Duas correções de fronteira que o desenho anterior errava:

- **`netWorth` continua filtrando `ASSET`/`LIABILITY`, e a conversão fica de fora — isso é correto e precisa estar dito.** Com o câmbio a 5,50, uma transferência de R$ 550 → US$ 100 deixa `−550 BRL` e `+100 USD` nas contas do usuário, que consolidam a zero: o patrimônio não muda, como deve. Se a taxa vai a 6,00, os mesmos saldos consolidam a `+50` — o ganho **aparece nas próprias contas do usuário**, e incluir a conversão o contaria duas vezes.
- **"Monomoeda por construção" vale para a conta; para a dimensão de fatura, é garantia da fachada.** Nada no razão amarra uma dimensão `INVOICE` a uma única conta `LIABILITY` — `DimensionKind.landsOn` só exige a natureza. Quem garante a moeda única é a fachada de cartão. A distinção importa porque o razão **não** deve consultar `DimensionKind` na leitura para decidir a forma do retorno: toda leitura por dimensão devolve por moeda, e é a feature de cartões que sabe que o seu mapa tem sempre uma chave.

- *Alternativa considerada:* um port `ExchangeRateProvider` injetado no razão, no espírito de `DimensionWriteGuard`/`TransactionRemovalHook`. Rejeitada: aqueles portos existem para que uma **fachada vete ou reaja a uma escrita** sem que o razão a nomeie. Uma taxa não é nem veto nem reação — é preferência de leitura.

### D9 — Exatidão derivada, e consolidação **parcial** quando falta taxa

Uma figura consolidada é, no caso geral, uma **lista de termos**; quase sempre de um só. A consolidação reduz o saldo por moeda **até onde as taxas permitirem**, e o que não puder ser reduzido permanece como termo próprio:

| entrada (saldo por moeda) | resultado |
|---|---|
| vazio | `R$ 0,00` — exato |
| `{BRL: 100}` (base) | `R$ 100,00` — exato |
| `{USD: 50}`, taxa conhecida | `≈ R$ 275,00` — aproximado |
| `{BRL: 100, USD: 50}`, taxa conhecida | `≈ R$ 375,00` — aproximado |
| `{BRL: 100, USD: 50}`, **sem** taxa de USD | `R$ 100,00 + US$ 50,00` — aproximado |
| `{USD: 50}`, **sem** taxa | `US$ 50,00` — exato (nada foi convertido) |

A regra: um termo por moeda cuja taxa é desconhecida, mais um termo na base com tudo o que foi convertido. A figura é aproximada se **alguma** conversão ocorreu.

Nada é inventado e nada é omitido. Uma taxa ausente não vira `1`, não some da soma e não zera a tela — ela produz um termo a mais, que é honesto sobre o que o app sabe. Como consequência, o estado "primeira conta estrangeira criada, taxa ainda não cadastrada" — obrigatório no fluxo real — tem comportamento definido e útil, em vez de indefinido.

A exatidão é **derivada** desse cálculo, nunca declarada pela tela nem marcada à mão. Daí decorre o principal fator de redução de risco da mudança: **para um usuário com contas só em BRL, toda leitura devolve um mapa de uma chave igual à base, e a marca de aproximação não aparece em lugar nenhum do app** — sem flag, sem ramo de compatibilidade e sem caminho alternativo. O comportamento de hoje é o caso particular do caso geral.

### D10 — Moeda, exatidão e multiplicidade viajam **dentro** do tipo de exibição

`DisplayAmount` passa a carregar, indissociáveis, o valor, a política de sinal, a **moeda** em que está denominado e se é **exato ou aproximado**. Uma figura consolidada é uma **sequência** desses termos, quase sempre unitária, e a UI a renderiza justapondo os termos.

O argumento é literalmente o que a spec `money-display` já escreveu para a política de sinal: *"um valor construído sem a sua política, ou alterado sem ela, é o modo de falha que este requisito existe para tornar impossível."* Um valor que troca de moeda enquanto o símbolo ao lado não troca é o mesmo modo de falha — e pior, porque "R$ 830,00" sobre um saldo em dólar é uma frase inteiramente plausível.

Isso exige reconciliar o requisito que hoje diz que o tipo *"MUST NOT ... conhecer moeda"*. A razão original permanece e a redação passa a dizê-la com precisão: o que o tipo não pode é **calcular** — combinar dois valores, converter, somar. Carregar a denominação de um único número não é cálculo, é a legenda sem a qual ele não se lê. E justapor `R$ 100,00 + US$ 50,00` é precisamente **não** combinar: é a recusa de somar o que não se soma, expressa como layout.

`CurrencyFormatter.format` deixa de derivar a moeda do locale e passa a recebê-la; o locale continua governando **formato** — separador e posição do símbolo —, que é o que ele legitimamente sabe. Pelo mesmo argumento, o **campo de entrada** de valor exibe o símbolo da conta escolhida: entrada de 100 USD com "R$" no campo é este mesmo modo de falha, do lado da escrita.

### D11 — A taxa tem data, é colhida do próprio câmbio, e a política é "a última em ou antes"

Uma tabela `(moeda, data, taxa)` contra a moeda base, e não um valor corrente único. A consolidação de uma figura de um período usa **a última taxa em ou antes daquela data**.

Sem data, o patrimônio de dezembro é recalculado à taxa de hoje e **se move sozinho** quando a taxa muda — o passado deixa de ser estável. É o ponto em que os quatro sistemas pesquisados convergem contra o desenho anterior: GnuCash (`GNCPrice` com `time` e `source`), Beancount (diretiva `price`), Firefly III (`currency_exchange_rates(from,to,date,rate)`) e hledger (`P`) todos guardam preço por par e data. GnuCash sequer escolheu uma política única: expôs *Nearest in time / Most recent / Average Cost / Weighted Average* como opção de relatório. "A última em ou antes" é o padrão determinístico do Beancount e o `getFromDB` do Firefly III, e é a escolha aqui.

**Toda transação cruzada cadastra a sua própria taxa**, na sua data, derivada das duas pontas. É o `PRICE_SOURCE_XFER_DLG_VAL` do GnuCash, e é de graça: as duas pontas já existem, e o usuário nunca digita a mesma taxa duas vezes. A origem de cada taxa é registrada — colhida de um câmbio, ou digitada pelo usuário —, e a digitada prevalece na mesma data.

A taxa gravada é a **única** autoridade em qualquer conversão. Uma fonte externa pode preencher o campo como sugestão dentro da tela que edita a taxa, e em nenhum outro lugar: nenhuma leitura do app espera rede, tem estado de carregamento ou falha por indisponibilidade.

Uma taxa por moeda **→ base**; não uma matriz de pares. Trocar a moeda base não invalida o acervo: a taxa da antiga base contra a nova é a inversa da que já existe, e as demais se re-expressam por triangulação sobre as taxas de mesma data. Isso é derivação, não migração — nenhuma linha gravada muda.

### D12 — A moeda de uma conta é imutável a partir do primeiro lançamento

A regra usa `IEntryRepository.hasEntries(accountId)` — o **mesmo** fato que já decide apagar-vs-arquivar em `account-lifecycle`. Entre a criação e o primeiro lançamento a moeda é editável; a partir dele, o formulário a apresenta travada, com o motivo.

Pertence a `chart-of-accounts`, não a `account-lifecycle`: a moeda é atributo da linha do plano de contas, e por D4 isso vale para **toda** linha, inclusive as de sistema — que ninguém edita, o que torna a regra vacuamente verdadeira ali em vez de inaplicável.

- *Alternativa considerada:* permitir a troca e reinterpretar o histórico. Rejeitada: reescreve em silêncio o significado de toda entry já gravada.

### D13 — Orçamento e categoria consolidam de forma aproximada

Uma categoria é dimensão, não conta: não tem moeda, e as suas entries podem estar em várias. O gasto de "Alimentação" é, por natureza, leitura multimoeda — cai no lado aproximado de D9 pelo mesmo mecanismo de tudo o mais, sem regra própria.

O orçamento é denominado na moeda base, e o seu progresso é figura aproximada quando a categoria tem gasto em outra moeda. Consequência aceita: **o progresso pode se mover por variação de taxa, sem gasto novo algum.** É o preço de haver um único número, e ele vem marcado como aproximado. É exatamente a família de bugs que o Firefly III acumula em orçamento multimoeda (#1350, #3875, #9810, #9858, #11964) — lá sem marca e sem dono único; aqui, com ambos.

### D14 — O expoente da moeda permanece 2, e o conjunto oferecido é restrito a isso

`(amount * 100).roundToLong()` na escrita e a fronteira de leitura em `Double` permanecem. As moedas oferecidas no seletor são as de duas casas decimais.

Premissa **deliberada**: suportar JPY (0 casas) ou KWD (3) não é acrescentar um campo, é refazer toda a conversão `Double`↔centavos do razão e da UI, incluindo `MoneyInputTransformation`. Fazê-la junto misturaria duas mudanças de risco muito diferente.

A moeda continua um `String` ISO 4217 na persistência. O catálogo do que é oferecido mora na camada de consolidação, que é quem tem opinião sobre quais moedas o app suporta.

## Riscos / Trade-offs

- **Abrir o conjunto fechado de tipos de conta.** É a maior concessão da mudança, e toca todo `when (type)` exaustivo do app. Mitigação: é a alternativa a quebrar quatro comportamentos existentes (D2), e o compilador encontra cada site.
- **Uma tabela nova.** A tabela de taxas (D11) é a única alteração de schema — nenhuma tabela existente muda, e não há migração de dados. A afirmação "sem migração" do desenho anterior era falsa a partir do momento em que a taxa ganhou data.
- **Superfície de leitura.** As agregações da `EntryDao` que atravessam contas ganham `GROUP BY e.currency`, e os retornos multimoeda do `IEntryRepository` mudam de forma — inclusive `balanceUpTo(accountId = null)`, `naturalBalanceUpTo`, `dimensionBalanceInMonth` de categoria, `totalsByDimension` e `totalsByDimensionInScope`. As leituras escopadas a **uma** conta (`AccountFlows`) e à dimensão de **uma** fatura (`DimensionFlows`) permanecem monomoeda e só ganham a moeda no resultado — mas por D8 é a fachada, não o razão, quem sabe disso.
- **Silêncio residual até a UI existir.** Entre gravar `leg.currency` corretamente e todas as leituras agregarem por moeda existe uma janela em que uma segunda moeda produziria números errados. Mitigação de ordenação: as leituras vêm **antes** da escolha de moeda no formulário — enquanto nenhuma conta puder ser criada em outra moeda, nenhum dado errado é produzível.
- **A soma de perímetros disjuntos perde dono.** `dashboard-balance-widgets` exige somar `assetMonthFlows` + `liabilityMonthFlows` fora do razão e "sem agregado dedicado". Com os dois devolvendo por moeda, essa soma passa a ser soma de mapas — que **não** é conversão e portanto não é da camada de consolidação, e **não** pode ser do tipo de exibição, que D10 proíbe de combinar valores. Ela precisa de dono explícito, no razão, como operação sobre saldos por moeda.
- **Uma guarda que pode recusar câmbio legítimo?** A guarda de D1 (resíduos não todos do mesmo sinal) recusa uma intenção cruzada em que toda moeda ganha valor. Não conheço câmbio real com essa forma; se aparecer, é a guarda que muda, não a invariante.
