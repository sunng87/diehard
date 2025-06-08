(ns diehard.rate-limiter-test
  (:require [clojure.test :refer [deftest testing is]]
            [diehard.rate-limiter :as rl])
  (:import [java.time Duration]
           [java.util.concurrent ExecutorService Executors Future TimeUnit]
           [java.util.concurrent.atomic AtomicBoolean]))

(def default-error
  "Relative error in percent. 3% by default."
  0.03)

(defn- approx==
  ([expected actual]
   (approx== expected actual (* default-error expected)))
  ([expected actual tolerance]
   (< (abs (- expected actual)) tolerance)))

(defn- total-wait-time
  [rate n-threads]
  (long (* (/ 1.0 rate) n-threads 1000)))

(defn- submit
  ^Future [^ExecutorService executor ^Callable f]
  (ExecutorService/.submit executor f))

(defn- run-rate-limited-counting
  [& {:keys [proceed-run? do-shutdown!]}]
  (fn [rate-limiter n-threads run-time-sec]
    (let [pool (Executors/newFixedThreadPool n-threads)
          counter (atom 0)]
      (doseq [_ (range n-threads)]
        (submit pool (fn []
                       (loop []
                         (rl/acquire! rate-limiter)
                         (when (proceed-run?)
                           (swap! counter inc)
                           (recur))))))
      (Thread/sleep (Duration/ofSeconds run-time-sec))
      (do-shutdown! pool)
      {:await-fn (fn [timeout-ms]
                   (ExecutorService/.awaitTermination
                     pool timeout-ms TimeUnit/MILLISECONDS))
       :count    @counter})))

(defn- run-rate-limited-counting:interruption []
  (run-rate-limited-counting
    :proceed-run? (constantly true)
    :do-shutdown! ExecutorService/.shutdownNow))

(defn- run-rate-limited-counting:custom-flag []
  (let [running? (AtomicBoolean. true)]
    (run-rate-limited-counting
      :proceed-run? #(AtomicBoolean/.get running?)
      :do-shutdown! (fn [pool]
                      (AtomicBoolean/.set running? false)
                      (ExecutorService/.shutdown pool)))))

(deftest rate-limiter-at-high-rates-test
  (testing "high rates"
    (testing "with interruptible sleep"
      (testing "and interruption signal to stop tasks"
        (let [rate 1000
              rate-limiter (rl/rate-limiter {:rate rate})
              run-time-sec 2
              n-threads 32
              ;; tenfold be enough to cover up thread switching costs
              term-timeout-ms (* 10 (total-wait-time rate n-threads))
              {:keys [await-fn count]} ((run-rate-limited-counting:interruption)
                                        rate-limiter n-threads run-time-sec)]
          (is (await-fn term-timeout-ms)
              "Terminates successfully, without timeout")
          (is (approx== (* rate run-time-sec) count)
              "Count must be close to an expected value")))
      (testing "and custom running flag to stop tasks"
        (let [rate 1000
              rate-limiter (rl/rate-limiter {:rate rate})
              run-time-sec 2
              n-threads 32
              ;; tenfold be enough to cover up thread switching costs
              term-timeout-ms (* 10 (total-wait-time rate n-threads))
              {:keys [await-fn count]} ((run-rate-limited-counting:custom-flag)
                                        rate-limiter n-threads run-time-sec)]
          (is (await-fn term-timeout-ms)
              "Terminates successfully, without timeout")
          (is (approx== (* rate run-time-sec) count)
              "Count must be close to an expected value"))))

    (testing "with uninterruptible sleep"
      (testing "and interruption signal to stop tasks"
        (let [rate 1000
              rate-limiter (rl/rate-limiter {:rate     rate
                                             :sleep-fn rl/uninterruptible-sleep})
              run-time-sec 2
              n-threads 32
              ;; tenfold be enough to cover up thread switching costs
              term-timeout-ms (* 10 (total-wait-time rate n-threads))
              {:keys [await-fn count]} ((run-rate-limited-counting:interruption)
                                        rate-limiter n-threads run-time-sec)]
          (is (false? (await-fn term-timeout-ms))
              "Cannot terminate due to (some) tasks still running")
          (is (approx== (* rate run-time-sec) count)
              "Count must be close to an expected value")))
      (testing "and custom running flag to stop tasks"
        (let [rate 1000
              rate-limiter (rl/rate-limiter {:rate     rate
                                             :sleep-fn rl/uninterruptible-sleep})
              run-time-sec 2
              n-threads 32
              ;; tenfold be enough to cover up thread switching costs
              term-timeout-ms (* 10 (total-wait-time rate n-threads))
              {:keys [await-fn count]} ((run-rate-limited-counting:custom-flag)
                                        rate-limiter n-threads run-time-sec)]
          (is (await-fn term-timeout-ms)
              "Terminates successfully, without timeout")
          (is (approx== (* rate run-time-sec) count)
              "Count must be close to an expected value"))))))

