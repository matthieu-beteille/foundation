(ns foundation.models.user
  (:require [yesql.core :refer [defqueries]]
            [foundation.db :as db]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [clojure.data.json :as json]))

(defqueries "foundation/models/user.sql" {:connection db/db-spec})

;; grapqhl playground

(create-users!)

(create-user<! {:username "pierre" :description "ma description"})

(def schema
  '{:objects
    {:user
     {:fields {:id {:type Int}
               :username {:type String}
               :description {:type String}}}}
    :queries
    {:user {:type (non-null :user)
            :args {:username {:type String}}
            :resolve :get-user}}})

(defn get-user [context arguments value]
  (first (get-users-by-username arguments)))

(get-user nil {:username "matthieu"} nil)

(def test
  (-> schema
      (attach-resolvers {:get-user get-user})
      schema/compile))

(def query "{
  user(username: \"matthieu\") {
    username,
    description
  }
}")

(execute test query nil nil)
