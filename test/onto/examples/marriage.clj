(ns onto.examples.marriage
  "Apologies for the outdated example. Taken (almost) directly from
   'Semantic Web for the Working Ontologist', pp 99-100"
  (:require [onto.core :as o]
            [onto.examples.helpers :refer :all]))

(def marriage-properties
  (o/properties
   (o/dtproperty :maidenName :string :many)
   (o/domain     :maidenName "MarriedWoman")
   (o/range      :maidenName "Surname")))

(def modernize-names
  (o/properties
   (o/dtproperty  :hyphenatedName :string :many)
   (o/subproperty :hyphenatedName :maidenName)))

(def karens-maiden-name
  (o/nodes
   (o/subclass  "MarriedWoman" "Woman")
   (o/v "Karen" :maidenName    "Stephens")))

(def rachels-surname
  (o/nodes
   (o/v "Rachel" :hyphenatedName "Voight-Kampf")))

(assert (not (tempid-collisions? (concat marriage-properties karens-maiden-name modernize-names rachels-surname)))
        "Test data collision. All members must hash to unique values")
