(ns onto.examples.henleys
  (:require [onto.core :as o]))

(def henleys-short-example
  (o/nodes
   (o/subclass "Shirts" "Clothing")
   (o/subclass "Henleys" "Shirts")
   (o/type "ChamoisHenley" "Henleys")))

(def henleys-full-example
  (o/nodes
   (o/subclass "Henleys" "Shirts")
   (o/subclass "Shirts" "MensWear")
   (o/subclass "Blouses" "WomensWear")
   (o/subclass "Oxfords" "Shirts")
   (o/subclass "Tshirts" "Shirts")
   (o/type "ChamoisHenley" "Henleys")
   (o/type "ClassicOxford" "Oxfords")
   (o/type "ClassicOxford" "Shirts")
   (o/type "BikerT" "Tshirts")
   (o/type "BikerT" "MensWear")))
