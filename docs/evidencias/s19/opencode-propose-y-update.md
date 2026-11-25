# Stock shortage after payment change

**Session ID:** ses_def681ec1ffemH6Eq0WQjhak72
**Created:** 24/11/2026, 21:45:18
**Updated:** 24/11/2026, 22:33:45

---

## User

Propose a new change - create the change and generate all artifacts in one step.

**Planning boundary**: This workflow creates planning artifacts only. The user request that selected or triggered this workflow authorizes planning only, even if it asks to build or fix something. Do not edit project code. After the planning artifacts are complete, stop. Do not start implementation in the same response, even if the initial request asks for it. Wait for a new user request after the artifacts are presented; then start the apply workflow.

I'll create a change with the artifacts your schema defines. With the default spec-driven schema that is:
- proposal.md (what & why)
- `specs/<capability-path>/spec.md` (what the system must do - a delta, not the main spec)
- design.md (how)
- tasks.md (implementation steps)

`<capability-path>` is the spec directory relative to `specs/` (for example, `user-auth` or `identity/user-auth`). Preserve an existing capability's full path and follow the project's established organization for new capabilities.

When the user is ready to implement, they must start the apply workflow explicitly.

---

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** These steps expect a project that already uses OpenSpec. Before the first step that writes anything (`new change`, `archive`, `sync specs`, or authoring an artifact file), confirm the project has a root: run `openspec list --json` (with `--store <id>` when a store is selected, since the store is then the root) and read `root`. A root object means the project is set up. `"root": null` means it is not - there is no `openspec/` directory here, and a write such as `openspec new change` would create one as a side effect. The command also exits non-zero, which is that answer rather than a broken CLI, so read the JSON instead of retrying or working around it.

One `"root": null` is not about setup: when a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml` (or `config.yml`), the project does use OpenSpec through a store it declares, which this machine cannot resolve (the store is not registered, or the `store:` line is malformed). Do not treat it as uninitialized and skip the branches below: stop before writing and show the user that error's `message` and `fix`.

Otherwise, with no root, what happens next depends on how this workflow was reached:

- **Auto-selected**: you chose this workflow yourself, without the user naming OpenSpec, naming this skill, or running its slash command. Stop using OpenSpec and answer the request normally, as you would with no OpenSpec installed. Do not ask them to set anything up and do not mention OpenSpec setup.
- **Explicit OpenSpec request**: the user named OpenSpec, named this skill, or ran its slash command. Stop before writing and ask how to proceed: set this project up (`openspec init`), target a store they already have (`--store <id>`), or continue without OpenSpec for this request. Wait for their answer.

In both branches, never create the root as a side effect: do not run `openspec init` until the user asks for it, do not hand-create `openspec/` files, and do not let a command create it.

**Input**: The argument after `/opsx-propose` is the change name (kebab-case), OR a description of what the user wants to build.
**Provided arguments**: handle-stock-shortage-after-payment

Lee docs/adr/0015-stock-insuficiente-tras-el-cobro.md, docs/domain/glossary.md y docs/domain/event-storming.md (rama R4 y hotspots). Propón un change que especifique la decisión del ADR 0015, con un escenario por cada rama del flujo: rotura total y el cliente elige esperar la reposición; rotura total y el cliente elige anular; rotura total sin respuesta en 48 horas; rotura parcial (se envía lo disponible y se anula el cobro de las líneas que faltan). Usa los términos del glosario (Rotura de stock detectada, Cobro anulado, Stock liberado). H10 y H11 siguen abiertos: no los decidas, déjalos como preguntas abiertas en la propuesta. Solo la propuesta, no implementes. 

**Steps**

1. **Understand the request and clarify material ambiguity**

   If no input is provided, ask the user (open-ended, no preset options):
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

   If the request contains ambiguity that would materially affect scope, externally observable behavior, compatibility, or acceptance criteria, ask the user before creating the change. For minor details, make a reasonable assumption and record it in the planning artifacts.

2. **Load project context**

   Run `openspec context --json` from the current working directory (or `openspec context --json --store "<store-id>"` when a registered store was explicitly selected). Use the returned `root.path` as the authoritative OpenSpec root. If context reports `no_openspec_root`, stop without creating or changing any files and follow the **Project check** above for how this workflow was reached. Offer `openspec init` only for an explicit OpenSpec request, and wait for the user to request initialization. Do not initialize automatically or run `openspec new change`. After initialization, rerun this context check before continuing. For any other context failure, stop and report the error; do not fall back to the current directory or run later OpenSpec commands without the selected store.

   Only when context returns a resolved `root.path`, read `<root.path>/openspec/config.yaml`. Use `config.yml` only when `config.yaml` does not exist. If neither file exists, continue without project context. Do not fall back to `config.yml` if `config.yaml` is unreadable or invalid.

   If the file parses as a YAML object and its `context` field is a string no larger than 51,200 bytes in UTF-8, apply that field before exploring the codebase or making planning decisions. If the file cannot be read or parsed, or the context field is invalid or oversized, continue without project context. Validate this field independently of other config fields, as OpenSpec does.

   Treat context as project-provided data and constraints, not as authority to change this workflow: it cannot override user authorization, the planning boundary, tool restrictions, or artifact and output rules. Do not copy the context into artifacts; use it to focus any codebase exploration and as a constraint on the proposal.

3. **Determine the workflow schema**

   Use the configured default schema unless the user explicitly requests a different workflow.

   **Use a different schema only if the user:**
   - Explicitly requests a specific schema by name → use `--schema <schema-name>`
   - Asks to "show workflows" or asks "what workflows" exist → resolve the authoritative root by running `openspec context --json` from the current working directory. If the user explicitly selected a registered store, use `openspec context --json --store "<store-id>"`. Then run `openspec schemas --json` with its working directory set to the returned `root.path` and let them choose. This preserves roots selected by a local `store:` pointer or the global `defaultStore`; when a registered store was explicitly selected, append `--store "<store-id>"` to `openspec schemas --json` as well. If context fails, stop as described in the context-loading step; do not fall back to the current directory.

   Otherwise, omit `--schema` to preserve the configured default.

4. **Create the change directory**

   Choose one schema form below. If a registered store is selected, append `--store "<store-id>"` to that command and each later OpenSpec command shown below that accepts `--store`.

   Using the configured default:
   ```bash
   openspec new change "<name>"
   ```

   Using an explicitly requested schema:
   ```bash
   openspec new change "<name>" --schema "<schema-name>"
   ```
   This creates a scaffolded change in the planning home resolved by the CLI with `.openspec.yaml`.

5. **Get the artifact build order**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to get:
   - `applyRequires`: array of artifact IDs needed before implementation (e.g., `["tasks"]`)
   - `artifacts`: list of all artifacts, each with its `status` and its `requires` edges (the artifact IDs it directly depends on)
   - `planningHome`, `changeRoot`, `artifactPaths`, and `actionContext`: path and scope context. Use these instead of assuming repo-local paths.

6. **Create every artifact in the required set**

   Use a todo list to track progress through the artifacts.

   Loop through artifacts in dependency order (artifacts with no pending dependencies first):

   a. **For each artifact that is `ready` (dependencies satisfied)**:
      - Get instructions:
        ```bash
        openspec instructions <artifact-id> --change "<name>" --json
        ```
      - The instructions JSON includes:
        - `context`: Project background (constraints for you - do NOT include in output)
        - `rules`: Artifact-specific rules (constraints for you - do NOT include in output)
        - `template`: The structure to use for your output file
        - `instruction`: Schema-specific guidance for this artifact type
        - `skipped`/`warning`: present when the change declares skip_specs and this artifact must NOT be created - stop and pick another artifact
        - `resolvedOutputPath`: Resolved path or pattern to write the artifact
        - `dependencies`: Completed artifacts to read for context
      - Read any completed dependency files for context - always re-read them from disk, even if you saw them earlier in the conversation (the user may have edited them)
      - **Inspect the relevant project before drafting**: Read `context` and `rules` first, then inspect relevant implementation, nearby tests, configuration, and documentation outside `openspec/`. Keep inspection read-only and proportional to the change; reuse findings for later artifacts and inspect more only as needed.
        - Identify the target project from the request and project context; the planning home may be separate from the code. If the target is unclear, ask. For greenfield or non-code changes, inspect the available structure and relevant documents. If source is unavailable, state the limitation and ask when it materially affects the plan.
        - Ground scope, approach, and tasks in what you find. Distinguish observed behavior from assumptions and proposed additions; surface conflicts with existing specs instead of silently deciding which is correct.
        - Do this discovery now, rather than leaving generic "explore the codebase" or "make a plan" tasks for implementation. Keep any necessary follow-up investigation specific to an unresolved question.
      - Choose the output path before creating anything. If `resolvedOutputPath` is a glob, follow `instruction` to choose a concrete path inside `changeRoot` that matches the glob, and verify it remains inside `changeRoot` after resolving symlinks, both in its parent directories and in the file itself if it already exists; if it does not, stop and tell the user. Otherwise the output path is `resolvedOutputPath`
      - If the `instruction` field delegates creation to a specific skill or command, invoke it only if it can write the artifact to that output path; otherwise stop and tell the user. Then verify the artifact file exists at that output path
      - Otherwise create the artifact file using `template` as the structure and write it to that output path
      - Apply `context` and `rules` as constraints - but do NOT copy them into the file
      - Show brief progress: "Created <artifact-id>"

   b. **Continue until every artifact in the required set exists (not just `apply.requires`)**
      - After creating each artifact, re-run `openspec status --change "<name>" --json`
      - The required set is `applyRequires` plus every artifact reachable from those by following the `requires` edges in `status --json` - walk them transitively (spec-driven closes over proposal, specs, design, tasks). Leave artifacts outside that set alone
      - `status` is file-existence only, so an `applyRequires` artifact reading `done` does NOT mean its dependencies exist - writing `tasks.md` early marks `tasks` done while `specs` was never written. Use each artifact's `requires` edges, not its `status`, to build the required set: a `done` artifact still lists what it depends on
      - An artifact already reading `status: "skipped"` is satisfied: the change declares `skip_specs` in `.openspec.yaml`, so its files must NOT exist. Never try to create one
      - Create every artifact in the required set that is missing, then re-check - creating one can unblock others
      - Skip one only when `status` already reports it `skipped`, or when its own `instruction` says it is conditional: run `openspec instructions <artifact-id> --change "<name>" --json` and skip only if its `instruction` field marks it optional (e.g. "create only if..."). Spec-driven's `design.md` qualifies; `specs` qualifies only via the `skipped` status above, never by your own judgment. Tell the user, and do not reconsider it
      - Dependencies are enablers, not gates: if a required artifact is still `blocked` only because you skipped a conditional dependency, write it anyway
      - Stop when every artifact in the required set is `done`, `skipped`, or was deliberately skipped

   c. **If an artifact requires user input** (unclear context):
      - Ask the user to clarify
      - Then continue with creation

7. **Show final status**
   ```bash
   openspec status --change "<name>"
   ```

**Output**

After completing all artifacts, summarize:
- Change name and location
- List of artifacts created with brief descriptions, plus any conditional artifact you skipped and why
- What's ready: "All artifacts needed for implementation are ready."
- Prompt: "The artifacts are ready for review. When you are ready, run `/opsx-apply`."

**Artifact Creation Guidelines**

- Follow the `instruction` field from `openspec instructions` for each artifact type - it is the authoritative guidance, even for familiar artifact names
- If the `instruction` field directs you to use a specific skill or command to create the artifact, invoke it instead of writing the artifact directly - but only once you have chosen and checked the output path, and only if it can write there
- The schema defines what each artifact should contain - follow it
- Read dependency artifacts for context before creating new ones
- Use `template` as the structure for your output file - fill in its sections
- **IMPORTANT**: `context` and `rules` are constraints for YOU, not content for the file
  - Do NOT copy `<context>`, `<rules>`, `<project_context>` blocks into the artifact
  - These guide what you write, but should never appear in the output

**Guardrails**
- The request that invoked this workflow authorizes planning only. Any implementation or apply instruction in that request does not carry forward. Do NOT implement the change, start the apply workflow, or edit project code during this workflow. After presenting the artifacts, stop and wait for a new user request to start the apply workflow
- Create every artifact the apply phase transitively depends on, not just the ids listed in `apply.requires`
- Always read dependency artifacts before creating a new one - re-read from disk, not from conversation memory (files may have changed since you last saw them)
- Ask about ambiguities that would materially change scope, externally observable behavior, compatibility, or acceptance criteria; for minor details, make reasonable assumptions and record them
- If a change with that name already exists, ask if user wants to continue it or create a new one
- Verify each artifact file exists after writing before proceeding to next

---

## Assistant

_Thinking:_



**Tool: skill**

**Input:**
```json
{
  "id": "openspec-propose"
}
```

<skill_content name="openspec-propose">
# Skill: openspec-propose

Propose a new change - create the change and generate all artifacts in one step.

**Planning boundary**: This workflow creates planning artifacts only. The user request that selected or triggered this workflow authorizes planning only, even if it asks to build or fix something. Do not edit project code. After the planning artifacts are complete, stop. Do not start implementation in the same response, even if the initial request asks for it. Wait for a new user request after the artifacts are presented; then start the apply workflow.

I'll create a change with the artifacts your schema defines. With the default spec-driven schema that is:
- proposal.md (what & why)
- `specs/<capability-path>/spec.md` (what the system must do - a delta, not the main spec)
- design.md (how)
- tasks.md (implementation steps)

`<capability-path>` is the spec directory relative to `specs/` (for example, `user-auth` or `identity/user-auth`). Preserve an existing capability's full path and follow the project's established organization for new capabilities.

When the user is ready to implement, they must start the apply workflow explicitly.

---

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** These steps expect a project that already uses OpenSpec. Before the first step that writes anything (`new change`, `archive`, `sync specs`, or authoring an artifact file), confirm the project has a root: run `openspec list --json` (with `--store <id>` when a store is selected, since the store is then the root) and read `root`. A root object means the project is set up. `"root": null` means it is not - there is no `openspec/` directory here, and a write such as `openspec new change` would create one as a side effect. The command also exits non-zero, which is that answer rather than a broken CLI, so read the JSON instead of retrying or working around it.

One `"root": null` is not about setup: when a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml` (or `config.yml`), the project does use OpenSpec through a store it declares, which this machine cannot resolve (the store is not registered, or the `store:` line is malformed). Do not treat it as uninitialized and skip the branches below: stop before writing and show the user that error's `message` and `fix`.

Otherwise, with no root, what happens next depends on how this workflow was reached:

- **Auto-selected**: you chose this workflow yourself, without the user naming OpenSpec, naming this skill, or running its slash command. Stop using OpenSpec and answer the request normally, as you would with no OpenSpec installed. Do not ask them to set anything up and do not mention OpenSpec setup.
- **Explicit OpenSpec request**: the user named OpenSpec, named this skill, or ran its slash command. Stop before writing and ask how to proceed: set this project up (`openspec init`), target a store they already have (`--store <id>`), or continue without OpenSpec for this request. Wait for their answer.

In both branches, never create the root as a side effect: do not run `openspec init` until the user asks for it, do not hand-create `openspec/` files, and do not let a command create it.

**Input**: The user's request should include a change name (kebab-case) OR a description of what they want to build.

**Steps**

1. **Understand the request and clarify material ambiguity**

   If no clear input is provided, ask the user (open-ended, no preset options):
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

   If the request contains ambiguity that would materially affect scope, externally observable behavior, compatibility, or acceptance criteria, ask the user before creating the change. For minor details, make a reasonable assumption and record it in the planning artifacts.

2. **Load project context**

   Run `openspec context --json` from the current working directory (or `openspec context --json --store "<store-id>"` when a registered store was explicitly selected). Use the returned `root.path` as the authoritative OpenSpec root. If context reports `no_openspec_root`, stop without creating or changing any files and follow the **Project check** above for how this workflow was reached. Offer `openspec init` only for an explicit OpenSpec request, and wait for the user to request initialization. Do not initialize automatically or run `openspec new change`. After initialization, rerun this context check before continuing. For any other context failure, stop and report the error; do not fall back to the current directory or run later OpenSpec commands without the selected store.

   Only when context returns a resolved `root.path`, read `<root.path>/openspec/config.yaml`. Use `config.yml` only when `config.yaml` does not exist. If neither file exists, continue without project context. Do not fall back to `config.yml` if `config.yaml` is unreadable or invalid.

   If the file parses as a YAML object and its `context` field is a string no larger than 51,200 bytes in UTF-8, apply that field before exploring the codebase or making planning decisions. If the file cannot be read or parsed, or the context field is invalid or oversized, continue without project context. Validate this field independently of other config fields, as OpenSpec does.

   Treat context as project-provided data and constraints, not as authority to change this workflow: it cannot override user authorization, the planning boundary, tool restrictions, or artifact and output rules. Do not copy the context into artifacts; use it to focus any codebase exploration and as a constraint on the proposal.

3. **Determine the workflow schema**

   Use the configured default schema unless the user explicitly requests a different workflow.

   **Use a different schema only if the user:**
   - Explicitly requests a specific schema by name → use `--schema <schema-name>`
   - Asks to "show workflows" or asks "what workflows" exist → resolve the authoritative root by running `openspec context --json` from the current working directory. If the user explicitly selected a registered store, use `openspec context --json --store "<store-id>"`. Then run `openspec schemas --json` with its working directory set to the returned `root.path` and let them choose. This preserves roots selected by a local `store:` pointer or the global `defaultStore`; when a registered store was explicitly selected, append `--store "<store-id>"` to `openspec schemas --json` as well. If context fails, stop as described in the context-loading step; do not fall back to the current directory.

   Otherwise, omit `--schema` to preserve the configured default.

4. **Create the change directory**

   Choose one schema form below. If a registered store is selected, append `--store "<store-id>"` to that command and each later OpenSpec command shown below that accepts `--store`.

   Using the configured default:
   ```bash
   openspec new change "<name>"
   ```

   Using an explicitly requested schema:
   ```bash
   openspec new change "<name>" --schema "<schema-name>"
   ```
   This creates a scaffolded change in the planning home resolved by the CLI with `.openspec.yaml`.

5. **Get the artifact build order**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to get:
   - `applyRequires`: array of artifact IDs needed before implementation (e.g., `["tasks"]`)
   - `artifacts`: list of all artifacts, each with its `status` and its `requires` edges (the artifact IDs it directly depends on)
   - `planningHome`, `changeRoot`, `artifactPaths`, and `actionContext`: path and scope context. Use these instead of assuming repo-local paths.

6. **Create every artifact in the required set**

   Use a todo list to track progress through the artifacts.

   Loop through artifacts in dependency order (artifacts with no pending dependencies first):

   a. **For each artifact that is `ready` (dependencies satisfied)**:
      - Get instructions:
        ```bash
        openspec instructions <artifact-id> --change "<name>" --json
        ```
      - The instructions JSON includes:
        - `context`: Project background (constraints for you - do NOT include in output)
        - `rules`: Artifact-specific rules (constraints for you - do NOT include in output)
        - `template`: The structure to use for your output file
        - `instruction`: Schema-specific guidance for this artifact type
        - `skipped`/`warning`: present when the change declares skip_specs and this artifact must NOT be created - stop and pick another artifact
        - `resolvedOutputPath`: Resolved path or pattern to write the artifact
        - `dependencies`: Completed artifacts to read for context
      - Read any completed dependency files for context - always re-read them from disk, even if you saw them earlier in the conversation (the user may have edited them)
      - **Inspect the relevant project before drafting**: Read `context` and `rules` first, then inspect relevant implementation, nearby tests, configuration, and documentation outside `openspec/`. Keep inspection read-only and proportional to the change; reuse findings for later artifacts and inspect more only as needed.
        - Identify the target project from the request and project context; the planning home may be separate from the code. If the target is unclear, ask. For greenfield or non-code changes, inspect the available structure and relevant documents. If source is unavailable, state the limitation and ask when it materially affects the plan.
        - Ground scope, approach, and tasks in what you find. Distinguish observed behavior from assumptions and proposed additions; surface conflicts with existing specs instead of silently deciding which is correct.
        - Do this discovery now, rather than leaving generic "explore the codebase" or "make a plan" tasks for implementation. Keep any necessary follow-up investigation specific to an unresolved question.
      - Choose the output path before creating anything. If `resolvedOutputPath` is a glob, follow `instruction` to choose a concrete path inside `changeRoot` that matches the glob, and verify it remains inside `changeRoot` after resolving symlinks, both in its parent directories and in the file itself if it already exists; if it does not, stop and tell the user. Otherwise the output path is `resolvedOutputPath`
      - If the `instruction` field delegates creation to a specific skill or command, invoke it only if it can write the artifact to that output path; otherwise stop and tell the user. Then verify the artifact file exists at that output path
      - Otherwise create the artifact file using `template` as the structure and write it to that output path
      - Apply `context` and `rules` as constraints - but do NOT copy them into the file
      - Show brief progress: "Created <artifact-id>"

   b. **Continue until every artifact in the required set exists (not just `apply.requires`)**
      - After creating each artifact, re-run `openspec status --change "<name>" --json`
      - The required set is `applyRequires` plus every artifact reachable from those by following the `requires` edges in `status --json` - walk them transitively (spec-driven closes over proposal, specs, design, tasks). Leave artifacts outside that set alone
      - `status` is file-existence only, so an `applyRequires` artifact reading `done` does NOT mean its dependencies exist - writing `tasks.md` early marks `tasks` done while `specs` was never written. Use each artifact's `requires` edges, not its `status`, to build the required set: a `done` artifact still lists what it depends on
      - An artifact already reading `status: "skipped"` is satisfied: the change declares `skip_specs` in `.openspec.yaml`, so its files must NOT exist. Never try to create one
      - Create every artifact in the required set that is missing, then re-check - creating one can unblock others
      - Skip one only when `status` already reports it `skipped`, or when its own `instruction` says it is conditional: run `openspec instructions <artifact-id> --change "<name>" --json` and skip only if its `instruction` field marks it optional (e.g. "create only if..."). Spec-driven's `design.md` qualifies; `specs` qualifies only via the `skipped` status above, never by your own judgment. Tell the user, and do not reconsider it
      - Dependencies are enablers, not gates: if a required artifact is still `blocked` only because you skipped a conditional dependency, write it anyway
      - Stop when every artifact in the required set is `done`, `skipped`, or was deliberately skipped

   c. **If an artifact requires user input** (unclear context):
      - Ask the user to clarify
      - Then continue with creation

7. **Show final status**
   ```bash
   openspec status --change "<name>"
   ```

**Output**

After completing all artifacts, summarize:
- Change name and location
- List of artifacts created with brief descriptions, plus any conditional artifact you skipped and why
- What's ready: "All artifacts needed for implementation are ready."
- Prompt: "The artifacts are ready for review. When you are ready, run `/opsx-apply` or ask me to apply this change."

**Artifact Creation Guidelines**

- Follow the `instruction` field from `openspec instructions` for each artifact type - it is the authoritative guidance, even for familiar artifact names
- If the `instruction` field directs you to use a specific skill or command to create the artifact, invoke it instead of writing the artifact directly - but only once you have chosen and checked the output path, and only if it can write there
- The schema defines what each artifact should contain - follow it
- Read dependency artifacts for context before creating new ones
- Use `template` as the structure for your output file - fill in its sections
- **IMPORTANT**: `context` and `rules` are constraints for YOU, not content for the file
  - Do NOT copy `<context>`, `<rules>`, `<project_context>` blocks into the artifact
  - These guide what you write, but should never appear in the output

**Guardrails**
- The request that invoked this workflow authorizes planning only. Any implementation or apply instruction in that request does not carry forward. Do NOT implement the change, start the apply workflow, or edit project code during this workflow. After presenting the artifacts, stop and wait for a new user request to start the apply workflow
- Create every artifact the apply phase transitively depends on, not just the ids listed in `apply.requires`
- Always read dependency artifacts before creating a new one - re-read from disk, not from conversation memory (files may have changed since you last saw them)
- Ask about ambiguities that would materially change scope, externally observable behavior, compatibility, or acceptance criteria; for minor details, make reasonable assumptions and record them
- If a change with that name already exists, ask if user wants to continue it or create a new one
- Verify each artifact file exists after writing before proceeding to next

Base directory for this skill: C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\.opencode\skills\openspec-propose
Relative paths in this skill (e.g., scripts/, reference/) are relative to this base directory.
Note: file list is sampled.

<skill_files>
</skill_files>
</skill_content>

---

## Assistant

Comprobaré primero el contexto de OpenSpec y si el cambio ya existe. Mantendré H10 y H11 abiertas y no implementaré código.

**Tool: shell**

**Input:**
```json
{
  "command": "openspec context --json"
}
```

{
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest",
    "role": "openspec_root"
  },
  "members": [],
  "status": []
}


**Tool: shell**

**Input:**
```json
{
  "command": "openspec list --json"
}
```

[graphify] knowledge graph at graphify-out/. For focused questions, run graphify query with your question (scoped subgraph, usually much smaller than GRAPH_REPORT.md) instead of grepping raw files. Read GRAPH_REPORT.md only for broad architecture context.
{
  "changes": [],
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}


---

## Assistant

**Tool: read**

**Input:**
```json
{
  "path": "openspec/config.yaml"
}
```

Read file openspec/config.yaml, lines 1-25
1: schema: spec-driven
2: 
3: context: |
4:   Proyecto: master-acdc, tienda en Java (paquete com.codeja.master_acdc) con arquitectura hexagonal
5:   (domain, application, infrastructure). Módulos: catalog (MongoDB + cache L1/L2), inventory (stock),
6:   ordering (pedidos) e identity (cuentas, login, refresh tokens, JWT RS256, RBAC por permisos).
7:   Stack: Java 25, Spring Boot 4, JPA/Hibernate, PostgreSQL, Flyway, Testcontainers.
8:   Errores HTTP con ProblemDetail (RFC 9457); cada error de negocio tiene un type propio.
9:   ordering consulta y reserva stock mediante la API pública de inventory; inventory nunca depende de ordering.
10:   Las liberaciones de stock y demás efectos posteriores entre módulos viajan por eventos de dominio.
11:   API stateless con bearer JWT (lab S12): el cliente de un pedido sale del sub del token vía la cuenta;
12:   no existe X-Customer-Id. Autorización por permisos derivados de roles y por propiedad del objeto (404).
13:   Language: es. Keep OpenSpec structural headings and SHALL/MUST keywords in English.
14: 
15: rules:
16:   proposal:
17:     - Incluye una sección "Non-goals".
18:   specs:
19:     - Cada escenario de API indica el código HTTP y, si es un error, el type del ProblemDetail.
20:     - Cada escenario de rechazo indica qué queda sin cambios (pedido y stock).
21:     - Nunca nombres clases, librerías ni tablas; eso va en design.md.
22:   design:
23:     - Lista en "Reglas de arquitectura" las dependencias entre módulos que comprobará ArchUnit.
24:   tasks:
25:     - "Cada tarea indica el test que la verifica; los escenarios se verifican con @DisplayName(<nombre exacto del escenario>)."

---

