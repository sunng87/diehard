(ns diehard.core-test
  (:require [clojure.test :refer :all]
            [diehard.circuit-breaker :as cb]
            [diehard.core :refer :all])
  (:import [net.jodah.failsafe CircuitBreakerOpenException]
           [java.util.concurrent CountDownLatch]))

(deftest test-retry
  (testing "retry-on"
    (is (= 1 (with-retry {:retry-on IllegalStateException}
               (if (= 0 *executions*)
                 (throw (IllegalStateException.))
                 *executions*))))
    (is (= 1 (with-retry {:retry-on [UnsupportedOperationException
                                     IllegalStateException]}
               (if (= 0 *executions*)
                 (throw (IllegalStateException.))
                 *executions*)))))
  (testing "retry-if"
    (is (= 1 (with-retry {:retry-if (fn [v e]
                                      (if e
                                        (instance? IllegalStateException e)
                                        false))}
               (if (= 0 *executions*)
                 (throw (IllegalStateException.))
                 *executions*)))))
  (testing "retry-if"
    (is (= 1 (with-retry {:retry-if (fn [v _]
                                      (= v 0))}
               *executions*))))
  (testing "retry-if honors clojure truthiness"
    (is (= 1 (with-retry {:retry-if (fn [v e] (and e (instance? IllegalStateException e)))}
               (if (= 0 *executions*)
                 (throw (IllegalStateException.))
                 *executions*)))))
  (testing "retry-when"
    (is (= 2 (with-retry {:retry-when 0}
               (if (= 1 *executions*)
                 (throw (Exception.))
                 *executions*)))))
  (testing "abort-when"
    (is (= 1 (with-retry {:abort-when 1
                          :retry-if (fn [v e] (< v 10))}
               *executions*))))
  (testing "abort-if"
    (is (= 1 (with-retry {:abort-if (fn [v e] (= v 1))
                          :retry-if (fn [v e] (< v 10))}
               *executions*))))
  (testing "abort-on"
    (try
      (with-retry {:retry-if (fn [v e] (< v 10))
                   :abort-on IllegalStateException}
        (if (= 1 *executions*)
          (throw (IllegalStateException.))
          *executions*))
      (is false)
      (catch IllegalStateException _
        (is true))))

  (testing "max retry"
    (is (= 4 (with-retry {:retry-if (fn [v e] (< v 10))
                          :max-retries 4}
               *executions*))))
  (testing "delay"
    (with-retry {:retry-if (fn [v e] (< v 2))
                 :delay-ms 20}
      (when (= *executions* 1)
        (>= (- *elapsed-time-ms* *start-time-ms*) 100))
      *executions*))
  (testing "duration"
    (try
      (with-retry {:delay-ms 20
                   :max-duration-ms 50}
        (throw (IllegalStateException.)))
      (is false)
      (catch IllegalStateException _
        (is true))))
  (testing "delay backoff"
    (is (= 2 (with-retry {:backoff-ms [10 1000]
                          :max-duration-ms 50
                          :retry-if (constantly true)}
               (Thread/sleep 10)
               *executions*)))
    (is (= 2 (with-retry {:backoff-ms [10 1000 1.1]
                          :max-duration-ms 50
                          :retry-if (constantly true)}
               (Thread/sleep 10)
               *executions*))))
  (testing "invalid option given to policy map"
    (try
      (with-retry {:unknown-option 1}
        *executions*)
      (is false)
      (catch Exception e
        (is (not-empty (:clojure.spec.alpha/problems (ex-data e)))))))

  (testing "listeners"
    (let [retry-counter (atom 0)
          failed-attempt-counter (atom 0)
          complete-counter (atom 0)
          success-counter (atom 0)]
      (with-retry {:on-retry (fn [v e] (swap! retry-counter inc))
                   :on-failed-attempt (fn [v e] (swap! failed-attempt-counter inc))
                   :on-complete (fn [v e] (swap! complete-counter inc))
                   :on-success (fn [v] (swap! success-counter inc))

                   :retry-if (fn [v e]
                               (or (some? e) (< v 10)))}
        (if (even? *executions*)
          (throw (IllegalStateException.))
          *executions*))
      (are [x y] (= x y)
        11 @retry-counter
        11 @failed-attempt-counter
        1 @complete-counter
        1 @success-counter))
    (let [retry-counter (atom 0)
          failed-attempt-counter (atom 0)
          failure-counter (atom 0)
          complete-counter (atom 0)
          abort-counter (atom 0)
          retries-exceeded-counter (atom 0)]
      (try
        (with-retry {:on-retry (fn [v e] (swap! retry-counter inc))
                     :on-failed-attempt (fn [v e] (swap! failed-attempt-counter inc))
                     :on-complete (fn [v e] (swap! complete-counter inc))
                     :on-abort (fn [v e] (swap! abort-counter inc))
                     :on-failure (fn [v e] (swap! failure-counter inc))
                     :on-retries-exceeded (fn [v e] (swap! retries-exceeded-counter inc))

                     :retry-if (fn [v e] true)
                     :abort-when 11}
          (if (even? *executions*)
            (throw (IllegalStateException.))
            *executions*)
          (catch IllegalStateException _))
        (are [x y] (= x y)
          11 @retry-counter
          12 @failed-attempt-counter
          1 @failure-counter
          1 @complete-counter
          1 @abort-counter
          0 @retries-exceeded-counter))))

  (testing "fallback value"
    (is (= 5 (with-retry {:fallback 5 :max-retries 10} (throw (Exception.)))))
    (is (= 10 (with-retry {:fallback (fn [v e]
                                       (is (= v 10))
                                       (is (nil? e))
                                       v)
                           :retry-if (fn [v e] (< v 10))}
                *executions*)))
    (is (= false (with-retry {:fallback false :max-retries 2} (throw (Exception.))))))

  (testing "fallback function"
    (let [fallback-counter (atom 0)
          retry-counter (atom 0)]
      (with-retry {:fallback    (fn [& _] (swap! fallback-counter inc))
                   :max-retries 5}
        (swap! retry-counter inc)
        (throw (Exception.)))
      (is (= 1 @fallback-counter))
      (is (= 6 @retry-counter))))

  (testing "with-retry given a fallback function has been registered the arguments passed in should be [result exception]"
    (let [res (with-retry {:fallback    (fn [v e]
                                          (is (not (nil? e)))
                                          (is (nil? v))
                                          43)
                           :max-retries 5}
                (throw (Exception.)))]
      (is (= res 43))))

  (testing "with-retry given the circuit breaker is closed should invoke fallback only when retries have been exhausted"
    (let [cb (cb/circuit-breaker {:failure-threshold 10
                                  :delay-ms          10000})
          execution-counter (atom 0)
          fallback-counter (atom 0)
          execution-count 7
          fn-with-fallback (fn [exec-count]
                             (with-retry {:fallback        (fn [v e]
                                                             (is (not (nil? e)))
                                                             (is (nil? v))
                                                             (swap! fallback-counter inc)
                                                             exec-count)
                                          :circuit-breaker cb
                                          ;; If we don't put this in the fallback will not be invoked even for failed
                                          ;; executions, until the circuit breaker moves into the open state
                                          :max-retries     0}
                               (swap! execution-counter inc)
                               (throw (Exception. "Expected exception")))) ;; every execution will fail
          res (->> (range 0 execution-count)
                   (map fn-with-fallback))]
      (is (= (range 0 execution-count) res))
      (is (= execution-count @execution-counter))
      (is (= execution-count @fallback-counter))))

  (testing "predefined policy"
    (defretrypolicy the-test-policy
      {:retry-if (fn [v e] (< v 10))})

    (is (= 10 (with-retry {:policy the-test-policy} *executions*))))

  (testing "predefined policy with customization"
    (defretrypolicy the-test-policy
      {:retry-if (fn [v e] (< v 10))})

    (is (= 4 (with-retry {:policy the-test-policy
                          :max-retries 4}
                *executions*))))

  (testing "RuntimeException"
    (let [retries (atom 0)]
      (try
        (with-retry {:max-retries 1
                     :on-retry (fn [v e] (swap! retries inc))}
          (throw (RuntimeException.)))
        (is false)
        (catch RuntimeException _
          (is (= 1 @retries)))))))

