(ns foundation.utils
  (:require [clojure.spec :as s]
            [foundation.db :as db]
            [foundation.data-layer :refer [new-mysql-data-layer]]
            [clojure.java.jdbc :as j]))

(def relations #{:has-one :has-many :belongs-to})

;;define specs
(def user
  '{:id          {:type Int
                  :q true}
    :address     {:type :address
                  :relation :has-one}
    :username    {:type String
                  :q true}
    :description {:type String}})

(defn to-sql-type
  [field {:keys [type]} is-fk?]
  (cond
    is-fk? "int"
    (= 'ID type) (str "int NOT NULL AUTO_INCREMENT, PRIMARY KEY (`" (name field) "`)")
    (= 'Int type) "int"
    (= 'String type) "varchar(100)"))

(defn remove-data
  [schema]
  (into {} (map  #(-> [(first %)
                       (dissoc (second %) :q :relation)])
                 schema)))

(defn quote-unquote
  [value]
  (let [quote (when (string? value) "\"")]
    (str quote value quote)))

(defn gen-has-one-query
  [entity-name linked-entity context params value]
  (let [linked-entity-name (name linked-entity)
        query  (str "SELECT * FROM " linked-entity-name
                    " WHERE id = " (get value linked-entity))]
    (first (j/query db/db-spec [query]))))

(defn gen-belongs-to-query
  [entity-name linked-entity context params value]
  (let [linked-entity-name (name linked-entity)
        query  (str "SELECT * FROM " linked-entity-name
                    " WHERE " entity-name " = " (:id value))]
    (first (j/query db/db-spec [query]))))

(defn gen-query-handler
  [entity-name context params value]
  (let [query (str "select * from " entity-name " where "
                   (->> params
                        (map (fn [[key value]] (str (name key) " = "  (quote-unquote value))))
                        (interpose " and ")
                        (apply str)))]
    (j/query db/db-spec [query])))

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

(def needs-fk #{:has-one :belongs-to})

(defn gen-create-table
  [entity-name schema db]
  (let [query (str "CREATE TABLE IF NOT EXISTS `" entity-name "` ( "
                   (->> schema
                        (map (fn [[field spec]]
                               (let [field-name (name field)
                                     is-relation? (contains? spec :relation)
                                     needs-fk? (needs-fk (:relation spec))]
                                 (when (or needs-fk? (not is-relation?))
                                   (str "`"  field-name "` " (to-sql-type field spec needs-fk?)
                                        (when needs-fk? (str ", FOREIGN KEY (`" field-name "`) REFERENCES " field-name "(`id`)")))))))
                        (remove nil?)
                        (interpose ", ")
                        (apply str))
                   " ) ENGINE=INNODB;")]
    (j/execute! db query)))

(defn handle-relations
  [entity-name schema]
  (reduce
   (fn [acc [field spec]]
     (case (:relation spec)
       :belongs-to
       (let [resolver-id (keyword (str "get-" entity-name "-" (name field)))]
         (-> acc
             (update-in [:objects]
                        #(assoc %
                                field
                                (assoc spec :resolve resolver-id)))
             (update-in [:resolvers]
                        #(assoc %
                                resolver-id
                                (partial gen-belongs-to-query entity-name field)))))
       :has-one
       (let [resolver-id (keyword (str "get-" entity-name "-" (name field)))]
         (-> acc
             (update-in [:objects]
                        #(assoc %
                                field
                                (assoc spec :resolve resolver-id)))
             (update-in [:resolvers]
                        #(assoc %
                                resolver-id
                                (partial gen-has-one-query entity-name field)))))
       (update-in acc [:objects] assoc field spec)))
   {:objects {}
    :resolvers {}}
   schema))

(defn gen-lacinia-sch
  ([entity schema]
   (gen-lacinia-sch (new-mysql-data-layer db/db-spec)
                    entity
                    schema))
  ([data-layer entity schema]
   (let [entity-name       (name entity)
         resolver-id       (keyword (str "get-" entity-name))
         {:keys [resolvers
                 objects]} (handle-relations entity-name schema)
         queryiable-fields (filter (comp :q second) schema)
         query-function    (partial gen-query-handler entity-name)
         query             {:type (list (symbol 'list) entity)
                            :resolve resolver-id
                            :args (->> queryiable-fields
                                       (remove-data)
                                       (into {}))}]
     {:schema {:fields (remove-data objects)}
      :queries
      {entity query}
      :resolvers (merge {resolver-id query-function} resolvers)
      :utils {:create-table (partial gen-create-table entity-name schema)}})))
