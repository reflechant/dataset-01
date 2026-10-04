# Inviolable Clojure & ClojureScript Rules

1. **REPL-First, Always:**
   - NEVER approach Clojure like Rust, Python, or TypeScript. Do not edit source files blindly and rely on shell test-runner failures to debug.
   - You MUST probe, evaluate, and verify expressions interactively in the REPL (`clojure_eval` or `clojure_evaluate_code`) BEFORE committing them to a namespace.
   - For ClojureScript / Replicant, inspect the rendered pure data (Hiccup vectors, component maps) in the REPL.

2. **Zero Shell File Hacks:**
   - NEVER use `sed`, `awk`, `perl`, `python`, or regex shell commands to edit Clojure files (`.clj`, `.cljs`, `.cljc`, `.edn`).
   - Use AST-aware structural editing tools (`clojure_edit`, `clojure_edit_replace_sexp`, `clojure_edit_files`) or repair tools (`paren_repair`, `clojure_balance_brackets`).

3. **Preserve Rich Comments:**
   - NEVER remove or overwrite `(comment ...)` blocks. They are live documentation and interactive testbeds. Append your exploratory REPL forms into the namespace's `(comment ...)` block.

4. **Static Verification Gate:**
   - Before completing any task, run `lsp diagnostics` on all modified files. You MUST resolve all `[clj-kondo]` errors and warnings (`unresolved-symbol`, `invalid-arity`, `unused-namespace`).

5. **Idiomatic Simplicity & Anti-Defensive Philosophy:**
   - NEVER introduce ceremonial validation (e.g. pre-validating file existence, paths, or types that the underlying runtime naturally reports with standard exceptions like `FileNotFoundException`). Let standard operations fail fast and idiomatic.
   - Zero Ceremony / Pure Data: Work with plain immutable Clojure data (maps, vectors, keywords) and pure functions. Never introduce DTOs, adapters, wrappers, or OOP/DDD layers.
