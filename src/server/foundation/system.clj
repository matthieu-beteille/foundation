(ns foundation.system
  (:require [com.stuartsierra.component :refer [system-map using start]]
            [foundation.server :refer [new-server]]
            [environ.core :refer [env]])
  (:gen-class))

(defn new-system
  [port is-dev-mode?]
  (system-map
   :app (new-server port is-dev-mode?)))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (start (new-system port false))))
