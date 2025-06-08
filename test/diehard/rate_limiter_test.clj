(ns diehard.rate-limiter-test
  (:require [clojure.test :refer [deftest testing is]]
            [diehard.rate-limiter :as rl])
  (:import [java.util.concurrent ExecutorService Executors Future TimeUnit]
           [java.util.concurrent.atomic AtomicBoolean]))

(def default-error
  "Relative error in percent. 3% by default."
  0.03)

(defn- approx==
  ([expected actual]
   (approx== expected actual (* default-error expected)))
  ([expected actual tolerance]
   (< (abs (- expected actual)) tolerance)))

(defn- total-block-time
  [rate n-threads]
  (long (* (/ 1.0 rate) n-threads 1000)))

(defn- submit
  ^Future [^ExecutorService executor ^Callable f]
  (ExecutorService/.submit executor f))

(defn- run-rate-limited-counting
  [& {:keys [proceed-run? do-shutdown!]}]
  (fn [rl-opts n-threads run-time-sec]
    (let [pool (Executors/newFixedThreadPool n-threads)
          stop? (AtomicBoolean. false)
          rate-limiter (rl/rate-limiter rl-opts)
          counter (atom 0)]
      (doseq [_ (range n-threads)]
        (submit pool (fn []
                       (try
                         (loop []
                           (rl/acquire! rate-limiter)
                           (when (AtomicBoolean/.get stop?)
                             (throw (ex-info "Stopped" {:type ::stop})))
                           (when (proceed-run?)
                             (swap! counter inc)
                             (recur)))
                         (catch Exception ex
                           (when-not (= ::stop (:type (ex-data ex)))
                             (throw ex)))))))
      (Thread/sleep ^long (* 1000 run-time-sec))
      (do-shutdown! pool)
      {:await-fn (fn [timeout-ms]
                   (ExecutorService/.awaitTermination
                    pool timeout-ms TimeUnit/MILLISECONDS))
       :stop-fn  #(AtomicBoolean/.set stop? true)
       :counter  counter})))

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

(defn- run-rate-limited-counting:exception []
  (let [throw? (AtomicBoolean. false)]
    (run-rate-limited-counting
     :proceed-run? #(if (AtomicBoolean/.get throw?)
                      (throw (Exception. "Task failed!"))
                      true)
     :do-shutdown! (fn [pool]
                     (AtomicBoolean/.set throw? true)
                     (ExecutorService/.shutdown pool)))))

(deftest rate-limiter-at-high-rates-test
  (testing "high rates"
    (let [rate 1000
          run-time-sec 2

          exp-count (* rate run-time-sec)

          n-threads 32
          ;; tenfold be enough to cover up thread switching costs
          term-timeout-ms (* 10 (total-block-time rate n-threads))]

      (testing "with interruptible sleep"
        (testing "and interruption signal to stop tasks"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:interruption)
                                            {:rate rate} n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter)
                "Count must be close to an expected value")))
        (testing "and custom running flag to stop tasks"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:custom-flag)
                                            {:rate rate} n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter)
                "Count must be close to an expected value")))
        (testing "when a task logic throws an exception"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:exception)
                                            {:rate rate} n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter)
                "Count must be close to an expected value"))))

      (testing "with uninterruptible sleep"
        (testing "and interruption signal to stop tasks"
          (let [{:keys [await-fn stop-fn counter]} ((run-rate-limited-counting:interruption)
                                                    {:rate     rate
                                                     :sleep-fn rl/uninterruptible-sleep}
                                                    n-threads run-time-sec)]
            ;; here we are forced to check the count before it's too late,
            ;; since the task pool termination won't happen straight away
            (is (approx== exp-count @counter)
                "Count must be close to an expected value")

            (is (false? (await-fn term-timeout-ms))
                "Cannot terminate due to (some) tasks still running")
            ;; the only way to actually stop all running tasks (isolation)
            (stop-fn)
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout THIS TIME")))
        (testing "and custom running flag to stop tasks"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:custom-flag)
                                            {:rate     rate
                                             :sleep-fn rl/uninterruptible-sleep}
                                            n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter)
                "Count must be close to an expected value")))
        (testing "when a task logic throws an exception"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:exception)
                                            {:rate     rate
                                             :sleep-fn rl/uninterruptible-sleep}
                                            n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter)
                "Count must be close to an expected value")))))))

(deftest rate-limiter-at-low-rates-test
  (testing "low rates" ; less than 1.0
    (let [rate 0.5
          run-time-sec 5

          exp-count (* rate run-time-sec)
          ;; exact error tolerance, since we are dealing with much slower ticks
          tolerance 1

          n-threads 2
          ;; each thread will have to sleep for ≈2s (that's why we have just 2)
          term-timeout-ms (total-block-time rate n-threads)]

      (testing "with interruptible sleep"
        (testing "and interruption signal to stop tasks"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:interruption)
                                            {:rate rate} n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter tolerance)
                "Count must be close to an expected value")))
        (testing "and custom running flag to stop tasks"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:custom-flag)
                                            {:rate rate} n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter tolerance)
                "Count must be close to an expected value")))
        (testing "when a task logic throws an exception"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:exception)
                                            {:rate rate} n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter tolerance)
                "Count must be close to an expected value"))))

      (testing "with uninterruptible sleep"
        (testing "and interruption signal to stop tasks"
          (let [{:keys [await-fn stop-fn counter]} ((run-rate-limited-counting:interruption)
                                                    {:rate     rate
                                                     :sleep-fn rl/uninterruptible-sleep}
                                                    n-threads run-time-sec)]
            ;; here we are forced to check the count before it's too late,
            ;; since the task pool termination won't happen straight away
            (is (approx== exp-count @counter tolerance)
                "Count must be close to an expected value")

            (is (false? (await-fn term-timeout-ms))
                "Cannot terminate due to (some) tasks still running")
            ;; the only way to actually stop all running tasks (isolation)
            (stop-fn)
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout THIS TIME")))
        (testing "and custom running flag to stop tasks"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:custom-flag)
                                            {:rate     rate
                                             :sleep-fn rl/uninterruptible-sleep}
                                            n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter tolerance)
                "Count must be close to an expected value")))
        (testing "when a task logic throws an exception"
          (let [{:keys [await-fn counter]} ((run-rate-limited-counting:exception)
                                            {:rate     rate
                                             :sleep-fn rl/uninterruptible-sleep}
                                            n-threads run-time-sec)]
            (is (await-fn term-timeout-ms)
                "Terminates successfully, without timeout")
            (is (approx== exp-count @counter tolerance)
                "Count must be close to an expected value")))))))
