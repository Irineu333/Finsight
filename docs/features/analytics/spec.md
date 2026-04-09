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
Então um user ID anônimo é gerado e associado à sessão
     e esse ID persiste entre sessões
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
| `create_transaction` | `type`: `income` \| `expense` |
| `delete_transaction` | — |
| `filter_transactions` | `filter_type`: `account` \| `category` \| `month` |

**Accounts**

| Evento | Parâmetros |
|---|---|
| `create_account` | — |
| `delete_account` | — |
| `transfer_between_accounts` | — |
| `adjust_account_balance` | — |

**Credit Cards**

| Evento | Parâmetros |
|---|---|
| `create_credit_card` | — |
| `close_invoice` | — |
| `pay_invoice` | — |
| `reopen_invoice` | — |
| `adjust_invoice_balance` | — |

**Installments**

| Evento | Parâmetros |
|---|---|
| *(apenas screen view)* | — |

**Budgets**

| Evento | Parâmetros |
|---|---|
| `create_budget` | — |
| `delete_budget` | — |

**Recurring**

| Evento | Parâmetros |
|---|---|
| `confirm_recurring` | — |
| `skip_recurring` | — |
| `stop_recurring` | — |
| `reactivate_recurring` | — |

**Categories**

| Evento | Parâmetros |
|---|---|
| `create_category` | — |
| `delete_category` | — |

**Dashboard**

| Evento | Parâmetros |
|---|---|
| `enter_dashboard_edit_mode` | — |
| `save_dashboard_layout` | — |

**Reports**

| Evento | Parâmetros |
|---|---|
| `generate_report` | — |

---

## Regras de negócio

- Nenhum dado pessoal ou financeiro é incluído em parâmetros de eventos (sem nomes, valores, descrições).
- Eventos de ação são disparados somente após confirmação bem-sucedida — não no clique do botão.
- O user ID é anônimo e gerado localmente (UUID v4), persistido entre sessões.
- O user ID deve ser atualizável externamente (para quando autenticação for adicionada).
- Desktop/JVM: todas as chamadas de analytics são no-op — sem exceções, sem logs.
- Nomes de eventos e parâmetros seguem `snake_case`, máx. 40 caracteres (restrição Firebase).

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
- Edição de transações e outros fluxos secundários (fora do caminho principal)
- Dashboard de analytics ou visualização in-app
- Consentimento/opt-out de analytics

---

## Critério de aceite

### Validação manual

1. Abrir o app no Android → no Firebase Console (DebugView), confirmar evento `screen_view` com `screen_name: dashboard`.
2. Navegar para Transactions → confirmar `screen_view` com `screen_name: transactions`.
3. Criar uma transação de receita → confirmar evento `create_transaction` com `type: income`.
4. Cancelar a criação de uma transação → confirmar que nenhum evento de ação foi disparado.
5. Abrir o app no Desktop → confirmar que nenhum erro é lançado e o app funciona normalmente.
6. Fechar e reabrir o app → confirmar que o mesmo `user_id` é usado nas sessões.

### Revisão de código

- [ ] `Analytics` é uma interface em camada independente de plataforma
- [ ] Implementação Firebase está em `androidMain` e `iosMain` (não em `commonMain`)
- [ ] Implementação no-op está em `jvmMain`
- [ ] `user_id` persistido entre sessões (não gerado a cada abertura)
- [ ] Nenhum parâmetro contém dados financeiros ou pessoais
- [ ] Eventos disparados após confirmação, não no clique
- [ ] `Analytics` registrado como `single {}` no Koin com módulo separado