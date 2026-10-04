(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib 'lionrouge/dataset-01)
(def version "0.1.0-SNAPSHOT")
(def main 'lionrouge.dataset-01)
(def class-dir "target/classes")

(defn- exec [cmds error-msg]
  (let [{:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info error-msg {})))))

(defn clean "Clean the target directory." [opts]
  (println "\nCleaning target...")
  (b/delete {:path "target"})
  opts)

(defn test "Run all the tests." [opts]
  (println "\nRunning tests...")
  (let [basis    (b/create-basis {:aliases [:test]})
        cmds     (b/java-command
                  {:basis     basis
                   :main      'clojure.main
                   :main-args ["-m" "cognitect.test-runner"]})]
    (exec cmds "Tests failed"))
  opts)

(defn- uber-opts [opts]
  (assoc opts
         :lib lib :main main
         :uber-file (format "target/%s-%s.jar" lib version)
         :basis (b/create-basis {})
         :class-dir class-dir
         :src-dirs ["src"]
         :ns-compile [main]))

(defn build-uber "Build the uberjar." [opts]
  (clean opts)
  (let [opts (uber-opts opts)]
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources"] :target-dir class-dir})
    (println (str "\nCompiling " main "..."))
    (b/compile-clj opts)
    (println "\nBuilding JAR..." (:uber-file opts))
    (b/uber opts)
    opts))

(defn ci "Run the CI pipeline of tests (and build the uberjar)." [opts]
  (test opts)
  (build-uber opts)
  opts)

(defn native "Build a standalone native binary using GraalVM locally." [opts]
  (let [opts (build-uber opts)]
    (println "\nBuilding native image...")
    (exec {:command-args ["native-image"
                          "--initialize-at-build-time"
                          "-jar" (:uber-file opts)
                          "-H:Name=target/dataset-01"
                          "--no-fallback"]}
          "Native image build failed")
    opts))

(defn native-linux "Build a Linux standalone native binary using GraalVM in Docker." [opts]
  (let [opts (build-uber opts)]
    (println "\nBuilding Linux native image via Docker...")
    (exec {:command-args ["docker" "run" "--rm"
                          "-v" (str (System/getProperty "user.dir") ":/app")
                          "-w" "/app"
                          "ghcr.io/graalvm/native-image-community:21"
                          "native-image"
                          "--initialize-at-build-time"
                          "-jar" (:uber-file opts)
                          "-H:Name=target/dataset-01-linux"
                          "--no-fallback"]}
          "Linux native image build failed")
    opts))

(defn- build-container [opts container-file image-tag]
  (println (str "\nBuilding " image-tag " container..."))
  (exec {:command-args ["docker" "build"
                        "-f" container-file
                        "--build-arg" (str "UBERJAR_PATH=" (:uber-file opts))
                        "-t" image-tag
                        "."]}
        (str image-tag " container build failed")))

(defn container-scratch "Builds a minimal scratch container image." [opts]
  (let [opts (build-uber opts)]
    (build-container opts "Containerfile.scratch" "dataset-01-scratch")
    opts))

(defn container-distroless "Builds a minimal distroless container image." [opts]
  (let [opts (build-uber opts)]
    (build-container opts "Containerfile.distroless" "dataset-01-distroless")
    opts))
