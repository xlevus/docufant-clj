(ns docufant.core
  (:refer-clojure :exclude [get < > = <= >=])
  (:require [clojure.java.jdbc :as j]
            [cheshire.core :refer [parse-string]]
            [docufant.db :as db]
            [docufant.postgres :as pg]
            [docufant.operator :as operator]
            [docufant.honeysql :refer [jsonb-path]]
            [docufant.util :refer [strip-kwargs]]
            [docufant.options :refer [with-options get-options get-connection]]
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


(defn find-id [inst]
  (if (contains? inst :id) (:id inst) inst))


(defn create!
  "Creates a document of type `type` with body `data`.
  Returns the created object, with the `:id` field set to `[type id]`"
  [options type data]
  (with-options options
    (->> {:_type (name type) :_data (pg/jsonb data)}
         (j/insert! (get-connection)
                    (get-options :doc-table))
         (first)
         (from-db-row))))


(defn update!
  "Update document with `[type id]` to value `data`."
  [options [type id] data]
  (with-options options
    (j/update! (get-connection)
               (get-options :doc-table)
               {:_data (pg/jsonb data)}
               ["_type = ? AND _id = ?" (name type) id])))


(defn link!
  [options link-type left right]
  (with-options options
    (->> {:_left (last (find-id left)) :_right (last (find-id right)) :_linktype (name link-type)}
         (j/insert! (get-connection)
                    (get-options :link-table)))))


(defmulti select-modifier (fn [modifier query value] modifier))

(defmethod select-modifier :limit [_ query value]
  (honeysql/limit query value))

(defmethod select-modifier :offset [_ query value]
  (honeysql/offset query value))

(defmethod select-modifier :order-by [_ query [path direction]]
  (honeysql/order-by query [(jsonb-path :_data path) direction]))

(defmethod select-modifier :linked-to [_ query [link-type link-target]]
  (-> query
      (honeysql/join [(get-options :link-table) :l] [:= :_id :l._right])
      (honeysql/merge-where [:= :l._left (last (find-id link-target))]
                            [:= :l._linktype (name link-type)])
      ))


(defn base-sqlmap [type]
  (cond-> (honeysql/select :_type :_id :_data)
    true (honeysql/from (get-options :doc-table))
    type (honeysql/merge-where [:= :_type (name type)])))


(defn apply-clauses [query clauses]
  (if (empty? clauses)
    query
    (apply honeysql/merge-where query clauses)))


(defn apply-modifiers [query modifiers]
  (reduce
   (fn [q [m v]] (select-modifier m q v))
   query
   modifiers))


(defn build-sqlmap [type clauses]
  (let [[clauses modifiers] (strip-kwargs clauses)]
    (-> (base-sqlmap type)
        (apply-clauses clauses)
        (apply-modifiers modifiers))))


(defn select [options type & clauses]
  (with-options options
    (->> (build-sqlmap type clauses)
        (sql/format)
        (j/query (get-connection))
        (map from-db-row))))


(defn get [options [type id]]
  (with-options options
    (some->> (honeysql/merge-where (base-sqlmap type) [:= :_id id])
             (sql/format)
             (j/query (get-connection))
             (first)
             (from-db-row))))
