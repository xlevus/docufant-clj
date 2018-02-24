(ns docufant.db
  (:require [clojure.java.jdbc :as j]))


(defonce ^:private default-connection (atom nil))
(def ^:dynamic *transaction* nil)


(defn set-default-connection! [conn] (reset! default-connection conn))
(defn connection [] (or *transaction* @default-connection))


(defn do-in-transaction [f]
  (j/with-db-transaction [conn (connection)]
    (binding [*transaction* conn]
      (f))))


(defmacro transaction
  {:arglists '([body] [options & body]), :style/indent 0}
  [& body]
  `(do-in-transaction (fn [] ~@body)))


(defn rollback! []
  (if (nil? *transaction*) (throw (Exception. "Not in a transaction"))
      (j/db-set-rollback-only! *transaction*)))
