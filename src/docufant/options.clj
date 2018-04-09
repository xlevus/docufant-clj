(ns docufant.options)

(def default-options {:doc-table :docufant
                      :link-table :docufant_link
                      :force-creation false
                      :schema nil})


(def ^:private ^:dynamic *options* nil)
(def ^:private ^:dynamic *db-spec* nil)
(def ^:private ^:dynamic *transaction* nil)


(defn get-options
  ([] (or *options* default-options))
  ([key] (key (get-options))))


(defn get-connection []
  (or *transaction*
      *db-spec*
      (throw (Exception. "No db-spec provided."))))


(defn split-spec-from-options [options]
  (if (contains? options :db-spec)
    [(:db-spec options) (merge default-options (dissoc options :db-spec))]
    [options default-options]))


(defn do-with-options [opts f]
  (let [[db-spec options] (split-spec-from-options opts)]
    (binding [*options* options
              *db-spec* db-spec]
      (f))))


(defmacro with-options [options & body]
  `(do-with-options ~options (fn [] ~@body)))
