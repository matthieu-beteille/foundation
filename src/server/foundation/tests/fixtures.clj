(ns foundation.tests.fixtures
  (:require [foundation.utils :as utils]
            [foundation.db :as db]
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

(def user
  {:name :user
   :fields '{:id {:type ID
                  :q true}
             :username {:type String
                        :q true}
             :description {:type String}
             :address {:type :address
                       :relation :has-one}}})

(def juan-address {:postcode "SW111EA"
                   :line1 "137LavenderSweep"
                   :line2 "we"
                   :city "London"})

(def sanjay-address {:postcode "ABCCURRY123"
                     :line1 "23 bollywood road"
                     :city "Bombay"})

(def stefan-address {:postcode "IAMFAT123"
                     :line1 "some random place"
                     :city "Amsterdam"})

(def william-address {:postcode "SE153UA"
                      :line1 "somewhere in Pekcham"
                      :city "London"})


(def juan {:username "juanito"
           :description "whiny churros"
           :address 1})

(def stefan {:username "stefan"
             :description "android dev dutch"
             :address 2})

(def sanjay {:username "sanjay"
             :description "ux designer"
             :address 3})

(def william {:username "william"
              :description "android dev"
              :address "4"})

(defn insert-data!
  [db]
  (j/insert! db :address juan-address)
  (j/insert! db :address stefan-address)
  (j/insert! db :address sanjay-address)
  (j/insert! db :address william-address)
  (j/insert! db :user juan)
  (j/insert! db :user stefan)
  (j/insert! db :user sanjay)
  (j/insert! db :user william))

(defn drop-tables! 
  [db]
  (j/db-do-commands db ["DROP TABLE IF EXISTS `user`"
                        "DROP TABLE IF EXISTS `address`"]))
