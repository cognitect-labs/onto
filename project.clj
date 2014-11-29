(defproject com.cognitect/onto "0.1.0-SNAPSHOT"
  :description  "Ontological inferencing for Datomic"
  :license      {:name "Copyright 2014, Cognitect, Inc. All rights reserved."}

  :plugins      [[codox "0.8.10"]]

  :dependencies [[org.clojure/clojure            "1.7.0-alpha4"]
                 [com.datomic/datomic-free       "0.9.5078"]]

  :profiles     {:dev {:dependencies [[org.clojure/test.check      "0.5.8"]]
                       :aliases {"ci" ["do" "clean," "test," "doc," "jar"]}}})
