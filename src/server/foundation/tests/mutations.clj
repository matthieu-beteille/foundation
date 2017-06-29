(ns foundation.tests.mutations
  (:require  [clojure.test :refer [deftest is testing use-fixtures]]
             [clojure.pprint :refer [pprint]]
             [foundation.graphql.lib :as lib]
             [foundation.graphql.data-layer :as data]
             [foundation.db :as db]
             [foundation.tests.fixtures :as f]
             [clojure.java.jdbc :as j]
             [com.walmartlabs.lacinia :refer [execute]]))

(def data-layer (data/new-mysql-data-layer db/db-spec))
(def testql nil)
(def query-fn nil)

(defn fixtures [f]
  (alter-var-root #'testql (constantly
                            (lib/create-graphql data-layer
                                                [f/user
                                                 f/address
                                                 f/book
                                                 f/author
                                                 f/dog])))
  (alter-var-root #'query-fn (constantly  #(execute testql % nil nil)))
  (f)
  (f/drop-tables! db/db-spec))

(use-fixtures :once fixtures)

(deftest create

  (testing "should be able to create an entity"

    (let [query "mutation { createUser(username: \"jojo\", description: \"cool jojo 123\") { username, description } }"
          result (query-fn query)]
      (is (= (:createUser (:data result))
             {:username "jojo"
              :description "cool jojo 123"}))))

  (testing "shouldnt' create an entity if one of the param is invalid"

    (let [query "mutation { createUser(username: \"sh\", description: \"cool jojo 123\") { username, description } }"
          result (query-fn query)]
      (is (nil? (:createUser (:data result))))
      (is (= "invalid mutation parameters"
             (:message (first (:errors result)))))))

  (testing "shouldnt' create an entity if the entity is invalid"

    (let [query "mutation { createUser(username: \"forbidden-name\", description: \"cool jojo 123\") { username, description } }"
          result (query-fn query)]
      (is (nil? (:createUser (:data result))))
      (is (= "invalid mutation parameters"
             (:message (first (:errors result)))))))

  (testing "should be able to create nested entity as well"
    (let [query "mutation { createUser(username: \"user-100\", description: \"sweet description\", address: { postcode: \"sw111pj\", line1: \"19a ilminster gardens\" }) { username, address { postcode } } }"
          result (query-fn query)]
      (is (= (:createUser (:data result))
             {:username "user-100"
              :address {:postcode "sw111pj"}}))))

  (testing "should be able to create nested entities as well"

    (let [query "mutation { createUser(username: \"user-101\", description: \"sweet description\", address: { postcode: \"sw111pj\", line1: \"19a ilminster gardens\" }, favoriteBook: {title: \"book-favorited\"}) { username, favoriteBook { title }, address { postcode } } }"
          result (query-fn query)]
      (is (= (:createUser (:data result))
             {:username "user-101"
              :favoriteBook {:title "book-favorited"}
              :address {:postcode "sw111pj"}})))))


