(defproject diehard "0.1.1-SNAPSHOT"
  :description "A Failsafe wrapper for Clojure"
  :url "http://github.com/sunng87/diehard"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [net.jodah/failsafe "0.8.1"]]
  :deploy-repositories {"releases" :clojars})
