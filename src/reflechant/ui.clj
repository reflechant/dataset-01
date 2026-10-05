(ns reflechant.ui
  (:require [reflechant.query :as q]
            [tech.v3.dataset :as ds]))

(defonce ^:private current-state-atom (atom nil))
(defonce ^:private renderer-atom (atom nil))
(defonce ^:private close-latch-atom (atom nil))
(defonce ^:private sorting? (volatile! false))

(defn sort-policy
  [table]
  (if @sorting?
    true
    (do
      (vreset! sorting? true)
      (try
        (let [sort-order (vec (.getSortOrder table))
              specs (mapv (fn [col]
                            (let [col-text (.getText col)
                                  st (str (.getSortType col))
                                  dir (if (.contains st "DESCENDING") :desc :asc)]
                              {:col col-text :dir dir}))
                          sort-order)]
          (when-let [a @current-state-atom]
            (swap! a assoc :sort specs :page 0 :selected nil))
          true)
        (finally
          (vreset! sorting? false))))))

(defn prepare-state
  [state]
  (let [ds (:ds state)
        filters (get state :filters [])
        page-size (or (:page-size state) 50)
        filter-res (q/apply-filters ds filters)
        filtered-rows (if-let [filtered-ds (:ds filter-res)]
                        (ds/row-count filtered-ds)
                        0)
        pages (if (pos? filtered-rows)
                (max 1 (long (Math/ceil (/ (double filtered-rows) (double page-size)))))
                1)
        clamped-page (min (max 0 (long (or (:page state) 0))) (max 0 (dec pages)))
        offset (* clamped-page page-size)
        query (q/query-ds ds {:filters filters
                              :sort (get state :sort [])
                              :offset offset
                              :limit page-size})]
    (assoc state
           :page clamped-page
           :pages pages
           :query query)))

