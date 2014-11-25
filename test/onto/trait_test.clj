(ns onto.trait-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [db-util.in-memory-db :as imdb :refer [*conn* using]]
            [onto.core :as o]
            [onto.examples.ecommerce :refer :all]
            [onto.traits :as t :refer [deftrait]]))

(use-fixtures :once (imdb/with-disposable-in-mem-db o/bootstrap))

(deftest polymorphism-on-inferred-classes
  (let [now (using *conn* (:txn Delivery) (:txn Organic) product-catalog shipping harvesting restbucks)]
    (let [physical (t/trait-invoke (t/entity "sku:200" now) 'methods)
          digital  (t/trait-invoke (t/entity "sku:201" now) 'methods)]
      (is (= #{"USPS" "UPS"}      physical))
      (is (= #{"email" "dropbox"} digital)))
    (let [harvesting (t/trait-invoke (t/entity "sku:100" now) 'methods)]
      (is (= #{"hand picked (human)" "hand picked (trained monkey)" "machine collected"}
             harvesting)))))
