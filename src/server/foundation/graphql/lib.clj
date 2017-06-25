(ns foundation.graphql.lib
  (:require [clojure.spec :as s]
            [clojure.pprint :refer [pprint]]
            [foundation.utils :as utils]
            [foundation.db :as db]
            [foundation.graphql.data-layer :as data]
            [clojure.java.jdbc :as j]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]))

(s/def ::type (s/or :scalar #{'Int 'String 'ID 'Float 'Boolean}
                    :object keyword?
                    :adt (s/cat :modifier #{(symbol 'list) (symbol 'non-null)}
                                :type ::type)))
(s/def ::q #{true false})
(s/def ::relation #{:has-one :belongs-to :has-many :has-and-belongs-to-many})
(s/def ::relation-name keyword?)
(s/def ::as keyword?)
(s/def ::field (s/keys :req-un [::type]
                       :opt-un [::q ::relations ::as ::relation-name]))
(s/def ::fields (s/map-of keyword? ::field))
(s/def ::name keyword?)
(s/def ::schema (s/keys :req-un [::name
                                 ::fields]))

(defn remove-data
  [schema]
  (into {} (map  #(-> [(first %)
                       (dissoc (second %) :q :relation :as)])
                 schema)))

(defn get-resolver
  [data-layer entity-name relation field field-spec]
  (case relation
    :has-one
    (partial data/query-one-nested-entity data-layer entity-name field field-spec)
    :belongs-to
    (partial data/query-one-parent-entity data-layer entity-name field field-spec)
    :has-many
    (partial data/query-many-nested-entities data-layer entity-name field field-spec)
    :has-and-belongs-to-many
    (partial data/query-many-to-many-entities data-layer entity-name field field-spec)))

(defn handle-relations
  [data-layer q-fields entity-name res [field spec]]
  (let [has-relation? (:relation spec)]
    (if has-relation?
      (let [linked-entity (keyword (utils/get-entity-name (:type spec)))
            resolver-id (keyword (str "get-" entity-name "-" (name field)))
            resolver (get-resolver data-layer entity-name (:relation spec) field spec)]
        (-> res
            (update-in [:fields] #(assoc % field (assoc spec
                                                        :resolve resolver-id
                                                        :args (linked-entity q-fields))))
            (update-in [:resolvers] #(assoc % resolver-id resolver))))
      (update-in res [:fields] assoc field spec))))

(defn process-schema
  [data-layer q-fields entity-name schema]
  (reduce (partial handle-relations data-layer q-fields entity-name)
          {:fields {}
           :resolvers {}}
          schema))

(defn gen-lacinia-sch
  [data-layer q-fields schema]
  (let [entity            (:name schema)
        entity-name       (name entity)
        schema            (:fields schema)
        {:keys [resolvers
                fields]}  (process-schema data-layer q-fields entity-name schema)
        resolver-id       (keyword (str "get-" entity-name))
        query-function    (partial data/query data-layer entity-name)
        query             {:type (list (symbol 'list) entity)
                           :resolve resolver-id
                           :args (entity q-fields)}]
    (data/init-each! data-layer entity-name schema)
    {:name      entity
     :schema    {:fields fields}
     :queries   {entity query}
     :resolvers (merge {resolver-id query-function} resolvers)}))

(defn get-q-fields
  [{:keys [name fields]}]
  [name (->> fields
             (filter (comp :q second)) 
             (remove-data))])

(s/def ::schemas (s/+ ::schema))

(defn create-graphql
  ([schemas]
   (create-graphql (data/new-mysql-data-layer db/db-spec) schemas))
  ([data-layer schemas]
   (when-not (s/valid? ::schemas schemas)
     (pprint (s/explain-data ::schemas schemas)))
   (let [q-fields  (into {} (map get-q-fields schemas)) ;; first figure out the q fiels for each schema
         schemas   (map (partial gen-lacinia-sch data-layer q-fields) schemas) ;; then generate the schemas
         objects   (into {} (map #(-> [(:name %) (:schema %)]) schemas))
         queries   (->> schemas
                        (map :queries)
                        (apply merge))
         resolvers (->> schemas
                        (map :resolvers)
                        (apply merge))]
     (data/init! data-layer schemas)
     (-> {:objects objects
          :queries queries}
         (attach-resolvers resolvers)
         schema/compile))))
