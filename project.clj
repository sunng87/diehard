(defproject diehard "0.5.0"
  :description "A Failsafe wrapper for Clojure"
  :url "http://github.com/sunng87/diehard"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [net.jodah/failsafe "1.0.0"]]
  :plugins [[lein-codox "0.9.5"]]
  :codox {:output-path "target/codox"
          :source-uri "https://github.com/sunng87/diehard/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :deploy-repositories {"releases" :clojars})
