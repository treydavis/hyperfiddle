(ns hyperfiddle.service.lib.jwt
  (:require
    #?(:clj [clojure.walk :as walk])
    #?(:clj [contrib.data :refer [map-values]])
    #?(:cljs [jsonwebtoken :as jwt]))
  #?(:clj
     (:import (com.auth0.jwt JWT)
              (com.auth0.jwt.algorithms Algorithm))))

(defn build-verifier [env]
  #?(:clj  (let [jwt-verifier (-> (Algorithm/HMAC256 (:AUTH0_CLIENT_SECRET env))
                                  (JWT/require)
                                  (.withIssuer (str (:AUTH0_DOMAIN env) "/"))
                                  (.build))]
             (fn [token]
               (-> (.verify jwt-verifier token)
                   (.getClaims)
                   (->> (map-values #(.as % Object)))
                   (walk/keywordize-keys))))
     :cljs (fn [token]
             (some-> (.verify jwt token (:AUTH0_CLIENT_SECRET env))
                     (js->clj :keywordize-keys true)))))

(defn sign [claims secret & [options]]
  #?(:clj  (throw (RuntimeException. "Not Implemented"))    ; todo
     :cljs (.sign jwt (clj->js claims) secret (clj->js options))))
