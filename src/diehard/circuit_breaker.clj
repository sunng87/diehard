(ns diehard.circuit-breaker
  (:require [diehard.util :as u])
  (:import [java.time Duration]
           [java.util List]
           [dev.failsafe CircuitBreaker]
           [dev.failsafe.function CheckedBiPredicate]))

(def ^{:const true :no-doc true}
  allowed-circuit-breaker-option-keys
  #{:failure-threshold :failure-threshold-ratio :failure-threshold-ratio-in-period
    :failure-rate-threshold-in-period
    :success-threshold :success-threshold-ratio
    :delay-ms :timeout-ms

    :fail-if :fail-on :fail-when

    :on-open :on-close :on-half-open})

(defn circuit-breaker [opts]
  (u/verify-opt-map-keys-with-spec :circuit-breaker/circuit-breaker opts)
  (let [cb (CircuitBreaker/builder)]
    (when (contains? opts :fail-on)
      (.handle cb ^List (u/as-vector (:fail-on opts))))
    (when (contains? opts :fail-if)
      (.handleIf cb ^CheckedBiPredicate (u/bipredicate (:fail-if opts))))
    (when (contains? opts :fail-when)
      (.handleResult cb (:fail-when opts)))

    (when-let [delay (:delay-ms opts)]
      (.withDelay cb (Duration/ofMillis delay)))

    (when-let [failure-threshold (:failure-threshold opts)]
      (.withFailureThreshold cb failure-threshold))

    (when-let [[^int failures ^int executions] (:failure-threshold-ratio opts)]
      (.withFailureThreshold cb failures executions))

    (when-let [[failures executions period-ms]
               (:failure-threshold-ratio-in-period opts)]
      (.withFailureThreshold cb failures executions (Duration/ofMillis period-ms)))

    (when-let [[failure-rate executions period-ms]
               (:failure-rate-threshold-in-period opts)]
      (.withFailureRateThreshold cb failure-rate executions (Duration/ofMillis period-ms)))

    (when-let [success-threshold (:success-threshold opts)]
      (.withSuccessThreshold cb success-threshold))

    (when-let [[successes executions] (:success-threshold-ratio opts)]
      (.withSuccessThreshold cb successes executions))

    (when-let [on-open (:on-open opts)]
      (.onOpen cb (u/wrap-event-listener on-open)))

    (when-let [on-half-open (:on-half-open opts)]
      (.onHalfOpen cb (u/wrap-event-listener on-half-open)))

    (when-let [on-close (:on-close opts)]
      (.onClose cb (u/wrap-event-listener on-close)))

    (.build cb)))

(defn state
  "Get current state of this circuit breaker, values in `:open`, `:closed` and `half-open` "
  [^CircuitBreaker cb]
  (cond
    (.isOpen cb) :open
    (.isClosed cb) :closed
    :else :half-open))

(defn allow-execution?
  "Test if this circuit breaker allow code execution. The result is based
on current state:
  * `:open` will deny all execution requests
  * `:close` allows all executions
  * `:half-open` only allows some of execution requests"
  [^CircuitBreaker cb]
  (.allowsExecution cb))
