(ns foundation.common.auth.auth
  (:require [environ.core :refer [env]]
            [foundation.graphql.lib :as lib]
            [foundation.utils :as utils]))

(defn lookup-user
  [username password schema]
  (let [user {:username "SDFS" :password "sdfasf"}]
    user))

(defn do-login
  "Login endpoint method, returns jws if username and password correct"
  [request]
  (let [schema (:schema request)
        params (:params request)]
  (if-let [user (lookup-user (:username params) (:password params) schema)]
    {:status 201
     :headers {"Content-type" "application/json"}
     :body {:token (utils/generate-token user (env :jws-secret))
            :user user}}
    {:status 401
     :headers {"Content-type" "application/json"}
     :body {:error "Unauthorized: Username or Password incorrect"}})))
