(ns foundation.graphql.data-layer
  (:require [clojure.java.jdbc :as j]
            [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [foundation.graphql.utils :as utils]))

(comment "for now we only have mysql but in the future we can easily swap the data layer, we'll just have to reimplement the following interface")

(defprotocol DataLayer
  "A simple protocol describing foundation's data layer"
  (init! [this fschemas]
    "effectul method to initialise things")
  (init-entity! [this fschema]
    "effectful method to initialise things related to an entity (ex: create tables with sql)")
  (query [this fschema context params value]
    "query method to retrieve an entity.")
  (query-one-nested-entity
    [this fschema field field-spec ctx params value]
    "returns a one-to-one linked entity. (:belongs-to side)")
  (query-one-parent-entity
    [this fschema field field-spec ctx params value]
    "returns a one-to-one parent entity. (:has-one side)")
  (query-many-nested-entities
    [this fschema field field-spec ctx params value]
    "returns multiple has-many nested entities")
  (query-many-to-many-entities
    [this fschema field field-spec ctx params value]
    "returns multiple many-to-any nested entities")
  (create-entity
    [this fschema context params value]
    "create entity")
  (update-entity
    [this fschema context params value]
    "create entity"))

(defn- to-sql-type
  "graphql to mysql type mapping"
  [field {:keys [type]} is-fk?]
  (cond
    is-fk? "int"
    (= 'ID type)   (str "int NOT NULL AUTO_INCREMENT, PRIMARY KEY (`" (name field) "`)")
    (= 'Int type) "int"
    (= 'String type) "varchar(100)"))

(def relations-with-fk #{:belongs-to})
(def relations-with-unique-fk #{:belongs-to})
(def needs-join-table #{:has-and-belongs-to-many})

(defn get-linked-entity
  [field-name field-spec]
  (if-let [through (:as field-spec)]
    (name through)
    (name field-name)))

(defn get-fk
  [entity-name field-spec]
  (if-let [through (:as field-spec)]
    (name through)
    (name entity-name)))

(defn generate-sql-fields
  [[field spec]]
  (let [field-name     (name field)
        is-relation?   (contains? spec :relation)
        should-add-fk? (relations-with-fk (:relation spec))
        is-unique-fk?  (relations-with-unique-fk (:relation spec)) ;; check that
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
  (str "linked_" (name entity-name)))

(defn create-join-table
  [{:keys [relation  relation-name type from to] :as param}]
  (let [table-name (name relation-name)
        entity-name (name from)
        linked-entity-name (name to)
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

(s/def ::dbtype string?)
(s/def ::dbname string?)
(s/def ::host string?)
(s/def ::user string?)
(s/def ::password string?)

(def data-layer (s/keys :req-un [::dbtype ::dbname ::host ::user ::password]))

(defn get-by-id
  [db-spec entity id]
  (first (j/query db-spec [(str "SELECT * FROM " (name entity)
                                " WHERE id = ?") id])))

(defn get-by
  [db-spec entity field value]
  (first (j/query db-spec [(str "SELECT * FROM " (name entity)
                                " WHERE " (name field) " = ?") value])))

; should probably use INSERT ... ON DUPLICATE KEYS UPDATE ... (to reduce the number of round trips to the db), but good enough for now.
(defn update-or-create-nested!
  [db-spec {:keys [entity-name relations]} own-params nested-params]
  (->> nested-params
       (map (fn [[key val]]
              (let [relation   (key relations)
                    fk         (get-fk entity-name relation)
                    query      (get-by db-spec (name (:to relation)) fk (:id own-params))
                    updated    (if query
                                 (do (j/update! db-spec
                                                (name (:to relation))
                                                val [(str fk " = ? ") (:id own-params)])
                                     (merge query val))
                                 (assoc val :id (:generated_id (j/insert! db-spec
                                                                          (name (:to relation))
                                                                          (assoc val
                                                                                 (keyword fk)
                                                                                 (:id own-params))))))]
                [key updated])))
       (into {})))

(defrecord MySQL [dbtype dbname host user password]
  DataLayer
  (init!
    [db-spec fschemas]
    (let [join-tables (->> fschemas
                           (mapcat :relations)
                           (map second)
                           (filter (comp (partial = :has-and-belongs-to-many)
                                         :relation))
                           (map create-join-table))]
      (j/db-do-commands db-spec join-tables)))

  (init-entity!
    [db-spec {:keys [entity-name fields]}]
    (let [main-table  (str "CREATE TABLE IF NOT EXISTS `" (name entity-name) "` ( "
                           (->> fields
                                (map generate-sql-fields)
                                (remove nil?)
                                (interpose ", ")
                                (apply str))
                           " ) ENGINE=INNODB;")]
      (j/execute! db-spec main-table)))

  (query
    [db-spec {:keys [entity-name]} context params value]
    (let [query (str "SELECT * FROM " (name entity-name)
                     (add-parameters "WHERE" params))]
      (j/query db-spec [query])))

  (query-one-parent-entity
    [db-spec fschema field field-spec context params value]
    (let [linked-entity-name (utils/get-entity-name (:type field-spec))
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE id = " (get value field)
                                  (add-parameters "AND" params))]
      (first (j/query db-spec [query]))))

  (query-one-nested-entity
    [db-spec {:keys [entity-name]} field field-spec context params value]
    (let [linked-entity-name (utils/get-entity-name (:type field-spec))
          fk                 (get-fk entity-name field-spec)
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE " fk " = " (:id value)
                                  (add-parameters "AND" params))]
      (first (j/query db-spec [query]))))

  (query-many-nested-entities
    [db-spec {:keys [entity-name]} field field-spec context params value]
    (let [linked-entity-name (utils/get-entity-name (:type field-spec))
          fk                 (get-fk entity-name field-spec)
          query              (str "SELECT * FROM " linked-entity-name
                                  " WHERE " fk " = " (:id value)
                                  (add-parameters "AND" params))]
      (j/query db-spec [query])))

  (query-many-to-many-entities
    [db-spec {:keys [entity-name]} field field-spec context params value]
    (let [join-table           (name (:relation-name field-spec))
          linked-entity-name   (utils/get-entity-name (:type field-spec))
          is-self-association? (= (name entity-name) linked-entity-name)
          fk-name              (if is-self-association?
                                 (get-self-assoc-key entity-name)
                                 linked-entity-name)
          query (str "SELECT a.* FROM "  linked-entity-name " a"
                     " JOIN " join-table " b ON a.id = b." fk-name
                     " WHERE b." (name entity-name) " = " (:id value)
                     (add-parameters "AND" params))]
      (j/query db-spec [query])))

  (create-entity
    [db-spec {:keys [entity-name fields relations]} context params value]
    (let [{:keys [own nested]} (utils/split-params params)
          inserted-id (->> own
                           (j/insert! db-spec entity-name)
                           (first)
                           :generated_key)]
      (doseq [[key val] nested]
        (let [relation   (key relations)
              fk         (get-fk entity-name relation)]
          (j/insert! db-spec
                     (:to relation)
                     (assoc val fk inserted-id))))
      (assoc params :id inserted-id)))

  (update-entity
    [db-spec {:keys [entity-name fields relations] :as fschema} context params value]
    (if-let [query (get-by-id db-spec entity-name (:id params))]
      (let [{:keys [own nested]} (utils/split-params params)
            updated-nested       (update-or-create-nested! db-spec fschema own nested)
            fields-to-update (dissoc own :id)]
        (when-not (zero? (count fields-to-update))
          (j/update! db-spec entity-name fields-to-update ["id = ?" (:id params)]))
        (merge query params updated-nested))
      (resolve/resolve-as nil {:message "the entity you are trying to update doesn't exist"}))))

(defn new-mysql-data-layer
  [db-spec]
  (map->MySQL db-spec))
