(ns docufant.honeysql
  (:require [honeysql.format :as fmt]
            [docufant.postgres :as pg]))


(defrecord JsonbPath [field path type]
  honeysql.format.ToSql
  (to-sql [v] (let [path (if (< 1 (count path))
                           path
                           (first path))]
                (pg/cast-handler type
                                 (str (fmt/to-sql field)
                                      (if path (str " "
                                                    (if (coll? path) "#" "-")
                                                    (if type ">>" ">")
                                                    " "
                                                    (fmt/to-sql (pg/json-path path)))))
                                 ))))


(defn build-fn-handler [operator]
  (defmethod fmt/fn-handler (str "jsonb" operator)
    ([op field value] (fmt/fn-handler op field (pg/jsonb value) nil))
    ([_ [field & path] value type]
     (str (fmt/to-sql (->JsonbPath field path type))
          " " operator " " (fmt/to-sql value)))))


(build-fn-handler "@>")
(build-fn-handler "<@")
(build-fn-handler "=")
(build-fn-handler ">")
(build-fn-handler ">=")
(build-fn-handler "<")
(build-fn-handler "<=")
(build-fn-handler "<>")



(defmethod fmt/format-clause :create-index
  [[op {:keys [name unique on force]}] sqlmap]
  (str "CREATE "
       (if unique "UNIQUE ")
       "INDEX "
       (if (not force) "IF NOT EXISTS ")
       (fmt/to-sql name)
       " ON " (fmt/to-sql on)))

(fmt/register-clause! :create-index 0)
