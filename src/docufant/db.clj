(ns docufant.db
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as string]
            [docufant.postgres :as pg]))


(def gin-index-types #{:jsonb_path_ops :jsonb_ops})
(def default-opts {:tablename :docufant
                   :gin :jsonb_path_ops
                   :force false})


(defn get-opts
  "Gets the opts from an opts/spec map, and fills in any defaults."
  ([{:keys [db-spec] :as options}]
   (merge default-opts
          (if db-spec
            (dissoc options :db-spec)
            {})))
  ([options key] (key (get-opts options))))

(defn get-spec
  "Gets the db-spec from an opts/spec map"
  [{:keys [db-spec] :as options}]
  (if db-spec db-spec options))


(defn create-tables! [options]
  (let [{:keys [force tablename]} (get-opts options)]
    (j/execute!
     (get-spec options)
     [(str "CREATE TABLE"
           (if force nil " IF NOT EXISTS ")
           (name tablename)
           " (_id SERIAL PRIMARY KEY,"
           "_type VARCHAR(100),"
           "_data JSONB);")])))


(defn create-gin-index! [options]
  (let [{:keys [gin force tablename]} (get-opts options)]
    (when (contains? gin-index-types gin)
      (j/execute!
       (get-spec options)
       [(str "CREATE INDEX "
             (if force nil " IF NOT EXISTS ")
             "idx_"
             (name tablename)
             "_data ON "
             (name tablename)
             " USING GIN(_data "
             (name gin)
             ")")]))))


(defn indexname [path]
  (if (coll? path)
    (string/join "_" (map name path))
    (name path)))


(defn format-index [options index-name clause {:keys [unique type]}]
  (let [{:keys [force tablename] :as opts} (get-opts options)]
   (pg/reduce-q ["CREATE " (if unique "UNIQUE ") "INDEX "
                (if force nil "IF NOT EXISTS ")
                "idx_" (name tablename) (if type (str "__" (name type))) "__" index-name
                " ON " (name tablename)
                " ( "clause " )"
                (if type [" WHERE _type = ?" type])])))


(defn build-index [options {:keys [path type unique] :as index}] )


(defn create-index! [options index]
  (j/execute! (get-spec options)(build-index options index)))

