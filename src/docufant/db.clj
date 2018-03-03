(ns docufant.db
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as string]
            [docufant.postgres :as pg]))


(def gin-index-types #{:jsonb_path_ops :jsonb_ops})
(def default-opts {:tablename :docufant
                   :force false})


(defn get-opts
  "Gets the docufant-options from an opts/db-spec map, and fills in any defaults.

  If `:db-spec` is present, the map will be returned sans db-spec, otherwise the
  default options will be returned."
  ([{:keys [db-spec] :as options}]
   (merge default-opts
          (if db-spec
            (dissoc options :db-spec)
            {})))
  ([options key] (key (get-opts options))))

(defn get-spec
  "Returns the db-spec from a options/db-spec map.

  If a key `:db-spec` is present, that will be returned, otherwise the map will be
  returned."
  [{:keys [db-spec] :as options}]
  (if db-spec db-spec options))


(defn create-table-sql
  "Gets the SQL for the docufant tables.
  If the `:force option` is `false` (the default), the table will only be created if
  it does not already exist."
  [options]
  (let [{:keys [force tablename]} (get-opts options)]
    [(str "CREATE TABLE"
          (if force nil " IF NOT EXISTS ")
          (name tablename)
          " (_id SERIAL PRIMARY KEY,"
          "_type VARCHAR(100),"
          "_data JSONB);")]))


(defn create-tables!
  "Create the docufant tables."
  [options]
  (j/execute! (get-spec options) (create-table-sql options)))


(defn indexname
  [path]
  (if (coll? path)
    (string/join "_" (map name path))
    (name path)))


(defn format-index
  "Formats the SQL for the given index."
  [options index-name clause {:keys [unique type]}]
  (let [{:keys [force tablename] :as opts} (get-opts options)]
    (pg/reduce-q ["CREATE " (if unique "UNIQUE ") "INDEX "
                  (if force nil "IF NOT EXISTS ")
                  "idx_" (name tablename) (if type (str "__" (name type))) "__" index-name
                  " ON " (name tablename) " "
                  clause
                  (if type [" WHERE _type = ?" type])])))


(defmulti build-index (fn [options {:keys [index-type]}] index-type))

(defmethod build-index :gin [options {:keys [gin-type type]}]
  (format-index options
                "gin"
                (str "USING GIN(_data " (name gin-type) ")")
                {:type type}))


(defmethod build-index nil [options {:keys [path unique type as]}]
  (format-index options
                (indexname path)
                (pg/reduce-q ["((" (pg/json-subq :_data path {:as as}) "))"])
                {:type type}))


(defn create-index!
  "Creates an `index`. The same force semantics apply as to `create-tables!`

  Accepts the following keys:

  * `:index-type` either `:gin` or `nil`
  * `:gin-type` If the index is a GIN index, the gin type to create.
    Defaults to `:jsonb_path_ops`
  * `:type` The document type to apply the index against, if `nil`, all types
    will be indexed.
  * `:path` The JSON path to the value to index.
  * `:unique` Creates a UNIQUE index.
  * `:as` Cast the JSON value to a Postgres type. Accepts `Integer`,`Double`,
    `Long`. More types can be supported by adding methods to
    `docufant.postgres/format-cast`"
  [options index]
  (j/execute! (get-spec options)
              (build-index options index)))
