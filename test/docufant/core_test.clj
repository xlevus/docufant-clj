(ns docufant.core-test
  (:require [docufant.core :as doc]
            [docufant.db :as db]
            [docufant.postgres :as pg]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.java.jdbc :as j]))


(def ^:dynamic *db-spec* {:dbtype "postgresql"
              :dbname "docufant_test"
              :user "docufant"
              :password "password"})


(defn tables [f]
  ;;(db/transaction
   (doc/init! *db-spec*)
  (f)
  )


(defn transaction [f]
  (j/with-db-transaction [t-con *db-spec*]
    (binding [*db-spec* t-con]
      (f))
   (j/db-set-rollback-only! t-con)))

(t/use-fixtures :once tables)
(t/use-fixtures :each transaction)


(deftest test-mutations
  (testing "create!"
    (let [inst (doc/create! *db-spec* :test {:a 100})
          [type id] (:id inst)]
      (is (= :test type))
      (is (int? id))
      (is (= {:a 100} (dissoc inst :id)))
      ))

  (testing "update!"
    (let [inst (doc/create! *db-spec* :test {:a 200})
          id (:id inst)]
      (doc/update! *db-spec* id {:a 300})
      (is (= 300 (:a (doc/get *db-spec* id)))))
  ))


(deftest test-clause-handler
  (testing "="
    (is (= ["_data = ?"
            (pg/jsonb 1)] (doc/clause-handler := 1)))
    (is (= ["_data -> ? = ?" "a"
            (pg/jsonb 1)] (doc/clause-handler := :a 1)))
    (is (= ["_data #> ? = ?"
            (pg/text-array [:a :b])
            (pg/jsonb 1)] (doc/clause-handler := [:a :b] 1)))
    )

  (testing ">"
    (is (= ["_data > ?"
            (pg/jsonb 1)] (doc/clause-handler :> 1)))
    (is (= ["_data -> ? > ?" "a"
            (pg/jsonb 1)] (doc/clause-handler :> :a 1)))
    (is (= ["_data #> ? > ?"
            (pg/text-array [:a :b])
            (pg/jsonb 1)] (doc/clause-handler :> [:a :b] 1)))
    )

  (testing ":contains"
    (is (= ["_data @> ?" (pg/jsonb {:foo 1})] (doc/clause-handler :contains {:foo 1})))
    )

  (testing ":has-keys"
    (is (= ["_data ??& ?" (pg/text-array [:a :b])] (doc/clause-handler :has-keys [:a :b])))
    (is (= ["_data -> ? ??& ?" "a" (pg/text-array [:a :b])] (doc/clause-handler :has-keys :a [:a :b])))
    )
  )

(deftest test-query-builder
  (testing "format-sql"
    (is (= "SELECT * FROM docufant WHERE _type = ?" (first (doc/format-sql *db-spec* :test []))))
    (is (= "SELECT * FROM docufant WHERE _type = ? AND _data = ?"
           (first (doc/format-sql *db-spec* :test [[:= 1]]))))
    (is (= "SELECT * FROM docufant WHERE _type = ? AND _data = ? AND _data @> ?"
           (first (doc/format-sql *db-spec* :test [[:= 1] [:contains {:foo 1}]]))))
    ))

(deftest test-queries
  (testing "equality"
    (let [i1 (doc/create! *db-spec* :test {:a 1 :name "i1"})
          i2 (doc/create! *db-spec* :test {:a {:b 2} :name "i2"})
          i3 (doc/create! *db-spec* :tess {:a {:b 2 :c {:z 1 :x 2}} :name "i3"})
          i4 (doc/create! *db-spec* :tess {:a {:b 3 :c {:x 2 :y 1}} :name "i4"})]

      (is (= [i1 i2 i3 i4] (doc/select *db-spec* nil [])))
      (is (= [i1 i2] (doc/select *db-spec* :test [])))

      (is (= [i1] (doc/select *db-spec* :test [[:= :a 1]])))
      (is (= [i2] (doc/select *db-spec* :test [[:= [:a :b] 2]])))

      (is (= [i1 i2] (doc/select *db-spec* :test [[:has-keys [:a :name]]])))
      (is (= [i3 i4] (doc/select *db-spec* :tess [[:has-keys :a [:b]]])))
      (is (= [i4] (doc/select *db-spec* :tess [[:has-keys [:a :c] [:x :y]]])))

      (is (= [i2 i3] (doc/select *db-spec* nil [[:contains {:a {:b 2}}]])))
      (is (= [i3] (doc/select *db-spec* :tess [[:contains {:a {:c {:z 1}}}]])))
      ))

  (testing "inequality"
    (let [i1 (doc/create! *db-spec* :ordered {:a 1})
          i2 (doc/create! *db-spec* :ordered {:a 2})
          i3 (doc/create! *db-spec* :ordered {:a 3 :c {:d 10}})
          i4 (doc/create! *db-spec* :ordered {:b 1})]
      (is (= [] (doc/select *db-spec* :ordered [[:< :a 0]])))
      (is (= [i1 i2] (doc/select *db-spec* :ordered [[:<= :a 2]])))
      (is (= [i3] (doc/select *db-spec* :ordered [[:> :a 1]
                                        [:> [:c :d] 5]])))
      ))

  (testing "get"
    (let [i1 (doc/create! *db-spec* :test {:a 1})
          i2 (doc/create! *db-spec* :test {:a 3})]
      (is (= i1 (doc/get *db-spec* (:id i1))))
      (is (nil? (doc/get *db-spec* [:foo 1])))
      )))


(deftest postgres
  (testing "reduce-q"
    (is (= ["SELECT A"] (pg/reduce-q ["SELECT " "A"])))
    (is (= ["SELECT a = ?" 11] (pg/reduce-q ["SELECT " ["a = ?" 11]])))
    (is (= ["SELECT a = ? AND b = ?" 11 12]
           (pg/reduce-q ["SELECT " ["a = ?" 11] " AND " ["b = ?" 12]])))
    )
  (testing "json-subq"
    (is (= ["_data"] (pg/json-subq :_data nil)))
    (is (= ["(_data)::int"] (pg/json-subq :_data nil {:as Integer})))

    (is (= ["_data -> ?" "a"] (pg/json-subq :_data :a)))

    (is (= ["(_data ->> ?)::int" "a"] (pg/json-subq :_data :a {:as Integer})))
    (is (= ["(_data ->> ?)::int" "a"] (pg/json-subq :_data :a {:as Long})))
    (is (= ["(_data ->> ?)::float" "a"] (pg/json-subq :_data :a {:as Double})))

    (is (= ["_data #> ?" (pg/json-path [:a :b])] (pg/json-subq :_data [:a :b])))
    (is (= ["(_data #>> ?)::int" (pg/json-path [:a :b])] (pg/json-subq :_data [:a :b] {:as Integer})))
    (is (= ["(_data #>> ?)::int" (pg/json-path [:a :b])] (pg/json-subq :_data [:a :b] {:as Long})))
    (is (= ["(_data #>> ?)::float" (pg/json-path [:a :b])] (pg/json-subq :_data [:a :b] {:as Double})))
    ))


(deftest test-indexes
  (testing "format-index"
    (is (= ["CREATE INDEX IF NOT EXISTS idx_docufant__name ON docufant ( clause )"]
           (db/format-index *db-spec* "name" "clause" {})))

    (is (= ["CREATE INDEX IF NOT EXISTS idx_docufant__test__typed ON docufant ( clause ) WHERE _type = ?"
            :test]
           (db/format-index *db-spec* "typed" "clause" {:type :test})))

    (is (= ["CREATE UNIQUE INDEX IF NOT EXISTS idx_docufant__unique ON docufant ( clause )"]
           (db/format-index *db-spec* "unique" "clause" {:unique true})))

    (is (= ["CREATE INDEX idx_docufant__force ON docufant ( clause )"]
           (db/format-index {:force true
                             :db-spec {}} "force" "clause" {})))

    ))
