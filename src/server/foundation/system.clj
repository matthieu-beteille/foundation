(ns foundation.system
  (:require [com.stuartsierra.component :refer [using system-map using start]]
            [foundation.server :as server]
            [foundation.db :as db]
            [foundation.graphql-layer :as graphql-layer]
            [environ.core :refer [env]])
  (:gen-class))

(defn new-system
  [port is-dev-mode?]
  (system-map
   :database (db/new-database)
   :graphql-layer (graphql-layer/new-graphql-layer)
   :app (using
         (server/new-server port is-dev-mode?)
         [:database :graphql-layer])))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (start (new-system port false))))
