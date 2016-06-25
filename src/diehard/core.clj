(ns diehard.core
  (:import [java.util.concurrent TimeUnit]
           [net.jodah.failsafe Failsafe RetryPolicy
            ExecutionContext]
           [net.jodah.failsafe.function Predicate BiPredicate
            ContextualCallable]
           [net.jodah.failsafe.util Duration]))

(defn verify-policy-map-keys [policy-map]
  )

(defn retry-policy-from-config [policy-map]
  (verify-policy-map-keys policy-map)
  (let [policy (RetryPolicy.)]
    (when (contains? policy-map :abort-if)
      (.abortIf policy (reify BiPredicate
                         (test [_ return-value thrown-exception]
                           ((:abort-if policy-map) return-value thrown-exception)))))
    (when (contains? policy-map :abort-on)
      (let [exps (:abort-on policy-map)]
        (if (fn? exps)
          (.abortOn policy (reify Predicate
                             (test [_ c]
                               (exps c))))
          (.abortOn policy (if (vector? exps) exps [exps])))))
    (when (contains? policy-map :abort-when)
      (.abortWhen policy (:abort-when policy-map)))
    (when (contains? policy-map :retry-if)
      (.retryIf policy (reify BiPredicate
                         (test [_ return-value thrown-exception]
                           ((:retry-if policy-map) return-value thrown-exception)))))
    (when (contains? policy-map :retry-on)
      (let [exps (:retry-on policy-map)]
        (if (fn? exps)
          (.retryOn policy (reify Predicate
                             (test [_ c]
                               (exps c))))
          (.retryOn policy (if (vector? exps) exps [exps])))))
    (when (contains? policy-map :retry-when)
      (.retryWhen policy (:retry-when policy-map)))
    (when (contains? policy-map :backoff-ms)
      (let [backoff-config (:backoff-ms policy-map)
            [delay max-delay unit multiplier] backoff-config]
        (if (nil? multiplier)
          (.withBackoff policy delay max-delay TimeUnit/MILLISECONDS)
          (.withBackoff policy delay max-delay TimeUnit/MILLISECONDS multiplier))))
    (when (contains? policy-map :delay-ms)
      (let [delay (:delay-ms policy)]
        (.withDelay policy delay TimeUnit/MILLISECONDS)))
    (when (contains? policy-map :max-duration-ms)
      (let [duration (:max-duration-ms policy)]
        (.withMaxDuration policy duration TimeUnit/MILLISECONDS)))
    (when (contains? policy-map :max-retries)
      (.withMaxRetries policy (:max-retries policy-map)))
    policy))

(def ^:dynamic *elapsed-time-ms*)
(def ^:dynamic *executions*)
(def ^:dynamic *start-time-ms*)

(defmacro with-retry [opt & body]
  `(let [retry-policy# (retry-policy-from-config ~opt)]
     (.. (Failsafe/with ^RetryPolicy retry-policy#)
         (get (reify ContextualCallable
                (call [_ ^ExecutionContext ctx#]
                  (binding [*elapsed-time-ms* (.toMillis ^Duration (.getElapsedTime ctx#))
                            *executions* (.getExecutions ctx#)
                            *start-time-ms* (.toMillis ^Duration (.getStartTime ctx#))]
                       ~@body)))))))
