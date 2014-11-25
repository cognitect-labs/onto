(ns onto.examples.labeling
  (:require [onto.core :as o :refer [v]]
            [onto.examples.helpers :refer :all]))

(def generic-display
  (o/properties
   (o/label :personName)
   (o/label :movieTitle)))

(def named-things
  (o/nodes
   (v "Person1" :personName "James Dean")
   (v "Person2" :personName "Elizabeth Taylor")
   (v "Person3" :personName "Rock Hudson")
   (v "Movie1" :movieTitle "Rebel Without a Cause")
   (v "Movie2" :movieTitle "Giant")
   (v "Movie3" :movieTitle "East of Eden")))
