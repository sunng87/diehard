(ns diehard.timeout
  (:require [diehard.util :as util])
  (:import (java.time Duration)
           (net.jodah.failsafe Timeout Failsafe Policy)))

(defn- get-executor [timeout-policy]
  (Failsafe/with (into-array Policy [timeout-policy])))

(defn timeout
  ([^Duration duration]
   (timeout duration {}))
  ([^Duration duration, opts]
   (let [timeout-policy (Timeout/of duration)]
     (when (contains? opts :on-success)
       (.onSuccess timeout-policy (util/fn-as-consumer (:on-success opts))))
     (when (contains? opts :on-failure)
       (.onFailure timeout-policy (util/fn-as-consumer (:on-failure opts))))
     timeout-policy)))

(defn get-with-timeout [timeout block]
  (.get (get-executor timeout) (util/fn-as-checked-supplier block)))

(defn run-with-timeout [timeout block]
  (.run (get-executor timeout) (util/fn-as-runnable block)))

(defn get-async-with-timeout [timeout block]
  (.getAsync (get-executor timeout) (util/fn-as-checked-supplier block)))

(defn run-async-with-timeout [timeout block]
  (.runAsync (get-executor timeout) (util/fn-as-runnable block)))
