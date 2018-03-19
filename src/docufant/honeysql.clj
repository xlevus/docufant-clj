(ns docufant.honeysql
  (:require [honeysql.format :as fmt]
            [docufant.postgres :as pg]))


(defmulti cast-handler
  "Format a sql string `q` to a cast of type `t`."
  (fn [t q] t))

(defmethod cast-handler :default [t _] (throw (Exception. "Unknown type" t)))
(defmethod cast-handler nil [_ q] (str "(" q ")"))
(defmethod cast-handler Long [_ q] (str "(" q ")::int"))
(defmethod cast-handler Double [_ q] (str "(" q ")::float"))


(defn json-path
  ([field] (fmt/to-sql field))
  ([field path] (json-path field path nil))
  ([field path type]
   (let [path (if (< 1 (count path))
                path
                (first path))]
     (cast-handler type
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
     (str (json-path field path type) " " operator " " (fmt/to-sql value)))))


;; (defmethod fmt/fn-handler "jsonb_="
;;   ([op field value] (fmt/fn-handler op field (pg/jsonb value) nil))
;;   ([_ [field & path] value type]
;;    (str (json-path field path type) " = " (fmt/to-sql value))))


(build-fn-handler "@>")
(build-fn-handler "<@")
(build-fn-handler "=")
(build-fn-handler ">")
(build-fn-handler ">=")
(build-fn-handler "<")
(build-fn-handler "<=")
(build-fn-handler "<>")
