## Why

Uma transferência entre contas é a única operação corriqueira do app que, uma vez gravada, não pode ser corrigida — só apagada e refeita. O motivo não é uma decisão de produto: é um estreitamento de assinatura. `ITransactionRepository.updateTransaction` recebe **uma** perna (`leg: TransactionLeg`) e chama `rewriteEntries(id, listOf(leg), contra)`, enquanto a fronteira de escrita que ela invoca — `LedgerEntryWriter.rewriteEntries(transactionId, legs: List<TransactionLeg>, contra)` — sempre aceitou a lista. O razão nunca teve essa limitação; ela mora no andar acima dele, e o próprio KDoc do repositório registra a dívida: *"Any future support for editing those must change this shape."*

O custo do desvio recai sobre quem menos deveria pagá-lo. Apagar e refazer uma transferência entre moedas é a operação de mais alto risco do app: ela reescreve duas pernas monetárias, duas pernas de conversão e colhe uma taxa nova — tudo para consertar um dígito.

## What Changes

- **A transferência entre contas passa a ser editável no lugar**, pelo mesmo formulário que a criou (`TransferBetweenAccountsModal`), no molde criar-ou-editar que o `AccountFormModal` já pratica: um `Modal` parametrizado pela transação, `isEditMode = transaction != null`, título próprio e um único botão de salvar. Origem, destino, valor, valor de destino e data são todos corrigíveis — nenhum campo da criação é congelado na edição, porque nenhum deles é identidade.

- **O gate de editabilidade passa a falar por rótulo, e não por contagem de pernas.** Hoje o pagamento de fatura está fora do alcance da edição **por acidente**: ele cai no mesmo `monetaryEntries.size == 1` que barra a transferência. No instante em que a transferência entra, essa contagem deixa de ser o guardião do pagamento, e mantê-lo fora vira uma afirmação que precisa ser feita — sob pena de "fora de escopo" virar "esquecemos". O gate passa a admitir `TRANSFER` nominalmente; `PAYMENT` permanece recusado por declaração própria.

- **A transferência entre moedas entra pela mesma porta, sem regra própria.** Uma transferência cruzada tem pernas `{ASSET, CONVERSION}`, e `deriveTransactionLabel` já a classifica como `TRANSFER` — o fall-through que existe precisamente porque a conversão tem tipo próprio. Mono e cruzada são o mesmo caso para este gate.

- **Uma perna sobre conta arquivada continua congelando a operação inteira**, edição e remoção. Não é regra nova: `closedLegBlockingChange` já é o dono dela e `isChangeable` já a aplica. A transferência a herda sem código, e a consequência é deliberada — transferência para conta arquivada depois não se edita nem se apaga, e a tela diz por quê.

- **A taxa colhida não é revogada pela edição.** A edição colhe a taxa nova exatamente como a criação colhe, e não toca na antiga. Não é omissão: é a única leitura coerente com `currency-consolidation`, onde a taxa é observação sobre um dia e sobrevive à operação que a revelou — e onde apagar a operação **já** deixa a observação de pé. Uma edição que limpasse o que a remoção não limpa inverteria a regra. Na prática o caso dominante se corrige sozinho: a gravação é `REPLACE` sobre `(par, data, origem)`, então corrigir o valor sem mexer em data nem em contas **sobrescreve** a observação anterior. Muda a data ou uma das pontas e a antiga permanece, alcançável pela tela de taxas, como já acontece hoje ao apagar.

- **As validações da transferência ganham dono único antes de existir um segundo chamador.** As cinco regras hoje embutidas em `TransferBetweenAccountsUseCase` — valor positivo, valor de destino positivo, contas distintas, data não futura, contas existentes — valem inalteradas na edição. Escrever um segundo caso de uso que as repita violaria a primeira das regras de estilo do projeto; extraí-las é o que `CreateAccountUseCase`/`UpdateAccountUseCase` já fazem com `ValidateAccountNameUseCase`.

