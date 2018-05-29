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
(s/def ::value integer?)
(s/def ::weight integer?)
(s/def ::identifier uuid?)
(s/def ::a string?)
(s/def ::b string?)
(s/def ::c string?)
(s/def ::widget (s/keys :req-un [::name ::value ::weight ::identifier ::a ::b ::c]))

(def widget-gen (s/gen ::widget))

(s/def ::age (s/int-in 1 100))
(s/def ::occupation #{:butcher :baker :candlestick-maker})
(s/def ::widgets (s/coll-of ::widget :count 10 :kind vector?))
(s/def ::person (s/keys :req-un [::name ::occupation ::age ::widget ::widgets]))


(def person-gen (s/gen ::person))


(defn drop-tables []
  (doseq [t ["docufant_link" "docufant" "person" "widget"]]
    (j/execute! *db* (str "DROP TABLE IF EXISTS " t " CASCADE;"))))


(defn create-table-sql []
  (println "Creating tables.")
  (time (do
   (j/execute! *db*
               (str "CREATE TABLE IF NOT EXISTS person ("
                    "id SERIAL PRIMARY KEY, "
                    "name VARCHAR(100), "
                    "occupation VARCHAR(50), "
                    "age SMALLINT);"))
   (j/execute! *db*
               (str "CREATE TABLE IF NOT EXISTS widget ("
                    "owner INTEGER REFERENCES person (id), "
                    "main boolean, "
                    "name VARCHAR(100), "
                    "identifier VARCHAR(36) UNIQUE, "
                    "type VARCHAR(10), "
                    "value BIGINT, "
                    "weight BIGINT, "
                    "a VARCHAR(100), "
                    "b VARCHAR(100), "
                    "c VARCHAR(100));")))))


(defn create-table-docufant []
  (println "Creating tables.")
  (time (doc/init! *db*
                   {:type :person :path [:widget :identifier] :unique true}
                   {:type :widget :path [:identifier] :unique true})))


(defn insert-widget-docufant [tx owner widget]
  (let [{:keys [id]} (doc/create! tx :widget widget)]
    (doc/link! tx :owner (:id owner) id)))


(defn insert-person-docufant [p]
  (j/with-db-transaction [tx *db*]
    (let [person (doc/create! tx :person (dissoc p :widgets))]
      (doseq [w (:widgets p)]
        (insert-widget-docufant tx person w))
     )))


(defn get-person-row [p]
  {:name (:name p) :occupation (name (:occupation p)) :age (:age p)})


(defn insert-widget-sql [tx owner widget main]
  (j/insert! tx :widget (conj widget {:owner (:id owner)
                                      :main main})))


(defn insert-person-sql [p]
  (j/with-db-transaction [tx *db*]
    (let [person (first (j/insert! tx :person (get-person-row p)))]
      (insert-widget-sql tx person (:widget p) true)
      (doseq [w (:widgets p)]
        (insert-widget-sql tx person w false)))))


(defn insert-people [people insert-fn]
  (println "Inserting " (count people) " rows")
  (time
   (doseq [p people]
     (insert-fn p))))


(defn select-person-docufant [occupation]
  (doc/select *db* :person (doc/= [:occupation] occupation)))


(defn select-person-sql [occupation]
  (j/query *db* [(str "SELECT p.* FROM person p, widget w "
                      "WHERE p.id = w.owner "
                      "AND w.main = true "
                      "AND occupation = ?") (name occupation)]))


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
  (j/query *db* [(str "SELECT p.* FROM person p, widget w "
                      "WHERE p.id = w.owner "
                      "AND w.main = true "
                      "AND occupation = ? "
                      "AND age > ?"
                      "ORDER BY age DESC") (name occupation) age]))


(defn select-old-people [occupation age select-fn]
  (println "Selecting all " occupation " older than " age)
  (time
   (println "Found" (count (select-fn occupation age)))))


(defn select-hv-widgets-docufant [people]
  (doseq [p (take 10 people)]
    (count (doc/select *db* :widget
                       (doc/> [:value] 0)
                       (doc/> [:weight] 0)
                       :linked-to [:owner p]))))


(defn select-hv-widgets-sql [people]
  (doseq [p people]
    (count (j/query *db* [(str "SELECT * FROM widget WHERE "
                               "owner = ? "
                               "AND main = false "
                               "AND weight > ? "
                               "AND value > ? ") (:id p) 0 0]))))


(defn select-hv-widgets [select-people-fn select-widget-fn]
  (println "Selecting heavy-valuable widgets owned by 50 butchers")
  (let [people (select-people-fn :butcher)]
    (time (select-widget-fn (take 50 people))))
  )


(defn bench-docufant [people insert]
  (println "Docufant: ")
  (create-table-docufant)
  (if insert (insert-people people insert-person-docufant))
  (select-people :butcher select-person-docufant)
  (select-old-people :butcher 50 select-old-person-docufant)
  (select-hv-widgets select-person-docufant select-hv-widgets-docufant)
  (println ""))


(defn bench-sql [people insert]
  (println "SQL: ")
  (create-table-sql)
  (if insert (insert-people people insert-person-sql))
  (select-people :butcher select-person-sql)
  (select-old-people :butcher 50 select-old-person-sql)
  (select-hv-widgets select-person-sql select-hv-widgets-sql)
  (println ""))



(defn -main []

  (drop-tables)

  (let [people (doall (gen/sample person-gen 1000))]
    (bench-docufant people true)
    (bench-sql people true)
    ))
