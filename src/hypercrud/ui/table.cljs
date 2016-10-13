(ns hypercrud.ui.table
  (:require [hypercrud.browser.links :as links]
            [hypercrud.client.core :as hc]
            [hypercrud.client.tx :as tx]
            [hypercrud.form.option :as option]
            [hypercrud.ui.auto-control :refer [auto-table-cell]]
            [reagent.core :as r]))


(defn build-col-heads [form]
  (map (fn [{:keys [:ident :prompt]}]
         [:td {:key ident} prompt])
       (:form/field form)))


(defn build-row-cells [form entity fieldless-widget-args]
  (->> (:form/field form)
       (map (fn [{:keys [ident] :as field}]
              [:td.truncate {:key ident}
               [auto-table-cell entity (:db/id form) (-> fieldless-widget-args
                                                         (update :expanded-cur #(% [ident]))
                                                         (assoc :field field))]]))))


(defn links-cell [entity form retract-entity! show-links? queries navigate-cmp]
  (let [open? (r/atom false)]
    (fn [entity form retract-entity! show-links? queries navigate-cmp]
      (if @open?
        [:div.link-menu
         (if show-links?
           (conj
             (->> (:form/link form)
                  (map (fn [{:keys [:link/ident :link/prompt :link/query]}]
                         (let [query (get queries query)
                               param-ctx {:entity entity}]
                           (navigate-cmp {:key ident
                                          :href (links/query-link query param-ctx)} prompt)))))
             (navigate-cmp {:key "view"
                            :href (links/entity-link (:db/id form) (:db/id entity))} "Entity View")
             (if retract-entity!
               [:span {:key "delete" :on-click #(retract-entity! (:db/id entity))} "Delete Row"])))
         [:span {:key "close" :on-click #(reset! open? false)} "Close"]]
        [:div {:on-click #(reset! open? true)} "⚙"]))))


(defn table-row [entity form retract-entity! show-links? {:keys [queries navigate-cmp] :as fieldless-widget-args}]
  [:tr
   [:td.link-cell {:key "links"}
    [links-cell entity form retract-entity! show-links? queries navigate-cmp]]
   (build-row-cells form entity fieldless-widget-args)])


(defn body [graph eids forms queries form expanded-cur stage-tx! navigate-cmp retract-entity! add-entity!]
  [:tbody
   (->> eids
        (map #(hc/entity graph %))
        (map (fn [entity]
               ^{:key (:db/id entity)}
               [table-row entity form retract-entity! true {:expanded-cur (expanded-cur [(:db/id entity)])
                                                            :forms forms
                                                            :graph graph
                                                            :navigate-cmp navigate-cmp
                                                            :queries queries
                                                            :stage-tx! stage-tx!}])))
   (let [id (hc/*temp-id!*)]
     ^{:key (hash eids)}
     [table-row {:db/id id} form retract-entity! false {:expanded-cur (expanded-cur [id])
                                                        :forms forms
                                                        :graph graph
                                                        :navigate-cmp navigate-cmp
                                                        :queries queries
                                                        :stage-tx! (fn [tx]
                                                                     (add-entity! id)
                                                                     (stage-tx! tx))}])])


(defn table-managed [graph eids forms queries form-id expanded-cur stage-tx! navigate-cmp retract-entity! add-entity!]
  (let [form (get forms form-id)]
    [:table.ui-table
     [:colgroup [:col {:span "1" :style {:width "20px"}}]]
     [:thead
      [:tr
       [:td.link-cell {:key "links"}]
       (build-col-heads form)]]
     [body graph eids forms queries form expanded-cur stage-tx! navigate-cmp retract-entity! add-entity!]]))


(defn- table* [graph eids forms queries form-id expanded-cur stage-tx! navigate-cmp retract-entity!]
  ;; resultset tables don't appear managed from the call site, but we need to provide a fake add-entity!
  ;; so we can create-new inline like a spreadsheet, which means we internally use the managed table
  (let [new-entities (r/atom [])
        add-entity! #(swap! new-entities conj %)]
    (fn [graph eids forms queries form-id expanded-cur stage-tx! navigate-cmp retract-entity!]
      (let [retract-entity! (fn [id]
                              (if (tx/tempid? id)
                                (swap! new-entities remove id)
                                (retract-entity! id)))]
        [table-managed graph (concat eids @new-entities) forms queries form-id expanded-cur stage-tx! navigate-cmp retract-entity! add-entity!]))))


(defn table [graph eids forms queries form-id expanded-cur stage-tx! navigate-cmp retract-entity]
  ^{:key (hc/t graph)}
  [table* graph eids forms queries form-id expanded-cur stage-tx! navigate-cmp retract-entity])


(defn table-pull-exp [form]
  (concat
    [:db/id]
    (map (fn [{:keys [ident valueType isComponent options] :as field}]
           (if (or isComponent (= valueType :ref))
             ; components and refs render nested entities, so pull another level deeper
             {ident [:db/id (option/label-prop options)]}

             ; otherwise just add the attribute to the list
             ident))
         (:form/field form))))


(defn field-queries [queries p-filler param-ctx
                     {:keys [cardinality valueType isComponent options] :as field}]
  (if (and (= valueType :ref)
           (= cardinality :db.cardinality/one)
           (not isComponent)
           (not (option/has-holes? options queries)))
    (option/get-query options queries p-filler (option/label-prop options) param-ctx)))


(defn option-queries [queries form p-filler param-ctx]
  (apply merge
         (map #(field-queries queries p-filler param-ctx %)
              (:form/field form))))


(defn query [query p-filler param-ctx forms queries form-id]
  (let [form (get forms form-id)]
    (merge
      (option-queries queries form p-filler param-ctx)
      {::query [(:query/value query) (p-filler query param-ctx) (table-pull-exp form)]})))