- **`updateTransaction` passa a aceitar `legs: List<TransactionLeg>`**, alinhando a reescrita ao vocabulário que a criação já usa e que a fronteira já aceita. Junto vai uma correção de asfixia: o `settlesALiability = false` **literal** que a reescrita passa hoje ao guarda de dimensões deixa de ser constante e passa a ser derivado das pernas, como `createTransaction` já o deriva. Para `ASSET → ASSET` o valor derivado é o mesmo `false`, então nada muda de comportamento — o que muda é que a próxima operação de duas pernas a chegar aqui não encontra uma constante mentindo em silêncio.

- **Fora de escopo, declarado:** editar pagamento de fatura, editar ajuste, editar parcela e converter uma transferência em outra natureza. O formulário de transferência só oferece contas, então a última é impedida pela forma, não por guarda.

## Capabilities

### New Capabilities
- `transfer-editing`: a transferência entre contas como operação corrigível no lugar — o formulário único para criar e corrigir, as duas pontas e a data como campos livres, as validações da criação valendo integralmente na correção, o congelamento herdado da conta arquivada, e a taxa colhida que a correção não revoga.

### Modified Capabilities
- `balanced-ledger`: o requisito de editabilidade derivada deixa de expressar o gate como "exatamente uma perna monetária" e passa a nomear os rótulos que admite; os dois cenários que afirmam que a transferência não é editável são invertidos, e o do pagamento de fatura passa a ter razão própria em vez de herdar a contagem. A reescrita de uma transação passa a aceitar o mesmo vocabulário de pernas que a criação, fechando a assimetria em que só a criação podia expressar mais de uma.
- `currency-consolidation`: o requisito de que uma operação cruzada cadastra a própria taxa passa a dizer o que acontece quando ela é **corrigida** — a correção colhe a taxa nova, sobrescreve a observação de mesmo par, data e origem, e MUST NOT revogar a que ficou noutra data ou noutro par.

## Impact

- **`core/ledger`** — `ITransactionRepository.updateTransaction` troca `leg: TransactionLeg` por `legs: List<TransactionLeg>` e perde o KDoc que documentava o estreitamento; `TransactionRepository.updateTransaction` repassa a lista e substitui o `settlesALiability = false` literal pelo `legs.settlesALiability()` que já existe no arquivo. `LedgerEntryWriter` **não muda** — `rewriteEntries` já recebe a lista, e o caminho de duas pernas (Σ = 0 por moeda em moeda única; resíduos opostos para a conversão na cruzada) é o mesmo que `createTransaction` percorre hoje.
- **`feature/transactions/impl`** — `ViewTransactionUiState.isEditable` passa a decidir por rótulo; `ViewTransactionModal` passa a escolher o modal de edição conforme a natureza da operação, obtendo o da transferência pelo entry point em vez de nomear o `impl` que o hospeda.
- **`feature/accounts/api`** — `AccountsEntry` ganha o modal de transferência, no molde do `accountFormModal(account: Account? = null)` que já existe: é o mecanismo pelo qual `feature/transactions/impl` alcança uma modal de `feature/accounts/impl` sem violar a regra `impl ⊄ impl`.
- **`feature/accounts/impl`** — `TransferBetweenAccountsModal`/`ViewModel` ganham o modo de edição e a semeadura a partir da transação; nasce o caso de uso de correção e o validador extraído de que ele e o de criação passam a depender; `AccountsModule` registra os dois e o entry point; `AccountsEntryImpl` implementa o membro novo.
- **`core/resources`** — o título do formulário em modo de correção, nos **dois** arquivos de strings (pt e en).
- **`core/analytics`** — o evento de correção de transferência, ao lado do de criação que já existe.
- **Testes** — `ViewTransactionGatesTest` inverte os casos de transferência e ganha o do pagamento de fatura permanecendo fora por razão própria; o caso de uso de correção e o validador extraído ganham suítes; e um teste de ponta a ponta cobre a correção cruzada, que é onde a reescrita de quatro pernas e a colheita de taxa se encontram.
