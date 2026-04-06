# Spec: Dívidas

> A spec descreve *o que* o sistema deve fazer. Não inclui como implementar.
> Código só é válido aqui quando define padrões arquiteturais obrigatórios.

---

## Contexto

O que já existe no sistema relevante para essa feature:
- `Account` — contas bancárias/carteira que servirão de origem para os pagamentos
- `Operation` + `Transaction` — infraestrutura de lançamentos que registrará os pagamentos efetuados
- `Category` — categorias de despesa já existem e podem classificar a dívida
- `CreditCard` + `Invoice` — referência de ciclo de vida com estados e operações por estado
- `Recurring` + `RecurringOccurrence` — sistema de pendências com confirmação/skip que serve de modelo para parcelas
- `GetPendingRecurringUseCase` — padrão de detecção de pendências calculada em memória (sem status PENDING no banco)
- `ConfirmRecurringUseCase` / `SkipRecurringUseCase` — padrão de efetivação que cria Operation + Occurrence
- Modais de formulário, confirmação de exclusão e `ModalManager` já implementados
- Navegação via `AppRoute` (sealed class) com rotas tipadas

O usuário hoje não tem como registrar dívidas contraídas, acompanhar o saldo devedor nem registrar pagamentos vinculados a uma conta.

---

## Objetivo

Permitir que o usuário registre dívidas que contraiu, acompanhe o saldo devedor ao longo do tempo e registre pagamentos que movimentam o saldo de uma conta.

---

## Comportamentos

### Caminho principal

**Listar dívidas**
```
Dado que existem dívidas cadastradas
Quando o usuário acessa a tela de Dívidas
Então vê a lista de dívidas ativas com: nome, valor total, saldo devedor e progresso de pagamento
     e vê as dívidas pagas em seção separada (ou ocultas por padrão)
```

**Cadastrar dívida sem lançamento inicial**
```
Dado que o usuário está na tela de Dívidas
Quando abre o formulário de nova dívida e preenche: nome, valor total, credor
     e não opta por registrar lançamento inicial
     e confirma
Então a dívida é criada com status ATIVA
     e o saldo devedor é igual ao valor total
     e nenhuma transação é gerada
```

**Cadastrar dívida com lançamento inicial**
```
Dado que o usuário está na tela de Dívidas
Quando abre o formulário de nova dívida, preenche os dados obrigatórios
     e opta por registrar lançamento inicial, selecionando uma conta
     e confirma
Então a dívida é criada com status ATIVA
     e uma transação de RECEITA é gerada na conta selecionada com o valor total da dívida
     e a data do lançamento é a data de início da dívida
```

**Registrar pagamento livre**
```
Dado que existe uma dívida ativa
Quando o usuário registra um pagamento informando: valor, data e conta de origem
Então uma transação de DESPESA é gerada na conta selecionada
     e o saldo devedor da dívida é reduzido pelo valor pago
     e se o saldo devedor chegar a zero, a dívida passa para status PAGA
```

**Visualizar parcelas pendentes**
```
Dado que existe uma dívida ativa com plano de parcelas
     e o dia de vencimento da parcela atual já chegou ou passou
     e a parcela ainda não foi confirmada nem ignorada
Quando o usuário acessa a tela de Dívidas
Então vê a parcela pendente destacada na dívida correspondente
     e pode confirmar ou ignorar a parcela diretamente
```

**Confirmar parcela pendente**
```
Dado que existe uma parcela pendente
Quando o usuário abre o modal de confirmação da parcela
     e confirma com valor (editável, padrão = valor total ÷ qtd de parcelas), data e conta de origem
Então uma transação de DESPESA é gerada na conta selecionada
     e o saldo devedor da dívida é reduzido pelo valor confirmado
     e a ocorrência da parcela é registrada como CONFIRMADA
     e se o saldo devedor chegar a zero, a dívida passa para status PAGA
```

**Ignorar parcela pendente**
```
Dado que existe uma parcela pendente
Quando o usuário opta por ignorar a parcela
Então nenhuma transação é gerada
     e o saldo devedor não é alterado
     e a ocorrência da parcela é registrada como IGNORADA
     e a parcela some da lista de pendências
```

**Visualizar detalhes de uma dívida**
```
Dado que existe uma dívida cadastrada
Quando o usuário abre os detalhes da dívida
Então vê: nome, credor, valor total, saldo devedor, data de início, data de vencimento (se informada)
     e vê o plano de parcelas (se configurado): quantidade, valor por parcela, dia do mês
     e vê o histórico de pagamentos realizados com data, valor e conta de origem
     e vê o histórico de ocorrências de parcelas (confirmadas e ignoradas)
```

---

### Casos de borda

