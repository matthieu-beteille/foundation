(ns foundation.graphql-layer
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [foundation.graphql.lib :as lib]
            [foundation.graphql.data-layer :as data]
            [foundation.ressources.ressources :as ressources]
            [clojure.tools.logging :as log]))

(def db-spec {:dbtype "mysql"
              :dbname "foundation"
              :host "localhost"
              :user "root"
              :password "password007"})

(defrecord GraphqlLayer []
  Lifecycle
  (start [component]
    (log/info ";; Starting foundation graphql")
    (let [data-layer (data/new-mysql-data-layer db-spec)
          schema (lib/create-graphql data-layer ressources/schema)]
          (assoc component :schema schema
                           :data-layer data-layer
                           :spec db-spec)))

  (stop [component]
    (log/info ";; Stopping foundation graphql")
    (assoc component :schema nil
                     :data-layer nil
                     :spec nil)))

(defn new-graphql-layer []
  (map->GraphqlLayer {}))
