(ns diehard.core
  (:require [clojure.set :as set]
            [diehard.util :as u]
            [diehard.circuit-breaker :as cb])
  (:import [java.util List]
           [java.util.concurrent TimeUnit]
           [net.jodah.failsafe Failsafe RetryPolicy CircuitBreaker
            ExecutionContext FailsafeException Listeners SyncFailsafe]
           [net.jodah.failsafe.function CheckedBiFunction ContextualCallable]
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

    (.copy policy)

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

(defn ^:no-doc fallback [opts]
  (when-let [fb (:fallback opts)]
    (u/fn-as-bi-function
     (if-not (fn? fb)
       (constantly fb)
       fb))))

(defmacro ^{:doc "Predefined retry policy.
#### Available options

##### Retry criteria

* `:retry-when` retry when return value is given value
* `:retry-on` retry on given exception / exceptions(vector) were thrown
* `:retry-if` specify a function `(fn [return-value
  exception-thrown])`, retry if the function returns true

##### Retry abortion criteria

* `:abort-when` abort retry when return value is given value
* `:abort-on` abort retry on given exception / exceptions(vector) were
  thrown
* `:abort-if` specify a function `(fn [return-value
  exception-thrown])`, abort retry if the function returns true
* `:max-retries` abort retry when max attempts reached
* `:max-duration` abort retry when duration reached

##### Delay

* `:backoff-ms` specify a vector `[initial-delay-ms max-delay-ms
  multiplier]` to control the delay between each retry, the delay for
  **n**th retry will be `(max (* initial-delay-ms n) max-delay-ms)`
* `:delay-ms` use constant delay between each retry
* `:jitter-factor` random factor for each delay
* `:jitter-ms` random time `(-jitter-ms, jitter-ms)` adds to each delay

##### Use pre-defined policy

You can put together all those retry policies in a `defretrypolicy`.
And use `:policy` option in option map.

```clojure
(diehard/defretrypolicy policy
  {:max-retries 5
   :backoff-ms [1000 10000]})

(diehard/with-retry {:policy policy}
  ;; your code here
  )
```
"}
  defretrypolicy [name opts]
  `(do
     (u/verify-opt-map-keys ~opts ~policy-allowed-keys)
     (def ~name (retry-policy-from-config ~opts))))

(defmacro ^{:doc "Predefined listener.
##### Retry Listeners

* `:on-abort` accepts a function which takes `result`, `exception` as
  arguments, called when retry aborted
* `:on-complete` accepts a function which takes `result`, `exception` as
  arguments, called when exiting `retry` block
* `:on-failed-attempt` accepts a function which takes `result`,
  `exception` as arguments, called when execution failed (matches
  retry criteria)
* `:on-failure` accepts a function which takes `result`,
  `exception` as arguments, called when existing `retry` block with
  failure (matches retry criteria)
* `:on-success` accepts a function which takes `result` as arguments,
  called when existing `retry` block with success (mismatches retry
  criteria)
* `:on-retry` accepts a function which takes `result` as arguments,
  called when a retry attempted.

##### Use predefined listeners

```clojure
(diehard/deflistener listener
  {:on-retry (fn [return-value exception-thrown] (println \"retried\"))})

(diehard/with-retry {:policy policy :listener listener}
  ;; your code here
  )
```
"}
  deflistener [name opts]
  `(do
     (u/verify-opt-map-keys ~opts ~listener-allowed-keys)
     (def ~name (listeners-from-config ~opts))))

(defmacro ^{:doc "Retry policy protected block.
If the return value of or exception thrown from the code block matches
the criteria of your retry policy, the code block will be executed
again, until it mismatch the retry policy or matches the abort
criteria. The block will return or throw the value or exception from
the last execution.

#### Available options

##### Retry criteria

* `:retry-when` retry when return value is given value
* `:retry-on` retry on given exception / exceptions(vector) were thrown
* `:retry-if` specify a function `(fn [return-value
  exception-thrown])`, retry if the function returns true

##### Retry abortion criteria

* `:abort-when` abort retry when return value is given value
* `:abort-on` abort retry on given exception / exceptions(vector) were
  thrown
* `:abort-if` specify a function `(fn [return-value
  exception-thrown])`, abort retry if the function returns true
* `:max-retries` abort retry when max attempts reached
* `:max-duration` abort retry when duration reached

##### Delay

* `:backoff-ms` specify a vector `[initial-delay-ms max-delay-ms
  multiplier]` to control the delay between each retry, the delay for
  **n**th retry will be `(max (* initial-delay-ms n) max-delay-ms)`
* `:delay-ms` use constant delay between each retry
* `:jitter-factor` random factor for each delay
* `:jitter-ms` random time `(-jitter-ms, jitter-ms)` adds to each delay

##### Use pre-defined policy

You can put together all those retry policies in a `defretrypolicy`.
And use `:policy` option in option map.

```clojure
(diehard/defretrypolicy policy
  {:max-retries 5
   :backoff-ms [1000 10000]})

(diehard/with-retry {:policy policy}
  ;; your code here
  )
```

##### Retry Listeners

* `:on-abort` accepts a function which takes `result`, `exception` as
  arguments, called when retry aborted
* `:on-complete` accepts a function which takes `result`, `exception` as
  arguments, called when exiting `retry` block
* `:on-failed-attempt` accepts a function which takes `result`,
  `exception` as arguments, called when execution failed (matches
  retry criteria)
* `:on-failure` accepts a function which takes `result`,
  `exception` as arguments, called when existing `retry` block with
  failure (matches retry criteria)
* `:on-success` accepts a function which takes `result` as arguments,
  called when existing `retry` block with success (mismatches retry
  criteria)
* `:on-retry` accepts a function which takes `result` as arguments,
  called when a retry attempted.

##### Use predefined listeners

```clojure
(diehard/deflistener listener
  {:on-retry (fn [return-value exception-thrown] (println \"retried\"))})

(diehard/with-retry {:policy policy :listener listener}
  ;; your code here
  )
```

##### Fallback

* `:fallback` fallback value or handler function when retry blocks
  exists with failure.

```clojure
;; return 5 when attempts failure
(with-retry {:fallback 5}
  ;; ...
  )

;; return fallback handler function result when failed
(with-retry {:fallback (fn [value exception]
                         ;; value: value returned from last attempt
                         ;; exp: exception thrown from last attempt
                         )}
  ;; ...
  )

```
"}
  with-retry [opt & body]
  `(do
     (u/verify-opt-map-keys ~opt ~allowed-keys)
     (let [retry-policy# (retry-policy-from-config ~opt)
           listeners# (listeners-from-config ~opt)
           fallback# (fallback ~opt)

           failsafe# (.. (Failsafe/with ^RetryPolicy retry-policy#)
                         (with ^Listeners listeners#))
           failsafe# (if fallback#
                       (.withFallback ^SyncFailsafe failsafe#
                                      ^CheckedBiFunction fallback#)
                       failsafe#)]
       (try
         (.get ^SyncFailsafe failsafe#
               ^ContextualCallable (reify ContextualCallable
                                     (call [_ ^ExecutionContext ctx#]
                                       (with-context ctx#
                                         ~@body))))
         (catch FailsafeException e#
           (throw (.getCause e#)))))))

(defmacro ^{:doc "Define a circuit breaker with option.
#### Available options

There options are available when creating circuit breaker in
`defcircuitbreaker`.

##### Failure criteria

All the three `fail` options share same meaning with similar option in
retry block.

* `:fail-if`
* `:fail-on`
* `:fail-when`
* `:timeout-ms` while give all you code a timeout is best practice in
  application level, circuit breaker also provides a timeout for
  marking a long running block as failure


##### Delay and threshold

* `:delay-ms` required. the delay for `:open` circuit breaker to turn
  into `:half-open`.
* `:failure-threshold`
* `:failure-threshold-ratio`
* `:success-threshold`
* `:success-threshold-ratio` All these four option is to determine at
  what condition the circuit breaker is open.

##### Listeners

* `:on-open` a function to be called when state goes `:open`
* `:on-close` a function to be called when state goes `:closed`
* `:on-half-open` a function to be called when state goes `:half-open`
"}
  defcircuitbreaker [name opts]
  `(def ~name (cb/circuit-breaker ~opts)))

(defmacro ^{:doc "Circuit breaker protected block.

```clj
(require '[diehard.core :as diehard])

(diehard/defcircuitbreaker test-cb {:failure-threshold-ratio [35 50]
                                    :delay-ms 1000})

(diehard/with-circuit-breaker test-cb
  ;; your protected code here
  )
```

In this scenario, if the circuit breaker protected code block fails 35
times in 50 executions, as defined in `:failure-threshold-ratio`, the
`test-cb` is entering into `:open` state. When circuit breaker is
open, all execution requests will be rejected immediately.

After `:delay-ms`, the circuit breaker will be `:half-open`. At the
moment, 50 execution will be allowed, to test the state to see if it's
recovered. If success, the circuit breaker is back to `:closed`
state. Otherwise, it will be `:open` again.

You can always check circuit breaker state with
`diehard.circuitbreaker/state`.
"}
  with-circuit-breaker [cb & body]
  `(let [opts# (if-not (map? ~cb)
                 {:circuitbreaker ~cb}
                 ~cb)
         cb# (:circuitbreaker opts#)
         failsafe# (Failsafe/with ^CircuitBreaker cb#)]
     (try
       (.get ^SyncFailsafe failsafe# ^Callable (fn [] ~@body))
       (catch FailsafeException e#
         (throw (.getCause e#))))))