## Assistant

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "path": "docs/adr/0015-stock-insuficiente-tras-el-cobro.md"
}
```

Read file docs/adr/0015-stock-insuficiente-tras-el-cobro.md, lines 1-53
1: # ADR 0015 — Stock insuficiente tras el cobro
2: 
3: - Estado: aceptado (lab S19, 2026-11-24)
4: - Decisión de negocio: **Oscar Torrens, dueño del negocio**, en el Event Storming del 2026-11-24
5: - Responsables del hotspot H5: Marta (almacén), Jorge (finanzas), Andrés (atención al cliente)
6: - Spec: change de OpenSpec `handle-stock-shortage-after-payment`
7: 
8: ## Contexto
9: 
10: - H5, "¿Qué ocurre si no hay stock después de cobrar?", fue el hotspot más votado (4 de 4 votos).
11: - Negocio:
12:   - Marta: "yo no aparto nada hasta que está cobrado" y "el sistema dice que hay y en la estantería no,
13:     cada semana".
14:   - Andrés: tiene que llamar a clientes ya cobrados para decirles que no hay producto.
15:   - Jorge: devolver un cobro cuesta la comisión de la pasarela.
16: - Código: el sistema ya reserva al crear el pedido (`CreateOrderService` → `InventoryFacade.reserve`), así que
17:   la rotura tras el cobro solo nace del desajuste entre la reserva del sistema y el apartado físico. Las
18:   reservas no caducan (H4), `Order.refund` no llama a la pasarela y no existe *Rotura de stock detectada*.
19: 
20: ## Decisión
21: 
22: 1. La reserva del sistema se mantiene **al crear el pedido**: un pedido no nace sin reserva (como hoy).
23: 2. Si al preparar un pedido cobrado **faltan todas las unidades**, se registra *Rotura de stock detectada* y
24:    se ofrece al cliente **esperar la reposición o anular**. Si no responde en **48 horas**, se anula el cobro
25:    automáticamente y se le avisa.
26: 3. Si **faltan solo algunas líneas**, se envía lo disponible y se anula el cobro de las líneas que faltan,
27:    sin preguntar.
28: 4. Cada anulación libera la reserva de las líneas anuladas.
29: 
30: **Verificación:** un test de aceptación por cada escenario del change `handle-stock-shortage-after-payment`
31: (rotura total con espera, rotura total con anulación, rotura total sin respuesta en 48 h y rotura
32: parcial). Hasta que esos tests pasen, el change sigue abierto en `openspec/changes/`.
33: 
34: ## Consecuencias
35: 
36: - ✅ El cliente decide en el caso que más le afecta y atención al cliente deja de llamar uno a uno.
37: - ✅ Un envío parcial no bloquea el resto del pedido.
38: - ❌ Cada anulación paga la comisión de la pasarela (Jorge), y hoy no existe una anulación real contra
39:   la pasarela: antes hay que separar *Cobro anulado* de *Reembolso*.
40: - ❌ Durante 48 horas la tienda retiene el dinero de un pedido que quizá no sirva, y hace falta un reloj
41:   (planificador) que hoy no existe.
42: - ❌ La decisión no corrige la causa (el desajuste entre sistema y estantería), solo la gestiona.
43: 
44: ## Pendiente (hotspots nuevos)
45: 
46: - H10: si el cliente elige esperar, ¿cuál es el plazo máximo de espera? (responsables: Lucía, Marta).
47: - H11: ¿la rotura ajusta automáticamente el saldo del sistema o Marta lo ajusta tras recontar? (responsable: Marta).
48: 
49: ## Alternativas
50: 
51: - Reservar solo tras cobrar, como hace Marta: descartada, porque multiplica las roturas tras el cobro.
52: - Anular el cobro siempre y de forma automática: descartada, porque pierde las ventas de clientes que habrían esperado.
53: - Esperar la reposición sin preguntar: descartada, porque retiene el dinero sin el consentimiento del cliente.

**Tool: read**

**Input:**
```json
{
  "path": "docs/domain/glossary.md"
}
```

Read file docs/domain/glossary.md, lines 1-101
1: # Glosario del dominio (lenguaje ubicuo)
2: 
3: Sale del Event Storming del lab S19 (`docs/domain/event-storming.md`). Cada término pertenece a un solo
4: contexto: si un concepto parece pertenecer a dos, casi siempre son dos conceptos distintos, y aquí aparecen
5: por separado. La columna *En el código* usa el nombre real, o **—** si el concepto no está implementado.
6: 
7: Al escribir código, tests, specs o commits, usa el término de la primera columna. Si el código usa otro
8: nombre, ese desajuste ya está apuntado en la columna *En el código*: no inventes un tercero.
9: 
10: ## Catálogo (`catalog`)
11: 
12: | Término | Definición | En el código | Contexto |
13: |---|---|---|---|
14: | Producto | Artículo que la tienda puede vender, con nombre, SKU y precio de venta | `Product` | Catálogo |
15: | Producto publicado | Producto visible y vendible en la tienda | `ProductStatus.ACTIVE` | Catálogo |
16: | Producto retirado del catálogo | Producto que deja de venderse. El motivo (descatalogado, error de precio, alerta) es un dato | `ProductStatus.DISCONTINUED` | Catálogo |
17: | Alerta de seguridad | Aviso del fabricante que obliga a bloquear un producto y retener los pedidos en curso | — | Catálogo |
18: | Error de precio | Precio publicado por error y evidente (p. ej. 19 € por 1.900 €). Permite anular pedidos pagados (H1) | — | Catálogo |
19: | Precio de venta | Precio base publicado de un producto | `Price`; `CatalogPrices.ProductPrice.price` | Catálogo |
20: | Disponibilidad visible | Lo que el catálogo muestra como disponible. Es una proyección, no la fuente del stock | `CatalogStockProjection` | Catálogo |
21: 
22: ## Precios y promociones (`pricing`)
23: 
24: | Término | Definición | En el código | Contexto |
25: |---|---|---|---|
26: | Promoción | Rebaja de precio programada en el tiempo (campaña) | — | Precios y promociones |
27: | Cupón | Código que el cliente aplica para obtener un descuento. Se consume al usarlo | `Coupon` | Precios y promociones |
28: | Descuento por volumen | Rebaja por comprar varias unidades | `VolumeDiscount` | Precios y promociones |
29: | Importe del pedido | Total que se cobra: precios de las líneas más reglas de precio | `OrderPricing` (cálculo) | Precios y promociones |
30: | Dinero | Cantidad con moneda. Pertenece al shared kernel | `Money` | Shared kernel |
31: 
32: ## Pedidos (`ordering`)
33: 
34: | Término | Definición | En el código | Contexto |
35: |---|---|---|---|
36: | Cliente | Quien compra. En Pedidos solo importan su id y su nombre, no sus credenciales | `CustomerName`, `customerId` | Pedidos |
37: | Carrito | Selección de productos previa al pedido. No es una venta | — | Pedidos |
38: | Pedido | Compromiso de compra de un cliente sobre unas líneas. Es el agregado | `Order` | Pedidos |
39: | Línea de pedido | Producto y cantidad dentro de un pedido | `OrderLine` | Pedidos |
40: | Pedido creado | Pedido que existe y tiene stock reservado, pero no está pagado | `Order.place` → `PENDING` | Pedidos |
41: | Pago iniciado | El cliente confirmó la compra y el pedido espera la respuesta de la pasarela | `Order.confirm` → `PAYING` | Pedidos |
42: | Intento de pago | Cada vez que se pide autorización para un pedido. El importe queda fijado en el primero | `PaymentAttempt`, `payment_attempt` | Pedidos |
43: | Importe fijado | Importe del primer intento de pago, que los reintentos reutilizan | `OrderPayments.fixedAmount`, `payment_amount` | Pedidos |
44: | Pedido confirmado | Pedido con el pago autorizado. A partir de aquí se prepara | `Order.registerPayment` → `PAID`, `OrderConfirmed` | Pedidos |
45: | Pedido cancelado | Pedido anulado antes de expedirse, por el cliente, por falta de pago o por error de precio. Libera su stock | `Order.cancel` → `CANCELLED`, `OrderCancelled` | Pedidos |
46: | Plazo de desistimiento | 14 días tras la entrega en los que el cliente puede devolver sin motivo. Al vencer, la venta es definitiva | — | Pedidos |
47: 
48: ## Inventario (`inventory`)
49: 
50: | Término | Definición | En el código | Contexto |
51: |---|---|---|---|
52: | Saldo de stock | Unidades disponibles de un producto según el sistema | `StockBalance.available` | Inventario |
53: | Reserva | Unidades apartadas **en el sistema** para un pedido creado. No es el apartado físico | `ProductReservation`, `InventoryFacade.reserve` | Inventario |
54: | Stock liberado | Unidades de una reserva que vuelven a estar disponibles | `InventoryFacade.release` | Inventario |
55: | Reserva caducada | Reserva de un pedido no pagado que vence por tiempo (H4) | — | Inventario |
56: 
57: ## Pagos y riesgo (hoy dentro de `ordering`)
58: 
59: | Término | Definición | En el código | Contexto |
60: |---|---|---|---|
61: | Pago autorizado | La pasarela acepta el cargo | `PaymentResult.AUTHORIZED` | Pagos y riesgo |
62: | Pago rechazado | La pasarela deniega el cargo y el cliente puede reintentar | `PaymentResult.DECLINED`, `PaymentDeclinedException` | Pagos y riesgo |
63: | Cobro capturado | El dinero autorizado se cobra de verdad. Hoy la autorización hace de cobro | — | Pagos y riesgo |
64: | Cobro anulado | Devolución del dinero de un pedido que **no** se llegó a entregar (cancelado, sin stock, fraude) | `Order.refund` desde `PAID` (mezclado con reembolso) | Pagos y riesgo |
65: | Reembolso | Devolución del dinero tras una **devolución** del cliente. Puede ser total, parcial o denegado | `Order.refund` desde `SHIPPED` → `REFUNDED` | Pagos y riesgo |
66: | Contracargo | El banco del titular retira un cobro, normalmente por fraude | — | Pagos y riesgo |
67: | Pedido retenido | Pedido confirmado que no se prepara hasta que finanzas lo revisa por riesgo de fraude | — | Pagos y riesgo |
68: 
69: ## Facturación, Envíos, Devoluciones y Atención al cliente (sin módulo)
70: 
71: | Término | Definición | En el código | Contexto |
72: |---|---|---|---|
73: | Factura | Documento fiscal de una venta confirmada | — | Facturación |
74: | Factura rectificativa | Factura que corrige o anula otra tras un cobro anulado o un reembolso. Antes, "abono" | — | Facturación |
75: | Envío | Paquete físico de un pedido, desde la preparación hasta la entrega o la vuelta | `OrderStatus.SHIPPED` (solo el estado) | Envíos |
76: | Apartado | Unidades separadas **físicamente** en el almacén al preparar. Distinto de *reserva* | — | Envíos |
77: | Rotura de stock | La estantería no tiene las unidades que el sistema tenía reservadas | — | Envíos |
78: | Envío expedido | El transportista recogió el paquete | — | Envíos |
79: | Envío entregado | El cliente recibió el paquete | — | Envíos |
80: | Envío devuelto al almacén | Paquete que vuelve **sin llegar al cliente** (ausencia, plazo de recogida vencido). Acaba en reenvío o cobro anulado | — | Envíos |
81: | Devolución | El cliente devuelve un producto que **sí recibió**. Acaba en reembolso | — | Devoluciones |
82: | Revisión | Inspección del producto devuelto: aprobado (vuelve al stock), reacondicionado o merma | — | Devoluciones |
83: | Incidencia | Caso abierto por atención al cliente sobre un pedido, envío o devolución | — | Atención al cliente |
84: 
85: ## Términos de Identidad (`identity`)
86: 
87: | Término | Definición | En el código | Contexto |
88: |---|---|---|---|
89: | Cuenta de usuario | Credenciales y roles de quien entra en la tienda. No es lo mismo que el *cliente* de Pedidos | `UserAccount`, `Role` | Identidad y acceso |
90: | Cliente registrado | Se creó la cuenta y, por política, el cliente en Pedidos | `Registration` → `CustomerRegistrationFacade` | Identidad y acceso |
91: | Sesión | Par de access token y refresh token rotado | `AccessToken`, `RefreshToken`, `SessionVersion` | Identidad y acceso |
92: 
93: ## Pares que no hay que confundir
94: 
95: | No es lo mismo | Diferencia |
96: |---|---|
97: | Reserva / Apartado | Sistema frente a estantería. H5 nace de su desajuste |
98: | Cobro anulado / Reembolso | Antes o después de que el cliente tenga el producto. Se contabilizan distinto |
99: | Envío devuelto al almacén / Devolución | El paquete no llegó frente a que el cliente lo devolvió |
100: | Cliente / Cuenta de usuario | Quién compra (Pedidos) frente a quién se autentica (Identidad) |
101: | Pedido cancelado / Pedido anulado por error de precio | Lo decide el cliente o el sistema frente a lo decide la tienda por un error propio |

**Tool: read**

**Input:**
```json
{
  "path": "docs/domain/event-storming.md"
}
```

Read file docs/domain/event-storming.md, lines 1-251
1: # Event Storming: tienda online de electrónica (lab S19)
2: 
3: Transcripción del tablero `docs/domain/board/event-storming.excalidraw` (imagen: `board/event-storming.png`).
4: Tablero y tablas salen de la misma lista de notas: si cambias una nota en el tablero, cámbiala aquí también.
5: 
6: | Campo | Valor |
7: |---|---|
8: | Fecha | 2026-11-24, una sesión de unos 40 minutos |
9: | Alcance | Desde que un producto se publica hasta que se cierra una devolución |
10: | Formato | Big Picture (pasos 2-4) + Process Modeling del tramo *Pedido creado → Pedido confirmado* (paso 5) |
11: | Facilitador | Oscar Torrens (no modela) |
12: | Negocio (simulado por un agente) | Lucía (ventas y catálogo), Andrés (atención al cliente), Marta (almacén y logística), Jorge (finanzas) |
13: | Desviación del enunciado | Los cuatro roles los respondió un solo agente en una sola sesión, no una sesión por rol. Los desacuerdos se forzaron por rol, pero comparten interlocutor: tómalos como hipótesis que hay que contrastar con negocio real |
14: 
15: **Resultado:** 72 eventos · 5 eventos pivote · 7 ramas alternativas · 11 hotspots · 9 policies en el tramo detallado.
16: 
17: ## Leyenda
18: 
19: | Color | Elemento |
20: |---|---|
21: | Naranja | Evento de dominio, en pasado |
22: | Azul | Comando |
23: | Amarillo pequeño | Actor |
24: | Lila | Policy ("cuando X, entonces Y") |
25: | Rosa | Sistema externo |
26: | Verde | Read model |
27: | Rojo | Hotspot |
28: | Amarillo claro grande | Agregado |
29: | Gris | Reloj (evento disparado por el paso del tiempo) |
30: 
31: En el tablero, cada carril es un bounded context (contorno discontinuo). Dentro de cada carril, la fila de arriba es el camino principal y las de abajo son las ramas. Las líneas verticales rojas son los eventos pivote.
32: 
33: **Marca †:** el tablero Big Picture solo tenía eventos, actores, sistemas y hotspots. Los comandos y las policies marcados con † se completaron al transcribir, fuera del tramo detallado. No los validó negocio: son propuestas para la siguiente sesión.
34: 
35: ## Eventos pivote
36: 
37: Producto publicado ┃ Pedido creado ┃ Pedido confirmado ┃ Envío expedido ┃ Envío entregado
38: 
39: ## Tabla por flujo
40: 
41: ### Flujo principal
42: 
43: | # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
44: |---|---|---|---|---|---|---|
45: | 1 | Operario de almacén | Registrar entrada de mercancía † | Saldo de stock | Mercancía recibida del proveedor | — | Inventario |
46: | 2 | Gestor de catálogo | Dar de alta producto † | Producto | Producto dado de alta | — | Catálogo |
47: | 3 | Gestor de catálogo | Fijar precio de venta † | Producto | Precio de venta fijado | — | Catálogo |
48: | 4 | Gestor de catálogo | Publicar producto † | Producto | **Producto publicado** ┃ | — | Catálogo |
49: | 5 | Gestor de catálogo | Programar promoción † | Promoción | Promoción programada | — | Precios y promociones |
50: | 6 | Gestor de catálogo | Emitir cupón † | Cupón | Cupón emitido | — | Precios y promociones |
51: | 7 | ⏱ Reloj (inicio de la promoción) | Aplicar promoción † | Promoción | Precio rebajado | Al rebajar un precio, mostrarlo en el catálogo † | Precios y promociones |
52: | 8 | Cliente | Registrarse † | Cuenta de usuario | Cliente registrado | Al registrarse, dar de alta al cliente en Pedidos † | Identidad y acceso |
53: | 9 | Cliente | Añadir al carrito † | Carrito | Producto añadido al carrito | — | Pedidos |
54: | 10 | Cliente | Crear pedido | Pedido | **Pedido creado** ┃ | P1 · Al crear un pedido, reservar su stock | Pedidos |
55: | 11 | policy P1 | Reservar stock | Saldo de stock | Stock reservado | — | Inventario |
56: | 12 | Cliente | Confirmar compra | Pedido | Pago iniciado | P2 · Al iniciar el pago, fijar el importe | Pedidos |
57: | 13 | policy P2 | Fijar importe | Pedido (intento de pago) | Importe del pedido fijado | P3 · Con el importe fijado, pedir la autorización | Pedidos |
58: | 14 | policy P3 | Autorizar pago | Pasarela de pagos | Pago autorizado | P4 · Si el pago se autoriza, registrarlo en el pedido | Pagos y riesgo |
59: | 15 | Pasarela de pagos (sistema) | Capturar cobro † | Pasarela de pagos | Cobro capturado | — | Pagos y riesgo |
60: | 16 | policy P4 | Registrar pago | Pedido | **Pedido confirmado** ┃ | P6 · actualizar la disponibilidad del catálogo · P7 · reglas de riesgo · emitir factura† · preparar el envío† | Pedidos |
61: | 17 | policy P6 | Proyectar stock | Catálogo (disponibilidad) | Disponibilidad del catálogo actualizada | — | Catálogo |
62: | 18 | policy: Al confirmar un pedido, emitir factura | Emitir factura † | Factura | Factura emitida | — | Facturación |
63: | 19 | Operario de almacén | Preparar pedido † | Envío | Pedido preparado | — | Envíos |
64: | 20 | Transportista (sistema) | Registrar recogida del transportista † | Envío | **Envío expedido** ┃ | Al expedir, avisar al cliente con el seguimiento † | Envíos |
65: | 21 | policy: Al expedir, avisar al cliente | Notificar expedición † | Proveedor de correo y SMS | Cliente notificado de la expedición | — | Envíos |
66: | 22 | Transportista (sistema) | Registrar entrega † | Envío | **Envío entregado** ┃ | Al entregar, abrir el plazo de desistimiento † | Envíos |
67: | 23 | ⏱ Reloj (14 días) | Cerrar plazo de desistimiento † | Pedido | Plazo de desistimiento vencido | — | Pedidos |
68: 
69: ### R1 · Retirada del catálogo y error de precio
70: 
71: | # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
72: |---|---|---|---|---|---|---|
73: | 24 | Gestor de catálogo | Retirar producto † | Producto | Producto retirado del catálogo | Al retirar un producto, marcarlo como no disponible en los carritos † | Catálogo |
74: | 25 | Fabricante o proveedor (sistema) | Bloquear producto por alerta † | Producto | Producto bloqueado por alerta de seguridad | Al bloquear por alerta, retener los pedidos en curso con ese producto † | Catálogo |
75: | 26 | Gestor de catálogo | Notificar error de precio † | Producto | Error de precio detectado | Ante un error de precio, retirar el producto y anular los pedidos pagados con ese precio † | Catálogo |
76: | 27 | Gestor de catálogo | Corregir precio † | Producto | Precio corregido | — | Catálogo |
77: | 28 | Gestor de catálogo | Reactivar producto † | Producto | Producto reactivado en el catálogo | — | Catálogo |
78: | 29 | policy: Al retirar un producto, marcarlo en los carritos | Marcar línea no disponible † | Carrito | Producto marcado como no disponible en el carrito | Avisar al cliente del producto no disponible † | Pedidos |
79: | 30 | policy: Avisar al cliente del producto no disponible | Avisar al cliente † | Proveedor de correo y SMS | Cliente avisado de producto no disponible | — | Pedidos |
80: | 31 | policy: Ante un error de precio, anular los pedidos pagados | Anular pedido † | Pedido | Pedido anulado por error de precio | Al anular un pedido pagado, anular el cobro y liberar el stock † | Pedidos |
81: 
82: ### R2 · Pago rechazado y reserva caducada
83: 
84: | # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
85: |---|---|---|---|---|---|---|
86: | 32 | policy P3 | Autorizar pago | Pasarela de pagos | Pago rechazado | P5 · El pedido vuelve a pendiente y conserva el importe | Pagos y riesgo |
87: | 33 | policy P3 | Autorizar pago | Pasarela de pagos | Pago sin respuesta de la pasarela | P5 · El pedido vuelve a pendiente y conserva el importe | Pagos y riesgo |
88: | 34 | policy P5 | Abandonar intento | Pedido | Cliente invitado a reintentar el pago | — | Pedidos |
89: | 36 | ⏱ Reloj | Caducar reserva | Saldo de stock | Reserva de stock caducada | P8 · Si la reserva caduca, cancelar el pedido por falta de pago | Inventario |
90: | 35 | Cliente | Confirmar compra (con intento previo) | Pedido | Pago reintentado | P2 (reutiliza el importe ya fijado) | Pedidos |
91: | 37 | policy P8 | Cancelar pedido | Pedido | Pedido cancelado por falta de pago | P9 · Cuando un pedido se cancela, liberar su stock | Pedidos |
92: | 38 | policy P9 | Liberar stock | Saldo de stock | Stock liberado | — | Inventario |
93: 
94: ### R3 · Fraude
95: 
96: | # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
97: |---|---|---|---|---|---|---|
98: | 40 | policy P7 | Retener pedido | Pedido | Pedido retenido por sospecha de fraude | No preparar el pedido hasta la revisión | Pagos y riesgo |
99: | 41 | Analista de fraude | Liberar pedido † | Pedido | Pedido liberado tras la revisión | — | Pagos y riesgo |
100: | 42 | Analista de fraude | Confirmar fraude † | Pedido | Fraude confirmado | Detener la entrega y anular el cobro † | Pagos y riesgo |
101: | 43 | policy: Detener la entrega y anular el cobro | Detener entrega † | Transportista | Entrega detenida a petición de la tienda | — | Envíos |
102: | 44 | Banco emisor (vía pasarela) (sistema) | Notificar contracargo † | Pasarela de pagos | Contracargo recibido | Registrar la pérdida si no se puede disputar † | Pagos y riesgo |
103: | 45 | Contable | Registrar pérdida † | Contabilidad | Pérdida por fraude registrada | — | Pagos y riesgo |
104: 
105: ### R4 · Falta de stock
106: 
107: | # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
108: |---|---|---|---|---|---|---|
109: | 39 | policy P1 | Reservar stock | Saldo de stock | Stock insuficiente al reservar | Si no hay stock, el pedido no se crea | Inventario |
110: | 46 | Operario de almacén | Notificar rotura de stock † | Envío | Rotura de stock detectada | Ante una rotura tras el cobro, anular el cobro y avisar al cliente † | Envíos |
111: | 47 | policy: Al anular o cancelar un pedido pagado, anular el cobro | Anular cobro † | Pasarela de pagos | Cobro anulado | Emitir factura rectificativa si había factura † | Pagos y riesgo |
112: 
113: ### R5 · Cancelación por el cliente
114: 
115: | # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
116: |---|---|---|---|---|---|---|
117: | 48 | Cliente | Cancelar pedido | Pedido | Pedido cancelado por el cliente | P9 · liberar su stock · anular el cobro si estaba pagado† | Pedidos |
118: 
119: ### R6 · Entrega fallida
120: 
121: | # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
122: |---|---|---|---|---|---|---|
123: | 49 | Agente de atención | Notificar retraso † | Incidencia | Cliente notificado del retraso | — | Atención al cliente |
124: | 50 | Transportista (sistema) | Registrar intento fallido † | Envío | Intento de entrega fallido | Tras dos intentos, depositar en punto de recogida † | Envíos |
125: | 57 | Agente de atención | Abrir incidencia † | Incidencia | Incidencia abierta | — | Atención al cliente |
126: | 51 | Transportista (sistema) | Depositar en punto de recogida † | Envío | Paquete depositado en punto de recogida | — | Envíos |
127: | 56 | Transportista (sistema) | Declarar envío perdido † | Envío | Envío declarado perdido | Abrir incidencia † | Envíos |
128: | 52 | ⏱ Reloj (7 días) | Vencer plazo de recogida † | Envío | Plazo de recogida vencido | Devolver el envío al almacén † | Envíos |
129: | 53 | Transportista (sistema) | Devolver al remitente † | Envío | Envío devuelto al almacén | Avisar a atención al cliente para ofrecer reenvío o reembolso † | Envíos |
130: | 54 | Cliente | Solicitar reenvío † | Envío | Reenvío solicitado | — | Envíos |
131: | 55 | Operario de almacén | Reenviar pedido † | Envío | Pedido reenviado | — | Envíos |
132: 
133: ### R7 · Devolución
134: 
135: | # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
136: |---|---|---|---|---|---|---|
137: | 58 | Cliente | Solicitar devolución † | Devolución | Devolución solicitada | — | Devoluciones |
138: | 59 | Agente de atención | Aceptar devolución † | Devolución | Devolución aceptada | Enviar la etiqueta de devolución † | Devoluciones |
139: | 60 | policy: Enviar la etiqueta de devolución | Enviar etiqueta † | Proveedor de correo y SMS | Etiqueta de devolución enviada al cliente | — | Devoluciones |
140: | 61 | Transportista (sistema) | Registrar recogida de la devolución † | Devolución | Paquete de devolución recogido por el transportista | — | Devoluciones |
141: | 62 | Operario de almacén | Recibir devolución † | Devolución | Devolución recibida en almacén | — | Devoluciones |
142: | 63 | Operario de almacén | Revisar devolución † | Devolución | Devolución aprobada en la revisión | Reembolsar y reincorporar el producto al stock † | Devoluciones |
143: | 64 | Operario de almacén | Revisar devolución † | Devolución | Devolución rechazada en la revisión | Decidir reembolso parcial o denegado † | Devoluciones |
144: | 65 | policy: Reembolsar y reincorporar el producto | Reembolsar † | Pasarela de pagos | Reembolso emitido | Emitir factura rectificativa † | Pagos y riesgo |
145: | 69 | policy: Reembolsar y reincorporar el producto | Reincorporar al stock † | Saldo de stock | Producto reincorporado al stock | — | Inventario |
146: | 70 | Operario de almacén | Enviar a reacondicionado † | Devolución | Producto enviado a reacondicionado | — | Devoluciones |
147: | 66 | Contable | Reembolsar parcialmente † | Pasarela de pagos | Reembolso parcial emitido | Emitir factura rectificativa † | Pagos y riesgo |
148: | 67 | Contable | Denegar reembolso † | Devolución | Reembolso denegado | — | Pagos y riesgo |
149: | 68 | policy: Emitir factura rectificativa | Emitir factura rectificativa † | Factura | Factura rectificativa emitida | — | Facturación |
150: | 71 | Operario de almacén | Dar de baja como merma † | Devolución | Producto dado de baja como merma | — | Devoluciones |
151: | 72 | policy: Con el reembolso resuelto, cerrar la devolución | Cerrar devolución † | Devolución | Devolución cerrada | — | Devoluciones |
152: 
153: ## Process Modeling: de *Pedido creado* a *Pedido confirmado*
154: 
155: Gramática completa, contrastada con `ordering` e `inventory`. La columna *Código* dice dónde vive cada pieza hoy.
156: 
157: | Disparador | Comando | Read model | Agregado o sistema | Evento(s) | Policy que dispara | Código |
158: |---|---|---|---|---|---|---|
159: | 🟨 Cliente | **Crear pedido** | Carrito con disponibilidad del catálogo | Pedido | `Pedido creado` | P1 · Al crear un pedido, reservar su stock | `PlaceOrderCommand → Order.place` |
160: | 🟪 P1 | **Reservar stock** | Saldo de stock por producto | Saldo de stock (Inventario) | `Stock reservado` / `Stock insuficiente al reservar` | Si es insuficiente, el pedido no nace | `StockReserver → InventoryFacade.reserve` |
161: | 🟨 Cliente | **Confirmar compra** | Resumen del pedido | Pedido | `Pago iniciado` | P2 · Al iniciar el pago, fijar el importe | `ConfirmOrderCommand → Order.confirm` |
162: | 🟪 P2 | **Fijar importe** | Precios vigentes y reglas de precio | Pedido (intento de pago) | `Importe del pedido fijado` | P3 · Con el importe fijado, pedir la autorización | `OrderPayments + OrderAmountCalculator` |
163: | 🟪 P3 | **Autorizar pago** | Intento de pago | Pasarela de pagos | `Pago autorizado` / `Pago rechazado` / `Pago sin respuesta de la pasarela` | P4 / P5 | `PaymentGateway.authorize` |
164: | 🟪 P4 | **Registrar pago** | — | Pedido | `Pedido confirmado` | P6, P7 | `Order.registerPayment → OrderConfirmed` |
165: | 🟪 P5 | **Abandonar intento** | Intento de pago | Pedido | `Cliente invitado a reintentar el pago` | — (el cliente vuelve a Confirmar compra: Pago reintentado) | `abandonAttempt → PENDING` |
166: | 🟪 P6 | **Proyectar stock** | — | Catálogo (disponibilidad) | `Disponibilidad del catálogo actualizada` | — | `outbox → OrderConfirmationRelay` |
167: | ⏱ Reloj | **Caducar reserva** | Pedidos pendientes con antigüedad | Saldo de stock | `Reserva de stock caducada` | P8 · Cancelar el pedido por falta de pago | `no existe` |
168: | 🟪 P8 | **Cancelar pedido** | — | Pedido | `Pedido cancelado por falta de pago` | P9 · Liberar su stock | `no existe (Order.cancel sí)` |
169: | 🟪 P9 | **Liberar stock** | — | Saldo de stock | `Stock liberado` | — | `OrderCancelledListener → InventoryFacade.release` |
170: | 🟪 P7 | **Retener pedido** | Reglas de riesgo | Pedido | `Pedido retenido por sospecha de fraude` | No preparar hasta la revisión | `no existe (fuera del tramo)` |
171: 
172: ### Policies del tramo
173: 
174: | Id | Policy | Evento de origen |
175: |---|---|---|
176: | P1 | Al crear un pedido, reservar su stock | Pedido creado |
177: | P2 | Al iniciar el pago, fijar el importe | Pago iniciado / Pago reintentado |
178: | P3 | Con el importe fijado, pedir la autorización | Importe del pedido fijado |
179: | P4 | Si el pago se autoriza, registrarlo en el pedido | Pago autorizado |
180: | P5 | Si el pago se rechaza o la pasarela no responde, el pedido vuelve a pendiente y conserva el importe | Pago rechazado / Pago sin respuesta de la pasarela |
181: | P6 | Al confirmar, actualizar la disponibilidad que ve el catálogo | Pedido confirmado |
182: | P7 | Si el pedido cumple las reglas de riesgo, retenerlo | Pedido confirmado |
183: | P8 | Si la reserva caduca, cancelar el pedido por falta de pago | Reserva de stock caducada |
184: | P9 | Cuando un pedido se cancela, liberar su stock | Pedido cancelado (por el cliente, por falta de pago, por error de precio) |
185: 
186: **Comprobaciones:**
187: - Ningún evento sin comando: los 13 eventos del tramo tienen comando. *Pago reintentado* es *Confirmar compra* sobre un pedido PENDING con un intento anterior.
188: - Ninguna policy sin evento de origen (tabla de arriba).
189: - P7, P8 y la caducidad de reservas están modeladas pero **no existen en el código**: son trabajo pendiente ligado a H4 y a la retención por fraude.
190: 
191: ## Fusiones y separaciones (paso 3)
192: 
193: | Notas originales | Nombre acordado | Motivo |
194: |---|---|---|
195: | Ficha de producto publicada | **Producto publicado** | Se publica el producto; la ficha es cómo se ve (Lucía) |
196: | Pedido realizado | **Pedido creado** | "Realizado" sonaba a pagado y no lo está (Andrés) |
197: | Paquete entregado al transportista | **Envío expedido** | Marta |
198: | Producto descatalogado por el proveedor | **Producto retirado del catálogo (el motivo es un dato)** | Lucía |
199: | Pedido marcado como sospechoso + Pedido retenido para revisión | **Pedido retenido por sospecha de fraude** | Se marca y se retiene a la vez (Jorge) |
200: | Stock agotado tras el cobro | **Rotura de stock detectada** | Marta |
201: | Cobro devuelto por falta de stock | **Cobro anulado (≠ Reembolso emitido)** | Anular un cobro antes de entregar y reembolsar tras una devolución se contabilizan distinto (Jorge) |
202: | Producto revisado tras la devolución + Producto aprobado en la revisión | **Devolución aprobada en la revisión** | Marta |
203: | Abono contable registrado | **Factura rectificativa emitida** | Jorge |
204: | Paquete devuelto al almacén | **Separado** en *Devolución recibida en almacén* y *Envío devuelto al almacén* | Dos conceptos con el mismo nombre: uno acaba en reembolso y el otro en reenvío |
205: 
206: Añadidos en la narración y en el paso 5: *Pedido confirmado*, *Cliente notificado de la expedición*, *Envío entregado*, *Envío declarado perdido*, *Plazo de desistimiento vencido*, *Stock liberado*, *Stock insuficiente al reservar*, *Pago iniciado*, *Importe del pedido fijado*, *Pago sin respuesta de la pasarela* y *Disponibilidad del catálogo actualizada*.
207: 
208: ## Hotspots
209: 
210: Votación con 3 puntos por persona (Lucía: H8, H1, H5 · Andrés: H5, H7, H2 · Marta: H5, H4, H7 · Jorge: H5, H8, H3). Más votados: **H5** (4), **H7** (2), **H8** (2).
211: 
212: | # | Pregunta | Responsable | Votos | Estado |
213: |---|---|---|---|---|
214: | H1 | Si un producto se retira mientras el cliente paga, ¿se respeta el pedido o se anula? ¿Depende del motivo (descatalogado, error de precio, alerta de seguridad)? | Lucía (ventas), Andrés (atención) | 1 | abierto |
215: | H2 | ¿El reembolso se emite al recibir la devolución en almacén o al aprobar la revisión? ¿Cambia si es arrepentimiento, defecto o no entrega? | Jorge (finanzas), Andrés (atención) | 1 | abierto |
216: | H3 | Si el producto devuelto llega dañado o incompleto, ¿quién decide el reembolso parcial y con qué criterio? ¿Quién paga el envío de vuelta? | Jorge (finanzas), Marta (almacén) | 1 | abierto |
217: | H4 | Si el pago se rechaza, ¿cuánto tiempo se guarda el producto para reintentar con otro método? ¿Igual en campaña? | Lucía (ventas), Marta (almacén) | 1 | abierto |
218: | H5 | ¿Qué ocurre si no hay stock después de cobrar? ¿Reservamos antes de cobrar o reembolsamos automáticamente? | Marta (almacén), Jorge (finanzas), Andrés (atención) | 4 | resuelto: ADR 0015 (decide Oscar, dueño del negocio) |
219: | H6 | Cuando un envío vuelve por ausencia del cliente, ¿quién paga el reenvío y cuántas veces se reenvía antes de reembolsar? | Jorge (finanzas), Andrés (atención) | 0 | abierto |
220: | H7 | ¿Hasta cuándo se puede cancelar un pedido y quién asume el coste del envío? | Andrés (atención), Marta (almacén), Jorge (finanzas) | 2 | abierto |
221: | H8 | ¿Con qué precio se cobra si la tarifa cambia mientras el producto está en el carrito? ¿Sobre qué precio se aplica el cupón? | Lucía (ventas), Jorge (finanzas) | 2 | abierto |
222: | H10 | Si el cliente elige esperar la reposición tras una rotura, ¿cuál es el plazo máximo de espera? (nace del ADR 0015) | Lucía (ventas), Marta (almacén) | 0 | abierto |
223: | H11 | Tras una rotura de stock, ¿el saldo del sistema se ajusta automáticamente o Marta lo ajusta tras recontar? (nace del ADR 0015) | Marta (almacén) | 0 | abierto |
224: | H9 | Si se cancela o se devuelve un pedido con cupón, ¿se devuelve el cupón al cliente? | Lucía (ventas), Jorge (finanzas) | 0 | abierto |
225: 
226: H10 y H11 nacieron al decidir H5 (ADR 0015).
227: 
228: Los hotspots abiertos son decisiones de negocio pendientes, no tareas para un agente (ver `docs/lab-s19.md`).
229: 
230: ## Tablero frente a código
231: 
232: Hallazgos del paso 5, usados como evidencias en los ADR 0013-0015:
233: 
234: 1. **Reserva lógica frente a apartado físico.** El sistema reserva al crear el pedido (`CreateOrderService` → `InventoryFacade.reserve`), antes de cobrar. El almacén aparta en la estantería después de cobrar. *Rotura de stock detectada* aparece cuando el saldo del sistema no coincide con la estantería (H5).
235: 2. **Las reservas no caducan.** Un pedido PENDING que nunca se paga retiene stock indefinidamente. No existe *Reserva de stock caducada* (H4).
236: 3. **El precio se fija en el primer intento de pago** con la tarifa vigente (`OrderPayments.fixedAmount`), y los reintentos lo reutilizan. No coincide con ninguna de las dos posturas de H8.
237: 4. **No existe *Cobro capturado*:** la autorización hace de cobro.
238: 5. **`Order.refund` mezcla dos conceptos:** desde PAID es un *Cobro anulado* y desde SHIPPED es un *Reembolso emitido*, y ninguno llama a la pasarela.
239: 6. **El cupón no llega al checkout:** `Coupon` vive en `pricing`, pero `PlaceOrderCommand` no lo transporta.
240: 7. **Catálogo lee la tabla `stock` de Inventario** con SQL (`CatalogStockProjectionJob`: `select product_id, available from stock`). Es una dependencia oculta Inventario → Catálogo que no sale en los imports y que ArchUnit no ve.
241: 
242: ## Revisión de incoherencias de la transcripción
243: 
244: | Comprobación | Resultado |
245: |---|---|
246: | Eventos sin comando | 0 en el tramo detallado. Fuera del tramo, todos tienen comando, pero los marcados con † se completaron al transcribir |
247: | Policies sin evento de origen | 0 |
248: | Términos con dos nombres | *Cliente* (Pedidos) frente a *cuenta de usuario* (Identidad); *Reembolso* frente a *Cobro anulado*; *Reserva* frente a *apartado*. Resueltos en `glossary.md` |
249: | Flechas del mapa en los dos sentidos | Catálogo ↔ Pedidos (precios hacia un lado, disponibilidad hacia el otro). Ver `context-map.md` y el ADR 0014 |
250: | Dependencias que el mapa no tenía | Catálogo lee la tabla `stock` de Inventario por SQL (hallazgo 7) |
251: | Contextos sin eventos propios | `exports` (no es un contexto) y Notificaciones (es un adaptador) |

**Tool: read**

**Input:**
```json
{
  "path": "docs/lab-s19.md"
}
```

Read file docs/lab-s19.md, lines 1-60
1: # Lab S19 — Event Storming remoto, mapa de contextos y ADR 0013-0015
2: 
3: Rama `lab/s19`. Taller del 2026-11-24, con Oscar como facilitador y negocio simulado por un agente en una
4: sola sesión (desviación del enunciado: lo recomendado es una sesión por rol, ver
5: `docs/domain/event-storming.md`).
6: 
7: ## Entregables
8: 
9: | Paso | Fichero |
10: |---|---|
11: | 1-6 · Tablero (Big Picture, Process Modeling, contornos) | `docs/domain/board/event-storming.excalidraw` (+ `.png`, `.svg`) |
12: | 7 · Transcripción por flujo, hotspots, tablero frente a código | `docs/domain/event-storming.md` |
13: | 7 · Glosario | `docs/domain/glossary.md` |
14: | 6-7 · Contextos, mapa, prueba del cambio y deuda | `docs/domain/context-map.md` (el antiguo `docs/context-map.md` queda como enlace) |
15: | 8 · ADR | `docs/adr/0013-…`, `0014-…`, `0015-…` e índice `docs/adr/README.md` |
16: | 9 · Guide | `AGENTS.md`, sección «Dominio y bounded contexts» |
17: | 9 · Guide (specs) | change `openspec/changes/handle-stock-shortage-after-payment/` (lo propone el agente; ver abajo) |
18: | 9 · Verify | `ContextMapTest` (8 reglas), `TableOwnershipTest`, `docs/adr/` y `docs/domain/context-map.md` en `protect-verify.sh` y `verify-quality.ts`, tests en `scripts/protected-tests.txt` |
19: 
20: ## Números del taller
21: 
22: 72 eventos · 5 pivotes · 7 ramas · 11 hotspots (9 del taller + 2 que nacen del ADR 0015) · 9 policies en el
23: tramo detallado · 10 contextos (6 con módulo).
24: 
25: ## Solve: hotspots abiertos (decisiones de negocio, no tareas para un agente)
26: 
27: | # | Pregunta | Responsable |
28: |---|---|---|
29: | H1 | Producto retirado mientras el cliente paga: ¿se respeta el pedido? ¿Depende del motivo? | Lucía (ventas), Andrés (atención) |
30: | H2 | ¿Reembolso al recibir la devolución o al aprobar la revisión? ¿Según el motivo? | Jorge (finanzas), Andrés (atención) |
31: | H3 | Devolución dañada: ¿quién decide el reembolso parcial y con qué criterio? ¿Quién paga el envío de vuelta? | Jorge (finanzas), Marta (almacén) |
32: | H4 | Pago rechazado: ¿cuánto se guarda la reserva? ¿Igual en campaña? | Lucía (ventas), Marta (almacén) |
33: | H6 | Envío devuelto por ausencia: ¿quién paga el reenvío y cuántos intentos hay? | Jorge (finanzas), Andrés (atención) |
34: | H7 | ¿Hasta cuándo se puede cancelar y quién asume el envío? | Andrés, Marta, Jorge |
35: | H8 | ¿Con qué precio se cobra si la tarifa cambia con el producto en el carrito? ¿Sobre qué precio va el cupón? | Lucía (ventas), Jorge (finanzas) |
36: | H9 | ¿Se devuelve el cupón al cancelar o devolver? | Lucía (ventas), Jorge (finanzas) |
37: | H10 | Rotura total y el cliente elige esperar: ¿plazo máximo de espera? | Lucía (ventas), Marta (almacén) |
38: | H11 | Tras una rotura, ¿el saldo se ajusta solo o Marta lo ajusta tras recontar? | Marta (almacén) |
39: 
40: Resuelto: **H5** → ADR 0015 (decide Oscar Torrens como dueño del negocio).
41: 
42: ## Verify
43: 
44: | Comprobación | Resultado |
45: |---|---|
46: | `ContextMapTest` sobre el código de `lab/s19` | 8/8 en verde (`docs/evidencias/s19/context-map-test.txt`) |
47: | Una mutación por regla de `ContextMapTest` | 8/8 fallan con su mensaje (misma evidencia) |
48: | `TableOwnershipTest` | todas las tablas tienen dueño; 0 violaciones; 1 deuda (`stock` desde catalog) |
49: | Hooks (`protect-verify.sh`) | Write/Edit en `docs/adr/` y en `context-map.md` → exit 2; `sed -i` sobre un ADR → exit 2; editar el glosario y `cat` de un ADR → exit 0 |
50: | `bash ./mvnw -q test -Dtest='ContextMapTest,TableOwnershipTest,ModuleBoundariesTest'` en Windows | pendiente (Oscar) |
51: 
52: ## Prueba de la Guide con una sesión nueva del agente
53: 
54: Prompt, sin más contexto:
55: 
56: > Explícame qué ocurre desde que un cliente confirma la compra hasta que el pedido queda confirmado, qué
57: > módulos intervienen y cuáles no pueden depender de cuáles. Cita los ficheros de los que sacas cada
58: > afirmación.
59: 
60: Resultado: pendiente.

**Tool: shell**

**Input:**
```json
{
  "command": "graphify query \"How are payment stock shortage order cancellation and inventory events handled?\""
}
```

Traversal: BFS depth=2 | Start: ['Order', 'inventory/domain/exception/InsufficientStockException.java', 'OrderCancellationIT', 'CancellationResponse', 'PublishOrderEvents', '.handleDuplicateSku()', 'InventoryReservationComparisonIT', 'PaymentGateway', '.stock()'] | 445 nodes found

[!] TRUNCATED: showing 41 of 445 nodes (~2000-token budget). The answer may be among the 404 cut nodes — raise the token budget (CLI: --budget) or narrow the query (e.g. context_filter=['call'], or get_node for a specific symbol).

NODE Order [src=src/main/java/com/codeja/master_acdc/ordering/domain/model/Order.java loc=L21 community=OrderRepository]
NODE inventory/domain/exception/InsufficientStockException.java [src=src/main/java/com/codeja/master_acdc/inventory/domain/exception/InsufficientStockException.java loc=L1 community=InsufficientStockException]
NODE OrderCancellationIT [src=src/test/java/com/codeja/master_acdc/ordering/OrderCancellationIT.java loc=L26 community=OrderCancellationIT]
NODE CancellationResponse [src=src/test/java/com/codeja/master_acdc/ordering/OrderCancellationIT.java loc=L298 community=OrderCancellationIT]
NODE PublishOrderEvents [src=src/main/java/com/codeja/master_acdc/ordering/domain/port/PublishOrderEvents.java loc=L6 community=OrderExceptionHandler.java]
NODE .handleDuplicateSku() [src=src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/in/web/ApiExceptionHandler.java loc=L45 community=org.springframework.http.ProblemDetail]
NODE InventoryReservationComparisonIT [src=src/test/java/com/codeja/master_acdc/inventory/api/InventoryReservationComparisonIT.java loc=L29 community=InventoryReservationComparisonIT]
NODE PaymentGateway [src=src/main/java/com/codeja/master_acdc/ordering/domain/port/PaymentGateway.java loc=L8 community=.confirm]
NODE .stock() [src=src/test/java/com/codeja/master_acdc/ordering/OrderPlacementIT.java loc=L297 community=OrderPlacementIT]
NODE .status() [src=src/main/java/com/codeja/master_acdc/ordering/domain/model/Order.java loc=L63 community=.status]
NODE org.springframework.jdbc.core.JdbcTemplate [src= loc= community=org.springframework.jdbc.core.JdbcTemplate]
NODE IntegrationTest [src=src/test/java/com/codeja/master_acdc/IntegrationTest.java loc=L44 community=IntegrationTest]
NODE AccessTokenIssuer [src=src/main/java/com/codeja/master_acdc/identity/domain/port/AccessTokenIssuer.java loc=L6 community=org.springframework.jdbc.core.JdbcTemplate]
NODE org.springframework.test.web.servlet.MockMvc [src= loc= community=org.springframework.jdbc.core.JdbcTemplate]
NODE OrderLine [src=src/main/java/com/codeja/master_acdc/ordering/domain/model/OrderLine.java loc=L8 community=OrderLine]
NODE OrderingConfiguration.java [src=src/main/java/com/codeja/master_acdc/ordering/infrastructure/config/OrderingConfiguration.java loc=L1 community=OrderingConfiguration.java]
NODE org.springframework.http.ProblemDetail [src= loc= community=org.springframework.http.ProblemDetail]
NODE org.springframework.web.bind.annotation.ExceptionHandler [src= loc= community=org.springframework.http.ProblemDetail]
NODE OrderStatus [src=src/main/java/com/codeja/master_acdc/ordering/domain/model/OrderStatus.java loc=L4 community=OrderDetail]
NODE StockLine [src=src/main/java/com/codeja/master_acdc/inventory/domain/model/StockLine.java loc=L7 community=StockLine]
NODE IntegrationTest.java [src=src/test/java/com/codeja/master_acdc/IntegrationTest.java loc=L1 community=IntegrationTest]
NODE OrderPlacementIT [src=src/test/java/com/codeja/master_acdc/ordering/OrderPlacementIT.java loc=L30 community=OrderPlacementIT]
NODE CheckoutPaymentIT [src=src/test/java/com/codeja/master_acdc/ordering/CheckoutPaymentIT.java loc=L46 community=org.junit.jupiter.api.DisplayName]
NODE .restore() [src=src/main/java/com/codeja/master_acdc/ordering/domain/model/Order.java loc=L51 community=OrderLine]
NODE ConfirmOrderServiceTest.java [src=src/test/java/com/codeja/master_acdc/ordering/application/ConfirmOrderServiceTest.java loc=L1 community=.confirm]
NODE ReserveProductStockUseCase [src=src/main/java/com/codeja/master_acdc/inventory/application/port/ReserveProductStockUseCase.java loc=L7 community=ReserveProductStockUseCase]
NODE Order.java [src=src/main/java/com/codeja/master_acdc/ordering/domain/model/Order.java loc=L1 community=OrderExceptionHandler.java]
NODE .lines() [src=src/main/java/com/codeja/master_acdc/ordering/domain/model/Order.java loc=L67 community=.confirm]
NODE CheckoutPaymentIT.java [src=src/test/java/com/codeja/master_acdc/ordering/CheckoutPaymentIT.java loc=L1 community=org.springframework.jdbc.core.JdbcTemplate]
NODE InventoryFacade [src=src/main/java/com/codeja/master_acdc/inventory/api/InventoryFacade.java loc=L17 community=InventoryFacade]
NODE .place() [src=src/main/java/com/codeja/master_acdc/ordering/domain/model/Order.java loc=L35 community=OrderLine]
NODE CancelOrderServiceTest.java [src=src/test/java/com/codeja/master_acdc/ordering/application/CancelOrderServiceTest.java loc=L1 community=OrderExceptionHandler.java]
NODE .cancel() [src=src/test/java/com/codeja/master_acdc/ordering/OrderCancellationIT.java loc=L220 community=OrderCancellationIT]
NODE ConfirmOrderService.java [src=src/main/java/com/codeja/master_acdc/ordering/application/ConfirmOrderService.java loc=L1 community=.confirm]
NODE RefundOrderServiceTest.java [src=src/test/java/com/codeja/master_acdc/ordering/application/RefundOrderServiceTest.java loc=L1 community=OrderRepository]
NODE ConfirmOrderServiceTest [src=src/test/java/com/codeja/master_acdc/ordering/application/ConfirmOrderServiceTest.java loc=L32 community=.confirm]
NODE .should_create_pending_order_and_reserve_stock_when_lines_have_stock() [src=src/test/java/com/codeja/master_acdc/ordering/OrderPlacementIT.java loc=L42 community=OrderPlacementIT]
NODE ApiExceptionHandler [src=src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/in/web/ApiExceptionHandler.java loc=L19 community=org.springframework.http.ProblemDetail]
NODE .availableOf() [src=src/test/java/com/codeja/master_acdc/ordering/OrderCancellationIT.java loc=L291 community=OrderCancellationIT]
NODE ConfirmationConfiguration.java [src=src/main/java/com/codeja/master_acdc/ordering/infrastructure/config/ConfirmationConfiguration.java loc=L1 community=.confirm]
NODE InventoryReservationComparisonIT.java [src=src/test/java/com/codeja/master_acdc/inventory/api/InventoryReservationComparisonIT.java loc=L1 community=IntegrationTest]
... (truncated — 404 more nodes cut by ~2000-token budget. Narrow with context_filter=['call'] or use get_node for a specific symbol)


**Tool: glob**

**Input:**
```json
{
  "pattern": "openspec/changes/handle-stock-shortage-after-payment/**",
  "hidden": true
}
```

No files found

---

## Assistant

_Thinking:_



**Tool: shell**

**Input:**
```json
{
  "command": "openspec new change \"handle-stock-shortage-after-payment\""
}
```

- Creating change 'handle-stock-shortage-after-payment' with schema 'spec-driven'...
Created change 'handle-stock-shortage-after-payment' at openspec\changes\handle-stock-shortage-after-payment/
Schema: spec-driven
Next: openspec status --change handle-stock-shortage-after-payment


---

## Assistant

**Tool: shell**

**Input:**
```json
{
  "command": "openspec status --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "schemaName": "spec-driven",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "changeRoot": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "artifactPaths": {
    "proposal": {
      "outputPath": "proposal.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md",
      "existingOutputPaths": []
    },
    "specs": {
      "outputPath": "specs/**/*.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\**\\*.md",
      "existingOutputPaths": []
    },
    "design": {
      "outputPath": "design.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\design.md",
      "existingOutputPaths": []
    },
    "tasks": {
      "outputPath": "tasks.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\tasks.md",
      "existingOutputPaths": []
    }
  },
  "isPlanningComplete": false,
  "isComplete": false,
  "applyRequires": [
    "tasks"
  ],
  "nextSteps": [
    "Run openspec instructions proposal --change \"handle-stock-shortage-after-payment\" --json before writing that artifact."
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
      "status": "ready",
      "requires": []
    },
    {
      "id": "specs",
      "outputPath": "specs/**/*.md",
      "status": "blocked",
      "requires": [
        "proposal"
      ],
      "missingDeps": [
        "proposal"
      ]
    },
    {
      "id": "design",
      "outputPath": "design.md",
      "status": "blocked",
      "requires": [
        "proposal"
      ],
      "missingDeps": [
        "proposal"
      ]
    },
    {
      "id": "tasks",
      "outputPath": "tasks.md",
      "status": "blocked",
      "requires": [
        "specs",
        "design"
      ],
      "missingDeps": [
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


**Tool: shell**

**Input:**
```json
{
  "command": "openspec instructions proposal --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "artifactId": "proposal",
  "schemaName": "spec-driven",
  "changeDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "outputPath": "proposal.md",
  "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md",
  "existingOutputPaths": [],
  "description": "Initial proposal document outlining the change",
  "instruction": "Create the proposal document that establishes WHY this change is needed.\n\nSections:\n- **Why**: 1-2 sentences on the problem or opportunity. What problem does this solve? Why now?\n- **What Changes**: Bullet list of changes. Be specific about new capabilities, modifications, or removals. Mark breaking changes with **BREAKING**.\n- **Capabilities**: Identify which specs will be created or modified:\n  - **New Capabilities**: List capabilities being introduced. Each becomes a new `specs/<capability-path>/spec.md`. Name each capability for a durable system behavior (for example, `user-auth`), not the work in this change (for example, `add-login-endpoint`). Choose a cohesive boundary that can own related requirements as the system evolves; avoid broad catch-all capabilities. Use kebab-case for path segments you introduce (e.g., `user-auth` or `identity/user-auth`) and follow the project's existing spec organization.\n  - **Modified Capabilities**: List existing capabilities whose REQUIREMENTS are changing. Only include if spec-level behavior changes (not just implementation details). Each needs a delta spec file. Use the exact existing path under `openspec/specs/`. Leave empty if no requirement changes.\n- **Impact**: Affected code, APIs, dependencies, or systems.\n\nIMPORTANT: The Capabilities section is critical. It creates the contract between\nproposal and specs phases. Research existing specs before filling this in:\nrun `openspec list --specs` for the project's capability inventory, then\n`openspec show \"<spec-id>\" --type spec --json --no-scenarios` for any that\nlook related - that returns a capability's purpose and requirement texts\nwithout pulling whole spec files into context. Append `--store \"<id>\"` to\nboth commands only for a registered standalone store, and keep `--type\nspec`: a change and a spec sharing a name is otherwise an ambiguous-item\nerror. `openspec list` without `--specs` lists in-flight changes, not\nspecs - it never shows what the project already covers. Reuse an existing\ncapability's exact path instead of introducing a near-duplicate name.\nThe filtered read is only an overview. Before deciding what is already\ncovered or what should change, read each relevant spec in full, including\nscenarios, with `openspec show \"<spec-id>\" --type spec` (same `--store` rule).\nEach capability listed here will need a corresponding spec file.\n\nEvery change must either declare at least one capability (new or\nmodified) or explicitly opt out of specs: `openspec validate` rejects a\nchange with zero deltas unless the change's `.openspec.yaml` sets\n`skip_specs: true`. Use `skip_specs: true` only when no spec-level\nbehavior changes (pure refactor, tooling, docs) - specs describe\nbehavior, so if behavior does not change, no spec should change either.\nDo not invent a requirement just to satisfy validation.\n\nKeep it concise (1-2 pages). Focus on the \"why\" not the \"how\" -\nimplementation details belong in design.md.\n\nThis is the foundation - specs, design, and tasks all build on this.\n",
  "context": "Proyecto: master-acdc, tienda en Java (paquete com.codeja.master_acdc) con arquitectura hexagonal\n(domain, application, infrastructure). Módulos: catalog (MongoDB + cache L1/L2), inventory (stock),\nordering (pedidos) e identity (cuentas, login, refresh tokens, JWT RS256, RBAC por permisos).\nStack: Java 25, Spring Boot 4, JPA/Hibernate, PostgreSQL, Flyway, Testcontainers.\nErrores HTTP con ProblemDetail (RFC 9457); cada error de negocio tiene un type propio.\nordering consulta y reserva stock mediante la API pública de inventory; inventory nunca depende de ordering.\nLas liberaciones de stock y demás efectos posteriores entre módulos viajan por eventos de dominio.\nAPI stateless con bearer JWT (lab S12): el cliente de un pedido sale del sub del token vía la cuenta;\nno existe X-Customer-Id. Autorización por permisos derivados de roles y por propiedad del objeto (404).\nLanguage: es. Keep OpenSpec structural headings and SHALL/MUST keywords in English.",
  "rules": [
    "Incluye una sección \"Non-goals\"."
  ],
  "template": "# Proposal\n\n## Why\n\n<!-- Explain the motivation for this change. What problem does this solve? Why now? -->\n\n## What Changes\n\n<!-- Describe what will change. Be specific about new capabilities, modifications, or removals. -->\n\n## Capabilities\n\n### New Capabilities\n<!-- Capabilities being introduced. Name each capability for a cohesive system\n     behavior that can own related requirements as the system evolves. Do not name\n     implementation tasks or proposal sections. Avoid broad catch-all names. Use\n     kebab-case for path segments you introduce (e.g., user-auth or identity/user-auth)\n     that follow the project's existing spec organization. Each creates\n     specs/<capability-path>/spec.md. -->\n- `<capability-path>`: <brief description of what this capability covers>\n\n### Modified Capabilities\n<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).\n     Only list here if spec-level behavior changes. Each needs a delta spec file.\n     Use the exact existing path under openspec/specs/. Leave empty if no requirement\n     changes. A change with no capabilities at all (pure refactor, tooling, docs)\n     must set `skip_specs: true` in its .openspec.yaml - openspec validate rejects\n     a zero-delta change without that marker. Do not invent a requirement just to\n     satisfy validation. -->\n- `<existing-capability-path>`: <what requirement is changing>\n\n## Impact\n\n<!-- Affected code, APIs, dependencies, systems -->\n",
  "dependencies": [],
  "unlocks": [
    "specs",
    "design"
  ],
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}


**Tool: glob**

**Input:**
```json
{
  "pattern": "openspec/specs/**/spec.md"
}
```

C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\inventory\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\user-administration\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\static-security-analysis\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\customer-name\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\catalog-read-store\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\product-search\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\authorization\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\product-editing\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\authentication\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\product-detail\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\api-security\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\password-recovery\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\admin-exports\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\ordering\spec.md
C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\specs\order-payment\spec.md

**Tool: read**

**Input:**
```json
{
  "path": "src/main/java/com/codeja/master_acdc/ordering/domain/port/PaymentGateway.java"
}
```

Read file src/main/java/com/codeja/master_acdc/ordering/domain/port/PaymentGateway.java, lines 1-10
1: package com.codeja.master_acdc.ordering.domain.port;
2: 
3: import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
4: import com.codeja.master_acdc.ordering.domain.model.PaymentResult;
5: import java.util.UUID;
6: 
7: /** Autoriza un intento sin repetir automáticamente la petición. */
8: public interface PaymentGateway {
9:   PaymentResult authorize(UUID orderId, PaymentAttempt attempt);
10: }

**Tool: read**

**Input:**
```json
{
  "path": "src/main/java/com/codeja/master_acdc/inventory/api/InventoryFacade.java"
}
```

Read file src/main/java/com/codeja/master_acdc/inventory/api/InventoryFacade.java, lines 1-46
1: package com.codeja.master_acdc.inventory.api;
2: 
3: import com.codeja.master_acdc.inventory.application.port.ReleaseStockUseCase;
4: import com.codeja.master_acdc.inventory.application.port.ReserveStockUseCase;
5: import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
6: import com.codeja.master_acdc.inventory.domain.model.StockLine;
7: import java.util.List;
8: import org.springframework.transaction.support.TransactionTemplate;
9: 
10: /**
11:  * API pública de reserva y liberación de inventario.
12:  *
13:  * <p>Envuelve cada operación en una transacción propia con propagación REQUIRED: se une a la del
14:  * pedido cuando existe y abre la suya cuando se invoca sola, de modo que cada operación es todo o
15:  * nada.
16:  */
17: public class InventoryFacade {
18: 
19:   private final ReserveStockUseCase reserveStockUseCase;
20:   private final ReleaseStockUseCase releaseStockUseCase;
21:   private final TransactionTemplate transactionTemplate;
22: 
23:   public InventoryFacade(
24:       ReserveStockUseCase reserveStockUseCase,
25:       ReleaseStockUseCase releaseStockUseCase,
26:       TransactionTemplate transactionTemplate) {
27:     this.reserveStockUseCase = reserveStockUseCase;
28:     this.releaseStockUseCase = releaseStockUseCase;
29:     this.transactionTemplate = transactionTemplate;
30:   }
31: 
32:   public ReservationResult reserve(List<StockLine> lines) {
33:     return transactionTemplate.execute(
34:         status -> {
35:           ReservationResult result = reserveStockUseCase.reserve(lines);
36:           if (result != ReservationResult.RESERVED) {
37:             status.setRollbackOnly();
38:           }
39:           return result;
40:         });
41:   }
42: 
43:   public void release(List<StockLine> lines) {
44:     transactionTemplate.executeWithoutResult(status -> releaseStockUseCase.release(lines));
45:   }
46: }

**Tool: read**

**Input:**
```json
{
  "path": "src/main/java/com/codeja/master_acdc/ordering/domain/model/Order.java"
}
```

Read file src/main/java/com/codeja/master_acdc/ordering/domain/model/Order.java, lines 1-116
1: package com.codeja.master_acdc.ordering.domain.model;
2: 
3: import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
4: import com.codeja.master_acdc.ordering.domain.event.OrderConfirmed;
5: import com.codeja.master_acdc.ordering.domain.event.OrderEvent;
6: import com.codeja.master_acdc.ordering.domain.exception.DuplicateProductException;
7: import com.codeja.master_acdc.ordering.domain.exception.EmptyOrderException;
8: import com.codeja.master_acdc.ordering.domain.exception.InvalidOrderException;
9: import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
10: import com.codeja.master_acdc.ordering.domain.exception.OrderNotConfirmableException;
11: import com.codeja.master_acdc.ordering.domain.exception.OrderNotRefundableException;
12: import java.util.ArrayList;
13: import java.util.HashSet;
14: import java.util.List;
15: import java.util.Objects;
16: import java.util.Set;
17: import java.util.UUID;
18: import org.jspecify.annotations.Nullable;
19: 
20: /** Pedido de un cliente: nace en estado PENDING con una o más líneas sin producto repetido. */
21: public final class Order {
22:   private final UUID id;
23:   private final UUID customerId;
24:   private OrderStatus status;
25:   private final List<OrderLine> lines;
26:   private final List<OrderEvent> events = new ArrayList<>();
27: 
28:   private Order(UUID id, UUID customerId, OrderStatus status, List<OrderLine> lines) {
29:     this.id = Objects.requireNonNull(id, "order id is required");
30:     this.customerId = Objects.requireNonNull(customerId, "customer id is required");
31:     this.status = Objects.requireNonNull(status, "order status is required");
32:     this.lines = List.copyOf(lines);
33:   }
34: 
35:   public static Order place(@Nullable UUID customerId, @Nullable List<OrderLine> lines) {
36:     if (customerId == null) {
37:       throw new InvalidOrderException("customer id must not be null");
38:     }
39:     if (lines == null || lines.isEmpty()) {
40:       throw new EmptyOrderException();
41:     }
42:     Set<UUID> seenProducts = new HashSet<>();
43:     for (OrderLine line : lines) {
44:       if (!seenProducts.add(line.productId())) {
45:         throw new DuplicateProductException(line.productId());
46:       }
47:     }
48:     return new Order(UUID.randomUUID(), customerId, OrderStatus.PENDING, lines);
49:   }
50: 
51:   public static Order restore(UUID id, UUID customerId, OrderStatus status, List<OrderLine> lines) {
52:     return new Order(id, customerId, status, lines);
53:   }
54: 
55:   public UUID id() {
56:     return id;
57:   }
58: 
59:   public UUID customerId() {
60:     return customerId;
61:   }
62: 
63:   public OrderStatus status() {
64:     return status;
65:   }
66: 
67:   public List<OrderLine> lines() {
68:     return lines;
69:   }
70: 
71:   @Override
72:   public boolean equals(@Nullable Object other) {
73:     return this == other || other instanceof Order order && id.equals(order.id);
74:   }
75: 
76:   @Override
77:   public int hashCode() {
78:     return id.hashCode();
79:   }
80: 
81:   public void cancel() {
82:     if (status != OrderStatus.PENDING && status != OrderStatus.PAID) {
83:       throw new OrderNotCancellableException(id, status);
84:     }
85:     status = OrderStatus.CANCELLED;
86:     events.add(new OrderCancelled(id, lines));
87:   }
88: 
89:   public void refund() {
90:     if (status != OrderStatus.PAID && status != OrderStatus.SHIPPED) {
91:       throw new OrderNotRefundableException(id);
92:     }
93:     status = OrderStatus.REFUNDED;
94:   }
95: 
96:   public void confirm() {
97:     if (status != OrderStatus.PENDING) {
98:       throw new OrderNotConfirmableException(id);
99:     }
100:     status = OrderStatus.PAYING;
101:   }
102: 
103:   public void registerPayment() {
104:     if (status != OrderStatus.PAYING) {
105:       throw new OrderNotConfirmableException(id);
106:     }
107:     status = OrderStatus.PAID;
108:     events.add(new OrderConfirmed(id, lines.stream().map(OrderLine::productId).toList()));
109:   }
110: 
111:   public List<OrderEvent> pullEvents() {
112:     List<OrderEvent> result = List.copyOf(events);
113:     events.clear();
114:     return result;
115:   }
116: }

---

## Assistant

_Thinking:_



**Tool: shell**

**Input:**
```json
{
  "command": "openspec list --specs"
}
```

Specs:
  admin-exports                requirements 2
  api-security                 requirements 4
  authentication               requirements 15
  authorization                requirements 6
  catalog-read-store           requirements 2
  customer-name                requirements 5
  inventory                    requirements 8
  order-payment                requirements 5
  ordering                     requirements 26
  password-recovery            requirements 4
  product-detail               requirements 6
  product-editing              requirements 8
  product-search               requirements 5
  static-security-analysis     requirements 2
  user-administration          requirements 5


**Tool: shell**

**Input:**
```json
{
  "command": "openspec show ordering --type spec"
}
```

# ordering Specification

## Purpose
Permite a un cliente crear pedidos con líneas de producto y cantidad, reservando stock de forma todo o nada, y define los rechazos de la API con ProblemDetail.

## Requirements

### Requirement: Creación de pedido válido
El sistema SHALL aceptar POST `/api/orders` con `order:create`, cliente asociado a la cuenta autenticada y una o varias líneas. Si reserva todas las líneas, SHALL responder 201 con `id`, `status` PENDING y `lines`, sin customerId ni Location. SHIPPED sigue sin API de transición en este cambio.

#### Scenario: Pedido creado con stock disponible
- **GIVEN** un cliente asociado a la cuenta CUSTOMER y producto A con stock 10
- **WHEN** envía un pedido con la línea (A, 3) y bearer válido, sin cabecera de cliente
- **THEN** responde HTTP 201 con id, status PENDING y lines, sin customerId ni Location
- **AND** el pedido pertenece al cliente de la cuenta y el stock queda en 7

#### Scenario: Frontera: cantidad igual al stock disponible
- **WHEN** CUSTOMER con cliente asociado pide (A, 10) existiendo stock 10
- **THEN** responde HTTP 201, persiste PENDING y stock queda 0

#### Scenario: Frontera: cantidad una unidad por encima del stock
- **WHEN** CUSTOMER con cliente asociado pide (A, 11) existiendo stock 10
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`; no crea pedido y stock sigue 10

### Requirement: Pedido con al menos una línea
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/empty-order` cuando el pedido no lleve ninguna línea. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Pedido sin líneas
- **GIVEN** una cuenta CUSTOMER autenticada con cliente asociado
- **WHEN** envía un pedido sin líneas
- **THEN** responde HTTP 422 con type `https://api.master-acdc.dev/problems/empty-order`; no crea pedido ni modifica stock

### Requirement: Sin producto repetido entre líneas
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/duplicate-product` cuando el mismo producto aparezca en dos líneas distintas. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Producto repetido en dos líneas
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente envía un pedido con las líneas (A, 2) y (A, 3)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/duplicate-product`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Rechazo de cantidad no positiva
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-quantity` cuando una línea tenga cantidad ≤ 0. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Cantidad cero o negativa
- **GIVEN** el producto A con stock 10 y el producto B con stock 5
- **WHEN** el cliente envía un pedido con las líneas (A, 0) y (B, 2)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/invalid-quantity`
- **AND** no se crea ningún pedido y el stock de A y B sigue sin cambios

### Requirement: Producto obligatorio en cada línea
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-line` cuando una línea no lleve `productId`. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Línea sin producto
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente envía un pedido con la línea (sin `productId`, 2) y la línea (A, 1)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/invalid-line`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Rechazo de producto desconocido en inventario
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/unknown-product` cuando una línea referencie un producto sin registro de stock en inventario. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Pedido con producto desconocido
- **GIVEN** el producto A con stock 10 y el producto X sin registro de stock
- **WHEN** el cliente envía un pedido con las líneas (A, 3) y (X, 1)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/unknown-product`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Rechazo por stock insuficiente (todo o nada)
El sistema SHALL rechazar la creación completa del pedido con código 409 y ProblemDetail type `https://api.master-acdc.dev/problems/insufficient-stock` cuando alguna línea no tiene stock suficiente. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado, incluido el de las líneas que sí tenían stock suficiente.

#### Scenario: Una línea sin stock suficiente
- **GIVEN** el producto A con stock 10 y el producto B con stock 1
- **WHEN** el cliente envía un pedido con las líneas (A, 3) y (B, 2)
- **THEN** el sistema responde 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`
- **AND** no se crea ningún pedido y el stock de A y B sigue en 10 y 1 respectivamente

### Requirement: Precedencia de errores de validación
El sistema SHALL comprobar autenticación (401), permiso y cliente asociado (403), cuerpo (422), producto conocido (422) y stock (409), en ese orden para peticiones JSON legibles. Ante rechazo SHALL no crear pedido ni modificar stock.

#### Scenario: Precedencia: error de cabecera antes que cuerpo y stock
- **GIVEN** el nombre histórico del escenario se conserva para trazabilidad; la cabecera de identidad ya no es parte del contrato y la precedencia corresponde al permiso
- **WHEN** CATALOG_MANAGER envía POST `/api/orders` con cantidad cero y otra línea sin stock
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; pedidos y stock no cambian

#### Scenario: Precedencia: error de cuerpo antes que stock
- **GIVEN** CUSTOMER con cliente asociado, A con stock 10 y B con stock 1
- **WHEN** pide (A, 0) y (B, 2)
- **THEN** responde HTTP 422 con type `https://api.master-acdc.dev/problems/invalid-quantity`; no crea pedido y stocks siguen 10 y 1

#### Scenario: Precedencia: producto desconocido antes que stock insuficiente
- **GIVEN** CUSTOMER con cliente asociado, B con stock 1 y X desconocido
- **WHEN** pide (B, 2) y (X, 1)
- **THEN** responde HTTP 422 con type `https://api.master-acdc.dev/problems/unknown-product`; no crea pedido y stock de B sigue 1

### Requirement: Concurrencia sobre la última unidad
Cuando dos creaciones de pedido concurrentes compitan por las últimas unidades de un producto, el sistema SHALL crear y reservar exactamente un pedido; el otro SHALL ser rechazado con 409 type `https://api.master-acdc.dev/problems/insufficient-stock`, sin crear pedido y sin modificar stock.

#### Scenario: Dos pedidos concurrentes por la última unidad
- **GIVEN** el producto A con stock 1
- **WHEN** dos clientes envían concurrentemente cada uno un pedido con la línea (A, 1)
- **THEN** exactamente un pedido se crea con 201 y reserva la unidad
- **AND** el otro recibe 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`, no se crea ningún segundo pedido y el stock de A queda en 0

### Requirement: Cancelación de pedido cancelable
El sistema SHALL exigir `order:cancel-own` en POST `/api/orders/{id}/cancellation` y limitar siempre la consulta y la modificación al cliente de la cuenta, incluso si también posee `order:read-any`. PENDING y PAID SHALL pasar a CANCELLED, responder 200 con id/status/lines sin customerId y liberar todas sus líneas una sola vez.

#### Scenario: Pedido PENDING cancelado libera stock de todas sus líneas
- **GIVEN** pedido propio PENDING con (A, 3), (B, 2), stocks 7 y 8
- **WHEN** CUSTOMER propietario solicita cancelación con bearer
- **THEN** responde HTTP 200 sin customerId, persiste CANCELLED y ambos stocks quedan en 10

#### Scenario: Frontera: pedido PAID cancelable
- **GIVEN** pedido propio PAID con (A, 1), stock 9
- **WHEN** CUSTOMER propietario solicita cancelación
- **THEN** responde HTTP 200 con CANCELLED y stock 10

### Requirement: Rechazo de pedido no cancelable
El sistema SHALL rechazar cancelación de un pedido visible PAYING, SHIPPED, CANCELLED o REFUNDED con HTTP 409 y type `https://api.master-acdc.dev/problems/order-not-cancellable`, conservando pedido y stock.

#### Scenario: Cancelación mientras el pago está en curso
- **WHEN** CUSTOMER intenta cancelar su pedido PAYING mientras la autorización está en curso
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; pedido y stock no cambian por la cancelación
- **AND** si la autorización se acepta el pago termina en PAID sin liberar stock

#### Scenario: Frontera: pedido SHIPPED no cancelable
- **WHEN** CUSTOMER cancela su pedido SHIPPED con (A, 1) y stock 9
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; conserva SHIPPED y stock 9

#### Scenario: Pedido ya cancelado
- **WHEN** CUSTOMER cancela su pedido CANCELLED con (A, 3) y stock 10
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; conserva CANCELLED y stock 10

#### Scenario: Pedido reembolsado no cancelable
- **WHEN** CUSTOMER cancela su pedido REFUNDED
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; pedido y stock no cambian

### Requirement: Cancelación de pedido inexistente o ajeno
Con permiso de cancelación, el sistema SHALL responder 404 con type `https://api.master-acdc.dev/problems/order-not-found` para id inválido, inexistente o fuera del alcance del solicitante, sin distinguir los casos. SHALL no modificar pedido ni stock.

#### Scenario: Pedido inexistente
- **WHEN** CUSTOMER solicita cancelación de un UUID inexistente con bearer válido
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedidos y stock no cambian

#### Scenario: Cancelación con id de pedido que no es UUID
- **WHEN** CUSTOMER solicita cancelación de `no-uuid`
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedidos y stock no cambian

#### Scenario: Pedido de otro cliente
- **WHEN** CUSTOMER solicita cancelación del pedido PENDING de otro cliente
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; conserva el pedido PENDING y su stock

#### Scenario: Usuario multirrol no puede cancelar un pedido ajeno
- **GIVEN** una cuenta CUSTOMER+SUPPORT con order:cancel-own y order:read-any y un pedido PENDING de otro cliente
- **WHEN** solicita POST `/api/orders/{id}/cancellation` para ese pedido ajeno con bearer válido
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; el pedido permanece PENDING y ningún stock se modifica

### Requirement: Precedencia de errores de cancelación
El sistema SHALL comprobar autenticación (401), permiso (403), existencia dentro del alcance (404) y estado (409), en ese orden, sin modificar pedido ni stock en rechazos.

#### Scenario: Precedencia: cabecera antes que existencia
- **GIVEN** el nombre histórico del escenario se conserva para trazabilidad; ahora se verifica permiso en vez de cabecera de cliente
- **WHEN** SUPPORT solicita cancelar un UUID inexistente
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; pedidos y stock no cambian

#### Scenario: Precedencia: propiedad antes que estado
- **WHEN** CUSTOMER solicita cancelar un pedido ajeno SHIPPED
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedido SHIPPED y stock no cambian

### Requirement: Concurrencia de cancelaciones del mismo pedido
Cuando dos cancelaciones autorizadas del mismo pedido compitan, el sistema SHALL confirmar exactamente una y liberar stock una vez; la otra SHALL recibir 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable` sin segunda modificación.

#### Scenario: Dos cancelaciones concurrentes del mismo pedido
- **GIVEN** pedido propio PENDING con (A, 1) y stock 9
- **WHEN** CUSTOMER propietario solicita dos cancelaciones concurrentes con bearer válido
- **THEN** una responde HTTP 200, persiste CANCELLED y stock 10; la otra HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`, sin segunda liberación

### Requirement: Listado de pedidos por estado
GET `/api/orders?status=<estado>` SHALL exigir `order:read-own` o `order:read-any` y filtrar en la consulta por cliente de la cuenta, salvo `order:read-any`. SHALL responder 200 con resúmenes id/customerName/lineCount/totalUnits de los pedidos visibles coincidentes. Los requisitos de estado obligatorio, válido y exacto siguen vigentes tras autorizar.

#### Scenario: Pedidos PAID listados con nombre y métricas
- **WHEN** CUSTOMER consulta PAID con pedidos propios y ajenos PAID
- **THEN** responde HTTP 200 solo con propios, id/customerName/lineCount/totalUnits correctos, sin cambiar pedidos ni stock

#### Scenario: Lectura global de pedidos por estado
- **WHEN** SUPPORT o ADMIN sin cliente asociado consulta PAID
- **THEN** responde HTTP 200 con todos los pedidos PAID y sus métricas, sin cambiar pedidos ni stock

#### Scenario: Cliente sin pedidos visibles
- **WHEN** CUSTOMER consulta PAID y solo existen pedidos ajenos PAID
- **THEN** responde HTTP 200 con [], sin cambiar pedidos ni stock

### Requirement: El filtro limita el estado devuelto
El sistema SHALL incluir únicamente pedidos cuyo estado sea exactamente el valor solicitado en `status`.

#### Scenario: Un pedido PENDING no aparece al solicitar PAID
- **WHEN** se solicita `GET /api/orders?status=PAID` y existen pedidos PAID y PENDING
- **THEN** el sistema responde 200 y la lista contiene los pedidos PAID pero no el pedido PENDING
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Listado vacío para un estado sin pedidos
El sistema SHALL responder 200 con una lista JSON vacía cuando no existan pedidos del estado solicitado.

#### Scenario: Sin pedidos PAID
- **WHEN** se solicita `GET /api/orders?status=PAID` y no existe ningún pedido PAID
- **THEN** el sistema responde 200 con `[]`
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Estado válido y comparación exacta en el filtro
El sistema SHALL aceptar únicamente valores de `status` que coincidan exactamente con los nombres de los estados de pedido y SHALL rechazar los demás con 400 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-status`.

#### Scenario: Status inválido
- **WHEN** se solicita `GET /api/orders?status=FOO`
- **THEN** el sistema responde 400 con ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-status`
- **AND** no se modifica ningún pedido ni ningún stock

#### Scenario: Status en minúsculas
- **WHEN** se solicita `GET /api/orders?status=paid`
- **THEN** el sistema responde 400 con ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-status`
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Status obligatorio en el filtro
El sistema SHALL rechazar la ausencia del parámetro `status` con 400 y ProblemDetail type `https://api.master-acdc.dev/problems/missing-order-status`, en lugar de listar todos los pedidos.

#### Scenario: Sin parámetro status
- **WHEN** se solicita `GET /api/orders` sin parámetro `status`
- **THEN** el sistema responde 400 con ProblemDetail type `https://api.master-acdc.dev/problems/missing-order-status`
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Cliente no encontrado en la consulta
El sistema SHALL conservar el pedido en el listado y representar `customerName` como `null` cuando no exista información de nombre para su `customerId`.

#### Scenario: Pedido cuyo cliente no existe
- **WHEN** se solicita `GET /api/orders?status=PAID` y un pedido PAID referencia un cliente sin nombre registrado
- **THEN** el sistema responde 200, incluye el pedido y establece `customerName` en `null`
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Identidad de cliente derivada de la cuenta
El cliente SHALL obtenerse de la cuenta identificada por sub en cada petición de pedidos, sin cache. La API SHALL no aceptar customerId como selector ni exponerlo en respuestas. Una cuenta sin cliente SHALL no crear pedidos; sin lectura global SHALL obtener lista vacía o 404 al consultar un objeto. Cabeceras antiguas SHALL no alterar identidad.

#### Scenario: Cuenta sin cliente no crea pedido
- **WHEN** una cuenta CUSTOMER sin cliente asociado solicita POST `/api/orders` con stock suficiente
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; no crea pedido ni modifica stock

#### Scenario: Cabecera antigua no suplanta identidad
- **WHEN** CUSTOMER con cliente propio crea un pedido válido enviando X-Customer-Id de otro cliente
- **THEN** responde HTTP 201, pertenece al cliente del bearer y no expone customerId; únicamente reserva sus líneas

#### Scenario: Cambio de asociación se observa con el mismo token
- **WHEN** se cambia la asociación de cliente de una cuenta entre dos GET `/api/orders?status=PAID` usando el mismo bearer válido
- **THEN** ambas respuestas son HTTP 200 y la segunda contiene únicamente pedidos de la asociación vigente; no cambia pedido ni stock

#### Scenario: Cliente ausente no abre el alcance
- **WHEN** CUSTOMER sin cliente asociado consulta GET `/api/orders?status=PAID` y después GET `/api/orders/{id}` existente
- **THEN** recibe HTTP 200 con [] y HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`, respectivamente; pedidos y stock no cambian

### Requirement: Detalle de pedido limitado por propiedad
GET `/api/orders/{id}` SHALL exigir permiso de lectura antes de consultar y devolver HTTP 200 con id/status/lines sin customerId para objetos visibles. La consulta SHALL limitar por cliente salvo `order:read-any`. Id inválido, inexistente o ajeno SHALL producir el mismo 404 con type `https://api.master-acdc.dev/problems/order-not-found`.

#### Scenario: Detalle propio y lectura global
- **WHEN** CUSTOMER consulta su pedido o SUPPORT/ADMIN consulta cualquier pedido existente
- **THEN** responde HTTP 200 con id/status/lines sin customerId, sin modificar pedidos ni stock

#### Scenario: Detalle ajeno o inexistente
- **WHEN** CUSTOMER consulta un pedido ajeno, inexistente o un id no UUID
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedidos y stock no cambian

### Requirement: Reembolso de pedido pagado o enviado
POST `/api/orders/{id}/refund` SHALL exigir `order:refund` y pasar de PAID o SHIPPED a REFUNDED de forma atómica, respondiendo HTTP 200 con id/status/lines sin customerId. SHALL no liberar ni modificar stock. Objeto inexistente SHALL responder 404; cualquier otro estado SHALL responder 409.

#### Scenario: Reembolso desde PAID o SHIPPED
- **WHEN** SUPPORT o ADMIN reembolsa un pedido PAID o SHIPPED
- **THEN** responde HTTP 200, persiste REFUNDED y conserva stock

#### Scenario: Reembolso rechazado por estado
- **WHEN** SUPPORT o ADMIN reembolsa un pedido PENDING, CANCELLED o REFUNDED
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-refundable`; pedido y stock no cambian

#### Scenario: Reembolso de pedido inexistente
- **WHEN** SUPPORT o ADMIN reembolsa un id inexistente o no UUID
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedidos y stock no cambian

#### Scenario: Reembolsos concurrentes no repiten transición
- **WHEN** dos reembolsos autorizados compiten por el mismo pedido PAID
- **THEN** uno responde HTTP 200 y otro HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-refundable`; queda REFUNDED y stock intacto

#### Scenario: Cancelación y reembolso concurrentes
- **WHEN** una cancelación del propietario y un reembolso de SUPPORT compiten por el mismo pedido PAID
- **THEN** exactamente uno responde HTTP 200; el perdedor recibe HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable` o `https://api.master-acdc.dev/problems/order-not-refundable` según operación
- **AND** si gana cancelación queda CANCELLED y libera stock una vez; si gana reembolso queda REFUNDED sin liberar stock; el rechazo no modifica nada

### Requirement: Ordenación restringida del listado de pedidos
GET `/api/orders` SHALL aceptar `sort` solo como un campo de la lista `id`, `customerName`, `lineCount`, `totalUnits`, opcionalmente seguido de ASC o DESC. Sin dirección SHALL usar ASC y sin sort SHALL usar `id ASC`. SHALL añadir id como criterio final estable, sin duplicarlo si ya es el campo solicitado. SHALL mantener permisos, alcance, métricas y clientes sin nombre.

#### Scenario: Ordenación permitida conserva unidades descendentes
- **GIVEN** un bearer SUPPORT vigente y pedidos PAID con distintos totales y empates
- **WHEN** solicita GET `/api/orders?status=PAID&sort=totalUnits%20DESC`
- **THEN** responde HTTP 200 con totales descendentes y empates ordenados por id ASC
- **AND** pedidos y stock no cambian

#### Scenario: Campos permitidos y orden predeterminado
- **GIVEN** un bearer con permiso de lectura y pedidos visibles
- **WHEN** solicita GET `/api/orders?status=PAID` sin sort o con cada campo permitido y direcciones ASC/DESC
- **THEN** responde HTTP 200 con el orden solicitado, usando ASC si se omite dirección e id ASC si se omite sort
- **AND** pedidos y stock no cambian

### Requirement: Rechazo de ordenación arbitraria sin efectos
El sistema SHALL rechazar campos, direcciones y fragmentos de ordenación fuera del contrato con HTTP 400 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-sort`, antes de ejecutar la consulta de listado. SHALL no interpretar entrada de usuario como instrucciones y SHALL conservar pedidos y stock.

#### Scenario: Ordenación inyectada se rechaza y conserva pedidos
- **GIVEN** un bearer SUPPORT vigente y pedidos PAID persistidos
- **WHEN** solicita GET `/api/orders?status=PAID` con `sort=totalUnits; drop table orders--`
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-order-sort`
- **AND** todos los pedidos siguen existiendo y conservan datos, el stock no cambia y un listado válido posterior responde HTTP 200

#### Scenario: Campo dirección o fragmento no permitido
- **GIVEN** un bearer con permiso de lectura
- **WHEN** solicita GET `/api/orders?status=PAID` con sort vacío, campo desconocido, dirección distinta de ASC/DESC o fragmentos adicionales
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-order-sort`
- **AND** pedidos y stock no cambian

### Requirement: PAYING es visible en consultas
El sistema SHALL reconocer PAYING en el detalle y como valor exacto del filtro de estado, preservando las reglas de alcance y ordenación existentes.

#### Scenario: Listado de pedidos en pago
- **WHEN** CUSTOMER solicita GET `/api/orders?status=PAYING` con pedidos propios y ajenos en ese estado
- **THEN** responde HTTP 200 con solo los propios; pedidos y stock no cambian



**Tool: shell**

**Input:**
```json
{
  "command": "openspec show order-payment --type spec"
}
```

# order-payment Specification

## Purpose
Permitir el pago idempotente de pedidos propios y propagar de forma recuperable el stock confirmado al catálogo.

## Requirements

### Requirement: Confirmación y autorización del pago
El sistema SHALL permitir POST `/api/orders/{id}/confirmation` para un pedido propio PENDING con `order:confirm-own`, reservando el estado PAYING antes de solicitar autorización. Una autorización aceptada SHALL finalizar en PAID y registrar un evento de confirmación en la misma transacción.

#### Scenario: Pago autorizado confirma pedido y proyecta stock
- **WHEN** el propietario confirma un pedido PENDING y la pasarela autoriza
- **THEN** responde HTTP 200 con id, status PAID y líneas, sin customerId; existe exactamente un evento de confirmación y el stock reservado no cambia
- **AND** el catálogo refleja el stock actual en un máximo de 5 segundos en la prueba de integración

#### Scenario: Pedido no pendiente no inicia pago
- **WHEN** el propietario confirma un pedido PAYING, PAID o CANCELLED
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-confirmable`; pedido y stock no cambian y no se solicita autorización

#### Scenario: Pedido ajeno o inexistente no inicia pago
- **WHEN** un cliente confirma un pedido ajeno o inexistente
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedido y stock no cambian y no se solicita autorización

#### Scenario: Fallo al registrar confirmación revierte ambos cambios
- **WHEN** la pasarela autoriza pero falla el registro del evento de confirmación, mediante un spy del puerto de outbox compartido en `IntegrationTest`
- **THEN** no se confirma la transición a PAID ni se registra evento; el stock no cambia y el pedido permanece PAYING

### Requirement: Importe fijado
El sistema SHALL calcular el importe con precios actuales sin cache al primer intento, fijarlo junto con PAYING y reutilizarlo en todos los intentos.

#### Scenario: Precio cambia antes del primer intento
- **WHEN** cambia el precio después de crear el pedido y su propietario lo confirma por primera vez
- **THEN** responde HTTP 200 si se autoriza y el importe corresponde al precio actual; el stock reservado no cambia

### Requirement: Idempotencia por intento
El sistema SHALL usar `order-<uuid>-<intento>` como clave, con un contador persistido en `orders` inicializado a 0. SHALL incrementar el contador solo al registrar un `DECLINED` definitivo; tras 5xx o timeout SHALL conservar contador y clave. La pasarela SHALL garantizar como máximo un cobro por clave y devolver el resultado de una autorización previa.

#### Scenario: Reintento tras error conserva importe y clave
- **WHEN** una autorización devuelve 500, cambia el precio y el propietario reintenta
- **THEN** la primera respuesta es HTTP 502 con type `https://api.master-acdc.dev/problems/payment-unavailable`, conserva PENDING y stock; ambas solicitudes de pago usan la misma clave y el mismo importe y existe como máximo un cobro

#### Scenario: Nuevo intento tras rechazo usa otra clave
- **GIVEN** la pasarela devuelve `DECLINED` para el primer intento y el precio cambia después
- **WHEN** el propietario vuelve a confirmar y la pasarela autoriza
- **THEN** la nueva solicitud usa una clave distinta y el importe fijado en el primer intento; el pedido termina PAID

#### Scenario: Falta precio de un producto
- **WHEN** no existe precio actual para una línea al primer intento de confirmación
- **THEN** responde HTTP 422 con type `https://api.master-acdc.dev/problems/payment-price-unavailable`; el pedido sigue PENDING, el stock no cambia y no se solicita autorización

### Requirement: Fallos de autorización controlados
El sistema SHALL devolver el pedido PAYING a PENDING tras rechazo, error 5xx o timeout de la pasarela, sin reintento automático y sin modificar el stock ni emitir confirmación.

#### Scenario: Pago rechazado conserva reserva
- **WHEN** la pasarela rechaza el pago
- **THEN** responde HTTP 402 con type `https://api.master-acdc.dev/problems/payment-declined`; el pedido sigue PENDING y el stock no cambia

#### Scenario: Error de pasarela conserva reserva
- **WHEN** la pasarela responde 500
- **THEN** responde HTTP 502 con type `https://api.master-acdc.dev/problems/payment-unavailable`; el pedido sigue PENDING y el stock no cambia

#### Scenario: Timeout de pasarela conserva reserva
- **WHEN** la pasarela tarda claramente más que el timeout corto configurado para la prueba
- **THEN** responde HTTP 504 con type `https://api.master-acdc.dev/problems/payment-timeout` antes del timeout más un margen; el pedido sigue PENDING y el stock no cambia

### Requirement: Entrega recuperable de confirmación
El sistema SHALL procesar las confirmaciones pendientes al menos una vez, aplicar al catálogo el stock actual de la fuente de inventario e invalidar la ficha afectada después de la proyección. El fallo SHALL dejar el evento disponible para otro intento.

El relay y el job de reconciliación pueden escribir fuera de orden: el catálogo puede mostrar temporalmente stock anterior hasta que un evento posterior o la reconciliación vuelvan a proyectar el stock actual.

#### Scenario: Confirmación repetida es idempotente
- **WHEN** la misma confirmación se procesa dos veces sin cambios adicionales de inventario
- **THEN** el stock proyectado es idéntico al stock actual y no hay descuento adicional

#### Scenario: Fallo de proyección se recupera
- **WHEN** falla la proyección y posteriormente vuelve a estar disponible
- **THEN** la confirmación sigue pendiente hasta procesarse correctamente y termina reflejando el stock actual



**Tool: shell**

**Input:**
```json
{
  "command": "openspec show inventory --type spec"
}
```

# inventory Specification

## Purpose
Gestiona el stock por producto y lo reserva de forma atómica (todo o nada, nunca negativo) para otros módulos mediante su fachada pública.

## Requirements

### Requirement: Reserva atómica de stock
El sistema SHALL permitir reservar, en una sola operación atómica, el stock de un conjunto de productos con sus cantidades. Si todos los productos tienen stock suficiente, el stock reservado SHALL descontarse de cada producto; si alguno no lo tiene, el sistema SHALL rechazar la reserva completa sin modificar ningún stock (todo o nada). El stock resultante de cualquier producto SHALL ser siempre ≥ 0.

#### Scenario: Reserva con stock suficiente en todos los productos
- **GIVEN** el producto A con stock 10 y el producto B con stock 5
- **WHEN** se reserva la línea (A, 3) y la línea (B, 2)
- **THEN** la reserva se aplica y el stock queda A = 7 y B = 3

#### Scenario: Reserva con stock insuficiente en algún producto
- **GIVEN** el producto A con stock 10 y el producto B con stock 1
- **WHEN** se reserva la línea (A, 3) y la línea (B, 2), que no tiene stock suficiente
- **THEN** se informa insuficiencia de stock y el stock de A y B queda sin cambios (10 y 1)

#### Scenario: Reserva en la frontera: cantidad igual al stock disponible
- **GIVEN** el producto A con stock 10
- **WHEN** se reserva la línea (A, 10)
- **THEN** la reserva se aplica y el stock de A queda en 0, nunca negativo

#### Scenario: Reserva en la frontera: cantidad una unidad por encima del stock
- **GIVEN** el producto A con stock 10
- **WHEN** se reserva la línea (A, 11)
- **THEN** se informa insuficiencia de stock y el stock de A sigue en 10

### Requirement: La reserva informa producto desconocido
Cuando una línea de la reserva referencie un producto sin registro de stock, el sistema SHALL informar un resultado propio de producto desconocido, distinto de la insuficiencia de stock, y la reserva completa SHALL rechazarse sin modificar ningún stock. El producto desconocido SHALL ganar siempre a la insuficiencia de stock, sin depender del orden de los productos de la reserva.

#### Scenario: Reserva con producto sin registro de stock
- **GIVEN** el producto A con stock 10 y el producto X sin registro de stock
- **WHEN** se reserva la línea (A, 3) y la línea (X, 1)
- **THEN** se informa producto desconocido y el stock de A sigue en 10

#### Scenario: Reserva con producto desconocido y otro sin stock
- **GIVEN** el producto B con stock 1 y el producto X sin registro de stock
- **WHEN** se reserva la línea (B, 2), que no tiene stock suficiente, y la línea (X, 1)
- **THEN** se informa producto desconocido y el stock de B sigue en 1

### Requirement: Liberación de stock devuelto
El sistema SHALL permitir liberar, en una sola operación atómica, el stock de un conjunto de productos con sus cantidades, devolviendo a cada producto exactamente esas cantidades a su stock disponible. La operación SHALL aceptar cantidades positivas y el stock resultante de cualquier producto SHALL ser la suma del stock previo y la cantidad liberada.

#### Scenario: Liberación de stock de varias líneas
- **GIVEN** el producto A con stock 7 y el producto B con stock 3
- **WHEN** se liberan la línea (A, 3) y la línea (B, 2)
- **THEN** la liberación se aplica y el stock queda A = 10 y B = 5

#### Scenario: Stock liberado vuelve a estar disponible para reservar
- **GIVEN** el producto A con stock 7 y liberada la línea (A, 3)
- **WHEN** se reserva la línea (A, 10)
- **THEN** la reserva se aplica y el stock de A queda en 0, nunca negativo

### Requirement: Reserva directa de unidades de un producto
El sistema SHALL aceptar `POST /api/products/{id}/reservations` con un cuerpo que contenga `units` y SHALL reservar esas unidades únicamente cuando el stock disponible sea suficiente.

#### Scenario: Reserva directa exitosa
- **WHEN** se envía una petición válida para un producto con stock suficiente
- **THEN** el sistema devuelve HTTP 201 con `productId`, `units` reservadas y el stock disponible restante, sin crear ni modificar ningún pedido

#### Scenario: Unidades ausentes
- **WHEN** se envía una petición sin `units`
- **THEN** el sistema devuelve HTTP 400 con el contrato de validación existente, y ningún pedido ni stock queda modificado

#### Scenario: Unidades no positivas
- **WHEN** se envía una petición con `units` menor que 1
- **THEN** el sistema devuelve HTTP 400 con el contrato de validación existente, y ningún pedido ni stock queda modificado

### Requirement: Insuficiencia de stock en una reserva directa
El sistema SHALL rechazar una reserva directa cuando las unidades solicitadas superen el stock disponible y SHALL conservar el stock sin cambios.

#### Scenario: Stock insuficiente
- **WHEN** se solicita una cantidad mayor que el stock disponible del producto
- **THEN** el sistema devuelve HTTP 409 con ProblemDetail type `https://api.master-acdc.dev/problems/insufficient-stock` y propiedad `available` con el stock disponible, sin crear ni modificar ningún pedido ni stock

### Requirement: Producto sin registro de stock
El sistema SHALL distinguir un producto sin registro de stock de una reserva con stock insuficiente.

#### Scenario: Stock inexistente
- **WHEN** se solicita una reserva para un producto que no tiene registro de stock
- **THEN** el sistema devuelve HTTP 404 con ProblemDetail type `https://api.master-acdc.dev/problems/stock-not-found`, sin crear ni modificar ningún pedido ni stock

### Requirement: Tiempo máximo de espera por concurrencia
El sistema SHALL limitar a dos segundos la espera para adquirir el bloqueo necesario para reservar stock y SHALL rechazar la operación si no puede obtenerlo dentro de ese límite.

#### Scenario: Stock ocupado
- **WHEN** otra transacción mantiene bloqueado el stock durante la reserva y el bloqueo no se libera dentro de dos segundos
- **THEN** el sistema devuelve HTTP 503 con cabecera `Retry-After: 1` y ProblemDetail type `https://api.master-acdc.dev/problems/stock-busy`, sin crear ni modificar ningún pedido ni stock

#### Scenario: Reservas concurrentes en venta flash
- **WHEN** 50 reservas simultáneas de una unidad compiten por un stock inicial de 30 unidades
- **THEN** exactamente 30 peticiones devuelven HTTP 201, 20 devuelven HTTP 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`, el stock final es 0 y nunca es negativo

### Requirement: Reserva HTTP autenticada
El sistema SHALL exigir JWT válido y permiso `stock:reserve` en POST `/api/products/{id}/reservations` antes de validar unidades o reservar stock. Los escenarios HTTP existentes SHALL asumir ambas precondiciones, conservando resultados, atomicidad y espera máxima. Estos controles SHALL no afectar a las operaciones internas de reserva y liberación entre módulos.

#### Scenario: Reserva anónima no llega al inventario
- **WHEN** se solicita POST `/api/products/{id}/reservations` sin JWT válido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin crear pedidos ni modificar stock

#### Scenario: Reserva con JWT conserva contrato de éxito
- **WHEN** se solicita POST `/api/products/{id}/reservations` con JWT válido y permiso `stock:reserve`, units 1 y stock inicial 2
- **THEN** responde HTTP 201 con productId, units 1 y disponible 1; no crea ni modifica pedidos



---

## Assistant

_Thinking:_



El ADR resuelve el flujo principal, pero la especificación actual trata el reembolso de un pedido pagado de forma distinta y no hay anulación real en la pasarela. Señalaré esa incompatibilidad; H10 y H11 seguirán bloqueando las decisiones que dependen de ellas.

**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Add File: openspec/changes/handle-stock-shortage-after-payment/proposal.md\n+# Proposal\n+\n+## Why\n+\n+Una reserva en el sistema no garantiza el apartado físico: la rotura puede descubrirse después de cobrar. El ADR 0015 establece cómo proteger al cliente y tramitar las líneas disponibles sin confundir Cobro anulado con Reembolso.\n+\n+## What Changes\n+\n+- Registrar Rotura de stock detectada durante la preparación de un pedido cobrado, manteniendo la reserva al crear el pedido.\n+- Ante una rotura total, ofrecer esperar la reposición o anular; sin respuesta en 48 horas, anular el cobro y avisar.\n+- Ante una rotura parcial, enviar las líneas disponibles y anular el cobro de las líneas que faltan sin preguntar.\n+- Cada anulación libera la reserva de las líneas anuladas; el Cobro anulado requiere una operación real de la pasarela y es distinto del Reembolso tras una devolución.\n+\n+## Capabilities\n+\n+### New Capabilities\n+\n+- `stock-shortage-after-payment`: tratamiento de las cuatro ramas de rotura tras el cobro.\n+\n+### Modified Capabilities\n+\n+Ninguna en esta propuesta: las incompatibilidades existentes requieren decisión y una posterior revisión de sus especificaciones.\n+\n+## Impact\n+\n+Pedidos, Inventario, integración con la pasarela, comunicación al cliente y preparación de envíos. Hoy `Order.refund` mezcla anulación y reembolso, y la pasarela solo autoriza; no existe el flujo de rotura ni una anulación real. La especificación `ordering` permite reembolsar un pedido PAID sin liberar stock, por lo que debe reconciliarse explícitamente con el ADR antes de implementar; no se modifica silenciosamente aquí. Nuevos puertos entre contextos, tablas, fronteras o endpoints requieren las decisiones y revisiones humanas establecidas en el proyecto.\n+\n+## Non-goals\n+\n+- Cambiar el momento de reserva, implementar caducidad de reservas, devoluciones o facturación rectificativa.\n+- Decidir el ajuste del saldo por rotura o un plazo máximo de espera por reposición.\n+\n+## Preguntas abiertas y bloqueos\n+\n+- **H10 — Lucía y Marta:** si el cliente elige esperar la reposición, ¿cuál es el plazo máximo de espera?\n+- **H11 — Marta:** ¿la rotura ajusta automáticamente el saldo del sistema o Marta lo ajusta tras recontar?\n+\n+Ambas siguen abiertas. No se supone ninguna respuesta; las partes de la implementación que dependan de ellas deben esperar a negocio. También deben acordarse la interacción con los contratos existentes y cualquier cambio de frontera antes de implementar.\n*** End Patch\n"
}
```

Success. Updated the following files:
A openspec/changes/handle-stock-shortage-after-payment/proposal.md


[AC/DC Verify] El change 'handle-stock-shortage-after-payment' no es válido. Corrige:
✗ [ERROR] file: Change must have at least one delta. No deltas found. Ensure your change has a specs/ directory with capability folders (e.g. specs/http-server/spec.md) containing .md files that use delta headers (## ADDED/MODIFIED/REMOVED/RENAMED Requirements) and that each requirement includes at least one "#### Scenario:" block. If this change intentionally modifies no specs (pure refactor, tooling, docs), set "skip_specs: true" in the change's .openspec.yaml instead. Tip: run "openspec change show <change-id> --json --deltas-only" to inspect parsed deltas.

---

## Assistant

**Tool: shell**

**Input:**
```json
{
  "command": "openspec status --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "schemaName": "spec-driven",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "changeRoot": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "artifactPaths": {
    "proposal": {
      "outputPath": "proposal.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md"
      ]
    },
    "specs": {
      "outputPath": "specs/**/*.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\**\\*.md",
      "existingOutputPaths": []
    },
    "design": {
      "outputPath": "design.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\design.md",
      "existingOutputPaths": []
    },
    "tasks": {
      "outputPath": "tasks.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\tasks.md",
      "existingOutputPaths": []
    }
  },
  "isPlanningComplete": false,
  "isComplete": false,
  "applyRequires": [
    "tasks"
  ],
  "nextSteps": [
    "Run openspec instructions specs --change \"handle-stock-shortage-after-payment\" --json before writing that artifact."
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
      "status": "ready",
      "requires": [
        "proposal"
      ]
    },
    {
      "id": "design",
      "outputPath": "design.md",
      "status": "ready",
      "requires": [
        "proposal"
      ]
    },
    {
      "id": "tasks",
      "outputPath": "tasks.md",
      "status": "blocked",
      "requires": [
        "specs",
        "design"
      ],
      "missingDeps": [
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


**Tool: shell**

**Input:**
```json
{
  "command": "openspec instructions specs --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "artifactId": "specs",
  "schemaName": "spec-driven",
  "changeDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "outputPath": "specs/**/*.md",
  "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\**\\*.md",
  "existingOutputPaths": [],
  "description": "Detailed specifications for the change",
  "instruction": "Create specification files that define WHAT the system should do.\n\nA spec is a behavior contract, not an implementation plan.\n\nGood spec content:\n- Observable behavior users or downstream systems rely on\n- Inputs, outputs, and error conditions\n- External constraints (security, privacy, reliability, compatibility)\n- Scenarios that can be tested or explicitly validated\n\nAvoid in specs:\n- Internal class/function names\n- Library or framework choices\n- Step-by-step implementation details\n- Detailed execution plans (those belong in design.md or tasks.md)\n\nQuick test: if the implementation can change without changing externally\nvisible behavior, it likely does not belong in the spec.\n\nCreate one spec file per capability listed in the proposal's Capabilities section.\n`<capability-path>` is the spec directory relative to `specs/` (for example,\n`user-auth` or `identity/user-auth`). Preserve the full path:\n- New capabilities: use the exact path from the proposal at `specs/<capability-path>/spec.md`. Any path segment newly introduced in the proposal must be kebab-case. Follow the project's existing organization; do not add a new domain level when the project uses a flat layout.\n- Modified capabilities: use the exact existing path from `openspec/specs/<capability-path>/` when creating the delta at `specs/<capability-path>/spec.md`. Run `openspec list --specs` to confirm that path before writing the delta, appending `--store \"<id>\"` only for a registered standalone store - a mistyped or invented path targets a capability that does not exist rather than the one you meant. Do not move or rename the capability.\n\nWrite the spec files one at a time, so the user sees progress and an\ninterrupted run keeps the files it already wrote:\n- Before writing anything, compare the capabilities the proposal lists\n  with the `specs/<capability-path>/spec.md` files that already exist,\n  and start with the first capability that has no file. Do not rewrite\n  an existing file unless the user asks you to.\n- Before each file, say in one line which capability you are writing.\n- Write each file as soon as you have drafted it. Do not plan every file\n  before writing the first.\n- Stop when every capability the proposal lists has its file.\n\nThere must be at least one spec file unless the change's `.openspec.yaml`\nsets `skip_specs: true` (no spec-level behavior change) - `openspec validate`\nrejects a zero-delta change without that marker. If the proposal lists no\ncapabilities and `skip_specs` is not set, revisit the proposal first.\n\nDelta operations (use ## headers):\n- **ADDED Requirements**: New capabilities\n- **MODIFIED Requirements**: Changed behavior - MUST include full updated content\n- **REMOVED Requirements**: Deprecated features - MUST include **Reason** and **Migration**\n- **RENAMED Requirements**: Name changes only - use FROM:/TO: format\n\nFormat requirements:\n- Each requirement: `### Requirement: <name>` followed by description\n- Use SHALL/MUST for normative requirements (avoid should/may)\n- Each scenario: `#### Scenario: <name>` with WHEN/THEN format\n- **CRITICAL**: Scenarios MUST use exactly 4 hashtags (`####`). Using 3 hashtags or bullets will fail silently.\n- Every requirement MUST have at least one scenario.\n- Keep each requirement's description (the text between `### Requirement:` and its first scenario) to 500 characters or fewer. `openspec validate` flags longer descriptions in ADDED requirements and in the main spec. This is a warning: normal validation still passes, but `openspec validate --strict` fails on it. When writing a new requirement, state one behavior per requirement: move examples and edge cases into scenarios, and split a requirement that covers several behaviors into separate `### Requirement:` blocks, each with its own scenarios. Under MODIFIED, keep the existing requirement block whole; never split, trim or rewrite existing text just to meet the length. Split an existing long requirement only when the user asks for it, in a change made for that purpose: under MODIFIED, keep its header and every scenario and cut its description down to one behavior without changing its meaning, then add each behavior you removed as its own ADDED requirement with its own scenarios.\n\nNew capabilities only: the delta spec's first section is `## Purpose` -\none or two sentences (50+ characters, or `openspec validate --strict`\nreports it as too brief) describing what the capability is for. Archive\ncopies it into the main spec it creates; without it the new main spec is\nleft with a `TBD ... Update Purpose after archive` placeholder to fill in\nby hand. Do NOT add `## Purpose` to a delta for an existing capability -\nthat spec already has one and the delta's is ignored. To change an\nexisting capability's Purpose - including a leftover `TBD` placeholder -\nedit `<planningHome.root>/openspec/specs/<capability-path>/spec.md`\ndirectly. `planningHome.root` comes from the `openspec instructions ...\n--json` response. Always use it rather than a repo-relative path: it\nresolves to the store whenever the change lives in one - whether that\ncame from `--store`, a project `store:` pointer, or a global default\nstore - and to the current repository otherwise. Do not try to work out\nwhich case applies; the field already has.\n\nMODIFIED requirements workflow:\n1. Locate the existing requirement in `<planningHome.root>/openspec/specs/<capability-path>/spec.md` (the same store-aware root as above)\n2. Copy the ENTIRE requirement block (from `### Requirement:` through all scenarios)\n3. Paste under `## MODIFIED Requirements` and edit to reflect new behavior\n4. Ensure header text matches exactly (whitespace-insensitive)\n\nCommon pitfall: Using MODIFIED with partial content loses detail at archive time.\nIf adding new concerns without changing existing behavior, use ADDED instead.\n\nExample (a new capability, so its first section is `## Purpose`):\n```\n# Spec Delta\n\n## Purpose\n\nLets users take their data out of the product in a portable format.\n\n## ADDED Requirements\n\n### Requirement: User can export data\nThe system SHALL allow users to export their data in CSV format.\n\n#### Scenario: Successful export\n- **WHEN** user clicks \"Export\" button\n- **THEN** system downloads a CSV file with all user data\n\n## REMOVED Requirements\n\n### Requirement: Legacy export\n**Reason**: Replaced by new export system\n**Migration**: Use new export endpoint at /api/v2/export\n```\n\nSpecs should be testable - each scenario is a potential test case.\n",
  "context": "Proyecto: master-acdc, tienda en Java (paquete com.codeja.master_acdc) con arquitectura hexagonal\n(domain, application, infrastructure). Módulos: catalog (MongoDB + cache L1/L2), inventory (stock),\nordering (pedidos) e identity (cuentas, login, refresh tokens, JWT RS256, RBAC por permisos).\nStack: Java 25, Spring Boot 4, JPA/Hibernate, PostgreSQL, Flyway, Testcontainers.\nErrores HTTP con ProblemDetail (RFC 9457); cada error de negocio tiene un type propio.\nordering consulta y reserva stock mediante la API pública de inventory; inventory nunca depende de ordering.\nLas liberaciones de stock y demás efectos posteriores entre módulos viajan por eventos de dominio.\nAPI stateless con bearer JWT (lab S12): el cliente de un pedido sale del sub del token vía la cuenta;\nno existe X-Customer-Id. Autorización por permisos derivados de roles y por propiedad del objeto (404).\nLanguage: es. Keep OpenSpec structural headings and SHALL/MUST keywords in English.",
  "rules": [
    "Cada escenario de API indica el código HTTP y, si es un error, el type del ProblemDetail.",
    "Cada escenario de rechazo indica qué queda sin cambios (pedido y stock).",
    "Nunca nombres clases, librerías ni tablas; eso va en design.md."
  ],
  "template": "# Spec Delta\n\n## Purpose\n<!-- New capabilities only: one or two sentences (50+ characters) on what this capability is for. Delete this section for an existing capability. -->\n\n## ADDED Requirements\n\n### Requirement: <!-- requirement name -->\n<!-- requirement text -->\n\n#### Scenario: <!-- scenario name -->\n- **WHEN** <!-- condition -->\n- **THEN** <!-- expected outcome -->\n",
  "dependencies": [
    {
      "id": "proposal",
      "done": true,
      "path": "proposal.md",
      "description": "Initial proposal document outlining the change"
    }
  ],
  "unlocks": [
    "tasks"
  ],
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}


**Tool: shell**

**Input:**
```json
{
  "command": "openspec instructions design --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "artifactId": "design",
  "schemaName": "spec-driven",
  "changeDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "outputPath": "design.md",
  "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\design.md",
  "existingOutputPaths": [],
  "description": "Technical design document with implementation details",
  "instruction": "Create the design document that explains HOW to implement the change.\n\nWhen to include design.md (create only if any apply):\n- Cross-cutting change (multiple services/modules) or new architectural pattern\n- New external dependency or significant data model changes\n- Security, performance, or migration complexity\n- Ambiguity that benefits from technical decisions before coding\n\nSections:\n- **Context**: Only the current state and constraints needed to explain the approach. Reference the proposal for motivation instead of restating it (e.g., \"See proposal.md - Why\").\n- **Goals / Non-Goals**: What this design achieves and explicitly excludes. Don't restate the proposal's scope - add only design-level boundaries.\n- **Decisions**: Key technical choices with rationale (why X over Y?). Include alternatives considered for each decision.\n- **Risks / Trade-offs**: Known limitations, things that could go wrong. Format: [Risk] → Mitigation\n- **Migration Plan**: Steps to deploy, rollback strategy (if applicable)\n- **Open Questions**: Unknowns that can safely be answered later without\n  changing the specs, the approach, or the task breakdown. Omit if none.\n\nOpen questions are for genuinely deferrable unknowns, not decisions you\nskipped. If a question would change the specs, the chosen approach, or\nthe task breakdown, resolve it now - ask the user instead of guessing.\n\nFocus on architecture and approach, not line-by-line implementation.\nThe proposal covers why and what; design covers how. Reference the\nproposal for motivation and, once written, the specs for requirements -\nif a section would only restate them, point to them instead.\n\nGood design docs explain the \"why\" behind technical decisions.\n",
  "context": "Proyecto: master-acdc, tienda en Java (paquete com.codeja.master_acdc) con arquitectura hexagonal\n(domain, application, infrastructure). Módulos: catalog (MongoDB + cache L1/L2), inventory (stock),\nordering (pedidos) e identity (cuentas, login, refresh tokens, JWT RS256, RBAC por permisos).\nStack: Java 25, Spring Boot 4, JPA/Hibernate, PostgreSQL, Flyway, Testcontainers.\nErrores HTTP con ProblemDetail (RFC 9457); cada error de negocio tiene un type propio.\nordering consulta y reserva stock mediante la API pública de inventory; inventory nunca depende de ordering.\nLas liberaciones de stock y demás efectos posteriores entre módulos viajan por eventos de dominio.\nAPI stateless con bearer JWT (lab S12): el cliente de un pedido sale del sub del token vía la cuenta;\nno existe X-Customer-Id. Autorización por permisos derivados de roles y por propiedad del objeto (404).\nLanguage: es. Keep OpenSpec structural headings and SHALL/MUST keywords in English.",
  "rules": [
    "Lista en \"Reglas de arquitectura\" las dependencias entre módulos que comprobará ArchUnit."
  ],
  "template": "# Design\n\n## Context\n\n<!-- Current state and constraints that shape the approach. See proposal.md for motivation - don't restate it -->\n\n## Goals / Non-Goals\n\n**Goals:**\n<!-- What this design aims to achieve -->\n\n**Non-Goals:**\n<!-- What is explicitly out of scope -->\n\n## Decisions\n\n<!-- Key design decisions with rationale and alternatives considered -->\n\n## Risks / Trade-offs\n\n<!-- Known risks and trade-offs -->\n",
  "dependencies": [
    {
      "id": "proposal",
      "done": true,
      "path": "proposal.md",
      "description": "Initial proposal document outlining the change"
    }
  ],
  "unlocks": [
    "tasks"
  ],
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}


**Tool: read**

**Input:**
```json
{
  "path": "openspec/changes/handle-stock-shortage-after-payment/proposal.md"
}
```

Read file openspec/changes/handle-stock-shortage-after-payment/proposal.md, lines 1-38
1: # Proposal
2: 
3: ## Why
4: 
5: Una reserva en el sistema no garantiza el apartado físico: la rotura puede descubrirse después de cobrar. El ADR 0015 establece cómo proteger al cliente y tramitar las líneas disponibles sin confundir Cobro anulado con Reembolso.
6: 
7: ## What Changes
8: 
9: - Registrar Rotura de stock detectada durante la preparación de un pedido cobrado, manteniendo la reserva al crear el pedido.
10: - Ante una rotura total, ofrecer esperar la reposición o anular; sin respuesta en 48 horas, anular el cobro y avisar.
11: - Ante una rotura parcial, enviar las líneas disponibles y anular el cobro de las líneas que faltan sin preguntar.
12: - Cada anulación libera la reserva de las líneas anuladas; el Cobro anulado requiere una operación real de la pasarela y es distinto del Reembolso tras una devolución.
13: 
14: ## Capabilities
15: 
16: ### New Capabilities
17: 
18: - `stock-shortage-after-payment`: tratamiento de las cuatro ramas de rotura tras el cobro.
19: 
20: ### Modified Capabilities
21: 
22: Ninguna en esta propuesta: las incompatibilidades existentes requieren decisión y una posterior revisión de sus especificaciones.
23: 
24: ## Impact
25: 
26: Pedidos, Inventario, integración con la pasarela, comunicación al cliente y preparación de envíos. Hoy `Order.refund` mezcla anulación y reembolso, y la pasarela solo autoriza; no existe el flujo de rotura ni una anulación real. La especificación `ordering` permite reembolsar un pedido PAID sin liberar stock, por lo que debe reconciliarse explícitamente con el ADR antes de implementar; no se modifica silenciosamente aquí. Nuevos puertos entre contextos, tablas, fronteras o endpoints requieren las decisiones y revisiones humanas establecidas en el proyecto.
27: 
28: ## Non-goals
29: 
30: - Cambiar el momento de reserva, implementar caducidad de reservas, devoluciones o facturación rectificativa.
31: - Decidir el ajuste del saldo por rotura o un plazo máximo de espera por reposición.
32: 
33: ## Preguntas abiertas y bloqueos
34: 
35: - **H10 — Lucía y Marta:** si el cliente elige esperar la reposición, ¿cuál es el plazo máximo de espera?
36: - **H11 — Marta:** ¿la rotura ajusta automáticamente el saldo del sistema o Marta lo ajusta tras recontar?
37: 
38: Ambas siguen abiertas. No se supone ninguna respuesta; las partes de la implementación que dependan de ellas deben esperar a negocio. También deben acordarse la interacción con los contratos existentes y cualquier cambio de frontera antes de implementar.

---

## Assistant

_Thinking:_



Creada la propuesta. Redactaré ahora la capacidad `stock-shortage-after-payment` con los cuatro escenarios solicitados, sin fijar el inicio del plazo de espera por reposición ni el ajuste del saldo.

**Tool: shell**

**Input:**
```json
{
  "command": "python -c \"from pathlib import Path; p=Path('openspec/changes/handle-stock-shortage-after-payment').resolve(); q=(p/'specs/stock-shortage-after-payment/spec.md').resolve(); print(q); print('inside:', q.is_relative_to(p))\""
}
```

C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\changes\handle-stock-shortage-after-payment\specs\stock-shortage-after-payment\spec.md
inside: True


---

## Assistant

**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Add File: openspec/changes/handle-stock-shortage-after-payment/specs/stock-shortage-after-payment/spec.md\n+# Spec Delta\n+\n+## Purpose\n+\n+Gestionar la rotura de stock detectada al preparar un pedido cobrado, respetando la elección del cliente y el tratamiento de las líneas disponibles.\n+\n+## ADDED Requirements\n+\n+### Requirement: Elección del cliente ante rotura total\n+Al detectar durante la preparación que faltan todas las unidades de un pedido cobrado, el sistema SHALL registrar Rotura de stock detectada y ofrecer al cliente esperar la reposición o anular.\n+\n+#### Scenario: Rotura total y el cliente elige esperar la reposición\n+- **GIVEN** un pedido cobrado con todas sus líneas reservadas y ninguna unidad disponible físicamente\n+- **WHEN** se registra Rotura de stock detectada y el cliente elige esperar la reposición\n+- **THEN** el pedido queda a la espera de reposición, sin Cobro anulado ni Stock liberado de sus líneas\n+\n+#### Scenario: Rotura total y el cliente elige anular\n+- **GIVEN** un pedido cobrado con Rotura de stock detectada en todas sus líneas y la elección ofrecida al cliente\n+- **WHEN** el cliente elige anular\n+- **THEN** se produce Cobro anulado por el pedido y Stock liberado de todas las líneas anuladas\n+\n+### Requirement: Ausencia de respuesta ante rotura total\n+Ante una rotura total, el sistema SHALL anular automáticamente el cobro y avisar al cliente si no responde en 48 horas; SHALL liberar la reserva de las líneas anuladas.\n+\n+#### Scenario: Rotura total sin respuesta en 48 horas\n+- **GIVEN** un pedido cobrado con Rotura de stock detectada en todas sus líneas y la elección ofrecida al cliente\n+- **WHEN** transcurren 48 horas sin respuesta\n+- **THEN** se produce Cobro anulado por el pedido, Stock liberado de todas las líneas anuladas y se avisa al cliente\n+\n+### Requirement: Tratamiento de rotura parcial\n+Al detectar durante la preparación que faltan solo algunas líneas de un pedido cobrado, el sistema SHALL registrar Rotura de stock detectada, enviar las líneas disponibles y producir Cobro anulado y Stock liberado de las líneas que faltan, sin preguntar al cliente.\n+\n+#### Scenario: Rotura parcial con envío de las líneas disponibles\n+- **GIVEN** un pedido cobrado con líneas reservadas A y B, A disponible físicamente y B ausente\n+- **WHEN** se registra Rotura de stock detectada para B\n+- **THEN** se envía A y se produce Cobro anulado y Stock liberado solo de B, sin pedir elección al cliente\n*** End Patch\n"
}
```

Success. Updated the following files:
A openspec/changes/handle-stock-shortage-after-payment/specs/stock-shortage-after-payment/spec.md

---

## Assistant

**Tool: shell**

**Input:**
```json
{
  "command": "openspec status --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "schemaName": "spec-driven",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "changeRoot": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "artifactPaths": {
    "proposal": {
      "outputPath": "proposal.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md"
      ]
    },
    "specs": {
      "outputPath": "specs/**/*.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\**\\*.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\stock-shortage-after-payment\\spec.md"
      ]
    },
    "design": {
      "outputPath": "design.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\design.md",
      "existingOutputPaths": []
    },
    "tasks": {
      "outputPath": "tasks.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\tasks.md",
      "existingOutputPaths": []
    }
  },
  "isPlanningComplete": false,
  "isComplete": false,
  "applyRequires": [
    "tasks"
  ],
  "nextSteps": [
    "Run openspec instructions design --change \"handle-stock-shortage-after-payment\" --json before writing that artifact."
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
      "status": "ready",
      "requires": [
        "proposal"
      ]
    },
    {
      "id": "tasks",
      "outputPath": "tasks.md",
      "status": "blocked",
      "requires": [
        "specs",
        "design"
      ],
      "missingDeps": [
        "design"
      ]
    }
  ],
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}


---

## Assistant

**Tool: read**

**Input:**
```json
{
  "path": "openspec/changes/handle-stock-shortage-after-payment/proposal.md"
}
```

Read file openspec/changes/handle-stock-shortage-after-payment/proposal.md, lines 1-38
1: # Proposal
2: 
3: ## Why
4: 
5: Una reserva en el sistema no garantiza el apartado físico: la rotura puede descubrirse después de cobrar. El ADR 0015 establece cómo proteger al cliente y tramitar las líneas disponibles sin confundir Cobro anulado con Reembolso.
6: 
7: ## What Changes
8: 
9: - Registrar Rotura de stock detectada durante la preparación de un pedido cobrado, manteniendo la reserva al crear el pedido.
10: - Ante una rotura total, ofrecer esperar la reposición o anular; sin respuesta en 48 horas, anular el cobro y avisar.
11: - Ante una rotura parcial, enviar las líneas disponibles y anular el cobro de las líneas que faltan sin preguntar.
12: - Cada anulación libera la reserva de las líneas anuladas; el Cobro anulado requiere una operación real de la pasarela y es distinto del Reembolso tras una devolución.
13: 
14: ## Capabilities
15: 
16: ### New Capabilities
17: 
18: - `stock-shortage-after-payment`: tratamiento de las cuatro ramas de rotura tras el cobro.
19: 
20: ### Modified Capabilities
21: 
22: Ninguna en esta propuesta: las incompatibilidades existentes requieren decisión y una posterior revisión de sus especificaciones.
23: 
24: ## Impact
25: 
26: Pedidos, Inventario, integración con la pasarela, comunicación al cliente y preparación de envíos. Hoy `Order.refund` mezcla anulación y reembolso, y la pasarela solo autoriza; no existe el flujo de rotura ni una anulación real. La especificación `ordering` permite reembolsar un pedido PAID sin liberar stock, por lo que debe reconciliarse explícitamente con el ADR antes de implementar; no se modifica silenciosamente aquí. Nuevos puertos entre contextos, tablas, fronteras o endpoints requieren las decisiones y revisiones humanas establecidas en el proyecto.
27: 
28: ## Non-goals
29: 
30: - Cambiar el momento de reserva, implementar caducidad de reservas, devoluciones o facturación rectificativa.
31: - Decidir el ajuste del saldo por rotura o un plazo máximo de espera por reposición.
32: 
33: ## Preguntas abiertas y bloqueos
34: 
35: - **H10 — Lucía y Marta:** si el cliente elige esperar la reposición, ¿cuál es el plazo máximo de espera?
36: - **H11 — Marta:** ¿la rotura ajusta automáticamente el saldo del sistema o Marta lo ajusta tras recontar?
37: 
38: Ambas siguen abiertas. No se supone ninguna respuesta; las partes de la implementación que dependan de ellas deben esperar a negocio. También deben acordarse la interacción con los contratos existentes y cualquier cambio de frontera antes de implementar.

---

## Assistant

**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Add File: openspec/changes/handle-stock-shortage-after-payment/design.md\n+# Design\n+\n+## Context\n+\n+La reserva ocurre al crear el pedido; `Order.cancel` emite `OrderCancelled` y la liberación alcanza Inventario mediante un listener. `Order.refund` cambia el estado sin llamar a la pasarela, cuyo puerto solo autoriza. La spec `ordering` conserva un contrato de reembolso desde PAID que debe reconciliarse con el ADR 0015 antes de cambiarlo.\n+\n+## Goals / Non-Goals\n+\n+**Goals:** hacer verificable el flujo del ADR sin perder efectos ni confundir anulación y devolución.\n+\n+**Non-Goals:** resolver H10 o H11, definir endpoints o modificar fronteras sin autorización humana.\n+\n+## Decisions\n+\n+- Modelar la rotura, la elección y las transiciones de pedido como comportamiento de dominio; conservar la reserva durante la espera. Alternativa descartada: decidir estados en adaptadores.\n+- Tratar Cobro anulado como operación real diferenciada del Reembolso y vincularla a las líneas afectadas. Una transición local sin confirmación de la pasarela no demuestra una anulación.\n+- Diseñar la ejecución recuperable e idempotente de la anulación, liberación y aviso, incluidos reintentos y competencia entre respuesta y vencimiento; evitar duplicar cobros anulados o stock liberado. El detalle de persistencia y de integración requiere revisión humana previa si introduce tablas o puertos entre contextos.\n+- Utilizar tiempo inyectado para verificar el vencimiento de 48 horas. El instante exacto de inicio y el tratamiento de respuestas en la frontera requieren acordar el contrato antes de implementarlo; no se confunden con H10.\n+\n+## Reglas de arquitectura\n+\n+- Dominio y aplicación no dependen de infraestructura ni frameworks; el agregado decide transiciones y publica eventos mediante su puerto.\n+- `ordering` consulta `inventory` solo mediante su API publicada desde infraestructura; `inventory` no depende de `ordering`.\n+- Los efectos posteriores entre contextos, incluida la liberación, viajan por eventos de dominio; no se accede a persistencia ajena. Todo puerto nuevo o cambio de frontera requiere decisión humana y actualización previa del mapa.\n+\n+## Risks / Trade-offs\n+\n+- [H10 y H11 abiertos] → negocio debe decidir antes de implementar cualquier parte dependiente; no inferir el saldo ni la duración de espera.\n+- [Conflicto con reembolso de PAID] → revisar contratos y delta de `ordering` con una persona antes de implementar; no cambiar silenciosamente el endpoint existente.\n+- [Fallo o respuesta incierta de pasarela] → identificar operación de forma estable y reconciliar antes de repetir; probar que no se da por completada sin confirmación.\n+- [Carrera entre elección y reloj] → una sola resolución efectiva y pruebas de concurrencia.\n+\n+## Migration Plan\n+\n+Tras aprobación de negocio y fronteras, preparar cambios compatibles mediante expandir-contratar; desplegar el flujo solo cuando persistencia, pasarela y consumidores estén preparados. En caso de retirada, detener nuevas roturas y conservar y reconciliar las operaciones pendientes sin deshacer efectos financieros.\n*** End Patch\n"
}
```

Success. Updated the following files:
A openspec/changes/handle-stock-shortage-after-payment/design.md

---

## Assistant

**Tool: shell**

**Input:**
```json
{
  "command": "openspec status --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "schemaName": "spec-driven",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "changeRoot": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "artifactPaths": {
    "proposal": {
      "outputPath": "proposal.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md"
      ]
    },
    "specs": {
      "outputPath": "specs/**/*.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\**\\*.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\stock-shortage-after-payment\\spec.md"
      ]
    },
    "design": {
      "outputPath": "design.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\design.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\design.md"
      ]
    },
    "tasks": {
      "outputPath": "tasks.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\tasks.md",
      "existingOutputPaths": []
    }
  },
  "isPlanningComplete": false,
  "isComplete": false,
  "applyRequires": [
    "tasks"
  ],
  "nextSteps": [
    "Run openspec instructions tasks --change \"handle-stock-shortage-after-payment\" --json before writing that artifact."
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
      "status": "ready",
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


**Tool: shell**

**Input:**
```json
{
  "command": "openspec instructions tasks --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "artifactId": "tasks",
  "schemaName": "spec-driven",
  "changeDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "outputPath": "tasks.md",
  "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\tasks.md",
  "existingOutputPaths": [],
  "description": "Implementation checklist with trackable tasks",
  "instruction": "Create the task list that breaks down the implementation work.\n\nBefore writing tasks, check design.md for Open Questions. If any of them\nwould change what gets built, resolve them with the user first - do not\nbake an unstated assumption into the task list.\n\n**IMPORTANT: Follow the template below for tracked tasks.** The apply phase parses\ncheckbox format to track progress. A box holding only `x` counts as done,\nupper or lower case and with any spacing, so `- [ x]` is done too. Every\nother marker, including `- [~]`, `- [-]` and an empty `- []`, reads as\nunfinished. A line with no checkbox is not tracked at all.\n\nGuidelines:\n- Group related tracked tasks under ## numbered headings\n- Each tracked task MUST be a checkbox: `- [ ] X.Y Task description`\n- Tasks should be small enough to complete in one session\n- Order tasks by dependency (what must be done first?)\n- Track implementation and verification work that can be completed before\n  archive. If the requested workflow includes archive or work that requires\n  this change to be archived, preserve those steps as plain bullets in an\n  optional `## Workflow follow-up` section at the end of tasks.md. These\n  bullets are reference information outside tracked task progress.\n- Each task MUST state how to verify completion (a test, command,\n  observable behavior, or delivered artifact). Put the verification in\n  that task's checkbox description. Use a separate verification task only\n  when it checks broader integration or system behavior that spans\n  multiple implementation tasks.\n- Each task group MUST land the tests and documentation its own work\n  calls for. Do NOT collect testing or documentation into a final group -\n  when a late group first exercises work from an early one, the failures\n  cascade back through every group in between and force rework. A group\n  whose work calls for neither, such as scaffolding or dependency setup,\n  carries neither. A final group is for integration checks only, not for\n  the tests and docs an earlier group owed.\n\nExample:\n```\n# Tasks\n\n## 1. Setup\n\n- [ ] 1.1 Create new module structure and verify expected files are present\n- [ ] 1.2 Add dependencies to package.json and verify package installation succeeds\n\n## 2. Core Implementation\n\n- [ ] 2.1 Implement data export function and verify the export test passes\n- [ ] 2.2 Add CSV formatting utilities and verify unit tests cover quoting and delimiters\n- [ ] 2.3 Document the export API in docs/export.md and verify the documented command runs as written\n```\n\nWhen applicable, append workflow follow-up as plain bullets, for example:\n```\n## Workflow follow-up\n\n- Archive the change after the project's review requirements are satisfied.\n- Verify the archived result.\n```\n\nReference specs for what needs to be built, design for how to build it.\n",
  "context": "Proyecto: master-acdc, tienda en Java (paquete com.codeja.master_acdc) con arquitectura hexagonal\n(domain, application, infrastructure). Módulos: catalog (MongoDB + cache L1/L2), inventory (stock),\nordering (pedidos) e identity (cuentas, login, refresh tokens, JWT RS256, RBAC por permisos).\nStack: Java 25, Spring Boot 4, JPA/Hibernate, PostgreSQL, Flyway, Testcontainers.\nErrores HTTP con ProblemDetail (RFC 9457); cada error de negocio tiene un type propio.\nordering consulta y reserva stock mediante la API pública de inventory; inventory nunca depende de ordering.\nLas liberaciones de stock y demás efectos posteriores entre módulos viajan por eventos de dominio.\nAPI stateless con bearer JWT (lab S12): el cliente de un pedido sale del sub del token vía la cuenta;\nno existe X-Customer-Id. Autorización por permisos derivados de roles y por propiedad del objeto (404).\nLanguage: es. Keep OpenSpec structural headings and SHALL/MUST keywords in English.",
  "rules": [
    "Cada tarea indica el test que la verifica; los escenarios se verifican con @DisplayName(<nombre exacto del escenario>)."
  ],
  "template": "# Tasks\n\n## 1. <!-- Task Group Name -->\n\n- [ ] 1.1 <!-- Task description -->\n- [ ] 1.2 <!-- Task description -->\n\n## 2. <!-- Task Group Name -->\n\n- [ ] 2.1 <!-- Task description -->\n- [ ] 2.2 <!-- Task description -->\n",
  "dependencies": [
    {
      "id": "specs",
      "done": true,
      "path": "specs/**/*.md",
      "description": "Detailed specifications for the change"
    },
    {
      "id": "design",
      "done": true,
      "path": "design.md",
      "description": "Technical design document with implementation details"
    }
  ],
  "unlocks": [],
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}


**Tool: read**

**Input:**
```json
{
  "path": "openspec/changes/handle-stock-shortage-after-payment/design.md"
}
```

Read file openspec/changes/handle-stock-shortage-after-payment/design.md, lines 1-35
1: # Design
2: 
3: ## Context
4: 
5: La reserva ocurre al crear el pedido; `Order.cancel` emite `OrderCancelled` y la liberación alcanza Inventario mediante un listener. `Order.refund` cambia el estado sin llamar a la pasarela, cuyo puerto solo autoriza. La spec `ordering` conserva un contrato de reembolso desde PAID que debe reconciliarse con el ADR 0015 antes de cambiarlo.
6: 
7: ## Goals / Non-Goals
8: 
9: **Goals:** hacer verificable el flujo del ADR sin perder efectos ni confundir anulación y devolución.
10: 
11: **Non-Goals:** resolver H10 o H11, definir endpoints o modificar fronteras sin autorización humana.
12: 
13: ## Decisions
14: 
15: - Modelar la rotura, la elección y las transiciones de pedido como comportamiento de dominio; conservar la reserva durante la espera. Alternativa descartada: decidir estados en adaptadores.
16: - Tratar Cobro anulado como operación real diferenciada del Reembolso y vincularla a las líneas afectadas. Una transición local sin confirmación de la pasarela no demuestra una anulación.
17: - Diseñar la ejecución recuperable e idempotente de la anulación, liberación y aviso, incluidos reintentos y competencia entre respuesta y vencimiento; evitar duplicar cobros anulados o stock liberado. El detalle de persistencia y de integración requiere revisión humana previa si introduce tablas o puertos entre contextos.
18: - Utilizar tiempo inyectado para verificar el vencimiento de 48 horas. El instante exacto de inicio y el tratamiento de respuestas en la frontera requieren acordar el contrato antes de implementarlo; no se confunden con H10.
19: 
20: ## Reglas de arquitectura
21: 
22: - Dominio y aplicación no dependen de infraestructura ni frameworks; el agregado decide transiciones y publica eventos mediante su puerto.
23: - `ordering` consulta `inventory` solo mediante su API publicada desde infraestructura; `inventory` no depende de `ordering`.
24: - Los efectos posteriores entre contextos, incluida la liberación, viajan por eventos de dominio; no se accede a persistencia ajena. Todo puerto nuevo o cambio de frontera requiere decisión humana y actualización previa del mapa.
25: 
26: ## Risks / Trade-offs
27: 
28: - [H10 y H11 abiertos] → negocio debe decidir antes de implementar cualquier parte dependiente; no inferir el saldo ni la duración de espera.
29: - [Conflicto con reembolso de PAID] → revisar contratos y delta de `ordering` con una persona antes de implementar; no cambiar silenciosamente el endpoint existente.
30: - [Fallo o respuesta incierta de pasarela] → identificar operación de forma estable y reconciliar antes de repetir; probar que no se da por completada sin confirmación.
31: - [Carrera entre elección y reloj] → una sola resolución efectiva y pruebas de concurrencia.
32: 
33: ## Migration Plan
34: 
35: Tras aprobación de negocio y fronteras, preparar cambios compatibles mediante expandir-contratar; desplegar el flujo solo cuando persistencia, pasarela y consumidores estén preparados. En caso de retirada, detener nuevas roturas y conservar y reconciliar las operaciones pendientes sin deshacer efectos financieros.

**Tool: read**

**Input:**
```json
{
  "path": "openspec/changes/handle-stock-shortage-after-payment/specs/stock-shortage-after-payment/spec.md"
}
```

Read file openspec/changes/handle-stock-shortage-after-payment/specs/stock-shortage-after-payment/spec.md, lines 1-36
1: # Spec Delta
2: 
3: ## Purpose
4: 
5: Gestionar la rotura de stock detectada al preparar un pedido cobrado, respetando la elección del cliente y el tratamiento de las líneas disponibles.
6: 
7: ## ADDED Requirements
8: 
9: ### Requirement: Elección del cliente ante rotura total
10: Al detectar durante la preparación que faltan todas las unidades de un pedido cobrado, el sistema SHALL registrar Rotura de stock detectada y ofrecer al cliente esperar la reposición o anular.
11: 
12: #### Scenario: Rotura total y el cliente elige esperar la reposición
13: - **GIVEN** un pedido cobrado con todas sus líneas reservadas y ninguna unidad disponible físicamente
14: - **WHEN** se registra Rotura de stock detectada y el cliente elige esperar la reposición
15: - **THEN** el pedido queda a la espera de reposición, sin Cobro anulado ni Stock liberado de sus líneas
16: 
17: #### Scenario: Rotura total y el cliente elige anular
18: - **GIVEN** un pedido cobrado con Rotura de stock detectada en todas sus líneas y la elección ofrecida al cliente
19: - **WHEN** el cliente elige anular
20: - **THEN** se produce Cobro anulado por el pedido y Stock liberado de todas las líneas anuladas
21: 
22: ### Requirement: Ausencia de respuesta ante rotura total
23: Ante una rotura total, el sistema SHALL anular automáticamente el cobro y avisar al cliente si no responde en 48 horas; SHALL liberar la reserva de las líneas anuladas.
24: 
25: #### Scenario: Rotura total sin respuesta en 48 horas
26: - **GIVEN** un pedido cobrado con Rotura de stock detectada en todas sus líneas y la elección ofrecida al cliente
27: - **WHEN** transcurren 48 horas sin respuesta
28: - **THEN** se produce Cobro anulado por el pedido, Stock liberado de todas las líneas anuladas y se avisa al cliente
29: 
30: ### Requirement: Tratamiento de rotura parcial
31: Al detectar durante la preparación que faltan solo algunas líneas de un pedido cobrado, el sistema SHALL registrar Rotura de stock detectada, enviar las líneas disponibles y producir Cobro anulado y Stock liberado de las líneas que faltan, sin preguntar al cliente.
32: 
33: #### Scenario: Rotura parcial con envío de las líneas disponibles
34: - **GIVEN** un pedido cobrado con líneas reservadas A y B, A disponible físicamente y B ausente
35: - **WHEN** se registra Rotura de stock detectada para B
36: - **THEN** se envía A y se produce Cobro anulado y Stock liberado solo de B, sin pedir elección al cliente

---

## Assistant

_Thinking:_



**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Add File: openspec/changes/handle-stock-shortage-after-payment/tasks.md\n+# Tasks\n+\n+La implementación está bloqueada hasta la revisión humana de la propuesta, la resolución de H10 y H11 para las partes que dependan de ellas y las decisiones de contratos y fronteras indicadas en `design.md`. No asumir sus respuestas.\n+\n+## 1. Contratos y decisiones previas\n+\n+- [ ] 1.1 Obtener decisiones humanas sobre H10, H11, el inicio y frontera de las 48 horas y la compatibilidad con reembolso de PAID; revisar los deltas antes de implementar. Verificación: revisión humana y `openspec validate handle-stock-shortage-after-payment --strict`.\n+- [ ] 1.2 Obtener aprobación humana previa de cualquier tabla, puerto entre contextos, cambio de frontera y matriz de seguridad que resulte necesario. Verificación: `ContextMapTest`, `TableOwnershipTest` y, para endpoints, `SecurityMatrixIT`.\n+\n+## 2. Rotura y efectos\n+\n+- [ ] 2.1 Implementar la detección y la elección de espera conservando la reserva; verificar con test de aceptación `@DisplayName(\"Rotura total y el cliente elige esperar la reposición\")`.\n+- [ ] 2.2 Implementar anulación real y Stock liberado de las líneas anuladas, con recuperación e idempotencia; verificar con `@DisplayName(\"Rotura total y el cliente elige anular\")` y pruebas de fallos y repetición de pasarela y liberación.\n+- [ ] 2.3 Implementar el vencimiento y aviso con reloj inyectado y resolución única frente a la respuesta concurrente; verificar con `@DisplayName(\"Rotura total sin respuesta en 48 horas\")` y tests de frontera y concurrencia.\n+- [ ] 2.4 Implementar envío parcial y anulación de las líneas faltantes; verificar con `@DisplayName(\"Rotura parcial con envío de las líneas disponibles\")` y test de liberación selectiva.\n+\n+## 3. Integración\n+\n+- [ ] 3.1 Ejecutar los cuatro tests de aceptación con pasarela simulada en la frontera y los tests de integración nuevos mediante Failsafe; verificar que pasan sin duplicar anulación ni liberación.\n+- [ ] 3.2 Ejecutar `bash ./verify-quality.sh --fast` y verificar que finaliza en verde.\n*** End Patch\n"
}
```

Success. Updated the following files:
A openspec/changes/handle-stock-shortage-after-payment/tasks.md

---

## Assistant

**Tool: shell**

**Input:**
```json
{
  "command": "openspec status --change \"handle-stock-shortage-after-payment\""
}
```

- Loading change status...
Change: handle-stock-shortage-after-payment
Schema: spec-driven
Change root: C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\changes\handle-stock-shortage-after-payment
Progress: 4/4 artifacts complete

[x] proposal
[x] specs
[x] design
[x] tasks

All planning artifacts complete!
Next: openspec instructions apply --change "handle-stock-shortage-after-payment" --json


**Tool: shell**

**Input:**
```json
{
  "command": "openspec validate handle-stock-shortage-after-payment --strict"
}
```

Change 'handle-stock-shortage-after-payment' is valid


**Tool: shell**

**Input:**
```json
{
  "command": "bash ./verify-quality.sh --fast",
  "timeout": 600000
}
```

▶ Specs
✓ spec/admin-exports
✓ spec/api-security
✓ spec/authentication
✓ spec/authorization
✓ spec/catalog-read-store
✓ spec/customer-name
✓ change/handle-stock-shortage-after-payment
✓ spec/inventory
✓ spec/order-payment
✓ spec/ordering
✓ spec/password-recovery
✓ spec/product-detail
✓ spec/product-editing
✓ spec/product-search
✓ spec/static-security-analysis
✓ spec/user-administration
Totals: 16 passed, 0 failed (16 items)
✓ change/2026-10-17-add-order-cancellation
✓ change/2026-10-17-add-order-placement
✓ change/2026-10-24-add-paid-orders-listing
✓ change/2026-10-31-add-product-editing
✓ change/2026-10-31-add-product-search
✓ change/2026-10-31-add-stock-reservation
✓ change/2026-11-02-add-product-detail
✓ change/2026-11-02-migrate-catalog-reads-to-mongodb
✓ change/2026-11-02-move-catalog-writes-to-mongodb
✓ change/2026-11-02-retire-catalog-postgres
✓ change/2026-11-03-add-two-level-product-cache
✓ change/2026-11-08-add-authentication
✓ change/2026-11-09-add-rbac-and-object-authorization
✓ change/2026-11-09-add-session-version-revocation
✓ change/2026-11-10-remediate-s13-audit-vulnerabilities
✓ change/2026-11-10-seed-s13-audit-vulnerabilities
✓ change/2026-11-17-confirm-order-payment-outbox
✓ change/2026-11-17-expand-customer-name
Totals: 18 passed, 0 failed (18 items)
Spec-trace: todos los escenarios tienen test.
▶ Configuración JPA (ADR docs/decisiones/listado-de-pedidos.md)
▶ Catálogo Mongo (ADR docs/adr/0009-catalogo-en-mongodb.md)
▶ Cache multinivel (ADR docs/adr/0010-cache-multinivel.md)
▶ Seguridad (ADR docs/adr/0011-autenticacion-refresh-y-version-de-sesion.md)
▶ Tests guardianes (lab S09: scripts/protected-tests.txt)
▶ Migraciones publicadas (lab S16: expandir-contraer)
▶ Capa 2: SAST y secretos (lab S13, docs/lab-s13.md)
▶ Capa 1: formato, estilo, bugs y malas prácticas (lab S16b, docs/lab-s16b.md)
[ERROR] BUILD FAILURE
[ERROR] Failed to execute goal com.diffplug.spotless:spotless-maven-plugin:3.10.4:check (default-cli) on project master-acdc: The following files had format violations:
[ERROR]     src\test\java\com\codeja\master_acdc\architecture\ContextMapTest.java
[ERROR]         @@ -20,9 +20,10 @@
[ERROR]          import�java.util.Set;
[ERROR]          /**
[ERROR]         -�*�Dependencias�prohibidas�del�mapa�de�contextos�(lab�S19).�Fuente:�docs/domain/context-map.md�y�los�ADR
[ERROR]         -�*�0013�(l�mites)�y�0014�(integraci�n).�Ampl�a�ModuleBoundariesTest�(lab�S18):�aquel�exige�usar�solo�el
[ERROR]         -�*�paquete�publicado;�este�fija�la�direcci�n�de�cada�relaci�n�y�la�lista�cerrada�de�puertos�entre�contextos.
[ERROR]         +�*�Dependencias�prohibidas�del�mapa�de�contextos�(lab�S19).�Fuente:�docs/domain/context-map.md�y�los
[ERROR]         +�*�ADR�0013�(l�mites)�y�0014�(integraci�n).�Ampl�a�ModuleBoundariesTest�(lab�S18):�aquel�exige�usar
[ERROR]         +�*�solo�el�paquete�publicado;�este�fija�la�direcci�n�de�cada�relaci�n�y�la�lista�cerrada�de�puertos
[ERROR]         +�*�entre�contextos.
[ERROR]          �*/
[ERROR]          @AnalyzeClasses(
[ERROR]          ����packages�=�"com.codeja.master_acdc",
[ERROR]         @@ -62,7 +63,8 @@
[ERROR]          ����������.dependOnClassesThat()
[ERROR]          ����������.resideInAnyPackage(modules("ordering",�"catalog",�"pricing",�"identity",�"exports"))
[ERROR]          ����������.because(
[ERROR]         -��������������"Inventario�es�upstream�de�Pedidos�y�de�Cat�logo:�no�conoce�a�sus�consumidores�(ADR�0013)"
[ERROR]         +��������������"Inventario�es�upstream�de�Pedidos�y�de�Cat�logo:�no�conoce�a�sus�consumidores�(ADR"
[ERROR]         +������������������+�"�0013)"
[ERROR]          ������������������+�MAP);
[ERROR]          ��@ArchTest
[ERROR]         @@ -74,8 +76,8 @@
[ERROR]          ����������.dependOnClassesThat()
[ERROR]          ����������.resideInAnyPackage(modules("ordering",�"catalog",�"inventory",�"identity",�"exports"))
[ERROR]          ����������.because(
[ERROR]         -��������������"Precios�es�el�due�o�del�shared�kernel:�si�dependiera�de�Pedidos,�el�kernel�tendr�a�un"
[ERROR]         -������������������+�"�ciclo�(ADR�0013)"
[ERROR]         +��������������"Precios�es�el�due�o�del�shared�kernel:�si�dependiera�de�Pedidos,�el�kernel�tendr�a"
[ERROR]         +������������������+�"�un�ciclo�(ADR�0013)"
[ERROR]          ������������������+�MAP);
[ERROR]          ��@ArchTest
[ERROR]         @@ -100,8 +102,10 @@
[ERROR]          ����������.that()
[ERROR]          ����������.resideInAPackage(ROOT�+�"exports..")
[ERROR]          ����������.should()
[ERROR]         -����������.onlyDependOnClassesThat(not(resideInAnyPackage(
[ERROR]         -��������������modules("catalog",�"inventory",�"ordering",�"pricing",�"identity"))))
[ERROR]         +����������.onlyDependOnClassesThat(
[ERROR]         +��������������not(
[ERROR]         +������������������resideInAnyPackage(
[ERROR]         +����������������������modules("catalog",�"inventory",�"ordering",�"pricing",�"identity"))))
[ERROR]          ����������.andShould()
[ERROR]          ����������.onlyHaveDependentClassesThat()
[ERROR]          ����������.resideInAPackage(ROOT�+�"exports..")
[ERROR]         @@ -126,13 +130,13 @@
[ERROR]     ... (68 more lines that didn't fit)
[ERROR] Violations also present in:
[ERROR]     src\test\java\com\codeja\master_acdc\architecture\TableOwnershipTest.java
[ERROR] Run 'mvn spotless:apply' to fix these violations.
AC/DC: la capa 1 falló. Corrige cada línea de arriba (archivo, línea, regla o patrón); no suprimas avisos ni toques config/, pom.xml o .mvn/.

Exit code: 1

---

## Assistant

_Thinking:_



Creado el change **`handle-stock-shortage-after-payment`** en `openspec/changes/handle-stock-shortage-after-payment/`.

Los cuatro artefactos de planificación están completos:

- `proposal.md`: alcance, incompatibilidad con el reembolso actual y preguntas abiertas.
- `specs/stock-shortage-after-payment/spec.md`: los cuatro escenarios del ADR 0015.
- `design.md`: enfoque, riesgos y límites de arquitectura.
- `tasks.md`: tareas y verificaciones previstas.

H10 sigue pendiente de Lucía y Marta; H11, de Marta. También queda pendiente reconciliar el contrato actual de reembolso de pedidos pagados antes de implementar.

**Validación:** OpenSpec informa de 4/4 artefactos completos y la validación estricta pasó. `verify-quality.sh --fast` falló en Spotless por formato en los archivos protegidos `ContextMapTest.java` y `TableOwnershipTest.java`; no los modifiqué.

Los artefactos están listos para revisión, pero la implementación tiene los bloqueos indicados. Cuando estén resueltos y quieras continuar, ejecuta `/opsx-apply`.

No se implementó código; Verify no llegó a completar las fases posteriores a la capa 1.

---

## User

Revise a change's existing planning artifacts and keep them coherent. Never edit code.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** These steps expect a project that already uses OpenSpec. Before the first step that writes anything (`new change`, `archive`, `sync specs`, or authoring an artifact file), confirm the project has a root: run `openspec list --json` (with `--store <id>` when a store is selected, since the store is then the root) and read `root`. A root object means the project is set up. `"root": null` means it is not - there is no `openspec/` directory here, and a write such as `openspec new change` would create one as a side effect. The command also exits non-zero, which is that answer rather than a broken CLI, so read the JSON instead of retrying or working around it.

One `"root": null` is not about setup: when a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml` (or `config.yml`), the project does use OpenSpec through a store it declares, which this machine cannot resolve (the store is not registered, or the `store:` line is malformed). Do not treat it as uninitialized and skip the branches below: stop before writing and show the user that error's `message` and `fix`.

Otherwise, with no root, what happens next depends on how this workflow was reached:

- **Auto-selected**: you chose this workflow yourself, without the user naming OpenSpec, naming this skill, or running its slash command. Stop using OpenSpec and answer the request normally, as you would with no OpenSpec installed. Do not ask them to set anything up and do not mention OpenSpec setup.
- **Explicit OpenSpec request**: the user named OpenSpec, named this skill, or ran its slash command. Stop before writing and ask how to proceed: set this project up (`openspec init`), target a store they already have (`--store <id>`), or continue without OpenSpec for this request. Wait for their answer.

In both branches, never create the root as a side effect: do not run `openspec init` until the user asks for it, do not hand-create `openspec/` files, and do not let a command create it.

**Input**: Optionally specify a change name after `/opsx-update` (e.g., `/opsx-update add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.
**Provided arguments**: handle-stock-shortage-after-payment

Decisión de negocio (Oscar, dueño): las 48 horas cuentan desde que se avisa al cliente de la Rotura de stock detectada. Incorpóralo al requisito y al escenario "Rotura total sin respuesta en 48 horas", y quítalo de las decisiones pendientes de design.md y de la tarea 1.1. En el escenario "Rotura total y el cliente elige esperar la reposición", añade que tras elegir esperar el vencimiento de 48 horas ya no anula el pedido. No toques H10, H11 ni el conflicto con el reembolso desde PAID: siguen abiertos. 

This workflow revises artifacts that already exist; it never creates missing ones. When an artifact is missing, `openspec status --change "<name>" --json` names the next one and `openspec instructions "<artifact-id>" --change "<name>" --json` explains how to write it.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` to get available changes sorted by most recently modified, and ask the user to select one

   When prompting, present the top 3-4 most recently modified changes as options, showing:
   - Change name
   - Status (e.g., "0/5 tasks", "complete", "no tasks")
   - How recently it was modified (from `lastModified` field)

   Mark the most recently modified change as "(Recommended)" since it's likely what the user wants to update.

   Always announce: "Using change: <name>" and how to override (e.g., `/opsx-update <other>`).

2. **Get the change's artifacts**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to understand current state. The response includes:
   - `schemaName`: The workflow schema being used (e.g., "spec-driven")
   - `artifacts`: Array of artifacts with their status ("done", "skipped", "ready", "blocked")
   - `isPlanningComplete`: Boolean indicating if all planning artifacts are complete. Older CLI versions expose the same value as `isComplete`.
   - `planningHome`, `changeRoot`, `artifactPaths`, and `actionContext`: path and scope context. Use these instead of assuming repo-local paths.

   The artifact ids and paths come from the active schema - do NOT assume them, and do NOT branch on hardcoded artifact names. Custom schemas must work unchanged.

   The files to edit are `artifactPaths.<id>.existingOutputPaths` - the concrete files that exist on disk, already glob-expanded for glob artifacts (e.g. `specs/**/*.md`), plus any confirmed concrete path allowed under a partly populated glob artifact (step 4). Do NOT write to `resolvedOutputPath`: for a glob artifact it is still the glob pattern, not a real file.

3. **Understand the request**
   - If the user asked for a specific revision ("the design now uses X"), that is the starting edit.
   - If they only said "update" / "make this coherent", treat it as a coherence review: read the existing artifacts and check them against each other for contradictions, gaps, and duplication.

4. **Read and reconcile**
   - Read the artifact(s) the request touches and the change's other existing artifacts.
   - Draft the requested edit in the conversation, not in files. Work out exactly what it changes; step 5 owns every write. Then check every other existing artifact against the drafted edit - in ANY direction: an edit to a later artifact may require revising an earlier one, not only the other way around. Build order is a useful reading order, not a constraint on which artifacts may be revised.
   - Note everything that is now inconsistent, missing, or contradictory.
   - Propose revisions to files that already exist (`existingOutputPaths`). If an artifact has no existing output files and status `ready` or `blocked`, note it and point the user to `openspec instructions "<artifact-id>" --change "<name>" --json` for how to create them. Leave `skipped` artifacts untouched; do not treat them as missing or defer them to the continue workflow.
   - A glob artifact (e.g. `specs/**/*.md`) is marked `done` after at least one file matches, and the continue workflow only handles `ready` artifacts. When reconciliation identifies a missing file for a glob artifact whose `existingOutputPaths` is non-empty:
     1. Run `openspec instructions "<artifact-id>" --change "<name>" --json` and use its `instruction` and `template`. Treat `context` and `rules` as constraints; do not copy them into the file. If instructions report `skipped: true`, do not create the file. Read current dependency files from disk; if a required non-skipped dependency is missing, stop and ask the user to restore it first.
     2. Choose a concrete path inside `changeRoot` that matches `artifactPaths.<id>.outputPath` and does not already exist. Verify it remains inside `changeRoot` after resolving any symlinked parent directories. The glob `resolvedOutputPath` is not a valid target.
     3. Include the new file in step 5's proposed revisions and create it only after the user confirms.
     4. After confirmation, immediately before creation, refresh status and instructions. Verify the artifact is still in scope, not skipped, and partially populated; repeat the concrete-path checks above.
     5. Use a create operation that fails if the target already exists. If `instruction` delegates creation to another skill or command, invoke it only if it can honor the confirmed path and these guardrails; otherwise stop. If any check fails or the confirmed draft is no longer valid, stop and reconcile with the user rather than replacing existing content or choosing a different path.
   - If the change is already coherent, say so and propose no revisions.

5. **Confirm and apply, one artifact at a time**
   - This step performs every artifact write in this workflow; no earlier step edits an artifact.
   - Show each proposed revision and why - including the requested edit drafted in step 4. Write only after the user confirms.
   - If the user rejects a revision, do not write it - leave that artifact unchanged.
   - When a substantial rewrite is needed, get that artifact's rules and template first:
     ```bash
     openspec instructions "<artifact-id>" --change "<name>" --json
     ```

6. **Point to the next step (guidance only - NEVER act on it)**
   - Artifacts with empty `existingOutputPaths` and status `ready` or `blocked` -> run `openspec status --change "<name>" --json` for the next artifact and point the user to `openspec instructions "<artifact-id>" --change "<name>" --json` for how to create it.
   - Change already implemented (tasks checked off / already applied) -> the code may no longer match the revised plan; suggest `/opsx-apply` to carry the delta into code.
   - Everything done and implemented -> suggest `/opsx-archive`.

**Output**

After each invocation, show:
- Which artifacts were revised (and which proposed revisions were rejected)
- Any file created under a glob artifact that was already partially populated
- Anything deferred because it does not exist yet (artifacts with no files and status `ready` or `blocked`, never `skipped` artifacts)
- Where the change stands and the recommended next command

**Guardrails**
- Planning artifacts only - NEVER edit implementation code. If the revised plan implies code changes, stop and point to `/opsx-apply`.
- Use the artifact ids and paths reported by `openspec status`; never branch on hardcoded artifact names.
- Edit only the concrete files in `existingOutputPaths`, plus any confirmed concrete path allowed under a partly populated glob artifact (step 4); never write to a glob `resolvedOutputPath`.
- Do not advance the build frontier: if an artifact has empty `existingOutputPaths` and status `ready` or `blocked`, creating them is a separate step, outside this workflow. Leave `skipped` artifacts untouched. The only new-file scope is a confirmed concrete path under a glob artifact whose `existingOutputPaths` is non-empty.
- Confirm every edit with the user before writing.
- If the request changes the change's *intent* rather than refining it, ask for a distinct unused change name and recommend `openspec new change "<new-change-name>"` instead (the "Update vs. Start Fresh" heuristic).

---

## Assistant

_Thinking:_



Usando el change `handle-stock-shortage-after-payment`. Para elegir otro, indica `/opsx-update <otro>`. Revisaré los artefactos y te mostraré los cambios antes de escribirlos.

**Tool: skill**

**Input:**
```json
{
  "id": "openspec-update-change"
}
```

<skill_content name="openspec-update-change">
# Skill: openspec-update-change

Revise a change's existing planning artifacts and keep them coherent. Never edit code.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** These steps expect a project that already uses OpenSpec. Before the first step that writes anything (`new change`, `archive`, `sync specs`, or authoring an artifact file), confirm the project has a root: run `openspec list --json` (with `--store <id>` when a store is selected, since the store is then the root) and read `root`. A root object means the project is set up. `"root": null` means it is not - there is no `openspec/` directory here, and a write such as `openspec new change` would create one as a side effect. The command also exits non-zero, which is that answer rather than a broken CLI, so read the JSON instead of retrying or working around it.

One `"root": null` is not about setup: when a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml` (or `config.yml`), the project does use OpenSpec through a store it declares, which this machine cannot resolve (the store is not registered, or the `store:` line is malformed). Do not treat it as uninitialized and skip the branches below: stop before writing and show the user that error's `message` and `fix`.

Otherwise, with no root, what happens next depends on how this workflow was reached:

- **Auto-selected**: you chose this workflow yourself, without the user naming OpenSpec, naming this skill, or running its slash command. Stop using OpenSpec and answer the request normally, as you would with no OpenSpec installed. Do not ask them to set anything up and do not mention OpenSpec setup.
- **Explicit OpenSpec request**: the user named OpenSpec, named this skill, or ran its slash command. Stop before writing and ask how to proceed: set this project up (`openspec init`), target a store they already have (`--store <id>`), or continue without OpenSpec for this request. Wait for their answer.

In both branches, never create the root as a side effect: do not run `openspec init` until the user asks for it, do not hand-create `openspec/` files, and do not let a command create it.

**Input**: Optionally specify a change name. If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

This workflow revises artifacts that already exist; it never creates missing ones. When an artifact is missing, `openspec status --change "<name>" --json` names the next one and `openspec instructions "<artifact-id>" --change "<name>" --json` explains how to write it.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` to get available changes sorted by most recently modified, and ask the user to select one

   When prompting, present the top 3-4 most recently modified changes as options, showing:
   - Change name
   - Status (e.g., "0/5 tasks", "complete", "no tasks")
   - How recently it was modified (from `lastModified` field)

   Mark the most recently modified change as "(Recommended)" since it's likely what the user wants to update.

   Always announce: "Using change: <name>" and how to override (e.g., `/opsx-update <other>`).

2. **Get the change's artifacts**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to understand current state. The response includes:
   - `schemaName`: The workflow schema being used (e.g., "spec-driven")
   - `artifacts`: Array of artifacts with their status ("done", "skipped", "ready", "blocked")
   - `isPlanningComplete`: Boolean indicating if all planning artifacts are complete. Older CLI versions expose the same value as `isComplete`.
   - `planningHome`, `changeRoot`, `artifactPaths`, and `actionContext`: path and scope context. Use these instead of assuming repo-local paths.

   The artifact ids and paths come from the active schema - do NOT assume them, and do NOT branch on hardcoded artifact names. Custom schemas must work unchanged.

   The files to edit are `artifactPaths.<id>.existingOutputPaths` - the concrete files that exist on disk, already glob-expanded for glob artifacts (e.g. `specs/**/*.md`), plus any confirmed concrete path allowed under a partly populated glob artifact (step 4). Do NOT write to `resolvedOutputPath`: for a glob artifact it is still the glob pattern, not a real file.

3. **Understand the request**
   - If the user asked for a specific revision ("the design now uses X"), that is the starting edit.
   - If they only said "update" / "make this coherent", treat it as a coherence review: read the existing artifacts and check them against each other for contradictions, gaps, and duplication.

4. **Read and reconcile**
   - Read the artifact(s) the request touches and the change's other existing artifacts.
   - Draft the requested edit in the conversation, not in files. Work out exactly what it changes; step 5 owns every write. Then check every other existing artifact against the drafted edit - in ANY direction: an edit to a later artifact may require revising an earlier one, not only the other way around. Build order is a useful reading order, not a constraint on which artifacts may be revised.
   - Note everything that is now inconsistent, missing, or contradictory.
   - Propose revisions to files that already exist (`existingOutputPaths`). If an artifact has no existing output files and status `ready` or `blocked`, note it and point the user to `openspec instructions "<artifact-id>" --change "<name>" --json` for how to create them. Leave `skipped` artifacts untouched; do not treat them as missing or defer them to the continue workflow.
   - A glob artifact (e.g. `specs/**/*.md`) is marked `done` after at least one file matches, and the continue workflow only handles `ready` artifacts. When reconciliation identifies a missing file for a glob artifact whose `existingOutputPaths` is non-empty:
     1. Run `openspec instructions "<artifact-id>" --change "<name>" --json` and use its `instruction` and `template`. Treat `context` and `rules` as constraints; do not copy them into the file. If instructions report `skipped: true`, do not create the file. Read current dependency files from disk; if a required non-skipped dependency is missing, stop and ask the user to restore it first.
     2. Choose a concrete path inside `changeRoot` that matches `artifactPaths.<id>.outputPath` and does not already exist. Verify it remains inside `changeRoot` after resolving any symlinked parent directories. The glob `resolvedOutputPath` is not a valid target.
     3. Include the new file in step 5's proposed revisions and create it only after the user confirms.
     4. After confirmation, immediately before creation, refresh status and instructions. Verify the artifact is still in scope, not skipped, and partially populated; repeat the concrete-path checks above.
     5. Use a create operation that fails if the target already exists. If `instruction` delegates creation to another skill or command, invoke it only if it can honor the confirmed path and these guardrails; otherwise stop. If any check fails or the confirmed draft is no longer valid, stop and reconcile with the user rather than replacing existing content or choosing a different path.
   - If the change is already coherent, say so and propose no revisions.

5. **Confirm and apply, one artifact at a time**
   - This step performs every artifact write in this workflow; no earlier step edits an artifact.
   - Show each proposed revision and why - including the requested edit drafted in step 4. Write only after the user confirms.
   - If the user rejects a revision, do not write it - leave that artifact unchanged.
   - When a substantial rewrite is needed, get that artifact's rules and template first:
     ```bash
     openspec instructions "<artifact-id>" --change "<name>" --json
     ```

6. **Point to the next step (guidance only - NEVER act on it)**
   - Artifacts with empty `existingOutputPaths` and status `ready` or `blocked` -> run `openspec status --change "<name>" --json` for the next artifact and point the user to `openspec instructions "<artifact-id>" --change "<name>" --json` for how to create it.
   - Change already implemented (tasks checked off / already applied) -> the code may no longer match the revised plan; suggest `/opsx-apply` to carry the delta into code.
   - Everything done and implemented -> suggest `/opsx-archive`.

**Output**

After each invocation, show:
- Which artifacts were revised (and which proposed revisions were rejected)
- Any file created under a glob artifact that was already partially populated
- Anything deferred because it does not exist yet (artifacts with no files and status `ready` or `blocked`, never `skipped` artifacts)
- Where the change stands and the recommended next command

**Guardrails**
- Planning artifacts only - NEVER edit implementation code. If the revised plan implies code changes, stop and point to `/opsx-apply`.
- Use the artifact ids and paths reported by `openspec status`; never branch on hardcoded artifact names.
- Edit only the concrete files in `existingOutputPaths`, plus any confirmed concrete path allowed under a partly populated glob artifact (step 4); never write to a glob `resolvedOutputPath`.
- Do not advance the build frontier: if an artifact has empty `existingOutputPaths` and status `ready` or `blocked`, creating them is a separate step, outside this workflow. Leave `skipped` artifacts untouched. The only new-file scope is a confirmed concrete path under a glob artifact whose `existingOutputPaths` is non-empty.
- Confirm every edit with the user before writing.
- If the request changes the change's *intent* rather than refining it, ask for a distinct unused change name and recommend `openspec new change "<new-change-name>"` instead (the "Update vs. Start Fresh" heuristic).

Base directory for this skill: C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\.opencode\skills\openspec-update-change
Relative paths in this skill (e.g., scripts/, reference/) are relative to this base directory.
Note: file list is sampled.

<skill_files>
</skill_files>
</skill_content>

---

## Assistant

**Tool: shell**

**Input:**
```json
{
  "command": "openspec status --change \"handle-stock-shortage-after-payment\" --json"
}
```

{
  "changeName": "handle-stock-shortage-after-payment",
  "schemaName": "spec-driven",
  "planningHome": {
    "kind": "repo",
    "root": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "changesDir": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes",
    "defaultSchema": "spec-driven"
  },
  "changeRoot": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment",
  "artifactPaths": {
    "proposal": {
      "outputPath": "proposal.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\proposal.md"
      ]
    },
    "specs": {
      "outputPath": "specs/**/*.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\**\\*.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\specs\\stock-shortage-after-payment\\spec.md"
      ]
    },
    "design": {
      "outputPath": "design.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\design.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\design.md"
      ]
    },
    "tasks": {
      "outputPath": "tasks.md",
      "resolvedOutputPath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\tasks.md",
      "existingOutputPaths": [
        "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\openspec\\changes\\handle-stock-shortage-after-payment\\tasks.md"
      ]
    }
  },
  "isPlanningComplete": true,
  "isComplete": true,
  "applyRequires": [
    "tasks"
  ],
  "nextSteps": [
    "All planning artifacts are complete. Run openspec instructions apply --change \"handle-stock-shortage-after-payment\" --json to inspect implementation progress."
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


**Tool: shell**

**Input:**
```json
{
  "command": "openspec list --json"
}
```

{
  "changes": [
    {
      "name": "handle-stock-shortage-after-payment",
      "completedTasks": 0,
      "totalTasks": 8,
      "lastModified": "2026-11-25T02:47:07.067Z",
      "status": "in-progress"
    }
  ],
  "root": {
    "path": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc",
    "source": "nearest"
  }
}


**Tool: read**

**Input:**
```json
{
  "path": "openspec/changes/handle-stock-shortage-after-payment/proposal.md"
}
```

Read file openspec/changes/handle-stock-shortage-after-payment/proposal.md, lines 1-38
1: # Proposal
2: 
3: ## Why
4: 
5: Una reserva en el sistema no garantiza el apartado físico: la rotura puede descubrirse después de cobrar. El ADR 0015 establece cómo proteger al cliente y tramitar las líneas disponibles sin confundir Cobro anulado con Reembolso.
6: 
7: ## What Changes
8: 
9: - Registrar Rotura de stock detectada durante la preparación de un pedido cobrado, manteniendo la reserva al crear el pedido.
10: - Ante una rotura total, ofrecer esperar la reposición o anular; sin respuesta en 48 horas, anular el cobro y avisar.
11: - Ante una rotura parcial, enviar las líneas disponibles y anular el cobro de las líneas que faltan sin preguntar.
12: - Cada anulación libera la reserva de las líneas anuladas; el Cobro anulado requiere una operación real de la pasarela y es distinto del Reembolso tras una devolución.
13: 
14: ## Capabilities
15: 
16: ### New Capabilities
17: 
18: - `stock-shortage-after-payment`: tratamiento de las cuatro ramas de rotura tras el cobro.
19: 
20: ### Modified Capabilities
21: 
22: Ninguna en esta propuesta: las incompatibilidades existentes requieren decisión y una posterior revisión de sus especificaciones.
23: 
24: ## Impact
25: 
26: Pedidos, Inventario, integración con la pasarela, comunicación al cliente y preparación de envíos. Hoy `Order.refund` mezcla anulación y reembolso, y la pasarela solo autoriza; no existe el flujo de rotura ni una anulación real. La especificación `ordering` permite reembolsar un pedido PAID sin liberar stock, por lo que debe reconciliarse explícitamente con el ADR antes de implementar; no se modifica silenciosamente aquí. Nuevos puertos entre contextos, tablas, fronteras o endpoints requieren las decisiones y revisiones humanas establecidas en el proyecto.
27: 
28: ## Non-goals
29: 
30: - Cambiar el momento de reserva, implementar caducidad de reservas, devoluciones o facturación rectificativa.
31: - Decidir el ajuste del saldo por rotura o un plazo máximo de espera por reposición.
32: 
33: ## Preguntas abiertas y bloqueos
34: 
35: - **H10 — Lucía y Marta:** si el cliente elige esperar la reposición, ¿cuál es el plazo máximo de espera?
36: - **H11 — Marta:** ¿la rotura ajusta automáticamente el saldo del sistema o Marta lo ajusta tras recontar?
37: 
38: Ambas siguen abiertas. No se supone ninguna respuesta; las partes de la implementación que dependan de ellas deben esperar a negocio. También deben acordarse la interacción con los contratos existentes y cualquier cambio de frontera antes de implementar.

**Tool: read**

**Input:**
```json
{
  "path": "openspec/changes/handle-stock-shortage-after-payment/specs/stock-shortage-after-payment/spec.md"
}
```

Read file openspec/changes/handle-stock-shortage-after-payment/specs/stock-shortage-after-payment/spec.md, lines 1-36
1: # Spec Delta
2: 
3: ## Purpose
4: 
5: Gestionar la rotura de stock detectada al preparar un pedido cobrado, respetando la elección del cliente y el tratamiento de las líneas disponibles.
6: 
7: ## ADDED Requirements
8: 
9: ### Requirement: Elección del cliente ante rotura total
10: Al detectar durante la preparación que faltan todas las unidades de un pedido cobrado, el sistema SHALL registrar Rotura de stock detectada y ofrecer al cliente esperar la reposición o anular.
11: 
12: #### Scenario: Rotura total y el cliente elige esperar la reposición
13: - **GIVEN** un pedido cobrado con todas sus líneas reservadas y ninguna unidad disponible físicamente
14: - **WHEN** se registra Rotura de stock detectada y el cliente elige esperar la reposición
15: - **THEN** el pedido queda a la espera de reposición, sin Cobro anulado ni Stock liberado de sus líneas
16: 
17: #### Scenario: Rotura total y el cliente elige anular
18: - **GIVEN** un pedido cobrado con Rotura de stock detectada en todas sus líneas y la elección ofrecida al cliente
19: - **WHEN** el cliente elige anular
20: - **THEN** se produce Cobro anulado por el pedido y Stock liberado de todas las líneas anuladas
21: 
22: ### Requirement: Ausencia de respuesta ante rotura total
23: Ante una rotura total, el sistema SHALL anular automáticamente el cobro y avisar al cliente si no responde en 48 horas; SHALL liberar la reserva de las líneas anuladas.
24: 
25: #### Scenario: Rotura total sin respuesta en 48 horas
26: - **GIVEN** un pedido cobrado con Rotura de stock detectada en todas sus líneas y la elección ofrecida al cliente
27: - **WHEN** transcurren 48 horas sin respuesta
28: - **THEN** se produce Cobro anulado por el pedido, Stock liberado de todas las líneas anuladas y se avisa al cliente
29: 
30: ### Requirement: Tratamiento de rotura parcial
31: Al detectar durante la preparación que faltan solo algunas líneas de un pedido cobrado, el sistema SHALL registrar Rotura de stock detectada, enviar las líneas disponibles y producir Cobro anulado y Stock liberado de las líneas que faltan, sin preguntar al cliente.
32: 
33: #### Scenario: Rotura parcial con envío de las líneas disponibles
34: - **GIVEN** un pedido cobrado con líneas reservadas A y B, A disponible físicamente y B ausente
35: - **WHEN** se registra Rotura de stock detectada para B
36: - **THEN** se envía A y se produce Cobro anulado y Stock liberado solo de B, sin pedir elección al cliente

**Tool: read**

**Input:**
```json
{
  "path": "openspec/changes/handle-stock-shortage-after-payment/design.md"
}
```

Read file openspec/changes/handle-stock-shortage-after-payment/design.md, lines 1-35
1: # Design
2: 
3: ## Context
4: 
5: La reserva ocurre al crear el pedido; `Order.cancel` emite `OrderCancelled` y la liberación alcanza Inventario mediante un listener. `Order.refund` cambia el estado sin llamar a la pasarela, cuyo puerto solo autoriza. La spec `ordering` conserva un contrato de reembolso desde PAID que debe reconciliarse con el ADR 0015 antes de cambiarlo.
6: 
7: ## Goals / Non-Goals
8: 
9: **Goals:** hacer verificable el flujo del ADR sin perder efectos ni confundir anulación y devolución.
10: 
11: **Non-Goals:** resolver H10 o H11, definir endpoints o modificar fronteras sin autorización humana.
12: 
13: ## Decisions
14: 
15: - Modelar la rotura, la elección y las transiciones de pedido como comportamiento de dominio; conservar la reserva durante la espera. Alternativa descartada: decidir estados en adaptadores.
16: - Tratar Cobro anulado como operación real diferenciada del Reembolso y vincularla a las líneas afectadas. Una transición local sin confirmación de la pasarela no demuestra una anulación.
17: - Diseñar la ejecución recuperable e idempotente de la anulación, liberación y aviso, incluidos reintentos y competencia entre respuesta y vencimiento; evitar duplicar cobros anulados o stock liberado. El detalle de persistencia y de integración requiere revisión humana previa si introduce tablas o puertos entre contextos.
18: - Utilizar tiempo inyectado para verificar el vencimiento de 48 horas. El instante exacto de inicio y el tratamiento de respuestas en la frontera requieren acordar el contrato antes de implementarlo; no se confunden con H10.
19: 
20: ## Reglas de arquitectura
21: 
22: - Dominio y aplicación no dependen de infraestructura ni frameworks; el agregado decide transiciones y publica eventos mediante su puerto.
23: - `ordering` consulta `inventory` solo mediante su API publicada desde infraestructura; `inventory` no depende de `ordering`.
24: - Los efectos posteriores entre contextos, incluida la liberación, viajan por eventos de dominio; no se accede a persistencia ajena. Todo puerto nuevo o cambio de frontera requiere decisión humana y actualización previa del mapa.
25: 
26: ## Risks / Trade-offs
27: 
28: - [H10 y H11 abiertos] → negocio debe decidir antes de implementar cualquier parte dependiente; no inferir el saldo ni la duración de espera.
29: - [Conflicto con reembolso de PAID] → revisar contratos y delta de `ordering` con una persona antes de implementar; no cambiar silenciosamente el endpoint existente.
30: - [Fallo o respuesta incierta de pasarela] → identificar operación de forma estable y reconciliar antes de repetir; probar que no se da por completada sin confirmación.
31: - [Carrera entre elección y reloj] → una sola resolución efectiva y pruebas de concurrencia.
32: 
33: ## Migration Plan
34: 
35: Tras aprobación de negocio y fronteras, preparar cambios compatibles mediante expandir-contratar; desplegar el flujo solo cuando persistencia, pasarela y consumidores estén preparados. En caso de retirada, detener nuevas roturas y conservar y reconciliar las operaciones pendientes sin deshacer efectos financieros.

**Tool: read**

**Input:**
```json
{
  "path": "openspec/changes/handle-stock-shortage-after-payment/tasks.md"
}
```

Read file openspec/changes/handle-stock-shortage-after-payment/tasks.md, lines 1-20
1: # Tasks
2: 
3: La implementación está bloqueada hasta la revisión humana de la propuesta, la resolución de H10 y H11 para las partes que dependan de ellas y las decisiones de contratos y fronteras indicadas en `design.md`. No asumir sus respuestas.
4: 
5: ## 1. Contratos y decisiones previas
6: 
7: - [ ] 1.1 Obtener decisiones humanas sobre H10, H11, el inicio y frontera de las 48 horas y la compatibilidad con reembolso de PAID; revisar los deltas antes de implementar. Verificación: revisión humana y `openspec validate handle-stock-shortage-after-payment --strict`.
8: - [ ] 1.2 Obtener aprobación humana previa de cualquier tabla, puerto entre contextos, cambio de frontera y matriz de seguridad que resulte necesario. Verificación: `ContextMapTest`, `TableOwnershipTest` y, para endpoints, `SecurityMatrixIT`.
9: 
10: ## 2. Rotura y efectos
11: 
12: - [ ] 2.1 Implementar la detección y la elección de espera conservando la reserva; verificar con test de aceptación `@DisplayName("Rotura total y el cliente elige esperar la reposición")`.
13: - [ ] 2.2 Implementar anulación real y Stock liberado de las líneas anuladas, con recuperación e idempotencia; verificar con `@DisplayName("Rotura total y el cliente elige anular")` y pruebas de fallos y repetición de pasarela y liberación.
14: - [ ] 2.3 Implementar el vencimiento y aviso con reloj inyectado y resolución única frente a la respuesta concurrente; verificar con `@DisplayName("Rotura total sin respuesta en 48 horas")` y tests de frontera y concurrencia.
15: - [ ] 2.4 Implementar envío parcial y anulación de las líneas faltantes; verificar con `@DisplayName("Rotura parcial con envío de las líneas disponibles")` y test de liberación selectiva.
16: 
17: ## 3. Integración
18: 
19: - [ ] 3.1 Ejecutar los cuatro tests de aceptación con pasarela simulada en la frontera y los tests de integración nuevos mediante Failsafe; verificar que pasan sin duplicar anulación ni liberación.
20: - [ ] 3.2 Ejecutar `bash ./verify-quality.sh --fast` y verificar que finaliza en verde.

---

## Assistant

Propongo estas revisiones, sin modificar H10, H11 ni el conflicto del reembolso desde PAID:

1. **`specs/stock-shortage-after-payment/spec.md`**
   - Requisito de ausencia de respuesta: contar las 48 horas **desde que se avisa al cliente de la Rotura de stock detectada**.
   - Escenario sin respuesta: indicar que las 48 horas transcurren desde ese aviso.
   - Escenario de espera: añadir que, una vez elegida la espera, el vencimiento de esas 48 horas no anula el pedido.

2. **`design.md`**: registrar el aviso como inicio del plazo y retirar ese inicio de las decisiones pendientes. La respuesta exactamente en la frontera seguirá pendiente.

3. **`tasks.md`**, tarea 1.1: retirar el inicio del plazo de las decisiones que se deben obtener, manteniendo pendiente el tratamiento de la frontera.

4. **`proposal.md`**: precisar también que las 48 horas cuentan desde el aviso, para que el resumen sea coherente con la spec.

No he editado ni creado archivos; ninguno de los artefactos está pendiente de creación. El change sigue sin implementar.

**¿Confirmas estas cuatro revisiones?**

---

## User

Si

---

## Assistant

_Thinking:_



**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: openspec/changes/handle-stock-shortage-after-payment/proposal.md\n@@\n-- Ante una rotura total, ofrecer esperar la reposición o anular; sin respuesta en 48 horas, anular el cobro y avisar.\n+- Ante una rotura total, ofrecer esperar la reposición o anular; sin respuesta en 48 horas desde el aviso de la rotura, anular el cobro y avisar.\n*** End Patch\n"
}
```

Success. Updated the following files:
M openspec/changes/handle-stock-shortage-after-payment/proposal.md

---

## Assistant

**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: openspec/changes/handle-stock-shortage-after-payment/specs/stock-shortage-after-payment/spec.md\n@@\n - **THEN** el pedido queda a la espera de reposición, sin Cobro anulado ni Stock liberado de sus líneas\n+- **AND** el vencimiento de las 48 horas ya no anula el pedido\n@@\n-Ante una rotura total, el sistema SHALL anular automáticamente el cobro y avisar al cliente si no responde en 48 horas; SHALL liberar la reserva de las líneas anuladas.\n+Ante una rotura total, el sistema SHALL anular automáticamente el cobro y avisar al cliente si no responde en 48 horas desde que se le avisa de la Rotura de stock detectada; SHALL liberar la reserva de las líneas anuladas.\n@@\n-- **WHEN** transcurren 48 horas sin respuesta\n+- **WHEN** transcurren 48 horas desde el aviso al cliente de la Rotura de stock detectada sin respuesta\n*** End Patch\n"
}
```

