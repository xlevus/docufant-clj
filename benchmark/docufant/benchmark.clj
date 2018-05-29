(ns docufant.benchmark
  (:require [clojure.java.jdbc :as j]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [docufant.core :as doc]
            [clojure.java.jdbc :as jdbc]))

(def ^:dynamic *db*
  {:dbtype "postgresql"
   :dbname "docufant_test"
   :user "docufant"
   :password "password"
   :port 5433})


(s/def ::name string?)
(s/def ::age (s/int-in 1 100))
(s/def ::occupation #{:butcher :baker :candlestick-maker})
(s/def ::person (s/keys :req-un [::name ::occupation ::age]))


(def person-gen (s/gen ::person))


(defn drop-tables []
  (doseq [t ["docufant_link" "docufant" "person"]]
    (j/execute! *db* (str "DROP TABLE IF EXISTS " t))))


(defn create-table-sql []
  (println "Creating tables.")
  (time
   (j/execute! *db*
               (str "CREATE TABLE IF NOT EXISTS person ("
                    "name VARCHAR(100), "
                    "occupation VARCHAR(50), "
                    "age SMALLINT);"))))


(defn create-table-docufant []
  (println "Creating tables.")
  (time (doc/init! *db*)))


(defn insert-person-docufant [p]
  (doc/create! *db* :person p))


(defn insert-person-sql [p]
  (j/insert! *db* :person {:name (:name p)
                           :occupation (name (:occupation p))
                           :age (:age p)}))


(defn insert-people [people insert-fn]
  (println "Inserting " (count people) " rows")
  (time
   (doseq [p people]
     (insert-fn p))))


(defn select-person-docufant [occupation]
  (doc/select *db* :person (doc/= [:occupation] occupation)))


(defn select-person-sql [occupation]
  (j/query *db* ["SELECT * FROM person WHERE occupation = ?" (name occupation)]))


(defn select-people [occupation select-fn]
  (println "Selecting all " occupation)
  (time
   (println "Found" (count (select-fn occupation)))))


(defn select-old-person-docufant [occupation age]
  (doc/select *db* :person
              (doc/= [:occupation] occupation)
              (doc/> [:age] age)
              :order-by [[:age] :desc]))


(defn select-old-person-sql [occupation age]
  (j/query *db* ["SELECT * FROM person WHERE occupation = ? AND age > ? ORDER BY AGE DESC"
                 (name occupation)
                 age]))


(defn select-old-people [occupation age select-fn]
  (println "Selecting all " occupation " older than " age)
  (time
   (println "Found" (count (select-fn occupation age)))))


(defn bench-docufant [people insert]
  (println "Docufant: ")
  (create-table-docufant)
  (if insert (insert-people people insert-person-docufant))
  (select-people :butcher select-person-docufant)
  (select-old-people :butcher 50 select-old-person-docufant)
  (println ""))


(defn bench-sql [people insert]
  (println "SQL: ")
  (create-table-sql)
  (if insert (insert-people people insert-person-sql))
  (select-people :butcher select-person-sql)
  (select-old-people :butcher 50 select-old-person-sql)
  (println ""))


(defn create-indexes []
  (println "Creating indexes")
  (doc/init! *db* {:type :person :path [:occupation]})
  (j/execute! *db* "CREATE INDEX person_occupation_idx ON person (occupation);")
  )


(defn -main []

  (drop-tables)

  (let [people (doall (gen/sample person-gen 100))]
    (bench-docufant people true)
    (bench-sql people true)

    (create-indexes)

    (bench-docufant people false)
    (bench-sql people false)
    ))
