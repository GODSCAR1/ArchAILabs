# Implementing avoid-caching-inactive-products OpenSpec change

**Session ID:** ses_e5c29d541ffeF2p3Ke398z491p
**Created:** 3/11/2026, 18:54:44
**Updated:** 3/11/2026, 18:55:29

---

## User

Implement tasks from an OpenSpec change.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** These steps expect a project that already uses OpenSpec. Before the first step that writes anything (`new change`, `archive`, `sync specs`, or authoring an artifact file), confirm the project has a root: run `openspec list --json` (with `--store <id>` when a store is selected, since the store is then the root) and read `root`. A root object means the project is set up. `"root": null` means it is not - there is no `openspec/` directory here, and a write such as `openspec new change` would create one as a side effect. The command also exits non-zero, which is that answer rather than a broken CLI, so read the JSON instead of retrying or working around it.

One `"root": null` is not about setup: when a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml` (or `config.yml`), the project does use OpenSpec through a store it declares, which this machine cannot resolve (the store is not registered, or the `store:` line is malformed). Do not treat it as uninitialized and skip the branches below: stop before writing and show the user that error's `message` and `fix`.

Otherwise, with no root, what happens next depends on how this workflow was reached:

- **Auto-selected**: you chose this workflow yourself, without the user naming OpenSpec, naming this skill, or running its slash command. Stop using OpenSpec and answer the request normally, as you would with no OpenSpec installed. Do not ask them to set anything up and do not mention OpenSpec setup.
- **Explicit OpenSpec request**: the user named OpenSpec, named this skill, or ran its slash command. Stop before writing and ask how to proceed: set this project up (`openspec init`), target a store they already have (`--store <id>`), or continue without OpenSpec for this request. Wait for their answer.

In both branches, never create the root as a side effect: do not run `openspec init` until the user asks for it, do not hand-create `openspec/` files, and do not let a command create it.

**Input**: Optionally specify a change name (e.g., `/opsx-apply add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.
**Provided arguments**: avoid-caching-inactive-products

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` to get available changes and ask the user to select one

   Always announce: "Using change: <name>" and how to override (e.g., `/opsx-apply <other>`).

2. **Check status to understand the schema**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to understand:
   - `schemaName`: The workflow being used (e.g., "spec-driven")
   - `planningHome`, `changeRoot`, and `actionContext`: planning scope and edit constraints
   - Which artifact contains the tasks (typically "tasks" for spec-driven, check status for others)

3. **Get apply instructions**

   ```bash
   openspec instructions apply --change "<name>" --json
   ```

   This returns:
   - `contextFiles`: artifact ID -> array of concrete file paths (varies by schema - could be proposal/specs/design/tasks or spec/tests/implementation/docs)
   - Progress (total, complete, remaining)
   - Task list with status, source path, and source line
   - Dynamic instruction based on current state
   - Optional `context`: current required project instruction input from the selected root
   - Optional `operationGuidance`: current advisory guidance for apply
   - `missingArtifacts` (when present): required artifact ids with no output

   **Handle states:**
   - If `state: "blocked"`: show the message and pause implementation.
     - If `missingArtifacts` is non-empty: suggest completing the missing artifacts. Run `openspec status --change "<name>" --json`, select the next `ready` artifact (not `skipped` or `blocked`), and use `openspec instructions "<artifact-id>" --change "<name>" --json` for its rules and template. Keep the selected `--store <id>` on both commands.
     - Otherwise, follow the CLI instruction to create or repair the schema-configured tracking file from existing planning artifacts. Do not assume another artifact is ready or start implementation while blocked.
   - If `state: "all_done"`: report that all tracked tasks are complete and suggest review or verification as appropriate before archiving
   - Otherwise: proceed to implementation

   Treat `context` as a required prompt-level input. Read and consider it, and
   apply relevant project facts, conventions, and constraints while implementing.
   Treat `operationGuidance` as optional additive advice. Read and consider every
   entry, and follow entries that are applicable and compatible with the built-in
   workflow.

   Keep both fields separate from CLI-returned state, missing artifacts, tasks,
   progress, `contextFiles`, and the built-in `instruction`. They are not
   evidence of task completion, do not replace the built-in instruction, and do
   not permit bypassing a blocked state. If context conflicts with the built-in
   instruction, an explicit user choice, or a CLI-controlled value, report the
   conflict and preserve the controlling value. If guidance is inapplicable or
   conflicts with those controlling inputs, do not follow it and explain why.
   These are prompt-level behavior contracts, not enforceable checks.

4. **Read context files**

   Read every file path listed under `contextFiles` from the apply instructions output.
   The files depend on the schema being used:
   - **spec-driven**: proposal, specs, design, tasks
   - Other schemas: follow the contextFiles from CLI output

   Do not copy `context` or `operationGuidance` verbatim into implementation
   files or planning artifacts unless the user separately asks for that content.

5. **Show current progress**

   Display:
   - Schema being used
   - Progress: "N/M tasks complete"
   - Remaining tasks overview
   - Dynamic instruction from CLI

6. **Implement tasks (loop until done or blocked)**

   For each pending task:
   - Show which task is being worked on
   - Make the code changes required
   - Keep changes minimal and focused
   - Before editing, confirm the checkbox at the returned `sourcePath` and `line` still matches the task description; if it does not, rerun the apply instructions and use the refreshed location
   - Mark the task complete at its returned `sourcePath` and `line`: `- [ ]` → `- [x]`
   - Rerun the apply instructions and confirm that task is now done and progress changed
   - Continue to next task

   **Pause if:**
   - Task is unclear → ask for clarification
   - Implementation reveals a design issue → suggest updating artifacts
   - A task needs work beyond what the spec and tasks describe, or you are tempted to drop, narrow, defer, or accept exceptions to specified behavior to make it fit → surface the added scope and ask; do not absorb it silently
   - Error or blocker encountered → report and wait for guidance
   - User interrupts

7. **On completion or pause, show status**

   Display:
   - Tasks completed this session
   - Overall progress: "N/M tasks complete"
   - If all done: report that tracked tasks are complete and suggest review or verification as appropriate before archiving
   - If paused: explain why and wait for guidance

**Output During Implementation**

```
## Implementing: <change-name> (schema: <schema-name>)

Working on task 3/7: <task description>
[...implementation happening...]
✓ Task complete

Working on task 4/7: <task description>
[...implementation happening...]
✓ Task complete
```

**Output On Completion**

```
## Implementation Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 7/7 tasks complete ✓

### Completed This Session
- [x] Task 1
- [x] Task 2
...

All tracked tasks are complete. Review or verify the change as appropriate
before archiving. You can archive this change with `/opsx-archive`.
```

**Output On Pause (Issue Encountered)**

```
## Implementation Paused

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 4/7 tasks complete

### Issue Encountered
<description of the issue>

**Options:**
1. <option 1>
2. <option 2>
3. Other approach

What would you like to do?
```

**Guardrails**
- Keep going through tasks until done or blocked
- Always read context files before starting (from the apply instructions output)
- If task is ambiguous, pause and ask before implementing
- If implementation reveals issues, pause and suggest artifact updates
- Keep code changes minimal and scoped to each task
- Update task checkbox immediately after completing each task
- Pause on errors, blockers, or unclear requirements - don't guess
- When a task needs work beyond what the spec describes, surface the added scope and pause - never silently narrow, defer, or simplify away specified behavior
- Only mark a task `- [x]` when its specified behavior is fully implemented, not when it is partially done or deferred
- Use contextFiles from CLI output, don't assume specific file names
- Use each task's sourcePath and line to update its exact checkbox
- Do not use context or operation guidance as proof that a task is complete
- Apply relevant project context; report conflicts with controlling workflow inputs
- Consider every guidance entry; explain any inapplicable or conflicting advice
- Do not copy runtime context or operation guidance into implementation files or planning artifacts
- Preserve CLI-controlled blocked/ready/all-done behavior and completion criteria

**Fluid Workflow Integration**

This skill supports the "actions on a change" model:

- **Can be invoked anytime**: Before all artifacts are done (if tasks exist), after partial implementation, interleaved with other actions
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts - not phase-locked, work fluidly

---

## Assistant (Build · GPT-6 Luna · 2.0s)

**Tool: skill**

**Input:**
```json
{
  "name": "openspec-apply-change"
}
```

**Output:**
```
<skill_content name="openspec-apply-change">
# Skill: openspec-apply-change

Implement tasks from an OpenSpec change.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** These steps expect a project that already uses OpenSpec. Before the first step that writes anything (`new change`, `archive`, `sync specs`, or authoring an artifact file), confirm the project has a root: run `openspec list --json` (with `--store <id>` when a store is selected, since the store is then the root) and read `root`. A root object means the project is set up. `"root": null` means it is not - there is no `openspec/` directory here, and a write such as `openspec new change` would create one as a side effect. The command also exits non-zero, which is that answer rather than a broken CLI, so read the JSON instead of retrying or working around it.

