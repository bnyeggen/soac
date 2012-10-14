(defproject soac "0.1.3"
  :description "Flexible structs-of-arrays"
  :url "https://github.com/fiatmoney/soac"
  ;I believe this is a Leiningen 2 option, currently no raw Java but may be
  ;some later
  :java-source-paths ["java-src"]
  ;And this is the Leiningen 1.7.x version
  :java-source-path "java-src"
  :dependencies [[org.clojure/clojure "1.4.0"]])