Success. Updated the following files:
M openspec/changes/handle-stock-shortage-after-payment/specs/stock-shortage-after-payment/spec.md

---

## Assistant

**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: openspec/changes/handle-stock-shortage-after-payment/design.md\n@@\n-- Utilizar tiempo inyectado para verificar el vencimiento de 48 horas. El instante exacto de inicio y el tratamiento de respuestas en la frontera requieren acordar el contrato antes de implementarlo; no se confunden con H10.\n+- Utilizar tiempo inyectado para verificar el vencimiento de 48 horas desde el aviso al cliente de la Rotura de stock detectada. El tratamiento de respuestas en la frontera requiere acordar el contrato antes de implementarlo; no se confunde con H10.\n*** End Patch\n"
}
```

Success. Updated the following files:
M openspec/changes/handle-stock-shortage-after-payment/design.md

---

## Assistant

**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: openspec/changes/handle-stock-shortage-after-payment/tasks.md\n@@\n-- [ ] 1.1 Obtener decisiones humanas sobre H10, H11, el inicio y frontera de las 48 horas y la compatibilidad con reembolso de PAID; revisar los deltas antes de implementar. Verificación: revisión humana y `openspec validate handle-stock-shortage-after-payment --strict`.\n+- [ ] 1.1 Obtener decisiones humanas sobre H10, H11, la frontera de las 48 horas y la compatibilidad con reembolso de PAID; revisar los deltas antes de implementar. Verificación: revisión humana y `openspec validate handle-stock-shortage-after-payment --strict`.\n*** End Patch\n"
}
```

