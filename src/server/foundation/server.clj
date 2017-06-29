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
            [compojure.core :refer [routes GET POST]]
            [foundation.common.auth.auth :as auth]
            [foundation.graphql.lib :as lib]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            [com.walmartlabs.lacinia :refer [execute]]
            [buddy.auth.backends :as backends]
            [foundation.templates.index :as index]))

(defn graphql-handler
  [request]
  (let [res (execute (:graphql-layer request) "{ user(username: \"juanito\"){ username } }" nil nil)]
     (json/write-str res)))

(def my-routes
  (routes
   (POST "/login" request (auth/do-login request))
   (GET "/graphql" request (graphql-handler request))))
   (GET "/*" [] index/render)

(defrecord Server [port is-dev-mode? graphql-layer]
  Lifecycle
  (start [component]
    (log/info ";; Starting foundation server")
    (let [wrap-dependencies (fn [handler]
                              (fn [request]
                                (handler (assoc request
                                                :schema (:schema graphql-layer)
                                                :data-layer (:data-layer graphql-layer)
                                                :database (:database graphql-layer)))))
          handler (-> my-routes
                      wrap-keyword-params
                      wrap-params
                      wrap-json-params
                      (wrap-json-body {:keywords? true})
                      wrap-dependencies
                      (wrap-authentication (backends/jws {:secret (env :jws-secret)})))
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