**Dívida com plano de parcelas**
```
Dado que o usuário está cadastrando uma dívida
Quando configura um plano de parcelas informando quantidade e dia do mês de vencimento
Então o sistema exibe o valor estimado por parcela (valor total ÷ quantidade de parcelas)
     e o plano fica registrado com o dia de vencimento mensal
     e a cada mês, quando o dia de vencimento chega, a próxima parcela não-tratada aparece como pendente
     mas o usuário ainda pode pagar qualquer valor a qualquer momento via pagamento livre
```

**Parcelas pendentes não bloqueiam pagamentos livres**
```
Dado que existe uma dívida ativa com parcelas pendentes
Quando o usuário registra um pagamento livre
Então o pagamento é processado normalmente
     e as parcelas pendentes permanecem disponíveis para confirmação ou ignorar
```

**Dívida quitada com parcelas pendentes restantes**
```
Dado que uma dívida ativa tem parcelas pendentes e saldo devedor maior que zero
Quando o saldo devedor chega a zero (por pagamento livre ou confirmação de parcela)
Então a dívida passa para status PAGA
     e as parcelas restantes não aparecem mais como pendentes
```

**Pagamento que zera o saldo**
```
Dado que uma dívida ativa tem saldo devedor de R$200
Quando o usuário registra um pagamento de R$200
Então a transação de DESPESA é gerada normalmente
     e a dívida passa automaticamente para status PAGA
     mas NÃO é possível registrar novos pagamentos em dívidas com status PAGA
```

**Dívidas pagas**
```
Dado que existem dívidas pagas
Quando o usuário acessa a tela de Dívidas
Então as dívidas pagas não aparecem na lista principal
     e o usuário pode optar por exibi-las
```

**Reabrir dívida paga**
```
Dado que existe uma dívida com status PAGA
Quando o usuário reabri a dívida
Então o status volta para ATIVA
     e novos pagamentos podem ser registrados
     mas NÃO é excluído o histórico de pagamentos anteriores
```

**Editar dívida**
```
Dado que existe uma dívida cadastrada
Quando o usuário edita o nome, credor, data de vencimento ou plano de parcelas
Então as alterações são salvas
     mas NÃO é possível alterar o valor total de uma dívida que já possui pagamentos registrados
```

**Excluir dívida**
```
Dado que existe uma dívida com pagamentos registrados
Quando o usuário solicita exclusão e confirma
Então a dívida e seu histórico de pagamentos são excluídos
     mas as transações geradas pelos pagamentos permanecem nas contas (não são estornadas)
```

---

### Erros esperados

**Nome vazio**
```
Dado que o usuário está no formulário de dívida
Quando confirma sem preencher o nome
Então a dívida não é salva
     e a mensagem de erro exibida é "Informe o nome da dívida"
```

**Valor total inválido**
```
Dado que o usuário está no formulário de dívida
Quando informa um valor total igual a zero ou negativo
Então a dívida não é salva
     e a mensagem de erro exibida é "O valor deve ser maior que zero"
```

**Valor de pagamento inválido**
```
Dado que o usuário está registrando um pagamento
Quando informa um valor igual a zero, negativo ou maior que o saldo devedor
Então o pagamento não é salvo
     e a mensagem de erro exibida é "Valor inválido"
```

**Pagamento sem conta selecionada**
```
Dado que o usuário está registrando um pagamento
Quando não seleciona nenhuma conta de origem
Então o pagamento não é salvo
     e a mensagem de erro exibida é "Selecione uma conta de origem"
```

---

## Regras de negócio

- O saldo devedor é calculado como: `valor total − soma de todos os pagamentos registrados`
- Um pagamento não pode exceder o saldo devedor atual
- Uma dívida com saldo devedor igual a zero assume automaticamente o status PAGA
- Dívidas PAGAS não aceitam novos pagamentos nem geram novas parcelas pendentes
- Todo pagamento gera obrigatoriamente uma transação de DESPESA em uma conta
- O lançamento inicial (quando optado) gera uma transação de RECEITA na conta selecionada, com a data de início da dívida
- Não é possível alterar o valor total de uma dívida que já possui pagamentos, pois isso invalidaria o histórico
- A exclusão de uma dívida não estorna as transações já geradas pelos pagamentos
- Credor é informação textual livre (não é uma entidade do sistema)
- Data de vencimento global da dívida é opcional e independente do plano de parcelas
- **Plano de parcelas:** requer quantidade de parcelas e dia do mês de vencimento; o valor por parcela é `valor total ÷ quantidade` (calculado, não armazenado)
- **Detecção de parcela pendente:** calculada em memória — uma parcela é pendente quando a dívida está ATIVA, o dia de vencimento do mês corrente já chegou ou passou, o número da parcela não ultrapassa o total configurado e não existe ocorrência (CONFIRMADA ou IGNORADA) para aquele ciclo
- **Ciclo de parcela:** determinado pelos meses decorridos desde o mês de início da dívida (mês 1 = mês de criação); a parcela N é devida no mês N a partir do início
- **Confirmar parcela** é equivalente a registrar um pagamento livre, mas iniciado pelo sistema de pendências; o valor padrão é o valor estimado por parcela, porém editável
- **Ignorar parcela** registra a ocorrência como IGNORADA sem gerar transação; o saldo devedor não é alterado
- Pagamentos livres e confirmação de parcelas coexistem; ambos reduzem o mesmo saldo devedor

