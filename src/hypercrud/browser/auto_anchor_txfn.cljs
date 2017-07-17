(ns hypercrud.browser.auto-anchor-txfn
  (:require [hypercrud.browser.auto-anchor-formula :refer [auto-entity-dbid]]
            [hypercrud.client.tx :as tx]
            [hypercrud.types.DbId :refer [->DbId]]
            [hypercrud.util.core :refer [pprint-str]]))


(defn auto-txfn [anchor]
  (let [{m :anchor/managed? e :anchor/find-element a :anchor/attribute ident :anchor/ident} anchor
        form (cond
               ; link-query's always have a find-element (e)
               ; link-entity's never do (which is semantically unsound since semantically they always do)

               (and m a)                                    ; attr create
               `(fn [ctx# get-tx-from-modal#]
                  (let [new-dbid# (auto-entity-dbid ctx#)]
                    {:tx (concat
                           (get-tx-from-modal#)
                           (tx/edit-entity (-> ctx# :entity :db/id)
                                           (-> ctx# :attribute :attribute/ident)
                                           []
                                           [new-dbid#]))}))

               (and m e (not a))
               `(fn [ctx# get-tx-from-modal#]
                  (let [branch# (auto-entity-dbid ctx#)
                        id'# (-> (js/Date.now) - str)
                        ; alter the dbid before transacting so it can be reused.
                        ; This has to happen at a side-effect point and will cause a hydrate
                        ; so we do it when we already have to hydrate.
                        tx-from-modal'# (->> (get-tx-from-modal#)
                                             (mapv (fn [[op# dbid# a# v#]]
                                                     (if (= (:id dbid#) branch#)
                                                       [op# (->DbId id'# (:conn-id dbid#)) a# v#]
                                                       [op# dbid# a# v#]))))]
                    {:tx tx-from-modal'#}))

               :else nil)]
    (some-> form pprint-str)))
