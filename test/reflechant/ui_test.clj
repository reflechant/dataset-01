(ns reflechant.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [reflechant.ui :as ui]))

(defn- find-node
  [pred tree]
  (first (filter pred (tree-seq coll? seq tree))))

(deftest view-desc-shape-test
  (testing "view-desc returns cljfx description with table-view and does not load JavaFX"
    (let [state {:path "data/orders.json"
                 :query {:rows [{"id" "ORD-18472" "status" "shipped"}
                                {"id" "ORD-18473" "status" "processing"}]
                         :columns ["id" "status" "total"]
                         :filtered-rows 2
                         :total-rows 5
                         :offset 0
                         :limit 50
                         :error nil}
                 :pages 1
                 :page 0
                 :page-size 50
                 :sort []
                 :filters []
                 :filter-text ""
                 :filter-error nil
                 :selected nil}
          desc (ui/view-desc state)
          table-node (find-node #(and (map? %) (= (:fx/type %) :table-view)) desc)]
      ;; The returned map contains a :table-view node
      (is (some? table-node))
      ;; a column whose :text is "status"
      (is (some #(= (:text %) "status") (:columns table-node)))
      ;; and two items
      (is (= 2 (count (:items table-node))))
      ;; No JavaFX classes are loaded
      (is (nil? (resolve 'javafx.scene.control.TableView))))))