(deftest test-circuit-breaker-params
  (testing "failure threshold ratio"
    (defcircuitbreaker test-cb-p1 {:failure-threshold-ratio [7 10]})
    (is (= 7 (.getFailureThreshold test-cb-p1))))
  (testing "failure threshold"
    (defcircuitbreaker test-cb-p2 {:failure-threshold 7})
    (is (= 7 (.getFailureThreshold test-cb-p2))))
  (testing "success threshold ratio"
    (defcircuitbreaker test-cb-p3 {:success-threshold-ratio [10 10]})
    (is (= 10 (.getSuccessThreshold test-cb-p3))))
  (testing "success threshold"
    (defcircuitbreaker test-cb-p4 {:success-threshold 10})
    (is (= 10 (.getSuccessThreshold test-cb-p4)))))

(deftest test-retry-policy-params
  (testing "retry policy param"
    (defretrypolicy rp {:delay-ms 1000
                        :max-duration-ms 2000
                        :jitter-factor 0.5
                        :max-retries 10})
    (is (= 1000 (.. rp (getDelay) (toMillis))))
    (is (= 2000 (.. rp (getMaxDuration) (toMillis))))
    (is (= 0.5 (.. rp (getJitterFactor))))
    (is (= 10 (.. rp (getMaxRetries))))))

