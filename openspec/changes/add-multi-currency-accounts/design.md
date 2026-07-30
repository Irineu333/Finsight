## Context

O `:core:ledger` foi desenhado para multimoeda e nunca a exerceu. O estado atual, verificado no código:

- `AccountEntity.currency` e `EntryEntity.currency` existem, com default `"BRL"`; **toda linha de todo banco existente já tem `'BRL'`**.
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
- Moeda por conta e por cartão, escolhida na criação e imutável para sempre — o app não oferece a troca.
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
- Reescrita de dado existente — a migração cria a tabela de taxas e acrescenta uma coluna a `budgets`, preenchida com a moeda que já denominava aqueles limites. Nenhum valor gravado é alterado.
- **Editar uma transação que atravessa moedas.** Ver D19: `updateTransaction` recebe uma única perna, e o gate de editabilidade já recusa duas pernas monetárias. Apagar e refazer.
- **Taxa entre duas moedas não-base.** Ver D11: uma transferência USD→EUR com base BRL não produz taxa contra a base, e nenhuma é colhida.
- **Trocar a moeda base.** Ver D18 e D28: a base é resolvida pelo locale na primeira execução e a v1 não oferece a troca; o requisito que a descreve existe para que a implementação não a impossibilite.
- **Limite de orçamento `PERCENTAGE` derivado de recorrência em moeda não-base.** Ver D13.

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

**E o modelo de domínio passa a dizer o mesmo.** `Entry` (`core/ledger/.../domain/model/Entry.kt`) carrega hoje `val account: Account` **e** `val currency: String = BASE_CURRENCY` lado a lado — dois campos onde esta decisão afirma existir um fato só, e portanto duas fontes que podem divergir. A moeda de `Entry` passa a ser **derivada**: `val currency get() = account.currency`. A perna em moeda divergente deixa de ser inexprimível apenas na escrita e passa a sê-lo também na leitura, e os 76 sítios que constroem `Entry` deixam de ter uma decisão a tomar. É seguro porque os dois únicos sítios de construção em produção — `EntryRepository.kt:33-51` e `TransactionRepository.kt:61-76` — hidratam a `Account` completa a partir da projeção com *join*; não existe caminho que monte um `Entry` sobre uma `Account` sintética cuja moeda mentiria.

A **coluna** `entries.currency` permanece, e não por inércia: `balanced-ledger` exige que a invariante seja verificável *"lendo apenas as entries"*, e `LedgerBalanceCheck.kt:38-40` a verifica com um `GROUP BY (transactionId, currency)` sem tocar em `accounts`. Derivar no domínio e persistir na tabela não é duplicação: é a diferença entre o que o modelo garante e o que a checagem de integridade precisa poder ler sozinha.

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
| vazio | zero — exato |
| `{BRL: 100}`, base BRL | `R$ 100,00` — exato |
| `{USD: 50}`, base BRL, **com ou sem** taxa | `US$ 50,00` — **exato** |
| `{BRL: 100, USD: 50}`, taxa conhecida | `≈ R$ 375,00` — aproximado |
| `{BRL: 100, USD: 50}`, **sem** taxa de USD | `R$ 100,00 + US$ 50,00` — aproximado |

A regra, em duas partes:

1. **Uma única moeda no resultado → a figura é ela, exata, naquela moeda — qualquer que ela seja.** A base não participa. Não havia nada a reconciliar, então converter seria perda pura: um número aproximado no lugar de um exato, em troca de nada.
2. **Duas ou mais moedas → reduz-se à base até onde as taxas permitirem**, um termo por moeda sem taxa mais um termo na base com o que foi convertido. A figura é aproximada porque alguma conversão ocorreu.

O caso que essa distinção existe para acertar: **um usuário com todas as contas e cartões em dólar e o dispositivo em locale brasileiro.** A base resolvida pelo locale é o real, mas ele não tem um centavo em reais — e vê tudo em dólar, exato, sem marca, inclusive no dashboard, porque em nenhuma leitura houve mais de uma moeda para reconciliar. A redação anterior desta tabela convertia o patrimônio dele para reais no instante em que uma taxa de dólar existisse, e a tela de taxas listava exatamente essa moeda, convidando-o a acionar o problema.

Nada é inventado e nada é omitido. Uma taxa ausente não vira `1`, não some da soma e não zera a tela — ela produz um termo a mais, que é honesto sobre o que o app sabe. Como consequência, o estado "primeira conta estrangeira criada, taxa ainda não cadastrada" — obrigatório no fluxo real — tem comportamento definido e útil, em vez de indefinido.

A exatidão é **derivada** desse cálculo, nunca declarada pela tela nem marcada à mão. Daí decorre o principal fator de redução de risco da mudança: **para um usuário de uma moeda só — qualquer moeda —, toda leitura devolve um mapa de uma chave, toda figura é exata, e a marca de aproximação não aparece em lugar nenhum do app** — sem flag, sem ramo de compatibilidade e sem caminho alternativo. O comportamento de hoje é o caso particular do caso geral, e a garantia deixa de depender de a moeda do usuário coincidir com a base.

### D10 — Moeda, exatidão e multiplicidade viajam **dentro** do tipo de exibição

`DisplayAmount` passa a carregar, indissociáveis, o valor, a política de sinal, a **moeda** em que está denominado e se é **exato ou aproximado**. Uma figura consolidada é uma **sequência** desses termos, quase sempre unitária, e a UI a renderiza justapondo os termos.

O argumento é literalmente o que a spec `money-display` já escreveu para a política de sinal: *"um valor construído sem a sua política, ou alterado sem ela, é o modo de falha que este requisito existe para tornar impossível."* Um valor que troca de moeda enquanto o símbolo ao lado não troca é o mesmo modo de falha — e pior, porque "R$ 830,00" sobre um saldo em dólar é uma frase inteiramente plausível.

Isso exige reconciliar o requisito que hoje diz que o tipo *"MUST NOT ... conhecer moeda"*. A razão original permanece e a redação passa a dizê-la com precisão: o que o tipo não pode é **calcular** — combinar dois valores, converter, somar. Carregar a denominação de um único número não é cálculo, é a legenda sem a qual ele não se lê. E justapor `R$ 100,00 + US$ 50,00` é precisamente **não** combinar: é a recusa de somar o que não se soma, expressa como layout.

