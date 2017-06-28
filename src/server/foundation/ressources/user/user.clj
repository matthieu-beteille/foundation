(ns foundation.ressources.user.user)

(def graphql-schema
  {:name :user
   :fields '{:id {:type ID
                  :q true}
             :uid {:type String
                   :q true}}})
