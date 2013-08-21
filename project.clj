(defproject soac "0.1.5-SNAPSHOT"
  :description "Flexible structs-of-arrays"
  :url "https://github.com/fiatmoney/soac"
  :java-source-paths ["java-src"]
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :test-selectors {:default (complement :performance)
                   :performance :performance
                   :all (constantly true)})
