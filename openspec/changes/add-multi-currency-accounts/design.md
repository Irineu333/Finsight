## Context

O `:core:ledger` foi desenhado para multimoeda e nunca a exerceu. O estado atual, verificado no código:

- `AccountEntity.currency` e `EntryEntity.currency` existem, com default `"BRL"`; **toda linha de todo banco existente já tem `'BRL'`**. Nenhuma migração de schema é necessária.
- `LedgerEntryWriter` valida `Σ = 0` **por moeda** (`entries.groupBy { it.currency }`), mas grava `currency = BASE_CURRENCY` literal nas duas construções de `EntryEntity` (linhas 73 e 88) e na criação de conta de sistema (linha 171). O `groupBy` tem, hoje, sempre um grupo só.
- `TransactionLeg` não carrega moeda. `LedgerEntryWriter.orRejectIfClosed` **já carrega a `AccountEntity`** de cada perna para verificar fechamento — a moeda está ali, sem custo adicional de leitura.
- `SystemAccount` nomeia cinco chaves (`RECONCILIATION`, `EXPENSES`, `INCOMES`, e as duas de migração `CLOSED_ACCOUNT`/`CLOSED_CARD`), resolvidas por `AccountDao.getByTypeAndName(type, name)` e criadas sob demanda por `ensureSystemAccount`.
- `EntryDao` tem ~15 agregações. **Nenhuma** nomeia `currency`.
- `LedgerBalanceCheck.unbalancedTransactions()` já agrupa por `(transactionId, currency)` e já reporta a moeda no erro. Ele está pronto e não muda.
- `CurrencyFormatter` é um `expect class` **sem argumentos**, `single` no Koin, que usa `NumberFormat.getCurrencyInstance()` / `NSLocale.currentLocale`. Ele não formata *uma* moeda: formata *a* moeda do dispositivo.
- `DisplayAmount` declara de si, em KDoc e na spec `money-display`, que **não conhece moeda**.
- `feature/creditcards/impl/CreditCardRepository:57` grava `currency = BASE_CURRENCY` ao criar a conta `LIABILITY` do cartão — o único uso de `BASE_CURRENCY` fora do `:core:ledger`.

## Goals / Non-Goals

**Goals:**
- Moeda por conta e por cartão, escolhida na criação e imutável a partir do primeiro lançamento.
- `Σ = 0` por moeda **sem exceção alguma**, inclusive na transação que atravessa moedas.
- Nenhuma agregação capaz de somar moedas diferentes num mesmo número sobreviver à mudança.
- Exato onde é monomoeda; aproximado, e **visivelmente** aproximado, onde consolidou.
- Comportamento idêntico ao de hoje para um usuário de uma moeda só, sem flag e sem ramo de compatibilidade.

**Non-Goals:**
- Moedas de expoente ≠ 2. Ver D11.
- Taxa histórica por data; toda consolidação usa a taxa corrente. Ver D8.
- Busca automática de taxa fora da tela que a edita. Ver D8.
- Tela de ganho/perda cambial. Ver D2 (o dado passa a existir; expô-lo é mudança própria).
- Orçamento denominado em moeda não-base. Ver D10.
- Migração de schema — não existe nenhuma nesta mudança.

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

O passo 3 balancearia qualquer coisa, inclusive um erro de digitação, e por isso carrega a única guarda nova: **os resíduos não podem ser todos do mesmo sinal**. Se toda moeda envolvida ganha valor, a intenção cria dinheiro do nada — não é câmbio, é defeito, e a fronteira recusa. Com duas moedas isso é o par de sinais opostos que todo câmbio real tem.

