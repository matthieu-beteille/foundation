(ns foundation.utils
  (:require [clojure.spec :as s]
            [foundation.db :as db]
            [foundation.data-layer :as data]
            [clojure.java.jdbc :as j]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]))

(s/def ::type (s/or :scalar #{'Int 'String 'ID 'Float 'Boolean}
                    :object keyword?))
(s/def ::q #{true false})
(s/def ::relation #{:has-one :belongs-to :has-many})
(s/def ::field (s/keys ::req-un [::type]
                       :opt-un [::q ::relations]))
(s/def ::schema (s/map-of keyword? ::field))


(defn remove-data
  [schema]
  (into {} (map  #(-> [(first %)
                       (dissoc (second %) :q :relation)])
                 schema)))

(def user
  '{:id {:type ID
         :q true}
    :username {:type String
               :q true}
    :description {:type String}
    :address {:type :address
              :relation :has-one}
    :friends {:type :user
              :relation :has-many}})

(defn get-resolver
  [data-layer entity-name relation field]
  (case relation
    :has-one
    (partial data/get-one-nested-entity data-layer entity-name field)
    :belongs-to
    (partial data/get-one-parent-entity data-layer entity-name field)
    :has-many
    #(-> {:test "ok"})))

(defn handle-relations
  [data-layer entity-name field spec res]
  (let [has-relation? (:relation spec)]
    (if has-relation?
      (let [resolver-id (keyword (str "get-" entity-name "-" (name field)))
            resolver (get-resolver data-layer entity-name (:relation spec) field)]
        (-> res
            (update-in [:fields] #(assoc % field (assoc spec :resolve resolver-id)))
            (update-in [:resolvers] #(assoc % resolver-id resolver))))
      (update-in res [:fields] assoc field spec))))

(defn add-to-q
  [field spec res]
  (if (:q spec)
    (update-in res [:q-fields] assoc field spec)
    res))

(defn process-schema
  [data-layer entity-name schema]
  (reduce (fn [acc [field spec]]
            (->> acc
                (add-to-q field spec)
                (handle-relations data-layer entity-name field spec)))
          {:q-fields {}
           :fields {}
           :resolvers {}}
          schema))

(defn gen-lacinia-sch
  ([schema]
   (gen-lacinia-sch (data/new-mysql-data-layer db/db-spec) schema))
  ([data-layer schema]
   (let [entity            (:name schema)
         entity-name       (name entity)
         schema            (:fields schema)
         {:keys [q-fields
                 resolvers
                 fields]}  (process-schema data-layer entity-name schema)
         resolver-id       (keyword (str "get-" entity-name))
         query-function    (partial data/find data-layer entity-name)
         query             {:type (list (symbol 'list) entity)
                            :resolve resolver-id
                            :args (->> q-fields
                                       (remove-data)
                                       (into {}))}]
     (data/init! data-layer entity-name schema)
     {:name entity
      :schema {:fields fields}
      :queries {entity query}
      :resolvers (merge {resolver-id query-function} resolvers)})))

(defn create-lacinia
  ([schemas]
   (create-lacinia (data/new-mysql-data-layer db/db-spec) schemas))
  ([data-layer schemas]
   (let [schemas (map (partial gen-lacinia-sch data-layer) schemas)
         objects (into {} (map #(-> [(:name %) (:schema %)]) schemas))
         queries (->> schemas
                      (map :queries)
                      (apply merge))
         resolvers (->> schemas
                        (map :resolvers)
                        (apply merge))]
     (-> {:objects objects
          :queries queries}
         (attach-resolvers resolvers)
         schema/compile))))
