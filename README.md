# diehard

[![Build
Status](https://travis-ci.org/sunng87/diehard.svg?branch=master)](https://travis-ci.org/sunng87/diehard)
[![Clojars](https://img.shields.io/clojars/v/diehard.svg?maxAge=2592000)]()
[![license](https://img.shields.io/github/license/sunng87/diehard.svg?maxAge=2592000)]()

A Clojure wrapper over [Failsafe](https://github.com/jhalterman/failsafe)

## Usage

### Retry block

```clojure
(require '[diehard.core :as diehard])

(diehard/with-retry {:retry-on IOException}
  ;; your code here
  )

```

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

### Circuit breaker protected block

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

### Clojure API docs

The API is pretty simple but you can still find API docs
[here](https://sunng87.github.io/diehard).

## License

Copyright © 2016 Ning Sun

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
