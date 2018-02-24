(ns docufant.core
  (:require [clojure.java.jdbc :as j]
            [cheshire.core :refer [parse-string]]
            [docufant.db :as db]
            [docufant.postgres :as pg]))

(def ^:private table-name (atom nil))

(def gin-index-types #{:jsonb_path_ops :jsonb_ops})


(defn init-tables! [db-spec {:keys [table force]}]
  (j/execute!
   db-spec
   [(str "CREATE TABLE"
         (if force nil " IF NOT EXISTS ")
         (name table)
         " (_id SERIAL PRIMARY KEY,"
         "_type VARCHAR(100),"
         "_data JSONB);")]))


(defn init-gin! [db-spec {:keys [table force gin]}]
  (j/execute!
   db-spec
   [(str "CREATE INDEX "
         (if force nil " IF NOT EXISTS ")
         "idx_"
         (name table)
         "_data ON "
         (name table)
         " USING GIN(_data "
         (name gin)
         ")")]))


(defn init!
  ([db-spec] (init! db-spec {:table :docufant
                             :force false
                             :gin :jsonb_path_ops}))
  ([db-spec opts]
   (reset! table-name (:table opts))
   (init-tables! db-spec opts)

   (when (contains? gin-index-types (:gin opts))
     (init-gin! db-spec opts))))


(defn from-db-row [row]
  (let [id (:_id row)
        type (-> row
                 (:_type)
                 (keyword))
        data (-> row
                 (:_data)
                 (.getValue)
                 (parse-string true))]
    (assoc data :id [type id])))


(defn create!
  ([type data] (create! (db/connection) type data))
  ([db-spec type data]
   (->> {:_type (name type) :_data (pg/jsonb data)}
        (j/insert! db-spec @table-name)
        (first)
        (from-db-row))))

(defn update!
  ([id data] (update! (db/connection) id data))
  ([db-spec [type id] data] (j/update! db-spec @table-name
                                 {:_data (pg/jsonb data)}
                                 ["_type = ? AND _id = ?" (name type) id])))

(defn pointer [path]
  (if (and (coll? path) (> (count path) 1)) "#>" "->"))


(defn path
  ([] nil)
  ([key] (if (coll? key) (apply path key) (name key)))
  ([key & keys] (pg/text-array (cons key keys))))


(defmulti clause-handler (fn [op & args] op))

(defmethod clause-handler :=
  ([_ v] [(str "_data = ?") (pg/jsonb v)])
  ([_ p v] [(str "_data " (pointer p) " ? = ?") (path p) (pg/jsonb v)]))

(defmethod clause-handler :contains [_ v]
  [(str "_data @> ?") (pg/jsonb v)])

(defmethod clause-handler :has-keys
  ([_ v] [(str "_data ??& ?") (pg/text-array v)])
  ([_ p v] [(str "_data " (pointer p) " ? ??& ?") (path p) (pg/text-array v)]))


(defn format-sql [type clauses]
  (loop [rest clauses
         sql (str "SELECT * FROM "
                  (name @table-name)
                  (if (nil? type)
                    " WHERE true"
                    " WHERE _type = ?"))
         params (if (nil? type) [] [(name type)])]
    (if (empty? rest)
      (cons sql params)
      (let [[q & p] (apply clause-handler (first rest))]
        (recur (next rest)
               (str sql " AND " q)
               (concat params p)))
      )))


(defn select
  ([type clauses] (select (db/connection) type clauses))
  ([db-spec type clauses] (->> (format-sql type clauses)
                               (j/query db-spec)
                               (map from-db-row))))

(defn get
  ([id] (get (db/connection) id))
  ([db-spec [type id]] (some-> (j/query db-spec
                                        [(str "SELECT * FROM "
                                              (name @table-name)
                                              " WHERE _type = ? AND _id = ? LIMIT 1") (name type) id])
                               (first)
                               (from-db-row))))
