(ns hyperfiddle.ui.select$                                  ; Namespace clashes with var hyperfiddle.ui/select
  (:require
    [cats.core :refer [mlet return]]
    [cats.monad.either :as either :refer [left right branch]]
    [contrib.ct :refer [unwrap]]
    [contrib.data :refer [unqualify]]
    [contrib.eval]
    [contrib.reactive :as r]
    [contrib.reader]
    [contrib.string :refer [blank->nil]]
    [datascript.parser :refer [FindRel FindColl FindTuple FindScalar Variable Aggregate Pull]]
    [hypercrud.browser.context :as context]
    [hyperfiddle.data :as data]
    [hyperfiddle.ui.util :refer [with-entity-change! writable-entity?]]
    [taoensso.timbre :as timbre]
    [cljs.spec.alpha :as s]
    [hyperfiddle.api :as hf]))

(defn ident->label [v]
  (cond
    ; A sensible default for userland whose idents usually share a long namespace.
    (instance? cljs.core/Keyword v) (name v)
    (and (vector? v) (= 1 (count v))) (str (first v))
    :else (str v)))

;(defn option-label-default' [row ctx]                                ; typechecks with keyword
;  ; spread-rows is external, f.
;  (->>
;    (for [[_ ctx] (hypercrud.browser.context/spread-fiddle ctx)
;          [_ ctx] (hypercrud.browser.context/spread-elements ctx)]
;      (let [[_ _ v] @(:hypercrud.browser/eav ctx)]
;        (condp = (type @(:hypercrud.browser/element ctx))
;          Variable [v]
;          Aggregate [v]
;          Pull (for [[_ ctx] (hypercrud.browser.context/spread-attributes ctx)]
;                 (let [[_ _ v] @(:hypercrud.browser/eav ctx)]
;                   (ident->label v))))))
;    (mapcat identity)
;    (remove nil?)
;    (interpose ", ")
;    (apply str)))

(defn option-label-default [row ctx]
  ; row is FindRel or FindCol
  (or
    (some-> (hypercrud.browser.context/row-key ctx row) ident->label)
    (clojure.string/join " " (vals row))))

