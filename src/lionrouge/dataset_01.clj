(ns lionrouge.dataset-01
  (:gen-class)
  (:require [tech.v3.dataset :as ds]))

(defn -main
  [& [path]]
  (println (ds/->dataset path)))

(comment
  (ds/->>dataset "data/orders.json")
  )


; (clojure.repl.deps/sync-deps)