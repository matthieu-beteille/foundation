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

(defn create-entity-handler
  [data-layer {:keys [fields validation] :as fschema} context params value]
  (let [valid?  (and (every? (fn [[key value]]
                               (if-let [validation-fn (get-in fields [key :validation])]
                                 (validation-fn value)
                                 true))
                             params)
                     (if validation
                       (validation params)
                       true))]
    (if valid?
      (data/create-entity data-layer fschema context params value)
      (resolve/resolve-as nil {:message "invalid mutation parameters"}))))

(defn gen-create
  [data-layer {:keys [entity-name m-fields entity-name fields validation] :as fschema}]
  (let [create-resolver-id (to-camel-case "create" (name entity-name))
        args (utils/remove-data
              (merge m-fields
                     (utils/gen-relations-args fields :has-one)))]
    {:resolvers {create-resolver-id
                (partial create-entity-handler data-layer fschema)}
     :mutations {create-resolver-id {:type entity-name
                                     :resolve create-resolver-id
                                     :args args}}}))

(defn gen-mutations
  [data-layer fschema]
  (let [create (gen-create data-layer fschema)]
    {:mutations (:mutations create)
     :resolvers (:resolvers create)}))