(defn view-desc
  [state]
  (let [path (:path state)
        title (if (nil? path)
                "dataset-01"
                (str "dataset-01 \u2014 " path))
        filter-err (:filter-error state)
        query (:query state)
        cols (get query :columns [])
        rows (get query :rows [])
        filtered-rows (get query :filtered-rows 0)
        total-rows (get query :total-rows 0)
        pages (or (:pages state) 1)
        page (or (:page state) 0)
        page-size (or (:page-size state) 50)
        sort-specs (or (:sort state) [])
        selection-wrapper (if-let [v (try (requiring-resolve 'cljfx.ext.table-view/with-selection-props)
                                          (catch Throwable _ nil))]
                            @v
                            :cljfx.ext.table-view/with-selection-props)]
    {:fx/type :stage
     :title title
     :showing true
     :width 1100
     :height 720
     :on-close-request {:event/type ::close}
     :scene {:fx/type :scene
             :root {:fx/type :v-box
                    :spacing 8
                    :padding 8
                    :children
                    [{:fx/type :h-box
                      :spacing 8
                      :children
                      (cond-> [{:fx/type :text-field
                                :prompt-text "status = shipped AND total >= 100"
                                :text (or (:filter-text state) "")
                                :h-box/hgrow :always
                                :on-text-changed {:event/type ::filter-text-changed}
                                :on-action {:event/type ::apply-filter}}
                               {:fx/type :button
                                :text "Apply"
                                :on-action {:event/type ::apply-filter}}]
                        filter-err
                        (conj {:fx/type :label
                               :text filter-err
                               :text-fill "#b00020"})
                        (not filter-err)
                        (conj {:fx/type :label
                               :text ""}))}
                     {:fx/type selection-wrapper
                      :v-box/vgrow :always
                      :props {:selection-mode :single
                              :selected-item (:selected state)
                              :on-selected-item-changed {:event/type ::select-row}}
                      :desc {:fx/type :table-view
                             :placeholder {:fx/type :label :text "no rows"}
                             :sort-policy #'reflechant.ui/sort-policy
                             :columns (mapv (fn [col-name]
                                              {:fx/type :table-column
                                               :text col-name
                                               :pref-width 140
                                               :cell-value-factory (fn [row] (get row col-name))
                                               :cell-factory {:fx/cell-type :table-cell
                                                              :describe (fn [v] {:text (q/cell-str v)})}})
                                            cols)
                             :items rows}}
                     {:fx/type :h-box
                      :spacing 8
                      :children
                      [{:fx/type :label
                        :text (str "rows " filtered-rows "/" total-rows)}
                       {:fx/type :button
                        :text "Prev"
                        :on-action {:event/type ::prev-page}}
                       {:fx/type :label
                        :text (str "page " (inc page) "/" pages)}
                       {:fx/type :button
                        :text "Next"
                        :on-action {:event/type ::next-page}}
                       {:fx/type :label
                        :text "page size"}
                       {:fx/type :combo-box
                        :items [25 50 100 500]
                        :value page-size
                        :on-value-changed {:event/type ::change-page-size}}
                       {:fx/type :label
                        :text (str "sort " (q/sort-label sort-specs))}]}]}}}))

(defn- make-event-handler
  [state-atom close-latch]
  (fn [event]
    (case (:event/type event)
      ::close
      (do
        (when-let [exit-platform (try (requiring-resolve 'cljfx.api/on-fx-thread)
                                      (catch Throwable _ nil))]
          (exit-platform (fn []
                           (try
                             (let [p-cls (Class/forName "javafx.application.Platform")
                                   m (.getMethod p-cls "exit" (into-array Class []))]
                               (.invoke m nil (into-array Object [])))
                             (catch Throwable _ nil)))))
        (when-let [r @renderer-atom]
          (r)
          (reset! renderer-atom nil))
        (when close-latch
          (.countDown ^java.util.concurrent.CountDownLatch close-latch)))

      ::filter-text-changed
      (let [txt (or (:text event) (:fx/event event))]
        (swap! state-atom assoc :filter-text txt))

      ::apply-filter
      (let [{:keys [ds filter-text]} @state-atom
            parsed (q/parse-filter filter-text)]
        (if-let [err (:error parsed)]
          (swap! state-atom assoc :filter-error err)
          (let [filtered (q/apply-filters ds (:filters parsed))]
            (if-let [err (:error filtered)]
              (swap! state-atom assoc :filter-error err)
              (swap! state-atom assoc :filters (:filters parsed)
                                      :filter-text filter-text
                                      :filter-error nil
                                      :page 0
                                      :selected nil)))))

      ::select-row
      (let [row (or (:row event) (:fx/event event))]
        (swap! state-atom assoc :selected row))

      ::prev-page
      (swap! state-atom update :page (fn [p] (if (pos? (or p 0)) (dec p) 0)))

      ::next-page
      (swap! state-atom update :page (fn [p]
                                       (let [pages (:pages @state-atom 1)]
                                         (if (< (or p 0) (dec pages))
                                           (inc (or p 0))
                                           (or p 0)))))

      ::change-page-size
      (let [sz (or (:page-size event) (:fx/event event))]
        (swap! state-atom assoc :page-size sz :page 0 :selected nil))

      nil)))

(defn start!
  [ds path]
  ;; If JavaFX classes are missing, this will throw
  (Class/forName "javafx.application.Platform")
  (let [initial-state {:ds ds
                       :path path
                       :filters []
                       :filter-text ""
                       :filter-error nil
                       :sort []
                       :page 0
                       :page-size 50
                       :selected nil}
        state-atom (atom initial-state)
        close-latch (java.util.concurrent.CountDownLatch. 1)
        event-handler (make-event-handler state-atom close-latch)
        create-renderer (requiring-resolve 'cljfx.api/create-renderer)
        wrap-map-desc (requiring-resolve 'cljfx.api/wrap-map-desc)
        mount-renderer (requiring-resolve 'cljfx.api/mount-renderer)
        custom-type->lifecycle (eval '(do
                                        (require (quote [cljfx.composite :as comp])
                                                 (quote [cljfx.lifecycle :as lc])
                                                 (quote [cljfx.coerce :as coerce])
                                                 (quote [cljfx.fx.table-view :as tv])
                                                 (quote [cljfx.api :as fx]))
                                        (fn [type]
                                          (if (= type :table-view)
                                            (comp/describe javafx.scene.control.TableView
                                              :ctor []
                                              :props (merge tv/props
                                                            (comp/props javafx.scene.control.TableView
                                                              :sort-policy
                                                              [:setter lc/scalar
                                                               :coerce (fn [x]
                                                                         (cond
                                                                           (instance? javafx.util.Callback x) x
                                                                           (or (var? x) (fn? x))
                                                                           (reify javafx.util.Callback
                                                                             (call [_ table] (x table)))
                                                                           (= :default x) javafx.scene.control.TableView/DEFAULT_SORT_POLICY
                                                                           :else (coerce/fail javafx.util.Callback x)))
                                                               :default :default])))
                                            (fx/keyword->lifecycle type)))))
        renderer (create-renderer
                  :middleware (wrap-map-desc (fn [state]
                                               (view-desc (prepare-state state))))
                  :opts {:fx.opt/map-event-handler event-handler
                         :fx.opt/type->lifecycle custom-type->lifecycle})]
    (reset! current-state-atom state-atom)
    (reset! renderer-atom renderer)
    (reset! close-latch-atom close-latch)
    (mount-renderer state-atom renderer)
    (.await close-latch)
    nil))
