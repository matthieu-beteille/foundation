(ns foundation.templates.index
  (:require [hiccup.core :refer [html]]
            [environ.core :refer [env]]))

(def client-env-variables
  {:test 1})

(defn render
  [_]
  (let [bundle "/js/compiled/app.js"
        init (str "foundation.app.init();")]
    (html
     [:html
      {:lang "en"}
      [:head
       [:title "title"]
       [:base {:href "/"}]
       [:meta {:charset "utf-8"}]
       [:meta
        {:content
         "width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimal-ui",
         :name "viewport"}]]
      [:body [:div#app]]
      [:script (str "window.ENV={};"
                    (reduce (fn [res [key val]]
                              (str res "window.ENV[\"" (name key) "\"]=\"" val "\";"))
                            ""
                            client-env-variables))]
      [:script {:src bundle}]
      [:script init]])))
