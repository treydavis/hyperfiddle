(ns contrib.ui
  (:require
    [cats.monad.either :as either]
    [contrib.cljs-platform :refer [global!]]
    [contrib.pprint :refer [pprint-str]]
    [contrib.reactive :as r]
    [contrib.string :refer [safe-read-edn-string]]
    [contrib.ui.codemirror :refer [-codemirror]]
    [re-com.core :as re-com]
    [reagent.core :as reagent]
    [taoensso.timbre :as timbre]))


(defn ^:export checkbox [value change! & [props]]
  [:input {:type "checkbox"
           :checked value
           :disabled (:read-only props)
           :on-change change!}])

(defn ^:export code [value change! props]
  (let [defaults {:lineNumbers true
                  :matchBrackets true
                  :autoCloseBrackets true
                  :viewportMargin js/Infinity}
        props (merge defaults props)]
    ; There is nothing to be done about invalid css down here.
    ; You'd have to write CodeMirror implementation-specific css.
    [-codemirror value change! props]))

(defn ^:export code-block [props value change!]
  (let [props (if-not (nil? (:read-only props))
                (-> props
                    (dissoc :read-only)
                    (assoc :readOnly (:read-only props)))
                props)]
    [code value change! props]))

(defn ^:export code-inline-block [& args]
  (let [showing? (r/atom false)]
    (fn [props value change!]
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
                  :body (code-block props value change!)]]
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
