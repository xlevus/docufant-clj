(ns docufant.postgres
  (:require [clojure.java.jdbc :as jdbc]
            [cheshire.core :refer [generate-string]]
            [clojure.string :as str])
  (:import org.postgresql.util.PGobject))


(defn pgobject [type value]
  (doto (PGobject.)
    (.setType type)
    (.setValue value)))


(defn jsonb [value]
  (pgobject "jsonb" (generate-string value)))


(defn text-array [value]
  (pgobject "text[]" (str "{" (str/join "," (map name value)) "}")))


(defn pointer-operator [path]
  (if (and (coll? path) (> (count path) 1)) "#>" "->"))


(defn json-path
  ([] nil)
  ([key] (if (coll? key) (apply json-path key) (name key)))
  ([key & keys] (text-array (cons key keys))))


(defmulti format-cast
  "Format a sql vector `q` to a cast of type `t`."
  (fn [t q] t))

(defmethod format-cast :default [t _] (throw (Exception. "Unknown type" t)))
(defmethod format-cast nil [_ q] q)
(defmethod format-cast Long [_ [q & params]] (cons (str "(" q ")::int") params))
(defmethod format-cast Integer [_ [q & params]] (cons (str "(" q ")::int") params))
(defmethod format-cast Double [_ [q & params]] (cons (str "(" q ")::float") params))


(defn json-subq
  "Build a sub-query for a postgres JSON path against field."
  ([field path] (json-subq field path {}))
  ([field path {:keys [as]}]
   (format-cast as (filter some?
            [(str (name field)
                  (when path (str
                              " "
                              (if (coll? path) "#" "-")
                              (if as ">>" ">")
                              " ?")))
             (if path (json-path path))]))
   ))


(defn query-reducer
  ([[query & params] new-query]
   (if (coll? new-query)
     (cons (str query (first new-query))
           (concat params (rest new-query)))
     (cons (str query new-query)
           params))))

(defn reduce-q [q]
  (reduce query-reducer [] q))
