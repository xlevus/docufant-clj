(ns docufant.core-test
  (:require [docufant.core :as doc]
            [docufant.db :as db]
            [docufant.postgres :as pg]
            [docufant.operator :as oper]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.java.jdbc :as j]))


(def ^:dynamic *db-spec* {:dbtype "postgresql"
              :dbname "docufant_test"
              :user "docufant"
              :password "password"})


(defn tables [f]
  ;;(db/transaction
   (db/create-tables! *db-spec*)
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


(deftest test-queries
  (testing "equality"
    (let [i1 (doc/create! *db-spec* :type1 {:a 1 :name "i1"})
          i2 (doc/create! *db-spec* :type1 {:a {:b 2} :name "i2"})
          i3 (doc/create! *db-spec* :type1 {:a {:b 2 :c {:z 1 :x 2}} :name "i3"})
          i4 (doc/create! *db-spec* :type2 {:a {:b 3 :c {:x 2 :y 1}} :name "i4"})]

      (is (= [i1] (doc/select *db-spec* nil (oper/= [:a] 1))))
      (is (= [i3 i4] (doc/select *db-spec* nil (oper/= [:a :c :x] 2))))
      (is (= [i3] (doc/select *db-spec* :type1 (oper/= [:a :c :x] 2))))
      (is (= [i4] (doc/select *db-spec* :type2 (oper/= [:a :c :x] 2))))
      ))

  (testing "inequality"
    (let [i1 (doc/create! *db-spec* :ordered {:a 1 :b 3})
          i2 (doc/create! *db-spec* :ordered {:a 2 :b 4})
          i3 (doc/create! *db-spec* :ordered {:a 3 :b 5 :c {:d 10}})
          i4 (doc/create! *db-spec* :ordered {:b 6 :c {:d 5}})]

      (is (= [i1] (doc/select *db-spec* :ordered (oper/< [:a] 2))))
      (is (= [i3] (doc/select *db-spec* :ordered (oper/> [:a] 2))))
      (is (= [i1 i2] (doc/select *db-spec* :ordered (oper/<= [:a] 2))))
      (is (= [i2 i3] (doc/select *db-spec* :ordered (oper/>= [:a] 2))))
      (is (= [i2 i3] (doc/select *db-spec* :ordered (oper/<> [:a] 1))))

      (is (= [i3] (doc/select *db-spec* :ordered
                              (oper/> [:b] 1)
                              (oper/> [:c :d] 6))))

      (is (= [i1 i2] (doc/select *db-spec* :ordered (oper/> [:b] 1)
                                       :limit 2)))

      (is (= [i3 i4] (doc/select *db-spec* :ordered (oper/> [:b] 1)
                                 :limit 2
                                 :offset 2)))

      (is (= [i4 i3 i2 i1] (doc/select *db-spec* :ordered (oper/> [:b] 1)
                                       :order-by [[:b] :desc])))
      ))

  (testing "get"
    (let [i1 (doc/create! *db-spec* :test {:a 1})
          i2 (doc/create! *db-spec* :test {:a 3})]
      (is (= i1 (doc/get *db-spec* (:id i1))))
      (is (nil? (doc/get *db-spec* [:foo 1])))

      )))
