(ns frontend.components.right-sidebar
  (:require [rum.core :as rum]
            [frontend.ui :as ui]
            [frontend.components.svg :as svg]
            [frontend.components.page :as page]
            [frontend.components.hiccup :as hiccup]
            [frontend.handler :as handler]
            [frontend.state :as state]
            [frontend.db :as db]
            [frontend.util :as util]
            [medley.core :as medley]
            [frontend.graph.vis :as vis]
            [clojure.string :as string]))

(rum/defc heading-cp < rum/reactive
  [heading]
  (let [id (:heading/uuid heading)
        heading (db/sub-heading id)]
    (hiccup/heading {:id id
                     :sidebar? true}
                    (assoc heading
                           :heading/show-page? true))))

(defn build-sidebar-item
  [db-id block-type block-data]
  (case block-type
    :page-ref
    ["Page reference"
     [:div.ml-2
      (heading-cp (:heading block-data))]]

    :heading-ref
    ["Block reference"
     [:div.ml-2
      (heading-cp (:heading block-data))]]

    :heading
    ["Block"
     [:div.ml-2
      (heading-cp block-data)]]

    :page
    (let [page-name (get-in block-data [:page :page/name])]
      [page-name (page/page {:parameters {:path {:name page-name}}
                             :sidebar? true})])

    ["" [:span]]))

(rum/defc sidebar-item < rum/reactive
  [idx db-id block-type block-data]
  (let [collapse? (state/sub [:ui/sidebar-collapsed-blocks db-id])]
    [:div.sidebar-item
     (let [[title component] (build-sidebar-item db-id block-type block-data)]
       [:div.flex.flex-col
        [:div.flex.flex-row.justify-between
         [:div.flex.flex-row.justify-center
          [:a.hover:text-gray-900.text-gray-500.flex.items-center.pl-1.pr-1
           {:on-click #(state/sidebar-block-toggle-collapse! db-id)}
           (if collapse?
             (svg/caret-right)
             (svg/caret-down))]
          [:div.ml-1 {:style {:font-size "1.5rem"}} title]]
         [:a.close.hover:text-gray-900.text-gray-500.flex.items-center
          {:on-click (fn []
                       (state/sidebar-remove-block! idx)
                       (when (empty? (state/get-sidebar-blocks))
                         (handler/hide-right-sidebar)))}
          svg/close]]
        [:div {:class (if collapse? "hidden" "initial")}
         component]])]))

(defn- get-page
  [match]
  (let [route-name (get-in match [:data :name])
        page (case route-name
               :page
               (get-in match [:path-params :name])

               :file
               (get-in match [:path-params :path])

               (util/journal-name))]
    (if page
      (util/url-decode (string/lower-case page)))))

(defonce fake-db-id "graph-db-id")
(defn render-graph
  [state]
  (let [match (:route-match @state/state)
        collapse? (get-in @state/state [:ui/sidebar-collapsed-blocks fake-db-id])]
    (when-not collapse?
      (when-let [page (get-page match)]
        (let [graph (db/build-page-graph page)]
          (vis/new-network "page-graph" graph))))
    state))

(rum/defc graph < rum/reactive
  {:did-mount render-graph
   :did-remount render-graph}
  []
  (let [match (state/sub :route-match)]
    [:div.sidebar-item.flex-col.flex-1
     [:div.flex.flex-row
      [:div.ml-2.font-bold "Graph"]]
     [:div#page-graph]]))

(rum/defcs sidebar < rum/reactive
  [state]
  (let [blocks (state/sub :sidebar/blocks)]
    [:div#right-sidebar.flex.flex-col.p-2.shadow-xs.overflow-y-auto.content {:style {:padding-bottom 300}}
     (for [[idx [db-id block-type block-data]] (medley/indexed blocks)]
       (rum/with-key
         (sidebar-item idx db-id block-type block-data)
         (str "sidebar-block-" idx)))
     (graph)]))
