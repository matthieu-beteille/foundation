(ns foundation.server
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.util.response :refer [response]]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :refer [run-jetty]]
            [compojure.route :refer [resources]]
            [compojure.core :refer [routes GET]]
            [foundation.templates.index :as index]))

(def my-routes
  (routes
   (resources "/")
   (GET "/*" [] index/render)))

(defrecord Server [port is-dev-mode? database]
  Lifecycle
  (start [component]
    (log/info ";; Starting foundation server")
    (let [wrap-dependencies (fn [handler]
                              (fn [request]
                                (handler (assoc request
                                                :database database))))
          handler (-> my-routes
                      wrap-keyword-params
                      wrap-params
                      wrap-json-params
                      wrap-dependencies)
          server (run-jetty handler
                            {:port port
                             :join? false})]
      (assoc component :server server)))

  (stop [component]
    (log/info ";; Stopping foundation server")
    (.stop (:server component))
    (assoc component :server nil)))

(defn new-server [port is-dev-mode?]
  (map->Server {:port port
                :is-dev-mode? is-dev-mode?}))

