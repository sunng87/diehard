(ns diehard.timeout-test
  (:require [clojure.test :refer :all]
            [diehard.core :refer :all])
  (:import [java.time Duration]
           [dev.failsafe TimeoutExceededException]
           [java.util.concurrent ExecutionException]
           [clojure.lang ExceptionInfo]))

(def timeout-duration 50)

(deftest get-with-timeout-test
  (testing "get"
    (is (= "result" (with-timeout {:timeout-ms timeout-duration}
                      (Thread/sleep 25)
                      "result"))))

  (testing "get exceeds timeout and throws timeout exception"
    (is (thrown? TimeoutExceededException
                 (with-timeout {:timeout-ms timeout-duration}
                   (Thread/sleep 100)
                   "result"))))
  (testing "get given interrupt flag set exceeds timeout and throws"
    (let [start (System/currentTimeMillis)
          timeout-ms 500]
      (is (thrown? TimeoutExceededException
                   (with-timeout {:timeout-ms timeout-ms
                                  :interrupt? true}
                     (Thread/sleep 5000)
                     "result")))
      (let [end (System/currentTimeMillis)]
        (is (< (- end start) (* 1.5 timeout-ms))))))

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
                                :unknown-key 1}))))
  (testing "should raise error on receiving unknown types"
    (is (thrown? ExceptionInfo
                 (with-timeout {:timeout-ms 5000
                                :on-success "string instead of function"
                                :unknown-key 1})))
    (is (thrown? ExceptionInfo
                 (with-timeout {:timeout-ms 5000
                                :on-failure "string instead of function"
                                :unknown-key 1})))))