`CurrencyFormatter.format` deixa de derivar a moeda do locale e passa a recebê-la; o locale continua governando **formato** — separador e posição do símbolo —, que é o que ele legitimamente sabe. Pelo mesmo argumento, o **campo de entrada** de valor exibe o símbolo da conta escolhida: entrada de 100 USD com "R$" no campo é este mesmo modo de falha, do lado da escrita.

**Onde o tipo da figura mora, e por que não é onde o resto da consolidação mora.** A camada de consolidação vive em `:core:model` (ver Impact), mas o **tipo da figura** vive em `:core:common`, ao lado de `DisplayAmount`. A razão é de compilação e não de gosto: o renderizador multitermo de D22 é regra única de layout e portanto pertence a `:core:designsystem`, cujo `build.gradle.kts` vê **apenas** `core/common` e `core/resources`. Pôr a figura em `:core:model` tornaria a regra única irrealizável, ou exigiria abrir uma aresta `designsystem → model` para que o sistema de design conhecesse modelo de fachada — o que é a inversão errada.

É um **tipo nomeado**, não uma `List<DisplayAmount>` crua, e os três buracos da lista dizem por quê: a exatidão é propriedade da **figura** e não de cada termo (dois termos exatos justapostos formam uma figura aproximada, porque houve conversão); D20 precisa saber **qual** termo é o da base para degradar a ele, o que uma ordem posicional não garante; e uma lista não tem como tornar a exatidão inescapável, que é o que `currency-consolidation` exige. Construí-lo é **exclusivo do redutor** em `:core:model` — o tipo carrega, o redutor calcula, e a proibição de o tipo de exibição combinar valores permanece intacta.

Custo obrigatório que decorre disto e que vale registrar: `DisplayAmount.kt:62-68` tem `equals`, `hashCode` e `toString` escritos à mão. Moeda e exatidão têm de entrar nos três, ou dois valores de moedas diferentes passam a comparar iguais.

### D11 — A taxa tem data, é colhida do próprio câmbio, e a política é "a última em ou antes"

Uma tabela `(moeda, data, taxa)` contra a moeda base, e não um valor corrente único. A consolidação de uma figura de um período usa **a última taxa em ou antes daquela data**.

Sem data, o patrimônio de dezembro é recalculado à taxa de hoje e **se move sozinho** quando a taxa muda — o passado deixa de ser estável. É o ponto em que os quatro sistemas pesquisados convergem contra o desenho anterior: GnuCash (`GNCPrice` com `time` e `source`), Beancount (diretiva `price`), Firefly III (`currency_exchange_rates(from,to,date,rate)`) e hledger (`P`) todos guardam preço por par e data. GnuCash sequer escolheu uma política única: expôs *Nearest in time / Most recent / Average Cost / Weighted Average* como opção de relatório. "A última em ou antes" é o padrão determinístico do Beancount e o `getFromDB` do Firefly III, e é a escolha aqui.

**Toda transação cruzada cadastra a sua própria taxa**, na sua data, derivada das duas pontas. É o `PRICE_SOURCE_XFER_DLG_VAL` do GnuCash, e é de graça: as duas pontas já existem, e o usuário nunca digita a mesma taxa duas vezes. A origem de cada taxa é registrada — colhida de um câmbio, ou digitada pelo usuário —, e a digitada prevalece na mesma data.

A taxa gravada é a **única** autoridade em qualquer conversão. Uma fonte externa pode preencher o campo como sugestão dentro da tela que edita a taxa, e em nenhum outro lugar: nenhuma leitura do app espera rede, tem estado de carregamento ou falha por indisponibilidade.

**A direção e a precisão da taxa, que D14 fixa para a moeda e faltava fixar para a taxa.** A direção é **moeda → base**: a taxa é o número de unidades da base por **uma** unidade da moeda (com base BRL, o dólar a `5,50`). Fixá-la importa porque a inversa não é a mesma decisão de arredondamento, e uma tabela em que metade das linhas está numa direção é uma tabela sem autoridade.

E a taxa gravada é o **quociente pleno** — valor-na-base dividido por valor-na-moeda, derivado das duas pernas em centavos (D6) —, persistido como `REAL`, **nunca a forma exibida**. As 4 casas decimais são decisão de **formatação**, e só existem na tela de taxas. São dois números distintos com dois donos distintos: o quociente é a autoridade, a formatação é apresentação. Confundi-los é o modo de falha aqui, porque gravar o texto arredondado transformaria cada exibição numa perda de precisão acumulável.

A não-coincidência do *round-trip* — reaplicar a taxa gravada ao valor de origem não devolve exatamente o valor de destino — é **não-problema onde importa e já resolvido onde apareceria**. No razão não existe: as duas pernas são o dado, a taxa é derivada delas e nunca reaplicada (D6, e a perna de conversão recebe o resíduo por diferença justamente para concentrar o arredondamento). Na consolidação existe e é exatamente o que a marca de aproximação de D9 declara. O arredondamento do redutor é declarado uma vez, no redutor (`roundToLong`), e em nenhum outro lugar.

Uma taxa por moeda **→ base**; não uma matriz de pares. Trocar a moeda base não invalida o acervo: a taxa da antiga base contra a nova é a inversa da que já existe, e as demais se re-expressam por triangulação sobre as taxas de mesma data. Isso é derivação, não migração — nenhuma linha gravada muda.

### D12 — A moeda de uma conta é fixada na criação e **nunca** muda

Não é "imutável a partir do primeiro lançamento": é imutável desde o instante em que a conta existe. O app **não oferece** trocar a moeda de uma conta, em nenhum estado, e o domínio recusa a tentativa sem consultar condição alguma.

A regra condicional era mais fraca em três dimensões, e todas importam:

1. **Precisava de um fato para decidir.** `hasEntries(accountId)` numa regra que não precisa dele — a moeda é atributo de identidade, não de histórico.
2. **Produzia um controle de dois comportamentos** na mesma tela de edição, e um estado que o usuário encontra uma vez na vida e não reconhece.
3. **Deixava a recusa condicional**, e uma recusa condicional é uma recusa que alguém precisa lembrar de manter correta.

