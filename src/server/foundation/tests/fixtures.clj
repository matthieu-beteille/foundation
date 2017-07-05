(ns foundation.tests.fixtures
  (:require [foundation.db :as db]
            [clojure.java.jdbc :as j]))

(def address
  {:name :address
   :validation #(not= (:postcode %) "forbidden-postcode")
   :fields  {:id {:type 'ID
                  :q true}
             :user {:type :user
                    :relation :belongs-to}
             :postcode {:type 'String
                        :m true
                        :validation (partial not= "forbidden-postcode-field")
                        :q true}
             :line1 {:type 'String
                     :m true}
             :line2 {:type 'String}
             :city {:type 'String}}})

(def author
  {:name :author
   :fields '{:id {:type ID
                  :q true}
             :name {:type String}
             :books {:type (list :book)
                     :relation :has-and-belongs-to-many
                     :relation-name :author_book}}})

(def book
  {:name :book
   :fields '{:id {:type ID
                  :q true}
             :mostLikedBy {:type :user
                             :relation :belongs-to}
             :title {:type String
                     :m true}
             :authors {:type (list :author)
                       :relation :has-and-belongs-to-many
                       :relation-name :author_book}}})

(def dog
  {:name :dog
   :fields '{:id {:type ID
                  :q true}
             :name {:type String
                    :q true
                    :m true}
             :breed {:type String
                     :q true}
             :owner {:type :user
                     :relation :belongs-to}}})

(def user
  {:name :user
   :validation #(not= (:username %) "forbidden-name") ;; example of entity validation, useful when fields depend on each other
   :fields
   {:id {:type 'ID
         :q true}
    :username {:type 'String
               :m true
               :validation #(> (count %) 3)  ;; example of field validation
               :q true}
    :description {:type 'String
                  :m true}
    :address {:type :address
              :m true
              :relation :has-one}
    :friends {:type '(list :user)
              :relation :has-and-belongs-to-many
              :relation-name :friendship}
    :favoriteBook {:type :book
                    :m true
                    :relation :has-one
                    :as :mostLikedBy}
    :dogs {:type '(list :dog)
           :relation :has-many
           :as :owner}}})

(def juan-address {:postcode "SW111EA"
                   :line1 "137LavenderSweep"
                   :line2 "we"
                   :user 1
                   :city "London"})

(def stefan-address {:postcode "IAMFAT123"
                     :line1 "some random place"
                     :city "Amsterdam"
                     :user 2})

(def sanjay-address {:postcode "ABCCURRY123"
                     :line1 "23 bollywood road"
                     :user 3
                     :city "Bombay"})

(def william-address {:postcode "SE153UA"
                      :line1 "somewhere in Pekcham"
                      :city "London"
                      :user 4})

(def juan {:username "juanito"
           :description "whiny churros"})

(def stefan {:username "stefan"
             :description "android dev dutch"})

(def sanjay {:username "sanjay"
             :description "ux designer"})

(def william {:username "william"
              :description "android dev"})

(def sausage-dog {:name "vegeta"
                  :breed "sausage dog"
                  :owner 1})

(def pug {:name "franck the pug"
          :breed "pug"
          :owner 1})

(def dogs (into [] (map #(-> {:name (str "dog-" %)
                              :breed (str "breed-" %)
                              :owner 2})
                        (range 50))))

(def friends (into [] (map #(-> {:username (str "friend-" %)
                                 :description (str "description-" %)})
                           (range 50))))


(def authors (into [] (map #(-> {:name (str "author-" %)})
                           (range 4))))

(def books (into [] (map #(-> {:title (str "book-" %)})
                           (range 5))))

(defn insert-data!
  [db]
  (j/insert! db :user juan)
  (j/insert! db :user stefan)
  (j/insert! db :user sanjay)
  (j/insert! db :user william)
  (j/insert! db :address stefan-address)
  (j/insert! db :address sanjay-address)
  (j/insert! db :address william-address)
  (j/insert! db :address juan-address)
  (j/insert! db :dog sausage-dog)
  (j/insert! db :dog pug)
  (doseq [dog dogs]
    (j/insert! db :dog dog))
  (doseq [book books]
    (j/insert! db :book book))
  (doseq [author authors]
    (j/insert! db :author author))
  (j/insert! db :author_book {:author 1 :book 1}) ; author 1 has 3 books
  (j/insert! db :author_book {:author 1 :book 2})
  (j/insert! db :author_book {:author 1 :book 3})
  (j/insert! db :author_book {:book 1 :author 2}) ; book 1 has 2 authors
  (j/insert! db :author_book {:book 1 :author 3})
  (doseq [friend friends]
    (let [inserted (j/insert! db :user friend)]
      (j/insert! db :friendship {:user 1
                                 :linked_user (:generated_key (first inserted))}))))

(def db db/db-spec)

(defn drop-tables!
  [db]
  (j/db-do-commands db ["DROP TABLE IF EXISTS `friendship`"
                        "DROP TABLE IF EXISTS `author_book`"
                        "DROP TABLE IF EXISTS `author`"
                        "DROP TABLE IF EXISTS `book`"
                        "DROP TABLE IF EXISTS `address`"
                        "DROP TABLE IF EXISTS `dog`"
                        "DROP TABLE IF EXISTS `user`"])) 
