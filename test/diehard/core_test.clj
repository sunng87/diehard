(ns diehard.core-test
  (:require [clojure.test :refer :all]
            [diehard.core :refer :all]))

(deftest test-retry
  (testing "retry-on"
    (is (= 1 (with-retry {:retry-on IllegalStateException}
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
    (is (= 1 (with-retry {:retry-when (int 0)}
               *executions*))))))
