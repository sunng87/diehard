(ns diehard.timeout
  (:require [diehard.util :as util])
  (:import [java.time Duration]
           [dev.failsafe Timeout]))

(defn timeout-policy-from-config-map [opts]
  (util/verify-opt-map-keys-with-spec :timeout/timeout-new opts)
  (let [duration (Duration/ofMillis (:timeout-ms opts))
        timeout-policy (Timeout/builder duration)]
    (when (:interrupt? opts)
      (.withInterrupt timeout-policy))
    (when (contains? opts :on-success)
      (.onSuccess timeout-policy (util/wrap-event-listener (:on-success opts))))
    (when (contains? opts :on-failure)
      (.onFailure timeout-policy (util/wrap-event-listener (:on-failure opts))))
    (.build timeout-policy)))
