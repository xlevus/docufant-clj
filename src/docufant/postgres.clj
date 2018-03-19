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
  (pgobject "text[]" (str "{" (str/join "," (map #(if (keyword %) (name %) %) value)) "}")))


(defn pointer-operator [path]
  (if (and (coll? path) (> (count path) 1)) "#>" "->"))


(defn json-path
  ([] nil)
  ([key] (if (coll? key)
           (apply json-path key)
           (name key)))
  ([key & keys] (text-array (cons key keys))))


(defmulti cast-handler
  "Format a sql string `q` to a cast of type `t`."
  (fn [t q] t))

(defmethod cast-handler :default [t _] (throw (Exception. "Unknown type" t)))
(defmethod cast-handler nil [_ q] (str "(" q ")"))
(defmethod cast-handler Long [_ q] (str "(" q ")::int"))
(defmethod cast-handler Double [_ q] (str "(" q ")::float"))
