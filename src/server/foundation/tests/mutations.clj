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
          result (query-fn query)
          check-query "{ user (username: \"jojo\") { username description } }"
          check-result (query-fn check-query)]
      (is (= (:createUser (:data result))
             (first (:user (:data check-result)))
             {:username "jojo"
              :description "cool jojo 123"}))))

  (testing "shouldnt' create an entity if one of the param is invalid"

    (let [query "mutation { createUser(username: \"sh\", description: \"cool jojo 123\") { username, description } }"
          result (query-fn query)
          check-query "{ user(username: \"sh\") { username, description } }"
          check-result (query-fn check-query)]
      (is (nil? (:createUser (:data result))))
      (is (nil? (first (:user (:data check-result)))))
      (is (= "invalid mutation parameters"
             (:message (first (:errors result)))))))

  (testing "shouldnt' create an entity if one of the param is invalid (global validation)"

    (let [query "mutation { createUser(username: \"forbidden-name\", description: \"cool jojo 123\") { username, description } }"
          result (query-fn query)
          check-query "{ user(username: \"forbidden-name\") { username, description } }"
          check-result (query-fn check-query)]
      (is (nil? (:createUser (:data result))))
      (is (nil? (first (:user (:data check-result)))))
      (is (= "invalid mutation parameters"
             (:message (first (:errors result)))))))

  (testing "shouldnt' create an entity if one of the param is invalid (global validation)"

    (let [query "mutation { createUser(username: \"o\", description: \"cool jojo 123\") { username, description } }"
          result (query-fn query)
          check-query "{ user(username: \"o\") { username, description } }"
          check-result (query-fn check-query)]
      (is (nil? (first (:user (:data check-result)))))
      (is (nil? (:createUser (:data result))))
      (is (= "invalid mutation parameters"
             (:message (first (:errors result)))))))

  (testing "should get an error when nested mutation params not valid (global validation)"

    (let [query "mutation { createUser( username: \"test\", address: { postcode: \"forbidden-postcode\" }) { description } }"
          result (query-fn query)
          check-query "{ user(username: \"test\") { username, description } }"
          check-result (query-fn check-query)]
      (is (nil? (first (:user (:data check-result)))))
      (is (= (:message (first (:errors result)))
             "invalid mutation parameters"))))

  (testing "should get an error when nested mutation params not valid (field validation)"

    (let [query "mutation { createUser( username: \"test\", address: { postcode: \"forbidden-postcode-field\" }) { description } }"
          result (query-fn query)
          check-query "{ user(username: \"test\") { username, description } }"
          check-result (query-fn check-query)]
      (is (nil? (first (:user (:data check-result)))))
      (is (= (:message (first (:errors result)))
             "invalid mutation parameters"))))

  (testing "should be able to create nested entity as well"
    (let [query "mutation { createUser(username: \"user-100\", description: \"sweet description\", address: { postcode: \"sw111pj\", line1: \"19a ilminster gardens\" }) { username, address { postcode } } }"
          result (query-fn query)
          check-query "{ user(username: \"user-100\") { username, address { postcode } }}"
          check-result (query-fn check-query)]
      (is (= (:createUser (:data result))
             (first (:user (:data check-result)))
             {:username "user-100"
              :address {:postcode "sw111pj"}}))))

  (testing "should be able to create nested entities (two has-one nested) as well"

    (let [query "mutation { createUser(username: \"user-101\", description: \"sweet description\", address: { postcode: \"sw111pj\", line1: \"19a ilminster gardens\" }, favoriteBook: {title: \"book-favorited\"}) { username, favoriteBook { title }, address { postcode } } }"
          result (query-fn query)
          check-query "{ user(username: \"user-101\") { username, favoriteBook { title },  address { postcode } }}"
          check-result (query-fn check-query)]
      (is (= (:createUser (:data result))
             (first (:user (:data check-result)))
             {:username "user-101"
              :favoriteBook {:title "book-favorited"}
              :address {:postcode "sw111pj"}})))))

