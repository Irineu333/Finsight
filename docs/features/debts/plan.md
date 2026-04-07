# Plano: Dívidas

> O plano descreve *como* entregar o que a spec define.
> Pode ser ajustado durante a implementação. Desvios devem ser registrados.
> A spec não muda por dificuldade técnica — só quando a intenção mudar.

---

## Contexto técnico

**Reutilizações diretas:**
- `IOperationRepository.createOperation()` — usado para gerar DESPESA (pagamento) e RECEITA (lançamento inicial); o mesmo fluxo dos demais use cases de pagamento
- `ConfirmRecurringUseCase` — modelo exato para `ConfirmDebtInstallmentUseCase`: cria Operation + Occurrence com cycle number
- `SkipRecurringUseCase` — modelo para `IgnoreDebtInstallmentUseCase`: só cria Occurrence (IGNORED), sem Operation
- `GetPendingRecurringUseCase` — modelo para detecção de parcelas pendentes em memória (sem status PENDING no banco)
- `RecurringOccurrence` + `RecurringOccurrenceEntity` — modelo estrutural para `DebtInstallmentOccurrence`
- `InvoiceError` — modelo para `DebtError` e `DebtPaymentError` (sealed class com `message` + `toUiText()`)
- `AppRoute` sealed class — adicionar `AppRoute.Debts` seguindo o padrão existente
- `ModalBottomSheet` + `ModalManager` — formulários de dívida, pagamento e detalhes

**Decisões técnicas:**
- O plano de parcelas (`DebtInstallmentPlan`) será armazenado como colunas nullable dentro de `DebtEntity` (sem tabela separada), já que é opcional e sempre 1:1 com a dívida
- O saldo devedor **nunca** é armazenado — calculado em use case como `valorTotal − Σpagamentos`
- Os pagamentos são rastreados por `operationId` na tabela `debt_installment_occurrences` (para confirmação de parcelas) e pela query de operações vinculadas à dívida (para pagamentos livres)
- Pagamentos livres e confirmações de parcelas usam o mesmo mecanismo de `IOperationRepository`, vinculando a `debtId` via campo na `OperationEntity` ou via tabela de junção — **risco técnico**: verificar se `OperationEntity` suporta campo `debtId` ou se precisamos adicionar

**Riscos técnicos:**
1. **Migração Room v7→v8** — novas entidades `DebtEntity` e `DebtInstallmentOccurrenceEntity`; deve ser testada manualmente antes de prosseguir com etapas de comportamento
2. **Vínculo Operation→Debt** — precisamos recuperar o histórico de pagamentos de uma dívida; o mecanismo mais limpo é um campo `debtId` nullable em `OperationEntity`; avaliar durante a Etapa 0 se isso requer uma coluna nova ou uma tabela de junção
3. **Cálculo de ciclo de parcela** — análogo ao `ConfirmRecurringUseCase` (meses desde criação), mas baseado no mês de início da dívida (`startDate.yearMonth`); a parcela N é devida no mês N a partir do início (mês 1 = mês de criação)

---

## Referências

Sem referências em `docs/references/` — os padrões são derivados diretamente do código existente (`ConfirmRecurringUseCase`, `GetPendingRecurringUseCase`, `RecurringOccurrenceEntity`).

---

## Etapas

- [ ] [00 — Fundação](steps/00-foundation.md)
- [ ] [01 — Listar e cadastrar dívida](steps/01-list-register.md)
- [ ] [02 — Registrar pagamento livre](steps/02-free-payment.md)
- [ ] [03 — Parcelas pendentes](steps/03-pending-installments.md)
- [ ] [04 — Detalhes, edição, exclusão e reabertura](steps/04-details-management.md)

---

## Registro de desvios

_Nenhum desvio registrado ainda._

---

## Issues

_Nenhuma issue registrada ainda._
