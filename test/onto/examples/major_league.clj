(ns onto.examples.major-league
  (:require [onto.core :as o]
            [onto.examples.helpers :refer :all]))

(def kaneda-allstar
  (o/nodes
   (o/subclass "AllStarPlayer" "MajorLeaguePlayer")
   (o/type "Kaneda" "AllStarPlayer")))

(def all-star-player-properties
  (o/properties
   (o/oproperty  :plays-for :many)
   (o/inverse-of :plays-for :has-player :many)))

(def all-star-player-values
  (o/nodes
   (o/some-values-from "AllStarPlayers" :plays-for "AllStarTeam")
   (o/t "Cardinals"         :type "Team")
   (o/t "Twins"             :type "Team")
   (o/t "AL East All Stars" :type "AllStarTeam")
   (o/t "Kaneda"            :plays-for "Twins")
   (o/t "Kaneda"            :plays-for "AL East All Stars")
   (o/t "Hernandez"         :plays-for "Cardinals")
   (o/t "Shoup"             :plays-for "AL East All Stars")))
