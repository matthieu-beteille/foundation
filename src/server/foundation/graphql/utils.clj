(ns foundation.graphql.utils)

(defn get-entity-name
  "return entity name, (list :users) or (non-null :users) or :users will all return users"
  [type]
  (cond
    (keyword? type) (name type)
    (list? type)    (name (second type))
    :default
    (throw (Exception. "entity type should be another entity identifier (or a list of another entity identifier"))))

(defn quote-unquote
  [value]
  (let [quote (when (string? value) "\"")]
    (str quote value quote)))

(defn gen-relations-args
  "generate the "
  [schema type]
  (->> schema
       (filter (comp (partial = type) :relation second))
       (map (juxt (comp first) (comp (partial assoc {} :type)
                                     keyword
                                     (partial str "input-")
                                     get-entity-name
                                     :type
                                     second)))
       (into {})))

(defn remove-data
  [schema]
  (into {} (map  #(-> [(first %)
                       (dissoc (second %) :q :m :validation :relation-name :relation :as)])
                 schema)))
