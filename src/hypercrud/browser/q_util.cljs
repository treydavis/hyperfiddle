(ns hypercrud.browser.q-util
  (:require [cats.core :as cats :refer [mlet]]
            [cats.monad.either :as either]
            [clojure.string :as string]
            [hypercrud.client.core :as hc]
            [hypercrud.util.core :as util]
            [hypercrud.util.string :as hc-string]))


(defn parse-holes [q]
  {:pre [(vector? q)]
   :post [(vector? %)]}
  (->> (util/parse-query-element q :in)
       ;; the string conversion should happen at the other side imo
       (mapv str)))

(defn parse-param-holes [q]
  (->> (parse-holes q)
       (remove #(string/starts-with? % "$"))))

(defn safe-parse-query-validated [link]
  (mlet [q (hc-string/memoized-safe-read-edn-string (:link-query/value link))]
    (if (vector? q)
      (cats/return q)
      (either/left {:message (str "Invalid query '" (pr-str q) "', only vectors supported")}))))

(defn build-dbhole-lookup [ctx]
  (->> (get-in ctx [:respository :source/databases])
       (map (juxt :dbhole/name #(hc/db (:peer ctx) (:dbhole/uri %) (:branch ctx))))
       (into {})))

(defn form-pull-exp [form]
  (if form
    (->> (:form/field form)
         (map :field/attribute)
         (concat [:db/id])
         distinct
         (remove nil?))
    ['*]))
