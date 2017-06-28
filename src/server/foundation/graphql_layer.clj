(ns foundation.graphql-layer
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [foundation.db :as db]
            [foundation.graphql.lib :as lib]
            [foundation.graphql.data-layer :as data]
            [foundation.ressources.ressources :as ressources]
            [clojure.tools.logging :as log]))

(defn build-schema
  []
  (let [data-layer (data/new-mysql-data-layer db/db-spec)]
    (lib/create-graphql data-layer ressources/schema)))

(defrecord GraphqlLayer []
  Lifecycle
  (start [component]
    (log/info ";; Starting foundation graphql")
    (assoc component :schema (build-schema)))

  (stop [component]
    (log/info ";; Stopping foundation graphql")
    (assoc component :schema nil)))

(defn new-graphql-layer []
  (map->GraphqlLayer {}))