- *Alternativa considerada:* **flexibilizar a invariante** para a transação cruzada — "exatamente duas moedas, taxa implícita, `Σ ≠ 0` tolerado". Rejeitada por três razões, em ordem de peso. (a) A invariante deixaria de ser verificável por quem lê `entries`: `LedgerBalanceCheck`, que hoje é um `HAVING total <> 0` de quatro linhas rodando em toda migração, precisaria saber distinguir a exceção legítima do dado corrompido — e a forma de saber seria consultar as contas das pernas, que é justamente o que ele evita fazer para permanecer válido antes e depois de qualquer reescrita do plano. (b) A spec `balanced-ledger` exige que soma zero, compatibilidade de dimensão e fechamento sejam validados *"no mesmo ponto, e em nenhum outro"*; uma segunda regra de balanceamento no mesmo ponto é o começo da erosão dessa frase. (c) A informação de câmbio ficaria implícita e não consultável, enquanto a conta de conversão a torna um saldo como outro qualquer.
- *Alternativa considerada:* o modelo do Firefly III — colunas `foreign_amount`/`foreign_currency` na transação. Rejeitada frontalmente: `balanced-ledger` proíbe que o sistema *"mantenha um modelo de perna paralelo espelhando o razão"*, e um segundo par valor/moeda no agregado é exatamente isso.
- *Alternativa considerada:* uma coluna `amountBase` em `entries`, no estilo dos apps de consumo, que manteria as ~6 agregações consolidadas como um `SUM` de coluna. Rejeitada: criaria um **segundo** invariante (`Σ amountBase = 0`) que o arredondamento do câmbio quebra por construção, congelaria a taxa dentro do razão, e tornaria a troca de moeda base uma migração de dados em vez de uma preferência.

### D2 — Uma conta de sistema **por moeda**

`ensureSystemAccount` passa a resolver por `(type, name, currency)`, e `AccountDao.getByTypeAndName` ganha a moeda na chave. Uma despesa em USD posta em `EXPENSES/USD`; o resíduo BRL de um câmbio posta em `CONVERSION/BRL`.

O que decide é o significado de `Account.currency`. Com uma nominal única, uma despesa em USD cairia na conta `EXPENSES` cujo `currency` é `'BRL'`, e o campo passaria a significar duas coisas: uma restrição real nas contas do usuário e um rótulo sem sentido em três linhas de sistema. Com uma por moeda, o campo significa **a mesma coisa em toda linha do plano, sem exceção** — que é a condição para D9 ("a moeda de uma conta é imutável") ser uma regra do plano de contas e não uma regra da fachada de conta.

Os nomes continuam sendo chaves de busca nunca renderizadas (design D10 original), então o usuário não vê nada. `CLOSED_ACCOUNT`/`CLOSED_CARD` são artefatos da migração `v7 → v9`, existem apenas em BRL e não são tocados.

Nenhuma migração: as contas de sistema existentes são `'BRL'` e continuam sendo, agora, as contas de sistema **de BRL**.

- *Alternativa considerada:* contas de sistema multimoeda, com `Account.currency` documentado como significativo apenas para `ASSET`/`LIABILITY`. Rejeitada: é uma exceção documentada num módulo cujo valor está em não ter nenhuma, e enfraquece D9 de regra do plano para regra de fachada.
- *Precedente:* é o que GnuCash faz com as suas *trading accounts* (`Trading:USD`, `Trading:BRL`) e o que Beancount faz com `Equity:Conversions` por commodity.

### D3 — A moeda de uma perna vem da conta, e `TransactionLeg` não ganha campo

`TransactionLeg` permanece `(type, amount, accountId, dimensionId?)`. A fronteira lê a moeda da `AccountEntity` que **já carrega** em `orRejectIfClosed`.

Isto não é economia de campo, é o que torna a classe inteira de erro inexprimível: não existe forma de dizer "poste 100 USD numa conta BRL", porque o chamador não tem onde dizer a moeda. Nenhuma validação precisa recusá-lo. E preserva intacto o requisito de que *"a intenção de escrita SHALL expressar cada perna por identidade de conta"* — a moeda é atributo da identidade, não um segundo dado ao lado dela.

### D4 — A taxa é derivada, nunca informada nem persistida

O chamador de uma transação cruzada informa **os dois valores** — 550 saíram daqui, 100 entraram ali —, que é literalmente o que o extrato mostra e o que o usuário sabe. Nenhum parâmetro de taxa existe em nenhum ponto do caminho de escrita.

A taxa aplicada (5,50) é derivável a qualquer momento das duas pernas, e por isso não é gravada — mesma decisão já tomada para o rótulo da transação (`deriveTransactionLabel`) e para o sinal de exibição (`AccountType.displaySign`). Uma taxa persistida ao lado das pernas seria um terceiro número obrigado a concordar com dois outros, sem nada garantindo que concorde.

Efeito colateral deliberado: o saldo das contas de conversão, avaliado à taxa corrente, **é** o ganho/perda cambial não realizado. O dado passa a existir de graça; expô-lo em tela é mudança própria (Non-Goal).

### D5 — O razão nunca consolida

