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