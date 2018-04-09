(ns docufant.db
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as string]
            [honeysql.core :as sql]
            [docufant.postgres :as pg]
            [docufant.honeysql :refer [jsonb-path]]
            [docufant.options :refer [with-options get-options get-connection]]
            [clojure.string :as str]))


(defn create-table-sql
  "Gets the SQL for the docufant tables.
  If the `:force option` is `false` (the default), the table will only be created if
  it does not already exist."
  []
  [(str "CREATE TABLE "
        (if (get-options :force-creation) nil "IF NOT EXISTS ")
        (name (get-options :doc-table))
        " (_id SERIAL PRIMARY KEY,"
        "_type VARCHAR(100),"
        "_data JSONB);")])


(defn create-link-sql
  []
  [(str "CREATE TABLE "
        (if (get-options :force-creation) nil "IF NOT EXISTS ")
        (name (get-options :link-table))
        "(_left INTEGER REFERENCES " (name (get-options :doc-table)) "(_id), "
        "_right INTEGER REFERENCES " (name (get-options :doc-table)) "(_id), "
        "_linktype VARCHAR(100));"
        )])


(defn indexname [{:keys [unique index-type type path as]}]
  (str "dfidx_"
       (name (get-options :doc-table)) "_"
       (if path (str/join "_" (map name path)))
       (if index-type (str "__" (name index-type)))
       (if type (str "__" (name type)))
       (if unique "__uniq")
       ))


(defmulti index-target (fn [index] (:index-type index)))

(defmethod index-target nil [{:keys [path]}] {:field (jsonb-path :_data path)})
(defmethod index-target :gin [{:keys [gin-type]}] {:using (sql/raw "gin(_data jsonb_ops)")})


(defn build-index
  "Formats the SQL for the given index."
  [{:keys [unique path type as] :as index}]
  {:create-index (merge {:name (sql/raw (indexname index))
                         :on (get-options :doc-table)
                         :unique unique}
                        (index-target index))
   :where (if type [:= :_type (name type)] true)})


(defn create-tables!
  "Create the docufant tables."
  [options]
  (with-options options
    (j/execute! (get-connection) (create-table-sql))
    (j/execute! (get-connection) (create-link-sql))))


(defn create-index!
  "Creates an `index`. The same force semantics apply as to `create-tables!`

  Accepts the following keys:

  * `:index-type` either `:gin` or `nil`
  * `:type` The document type to apply the index against, if `nil`, all types
    will be indexed.
  * `:path` The JSON path to the value to index.
  * `:unique` Creates a UNIQUE index.
  * `:as` Cast the JSON value to a Postgres type. Accepts `Integer`,`Double`,
    `Long`. More types can be supported by adding methods to
    `docufant.postgres/format-cast`"
  [options index]
  (with-options options
    (j/execute! (get-connection)
               (-> (build-index index)
                   (sql/format)))))
