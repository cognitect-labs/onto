(defproject com.cognitect/onto "0.1.0-SNAPSHOT"
  :description  "Ontological inferencing for Datomic"
  :license      {:name "Eclipse Public License"
                 :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins      [[codox "0.8.10"]]

  :dependencies [[org.clojure/clojure            "1.9.0"]
                 [com.datomic/datomic-free       "0.9.5656"]]

  :codox        {:src-dir-uri "https://github.com/cognitect-labs/onto/blob/master/"
                 :src-linenum-anchor-prefix "L"
                 :defaults {:doc/format :markdown}}

  :profiles     {:dev {:dependencies [[org.clojure/test.check      "0.9.0"]]
                       :aliases {"ci" ["do" "clean," "test," "doc," "jar"]}}})
