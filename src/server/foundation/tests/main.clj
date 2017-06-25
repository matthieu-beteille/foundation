(ns foundation.tests.main
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
                                                 f/dog
                                                 f/author
                                                 f/book])))
  (alter-var-root #'query-fn (constantly  #(execute testql % nil nil)))
  (f/insert-data! db/db-spec)
  (f)
  (f/drop-tables! db/db-spec))

(use-fixtures :once fixtures)

(deftest main-query
  (testing "should get all users"
    (let [query "{ user { username } }"]
      (is (= (:data (query-fn query))
             {:user (map #(select-keys % [:username])
                         (concat [f/juan f/stefan f/sanjay f/william]
                                 f/friends))}))))

  (testing "should be able to query by a combination of all q fields"
    (let [query "{ user(username: \"juanito\") { username, description } }"]
      (is (= (:data (query-fn query))
             {:user (list (select-keys f/juan [:username :description]))})))
    (let [query "{ user(id: 1) { username, description } }"]
      (is (= (:data (query-fn query))
             {:user (list (select-keys f/juan [:username :description]))})))
    (let [query "{ user(username: \"juanito\", id: 1) { username, description } }"]
      (is (= (:data (query-fn query))
             {:user (list (select-keys f/juan [:username :description]))})))
    (let [query "{ address(postcode: \"SW111EA\") { postcode } }"]
      (is (= (:data (query-fn query))
             {:address (list (select-keys f/juan-address [:postcode]))})))
    (let [query "{ dog(name: \"dog-1\") { breed } }"]
      (is (= (:data (query-fn query))
             {:dog (list {:breed "breed-1"})})))
    (let [query "{ dog(breed: \"breed-27\") { name, breed } }"]
      (is (= (:data (query-fn query))
             {:dog (list {:breed "breed-27" :name "dog-27"})})))
    (let [query "{ dog(breed: \"breed-27\", name: \"breed-40\") { name, breed } }"]
      (is (= (:data (query-fn query))
             {:dog '()})))))

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
      (let [query "{ address(postcode: \"SW111EA\") { postcode,  user { username } } }"]
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

(deftest one-to-many-relationship
  (testing "should return list of nested entities"
    (let [query "{ user(username: \"juanito\") { username, description, dogs { name } }}"]
      (is (= (:data (query-fn query))
             {:user (list (assoc
                           (select-keys f/juan [:username :description])
                           :dogs [(select-keys f/sausage-dog [:name])
                                  (select-keys f/pug [:name])]))})))
    (let [query "{ user(id: 2) { username, dogs { name } }}"]
      (is (= (:data (query-fn query))
             {:user (list (assoc
                           (select-keys f/stefan [:username])
                           :dogs (map #(select-keys % [:name]) f/dogs)))})))
    (let [query "{ user(id: 2) { username, dogs { breed } }}"]
      (is (= (:data (query-fn query))
             {:user (list (assoc
                           (select-keys f/stefan [:username])
                           :dogs (map #(select-keys % [:breed]) f/dogs)))}))))

  (testing "should return parent entity"
    (let [query "{ dog(id: 1) { name, owner { username } } }"]
      (is (= (:data (query-fn query))
             {:dog (list (assoc
                           (select-keys f/sausage-dog [:name])
                           :owner (select-keys f/juan [:username])))}))))

  (testing "should accept any of the q fields as a subquery"
    (let [query "{ user(id: 2) { dogs(name: \"dog-1\") { name } } }"]
      (is (= (:data (query-fn query))
             {:user (list {:dogs (list {:name "dog-1"})})})))
    (let [query "{ user(id: 2) { dogs(name: \"dog-34\") { breed } } }"]
      (is (= (:data (query-fn query))
             {:user (list {:dogs (list {:breed "breed-34"})})})))
    (let [query "{ user(id: 2) { dogs(breed: \"breed-14\") { name, breed, owner { id } } } }"]
      (is (= (:data (query-fn query))
             {:user (list {:dogs (list {:name "dog-14" :breed "breed-14" :owner {:id "2"}})})})))))

(deftest many-to-many-relationship
  (testing "should retrieve list of nested entities"
    (let [query "{ user(username: \"juanito\") { username, friends { username } } }"]
      (is (= (:data (query-fn query))
             {:user (list {:username "juanito"
                           :friends (map #(select-keys % [:username]) f/friends)})})))
    (let [query "{ user(username: \"juanito\") { username, friends(username: \"friend-1\") { username } } }"]
      (is (= (:data (query-fn query))
             {:user (list {:username "juanito"
                           :friends (list {:username "friend-1"})})})))
    (let [query "{ author(id:1) { name, books { title } } }"]
      (pprint (query-fn query))
      (is (= (:data (query-fn query))
             {:author (list {:name "author-0"
                           :books (list {:title "book-0"}
                                        {:title "book-1"}
                                        {:title "book-2"})})})))
    (let [query "{ book(id:1) { title, authors { name } } }"]
      (is (= (:data (query-fn query))
             {:book (list {:title "book-0"
                           :authors (list {:name "author-0"}
                                          {:name "author-1"}
                                          {:name"author-2"})})}))))
  (testing "should retrieve empty list"
    (let [query " { user(username: \"friend-1\") { username, friends { username } } }"]
      (is (= (:data (query-fn query))
             {:user (list {:username "friend-1" :friends '()})})))))
