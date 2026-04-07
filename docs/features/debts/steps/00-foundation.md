# Etapa 00 — Fundação

> Parte do plano: [Dívidas](../plan.md)

---

## O que fazer

Criar toda a infraestrutura necessária para que as etapas seguintes possam entregar comportamento visível. Nenhum comportamento de negócio é entregue aqui — apenas a base que compila, migra e navega.

**Domínio:**
- `Debt` — modelo com: `id`, `name`, `creditor`, `totalAmount`, `startDate`, `dueDate?`, `status` (`ACTIVE`/`PAID`), `installmentPlan?` (embedded: `count`, `dayOfMonth`), `createdAt`
- `DebtInstallmentOccurrence` — modelo com: `id`, `debtId`, `cycleNumber`, `yearMonth`, `status` (`CONFIRMED`/`IGNORED`), `operationId?`, `effectiveDate?`, `handledAt`
- `DebtError` — sealed class com `message: String` e `toUiText()`: `EmptyName`, `InvalidAmount`, `NotFound`
- `DebtPaymentError` — sealed class com `message: String` e `toUiText()`: `InvalidAmount`, `ExceedsBalance`, `NoAccountSelected`
- `IDebtRepository` — interface: `save(debt)`, `getAll(): Flow<List<Debt>>`, `getById(id): Debt?`, `update(debt)`, `delete(id)`
- `IDebtInstallmentOccurrenceRepository` — interface: `save(occurrence)`, `getByDebt(debtId): List<DebtInstallmentOccurrence>`, `getOccurrenceBy(debtId, yearMonth): DebtInstallmentOccurrence?`

**Banco de dados:**
- `DebtEntity` — entidade Room com: id, name, creditor, totalAmount, startDate, dueDate?, status, installmentCount?, installmentDayOfMonth?, createdAt
- `DebtInstallmentOccurrenceEntity` — entidade Room com: id, debtId (FK→debts CASCADE), operationId (FK→operations CASCADE nullable), cycleNumber, yearMonth, status, effectiveDate?, handledAt; índices únicos em (debtId, yearMonth) e (debtId, cycleNumber)
- `OperationEntity` — adicionar coluna `debtId Long?` nullable (permite recuperar pagamentos de uma dívida); FK para `debts` com `SET_NULL`
- `DebtDao` — insert, update, delete, `getAll(): Flow<List<DebtEntity>>`, `getById(id): DebtEntity?`
- `DebtInstallmentOccurrenceDao` — insert, `getByDebt(debtId): List<DebtInstallmentOccurrenceEntity>`, `getOccurrenceBy(debtId, yearMonth): DebtInstallmentOccurrenceEntity?`
- `AppDatabase` — bump para v8, adicionar novas entidades e DAOs, adicionar coluna `debtId` em operations via migração
- Migration v7→v8 — `CREATE TABLE debts (...)`, `CREATE TABLE debt_installment_occurrences (...)`, `ALTER TABLE operations ADD COLUMN debtId INTEGER REFERENCES debts(id) ON DELETE SET NULL`

**Mappers:**
- `DebtMapper` — `DebtEntity` ↔ `Debt`
- `DebtInstallmentOccurrenceMapper` — `DebtInstallmentOccurrenceEntity` ↔ `DebtInstallmentOccurrence`

**Repositórios (implementações):**
- `DebtRepository` — implementa `IDebtRepository` usando `DebtDao` + `DebtMapper`
- `DebtInstallmentOccurrenceRepository` — implementa `IDebtInstallmentOccurrenceRepository` usando dao + mapper

**DI (Koin):**
- `DatabaseModule` — adicionar `DebtDao`, `DebtInstallmentOccurrenceDao`
- `MapperModule` — adicionar `DebtMapper`, `DebtInstallmentOccurrenceMapper`
- `RepositoryModule` — adicionar `IDebtRepository`, `IDebtInstallmentOccurrenceRepository`

**Navegação:**
- `AppRoute` — adicionar `data object Debts : AppRoute()`
- `AppNavHost` — registrar `composable<AppRoute.Debts>` com `DebtsScreen(onNavigateBack = ...)`
- `DebtsScreen` — tela mínima: Scaffold com TopAppBar "Dívidas" e botão de voltar; sem conteúdo

---

## Arquivos afetados

**Criar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/model/Debt.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/model/DebtInstallmentOccurrence.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/error/DebtError.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/error/DebtPaymentError.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IDebtRepository.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/domain/repository/IDebtInstallmentOccurrenceRepository.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/entity/DebtEntity.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/entity/DebtInstallmentOccurrenceEntity.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/dao/DebtDao.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/dao/DebtInstallmentOccurrenceDao.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/mapper/DebtMapper.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/mapper/DebtInstallmentOccurrenceMapper.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/repository/DebtRepository.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/repository/DebtInstallmentOccurrenceRepository.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsScreen.kt` (tela mínima)
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsUiState.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsAction.kt`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/debts/DebtsViewModel.kt`

**Modificar:**
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/entity/OperationEntity.kt` — adicionar `debtId: Long?`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/AppDatabase.kt` — bump v8, novas entidades e DAOs
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/database/Database.kt` — adicionar migration v7→v8
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/DatabaseModule.kt` — registrar novos DAOs
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/MapperModule.kt` — registrar novos mappers
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/di/RepositoryModule.kt` — registrar novos repositórios
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/home/HomeRoute.kt` — adicionar `AppRoute.Debts`
- `composeApp/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/root/AppNavHost.kt` — registrar rota de dívidas
- `composeApp/src/commonMain/composeResources/values/strings.xml` — strings mínimas: `debts_screen_title`

---

## Critério de aceite

**Validação manual:**
1. Executar o app — deve abrir sem crash
2. Navegar para a tela de Dívidas (via menu ou rota direta) — deve exibir uma tela com título "Dívidas" e botão de voltar
3. Pressionar voltar — deve retornar à tela anterior sem crash
4. (Opcional, mas recomendado) Instalar sobre versão anterior — migração v7→v8 deve ocorrer sem crash

**Revisão de código:**
- [ ] `Debt` e `DebtInstallmentOccurrence` não importam nada de `database` ou `ui`
- [ ] `IDebtRepository` e `IDebtInstallmentOccurrenceRepository` são interfaces puras no domínio
- [ ] `DebtError` e `DebtPaymentError` seguem o padrão de `InvoiceError`: sealed class com `val message: String` e extensão `toUiText()` retornando `UiText.Res`
- [ ] `DebtInstallmentOccurrenceEntity` tem índice único em `(debtId, yearMonth)` e `(debtId, cycleNumber)`, análogo a `RecurringOccurrenceEntity`
- [ ] `OperationEntity` tem `debtId: Long?` com FK para `debts` e `onDelete = SET_NULL`
- [ ] Migration v7→v8 cria as tabelas e altera `operations` corretamente
- [ ] Koin: repositórios como `single {}`, ViewModel como `viewModel {}`

---

## Desvio

> Preencha apenas se a implementação divergiu do planejado.
