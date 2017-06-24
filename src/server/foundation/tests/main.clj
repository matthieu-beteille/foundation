(ns foundation.tests.main
  (:require  [clojure.test :refer [deftest is testing use-fixtures]]
             [clojure.pprint :refer [pprint]]
             [foundation.utils :as utils]
             [foundation.data-layer :as data]
             [foundation.db :as db]
             [foundation.tests.fixtures :as f]
             [clojure.java.jdbc :as j]
             [com.walmartlabs.lacinia :refer [execute]]))

(def data-layer (data/new-mysql-data-layer db/db-spec))
(def testql nil)
(def query-fn nil)

(defn my-test-fixture [f]
  (alter-var-root #'testql (constantly (utils/create-lacinia data-layer [f/address f/user])))
  (alter-var-root #'query-fn (constantly  #(execute testql % nil nil)))
  (f/insert-data! db/db-spec)
  (f)
  (f/drop-tables! db/db-spec))

(use-fixtures :once my-test-fixture)

(deftest main-query
  (testing "should be able to query by a combination of all q fields"
    (let [query "{ user(username: \"juanito\") { username, description } }"]
      (is (= (:data (query-fn query))
             {:user (list (select-keys f/juan [:username :description]))}))))
  (let [query "{ user(username: \"juanito\", id: 1) { username, description } }"]
    (is (= (:data (query-fn query))
           {:user (list (select-keys f/juan [:username :description]))})))
  (let [query "{ address(postcode: \"SW111EA\") { postcode } }"]
    (is (= (:data (query-fn query))
           {:address (list (select-keys f/juan-address [:postcode]))}))))

(deftest own-fields
  (testing "should return some of its own fields"
    (let [query "{ user(username:\"juanito\") { username, description } }"]
      (is (= (:data (query-fn query))
             {:user (list (select-keys f/juan [:username :description]))})))))

(deftest one-to-one-relationship
  (testing "should return nested entity one: has-one"
    (let [query "{ user(username: \"juanito\") { username, description, address { postcode, line1, city } } }"]
      (is (= (:data (query-fn query))
             {:user (list (assoc
                           (select-keys f/juan [:username :description])
                           :address
                           (select-keys f/juan-address [:postcode :line1 :city])))})))

    (testing "should return parent entity: belongs-to"
      (let [query "{ address(id: 1) { postcode,  user { username } } }"]
        (is (= (:data (query-fn query))
               {:address (list (assoc (select-keys f/juan-address [:postcode])
                                      :user (select-keys f/juan [:username])))}))))

    (testing "should return nested and itself again in the nested entity"
      (let [query "{ user(username: \"juanito\") { username, description, address { postcode, line1, city, user { username  } } } }"]
        (is (= (:data (query-fn query))
               {:user (list (assoc
                             (select-keys f/juan [:username :description])
                             :address
                             (assoc
                              (select-keys f/juan-address [:postcode :line1 :city])
                              :user
                              (select-keys f/juan [:username]))))}))))))

(comment (deftest one-to-many-relationship
           (testing "should return list of nested entities"
             (let [query "{ user(username: \"juanito\" { username, description, friends { username } }}"]
               (is (= (:data (query-fn query))
                      {:user (list (assoc
                                    (select-keys juan [:username :description])
                                    :friends [(select-keys stefan [:username])
                                              (select-keys sanjay [:username])]))}))))))
