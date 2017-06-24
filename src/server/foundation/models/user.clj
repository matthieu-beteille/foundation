(ns foundation.models.user
  (:require [yesql.core :refer [defqueries]]
            [foundation.db :as db]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [clojure.data.json :as json]))

(comment
  (defqueries "foundation/models/user.sql" {:connection db/db-spec})

  (create-address!)

  (create-addr<! {:postcode "sw111pj"})

  ;; grapqhl playground

  (create-users!)

  (create-user<!
   {:username "pierre"
    :address 1
    :description "ma description"})

  (get-user-address {:id 1})

  (def schema
    '{:objects
      {:address {:fields {:id {:type Int}
                          :postcode {:type String}}}
       :user
       {:fields {:id {:type Int}
                 :username {:type String}
                 :address {:type :address
                           :resolve :get-user-address}
                 :description {:type String}}}}
      :queries
      {:user {:type (non-null :user)
              :args {:username {:type String}}
              :resolve :get-user}}})

  (defn get-house [context arguments value]
    value)

  (defn get-address [context arguments value]
    100)

  (defn get-user [context arguments value]
    (first (get-users-by-username arguments)))

  (get-user nil {:username "matthieu"} nil)

  (def test
    (-> schema
        (attach-resolvers {:get-user get-user
                           :get-user-address get-address})
        schema/compile))

  (def query "{
  user(username: \"pierre\") {
    username,
    description,
    address
  }
  }")

  (execute test query nil nil)
  )
