## Context

O app desktop é um processo Compose/JVM que inicia o Koin no `main()` e mantém o grafo inteiro
em memória (`app/desktop/.../main.kt:22-27`). O banco é um arquivo único em
`~/.finance/finsight.db` (`core/database/.../Database.jvm.kt:8`), e `:app:shared` reexporta
todas as `feature:*:api` e `:core:*` com `api(...)`, de modo que `:app:desktop` já enxerga o
contrato público inteiro (`app/shared/build.gradle.kts:9-42`).

O domínio é rico e a maior parte das regras já tem dono: 17 use cases estão em `api`, 63 vivem
em `impl`, e a escrita direta de ViewModel para repositório sobrevive em apenas oito pontos.
As leituras agregadas que um agente precisaria — saldo, resumo do mês, gasto por categoria,
progresso de orçamento, limite disponível — já existem, e devolvem `MoneyByCurrency` sempre que
podem atravessar contas.

O levantamento completo da superfície está em `docs/mcp-tool-surface.md`, com o use case que
decide cada regra, verificado arquivo a arquivo.

**Restrições que a mudança não escolhe:**

- O `invalidationTracker` do Room é do processo. Reatividade e isolamento de processo são
  mutuamente exclusivos.
- Ktor está pinado em `3.4.3` — a última cujo stdlib não passa à frente do compilador do
  projeto (Kotlin `2.3.10`) — e o catálogo registra que ele *"vive num módulo só,
  `feature/settings/impl`"*.
- `:app:desktop` é `kotlin("jvm")` puro; não é KMP e não vê `impl` algum.
- Toda leitura que atravessa contas é `MoneyByCurrency`, e reduzir a um número é conversão,
  que vive acima do razão.

## Goals / Non-Goals

**Goals:**

- Um agente MCP lê e escreve dados do app, com o app calculando as figuras em vez de o agente
  somá-las.
- Um único binário: o app configura, sobe e derruba o servidor, e a UI reage em tempo real.
- Escrita autorizada por eixos que o usuário controla, com o padrão em só-leitura.
- Toda regra continua com um dono; o servidor não ganha nenhuma.

**Non-Goals:**

- Dirigir a UI (navegar, abrir modal) ou ler estado de tela.
- Android e iOS.
- Idempotência de escrita: um agente que repete uma chamada perdida duplica o lançamento.
- Acesso remoto, autenticação de múltiplos usuários, ou qualquer superfície fora da máquina.
- Configurar moeda base e acervo de taxas por MCP — permanece configuração do app.

## Decisions

### D1 — O servidor é embutido, não um processo à parte

A alternativa era um executável separado (stdio) abrindo o mesmo `finsight.db`. Rejeitada: o
`invalidationTracker` do Room é in-process, então um segundo processo escrevendo no arquivo não
acorda `Flow` algum do app aberto. O agente lançaria uma despesa e a tela do usuário continuaria
exibindo o saldo anterior — e dois writers no mesmo SQLite ainda disputariam lock.

O preço é explícito: **sem app aberto, sem servidor**. A seção de configurações diz isso, para
que uma falha de conexão não seja lida como configuração errada.

### D2 — Transporte streamable HTTP em loopback

stdio é o transporte mais comum de MCP, e não serve aqui: um cliente stdio **lança** o processo
servidor, e o nosso já está rodando como aplicação gráfica. Sobra HTTP na interface de loopback,
que o SDK Kotlin oferece via `mcpStreamableHttp` sobre um engine Ktor.

Trade-off aceito: clientes que só falam stdio precisam de um adaptador de terceiros
(`mcp-remote` e similares). Não é binário nosso e não viola "um único binário" — mas é uma linha
a mais nas instruções, e ela deve estar lá.

### D3 — A feature é `feature/mcp/{api,impl}`, e o desktop apenas a liga

As alternativas eram pôr tudo em `:app:desktop` (que já vê todo o contrato público) ou criar
`:core:mcp` (impossível — `core` não pode ver feature).

Escolhemos a feature porque ela tem tela: a seção de configurações é rota, `NavGraphBuilder`,
módulo Koin e entry point — uma feature completa, não um puxadinho do shell. E porque
`feature/*/impl` pode depender de **qualquer** `api`, que é exatamente o perfil de um servidor
que precisa alcançar oito features.

