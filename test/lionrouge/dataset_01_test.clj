(ns lionrouge.dataset-01-test
  (:require [clojure.test :refer [deftest is testing]]
            [tech.v3.dataset :as ds]))

(deftest dataset-load-test
  (testing "Can load orders.json into a dataset"
    (let [ds (ds/->dataset "data/orders.json")]
      (is (= 5 (ds/row-count ds)))
      (is (= 15 (ds/column-count ds))))))
