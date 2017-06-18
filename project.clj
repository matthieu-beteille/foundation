(defproject foundation "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [org.clojure/clojurescript "1.9.293"]
                 ;; server side dependencies
                 [com.stuartsierra/component "0.3.2"]
                 [ring.middleware.logger "0.5.0"]
                 [compojure "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.2.0"]
                 [environ "1.0.3"]
                 [ring "1.5.1"]
                 [hiccup "1.0.5"]
                 ;; client side dependencies
                 [reagent "0.6.0"] ;;:exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]
                 [re-frame "0.9.1"]
                 [cljs-exponent "0.1.6"]
                 [react-native-externs "0.0.2-SNAPSHOT"]]

  :main foundation.system

  :source-paths ["src/server"]

  :repl-options {:init-ns user}

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.4-7"]]

  :clean-targets ^{:protect false} ["target/" "main.js" "resources/public/js/compiled/"]

  :aliases {;;"figwheel" ["run" "-m" "user" "--figwheel"]
            "externs" ["do" "clean"
                       ["run" "-m" "externs"]]
            "rebuild-modules" ["run" "-m" "user" "--rebuild-modules"]
            "prod-build" ^{:doc "Recompile code with prod profile."}
            ["externs"
             ["with-profile" "prod" "cljsbuild" "once" "main"]]}

  :cljsbuild {:builds [{:id           "web"
                        :source-paths ["src/client/web" "src/client/shared"]
                        :figwheel     true
                        :compiler     {:output-to "resources/public/js/compiled/app.js"
                                       :output-dir "resources/public/js/compiled/out"
                                       :asset-path  "js/compiled/out"
                                       :main "foundation.app"
                                       :optimizations :none}}
                       {:id "main2"
                        :source-paths ["src/client/native" "src/client/shared" "env/dev"]
                        :figwheel     true
                        :compiler     {:output-to     "target/not-used.js"
                                       :main          "env.main"
                                       :output-dir    "target"
                                       :optimizations :none}}]}
  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.4-7"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.namespace "0.2.7"]]
                   :source-paths ["env/dev"]

                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
             :prod {:cljsbuild {:builds [{:id "main"
                                          :source-paths ["src/client/native" "env/prod"]
                                          :compiler     {:output-to     "main.js"
                                                         :main          "env.main"
                                                         :output-dir    "target"
                                                         :static-fns    true
                                                         :externs       ["js/externs.js"]
                                                         :parallel-build     true
                                                         :optimize-constants true
                                                         :optimizations :advanced
                                                         :closure-defines {"goog.DEBUG" false}}}]}}})
