(ns foundation.graphql.lib
  (:require [clojure.spec :as s]
            [clojure.spec.test :as st]
            [clojure.test :as test]
            [clojure.pprint :refer [pprint]]
            [foundation.utils :as utils]
            [foundation.db :as db]
            [foundation.graphql.data-layer :as data]
            [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]))

(s/def ::type (s/or :scalar #{'Int 'String 'ID 'Float 'Boolean}
                    :object keyword?
                    :adt (s/cat :modifier #{(symbol 'list) (symbol 'non-null)}
                                :type ::type)))
(s/def ::validation test/function?)
(s/def ::q #{true false})
(s/def ::m #{true false})
(s/def ::relation #{:has-one :belongs-to :has-many :has-and-belongs-to-many})
(s/def ::relation-name keyword?)
(s/def ::as keyword?)
(s/def ::field  (s/keys :req-un [::type]
                        :opt-un [::q ::m ::validation ::relation ::as ::relation-name]))
(s/def ::fields (s/map-of keyword? ::field))
(s/def ::name keyword?)
(s/def ::schema (s/keys :req-un [::name ::fields]
                        :opt-un [::validation]))


(defn remove-data
  [schema]
  (into {} (map  #(-> [(first %)
                       (dissoc (second %) :q :m :relation-name :relation :as)])
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

(defn create-entity-handler
  [data-layer entity-name schema global-validation context params value]
  (let [valid?  (and (every? (fn [[key value]]
                               (if-let [validation-fn (get-in schema [key :validation])]
                                 (validation-fn value)
                                 true))
                             params)
                     (if global-validation
                       (global-validation params)
                       true))]
    (if valid?
      (data/create data-layer entity-name context params value)
      (resolve/resolve-as nil {:message "invalid mutation parameters"})))) ;; we might want to do sth smarter here and explain

(defn gen-lacinia-sch
  [data-layer q-fields m-fields schema]
  (let [entity             (:name schema)
        validation         (:validation schema)
        entity-name        (name entity)
        schema             (:fields schema)
        {:keys [resolvers
                fields]}   (process-schema data-layer q-fields entity-name schema)
        query-resolver-id  (keyword (str "get-" entity-name))
        query-function     (partial data/query data-layer entity-name)
        query              {:type (list (symbol 'list) entity)
                            :resolve query-resolver-id
                            :args (entity q-fields)}
        create-resolver-id (keyword (str "create-" entity-name))
        create             {:type entity
                            :resolve create-resolver-id
                            :args (entity m-fields)}
        create-function    (partial create-entity-handler
                                    data-layer
                                    entity-name
                                    schema
                                    validation)]
    (data/init-entity! data-layer entity-name schema)
    {:name      entity
     :schema    {:fields fields}
     :queries   {entity query}
     :mutations {(keyword (str "create" (str/capitalize entity-name))) create}
     :resolvers (merge {query-resolver-id query-function
                        create-resolver-id create-function}
                       resolvers)}))

(defn get-x-fields
  [x {:keys [name fields]}]
  [name (->> fields
             (filter (comp x second))
             (remove-data))])

(s/fdef create-graphql
        :args (s/cat :data-layer data/data-layer
                     :schemas (s/coll-of ::schema)))

(defn create-graphql
  ([schemas]
   (create-graphql (data/new-mysql-data-layer db/db-spec) schemas))
  ([data-layer schemas]
   (let [q-fields  (into {} (map (partial get-x-fields :q) schemas)) ;; first figure out the q fiels for each schema
         m-fields  (into {} (map (partial get-x-fields :m) schemas))
         schemas   (map (partial gen-lacinia-sch data-layer q-fields m-fields) schemas) ;; then generate the schemas
         objects   (into {} (map #(-> [(:name %) (:schema %)]) schemas))
         queries   (->> schemas
                        (map :queries)
                        (apply merge))
         mutations (->> schemas
                        (map :mutations)
                        (apply merge))
         resolvers (->> schemas
                        (map :resolvers)
                        (apply merge))]
     (data/init! data-layer schemas)
     (-> {:objects objects
          :queries queries
          :mutations mutations}
         (attach-resolvers resolvers)
         schema/compile))))

(comment "beautiful"
  (->> schemas
       ((juxt (partial map :queries)
              (partial map :mutations)
              (partial map :resolvers)))
       (map (partial apply merge))
       (zipmap [:queries :mutations :resolvers])))

(st/instrument `create-graphql)
