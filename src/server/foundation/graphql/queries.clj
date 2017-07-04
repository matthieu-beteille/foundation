(ns foundation.graphql.queries
  (:require [clojure.pprint :refer [pprint]]
            [foundation.graphql.utils :as utils]
            [foundation.graphql.data-layer :as data]))

(defn gen-main-query
  [data-layer {:keys [entity-name] :as fschema}]
  (let [query-resolver-id (keyword (str "get-" (name entity-name)))
        query-function    (partial data/query data-layer fschema)
        query             {:type (list (symbol 'list) entity-name)
                           :resolve query-resolver-id
                           :args (:q-fields fschema)}]
    {:query    {entity-name query}
     :resolver {query-resolver-id query-function}}))

(defn get-resolver
  [data-layer fschema field field-spec]
  (case (:relation field-spec)
    :has-one
    (partial data/query-one-nested-entity data-layer fschema field field-spec)
    :belongs-to
    (partial data/query-one-parent-entity data-layer fschema field field-spec)
    :has-many
    (partial data/query-many-nested-entities data-layer fschema field field-spec)
    :has-and-belongs-to-many
    (partial data/query-many-to-many-entities data-layer fschema field field-spec)))

(defn handle-relations
  [data-layer
   {:keys [entity-name] :as fschema}
   all-q-fields
   acc
   [field spec]]
  (let [has-relation? (:relation spec)]
    (if has-relation?
      (let [linked-entity (keyword (utils/get-entity-name (:type spec)))
            resolver-id (keyword (str "get-" (name entity-name) "-" (name field)))
            resolver (get-resolver data-layer fschema field spec)]
        (-> acc
            (update-in [:fields] #(assoc % field (assoc spec
                                                        :resolve resolver-id
                                                        :args (linked-entity all-q-fields))))
            (update-in [:resolvers] #(assoc % resolver-id resolver))))
      (update-in acc [:fields] assoc field spec))))

(defn gen-fields-queries
  [data-layer {:keys [fields] :as fschema} all-q-fields]
  (reduce (partial handle-relations data-layer fschema all-q-fields)
          {:fields {}
           :resolvers {}}
          fields))

(defn gen-queries
  [data-layer all-q-fields fschema]
  (let [main-query        (gen-main-query data-layer fschema)
        {:keys [resolvers
                fields]}  (gen-fields-queries data-layer fschema all-q-fields)]
    (data/init-entity! data-layer fschema)
    {:fields    fields
     :queries   (:query main-query)
     :resolvers (merge (:resolver main-query)
                       resolvers)}))
