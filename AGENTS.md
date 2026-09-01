# Project Agent Instructions

## Fonte única de contexto local
- Para qualquer tarefa neste repositório, leia e siga `./CLAUDE.md` antes de planejar ou editar código.
- Considere `CLAUDE.md` como a referência principal de convenções, arquitetura e fluxo de trabalho deste projeto.

## Skills locais do projeto (Claude Code)
Use as skills abaixo diretamente dos caminhos locais, sem cópia e sem symlink:

- `commit`: `./.claude/skills/commit/SKILL.md` — versiona seguindo a convenção do projeto
- `bump-version`: `./.claude/skills/bump-version/SKILL.md` — sobe a versão no Android, iOS e Desktop
- `issues`: `./.claude/skills/issues/SKILL.md` — registra, corrige e arquiva os bugs em `issues/`
- `openspec-explore`: `./.claude/skills/openspec-explore/SKILL.md` — pensa uma ideia antes ou durante uma mudança
- `openspec-propose`: `./.claude/skills/openspec-propose/SKILL.md` — propõe uma mudança com os artefatos dela
- `openspec-apply-change`: `./.claude/skills/openspec-apply-change/SKILL.md` — implementa as tarefas de uma mudança
- `openspec-verify-change`: `./.claude/skills/openspec-verify-change/SKILL.md` — verifica a implementação contra os artefatos
- `openspec-sync-specs`: `./.claude/skills/openspec-sync-specs/SKILL.md` — leva as delta specs para o acervo
- `openspec-archive-change`: `./.claude/skills/openspec-archive-change/SKILL.md` — arquiva uma mudança concluída

Esta lista descreve o que existe em `.claude/skills/`, e nada além disso. Uma instrução
obrigatória apontando para arquivo inexistente é pior do que a ausência dela: manda ler o
que não há, e não dá sinal algum de que está errada. `AgentInstructionsTest` compara os
dois lados, porque este desvio já passou despercebido duas vezes.

## Regra de acionamento das skills
- Se o usuário citar uma skill pelo nome, carregue o respectivo `SKILL.md` e siga as instruções.
- Se a tarefa corresponder claramente ao domínio da skill, acione a skill mesmo sem citação explícita.
- Se mais de uma skill se aplicar, use apenas o conjunto mínimo necessário.
