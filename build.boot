(set-env!
 :source-paths  #{"src/server" "env/dev"}
 :resource-paths #{"resources"}
 :dependencies '[[ajchemist/boot-figwheel "0.5.4-6" :scope "test"] ;; latest release
                 [org.clojure/tools.nrepl "0.2.12" :scope "test"]
                 [com.cemerick/piggieback "0.2.1" :scope "test"]
                 [figwheel-sidecar "0.5.4-7" :scope "test"]
                 [react-native-externs "0.0.2-SNAPSHOT" :scope "test"]
                 ;; clojure/clojurescript
                 [org.clojure/clojure "1.9.0-alpha10"]
                 [org.clojure/clojurescript "1.9.293"]
                 ;; server side deps
                 [com.stuartsierra/component "0.3.2"]
                 [ring.middleware.logger "0.5.0"]
                 [compojure "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.2.0"]
                 [environ "1.0.3"]
                 [ring "1.5.1"]
                 [hiccup "1.0.5"]
                 ;; client side deps
                 [reagent "0.6.0"   :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]]
                 [re-frame "0.9.1"]
                 [cljs-exponent "0.1.6"]])

(require
 '[boot-figwheel :refer [figwheel cljs-repl]]
 '[cljs.build.api :as b]
 '[user :as user]
 '[externs :as externs])

(require 'boot.repl)
(swap! boot.repl/*default-middleware*
       conj 'cemerick.piggieback/wrap-cljs-repl)

(deftask dev-native
  "boot dev, then input (cljs-repl)"
  []
  (set-env! #(conj % "src/client/native"))
  (user/prepare)
  (comp
   (figwheel
    :build-ids  ["main"]
    :all-builds [{:id "main"
                  :source-paths ["src/client/native" "src/client/shared" "env/dev"]
                  :figwheel true
                  :compiler     {:output-to     "not-used.js"
                                 :main          "env.main"
                                 :optimizations :none
                                 :output-dir    "."}}]
    :figwheel-options {:open-file-command "emacsclient"
                       :validate-config false})
   (repl)))

(deftask web-dev
  []
  (println "boot web-dev")
  ; add react dependencies (not required for native, coming from npm)
  (set-env! :dependencies #(concat % '[[cljsjs/react "15.6.1-0"]
                                       [cljsjs/react-dom "15.6.1-0"]
                                       [cljsjs/react-dom-server "15.6.1-0"]]))
  (comp
   (figwheel
    :build-ids  ["web"]
    :target-path "resources"
    :all-builds [{:id           "web"
                  :source-paths ["src/client/web" "src/client/shared"]
                  :figwheel     true
                  :compiler     {:output-to "public/js/compiled/app.js"
                                 :output-dir "."
                                 :asset-path  "js/compiled/"
                                 :main "foundation.app"
                                 :optimizations :none}}]
    :figwheel-options {:open-file-command "emacsclient"
                       :validate-config false})
   (repl)))

(deftask prod
  []
  (set-env! #(conj % "src/client/native"))
  (externs/-main)
  (println "Start to compile clojurescript ...")
  (let [start (System/nanoTime)]
    (b/build ["src/client/native" "env/prod"]
             {:output-to     "main.js"
              :main          "env.main"
              :output-dir    "target"
              :static-fns    true
              :externs       ["js/externs.js"]
              :parallel-build     true
              :optimize-constants true
              :optimizations :advanced
              :closure-defines {"goog.DEBUG" false}})
    (println "... done. Elapsed" (/ (- (System/nanoTime) start) 1e9) "seconds")))
