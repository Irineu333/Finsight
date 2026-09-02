---
area: app
severity: low
type: ux
---

# Uma abertura fria desenha o botão de ação só para deixá-lo cair

## Cenário

**DADO** o app aberto do zero — ou a Activity recriada, que o manifesto não impede em nenhuma
mudança de configuração — numa tela que não é aba primária (Configurações, Backup, Relatórios,
qualquer arquivado), em janela `COMPACT`
**QUANDO** os primeiros quadros são compostos
**ENTÃO** o botão de ação aparece no canto inferior direito e, um ou dois quadros depois, desce e
some
**DEVERIA** não aparecer: essa tela nunca quis um botão

## Mecânica

Na abertura fria o registro está vazio, então `ChromeStateHolder.isSilent` é verdadeiro e a casca
responde por si com `ChromeConfig.Default` — que inclui o botão. A tela só publica
`NoButtonOverContent` de dentro de um `SideEffect`, que roda **depois** da composição que leu o
registro. O intervalo entre os dois é um botão inteiro, com entrada e saída completas.

A barra não pisca junto: o `Default` da casca é mascarado por `isOnPrimaryTab`, falso ali. É só o
botão.

O `isSilent` existe para o caso oposto — um destino que **nunca** publica não pode herdar para
sempre uma cromagem calculada para outro — e é ele que faz a casca responder cedo demais aqui.

## Evidência

- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `chromeController.configOf(destinationId)
  ?: ChromeConfig.Default.takeIf { chromeController.isSilent }`
- `feature/shell/impl/.../screen/home/ChromeStateHolder.kt` — `isSilent`
- `feature/shell/api/.../Chrome.kt` — `ChromeEffect` publica de um `SideEffect`
- `feature/settings/impl/.../screen/settings/SettingsScreen.kt` — publica `NoButtonOverContent`
- `app/android/src/main/AndroidManifest.xml` — a Activity não declara `configChanges`, então girar
  o aparelho recria e repete o caso

## Consequência

Toda abertura fria numa tela empilhada mostra um botão que não pertence a ela e o vê cair. Não
engana e não impede.

## Sugestão

O mesmo `fabTarget == null` que já segura o botão antes de o destino resolver poderia valer
enquanto ninguém tiver falado. Cuidado: `isSilent` é verdadeiro também no destino que nunca publica,
e ali o botão é devido. Não vinculante.
