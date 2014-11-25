(ns onto.examples.shakespeare
  (:require [onto.core :as o]))

(def parentage
  (o/properties
   (o/oproperties :has-father :buried-at)
   (o/functional-property :has-father)
   (o/inverse-functional-property :buried-at)
   (o/range :has-father "Person")))

(def the-bards-family
  (o/nodes
   (o/t "lit:Shakespeare" :has-father "bio:JohannesShakespeare")
   (o/t "lit:Shakespeare" :has-father "bio:JohnShakespeare")
   (o/t "lit:Shakespeare" :buried-at "TrinityChancel")
   (o/t "spr:Shakespeare" :buried-at "TrinityChancel")))

(def settings
  (o/properties
   (o/oproperty :lit/wrote :one)
   (o/oproperty :lit/setIn :one)
   (o/inverse-of :lit/wrote :lit/writtenBy)
   (o/inverse-of :lit/setIn :lit/settingFor)))

(def the-bards-works
  (o/nodes
   (o/t "lit:Shakespeare" :lit/wrote "lit:Macbeth")
   (o/t "lit:Macbeth"     :lit/setIn "geo:Scotland")))
