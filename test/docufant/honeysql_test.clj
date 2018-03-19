(ns docufant.honeysql-test
  (:require [docufant.honeysql :as df-honeysql]
            [docufant.postgres :as pg]
            [honeysql.core :as sql]
            [clojure.test :as t :refer [deftest testing is]]))


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
