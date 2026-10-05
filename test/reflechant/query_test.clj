(ns reflechant.query-test
  (:require [clojure.test :refer [deftest is testing]]
            [reflechant.query :as q]
            [tech.v3.dataset :as ds]))

(deftest sort-composite-test
  (testing "Inline dataset sorted by a asc then b asc"
    (let [d (ds/->dataset [{"a" 1 "b" "b"} {"a" 1 "b" "a"} {"a" 2 "b" "a"}])
          sorted (q/sort-ds d [{:col "a" :dir :asc} {:col "b" :dir :asc}])
          rows (q/query-ds sorted {})
          pairs (mapv (juxt #(get % "a") #(get % "b")) (:rows rows))]
      (is (= [[1 "a"] [1 "b"] [2 "a"]] pairs)))))

(deftest sort-nulls-test
  (testing "Inline dataset sorted asc and desc on n with nils last"
    (let [d (ds/->dataset [{"n" "b"} {"n" nil} {"n" "a"}])
          s-asc (q/sort-ds d [{:col "n" :dir :asc}])
          s-desc (q/sort-ds d [{:col "n" :dir :desc}])]
      (is (= ["a" "b" nil] (mapv #(get % "n") (:rows (q/query-ds s-asc {})))))
      (is (= ["b" "a" nil] (mapv #(get % "n") (:rows (q/query-ds s-desc {}))))))))

(deftest filter-composite-and-nested-test
  (testing "status = shipped AND customer.name contains mara on orders -> 1 row ORD-18472"
    (let [orders (q/load-dataset "data/orders.json")
          parsed (q/parse-filter "status = shipped AND customer.name contains mara")
          res (q/query-ds orders {:filters (:filters parsed)})]
      (is (= 1 (:filtered-rows res)))
      (is (= 1 (count (:rows res))))
      (is (= "ORD-18472" (get (first (:rows res)) "id"))))))

(deftest filter-total-and-sort-test
  (testing "total >= 200 plus total desc -> totals 384.5 then 207.26"
    (let [orders (q/load-dataset "data/orders.json")
          parsed (q/parse-filter "total >= 200")
          res (q/query-ds orders {:filters (:filters parsed)
                                  :sort [{:col "total" :dir :desc}]})]
      (is (= 2 (:filtered-rows res)))
      (is (= [384.5 207.26] (mapv #(get % "total") (:rows res)))))))

(deftest filter-in-test
  (testing "channel in (web, store) -> 4 rows"
    (let [orders (q/load-dataset "data/orders.json")
          parsed (q/parse-filter "channel in (web, store)")
          res (q/query-ds orders {:filters (:filters parsed)})]
      (is (= 4 (:filtered-rows res)))
      (is (= 4 (count (:rows res)))))))

(deftest filter-is-null-test
  (testing "notes is null -> 1 row"
    (let [orders (q/load-dataset "data/orders.json")
          parsed (q/parse-filter "notes is null")
          res (q/query-ds orders {:filters (:filters parsed)})]
      (is (= 1 (:filtered-rows res)))
      (is (= 1 (count (:rows res)))))))

(deftest parse-filter-error-test
  (testing "(parse-filter \"status =\") -> {:error \"expected value\"}"
    (is (= {:error "expected value"} (q/parse-filter "status =")))))

(deftest unknown-column-error-test
  (testing "Filter {:path [\"nope\"] :op :eq :val \"x\"} -> error unknown column: nope"
    (let [orders (q/load-dataset "data/orders.json")
          res (q/query-ds orders {:filters [{:path ["nope"] :op :eq :val "x"}]})]
      (is (= "unknown column: nope" (:error res)))
      (is (= [] (:rows res)))
      (is (= 0 (:filtered-rows res)))
      (is (= 5 (:total-rows res))))))

(deftest pagination-test
  (testing "Offset 2 limit 2 on unfiltered orders -> 2 rows, :filtered-rows 5"
    (let [orders (q/load-dataset "data/orders.json")
          res (q/query-ds orders {:offset 2 :limit 2})]
      (is (= 2 (count (:rows res))))
      (is (= 5 (:filtered-rows res)))
      (is (= 5 (:total-rows res))))))