(deftest update

  (testing "should be able to update an entity"

    (let [query "mutation { updateUser(id: \"1\", description: \"updated\") { description } }"
          result (query-fn query)
          check-query "{ user(id: \"1\") { description } }"
          check-result (query-fn check-query)]
      (is (= (:updateUser (:data result))
             (first (:user (:data check-result)))
             {:description "updated"}))))

  (testing "should get an error when entity doesn't exist"

    (let [query "mutation { updateUser(id: \"1312321321\", description: \"updated\") { description } }"
          result (query-fn query)]
      (is (= (:message (first (:errors result)))
             "the entity you are trying to update doesn't exist"))))

  (testing "should create nested entity if doesn't exist"

    (let [query "mutation { updateUser(id: \"1\", description: \"updated-2\", address: { postcode: \"TEST\", line1: \"cool\" }) { description, address { postcode } } }"
          result (query-fn query)
          check-query " { user(id:\"1\") { description, address { postcode } } }"
          check-result (query-fn check-query)]

      (is (= (:updateUser (:data result))
             (first (:user (:data check-result)))
             {:description "updated-2"
              :address {:postcode "TEST"}}))))

  (testing "should update nested entity if exists"

    (let [query "mutation { updateUser(id: \"1\", description: \"updated-3\", address: { postcode: \"TEST2\" }) { description, address { postcode, line1 } } }"
          result (query-fn query)
          check-query " { user(id:\"1\") { description, address { postcode, line1 } } }"
          check-result (query-fn check-query)]
      (is (= (:updateUser (:data result))
             (first (:user (:data check-result)))
             {:description "updated-3"
              :address {:postcode "TEST2"
                        :line1 "cool"}}))))

  (testing "should get an error when mutation params not valid (global validation)"

    (let [query "mutation { updateUser(id: \"1\", username: \"forbidden-name\") { description } }"
          result (query-fn query)]
      (is (= (:message (first (:errors result)))
             "invalid mutation parameters"))))

  (testing "should get an error when mutation params not valid (field validation)"

    (let [query "mutation { updateUser(id: \"1\", username: \"o\") { description } }"
          result (query-fn query)]
      (is (= (:message (first (:errors result)))
             "invalid mutation parameters"))))

  (testing "should get an error when nested mutation params not valid (global validation)"

    (let [query "mutation { updateUser(id: \"1\", address: { postcode: \"forbidden-postcode\" }) { description } }"
          result (query-fn query)]
      (is (= (:message (first (:errors result)))
             "invalid mutation parameters"))))

  (testing "should get an error when nested mutation params not valid (field-validation)"

    (let [query "mutation { updateUser(id: \"1\", address: { postcode: \"forbidden-postcode-field\" }) { description } }"
          result (query-fn query)]
      (is (= (:message (first (:errors result)))
             "invalid mutation parameters")))))

(deftest delete
  (testing "should be able to delete an entity and its nested entities (has-one)"

    (let [query "mutation { deleteUser(id: \"1\") { username } }"
          check-query "{ user(id: \"1\") { username } }"
          result (query-fn query)
          check-result (query-fn check-query)
          check-nested-query "{ address { postcode, user { id } } }"
          check-nested-result  (query-fn check-nested-query)]
      (is (zero? (->> (:address (:data check-nested-result))
                      (filter (comp (partial = "1") :id :user))
                      (count))))
      (is (nil? (first (:user (:data check-result)))))
      (is (= (:deleteUser (:data result))
             {:username "jojo"}))))

  (testing "should get an error if entity doesn't exist"

    (let [query "mutation { deleteUser(id: \"100\") { username } }"
          result (query-fn query)]
      (is (= (:message (first (:errors result)))
             "the entity you are trying to delete doesn't exist")))))

(deftest delete-nested
  (testing "should be able to delete a nested entity"

    (let [query "mutation { deleteUserAddress(id: \"2\") { username, address { postcode } } }"
          result (query-fn query)
          check-query " { user(id: \"2\") { username, address { postcode } } } "
          check-result (query-fn check-query)]
      (is (= (:deleteUserAddress (:data result))
             {:username "user-100"
              :address nil}))
      (is (= (first (:user (:data check-result)))
             {:username "user-100"
              :address nil}))))

  (testing "should get an error if entity doesn't exist"

    (let [query "mutation { deleteUserAddress(id: \"123222\") { username, address { postcode } } }"
          result (query-fn query)]
      (is (= (:message (first (:errors result)))
             "entity doesn't exist")))))
