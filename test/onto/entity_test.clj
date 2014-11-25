(ns onto.entity-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [db-util.in-memory-db :as imdb :refer [*conn* using]]
            [onto.core :as o]
            [onto.examples.social :refer :all]))

(defn bootstrap
  [conn]
  (doseq [s o/core-schemata]
    @(d/transact conn s))
  conn)

(use-fixtures :once (imdb/with-disposable-in-mem-db bootstrap))

(deftest triples-with-bnodes
  (let [now (using *conn* tweeters-are-people tweeters-have-printable-ids two-tweeters-using-bnodes)]
    (is (= #{"@mtnygard"}  (o/get-value now "Michael" [:twitter-handle :nickname])))
    (is (not (nil?         (o/get-value now "Michael" [:twitter-handle :uri]))))
    (is (= #{"@holy_chao"} (o/get-value now "Alex" [:twitter-handle :nickname])))))
