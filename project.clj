(defproject docufant "0.1.0-SNAPSHOT"
  :description "A document store for Postgres."
  :url "http://github.com/xlevus/docufant-clj"
  :license {:name "The MIT License"
            :url "https://github.com/xlevus/docufant-clj/blob/master/LICENSE"}
  :dependencies [[cheshire "5.8.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/java.jdbc "0.7.3"]
                 [org.postgresql/postgresql "42.1.4"]])
