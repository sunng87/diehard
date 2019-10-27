(ns diehard.timeout-test
  (:require [clojure.test :refer :all]
            [diehard.core :refer :all])
  (:import [java.time Duration]
           [net.jodah.failsafe TimeoutExceededException]
           [java.util.concurrent ExecutionException]
           [clojure.lang ExceptionInfo]))

(def timeout-duration 50)

(deftest get-with-timeout-test
  (testing "get"
    (is (= "result" (with-timeout {:timeout-ms timeout-duration}
                      (Thread/sleep 25)
                      "result"))))

  (testing "get with timeout exception"
    (is (thrown? TimeoutExceededException
                 (with-timeout {:timeout-ms timeout-duration}
                   (Thread/sleep 60)
                   "result"))))

  (testing "get on success callback"
    (let [call-count (atom 0)]
      (is (= "result" (with-timeout {:timeout-ms timeout-duration
                                     :on-success (fn [_]
                                                   (swap! call-count inc))}
                        (Thread/sleep 25)
                        "result")))
      (is (= 1 @call-count))))

  (testing "get on failure callback"
    (let [call-count (atom 0)]
      (is (thrown? TimeoutExceededException
                   (with-timeout {:timeout-ms timeout-duration
                                  :on-failure (fn [_]
                                                (swap! call-count inc))}
                     (Thread/sleep 60)
                     "result")))
      (is (= 1 @call-count)))))

(deftest timeout-test
  (testing "should raise error on receiving unknown keys"
    (is (thrown? ExceptionInfo
                 (with-timeout {:timeout-ms 5000
                                :on-success  (fn [_])
                                :unknown-key 1}
                   ))))
  (testing "should raise error on receiving unknown types"
    (is (thrown? ExceptionInfo
                 (with-timeout {:timeout-ms 5000
                                :on-success "string instead of function"
                                :unknown-key 1}
                   )))
    (is (thrown? ExceptionInfo
                 (with-timeout {:timeout-ms 5000
                                :on-failure "string instead of function"
                                :unknown-key 1}
                   )))))