---

## Padrões obrigatórios

- **Tela de dívidas como `AppRoute`:** A tela de Dívidas deve ser uma rota top-level (não aninhada em Home), seguindo o padrão das demais telas (Contas, Cartões, Orçamentos).
- **Pagamentos via modal:** O registro de pagamento deve ser feito via `ModalBottomSheet`, seguindo o padrão de modais do projeto.
- **Status como enum com `toUiText()`:** O status da dívida deve ser representado como `enum class` com `val message: String` (para log) e extensão `toUiText()` (para UI), seguindo `InvoiceError` e similares.

---

## Fora do escopo

- Notificações ou lembretes de vencimento
- Juros, correção monetária ou multa por atraso
- Dívidas que terceiros têm com o usuário (contas a receber)
- Importação automática de dívidas a partir de extratos bancários
- Relatórios ou gráficos específicos de endividamento
- Agendamento automático de parcelas como transações futuras no extrato
- Integração das dívidas no Dashboard (resumo na tela inicial)
- Vinculação de dívidas a categorias no orçamento

---

## Critério de aceite

### Validação manual

1. Criar uma dívida sem lançamento inicial → dívida aparece na lista, saldo devedor = valor total, nenhuma transação gerada
2. Criar uma dívida com lançamento inicial selecionando uma conta → receita aparece na tela de Transações na conta selecionada
3. Registrar um pagamento parcial → saldo devedor reduz, transação de despesa aparece na conta selecionada
4. Registrar o pagamento final que zera o saldo → dívida passa para PAGA automaticamente, botão de pagamento some
5. Tentar registrar pagamento com valor acima do saldo devedor → erro exibido, pagamento não salvo
6. Excluir uma dívida com pagamentos → dívida some da lista, transações permanecem nas contas
7. Editar nome/credor/vencimento de uma dívida com pagamentos → alterações salvas
8. Tentar alterar valor total de dívida com pagamentos → opção bloqueada ou erro
9. Reabrir dívida paga → status volta para ATIVA, histórico de pagamentos preservado
10. Criar dívida com plano de parcelas (ex: 12x, dia 10) → valor por parcela exibido corretamente (total ÷ 12)
11. Com dia de vencimento já passado no mês corrente → parcela aparece como pendente na tela de dívidas
12. Confirmar parcela pendente (valor editável, conta selecionada) → transação de despesa gerada, saldo devedor reduz, parcela some das pendências
13. Ignorar parcela pendente → nenhuma transação gerada, saldo devedor inalterado, parcela some das pendências
14. Registrar pagamento livre em dívida com parcelas pendentes → saldo reduz, parcelas pendentes permanecem
15. Zerar saldo por pagamento livre enquanto ainda havia parcelas futuras → dívida PAGA, próximas parcelas não aparecem mais como pendentes

### Revisão de código

- [ ] `Debt` e `DebtInstallmentOccurrence` pertencem ao domínio, sem dependências de framework
- [ ] `DebtRepository` e `DebtInstallmentOccurrenceRepository` são interfaces no domínio
- [ ] `DebtError` e `DebtPaymentError` seguem o padrão com `message` e `toUiText()`
- [ ] Pagamentos usam use cases existentes de transação para gerar os lançamentos
- [ ] Saldo devedor é calculado em use case, não no ViewModel nem na UI
- [ ] Detecção de parcelas pendentes é calculada em memória (use case), sem status PENDING no banco
- [ ] Confirmar parcela cria Operation + DebtInstallmentOccurrence (CONFIRMADA), análogo a `ConfirmRecurringUseCase`
- [ ] Ignorar parcela cria apenas DebtInstallmentOccurrence (IGNORADA), sem Operation
- [ ] `DebtUiState` segue o padrão Loading/Empty/Content
- [ ] Dívidas PAGAS ficam fora do estado `Content` principal (seção separada ou flag de toggle)
- [ ] Exclusão de dívida apresenta modal de confirmação antes de executar
- [ ] Koin: repositórios como `single {}`, use cases como `factory {}`, ViewModels como `viewModel {}`
