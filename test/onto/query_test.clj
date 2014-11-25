(ns onto.query-test
  (:require [datomic.api :as d]
            [db-util.in-memory-db :as imdb :refer [*conn* using]]
            [onto.core :as o]
            [onto.examples.employment :refer :all]
            [onto.examples.henleys :refer :all]
            [onto.examples.labeling :refer :all]
            [onto.examples.major-league :refer :all]
            [onto.examples.marriage :refer :all]
            [onto.examples.shakespeare :refer :all]
            [onto.examples.social :refer :all])
  (:use clojure.pprint
        clojure.test))

(defn bootstrap
  [conn]
  (doseq [s o/core-schemata]
    @(d/transact conn s))
  conn)

;(use-fixtures :once (imdb/with-fixed-in-mem-db bootstrap))
(use-fixtures :once (imdb/with-disposable-in-mem-db bootstrap))

(deftest type-propagation-rules
  (let [now (using *conn* henleys-short-example)]
    (testing "Chamois is a Shirt (via Henleys)"
      (is (= #{"ChamoisHenley"} (o/inhabitants now "Shirts"))))
    (testing "Chamois is a Clothing (via Henleys and Shirts)"
      (is (= #{"ChamoisHenley"} (o/inhabitants now "Clothing")))))

  (let [now (using *conn* henleys-full-example)]
    (testing "Various cases from 'Semantic Web for the Working Ontologist, pp 85"
      (are [x y] (= y (o/inhabitants now x))
           "MensWear"           #{"ClassicOxford" "ChamoisHenley" "BikerT"}
           "Shirts"             #{"ClassicOxford" "BikerT" "ChamoisHenley"}
           "Oxfords"            #{"ClassicOxford"})
      (are [x y] (= y (o/subclasses now x))
           "Shirts"      #{"Henleys" "Oxfords" "Tshirts"}
           "WomensWear"  #{"Blouses"}
           "MensWear"    #{"Shirts" "Henleys" "Oxfords" "Tshirts"})))

  (let [now (using *conn* kaneda-allstar)]
    (testing "Inference through subclass, pp. 94"
      (is (= #{"AllStarPlayer" "MajorLeaguePlayer"} (o/classes now "Kaneda"))))))

(deftest property-propagation-rules
  (let [now (using *conn* employment-properties workers-in-firm)
        later (using now add-contractor-relationship add-contractors)]
    (testing "Direct property dereference"
      (is (= #{"TheFirm"}  (o/dereference now   "McAdams" :worksFor)))
      (is (= #{}           (o/dereference now   "Spence"  :freelancesTo)))
      (is (= #{"TheFirm"}  (o/dereference later "Spence"  :freelancesTo)))
      (is (= #{"TheFirm"}  (o/dereference now   "Goldman" :isEmployedBy))))
    (testing "Goldman works for The Firm"
      (is (= #{"TheFirm"} (o/dereference now "Goldman" :worksFor))))))

(deftest domain-and-range
  (let [now (using *conn* marriage-properties karens-maiden-name modernize-names rachels-surname)]
    (testing "Domain inference"
      (is (= #{"MarriedWoman" "Woman"} (o/classes now "Karen")))
      (is (= #{"MarriedWoman" "Woman"} (o/classes now "Rachel")))
      (is (= #{"Karen" "Rachel"} (o/inhabitants now "MarriedWoman")))))
  (let [now (using *conn* parentage the-bards-family)]
    (is (= #{"Person"} (o/classes now "bio:JohannesShakespeare")))))

(deftest layering-property-semantics
  (let [now (using *conn* tweeters-are-people two-tweeters)
        later (using now tweeters-have-printable-ids)]
    (is (= #{"Person"}       (o/classes later "Alex")))
    (is (= #{}               (o/classes now   "@holy_chao")))
    (is (= #{"Alphanumeric"} (o/classes later "@holy_chao")))))

(deftest functional-properties
  (let [now (using *conn* parentage the-bards-family)]
    (is (= #{"bio:JohnShakespeare" "bio:JohannesShakespeare"} (o/same-as now "bio:JohannesShakespeare")))
    (is (= #{"bio:JohnShakespeare" "bio:JohannesShakespeare"} (o/same-as now "bio:JohnShakespeare")))
    (is (= #{"spr:Shakespeare" "lit:Shakespeare"} (o/same-as now "lit:Shakespeare")))
    (is (= #{"spr:Shakespeare" "lit:Shakespeare"} (o/same-as now "spr:Shakespeare")))))

(deftest display-labels
  (let [now (using *conn* generic-display named-things)]
    (is (= #{"James Dean"}            (o/identify now "Person1")))
    (is (= #{"Elizabeth Taylor"}      (o/identify now "Person2")))
    (is (= #{"Rebel Without a Cause"} (o/identify now "Movie1")))))

(deftest path-deref
  (let [now (using *conn* tweeters-are-people tweeters-have-printable-ids nicknames two-tweeters)]
    (is (= #{"@mtnygard"}  (o/get-value now "Michael" [:twitter-handle :nickname])))
    (is (= #{"@holy_chao"} (o/get-value now "Alex" [:twitter-handle :nickname])))))

(deftest inverse-properties
  (let [now (using *conn* settings the-bards-works)]
    (is (= #{"lit:Shakespeare"} (o/dereference now "lit:Macbeth"  :lit/writtenBy)))
    (is (= #{"lit:Macbeth"}     (o/dereference now "geo:Scotland" :lit/settingFor)))))

(deftest some-values-from-restriction
  (let [now (using *conn* all-star-player-properties all-star-player-values)]
    (is (= #{"AllStarPlayers"} (o/classes now "Kaneda")))
    (is (= #{"AllStarPlayers"} (o/classes now "Shoup")))
    (is (= #{}                 (o/classes now "Hernandez")))
    (is (= #{"Shoup" "Kaneda"} (o/inhabitants now "AllStarPlayers")))))
