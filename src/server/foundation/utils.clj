(ns foundation.utils.lacinia
  (:require [clojure.spec.alpha :as s]))

; maybe use lacinia's own specs
(comment (s/def ::type (s/or :scalar #{'Int 'String 'ID 'Float 'Boolean '}
                             :object keyword?))
         (s/def ::field (s/keys ::req-un [::type]
                                :opt [::q]))
         (s/def ::schema integer?))

(def user
  '{:id          {:type Int
                  :q true}
    :username    {:type String
                  :q true}
    :description {:type String}})

(defn remove-data
  [schema]
  (map #(-> [(first %)
             (select-keys (second %) [:type])]) schema))

(defn quote-unquote
  [value]
  (let [quote (when (string? value) "\"")]
    (str quote value quote)))

(defn gen-query-handler
  [entity-name params]
  (str "select * from " (name entity-name) " where "
       (->> params
            (map (fn [[key value]] (str (name key) " = "  (quote-unquote value))))
            (interpose " and ")
            (apply str))))

(defn gen-lacinia-sch
  [entity-name schema]
  (let [resolver-id       (keyword (str "get-"(name entity-name)))
        queryiable-fields (filter (comp :q second) schema)
        query-function    (partial gen-query-handler entity-name)
        query             {:type (keyword entity-name)
                           :args (->> queryiable-fields
                                      (remove-data)
                                      (into {}))}]
    {:objects {name (remove-data schema)}
     :queries
     {name (assoc query :resolver resolver-id)}
     :resolver {:id      resolver-id
                :handler query-function}}))
