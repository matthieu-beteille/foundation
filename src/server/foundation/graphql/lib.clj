(ns foundation.graphql.lib
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [clojure.test :as test]
            [clojure.pprint :refer [pprint]]
            [foundation.graphql.utils :as utils]
            [foundation.graphql.parser :as parser]
            [foundation.db :as db]
            [foundation.graphql.mutations :as m]
            [foundation.graphql.queries :as q]
            [foundation.graphql.data-layer :as data]
            [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]))

(def relations #{:has-one :belongs-to :has-many :has-and-belongs-to-many})
(s/def ::type (s/or :scalar #{'Int 'String 'ID 'Float 'Boolean}
                    :object keyword?
                    :adt (s/cat :modifier #{(symbol 'list) (symbol 'non-null)}
                                :type ::type)))
(s/def ::validation test/function?)
(s/def ::q #{true false})
(s/def ::m #{true false})
(s/def ::relation relations)
(s/def ::relation-name keyword?)
(s/def ::as keyword?)
(s/def ::field  (s/keys :req-un [::type]
                        :opt-un [::q ::m ::validation ::relation ::as ::relation-name]))
(s/def ::fields (s/map-of keyword? ::field))
(s/def ::name keyword?)
(s/def ::schema (s/keys :req-un [::name ::fields]
                        :opt-un [::validation]))



(defn gen-lacinia-sch
  [data-layer all-q-fields all-fschemas fschema]
  (let [queries   (q/gen-queries data-layer all-q-fields fschema)
        mutations (m/gen-mutations data-layer all-fschemas fschema)]
    (data/init-entity! data-layer fschema)
    {:name      (:entity-name fschema)
     :fields    (:fields queries)
     :queries   (:queries queries)
     :mutations (:mutations mutations)
     :resolvers (merge (:resolvers queries)
                       (:resolvers mutations))}))

(s/fdef create-graphql
        :args (s/cat :data-layer data/data-layer
                     :schemas (s/coll-of ::schema)))

(defn create-graphql
  ([schemas]
   (create-graphql (data/new-mysql-data-layer db/db-spec) schemas))
  ([data-layer schemas]
   (let [fschemas  (parser/parse-schemas schemas)
         all-fschemas (->> fschemas
                           (map (juxt :entity-name identity))
                           (into {}))
         all-q-fields (->> fschemas
                           (map (juxt :entity-name :q-fields))
                           (into {}))
         processed (map (partial gen-lacinia-sch data-layer all-q-fields all-fschemas) fschemas) ;; then generate the schemas
         objects   (into {} (map #(-> [(:name %) {:fields (:fields %)}]) processed))
         queries   (->> processed
                        (map :queries)
                        (apply merge))
         mutations (->> processed
                        (map :mutations)
                        (apply merge))
         resolvers (->> processed
                        (map :resolvers)
                        (apply merge))
         input-objects (->> fschemas
                            (map (juxt (comp parser/get-io-key :entity-name)
                                       :input-object))
                            (into {}))]
     (data/init! data-layer fschemas)
     (-> {:objects objects
          :input-objects input-objects
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
