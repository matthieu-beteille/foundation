(ns foundation.graphql.parser
  (:require [foundation.graphql.utils :as utils]
            [clojure.pprint :refer [pprint]]))

(def relations #{:has-one :belongs-to :has-many :has-and-belongs-to-many})

(defn get-x-fields
  [x {:keys [fields]}]
  (->> fields
       (filter (comp x second))
       (into {})))

(defn format-relation
  [name [field field-spec]]
  {:relation (:relation field-spec)
   :field    field
   :from     name
   :to       (keyword (utils/get-entity-name (:type field-spec)))
   :as       (:as field-spec)
   :relation-name (:relation-name field-spec)})

(defn get-relations
  [{:keys [fields
           name]}]
  (->> fields
       (filter (comp relations :relation second))
       (map (partial format-relation name))
       (into [])))

(defn get-own-fields
  [{:keys [fields]}]
  (->> fields
       (remove (comp relations :relation second))
       (into {})))

(defn get-io-key
  [key]
  (keyword (str "input-" (name key))))

(defn create-input-object
  [{:keys [fields name]}]
  (->> fields
       (filter (comp :m second))
       (remove (comp (partial every? identity)
                     (juxt (comp relations :relation second)
                           (comp (partial not= :has-one) :relation second))))
       (map (fn [[key spec]]
              [key (if (:relation spec)
                     (assoc spec :type (get-io-key (:type spec)))
                     spec)]))
       (into {})))

(defn parse-schema
  [schema]
  {:entity-name  (:name schema)
   :validation   (:validation schema)
   :q-fields     (get-x-fields :q schema)
   :m-fields     (get-x-fields :m schema)
   :relations    (get-relations schema)
   :input-object {:fields (utils/remove-data (create-input-object schema))}
   :own-fields   (get-own-fields schema)
   :fields       (:fields schema)})

(comment "first figure out q-fields, ")

(defn parse-schemas
  [schemas]
  (into [] (map parse-schema schemas)))