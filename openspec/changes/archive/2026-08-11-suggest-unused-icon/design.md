## Context

O ícone inicial do formulário de conta é resolvido hoje em uma linha do ViewModel:

```kotlin
// feature/accounts/impl/.../accountForm/AccountFormViewModel.kt:53
private val selectedIcon = MutableStateFlow(AppIcon.fromKey(account?.iconKey ?: AppIcon.WALLET.key))
```

Duas coisas convivem nessa expressão: **na edição**, o ícone que a conta já tem — que continua correto e não muda; **na criação**, uma constante. É só a segunda que este design substitui.

As restrições que moldam a solução:

- O catálogo oferecido ao formulário de conta é `FeatureIconCatalog.accounts` (`core/common` — onze ícones), estendido por `withGeneral` no momento de exibir. A sugestão precisa escolher entre os mesmos ícones que o seletor mostra em primeiro lugar.
- Saber quais ícones estão em uso exige ler as contas — `IAccountRepository.getAllAccounts()` é `suspend` e já devolve apenas as **abertas** (o método que inclui arquivadas é o `...IncludingClosed`). O valor inicial deixa, portanto, de ser calculável no construtor.
- A regra de derivação do projeto: quem deriva do domínio tem **um dono, no domínio**. O ViewModel consome; não reimplementa nem o critério de "em uso" nem a ordem de preferência.
- `AccountFormViewModel` é criado por Koin com `parametersOf(account)` a partir de `AccountFormModal` — o modal já está em tela quando o ViewModel nasce.

## Goals / Non-Goals

**Goals:**

- Que duas contas criadas em sequência, sem o usuário tocar no seletor, saiam com ícones diferentes.
- Que a escolha do ícone sugerido tenha um dono único e testável no domínio da feature de contas.
- Que o valor inicial assíncrono não pisque na tela nem sobrescreva uma escolha que o usuário já fez.

**Non-Goals:**

- Garantir unicidade de ícone entre contas. O usuário pode escolher um repetido, e o catálogo pode acabar.
- Bloquear, esconder ou marcar no seletor os ícones já em uso — o seletor continua idêntico.
- Mudar a edição de conta.
- Estender a regra a cartões e categorias.

## Decisions

### 1. A sugestão é um use case da feature de contas, não lógica do ViewModel

`SuggestAccountIconUseCase` — interface em `feature/accounts/api` (`domain/usecase/`), implementação em `impl`, registrada como `factory {}` no `AccountsModule`, como os demais use cases da feature.

A assinatura devolve um `AppIcon`, sempre — nunca nulo, nunca `Either`. Não há caso de erro: o esgotamento do catálogo tem resposta definida (decisão 3), e uma sugestão que falha não teria o que a tela fizesse a respeito.

*Alternativa considerada:* calcular dentro do `AccountFormViewModel`. Rejeitada por duplicar no ViewModel uma regra de domínio — o critério de "em uso" e a ordem de preferência — que o projeto exige ter dono único; e por deixá-la testável só através da máquina de estados do formulário.

*Alternativa considerada:* pôr a regra em `FeatureIconCatalog` (`core/common`). Rejeitada porque o catálogo não conhece — e não deve conhecer — contas nem repositórios; a regra precisa ler o estado do usuário.

### 2. "Em uso" é o ícone de uma conta aberta

O conjunto de ícones em uso é `getAllAccounts().map { it.iconKey }`, que já exclui as arquivadas. A intenção da sugestão é distinguir contas que aparecem **lado a lado**; uma conta arquivada não aparece na listagem ativa nem nos seletores, então o ícone dela está livre.

Consequência aceita: desarquivar uma conta pode recriar uma colisão de ícone. É colisão já possível hoje por escolha manual, e a alternativa — reservar para sempre o ícone de toda conta já fechada — esgotaria o catálogo com o uso.

A comparação é por `iconKey` (a `String` persistida), não pelo enum, porque é a chave que o banco guarda e um valor desconhecido cai em `AppIcon.DEFAULT` sem ambiguidade.

### 3. Ordem de preferência: o catálogo de contas; esgotado, `WALLET`

A sugestão é o **primeiro** `AppIcon` de `FeatureIconCatalog.accounts` cuja chave não está em uso. Se todos estiverem, é `AppIcon.WALLET` — exatamente o comportamento de hoje.

Isso promove a ordem de `FeatureIconCatalog.accounts` a comportamento observável: reordenar a lista muda o que o usuário vê sugerido. É um custo pequeno e explícito, e a ordem atual já começa em `WALLET`, o que faz a **primeira** conta criada em uma instalação limpa continuar recebendo `WALLET` como hoje.

O universo é `FeatureIconCatalog.accounts` puro, sem passar por `withGeneral`. `withGeneral` existe para o **seletor**, para que a lista exibida não tenha buracos; a sugestão é uma preferência editorial da feature, e os ícones gerais estão lá como complemento, não como sugestão.

*Alternativa considerada:* sugerir o ícone menos usado quando o catálogo esgota. Rejeitada por trocar uma regra simples por uma contagem com desempate, para ganhar quase nada — onze contas abertas é um cenário raro, e o usuário nesse ponto escolhe o ícone conscientemente.

### 4. O ViewModel resolve a sugestão no `init` e só aplica se o campo estiver intocado

`selectedIcon` continua sendo um `MutableStateFlow`, inicializado como hoje. Na criação, o `init` dispara uma corrotina que chama o use case e o aplica **apenas** se ninguém tiver mexido no campo enquanto isso — um `AccountFormAction.IconSelected` recebido antes da resposta vence a sugestão. Na edição, o use case não é chamado.

*Alternativa considerada:* resolver a sugestão antes de abrir o modal e passá-la como parâmetro. Rejeitada porque espalharia a responsabilidade por todo call site de `AccountFormModal()` — incluindo `AccountsEntryImpl`, que é a porta de outras features — e faria a abertura do modal esperar por I/O.

*Alternativa considerada:* `selectedIcon` como `StateFlow` derivado de um `flow {}` do use case. Rejeitada porque o campo é editável pelo usuário; um fluxo derivado precisaria de um segundo estado para a edição manual, e o resultado seria mais complexo, não menos.

O risco visível dessa escolha é o ícone trocar sozinho logo após a abertura do modal. Na prática é uma leitura local de banco de contas — a mesma que a tela de contas já faz —, e o estado inicial exibido é `WALLET`, um valor válido e não um placeholder.

## Risks / Trade-offs

- **O ícone muda depois do modal aberto, se o usuário for muito rápido** → a sugestão é descartada assim que o usuário toca no seletor; e o valor inicial exibido já é um ícone legítimo, então nada aparece vazio ou incorreto no intervalo.
- **A ordem de `FeatureIconCatalog.accounts` vira comportamento** → documentado aqui e coberto por teste do use case, que fixa a expectativa de que a sugestão respeita a ordem do catálogo.
- **Desarquivar uma conta pode recriar colisão** → aceito conscientemente (decisão 2); é o mesmo desfecho de uma escolha manual repetida, que a mudança nunca prometeu impedir.
- **Cartões e categorias seguem com constante fixa** → inconsistência assumida e declarada fora de escopo; o use case fica com nome e assinatura próprios de conta, sem generalização especulativa, e nada no desenho impede estendê-lo depois.
