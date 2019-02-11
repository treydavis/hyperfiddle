(ns hyperfiddle.service.pedestal.global-basis
  (:refer-clojure :exclude [sync])
  (:require
    [hyperfiddle.io.core :as io]
    [hyperfiddle.io.datomic.sync :refer [sync]]
    [hyperfiddle.io.global-basis :refer [global-basis]]
    [hyperfiddle.service.http :as http-service :refer [handle-route]]
    [hyperfiddle.service.pedestal.interceptors :refer [platform->pedestal-req-handler]]
    [promesa.core :as p]))


(deftype IOImpl [domain jwt ?subject]
  io/IO
  (global-basis [io]
    (global-basis io domain))

  (sync [io dbnames]
    (p/do* (sync domain dbnames))))

(defmethod handle-route :global-basis [handler env req]
  (platform->pedestal-req-handler env (partial http-service/global-basis-handler ->IOImpl) req))
