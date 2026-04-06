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

**Visualizar detalhes de uma dívida**
```
Dado que existe uma dívida cadastrada
Quando o usuário abre os detalhes da dívida
Então vê: nome, credor, valor total, saldo devedor, data de início, data de vencimento (se informada)
     e vê o plano de parcelas (se configurado): quantidade, valor por parcela
     e vê o histórico de pagamentos realizados com data, valor e conta de origem
```

---

### Casos de borda

**Dívida com plano de parcelas**
```
Dado que o usuário está cadastrando uma dívida
Quando configura um plano de parcelas informando a quantidade
Então o sistema exibe o valor estimado por parcela (valor total ÷ quantidade de parcelas)
     e o plano fica registrado como referência
     mas o usuário ainda pode pagar qualquer valor a qualquer momento (pagamento livre)
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
- Dívidas PAGAS não aceitam novos pagamentos
- Todo pagamento gera obrigatoriamente uma transação de DESPESA em uma conta
- O lançamento inicial (quando optado) gera uma transação de RECEITA na conta selecionada, com a data de início da dívida
- Não é possível alterar o valor total de uma dívida que já possui pagamentos, pois isso invalidaria o histórico
- A exclusão de uma dívida não estorna as transações já geradas pelos pagamentos
- Credor é informação textual livre (não é uma entidade do sistema)
- O plano de parcelas é apenas uma referência visual; não cria agendamentos nem parcelas individuais no sistema
- Data de vencimento é opcional

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
- Agendamento automático de parcelas como lançamentos futuros
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
10. Criar dívida com plano de parcelas → valor por parcela exibido corretamente (total ÷ qtd)

### Revisão de código

- [ ] `Debt` e `DebtPayment` pertencem ao domínio, sem dependências de framework
- [ ] `DebtRepository` e `DebtPaymentRepository` são interfaces no domínio
- [ ] `DebtError` e `DebtPaymentError` seguem o padrão com `message` e `toUiText()`
- [ ] Pagamentos usam use cases existentes de transação para gerar os lançamentos
- [ ] Saldo devedor é calculado em use case, não no ViewModel nem na UI
- [ ] `DebtUiState` segue o padrão Loading/Empty/Content
- [ ] Dívidas PAGAS ficam fora do estado `Content` principal (seção separada ou flag de toggle)
- [ ] Exclusão de dívida apresenta modal de confirmação antes de executar
- [ ] Koin: repositórios como `single {}`, use cases como `factory {}`, ViewModels como `viewModel {}`