A `api` declara um controlador (`start`, `stop`, estado observável) em tipos de `:core:*`. O
`impl` o implementa em `jvmMain`, com `actual` no-op nos demais targets — o padrão que
`SupportModule` já usa. `:app:desktop` obtém o controlador via Koin e o liga ao ciclo de vida do
processo; ele nunca vê o `impl`.

### D4 — A superfície é de apresentação, não espelho do domínio

Três alternativas foram descartadas:

- **CRUD uniforme sobre as oito entidades.** Não alcança metade do domínio (fatura tem ciclo de
  vida, não CRUD) e abre uma porta de corrupção silenciosa: `update_invoice(status = PAID)`
  grava "paga" sem lançar o pagamento, e o saldo passa a mentir sem que nada falhe.
- **Espelho 1:1 dos use cases.** Devolve ao agente a tarefa de compor e somar, que é justamente
  onde ele erra.
- **Ferramentas genéricas (`query`/`mutate` com `entity` + `action`).** Economizam contexto no
  `tools/list` e pagam com um `payload` sem schema, que o agente preenche por adivinhação.

A superfície é, então, o que uma tela do app já faz: mostra números no topo (perguntas), mostra
uma lista (catálogo), tem formulário (registro) e tem botões de ação (operações). Cada
ferramenta traduz e compõe; nenhuma decide.

### D5 — A permissão decide quais ferramentas existem

As quatro famílias **são** os quatro eixos. Uma permissão não concedida não vira um `if` no
início da ferramenta: as ferramentas correspondentes deixam de ser anunciadas no `tools/list`.
O agente não as tenta, não erra e não gasta contexto com elas.

`ServerCapabilities.Tools(listChanged = true)` permite notificar quem já está conectado quando o
usuário mexe no interruptor. A recusa na execução permanece, porque o anúncio é consequência da
permissão e não a sua única aplicação.

Alternativa descartada: matriz entidade × verbo, que daria quarenta interruptores numa tela.

### D6 — O id é a forma canônica; o agregado é sobrecarga que delega

Hoje sete use cases recebem id, catorze recebem o agregado, e `PayInvoicePaymentUseCase` recebe
os dois na mesma assinatura. Não há regra a corrigir, e sim uma a escolher.

O id carrega a implementação e resolve a identidade no momento da execução; a forma por agregado
vira uma linha na interface (`invoke(account) = invoke(account.id)`). A mudança é **aditiva** —
nenhum dos 24 chamadores existentes muda —, e resolver na hora da ação é mais correto do que
receber uma leitura carregada minutos antes, agora que existe uma segunda porta de escrita.

O padrão já existe no repositório: `IEntryRepository.accountBalanceUpTo(accountId, YearMonth)`
delega para a forma por dia sob o KDoc *"Not another number, so not another implementation."*

### D7 — Onde há lote, a forma é plural

`CalculateAvailableLimitUseCase(creditCard)` chamado num laço sobre uma lista produz N+1
consultas. A resposta não é receber o agregado, e sim receber a coleção de identidades e
devolver um mapa — como `IEntryRepository.owedByDimensionByCurrency` já faz sob a regra *"N
invoices custam uma leitura, não N"*.

### D8 — `ConfirmRecurringUseCase` perde os cinco defaults derivados do agregado

É o único use case do repositório cuja assinatura declara valores padrão lidos de outro
parâmetro (`amount = recurring.amount`, `title = recurring.title`, e mais três). Parecia forçar a
delegação na direção inversa — id resolvendo e chamando a forma por agregado.

Não força: nenhum é exercido. O único chamador (`ConfirmRecurringViewModel:206-219`) passa os
oito argumentos explicitamente, duplica o de `amount` antes de chamar, e contorna o de `title`
com um comentário explicando que cair no template *"would hand the user a name they had just
erased"*. O default existe e o único chamador o evita de propósito; um segundo chamador que
confiasse nele reintroduziria o bug.

### D9 — Os use cases faltantes nascem no dono, e o ViewModel passa a consumi-los

Sete operações não existem como use case: criar e editar categoria, criar/editar/apagar
orçamento, editar parcelamento, e o despacho entre parcelamento, recorrência e transação simples
(hoje um `if` em `AddTransactionViewModel:299-340`).

Cada um nasce no módulo que possui a regra, e o ViewModel que hoje a executa passa a chamá-lo no
mesmo passo. Extrair sem migrar o chamador criaria duas verdades sobre a mesma operação — a da
tela e a do agente —, que é exatamente o defeito que a mudança existe para não introduzir.

