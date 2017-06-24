(ns foundation.data-layer
  (:require [clojure.java.jdbc :as j]))

(comment "for now we only have mysql but we can easily swap the data layer, we'll just have to reimplement the following interface")

(defprotocol DataLayer
  "A simple protocol describing foundation's data layer"
  (init! [this entity-name schema]
    "effectful method to initialise things related to an entity (ex: create tables)")
  (find [this entity-name context params value]
    "query method to retrieve an entity.")
  (get-one-nested-entity
    [this entity-name linked-entity ctx params value]
    "returns a one-to-one linked entity.")
  (get-one-parent-entity
    [this entity-name linked-entity ctx params value]
    "returns a one-to-one parent entity."))

(def needs-fk #{:has-one})

(defn- quote-unquote
  [value]
  (let [quote (when (string? value) "\"")]
    (str quote value quote)))

(defn- to-sql-type
  "graphql to mysql type mapping"
  [field {:keys [type]} is-fk?]
  (cond
    is-fk? "int"
    (= 'ID type) (str "int NOT NULL AUTO_INCREMENT, PRIMARY KEY (`" (name field) "`)")
    (= 'Int type) "int"
    (= 'String type) "varchar(100)"))

(defrecord MySQL [db-spec]
  DataLayer
  (init!
    [db-spec entity-name schema]
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
      (j/execute! db-spec query)))

  (find
    [db-spec entity-name context params value]
    (let [query (str "SELECT * FROM " entity-name " WHERE "
                     (->> params
                          (map (fn [[key value]] (str (name key) " = "  (quote-unquote value))))
                          (interpose " AND ")
                          (apply str)))]
      (j/query db-spec [query])))

  (get-one-nested-entity
    [db-spec entity-name linked-entity context params value]
    (let [linked-entity-name (name linked-entity)
          query  (str "SELECT * FROM " linked-entity-name
                      " WHERE id = " (get value linked-entity))]
      (first (j/query db-spec [query]))))

  (get-one-parent-entity
    [db-spec entity-name linked-entity context params value]
    (let [linked-entity-name (name linked-entity)
          query  (str "SELECT * FROM " linked-entity-name
                      " WHERE " entity-name " = " (:id value))]
      (first (j/query db-spec [query])))))

(defn new-mysql-data-layer
  [db-spec]
  (map->MySQL db-spec))