Toda leitura do `IEntryRepository` capaz de atravessar contas passa a devolver valor **por moeda**. `netWorth()` deixa de devolver `Double` e passa a devolver o saldo por moeda — que é o que ele sempre foi, com uma moeda só.

A consolidação vive **acima** do razão: recebe o valor por moeda, aplica a taxa corrente e devolve uma figura aproximada na moeda base. `:core:ledger` continua sem conhecer moeda base e sem conhecer taxa.

O que decide é a frase que o módulo sustenta: *"every figure is `Σ entries`"* (`ledger-reporting`: **"Razão como única fonte de leitura"**). Um `netWorth()` que consulta uma taxa deixa de ser `Σ entries` e passa a ser `Σ (entries × taxa)` — a primeira leitura do razão a depender de algo que não é o razão. Devolver por moeda mantém a frase literalmente verdadeira e empurra a aproximação para onde ela é uma escolha de apresentação.

Consequência que vale registrar: **"moeda base" deixa de ser um fato contábil e passa a ser o que ele é — uma preferência de exibição.** Trocá-la é grátis e retroativo, porque nada convertido foi persistido.

`BASE_CURRENCY` permanece em `:core:ledger` com significado estreito: a moeda que uma conta nova recebe quando nenhuma é escolhida. A moeda base do usuário mora na camada de consolidação, sobre `Settings`.

- *Alternativa considerada:* um port `ExchangeRateProvider` injetado no razão, no espírito de `DimensionWriteGuard`/`TransactionRemovalHook`. Rejeitada: aqueles dois portos existem para que uma **fachada vete ou reaja a uma escrita** sem que o razão a nomeie. Uma taxa não é nem veto nem reação — é preferência de leitura, e injetá-la tornaria falsa a frase acima em troca de nada.

### D6 — Exatidão é derivada da leitura, não declarada pela tela

Um valor é **exato** quando nenhuma conversão participou dele. Isso se lê direto do saldo por moeda que o razão devolveu:

| saldo por moeda | leitura |
|---|---|
| nenhuma moeda (zero) | exato |
| uma moeda, igual à base | exato |
| uma moeda, diferente da base | aproximado |
| duas ou mais moedas | aproximado |

Nenhuma tela decide, nenhum agregado é marcado à mão, e a regra tem um dono só — o mesmo desenho da Regra de Derivação que o projeto aplica em todo o resto.

A consequência é o principal fator de redução de risco da mudança: **para um usuário com contas só em BRL, toda leitura devolve um mapa de uma chave igual à base, e a marca de aproximação não aparece em lugar nenhum do app.** O comportamento de hoje se preserva sem flag de feature, sem ramo de compatibilidade e sem caminho de código alternativo — ele é o caso particular do caso geral.

### D7 — Moeda e exatidão viajam **dentro** do tipo de exibição

`DisplayAmount` passa a carregar três coisas indissociáveis: o valor, a política de sinal e — novos — a **moeda** em que está denominado e se é **exato ou aproximado**.

O argumento é literalmente o que a spec `money-display` já escreveu para a política de sinal: *"Valor e política MUST NOT ser campos independentes de um modelo de UI: um valor construído sem a sua política, ou alterado sem ela, é o modo de falha que este requisito existe para tornar impossível."* Um valor que troca de moeda enquanto o símbolo ao lado não troca é o mesmo modo de falha com outra roupa — e é pior, porque "R$ 830,00" sobre um saldo em dólar é uma frase inteiramente plausível.

Isso exige reconciliar o requisito que hoje diz que o tipo *"MUST NOT ... conhecer moeda"*. A razão original permanece intacta e a redação passa a dizê-la com precisão: o que o tipo não pode é **calcular** — combinar dois valores, converter, somar. Carregar a denominação de um único número não é cálculo, é a legenda sem a qual o número não se lê. *Quanto* vale continua sendo do razão; *em que moeda está* e *se é aproximado* são propriedades de como aquela figura se lê, que é precisamente o que o tipo responde.

`CurrencyFormatter.format` deixa de derivar a moeda do locale do dispositivo e passa a recebê-la. O locale continua governando **formato** — separador, posição do símbolo —, que é o que ele legitimamente sabe.

### D8 — Taxa manual, local e única; a rede só existe dentro da tela que a edita

A taxa gravada localmente é a **verdade**. Uma fonte externa pode preencher o campo como sugestão dentro da tela de edição, e em nenhum outro lugar.

