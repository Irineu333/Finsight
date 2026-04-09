# Spec: Analytics

> A spec descreve *o que* o sistema deve fazer. Não inclui como implementar.
> Código só é válido aqui quando define padrões arquiteturais obrigatórios.

---

## Contexto

O app não possui nenhuma forma de rastreamento de uso hoje. As três plataformas suportadas são Android, iOS e Desktop/JVM. O Firebase já está integrado via `dev.gitlive` 2.1.0 (Firestore + Auth) para o módulo de suporte, mas Firebase Analytics ainda não está presente. Desktop/JVM não é suportado pelo Firebase Analytics.

---

## Objetivo

Rastrear quais features são utilizadas e como o usuário navega pelo app, para embasar decisões de produto.

---

## Comportamentos

### Caminho principal

**Rastrear navegação entre telas**
```
Dado que o usuário abre qualquer tela do app
Quando a tela é exibida
Então um evento de screen view é registrado com o nome da tela
```

**Rastrear ação principal de uma feature**
```
Dado que o usuário conclui uma ação rastreada com sucesso
Quando a operação é confirmada
Então um evento com o nome da ação é registrado
     e o evento inclui parâmetros de contexto relevantes
     mas NÃO são registrados dados pessoais ou financeiros do usuário
```

**Identificação de usuário**
```
Dado que o app é iniciado
Quando o analytics é inicializado
Então o user ID do Firebase Auth é associado à sessão de analytics
     e esse ID persiste entre sessões (gerenciado pelo Firebase Auth)
```

**Desktop sem analytics**
```
Dado que o app está rodando em Desktop/JVM
Quando qualquer evento de analytics é disparado
Então a chamada é ignorada silenciosamente (no-op)
     e nenhum erro é lançado
```

### Casos de borda

**Ação cancelada não é rastreada**
```
Dado que o usuário abre um modal de criação
Quando o usuário cancela sem confirmar
Então nenhum evento de ação é registrado
```

**Navegação duplicada não gera evento duplicado**
```
Dado que o usuário já está em uma tela
Quando a mesma rota é empurrada novamente
Então o comportamento de rastreamento segue o comportamento do NavHost
```

---

## Eventos rastreados

### Screen views

Todas as telas principais rastreadas com `screen_view`:

| Tela | `screen_name` |
|---|---|
| Dashboard | `dashboard` |
| Transactions | `transactions` |
| Accounts | `accounts` |
| Credit Cards | `credit_cards` |
| Invoice Transactions | `invoice_transactions` |
| Installments | `installments` |
| Budgets | `budgets` |
| Recurring | `recurring` |
| Categories | `categories` |
| Reports Config | `reports_config` |
| Reports Viewer | `reports_viewer` |
| Support | `support` |
| Support Issue | `support_issue` |

### Ações por feature

**Transactions**

| Evento | Parâmetros |
|---|---|
| `create_transaction` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `is_installment`: `true` \| `false`, `category`: nome da categoria |
| `edit_transaction` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |
| `delete_transaction` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |

**Accounts**

| Evento | Parâmetros |
|---|---|
| `create_account` | `is_default`: `true` \| `false` |
| `edit_account` | `is_default`: `true` \| `false` |
| `delete_account` | — |
| `transfer_between_accounts` | — |
| `adjust_account_balance` | — |

**Credit Cards**

| Evento | Parâmetros |
|---|---|
| `create_credit_card` | — |
| `edit_credit_card` | — |
| `delete_credit_card` | — |
| `close_invoice` | — |
| `pay_invoice` | — |
| `reopen_invoice` | — |
| `adjust_invoice_balance` | — |
| `delete_future_invoice` | — |
| `advance_invoice_payment` | — |

**Installments**

| Evento | Parâmetros |
|---|---|
| `create_installments` | `category`: nome da categoria, `installments_count`: quantidade de parcelas |
| `delete_installments` | `category`: nome da categoria, `installments_count`: quantidade de parcelas |

**Budgets**

| Evento | Parâmetros |
|---|---|
| `create_budget` | `type`: `fixed` \| `percentage`, `categories`: lista de nomes das categorias separados por vírgula |
| `edit_budget` | `type`: `fixed` \| `percentage`, `categories`: lista de nomes das categorias separados por vírgula |
| `delete_budget` | — |

**Recurring**

| Evento | Parâmetros |
|---|---|
| `create_recurring` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |
| `edit_recurring` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |
| `delete_recurring` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |
| `confirm_recurring` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |
| `skip_recurring` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |
| `stop_recurring` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |
| `reactivate_recurring` | `type`: `income` \| `expense`, `target`: `account` \| `credit_card`, `category`: nome da categoria |

**Categories**

| Evento | Parâmetros |
|---|---|
| `create_category` | `name`: nome da categoria, `type`: `income` \| `expense` |
| `edit_category` | `name`: nome da categoria, `type`: `income` \| `expense` |
| `delete_category` | `name`: nome da categoria, `type`: `income` \| `expense` |

**Dashboard**

| Evento | Parâmetros |
|---|---|
| `enter_dashboard_edit_mode` | — |
| `save_dashboard_layout` | `components`: lista ordenada dos IDs dos componentes ativos, separados por vírgula |

**Reports**

