(ns onto.examples.social
  (:require [onto.core :as o]
            [onto.examples.helpers :refer :all]))

(def tweeters-are-people
  (o/properties
   (o/oproperty :twitter-handle :many)
   (o/domain :twitter-handle "Person")))

(def tweeters-have-printable-ids
  (o/properties
   (o/range :twitter-handle "Alphanumeric")
   (o/dtproperty :nickname :string :one)))

(def two-tweeters
  (o/nodes
   (o/t "Michael"    :twitter-handle "@mtnygard")
   (o/t "Alex"       :twitter-handle "@holy_chao")))

(def nicknames
  (o/nodes
   (o/v "@holy_chao" :nickname "@holy_chao")
   (o/v "@mtnygard"  :nickname "@mtnygard")))

(def two-tweeters-using-bnodes
  (o/nodes
   (o/v "Michael" :twitter-handle [:nickname "@mtnygard"])
   (o/v "Alex"    :twitter-handle [:nickname "@holy_chao"])))

(assert (not (tempid-collisions? (concat tweeters-are-people tweeters-have-printable-ids two-tweeters)))
        "Test data collision. All members must hash to unique values")
