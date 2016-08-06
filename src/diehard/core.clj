(ns diehard.core
  (:require [clojure.set :as set]
            [diehard.util :as u]
            [diehard.circuit-breaker :as cb])
  (:import [java.util List]
           [java.util.concurrent TimeUnit]
           [net.jodah.failsafe Failsafe RetryPolicy CircuitBreaker
            ExecutionContext FailsafeException Listeners]
           [net.jodah.failsafe.function ContextualCallable]
           [net.jodah.failsafe.util Duration]))

(def ^:const ^:no-doc
  policy-allowed-keys #{:policy

                        :retry-if :retry-on :retry-when
                        :abort-if :abort-on :abort-when
                        :backoff-ms :max-retries :max-duration-ms :delay-ms
                        :jitter-factor :jitter-ms})

(def ^:const ^:no-doc listener-allowed-keys
  #{:listener
    :on-abort :on-complete :on-failed-attempt
    :on-failure :on-retry :on-retries-exceeded :on-success})

(def ^:const ^:no-doc allowed-keys
  (set/union policy-allowed-keys listener-allowed-keys #{:fallback}))

(defn ^:no-doc retry-policy-from-config [policy-map]
  (if-let [policy (:policy policy-map)]

    policy

    (let [policy (RetryPolicy.)]
      (when (contains? policy-map :abort-if)
        (.abortIf policy (u/bipredicate (:abort-if policy-map))))
      (when (contains? policy-map :abort-on)
        (.abortOn policy (u/predicate-or-value (:abort-on policy-map))))
      (when (contains? policy-map :abort-when)
        (.abortWhen policy (:abort-when policy-map)))
      (when (contains? policy-map :retry-if)
        (.retryIf policy (u/bipredicate (:retry-if policy-map))))
      (when (contains? policy-map :retry-on)
        (.retryOn policy (u/predicate-or-value (:retry-on policy-map))))
      (when (contains? policy-map :retry-when)
        (.retryWhen policy (:retry-when policy-map)))
      (when (contains? policy-map :backoff-ms)
        (let [backoff-config (:backoff-ms policy-map)
              [delay max-delay multiplier] backoff-config]
          (if (nil? multiplier)
            (.withBackoff policy delay max-delay TimeUnit/MILLISECONDS)
            (.withBackoff policy delay max-delay TimeUnit/MILLISECONDS multiplier))))
      (when-let [delay  (:delay-ms policy-map)]
        (.withDelay policy delay TimeUnit/MILLISECONDS))
      (when-let [duration (:max-duration-ms policy-map)]
        (.withMaxDuration policy duration TimeUnit/MILLISECONDS))
      (when-let [retries (:max-retries policy-map)]
        (.withMaxRetries policy retries))
      (when-let [jitter (:jitter-factor policy-map)]
        (.withJitter policy jitter))
      (when-let [jitter (:jitter-ms policy-map)]
        (.withJitter policy jitter TimeUnit/MILLISECONDS))
      policy)))

(def ^{:dynamic true
       :doc "Available in retry block. Contexual value represents time elasped since first attempt"}
  *elapsed-time-ms*)
(def ^{:dynamic true
       :doc "Available in retry block. Contexual value represents execution times"}
  *executions*)
(def ^{:dynamic true
       :doc "Available in retry block. Contexual value represents first attempt time"}
  *start-time-ms*)

(defmacro ^:no-doc with-context [ctx & body]
  `(binding [*elapsed-time-ms* (.toMillis ^Duration (.getElapsedTime ~ctx))
             *executions* (long (.getExecutions ~ctx))
             *start-time-ms* (.toMillis ^Duration (.getStartTime ~ctx))]
     ~@body))

(defn ^:no-doc listeners-from-config [policy-map]
  (if-let [listener (:listener policy-map)]

    listener

    (proxy [Listeners] []
      (onAbort [result exception context]
        (when-let [handler (:on-abort policy-map)]
          (with-context ^ExecutionContext context
            (handler result exception))))
      (onComplete [result exception context]
        (when-let [handler (:on-complete policy-map)]
          (with-context ^ExecutionContext context
            (handler result exception))))
      (onFailedAttempt [result exception context]
        (when-let [handler (:on-failed-attempt policy-map)]
          (with-context ^ExecutionContext context
            (handler result exception))))
      (onFailure [result exception context]
        (when-let [handler (:on-failure policy-map)]
          (with-context ^ExecutionContext context
            (handler result exception))))
      (onRetry [result exception context]
        (when-let [handler (:on-retry policy-map)]
          (with-context  ^ExecutionContext context
            (handler result exception))))
      (onSuccess [result context]
        (when-let [handler (:on-success policy-map)]
          (with-context ^ExecutionContext context
            (handler result))))
      (onRetriesExceeded [result exception]
        (when-let [handler (:on-retries-exceeded policy-map)]
          (handler result exception))))))

(defn fallback [opts]
  (when-let [fb (:fallback opts)]
    (u/fn-as-bi-function
     (if-not (fn? fb)
       (constantly fb)
       fb))))

(defmacro defretrypolicy [name opts]
  `(do
     (u/verify-opt-map-keys ~opts ~policy-allowed-keys)
     (def ~name (retry-policy-from-config ~opts))))

(defmacro deflistener [name opts]
  `(do
     (u/verify-opt-map-keys ~opts ~listener-allowed-keys)
     (def ~name (listeners-from-config ~opts))))

(defmacro with-retry [opt & body]
  `(do
     (u/verify-opt-map-keys ~opt ~allowed-keys)
     (let [retry-policy# (retry-policy-from-config ~opt)
           listeners# (listeners-from-config ~opt)
           fallback# (fallback ~opt)

           failsafe# (.. (Failsafe/with ^RetryPolicy retry-policy#)
                         (with ^Listeners listeners#))
           failsafe# (if fallback# (.withFallback failsafe# fallback#) failsafe#)]
       (try
         (.get failsafe#
               (reify ContextualCallable
                 (call [_ ^ExecutionContext ctx#]
                   (with-context ctx#
                     ~@body))))
         (catch FailsafeException e#
           (throw (.getCause e#)))))))

(defmacro defcircuitbreaker [name opts]
  `(def ~name (cb/circuit-breaker ~opts)))

(defmacro with-circuit-breaker [cb & body]
  `(try
     (.. (Failsafe/with ^CircuitBreaker ~cb)
         (get (fn [] ~@body)))
     (catch FailsafeException e#
       (throw (.getCause e#)))))
