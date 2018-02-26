(ns docufant.db
  (:require [clojure.java.jdbc :as j]))


(def gin-index-types #{:jsonb_path_ops :jsonb_ops})
(def default-opts {:tablename :docufant
                   :gin :jsonb_path_ops})


(defn tablename [db-spec] :docufant)


(defn opts [db-spec] (merge default-opts (:docufant db-spec)))


(defn create-tables! [db-spec]
  (let [{:keys [force]} (opts db-spec)]
    (j/execute!
     db-spec
     [(str "CREATE TABLE"
           (if force nil " IF NOT EXISTS ")
           (name (tablename db-spec))
           " (_id SERIAL PRIMARY KEY,"
           "_type VARCHAR(100),"
           "_data JSONB);")])))


(defn create-gin-index! [db-spec]
  (let [{:keys [gin force]} (opts db-spec)]
    (when (contains? gin-index-types gin)
      (j/execute!
       db-spec
       [(str "CREATE INDEX "
             (if force nil " IF NOT EXISTS ")
             "idx_"
             (name (tablename db-spec))
             "_data ON "
             (name (tablename db-spec))
             " USING GIN(_data "
             (name gin)
             ")")]))))

