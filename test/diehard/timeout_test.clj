(ns diehard.timeout-test
  (:require [clojure.test :refer :all]
            [diehard.core :refer :all]
            [diehard.timeout :as dt])
  (:import (java.time Duration)
           (net.jodah.failsafe TimeoutExceededException)
           (java.util.concurrent ExecutionException)
           (clojure.lang ExceptionInfo)))

(def timeout-duration 50)

(deftest get-with-timeout-test
  (testing "get"
    (let [timeout (dt/timeout (Duration/ofMillis timeout-duration))]
      (is (= "result" (dt/get-with-timeout timeout (fn []
                                                     (Thread/sleep 25)
                                                     "result"))))))

  (testing "get with timeout exception"
    (let [timeout (dt/timeout (Duration/ofMillis timeout-duration))]
      (is (thrown? TimeoutExceededException (dt/get-with-timeout timeout (fn []
                                                                           (Thread/sleep 60)
                                                                           "result"))))))

  (testing "get on success callback"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration) {:on-success (fn [_]
                                                                     (swap! call-count inc))})]
      (is (= "result" (dt/get-with-timeout timeout (fn []
                                                     (Thread/sleep 25)
                                                     "result"))))
      (is (= 1 @call-count))))

  (testing "get on failure callback"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration) {:on-failure (fn [_]
                                                                     (swap! call-count inc))})]
      (is (thrown? TimeoutExceededException (dt/get-with-timeout timeout (fn []
                                                                           (Thread/sleep 60)
                                                                           "result"))))
      (is (= 1 @call-count)))))

(deftest run-with-timeout-test
  (testing "run"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration))]
      (dt/run-with-timeout timeout (fn []
                                     (Thread/sleep 25)
                                     (swap! call-count inc)))
      (is (= 1 @call-count))))

  (testing "run with timeout exception"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration))]
      (is (thrown? TimeoutExceededException (dt/run-with-timeout timeout (fn []
                                                                           (Thread/sleep 60)
                                                                           (swap! call-count inc)
                                                                           "result"))))
      (is (= 1 @call-count))))

  (testing "run on success callback"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration) {:on-success (fn [_]
                                                                     (swap! call-count inc))})]
      (dt/run-with-timeout timeout (fn []
                                     (Thread/sleep 25)
                                     "result"))
      (is (= 1 @call-count))))

  (testing "get on failure callback"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration) {:on-failure (fn [_]
                                                                     (swap! call-count inc))})]
      (is (thrown? TimeoutExceededException (dt/run-with-timeout timeout (fn []
                                                                           (Thread/sleep 60)
                                                                           "result"))))
      (is (= 1 @call-count)))))

(deftest get-async-with-timeout-test
  (testing "get async on success callback"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration) {:on-success (fn [_]
                                                                    (swap! call-count inc))})]
      (is (= "result" (.get (dt/get-async-with-timeout timeout (fn []
                                                                 (Thread/sleep 25)
                                                                 "result")))))
      (is (= 1 @call-count))))

  (testing "get async on failure callback"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration) {:on-failure (fn [_]
                                                                    (swap! call-count inc))})]
      (is (thrown? ExecutionException (.get (dt/get-async-with-timeout timeout (fn []
                                                                                       (Thread/sleep 60)
                                                                                       "result")))))
      (is (= 1 @call-count)))))

(deftest run-async-with-timeout-test
  (testing "run async on success callback"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration) {:on-success (fn [_]
                                                                    (swap! call-count inc))})]
      (.get (dt/run-async-with-timeout timeout (fn []
                                                 (Thread/sleep 25)
                                                 "result")))
      (is (= 1 @call-count))))

  (testing "run async on failure callback"
    (let [call-count (atom 0)
          timeout (dt/timeout (Duration/ofMillis timeout-duration) {:on-failure (fn [_]
                                                                    (swap! call-count inc))})]
      (is (thrown? ExecutionException (.get (dt/run-async-with-timeout timeout (fn []
                                                                                       (Thread/sleep 60)
                                                                                       "result")))))
      (is (= 1 @call-count)))))

(deftest timeout-test
  (testing "should raise error on receiving unknown keys"
    (is (thrown? ExceptionInfo
                 (dt/timeout (Duration/ofMillis timeout-duration) {:on-success  (fn [_])
                                                                   :unknown-key 1}))))
  (testing "should raise error on receiving unknown types"
    (is (thrown? ExceptionInfo
                 (dt/timeout (Duration/ofMillis timeout-duration) {:on-success  "string instead of function"})))
    (is (thrown? ExceptionInfo
                 (dt/timeout (Duration/ofMillis timeout-duration) {:on-failure  "string instead of function"})))))
