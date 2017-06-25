(ns foundation.tests.fixtures
  (:require [foundation.db :as db]
            [clojure.java.jdbc :as j]))

(def address
  {:name :address
   :fields '{:id {:type ID
                  :q true}
             :user {:type :user
                    :relation :belongs-to}
             :postcode {:type String
                        :q true}
             :line1 {:type String}
             :line2 {:type String}
             :city {:type String}}})

(def dog
  {:name :dog
   :fields '{:id {:type ID
                  :q true}
             :name {:type String
                    :q true}
             :breed {:type String
                     :q true}
             :owner {:type :user
                     :relation :belongs-to}}})

(def user
  {:name :user
   :fields '{:id {:type ID
                  :q true}
             :username {:type String
                        :q true}
             :description {:type String}
             :address {:type :address
                       :relation :has-one}
             :dogs {:type (list :dog)
                    :relation :has-many
                    :through :owner}}})

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
                              :owner 2}) (range 50))))

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
    (j/insert! db :dog dog)))

(defn drop-tables! 
  [db]
  (j/db-do-commands db ["DROP TABLE IF EXISTS `address`"
                        "DROP TABLE IF EXISTS `dog`"
                        "DROP TABLE IF EXISTS `user`"]))
