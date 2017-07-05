(ns foundation.graphql.mutations
  (:require [clojure.string :as str]
            [foundation.graphql.data-layer :as data]
            [foundation.graphql.utils :as utils]
            [clojure.pprint :refer [pprint]]
            [com.walmartlabs.lacinia.resolve :as resolve]))

(defn to-camel-case
  [prefix entity-name]
  (->> entity-name
       (str/capitalize)
       (str prefix)
       (keyword)))

(defn validate-params
  [all-fschemas {:keys [validations types]} params]
  (and (every? (fn [[key value]]
                 (if (map? value)
                   (let [linked-fschema ((key types) all-fschemas)]
                     (validate-params all-fschemas
                                      linked-fschema
                                      value))
                   (if-let [validation-fn (get-in validations [key])]
                     (validation-fn value)
                     true)))
               params)
       (if-let [global-validation (:global-validation validations)]
         (global-validation params)
         true)))


(defn gen-handler
  [all-fschemas handler {:keys [fields validations] :as fschema} context params value]
  (let [valid?  (validate-params all-fschemas fschema params)]
    (if valid?
      (handler fschema context params value)
      (resolve/resolve-as nil {:message "invalid mutation parameters"}))))

(defn gen-create-and-update
  [data-layer all-fschemas {:keys [entity-name m-fields entity-name fields] :as fschema}]
  (let [update-resolver-id (to-camel-case "update" (name entity-name))
        create-resolver-id (to-camel-case "create" (name entity-name))
        args               (utils/remove-data (merge m-fields
                                                     (utils/gen-relations-args fields :has-one)))]
    {:resolvers {create-resolver-id (partial gen-handler
                                             all-fschemas
                                             (partial data/create-entity data-layer)
                                             fschema)
                 update-resolver-id (partial gen-handler
                                             all-fschemas
                                             (partial data/update-entity data-layer)
                                             fschema)}
     :mutations {create-resolver-id {:type    entity-name
                                     :resolve create-resolver-id
                                     :args    args}
                 update-resolver-id {:type    entity-name
                                     :resolve update-resolver-id
                                     :args    (assoc args :id
                                                     {:type '(non-null ID)})}}}))

(defn gen-delete
  [data-layer {:keys [entity-name] :as fschema}]
  (let [delete-resolver-id (to-camel-case "delete" (name entity-name))
        handler             (partial data/delete-entity data-layer fschema)]
    {:mutations {delete-resolver-id {:type entity-name
                                     :resolve delete-resolver-id
                                     :args {:id {:type '(non-null ID)}}}}
     :resolvers {delete-resolver-id handler}}))

(defn gen-mutations
  [data-layer all-fschemas fschema]
  (let [create (gen-create-and-update data-layer all-fschemas fschema)
        delete (gen-delete data-layer fschema)]
    {:mutations (merge (:mutations create)
                       (:mutations delete))
     :resolvers (merge (:resolvers create)
                       (:resolvers delete))}))
