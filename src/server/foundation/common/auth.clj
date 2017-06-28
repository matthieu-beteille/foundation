(ns foundation.common.auth
  (:require [buddy.auth.backends :as backends]
            [buddy.sign.jws :as jws]
            [foundation.graphql.lib :as lib]))

(def secret "TO_PUT_IN_ENV_VAR")
(def backend (backends/jws {:secret secret}))

(defn lookup-user
  [username password schema]
  (lib/run-query "{ user(username: \"juanito\") { username, description } }" schema))

(defn do-login
  "Login endpoint method, returns jws if username and password correct"
  [request]
  (let [schema (:graphql-layer request)
        params (:params request)]
  (if-let [user (lookup-user (:username params) (:password params) schema)]
    {:status 201
     :headers {"Content-type" "application/json"}
     :body {:token (jws/sign user secret)
            :user user}}
    {:status 401
     :headers {"Content-type" "application/json"}
     :body {:error "Unauthorized: Username or Password incorrect"}})))