(deftest test-circuit-breaker
  (testing "circuit open"
    (defcircuitbreaker test-cb {:failure-threshold 2
                                :delay-ms 100000})
    (dotimes [n 5]
      (try
        (with-circuit-breaker test-cb
          (throw (IllegalStateException.)))
        (is false)
        (catch Exception e
          (if (< n 2)
            (is (instance? IllegalStateException e))
            (is (instance? CircuitBreakerOpenException e)))))))
  (testing "failure threshold ratio for period"
    (defcircuitbreaker test-cb-2 {:failure-threshold-ratio-in-period [1 2 10]})

    (dotimes [_ 4]
      (try
        (with-circuit-breaker test-cb-2
          (Thread/sleep 15)
          (throw (Exception. "expected")))
        (catch Exception e
          (is (not (instance? CircuitBreakerOpenException e))))))))

(deftest opt-eval-count
  (let [eval-counter (atom 0)]
    (with-retry {:retry-when (do (swap! eval-counter inc) nil)}
      "some value")
    (is (= @eval-counter 1))))

(deftest test-circuit-breaker-with-retry-block
  (testing "circuit open within retry block"
    (defcircuitbreaker test-cb-2 {:failure-threshold 2
                                  :delay-ms 100000})
    (try
      (with-retry {:circuit-breaker test-cb-2
                   :max-retries 100}
        (throw (IllegalStateException.)))
      (is false)
      (catch Exception e
        (is (instance? CircuitBreakerOpenException e))))))

(defmacro timed [& body]
  `(let [start-ts# (System/currentTimeMillis)]
     ~@body
     (- (System/currentTimeMillis) start-ts#)))

(deftest test-rate-limiter
  (testing "base case"
    (defratelimiter my-rl {:rate 150})
    (defratelimiter my-rl2 {:rate 150})
    (letfn [(my-fn [c] (swap! c inc))]
      (let [counter0 (atom 0)
            counter1 (atom 0)
            counter2 (atom 0)
            t1 (timed (while (< @counter0 200)
                        (my-fn counter0)))
            t2 (timed (while (< @counter1 99)
                        (with-rate-limiter my-rl (my-fn counter1))))
            t3 (timed (while (< @counter2 200)
                        (with-rate-limiter my-rl2 (my-fn counter2))))]
        (is (< t1 1000))
        (is (< t2 1000))
        (is (> t3 1000)))))

  (testing "max wait"
    (defratelimiter my-rl3 {:rate 150})
    (letfn [(my-fn [c] (swap! c inc))]
      (let [counter0 (atom 0)]
        (try
          (while (< @counter0 200)
            (with-rate-limiter {:ratelimiter my-rl
                                :max-wait-ms 1}
              (my-fn counter0)))
          (is false)
          (catch Exception e
            (is (:throttled (ex-data e))))))))

  (testing "permits"
    (defratelimiter my-rl4 {:rate 150})
    (letfn [(my-fn [c] (swap! c inc))]
      (let [counter0 (atom 0)
            t0 (timed (while (< @counter0 99)
                        (with-rate-limiter {:ratelimiter my-rl4
                                            :permits 2}
                          (my-fn counter0))))]
        (is (> t0 1000))))))

(deftest test-bulkhead
  (testing "only two thread is allow to run bulkhead block"
    (defbulkhead bh0 {:concurrency 2})
    (let [counter (atom 0)
          fun (fn [_]
                (with-bulkhead bh0
                  (let [v (swap! counter inc)]
                    (Thread/sleep 100)
                    (swap! counter dec)
                    v)))]
      (is (every? #(<= % 2) (pmap #(fun %) (range 20))))))

  (testing "timeout"
    (defbulkhead bh1 {:concurrency 2})
    (let [max-wait 100
          latch (CountDownLatch. 2)
          fun (fn [id]
                (with-bulkhead {:bulkhead bh1
                                :max-wait-ms 100}
                  #_(println (str "Running subtask" id))
                  (Thread/sleep 2000)))]
      (future
        (.countDown latch)
        (fun 1))
      (future
        (.countDown latch)
        (fun 2))

      (Thread/sleep 100)
      (try
        (.await latch)
        (fun 3)
        (is false)
        (catch Exception e
          (is (:bulkhead (ex-data e)))
          (is (= max-wait (:max-wait-ms (ex-data e)))))))))