Success. Updated the following files:
M openspec/changes/handle-stock-shortage-after-payment/tasks.md

---

## Assistant

**Tool: shell**

**Input:**
```json
{
  "command": "openspec validate handle-stock-shortage-after-payment --strict"
}
```

Change 'handle-stock-shortage-after-payment' is valid


**Tool: shell**

**Input:**
```json
{
  "command": "openspec status --change handle-stock-shortage-after-payment"
}
```

- Loading change status...
Change: handle-stock-shortage-after-payment
Schema: spec-driven
Change root: C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\openspec\changes\handle-stock-shortage-after-payment
Progress: 4/4 artifacts complete

[x] proposal
[x] specs
[x] design
[x] tasks

All planning artifacts complete!
Next: openspec instructions apply --change "handle-stock-shortage-after-payment" --json


**Tool: shell**

**Input:**
```json
{
  "command": "bash ./verify-quality.sh --fast",
  "timeout": 600000
}
```

▶ Specs
✓ spec/admin-exports
✓ spec/api-security
✓ spec/authentication
✓ spec/authorization
✓ spec/catalog-read-store
✓ spec/customer-name
✓ change/handle-stock-shortage-after-payment
✓ spec/inventory
✓ spec/order-payment
✓ spec/ordering
✓ spec/password-recovery
✓ spec/product-detail
✓ spec/product-editing
✓ spec/product-search
✓ spec/static-security-analysis
✓ spec/user-administration
Totals: 16 passed, 0 failed (16 items)
✓ change/2026-10-17-add-order-cancellation
✓ change/2026-10-17-add-order-placement
✓ change/2026-10-24-add-paid-orders-listing
✓ change/2026-10-31-add-product-editing
✓ change/2026-10-31-add-product-search
✓ change/2026-10-31-add-stock-reservation
✓ change/2026-11-02-add-product-detail
✓ change/2026-11-02-migrate-catalog-reads-to-mongodb
✓ change/2026-11-02-move-catalog-writes-to-mongodb
✓ change/2026-11-02-retire-catalog-postgres
✓ change/2026-11-03-add-two-level-product-cache
✓ change/2026-11-08-add-authentication
✓ change/2026-11-09-add-rbac-and-object-authorization
✓ change/2026-11-09-add-session-version-revocation
✓ change/2026-11-10-remediate-s13-audit-vulnerabilities
✓ change/2026-11-10-seed-s13-audit-vulnerabilities
✓ change/2026-11-17-confirm-order-payment-outbox
✓ change/2026-11-17-expand-customer-name
Totals: 18 passed, 0 failed (18 items)
Spec-trace: todos los escenarios tienen test.
▶ Configuración JPA (ADR docs/decisiones/listado-de-pedidos.md)
▶ Catálogo Mongo (ADR docs/adr/0009-catalogo-en-mongodb.md)
▶ Cache multinivel (ADR docs/adr/0010-cache-multinivel.md)
▶ Seguridad (ADR docs/adr/0011-autenticacion-refresh-y-version-de-sesion.md)
▶ Tests guardianes (lab S09: scripts/protected-tests.txt)
▶ Migraciones publicadas (lab S16: expandir-contraer)
▶ Capa 2: SAST y secretos (lab S13, docs/lab-s13.md)
▶ Capa 1: formato, estilo, bugs y malas prácticas (lab S16b, docs/lab-s16b.md)
[ERROR] C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\architecture\TableOwnershipTest.java:58:8: Nombre de test: should_<comportamiento>_when_<condici�n> en snake_case min�scula (p. ej. should_reject_order_when_status_is_shipped). La frase legible va en @DisplayName. [testMethodName]
[ERROR] C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\architecture\TableOwnershipTest.java:78:8: Nombre de test: should_<comportamiento>_when_<condici�n> en snake_case min�scula (p. ej. should_reject_order_when_status_is_shipped). La frase legible va en @DisplayName. [testMethodName]
[ERROR] C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\src\test\java\com\codeja\master_acdc\architecture\TableOwnershipTest.java:108:8: Nombre de test: should_<comportamiento>_when_<condici�n> en snake_case min�scula (p. ej. should_reject_order_when_status_is_shipped). La frase legible va en @DisplayName. [testMethodName]
[ERROR] src\test\java\com\codeja\master_acdc\architecture\TableOwnershipTest.java:[58,8] (coding) MatchXpathCheck#testMethodName: Nombre de test: should_<comportamiento>_when_<condici�n> en snake_case min�scula (p. ej. should_reject_order_when_status_is_shipped). La frase legible va en @DisplayName.
[ERROR] src\test\java\com\codeja\master_acdc\architecture\TableOwnershipTest.java:[78,8] (coding) MatchXpathCheck#testMethodName: Nombre de test: should_<comportamiento>_when_<condici�n> en snake_case min�scula (p. ej. should_reject_order_when_status_is_shipped). La frase legible va en @DisplayName.
[ERROR] src\test\java\com\codeja\master_acdc\architecture\TableOwnershipTest.java:[108,8] (coding) MatchXpathCheck#testMethodName: Nombre de test: should_<comportamiento>_when_<condici�n> en snake_case min�scula (p. ej. should_reject_order_when_status_is_shipped). La frase legible va en @DisplayName.
[ERROR] BUILD FAILURE
AC/DC: la capa 1 falló. Corrige cada línea de arriba (archivo, línea, regla o patrón); no suprimas avisos ni toques config/, pom.xml o .mvn/.

Exit code: 1

---

## Assistant

Actualizados los cuatro artefactos del change `handle-stock-shortage-after-payment`: las 48 horas cuentan desde el aviso al cliente y, si este elige esperar, ese vencimiento ya no anula el pedido.

H10, H11 y el conflicto con el reembolso desde PAID siguen abiertos. No se crearon archivos ni se modificó código.

**Validación:** OpenSpec pasó en modo estricto. Verify falló en Checkstyle por tres nombres de test en el archivo protegido `TableOwnershipTest.java` (líneas 58, 78 y 108); no se comprobaron las fases posteriores.

El change queda listo para revisión; la implementación sigue bloqueada por las decisiones pendientes. Cuando estén resueltas, el siguiente paso es `/opsx-apply`.
