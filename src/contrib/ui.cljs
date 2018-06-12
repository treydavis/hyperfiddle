(ns contrib.ui
  (:require
    [cats.monad.either :as either]
    [clojure.set :refer [rename-keys]]
    [contrib.cljs-platform :refer [global!]]
    [contrib.pprint :refer [pprint-str]]
    [contrib.reactive :as r]
    [contrib.string :refer [safe-read-edn-string]]
    [contrib.ui.codemirror :refer [-codemirror]]
    [contrib.ui.tooltip :refer [tooltip]]
    [contrib.ui.remark :as remark]
    [re-com.core :as re-com]
    [reagent.core :as reagent]
    [taoensso.timbre :as timbre]))


(defn ^:export checkbox [value change! & [props]]
  [:input {:type "checkbox"
           :class (:class props)
           :checked value
           :disabled (:read-only props)
           :on-change change!}])

(defn ^:export easy-checkbox [r label]
  [:label {:style {:font-weight "400"}}
   [:input {:type "checkbox"
            :checked @r :on-change #(swap! r not)}]
   (str " " label)])

(defn ^:export code [value ?change! props]
  (let [defaults {:lineNumbers true
                  :matchBrackets true
                  :autoCloseBrackets true
                  :viewportMargin js/Infinity}
        props (merge defaults props)]
    ; There is nothing to be done about invalid css down here.
    ; You'd have to write CodeMirror implementation-specific css.
    [-codemirror value ?change! props]))

(defn ^:export code-block [props value ?change!]
  (let [props (rename-keys props {:read-only :readOnly})]
    [code value ?change! props]))

(defn ^:export code-inline-block [& args]
  (let [showing? (r/atom false)]
    (fn [props value ?change!]
      [:div
       [re-com/popover-anchor-wrapper
        :showing? showing?
        :position :below-center
        :anchor [:a {:href "javascript:void 0;" :on-click #(swap! showing? not)} "edit"]
        :popover [re-com/popover-content-wrapper
                  :close-button? true
                  :on-cancel #(reset! showing? false)
                  :no-clip? true
                  :width "600px"
                  :body (code-block props value ?change!)]]
       " " value])))

(defn -edn [code-control value change! props]
  ; Must validate since invalid edn means there's no value to stage.
  ; Code editors are different since you are permitted to stage broken code (and see the error and fix it)
  (let [change! (fn [user-edn-str]
                  (-> (safe-read-edn-string user-edn-str)
                      (either/branch
                        (fn [e] (timbre/warn (pr-str e)) nil) ; report error
                        (fn [v] (change! v)))))]
    [code-control props (pprint-str value) change!]))       ; not reactive

(defn ^:export edn-block [value change! props]
  (-edn code-block value change! (assoc props :mode "clojure")))

(defn ^:export edn-inline-block [value change! props]
  (-edn code-inline-block value change! (assoc props :mode "clojure")))

(def ^:export ReactSlickSlider
  ; Prevents failure in tests, this is omitted from test preamble
  ; We don't have a way to differentiate tests-node from runtime-node, so check presence
  (if-let [reactSlickSlider (aget (global!) "reactSlickSlider")]
    (reagent/adapt-react-class reactSlickSlider)))

; this one doesn't have extensions and no hf deps,
; which helps break dump circular refs
(def -remark-instance (remark/remark))

(defn ^:export markdown [& args]
  (into [remark/markdown -remark-instance] args))

(defn radio-option [props]
  [tooltip {:label (:tooltip props)}
   [:label.radio-option {:class (if (:disabled props) "disabled")}
    [:input {:type "radio"
             :style {:width "auto"}
             :checked (= (:value props) (:target props))
             :on-change #((:change! props) (:target props))
             :disabled (:disabled props)}]
    (:label props)]])
