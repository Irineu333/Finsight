## 1. Contrato do use case

- [ ] 1.1 Criar `SuggestAccountIconUseCase` em `feature/accounts/api` (`domain/usecase/`) — interface `suspend operator fun invoke(): AppIcon`, com KDoc declarando o critério de "em uso" (conta aberta), a ordem de preferência (catálogo de contas) e o desfecho com catálogo esgotado (`AppIcon.WALLET`)
- [ ] 1.2 Verificar que `feature/accounts/api` já enxerga `AppIcon` (`core/common`) e ajustar o `build.gradle.kts` se não enxergar

## 2. Implementação da derivação

- [ ] 2.1 Implementar `SuggestAccountIconUseCaseImpl` em `feature/accounts/impl` (`domain/usecase/`), lendo `IAccountRepository.getAllAccounts()` e comparando por `iconKey`
- [ ] 2.2 Escolher o primeiro `AppIcon` de `FeatureIconCatalog.accounts` cuja chave não esteja em uso; sem candidato livre, devolver `AppIcon.WALLET`
- [ ] 2.3 Registrar o use case como `factory {}` em `AccountsModule`

## 3. Consumo no formulário

- [ ] 3.1 Injetar `SuggestAccountIconUseCase` em `AccountFormViewModel` e passá-lo pelo `viewModel {}` do `AccountsModule`
- [ ] 3.2 No `init`, apenas quando `account == null`, resolver a sugestão em `viewModelScope` e aplicá-la a `selectedIcon` somente se o usuário ainda não tiver selecionado ícone algum
- [ ] 3.3 Marcar a seleção manual em `AccountFormAction.IconSelected` de modo que ela vença uma sugestão que chegue depois
- [ ] 3.4 Confirmar que o modo de edição segue abrindo com `AppIcon.fromKey(account.iconKey)` e não chama o use case

## 4. Testes

- [ ] 4.1 Teste do use case: sem contas, sugere o primeiro ícone do catálogo
- [ ] 4.2 Teste do use case: com o primeiro ícone em uso, sugere o segundo (fixa a ordem do catálogo como comportamento)
- [ ] 4.3 Teste do use case: conta arquivada não ocupa ícone — o ícone dela volta a ser sugerido
- [ ] 4.4 Teste do use case: com todo o catálogo em uso, sugere `AppIcon.WALLET`
- [ ] 4.5 Teste do use case: `iconKey` desconhecido no banco não elimina nenhum ícone do catálogo
- [ ] 4.6 Teste do ViewModel: em modo de criação, `selectedIcon` passa a ser o sugerido
- [ ] 4.7 Teste do ViewModel: `IconSelected` recebido antes da sugestão prevalece sobre ela
- [ ] 4.8 Teste do ViewModel: em modo de edição, `selectedIcon` é o da conta e o use case não é invocado

## 5. Verificação

- [ ] 5.1 Rodar `./gradlew :app:shared:testDebugUnitTest` e ler a saída
- [ ] 5.2 Exercitar manualmente: criar duas contas seguidas sem tocar no seletor e confirmar que saem com ícones diferentes
