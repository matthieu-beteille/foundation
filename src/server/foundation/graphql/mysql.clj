(ns foundation.graphql.mysql
  (:require [foundation.graphql.utils :as utils]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [clojure.java.jdbc :as j]))

(def relations-with-fk #{:belongs-to})
(def relations-with-unique-fk #{:belongs-to})
(def needs-join-table #{:has-and-belongs-to-many})

(defn get-self-assoc-key
  [entity-name]
  (str "linked_" (name entity-name)))

(defn to-sql-type
  "graphql to mysql type mapping"
  [field {:keys [type]} is-fk?]
  (cond
    is-fk? "int"
    (= 'ID type)   (str "int NOT NULL AUTO_INCREMENT, PRIMARY KEY (`" (name field) "`)")
    (= 'Int type) "int"
    (= 'String type) "varchar(100)"))

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

(defn get-entity-by
  [db-spec entity field value]
  (first (j/query db-spec [(str "SELECT * FROM " (name entity)
                                " WHERE " (name field) " = ?") value])))

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
                                     (name field-type) "(`id`) ON DELETE CASCADE"))))))

(defn update-or-create!
  [db-spec {:keys [relations entity-name]} own-params nested-params [key val]]
  (let [relation   (key relations)
        fk         (utils/get-fk entity-name relation)
        query      (get-entity-by db-spec (name (:to relation)) fk (:id own-params))
        updated    (if query
                     (do (j/update! db-spec (name (:to relation)) val [(str fk " = ? ") (:id own-params)])
                         (merge query val))
                     (assoc val :id (:generated_id (j/insert! db-spec
                                                              (name (:to relation))
                                                              (assoc val
                                                                     (keyword fk)
                                                                     (:id own-params))))))]
    [key updated]))

(defn update-or-create-nested!
  [db-spec {:keys [entity-name relations] :as fschema} own-params nested-params]
  (->> nested-params
       (map (partial update-or-create! db-spec fschema own-params nested-params))
       (into {})))

(defn get-by-id
  [db-spec entity id]
  (first (j/query db-spec [(str "SELECT * FROM " (name entity)
                                " WHERE id = ?") id])))

(defn get-entity
  [db-spec entity-name params]
  (let [query (str "SELECT * FROM " (name entity-name)
                   (when params " WHERE ")
                   (->> params
                        (keys)
                        (map #(str " " (name %) " = ? "))
                        (interpose " AND ")
                        (apply str)))]
    (j/query db-spec (into [] (concat [query] (vals params))))))

(defn get-joined-entities
  "get entities linked by a joint table"
  [db-spec entity linked-entity join-table params id]
  (let [is-self-association? (= entity linked-entity)
        fk-name              (if is-self-association?
                               (get-self-assoc-key entity)
                               (name linked-entity))
        query (str "SELECT a.* FROM "  (name linked-entity) " a"
                   " JOIN " join-table " b ON a.id = b." fk-name
                   " WHERE b." (name entity) " = " id
                   (add-parameters "AND" params))]
    (j/query db-spec [query])))

(defn create-entity
  "create entity and any nested ones (in params)"
  [db-spec {:keys [entity-name fields relations]} params]
  (let [{:keys [own nested]} (utils/split-params params)
        inserted-id (->> own
                         (j/insert! db-spec entity-name)
                         (first)
                         :generated_key)]
    (doseq [[key val] nested]
      (let [relation   (key relations)
            fk         (utils/get-fk entity-name relation)]
        (j/insert! db-spec
                   (:to relation)
                   (assoc val fk inserted-id))))
    (assoc params :id inserted-id)))

(defn update-entity
  "update an entity and its nested fields (if don't exists, then created)"
  [db-spec {:keys [entity-name fields relations] :as fschema} params]
  (if-let [query (get-by-id db-spec entity-name (:id params))]
    (let [{:keys [own nested]} (utils/split-params params)
          updated-nested       (update-or-create-nested! db-spec fschema own nested)
          fields-to-update     (dissoc own :id)]
      (when-not (zero? (count fields-to-update))
        (j/update! db-spec entity-name fields-to-update ["id = ?" (:id params)]))
      (merge query params updated-nested))
    (resolve/resolve-as nil {:message "the entity you are trying to update doesn't exist"})))

(defn delete-entity
  [db-spec {:keys [entity-name] :as fschema} params]
  (let [entity (get-by-id db-spec entity-name (:id params))]
    (if entity
      (do (j/delete! db-spec entity-name ["id = ?" (:id params)])
          entity)
      (resolve/resolve-as nil {:message "the entity you are trying to delete doesn't exist"}))))
