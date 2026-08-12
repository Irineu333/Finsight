## 1. A operação única de criação (D1)

- [ ] 1.1 Substituir o conteúdo de `feature/creditcards/impl/.../domain/usecase/CreateInvoiceUseCase.kt`
  por `operator fun invoke(creditCard: CreditCard, dueMonth: YearMonth): Either<Throwable, Invoice>`,
  que recusa colisão de `dueMonth` com `InvoiceError.AlreadyExists`, deriva a janela por
  `creditCard.invoiceWindowFor(dueMonth)` e insere via `IInvoiceRepository.insert`
- [ ] 1.2 Classificar o status dentro dela: `RETROACTIVE` quando `dueMonth` é anterior ao `dueMonth`
  da fatura `OPEN` do cartão, `FUTURE` caso contrário; sem fatura `OPEN`, levantar
  `InvoiceError.NoOpenInvoice` (D3)
- [ ] 1.3 Garantir que a operação MUST NOT produzir `OPEN` — abrir continua exclusivo de
  `OpenInvoiceUseCase`
- [ ] 1.4 Atualizar o registro em `di/UseCaseModule.kt:105` para a assinatura nova

## 2. Colapsar os caminhos antigos (D1, D2)

- [ ] 2.1 Fazer `GetOrCreateInvoiceForMonthUseCaseImpl` delegar a criação a `CreateInvoiceUseCase`,
  mantendo só a busca da existente e a recusa da fechada (`isClosedToNewExpenses`)
- [ ] 2.2 Remover de `GetOrCreateInvoiceForMonthUseCaseImpl` a comparação com o `dueMonth` da
  fatura `OPEN` — a regra passa a ter dono único
- [ ] 2.3 Apagar `CreateFutureInvoiceUseCase.kt` e `CreateRetroactiveInvoiceUseCase.kt` e seus
  registros em `di/UseCaseModule.kt:127` e `:180`
- [ ] 2.4 Compilar e confirmar que nenhum chamador restou (`./gradlew :app:shared:compileKotlinDesktop`)

## 3. A modal de criação (D4)

- [ ] 3.1 Criar `ui/modal/createInvoice/` com `CreateInvoiceModal`, `CreateInvoiceViewModel`,
  `CreateInvoiceUiState` e `CreateInvoiceAction`, seguindo o padrão de `editInvoiceBalance/`
- [ ] 3.2 Usar `InvoiceMonthNavigator` sobre `InvoiceMonthSelection` como seletor de mês, abrindo
  no mês da fatura de onde a modal foi aberta
- [ ] 3.3 Derivar do estado a indisponibilidade do envio quando o mês selecionado já tem fatura,
  mantendo o mês visível e sinalizado — sem esconder nem saltar
- [ ] 3.4 Exibir a janela derivada do mês selecionado, a mesma que será gravada
- [ ] 3.5 Registrar o ViewModel em `di/CreditCardsModule.kt` junto dos demais modais
- [ ] 3.6 Publicar os test tags da modal com `Modifier.exposeTestTags()` na raiz da folha, e dar
  `testTag` ao seletor de mês e ao botão de confirmar

## 4. A tela de faturas (D5, D6)

- [ ] 4.1 Adicionar a ação de criar fatura em `ui/screen/invoiceTransactions/`, oculta em cartão
  arquivado como as demais ações de escrita
- [ ] 4.2 Após criar, selecionar a fatura nova no pager pelo seu índice na lista reordenada
- [ ] 4.3 Confirmar que nenhum formulário abre automaticamente depois da criação

## 5. Strings

- [ ] 5.1 Adicionar as chaves da modal (título, rótulo do mês, aviso de mês ocupado, ação de criar)
  em `core/resources/.../values/strings.xml` **e** `values-en/strings.xml`
- [ ] 5.2 Conferir que nenhuma chave existe em apenas um dos dois arquivos

## 6. Testes

- [ ] 6.1 `CreateInvoiceUseCaseTest`: mês anterior ao vencimento da `OPEN` nasce `RETROACTIVE`;
  igual ou posterior nasce `FUTURE`; colisão é recusada; sem fatura `OPEN` levanta `NoOpenInvoice`
- [ ] 6.2 `CreateInvoiceUseCaseTest`: a fatura aberta é a referência, não hoje — `OPEN` vencendo em
  julho com hoje em outubro faz agosto nascer `FUTURE`
- [ ] 6.3 `CreateInvoiceUseCaseTest`: a janela gravada é a de `invoiceWindowFor`, inclusive em cartão
  com `dueDay < closingDay`; a fatura nasce sem entries
- [ ] 6.4 Teste de equivalência: criar pela operação e criar pelo lançamento
  (`GetOrCreateInvoiceForMonthUseCase`) produzem janela, vencimento e status idênticos
- [ ] 6.5 `CreateInvoiceSubmitEnablementTest`: o envio fica indisponível no mês ocupado e disponível
  no livre, nos moldes de `PayInvoiceSubmitEnablementTest`
- [ ] 6.6 Teste do ViewModel: criar seleciona a fatura nova, e nenhum modal adicional é solicitado
  ao `ModalManager`
- [ ] 6.7 Rodar `./gradlew :app:shared:testDebugUnitTest` e ler a saída

## 7. Verificação

- [ ] 7.1 Confirmar por busca que `CreateFutureInvoiceUseCase` e `CreateRetroactiveInvoiceUseCase`
  não existem mais em lugar nenhum do repositório
- [ ] 7.2 Confirmar que a classificação retro/futuro aparece em um só arquivo
- [ ] 7.3 Rodar `./gradlew allTests` e reportar o resultado lido
- [ ] 7.4 Exercitar o fluxo no app (criar fatura retroativa → ajustar → fechar → pagar) e reportar
  em qual plataforma foi exercitado
