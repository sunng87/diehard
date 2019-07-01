# diehard

[![Build
Status](https://travis-ci.org/sunng87/diehard.svg?branch=master)](https://travis-ci.org/sunng87/diehard)
[![Clojars](https://img.shields.io/clojars/v/diehard.svg?maxAge=2592000)](https://clojars.org/diehard)
[![license](https://img.shields.io/github/license/sunng87/diehard.svg?maxAge=2592000)]()
[![Donate](https://img.shields.io/badge/donate-liberapay-yellow.svg)](https://liberapay.com/Sunng/donate)


Clojure library to provide safety guard to your application.
Some of the functionality is wrapper over
[Failsafe](https://github.com/jhalterman/failsafe).

Note that from 0.7 diehard uses Clojure 1.9 and spec.alpha for
configuration validation. Clojure 1.8 users could stick with diehard
`0.6.0`.

## Usage

A quick example for diehard usage.

### Retry block

A retry block will re-execute inner forms when retry criteria matches.

```clojure
(require '[diehard.core :as dh])
(dh/with-retry {:retry-on TimeoutException
                :max-retries 3}
  (fetch-data-from-the-moon))
```

### Circuit breaker

A circuit breaker will track the execution of inner block and skip
execution if the open condition triggered.

```clojure
(require '[diehard.core :as dh])

(defcircuitbreaker my-cb {:failure-threshold-ratio [8 10]
                          :delay-ms 1000})

(dh/with-circuit-breaker my-cb
  (fetch-data-from-the-moon))
```

### Rate limiter

A rate limiter protects your code block to run limited times per
second. It will block or throw exception depends on your
configuration.

```clojure
(require '[diehard.core :as dh])

(defratelimiter my-rl {:rate 100})

(dh/with-rate-limiter my-rl
  (send-people-to-the-moon))
```

### Bulkhead

Bulkhead allows you to limit concurrent execution on a code block.

```clojure
(require '[diehard.core :as dh])

;; at most 10 threads can run the code block concurrently
(defbulkhead my-bh {:concurrency 10})

(dh/with-bulkhead my-bh
  (send-people-to-the-moon))
```

## Examples
### Retry block

```clojure
(dh/with-retry {:retry-on          Exception
                :max-retries       3
                :on-retry          (fn [val ex] (prn "retrying..."))
                :on-failure        (fn [_ _] (prn "failed..."))
                :on-failed-attempt (fn [_ _] (prn "failed attempt"))
                :on-success        (fn [_] (prn "did it! success!"))}
               (throw (ex-info "not good" {:not "good"})))
```

output:
```
"failed attempt"
"retrying..."
"failed attempt"
"retrying..."
"failed attempt"
"retrying..."
"failed attempt"
"failed..."
Execution error (ExceptionInfo) at main.user$eval27430$reify__27441/get (form-init6791465293873302710.clj:7).
not good
```

## Docs

More options can be found in the documentation
[from cljdoc](https://cljdoc.org/d/diehard/diehard/).

## License

Copyright © 2016-2019 Ning Sun

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

## Donation

I'm now accepting donation on [liberapay](https://liberapay.com/Sunng/donate),
if you find my work helpful and want to keep it going.
