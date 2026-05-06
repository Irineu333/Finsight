# `:core:database`

Infraestrutura Room: `AppDatabase`, entidades e DAOs centralizados. **Sem mappers, sem regras de domínio** — esses ficam em cada `:feature:X:impl`.

## Responsabilidade

Persistência local. Define o schema Room e expõe DAOs que cada feature consome para implementar seus repositórios.

## Conteúdo principal

- **Database:** `AppDatabase` (Room) e `Database.kt` (helpers de inicialização por plataforma).
- **DAOs:** `TransactionDao`, `CategoryDao`, `CreditCardDao`, `InvoiceDao`, `AccountDao`, `InstallmentDao`, `OperationDao`, `BudgetDao`, `RecurringDao`, `RecurringOccurrenceDao`.
- **Entities:** `TransactionEntity`, `CategoryEntity`, `CreditCardEntity`, `InvoiceEntity`, `AccountEntity`, `InstallmentEntity`, `OperationEntity`, `BudgetEntity`, `BudgetCategoryEntity`, `RecurringEntity`, `RecurringOccurrenceEntity`.
- **Type converters:** `Converters` (LocalDate/YearMonth/Instant).
- **DI:** `DatabaseModule` (Koin).

## Dependências

- `:core:utils`

Adicionalmente: Room runtime + KSP, `kotlinx-datetime`.

## Quem depende

- Todos os `:feature:X:impl` que persistem dados.
