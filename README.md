# diehard

[![Build
Status](https://travis-ci.org/sunng87/diehard.svg?branch=master)](https://travis-ci.org/sunng87/diehard)
[![Clojars](https://img.shields.io/clojars/v/diehard.svg?maxAge=2592000)]()
[![license](https://img.shields.io/github/license/sunng87/diehard.svg?maxAge=2592000)]()

A Clojure wrapper over [Failsafe](https://github.com/jhalterman/failsafe)

## Usage

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

Available options:

* `:retry-when` retry when return value is given value
* `:retry-on` retry on given exception / exceptions(vector) were thrown
* `:retry-if` specify a function `(fn [return-value
  exception-thrown])`, retry if the function returns true
* `:abort-when` abort retry when return value is given value
* `:abort-on` abort retry on given exception / exceptions(vector) were
  thrown
* `:abort-if` specify a function `(fn [return-value
  exception-thrown])`, abort retry if the function returns true
* `backoff-ms` specify a vector `[initial-delay-ms max-delay-ms
  multiplier]` to control the delay between each retry, the delay for
  **n**th retry will be `(max (* initial-delay-ms n) max-delay-ms)`
* `delay-ms` use constant delay between each retry
* `max-retries` abort retry when max attempts reached
* `max-duration` abort retry when duration reached

## License

Copyright © 2016 Ning Sun

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
