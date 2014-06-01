(defproject com.nyeggen/soac "0.6-SNAPSHOT"
  :description "Primitive-backed collections for Clojure"
  :url "https://github.com/fiatmoney/soac"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :java-source-paths ["java-src"]
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :min-lein-version "2.0.0"
  :javac-options ["-target" "1.7" "-source" "1.7"]
  :test-selectors {:default (complement :performance)
                   :performance :performance
                   :all (constantly true)})