Isso mantém a rede fora de todo caminho de leitura: nenhum saldo, nenhum resumo e nenhum dashboard tem estado de carregamento, modo offline ou falha possível por causa de câmbio. O pior caso do app é uma taxa velha — e, coerente com D6, a idade da taxa é exibida junto de onde ela é editada.

Uma taxa por moeda **→ base**, e não uma matriz de pares: só se converte *para* a base, então N taxas bastam, e um par cruzado, se um dia for preciso, é derivável.

- *Alternativa considerada:* busca automática com cache, na leitura. Rejeitada: coloca uma falha possível e um estado de carregamento sob toda figura consolidada do app, em troca de precisão que D6 já declara não existir ali.

### D9 — A moeda de uma conta é imutável a partir do primeiro lançamento

A regra usa `IEntryRepository.hasEntries(accountId)` — o **mesmo** fato que já decide apagar-vs-arquivar em `account-lifecycle`. Nenhuma máquina nova: entre a criação e o primeiro lançamento a moeda é editável; a partir dele, o formulário a apresenta travada, com o motivo.

Ela pertence a `chart-of-accounts`, não a `account-lifecycle`: a moeda é atributo da linha do plano de contas, e por D2 isso vale para **toda** linha, inclusive as de sistema — que ninguém edita, o que torna a regra vacuamente verdadeira ali em vez de inaplicável.

- *Alternativa considerada:* permitir a troca e reinterpretar o histórico. Rejeitada: reescreve em silêncio o significado de toda entry já gravada — os mesmos números passariam a valer outra coisa, sem que nada no razão registrasse que valiam outra antes.

### D10 — Orçamento e categoria consolidam de forma aproximada

Uma categoria é dimensão, não conta: ela não tem moeda, e as suas entries podem estar em várias. O gasto de "Alimentação" é, por natureza, uma leitura multimoeda — cai no lado aproximado de D6 pelo mesmo mecanismo de tudo o mais, sem regra própria.

O orçamento é denominado na moeda base, e o seu progresso é uma figura aproximada quando a categoria tem gasto em outra moeda. Consequência aceita e registrada: **o progresso de um orçamento pode se mover por variação de taxa, sem que nenhum gasto novo tenha ocorrido.** É o preço de haver um único número, e ele vem marcado como aproximado.

- *Alternativa considerada:* orçamento por moeda, ou progresso multivalorado. Rejeitada nesta mudança (Non-Goal): multiplica a fachada de orçamento por N sem demanda, e a marca de aproximação já comunica honestamente o que o número é.

### D11 — O expoente da moeda permanece 2, e o conjunto oferecido é restrito a isso

`(amount * 100).roundToLong()` na escrita e a fronteira de leitura em `Double` "reais" permanecem. As moedas oferecidas no seletor são as de duas casas decimais.

É uma **premissa deliberada, não um esquecimento**: suportar JPY (0 casas) ou KWD (3) não é acrescentar um campo, é refazer toda a conversão `Double`↔centavos do razão e da UI, incluindo `MoneyInputTransformation`. Fazê-la junto misturaria duas mudanças de risco muito diferente numa só.

A moeda continua um `String` ISO 4217 na persistência — não há migração a fazer, e o razão não precisa saber mais do que isso. O catálogo do que é oferecido ao usuário mora na camada de consolidação, que é quem tem opinião sobre quais moedas o app suporta.

## Risks / Trade-offs

- **Superfície de leitura.** ~6 agregações da `EntryDao` ganham `GROUP BY e.currency`, e cinco tipos de retorno do `IEntryRepository` mudam de forma. É mecânico, mas amplo, e todo consumidor recompila. Mitigação: as leituras escopadas a **uma** conta ou à dimensão de **uma** fatura são monomoeda por construção e não mudam de forma — só ganham a moeda no resultado. O trabalho fica confinado ao que de fato atravessa contas.
- **Silêncio residual até a UI existir.** Entre gravar `leg.currency` corretamente e todas as leituras agregarem por moeda existe uma janela em que uma segunda moeda produziria números errados. Mitigação de ordenação: as leituras vêm **antes** da escolha de moeda no formulário — enquanto nenhuma conta puder ser criada em outra moeda, nenhum dado errado é produzível.
- **Uma guarda que pode recusar câmbio legítimo?** A guarda de D1 (resíduos não todos do mesmo sinal) recusa uma intenção cruzada em que toda moeda ganha valor. Não conheço câmbio real com essa forma; se aparecer, é a guarda que muda, não a invariante.