O **caminho de correção já existe e não é novo**: uma conta sem lançamento algum pode ser apagada (`account-lifecycle` — *"Uma conta sem nenhum lançamento MAY ser removida, por não haver história a preservar"*). Errou a moeda e ainda não usou a conta? Apaga e cria de novo, com a ação que o app já oferece. Errou e já usou? Aí não há correção possível de qualquer forma, porque o significado das entries já gravadas depende dela.

Pertence a `chart-of-accounts`: a moeda é atributo da linha do plano de contas, e por D4 isso vale para **toda** linha, inclusive as de sistema.

- *Alternativa considerada:* editável até o primeiro lançamento (a redação anterior desta decisão). Rejeitada pelas três razões acima; o ganho — corrigir sem recriar uma conta vazia — é coberto por apagar e recriar.
- *Alternativa considerada:* permitir a troca e reinterpretar o histórico. Rejeitada: reescreve em silêncio o significado de toda entry já gravada.

### D28 — O locale do dispositivo resolve as duas moedas iniciais, e o modelo perde o seu padrão

Duas coisas precisavam de uma definição e não tinham: a moeda da conta que o app cria sozinho, e a moeda base de consolidação. A redação anterior de D18 dizia que a base era "semeada na criação da primeira conta" — o que era **circular**, porque essa conta é a que `EnsureDefaultAccountUseCase` cria sem escolher moeda, caindo no `BASE_CURRENCY = "BRL"` do razão. A base derivava BRL de BRL, e nenhum usuário fora do Brasil tinha caminho para outra coisa.

**As duas passam a ser resolvidas pelo locale do dispositivo, na primeira execução.** Se a moeda do locale não estiver no catálogo curado (duas casas decimais, D14), cai numa constante declarada — que passa a ser *último recurso*, e não padrão.

Isto não acrescenta máquina nova: o app **já** deriva moeda do locale, e é exatamente o que `NumberFormat.getCurrencyInstance()` e `NSLocale.currentLocale` fazem hoje dentro do `CurrencyFormatter`. É por isso que um aparelho em `en-US` renderiza `$` sobre valores em real. A derivação existe; o que faltava era usá-la para **decidir** em vez de para *formatar* — e D10, que tira a moeda do locale na hora de exibir, é a outra metade da mesma correção: o locale deixa de dizer *em que moeda o número está* e passa a dizer, uma vez, *em que moeda este usuário vive*.

**E o modelo perde o seu padrão.** `Account.currency` e `AccountEntity.currency` deixam de ter valor default, de modo que **nenhuma conta é construível sem que alguém decida a sua moeda** — o compilador cobra, como em D5. Com isso `BASE_CURRENCY` sai de `:core:ledger`: o razão passa a saber que moeda existe e nada além disso, sem opinião sobre qual. Remover o default do Kotlin é neutro no schema — um default de construtor não emite `DEFAULT` em SQL, e a coluna segue `NOT NULL`.

A base é semeada **uma vez**. Trocar o locale do dispositivo depois MUST NOT mudá-la: seria mover em silêncio toda figura consolidada do histórico por causa de uma viagem.

### D13 — O limite de um orçamento carrega a moeda escolhida na sua criação

Uma categoria é dimensão, não conta: não tem moeda, e as suas entries podem estar em várias. O gasto de "Alimentação" é, por natureza, leitura por moeda — e cai nas duas partes de D9 sem regra própria: uma moeda passa exata, duas ou mais consolidam.

**O limite de um orçamento é denominado, e a denominação vem das contas cadastradas** — nunca da moeda base, que é preferência de exibição e não tem nada a dizer sobre um valor que o usuário digita. O progresso é o gasto da categoria **reduzido à moeda do limite**, e é exato quando não houve conversão.

A escolha só é **oferecida** quando existe escolha a fazer:

| moedas distintas entre as contas cadastradas | formulário de orçamento |
|---|---|
| uma | **sem controle** — assume a moeda da conta padrão |
| mais de uma | seletor, pré-selecionado com a moeda da conta padrão |

A conta padrão é a sugestão porque é onde o usuário de fato transaciona; a moeda base não é, porque ela responde *em que moeda ele lê totais*, não *em que moeda ele gasta*. Para o usuário monomoeda, o formulário de orçamento fica **idêntico ao de hoje** — nem um controle a mais.

Isso parece contradizer D23, que exige a linha de moeda **sempre visível** no formulário de conta, e não contradiz. A diferença não é de gosto: **o formulário de conta é a única porta pela qual uma segunda moeda nasce** — se ele esconder o controle enquanto houver uma moeda só, nunca haverá uma segunda, e a porta tem de estar sempre aberta. O formulário de orçamento não cria moeda alguma: ele escolhe entre as que já existem. Com uma existindo, não há o que escolher, e o valor não é um *default* silencioso — é a única resposta possível.

Denominar o limite na base era o desenho anterior, e produzia um custo indevido: um usuário com **tudo em dólar** e a base resolvida em real — que por D9 vê toda leitura exata e sem marca — teria o formulário de orçamento pedindo um limite em reais e a barra de progresso comparando reais com dólares, aproximada. Ele é monomoeda e pagaria o preço do multimoeda, pela porta da **entrada** em vez da leitura. Agora ele não escolhe nada, não vê controle novo, e o limite nasce em dólar porque é a moeda da conta dele.

É a generalização de D17 (*"um valor de fachada é denominado pela conta que ele nomeia"*) para o caso em que a fachada **não nomeia conta alguma**: um orçamento não tem escopo de conta, então a sua denominação não é derivável — e por isso é declarada, uma vez, e nunca herdada de uma preferência que pode mudar.

A denominação escolhida MUST NOT mudar depois, pela mesma razão de D12: reinterpretar um limite gravado é reescrever em silêncio o significado de um número que o usuário digitou. Trocá-la é criar outro orçamento.

