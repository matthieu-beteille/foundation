(ns foundation.utils
  (:require [buddy.sign.jws :as jws]))

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

(defn generate-token
  [user secret]
  (jws/sign user secret))
