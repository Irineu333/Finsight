## 1. Confirmar o diagnóstico antes de mexer no código

- [x] 1.1 Rodar o app com **três ou mais cartões cadastrados**, abrir a tela de cartões e deslizar para o segundo cartão — a fatura, as ações e a lista devem acompanhar
- [x] 1.2 Voltar para o primeiro cartão e verificar que o restante da tela **continua descrevendo o segundo** — é este o critério que valida o diagnóstico — **confirmado**
- [x] 1.3 ~~Se 1.2 não reproduzir, **parar**: a causa é outra e precisa ser reinvestigada antes de prosseguir~~ — não disparou

## 2. Alinhar o pager de cartões ao de contas

- [x] 2.1 Em `CreditCardsScreen.kt` (`CreditCardPager`), remover a guarda `if (pagerState.currentPage != selectedIndex)` do `collect`
- [x] 2.2 Encadear `.distinctUntilChanged()` no `snapshotFlow`, notificando `onSelectCard(page)` a cada parada — mesma forma de `AccountsScreen.kt`
- [x] 2.3 Remover o bloco comentado do efeito inverso (`LaunchedEffect(selectedIndex) { scrollToPage }`), que não está em uso e é a origem da guarda
- [x] 2.4 Confirmar que `selectedIndex` continua sendo usado para o `initialPage` e para a promoção a elemento compartilhado (`page == selectedIndex`), que não mudam

## 3. Verificação manual

- [x] 3.1 Repetir 1.1–1.2: voltar ao primeiro cartão agora atualiza fatura, ações e lista
- [x] 3.2 Vaivém entre dois cartões várias vezes: a tela acompanha em todas as paradas
- [x] 3.3 Abrir a tela via cartão da dashboard (`initialCreditCardId` de um cartão que não é o primeiro), navegar para outro e voltar — o cartão de entrada volta a ser descrito corretamente
- [x] 3.4 Um único cartão cadastrado: nada quebra e nenhuma notificação em laço acontece
- [x] 3.5 A transição compartilhada dashboard → cartões continua promovendo só o cartão selecionado, sem regressão do `fix-credit-card-shared-transition-overlay`

## 4. Fechamento

- [x] 4.1 `./gradlew :app:desktop:compileKotlin`
- [x] 4.2 `openspec validate fix-credit-card-pager-selection`
