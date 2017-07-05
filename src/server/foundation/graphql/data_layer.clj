(ns foundation.graphql.data-layer
  (:require [clojure.java.jdbc :as j]
            [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [foundation.graphql.utils :as utils]
            [foundation.graphql.mysql :as mysql]))

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
    "create entity")
  (delete-entity
    [this fschema context params value]
    "delete entity (and nested)"))

(s/def ::dbtype string?)
(s/def ::dbname string?)
(s/def ::host string?)
(s/def ::user string?)
(s/def ::password string?)

(def data-layer (s/keys :req-un [::dbtype ::dbname ::host ::user ::password]))

(defrecord MySQL [dbtype dbname host user password]
  DataLayer
  (init!
    [db-spec fschemas]
    (let [join-tables (->> fschemas
                           (mapcat :relations)
                           (map second)
                           (filter (comp (partial = :has-and-belongs-to-many)
                                         :relation))
                           (map mysql/create-join-table))]
      (j/db-do-commands db-spec join-tables)))

  (init-entity!
    [db-spec {:keys [entity-name fields]}]
    (let [main-table  (str "CREATE TABLE IF NOT EXISTS `" (name entity-name) "` ( "
                           (->> fields
                                (map mysql/generate-sql-fields)
                                (remove nil?)
                                (interpose ", ")
                                (apply str))
                           " ) ENGINE=INNODB;")]
      (j/execute! db-spec main-table)))

  (query
    [db-spec {:keys [entity-name]} context params value]
    (mysql/get-entity db-spec entity-name params))

  (query-one-parent-entity
    [db-spec fschema field field-spec context params value]
    (let [linked-entity (field (:types fschema))
          id (get value field)]
      (first (mysql/get-entity db-spec linked-entity (merge {:id id} params)))))

  (query-one-nested-entity
    [db-spec {:keys [entity-name] :as fschema} field field-spec context params value]
    (let [linked-entity (field (:types fschema))
          fk            (utils/get-fk entity-name field-spec)]
      (first (mysql/get-entity db-spec linked-entity (merge {fk (:id value)}
                                                      params)))))

  (query-many-nested-entities
    [db-spec {:keys [entity-name types]} field field-spec context params value]
    (let [linked-entity (field types)
          fk            (utils/get-fk entity-name field-spec)]
      (mysql/get-entity db-spec linked-entity (merge {fk (:id value)}
                                               params))))

  (query-many-to-many-entities
    [db-spec {:keys [entity-name types]} field field-spec context params value]
    (let [join-table  (name (:relation-name field-spec))
          linked-entity (field types)]
      (mysql/get-joined-entities db-spec
                                 entity-name
                                 linked-entity
                                 join-table
                                 params
                                 (:id value))))

  (create-entity
    [db-spec fschema context params value]
    (mysql/create-entity db-spec fschema params))

  (update-entity
    [db-spec fschema context params value]
    (mysql/update-entity db-spec fschema params))

  (delete-entity
    [db-spec fschema context params value]
    (mysql/delete-entity db-spec fschema params)))

(defn new-mysql-data-layer
  [db-spec]
  (map->MySQL db-spec))