| Evento | Parâmetros |
|---|---|
| `generate_report` | `target`: `account` \| `credit_card`, `sections`: lista das seções ativas separadas por vírgula (`spending_by_category`, `income_by_category`, `transaction_list`) |
| `share_report` | — |
| `print_report` | — |

**Support**

| Evento | Parâmetros |
|---|---|
| `create_support_issue` | `type`: `bug` \| `feature` \| `question` |
| `send_support_reply` | `type`: `bug` \| `feature` \| `question` |

---

## Regras de negócio

- Nenhum dado pessoal ou financeiro é incluído em parâmetros de eventos (sem nomes, valores, descrições).
- Eventos de ação são disparados somente após confirmação bem-sucedida — não no clique do botão.
- O user ID é obtido do Firebase Auth (`currentUser?.uid`) — anônimo por enquanto, mas já preparado para autenticação futura.
- A persistência do user ID é responsabilidade do Firebase Auth, não do app.
- Desktop/JVM: todas as chamadas de analytics são no-op — sem exceções, sem logs.
- Nomes de eventos e parâmetros seguem `snake_case`, máx. 40 caracteres (restrição Firebase).
- O parâmetro `components` do evento `save_dashboard_layout` reflete o estado final salvo: somente componentes ativos, na ordem em que aparecem no dashboard.

---

## Padrões obrigatórios

- **Abstração via interface no domínio:** o domínio deve definir uma interface `Analytics` (ou equivalente em camada comum). Implementações concretas ficam nas camadas de plataforma. Isso permite o no-op em Desktop e facilita testes.

```kotlin
// Exemplo de contrato — não inclui detalhes de implementação
interface Analytics {
    fun logScreenView(screenName: String)
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
    fun setUserId(id: String?)
}
```

- **Injeção via Koin:** `Analytics` registrado como `single {}` com implementação por plataforma, seguindo o mesmo padrão de `DatabaseModule` e `ReportModule`.

---

## Fora do escopo

- Crash reporting (Crashlytics)
- Monitoramento de performance (Firebase Performance)
- A/B testing ou Remote Config
- User properties além de `user_id`
- Rastreamento de erros de negócio como eventos
- Analytics em Desktop (no-op — sem analytics real)
- Filtros de transações
- Dashboard de analytics ou visualização in-app
- Consentimento/opt-out de analytics

---

## Critério de aceite

### Validação manual

Usando o Firebase Console (DebugView) com o app em modo debug no Android:

**Navegação**
1. Abrir o app → confirmar `screen_view` com `screen_name: dashboard`.
2. Navegar para cada tela principal → confirmar `screen_view` com o `screen_name` correspondente.

**Transactions**
3. Criar uma transação de receita em conta → confirmar `create_transaction` com `type: income`, `target: account`, `is_installment: false`.
4. Criar uma transação de despesa parcelada em cartão → confirmar `create_transaction` com `type: expense`, `target: credit_card`, `is_installment: true`.
5. Cancelar a criação → confirmar que nenhum evento foi disparado.
6. Editar e deletar uma transação → confirmar `edit_transaction` e `delete_transaction` com `type`, `target` e `category` corretos.

**Accounts**
7. Criar conta como padrão → confirmar `create_account` com `is_default: true`.
8. Editar conta → confirmar `edit_account` com `is_default` refletindo o estado salvo.

**Credit Cards**
9. Criar cartão → confirmar `create_credit_card`.
10. Fechar, pagar e reabrir fatura → confirmar `close_invoice`, `pay_invoice`, `reopen_invoice`.
11. Realizar pagamento antecipado de fatura → confirmar `advance_invoice_payment`.
12. Deletar fatura futura → confirmar `delete_future_invoice`.

**Budgets**
13. Criar orçamento por porcentagem com categorias → confirmar `create_budget` com `type: percentage` e `categories` com a lista correta.

**Recurring**
14. Criar recorrência → confirmar `create_recurring` com `type`, `target`, `category`.
15. Confirmar e pular ocorrência → confirmar `confirm_recurring` e `skip_recurring`.

**Dashboard**
16. Entrar no modo de edição → confirmar `enter_dashboard_edit_mode`.
17. Salvar layout → confirmar `save_dashboard_layout` com `components` listando apenas os ativos na ordem correta.

**Reports**
18. Gerar relatório de conta com seções parciais → confirmar `generate_report` com `target: account` e `sections` refletindo apenas as seções ativas.
19. Compartilhar relatório → confirmar `share_report`.

**Persistência de usuário**
20. Fechar e reabrir o app → confirmar que o mesmo `user_id` é usado nas duas sessões.

**Desktop**
21. Rodar o app no Desktop → confirmar que nenhum erro é lançado e todas as funcionalidades operam normalmente.

### Revisão de código

- [x] `Analytics` é uma interface em camada independente de plataforma
- [x] Implementação Firebase está em `androidMain` e `iosMain` (não em `commonMain`)
- [x] Implementação no-op está em `jvmMain`
- [ ] `user_id` obtido do Firebase Auth (`currentUser?.uid`) — não gerado manualmente
- [ ] mesmo `user_id` usado entre sessões (persistência gerenciada pelo Firebase Auth)
- [ ] Nenhum parâmetro contém dados financeiros (valores, saldos)
- [ ] Eventos disparados após confirmação bem-sucedida, não no clique do botão
- [x] `Analytics` registrado como `single {}` no Koin em módulo separado
- [ ] Todos os eventos da tabela estão implementados