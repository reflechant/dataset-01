---
description: Never edit Clojure or ClojureScript files with sed, awk, or shell scripts. Use structural editing or paren repair tools instead.
condition:
  - '\b(sed|awk|perl)\b.*(\.clj|\.cljs|\.cljc|\.edn)'
scope:
  - tool:bash
interruptMode: always
---

STOP! Never use sed, awk, perl, or ad-hoc shell commands to modify Clojure or ClojureScript source files!
Lisp syntax has strict tree structure and nested delimiters. Shell text hacking will corrupt brackets and break the AST.

Use the proper Clojure structural editing tools:
- `clojure_edit` / `clojure_edit_replace_sexp` (clojure-mcp)
- `clojure_edit_files` / `clojure_balance_brackets` (calva)
- `paren_repair` (clojure-mcp)
