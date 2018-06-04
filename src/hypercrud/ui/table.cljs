(ns hypercrud.ui.table
  (:require [contrib.css :refer [css-slugify classes]]
            [contrib.reactive :as r]
            [hypercrud.browser.system-link :refer [system-link?]]
            [hypercrud.browser.context :as context]
            [hypercrud.browser.link :as link]
            [hypercrud.ui.auto-control :refer [auto-control control-props]]
            [hypercrud.ui.connection-color :as connection-color]
            [hypercrud.ui.control.link-controls :as link-controls]
            [hypercrud.ui.label :refer [label]]))


(defn attr-sortable? [fe attribute ctx]
  (if-let [dbname (some-> (:source-symbol fe) str)]
    (let [{:keys [:db/cardinality :db/valueType]} @(r/cursor (:hypercrud.browser/schemas ctx) [dbname attribute])]
      (and
        (= (:db/ident cardinality) :db.cardinality/one)
        ; ref requires more work (inspect label-prop)
        (contains? #{:db.type/keyword
                     :db.type/string
                     :db.type/boolean
                     :db.type/long
                     :db.type/bigint
                     :db.type/float
                     :db.type/double
                     :db.type/bigdec
                     :db.type/instant
                     :db.type/uuid
                     :db.type/uri
                     :db.type/bytes
                     :db.type/code}
                   (:db/ident valueType))))
    (not (nil? fe))))

(defn relation-keyfn [relation] (hash (map #(or (:db/id %) %) relation)))

(defn sort-fn [sort-col ctx relations-val]
  (let [[sort-fe-pos sort-attr direction] @sort-col
        fe @(r/cursor (:hypercrud.browser/ordered-fes ctx) [sort-fe-pos])
        attr (->> (map :attribute (:fields fe))
                  (filter #(= % sort-attr))
                  first)]
    (if (attr-sortable? fe attr ctx)
      (let [sort-fn (if sort-attr
                      #(get-in % [sort-fe-pos sort-attr])
                      #(get % sort-fe-pos))]
        (sort-by sort-fn
                 (case direction
                   :asc #(compare %1 %2)
                   :desc #(compare %2 %1))
                 relations-val))
      relations-val)))

; sorting currently breaks click handling in popovers
(defn links-dont-break-sorting? [path ctx]
  (->> @(:hypercrud.browser/links ctx)
       (filter (link/same-path-as? path))
       (remove :link/dependent?)
       (link/options-processor)
       (not-any? link/popover-link?)))

(defn LinkCell [ctx]                                        ; Drive by markdown also (forces unify)
  [(if (:relation ctx) :td.link-cell :th.link-cell)
   (->> (r/unsequence (:hypercrud.browser/ordered-fes ctx))
        (mapcat (fn [[fe i]]
                  (let [ctx (context/find-element ctx i)
                        ctx (context/cell-data ctx)
                        path [(:fe-pos ctx)]]
                    (link-controls/anchors path (not (nil? (:relation ctx))) ctx)
                    ; inline entity-anchors are not yet implemented
                    #_(link-controls/iframes path dependent? ctx)))))])

(defn col-head [ctx]
  (let [field (:hypercrud.browser/field ctx)
        path [(:fe-pos ctx) (:hypercrud.browser/attribute ctx)]
        sortable? (and (attr-sortable? @(:hypercrud.browser/find-element ctx) (:attribute field) ctx)
                       @(r/track links-dont-break-sorting? path ctx))
        sort-direction (let [[sort-fe-pos sort-attr direction] @(::sort-col ctx)]
                         (if (and (= (:fe-pos ctx) sort-fe-pos) (= sort-attr (:attribute field)))
                           direction))
        on-click (fn []
                   (if sortable?
                     (reset! (::sort-col ctx)
                             (case sort-direction
                               :asc [(:fe-pos ctx) (:attribute field) :desc]
                               :desc nil
                               [(:fe-pos ctx) (:attribute field) :asc]))))]
    [:th {:class (classes "hyperfiddle-table-cell"
                          (css-slugify (:hypercrud.browser/attribute ctx))
                          (if sortable? "sortable")
                          (some-> sort-direction name))
          :style {:background-color (connection-color/connection-color ctx)}
          :on-click on-click}
     [label ctx]
     [:div.anchors
      (link-controls/anchors path false ctx link/options-processor)
      (link-controls/iframes path false ctx link/options-processor)]]))

(defn Field "Form fields are label AND value. Table fields are label OR value."
  ([ctx] (Field nil ctx nil))
  ([?f ctx props]
   (if (:relation ctx)
     [:td {:class (classes (:class props) "hyperfiddle-table-cell" "truncate")
           :style {:border-color
                   (let [shadow-link @(r/fmap system-link? (r/cursor (:cell-data ctx) [:db/id]))]
                     (if-not shadow-link (connection-color/connection-color ctx)))}}
      ; todo unsafe execution of user code: control
      [(or ?f (auto-control ctx)) @(:value ctx) ctx (merge (control-props ctx) props)]]
     [col-head ctx])))

(defn FeFields "table labels OR values for a find-element" [ctx] ; should be row - fields (labels or values)
  (let [ctx (context/cell-data ctx)]
    (->> (r/cursor (:hypercrud.browser/find-element ctx) [:fields])
         (r/unsequence :id)
         (mapv (fn [[field id]]
                 (let [ctx (context/field ctx @field)
                       ctx (context/value ctx)]
                   ^{:key id} [Field ctx]))))))

(defn Relation [ctx]
  (->> (r/unsequence (:hypercrud.browser/ordered-fes ctx))
       (mapcat (fn [[fe i]]
                 (FeFields (context/find-element ctx i))))))

(defn Row [ctx]
  [:tr (Relation ctx) [LinkCell ctx]])

(defn Table-inner [ctx]
  (let [sort-col (r/atom nil)]
    (fn [ctx]
      (let [ctx (assoc ctx :layout (:layout ctx :table)
                           ::sort-col sort-col)]
        [:table.ui-table
         [:thead (Row ctx)]
         [:tbody
          (->> (r/fmap (r/partial sort-fn sort-col ctx) (:relations ctx))
               (r/unsequence relation-keyfn)
               (map (fn [[relation k]]
                      ^{:key k} [Row (context/relation ctx relation)]))
               (doall))]]))))

(defn Table [ctx]
  ; Relations could be in the ctx, but I chose not for now, because the user renderers don't get to see it i think.
  ; The framework does the mapping and calls userland with a single relation the right number of times.
  [Table-inner ctx])