One `"root": null` is not about setup: when a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml` (or `config.yml`), the project does use OpenSpec through a store it declares, which this machine cannot resolve (the store is not registered, or the `store:` line is malformed). Do not treat it as uninitialized and skip the branches below: stop before writing and show the user that error's `message` and `fix`.

Otherwise, with no root, what happens next depends on how this workflow was reached:

- **Auto-selected**: you chose this workflow yourself, without the user naming OpenSpec, naming this skill, or running its slash command. Stop using OpenSpec and answer the request normally, as you would with no OpenSpec installed. Do not ask them to set anything up and do not mention OpenSpec setup.
- **Explicit OpenSpec request**: the user named OpenSpec, named this skill, or ran its slash command. Stop before writing and ask how to proceed: set this project up (`openspec init`), target a store they already have (`--store <id>`), or continue without OpenSpec for this request. Wait for their answer.

In both branches, never create the root as a side effect: do not run `openspec init` until the user asks for it, do not hand-create `openspec/` files, and do not let a command create it.

**Input**: Optionally specify a change name (e.g., `/opsx-apply add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` to get available changes and ask the user to select one

   Always announce: "Using change: <name>" and how to override (e.g., `/opsx-apply <other>`).

2. **Check status to understand the schema**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to understand:
   - `schemaName`: The workflow being used (e.g., "spec-driven")
   - `planningHome`, `changeRoot`, and `actionContext`: planning scope and edit constraints
   - Which artifact contains the tasks (typically "tasks" for spec-driven, check status for others)

3. **Get apply instructions**

   ```bash
   openspec instructions apply --change "<name>" --json
   ```

   This returns:
   - `contextFiles`: artifact ID -> array of concrete file paths (varies by schema - could be proposal/specs/design/tasks or spec/tests/implementation/docs)
   - Progress (total, complete, remaining)
   - Task list with status, source path, and source line
   - Dynamic instruction based on current state
   - Optional `context`: current required project instruction input from the selected root
   - Optional `operationGuidance`: current advisory guidance for apply
   - `missingArtifacts` (when present): required artifact ids with no output

   **Handle states:**
   - If `state: "blocked"`: show the message and pause implementation.
     - If `missingArtifacts` is non-empty: suggest completing the missing artifacts. Run `openspec status --change "<name>" --json`, select the next `ready` artifact (not `skipped` or `blocked`), and use `openspec instructions "<artifact-id>" --change "<name>" --json` for its rules and template. Keep the selected `--store <id>` on both commands.
     - Otherwise, follow the CLI instruction to create or repair the schema-configured tracking file from existing planning artifacts. Do not assume another artifact is ready or start implementation while blocked.
   - If `state: "all_done"`: report that all tracked tasks are complete and suggest review or verification as appropriate before archiving
   - Otherwise: proceed to implementation

   Treat `context` as a required prompt-level input. Read and consider it, and
   apply relevant project facts, conventions, and constraints while implementing.
   Treat `operationGuidance` as optional additive advice. Read and consider every
   entry, and follow entries that are applicable and compatible with the built-in
   workflow.

   Keep both fields separate from CLI-returned state, missing artifacts, tasks,
   progress, `contextFiles`, and the built-in `instruction`. They are not
   evidence of task completion, do not replace the built-in instruction, and do
   not permit bypassing a blocked state. If context conflicts with the built-in
   instruction, an explicit user choice, or a CLI-controlled value, report the
   conflict and preserve the controlling value. If guidance is inapplicable or
   conflicts with those controlling inputs, do not follow it and explain why.
   These are prompt-level behavior contracts, not enforceable checks.

4. **Read context files**

   Read every file path listed under `contextFiles` from the apply instructions output.
   The files depend on the schema being used:
   - **spec-driven**: proposal, specs, design, tasks
   - Other schemas: follow the contextFiles from CLI output

   Do not copy `context` or `operationGuidance` verbatim into implementation
   files or planning artifacts unless the user separately asks for that content.

5. **Show current progress**

   Display:
   - Schema being used
   - Progress: "N/M tasks complete"
   - Remaining tasks overview
   - Dynamic instruction from CLI

6. **Implement tasks (loop until done or blocked)**

   For each pending task:
   - Show which task is being worked on
   - Make the code changes required
   - Keep changes minimal and focused
   - Before editing, confirm the checkbox at the returned `sourcePath` and `line` still matches the task description; if it does not, rerun the apply instructions and use the refreshed location
   - Mark the task complete at its returned `sourcePath` and `line`: `- [ ]` → `- [x]`
   - Rerun the apply instructions and confirm that task is now done and progress changed
   - Continue to next task

   **Pause if:**
   - Task is unclear → ask for clarification
   - Implementation reveals a design issue → suggest updating artifacts
   - A task needs work beyond what the spec and tasks describe, or you are tempted to drop, narrow, defer, or accept exceptions to specified behavior to make it fit → surface the added scope and ask; do not absorb it silently
   - Error or blocker encountered → report and wait for guidance
   - User interrupts

7. **On completion or pause, show status**

   Display:
   - Tasks completed this session
   - Overall progress: "N/M tasks complete"
   - If all done: report that tracked tasks are complete and suggest review or verification as appropriate before archiving
   - If paused: explain why and wait for guidance

**Output During Implementation**

```
## Implementing: <change-name> (schema: <schema-name>)

Working on task 3/7: <task description>
[...implementation happening...]
✓ Task complete

Working on task 4/7: <task description>
[...implementation happening...]
✓ Task complete
```

**Output On Completion**

```
## Implementation Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 7/7 tasks complete ✓

### Completed This Session
- [x] Task 1
- [x] Task 2
...

All tracked tasks are complete. Review or verify the change as appropriate
before archiving. You can archive this change with `/opsx-archive`.
```

**Output On Pause (Issue Encountered)**

```
## Implementation Paused

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 4/7 tasks complete

### Issue Encountered
<description of the issue>

**Options:**
1. <option 1>
2. <option 2>
3. Other approach

What would you like to do?
```

**Guardrails**
- Keep going through tasks until done or blocked
- Always read context files before starting (from the apply instructions output)
- If task is ambiguous, pause and ask before implementing
- If implementation reveals issues, pause and suggest artifact updates
- Keep code changes minimal and scoped to each task
- Update task checkbox immediately after completing each task
- Pause on errors, blockers, or unclear requirements - don't guess
- When a task needs work beyond what the spec describes, surface the added scope and pause - never silently narrow, defer, or simplify away specified behavior
- Only mark a task `- [x]` when its specified behavior is fully implemented, not when it is partially done or deferred
- Use contextFiles from CLI output, don't assume specific file names
- Use each task's sourcePath and line to update its exact checkbox
- Do not use context or operation guidance as proof that a task is complete
- Apply relevant project context; report conflicts with controlling workflow inputs
- Consider every guidance entry; explain any inapplicable or conflicting advice
- Do not copy runtime context or operation guidance into implementation files or planning artifacts
- Preserve CLI-controlled blocked/ready/all-done behavior and completion criteria

**Fluid Workflow Integration**

This skill supports the "actions on a change" model:

- **Can be invoked anytime**: Before all artifacts are done (if tasks exist), after partial implementation, interleaved with other actions
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts - not phase-locked, work fluidly

Base directory for this skill: C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\.opencode\skills\openspec-apply-change
Relative paths in this skill (e.g., scripts/, reference/) are relative to this base directory.
Note: file list is sampled.

<skill_files>

</skill_files>
</skill_content>
```

---

## User

[AC/DC contexto de sesión]
Rama: lab/s11-pruebas
Umbral PIT vigente: 75%
Hallazgos abiertos (no introduzcas más de estos tipos):
ninguno

---

## Assistant (Build · GPT-6 Luna · 3.9s)

Using change: `avoid-caching-inactive-products`. To choose another, use `/opsx-apply <other>`. I’ll check its OpenSpec status and current task instructions first.

**Tool: bash**

**Input:**
```json
{
  "command": "openspec status --change \"avoid-caching-inactive-products\" --json",
  "workdir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc"
}
```

**Output:**
```
{
  "changeName": "avoid-caching-inactive-products",
  "schemaName": "spec-driven",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "changeRoot": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products",
  "artifactPaths": {
    "proposal": {
      "outputPath": "proposal.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\proposal.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\proposal.md"
      ]
    },
    "specs": {
      "outputPath": "specs/**/*.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\specs\\**\\*.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\specs\\product-detail\\spec.md"
      ]
    },
    "design": {
      "outputPath": "design.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\design.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\design.md"
      ]
    },
    "tasks": {
      "outputPath": "tasks.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\tasks.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\tasks.md"
      ]
    }
  },
  "isPlanningComplete": true,
  "isComplete": true,
  "applyRequires": [
    "tasks"
  ],
  "nextSteps": [
    "All planning artifacts are complete. Run openspec instructions apply --change \"avoid-caching-inactive-products\" --json to inspect implementation progress."
  ],
  "actionContext": {
    "mode": "repo-local",
    "sourceOfTruth": "repo",
    "planningArtifacts": [
      "proposal",
      "specs",
      "design",
      "tasks"
    ],
    "linkedContext": [],
    "allowedEditRoots": [
      "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc"
    ],
    "requiresAffectedAreaSelection": false,
    "constraints": [
      "Repo-local change artifacts and implementation edits are scoped to this project."
    ]
  },
  "artifacts": [
    {
      "id": "proposal",
      "outputPath": "proposal.md",
      "status": "done",
      "requires": []
    },
    {
      "id": "specs",
      "outputPath": "specs/**/*.md",
      "status": "done",
      "requires": [
        "proposal"
      ]
    },
    {
      "id": "design",
      "outputPath": "design.md",
      "status": "done",
      "requires": [
        "proposal"
      ]
    },
    {
      "id": "tasks",
      "outputPath": "tasks.md",
      "status": "done",
      "requires": [
        "specs",
        "design"
      ]
    }
  ],
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}

```

---

## Assistant (Build · GPT-6 Luna · 5.1s)

