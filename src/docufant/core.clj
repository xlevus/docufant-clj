(ns docufant.core
  (:refer-clojure :exclude [get < > = <= >=])
  (:require [clojure.java.jdbc :as j]
            [cheshire.core :refer [parse-string]]
            [docufant.db :as db]
            [docufant.postgres :as pg]
            [docufant.operator :as operator]
            [docufant.honeysql :refer [jsonb-path]]
            [honeysql.helpers :as honeysql]
            [honeysql.util :refer [defalias]]
            [honeysql.core :as sql]))

(defalias = operator/=)
(defalias > operator/>)
(defalias < operator/<)
(defalias <= operator/<=)
(defalias >= operator/>=)
(defalias <> operator/<>)


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
  "Update document with `[type id]` to value `data`."
  [options [type id] data]
  (j/update! (db/get-spec options) (db/get-opts options :tablename)
             {:_data (pg/jsonb data)}
             ["_type = ? AND _id = ?" (name type) id]))


(defn- strip-kwargs
  "Strips any 'keyword arguments' from the tail of a list"
  [clauses]
  (loop [r clauses
         claus []
         opts {}]
    (if r
      (if (or (not (empty? opts)) (keyword? (first r)))
        (recur (nthnext r 2) claus (assoc opts (first r) (second r)))
        (recur (next r) (conj claus (first r)) opts))
      [claus opts])))



(defmulti select-modifier (fn [query [modifier value]] modifier))

(defmethod select-modifier :limit [query [_ value]]
  (honeysql/limit query value))

(defmethod select-modifier :offset [query [_ value]]
  (honeysql/offset query value))

(defmethod select-modifier :order-by [query [_ [path direction]]]
  (honeysql/order-by query [(jsonb-path :_data path) direction]))


(defn base-sqlmap [options type]
  (cond-> (honeysql/select :_type :_id :_data)
    true (honeysql/from (:tablename (db/get-opts options)))
    type (honeysql/merge-where [:= :_type (name type)])))


(defn apply-clauses [query clauses] (apply honeysql/merge-where query clauses))
(defn apply-modifiers [query modifiers] (reduce select-modifier query modifiers))


(defn build-sqlmap [options type clauses]
  (let [[clauses modifiers] (strip-kwargs clauses)]
    (-> (base-sqlmap options type)
        (apply-clauses clauses)
        (apply-modifiers modifiers))))


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
