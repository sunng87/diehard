(ns diehard.circuit-breaker
  (:require [diehard.util :as u])
  (:import [java.util.concurrent TimeUnit]
           [net.jodah.failsafe CircuitBreaker]))

(def ^:const allowed-circuit-breaker-option-keys
  #{:failure-threshold :failure-threshold-ratio
    :success-threshold :success-threshold-ratio
    :delay-ms :timeout-ms

    :fail-if :fail-on :fail-when

    :on-open :on-close :on-half-open})

(defn circuit-breaker [opts]
  (u/verify-opt-map-keys opts allowed-circuit-breaker-option-keys)
  (let [cb (CircuitBreaker.)]
    (when (contains? opts :fail-on)
      (.failOn cb (u/predicate-or-value (:fail-on opts))))
    (when (contains? opts :fail-if)
      (.failIf cb (u/bipredicate (:fail-if opts))))
    (when (contains? opts :fail-when)
      (.failWhen cb (:fail-when opts)))
    (when-let [timeout (:timeout-ms opts)]
      (.withTimeout cb timeout TimeUnit/MILLISECONDS))

    (when-let [delay (:delay-ms opts)]
      (.withDelay cb delay TimeUnit/MILLISECONDS))

    (when-let [failure-threshold (:failure-threshold opts)]
      (.withFailureThreshold cb failure-threshold))

    (when-let [[failures executions] (:failure-threshold-ratio opts)]
      (.withFailureThreshold cb failures executions))

    (when-let [success-threshold (:success-threshold opts)]
      (.withSuccessThreshold cb success-threshold))

    (when-let [[successes executions] (:success-threshold-ratio opts)]
      (.withSuccessThreshold cb successes executions))

    (when-let [on-open (:on-open opts)]
      (.onOpen cb (u/fn-as-runnable on-open)))

    (when-let [on-half-open (:on-half-open opts)]
      (.onHalfOpen cb (u/fn-as-runnable on-half-open)))

    (when-let [on-close (:on-close opts)]
      (.onClose cb (u/fn-as-runnable on-close)))
    cb))

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
