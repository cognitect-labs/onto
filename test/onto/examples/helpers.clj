(ns onto.examples.helpers)

(defn tempid-collisions?
  [data]
  (let [hashes (map hash (distinct (map :label data)))]
    (not= hashes (distinct hashes))))
