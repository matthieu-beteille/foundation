(ns foundation.data-layer
  (:require [clojure.java.jdbc :as j]))

(comment "for now we only have mysql but in the future we can easily swap the data layer, we'll just have to reimplement the following interface")

(defprotocol DataLayer
  "A simple protocol describing foundation's data layer"
  (init! [this entity-name schema]
    "effectful method to initialise things related to an entity (ex: create tables with sql)")
  (query [this entity-name context params value]
    "query method to retrieve an entity.")
  (query-one-nested-entity
    [this entity-name field field-spec ctx params value]
    "returns a one-to-one linked entity. (:belongs-to side")
  (query-one-parent-entity
    [this entity-name field field-spec ctx params value]
    "returns a one-to-one parent entity. (:has-one side)")
  (query-many-nested-entities
    [this entity-name field field-spec ctx params value]
    "returns multiple has-many nested entities "))

(defn- quote-unquote
  [value]
  (let [quote (when (string? value) "\"")]
    (str quote value quote)))

(defn- to-sql-type
  "graphql to mysql type mapping"
  [field {:keys [type]} is-fk?]
  (cond
    is-fk? "int"
    (= 'ID type)   (str "int NOT NULL AUTO_INCREMENT, PRIMARY KEY (`" (name field) "`)")
    (= 'Int type) "int"
    (= 'String type) "varchar(100)"))

(def relations-with-fk #{:belongs-to})

(defn get-fk
  [entity-name field-spec]
  (if-let [through (:through field-spec)]
    (name through)
    entity-name))

(defn generate-sql-fields
  [[field spec]]
  (let [field-name     (name field)
        is-relation?   (contains? spec :relation)
        should-add-fk? (relations-with-fk (:relation spec))
        field-type     (:type spec)]
    (when (or should-add-fk? (not is-relation?))
      (str "`"  field-name "` " (to-sql-type field spec should-add-fk?)
           (when should-add-fk? (str ", FOREIGN KEY (`"
                                field-name "`) REFERENCES "
                                (name field-type) "(`id`)"))))))

(defn get-entity-name
  [type]
  (cond
    (keyword? type) (name type)
    (list? type)    (name (second type))
    :default
    (throw (Exception. "entity type should be another entity identifier (or a list of another entity identifier"))))

(defrecord MySQL [db-spec]
  DataLayer
  (init!
    [db-spec entity-name schema]
    (let [query
          (str "CREATE TABLE IF NOT EXISTS `" entity-name "` ( "
               (->> schema
                    (map generate-sql-fields)
                    (remove nil?)
                    (interpose ", ")
                    (apply str))
               " ) ENGINE=INNODB;")]
      (j/execute! db-spec query)))

  (query
    [db-spec entity-name context params value]
    (let [query (str "SELECT * FROM " entity-name " WHERE "
                     (->> params
                          (map (fn [[key value]] (str (name key) " = "  (quote-unquote value))))
                          (interpose " AND ")
                          (apply str)))]
      (j/query db-spec [query])))

  (query-one-parent-entity
    [db-spec entity-name field field-spec context params value]
    (let [linked-entity-name (get-entity-name (:type field-spec))
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE id = " (get value field))]
      (first (j/query db-spec [query]))))

  (query-one-nested-entity
    [db-spec entity-name field field-spec context params value]
    (let [linked-entity-name (get-entity-name (:type field-spec))
          fk                 (get-fk entity-name field-spec)
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE " fk " = " (:id value))]
      (first (j/query db-spec [query]))))

  (query-many-nested-entities
    [db-spec entity-name field field-spec context params value]
    (let [linked-entity-name (get-entity-name (:type field-spec))
          fk                 (get-fk entity-name field-spec) 
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE " fk " = " (:id value))]
      (j/query db-spec [query]))))

(defn new-mysql-data-layer
  [db-spec]
  (map->MySQL db-spec))
