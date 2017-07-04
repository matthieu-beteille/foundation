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

(defn gen-handler
  [handler {:keys [fields validation] :as fschema} context params value]
  (let [valid?  (and (every? (fn [[key value]]
                               (if-let [validation-fn (get-in fields [key :validation])]
                                 (validation-fn value)
                                 true))
                             params)
                     (if validation
                       (validation params)
                       true))]
    (if valid?
      (handler fschema context params value)
      (resolve/resolve-as nil {:message "invalid mutation parameters"}))))

(comment "enforce id on all resources in the parser")

(defn gen-create-and-update
  [data-layer {:keys [entity-name m-fields entity-name fields validation] :as fschema}]
  (let [update-resolver-id (to-camel-case "update" (name entity-name))
        create-resolver-id (to-camel-case "create" (name entity-name))
        args               (utils/remove-data (merge m-fields
                                                     (utils/gen-relations-args fields :has-one)))]
    {:resolvers {create-resolver-id (partial gen-handler
                                             (partial data/create-entity data-layer)
                                             fschema)
                 update-resolver-id (partial gen-handler
                                             (partial data/update-entity data-layer)
                                             fschema)}
     :mutations {create-resolver-id {:type    entity-name
                                     :resolve create-resolver-id
                                     :args    args}
                 update-resolver-id {:type    entity-name
                                     :resolve update-resolver-id
                                     :args    (assoc args :id
                                                     {:type '(non-null ID)})}}}))

(defn gen-mutations
  [data-layer fschema]
  (let [create (gen-create-and-update data-layer fschema)]
    {:mutations (:mutations create)
     :resolvers (:resolvers create)}))
