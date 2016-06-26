(ns diehard.core-test
  (:require [clojure.test :refer :all]
            [diehard.core :refer :all])
  (:import [net.jodah.failsafe FailsafeException]))

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
    (is (= 1 (with-retry {:retry-when 0}
               *executions*))))
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
      (catch FailsafeException e
        (is (instance? IllegalStateException (.getCause e))))))

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
      (catch FailsafeException e
        (is (instance? IllegalStateException (.getCause e))))))
  (testing "delay backoff"
    (is (= 2 (with-retry {:backoff-ms [10 100]
                          :max-duration-ms 50
                          :retry-if (fn [v _] (< v 10))}
               *executions*)))))
