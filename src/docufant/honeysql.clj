(ns docufant.honeysql
  (:require [honeysql.format :as fmt]
            [docufant.postgres :as pg]))


(defrecord JsonbPath [field path as-type]
  honeysql.format.ToSql
  (to-sql [v]
    (let [path (if (< 1 (count path))
                 path
                 (first path))]
      (pg/cast-handler
       as-type
       (str (fmt/to-sql field)
            (if path (str " "
                          (if (coll? path) "#" "-")
                          (if as-type ">>" ">")
                          " "
                          (fmt/to-sql (pg/json-path path)))))
       ))))


(defn jsonb-path
  ([field] (jsonb-path field nil nil))
  ([field path] (jsonb-path field path nil))
  ([field path type] (->JsonbPath field path type))
  )


(defn build-fn-handler [operator]
  (defmethod fmt/fn-handler (str "jsonb" operator)
    [_ field value]
     (str (fmt/to-sql field) " " operator " "
          (fmt/to-sql
           (if (:as-type field) value (pg/jsonb value))))))


(build-fn-handler "@>")
(build-fn-handler "<@")
(build-fn-handler "=")
(build-fn-handler ">")
(build-fn-handler ">=")
(build-fn-handler "<")
(build-fn-handler "<=")
(build-fn-handler "<>")



(defmethod fmt/format-clause :create-index
  [[op {:keys [name unique on field force using]}] sqlmap]
  (str "CREATE "
       (if unique "UNIQUE ")
       "INDEX "
       (if (not force) "IF NOT EXISTS ")
       (fmt/to-sql name)
       " ON " (fmt/to-sql on)
       (if using (str " USING " (fmt/to-sql using)))
       (if field (str " (" (fmt/to-sql field) ")"))
       ))

(fmt/register-clause! :create-index 0)
