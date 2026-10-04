# Clojure & ClojureScript Interactive REPL Guidelines

## 1. REPL-First Workflow
Do not code file-first like in Python or Rust. Every change follows this feedback loop:
1. **Probe in REPL:** Evaluate sub-expressions, check data shapes, and test assumptions before touching source files.
2. **Graduate to file:** Move working logic into the namespace once verified in the runtime.
3. **Reload & Verify:** Reload the namespace in the REPL (`(require '[my.ns] :reload)` or `clojure_load_file`) and run tests directly in the REPL.

## 2. Tool Division: LSP vs. Calva vs. clojure-mcp
- **Static Code Intelligence & Linting (LSP / clj-kondo):**
  - **Linter check:** Run `lsp diagnostics` on changed files before yielding. Fix all `[clj-kondo]` errors and warnings (`unresolved-symbol`, `invalid-arity`, `unused-namespace`).
  - **Callsite discovery:** Always run `lsp references` before modifying or removing any exported function, component, or var.
  - **Cross-file rename:** Use `lsp rename` for semantic refactoring; never use find-and-replace across Clojure files.
  - **Definition lookup:** Use `lsp definition` to navigate directly to symbol declarations across the project and classpath.
- **Interactive REPL Evaluation (Calva vs. clojure-mcp):**
  - **If VS Code is open:** Prefer Calva (`clojure_evaluate_code`, `clojure_list_sessions`, `clojure_repl_output_log`) to stay in sync with the user's active editor buffers and browser runtime.
  - **If VS Code is closed (headless/terminal):** Calva is offline. Route all REPL evaluations to `clojure-mcp`'s `clojure_eval`.
- **AST Edits & Delimiter Safety (clojure-mcp / Calva):**
  - Never guess parens or use raw string replacement on nested forms.
  - Use `clojure_edit` / `clojure_edit_replace_sexp` (clojure-mcp) or `clojure_edit_files` (Calva) for form-level modifications.
  - If delimiters are unbalanced, run `paren_repair` or `clojure_balance_brackets` immediately.
- **Classpath & Dependency Source (clojure-mcp):**
  - Use `deps_list`, `deps_grep`, and `deps_read` to inspect library code and schemas inside dependency JARs.
- **Filesystem & Shell:**
  - Use native OMP tools (`read`, `edit`, `bash`) for general file inspection and git/build commands.
## 3. Rich Comment Blocks (`(comment ...)`)
- Keep exploratory, scratchpad, and example forms inside `(comment ...)` blocks at the bottom of the relevant file.
- Before implementing a function, write a representative invocation inside the `(comment ...)` block with sample input.
- **Never delete existing `(comment ...)` forms** unless explicitly told; they are documentation and interactive test harnesses for the human developer.

## 4. ClojureScript & Replicant Specifics
- **Target the CLJS runtime:** In ClojureScript, forms evaluated on the host JVM will fail on JS/DOM interop (`js/document`, `js/console`). Always verify the active session or specify the shadow-cljs port.
  - Calva: Set `replSessionKey` to the CLJS session (e.g. `"cljs"` or build name).
  - clojure-mcp: Use `list_nrepl_ports` to find the shadow-cljs port; pass `:port` to `clojure_eval`. If in shadow Clojure mode, jack into the build: `(shadow/repl :build-id)`.
- **Replicant / Hiccup DOM:**
  - Replicant functions return pure data (nested vectors, maps, keywords). Inspect render output directly in REPL—no headless browser required to verify DOM data structure correctness.
  - Keep state management pure and inspect atoms/watches directly with `@app-state`.

## 5. Idiomatic Simplicity & Anti-Defensive Philosophy
- **No Needless Defensive Code:** Never introduce ceremonial validation (e.g., pre-validating file paths/existence or checking types) when the underlying platform/runtime naturally throws standard errors (like `FileNotFoundException`). Let standard operations fail fast and idiomatic.
- **Flat Structure:** Avoid deep or premature nested directory hierarchies for small modules. Keep namespaces flat.
- **Zero Ceremony / Pure Data:** Strictly adhere to Clojure's data-driven philosophy. Work with plain immutable data (maps, vectors, sets, keywords) and pure functions. Never introduce DTOs, adapters, wrappers, or OOP/DDD abstraction layers.
