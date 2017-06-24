(ns foundation.tests.main
  (:require  [clojure.test :refer [deftest is testing]]
             [foundation.utils :as utils]
             [foundation.db :as db]
             [clojure.java.jdbc :as j]
             [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
             [com.walmartlabs.lacinia :refer [execute]]
             [com.walmartlabs.lacinia.schema :as schema]))

(comment (deftest gen-query-hander
           (testing "gen-query-handler: sql query generator."
             (is (= (utils/gen-query-handler :fruit
                                             {:name "apple"})
                    "select * from fruit where name = \"apple\""))
             (is (= (utils/gen-query-handler :user
                                             {:description "description123"
                                              :id 123456})
                    "select * from user where description = \"description123\" and id = 123456")))))

(try
  (j/db-do-commands db/db-spec
                    (j/drop-table-ddl :user
                                      :address))
  (catch Exception e (str "caught exception: " (.getMessage e))))

(def address (utils/gen-lacinia-sch :address
                                    '{:id {:type ID
                                           :q true}
                                      :user {:type :user
                                             :relation :belongs-to}
                                      :postcode {:type String}
                                      :line1 {:type String}
                                      :line2 {:type String}
                                      :city {:type String}}))

((get-in address [:utils :create-table]) db/db-spec)

(def user (utils/gen-lacinia-sch :user
                                 '{:id {:type ID
                                        :q true}
                                   :username {:type String
                                              :q true}
                                   :description {:type String}
                                   :address {:type :address
                                             :relation :has-one}
                                   :friends {:type :user
                                             :relation :has-many}}))

((get-in user [:utils :create-table]) db/db-spec)

(clojure.pprint/pprint (merge (:resolvers user)
                              (:resolvers address)))

(clojure.pprint/pprint (merge (:queries user)
                              (:queries address)))

(clojure.pprint/pprint {:user (:schema user)
                        :address (:schema address)})

(def testql (-> {:objects {:user (:schema user)
                           :address (:schema address)}
                 :queries (merge (:queries user)
                                 (:queries address))}
                (attach-resolvers (merge (:resolvers user)
                                         (:resolvers address)))
                schema/compile))

(def query-fn #(execute testql % nil nil))

(query-fn "{ user(id: 1) { username } }")

(def juan-address {:postcode "SW111EA"
                   :line1 "137LavenderSweep"
                   :line2 "we"
                   :city "London"})

(def sanjay-address {:postcode "ABCCURRY123"
                     :line1 "23 bollywood road"
                     :city "Bombay"})

(def stefan-address {:postcode "IAMFAT123"
                     :line1 "some random shitty place"
                     :city "Amsterdam"})

(def william-address {:postcode "SE153UA"
                      :line1 "some shitty place in Pekcham"
                      :city "London"})


(def juan {:username "juanito"
           :description "whiny churros"
           :address 1})

(def stefan {:username "stefan"
             :description "fat racist dutch"
             :address 2})

(def sanjay {:username "sanjay"
             :description "curry and stupid jokes"
             :address 3})

(def william {:username "william"
              :description "bitcoins and dogshite"
              :address "4"})

(j/insert! db/db-spec :address juan-address)
(j/insert! db/db-spec :address stefan-address)
(j/insert! db/db-spec :address sanjay-address)
(j/insert! db/db-spec :address william-address)
(j/insert! db/db-spec :user juan)
(j/insert! db/db-spec :user stefan)
(j/insert! db/db-spec :user sanjay)
(j/insert! db/db-spec :user william)


(deftest own-fields
  (testing "should return some of its own fields"
    (let [query "{ user(username:\"juanito\") { username, description } }"]
      (is (= (:data (query-fn query))
             {:user (list (select-keys juan [:username :description]))})))))

(deftest one-to-one-relationship
  (testing "should return nested entity one: has-one"
    (let [query "{ user(username: \"juanito\") { username, description, address { postcode, line1, city } } }"]
      (is (= (:data (query-fn query))
             {:user (list (assoc
                           (select-keys juan [:username :description])
                           :address
                           (select-keys juan-address [:postcode :line1 :city])))})))

    (testing "should return parent entity: belongs-to"
      (let [query "{ address(id: 1) { postcode,  user { username } } }"]
        (is (= (:data (query-fn query))
               {:address (list (assoc (select-keys juan-address [:postcode])
                                      :user (select-keys juan [:username])))}))))

    (testing "should return nested and itself again in the nested entity"
      (let [query "{ user(username: \"juanito\") { username, description, address { postcode, line1, city, user { username  } } } }"]
        (is (= (:data (query-fn query))
               {:user (list (assoc
                             (select-keys juan [:username :description])
                             :address
                             (assoc
                              (select-keys juan-address [:postcode :line1 :city])
                              :user
                              (select-keys juan [:username]))))}))))))

(comment (deftest one-to-many-relationship
           (testing "should return list of nested entities"
             (let [query "{ user(username: \"juanito\" { username, description, friends { username } }}"]
               (is (= (:data (query-fn query))
                      {:user (list (assoc
                                    (select-keys juan [:username :description])
                                    :friends [(select-keys stefan [:username])
                                              (select-keys sanjay [:username])]))}))))))
