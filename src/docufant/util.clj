(ns docufant.util)


(defn strip-kwargs
  "Strips any 'keyword arguments' from the tail of a list"
  [clauses]
  (loop [r clauses
         claus []
         opts {}]
    (if r
      (if (or (not (empty? opts)) (keyword? (first r)))
        (recur (nthnext r 2) claus (assoc opts (first r) (second r)))
        (recur (next r) (conj claus (first r)) opts))
      [claus opts])))



(defn spy
  ([x] (println x) x)
  ([x y] (println x y) y))
