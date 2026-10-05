(ns reflechant.query
  (:require [clojure.string :as str]
            [tech.v3.dataset :as ds]))

(defn load-dataset
  [path]
  (ds/->>dataset path))

(defn missing?
  [v]
  (or (nil? v)
      (and (number? v) (Double/isNaN (double v)))))

(defn cell-str
  [v]
  (cond
    (nil? v) ""
    (or (map? v) (instance? java.util.Map v)
        (vector? v) (instance? java.util.List v)) (pr-str v)
    :else (str v)))

(defn- whitespace?
  [c]
  (or (= c \space) (= c \tab) (= c \newline) (= c \return)))

(defn- tokenize
  [^String s]
  (let [len (.length s)]
    (loop [i 0 tokens []]
      (if (>= i len)
        {:tokens tokens}
        (let [c (.charAt s i)]
          (cond
            (whitespace? c)
            (recur (inc i) tokens)

            (= c \")
            (let [res (loop [j (inc i) sb (StringBuilder.)]
                        (if (>= j len)
                          {:error "unclosed string"}
                          (let [cj (.charAt s j)]
                            (cond
                              (= cj \")
                              {:val (.toString sb) :next (inc j)}

                              (= cj \\)
                              (if (>= (inc j) len)
                                {:error "unclosed string"}
                                (let [esc (.charAt s (inc j))]
                                  (cond
                                    (= esc \\) (do (.append sb \\) (recur (+ j 2) sb))
                                    (= esc \") (do (.append sb \") (recur (+ j 2) sb))
                                    :else (do (.append sb \\) (.append sb esc) (recur (+ j 2) sb)))))

                              :else
                              (do (.append sb cj) (recur (inc j) sb))))))]
              (if (:error res)
                res
                (recur (:next res) (conj tokens {:type :string :val (:val res)}))))

            (= c \()
            (let [res (loop [j (inc i) items []]
                        (if (>= j len)
                          {:error "unclosed list"}
                          (let [cj (.charAt s j)]
                            (cond
                              (whitespace? cj)
                              (recur (inc j) items)

                              (= cj \()
                              {:error "unclosed list"}

                              (= cj \))
                              {:val items :next (inc j)}

                              (= cj \,)
                              (recur (inc j) items)

                              (= cj \")
                              (let [s-res (loop [k (inc j) sb (StringBuilder.)]
                                            (if (>= k len)
                                              {:error "unclosed string"}
                                              (let [ck (.charAt s k)]
                                                (cond
                                                  (= ck \")
                                                  {:val (.toString sb) :next (inc k)}

                                                  (= ck \\)
                                                  (if (>= (inc k) len)
                                                    {:error "unclosed string"}
                                                    (let [esc (.charAt s (inc k))]
                                                      (cond
                                                        (= esc \\) (do (.append sb \\) (recur (+ k 2) sb))
                                                        (= esc \") (do (.append sb \") (recur (+ k 2) sb))
                                                        :else (do (.append sb \\) (.append sb esc) (recur (+ k 2) sb)))))

                                                  :else
                                                  (do (.append sb ck) (recur (inc k) sb))))))]
                                (if (:error s-res)
                                  s-res
                                  (recur (:next s-res) (conj items {:type :string :val (:val s-res)}))))

                              :else
                              (let [end (loop [k (inc j)]
                                          (if (>= k len)
                                            k
                                            (let [ck (.charAt s k)]
                                              (if (or (whitespace? ck) (= ck \,) (= ck \)) (= ck \() (= ck \"))
                                                k
                                                (recur (inc k))))))
                                    tok (.substring s j end)]
                                (recur end (conj items {:type :bare :val tok})))))))]
              (if (:error res)
                res
                (recur (:next res) (conj tokens {:type :list :val (:val res)}))))

            (or (= c \,) (= c \)))
            (recur (inc i) (conj tokens {:type :bare :val (str c)}))

            :else
            (let [end (loop [j (inc i)]
                        (if (>= j len)
                          j
                          (let [cj (.charAt s j)]
                            (if (or (whitespace? cj) (= cj \,) (= cj \)))
                              j
                              (recur (inc j))))))
                  tok (.substring s i end)]
              (recur end (conj tokens {:type :bare :val tok})))))))))

(defn- coerce-single
  [tok]
  (case (:type tok)
    :string (:val tok)
    :bare (let [s ^String (:val tok)]
            (cond
              (.equalsIgnoreCase s "true") true
              (.equalsIgnoreCase s "false") false
              (re-matches #"-?\d+" s) (Long/parseLong s)
              (re-matches #"-?\d+\.\d+" s) (Double/parseDouble s)
              :else s))))

(defn parse-filter
  [s]
  (if (or (nil? s) (str/blank? s))
    {:filters []}
    (let [tok-res (tokenize s)]
      (if-let [err (:error tok-res)]
        {:error err}
        (let [tokens (:tokens tok-res)
              n (count tokens)
              ident-re #"[A-Za-z_][A-Za-z0-9_]*"
              valid-path? (fn [tok]
                            (when (= (:type tok) :bare)
                              (let [parts (str/split (:val tok) #"\." -1)]
                                (and (seq parts)
                                     (every? #(boolean (re-matches ident-re %)) parts)))))]
          (loop [idx 0 clauses []]
            (if (>= idx n)
              {:filters clauses}
              ;; 1. Column path
              (let [path-tok (nth tokens idx)]
                (if-not (valid-path? path-tok)
                  {:error "expected column"}
                  (let [path (vec (str/split (:val path-tok) #"\."))
                        idx (inc idx)]
                    ;; 2. Operator
                    (if (>= idx n)
                      {:error "expected operator"}
                      (let [op-tok (nth tokens idx)]
                        (if-not (= (:type op-tok) :bare)
                          {:error "expected operator"}
                          (let [op-str (:val op-tok)
                                op-lower (.toLowerCase ^String op-str java.util.Locale/ROOT)]
                            (cond
                              (contains? #{"=" "!=" ">" ">=" "<" "<="} op-str)
                              (let [op (case op-str
                                         "=" :eq
                                         "!=" :neq
                                         ">" :gt
                                         ">=" :gte
                                         "<" :lt
                                         "<=" :lte)
                                    idx (inc idx)]
                                ;; 3. Value
                                (if (>= idx n)
                                  {:error "expected value"}
                                  (let [val-tok (nth tokens idx)]
                                    (if (or (= (:type val-tok) :list)
                                            (and (= (:type val-tok) :bare)
                                                 (.equalsIgnoreCase ^String (:val val-tok) "and")))
                                      {:error "expected value"}
                                      (let [v (coerce-single val-tok)
                                            clause {:path path :op op :val v}
                                            idx (inc idx)]
                                        (if (>= idx n)
                                          {:filters (conj clauses clause)}
                                          (let [sep-tok (nth tokens idx)]
                                            (if (and (= (:type sep-tok) :bare)
                                                     (.equalsIgnoreCase ^String (:val sep-tok) "and"))
                                              (if (>= (inc idx) n)
                                                {:error "expected column"}
                                                (recur (inc idx) (conj clauses clause)))
                                              {:error "expected column"}))))))))

                              (contains? #{"contains" "starts-with"} op-lower)
                              (let [op (if (= op-lower "contains") :contains :starts-with)
                                    idx (inc idx)]
                                (if (>= idx n)
                                  {:error "expected value"}
                                  (let [val-tok (nth tokens idx)]
                                    (if (or (= (:type val-tok) :list)
                                            (and (= (:type val-tok) :bare)
                                                 (.equalsIgnoreCase ^String (:val val-tok) "and")))
                                      {:error "expected value"}
                                      (let [v (coerce-single val-tok)
                                            clause {:path path :op op :val v}
                                            idx (inc idx)]
                                        (if (>= idx n)
                                          {:filters (conj clauses clause)}
                                          (let [sep-tok (nth tokens idx)]
                                            (if (and (= (:type sep-tok) :bare)
                                                     (.equalsIgnoreCase ^String (:val sep-tok) "and"))
                                              (if (>= (inc idx) n)
                                                {:error "expected column"}
                                                (recur (inc idx) (conj clauses clause)))
                                              {:error "expected column"}))))))))

                              (= op-lower "in")
                              (let [idx (inc idx)]
                                (if (>= idx n)
                                  {:error "expected value"}
                                  (let [val-tok (nth tokens idx)]
                                    (if-not (= (:type val-tok) :list)
                                      {:error "expected value"}
                                      (let [v (mapv coerce-single (:val val-tok))
                                            clause {:path path :op :in :val v}
                                            idx (inc idx)]
                                        (if (>= idx n)
                                          {:filters (conj clauses clause)}
                                          (let [sep-tok (nth tokens idx)]
                                            (if (and (= (:type sep-tok) :bare)
                                                     (.equalsIgnoreCase ^String (:val sep-tok) "and"))
                                              (if (>= (inc idx) n)
                                                {:error "expected column"}
                                                (recur (inc idx) (conj clauses clause)))
                                              {:error "expected column"}))))))))

                              (= op-lower "is")
                              (let [idx (inc idx)]
                                (if (>= idx n)
                                  {:error "expected operator"}
                                  (let [nxt-tok (nth tokens idx)]
                                    (if-not (= (:type nxt-tok) :bare)
                                      {:error (str "unknown operator: " op-str)}
                                      (let [nxt-lower (.toLowerCase ^String (:val nxt-tok) java.util.Locale/ROOT)]
                                        (cond
                                          (= nxt-lower "null")
                                          (let [clause {:path path :op :is-null}
                                                idx (inc idx)]
                                            (if (>= idx n)
                                              {:filters (conj clauses clause)}
                                              (let [sep-tok (nth tokens idx)]
                                                (if (and (= (:type sep-tok) :bare)
                                                         (.equalsIgnoreCase ^String (:val sep-tok) "and"))
                                                  (if (>= (inc idx) n)
                                                    {:error "expected column"}
                                                    (recur (inc idx) (conj clauses clause)))
                                                  {:error "expected column"}))))

                                          (= nxt-lower "not")
                                          (let [idx (inc idx)]
                                            (if (>= idx n)
                                              {:error "expected operator"}
                                              (let [null-tok (nth tokens idx)]
                                                (if (and (= (:type null-tok) :bare)
                                                         (= (.toLowerCase ^String (:val null-tok) java.util.Locale/ROOT) "null"))
                                                  (let [clause {:path path :op :is-not-null}
                                                        idx (inc idx)]
                                                    (if (>= idx n)
                                                      {:filters (conj clauses clause)}
                                                      (let [sep-tok (nth tokens idx)]
                                                        (if (and (= (:type sep-tok) :bare)
                                                                 (.equalsIgnoreCase ^String (:val sep-tok) "and"))
                                                          (if (>= (inc idx) n)
                                                            {:error "expected column"}
                                                            (recur (inc idx) (conj clauses clause)))
                                                          {:error "expected column"}))))
                                                  {:error (str "unknown operator: " (:val null-tok))}))))

                                          :else
                                          {:error (str "unknown operator: " (:val nxt-tok))}))))))

                              :else
                              {:error (str "unknown operator: " op-str)})))))))))))))))

(defn- resolve-path
  [row path]
  (loop [curr (get row (first path))
         segs (rest path)]
    (if (empty? segs)
      curr
      (if (or (map? curr) (instance? java.util.Map curr))
        (recur (get curr (first segs)) (rest segs))
        nil))))

(defn- matches-str?
  [s val op]
  (let [sl (.toLowerCase ^String (cell-str s) java.util.Locale/ROOT)
        vl (.toLowerCase ^String (cell-str val) java.util.Locale/ROOT)]
    (case op
      :contains (.contains sl vl)
      :starts-with (.startsWith sl vl))))

(defn- cell-matches-eq?
  [cell val]
  (cond
    (or (boolean? cell) (boolean? val))
    (= cell val)

    (and (number? cell) (number? val))
    (== (double cell) (double val))

    :else
    (= (cell-str cell) (cell-str val))))

(defn- cmp-num
  [cell val op]
  (if (and (number? cell) (number? val) (not (missing? cell)) (not (missing? val)))
    (case op
      :gt (> (double cell) (double val))
      :gte (>= (double cell) (double val))
      :lt (< (double cell) (double val))
      :lte (<= (double cell) (double val)))
    false))

(defn- row-matches-clause?
  [row clause]
  (let [cell (resolve-path row (:path clause))
        op (:op clause)
        val (:val clause)]
    (case op
      :is-null (missing? cell)
      :is-not-null (not (missing? cell))
      :eq (cell-matches-eq? cell val)
      :neq (not (cell-matches-eq? cell val))
      (:gt :gte :lt :lte) (cmp-num cell val op)
      :contains (if (or (vector? cell) (instance? java.util.List cell))
                  (boolean (some #(matches-str? % val :contains) cell))
                  (matches-str? cell val :contains))
      :starts-with (if (or (vector? cell) (instance? java.util.List cell))
                     (boolean (some #(matches-str? % val :starts-with) cell))
                     (matches-str? cell val :starts-with))
      :in (boolean (some #(cell-matches-eq? cell %) val)))))

(defn apply-filters
  [ds filters]
  (try
    (let [cols (set (ds/column-names ds))]
      (if-let [bad-col (first (keep (fn [{:keys [path]}]
                                      (let [c (first path)]
                                        (when-not (contains? cols c)
                                          c)))
                                    filters))]
        {:error (str "unknown column: " bad-col)}
        {:ds (if (seq filters)
               (ds/filter ds (fn [row]
                               (every? #(row-matches-clause? row %) filters)))
               ds)}))
    (catch Exception e
      {:error (.getMessage e)})))

(defn sort-ds
  [ds sort-specs]
  (let [cols (set (ds/column-names ds))
        valid-specs (filterv #(contains? cols (:col %)) sort-specs)]
    (if (empty? valid-specs)
      ds
      (let [idx-col (str (gensym "row-idx"))
            ds-idx (ds/add-column ds (ds/new-column idx-col (range (ds/row-count ds))))
            comparator (reify java.util.Comparator
                         (compare [_ r1 r2]
                           (loop [specs valid-specs]
                             (if (seq specs)
                               (let [{:keys [col dir]} (first specs)
                                     v1 (get r1 col)
                                     v2 (get r2 col)
                                     m1 (missing? v1)
                                     m2 (missing? v2)
                                     cmp (cond
                                           (and m1 m2) 0
                                           m1 1
                                           m2 -1
                                           :else
                                           (let [c (cond
                                                     (and (number? v1) (number? v2))
                                                     (Double/compare (double v1) (double v2))
                                                     (and (string? v1) (string? v2))
                                                     (compare v1 v2)
                                                     :else
                                                     (compare (cell-str v1) (cell-str v2)))]
                                             (if (= dir :desc) (- c) c)))]
                                 (if (zero? cmp)
                                   (recur (rest specs))
                                   cmp))
                               (Long/compare (long (get r1 idx-col)) (long (get r2 idx-col)))))))]
        (ds/remove-column (ds/sort-by ds-idx identity comparator) idx-col)))))

(defn paginate-ds
  [ds offset limit]
  (let [n (ds/row-count ds)
        offset (max 0 (long offset))
        limit (max 1 (long limit))]
    (if (>= offset n)
      (ds/select-rows ds [])
      (ds/select-rows ds (range offset (min n (+ offset limit)))))))

(defn query-ds
  [ds query-map]
  (try
    (let [filters (get query-map :filters [])
          sort-specs (get query-map :sort [])
          req-offset (get query-map :offset 0)
          req-limit (get query-map :limit 50)
          offset (max 0 (long req-offset))
          limit (max 1 (long req-limit))
          cols (vec (ds/column-names ds))
          total-rows (ds/row-count ds)
          filter-res (apply-filters ds filters)]
      (if-let [err (:error filter-res)]
        {:rows []
         :columns cols
         :offset 0
         :limit limit
         :filtered-rows 0
         :total-rows total-rows
         :error err}
        (let [filtered-ds (:ds filter-res)
              filtered-cnt (ds/row-count filtered-ds)
              sorted-ds (sort-ds filtered-ds sort-specs)
              page-ds (paginate-ds sorted-ds offset limit)
              rows (vec (ds/rows page-ds {:copying? true :nil-missing? true}))]
          {:rows rows
           :columns cols
           :offset offset
           :limit limit
           :filtered-rows filtered-cnt
           :total-rows total-rows
           :error nil})))
    (catch Exception e
      {:rows []
       :columns (vec (ds/column-names ds))
       :offset 0
       :limit 50
       :filtered-rows 0
       :total-rows (ds/row-count ds)
       :error (.getMessage e)})))

(defn sort-label
  [sort-specs]
  (if (empty? sort-specs)
    "none"
    (str/join ", "
              (map (fn [{:keys [col dir]}]
                     (str col (if (= dir :desc) " ↓" " ↑")))
                   sort-specs))))

(comment
  (load-dataset "data/orders.json")
  (parse-filter "status = shipped AND customer.name contains mara")
  (query-ds (load-dataset "data/orders.json")
            {:filters [{:path ["status"] :op :eq :val "shipped"}]
             :sort [{:col "total" :dir :asc}]
             :offset 0
             :limit 2}))