(deftest rate-limiter-at-low-rates-test
  (testing "low rates" ; less than 1.0
    (testing "with interruptible sleep"
      (testing "and interruption signal to stop tasks"
        (let [rate 0.5
              rate-limiter (rl/rate-limiter {:rate rate})
              run-time-sec 5
              n-threads 2
              ;; each thread will have to sleep for ≈2 seconds
              term-timeout-ms (total-wait-time rate n-threads)
              {:keys [await-fn count]} ((run-rate-limited-counting:interruption)
                                        rate-limiter n-threads run-time-sec)]
          (is (await-fn term-timeout-ms)
              "Terminates successfully, without timeout")
          ;; exact error tolerance, since we are dealing with much slower ticks
          (is (approx== (* rate run-time-sec) count 1)
              "Count must be close to an expected value")))
      (testing "and custom running flag to stop tasks"
        (let [rate 0.5
              rate-limiter (rl/rate-limiter {:rate rate})
              run-time-sec 5
              n-threads 2
              ;; each thread will have to sleep for ≈2 seconds
              term-timeout-ms (total-wait-time rate n-threads)
              {:keys [await-fn count]} ((run-rate-limited-counting:custom-flag)
                                        rate-limiter n-threads run-time-sec)]
          (is (await-fn term-timeout-ms)
              "Terminates successfully, without timeout")
          ;; exact error tolerance, since we are dealing with much slower ticks
          (is (approx== (* rate run-time-sec) count 1)
              "Count must be close to an expected value"))))

    (testing "with uninterruptible sleep"
      (testing "and interruption signal to stop tasks"
        (let [rate 0.5
              rate-limiter (rl/rate-limiter {:rate     rate
                                             :sleep-fn rl/uninterruptible-sleep})
              run-time-sec 5
              n-threads 2
              ;; each thread will have to sleep for ≈2 seconds
              term-timeout-ms (total-wait-time rate n-threads)
              {:keys [await-fn count]} ((run-rate-limited-counting:interruption)
                                        rate-limiter n-threads run-time-sec)]
          (is (false? (await-fn term-timeout-ms))
              "Cannot terminate due to (some) tasks still running")
          ;; exact error tolerance, since we are dealing with much slower ticks
          (is (approx== (* rate run-time-sec) count 1)
              "Count must be close to an expected value")))
      (testing "and custom running flag to stop tasks"
        (let [rate 0.5
              rate-limiter (rl/rate-limiter {:rate     rate
                                             :sleep-fn rl/uninterruptible-sleep})
              run-time-sec 5
              n-threads 2
              ;; each thread will have to sleep for ≈2 seconds
              term-timeout-ms (total-wait-time rate n-threads)
              {:keys [await-fn count]} ((run-rate-limited-counting:custom-flag)
                                        rate-limiter n-threads run-time-sec)]
          (is (await-fn term-timeout-ms)
              "Terminates successfully, without timeout")
          ;; exact error tolerance, since we are dealing with much slower ticks
          (is (approx== (* rate run-time-sec) count 1)
              "Count must be close to an expected value"))))))
