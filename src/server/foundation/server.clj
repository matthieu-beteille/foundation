(ns foundation.server
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.util.response :refer [response]]
            [buddy.auth.middleware :refer (wrap-authentication)]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :refer [run-jetty]]
            [compojure.route :refer [resources]]
            [compojure.core :refer [routes GET]]
            [foundation.common.auth :as auth]
            [foundation.graphql.lib :as lib]
            [clojure.data.json :as json]
            [foundation.templates.index :as index]))

(defn graphql-handler
  [request]
  (let [suce (println "SDFSD" request)]
      (lib/run-query "{ user(username: \"juanito\") { username, description } }" (:graphql-layer request))))

(def my-routes
  (routes
   ; (resources "/")
   (GET "/suce" [] "<h1>Hello World</h1>")
   (GET "/graphql" request (graphql-handler request))
   ; (GET "/*" [] index/render)
   (GET "/lol" [] (json/write-str {:a 1 :b 4}))))

(defrecord Server [port is-dev-mode? database graphql-layer]
  Lifecycle
  (start [component]
    (log/info ";; Starting foundation server")
    (let [wrap-dependencies (fn [handler]
                              (fn [request]
                                (handler (assoc request
                                                :database database
                                                :graphql-layer graphql-layer))))
          handler (-> my-routes
                      wrap-keyword-params
                      wrap-params
                      wrap-json-params
                      (wrap-json-body {:keywords? true})
                      wrap-dependencies
                      (wrap-authentication auth/backend))
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

