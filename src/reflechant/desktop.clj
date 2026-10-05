(ns reflechant.desktop
  (:gen-class)
  (:require [clojure.string :as str]
            [reflechant.query :as q]
            [reflechant.ui :as ui]))

(def usage
  "dataset-01 [options] [file]

  file          JSON array loaded with ->>dataset. Omit for an empty dataset.
  -p, --print   Print the dataset and exit.
  -h, --help    Show this help.

Opens a JavaFX table. Headless runs and --print print the dataset.
Linux JVM:  clojure -M:desktop
macOS JVM:  clojure -M:desktop:jfx-mac-aarch64
            clojure -M:desktop:jfx-mac
Windows JVM: clojure -M:desktop:jfx-win")

(defn- parse-args
  [args]
  (loop [remaining args
         help? false
         print? false
         path nil]
    (if (empty? remaining)
      {:help? help? :print? print? :path path}
      (let [tok (first remaining)]
        (cond
          (or (= tok "-h") (= tok "--help"))
          (recur (rest remaining) true print? path)

          (or (= tok "-p") (= tok "--print"))
          (recur (rest remaining) help? true path)

          (str/starts-with? tok "-")
          {:error-type :unknown-option :token tok}

          (nil? path)
          (recur (rest remaining) help? print? tok)

          :else
          {:error-type :unexpected-arg :token tok})))))

(defn -main
  [& args]
  (let [parsed (parse-args args)]
    (cond
      (= (:error-type parsed) :unknown-option)
      (binding [*out* *err*]
        (println (str "dataset-01: unknown option: " (:token parsed)))
        (println usage)
        (System/exit 1))

      (= (:error-type parsed) :unexpected-arg)
      (binding [*out* *err*]
        (println (str "dataset-01: unexpected argument: " (:token parsed)))
        (println usage)
        (System/exit 1))

      (:help? parsed)
      (do
        (println usage)
        nil)

      :else
      (let [ds (q/load-dataset (:path parsed))]
        (if (or (:print? parsed)
                (java.awt.GraphicsEnvironment/isHeadless))
          (do
            (println ds)
            nil)
          (ui/start! ds (:path parsed)))))))
