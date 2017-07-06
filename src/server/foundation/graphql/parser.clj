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
  [entity-name [field field-spec]]
  [field {:relation (:relation field-spec)
          :field    field
          :from     entity-name
          :to       (keyword (utils/get-entity-name (:type field-spec)))
          :as       (:as field-spec)
          :fk       (utils/get-fk entity-name field-spec)
          :relation-name (:relation-name field-spec)}])

(defn get-relations
  [{:keys [fields
           name]}]
  (->> fields
       (filter (comp relations :relation second))
       (map (partial format-relation name))
       (into {})))

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

(defn get-validations
  [{:keys [validation fields]}]
  (merge (if (nil? validation)
           {}
           {:global-validation validation})
         (->> fields
              (map (juxt first
                         (comp :validation second)))
              (remove (comp nil? second))
              (into {}))))

(defn get-types
  [{:keys [fields]}]
  (->> fields
       (map (juxt first (comp utils/get-type :type second)))
       (into {})))

(defn add-id-field
  [schema]
  (update-in schema [:fields] assoc :id {:type 'ID
                                         :q true}))

(defn parse-schema
  [schema]
  (let [schema (->> schema
                    (add-id-field))]
    {:entity-name  (:name schema)
     :validations  (get-validations schema)
     :q-fields     (get-x-fields :q schema)
     :m-fields     (get-x-fields :m schema)
     :relations    (get-relations schema)
     :input-object {:fields (utils/remove-data (create-input-object schema))}
     :own-fields   (get-own-fields schema)
     :fields       (:fields schema)
     :types        (get-types schema)}))

(defn parse-schemas
  [schemas]
  (into [] (map parse-schema schemas)))