Consequência que permanece e é aceita: quando a categoria tem gasto em moeda diferente da do limite, **o progresso pode se mover por variação de taxa, sem gasto novo algum** — e vem marcado como aproximado. É o preço de haver um único número, e agora só é pago por quem de fato mistura moedas. É a família de bugs que o Firefly III acumula em orçamento multimoeda (#1350, #3875, #9810, #9858, #11964): lá sem marca e sem dono único, aqui com ambos, e sem cobrar de quem não mistura.

- *Alternativa considerada:* limite sempre na base, com o progresso aproximado quando a categoria tem gasto em outra moeda. Rejeitada pelo caso acima: cobra do usuário monomoeda um custo que é do multimoeda, e o cobra no lugar mais visível do app.
- *Alternativa considerada:* limite implicitamente na "moeda única do app quando há uma só, base quando há várias". Rejeitada: o significado do número gravado mudaria no dia em que o usuário criasse a segunda moeda, que é o modo de falha que D12 e D17 existem para impedir.

### D14 — O expoente da moeda permanece 2, e o conjunto oferecido é restrito a isso

`(amount * 100).roundToLong()` na escrita e a fronteira de leitura em `Double` permanecem. As moedas oferecidas no seletor são as de duas casas decimais.

Premissa **deliberada**: suportar JPY (0 casas) ou KWD (3) não é acrescentar um campo, é refazer toda a conversão `Double`↔centavos do razão e da UI, incluindo `MoneyInputTransformation`. Fazê-la junto misturaria duas mudanças de risco muito diferente.

A moeda continua um `String` ISO 4217 na persistência. O catálogo do que é oferecido mora na camada de consolidação, que é quem tem opinião sobre quais moedas o app suporta.

### D15 — A perna de conversão não carrega dimensão

Uma perna de conversão MUST NOT herdar a dimensão da perna cujo resíduo ela absorve.

Sem isso, o pagamento de fatura cruzado **não persiste**: a perna `LIABILITY` carrega a dimensão da fatura, e se o escriturador a copiasse para a perna de resíduo, `rejectIfDimensionLandsWrong` (`LedgerEntryWriter:118`) recusaria a transação inteira — `DimensionKind.INVOICE.landsOn` é `{LIABILITY}`, e `CONVERSION` não está lá.

E é o que faz sentido contábil: a dimensão responde "a que sub-razão esta perna pertence". O resíduo cambial não pertence à fatura; ele pertence ao câmbio. Somá-lo ao devido da fatura seria contar como dívida do cartão o custo de ter trocado moeda.

### D16 — A perna primária passa a nomear o critério que ela sempre usou

`Transaction.primaryEntry` é hoje `monetaryEntries.minByOrNull { it.amount }`, e `Ledger.kt` `sourceLeg()` é o mesmo critério. Ele passa a ser, explicitamente, a perna monetária de **valor negativo** — a que o dinheiro deixou.

**Não é correção de defeito, e vale dizer por quê**, porque o contrário é a leitura natural: uma transação balanceada com duas pernas monetárias tem exatamente uma negativa e uma positiva, então `min` já devolve a negativa — independentemente das moedas envolvidas e de qualquer câmbio. O critério atual produz o resultado certo hoje e continuaria produzindo depois desta change.

O que muda é a **fragilidade**: `min` sobre `Long` de moedas distintas é uma comparação que só está certa por um invariante que ele não enuncia. No dia em que existir transação com duas pernas monetárias de mesmo sinal, ou em que alguém raciocinar sobre o código sem reconstruir o argumento, o critério vira defeito silencioso. Nomear o que se quer — a perna negativa — custa uma linha e remove a dependência tácita.

Uma transação sem perna monetária negativa (a compra em cartão, cuja única perna monetária é o passivo creditado) continua sendo lida pela perna que já é lida hoje.

Isto não é detalhe de apresentação: `presentation-mapping` já exige que a escolha da perna neutra tenha **um dono**, e todo item de lista sem perspectiva passa por ele (`TransactionUiMapper:37`, `ItemDisplayAmount:27-44`).

### D17 — Um valor de fachada é denominado pela conta que ele nomeia

Recorrência, parcelamento, limite de cartão e limite de orçamento guardam um `Double` sem moeda. Enquanto a moeda for uma só, isso é inócuo; deixa de ser no instante em que a fachada aponta para uma conta de outra moeda.

O caso alcançável hoje, e o único que produz **dado errado em silêncio fora do razão**: `ConfirmRecurringUseCase:34-46` permite redirecionar conta e cartão no momento da confirmação, com `amount = recurring.amount` por default. Confirmar uma recorrência criada numa conta BRL apontando-a para uma conta USD grava o número cru como se fosse dólar.

A regra: um valor de fachada é denominado na moeda da conta que a fachada nomeia, e **transportá-lo para uma conta de outra moeda é recusado**, não convertido. Recusar, e não converter, porque converter exigiria escolher uma taxa em nome do usuário no meio de uma confirmação — decisão que ele não pediu e não vê.

Os outros três caem por consequência, sem regra própria: o parcelamento é denominado na moeda do cartão, que D12 torna imutável; o limite do cartão idem — e é por isso que o medidor `limite − devido` (`CreditCardCard:215-265`) é garantidamente monomoeda; e o limite de orçamento é denominado na base (D13).

### D18 — A moeda base e a taxa moram numa feature nova

D11 exige uma tela que edita a taxa, e a moeda base é preferência do usuário. Não existe feature de configurações no repositório, e pela arquitetura do projeto isso é um `feature/settings/api` + `impl` inteiro — rota `@Serializable`, `NavGraphBuilder.settingsGraph()`, módulo Koin, entry point. Não é detalhe de implementação: sem isso a change não é implementável.

A moeda base é resolvida pelo **locale do dispositivo** na primeira execução (D28), e a v1 não oferece trocá-la. O requisito que descreve a troca existe assim mesmo, para que a implementação não a torne impossível: nada de convertido é persistido, então a troca é derivação — mas oferecê-la é escopo próprio.

A preferência precisa ser **observável**: toda figura consolidada reage à sua mudança. `Settings` é bindado como `single<Settings>` sem fluxo; o precedente de preferência observável no projeto é `DashboardPreferencesRepository`, e é a forma a seguir.

**E "reage" precisa nomear um mecanismo, porque hoje não existe nenhum que a alcance.** Dos 21 membros do `IEntryRepository`, **19 são `suspend`**, e o único gatilho reativo do app é um `SELECT COUNT(*) FROM entries` (`EntryDao.kt:129`). Cadastrar, corrigir ou remover uma taxa **não escreve em `entries`** — logo, sem mecanismo, nenhuma figura recomputa e esta frase seria falsa. O mecanismo é um **gatilho de invalidação composto**, publicado pela camada de consolidação: a preferência de base (`StateFlow` de um `single`) combinada com o `Flow` que o Room já dá sobre a tabela de taxas, fundido com o gatilho de razão existente onde os ViewModels já o fundem (`AccountsViewModel.kt:89`). Não é arquitetura nova: é a mesma costura, com uma fonte a mais.

Metade da promessa, porém, é **não-problema**, e vale dizer para não superdimensionar: a v1 não oferece trocar a moeda base (D28), então na prática o que muda em runtime é o acervo de taxas. Para a base, "observável" é requisito de forma — para que a troca, quando existir, não exija reescrever as leituras.

### D19 — Uma transação cruzada não é editável, e isso não é regra nova

`TransactionRepository.updateTransaction:258-294` recebe **uma única** `TransactionLeg` e chama `rewriteEntries(id, listOf(leg), contra)`. Não existe caminho de edição para duas pernas monetárias — nem para uma transferência comum, hoje.

E não precisa existir: o gate de editabilidade de `balanced-ledger` já recusa toda transação com número de pernas monetárias diferente de exatamente uma. Uma transação cruzada tem duas, e portanto **já cai no gate existente, sem regra nova**. As pernas de conversão não entram na contagem, por não serem monetárias (D2).

Registrado como Non-Goal explícito em vez de omissão: a operação é apagada e refeita, como toda transferência e todo pagamento de fatura já são.

### D20 — A figura multitermo tem degradação declarada, não decidida pelo layout

Uma figura de dois termos (`R$ 100,00 + US$ 50,00`) não cabe em toda superfície. As de largura fixa ou de gramática própria — medidor de limite, rótulo de barra de progresso de orçamento, `InstallmentCounter` ("3x de …"), e o documento HTML exportado, cujos modelos guardam **string já formatada** — SHALL exibir apenas o termo na moeda base, com a marca de aproximação e a indicação de que há parcela não convertida.

Isso é degradação **declarada**, e a alternativa não é "renderizar tudo": é o layout decidir sozinho, por truncamento ou quebra, o que uma superfície mostra de uma figura incompleta — que é como um número passa a mentir sem que ninguém tenha decidido isso.

### D21 — A marca de aproximação é um prefixo textual, e nunca cor

`≈` como prefixo, resolvido **dentro do formatador**, sempre mais externo que o sinal: `≈ +R$ 1.240,00`. É a porta que `DisplayAmount.format` já usa para concatenar `"+"` e `"-"` sobre o valor formatado, com o KDoc registrando que absorvê-lo ali é "um no-op demonstrável no texto". Ser o mais externo é também a única posição que sobrevive a um locale que ponha o símbolo à direita.

Cor está **descartada**, por duas razões independentes. A paleta já está inteiramente tomada por natureza de lançamento, e o âmbar que "aproximado" pediria é literalmente o `Adjustment` (`Color.kt:17`). E o projeto já escreveu a doutrina: *"a cor sozinha não carrega 'arquivado' — falha para quem não lê cor"* (`CategoryCard.kt:56-57`). Onde o app precisa de estado, ele usa ícone **e** rótulo.

Ícone e sufixo `(aprox.)` também descartados: o `SummaryCard` tem até seis linhas de dinheiro, e nem seis ícones de 16dp nem oito caracteres extras cabem numa coluna que já está em `SpaceBetween`.

A densidade é o que torna isso viável, e ela é consequência de D9, não escolha: a marca alcança ~6 famílias de **totais**, e **nenhuma lista** — extrato, saldo de conta, fatura, contador de parcelas e medidor de limite são monomoeda por construção (D17).

Como `≈` a 12–20sp não é alvo de toque, a explicação e a navegação vivem num **rodapé do card**, no idioma do `helperText` (13sp, `onSurfaceVariant`), renderizado só quando alguma linha do card é aproximada — como `AccountCard:199-213` já condiciona as linhas de ajuste e fatura a `!= 0.0`. Um elemento faz três trabalhos: explica a marca, revela que existe uma taxa, e é o alvo que leva à tela de taxas.

### D22 — Os termos de uma figura multitermo são empilhados, não justapostos

Justapor em linha não sobrevive ao `TotalBalanceCard` (`headlineMedium`) nem ao `BalanceCard.Default` (36sp). Os termos ocupam uma linha cada, alinhados à direita; o primeiro mantém o estilo tipográfico da superfície, os demais descem **um degrau** e vão para `onSurfaceVariant` — que é como `CreditCardCard:255-270` já desenha `disponível` a 20sp junto de `/ limite` a 14sp.

O degrau não significa "vale menos": significa "mesma figura, segunda linha". E o `+` fica colado ao termo, porque é operador de justaposição, não de soma.

Regra única, em toda superfície que comporte mais de um termo — nenhuma decide por conta própria, que é o que D20 exige.

### D23 — A linha de moeda é sempre visível no formulário

O controle reutiliza inteiro o `DefaultAccountSelector` (`AccountFormModal:207-290`): caixa de 52dp com o **símbolo como glifo** no lugar do ícone, título, subtítulo de estado, e a mesma mecânica de travamento que ele já tem — três subtítulos alternativos e a caixa trocando de `primary` para `onSurfaceVariant` quando `!canChange`, sem chevron. Consequência deliberada: "moeda travada" **lê igual** a "conta padrão não pode mudar", significante que o usuário deste app já aprendeu.

A linha é **sempre renderizada**, e não revelada por um link quando existe uma segunda moeda. Custo aceito: ~60dp permanentes no formulário para quem nunca vai tocá-la. Em troca, a moeda é atributo da conta do mesmo modo que o ícone é, e não uma feature que se descobre — o que também elimina a assimetria de um formulário que muda de forma conforme o estado global do app.

Por D12 a linha tem **dois comportamentos, decididos pelo modo do formulário e não pelo estado da conta**: na criação é um seletor, pré-selecionado com a moeda base; na edição é linha de estado travada, sempre. O `DefaultAccountSelector` já traz os dois — caixa de `primary` quando editável, `onSurfaceVariant` e sem chevron quando não.

O toque abre um `CurrencyPickerModal`, irmão do `IconPickerModal`, que já vive em `core/designsystem` exatamente por ser modal compartilhada entre features.

### D24 — Os três fluxos de dois valores compartilham uma gramática

**Ordem:** *quem participa* → *quanto* → *quando*. O `TransferBetweenAccountsModal` já é assim; `PayInvoiceModal` e `AdvancePaymentModal` pedem valor **antes** da conta, e são reordenados. Não é concessão ao multimoeda — é o alinhamento dos três —, mas muda o caso comum de todo usuário, inclusive quem nunca verá duas moedas. Aceito conscientemente: sem ele, revelar o segundo campo empurraria o seletor de conta para baixo com o dedo do usuário em cima dele.

**Revelação:** o segundo campo aparece por `AnimatedVisibility` quando `origem.currency != destino.currency`, no padrão que `ConfirmRecurringModal:123-178` já usa para revelar seletores em cascata. O caso de moeda única fica idêntico ao de hoje.

**Rótulos:** quando há duas moedas, "Valor" deixa de bastar e "valor de origem/destino" repete o que os seletores acima já dizem. Os campos nomeiam a conta — *"Sai de Nubank"* / *"Entra em Chase"* —, que é a frase que o extrato conta. O símbolo dentro do campo vem de graça de D10.

**A taxa derivada é exibida** como `supportingText` do segundo campo — o slot que os formulários já usam para dizer algo *sobre* um campo, aqui livre por este campo não ter validação a reportar. Lê-se como consequência, não como controle, que é o que um valor derivado é. Um campo desabilitado para a taxa foi descartado: pesa 56dp e sugere que ali houve entrada.

**Pré-preenchimento do segundo valor apenas quando a taxa conhecida é do mesmo dia.** Fora disso, o valor implícito vai para o *placeholder*, com a data no `supportingText` (*"pela taxa de 05/07: US$ 100,00"*). A regra não é de conveniência: como o valor digitado ali **vira** taxa colhida (D11), pré-preencher com uma cotação de duas semanas atrás gravaria a taxa velha como taxa nova, em silêncio e em laço.

**No pagamento de fatura, o campo somente-leitura que hoje mostra a dívida não muda de papel** — ele continua dizendo quanto se deve, exato, na moeda do cartão. O campo editável é **novo**, abaixo dele. Tornar editável um controle existente trocaria o seu significado.

**No pagamento antecipado, o teto `amount <= currentBillAmount` passa a valer sobre o campo na moeda do cartão**, e o campo na moeda da conta é livre — sem isso a validação compara moedas distintas.

### D25 — Configurações entra no `AppNavCatalog`; a porta real é o rodapé do card

`AppNavCatalog` é a fonte única de seções navegáveis, projetada em três afordâncias. Configurações entra imediatamente **antes** de `Support`, que o KDoc registra ser o último de propósito — os dois são "sobre o app".

Descartados: pôr as taxas no overflow de Contas (moeda base e taxa são preferências do app inteiro, não sub-assunto de contas, e ali ficam inencontráveis a partir de um orçamento aproximado), e um widget no dashboard (que o usuário pode ter desmontado — uma preferência não pode depender de um layout editável).

Mas o caminho **projetado** não é o catálogo: é o rodapé de D21, que aparece exatamente onde a taxa importa. E ele resolve sozinho a descoberta da taxa colhida — o rodapé nasce junto da primeira figura aproximada, que por construção só existe depois da primeira transação cruzada, que é o que colheu a taxa.

Uma taxa é sinalizada como **desatualizada aos 30 dias**, com cor `Warning` **e a palavra**, nunca cor sozinha (doutrina de D21). A data aparece sempre, atualizada ou não. Trinta dias não é derivável do domínio — é opinião sobre volatilidade —, e a razão de sinalizar em vez de só mostrar a data é que a consequência de uma taxa velha (o patrimônio de um mês passado exibido errado) não é visível de onde o usuário está.

A origem de cada taxa é dita no formato que `CategoryCard:58-75` já estabeleceu para procedência de estado — ícone de 16dp + `labelSmall` em `onSurfaceVariant` —, com dois ícones que o app já carrega e cujo sentido já está fixado: `SwapHoriz` (o ícone de transferência em `TransactionCard:173`) para a taxa colhida, `ModeEdit` (o glifo de "você editou isto", em `AccountCard:329` e `BalanceCard:133`) para a digitada.

### D26 — Uma recusa por moeda é prevenida no controle, não reportada como erro

O app tem dois registros de recusa, e a regra entre eles é: se o usuário pode consertar dentro da modal, é *inline*; se a recusa vem do domínio na escrita, é `ErrorModal`. As três recusas desta change ficam **antes** dos dois:

- **Moeda travada** não é erro, é estado: linha desabilitada com o motivo no subtítulo (D23). Nenhuma recusa alcança o escriturador.
- **Recorrência em conta de outra moeda** (D17): o `AccountSelector` oferece apenas contas da moeda da recorrência — a doutrina de D5 ("deixa de ser recusado e passa a ser inexprimível") aplicada à UI, e o que `TransferBetweenAccountsModal` já pratica ao excluir a origem dos destinos. Mas uma lista silenciosamente mais curta é mentira por omissão, então o seletor diz por que encolheu. A recusa do domínio permanece como rede, e nunca é o caminho projetado.
- **Resíduos de mesmo sinal** (D1) é defeito, não erro de usuário: num formulário onde um campo é "sai" e o outro "entra", os resíduos se opõem por construção, e a guarda só é alcançável por valor zerado — que o `enabled` do botão já barra, **desde que passe a cobrir o segundo campo**. É a alteração de validação mais fácil de esquecer.

E **falta de taxa não é erro nenhum**: a figura ganha um termo (D9). Nada recusa, nada bloqueia, nada pisca.

### D27 — Uma taxa sobrevive à transação que a originou

Apagar uma transação cruzada MUST NOT remover a taxa que ela colheu. Em compensação, a tela de taxas permite **remover** uma taxa, e não apenas corrigi-la.

O argumento é sobre o que uma taxa **é**: uma observação sobre o mundo numa data, não uma propriedade da transação. A transação foi a *ocasião* de aprendê-la, não a dona dela. E a consequência decide junto: apagar um lançamento errado de março não pode mover em silêncio o patrimônio daquele mês — o patrimônio de março depende da taxa de março, que continua sendo verdade sobre março independentemente de qual lançamento a revelou.

É o comportamento do GnuCash, que grava a taxa do diálogo de transferência no *price database* com a origem `PRICE_SOURCE_XFER_DLG_VAL` e não a remove quando a transação some; e do Beancount, onde a diretiva `price` é inteiramente separada de transação e não há acoplamento a desfazer. O contraexemplo é o Ledger CLI, em que o `@` faz parte do posting e o histórico de preços é **derivado** — mas ali não existe repositório de preços próprio. Quem tem tabela, não apaga.

A remoção na tela é o **corolário obrigatório**, não uma conveniência: se a taxa sobrevive à sua origem, o cenário em que o usuário apagou uma transação com erro de digitação deixa presa uma taxa errada que nenhum caminho alcança. É a mesma razão pela qual o GnuCash embarca um *Price Editor*. Corrigir não basta — uma taxa colhida por engano numa data em que nenhuma outra existe precisa poder deixar de existir, e não ser substituída por um palpite.

Consequência de leitura, e ela é desejável: removida a única taxa de uma moeda, as figuras daquele período voltam a exibir o termo próprio daquela moeda (D9) em vez de um valor convertido por uma taxa que ninguém mais sustenta.

### D29 — A base só aparece onde houve consolidação, e o teste monomoeda é cego a isso

A moeda base é exibida **apenas** em figura que passou por consolidação. Onde o razão devolveu uma única moeda — saldo de conta, devido de fatura, linha de extrato, parcela —, a exibição usa **aquela** moeda, e a base não aparece. É o que decorre de D8 e de D9, mas precisa ser afirmado por si, por causa do que vem a seguir.

**A violação é invisível na configuração mais comum.** Para um usuário cujas contas estão todas na base, exibir a base e exibir a moeda da conta produzem *exatamente o mesmo texto*. Um saldo de conta ligado à base por engano passa em todo teste, em toda revisão e em todo uso — até o dia em que alguém cria uma conta em dólar e vê o saldo dela em reais, sem conversão, apenas com o símbolo errado.

E o plano **cria a oportunidade** desse erro: a tarefa 1.1 instrui a passar a moeda base explicitamente nos sítios que agregam entre contas, porque ali ela é de fato a resposta certa. Os dois usos ficam a uma linha de distância um do outro, com aparência idêntica e significados opostos.

Daí a consequência que importa para o plano: **3.9 — o teste de regressão monomoeda — não prova este requisito, e não pode.** Ele afirma que nada mudou para quem usa uma moeda só, e é justamente essa configuração que torna a violação inobservável. O gate precisa de um segundo lado (3.10, repetido em superfície em 7.8): uma conta cuja moeda **difere da base**, exercitando saldo, extrato, fatura e parcela.

Registrar isso como decisão, e não apenas como tarefa de teste, é o que impede que a segunda metade do gate seja lida como redundante e cortada por economia.

### D30 — As contas legadas são reetiquetadas pelo locale, uma vez e em silêncio

Todo banco existente tem `currency = 'BRL'` em toda linha — não porque alguém escolheu, mas porque era o default do modelo. E `CurrencyFormatter` sempre formatou pelo locale do dispositivo: um usuário nos Estados Unidos **sempre viu `$`** enquanto o seu dado dizia BRL. A divergência nunca apareceu em tela.

Isso importa para o que a change faz: por D10 a moeda do dado passa a mandar no símbolo, e por D12 ela é imutável. Sem tratamento, todo usuário fora do Brasil veria o app inteiro virar `R$` e **não teria como corrigir** — as contas dele têm lançamentos, então não podem ser apagadas e recriadas.

**A migração reetiqueta, e "a migração" é literal: é a `MIGRATION_10_11`.** Se a moeda do locale diferir da constante legada e pertencer ao catálogo oferecido, as contas existentes passam a ser denominadas na moeda do locale. É reetiquetagem, **não conversão** — **nenhum valor e nenhum saldo mudam; a denominação de `accounts` e a de `entries` mudam junta, na mesma transação** —, e `Σ = 0` por moeda continua valendo porque a moeda de toda linha muda junto.

A redação anterior dizia "nenhuma entry é tocada", e isso era **incompatível com a própria change**: se as contas passassem a USD e o histórico continuasse dizendo BRL, as agregações por moeda partiriam a história de cada conta em duas moedas — e `LedgerBalanceCheck`, que agrupa por `(transactionId, currency)` sem consultar `accounts`, deixaria de poder ser lido como verdade sobre a conta. Toda linha das duas tabelas está em `'BRL'` hoje, então o `UPDATE` é exato e nenhum número se move.

**Por que a migração, e não um passo de app:** não existe passo de inicialização de app neste projeto. `App.kt` apenas seta o user-id, e `EnsureDefaultAccountUseCase` roda de `DashboardViewModel.init:50` num `launch` *fire-and-forget* concorrente com os fluxos do dashboard — não há nenhum ponto com garantia de acontecer uma vez e antes de qualquer leitura. A migração tem as três propriedades de graça: roda uma vez, **registra-se sozinha** pelo `user_version` (sem flag a inventar nem a manter), e precede toda leitura por construção.

**A moeda-alvo entra injetada, já resolvida.** `core/database` depende de `:core:ledger` e `:core:model`, **não** de `:core:common`, onde vive o resolvedor de locale — e não deve passar a depender: a migração não precisa conhecer locale nem catálogo, precisa de um código de moeda. A migração é **parametrizada** (`fun migration1011(relabelCurrency: String?)`), com o valor resolvido e validado contra o catálogo fora dela e fornecido na construção do banco. É o mesmo movimento que `DimensionWriteGuard` já faz no razão: o módulo de baixo recebe o que não pode nomear. E `null` significa "não reetiquetar", que é o caso comum.

A objeção de que uma migração que lê ambiente deixa de ser determinística **já está vencida no repositório**, e por precedente mais forte: `MIGRATION_3_4` (`Database.kt:130-136`) lê o relógio e o fuso do dispositivo (`strftime('%s','now')`, `'localtime'`). Com o parâmetro, esta é *mais* determinística que aquela — o teste de migração fixa o argumento.

E é a leitura mais honesta do dado: o `'BRL'` gravado nunca foi fato visível ao usuário, e a moeda que ele acreditou ter durante todo o uso do app é a que estava na tela. Reetiquetar faz o dado dizer o que o usuário sempre leu.

**O falso positivo, e ele é aceito:** um brasileiro cujo dispositivo esteja em região estrangeira tem as contas reetiquetadas para a moeda daquela região, sem aviso, e D12 impede desfazer. Duas coisas o estreitam. Primeiro, quem decide é a **região** do locale e não o idioma — `NumberFormat.getCurrencyInstance()` resolve pelo país —, então a interface em inglês com região Brasil não dispara nada. Segundo, o catálogo curado barra moedas não oferecidas, que caem no caso silencioso de manter a constante.

Consequência aceita e registrada: para quem cair no falso positivo, o estado é **irreversível pelo app**. A alternativa considerada era perguntar uma vez aos usuários de locale não-BRL, o que acertaria os dois casos ao preço de uma tela de migração; foi rejeitada em favor de zero atrito.

**Reetiquetar contradiz D12?** Não, e o projeto já tem o precedente escrito: `balanced-ledger` registra que arquivar não gera baixa em runtime *"mas a migração gera, e o dado migrado obedece às mesmas regras que o novo"*. Uma migração pode fazer o que o runtime proíbe, porque ela acontece **antes** de a moeda daquela conta ser fato observável. Depois dela, a imutabilidade vale sem exceção.

A reetiquetagem SHALL rodar **uma vez**, e o registro de que rodou é o próprio `user_version` do banco: uma troca posterior de região não pode dispará-la de novo porque a migração `10 → 11` não roda duas vezes. Nenhuma flag é criada, e nenhuma precisa ser mantida correta — o que seria, de outro modo, exatamente a mudança silenciosa de significado que D28 proíbe para a moeda base.

## Riscos / Trade-offs

- **Abrir o conjunto fechado de tipos de conta custa menos do que parece — e o risco real está noutro lugar.** Existem **três** `when` exaustivos sobre `AccountType` em todo o repositório (`AccountTypeMapper:13` e `:21`, e `systemAccountId` em `LedgerEntryWriter:158`, já coberto por D4); não há uso algum de `AccountType.entries`/`values()` nem `@Serializable` sobre ele. E `AccountEntity.Type` é persistido pelo suporte nativo do Room como `TEXT`, sem `TypeConverter`, de modo que **acrescentar um membro não altera o schema e não exige migração**. O risco de verdade não está nos `when`, e sim nos predicados por literal SQL da `EntryDao` (`a.type = 'EQUITY'`, `IN ('ASSET','LIABILITY')`), que o compilador não alcança, e nas somas cruzadas de `Double` espalhadas pelos ViewModels.
- **Uma tabela nova, uma coluna nova, e uma migração de verdade.** A tabela de taxas (D11) e a coluna de moeda do limite de orçamento (D13) levam `AppDatabase` de `version = 10` para `11`, com `MIGRATION_10_11` registrada, o schema `11.json` exportado (o plugin de convenção exporta schemas) e `MigrationSchemaEquivalenceTest` — hoje cobrindo apenas `7 → 10` — estendido. Nenhum valor gravado é alterado: todo banco existente está inteiramente em BRL, então a moeda que a coluna nova recebe é exatamente a que já denominava cada limite. Mas "sem migração" era falso a partir do momento em que a taxa ganhou data.
- **`netWorth()` não tem consumidor de produção.** A mudança de assinatura mais anunciada da change custa, hoje, apenas os ~25 fakes que implementam `IEntryRepository` inteiro. Ou é código morto, ou falta o consumidor — e o "saldo total" que o dashboard exibe vem de `CalculateBalanceUseCase(accountId = null)`, não dele.
- **O custo real é de superfície, não de núcleo.** ~105 sítios de formatação de dinheiro em 10 módulos, nenhum dos quais conhece a moeda hoje; 12 modais usando `MoneyInputTransformation`; ~25 arquivos de teste que implementam `IEntryRepository` como stub completo; e `LedgerFixture` sem parâmetro de moeda, sem o qual nenhum teste cruzado é escrevível.
- **Superfície de leitura.** O critério é único e não enumerado: **toda agregação que não filtre por uma única conta passa a ser expressa por moeda**. Só as escopadas a uma conta (`balanceOf`, `balanceUpToMonth`, `accountPeriodTotals` → `AccountFlows`) permanecem escalares, porque ali a moeda é atributo da conta. **Toda leitura por dimensão entra no conjunto que muda**, `DimensionFlows` e `dimensionOwed` inclusive: nada no razão amarra uma dimensão a uma única conta, e presumir o contrário para a fatura exigiria que o razão consultasse `DimensionKind` na leitura — exatamente o conhecimento de fachada que D8 proíbe. Quem sabe que o resultado de uma fatura tem uma chave só é a feature de cartões, e é lá que a redução acontece.
- **Silêncio residual até a UI existir.** Entre gravar `leg.currency` corretamente e todas as leituras agregarem por moeda existe uma janela em que uma segunda moeda produziria números errados. Mitigação de ordenação: as leituras vêm **antes** da escolha de moeda no formulário — enquanto nenhuma conta puder ser criada em outra moeda, nenhum dado errado é produzível.
- **A soma de perímetros disjuntos perde dono.** `dashboard-balance-widgets` exige somar `assetMonthFlows` + `liabilityMonthFlows` fora do razão e "sem agregado dedicado". Com os dois devolvendo por moeda, essa soma passa a ser soma de mapas — que **não** é conversão e portanto não é da camada de consolidação, e **não** pode ser do tipo de exibição, que D10 proíbe de combinar valores. Ela precisa de dono explícito, no razão, como operação sobre saldos por moeda.
- **Uma guarda que pode recusar câmbio legítimo?** A guarda de D1 (resíduos não todos do mesmo sinal) recusa uma intenção cruzada em que toda moeda ganha valor. Não conheço câmbio real com essa forma; se aparecer, é a guarda que muda, não a invariante.
