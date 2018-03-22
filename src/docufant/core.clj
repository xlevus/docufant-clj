(ns docufant.core
  (:require [clojure.java.jdbc :as j]
            [cheshire.core :refer [parse-string]]
            [docufant.db :as db]
            [docufant.postgres :as pg]
            [honeysql.helpers :as honeysql]
            [honeysql.core :as sql]))


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
  "Creates a document of type `type` with body `data`.
  Returns the created object, with the `:id` field set to `[type id]`"
  [options type data]
  (->> {:_type (name type) :_data (pg/jsonb data)}
       (j/insert! (db/get-spec options) (db/get-opts options :tablename))
       (first)
       (from-db-row)))


(defn update!
  "Update document"
  [options [type id] data]
  (j/update! (db/get-spec options) (db/get-opts options :tablename)
             {:_data (pg/jsonb data)}
             ["_type = ? AND _id = ?" (name type) id]))


(defn build-sqlmap [options type clauses]
  (apply honeysql/merge-where
         (cond-> (honeysql/select :_type :_id :_data)
           true (honeysql/from (:tablename (db/get-opts options)))
           type (honeysql/merge-where [:= :_type (name type)]))
         clauses))


(defn select [db-spec type & clauses]
  (->> (build-sqlmap db-spec type clauses)
       (sql/format)
       (j/query db-spec)
       (map from-db-row)))


(defn get [options [type id]]
  (some-> (j/query (db/get-spec options)
                   [(str "SELECT * FROM " (name (db/get-opts options :tablename))
                         " WHERE _type = ? AND _id = ? LIMIT 1") (name type) id])
          (first)
          (from-db-row)))
