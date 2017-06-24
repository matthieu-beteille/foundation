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