**Tool: bash**

**Input:**
```json
{
  "command": "openspec instructions apply --change \"avoid-caching-inactive-products\" --json",
  "workdir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc"
}
```

**Output:**
```
{
  "changeName": "avoid-caching-inactive-products",
  "changeDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products",
  "schemaName": "spec-driven",
  "contextFiles": {
    "proposal": [
      "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\proposal.md"
    ],
    "specs": [
      "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\specs\\product-detail\\spec.md"
    ],
    "design": [
      "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\design.md"
    ],
    "tasks": [
      "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\tasks.md"
    ]
  },
  "progress": {
    "total": 3,
    "complete": 0,
    "remaining": 3
  },
  "tasks": [
    {
      "id": "1",
      "description": "1.1 Retirar `sync = true` y excluir por estado los resultados inactivos; añadir prueba para el escenario «Consulta de producto inactivo» que confirme respuesta HTTP 200 y una nueva carga de origen en la segunda consulta.",
      "done": false,
      "sourcePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\tasks.md",
      "line": 5
    },
    {
      "id": "2",
      "description": "1.2 Añadir prueba que confirme que una ficha activa sigue usando la cache y documentar la excepción a la política de sincronización en `docs/caching.md` y el ADR 0010; verificar ambos comportamientos con los tests de cache y revisar coherencia documental.",
      "done": false,
      "sourcePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\tasks.md",
      "line": 6
    },
    {
      "id": "3",
      "description": "2.1 Ejecutar `bash ./verify-quality.sh --fast` y confirmar que specs, Spotless, Checkstyle y tests pasan.",
      "done": false,
      "sourcePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\tasks.md",
      "line": 10
    }
  ],
  "taskTrackingConfigured": true,
  "state": "ready",
  "instruction": "Read context files, work through pending tasks, mark complete as you go.\nPause if you hit blockers or need clarification.",
  "context": "Proyecto: master-acdc, tienda en Java (paquete com.codeja.master_acdc) con arquitectura hexagonal\n(domain, application, infrastructure). Hoy solo existe el catálogo de productos (Product, Sku).\nMódulos nuevos: inventory (stock por producto) y ordering (pedidos).\nStack: Java 25, Spring Boot 4, JPA/Hibernate, PostgreSQL, Flyway, Testcontainers.\nErrores HTTP con ProblemDetail (RFC 9457); cada error de negocio tiene un type propio.\nordering consulta y reserva stock mediante la API pública de inventory; inventory nunca depende de ordering.\nLas liberaciones de stock y demás efectos posteriores entre módulos viajan por eventos de dominio.\nEl cliente se identifica con la cabecera X-Customer-Id (simplificación: no hay autenticación).\nLanguage: es. Keep OpenSpec structural headings and SHALL/MUST keywords in English.\n",
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}

```

---

## Assistant (Build · GPT-6 Luna · 6.9s)

## Implementing: `avoid-caching-inactive-products` (schema: spec-driven)

Progress: **0/3 tasks complete**. Remaining: adjust cache behavior and test inactive products; prove active products remain cached and update both cache documents; run the required quality verification. The CLI instruction is to read the artifacts, complete pending tasks, and mark each complete as it goes.

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\proposal.md"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\changes\avoid-caching-inactive-products\proposal.md</path>
<type>file</type>
<content>
1: # Proposal
2: 
3: ## Why
4: 
5: Los productos inactivos no deberían permanecer almacenados en la cache de fichas; la lectura debe seguir devolviendo la ficha, pero no retenerla en L1/L2. Esto permite que una ficha inactiva no se sirva desde una entrada almacenada antes de una reactivación o cambio de estado.
6: 
7: ## What Changes
8: 
9: - Excluir de la cache de fichas los resultados cuyo estado sea inactivo.
10: - Quitar la carga sincronizada (`sync = true`) de la anotación `@Cacheable` para habilitar la condición de exclusión solicitada.
11: - Mantener la lectura y la respuesta HTTP de fichas inactivas sin cambios.
12: 
13: ## Capabilities
14: 
15: ### New Capabilities
16: 
17: Ninguna.
18: 
19: ### Modified Capabilities
20: 
21: - `product-detail`: precisar que las fichas inactivas se devuelven pero no se almacenan en la cache.
22: 
23: ## Impact
24: 
25: - Afecta al decorador de cache de ficha en `catalog.infrastructure`, su cobertura de pruebas y la documentación de política de cache.
26: - Cambia el comportamiento de concurrencia de cargas simultáneas de fichas ausentes al retirar `sync = true`.
27: - No cambia API HTTP ni dependencias.
28: 
29: ## Non-goals
30: 
31: - Cambiar TTL, claves, invalidación, número de niveles, ni política de errores de Redis.
32: - Modificar el modelo de estado o impedir que se consulten productos inactivos.

