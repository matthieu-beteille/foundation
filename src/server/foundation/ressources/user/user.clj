(ns foundation.ressources.user.user)

(def graphql-schema
  {:name :user
   :fields '{:username {:type String
                        :q true}}})