(defn wrap-change-for-select [hf-change!]
  (fn [e]
    (-> (some->> (.-target.value e)
                 blank->nil
                 contrib.reader/read-edn-string+
                 ; todo why handle this exception? just throw and call it a day
                 ; instead of terminating on error, user now transacts a retract
                 (unwrap #(timbre/warn %)))
        hf-change!)))

(declare options-value-bridge+)
(declare select-error-cmp)

(defn select-html [_ ctx props]                             ; element, etc
  (branch
    (options-value-bridge+ (::value-ctx props) props)
    (fn [err]
      [select-error-cmp err])
    (fn [[select-props option-props]]
      ; hack in the selected value if we don't have options hydrated?
      ; Can't, since we only have the #DbId hydrated, and it gets complicated with relaton vs entity etc
      ; This is possible if the value matches the row-key (qfind) of options query
      (let [is-no-options (empty? (hf/data ctx))
            select-props (-> select-props
                             (assoc :value (str (hf/v (::value-ctx props)))) ; serialize v to string for dom roundtrip
                             (update :on-change wrap-change-for-select)
                             (dissoc :disabled)             ; Use :read-only instead to allow click to expand options
                             (update :read-only #(or % (:disabled select-props) is-no-options))
                             (update :class #(str % (if (:disabled option-props) " disabled"))))]
        [:select.ui (select-keys select-props [:value :class :style :on-change :read-only :on-click]) ;(dissoc props :option-label)
         ; .ui is because options are an iframe and need the pink box
         (conj
           (->> (hf/data ctx)
                (mapv (juxt #((:option-value select-props) % ctx) ; is lookup ref good yet?
                            #((:option-label select-props) % ctx)))
                (sort-by second)
                (map (fn [[id label]]
                       [:option (assoc option-props :key (str id) :value (str id)) label])))
           [:option (assoc option-props :key :blank :value "") "--"])])
      )))

(defn select-error-cmp [msg]
  [:span msg])

(defn compute-disabled [ctx props]
  (let [[e _ _] (context/eav ctx)]
    (or (boolean (:disabled props))
        (boolean (:read-only props))                        ; legacy
        (nil? e))))

(defn options-value-bridge+ [anchor-ctx #_target-ctx props] ; no longer need target-ctx because it has to work without options hydrated
  ; if select is qfind-level, is value tupled according to options qfind?
  ; if select is pull-level, is options qfind untupled?
  ; We must compare both ctxs to decide this.
  (let [select-props {:option-value (let [option-element (contrib.eval/ensure-fn (:option-element props first))]
                                      (fn [val ctx]
                                        ; in the case of tupled options, the userland view props must
                                        ; indicate which element is the entity has an identity which matches the anchor eav.
                                        (condp some [(unqualify (contrib.datomic/parser-type @(:hypercrud.browser/qfind ctx)))]
                                          #{:find-coll :find-scalar} (context/row-key ctx val)
                                          #{:find-rel :find-tuple} (option-element (context/row-key ctx val)))))
                      :option-label (fn [val ctx]
                                      (let [option-label (contrib.eval/ensure-fn (:option-label props option-label-default))]
                                        (option-label val ctx)))}
        ; The pulled v is always the select value, options must align
        ; There is always an attribute here because all widgets are attribute-centric
        value (hf/v anchor-ctx)]
    (cond
      ; Select options renderer is an eav oriented control like all controls, thus the
      ; anchor is always a pull context, never a qfind context. To see this is true, consider that you can't
      ; pick a tuple then write it into a database. You can only write through entities.
      (context/qfind-level? anchor-ctx)
      (left (str "No attribute in scope. eav: " (context/eav anchor-ctx)))

      #_#_(let [options (->> (hf/data target-ctx)
                             (map #((:option-value select-props) % target-ctx)))]
            (and value (not (contains? (set options) value))))
          [select-error-cmp (str "Value not seen in options: " value)]

      :else
      (let [select-props (merge {:value value               ; typeahead does not use this
                                 :on-change (with-entity-change! anchor-ctx)}
                                select-props
                                props)
            option-props {:disabled (compute-disabled anchor-ctx select-props)}]
        (right [select-props option-props])))))

(defn ^:export select
  ([ctx props]
   (select nil ctx props))
  ([_ ctx props]
   {:pre [ctx]}
   (assert (:options props) "select: :options prop is required")
    ; http://hyperfiddle.hyperfiddle.net/:database!options-list/
    ; http://hyperfiddle.hyperfiddle.site/:hyperfiddle.ide!domain/~entity('$domains',(:domain!ident,'hyperfiddle'))
   [hyperfiddle.ui/link (keyword (:options props))
    #_#_ctx (assoc ctx :hypercrud.ui/display-mode (r/pure :hypercrud.browser.browser-ui/user))
    ctx nil
    (assoc props
      :user-renderer select-html
      ::value-ctx ctx)]))

(defn typeahead-html [_ options-ctx props]                  ; element, etc
  (branch
    (options-value-bridge+ (::value-ctx props) props)
    (fn [err]
      [select-error-cmp err])
    (fn [[select-props option-props]]
      ; hack in the selected value if we don't have options hydrated?
      ; Can't, since we only have the #DbId hydrated, and it gets complicated with relaton vs entity etc
      ; This is possible if the value matches the row-key (qfind) of options query
      (let [is-no-options (empty? (hf/data options-ctx))
            select-props (-> select-props
                             (dissoc :disabled)             ; Use :read-only instead to allow click to expand options
                             (update :read-only #(or % (:disabled select-props) is-no-options))
                             (update :class #(str % (if (:disabled option-props) " disabled"))))]

        #_(mapv (juxt #((:option-value select-props) % options-ctx) ; is lookup ref good yet?
                      #((:option-label select-props) % options-ctx)))

        (reagent.core/create-element
          js/ReactBootstrapTypeahead.Typeahead
          #js {"labelKey" #((:option-label select-props) % options-ctx)
               "placeholder" (:placeholder select-props)
               "options" (to-array (hf/data options-ctx))
               "onChange" (fn [jv]
                            (->> jv                         ; #js [] means retracted
                                 js->clj                    ; foreign lib state is js array
                                 first                      ; foreign lib treats single-select as an array, like multi-select
                                 (hf/id options-ctx)
                                 ((:on-change select-props))))

               ; V might not be in options
               ; V might have different keys than options
               ; V might be tuple???

               "selected" (let [v (hf/data (::value-ctx props))] ; (let [v' ((:option-value select-props) v options-ctx)])
                            (to-array (if v [v] [])))})))))

(defn ^:export typeahead
  [ctx props]
  (assert (:options props) "select: :options prop is required")
  [hyperfiddle.ui/link (keyword (:options props)) ctx nil
   (assoc props
     :user-renderer typeahead-html
     ::value-ctx ctx)])