### D10 — Endereço e segredo

O token é gerado pelo app, persistido para sobreviver a reinícios, e regenerável. `Settings`
(multiplatform-settings) é o mecanismo de preferência que o app já usa, mas guarda texto claro
num local previsível — aceitável para um segredo que só concede acesso ao loopback da própria
máquina, e que o usuário pode invalidar a qualquer momento. Um cofre de sistema operacional
seria mais forte e traz dependência nativa por plataforma; fica registrado como caminho de
reforço, não como requisito desta mudança.

A porta precisa ser estável para que a configuração feita no cliente continue valendo. Porta
fixa com fallback quando ocupada, e o valor efetivo sempre exibido na tela — ver Open Questions.

### D11 — O que a resposta faz quando falta taxa

`OfflineConsolidationTest` já prova que nenhum módulo no caminho de uma figura consolidada
alcança a rede. Quando a taxa necessária não existir no acervo, a ferramenta **diz isso** e
devolve a decomposição por moeda, em vez de omitir a moeda ou apresentar aproximação como exato.
O agente é livre para relatar a limitação; o que ele não pode é receber um número que parece
consolidado e não é.

## Risks / Trade-offs

- **O SDK MCP pode não resolver contra Ktor 3.4.3 / Kotlin 2.3.10** → é o primeiro passo da
  implementação, antes de qualquer código de produto. Se não resolver, as saídas são fixar o
  engine numa versão compatível isolada no módulo do MCP, ou implementar o transporte sobre o
  `HttpServer` do JDK e usar apenas as camadas de protocolo do SDK.
- **Ktor deixa de viver num módulo só** → a nota do catálogo é reescrita no mesmo commit que
  adiciona a dependência, dizendo qual módulo usa cliente e qual usa servidor.
- **Segunda porta de escrita concorrendo com a UI** → toda escrita atravessa a mesma fronteira
  do razão (`LedgerEntryWriter`), que já valida `Σ = 0` e as regras de dimensão. Nada do MCP
  escreve fora dela.
- **Agente repete chamada perdida e duplica lançamento** → reconhecido e não resolvido nesta
  mudança. O usuário vê o lançamento na tela em tempo real, o que reduz o tempo até a descoberta,
  mas não previne.
- **55 ferramentas ocupam contexto do cliente** → a permissão filtra o anúncio; só-leitura expõe
  19. As descrições precisam ser boas, e escrevê-las é trabalho de primeira classe, não
  acabamento.
- **Token em texto claro** → ver D10. Escopo limitado a loopback, revogável pelo usuário.
- **Uma tela que exibe token e endereço é um alvo em captura de tela e compartilhamento** → o
  token fica oculto por padrão, revelado sob ação explícita.

## Migration Plan

1. **Spike de compatibilidade** do SDK com o pino de Ktor/Kotlin. Bloqueia todo o resto.
2. **Domínio primeiro, sem o servidor**: as 14 sobrecargas por id, os 7 use cases novos com os
   ViewModels migrados, e as 35 promoções para `api`. Tudo isso é aditivo ou local, compila e é
   testável sem que exista servidor algum, e pode ser mesclado sozinho.
3. **Servidor no socket**, desligado por padrão, sem ferramenta alguma — apenas ciclo de vida,
   loopback, token e handshake.
4. **Famílias de ferramentas**, na ordem ler → registrar → operar → apagar, cada uma junto do
   eixo de permissão que a governa.
5. **Tela de configurações** e as instruções de conexão.

**Rollback**: o servidor nasce desligado e é desligável a qualquer momento; desligá-lo restaura
o comportamento anterior por completo. As mudanças do passo 2 são aditivas e não têm o que
reverter — nenhum chamador existente muda de forma.

## Open Questions

- **Porta fixa ou efêmera?** Fixa mantém a configuração do cliente válida entre execuções, mas
  colide quando ocupada. Efêmera nunca colide e obriga a reconfigurar o cliente a cada
  reinício — a menos que o app publique o valor num arquivo de descoberta previsível, o que
  reintroduz um artefato fora do binário.
- **A conversão para id vira change própria?** Os 14 use cases, os 24 chamadores e seus testes
  existem independentemente do servidor. Manter junto dá uma change grande; separar atrasa o
  MCP por uma mudança que não é dele.
- **O token vai para um cofre do sistema operacional?** Fora do escopo aqui, mas a decisão muda
  se o app um dia guardar outro segredo.
