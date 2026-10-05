(ns reflechant.dataset-01
  (:gen-class)
  (:require [tech.v3.dataset :as ds]))

(defn -main
  [& [path]]
  (println (ds/->dataset path)))

(comment
  (ds/->>dataset "data/orders.json")
  (ds/->dataset "data/orders.json")
  (ds/row-count (ds/->dataset "data/orders.json")))


; (clojure.repl.deps/sync-deps)
