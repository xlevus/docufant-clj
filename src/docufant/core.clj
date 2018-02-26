(ns docufant.core
  (:require [clojure.java.jdbc :as j]
            [cheshire.core :refer [parse-string]]
            [docufant.db :as db]
            [docufant.postgres :as pg]))


(defn init! [db-spec]
  (db/create-tables! db-spec)
  (db/create-gin-index! db-spec))


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


(defn pointer [path]
  (if (and (coll? path) (> (count path) 1)) "#>" "->"))


(defn path
  ([] nil)
  ([key] (if (coll? key) (apply path key) (name key)))
  ([key & keys] (pg/text-array (cons key keys))))


(defmulti clause-handler (fn [op & args] op))

(defmethod clause-handler :contains [_ v]
  [(str "_data @> ?") (pg/jsonb v)])

(defmethod clause-handler :has-keys
  ([_ v] [(str "_data ??& ?") (pg/text-array v)])
  ([_ p v] [(str "_data " (pointer p) " ? ??& ?") (path p) (pg/text-array v)]))

(defmethod clause-handler :default
  ([op v] [(str "_data " (name op) " ?") (pg/jsonb v)])
  ([op p v] [(str "_data " (pointer p) " ? " (name op) " ?") (path p) (pg/jsonb v)]))


(defn format-sql [options type clauses]
  (loop [rest clauses
         sql (str "SELECT * FROM "
                  (name (db/get-opts options :tablename))
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

(defn create! [options type data]
  (->> {:_type (name type) :_data (pg/jsonb data)}
       (j/insert! (db/get-spec options) (db/get-opts options :tablename))
       (first)
       (from-db-row)))


(defn update! [options [type id] data]
  (j/update! (db/get-spec options) (db/get-opts options :tablename)
             {:_data (pg/jsonb data)}
             ["_type = ? AND _id = ?" (name type) id]))


(defn select [db-spec type clauses]
  (->> (format-sql db-spec type clauses)
       (j/query db-spec)
       (map from-db-row)))


(defn get [options [type id]]
  (some-> (j/query (db/get-spec options)
                   [(str "SELECT * FROM " (name (db/get-opts options :tablename))
                         " WHERE _type = ? AND _id = ? LIMIT 1") (name type) id])
          (first)
          (from-db-row)))