(End of file - total 32 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\specs\\product-detail\\spec.md"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\changes\avoid-caching-inactive-products\specs\product-detail\spec.md</path>
<type>file</type>
<content>
1: # Spec Delta
2: 
3: ## ADDED Requirements
4: 
5: ### Requirement: Las fichas inactivas no se almacenan en cache
6: El sistema SHALL devolver la ficha de un producto inactivo sin almacenar ese resultado en la cache de fichas.
7: 
8: #### Scenario: Consulta de producto inactivo
9: - **WHEN** el cliente solicita `GET /api/products/{sku}` para un producto inactivo que existe
10: - **THEN** el sistema responde HTTP 200 con su ficha y una solicitud posterior vuelve a consultar el origen en lugar de reutilizar ese resultado cacheado

(End of file - total 10 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\design.md"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\changes\avoid-caching-inactive-products\design.md</path>
<type>file</type>
<content>
1: # Design
2: 
3: ## Context
4: 
5: Ver `proposal.md` para la motivación y `specs/product-detail/spec.md` para el contrato. La ficha contiene `ProductStatus`; el decorador cacheado aplica actualmente `@Cacheable(..., sync = true)`. La configuración de cache en `docs/caching.md` exige sincronización y prohíbe `unless`, por lo que esas reglas deben revisarse junto con esta decisión.
6: 
7: ## Goals / Non-Goals
8: 
9: **Goals:** Que el resultado de fichas inactivas no se guarde en ninguno de los niveles administrados por la cache compuesta.
10: 
11: **Non-Goals:** Cambiar el resultado de origen, la respuesta HTTP o los TTL e invalidaciones existentes.
12: 
13: ## Decisions
14: 
15: - Usar la condición `unless` sobre el estado de la ficha devuelta y retirar `sync = true`, ya que Spring no permite combinar `unless` con carga sincronizada. Esto cumple la exclusión solicitada; cargas simultáneas sin entrada pueden volver a ejecutar el origen y se acepta ese coste en lugar de agregar coordinación alternativa.
16: - Actualizar la fila de `docs/caching.md`/sus reglas para documentar la excepción a la política general de sincronización. El ADR 0010 describe actualmente `@Cacheable(sync = true)` y deberá quedar coherente con el comportamiento aceptado.
17: - **Reglas de arquitectura:** conservar `@Cacheable` y la condición en infraestructura; `domain` y `application` no incorporan dependencias de Spring. `ordering` e `inventory` siguen sin depender de la cache de catálogo.
18: 
19: ## Risks / Trade-offs
20: 
21: - La ausencia de `sync = true` puede duplicar cargas concurrentes de fichas no almacenadas o inactivas → cubrir con pruebas de no-cache para inactivos y comprobar que las fichas activas mantienen los hits esperados; no prometer single-flight para misses.
22: - Si se cambia la comparación de estado con una condición demasiado amplia, podría excluir también fichas activas → probar explícitamente ambos estados.
23: 
24: ## Migration Plan
25: 
26: Sin migración de datos ni cambio HTTP. Las entradas existentes de productos inactivos se purgan por invalidación cuando cambia su estado; nuevas lecturas inactivas no las vuelven a poblar.

(End of file - total 26 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\avoid-caching-inactive-products\\tasks.md"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\changes\avoid-caching-inactive-products\tasks.md</path>
<type>file</type>
<content>
1: # Tasks
2: 
3: ## 1. Excluir fichas inactivas de la cache
4: 
5: - [ ] 1.1 Retirar `sync = true` y excluir por estado los resultados inactivos; añadir prueba para el escenario «Consulta de producto inactivo» que confirme respuesta HTTP 200 y una nueva carga de origen en la segunda consulta.
6: - [ ] 1.2 Añadir prueba que confirme que una ficha activa sigue usando la cache y documentar la excepción a la política de sincronización en `docs/caching.md` y el ADR 0010; verificar ambos comportamientos con los tests de cache y revisar coherencia documental.
7: 
8: ## 2. Verificación
9: 
10: - [ ] 2.1 Ejecutar `bash ./verify-quality.sh --fast` y confirmar que specs, Spotless, Checkstyle y tests pasan.

(End of file - total 10 lines)
</content>
```

---

## Assistant (Build · GPT-6 Luna · 1.9s)

**Tool: glob**

**Input:**
```json
{
  "pattern": "**/*ProductDetail*Cache*"
}
```

**Output:**
```
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductDetailCacheTest.java
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductDetailCacheRedisIT.java
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\main\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\RedisProductDetailCache.java
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\main\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductDetailCache.java
```

---

## Assistant (Build · GPT-6 Luna · 6.2s)

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\src\\main\\java\\com\\codeja\\master_acdc\\catalog\\infrastructure\\adapters\\out\\cache\\ProductDetailCache.java"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\main\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductDetailCache.java</path>
<type>file</type>
<content>
1: package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;
2: 
3: import io.micrometer.core.instrument.MeterRegistry;
4: import java.util.concurrent.Callable;
5: import org.slf4j.Logger;
6: import org.slf4j.LoggerFactory;
7: import org.springframework.cache.Cache;
8: import org.springframework.cache.Cache.ValueRetrievalException;
9: import org.springframework.cache.support.AbstractValueAdaptingCache;
10: import org.springframework.cache.support.NoOpCache;
11: import org.springframework.dao.DataAccessException;
12: import org.springframework.data.redis.serializer.SerializationException;
13: 
14: /** Cache-aside adapter that combines a local and a shared cache. */
15: public final class ProductDetailCache extends AbstractValueAdaptingCache {
16: 
17:   private final Cache local;
18:   private final Cache shared;
19:   private final MeterRegistry meterRegistry;
20:   private static final Logger LOGGER = LoggerFactory.getLogger(ProductDetailCache.class);
21: 
22:   public ProductDetailCache(Cache local, Cache shared, MeterRegistry meterRegistry) {
23:     super(false);
24:     this.local = local;
25:     this.shared = shared;
26:     this.meterRegistry = meterRegistry;
27:   }
28: 
29:   @Override
30:   public String getName() {
31:     return "products";
32:   }
33: 
34:   @Override
35:   public Object getNativeCache() {
36:     return this;
37:   }
38: 
39:   @Override
40:   protected Object lookup(Object key) {
41:     Cache.ValueWrapper localValue = local.get(key);
42:     if (localValue != null) {
43:       record("products.l1", "hit");
44:       return localValue.get();
45:     }
46:     record("products.l1", "miss");
47:     try {
48:       Cache.ValueWrapper remoteValue = shared.get(key);
49:       if (remoteValue != null) {
50:         record("products.l2", "hit");
51:         local.put(key, remoteValue.get());
52:         return remoteValue.get();
53:       }
54:       record("products.l2", "miss");
55:     } catch (SerializationException exception) {
56:       logCorruptValue();
57:       evictCorruptValue(key);
58:       record("products.l2", "miss");
59:     } catch (DataAccessException exception) {
60:       LOGGER.warn("Shared product cache read failed; treating it as a miss", exception);
61:       record("products.l2", "miss");
62:       return null;
63:     }
64:     return null;
65:   }
66: 
67:   @Override
68:   public <T> T get(Object key, Callable<T> valueLoader) {
69:     Cache.ValueWrapper cached = local.get(key);
70:     if (cached != null) {
71:       record("products.l1", "hit");
72:       return (T) cached.get();
73:     }
74:     record("products.l1", "miss");
75:     try {
76:       return local.get(key, () -> loadThroughRemote(key, valueLoader));
77:     } catch (ValueRetrievalException exception) {
78:       ValueRetrievalException cause = exception;
79:       while (cause.getCause() instanceof ValueRetrievalException nested) {
80:         cause = nested;
81:       }
82:       throw cause;
83:     }
84:   }
85: 
86:   private <T> T loadThroughRemote(Object key, Callable<T> valueLoader) {
87:     try {
88:       Cache.ValueWrapper cached = shared.get(key);
89:       if (cached != null) {
90:         record("products.l2", "hit");
91:         return (T) cached.get();
92:       }
93:       record("products.l2", "miss");
94:     } catch (SerializationException exception) {
95:       logCorruptValue();
96:       evictCorruptValue(key);
97:       record("products.l2", "miss");
98:     } catch (DataAccessException exception) {
99:       LOGGER.warn("Shared product cache read failed; treating it as a miss", exception);
100:       record("products.l2", "miss");
101:       // Redis is an optional cache layer; load from the source of truth.
102:     }
103:     T value = new NoOpCache(getName()).get(key, valueLoader);
104:     try {
105:       shared.put(key, value);
106:     } catch (DataAccessException exception) {
107:       LOGGER.warn("Shared product cache write failed", exception);
108:       // The local cache and source read remain successful while Redis is unavailable.
109:     }
110:     return value;
111:   }
112: 
113:   @Override
114:   public void put(Object key, Object value) {
115:     local.put(key, value);
116:     try {
117:       shared.put(key, value);
118:     } catch (DataAccessException exception) {
119:       LOGGER.warn("Shared product cache write failed", exception);
120:       // Preserve the successful L1 write if Redis is unavailable.
121:     }
122:   }
123: 
124:   @Override
125:   public void evict(Object key) {
126:     local.evict(key);
127:     try {
128:       shared.evict(key);
129:     } catch (DataAccessException exception) {
130:       LOGGER.warn("Shared product cache eviction failed", exception);
131:       // Local invalidation must not depend on Redis availability.
132:     }
133:   }
134: 
135:   @Override
136:   public void clear() {
137:     local.clear();
138:     try {
139:       shared.clear();
140:     } catch (DataAccessException exception) {
141:       LOGGER.warn("Shared product cache clear failed", exception);
142:       // Local invalidation must not depend on Redis availability.
143:     }
144:   }
145: 
146:   @Override
147:   protected Object toStoreValue(Object value) {
148:     return value;
149:   }
150: 
151:   @Override
152:   protected Object fromStoreValue(Object storeValue) {
153:     return storeValue;
154:   }
155: 
156:   private void record(String cache, String result) {
157:     meterRegistry.counter("cache.gets", "cache", cache, "result", result).increment();
158:   }
159: 
160:   private void evictCorruptValue(Object key) {
161:     try {
162:       shared.evict(key);
163:     } catch (DataAccessException exception) {
164:       LOGGER.warn("Incompatible shared product cache value could not be evicted", exception);
165:     }
166:   }
167: 
168:   private static void logCorruptValue() {
169:     LOGGER.warn("Incompatible shared product cache value; treating it as a miss");
170:   }
171: }

(End of file - total 171 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\src\\test\\java\\com\\codeja\\master_acdc\\catalog\\infrastructure\\adapters\\out\\cache\\ProductDetailCacheTest.java"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductDetailCacheTest.java</path>
<type>file</type>
<content>
1: package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;
2: 
3: import static org.assertj.core.api.Assertions.assertThat;
4: import static org.assertj.core.api.Assertions.assertThatThrownBy;
5: import static org.mockito.BDDMockito.given;
6: 
7: import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
8: import com.codeja.master_acdc.catalog.domain.model.Category;
9: import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
10: import com.codeja.master_acdc.catalog.domain.model.ProductRating;
11: import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
12: import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
13: import java.math.BigDecimal;
14: import java.time.Duration;
15: import java.util.HashSet;
16: import java.util.List;
17: import java.util.Map;
18: import java.util.UUID;
19: import java.util.concurrent.atomic.AtomicInteger;
20: import org.junit.jupiter.api.DisplayName;
21: import org.junit.jupiter.api.Test;
22: import org.springframework.cache.Cache;
23: import org.springframework.cache.Cache.ValueRetrievalException;
24: import org.springframework.cache.caffeine.CaffeineCache;
25: import org.springframework.cache.concurrent.ConcurrentMapCache;
26: import org.springframework.cache.support.SimpleCacheManager;
27: import org.springframework.dao.DataAccessResourceFailureException;
28: 
29: class ProductDetailCacheTest {
30: 
31:   private static final String SKU = "SKU-CACHE";
32: 
33:   @Test
34:   @DisplayName("Ficha repetida")
35:   void should_load_once_when_detail_is_requested_twice() {
36:     ProductDetailCache cache = cache();
37:     AtomicInteger loads = new AtomicInteger();
38:     ProductDetail expected = detail();
39: 
40:     ProductDetail first = cache.get(SKU, () -> countLoad(loads, expected));
41:     ProductDetail second = cache.get(SKU, () -> countLoad(loads, expected));
42: 
43:     assertThat(first).isEqualTo(expected);
44:     assertThat(second).isEqualTo(expected);
45:     assertThat(loads).hasValue(1);
46:   }
47: 
48:   @Test
49:   @DisplayName("Lectura desde el almacén compartido")
50:   void should_populate_local_cache_when_shared_value_exists() {
51:     ConcurrentMapCache shared = new ConcurrentMapCache("products.l2");
52:     ProductDetail expected = detail();
53:     shared.put(SKU, expected);
54:     ProductDetailCache cache = cache(shared);
55:     AtomicInteger loads = new AtomicInteger();
56: 
57:     ProductDetail actual = cache.get(SKU, () -> countLoad(loads, detail()));
58: 
59:     assertThat(actual).isEqualTo(expected);
60:     assertThat(loads).hasValue(0);
61:     assertThat(cache.get(SKU).get()).isEqualTo(expected);
62:   }
63: 
64:   @Test
65:   @DisplayName("SKU inexistente conserva el 404")
66:   void should_rethrow_product_not_found_when_loader_fails() {
67:     ProductDetailCache cache = cache();
68:     ProductNotFoundException notFound = new ProductNotFoundException(SKU);
69: 
70:     assertThatThrownBy(() -> cache.get(SKU, () -> fail(notFound)))
71:         .isInstanceOf(ValueRetrievalException.class)
72:         .hasCause(notFound);
73:   }
74: 
75:   @Test
76:   @DisplayName("TTL aleatorios")
77:   void should_jitter_product_cache_ttl_when_ttl_is_generated() {
78:     List<Duration> values =
79:         java.util.stream.IntStream.range(0, 100)
80:             .mapToObj(index -> CacheTtlPolicy.jitter(CacheTtlPolicy.PRODUCTS_L1_TTL))
81:             .toList();
82: 
83:     assertThat(values)
84:         .allSatisfy(
85:             value -> {
86:               assertThat(value).isGreaterThanOrEqualTo(Duration.ofMillis(13_500));
87:               assertThat(value).isLessThanOrEqualTo(Duration.ofMillis(16_500));
88:             });
89:     assertThat(new HashSet<>(values)).hasSizeGreaterThan(1);
90:   }
91: 
92:   @Test
93:   @DisplayName("Métricas por nivel")
94:   void should_record_local_and_shared_cache_accesses_when_both_levels_are_used() {
95:     SimpleMeterRegistry registry = new SimpleMeterRegistry();
96:     ProductDetailCache cache = cache(new ConcurrentMapCache("products.l2"), registry);
97:     SimpleCacheManager manager = new SimpleCacheManager();
98:     manager.setCaches(List.of(cache));
99:     manager.afterPropertiesSet();
100: 
101:     cache.get(SKU, () -> detail());
102:     cache.get(SKU, () -> detail());
103: 
104:     assertThat(
105:             registry
106:                 .get("cache.gets")
107:                 .tag("cache", "products.l1")
108:                 .tag("result", "miss")
109:                 .counter()
110:                 .count())
111:         .isEqualTo(1);
112:     assertThat(
113:             registry
114:                 .get("cache.gets")
115:                 .tag("cache", "products.l1")
116:                 .tag("result", "hit")
117:                 .counter()
118:                 .count())
119:         .isEqualTo(1);
120:     assertThat(
121:             registry
122:                 .get("cache.gets")
123:                 .tag("cache", "products.l2")
124:                 .tag("result", "miss")
125:                 .counter()
126:                 .count())
127:         .isEqualTo(1);
128:     assertThat(registry.find("cache.gets").tag("cache", "products.l1").meters()).hasSize(2);
129:     assertThat(registry.find("cache.gets").tag("cache", "products").counter()).isNull();
130:     assertThat(manager.getCache("products")).isSameAs(cache);
131:     assertThat(manager.getCache("products.l1")).isNull();
132:     assertThat(manager.getCache("products.l2")).isNull();
133:   }
134: 
135:   @Test
136:   @DisplayName("Capacidad L1 cero")
137:   void should_use_l2_for_each_read_when_local_capacity_is_zero() {
138:     ConcurrentMapCache shared = new ConcurrentMapCache("products.l2");
139:     shared.put(SKU, detail());
140:     Cache local =
141:         new com.codeja.master_acdc.catalog.infrastructure.config.ProductCacheConfiguration()
142:             .productDetailLocalCache(0);
143:     SimpleMeterRegistry registry = new SimpleMeterRegistry();
144:     ProductDetailCache cache = new ProductDetailCache(local, shared, registry);
145: 
146:     cache.get(SKU, ProductDetailCacheTest::detail);
147:     cache.get(SKU, ProductDetailCacheTest::detail);
148: 
149:     assertThat(counter(registry, "products.l1", "miss")).isEqualTo(2);
150:     assertThat(counter(registry, "products.l2", "hit")).isEqualTo(2);
151:   }
152: 
153:   @Test
154:   @DisplayName("Almacén compartido no disponible durante una lectura")
155:   void should_load_from_origin_when_shared_cache_read_fails() {
156:     Cache shared = org.mockito.Mockito.mock(Cache.class);
157:     given(shared.get(SKU)).willThrow(new DataAccessResourceFailureException("Redis offline"));
158:     ProductDetailCache cache = cache(shared);
159:     AtomicInteger loads = new AtomicInteger();
160:     ProductDetail expected = detail();
161: 
162:     ProductDetail actual = cache.get(SKU, () -> countLoad(loads, expected));
163: 
164:     assertThat(actual).isEqualTo(expected);
165:     assertThat(loads).hasValue(1);
166:   }
167: 
168:   @Test
169:   @DisplayName("JSON incompatible se recupera desde origen")
170:   void should_load_from_origin_when_shared_value_cannot_be_deserialized() {
171:     Cache shared = org.mockito.Mockito.mock(Cache.class);
172:     given(shared.get(SKU))
173:         .willThrow(
174:             new org.springframework.data.redis.serializer.SerializationException(
175:                 "Incompatible JSON"));
176:     ProductDetailCache cache = cache(shared);
177:     AtomicInteger loads = new AtomicInteger();
178:     ProductDetail expected = detail();
179: 
180:     ProductDetail actual = cache.get(SKU, () -> countLoad(loads, expected));
181: 
182:     assertThat(actual).isEqualTo(expected);
183:     assertThat(loads).hasValue(1);
184:     org.mockito.BDDMockito.then(shared).should().evict(SKU);
185:   }
186: 
187:   @Test
188:   @DisplayName("Miss de L2 se cuenta una vez cuando falla el put")
189:   void should_count_l2_miss_once_when_shared_write_fails() {
190:     Cache shared = org.mockito.Mockito.mock(Cache.class);
191:     ProductDetail expected = detail();
192:     given(shared.get(SKU)).willReturn(null);
193:     org.mockito.BDDMockito.willThrow(new DataAccessResourceFailureException("Redis offline"))
194:         .given(shared)
195:         .put(SKU, expected);
196:     SimpleMeterRegistry registry = new SimpleMeterRegistry();
197:     ProductDetailCache cache = cache(shared, registry);
198: 
199:     assertThat(cache.get(SKU, () -> expected)).isEqualTo(expected);
200: 
201:     assertThat(
202:             registry
203:                 .get("cache.gets")
204:                 .tag("cache", "products.l2")
205:                 .tag("result", "miss")
206:                 .counter()
207:                 .count())
208:         .isEqualTo(1);
209:   }
210: 
211:   private static ProductDetailCache cache() {
212:     return cache(new ConcurrentMapCache("products.l2"));
213:   }
214: 
215:   private static ProductDetailCache cache(Cache shared) {
216:     return cache(shared, new SimpleMeterRegistry());
217:   }
218: 
219:   private static ProductDetailCache cache(Cache shared, SimpleMeterRegistry registry) {
220:     CaffeineCache local =
221:         new CaffeineCache(
222:             "products.l1", com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build(), false);
223:     return new ProductDetailCache(local, shared, registry);
224:   }
225: 
226:   private static ProductDetail detail() {
227:     return new ProductDetail(
228:         UUID.randomUUID(),
229:         SKU,
230:         "Cached product",
231:         100,
232:         ProductStatus.ACTIVE,
233:         new Category(UUID.randomUUID(), "Category"),
234:         0,
235:         List.of(),
236:         Map.of(),
237:         new ProductRating(BigDecimal.ZERO.setScale(2), 0),
238:         List.of());
239:   }
240: 
241:   private static ProductDetail countLoad(AtomicInteger loads, ProductDetail value) {
242:     loads.incrementAndGet();
243:     return value;
244:   }
245: 
246:   private static double counter(SimpleMeterRegistry registry, String cache, String result) {
247:     return registry.get("cache.gets").tag("cache", cache).tag("result", result).counter().count();
248:   }
249: 
250:   private static ProductDetail fail(ProductNotFoundException exception) {
251:     throw exception;
252:   }
253: }

(End of file - total 253 lines)
</content>
```

**Tool: grep**

**Input:**
```json
{
  "pattern": "sync = true|@Cacheable|ProductDetailCache",
  "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\src",
  "include": "*.java"
}
```

**Output:**
```
Found 32 matches
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\main\java\com\codeja\master_acdc\catalog\infrastructure\config\ProductCacheConfiguration.java:
  Line 6: import com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductDetailCache;

  Line 7: import com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.RedisProductDetailCache;

  Line 86:   public RedisProductDetailCache productDetailSharedCache(

  Line 88:     return new RedisProductDetailCache(template);

  Line 100:     ProductDetailCache products = new ProductDetailCache(local, shared, meterRegistry);


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductDetailCacheTest.java:
  Line 29: class ProductDetailCacheTest {

  Line 36:     ProductDetailCache cache = cache();

  Line 54:     ProductDetailCache cache = cache(shared);

  Line 67:     ProductDetailCache cache = cache();

  Line 96:     ProductDetailCache cache = cache(new ConcurrentMapCache("products.l2"), registry);

  Line 144:     ProductDetailCache cache = new ProductDetailCache(local, shared, registry);

  Line 146:     cache.get(SKU, ProductDetailCacheTest::detail);

  Line 147:     cache.get(SKU, ProductDetailCacheTest::detail);

  Line 158:     ProductDetailCache cache = cache(shared);

  Line 176:     ProductDetailCache cache = cache(shared);

  Line 197:     ProductDetailCache cache = cache(shared, registry);

  Line 211:   private static ProductDetailCache cache() {

  Line 215:   private static ProductDetailCache cache(Cache shared) {

  Line 219:   private static ProductDetailCache cache(Cache shared, SimpleMeterRegistry registry) {

  Line 223:     return new ProductDetailCache(local, shared, registry);


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\CachedProductDetailUseCaseTest.java:
  Line 59:       ProductDetailCache products =

  Line 60:           new ProductDetailCache(


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductDetailCacheRedisIT.java:
  Line 35: class ProductDetailCacheRedisIT {

  Line 45:   private static RedisProductDetailCache sharedCache;


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductCacheMultiInstanceIT.java:
  Line 176:         .isInstanceOf(ProductDetailCache.class);

  Line 178:         .isInstanceOf(ProductDetailCache.class);


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\main\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\CachedProductDetailUseCase.java:
  Line 17:   @Cacheable(cacheNames = "products", key = "#sku", sync = true)


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\main\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\RedisProductDetailCache.java:
  Line 12: public final class RedisProductDetailCache extends AbstractValueAdaptingCache {

  Line 17:   public RedisProductDetailCache(RedisTemplate<String, ProductDetail> redis) {


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\main\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\ProductDetailCache.java:
  Line 15: public final class ProductDetailCache extends AbstractValueAdaptingCache {

  Line 20:   private static final Logger LOGGER = LoggerFactory.getLogger(ProductDetailCache.class);

  Line 22:   public ProductDetailCache(Cache local, Cache shared, MeterRegistry meterRegistry) {

```

**Tool: grep**

**Input:**
```json
{
  "pattern": "sync = true|@Cacheable|sincron",
  "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\docs",
  "include": "*.md"
}
```

**Output:**
```
Found 30 matches
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\caching.md:
  Line 46: 4. `sync = true` en `@Cacheable` (sin `unless` ni varias caches en la misma anotación).

  Line 47: 5. `@Cacheable` y todo lo de cache solo en `infrastructure`: `domain` y `application` no dependen de


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\lab-s05.md:
  Line 190: - Hallazgo: la skill `/opsx-archive` **no usa la CLI para mover el change**; hace la sincronización ella misma y luego un `mv` del directorio a `changes/archive/`. Bloquear solo `openspec archive` no bastaría: lo cubre el filtro por ruta (`openspec/changes/archive/` + `mv`) y el de Edit/Write sobre `openspec/specs/`.


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\adr\0010-cache-multinivel.md:
  Line 19: Cache de dos niveles detrás de la abstracción de Spring (`@Cacheable(sync = true)`), aplicada con un


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\lab-s11.md:
  Line 10: | `add-two-level-product-cache` | L1 Caffeine + L2 Redis detrás de `@Cacheable(sync = true)` en un decorador de infraestructura; invalidación `AFTER_COMMIT` + pub/sub entre instancias; job de stock registrado y con invalidación selectiva; `CacheErrorHandler`; ArchUnit del checkout |

  Line 63: - Hay menos lecturas de L2 (4.194) que miss de L1 (6.964): con `sync = true` las peticiones


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\evidencias\s09-prueba-2-opencode-sesion.md:
  Line 4323: Aviso: OpenSpec informa que el spec base `product-search` aún no existe en `openspec/specs/`; el delta `MODIFIED` podría requerir sincronización previa al archivado.


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\evidencias\s11\prueba-2c-guardian.md:
  Line 825: 22: - Invalidar las páginas correspondientes tras cualquier escritura que cambie reseñas o al retirar/eliminar el producto. Conservar `sync = true`, claves explícitas y manejo Redis como miss.

  Line 1324: 19: Cache de dos niveles detrás de la abstracción de Spring (`@Cacheable(sync = true)`), aplicada con un

  Line 1435: 46: 4. `sync = true` en `@Cacheable` (sin `unless` ni varias caches en la misma anotación).

  Line 1436: 47: 5. `@Cacheable` y todo lo de cache solo en `infrastructure`: `domain` y `application` no dependen de

  Line 1933:   "patchText": "*** Begin Patch\n*** Update File: docs/caching.md\n@@\n | `products` | `sku` (explícita, `#sku`) | 15 s ±10% / `catalog.cache.local.maximum-size` (default 10.000; `0` desactiva retención L1) | 10 min ±10% | Cache-aside | 1) Edición de producto: evento `ProductChanged(sku)` tras el commit (`AFTER_COMMIT`). 2) `CatalogStockProjectionJob`: los SKU cuyo `availableStock` cambió en la pasada. Cada invalidación: evict L2 + L1 local + mensaje a las demás L1. |\n+| `product-reviews` | `sku:page:size` (explícita, parámetros normalizados) | 15 s ±10% / 10.000 entradas (mismo límite L1 que `products`) | 10 min ±10% | Cache-aside, `sync = true` | Invalidación de la página de un SKU ante cualquier escritura de reseñas y ante retirada de producto; evict L2 + L1 y propagación entre instancias, como `products`. |\n | `categories` | `\"tree\"` | 5 min, `refreshAfterWrite` | 1 h | Read-through | **Reservada:** el repo no tiene lectura del árbol de categorías; no se implementa en este lab. |\n@@\n-- Las reseñas paginadas (`ProductReviewsUseCase`) **no** se cachean.\n+- `product-reviews` cachea solo el record inmutable `ProductReviewPage`; su clave incorpora SKU, página y tamaño normalizado.\n*** End Patch"

  Line 2531:   "patchText": "*** Begin Patch\n*** Update File: openspec/changes/cache-paginated-product-reviews/proposal.md\n@@\n-- Cachear resultados inmutables de páginas de reseñas con una clave que incluya producto, página y tamaño normalizado.\n-- Definir caducidad y tamaño según la política de cache existente, y retirar páginas de reseñas al cambiar las reseñas o el producto asociado.\n+- Cachear resultados inmutables de páginas de reseñas con una clave que incluya producto, página y tamaño normalizado.\n+- Definir caducidad y tamaño según la política de cache existente, y retirar páginas de reseñas por TTL o al recibir `ProductChanged(sku)` para el SKU asociado.\n@@\n-- Añadir pruebas de hit, separación de claves e invalidación.\n+- Añadir pruebas de hit, separación de claves e invalidación por `ProductChanged`.\n*** Update File: openspec/changes/cache-paginated-product-reviews/specs/product-detail/spec.md\n@@\n-El sistema SHALL reutilizar el resultado de una consulta de reseñas cuando coincidan el producto y los parámetros de paginación normalizados, y SHALL dejar de servir ese resultado tras una modificación de las reseñas del producto.\n+El sistema SHALL reutilizar el resultado inmutable de una consulta de reseñas cuando coincidan el SKU y los parámetros de paginación normalizados. Las páginas SHALL caducar según la política de TTL definida. Al recibir `ProductChanged(sku)`, el sistema SHALL invalidar todas las páginas cacheadas para ese SKU. No se requiere invalidación por escrituras de reseñas, ya que no existen caminos de escritura de reseñas.\n@@\n-#### Scenario: Reseñas modificadas\n-- **WHEN** se añade, modifica o elimina una reseña de un producto con páginas cacheadas\n-- **THEN** la siguiente petición HTTP 200 devuelve los datos actualizados y no reutiliza las páginas anteriores\n+#### Scenario: Producto cambiado\n+- **WHEN** se recibe `ProductChanged(sku)` para un producto con páginas cacheadas\n+- **THEN** todas las páginas de reseñas de ese SKU dejan de serv...
  Line 2889: 23: - Conservar `sync = true`, claves explícitas y manejo Redis como miss; añadir pruebas para hit, aislamiento de claves e invalidación mediante `ProductChanged`.

  Line 3598: 17:   @Cacheable(cacheNames = "products", key = "#sku", sync = true)

  Line 3698:   "patchText": "*** Begin Patch\n*** Add File: src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/cache/CachedProductReviewsUseCase.java\n+package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;\n+\n+import com.codeja.master_acdc.catalog.application.port.ProductReviewsUseCase;\n+import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;\n+import org.springframework.cache.annotation.Cacheable;\n+\n+/** Adds the paginated reviews cache outside the transaction-owning delegate. */\n+public final class CachedProductReviewsUseCase implements ProductReviewsUseCase {\n+\n+  private final ProductReviewsUseCase delegate;\n+\n+  public CachedProductReviewsUseCase(ProductReviewsUseCase delegate) {\n+    this.delegate = delegate;\n+  }\n+\n+  @Override\n+  @Cacheable(\n+      cacheNames = \"product-reviews\",\n+      key = \"#sku + ':' + #page + ':' + #size\",\n+      sync = true)\n+  public ProductReviewPage findBySku(String sku, int page, int size) {\n+    return delegate.findBySku(sku, page, size);\n+  }\n+}\n*** End Patch"

  Line 4715: AC/DC: todo @Cacheable lleva sync = true en la misma línea (protección contra stampede, docs/caching.md).

  Line 4757: 17:   @Cacheable(

  Line 4760: 20:       sync = true)

  Line 4783:   "patchText": "*** Begin Patch\n*** Update File: src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/cache/CachedProductReviewsUseCase.java\n@@\n-  @Cacheable(\n-      cacheNames = \"product-reviews\",\n-      key = \"#sku + ':' + #page + ':' + (#size > 100 ? 100 : #size)\",\n-      sync = true)\n+  @Cacheable(cacheNames = \"product-reviews\", key = \"#sku + ':' + #page + ':' + (#size < 100 ? #size : 100)\", sync = true)\n*** End Patch"

  Line 4875: [ERROR]         -··@Cacheable(cacheNames·=·"product-reviews",·key·=·"#sku·+·':'·+·#page·+·':'·+·(#size·<·100·?·#size·:·100)",·sync·=·true)

  Line 4876: [ERROR]         +··@Cacheable(

  Line 4911:   "patchText": "*** Begin Patch\n*** Update File: src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/cache/CachedProductReviewsUseCase.java\n@@\n-  @Cacheable(cacheNames = \"product-reviews\", key = \"#sku + ':' + #page + ':' + (#size < 100 ? #size : 100)\", sync = true)\n+  @Cacheable(cacheNames = \"product-reviews\", keyGenerator = \"productReviewsKey\", sync = true)\n*** End Patch"

  Line 5041: Corregí la causa del fallo de cache: `@Cacheable` ahora lleva `sync = true` en la misma línea. Para mantener la clave con tamaño normalizado sin que Spotless la divida, usa el generador `productReviewsKey`. `bash ./mvnw -q checkstyle:check` pasó.

  Line 5261: **Qué intenté:** añadí la cache paginada y cambié el ensamblado del `CacheManager`. Al detectar que eso rompía la llamada del guardián, me detuve sin modificar el test ni añadir una sobrecarga de compatibilidad. En un intento posterior corregí también el formato de `@Cacheable`: `sync = true` debe estar en la misma línea. Checkstyle pasó, pero Verify siguió bloqueado por la incompatibilidad de firma.


C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\evidencias\s11\prueba-2b-apply.md:
  Line 825: 22: - Invalidar las páginas correspondientes tras cualquier escritura que cambie reseñas o al retirar/eliminar el producto. Conservar `sync = true`, claves explícitas y manejo Redis como miss.

  Line 1324: 19: Cache de dos niveles detrás de la abstracción de Spring (`@Cacheable(sync = true)`), aplicada con un

  Line 1435: 46: 4. `sync = true` en `@Cacheable` (sin `unless` ni varias caches en la misma anotación).

  Line 1436: 47: 5. `@Cacheable` y todo lo de cache solo en `infrastructure`: `domain` y `application` no dependen de

  Line 1933:   "patchText": "*** Begin Patch\n*** Update File: docs/caching.md\n@@\n | `products` | `sku` (explícita, `#sku`) | 15 s ±10% / `catalog.cache.local.maximum-size` (default 10.000; `0` desactiva retención L1) | 10 min ±10% | Cache-aside | 1) Edición de producto: evento `ProductChanged(sku)` tras el commit (`AFTER_COMMIT`). 2) `CatalogStockProjectionJob`: los SKU cuyo `availableStock` cambió en la pasada. Cada invalidación: evict L2 + L1 local + mensaje a las demás L1. |\n+| `product-reviews` | `sku:page:size` (explícita, parámetros normalizados) | 15 s ±10% / 10.000 entradas (mismo límite L1 que `products`) | 10 min ±10% | Cache-aside, `sync = true` | Invalidación de la página de un SKU ante cualquier escritura de reseñas y ante retirada de producto; evict L2 + L1 y propagación entre instancias, como `products`. |\n | `categories` | `\"tree\"` | 5 min, `refreshAfterWrite` | 1 h | Read-through | **Reservada:** el repo no tiene lectura del árbol de categorías; no se implementa en este lab. |\n@@\n-- Las reseñas paginadas (`ProductReviewsUseCase`) **no** se cachean.\n+- `product-reviews` cachea solo el record inmutable `ProductReviewPage`; su clave incorpora SKU, página y tamaño normalizado.\n*** End Patch"

```

---

## Assistant (Build · GPT-6 Luna · 7.1s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\src\\main\\java\\com\\codeja\\master_acdc\\catalog\\infrastructure\\adapters\\out\\cache\\CachedProductDetailUseCase.java"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\main\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\CachedProductDetailUseCase.java</path>
<type>file</type>
<content>
1: package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;
2: 
3: import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
4: import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
5: import org.springframework.cache.annotation.Cacheable;
6: 
7: /** Adds the product-detail cache outside the transaction-owning delegate. */
8: public class CachedProductDetailUseCase implements ProductDetailUseCase {
9: 
10:   private final ProductDetailUseCase delegate;
11: 
12:   public CachedProductDetailUseCase(ProductDetailUseCase delegate) {
13:     this.delegate = delegate;
14:   }
15: 
16:   @Override
17:   @Cacheable(cacheNames = "products", key = "#sku", sync = true)
18:   public ProductDetail findBySku(String sku) {
19:     return delegate.findBySku(sku);
20:   }
21: }

(End of file - total 21 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\src\\test\\java\\com\\codeja\\master_acdc\\catalog\\infrastructure\\adapters\\out\\cache\\CachedProductDetailUseCaseTest.java"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\catalog\infrastructure\adapters\out\cache\CachedProductDetailUseCaseTest.java</path>
<type>file</type>
<content>
1: package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;
2: 
3: import static org.assertj.core.api.Assertions.assertThat;
4: import static org.assertj.core.api.Assertions.assertThatThrownBy;
5: 
6: import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
7: import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
8: import com.codeja.master_acdc.catalog.domain.model.Category;
9: import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
10: import com.codeja.master_acdc.catalog.domain.model.ProductRating;
11: import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
12: import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
13: import java.math.BigDecimal;
14: import java.util.List;
15: import java.util.Map;
16: import java.util.UUID;
17: import java.util.concurrent.atomic.AtomicInteger;
18: import org.junit.jupiter.api.DisplayName;
19: import org.junit.jupiter.api.Test;
20: import org.springframework.beans.factory.annotation.Autowired;
21: import org.springframework.beans.factory.annotation.Qualifier;
22: import org.springframework.cache.CacheManager;
23: import org.springframework.cache.annotation.EnableCaching;
24: import org.springframework.cache.caffeine.CaffeineCache;
25: import org.springframework.cache.concurrent.ConcurrentMapCache;
26: import org.springframework.cache.support.SimpleCacheManager;
27: import org.springframework.context.annotation.Bean;
28: import org.springframework.context.annotation.Configuration;
29: import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
30: 
31: @SpringJUnitConfig(CachedProductDetailUseCaseTest.TestConfig.class)
32: class CachedProductDetailUseCaseTest {
33: 
34:   private static final String SKU = "SKU-NEW";
35: 
36:   @Autowired
37:   @Qualifier("cachedProductDetailUseCase") private ProductDetailUseCase cachedUseCase;
38: 
39:   @Autowired private SourceUseCase source;
40: 
41:   @Test
42:   @DisplayName("SKU inexistente se crea")
43:   void should_return_404_then_load_created_product_when_missing_sku_is_retried() {
44:     assertThatThrownBy(() -> cachedUseCase.findBySku(SKU))
45:         .isInstanceOf(ProductNotFoundException.class);
46: 
47:     ProductDetail detail = cachedUseCase.findBySku(SKU);
48: 
49:     assertThat(detail.sku()).isEqualTo(SKU);
50:     assertThat(source.calls).hasValue(2);
51:   }
52: 
53:   @Configuration
54:   @EnableCaching
55:   static class TestConfig {
56: 
57:     @Bean
58:     CacheManager cacheManager() {
59:       ProductDetailCache products =
60:           new ProductDetailCache(
61:               new CaffeineCache(
62:                   "products.l1",
63:                   com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build(),
64:                   false),
65:               new ConcurrentMapCache("products.l2"),
66:               new SimpleMeterRegistry());
67:       SimpleCacheManager manager = new SimpleCacheManager();
68:       manager.setCaches(List.of(products));
69:       return manager;
70:     }
71: 
72:     @Bean
73:     SourceUseCase sourceProductDetailUseCase() {
74:       return new SourceUseCase();
75:     }
76: 
77:     @Bean
78:     ProductDetailUseCase cachedProductDetailUseCase(
79:         @Qualifier("sourceProductDetailUseCase") SourceUseCase source) {
80:       return new CachedProductDetailUseCase(source);
81:     }
82:   }
83: 
84:   static class SourceUseCase implements ProductDetailUseCase {
85: 
86:     private final AtomicInteger calls = new AtomicInteger();
87: 
88:     @Override
89:     public ProductDetail findBySku(String sku) {
90:       if (calls.getAndIncrement() == 0) {
91:         throw new ProductNotFoundException(sku);
92:       }
93:       return new ProductDetail(
94:           UUID.randomUUID(),
95:           sku,
96:           "Created",
97:           100,
98:           ProductStatus.ACTIVE,
99:           new Category(UUID.randomUUID(), "Category"),
100:           0,
101:           List.of(),
102:           Map.of(),
103:           new ProductRating(BigDecimal.ZERO.setScale(2), 0),
104:           List.of());
105:     }
106:   }
107: }

(End of file - total 107 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\docs\\caching.md"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\caching.md</path>
<type>file</type>
<content>
1: # Caches del sistema
2: 
3: Fuente única de verdad de las caches. **Toda cache nueva añade aquí su fila antes de escribir código**
4: (lo exige AGENTS.md). Si una cache no está en esta tabla, no existe.
5: 
6: ## Requisito de negocio
7: 
8: - Un cambio en la ficha (precio, nombre, stock mostrado) puede tardar **como máximo 30 s** en verse en
9:   todas las instancias.
10: - El **checkout nunca lee de una cache**. En este repo el checkout es `ordering`: reserva stock contra
11:   `inventory` (`StockReserver`) y no lee del catálogo. Debe seguir así: `ordering` e `inventory` no
12:   dependen de la cache ni del caso de uso cacheado de la ficha.
13: - El **stock no se cachea como dato de decisión**: la ficha muestra `stock` (copia de
14:   `availableStock`), pero nadie reserva ni valida contra ese valor (AGENTS.md, catálogo en MongoDB).
15: 
16: ## Tabla
17: 
18: | Cache | Clave | L1 (TTL / tamaño) | L2 (TTL) | Estrategia | Quién invalida |
19: |---|---|---|---|---|---|
20: | `products` | `sku` (explícita, `#sku`) | 15 s ±10% / `catalog.cache.local.maximum-size` (default 10.000; `0` desactiva retención L1) | 10 min ±10% | Cache-aside | 1) Edición de producto: evento `ProductChanged(sku)` tras el commit (`AFTER_COMMIT`). 2) `CatalogStockProjectionJob`: los SKU cuyo `availableStock` cambió en la pasada. Cada invalidación: evict L2 + L1 local + mensaje a las demás L1. |
21: | `categories` | `"tree"` | 5 min, `refreshAfterWrite` | 1 h | Read-through | **Reservada:** el repo no tiene lectura del árbol de categorías; no se implementa en este lab. |
22: 
23: - Valor cacheado: el record inmutable `ProductDetail` (listas y mapas con `copyOf`), nunca un documento
24:   de Mongo (`CatalogProduct`) ni un objeto mutable.
25: - SKU inexistente: **no se cachea** (`disableCachingNullValues`); el 404 (`ProductNotFoundException`) se
26:   lanza sin escribir en la cache. Por eso crear un producto no necesita invalidación.
27: - Las reseñas paginadas (`ProductReviewsUseCase`) **no** se cachean.
28: 
29: ## Justificación de los TTL
30: 
31: - **TTL de L1 (15 s ±10% → máx. 16,5 s) < 30 s.** La invalidación entre instancias va por Redis pub/sub,
32:   que entrega *como mucho una vez*: si una instancia está reconectando, el mensaje se pierde y nadie lo
33:   reenvía. En ese caso la L1 se corrige sola al caducar, dentro del límite de 30 s.
34: - **TTL de L2 (10 min).** La L2 se invalida de forma explícita en cada cambio (edición y stock); su TTL
35:   solo limita la memoria y el daño si un evict de L2 falla (riesgo en el ADR 0010).
36: - **Jitter ±10%** en ambos niveles: evita que las claves cargadas a la vez (arranque, campaña) caduquen a
37:   la vez y provoquen un stampede.
38: - **Tamaño 10.000:** con ~500 SKU calientes todo cabe en L1; el hit ratio de L1 lo limita el TTL, no el
39:   tamaño.
40: 
41: ## Reglas para cualquier cache
42: 
43: 1. Clave explícita (`key = "#..."`), nunca la generada por defecto.
44: 2. Solo records inmutables como valor.
45: 3. TTL con jitter en L1 y L2.
46: 4. `sync = true` en `@Cacheable` (sin `unless` ni varias caches en la misma anotación).
47: 5. `@Cacheable` y todo lo de cache solo en `infrastructure`: `domain` y `application` no dependen de
48:    Spring (ArchUnit ya lo exige).
49: 6. Redis caído = miss (se registra), nunca un 5xx.
50: 7. Cambiar la forma de un valor cacheado → subir la versión del prefijo (`shop:cache:v1:` → `v2`).
51: 8. Toda cache en esta tabla, con tests de hit e invalidación.

(End of file - total 51 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\docs\\adr\\0010-cache-multinivel.md"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\adr\0010-cache-multinivel.md</path>
<type>file</type>
<content>
1: # ADR 0010 — Cache multinivel (Caffeine + Redis) para la ficha de producto
2: 
3: - Estado: Aceptado
4: - Fecha: 2026-11-03
5: - Relacionado: ADR 0009 (catálogo en MongoDB), `docs/caching.md`, `docs/catalog-model.md`
6: 
7: ## Contexto
8: 
9: `GET /api/products/{sku}` concentra el 70% del tráfico de lectura. Tras el ADR 0009 la ficha se resuelve
10: en una operación de MongoDB, pero con tres réplicas y picos de campaña MongoDB llega al 80% de CPU. El
11: negocio acepta hasta 30 s de obsolescencia de la ficha, salvo en el checkout (`ordering`), que no lee del
12: catálogo ni de ninguna cache.
13: 
14: La ficha (`ProductDetail`) incluye `stock`, una copia de `inventory` que `CatalogStockProjectionJob`
15: refresca cada minuto; por eso la cache también debe invalidarse cuando cambia el stock.
16: 
17: ## Decisión
18: 
19: Cache de dos niveles detrás de la abstracción de Spring (`@Cacheable(sync = true)`), aplicada con un
20: decorador en `catalog.infrastructure` sobre `ProductDetailUseCase` (domain y application siguen sin
21: Spring):
22: 
23: - **L1: Caffeine** en cada instancia, TTL 15 s ±10%, máx. 10.000 entradas, `recordStats()`.
24: - **L2: Redis** compartido mediante `RedisTemplate<String, ProductDetail>` y serializador JSON
25:   tipado de Jackson 3. El TTL de 10 min ±10% se aplica con jitter en cada `put` (no se usa
26:   `RedisCacheManager` ni `TtlFunction`); prefijo `shop:cache:v1:`, `clear` con SCAN y sin valores
27:   nulos. El `CacheManager` solo publica `products`; L1/L2 son caches internas construidas en
28:   configuración y entregadas por constructor al listener, no caches administradas adicionales.
29: - **Invalidación:** la edición publica `ProductChanged(sku)` y un `@TransactionalEventListener(AFTER_COMMIT)`
30:   hace evict en L2 y en la L1 local y publica `cache|key|instanceId` en `shop:cache:evictions`; el job de
31:   stock invalida solo los SKU cuyo stock cambió tras el bulk exitoso (con ejecución fallback cuando no
32:   hay transacción). Cada instancia invalida su L1 al recibir el mensaje e ignora los suyos. No se usa
33:   `transactionAware()` solo en L2, para no desfasar L1 y L2.
34: - **Fallos:** `CachingConfigurer` proporciona un `CacheErrorHandler` que registra y absorbe errores
35:   de get/put/evict/clear que llegan al aspecto; la cache compuesta trata `DataAccessException` de Redis
36:   como miss. Una `SerializationException` por JSON incompatible también produce miss, registra aviso,
37:   intenta evict la clave corrupta y carga desde origen. Los errores de L1 no se ocultan. Redis usa
38:   timeout de 200 ms y rechaza comandos mientras está desconectado (fallo rápido).
39: - **Checkout:** `ordering` e `inventory` no dependen de la cache ni del caso de uso cacheado (ArchUnit).
40: 
41: ## Alternativas descartadas
42: 
43: | Alternativa | Por qué no |
44: |---|---|
45: | Solo Redis | Cada lectura sigue pagando un salto de red y deserialización; Redis pasa a ser el nuevo cuello de botella en picos y un punto único de latencia. |
46: | Solo Caffeine con TTL corto | Sin nivel compartido, cada réplica carga cada SKU por su cuenta: con N réplicas, N cargas a MongoDB por caducidad. Sin invalidación explícita, la obsolescencia es siempre el TTL entero. |
47: | Invalidar L1 solo por TTL (sin pub/sub) | Cumple los 30 s, pero todo cambio tarda ~15 s en verse aunque nada falle. |
48: | Sacar `stock` del valor cacheado | Obligaría a una segunda lectura por ficha y rompería la regla de "la ficha en 1 operación". |
49: | Que el job de stock invalide todos los SKU en cada pasada | Vaciaría la L2 cada minuto y el hit ratio de L2 caería a casi cero. |
50: 
51: ## Consecuencias y riesgos aceptados
52: 
53: - **Pérdida de mensajes de pub/sub:** acotada por el TTL de L1 (< 30 s). Es la razón del TTL de L1.
54: - **Evict de L2 fallido con Redis inestable:** la L2 podría quedarse con el valor viejo hasta su TTL
55:   (10 min) y repoblar las L1. Mitigación: reintento del evict y registro del error. *Riesgo abierto:
56:   valorar bajar el TTL de L2 o un evict diferido; pendiente de revisar con Ricardo.*
57: - **Stock mostrado:** puede llegar con el retraso del job (1 min) más la propagación; es un dato
58:   informativo, nadie reserva contra él.
59: - Métricas propias `cache.gets` para L1 y L2, con tags `cache=products.l1|products.l2` y
60:   `result=hit|miss`; no se crean series de métricas para caches administradas individuales ni para
61:   la cache compuesta, y un miss seguido de un `put` fallido se cuenta una sola vez.
62: - **Carrera de carga en vuelo tras evict:** una carga iniciada antes de una invalidación puede escribir
63:   su valor antiguo en L2 después del evict, conservándolo hasta el TTL y pudiendo repoblar L1. Se acepta
64:   el riesgo; un segundo evict diferido es una mitigación posible, no implementada.
65: - Cambios en `ProductDetail` exigen subir la versión del prefijo.

(End of file - total 65 lines)
</content>
```

---

## Assistant (Build · GPT-6 Luna · 11.8s)

_Thinking:_



## Implementación pausada

**Cambio:** `avoid-caching-inactive-products`  
**Esquema:** `spec-driven`  
**Progreso:** 0/3 tareas completas

La propuesta y la tarea 1.1 piden usar `unless` en `@Cacheable` para no almacenar fichas inactivas y quitar `sync = true`. Esto contradice la regla de `AGENTS.md` que prohíbe `unless` y exige `sync = true`. El diseño confirma que Spring no permite combinar `unless` con carga sincronizada, así que no puedo implementar el comportamiento descrito sin resolver antes esa contradicción.

**¿Cómo quieres proceder?**
1. Autorizar esta excepción a la política de cache y documentarla en `docs/caching.md` y el ADR 0010.
2. Revisar el diseño del cambio para conservar `sync = true` y evitar `unless`.
3. Otro enfoque.

No marqué ninguna tarea como completa.

---

