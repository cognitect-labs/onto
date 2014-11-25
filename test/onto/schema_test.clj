(ns onto.schema-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test :refer :all]
            [datomic.api :as d]
            [db-util.in-memory-db :as imdb]
            [onto.core :as o])
  (:use clojure.pprint))

(defn bootstrap
  [conn]
  (doseq [s o/core-schemata]
    @(d/transact conn s))
  conn)

;(use-fixtures :once (imdb/with-fixed-in-mem-db bootstrap))
(use-fixtures :once (imdb/with-disposable-in-mem-db bootstrap))

(def dtproperty-type
  (gen/elements [:string :boolean :uuid :long :double :uri :bigint :bigdec :instant :bytes]))

(def dtproperty-card
  (gen/elements [:one :many]))

(def dtproperty-references
  (gen/elements [:subproperty :superproperty :domain :range]))

(defspec dtproperties-are-datomic-attributes
  (prop/for-all [property-name gen/keyword
                 type dtproperty-type
                 card dtproperty-card]
                (let [attrs (o/dtproperty property-name type card)
                      attr (first attrs)]
                  (and (get attr :db.install/_attribute)
                       (get attr :db/id)
                       (= (keyword "db.type" (name type)) (:db/valueType attr))
                       (= (keyword "db.cardinality" (name card)) (:db/cardinality attr))
                       (= :Property (:type attr))))))

(defmulti related (fn [t _ _ _] t))
(defmethod related :subproperty   [_ name other-name _] (o/subproperty name other-name))
(defmethod related :superproperty [_ name other-name _] (o/subproperty other-name name))
(defmethod related :domain        [_ name _ class]      (o/domain name class))
(defmethod related :range         [_ name _ class]      (o/range name class))

(defn disjoint
  [& gs]
  (gen/such-that (fn [vs] (apply not= vs))
                 (apply gen/tuple gs)))

(defn conflict?
  ([p1 p2]    (some #(conflict? % p1 p2) [:db/id :db/ident :db/valueType :db/cardinality]))
  ([kw p1 p2] (if (and (get p1 kw) (get p2 kw))
                (not= (get p1 kw) (get p2 kw))
                false)))

(defspec referring-to-dtproperty-doesnt-change-it
  (prop/for-all [[property-name other-property]  (disjoint gen/keyword gen/keyword)
                 type           dtproperty-type
                 card           dtproperty-card
                 relationship   dtproperty-references
                 class-name     (gen/resize 10 gen/string-alpha-numeric)]
                (let [property (o/dtproperty property-name type card)
                      second   (related relationship property-name other-property class-name)
                      decls    (get (group-by :db/ident (concat property second)) property-name)]
                  (not (conflict? property second)))))
