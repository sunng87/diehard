(ns diehard.spec
  (:require [clojure.spec.alpha :as s])
  (:import [net.jodah.failsafe RetryPolicy CircuitBreaker]))

;; retry policy

(s/def :retry/policy #(instance? RetryPolicy %))
(s/def :retry/circuit-breaker #(instance? CircuitBreaker %))

(s/def :retry/retry-if fn?)
(s/def :retry/retry-on #(instance? Exception %))
(s/def :retry/retry-when (constantly true))

(s/def :retry/abort-if fn?)
(s/def :retry/abort-on #(instance? Exception %))
(s/def :retry/abort-when (constantly true))

(s/def :retry/backoff-ms int?)
(s/def :retry/max-retries int?)
(s/def :retry/max-duration-ms int?)
(s/def :retry/delay-ms int?)

(s/def :retry/jitter-factor double?)
(s/def :retry/jitter-ms int?)

(s/def :retry/listener inst?)
(s/def :retry/on-abort fn?)
(s/def :retry/on-complete fn?)
(s/def :retry/on-failed-attempt fn?)
(s/def :retry/on-failure fn?)
(s/def :retry/on-retry fn?)
(s/def :retry/on-retries-exceeded fn?)
(s/def :retry/on-success fn?)

;; constant or function
(s/def :retry/fallback (constantly true))

(s/def :retry/retry-policy
  (s/keys :opt-un [:retry/policy :retry/circuit-breaker
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
(s/def :circuit-breaker/fail-on #(instance? Exception %))
(s/def :circuit-breaker/fail-when (constantly true))

(s/def :circuit-breaker/failure-threshold int?)
(s/def :circuit-breaker/failure-threshold-ratio (s/tuple int? int?))
(s/def :circuit-breaker/success-threshold int?)
(s/def :circuit-breaker/success-threshold-ratio (s/tuple int? int?))

(s/def :circuit-breaker/delay-ms int?)
(s/def :circuit-breaker/timeout-ms int?)

(s/def :circuit-breaker/on-open fn?)
(s/def :circuit-breaker/on-close fn?)
(s/def :circuit-breaker/on-half-open fn?)

(s/def :circuit-breaker/circuit-breaker
  (s/keys :opt-un [:circuit-breaker/fail-if
                   :circuit-breaker/fail-on
                   :circuit-breaker/fail-when

                   :circuit-breaker/failure-threshold
                   :circuit-breaker/failure-threshold-ratio
                   :circuit-breaker/success-threshold
                   :circuit-breaker/success-threshold-ratio

                   :circuit-breaker/on-open
                   :circuit-breaker/on-half-open
                   :circuit-breaker/on-close

                   :circuit-breaker/delay-ms
                   :circuit-breaker/timeout-ms]))

;; rate limiter
(s/def :rate-limiter/rate int?)
(s/def :rate-limiter/max-cached-tokens int?)

(s/def :rate-limiter/rate-limiter-new
  (s/keys :req-un [:rate-limiter/rate]
          :opt-un [:rate-limiter/max-cached-tokens]))

(s/def :rate-limiter/ratelimiter inst?)
(s/def :rate-limiter/max-wait-ms int?)
(s/def :rate-limiter/permits int?)

(s/def :rate-limiter/rate-limiter-block
  (s/keys :req-un [:rate-limiter/ratelimiter]
          :opt-un [:rate-limiter/max-wait-ms
                   :rate-limiter/permits]))

;; bulkhead
(s/def :bulkhead/concurrency int?)
(s/def :bulkhead/bulkhead-new
  (s/keys :req-un [:bulkhead/concurrency]))

(s/def :bulkhead/max-wait-ms int?)

(s/def :bulkhead/bulkhead-block
  (s/keys :req-un [:bulkhead/bulkhead]
          :opt-un [:bulkhead/max-wait-ms]))
