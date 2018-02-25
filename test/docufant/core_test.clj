(ns docufant.core-test
  (:require [docufant.core :as doc]
            [docufant.db :as db]
            [docufant.postgres :as pg]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.java.jdbc :as j]))


(def db-spec {:dbtype "postgresql" :dbname "docufant_test" :user "user" :password "pass"})


(defn tables [f]
  (db/set-default-connection! db-spec)
  ;;(db/transaction
   (doc/init! db-spec)
   (f)
   ;; (db/rollback!)
   ;;)
  (db/set-default-connection! nil))


(defn transaction [f]
  (db/transaction
   (f)
   (db/rollback!)))

(t/use-fixtures :once tables)
(t/use-fixtures :each transaction)


(deftest test-mutations
  (testing "create!"
    (let [inst (doc/create! :test {:a 100})
          [type id] (:id inst)]
      (is (= :test type))
      (is (int? id))
      (is (= {:a 100} (dissoc inst :id)))
      ))

  (testing "update!"
    (let [inst (doc/create! :test {:a 200})
          id (:id inst)]
      (doc/update! id {:a 300})
      (is (= 300 (:a (doc/get id)))))
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
    (is (= "SELECT * FROM docufant WHERE _type = ?" (first (doc/format-sql :test []))))
    (is (= "SELECT * FROM docufant WHERE _type = ? AND _data = ?"
           (first (doc/format-sql :test [[:= 1]]))))
    (is (= "SELECT * FROM docufant WHERE _type = ? AND _data = ? AND _data @> ?"
           (first (doc/format-sql :test [[:= 1] [:contains {:foo 1}]]))))
    ))

(deftest test-queries
  (testing "equality"
    (let [i1 (doc/create! :test {:a 1 :name "i1"})
          i2 (doc/create! :test {:a {:b 2} :name "i2"})
          i3 (doc/create! :tess {:a {:b 2 :c {:z 1 :x 2}} :name "i3"})
          i4 (doc/create! :tess {:a {:b 3 :c {:x 2 :y 1}} :name "i4"})]

      (is (= [i1 i2 i3 i4] (doc/select nil [])))
      (is (= [i1 i2] (doc/select :test [])))

      (is (= [i1] (doc/select :test [[:= :a 1]])))
      (is (= [i2] (doc/select :test [[:= [:a :b] 2]])))

      (is (= [i1 i2] (doc/select :test [[:has-keys [:a :name]]])))
      (is (= [i3 i4] (doc/select :tess [[:has-keys :a [:b]]])))
      (is (= [i4] (doc/select :tess [[:has-keys [:a :c] [:x :y]]])))

      (is (= [i2 i3] (doc/select nil [[:contains {:a {:b 2}}]])))
      (is (= [i3] (doc/select :tess [[:contains {:a {:c {:z 1}}}]])))
      ))

  (testing "inequality"
    (let [i1 (doc/create! :ordered {:a 1})
          i2 (doc/create! :ordered {:a 2})
          i3 (doc/create! :ordered {:a 3 :c {:d 10}})
          i4 (doc/create! :ordered {:b 1})]
      (is (= [] (doc/select :ordered [[:< :a 0]])))
      (is (= [i1 i2] (doc/select :ordered [[:<= :a 2]])))
      (is (= [i3] (doc/select :ordered [[:> :a 1]
                                        [:> [:c :d] 5]])))
      ))

  (testing "get"
    (let [i1 (doc/create! :test {:a 1})
          i2 (doc/create! :test {:a 3})]
      (is (= i1 (doc/get (:id i1))))
      (is (nil? (doc/get [:foo 1])))
      )))
