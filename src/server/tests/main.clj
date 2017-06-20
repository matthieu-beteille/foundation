(ns foundation.tests.main
  (:require  [clojure.test :as t]
             [foundation.utils.lacinia :as utils]))

(def user
  '{:id {:type Int
         :q true}
    :username {:type String
               :q true}
    :description {:type String}})

(t/deftest "gen-query-handler: sql query generator."
  (t/is (utils/gen-query-handler :fruit
                                 {:name "apple"})
        "select * from fruit where name = \"apple\"")
  (t/is (utils/gen-query-handler :user
                                 {:description "description123"
                                  :id 123456})
        "select * from user where description = \"description123\" and id = 123456"))
