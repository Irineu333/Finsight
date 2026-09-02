---
area: app
severity: low
type: ux
---

# Uma segunda troca de visibilidade no meio da animação pode teleportar o botão

## Cenário

**DADO** uma aba primária em janela `COMPACT`, botão encaixado no centro da barra
**QUANDO** o usuário vai para Configurações — que esconde o botão — e, **antes de a saída
terminar** (~300ms), vai para o Suporte com conversas, que quer o botão de volta
**ENTÃO** o botão, ainda visível no meio da saída, salta do centro para o canto direito e sobe de lá
**DEVERIA** subir reto: toda transição que envolve *oculto* é vertical

## Mecânica

O contrato das seis transições depende de distinguir *saindo* de *foi embora*, e a casca faz isso
comparando o `currentState` da transição de visibilidade do botão com o alvo. `Transition.updateTarget`
tem uma linha que quebra a comparação num realvo em pleno voo:

```kotlin
if (currentState != this.targetState) {
    transitionState.currentState = this.targetState
}
```

— `currentState` recebe o **alvo anterior**, um estado que nunca foi alcançado. Duas trocas dentro
de uma mesma animação bastam: a primeira não pré-data (os dois ainda são iguais), a segunda sim.
`currentState` vira "escondido" enquanto o botão está visivelmente na tela, `fabJourney` responde
`Place` em vez de `Hold`, e o lugar é colocado de uma vez em vez de segurado.

Dar ao botão uma transição própria já removeu a ocorrência mais fácil — a barra indo embora
retomava a transição da cromagem inteira e pré-datava o `currentState` do botão sem que a
visibilidade dele mudasse. O que sobra exige que a **visibilidade do próprio botão** troque duas
vezes dentro de uma animação, e que o lugar de destino seja diferente do de partida.

## Evidência

- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `fabVisibility`, e `fabVisibility.currentState`
  passado como `isDrawn`
- `feature/shell/impl/.../screen/home/FabPlacement.kt` — `fabJourney`, e o ramo `Place` devolvendo
  `target` direto
- Compose 1.10.1, `androidx/compose/animation/core/Transition.kt`, `updateTarget` — a atribuição
  citada acima

## Consequência

Um movimento lateral numa transição que o contrato define como vertical, na janela estreita de uma
animação em curso. Não engana e não impede.

*Derivado do fonte do Compose e da leitura da casca; **não foi observado em tela**. O que falta para
provar é exercitar a navegação dupla dentro dos ~300ms.*

## Sugestão

Não perguntar o estado à transição. Guardar "está na tela" como estado próprio, escrito quando a
animação de entrada ou de saída de fato termina. Não vinculante.
