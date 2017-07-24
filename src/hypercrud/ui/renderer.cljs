(ns hypercrud.ui.renderer
  (:require [cats.core :refer [mlet return]]
            [cats.monad.exception :as exception :refer [try-on]]
            [hypercrud.platform.safe-render :refer [safe-user-renderer]]
            [hypercrud.browser.anchor :as anchor]
            [hypercrud.compile.eval :refer [eval-str']]
            [hypercrud.util.core :as util :refer [pprint-str]]))


(defn empty-string-to-nil [s]
  (if (empty? s) nil s))

(defn user-renderer [param-ctx]
  (let [attr (:attribute param-ctx)]
    (or
      (empty-string-to-nil (get-in param-ctx [:fields (:attribute/ident attr) :renderer]))
      (empty-string-to-nil (-> attr :attribute/renderer))
      (empty-string-to-nil (-> attr :attribute/hc-type :hc-type/renderer)))))

(defn user-render [maybe-field anchors props param-ctx]
  (let [user-fn' (if-let [user-fn-str (user-renderer param-ctx)]
                   (eval-str' user-fn-str))]
    [:div.value
     (if (exception/failure? user-fn')
       [:pre (pprint-str (.-e user-fn'))]
       (let [anchor-lookup (->> anchors
                                (filter :anchor/ident)      ; cannot lookup nil idents
                                (mapv (juxt #(-> % :anchor/ident) identity))
                                (into {}))
             param-ctx (assoc param-ctx                     ; Todo unify link-fn with widget interface or something
                         :link-fn
                         (fn [ident label param-ctx]
                           (let [anchor (get anchor-lookup ident)
                                 props (anchor/build-anchor-props anchor param-ctx)] ; needs param-ctx to run formulas
                             [(:navigate-cmp param-ctx) props label])))]
         ; Same interface as auto-control widgets.
         ; pass value only as scope todo
         [safe-user-renderer @user-fn' maybe-field anchors props param-ctx]))]))
