(defproject com.nyeggen/soac "0.5"
  :description "Primitive-backed collections for Clojure"
  :url "https://github.com/fiatmoney/soac"
  :java-source-paths ["java-src"]
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :min-lein-version "2.0.0"
  :javac-options ["-target" "1.7" "-source" "1.7"]
  :test-selectors {:default (complement :performance)
                   :performance :performance
                   :all (constantly true)})
