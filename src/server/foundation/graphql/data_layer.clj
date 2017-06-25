(ns foundation.graphql.data-layer
  (:require [clojure.java.jdbc :as j]
            [foundation.utils :as utils]))

(comment "for now we only have mysql but in the future we can easily swap the data layer, we'll just have to reimplement the following interface")

(defprotocol DataLayer
  "A simple protocol describing foundation's data layer"
  (init! [this schemas]
    "effectul method to initialise things")
  (init-each! [this entity-name schema]
    "effectful method to initialise things related to an entity (ex: create tables with sql)")
  (query [this entity-name context params value]
    "query method to retrieve an entity.")
  (query-one-nested-entity
    [this entity-name field field-spec ctx params value]
    "returns a one-to-one linked entity. (:belongs-to side)")
  (query-one-parent-entity
    [this entity-name field field-spec ctx params value]
    "returns a one-to-one parent entity. (:has-one side)")
  (query-many-nested-entities
    [this entity-name field field-spec ctx params value]
    "returns multiple has-many nested entities")
  (query-many-to-many-entities
    [this entity-name field field-spec ctx params value]
    "returns multiple many-to-any nested entities"))

(defn- to-sql-type
  "graphql to mysql type mapping"
  [field {:keys [type]} is-fk?]
  (cond
    is-fk? "int"
    (= 'ID type)   (str "int NOT NULL AUTO_INCREMENT, PRIMARY KEY (`" (name field) "`)")
    (= 'Int type) "int"
    (= 'String type) "varchar(100)"))

(def relations-with-fk #{:belongs-to})
(def needs-join-table #{:has-and-belongs-to-many})

(defn get-fk
  [entity-name field-spec]
  (if-let [through (:as field-spec)]
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

(defn add-parameters
  ([params]
   (add-parameters "" params))
  ([prefix params]
   (if params
     (str " " prefix " "
          (->> params
               (map (fn [[key value]] (str (name key) " = "  (utils/quote-unquote value))))
               (interpose " AND ")
               (apply str)))
     "")))

(defn get-self-assoc-key
  [entity-name]
  (str "linked_" entity-name ))

(defn create-join-table
  [entity-name [field {:keys [relation-name relation type]}]]
  (let [table-name (name relation-name)
        linked-entity-name (utils/get-entity-name type)
        is-self-association? (= entity-name linked-entity-name)
        fk-name (if is-self-association?
                  (get-self-assoc-key entity-name)
                  linked-entity-name)]
    (str "CREATE TABLE IF NOT EXISTS `" table-name "` ( "
         entity-name " int NOT NULL, "
         fk-name " int NOT NULL, "
         "FOREIGN KEY (`" entity-name "`) REFERENCES " entity-name "(`id`),"
         "FOREIGN KEY (`" fk-name "`) REFERENCES " linked-entity-name "(`id`)"
         ");")))

(defrecord MySQL [dbtype dbname host user password]
  DataLayer
  (init!
    [db-spec schemas]
    (let [join-tables (mapcat #(->> (get-in % [:schema :fields])
                                    (filter (comp needs-join-table
                                                  :relation
                                                  second))
                                    (map (partial create-join-table (name (:name %)))))
                              schemas)]
      (j/db-do-commands db-spec join-tables)))

  (init-each!
    [db-spec entity-name schema]
    (let [main-table  (str "CREATE TABLE IF NOT EXISTS `" entity-name "` ( "
                           (->> schema
                                (map generate-sql-fields)
                                (remove nil?)
                                (interpose ", ")
                                (apply str))
                           " ) ENGINE=INNODB;")]
      (j/execute! db-spec main-table)))

  (query
    [db-spec entity-name context params value]
    (let [query (str "SELECT * FROM " entity-name
                     (add-parameters "WHERE" params))]
      (j/query db-spec [query])))

  (query-one-parent-entity
    [db-spec entity-name field field-spec context params value]
    (let [linked-entity-name (utils/get-entity-name (:type field-spec))
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE id = " (get value field)
                                  (add-parameters "AND" params))]
      (first (j/query db-spec [query]))))

  (query-one-nested-entity
    [db-spec entity-name field field-spec context params value]
    (let [linked-entity-name (utils/get-entity-name (:type field-spec))
          fk                 (get-fk entity-name field-spec)
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE " fk " = " (:id value)
                                  (add-parameters "AND" params))]
      (first (j/query db-spec [query]))))

  (query-many-nested-entities
    [db-spec entity-name field field-spec context params value]
    (let [linked-entity-name (utils/get-entity-name (:type field-spec))
          fk                 (get-fk entity-name field-spec) 
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE " fk " = " (:id value)
                                  (add-parameters "AND" params))]
      (j/query db-spec [query])))

  (query-many-to-many-entities
    [db-spec entity-name field field-spec context params value]
    (let [join-table           (name (:relation-name field-spec))
          linked-entity-name   (utils/get-entity-name (:type field-spec))
          is-self-association? (= entity-name linked-entity-name)
          fk-name              (if is-self-association?
                                 (get-self-assoc-key entity-name)
                                 linked-entity-name)
          query (str "SELECT a.* FROM "  linked-entity-name " a"
                     " JOIN " join-table " b ON a.id = b." fk-name
                     " WHERE b." entity-name " = " (:id value)
                     (add-parameters "AND" params))]
      (j/query db-spec [query]))))

(defn new-mysql-data-layer
  [db-spec]
  (map->MySQL db-spec))

