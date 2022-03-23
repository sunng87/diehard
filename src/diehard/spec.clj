(ns diehard.spec
  (:require [clojure.spec.alpha :as s]
            [diehard.rate-limiter :as dr]
            [diehard.bulkhead :as db])
  (:import [dev.failsafe RetryPolicy CircuitBreaker]))

;; copied from https://groups.google.com/forum/#!topic/clojure/fti0eJdPQJ8
(defmacro only-keys
  [& {:keys [req req-un opt opt-un] :as args}]
  `(s/merge (s/keys ~@(apply concat (vec args)))
            (s/map-of ~(set (concat req
                                    (map (comp keyword name) req-un)
                                    opt
                                    (map (comp keyword name) opt-un)))
                      any?)))

(def is-throwable-class?
  #(isa? % Throwable))

;; retry policy

(s/def :retry/policy #(instance? RetryPolicy %))
(s/def :retry/circuit-breaker #(instance? CircuitBreaker %))

(s/def :retry/retry-if fn?)
(s/def :retry/retry-on
  (s/or :single is-throwable-class?
        :multi (s/coll-of is-throwable-class?)))
(s/def :retry/retry-when any?)

(s/def :retry/abort-if fn?)
(s/def :retry/abort-on
  (s/or :single is-throwable-class?
        :multi (s/coll-of is-throwable-class?)))
(s/def :retry/abort-when any?)

(s/def :retry/backoff-ms
  (s/or :single int?
        :tuple2 (s/tuple int? int?)
        :tuple3 (s/tuple int? int? double?)))
(s/def :retry/max-retries int?)
(s/def :retry/max-duration-ms int?)
(s/def :retry/delay-ms int?)

(s/def :retry/jitter-factor double?)
(s/def :retry/jitter-ms int?)

(s/def :retry/on-abort fn?)
(s/def :retry/on-complete fn?)
(s/def :retry/on-failed-attempt fn?)
(s/def :retry/on-failure fn?)
(s/def :retry/on-retry fn?)
(s/def :retry/on-retries-exceeded fn?)
(s/def :retry/on-success fn?)

;; constant or function
(s/def :retry/fallback any?)

(s/def :retry/retry-policy-new
  (only-keys :opt-un [:retry/policy :retry/circuit-breaker
                      :retry/retry-if :retry/retry-on :retry/retry-when
                      :retry/abort-if :retry/abort-on :retry/abort-when
                      :retry/backoff-ms :retry/max-retries :retry/max-duration-ms
                      :retry/delay-ms :retry/jitter-factor :retry/jitter-ms

                      :retry/on-abort :retry/on-complete :retry/on-failed-attempt
                      :retry/on-failure :retry/on-retry :retry/on-retries-exceeded
                      :retry/on-success :retry/listener]))

(s/def :retry/retry-block
  (only-keys :opt-un [:retry/policy :retry/circuit-breaker
                      :retry/retry-if :retry/retry-on :retry/retry-when
                      :retry/abort-if :retry/abort-on :retry/abort-when
                      :retry/backoff-ms :retry/max-retries :retry/max-duration-ms
                      :retry/delay-ms :retry/jitter-factor :retry/jitter-ms

                      :retry/on-abort :retry/on-complete :retry/on-failed-attempt
                      :retry/on-failure :retry/on-retry :retry/on-retries-exceeded
                      :retry/on-success :retry/listener

                      :retry/fallback]))

;; circuit breaker

(s/def :circuit-breaker/fail-if fn?)
(s/def :circuit-breaker/fail-on
  (s/or :single is-throwable-class?
        :multi (s/coll-of is-throwable-class?)))
(s/def :circuit-breaker/fail-when any?)

(s/def :circuit-breaker/failure-threshold int?)
(s/def :circuit-breaker/failure-threshold-ratio (s/tuple int? int?))
(s/def :circuit-breaker/failure-threshold-ratio-in-period (s/tuple int? int? int?))
(s/def :circuit-breaker/failure-rate-threshold-in-period (s/tuple int? int? int?))
(s/def :circuit-breaker/success-threshold int?)
(s/def :circuit-breaker/success-threshold-ratio (s/tuple int? int?))

(s/def :circuit-breaker/delay-ms int?)
(s/def :circuit-breaker/timeout-ms int?)

(s/def :circuit-breaker/on-open fn?)
(s/def :circuit-breaker/on-close fn?)
(s/def :circuit-breaker/on-half-open fn?)

(s/def :circuit-breaker/circuit-breaker
  (only-keys :opt-un [:circuit-breaker/fail-if
                      :circuit-breaker/fail-on
                      :circuit-breaker/fail-when

                      :circuit-breaker/failure-threshold
                      :circuit-breaker/failure-threshold-ratio
                      :circuit-breaker/failure-threshold-ratio-in-period
                      :circuit-breaker/failure-rate-threshold-in-period
                      :circuit-breaker/success-threshold
                      :circuit-breaker/success-threshold-ratio

                      :circuit-breaker/on-open
                      :circuit-breaker/on-half-open
                      :circuit-breaker/on-close

                      :circuit-breaker/delay-ms
                      :circuit-breaker/timeout-ms]))

;; timeout
(s/def :timeout/on-success fn?)
(s/def :timeout/on-failure fn?)
(s/def :timeout/timeout-ms int?)
(s/def :timeout/interrupt? boolean?)
(s/def :timeout/timeout-new
  (only-keys :req-un [:timeout/timeout-ms]
             :opt-un [:timeout/interrupt?
                      :timeout/on-success
                      :timeout/on-failure]))

;; rate limiter
(s/def :rate-limiter/rate int?)
(s/def :rate-limiter/max-cached-tokens int?)

(s/def :rate-limiter/rate-limiter-new
  (only-keys :req-un [:rate-limiter/rate]
             :opt-un [:rate-limiter/max-cached-tokens]))

(s/def :rate-limiter/ratelimiter #(satisfies? dr/IRateLimiter %))
(s/def :rate-limiter/max-wait-ms int?)
(s/def :rate-limiter/permits int?)

(s/def :rate-limiter/rate-limiter-block
  (only-keys :req-un [:rate-limiter/ratelimiter]
             :opt-un [:rate-limiter/max-wait-ms
                      :rate-limiter/permits]))

;; bulkhead
(s/def :bulkhead/concurrency int?)
(s/def :bulkhead/bulkhead-new
  (only-keys :req-un [:bulkhead/concurrency]))

(s/def :bulkhead/bulkhead #(satisfies? db/IBulkhead %))
(s/def :bulkhead/max-wait-ms int?)

(s/def :bulkhead/bulkhead-block
  (only-keys :req-un [:bulkhead/bulkhead]
             :opt-un [:bulkhead/max-wait-ms]))
