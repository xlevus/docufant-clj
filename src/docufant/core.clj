(ns docufant.core
  (:require [clojure.java.jdbc :as j]
            [cheshire.core :refer [parse-string]]
            [docufant.db :as db]
            [docufant.postgres :as pg]))


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


(defmulti clause-handler (fn [op & args] op))

(defmethod clause-handler :contains [_ v]
  [(str "_data @> ?") (pg/jsonb v)])

(defmethod clause-handler :has-keys
  ([op v] (clause-handler op nil v))
  ([op p v] (pg/reduce-q [(pg/json-subq :_data p)
                          [(str " ??& ?") (pg/text-array v)]])))

(defmethod clause-handler :default
  ([op v] (clause-handler op nil v))
  ([op p v] (pg/reduce-q [(pg/json-subq :_data p)
                          [(str " " (name op) " ?") (pg/jsonb v)]])))


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
