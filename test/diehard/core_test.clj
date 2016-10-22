(ns diehard.core-test
  (:require [clojure.test :refer :all]
            [diehard.core :refer :all])
  (:import [net.jodah.failsafe FailsafeException CircuitBreakerOpenException]
           [net.jodah.failsafe.util Ratio]))

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
               *executions*))))
  (testing "invalid option given to policy map"
    (try
      (with-retry {:unknown-option 1}
        *executions*)
      (is false)
      (catch IllegalArgumentException _
        (is true))))

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
        0 @failure-counter
        0 @complete-counter
        1 @abort-counter
        0 @retries-exceeded-counter))))

  (testing "fallback value"
    (is (= 5 (with-retry {:fallback 5 :max-retries 10} (throw (Exception.)))))
    (is (= 10 (with-retry {:fallback (fn [v e]
                                       (is (= v 10))
                                       (is (nil? e))
                                       v)
                           :retry-if (fn [v e] (< v 10))}
                *executions*))))

  (testing "predefined policy"
    (defretrypolicy the-test-policy
      {:retry-if (fn [v e] (< v 10))})

    (is (= 10 (with-retry {:policy the-test-policy} *executions*)))))



(deftest test-circuit-breaker-params
  (testing "failure threshold ratio"
    (defcircuitbreaker test-cb {:failure-threshold-ratio [7 10]})
    (is (= (.ratio (Ratio. 7 10)) (.ratio (.getFailureThreshold test-cb)))))
  (testing "failure threshold"
    (defcircuitbreaker test-cb {:failure-threshold 7})
    (is (= 7 (.numerator (.getFailureThreshold test-cb)))))
  (testing "success threshold ratio"
    (defcircuitbreaker test-cb {:success-threshold-ratio [10 10]})
    (is (= (.ratio (Ratio. 10 10)) (.ratio (.getSuccessThreshold test-cb)))))
  (testing "success threshold"
    (defcircuitbreaker test-cb {:success-threshold 10})
    (is (= 10 (.numerator (.getSuccessThreshold test-cb))))))

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
        (catch Exception e
          (if (< n 2)
            (is (instance? IllegalStateException e))
            (is (instance? CircuitBreakerOpenException e))))))))

(deftest opt-eval-count
  (let [eval-counter (atom 0)]
    (with-retry {:retry-when (do (swap! eval-counter inc) nil)}
      "some value")
    (is (= @eval-counter 1))))
