(ns diehard.core
  (:require [diehard.util :as u]
            [diehard.circuit-breaker :as cb])
  (:import [java.util List]
           [java.util.concurrent TimeUnit]
           [net.jodah.failsafe Failsafe RetryPolicy CircuitBreaker
            ExecutionContext FailsafeException Listeners]
           [net.jodah.failsafe.function ContextualCallable]
           [net.jodah.failsafe.util Duration]))

(def ^:const allowed-keys #{:retry-if :retry-on :retry-when
                            :abort-if :abort-on :abort-when
                            :backoff-ms :max-retries :max-duration-ms :delay-ms

                            :on-abort :on-complete :on-failed-attempt
                            :on-failure :on-retry :on-success})

(defn retry-policy-from-config [policy-map]
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
    policy))

(def ^:dynamic *elapsed-time-ms*)
(def ^:dynamic *executions*)
(def ^:dynamic *start-time-ms*)

(defmacro with-context [ctx & body]
  `(binding [*elapsed-time-ms* (.toMillis ^Duration (.getElapsedTime ~ctx))
             *executions* (long (.getExecutions ~ctx))
             *start-time-ms* (.toMillis ^Duration (.getStartTime ~ctx))]
     ~@body))

(defn listeners-from-config [policy-map]
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
          (handler result))))))

(defmacro with-retry [opt & body]
  `(do
     (u/verify-opt-map-keys ~opt ~allowed-keys)
     (let [retry-policy# (retry-policy-from-config ~opt)
           listeners# (listeners-from-config ~opt)]
       (try
         (.. (Failsafe/with ^RetryPolicy retry-policy#)
             (with ^Listeners listeners#)
             (get (reify ContextualCallable
                    (call [_ ^ExecutionContext ctx#]
                      (with-context ctx#
                        ~@body)))))
         (catch FailsafeException e#
           (throw (.getCause e#)))))))

(defmacro defcircuitbreaker [name opts]
  `(def ~name (cb/circuit-breaker ~opts)))

(defmacro with-circuit-breaker [cb & body]
  `(.. (Failsafe/with ^CircuitBreaker cb)
       (get (fn [] ~@body))))
