(ns docufant.honeysql-test
  (:require [docufant.honeysql :as dhoney]
            [docufant.postgres :as pg]
            [honeysql.core :as sql]
            [clojure.test :as t :refer [deftest testing is]])
  (:use [docufant.honeysql]))


(defn where [& clause]
  (sql/format {:where clause}))


(deftest test-honeysql-fn-handler
  (testing "="
    (is (= ["WHERE (data) = ?" (pg/jsonb 1)]
           (where "jsonb=" [:data] 1)))

    (is (= ["WHERE (data -> ?) = ?" "a" (pg/jsonb 1)]
           (where "jsonb=" [:data :a] 1)))

    (is (= ["WHERE (data #> ?) = ?" (pg/text-array [:a :b]) (pg/jsonb 1)]
           (where "jsonb=" [:data :a :b] 1)))

    ))


(deftest test-honeysql-fn-handler-casts
  (testing "="
    (is (= ["WHERE (data)::int = ?" 1]
           (where "jsonb=" [:data] 1 Long)))

    (is (= ["WHERE (data ->> ?)::int = ?" "a" 1]
           (where "jsonb=" [:data :a] 1 Long)))

    (is (= ["WHERE (data #>> ?)::int = ?" (pg/text-array [:a :b]) 1]
           (where "jsonb=" [:data :a :b] 1 Long)))

    ))


(deftest test-honeysql-create-index
  (is (= ["CREATE INDEX IF NOT EXISTS indexname ON field"]
      (sql/format {:create-index {:name :indexname
                                  :on :field}})))

  (is (= ["CREATE INDEX indexname ON field WHERE (data ->> ?)::int = ?"
          "a" 1]
         (sql/format {:create-index {:name :indexname
                                     :force true
                                     :on :field}
                      :where ["jsonb=" [:data :a] 1 Long]})))

  (is (= ["CREATE INDEX indexname ON (data #> ?)"
          (pg/text-array [:a :b])]
         (sql/format {:create-index {:name :indexname
                                     :force true
                                     :on (->JsonbPath :data [:a :b] nil)}})))
  )
