(ns diehard.timeout
  (:require [diehard.util :as util])
  (:import [java.time Duration]
           [net.jodah.failsafe Timeout]))

(defn timeout-policy-from-config-map [opts]
  (util/verify-opt-map-keys-with-spec :timeout/timeout-new opts)
  (let [duration (Duration/ofMillis (:timeout-ms opts))
        timeout-policy (Timeout/of duration)]
    (when (contains? opts :interrupt?)
      (.withCancel timeout-policy true))
    (when (contains? opts :on-success)
      (.onSuccess timeout-policy (util/fn-as-consumer (:on-success opts))))
    (when (contains? opts :on-failure)
      (.onFailure timeout-policy (util/fn-as-consumer (:on-failure opts))))
    timeout-policy))
