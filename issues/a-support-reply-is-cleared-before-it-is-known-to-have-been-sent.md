---
area: support
severity: medium
type: ux
---

# A resposta de suporte é apagada antes do envio, e some sem aviso quando ele falha

## Cenário

**DADO** o usuário sem rede, escrevendo uma resposta longa num chamado
**QUANDO** toca em enviar
**ENTÃO** o campo esvazia, nenhuma mensagem aparece na conversa e nenhum erro é mostrado —
o texto está perdido
**DEVERIA** manter o rascunho e dizer que o envio falhou

## Mecânica

`sendReply()` faz `replyText.value = ""` **antes** do `viewModelScope.launch`, e o ramo de
falha só grava no Crashlytics. Falha de rede é um `Left` normal aqui — o caso de uso a
embrulha em `catch {}` —, então este é o caminho esperado, não o excepcional.

A tela não tem canal de erro nenhum: o composer recebe apenas `value`, `onValueChange` e
`onSend`, e `SupportIssueUiState` não carrega erro. O `ModalManager.showError`, que é onde
este app declara uma recusa, não é acionado.

## Evidência

- `feature/support/impl/.../support/SupportIssueViewModel.kt` — `sendReply()`:
  `replyText.value = ""` precede o `launch`; `.onLeft { crashlytics.recordException(it) }`
- `feature/support/impl/.../usecase/AddSupportReplyUseCase.kt` — `invoke()`:
  `catch { supportRepository.addReply(...) }.bind()`
- `feature/support/impl/.../support/SupportIssueScreen.kt` — o composer, sem canal de erro
- contraste: `feature/settings/impl/.../deleteCurrency/DeleteCurrencyViewModel.kt` —
  `.onLeft { modalManager.showError(it.toUiText()) }`

## Consequência

Perda silenciosa de texto digitado, exatamente no fluxo em que o usuário está relatando um
problema — e sem nada que o leve a tentar de novo.

## Sugestão

Limpar o campo só no `onRight`. A parte muda é a mesma de
`a-refused-write-says-nothing-to-whoever-asked-for-it`. Não vinculante.
