(ns foundation.app
  (:require [reagent.core :as reagent]
            [foundation.handlers]
            [foundation.subs]
            [re-frame.core :as rf]))

(defn app-root []
  (let [greeting (rf/subscribe [:get-greeting])]
    (fn []
      [:div {:style {:flex-direction "column" :margin 40 :align-items "center"}}
       [:img {:src nil
              :style {:width 200
                      :height 200}}]
       [:span {:style {:font-size 30 :font-weight "100" :margin-bottom 20 :text-align "center"}} @greeting]
       [:div {:style {:background-color "#999" :padding 10 :border-radius 5}
              :on-click #(rf/dispatch [:set-greeting "this is the best thing"])}
        [:span {:style {:color "white"
                        :text-align "center"
                        :font-weight "bold"}} "press"]]])))

(defn ^:export init []
  (reagent/render [:div "lol"] (.getElementById js/document "app")))
