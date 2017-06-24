(ns foundation.db
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [clojure.tools.logging :as log]))

(def db-spec {:dbtype "mysql"
              :dbname "foundation"
              :host "localhost"
              :user "root"
              :password "password007"})

(defrecord Database []
  Lifecycle
  (start [component]
    (log/info ";; Starting foundation db")
    (assoc component :spec db-spec))

  (stop [component]
    (log/info ";; Stopping foundation db")
    (assoc component :spec nil)))

(defn new-database []
  (map->Database {}))

(defprotocol DataLayer
  "A simple protocol describing foundation's data layer"
  (init! [this entity-name schema]
    "effectful method to initialise things related to an entity (ex: create tables)")
  (query-nested
    [this entity-name linked-entity ctx params value]
    "method to query a one-to-one linked entity")
  (query-parent
    [this entity-name linked-entity ctx params value]
    "method to query a one-to-one parent entity"))
